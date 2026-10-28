"""
Capteurs virtuels — phase 0 (sans apprentissage).

Calcule des *indices* proches de ce que mesureraient des capteurs physiques, à partir des
satellites uniquement :
  - humidité du sol de surface : détection de changement sur le radar Sentinel-1 (VV) ;
  - évapotranspiration de référence ET0 : FAO-56 Penman-Monteith avec NASA POWER ;
  - bilan hydrique : pluie − ET0 sur 7 et 14 jours.

Ces valeurs ne sont PAS des mesures. Quand les capteurs physiques seront installés, un modèle
entraîné dessus les remplacera (même format de réponse : `type` passera de "indice" à "modele").

Les fonctions de calcul (ET0, SMI, bilan) sont pures : testables sans Earth Engine.
"""
import json
import math
import urllib.request
from datetime import date, datetime, timedelta
from typing import Any, Optional

S1_COLLECTION = "COPERNICUS/S1_GRD"
NASA_URL = "https://power.larc.nasa.gov/api/temporal/daily/point"
NASA_PARAMS = "T2M,T2M_MAX,T2M_MIN,RH2M,ALLSKY_SFC_SW_DWN,WS10M,PRECTOTCORR"
NASA_FILL = -999.0
HISTORIQUE_RADAR_JOURS = 365
MIN_POINTS_RADAR = 8
# En dessous de cette plage (5ᵉ-95ᵉ percentile, en dB), le signal est du bruit : pas d'indice.
DYNAMIQUE_MIN_DB = 1.0
# Le SSM d'entraînement plafonne exactement à 65 : on applique le même plafond (borne basse non touchée).
SSM_PLAFOND_ENTRAINEMENT = 65.0


# ---------------------------------------------------------------------------
# Évapotranspiration de référence — FAO-56 Penman-Monteith (pas de temps journalier)
# ---------------------------------------------------------------------------
def _e0(t: float) -> float:
    """Pression de vapeur saturante (kPa) à la température t (°C)."""
    return 0.6108 * math.exp(17.27 * t / (t + 237.3))


def _rayonnement_extraterrestre(lat_deg: float, jour_annee: int) -> float:
    """Ra en MJ/m²/jour (FAO-56 éq. 21)."""
    phi = math.radians(lat_deg)
    dr = 1 + 0.033 * math.cos(2 * math.pi * jour_annee / 365)
    delta = 0.409 * math.sin(2 * math.pi * jour_annee / 365 - 1.39)
    x = max(-1.0, min(1.0, -math.tan(phi) * math.tan(delta)))
    ws = math.acos(x)
    return (24 * 60 / math.pi) * 0.0820 * dr * (
        ws * math.sin(phi) * math.sin(delta) + math.cos(phi) * math.cos(delta) * math.sin(ws)
    )


def et0_fao56(tmax: float, tmin: float, rh_moy: float, rs_mj: float, vent10: float,
              lat_deg: float, altitude_m: float, jour_annee: int) -> float:
    """ET0 journalière en mm/jour."""
    tmoy = (tmax + tmin) / 2
    es = (_e0(tmax) + _e0(tmin)) / 2
    ea = es * max(0.0, min(rh_moy, 100.0)) / 100

    delta = 4098 * _e0(tmoy) / (tmoy + 237.3) ** 2
    pression = 101.3 * ((293 - 0.0065 * altitude_m) / 293) ** 5.26
    gamma = 0.000665 * pression

    # Rayonnement net
    ra = _rayonnement_extraterrestre(lat_deg, jour_annee)
    rso = (0.75 + 2e-5 * altitude_m) * ra
    rns = (1 - 0.23) * rs_mj
    ratio = max(0.3, min(rs_mj / rso, 1.0)) if rso > 0 else 0.5
    sigma = 4.903e-9
    rnl = sigma * (((tmax + 273.16) ** 4 + (tmin + 273.16) ** 4) / 2) \
        * (0.34 - 0.14 * math.sqrt(max(ea, 0))) * (1.35 * ratio - 0.35)
    rn = rns - rnl

    u2 = vent10 * 4.87 / math.log(67.8 * 10 - 5.42)  # vent à 10 m -> 2 m
    num = 0.408 * delta * rn + gamma * 900 / (tmoy + 273) * u2 * (es - ea)
    den = delta + gamma * (1 + 0.34 * u2)
    return max(0.0, num / den)


# ---------------------------------------------------------------------------
# Humidité du sol par radar — détection de changement (Wagner et al.)
# ---------------------------------------------------------------------------
def _percentile(valeurs: list[float], p: float) -> float:
    v = sorted(valeurs)
    k = (len(v) - 1) * p
    f, c = math.floor(k), math.ceil(k)
    return v[int(k)] if f == c else v[f] + (v[c] - v[f]) * (k - f)


def indice_humidite_radar(serie_vv: list[tuple[str, float]]) -> Optional[list[dict[str, Any]]]:
    """
    Normalise VV (dB) entre le 5ᵉ percentile (sol sec) et le 95ᵉ (sol humide) de l'historique :
    SMI = (VV − sec) / (humide − sec), en %. Renvoie None si l'historique est insuffisant.
    """
    if len(serie_vv) < MIN_POINTS_RADAR:
        return None
    vals = [v for _, v in serie_vv]
    sec, humide = _percentile(vals, 0.05), _percentile(vals, 0.95)
    if humide - sec < DYNAMIQUE_MIN_DB:  # signal quasi constant (ex. forêt dense) : indice non fiable
        return None
    return [
        {"date": d, "valeur": round(max(0.0, min(1.0, (v - sec) / (humide - sec))) * 100, 1)}
        for d, v in serie_vv
    ]


# ---------------------------------------------------------------------------
# NASA POWER (quotidien, ~dernier mois)
# ---------------------------------------------------------------------------
def _nasa_quotidien(lat: float, lon: float, jours: int = 30) -> dict[str, dict[str, float]]:
    fin = date.today() - timedelta(days=1)
    debut = fin - timedelta(days=jours)
    url = (f"{NASA_URL}?parameters={NASA_PARAMS}&community=AG&longitude={lon:.6f}&latitude={lat:.6f}"
           f"&start={debut:%Y%m%d}&end={fin:%Y%m%d}&format=JSON")
    with urllib.request.urlopen(url, timeout=40) as r:
        brut = json.load(r)
    param = brut["properties"]["parameter"]
    jours_data: dict[str, dict[str, float]] = {}
    for nom, serie in param.items():
        for d, v in serie.items():
            if v is not None and v != NASA_FILL:
                jours_data.setdefault(d, {})[nom] = float(v)
    return jours_data


def _et0_et_pluie(brut: dict[str, dict[str, float]], lat: float, altitude_m: float) -> list[dict[str, Any]]:
    """Série journalière {date, et0, pluie} (jours complets uniquement), triée par date."""
    requis = {"T2M_MAX", "T2M_MIN", "RH2M", "ALLSKY_SFC_SW_DWN", "WS10M", "PRECTOTCORR"}
    out = []
    for d, v in sorted(brut.items()):
        if not requis.issubset(v):
            continue
        dt = datetime.strptime(d, "%Y%m%d").date()
        et0 = et0_fao56(v["T2M_MAX"], v["T2M_MIN"], v["RH2M"], v["ALLSKY_SFC_SW_DWN"],  # déjà en MJ/m²/j avec community=AG
                        
                        v["WS10M"], lat, altitude_m, dt.timetuple().tm_yday)
        out.append({"date": dt.isoformat(), "et0": round(et0, 2), "pluie": round(v["PRECTOTCORR"], 2)})
    return out


# ---------------------------------------------------------------------------
# Earth Engine : série radar + altitude
# ---------------------------------------------------------------------------
def _serie_radar(geom) -> list[tuple[str, float]]:
    """VV moyen (dB) par date sur le polygone, pour UNE seule géométrie d'orbite (angle d'incidence stable)."""
    import ee

    fin = date.today()
    debut = fin - timedelta(days=HISTORIQUE_RADAR_JOURS)
    col = (
        ee.ImageCollection(S1_COLLECTION)
        .filterBounds(geom).filterDate(debut.isoformat(), fin.isoformat())
        .filter(ee.Filter.eq("instrumentMode", "IW"))
        .filter(ee.Filter.listContains("transmitterReceiverPolarisation", "VV"))
    )
    orbites = col.aggregate_histogram("relativeOrbitNumber_start").getInfo() or {}
    if not orbites:
        return []
    orbite = int(float(max(orbites, key=orbites.get)))
    col = col.filter(ee.Filter.eq("relativeOrbitNumber_start", orbite))
    # Plateformes différentes (ex. S1C/S1D) = léger décalage systématique : on n'en garde qu'une.
    plateformes = col.aggregate_histogram("platform_number").getInfo() or {}
    if plateformes:
        col = col.filter(ee.Filter.eq("platform_number", max(plateformes, key=plateformes.get)))

    def _stats(img):
        m = img.select("VV").reduceRegion(ee.Reducer.mean(), geom, 10, maxPixels=int(1e9), bestEffort=True)
        return ee.Feature(None, {"t": img.date().format("YYYY-MM-dd"), "vv": m.get("VV")})

    feats = ee.FeatureCollection(col.map(_stats)).getInfo().get("features", [])
    par_date: dict[str, list[float]] = {}
    for f in feats:
        p = f["properties"]
        if p.get("vv") is not None:
            par_date.setdefault(p["t"], []).append(float(p["vv"]))
    return [(d, sum(v) / len(v)) for d, v in sorted(par_date.items())]


def _altitude(geom) -> float:
    import ee

    try:
        v = ee.Image("USGS/SRTMGL1_003").reduceRegion(
            ee.Reducer.mean(), geom, 30, maxPixels=int(1e8), bestEffort=True).get("elevation").getInfo()
        return float(v) if v is not None else 0.0
    except Exception:
        return 0.0


# ---------------------------------------------------------------------------
# Orchestration
# ---------------------------------------------------------------------------
def _confiance_radar(ndvi_moyen: Optional[float]) -> tuple[str, str]:
    if ndvi_moyen is None:
        return "faible", "Couvert végétal inconnu (nuages) : le radar peut être peu sensible au sol."
    if ndvi_moyen > 0.7:
        return "faible", "Végétation dense (NDVI > 0,7) : le radar voit surtout la canopée, pas le sol."
    if ndvi_moyen > 0.4:
        return "moyenne", "Végétation modérée : sensibilité correcte du radar à l'humidité du sol."
    return "bonne", "Couvert végétal faible : le radar est sensible à l'humidité du sol."


def _features_modeles(brut: dict[str, dict[str, float]], ndvi: Optional[float], ndwi: Optional[float],
                      s1: Optional[dict[str, Any]], ssm: Optional[float] = None) -> tuple[Optional[dict[str, Optional[float]]], str, str]:
    """Entrées des modèles IA : dernier jour NASA complet + composites Sentinel. (features, date, sources)"""
    requis = {"T2M", "RH2M", "PRECTOTCORR", "ALLSKY_SFC_SW_DWN"}
    jours = [d for d in sorted(brut) if requis.issubset(brut[d])]
    if not jours:
        return None, "", ""
    j = brut[jours[-1]]
    radar = s1 if s1 and s1.get("statut") == "ok" else {}
    features = {
        "NASA_Temp_Air_C": j["T2M"], "NASA_Hum_Air_pct": j["RH2M"], "NASA_Rain_mm": j["PRECTOTCORR"],
        "NASA_Solar_Rad": j["ALLSKY_SFC_SW_DWN"],
        "Sentinel1_VV": radar.get("vv_db"), "Sentinel1_VH": radar.get("vh_db"), "Sentinel1_RVI": radar.get("rvi"),
        "Sentinel1_SSM_Surface": None if ssm is None else min(ssm, SSM_PLAFOND_ENTRAINEMENT),
        "Sentinel2_NDVI": ndvi, "Sentinel2_NDWI": ndwi,
    }
    d = jours[-1]
    sources = (f"NASA POWER du {d[:4]}-{d[4:6]}-{d[6:]}, composites Sentinel-1/2 des 60 derniers jours")
    return features, f"{d[:4]}-{d[4:6]}-{d[6:]}", sources


def _ajouter_modeles(capteurs: list, erreurs: dict, brut, ndvi, ndwi, s1, ssm=None) -> None:
    """Les modèles IA ne doivent jamais faire échouer les autres capteurs."""
    try:
        import ml_sensors

        features, date_mesure, sources = _features_modeles(brut, ndvi, ndwi, s1, ssm)
        if features is None:
            for code in ml_sensors.CAPTEURS:
                erreurs[code] = "Données NASA POWER récentes insuffisantes pour alimenter le modèle."
            return
        for code in ml_sensors.CAPTEURS:
            capteur, raison = ml_sensors.predire(code, features, date_mesure, sources)
            if capteur:
                capteurs.append(capteur)
            else:
                erreurs[code] = raison
    except Exception as e:
        erreurs["modeles_ia"] = f"Modèles IA indisponibles : {e}"


def calculer(ring: list[list[float]], ndvi_moyen: Optional[float], ndwi_moyen: Optional[float] = None,
             s1: Optional[dict[str, Any]] = None) -> dict[str, Any]:
    """Renvoie la liste des capteurs virtuels (indices phase 0 + modèles IA) pour le polygone."""
    import ee

    geom = ee.Geometry.Polygon([ring + [ring[0]]], proj="EPSG:4326", geodesic=False)
    lat = sum(p[1] for p in ring) / len(ring)
    lon = sum(p[0] for p in ring) / len(ring)

    capteurs: list[dict[str, Any]] = []
    erreurs: dict[str, str] = {}
    ssm: Optional[float] = None

    # --- Humidité du sol (radar) ---
    try:
        serie = _serie_radar(geom)
        smi = indice_humidite_radar(serie)
        if smi is None:
            erreurs["humidite_sol"] = (
                "Historique radar Sentinel-1 insuffisant sur ce polygone." if len(serie) < MIN_POINTS_RADAR else
                "Signal radar quasi constant (variation < 1 dB sur 12 mois) : couvert végétal trop dense "
                "pour estimer l'humidité du sol par radar. Un capteur physique est nécessaire ici.")
        else:
            ssm = smi[-1]["valeur"]
            niveau, note = _confiance_radar(ndvi_moyen)
            capteurs.append({
                "code": "humidite_sol", "nom": "Humidité du sol (surface)",
                "valeur": smi[-1]["valeur"], "unite": "% (relatif)",
                "date_mesure": smi[-1]["date"], "serie": smi[-12:],
                "type": "indice", "confiance": niveau,
                "methode": "Radar Sentinel-1 VV — détection de changement sur 12 mois",
                "note": note + " 0 % = le plus sec observé, 100 % = le plus humide observé sur la parcelle.",
            })
    except Exception as e:
        erreurs["humidite_sol"] = f"Erreur Earth Engine : {e}"

    # --- ET0 et bilan hydrique (NASA POWER) ---
    try:
        brut = _nasa_quotidien(lat, lon)
        jours = _et0_et_pluie(brut, lat, _altitude(geom))
        _ajouter_modeles(capteurs, erreurs, brut, ndvi_moyen, ndwi_moyen, s1, ssm)
        if len(jours) < 7:
            erreurs["et0"] = "Données NASA POWER insuffisantes sur la période récente."
        else:
            derniers = jours[-14:]
            capteurs.append({
                "code": "et0", "nom": "Évapotranspiration de référence (ET0)",
                "valeur": derniers[-1]["et0"], "unite": "mm/j",
                "date_mesure": derniers[-1]["date"],
                "serie": [{"date": j["date"], "valeur": j["et0"]} for j in derniers],
                "type": "calcul", "confiance": "moyenne",
                "methode": "FAO-56 Penman-Monteith avec NASA POWER (grille ~50 km)",
                "note": "Eau que perdrait un gazon de référence bien alimenté. À multiplier par le coefficient de la culture.",
            })
            capteurs.append({
                "code": "pluie", "nom": "Pluie (14 jours)",
                "valeur": round(sum(j["pluie"] for j in derniers), 1), "unite": "mm",
                "date_mesure": derniers[-1]["date"],
                "serie": [{"date": j["date"], "valeur": j["pluie"]} for j in derniers],
                "type": "donnee", "confiance": "moyenne",
                "methode": "NASA POWER (précipitations corrigées, grille ~50 km)",
                "note": "Cumul des précipitations estimées par satellite. Peut différer d'un pluviomètre local.",
            })
            for nb in (7, 14):
                f = jours[-nb:]
                bilan = sum(j["pluie"] for j in f) - sum(j["et0"] for j in f)
                capteurs.append({
                    "code": f"bilan_hydrique_{nb}j", "nom": f"Bilan hydrique ({nb} jours)",
                    "valeur": round(bilan, 1), "unite": "mm",
                    "date_mesure": f[-1]["date"], "serie": None,
                    "type": "calcul", "confiance": "moyenne",
                    "methode": "Pluie NASA POWER − ET0",
                    "note": ("Déficit : le sol se dessèche, irrigation à envisager." if bilan < -10 else
                             "Excédent : l'eau apportée dépasse la demande." if bilan > 10 else
                             "Apports et demande en eau proches de l'équilibre."),
                })
    except Exception as e:
        erreurs["et0"] = f"Erreur NASA POWER : {e}"

    return {
        "statut": "ok" if capteurs else "no_data",
        "capteurs": capteurs,
        "erreurs": erreurs,
        "avertissement": ("Valeurs estimées à partir des satellites, pas des mesures de capteurs. "
                          "Les modèles IA ne sont pas validés sur des capteurs physiques de ces parcelles."),
    }

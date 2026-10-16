"""
Service d'extraction Sentinel-1 + Sentinel-2 (Agro-Parcelles).

Reçoit un polygone GeoJSON depuis le backend Spring, le valide
(bbox / centroïde / superficie / nombre de sommets) puis calcule le NDVI réel
et la couverture nuageuse à partir de l'imagerie Sentinel-2 (COPERNICUS/S2_SR_HARMONIZED),
ainsi que la rétrodiffusion radar VV/VH et le RVI à partir de Sentinel-1 (COPERNICUS/S1_GRD),
via Google Earth Engine.

Authentification GEE : compte de service (service account). Les identifiants sont
lus depuis l'environnement, jamais en dur :
  - GEE_PROJECT            : id du projet Google Cloud associé à Earth Engine
  - GEE_SERVICE_ACCOUNT    : email du compte de service (…@….iam.gserviceaccount.com)
  - GEE_PRIVATE_KEY_FILE   : chemin du fichier JSON de la clé du compte de service

Si ces variables sont absentes, /extract renvoie 503 avec un message explicite.
"""
import math
import os
from datetime import date, timedelta
from typing import Any, Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

app = FastAPI(
    title="Agro-Parcelles — Service d'extraction Sentinel-2",
    description="Reçoit un polygone GeoJSON et renvoie le NDVI réel via Google Earth Engine.",
    version="1.0.0",
)

# Le backend Spring est le seul appelant attendu, mais on autorise aussi
# un appel direct depuis le front en développement.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8082", "http://localhost:4200", "http://localhost:3000"],
    allow_methods=["*"],
    allow_headers=["*"],
)

EARTH_RADIUS_M = 6_371_000.0
M2_TO_HA = 1.0 / 10_000.0

# Collection Sentinel-2 niveau 2A (réflectance de surface), version harmonisée.
S2_COLLECTION = "COPERNICUS/S2_SR_HARMONIZED"
# Sentinel-1 GRD (radar bande C), déjà calibré en dB dans GEE.
S1_COLLECTION = "COPERNICUS/S1_GRD"
# Classes SCL considérées comme nuage / ombre / cirrus à masquer.
SCL_NUAGES = [3, 8, 9, 10]  # 3=ombre, 8=nuage moyen, 9=nuage haute proba, 10=cirrus
# Période par défaut si aucune date fournie (Sentinel-2 revisite ~5 j ; 90 j ≈ marge anti-nuages).
PERIODE_DEFAUT_JOURS = 90
# On ignore les scènes trop nuageuses globalement avant même le masquage par pixel.
SEUIL_NUAGE_SCENE_PCT = 80


class ExtractionRequest(BaseModel):
    """Payload envoyé par Spring : un GeoJSON + une période optionnelle."""
    geojson: dict[str, Any] = Field(..., description="Feature / geometry / FeatureCollection GeoJSON")
    date_debut: Optional[str] = None
    date_fin: Optional[str] = None


# ---------------------------------------------------------------------------
# Validation / géométrie du polygone (indépendant de GEE)
# ---------------------------------------------------------------------------
def _extraire_anneau(geojson: dict[str, Any]) -> list[list[float]]:
    """
    Extrait le premier anneau de coordonnées [[lon, lat], ...] quel que soit
    le conteneur GeoJSON (FeatureCollection, Feature, geometry, ou Polygon brut).
    """
    obj = geojson
    if obj.get("type") == "FeatureCollection":
        features = obj.get("features") or []
        if not features:
            raise ValueError("FeatureCollection vide.")
        obj = features[0]
    if obj.get("type") == "Feature":
        obj = obj.get("geometry") or {}

    geom_type = obj.get("type")
    coords = obj.get("coordinates")
    if geom_type != "Polygon" or not coords:
        raise ValueError(f"Géométrie attendue 'Polygon', reçue '{geom_type}'.")

    ring = coords[0]
    if not isinstance(ring, list) or len(ring) < 3:
        raise ValueError("Le polygone doit contenir au moins 3 sommets.")
    return ring


def _sommets_distincts(ring: list[list[float]]) -> list[list[float]]:
    """Retire le sommet de fermeture s'il duplique le premier."""
    if len(ring) >= 2 and ring[0] == ring[-1]:
        return ring[:-1]
    return ring


def _bbox(ring: list[list[float]]) -> list[float]:
    lons = [p[0] for p in ring]
    lats = [p[1] for p in ring]
    return [min(lons), min(lats), max(lons), max(lats)]


def _centroide(ring: list[list[float]]) -> dict[str, float]:
    lons = [p[0] for p in ring]
    lats = [p[1] for p in ring]
    return {"lat": sum(lats) / len(lats), "lon": sum(lons) / len(lons)}


def _superficie_ha(ring: list[list[float]]) -> float:
    """Shoelace sur projection équirectangulaire locale (parcelles < 100 km)."""
    if len(ring) < 3:
        return 0.0
    lat_ref = sum(p[1] for p in ring) / len(ring)
    cos_lat = math.cos(math.radians(lat_ref))
    aire = 0.0
    n = len(ring)
    for i in range(n):
        lon1, lat1 = ring[i]
        lon2, lat2 = ring[(i + 1) % n]
        x1 = math.radians(lon1) * cos_lat * EARTH_RADIUS_M
        y1 = math.radians(lat1) * EARTH_RADIUS_M
        x2 = math.radians(lon2) * cos_lat * EARTH_RADIUS_M
        y2 = math.radians(lat2) * EARTH_RADIUS_M
        aire += (x1 * y2) - (x2 * y1)
    return abs(aire) / 2.0 * M2_TO_HA


# ---------------------------------------------------------------------------
# Google Earth Engine
# ---------------------------------------------------------------------------
_gee_pret = False


def _init_gee() -> None:
    """
    Initialise Earth Engine via le compte de service (une seule fois).
    Lève RuntimeError si la lib n'est pas installée ou les credentials manquants.
    """
    global _gee_pret
    if _gee_pret:
        return

    try:
        import ee  # noqa: F401
    except ImportError as e:
        raise RuntimeError(
            "La bibliothèque earthengine-api n'est pas installée "
            "(pip install -r requirements.txt)."
        ) from e

    projet = os.environ.get("GEE_PROJECT")
    compte = os.environ.get("GEE_SERVICE_ACCOUNT")
    cle = os.environ.get("GEE_PRIVATE_KEY_FILE")
    if not (projet and compte and cle):
        raise RuntimeError(
            "Credentials GEE manquants : définissez GEE_PROJECT, GEE_SERVICE_ACCOUNT "
            "et GEE_PRIVATE_KEY_FILE."
        )
    if not os.path.isfile(cle):
        raise RuntimeError(f"Fichier de clé GEE introuvable : {cle}")

    credentials = ee.ServiceAccountCredentials(compte, cle)
    ee.Initialize(credentials, project=projet)
    _gee_pret = True


def _periode(date_debut: Optional[str], date_fin: Optional[str]) -> tuple[str, str]:
    """Renvoie (debut, fin) au format yyyy-MM-dd, avec valeurs par défaut."""
    fin = date_fin or date.today().isoformat()
    debut = date_debut or (date.today() - timedelta(days=PERIODE_DEFAUT_JOURS)).isoformat()
    return debut, fin


def _extraire_sentinel2(ring: list[list[float]],
                        date_debut: Optional[str],
                        date_fin: Optional[str]) -> dict[str, Any]:
    """
    Calcule le NDVI (moyen/min/max) et la couverture nuageuse réels sur le polygone
    à partir d'un composite médian Sentinel-2 masqué des nuages (bande SCL).
    """
    import ee

    debut, fin = _periode(date_debut, date_fin)

    # GeoJSON est en [lon, lat] ; ee.Geometry.Polygon attend une liste d'anneaux.
    # geodesic=False : on traite les coordonnées comme planaires (lat/lon importées).
    anneau_ferme = ring + [ring[0]]
    geom = ee.Geometry.Polygon([anneau_ferme], proj="EPSG:4326", geodesic=False)

    collection = (
        ee.ImageCollection(S2_COLLECTION)
        .filterBounds(geom)
        .filterDate(debut, fin)
        .filter(ee.Filter.lte("CLOUDY_PIXEL_PERCENTAGE", SEUIL_NUAGE_SCENE_PCT))
    )

    nb_images = collection.size().getInfo()
    if nb_images == 0:
        return {
            "statut": "no_data",
            "source": f"Sentinel-2 ({S2_COLLECTION}) via Google Earth Engine",
            "periode": {"debut": debut, "fin": fin},
            "nombre_images": 0,
            "ndvi": {"moyen": None, "min": None, "max": None},
            "couverture_nuageuse_pct": None,
            "message": "Aucune image Sentinel-2 exploitable sur cette zone et cette période. "
                       "Élargissez la plage de dates.",
        }

    # Couverture nuageuse spécifique à la parcelle (fraction de pixels nuageux dans l'AOI,
    # moyennée sur la période), calculée sur la bande SCL avant masquage.
    def _fraction_nuage(img):
        scl = img.select("SCL")
        nuage = scl.remap(SCL_NUAGES, [1] * len(SCL_NUAGES), 0).rename("nuage")
        frac = nuage.reduceRegion(
            reducer=ee.Reducer.mean(), geometry=geom, scale=20,
            maxPixels=int(1e9), bestEffort=True,
        ).get("nuage")
        return img.set("frac_nuage_aoi", frac)

    collection_frac = collection.map(_fraction_nuage)
    couverture_pct = collection_frac.aggregate_mean("frac_nuage_aoi").getInfo()

    # NDVI : masquage nuages par pixel (SCL) puis composite médian sur la période.
    def _ndvi_masque(img):
        scl = img.select("SCL")
        masque = scl.remap(SCL_NUAGES, [1] * len(SCL_NUAGES), 0).eq(0)
        ndvi = img.normalizedDifference(["B8", "B4"]).rename("NDVI")
        return ndvi.updateMask(masque)

    composite = collection.map(_ndvi_masque).median()

    stats = composite.reduceRegion(
        reducer=ee.Reducer.mean().combine(ee.Reducer.minMax(), sharedInputs=True),
        geometry=geom, scale=10, maxPixels=int(1e9), bestEffort=True,
    ).getInfo()

    moyen = stats.get("NDVI_mean")
    mini = stats.get("NDVI_min")
    maxi = stats.get("NDVI_max")
    if moyen is None:
        return {
            "statut": "no_data",
            "source": f"Sentinel-2 ({S2_COLLECTION}) via Google Earth Engine",
            "periode": {"debut": debut, "fin": fin},
            "nombre_images": nb_images,
            "ndvi": {"moyen": None, "min": None, "max": None},
            "couverture_nuageuse_pct": round(couverture_pct * 100, 1) if couverture_pct is not None else None,
            "message": "Zone entièrement masquée par les nuages sur la période. Élargissez la plage de dates.",
        }

    def _arr(v):
        return round(v, 4) if v is not None else None

    return {
        "statut": "ok",
        "source": f"Sentinel-2 ({S2_COLLECTION}) via Google Earth Engine",
        "periode": {"debut": debut, "fin": fin},
        "nombre_images": nb_images,
        "ndvi": {"moyen": _arr(moyen), "min": _arr(mini), "max": _arr(maxi)},
        "couverture_nuageuse_pct": round(couverture_pct * 100, 1) if couverture_pct is not None else None,
        "message": f"NDVI calculé sur un composite médian de {nb_images} image(s) Sentinel-2.",
    }


def _extraire_sentinel1(ring: list[list[float]],
                        date_debut: Optional[str],
                        date_fin: Optional[str]) -> dict[str, Any]:
    """
    Statistiques radar Sentinel-1 (SAR, bande C) sur le polygone : rétrodiffusion VV/VH
    en dB, rapport VH/VV et indice RVI. Le radar traverse les nuages : complète le NDVI
    optique quand Sentinel-2 est masqué. Moyenne calculée en linéaire puis reconvertie en dB.
    """
    import ee

    debut, fin = _periode(date_debut, date_fin)
    anneau_ferme = ring + [ring[0]]
    geom = ee.Geometry.Polygon([anneau_ferme], proj="EPSG:4326", geodesic=False)
    source = f"Sentinel-1 ({S1_COLLECTION}) via Google Earth Engine"

    collection = (
        ee.ImageCollection(S1_COLLECTION)
        .filterBounds(geom)
        .filterDate(debut, fin)
        .filter(ee.Filter.eq("instrumentMode", "IW"))
        .filter(ee.Filter.listContains("transmitterReceiverPolarisation", "VV"))
        .filter(ee.Filter.listContains("transmitterReceiverPolarisation", "VH"))
        .select(["VV", "VH"])
    )

    nb_images = collection.size().getInfo()
    vide = {
        "statut": "no_data",
        "source": source,
        "periode": {"debut": debut, "fin": fin},
        "nombre_images": nb_images,
        "vv_db": None, "vh_db": None, "ratio_vh_vv_db": None, "rvi": None,
    }
    if nb_images == 0:
        return {**vide, "message": "Aucune image Sentinel-1 sur cette zone et cette période. "
                                   "Élargissez la plage de dates."}

    # dB -> linéaire (10^(dB/10)), moyenne temporelle, puis stats spatiales sur le polygone.
    lineaire = collection.map(
        lambda img: ee.Image(10).pow(img.divide(10)).copyProperties(img, ["system:time_start"])
    ).mean()
    stats = lineaire.reduceRegion(
        reducer=ee.Reducer.mean(), geometry=geom, scale=10,
        maxPixels=int(1e9), bestEffort=True,
    ).getInfo()

    vv, vh = stats.get("VV"), stats.get("VH")
    if not vv or not vh or vv <= 0 or vh <= 0:
        return {**vide, "message": "Pixels Sentinel-1 non exploitables sur ce polygone."}

    vv_db = 10 * math.log10(vv)
    vh_db = 10 * math.log10(vh)
    return {
        "statut": "ok",
        "source": source,
        "periode": {"debut": debut, "fin": fin},
        "nombre_images": nb_images,
        "vv_db": round(vv_db, 2),
        "vh_db": round(vh_db, 2),
        "ratio_vh_vv_db": round(vh_db - vv_db, 2),
        # Radar Vegetation Index (Kim & van Zyl) : ~0 sol nu, ->1 végétation dense.
        "rvi": round(4 * vh / (vv + vh), 3),
        "message": f"Rétrodiffusion moyenne de {nb_images} image(s) Sentinel-1 (IW, VV+VH).",
    }


# ---------------------------------------------------------------------------
# Images (vignettes PNG)
# ---------------------------------------------------------------------------
NDVI_PALETTE = ["8B0000", "D2691E", "F0E68C", "9ACD32", "228B22", "006400"]
IMAGE_TAILLE_PX = 640
# Sous ce nombre de pixels Sentinel (10 m = 100 m²) dans la parcelle, on prévient l'utilisateur.
SEUIL_PIXELS_FAIBLE = 200


def _info_resolution(ring: list[list[float]]) -> dict[str, Any]:
    """Nombre de pixels Sentinel (10 m) couvrant la parcelle + avertissement si trop peu."""
    pixels = round(_superficie_ha(ring) * 100)  # 1 ha = 100 pixels de 10 m × 10 m
    info: dict[str, Any] = {"pixels_10m": pixels}
    if pixels < SEUIL_PIXELS_FAIBLE:
        info["avertissement_resolution"] = (
            f"Parcelle d'environ {pixels} pixels Sentinel (1 pixel = 10 m × 10 m) : les images sont "
            "floues par nature et les indices (NDVI, radar) restent indicatifs, surtout près des bords."
        )
    return info


def _png_data_uri(image, region) -> str:
    """Génère une vignette PNG via Earth Engine et la renvoie en data URI (URL GEE éphémère)."""
    import base64
    import urllib.request

    url = image.getThumbURL({"region": region, "dimensions": IMAGE_TAILLE_PX, "format": "png"})
    with urllib.request.urlopen(url, timeout=90) as r:
        return "data:image/png;base64," + base64.b64encode(r.read()).decode("ascii")


def _generer_images(ring: list[list[float]],
                    date_debut: Optional[str],
                    date_fin: Optional[str]) -> dict[str, Any]:
    """
    3 vignettes centrées sur la parcelle (contour jaune) :
      - sentinel2_rgb : couleurs réelles (B4/B3/B2), composite médian sans nuages
      - ndvi          : carte NDVI colorée
      - sentinel1     : radar en fausses couleurs (R=VV, G=VH, B=VV-VH)
    Chaque image échoue indépendamment (None + message).
    """
    import ee

    debut, fin = _periode(date_debut, date_fin)
    geom = ee.Geometry.Polygon([ring + [ring[0]]], proj="EPSG:4326", geodesic=False)
    # Cadrage CARRÉ centré sur la parcelle : côté = 1,8 × sa plus grande dimension, au moins 250 m,
    # pour garder du contexte même sur une petite parcelle (l'image n'est plus recadrée par la page).
    minx, miny, maxx, maxy = _bbox(ring)
    lat0, lon0 = (miny + maxy) / 2, (minx + maxx) / 2
    cos_lat = math.cos(math.radians(lat0))
    dim_m = max((maxx - minx) * 111_320 * cos_lat, (maxy - miny) * 111_320)
    cote_m = max(dim_m * 1.8, 250.0)
    d_lat = cote_m / 2 / 111_320
    d_lon = d_lat / max(cos_lat, 0.01)
    region = ee.Geometry.Rectangle([lon0 - d_lon, lat0 - d_lat, lon0 + d_lon, lat0 + d_lat])

    fc = ee.FeatureCollection([ee.Feature(geom)])
    halo = ee.Image().byte().paint(fc, 1, 5).selfMask().visualize(palette=["000000"], opacity=0.55)
    trait = ee.Image().byte().paint(fc, 1, 2).selfMask().visualize(palette=["FFFF00"])
    contour = halo.blend(trait)

    resultat: dict[str, Any] = {
        "periode": {"debut": debut, "fin": fin},
        "sentinel2_rgb": None, "ndvi": None, "sentinel1": None, "erreurs": {},
        "cote_m": round(cote_m), **_info_resolution(ring),
    }

    # --- Sentinel-2 (RGB + NDVI) ---
    try:
        s2 = (
            ee.ImageCollection(S2_COLLECTION)
            .filterBounds(geom).filterDate(debut, fin)
            .filter(ee.Filter.lte("CLOUDY_PIXEL_PERCENTAGE", SEUIL_NUAGE_SCENE_PCT))
        )
        if s2.size().getInfo() == 0:
            raise RuntimeError("Aucune image Sentinel-2 exploitable sur la période.")

        def _masque(img):
            img = img.resample("bilinear")
            nuage = img.select("SCL").remap(SCL_NUAGES, [1] * len(SCL_NUAGES), 0).eq(0)
            ndvi = img.normalizedDifference(["B8", "B4"]).rename("NDVI")
            return img.select(["B4", "B3", "B2"]).addBands(ndvi).updateMask(nuage)

        composite = s2.map(_masque).median()
        rgb = composite.visualize(bands=["B4", "B3", "B2"], min=0, max=3000, gamma=1.3)
        carte_ndvi = composite.select("NDVI").visualize(min=-0.1, max=0.9, palette=NDVI_PALETTE)
        resultat["sentinel2_rgb"] = _png_data_uri(rgb.blend(contour), region)
        resultat["ndvi"] = _png_data_uri(carte_ndvi.blend(contour), region)
    except Exception as e:
        resultat["erreurs"]["sentinel2"] = str(e)

    # --- Sentinel-1 (radar fausses couleurs) ---
    try:
        s1 = (
            ee.ImageCollection(S1_COLLECTION)
            .filterBounds(geom).filterDate(debut, fin)
            .filter(ee.Filter.eq("instrumentMode", "IW"))
            .filter(ee.Filter.listContains("transmitterReceiverPolarisation", "VV"))
            .filter(ee.Filter.listContains("transmitterReceiverPolarisation", "VH"))
            .select(["VV", "VH"])
        )
        if s1.size().getInfo() == 0:
            raise RuntimeError("Aucune image Sentinel-1 sur la période.")
        med = s1.map(lambda i: i.resample("bilinear")).median()
        fausse_couleur = ee.Image.cat([
            med.select("VV"), med.select("VH"), med.select("VV").subtract(med.select("VH")),
        ]).visualize(min=[-20, -25, 0], max=[0, -5, 15])
        resultat["sentinel1"] = _png_data_uri(fausse_couleur.blend(contour), region)
    except Exception as e:
        resultat["erreurs"]["sentinel1"] = str(e)

    return resultat


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------
@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "up", "service": "sentinel-extraction", "mode": "earth-engine"}


@app.post("/extract")
def extract(req: ExtractionRequest) -> dict[str, Any]:
    try:
        ring = _sommets_distincts(_extraire_anneau(req.geojson))
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

    try:
        _init_gee()
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))

    try:
        indices = _extraire_sentinel2(ring, req.date_debut, req.date_fin)
    except Exception as e:  # erreurs Earth Engine (quota, géométrie, réseau…)
        raise HTTPException(status_code=502, detail=f"Erreur Earth Engine : {e}")

    # Sentinel-1 est complémentaire : son échec ne doit pas faire perdre le NDVI Sentinel-2.
    try:
        radar = _extraire_sentinel1(ring, req.date_debut, req.date_fin)
    except Exception as e:
        radar = {"statut": "erreur", "message": f"Erreur Earth Engine (Sentinel-1) : {e}"}

    return {
        "nombre_sommets": len(ring),
        "superficie_ha": round(_superficie_ha(ring), 4),
        "bbox": _bbox(ring),
        "centroide": _centroide(ring),
        **indices,
        "sentinel1": radar,
        **_info_resolution(ring),
    }


@app.post("/images")
def images(req: ExtractionRequest) -> dict[str, Any]:
    """Vignettes PNG (data URI) : Sentinel-2 couleurs réelles, carte NDVI, radar Sentinel-1."""
    try:
        ring = _sommets_distincts(_extraire_anneau(req.geojson))
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

    try:
        _init_gee()
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))

    try:
        return _generer_images(ring, req.date_debut, req.date_fin)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"Erreur Earth Engine : {e}")


@app.post("/virtual-sensors")
def virtual_sensors(req: ExtractionRequest) -> dict[str, Any]:
    """Capteurs virtuels (phase 0) : humidité du sol radar, ET0 FAO-56, bilan hydrique."""
    try:
        ring = _sommets_distincts(_extraire_anneau(req.geojson))
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

    try:
        _init_gee()
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))

    try:
        import virtual_sensors as vs

        # NDVI récent : sert à estimer la confiance du radar (canopée dense = sol peu visible).
        fin = date.today()
        s2 = _extraire_sentinel2(ring, (fin - timedelta(days=60)).isoformat(), fin.isoformat())
        return vs.calculer(ring, (s2.get("ndvi") or {}).get("moyen"))
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"Erreur capteurs virtuels : {e}")

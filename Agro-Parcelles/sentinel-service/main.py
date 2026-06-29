"""
Service d'extraction Sentinel-2 (Agro-Parcelles).

Reçoit un polygone GeoJSON depuis le backend Spring, le valide
(bbox / centroïde / superficie / nombre de sommets) puis calcule le NDVI réel
et la couverture nuageuse à partir de l'imagerie Sentinel-2 (COPERNICUS/S2_SR_HARMONIZED)
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

    return {
        "nombre_sommets": len(ring),
        "superficie_ha": round(_superficie_ha(ring), 4),
        "bbox": _bbox(ring),
        "centroide": _centroide(ring),
        **indices,
    }

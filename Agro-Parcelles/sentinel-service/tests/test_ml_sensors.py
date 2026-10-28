"""Tests des modèles pré-entraînés (sans Earth Engine). Lancer : python -m pytest tests"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import ml_sensors
from virtual_sensors import _features_modeles

TYPIQUE = {
    "NASA_Temp_Air_C": 26.0, "NASA_Hum_Air_pct": 82.0, "NASA_Rain_mm": 5.0, "NASA_Solar_Rad": 19.0,
    "Sentinel1_VV": -8.0, "Sentinel1_VH": -14.0, "Sentinel1_RVI": 0.72,
    "Sentinel1_SSM_Surface": None, "Sentinel2_NDVI": 0.64, "Sentinel2_NDWI": 0.05,
}


def test_temperature_sol_dans_le_domaine():
    capteur, raison = ml_sensors.predire("temp_sol", TYPIQUE, "2026-10-26", "test")
    assert raison is None
    assert capteur["type"] == "modele" and capteur["confiance"] == "faible"
    assert 15 < capteur["valeur"] < 40


def test_humidite_refusee_sans_entree_radar_ssm():
    capteur, raison = ml_sensors.predire("humidite_sol_ia", TYPIQUE, "2026-10-26", "test")
    assert capteur is None and "Sentinel1_SSM_Surface" in raison


def test_humidite_calculee_quand_ssm_fournie():
    capteur, raison = ml_sensors.predire("humidite_sol_ia", {**TYPIQUE, "Sentinel1_SSM_Surface": 40.0},
                                         "2026-10-26", "test")
    assert raison is None and 15 < capteur["valeur"] < 70


def test_refus_hors_domaine_d_entrainement():
    # NDVI 0,2 (sol quasi nu) : le modèle n'a jamais vu de NDVI < 0,53.
    capteur, raison = ml_sensors.predire("azote_assimilable", {**TYPIQUE, "Sentinel2_NDVI": 0.2},
                                         "2026-10-26", "test")
    assert capteur is None and "Sentinel2_NDVI" in raison


def test_variable_absente_signalee_dans_la_note():
    capteur, _ = ml_sensors.predire("azote_assimilable", {**TYPIQUE, "Sentinel2_NDWI": None},
                                    "2026-10-26", "test")
    assert "Sentinel2_NDWI" in capteur["note"]


def test_features_prennent_le_dernier_jour_nasa_complet():
    brut = {
        "20261024": {"T2M": 25.0, "RH2M": 80.0, "PRECTOTCORR": 1.0, "ALLSKY_SFC_SW_DWN": 18.0},
        "20261025": {"T2M": 26.0, "RH2M": 81.0, "PRECTOTCORR": 2.0, "ALLSKY_SFC_SW_DWN": 19.0},
        "20261026": {"T2M": 27.0},   # jour incomplet : ignoré
    }
    f, date_mesure, _ = _features_modeles(brut, 0.6, 0.1, {"statut": "ok", "vv_db": -8, "vh_db": -14, "rvi": 0.7})
    assert date_mesure == "2026-10-25" and f["NASA_Temp_Air_C"] == 26.0
    assert f["Sentinel1_SSM_Surface"] is None


def test_ssm_plafonne_comme_a_l_entrainement():
    brut = {"20261025": {"T2M": 26.0, "RH2M": 81.0, "PRECTOTCORR": 2.0, "ALLSKY_SFC_SW_DWN": 19.0}}
    f, _, _ = _features_modeles(brut, 0.6, 0.1, None, 90.0)
    assert f["Sentinel1_SSM_Surface"] == 65.0

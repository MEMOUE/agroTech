"""Tests des calculs purs (sans Earth Engine). Lancer : python -m pytest tests"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from virtual_sensors import et0_fao56, indice_humidite_radar, anomalie_vegetation


def test_et0_exemple_fao56_uccle():
    # FAO-56 exemple 18 : Uccle (50,80°N, 100 m), 6 juillet -> ET0 = 3,88 mm/j.
    # RH moyenne = (84+63)/2 ; vent à 2 m 2,078 m/s -> équivalent à 10 m : /0,748.
    et0 = et0_fao56(tmax=21.5, tmin=12.3, rh_moy=73.5, rs_mj=22.07, vent10=2.078 / 0.748,
                    lat_deg=50.80, altitude_m=100, jour_annee=187)
    assert abs(et0 - 3.88) < 0.35


def test_et0_tropicale_plausible():
    et0 = et0_fao56(tmax=31, tmin=23, rh_moy=80, rs_mj=18, vent10=2.3,
                    lat_deg=5.4, altitude_m=20, jour_annee=285)
    assert 2.5 < et0 < 5.5


def test_humidite_radar_normalisation():
    serie = [(f"2026-01-{i + 1:02d}", -14 + i * 0.5) for i in range(20)]
    smi = indice_humidite_radar(serie)
    assert smi is not None
    assert smi[0]["valeur"] == 0.0 and smi[-1]["valeur"] == 100.0
    assert all(0 <= p["valeur"] <= 100 for p in smi)


def test_humidite_radar_historique_insuffisant():
    assert indice_humidite_radar([("2026-01-01", -10.0)] * 3) is None


def test_humidite_radar_signal_plat_refuse():
    # Forêt dense : VV varie de 0,3 dB seulement -> aucun indice (sinon on amplifierait du bruit)
    serie = [(f"2026-01-{i + 1:02d}", -8.2 + (i % 3) * 0.1) for i in range(20)]
    assert indice_humidite_radar(serie) is None


def test_anomalie_vegetation_chute_detectee():
    # 11 mois de NDVI stable autour de 0,6, puis chute franche sur le dernier point.
    serie = [(f"2026-{m:02d}-01", 0.6) for m in range(1, 12)] + [("2026-12-01", 0.1)]
    anomalie = anomalie_vegetation(serie)
    assert anomalie is not None
    assert anomalie["niveau"] == "basse"
    assert anomalie["position_percentile"] < 15


def test_anomalie_vegetation_stable_rien_a_signaler():
    serie = [(f"2026-{m:02d}-01", 0.6 + (m % 2) * 0.02) for m in range(1, 13)]
    assert anomalie_vegetation(serie) is None


def test_anomalie_vegetation_historique_insuffisant():
    assert anomalie_vegetation([("2026-01-01", 0.5)] * 3) is None


def test_anomalie_vegetation_signal_plat_refuse():
    # Parcelle forestière/stable toute l'année : dynamique trop faible pour être exploitable.
    serie = [(f"2026-{m:02d}-01", 0.70 + (m % 2) * 0.01) for m in range(1, 13)]
    assert anomalie_vegetation(serie) is None

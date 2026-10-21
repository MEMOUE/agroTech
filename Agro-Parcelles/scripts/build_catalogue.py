"""Construit le catalogue de cultures embarqué dans le backend (data/cultures.json).

Sources (téléchargées par ailleurs, non versionnées) :
  - ecocrop_afrique_ymax.xlsx          besoins agronomiques EcoCrop + rendement maximal observé
  - faostat_afrique_cultures_large.xlsx rendements FAOSTAT par pays et par année
  - ecocrop_clean.xlsx (feuille « complet ») zones climatiques EcoCrop, jointes par code d'espèce

Usage :
  python -I scripts/build_catalogue.py <ecocrop_afrique_ymax.xlsx> <faostat_afrique_cultures_large.xlsx> <ecocrop_clean.xlsx>

Le rendement de référence est la distribution des rendements pays x année FAOSTAT
(2014 et après) pour la culture FAO correspondante, sur toute l'Afrique.
"""
import json
import sys
from pathlib import Path

import pandas as pd

ANNEE_MIN_FAO = 2014
SORTIE = Path(__file__).resolve().parent.parent / "src/main/resources/data/cultures.json"

TYPES_RETENUS = {
    "céréale", "légume", "légumineuse", "racine/tubercule", "oléagineux",
    "culture de rente", "fruit", "épice", "fibre", "arbre fruitier",
}
# Espèces EcoCrop rattachées à tort au produit FAO de leur nom français (non cultivées pour ce produit).
ESPECES_EXCLUES = {"Musanga cecropioides", "Musa textilis", "Cucurbita foetidissima"}

BESOINS = ["temp", "pluie", "ph"]


def nombre(v):
    return None if pd.isna(v) else float(v)


def liste_texte(v):
    return [] if pd.isna(v) else [t.strip() for t in str(v).split(",") if t.strip()]


def stats_fao(faostat: pd.DataFrame) -> dict:
    recent = faostat[(faostat.annee >= ANNEE_MIN_FAO) & faostat.rendement_t_ha.notna() & (faostat.rendement_t_ha > 0)]
    out = {}
    for item, g in recent.groupby("culture"):
        r = g.rendement_t_ha
        out[item] = {
            "nObservations": int(len(r)),
            "nPays": int(g.pays.nunique()),
            "p25": round(float(r.quantile(0.25)), 3),
            "mediane": round(float(r.median()), 3),
            "p75": round(float(r.quantile(0.75)), 3),
        }
    return out


def familles_climatiques(zone) -> list:
    """« tropical wet (Ar), steppe or semiarid (Bs), ... » -> ['A', 'B', ...] (lettre de famille Köppen/Trewartha)."""
    if pd.isna(zone):
        return []
    import re
    return sorted({m[0] for m in re.findall(r"\(([A-Z][a-z]?)\)", str(zone))})


def main(chemin_ecocrop: str, chemin_fao: str, chemin_clean: str) -> None:
    eco = pd.read_excel(chemin_ecocrop)
    zones = pd.read_excel(chemin_clean, sheet_name="complet")[["code", "zone_climatique"]].drop_duplicates("code")
    eco = eco.merge(zones, on="code", how="left")
    fao = pd.read_excel(chemin_fao)
    stats = stats_fao(fao)

    eco = eco[eco.ymax_t_ha.notna() & eco.type_culture.isin(TYPES_RETENUS)]
    eco = eco[~eco.nom_scientifique.isin(ESPECES_EXCLUES)]
    # Besoins complets (température, pluie, pH) sinon la culture ne peut pas être évaluée honnêtement.
    eco = eco.dropna(subset=[f"{b}_{s}" for b in BESOINS for s in ("min", "opt_min", "opt_max", "max")])

    cultures = []
    for _, r in eco.iterrows():
        s = stats.get(r.fao_item) if isinstance(r.fao_item, str) else None
        cultures.append({
            "nom": r.nom_fr,
            "nomScientifique": r.nom_scientifique,
            "type": r.type_culture,
            "cycleJoursMin": nombre(r.cycle_jours_min),
            "cycleJoursMax": nombre(r.cycle_jours_max),
            "tempMin": nombre(r.temp_min), "tempOptMin": nombre(r.temp_opt_min),
            "tempOptMax": nombre(r.temp_opt_max), "tempMax": nombre(r.temp_max),
            "pluieMin": nombre(r.pluie_min), "pluieOptMin": nombre(r.pluie_opt_min),
            "pluieOptMax": nombre(r.pluie_opt_max), "pluieMax": nombre(r.pluie_max),
            "phMin": nombre(r.ph_min), "phOptMin": nombre(r.ph_opt_min),
            "phOptMax": nombre(r.ph_opt_max), "phMax": nombre(r.ph_max),
            "textures": liste_texte(r.texture_opt),
            "zonesClimatiques": familles_climatiques(r.zone_climatique),
            "rendement": {
                "produitFao": r.fao_item if isinstance(r.fao_item, str) else None,
                "correspondance": r.correspondance if isinstance(r.correspondance, str) else None,
                "ymax": nombre(r.ymax_t_ha),
                **(s or {}),
            } if s else {"ymax": nombre(r.ymax_t_ha)},
        })

    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    SORTIE.write_text(json.dumps(cultures, ensure_ascii=False, indent=1), encoding="utf-8")
    avec_rendement = sum(1 for c in cultures if "mediane" in c["rendement"])
    print(f"{len(cultures)} entrées ({len({c['nom'] for c in cultures})} cultures), "
          f"{avec_rendement} avec distribution de rendement -> {SORTIE}")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2], sys.argv[3])

"""Construit le catalogue de cultures embarqué dans le backend (data/cultures.json).

Sources (réelles, non versionnées en entier — trop volumineuses) :
  - EcoCrop_DB.csv
    Base agronomique EcoCrop (FAO) : besoins climatiques (température, pluie, pH),
    texture de sol tolérée, zones climatiques Köppen/Trewartha, durée de cycle (jours).
    Valeurs lues telles quelles, espèce par espèce (colonnes TOPMN/TOPMX/TMIN/TMAX,
    ROPMN/ROPMX/RMIN/RMAX, PHOPMN/PHOPMX/PHMIN/PHMAX, TEXT, CLIZ, GMIN/GMAX).
  - Export FAOSTAT "Cultures et produits animaux" (normalisé, bulk, français), fichier
    "..._Toutes_les_Données_(Normalisé).csv" : rendement réel par pays, culture et année
    (Code Élément = 5412 "Rendement", unité kg/ha).

Le périmètre géographique est l'Afrique subsaharienne (scripts/data/zones_afrique_subsaharienne.csv,
liste de pays explicite — exclut l'Afrique du Nord) et la période est 2019 -> année la plus
récente disponible dans l'export. Aucune valeur n'est inventée : chaque chiffre du catalogue
(min/optimal/max agro-climatique, quartiles et médiane de rendement, plafond observé) est lu ou
calculé directement à partir de ces deux sources ; une culture sans donnée suffisante est omise
du champ concerné plutôt que complétée par une estimation.

La correspondance culture <-> produit FAOSTAT (scripts/data/mapping_cultures_fao.csv) est reprise
du catalogue déjà publié (association nom français / nom scientifique / produit FAO déjà revue),
régénérée par scripts/generer_mapping_fao.py.

Usage :
  python -I scripts/build_catalogue.py <EcoCrop_DB.csv> <...Toutes_les_Données_(Normalisé).csv>
"""
import csv
import json
import sys
from pathlib import Path

ANNEE_MIN_FAO = 2019
RACINE = Path(__file__).resolve().parent.parent
SORTIE = RACINE / "src/main/resources/data/cultures.json"
MAPPING = Path(__file__).resolve().parent / "data/mapping_cultures_fao.csv"
ZONES_SSA = Path(__file__).resolve().parent / "data/zones_afrique_subsaharienne.csv"

CODE_ELEMENT_RENDEMENT = 5412

# Espèces EcoCrop rattachées à tort au produit FAO de leur nom français (non cultivées pour ce produit).
ESPECES_EXCLUES = {"Musanga cecropioides", "Musa textilis", "Cucurbita foetidissima"}

BESOINS = ["temp", "pluie", "ph"]


def nombre(v):
    if v is None:
        return None
    v = str(v).strip()
    if not v or v.upper() == "NA":
        return None
    try:
        return float(v)
    except ValueError:
        return None


def liste_texte(v):
    if not v:
        return []
    return [t.strip() for t in str(v).split(",") if t.strip()]


def familles_climatiques(zone) -> list:
    """« tropical wet (Ar), steppe or semiarid (Bs), ... » -> ['A', 'B', ...] (lettre de famille Köppen/Trewartha)."""
    if not zone:
        return []
    import re
    return sorted({m[0] for m in re.findall(r"\(([A-Z][a-z]?)\)", str(zone))})


def charger_ecocrop(chemin: str) -> dict:
    """{nom_scientifique: {champs utiles}}, lus tels quels depuis EcoCrop_DB.csv."""
    out = {}
    with open(chemin, encoding="cp1252", newline="") as f:
        for r in csv.DictReader(f):
            out[r["ScientificName"].strip()] = {
                "tempOptMin": nombre(r["TOPMN"]), "tempOptMax": nombre(r["TOPMX"]),
                "tempMin": nombre(r["TMIN"]), "tempMax": nombre(r["TMAX"]),
                "pluieOptMin": nombre(r["ROPMN"]), "pluieOptMax": nombre(r["ROPMX"]),
                "pluieMin": nombre(r["RMIN"]), "pluieMax": nombre(r["RMAX"]),
                "phOptMin": nombre(r["PHOPMN"]), "phOptMax": nombre(r["PHOPMX"]),
                "phMin": nombre(r["PHMIN"]), "phMax": nombre(r["PHMAX"]),
                "textures": liste_texte(r["TEXT"]),
                "zonesClimatiques": familles_climatiques(r["CLIZ"]),
                "cycleJoursMin": nombre(r["GMIN"]), "cycleJoursMax": nombre(r["GMAX"]),
            }
    return out


def charger_mapping() -> list:
    with MAPPING.open(encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def charger_zones_ssa() -> set:
    with ZONES_SSA.open(encoding="utf-8", newline="") as f:
        return {int(r["code_zone"]) for r in csv.DictReader(f)}


def stats_fao(chemin_fao: str, codes_produits: set) -> dict:
    """{code_produit: {nObservations, nPays, p25, mediane, p75, ymax}} en t/ha, Afrique
    subsaharienne, années >= ANNEE_MIN_FAO, lu en flux (le fichier source fait plusieurs
    centaines de Mo)."""
    import pandas as pd

    zones_ssa = charger_zones_ssa()
    cols = ["Code zone", "Code Produit", "Code Élément", "Année", "Valeur"]
    dtypes = {"Code zone": "int32", "Code Produit": "int32", "Code Élément": "int32",
              "Année": "int32", "Valeur": "float64"}

    morceaux = []
    for chunk in pd.read_csv(chemin_fao, usecols=cols, dtype=dtypes, chunksize=1_000_000):
        sub = chunk[
            chunk["Code zone"].isin(zones_ssa)
            & chunk["Code Produit"].isin(codes_produits)
            & (chunk["Code Élément"] == CODE_ELEMENT_RENDEMENT)
            & (chunk["Année"] >= ANNEE_MIN_FAO)
            & (chunk["Valeur"] > 0)
        ]
        if len(sub):
            morceaux.append(sub)

    if not morceaux:
        return {}
    donnees = pd.concat(morceaux, ignore_index=True)
    donnees["rendement_t_ha"] = donnees["Valeur"] / 1000.0  # kg/ha -> t/ha

    out = {}
    for code, g in donnees.groupby("Code Produit"):
        r = g["rendement_t_ha"]
        out[int(code)] = {
            "nObservations": int(len(r)),
            "nPays": int(g["Code zone"].nunique()),
            "p25": round(float(r.quantile(0.25)), 3),
            "mediane": round(float(r.median()), 3),
            "p75": round(float(r.quantile(0.75)), 3),
            "ymax": round(float(r.max()), 3),
        }
    return out


def main(chemin_ecocrop: str, chemin_fao: str) -> None:
    eco = charger_ecocrop(chemin_ecocrop)
    mapping = charger_mapping()
    codes_utilises = {int(m["fao_code_produit"]) for m in mapping if m["fao_code_produit"]}
    stats = stats_fao(chemin_fao, codes_utilises)

    cultures = []
    especes_sans_ecocrop = []
    for m in mapping:
        nom_sci = m["nom_scientifique"]
        if nom_sci in ESPECES_EXCLUES:
            continue
        e = eco.get(nom_sci)
        if e is None:
            especes_sans_ecocrop.append(nom_sci)
            continue
        # Besoins climatiques complets requis, sinon la culture ne peut pas être évaluée honnêtement.
        if any(e[f"{b}{s}"] is None for b in BESOINS for s in ("Min", "OptMin", "OptMax", "Max")):
            continue

        code = int(m["fao_code_produit"]) if m["fao_code_produit"] else None
        s = stats.get(code) if code else None
        rendement = {"produitFao": m["fao_item"] or None, "correspondance": m["correspondance"] or None}
        if s:
            rendement.update(s)
            rendement["ymax"] = s["ymax"]
        else:
            rendement["ymax"] = None

        cultures.append({
            "nom": m["nom_fr"],
            "nomScientifique": nom_sci,
            "type": m["type_culture"],
            "cycleJoursMin": e["cycleJoursMin"], "cycleJoursMax": e["cycleJoursMax"],
            "tempMin": e["tempMin"], "tempOptMin": e["tempOptMin"],
            "tempOptMax": e["tempOptMax"], "tempMax": e["tempMax"],
            "pluieMin": e["pluieMin"], "pluieOptMin": e["pluieOptMin"],
            "pluieOptMax": e["pluieOptMax"], "pluieMax": e["pluieMax"],
            "phMin": e["phMin"], "phOptMin": e["phOptMin"],
            "phOptMax": e["phOptMax"], "phMax": e["phMax"],
            "textures": e["textures"],
            "zonesClimatiques": e["zonesClimatiques"],
            "rendement": rendement,
        })

    if especes_sans_ecocrop:
        print(f"Attention : {len(especes_sans_ecocrop)} espèce(s) absente(s) d'EcoCrop_DB.csv, ignorée(s) : "
              f"{especes_sans_ecocrop}")

    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    SORTIE.write_text(json.dumps(cultures, ensure_ascii=False, indent=1), encoding="utf-8")
    avec_rendement = sum(1 for c in cultures if c["rendement"].get("mediane") is not None)
    print(f"{len(cultures)} entrées ({len({c['nom'] for c in cultures})} cultures), "
          f"{avec_rendement} avec distribution de rendement (Afrique subsaharienne, {ANNEE_MIN_FAO}+) -> {SORTIE}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])

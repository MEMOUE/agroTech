"""Génère scripts/data/mapping_cultures_fao.csv (table de passage, auditable) à partir de deux
sources déjà vérifiées :

  1. resources/data/cultures.json existant : association culture (nom français + nom
     scientifique + type) <-> produit FAO (nom anglais), déjà curée et publiée en production.
  2. Production_Cultures_ProduitsAnimaux_F_CodeProduits.csv (export FAOSTAT officiel, français) :
     chaque "Code Produit" FAOSTAT est un identifiant numérique invariant par langue. Le
     dictionnaire ci-dessous associe chaque nom de produit FAO (anglais, déjà dans le
     catalogue) à son "Code Produit" FAOSTAT, retrouvé par correspondance de sens avec le
     libellé français officiel + vérification du code CPC (Classification Centrale de
     Produits, norme ONU indépendante de la langue) dans ce même fichier.

Aucune valeur numérique n'est inventée ici : ce script ne produit qu'une table de
correspondance (identifiants), pas de données agronomiques ou de rendement.
"""
import json
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
CATALOGUE_ACTUEL = RACINE / "src/main/resources/data/cultures.json"
SORTIE = Path(__file__).resolve().parent / "data/mapping_cultures_fao.csv"

# produit FAO (anglais, tel que stocké dans cultures.json) -> Code Produit FAOSTAT.
# Résolu par lecture de Production_Cultures_ProduitsAnimaux_F_CodeProduits.csv (voir docstring).
CODE_PRODUIT_FAO = {
    "Olives": 260,
    "Cashew nuts, in shell": 217,
    "Cocoa beans": 661,
    "Coffee, green": 656,
    "Sugar cane": 156,
    "Seed cotton, unginned": 328,
    "Natural rubber in primary forms": 836,
    "Kola nuts": 224,
    "Oil palm fruit": 254,
    "Unmanufactured tobacco": 826,
    "Tea leaves": 667,
    "Oats": 75,
    "Wheat": 15,
    "Fonio": 94,
    "Maize (corn)": 56,
    "Millet": 79,
    "Barley": 44,
    "Rice": 27,
    "Rye": 71,
    "Sorghum": 83,
    "Kenaf, and other textile bast fibres, raw or retted": 782,
    "Sisal, raw": 789,
    "Apricots": 526,
    "Pineapples": 574,
    "Avocados": 572,
    "Bananas": 486,
    "Locust beans (carobs)": 461,
    "Cherries": 531,
    "Lemons and limes": 497,
    "Quinces": 523,
    "Figs": 569,
    "Strawberries": 544,
    "Tangerines, mandarins, clementines": 495,
    "Mangoes, guavas and mangosteens": 571,
    "Walnuts, in shell": 222,
    "Oranges": 490,
    "Dates": 577,
    "Pomelos and grapefruits": 507,
    "Papayas": 600,
    "Pistachios, in shell": 223,
    "Pears": 521,
    "Apples": 515,
    "Plums and sloes": 536,
    "Peaches and nectarines": 534,
    "Grapes": 560,
    "Green garlic": 406,
    "Artichokes": 366,
    "Eggplants (aubergines)": 399,
    "Sugar beet": 157,
    "Carrots and turnips": 426,
    "Cabbages": 358,
    "Cucumbers and gherkins": 397,
    "Pumpkins, squash and gourds": 394,
    "Okra": 430,
    "Lettuce and chicory": 372,
    "Cantaloupes and other melons": 568,
    "Onions and shallots, dry (excluding dehydrated)": 403,
    "Watermelons": 567,
    "Tomatoes": 388,
    "Spinach": 373,
    "Groundnuts, excluding shelled": 242,
    "Broad beans and horse beans, dry": 181,
    "Beans, dry": 176,
    "Lentils, dry": 201,
    "Lupins": 210,
    "Cow peas, dry": 195,
    "Peas, dry": 187,
    "Chick peas, dry": 191,
    "Pigeon peas, dry": 197,
    "Soya beans": 236,
    "Bambara beans, dry": 203,
    "Safflower seed": 280,
    "Coconuts, in shell": 249,
    "Rape or colza seed": 270,
    "Karite nuts (sheanuts)": 263,
    "Castor oil seeds": 265,
    "Sesame seed": 289,
    "Sunflower seed": 267,
    "Yams": 137,
    "Cassava, fresh": 125,
    "Sweet potatoes": 122,
    "Potatoes": 116,
    "Taro": 136,
    "Cinnamon and cinnamon-tree flowers, raw": 693,
    "Ginger, raw": 720,
    "Chillies and peppers, green (Capsicum spp. and Pimenta spp.)": 401,
    "Pepper (Piper spp.), raw": 687,
    "Vanilla, raw": 692,
}


def main() -> None:
    cultures = json.loads(CATALOGUE_ACTUEL.read_text(encoding="utf-8"))

    manquants = set()
    lignes = []
    for c in cultures:
        rendement = c.get("rendement") or {}
        produit_fao = rendement.get("produitFao")
        code = CODE_PRODUIT_FAO.get(produit_fao) if produit_fao else None
        if produit_fao and code is None:
            manquants.add(produit_fao)
        lignes.append({
            "nom_fr": c["nom"],
            "nom_scientifique": c["nomScientifique"],
            "type_culture": c["type"],
            "fao_item": produit_fao or "",
            "fao_code_produit": code or "",
            "correspondance": rendement.get("correspondance") or "",
        })

    if manquants:
        raise SystemExit(f"Code Produit FAOSTAT manquant pour : {sorted(manquants)}")

    import csv
    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    with SORTIE.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["nom_fr", "nom_scientifique", "type_culture", "fao_item", "fao_code_produit", "correspondance"])
        w.writeheader()
        w.writerows(lignes)
    print(f"{len(lignes)} lignes -> {SORTIE}")


if __name__ == "__main__":
    main()

"""
Capteurs virtuels — phase 1 : modèles LightGBM pré-entraînés (dossier models/).

Les modèles ont été entraînés hors de ce dépôt ; leur jeu de données n'est pas documenté. Leurs
entrées sont limitées au domaine vu à l'entraînement (plages lues dans le fichier du modèle) :
hors de ce domaine, on ne renvoie AUCUNE valeur plutôt qu'une extrapolation. Aucun intervalle
d'incertitude n'est fourni par ces modèles, donc la confiance reste "faible".
"""
import re
from pathlib import Path
from typing import Any, Optional

MODELES_DIR = Path(__file__).parent / "models"
# Marge tolérée autour des min/max d'entraînement, en fraction de l'étendue.
TOLERANCE_DOMAINE = 0.10

CAPTEURS: dict[str, dict[str, Any]] = {
    "temp_sol": {
        "fichier": "temp_sol.txt", "nom": "Température du sol (modèle IA)", "unite": "°C",
        "requis": set(),
    },
    "humidite_sol_ia": {
        "fichier": "humidite.txt", "nom": "Humidité du sol (modèle IA)", "unite": "%",
        # Cette cible suit presque uniquement l'entrée radar SSM : sans elle, aucune valeur.
        "requis": {"Sentinel1_SSM_Surface"},
    },
    "azote_assimilable": {
        "fichier": "azote_assimilable.txt", "nom": "Azote assimilable (modèle IA)", "unite": "mg/kg (à confirmer)",
        "requis": set(),
    },
}

_cache: dict[str, Any] = {}


def _lire_domaine(chemin: Path) -> tuple[list[str], dict[str, Optional[tuple[float, float]]]]:
    """Noms des variables et plage [min, max] vue à l'entraînement (None = variable jamais utilisée)."""
    noms: list[str] = []
    infos: list[str] = []
    with open(chemin, encoding="utf-8") as f:
        for ligne in f:
            if ligne.startswith("feature_names="):
                noms = ligne.strip().split("=", 1)[1].split(" ")
            elif ligne.startswith("feature_infos="):
                infos = ligne.strip().split("=", 1)[1].split(" ")
            elif ligne.startswith("tree_sizes="):
                break
    domaine: dict[str, Optional[tuple[float, float]]] = {}
    for nom, info in zip(noms, infos):
        m = re.fullmatch(r"\[([^:]+):([^\]]+)\]", info)
        domaine[nom] = (float(m.group(1)), float(m.group(2))) if m else None
    return noms, domaine


def _charger(code: str):
    if code not in _cache:
        import lightgbm as lgb

        chemin = MODELES_DIR / CAPTEURS[code]["fichier"]
        noms, domaine = _lire_domaine(chemin)
        _cache[code] = (lgb.Booster(model_file=str(chemin)), noms, domaine)
    return _cache[code]


def predire(code: str, features: dict[str, Optional[float]], date_mesure: str,
            sources: str) -> tuple[Optional[dict[str, Any]], Optional[str]]:
    """Renvoie (capteur, None) ou (None, raison du refus)."""
    import numpy as np

    cfg = CAPTEURS[code]
    booster, noms, domaine = _charger(code)

    manquantes = sorted(n for n in cfg["requis"] if features.get(n) is None)
    if manquantes:
        return None, f"Entrée indisponible : {', '.join(manquantes)}."

    hors: list[str] = []
    ligne: list[float] = []
    for nom in noms:
        plage = domaine[nom]
        v = features.get(nom)
        if plage is None or v is None:     # variable inutilisée par le modèle, ou absente (gérée par LightGBM)
            ligne.append(float("nan"))
            continue
        marge = (plage[1] - plage[0]) * TOLERANCE_DOMAINE
        if not (plage[0] - marge <= v <= plage[1] + marge):
            hors.append(f"{nom}={v:.3g} (appris sur {plage[0]:.3g} à {plage[1]:.3g})")
        ligne.append(float(v))
    if hors:
        return None, "Hors du domaine d'entraînement du modèle : " + " ; ".join(hors) + "."

    valeur = float(booster.predict(np.array([ligne]))[0])
    absentes = sorted(n for n, p in domaine.items() if p is not None and features.get(n) is None)
    note = ("Estimation d'un modèle pré-entraîné dont le jeu de données n'est pas documenté ; "
            "aucun intervalle d'incertitude n'est disponible. Entrées : " + sources + ".")
    if absentes:
        note += " Entrée(s) manquante(s), traitée(s) comme inconnue(s) : " + ", ".join(absentes) + "."
    return {
        "code": code, "nom": cfg["nom"], "valeur": round(valeur, 1), "unite": cfg["unite"],
        "date_mesure": date_mesure, "serie": None,
        "type": "modele", "confiance": "faible",
        "methode": "LightGBM (250 arbres) sur NASA POWER + Sentinel-1/2",
        "note": note,
    }, None

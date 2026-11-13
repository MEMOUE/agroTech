"""Tests de l'assistant IA (sans appel réseau réel). Lancer : python -m pytest tests"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import pytest

import assistant


def test_client_refuse_sans_cle(monkeypatch):
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    with pytest.raises(RuntimeError, match="ANTHROPIC_API_KEY"):
        assistant.repondre_chat("Bonjour", {})


def test_parser_json_reponse_extrait_objet():
    texte = 'Voici le résultat :\n{"observations": "feuilles jaunies", "hypotheses": []}\nMerci.'
    resultat = assistant._parser_json_reponse(texte)
    assert resultat["observations"] == "feuilles jaunies"


def test_parser_json_reponse_echoue_sans_json():
    with pytest.raises(ValueError):
        assistant._parser_json_reponse("pas de json ici")


class _BlocTexte:
    def __init__(self, texte):
        self.type = "text"
        self.text = texte


class _ReponseFactice:
    def __init__(self, texte):
        self.content = [_BlocTexte(texte)]


class _MessagesFactice:
    def __init__(self, texte):
        self._texte = texte

    def create(self, **kwargs):
        return _ReponseFactice(self._texte)


class _ClientFactice:
    def __init__(self, texte):
        self.messages = _MessagesFactice(texte)


def test_analyser_photo_repli_si_reponse_non_structuree(monkeypatch):
    monkeypatch.setattr(assistant, "_client", lambda: _ClientFactice("réponse illisible sans JSON"))
    resultat = assistant.analyser_photo("ZmFrZQ==", "image/jpeg", {})
    assert resultat["hypotheses"] == []
    assert "diagnostic" in resultat["avertissement"].lower() or "agronome" in resultat["avertissement"].lower()


def test_analyser_photo_parse_json_valide(monkeypatch):
    texte = ('{"observations": "taches brunes sur les feuilles", '
             '"hypotheses": [{"cause": "mildiou", "confiance": "moyenne"}], '
             '"recommandation": "surveiller", "avertissement": "confirmer avec un agronome"}')
    monkeypatch.setattr(assistant, "_client", lambda: _ClientFactice(texte))
    resultat = assistant.analyser_photo("ZmFrZQ==", "image/jpeg", {})
    assert resultat["hypotheses"][0]["cause"] == "mildiou"
    assert resultat["avertissement"] == "confirmer avec un agronome"


def test_repondre_chat_utilise_le_contexte(monkeypatch):
    monkeypatch.setattr(assistant, "_client", lambda: _ClientFactice("La parcelle va bien."))
    reponse = assistant.repondre_chat("Comment va ma parcelle ?", {"nom": "Parcelle test"})
    assert reponse == "La parcelle va bien."

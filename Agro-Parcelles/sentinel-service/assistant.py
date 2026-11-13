"""
Assistant IA (chat + analyse de photo) — Agro-Parcelles.

Utilise l'API Anthropic (Claude) pour :
  - répondre aux questions générales de l'agriculteur sur sa parcelle/ses cultures (chat) ;
  - analyser une photo d'une culture pour repérer des signes visibles de maladie/ravageur.

Authentification : clé API lue depuis l'environnement (ANTHROPIC_API_KEY), jamais en dur.
Si absente, les fonctions lèvent RuntimeError -> l'appelant (main.py) renvoie 503.

Principe directeur (honnêteté sur l'incertitude) : ni le chat ni l'analyse de photo ne doivent
affirmer un diagnostic certain. Le system prompt impose de signaler l'incertitude, de ne jamais
inventer de chiffres, et de recommander un agronome local avant toute décision importante
(traitement phytosanitaire, etc.).
"""
import json
import os
from typing import Any, Optional

MODELE = "claude-sonnet-5-5"
MAX_TOKENS_CHAT = 1024
MAX_TOKENS_PHOTO = 1024

SYSTEM_PROMPT_CHAT = """Tu es l'assistant agronomique d'AgroTech, une application destinée aux petits \
exploitants agricoles d'Afrique subsaharienne. Tu réponds en français, de façon simple et concrète.

Règles impératives :
- Tu n'as accès qu'aux données fournies dans le contexte (parcelle, cultures, indices satellite, \
recommandations). Ne jamais inventer de chiffres ou de données que tu n'as pas reçues.
- Si une information manque ou qu'un indice est incertain, dis-le clairement plutôt que de deviner.
- Pour toute décision importante (traitement phytosanitaire, achat d'intrants, changement de culture), \
recommande de confirmer auprès d'un agronome ou d'un service agricole local.
- Reste bref et actionnable : l'agriculteur lit sur un téléphone, pas un rapport."""

SYSTEM_PROMPT_PHOTO = """Tu es un assistant d'aide au diagnostic visuel pour l'agriculture en Afrique \
subsaharienne. On te montre une photo d'une culture prise par l'agriculteur. Tu dois analyser ce qui est \
visible sur l'image et répondre UNIQUEMENT avec un objet JSON (sans texte autour), au format exact :

{
  "observations": "ce qui est visible sur la photo (symptômes, couleur, aspect des feuilles, etc.)",
  "hypotheses": [{"cause": "nom de la maladie/du ravageur/cause possible", "confiance": "faible|moyenne|bonne"}],
  "recommandation": "action concrète et prudente à court terme",
  "avertissement": "rappel que ceci n'est pas un diagnostic certain et qu'une confirmation par un agronome \
ou un service agricole local est recommandée avant tout traitement"
}

Règles impératives :
- Ne jamais annoncer une cause avec une confiance "bonne" sauf si les symptômes visibles sont vraiment \
caractéristiques et sans ambiguïté possible.
- Si la photo est trop floue, trop sombre, ou ne montre pas assez d'éléments pour conclure, dis-le dans \
"observations" et renvoie une liste "hypotheses" vide plutôt que de deviner.
- "avertissement" est obligatoire et ne doit jamais être omis."""


def _client():
    """Construit le client Anthropic. Lève RuntimeError si la clé API est absente."""
    cle = os.environ.get("ANTHROPIC_API_KEY")
    if not cle:
        raise RuntimeError("Clé API Anthropic manquante : définissez ANTHROPIC_API_KEY.")

    try:
        import anthropic
    except ImportError as e:
        raise RuntimeError(
            "La bibliothèque anthropic n'est pas installée (pip install -r requirements.txt)."
        ) from e

    return anthropic.Anthropic(api_key=cle)


def _contexte_en_texte(contexte: dict[str, Any]) -> str:
    """Sérialise le contexte parcelle/cultures en texte lisible par le modèle."""
    return json.dumps(contexte, ensure_ascii=False, indent=2)


def repondre_chat(message: str, contexte: dict[str, Any],
                   historique: Optional[list[dict[str, str]]] = None) -> str:
    """
    Envoie la question de l'agriculteur à Claude avec le contexte de sa parcelle.
    `historique` : liste optionnelle de messages précédents [{"role": "user"|"assistant", "content": "..."}].
    """
    client = _client()

    messages: list[dict[str, str]] = []
    for m in historique or []:
        if m.get("role") in ("user", "assistant") and m.get("content"):
            messages.append({"role": m["role"], "content": m["content"]})

    contenu = f"Contexte de la parcelle :\n{_contexte_en_texte(contexte)}\n\nQuestion : {message}"
    messages.append({"role": "user", "content": contenu})

    reponse = client.messages.create(
        model=MODELE,
        max_tokens=MAX_TOKENS_CHAT,
        system=SYSTEM_PROMPT_CHAT,
        messages=messages,
    )
    return "".join(bloc.text for bloc in reponse.content if bloc.type == "text").strip()


def _parser_json_reponse(texte: str) -> dict[str, Any]:
    """Extrait le premier objet JSON de la réponse du modèle (au cas où il ajouterait du texte autour)."""
    debut, fin = texte.find("{"), texte.rfind("}")
    if debut == -1 or fin == -1 or fin < debut:
        raise ValueError("Réponse du modèle sans JSON exploitable.")
    return json.loads(texte[debut:fin + 1])


AVERTISSEMENT_SECOURS = (
    "Analyse automatique non concluante : ceci n'est pas un diagnostic. "
    "Faites confirmer par un agronome ou un service agricole local avant tout traitement."
)


def analyser_photo(image_b64: str, mime_type: str, contexte: dict[str, Any]) -> dict[str, Any]:
    """
    Envoie la photo à Claude (vision) avec le contexte de la parcelle, renvoie un dict structuré :
    {observations, hypotheses: [{cause, confiance}], recommandation, avertissement}.
    En cas de réponse du modèle non structurée, renvoie un résultat de repli honnête plutôt qu'une
    exception (l'agriculteur doit toujours recevoir une réponse, même prudente).
    """
    client = _client()

    contenu = [
        {"type": "image", "source": {"type": "base64", "media_type": mime_type, "data": image_b64}},
        {"type": "text", "text": f"Contexte de la parcelle :\n{_contexte_en_texte(contexte)}\n\n"
                                  "Analyse cette photo de la culture."},
    ]

    reponse = client.messages.create(
        model=MODELE,
        max_tokens=MAX_TOKENS_PHOTO,
        system=SYSTEM_PROMPT_PHOTO,
        messages=[{"role": "user", "content": contenu}],
    )
    texte = "".join(bloc.text for bloc in reponse.content if bloc.type == "text").strip()

    try:
        resultat = _parser_json_reponse(texte)
    except (ValueError, json.JSONDecodeError):
        return {
            "observations": texte or "Impossible d'analyser la photo.",
            "hypotheses": [],
            "recommandation": "Reprenez une photo nette, en lumière naturelle, centrée sur la zone affectée.",
            "avertissement": AVERTISSEMENT_SECOURS,
        }

    return {
        "observations": resultat.get("observations", ""),
        "hypotheses": resultat.get("hypotheses", []) or [],
        "recommandation": resultat.get("recommandation", ""),
        "avertissement": resultat.get("avertissement") or AVERTISSEMENT_SECOURS,
    }

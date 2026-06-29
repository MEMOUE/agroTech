# Service d'extraction Sentinel-2

Microservice FastAPI appelé par le backend Spring (`Agro-Parcelles`). Il reçoit un
polygone GeoJSON et renvoie le **NDVI réel** (moyen/min/max) et la **couverture nuageuse**
de la parcelle, calculés sur un composite médian **Sentinel-2** (`COPERNICUS/S2_SR_HARMONIZED`)
masqué des nuages, via **Google Earth Engine** (GEE).

## Chaîne complète

```
Angular (Leaflet, dessin) → Spring POST /api/extraction → FastAPI POST /extract → Earth Engine
```

---

## 1. Créer les credentials Google Earth Engine (à faire une fois)

GEE s'authentifie ici par **compte de service** (pas d'OAuth interactif), adapté à un serveur.

1. **Compte Earth Engine** : allez sur <https://earthengine.google.com/> → *Get Started* /
   *Register* et connectez-vous avec un compte Google. Choisissez un usage
   **non-commercial / recherche** (gratuit). Validez l'inscription.
2. **Projet Google Cloud** : sur <https://console.cloud.google.com/> créez (ou choisissez)
   un projet. Notez son **ID** (ex. `mon-projet-gee`). C'est votre `GEE_PROJECT`.
3. **Activer l'API** : dans le projet, activez *Google Earth Engine API*
   (<https://console.cloud.google.com/apis/library/earthengine.googleapis.com>).
4. **Enregistrer le projet auprès d'Earth Engine** :
   <https://code.earthengine.google.com/> → en haut à droite, *Project* → enregistrez
   le projet Cloud pour un usage non-commercial.
5. **Compte de service** : Console Cloud → *IAM & Admin* → *Service Accounts* →
   *Create service account*. Nom : ex. `agro-sentinel`. Donnez-lui le rôle
   **Earth Engine Resource Viewer** (ou *Service Usage Consumer*). Notez son email
   (`…@mon-projet-gee.iam.gserviceaccount.com`) → c'est `GEE_SERVICE_ACCOUNT`.
6. **Clé JSON** : sur ce compte de service → onglet *Keys* → *Add key* → *Create new key*
   → **JSON**. Un fichier se télécharge.
7. **Autoriser le compte de service sur Earth Engine** :
   <https://code.earthengine.google.com/> → *Settings / Service Accounts* (ou via la page
   d'enregistrement du projet) → ajoutez l'email du compte de service comme utilisateur autorisé.
8. Placez le fichier téléchargé dans `sentinel-service/secrets/gee-key.json`
   (le dossier `secrets/` est ignoré par git).

> Le code ne lit jamais la clé en dur : seulement via les variables d'environnement
> `GEE_PROJECT`, `GEE_SERVICE_ACCOUNT`, `GEE_PRIVATE_KEY_FILE`.

---

## 2. Lancer en local

```bash
cd sentinel-service
python -m venv .venv
# Windows PowerShell : .venv\Scripts\Activate.ps1
# bash               : source .venv/bin/activate
pip install -r requirements.txt

# Renseigner les credentials (voir .env.example)
export GEE_PROJECT=mon-projet-gee
export GEE_SERVICE_ACCOUNT=agro-sentinel@mon-projet-gee.iam.gserviceaccount.com
export GEE_PRIVATE_KEY_FILE=./secrets/gee-key.json
# PowerShell : $env:GEE_PROJECT="mon-projet-gee"  (etc.)

uvicorn main:app --reload --port 8000
```

- Docs interactives : <http://localhost:8000/docs>
- Santé : <http://localhost:8000/health>

## 3. Tester sans le frontend

```bash
curl -X POST http://localhost:8000/extract \
  -H "Content-Type: application/json" \
  -d '{
    "geojson": {
      "type": "Feature",
      "geometry": {
        "type": "Polygon",
        "coordinates": [[[11.50,3.84],[11.51,3.84],[11.51,3.85],[11.50,3.85],[11.50,3.84]]]
      }
    },
    "date_debut": "2026-03-01",
    "date_fin": "2026-06-29"
  }'
```

Réponse type :

```json
{
  "statut": "ok",
  "source": "Sentinel-2 (COPERNICUS/S2_SR_HARMONIZED) via Google Earth Engine",
  "nombre_sommets": 4,
  "superficie_ha": 123.3648,
  "bbox": [11.5, 3.84, 11.51, 3.85],
  "centroide": { "lat": 3.845, "lon": 11.505 },
  "periode": { "debut": "2026-03-01", "fin": "2026-06-29" },
  "nombre_images": 12,
  "ndvi": { "moyen": 0.61, "min": 0.12, "max": 0.88 },
  "couverture_nuageuse_pct": 18.4,
  "message": "NDVI calculé sur un composite médian de 12 image(s) Sentinel-2."
}
```

## Codes d'erreur

- **400** : GeoJSON invalide (pas un Polygon, < 3 sommets).
- **503** : credentials GEE manquants/incomplets (variables d'env ou fichier de clé).
- **502** : erreur côté Earth Engine (quota, géométrie refusée, réseau…).
- `statut: "no_data"` (HTTP 200) : aucune image exploitable sur la période → élargir les dates.

## Docker

Le `compose.yaml` du backend monte `./sentinel-service/secrets` en `/secrets` (lecture seule)
et lit `GEE_PROJECT` / `GEE_SERVICE_ACCOUNT` depuis un `.env` (à la racine d'`Agro-Parcelles`).
`GEE_PRIVATE_KEY_FILE` y vaut `/secrets/gee-key.json`.

## Réglages (dans `main.py`)

- `PERIODE_DEFAUT_JOURS` (90) : fenêtre utilisée si aucune date n'est fournie.
- `SEUIL_NUAGE_SCENE_PCT` (80) : on écarte les scènes globalement trop nuageuses.
- `SCL_NUAGES` : classes SCL masquées (ombre, nuages, cirrus).

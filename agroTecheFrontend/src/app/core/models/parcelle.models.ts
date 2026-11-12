export interface GpsPoint {
  lat: number;
  lon: number;
}

export interface ParcelleDtoRequest {
  nomParcelle: string;
  coordonneesGps: GpsPoint[];
  idUser: number;
}

export interface Parcelle {
  id: number;
  nomParcelle: string;
  coordonneesGps: GpsPoint[];
  superficie: number;
  perimetre: number;
  centroideLat: number;
  centroideLon: number;
  idUser: number;
  createdAt: string;
  updatedAt: string;
}

export interface ParcelleMapResponse {
  parcelleId: number;
  nomParcelle: string;
  geoJson: unknown;
  boundingBox: number[];
  centroideLat: number;
  centroideLon: number;
  zoomRecommande: number;
  tileUrlSatellite: string;
  tileUrlOsm: string;
  googleMapsUrl: string;
}

export interface ExtractionRequest {
  geoJson: unknown;
  dateDebut?: string;
  dateFin?: string;
}

export interface ExtractionResult {
  statut: string;
  source: string;
  nombre_sommets: number;
  superficie_ha: number;
  bbox: number[];
  centroide: { lat: number; lon: number };
  periode: { debut?: string | null; fin?: string | null };
  ndvi: { moyen: number; min: number; max: number };
  couverture_nuageuse_pct: number;
  nombre_images?: number;
  message: string;
  sentinel1?: Sentinel1Result;
}

export interface ExtractionImages {
  periode: { debut?: string | null; fin?: string | null };
  sentinel2_rgb: string | null;
  ndvi: string | null;
  sentinel1: string | null;
  erreurs: Record<string, string>;
  /** Côté de la zone affichée (m), nombre de pixels Sentinel dans la parcelle, alerte si trop peu. */
  cote_m?: number;
  pixels_10m?: number;
  avertissement_resolution?: string;
}

export interface Sentinel1Result {
  statut: 'ok' | 'no_data' | 'erreur';
  source?: string;
  nombre_images?: number;
  vv_db?: number | null;
  vh_db?: number | null;
  ratio_vh_vv_db?: number | null;
  rvi?: number | null;
  message: string;
}

export interface SatelliteDataResponse {
  parcelleId: number;
  nomParcelle: string;
  latitude: number;
  longitude: number;
  dateDebut: string;
  dateFin: string;
  temperatureMoyenne: Record<string, number>;
  precipitations: Record<string, number>;
  humiditeRelative: Record<string, number>;
  rayonnementSolaire: Record<string, number>;
  vitesseVent: Record<string, number>;
  source: string;
  unite: string;
}

export interface SolIndicateur {
  code: string;
  nom: string;
  valeur: number;
  unite: string;
  niveau: 'bas' | 'moyen' | 'bon' | 'eleve';
  interpretation: string;
  detail?: string | null;
}

export interface SolTexture {
  argilePct: number;
  limonPct: number;
  sablePct: number;
  classe: string;
}

export interface SolDataResponse {
  parcelleId: number;
  nomParcelle: string;
  profondeur: string;
  indicateurs: SolIndicateur[];
  texture: SolTexture;
  source: string;
  resolution: string;
  avertissements: string[];
}

export interface CapteurVirtuel {
  code: string;
  nom: string;
  valeur: number;
  unite: string;
  date_mesure: string;
  serie: { date: string; valeur: number }[] | null;
  /** indice / calcul aujourd'hui ; "modele" quand le modèle IA entraîné sur capteurs sera en place */
  type: 'indice' | 'calcul' | 'donnee' | 'modele';
  confiance: 'faible' | 'moyenne' | 'bonne';
  methode: string;
  note: string;
}

export interface CapteursVirtuelsResponse {
  statut: 'ok' | 'no_data';
  capteurs: CapteurVirtuel[];
  erreurs: Record<string, string>;
  avertissement: string;
}

export interface RecommandationFacteur {
  code: string;
  nom: string;
  valeurParcelle: number | null;
  unite: string;
  plageOptimale: string;
  plageTolerance: string;
  score: number;
  statut: 'optimal' | 'tolere' | 'defavorable';
}

/** Rendement de référence en t/ha (quartiles 25/75 % et médiane FAOSTAT) ; null si non disponible. */
export interface RecommandationRendement {
  bas: number | null;
  probable: number | null;
  haut: number | null;
  potentielMax: number | null;
  productionBasT: number | null;
  productionProbableT: number | null;
  productionHauteT: number | null;
  fiabilite: 'bonne' | 'moyenne' | 'faible' | 'indisponible';
  source: string;
}

export interface Recommandation {
  culture: string;
  nomScientifique: string;
  type: string;
  score: number;
  niveau: 'tres_adaptee' | 'adaptee' | 'moyenne';
  facteurLimitant: string | null;
  facteurs: RecommandationFacteur[];
  cycleJoursMin: number | null;
  cycleJoursMax: number | null;
  rendement: RecommandationRendement | null;
}

export interface RecommandationsResponse {
  parcelleId: number;
  nomParcelle: string;
  superficieHa: number;
  conditions: {
    temperatureMoyenneC: number;
    pluieAnnuelleMm: number;
    ph: number | null;
    textureClasse: string | null;
    textureCategorie: string | null;
    zoneClimatique: string | null;
  };
  recommandations: Recommandation[];
  avertissements: string[];
}

export interface ObjectifCultureRequest {
  culture: string;
  dateSemisPrevue: string;
  /** Optionnelle : calculée côté backend à partir du cycle de la culture si absente. */
  dateRecolteVisee?: string | null;
  rendementViseTHa?: number | null;
  /** Part de la parcelle occupée par cette culture, en % (0-100) ; optionnel. */
  partSurfacePct?: number | null;
}

export interface TacheUpdateRequest {
  fait: boolean;
  dateReelle?: string | null;
}

/** Ajuste une culture déjà en cours (rendement visé, date de récolte et/ou part de surface) sans la remplacer. */
export interface ObjectifUpdateRequest {
  dateRecolteVisee?: string | null;
  rendementViseTHa?: number | null;
  partSurfacePct?: number | null;
}

export interface TachePlanning {
  id: number;
  type: string;
  libelle: string;
  ordre: number;
  datePrevue: string;
  dateReelle: string | null;
  statut: 'A_FAIRE' | 'FAIT';
}

export interface PlanningResponse {
  objectifId: number;
  parcelleId: number;
  culture: string;
  cycleJoursMin: number | null;
  cycleJoursMax: number | null;
  dateSemisPrevue: string;
  dateSemisReelle: string | null;
  dateRecolteVisee: string;
  rendementViseTHa: number | null;
  /** Part de la parcelle occupée par cette culture, en % (null si non précisée). */
  partSurfacePct: number | null;
  /** Superficie correspondante (ha), calculée à partir de partSurfacePct. */
  superficieAllouee: number | null;
  statut: 'EN_COURS' | 'TERMINE' | 'ABANDONNE';
  taches: TachePlanning[];
}

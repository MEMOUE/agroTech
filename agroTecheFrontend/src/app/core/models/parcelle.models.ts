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

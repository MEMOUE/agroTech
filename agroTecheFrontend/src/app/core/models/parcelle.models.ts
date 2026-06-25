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

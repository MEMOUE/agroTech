import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ExtractionImages, ExtractionResult, GpsPoint, Parcelle, ParcelleDtoRequest,
  CapteursVirtuelsResponse, ParcelleMapResponse, SatelliteDataResponse, SolDataResponse,
} from '../models/parcelle.models';

const API = 'http://localhost:8082/api/parcelles';
const EXTRACTION_API = 'http://localhost:8082/api/extraction';

/** Construit un GeoJSON Feature (Polygon fermé) à partir de sommets lat/lon. */
export function pointsToGeoJson(points: GpsPoint[], nom = 'polygone-test'): unknown {
  const ring = points.map(p => [p.lon, p.lat]);
  if (points.length > 0) {
    ring.push([points[0].lon, points[0].lat]); // fermeture
  }
  return {
    type: 'Feature',
    properties: { nom },
    geometry: { type: 'Polygon', coordinates: [ring] },
  };
}

@Injectable({ providedIn: 'root' })
export class ParcelleService {
  private http = inject(HttpClient);

  getById(id: number): Observable<Parcelle> {
    return this.http.get<Parcelle>(`${API}/${id}`);
  }

  getByUser(idUser: number): Observable<Parcelle[]> {
    return this.http.get<Parcelle[]>(`${API}/utilisateur/${idUser}`);
  }

  create(dto: ParcelleDtoRequest): Observable<Parcelle> {
    return this.http.post<Parcelle>(API, dto);
  }

  update(id: number, dto: ParcelleDtoRequest): Observable<Parcelle> {
    return this.http.put<Parcelle>(`${API}/${id}`, dto);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/${id}`);
  }

  getMapData(id: number): Observable<ParcelleMapResponse> {
    return this.http.get<ParcelleMapResponse>(`${API}/${id}/map`);
  }

  /** Capteurs virtuels (indices satellites : humidité du sol radar, ET0, bilan hydrique). */
  getCapteursVirtuels(id: number): Observable<CapteursVirtuelsResponse> {
    return this.http.get<CapteursVirtuelsResponse>(`${API}/${id}/capteurs-virtuels`);
  }

  /** Composition du sol (pH, azote, matière organique, CEC, texture) — SoilGrids. */
  getSolData(id: number): Observable<SolDataResponse> {
    return this.http.get<SolDataResponse>(`${API}/${id}/sol`);
  }

  getSatelliteData(id: number, debut?: string, fin?: string): Observable<SatelliteDataResponse> {
    let params = new HttpParams();
    if (debut) params = params.set('debut', debut);
    if (fin) params = params.set('fin', fin);
    return this.http.get<SatelliteDataResponse>(`${API}/${id}/satellite`, { params });
  }

  /** Vignettes Sentinel-2 (couleurs réelles, NDVI) et Sentinel-1 (radar) du polygone. */
  extractImages(geoJson: unknown, dateDebut?: string, dateFin?: string): Observable<ExtractionImages> {
    return this.http.post<ExtractionImages>(`${EXTRACTION_API}/images`, { geoJson, dateDebut, dateFin });
  }

  /** Envoie un polygone GeoJSON au service d'extraction Sentinel-2. */
  extract(geoJson: unknown, dateDebut?: string, dateFin?: string): Observable<ExtractionResult> {
    return this.http.post<ExtractionResult>(EXTRACTION_API, { geoJson, dateDebut, dateFin });
  }
}

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ExtractionImages, ExtractionResult, GpsPoint, ObjectifCultureRequest, ObjectifUpdateRequest, Parcelle, ParcelleDtoRequest,
  CapteursVirtuelsResponse, ParcelleMapResponse, PlanningResponse, RecommandationsResponse, SatelliteDataResponse,
  SolDataResponse, TacheUpdateRequest,
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

  /** Cultures adaptées au climat et au sol de la parcelle, avec rendement de référence (calcul par règles). */
  getRecommandations(id: number, limite = 10): Observable<RecommandationsResponse> {
    return this.http.get<RecommandationsResponse>(`${API}/${id}/recommandations`, { params: { limite } });
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

  /** Cultures actives de la parcelle (une ou plusieurs à la fois) et leur planning généré. */
  getPlannings(id: number): Observable<PlanningResponse[]> {
    return this.http.get<PlanningResponse[]>(`${API}/${id}/objectifs`);
  }

  /** Ajoute une culture sur la parcelle (ne remplace pas les cultures déjà en cours). */
  definirObjectif(id: number, dto: ObjectifCultureRequest): Observable<PlanningResponse> {
    return this.http.post<PlanningResponse>(`${API}/${id}/objectifs`, dto);
  }

  /** Ajuste le rendement visé et/ou la date de récolte d'une culture déjà en cours. */
  ajusterObjectif(parcelleId: number, objectifId: number, dto: ObjectifUpdateRequest): Observable<PlanningResponse> {
    return this.http.patch<PlanningResponse>(`${API}/${parcelleId}/objectifs/${objectifId}`, dto);
  }

  /** Abandonne une culture (reste en historique mais ne réapparaît plus dans la liste active). */
  abandonnerObjectif(parcelleId: number, objectifId: number): Observable<void> {
    return this.http.post<void>(`${API}/${parcelleId}/objectifs/${objectifId}/abandonner`, {});
  }

  /** Coche/décoche une tâche du planning ; peut décaler les tâches suivantes. */
  marquerTache(parcelleId: number, objectifId: number, tacheId: number, dto: TacheUpdateRequest): Observable<PlanningResponse> {
    return this.http.patch<PlanningResponse>(`${API}/${parcelleId}/objectifs/${objectifId}/taches/${tacheId}`, dto);
  }
}

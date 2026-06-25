import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Parcelle, ParcelleDtoRequest, ParcelleMapResponse, SatelliteDataResponse } from '../models/parcelle.models';

const API = 'http://localhost:8082/api/parcelles';

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

  getSatelliteData(id: number, debut?: string, fin?: string): Observable<SatelliteDataResponse> {
    let params = new HttpParams();
    if (debut) params = params.set('debut', debut);
    if (fin) params = params.set('fin', fin);
    return this.http.get<SatelliteDataResponse>(`${API}/${id}/satellite`, { params });
  }
}

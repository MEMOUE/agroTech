import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ChatMessage, ChatResponse, PestPhotoAnalysis } from '../models/parcelle.models';

const API = 'http://localhost:8082/api';

@Injectable({ providedIn: 'root' })
export class AssistantService {
  private http = inject(HttpClient);

  /** Chat général (aucune parcelle précise) : utilisé par le widget flottant. */
  chatGeneral(message: string, historique: ChatMessage[]): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(`${API}/assistant/chat`, { message, historique });
  }

  /** Chat avec le contexte d'une parcelle précise. */
  chatParcelle(parcelleId: number, message: string, historique: ChatMessage[]): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(`${API}/parcelles/${parcelleId}/assistant/chat`, { message, historique });
  }

  /** Analyse une photo de la culture (signes de maladie/ravageur). Ce n'est pas un diagnostic certain. */
  analyserPhoto(parcelleId: number, photo: File): Observable<PestPhotoAnalysis> {
    const form = new FormData();
    form.append('photo', photo);
    return this.http.post<PestPhotoAnalysis>(`${API}/parcelles/${parcelleId}/assistant/pest-photo`, form);
  }
}

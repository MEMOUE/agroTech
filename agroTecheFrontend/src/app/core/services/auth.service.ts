import { Injectable, signal, computed, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { AuthResponse, LoginRequest, RegisterRequest, UserProfile } from '../models/auth.models';

const API_URL = 'http://localhost:8081/api/auth';
const TOKEN_KEY = 'agro_token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  private _token = signal<string | null>(
    this.isBrowser ? localStorage.getItem(TOKEN_KEY) : null
  );
  private _profile = signal<UserProfile | null>(null);

  readonly isLoggedIn = computed(() => !!this._token());
  readonly token = this._token.asReadonly();
  readonly currentUser = this._profile.asReadonly();

  login(req: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${API_URL}/login`, req).pipe(
      tap(res => { this.saveToken(res.token); this._profile.set(null); })
    );
  }

  register(req: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${API_URL}/register`, req).pipe(
      tap(res => { this.saveToken(res.token); this._profile.set(null); })
    );
  }

  getProfile(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${API_URL}/me`).pipe(
      tap(p => this._profile.set(p))
    );
  }

  logout(): void {
    if (this.isBrowser) localStorage.removeItem(TOKEN_KEY);
    this._token.set(null);
    this._profile.set(null);
    this.router.navigate(['/login']);
  }

  private saveToken(token: string): void {
    if (this.isBrowser) localStorage.setItem(TOKEN_KEY, token);
    this._token.set(token);
  }
}

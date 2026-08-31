import { Component, inject, signal, computed, OnInit, OnDestroy, PLATFORM_ID } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { isPlatformBrowser } from '@angular/common';
import { ParcelleService, pointsToGeoJson } from '../../../core/services/parcelle.service';
import { AuthService } from '../../../core/services/auth.service';
import { ExtractionResult, GpsPoint } from '../../../core/models/parcelle.models';
import { ParcelleMapComponent } from '../../../shared/parcelle-map/parcelle-map.component';
import { LogoComponent } from '../../../shared/logo/logo.component';

type CaptureState = 'idle' | 'locating' | 'success' | 'error';
type EntryMode = 'terrain' | 'manual';

@Component({
  selector: 'app-parcelle-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ParcelleMapComponent, LogoComponent],
  templateUrl: './parcelle-form.component.html',
})
export class ParcelleFormComponent implements OnInit, OnDestroy {
  private fb         = inject(FormBuilder);
  private svc        = inject(ParcelleService);
  private auth       = inject(AuthService);
  private route      = inject(ActivatedRoute);
  private router     = inject(Router);
  private isBrowser  = isPlatformBrowser(inject(PLATFORM_ID));

  isEdit       = signal(false);
  parcelleId   = signal<number | null>(null);
  loading      = signal(false);
  loadingData  = signal(false);
  error        = signal('');
  userId       = signal<number | null>(null);

  points       = signal<GpsPoint[]>([]);
  captureState = signal<CaptureState>('idle');
  geoError     = signal('');
  watchId: number | null = null;

  // Méthode d'enregistrement : sur le terrain (GPS/carte) ou coordonnées déjà connues
  entryMode = signal<EntryMode>('terrain');

  manualForm = this.fb.group({
    lat: this.fb.control<number | null>(null, [Validators.required, Validators.min(-90), Validators.max(90)]),
    lon: this.fb.control<number | null>(null, [Validators.required, Validators.min(-180), Validators.max(180)]),
  });

  bulkText  = signal('');
  bulkError = signal('');

  // Test d'extraction Sentinel-2 (envoi du polygone GeoJSON au backend)
  extracting       = signal(false);
  extractionResult = signal<ExtractionResult | null>(null);
  extractionError  = signal('');

  form = this.fb.nonNullable.group({
    nomParcelle: ['', [Validators.required, Validators.maxLength(100)]],
  });

  get pointCount()  { return this.points().length; }
  get canSubmit()   { return this.pointCount >= 3; }
  get progressPct() { return Math.min(this.pointCount / 3 * 100, 100); }

  /**
   * Détecte un polygone "nœud papillon" : des points saisis dans le désordre (fréquent en
   * saisie manuelle) forment des côtés qui se croisent — le périmètre reste correct mais
   * l'aire (Shoelace) s'annule. On compare l'aire du polygone à celle de sa bounding box.
   */
  readonly polygonSeemsCrossed = computed(() => {
    const pts = this.points();
    if (pts.length < 4) return false;
    const lats = pts.map(p => p.lat);
    const lons = pts.map(p => p.lon);
    const bboxArea = (Math.max(...lats) - Math.min(...lats)) * (Math.max(...lons) - Math.min(...lons));
    if (bboxArea <= 0) return false;
    let shoelace = 0;
    for (let i = 0; i < pts.length; i++) {
      const a = pts[i], b = pts[(i + 1) % pts.length];
      shoelace += a.lon * b.lat - b.lon * a.lat;
    }
    return Math.abs(shoelace) / 2 < bboxArea * 0.15;
  });

  /** Réordonne les points par angle autour de leur centre pour obtenir un polygone simple. */
  sortPointsAroundCentroid(): void {
    const pts = this.points();
    if (pts.length < 3) return;
    const centroidLat = pts.reduce((s, p) => s + p.lat, 0) / pts.length;
    const centroidLon = pts.reduce((s, p) => s + p.lon, 0) / pts.length;
    const sorted = [...pts].sort((a, b) =>
      Math.atan2(a.lat - centroidLat, a.lon - centroidLon) -
      Math.atan2(b.lat - centroidLat, b.lon - centroidLon)
    );
    this.points.set(sorted);
    this.extractionResult.set(null);
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit.set(true);
      this.parcelleId.set(+id);
      this.loadingData.set(true);
      this.svc.getById(+id).subscribe({
        next: p => {
          this.form.controls.nomParcelle.setValue(p.nomParcelle);
          this.points.set([...p.coordonneesGps]);
          this.userId.set(p.idUser);
          this.loadingData.set(false);
        },
        error: () => { this.error.set('Parcelle introuvable.'); this.loadingData.set(false); }
      });
    }
    const user = this.auth.currentUser();
    if (user) this.userId.set(user.id);
    else this.auth.getProfile().subscribe({ next: p => this.userId.set(p.id) });
  }

  capturePoint(): void {
    if (!this.isBrowser || !navigator.geolocation) {
      this.geoError.set('GPS non disponible sur cet appareil.');
      return;
    }
    this.captureState.set('locating');
    this.geoError.set('');
    navigator.geolocation.getCurrentPosition(
      pos => {
        this.points.update(pts => [
          ...pts,
          { lat: +pos.coords.latitude.toFixed(6), lon: +pos.coords.longitude.toFixed(6) }
        ]);
        this.captureState.set('success');
        setTimeout(() => this.captureState.set('idle'), 1800);
      },
      err => {
        const msgs: Record<number, string> = {
          1: 'Accès GPS refusé. Vérifiez les permissions.',
          2: 'Position introuvable. Essayez à l\'extérieur.',
          3: 'Signal GPS trop lent. Réessayez.',
        };
        this.geoError.set(msgs[err.code] ?? 'Erreur GPS inconnue.');
        this.captureState.set('error');
        setTimeout(() => this.captureState.set('idle'), 2500);
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
    );
  }

  removePoint(i: number): void {
    this.points.update(pts => pts.filter((_, idx) => idx !== i));
    this.extractionResult.set(null);
  }

  /** Ajoute un point saisi manuellement (lat/lon connus, sans être sur place). */
  addManualPoint(): void {
    if (this.manualForm.invalid) { this.manualForm.markAllAsTouched(); return; }
    const { lat, lon } = this.manualForm.getRawValue();
    this.points.update(pts => [...pts, { lat: lat!, lon: lon! }]);
    this.extractionResult.set(null);
    this.manualForm.reset();
  }

  /** Colle plusieurs coordonnées d'un coup, une par ligne : "lat, lon" (virgule, espace ou tabulation). */
  addBulkPoints(): void {
    this.bulkError.set('');
    const lines = this.bulkText().split('\n').map(l => l.trim()).filter(Boolean);
    if (!lines.length) { this.bulkError.set('Collez au moins une ligne de coordonnées.'); return; }

    const parsed: GpsPoint[] = [];
    for (const [i, line] of lines.entries()) {
      const parts = line.split(/[,;\t]+|\s+/).map(p => p.trim()).filter(Boolean);
      const lat = Number(parts[0]);
      const lon = Number(parts[1]);
      const valid = parts.length === 2 && Number.isFinite(lat) && Number.isFinite(lon)
        && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
      if (!valid) {
        this.bulkError.set(`Ligne ${i + 1} invalide : "${line}". Format attendu : latitude, longitude`);
        return;
      }
      parsed.push({ lat, lon });
    }

    this.points.update(pts => [...pts, ...parsed]);
    this.extractionResult.set(null);
    this.bulkText.set('');
  }

  /** Sommets ajoutés en cliquant directement sur la carte. */
  onMapPoints(pts: GpsPoint[]): void {
    this.points.set([...pts]);
    this.extractionResult.set(null);
  }

  /** Envoie le polygone courant (GeoJSON) au service d'extraction Sentinel-2. */
  testerExtraction(): void {
    if (this.pointCount < 3) {
      this.extractionError.set('Tracez au moins 3 coins avant de tester l\'extraction.');
      return;
    }
    this.extracting.set(true);
    this.extractionError.set('');
    this.extractionResult.set(null);

    const geoJson = pointsToGeoJson(this.points(), this.form.controls.nomParcelle.value || 'polygone-test');
    this.svc.extract(geoJson).subscribe({
      next: res => { this.extractionResult.set(res); this.extracting.set(false); },
      error: err => {
        this.extractionError.set(err.error?.error ?? err.error?.message ?? 'Échec de l\'extraction satellite.');
        this.extracting.set(false);
      },
    });
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    if (this.pointCount < 3) { this.error.set('Ajoutez au moins 3 points GPS.'); return; }
    if (!this.userId())      { this.error.set('Session expirée, reconnectez-vous.'); return; }

    this.loading.set(true);
    this.error.set('');
    const payload = {
      nomParcelle:     this.form.controls.nomParcelle.value,
      coordonneesGps:  this.points(),
      idUser:          this.userId()!,
    };
    const req = this.isEdit()
      ? this.svc.update(this.parcelleId()!, payload)
      : this.svc.create(payload);

    req.subscribe({
      next:  p => this.router.navigate(['/parcelles', p.id]),
      error: err => { this.error.set(err.error?.message ?? 'Erreur lors de l\'enregistrement.'); this.loading.set(false); }
    });
  }

  ngOnDestroy(): void {
    if (this.watchId !== null && this.isBrowser) navigator.geolocation.clearWatch(this.watchId);
  }
}

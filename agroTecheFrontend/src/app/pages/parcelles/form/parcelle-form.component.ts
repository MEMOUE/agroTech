import { Component, inject, signal, OnInit, OnDestroy, PLATFORM_ID } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { isPlatformBrowser } from '@angular/common';
import { ParcelleService } from '../../../core/services/parcelle.service';
import { AuthService } from '../../../core/services/auth.service';
import { GpsPoint } from '../../../core/models/parcelle.models';
import { ParcelleMapComponent } from '../../../shared/parcelle-map/parcelle-map.component';

type CaptureState = 'idle' | 'locating' | 'success' | 'error';

@Component({
  selector: 'app-parcelle-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ParcelleMapComponent],
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

  form = this.fb.nonNullable.group({
    nomParcelle: ['', [Validators.required, Validators.maxLength(100)]],
  });

  get pointCount()  { return this.points().length; }
  get canSubmit()   { return this.pointCount >= 3; }
  get progressPct() { return Math.min(this.pointCount / 3 * 100, 100); }

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

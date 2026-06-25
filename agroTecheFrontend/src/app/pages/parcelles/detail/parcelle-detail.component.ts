import { Component, inject, signal, OnInit, computed } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DecimalPipe, DatePipe } from '@angular/common';
import { ParcelleService } from '../../../core/services/parcelle.service';
import { Parcelle, ParcelleMapResponse, SatelliteDataResponse } from '../../../core/models/parcelle.models';
import { ParcelleMapComponent } from '../../../shared/parcelle-map/parcelle-map.component';

interface Bar { date: string; val: number; pct: number; }

@Component({
  selector: 'app-parcelle-detail',
  standalone: true,
  imports: [RouterLink, DecimalPipe, DatePipe, ParcelleMapComponent],
  templateUrl: './parcelle-detail.component.html',
})
export class ParcelleDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private svc = inject(ParcelleService);

  parcelle   = signal<Parcelle | null>(null);
  mapData    = signal<ParcelleMapResponse | null>(null);
  satellite  = signal<SatelliteDataResponse | null>(null);
  loading    = signal(true);
  loadingSat = signal(false);
  error      = signal('');
  satError   = signal('');

  readonly satIndicators = computed(() => {
    const s = this.satellite();
    if (!s) return [];
    const last = (obj: Record<string, number>) => {
      const keys = Object.keys(obj).sort();
      return keys.length ? obj[keys[keys.length - 1]] : 0;
    };
    return [
      { icon: '🌡️', label: 'Température',   value: last(s.temperatureMoyenne), unit: '°C',      bg: 'bg-orange-50', text: 'text-orange-700' },
      { icon: '🌧️', label: 'Précipitations', value: last(s.precipitations),     unit: 'mm/j',    bg: 'bg-blue-50',   text: 'text-blue-700'   },
      { icon: '💧', label: 'Humidité',       value: last(s.humiditeRelative),   unit: '%',       bg: 'bg-cyan-50',   text: 'text-cyan-700'   },
      { icon: '☀️', label: 'Rayonnement',    value: last(s.rayonnementSolaire), unit: 'kWh/m²',  bg: 'bg-yellow-50', text: 'text-yellow-700' },
      { icon: '💨', label: 'Vent',           value: last(s.vitesseVent),        unit: 'm/s',     bg: 'bg-gray-50',   text: 'text-gray-700'   },
    ];
  });

  readonly tempBars = computed(() => this.buildBars(this.satellite()?.temperatureMoyenne ?? {}));
  readonly rainBars = computed(() => this.buildBars(this.satellite()?.precipitations ?? {}));

  private buildBars(obj: Record<string, number>): Bar[] {
    const entries = Object.entries(obj).sort(([a], [b]) => a.localeCompare(b)).slice(-14);
    const max = Math.max(...entries.map(([, v]) => v), 1);
    return entries.map(([k, v]) => ({
      date: `${k.substring(6, 8)}/${k.substring(4, 6)}`,
      val: v,
      pct: Math.round((v / max) * 100),
    }));
  }

  ngOnInit(): void {
    const id = +this.route.snapshot.paramMap.get('id')!;
    this.svc.getById(id).subscribe({
      next: p => {
        this.parcelle.set(p);
        this.loading.set(false);
        this.loadSatellite(id);
        this.loadMap(id);
      },
      error: () => { this.error.set('Parcelle introuvable.'); this.loading.set(false); }
    });
  }

  private loadSatellite(id: number): void {
    this.loadingSat.set(true);
    this.svc.getSatelliteData(id).subscribe({
      next:  s => { this.satellite.set(s); this.loadingSat.set(false); },
      error: () => { this.satError.set('Données satellite indisponibles.'); this.loadingSat.set(false); }
    });
  }

  private loadMap(id: number): void {
    this.svc.getMapData(id).subscribe({
      next:  m => this.mapData.set(m),
      error: () => {}
    });
  }

  openGoogleMaps(): void {
    const p = this.parcelle();
    const url = this.mapData()?.googleMapsUrl
      ?? `https://www.google.com/maps/@${p?.centroideLat},${p?.centroideLon},15z`;
    window.open(url, '_blank');
  }
}

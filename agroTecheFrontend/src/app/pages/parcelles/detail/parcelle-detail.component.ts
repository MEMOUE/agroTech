import { Component, inject, signal, OnInit, computed } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DecimalPipe, DatePipe, KeyValuePipe } from '@angular/common';
import { ParcelleService, pointsToGeoJson } from '../../../core/services/parcelle.service';
import { CapteurVirtuel, CapteursVirtuelsResponse, ExtractionImages, ExtractionResult, Parcelle, ParcelleMapResponse, RecommandationsResponse, SatelliteDataResponse, SolDataResponse } from '../../../core/models/parcelle.models';
import { ParcelleMapComponent } from '../../../shared/parcelle-map/parcelle-map.component';
import { BarChartComponent, ChartPoint } from '../../../shared/bar-chart/bar-chart.component';
import { LogoComponent } from '../../../shared/logo/logo.component';

@Component({
  selector: 'app-parcelle-detail',
  standalone: true,
  imports: [RouterLink, DecimalPipe, DatePipe, KeyValuePipe, ParcelleMapComponent, LogoComponent, BarChartComponent],
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
  extraction    = signal<ExtractionResult | null>(null);
  loadingExtr   = signal(false);
  extractionErr = signal('');
  /** Interrupteur : passer à true pour réactiver les capteurs virtuels (bloc + appel API). */
  readonly capteursVirtuelsActifs = true;

  capteurs      = signal<CapteursVirtuelsResponse | null>(null);
  loadingCapt   = signal(false);
  capteursErr   = signal('');
  readonly confianceStyle: Record<string, { label: string; cls: string }> = {
    faible:  { label: 'Confiance faible',   cls: 'bg-red-50 text-red-700' },
    moyenne: { label: 'Confiance moyenne',  cls: 'bg-amber-50 text-amber-700' },
    bonne:   { label: 'Bonne confiance',    cls: 'bg-green-50 text-green-700' },
  };
  readonly capteurIcons: Record<string, string> = {
    humidite_sol: '💧', et0: '☀️', pluie: '🌧️', bilan_hydrique_7j: '⚖️', bilan_hydrique_14j: '⚖️',
    temp_sol: '🌡️', humidite_sol_ia: '💧', azote_assimilable: '🌱',
  };
  /** Noms lisibles des capteurs indisponibles (clés de `erreurs`). */
  readonly capteurNoms: Record<string, string> = {
    humidite_sol: 'Humidité du sol', et0: 'ET0 et bilan hydrique',
    temp_sol: 'Température du sol (IA)', humidite_sol_ia: 'Humidité du sol (IA)',
    azote_assimilable: 'Azote assimilable (IA)', modeles_ia: 'Modèles IA',
  };

  /** Style d'une tuile : couleur fixe par capteur, ou selon le signe pour les bilans hydriques. */
  tileStyle(c: CapteurVirtuel): { box: string; text: string; badge: string | null } {
    if (c.code.startsWith('bilan_hydrique')) {
      if (c.valeur > 10)  return { box: 'bg-sky-50 border-sky-100',     text: 'text-sky-700',     badge: 'Excédent' };
      if (c.valeur < -10) return { box: 'bg-amber-50 border-amber-100', text: 'text-amber-700',   badge: 'Déficit' };
      return                     { box: 'bg-green-50 border-green-100', text: 'text-green-700',   badge: 'Équilibre' };
    }
    if (c.code === 'pluie') return { box: 'bg-blue-50 border-blue-100',     text: 'text-blue-700',   badge: null };
    if (c.code === 'et0')   return { box: 'bg-orange-50 border-orange-100', text: 'text-orange-700', badge: null };
    return { box: 'bg-cyan-50 border-cyan-100', text: 'text-cyan-700', badge: null };
  }

  private serieDe(code: string): ChartPoint[] {
    const c = this.capteurs()?.capteurs.find(x => x.code === code);
    return c ? this.chartPoints(c) : [];
  }
  readonly pluieSerie = computed(() => this.serieDe('pluie'));
  readonly et0Serie   = computed(() => this.serieDe('et0'));
  recos         = signal<RecommandationsResponse | null>(null);
  loadingRecos  = signal(false);
  recosError    = signal('');
  recosToutes   = signal(false);
  /** Par défaut les 5 meilleures cultures ; le bouton « Voir tout » déplie le reste. */
  readonly recosVisibles = computed(() => {
    const r = this.recos()?.recommandations ?? [];
    return this.recosToutes() ? r : r.slice(0, 5);
  });
  readonly niveauReco: Record<string, { label: string; badge: string; bar: string }> = {
    tres_adaptee: { label: 'Très adaptée', badge: 'bg-green-50 text-green-700',   bar: 'bg-green-500' },
    adaptee:      { label: 'Adaptée',      badge: 'bg-lime-50 text-lime-700',     bar: 'bg-lime-500' },
    moyenne:      { label: 'Moyenne',      badge: 'bg-amber-50 text-amber-700',   bar: 'bg-amber-500' },
  };
  readonly statutFacteur: Record<string, string> = {
    optimal: 'bg-green-50 text-green-700', tolere: 'bg-amber-50 text-amber-700', defavorable: 'bg-red-50 text-red-700',
  };

  sol           = signal<SolDataResponse | null>(null);
  loadingSol    = signal(false);
  solError      = signal('');

  /** Style d'un niveau (bas = rouge, moyen = ambre, bon = vert, élevé = violet) et segments de jauge. */
  readonly niveauStyle: Record<string, { label: string; badge: string; bar: string; segments: number }> = {
    bas:   { label: 'Faible',  badge: 'bg-red-50 text-red-700',       bar: 'bg-red-500',     segments: 1 },
    moyen: { label: 'Moyen',   badge: 'bg-amber-50 text-amber-700',   bar: 'bg-amber-500',   segments: 2 },
    bon:   { label: 'Bon',     badge: 'bg-green-50 text-green-700',   bar: 'bg-green-500',   segments: 3 },
    eleve: { label: 'Élevé',   badge: 'bg-violet-50 text-violet-700', bar: 'bg-violet-500',  segments: 3 },
  };
  readonly gaugeSegments = [1, 2, 3];

  /** Image affichée en grand (clic sur une vignette). */
  lightbox      = signal<{ src: string; titre: string } | null>(null);
  images        = signal<ExtractionImages | null>(null);
  loadingImages = signal(false);

  /** Interprétation lisible du NDVI moyen (seuils usuels en agronomie). */
  readonly ndviLabel = computed(() => {
    const v = this.extraction()?.ndvi?.moyen;
    if (v == null) return null;
    if (v < 0.2) return { text: 'Sol nu / très faible couvert', cls: 'text-red-600' };
    if (v < 0.4) return { text: 'Végétation clairsemée', cls: 'text-amber-600' };
    if (v < 0.6) return { text: 'Végétation modérée', cls: 'text-lime-600' };
    return { text: 'Végétation dense et saine', cls: 'text-green-600' };
  });

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
      { icon: '☀️', label: 'Rayonnement',    value: last(s.rayonnementSolaire), unit: 'MJ/m²',  bg: 'bg-yellow-50', text: 'text-yellow-700' },
      { icon: '💨', label: 'Vent',           value: last(s.vitesseVent),        unit: 'm/s',     bg: 'bg-gray-50',   text: 'text-gray-700'   },
    ];
  });

  readonly tempBars = computed(() => this.buildBars(this.satellite()?.temperatureMoyenne ?? {}));
  readonly rainBars = computed(() => this.buildBars(this.satellite()?.precipitations ?? {}));

  private buildBars(obj: Record<string, number>): ChartPoint[] {
    return Object.entries(obj).sort(([a], [b]) => a.localeCompare(b)).slice(-14)
      .map(([k, v]) => ({ date: `${k.substring(6, 8)}/${k.substring(4, 6)}`, val: v }));
  }

  ngOnInit(): void {
    const id = +this.route.snapshot.paramMap.get('id')!;
    this.svc.getById(id).subscribe({
      next: p => {
        this.parcelle.set(p);
        this.loading.set(false);
        this.loadSatellite(id);
        this.loadExtraction(p);
        this.loadSol(id);
        this.loadRecos(id);
        if (this.capteursVirtuelsActifs) this.loadCapteurs(id);
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

  /** Sentinel-1 + Sentinel-2 (via Spring → service Python → Earth Engine), 180 derniers jours. */
  private loadExtraction(p: Parcelle): void {
    if (!p.coordonneesGps?.length) return;
    const fin = new Date();
    const debut = new Date(fin.getTime() - 180 * 86_400_000);
    const iso = (d: Date) => d.toISOString().substring(0, 10);
    this.loadingExtr.set(true);
    this.svc.extract(pointsToGeoJson(p.coordonneesGps, p.nomParcelle), iso(debut), iso(fin)).subscribe({
      next:  r => { this.extraction.set(r); this.loadingExtr.set(false); },
      error: () => { this.extractionErr.set('Données Sentinel indisponibles.'); this.loadingExtr.set(false); }
    });

    // Vignettes : requête séparée (plus lente), n'empêche pas l'affichage des chiffres.
    this.loadingImages.set(true);
    this.svc.extractImages(pointsToGeoJson(p.coordonneesGps, p.nomParcelle), iso(debut), iso(fin)).subscribe({
      next:  i => { this.images.set(i); this.loadingImages.set(false); },
      error: () => this.loadingImages.set(false)
    });
  }

  /** Composition du sol (SoilGrids, ~10 s au premier appel puis en cache côté serveur). */
  private loadSol(id: number): void {
    this.loadingSol.set(true);
    this.svc.getSolData(id).subscribe({
      next:  r => { this.sol.set(r); this.loadingSol.set(false); },
      error: e => {
        this.solError.set(e?.error?.error ?? 'Composition du sol indisponible.');
        this.loadingSol.set(false);
      }
    });
  }

  /** Recommandations de cultures (règles agronomiques + rendements de référence), ~10 s au premier appel. */
  private loadRecos(id: number): void {
    this.loadingRecos.set(true);
    this.svc.getRecommandations(id, 50).subscribe({
      next:  r => { this.recos.set(r); this.loadingRecos.set(false); },
      error: e => {
        this.recosError.set(e?.error?.error ?? 'Recommandations indisponibles.');
        this.loadingRecos.set(false);
      }
    });
  }

  /** Position (en %) d'une valeur sur l'échelle 0 → potentiel max, pour la barre d'intervalle. */
  pct(v: number | null, max: number | null): number {
    return v == null || !max ? 0 : Math.min(100, Math.max(0, (v / max) * 100));
  }

  /** Capteurs virtuels (≈ 15 s au premier appel, puis en cache pour la journée côté serveur). */
  private loadCapteurs(id: number): void {
    this.loadingCapt.set(true);
    this.svc.getCapteursVirtuels(id).subscribe({
      next:  r => { this.capteurs.set(r); this.loadingCapt.set(false); },
      error: () => { this.capteursErr.set('Capteurs virtuels indisponibles.'); this.loadingCapt.set(false); }
    });
  }

  chartPoints(c: { serie: { date: string; valeur: number }[] | null }): ChartPoint[] {
    return (c.serie ?? []).map(p => ({ date: p.date.substring(8, 10) + '/' + p.date.substring(5, 7), val: p.valeur }));
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

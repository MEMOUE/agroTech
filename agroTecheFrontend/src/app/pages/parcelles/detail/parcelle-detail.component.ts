import { Component, inject, signal, OnInit, computed } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DecimalPipe, DatePipe, KeyValuePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ParcelleService, pointsToGeoJson } from '../../../core/services/parcelle.service';
import { CapteurVirtuel, CapteursVirtuelsResponse, ExtractionImages, ExtractionResult, ObjectifCultureRequest, Parcelle, ParcelleMapResponse, PlanningResponse, Recommandation, RecommandationsResponse, SatelliteDataResponse, SolDataResponse, TachePlanning } from '../../../core/models/parcelle.models';
import { ParcelleMapComponent } from '../../../shared/parcelle-map/parcelle-map.component';
import { BarChartComponent, ChartPoint } from '../../../shared/bar-chart/bar-chart.component';
import { LogoComponent } from '../../../shared/logo/logo.component';
import { UserMenuComponent } from '../../../shared/user-menu/user-menu.component';

@Component({
  selector: 'app-parcelle-detail',
  standalone: true,
  imports: [RouterLink, DecimalPipe, DatePipe, KeyValuePipe, FormsModule, ParcelleMapComponent, LogoComponent, BarChartComponent, UserMenuComponent],
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

  // ── Cultiver cette parcelle : cultures en cours (une ou plusieurs) & planning ─
  /** Une parcelle peut porter plusieurs cultures à la fois (rotation, association) : une entrée par culture active. */
  plannings       = signal<PlanningResponse[]>([]);
  loadingPlanning = signal(false);
  planningError   = signal('');
  showObjectifForm = signal(false);
  savingObjectif   = signal(false);
  objectifErreur   = signal('');
  /** null = ajout d'une nouvelle culture ; sinon id de la culture en cours que l'on ajuste. */
  editingObjectifId = signal<number | null>(null);
  readonly modeEdition = computed(() => this.editingObjectifId() !== null);

  /** Ouvert automatiquement tant que rien n'est cultivé, replié dès qu'une culture est en cours ;
   * l'utilisateur garde la main pour le rouvrir/refermer entre deux chargements du planning. */
  recosOuvert = signal(true);
  private cultureEnCoursConnue: boolean | null = null;

  /** '' = aucune sélection, '__autre__' = culture hors liste (texte libre). */
  readonly AUTRE_CULTURE = '__autre__';
  objectifCultureChoix = signal('');
  objectifCultureLibre = signal('');
  objectifRendement = signal<number | null>(null);
  objectifDateSemis = signal(this.todayIso());
  objectifDateRecolte = signal<string | null>(null);
  /** true dès que l'utilisateur modifie la date de récolte à la main : on arrête de la recalculer. */
  private dateRecolteManuelle = false;

  readonly tacheIcones: Record<string, string> = {
    semis: '🌱', fertilisation_fond: '🧪', sarclage_1: '🌿', fertilisation_couverture: '🧪',
    irrigation: '💧', sarclage_2: '🌿', traitement_phytosanitaire: '🛡️', recolte: '🌾',
  };

  /** Les 10 cultures les mieux classées (score décroissant) pour le sol et le climat de la parcelle. */
  readonly topCultures = computed<Recommandation[]>(() =>
    (this.recos()?.recommandations ?? []).slice().sort((a, b) => b.score - a.score).slice(0, 10)
  );

  /** Culture sélectionnée telle que recommandée (score, cycle, rendement) — null si "autre culture" ou inconnue. */
  readonly cultureSelectionnee = computed<Recommandation | null>(() => {
    const nom = this.objectifCultureChoix();
    if (!nom || nom === this.AUTRE_CULTURE) return null;
    return this.recos()?.recommandations.find(r => r.culture === nom) ?? null;
  });

  /** Plage de rendement plausible (quartiles FAOSTAT) pour la culture sélectionnée, bornée par le plafond observé. */
  readonly rendementBornes = computed<{ min: number; max: number; probable: number } | null>(() => {
    const rd = this.cultureSelectionnee()?.rendement;
    if (!rd || rd.bas == null || rd.haut == null || rd.probable == null) return null;
    return { min: rd.bas, max: Math.max(rd.haut, rd.potentielMax ?? rd.haut), probable: rd.probable };
  });

  readonly objectifCultureFinal = computed(() =>
    this.objectifCultureChoix() === this.AUTRE_CULTURE ? this.objectifCultureLibre().trim() : this.objectifCultureChoix()
  );

  private todayIso(): string {
    return new Date().toISOString().substring(0, 10);
  }

  private addJoursIso(iso: string, jours: number): string {
    const d = new Date(iso + 'T00:00:00');
    d.setDate(d.getDate() + Math.round(jours));
    return d.toISOString().substring(0, 10);
  }

  /** Récolte visée = semis + cycle de la culture (le plus long connu, marge de sécurité). */
  private recalculerDateRecolte(): void {
    if (this.dateRecolteManuelle) return;
    const c = this.cultureSelectionnee();
    const cycle = c?.cycleJoursMax ?? c?.cycleJoursMin;
    this.objectifDateRecolte.set(cycle ? this.addJoursIso(this.objectifDateSemis(), cycle) : null);
  }

  /** Ouvre la fenêtre pour ajouter une nouvelle culture sur la parcelle, préremplie si une culture est fournie. */
  ouvrirObjectifPour(culture: string): void {
    this.editingObjectifId.set(null);
    this.dateRecolteManuelle = false;
    this.objectifDateSemis.set(this.todayIso());
    this.objectifDateRecolte.set(null);
    this.objectifCultureLibre.set('');
    this.objectifErreur.set('');
    this.objectifCultureChoix.set(culture || '');
    this.appliquerCultureChoisie();
    this.showObjectifForm.set(true);
  }

  /** Réouvre la fenêtre pour ajuster une culture déjà en cours (rendement visé, date de récolte). */
  modifierObjectif(pl: PlanningResponse): void {
    this.editingObjectifId.set(pl.objectifId);
    this.dateRecolteManuelle = true;
    this.objectifCultureChoix.set(pl.culture);
    this.objectifCultureLibre.set(pl.culture);
    this.objectifRendement.set(pl.rendementViseTHa);
    this.objectifDateSemis.set(pl.dateSemisPrevue);
    this.objectifDateRecolte.set(pl.dateRecolteVisee);
    this.objectifErreur.set('');
    this.showObjectifForm.set(true);
  }

  /** Choix dans la liste des cultures recommandées (ou "autre culture") : met à jour rendement et date de récolte. */
  onCultureChoixChange(nom: string): void {
    this.objectifCultureChoix.set(nom);
    this.appliquerCultureChoisie();
  }

  private appliquerCultureChoisie(): void {
    const bornes = this.rendementBornes();
    this.objectifRendement.set(bornes?.probable ?? null);
    this.recalculerDateRecolte();
  }

  onDateSemisChange(v: string): void {
    this.objectifDateSemis.set(v);
    this.recalculerDateRecolte();
  }

  onDateRecolteChange(v: string): void {
    this.dateRecolteManuelle = true;
    this.objectifDateRecolte.set(v || null);
  }

  annulerObjectif(): void {
    this.showObjectifForm.set(false);
    this.objectifErreur.set('');
  }

  private loadPlannings(id: number): void {
    this.loadingPlanning.set(true);
    this.svc.getPlannings(id).subscribe({
      next: p => {
        this.plannings.set(p);
        this.loadingPlanning.set(false);
        const cultivee = p.length > 0;
        if (this.cultureEnCoursConnue !== cultivee) {
          this.recosOuvert.set(!cultivee);
          this.cultureEnCoursConnue = cultivee;
        }
      },
      error: () => {
        this.loadingPlanning.set(false);
        this.planningError.set('Cultures en cours indisponibles pour le moment.');
      }
    });
  }

  soumettreObjectif(): void {
    const p = this.parcelle();
    if (!p) return;
    const objectifId = this.editingObjectifId();

    this.savingObjectif.set(true);
    this.objectifErreur.set('');

    if (objectifId != null) {
      // Ajustement d'une culture déjà en cours : seuls le rendement et la date de récolte changent.
      this.svc.ajusterObjectif(p.id, objectifId, {
        dateRecolteVisee: this.objectifDateRecolte() || null,
        rendementViseTHa: this.objectifRendement(),
      }).subscribe({
        next: () => { this.loadPlannings(p.id); this.savingObjectif.set(false); this.showObjectifForm.set(false); },
        error: e => {
          this.objectifErreur.set(e?.error?.error ?? "Impossible d'ajuster cette culture.");
          this.savingObjectif.set(false);
        }
      });
      return;
    }

    const culture = this.objectifCultureFinal();
    if (!culture || !this.objectifDateSemis()) { this.savingObjectif.set(false); return; }
    const dto: ObjectifCultureRequest = {
      culture,
      dateSemisPrevue: this.objectifDateSemis(),
      dateRecolteVisee: this.objectifDateRecolte() || null,
      rendementViseTHa: this.objectifRendement(),
    };
    this.svc.definirObjectif(p.id, dto).subscribe({
      next: () => { this.loadPlannings(p.id); this.savingObjectif.set(false); this.showObjectifForm.set(false); },
      error: e => {
        this.objectifErreur.set(e?.error?.error ?? "Impossible d'ajouter cette culture.");
        this.savingObjectif.set(false);
      }
    });
  }

  /** Arrête le suivi d'une culture (conservée en historique, retirée de la liste active). */
  abandonnerCulture(pl: PlanningResponse): void {
    if (!confirm(`Abandonner le suivi de « ${pl.culture} » sur cette parcelle ?`)) return;
    this.svc.abandonnerObjectif(pl.parcelleId, pl.objectifId).subscribe({
      next: () => this.loadPlannings(pl.parcelleId),
      error: () => this.planningError.set("Impossible d'abandonner cette culture.")
    });
  }

  basculerTache(pl: PlanningResponse, t: TachePlanning): void {
    this.svc.marquerTache(pl.parcelleId, pl.objectifId, t.id, { fait: t.statut !== 'FAIT' }).subscribe({
      next: () => this.loadPlannings(pl.parcelleId),
      error: () => this.planningError.set('Impossible de mettre à jour cette tâche.')
    });
  }

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
        this.loadPlannings(id);
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

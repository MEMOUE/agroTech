import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';

export interface ChartPoint { date: string; val: number; }

/** Histogramme avec axe gradué, lignes de repère, survol et stats (min / moyenne / max). */
@Component({
  selector: 'app-bar-chart',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './bar-chart.component.html',
})
export class BarChartComponent {
  title    = input.required<string>();
  subtitle = input('');
  unit     = input('');
  data     = input.required<ChartPoint[]>();
  /** Couleurs du dégradé des barres (haut → bas). */
  from     = input('#fdba74');
  to       = input('#f97316');
  /** true : l'axe démarre à 0 (pluie) ; false : axe resserré autour des valeurs (température). */
  baseZero = input(true);
  height   = input(160);

  private readonly range = computed(() => {
    const vals = this.data().map(d => d.val);
    if (!vals.length) return { min: 0, max: 1 };
    const lo = Math.min(...vals), hi = Math.max(...vals);
    if (this.baseZero()) return { min: 0, max: this.niceCeil(hi) };
    const pad = Math.max((hi - lo) * 0.25, 1);
    return { min: Math.floor(lo - pad), max: Math.ceil(hi + pad) };
  });

  readonly ticks = computed(() => {
    const { min, max } = this.range();
    const steps = 4;
    return Array.from({ length: steps + 1 }, (_, i) => ({
      value: min + ((max - min) * (steps - i)) / steps,
      pct: (i / steps) * 100,
    }));
  });

  readonly bars = computed(() => {
    const { min, max } = this.range();
    const span = max - min || 1;
    const last = this.data().length - 1;
    return this.data().map((d, i) => ({
      ...d,
      pct: Math.max(((d.val - min) / span) * 100, d.val > min ? 2 : 0),
      last: i === last,
    }));
  });

  readonly stats = computed(() => {
    const vals = this.data().map(d => d.val);
    if (!vals.length) return null;
    const total = vals.reduce((s, v) => s + v, 0);
    return { min: Math.min(...vals), max: Math.max(...vals), avg: total / vals.length, total };
  });

  private niceCeil(v: number): number {
    if (v <= 0) return 1;
    const mag = Math.pow(10, Math.floor(Math.log10(v)));
    const n = v / mag;
    return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * mag;
  }
}

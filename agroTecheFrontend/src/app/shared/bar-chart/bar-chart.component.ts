import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';

export interface ChartPoint { date: string; val: number; }

/**
 * Histogramme avec axe gradué, lignes de repère, survol et stats.
 * Une série par défaut ; avec `data2`, deux barres côte à côte par date (ex. pluie vs ET0).
 */
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

  /** Seconde série optionnelle, alignée sur `data` (même ordre de dates). */
  data2    = input<ChartPoint[]>([]);
  label    = input('');
  label2   = input('');
  from2    = input('#fdba74');
  to2      = input('#f97316');

  readonly dual = computed(() => this.data2().length > 0);

  private readonly range = computed(() => {
    const vals = [...this.data(), ...this.data2()].map(d => d.val);
    if (!vals.length) return { min: 0, max: 1 };
    const lo = Math.min(...vals), hi = Math.max(...vals);
    if (this.baseZero() || this.dual()) return { min: 0, max: this.niceCeil(hi) };
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

  private pct(v: number): number {
    const { min, max } = this.range();
    const span = max - min || 1;
    return Math.max(((v - min) / span) * 100, v > min ? 2 : 0);
  }

  readonly bars = computed(() => {
    const last = this.data().length - 1;
    const d2 = this.data2();
    return this.data().map((d, i) => ({
      ...d,
      pct: this.pct(d.val),
      val2: d2[i]?.val ?? null,
      pct2: d2[i] ? this.pct(d2[i].val) : 0,
      last: i === last,
    }));
  });

  readonly stats = computed(() => {
    const vals = this.data().map(d => d.val);
    if (!vals.length) return null;
    const total = vals.reduce((s, v) => s + v, 0);
    const total2 = this.data2().reduce((s, d) => s + d.val, 0);
    return { min: Math.min(...vals), max: Math.max(...vals), avg: total / vals.length, total, total2 };
  });

  private niceCeil(v: number): number {
    if (v <= 0) return 1;
    const mag = Math.pow(10, Math.floor(Math.log10(v)));
    const n = v / mag;
    return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * mag;
  }
}

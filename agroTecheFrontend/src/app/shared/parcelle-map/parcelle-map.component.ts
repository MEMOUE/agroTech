import {
  Component, Input, Output, EventEmitter, OnChanges, AfterViewInit, OnDestroy,
  ElementRef, ViewChild, inject, PLATFORM_ID, SimpleChanges,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { GpsPoint } from '../../core/models/parcelle.models';

@Component({
  selector: 'app-parcelle-map',
  standalone: true,
  template: `
    <div #mapEl class="w-full rounded-2xl overflow-hidden border border-gray-200"
         [style.height]="height"></div>`,
})
export class ParcelleMapComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() points: GpsPoint[] = [];
  @Input() height = '320px';
  @Input() satellite = true;
  @Input() editable = false;

  /** Émis quand l'utilisateur clique sur la carte pour ajouter un sommet (mode editable). */
  @Output() pointsChange = new EventEmitter<GpsPoint[]>();

  @ViewChild('mapEl') mapEl!: ElementRef<HTMLDivElement>;

  private isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private map: any;
  private polygon: any;
  private markers: any[] = [];
  private L: any;

  async ngAfterViewInit(): Promise<void> {
    if (!this.isBrowser) return;
    this.L = await import('leaflet');
    this.initMap();
    this.render();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['points'] && this.map) this.render();
  }

  private initMap(): void {
    const L = this.L;

    // Fix default marker icons path (Leaflet + bundler issue)
    delete (L.Icon.Default.prototype as any)._getIconUrl;
    L.Icon.Default.mergeOptions({
      iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
      iconUrl:       'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
      shadowUrl:     'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
    });

    const center: [number, number] = this.points.length
      ? [this.points[0].lat, this.points[0].lon]
      : [3.848, 11.502]; // Yaoundé par défaut

    this.map = L.map(this.mapEl.nativeElement, { zoomControl: true }).setView(center, 15);

    // Tuile satellite Esri
    L.tileLayer(
      'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
      { attribution: '© Esri — World Imagery', maxZoom: 19 }
    ).addTo(this.map);

    // Labels OpenStreetMap par dessus (noms des routes/villages)
    L.tileLayer(
      'https://stamen-tiles.a.ssl.fastly.net/toner-labels/{z}/{x}/{y}.png',
      { attribution: '', maxZoom: 19, opacity: 0.4 }
    ).addTo(this.map);

    // Mode dessin : clic sur la carte = nouveau sommet
    if (this.editable) {
      this.mapEl.nativeElement.style.cursor = 'crosshair';
      this.map.on('click', (e: any) => {
        const pt: GpsPoint = {
          lat: +e.latlng.lat.toFixed(6),
          lon: +e.latlng.lng.toFixed(6),
        };
        this.points = [...this.points, pt];
        this.pointsChange.emit(this.points);
        this.render();
      });
    }
  }

  private render(): void {
    if (!this.map || !this.L) return;
    const L = this.L;

    // Nettoyer
    this.markers.forEach(m => m.remove());
    this.markers = [];
    if (this.polygon) { this.polygon.remove(); this.polygon = null; }

    if (this.points.length === 0) return;

    const latlngs: [number, number][] = this.points.map(p => [p.lat, p.lon]);

    // Marqueurs numérotés
    this.points.forEach((pt, i) => {
      const color = i === 0 ? '#16a34a' : i === this.points.length - 1 ? '#2563eb' : '#f59e0b';
      const icon = L.divIcon({
        className: '',
        html: `<div style="
          width:28px;height:28px;border-radius:50%;
          background:${color};color:#fff;
          font-size:12px;font-weight:700;
          display:flex;align-items:center;justify-content:center;
          border:2px solid white;
          box-shadow:0 2px 6px rgba(0,0,0,0.4);">${i + 1}</div>`,
        iconSize: [28, 28],
        iconAnchor: [14, 14],
      });
      this.markers.push(L.marker([pt.lat, pt.lon], { icon }).addTo(this.map));
    });

    // Polygone (si ≥ 3 points)
    if (this.points.length >= 3) {
      this.polygon = L.polygon(latlngs, {
        color: '#16a34a',
        weight: 2.5,
        opacity: 0.9,
        fillColor: '#4ade80',
        fillOpacity: 0.2,
      }).addTo(this.map);
      // En mode dessin, on ne recadre pas : éviterait de "sauter" à chaque clic.
      if (!this.editable) this.map.fitBounds(this.polygon.getBounds(), { padding: [30, 30] });
    } else if (latlngs.length >= 2) {
      // Ligne si seulement 2 points
      L.polyline(latlngs, { color: '#16a34a', weight: 2.5, dashArray: '6 4' }).addTo(this.map);
      if (!this.editable) this.map.fitBounds(L.latLngBounds(latlngs), { padding: [40, 40] });
    } else if (!this.editable) {
      this.map.setView(latlngs[0], 17);
    }
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }
}

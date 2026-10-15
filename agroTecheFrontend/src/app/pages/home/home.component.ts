import { Component, HostListener, computed, inject, PLATFORM_ID, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LogoComponent } from '../../shared/logo/logo.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, LogoComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomeComponent {
  private isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  menuOpen = signal(false);
  scrolled = signal(false);
  progress = signal(0);
  activeId = signal('hero');

  /** Ombre du header, barre de progression et onglet actif (scroll-spy). */
  @HostListener('window:scroll')
  onScroll(): void {
    if (!this.isBrowser) return;
    const y = window.scrollY;
    const max = document.documentElement.scrollHeight - window.innerHeight;
    this.scrolled.set(y > 8);
    this.progress.set(max > 0 ? Math.min((y / max) * 100, 100) : 0);

    let current = this.navTabs[0].id;
    for (const tab of this.navTabs) {
      const el = document.getElementById(tab.id);
      if (el && el.getBoundingClientRect().top <= 120) current = tab.id;
    }
    this.activeId.set(current);
  }

  readonly navTabs = [
    { label: 'Accueil', id: 'hero' },
    { label: 'Solution', id: 'solution' },
    { label: 'Fonctionnalités', id: 'features' },
    { label: 'Comment ça marche', id: 'how-it-works' },
    { label: 'Langues', id: 'languages' },
  ];

  readonly socialLinks = [
    {
      label: 'Facebook',
      url: '#',
      icon: 'M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z',
    },
    {
      label: 'Twitter / X',
      url: '#',
      icon: 'M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z',
    },
    {
      label: 'WhatsApp',
      url: '#',
      icon: 'M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413z',
    },
  ];

  readonly languages = [
    { code: 'FR',  name: 'Français', region: 'Langue officielle',        grad: 'from-green-500 to-emerald-600', phrase: 'Bienvenue sur AgroTech' },
    { code: 'FUL', name: 'Fulfulde', region: 'Cameroun · Nigeria · Sahel', grad: 'from-amber-500 to-orange-600', phrase: 'Bisimilla e AgroTech' },
    { code: 'EWO', name: 'Ewondo',   region: 'Cameroun',                  grad: 'from-sky-500 to-blue-600',     phrase: 'Akiba na AgroTech' },
    { code: 'HAS', name: 'Hausa',    region: 'Nigeria · Niger',           grad: 'from-purple-500 to-fuchsia-600', phrase: 'Maraba da AgroTech' },
    { code: 'BAM', name: 'Bambara',  region: 'Mali',                      grad: 'from-rose-500 to-red-600',     phrase: 'Aw bisimilla AgroTech' },
    { code: 'WOL', name: 'Wolof',    region: 'Sénégal',                   grad: 'from-teal-500 to-cyan-600',    phrase: 'Dalal ak AgroTech' },
  ];

  /** Liste doublée pour un défilement infini sans coupure. */
  readonly tickerLanguages = [...this.languages, ...this.languages];

  selectedLang = signal('FR');
  readonly currentLang = computed(() => this.languages.find(l => l.code === this.selectedLang()) ?? this.languages[0]);

  readonly comparison = [
    { icon: '🌿', need: 'Santé des cultures',
      before: "Drones à 500 000 FCFA l'unité, pilote et batteries à gérer",
      after: 'NDVI Sentinel-2 : gratuit, à jour, sur chaque parcelle' },
    { icon: '📡', need: 'Humidité et structure',
      before: 'Capteurs de sol impossibles à entretenir sur le terrain',
      after: 'Radar Sentinel-1 : mesure même sous les nuages' },
    { icon: '🌦️', need: 'Météo locale',
      before: 'Stations météo inexistantes autour de la parcelle',
      after: 'NASA POWER : température, pluie, vent au point du champ' },
    { icon: '🧠', need: 'Analyse et conseils',
      before: 'Données perdues, aucun outil accessible sans électricité',
      after: 'IA frugale : indicateurs clairs, légers en données' },
    { icon: '🗣️', need: 'Accessibilité',
      before: 'Interfaces uniquement en langues étrangères',
      after: 'Interface pensée pour les langues locales' },
  ];

  readonly features = [
    {
      title: 'Végétation en continu', tag: 'Sentinel-2',
      desc: "Le NDVI calculé depuis Sentinel-2 mesure la santé de vos cultures, parcelle par parcelle.",
      points: ['Cartes NDVI colorées', 'Couleurs réelles sans nuages'],
      grad: 'from-green-500 to-emerald-600',
      path: 'M12 21V11m0 0c0-4 3-7 8-7 0 5-3 8-8 8zm0 0c0-3-2-5-6-5 0 4 2 6 6 6',
    },
    {
      title: 'Radar à travers les nuages', tag: 'Sentinel-1',
      desc: "Le radar voit même en saison des pluies : structure de la végétation et humidité de surface.",
      points: ['Rétrodiffusion VV / VH', 'Indice de végétation radar (RVI)'],
      grad: 'from-sky-500 to-blue-600',
      path: 'M9.348 14.652a3.75 3.75 0 010-5.304m5.304 0a3.75 3.75 0 010 5.304m-7.425 2.121a6.75 6.75 0 010-9.546m9.546 0a6.75 6.75 0 010 9.546M5.106 18.894c-3.808-3.807-3.808-9.98 0-13.788m13.788 0c3.808 3.807 3.808 9.98 0 13.788M12 12h.008v.008H12V12z',
    },
    {
      title: 'Météo hyper-locale', tag: 'NASA POWER',
      desc: "Température, pluie, humidité, rayonnement et vent au point exact de votre champ.",
      points: ['14 jours d\'historique', 'Aucune station météo requise'],
      grad: 'from-amber-400 to-orange-500',
      path: 'M3 15a4 4 0 004 4h9a5 5 0 10-.1-9.999 5.002 5.002 0 10-9.78 2.096A4.001 4.001 0 003 15z',
    },
    {
      title: 'IA frugale', tag: 'Intelligence artificielle',
      desc: "Des modèles légers transforment ces données en indicateurs clairs, sans capteur ni drone.",
      points: ['Capteurs virtuels', 'Pensée pour les faibles débits'],
      grad: 'from-purple-500 to-fuchsia-600',
      path: 'M8.25 3v1.5M4.5 8.25H3m18 0h-1.5M4.5 12H3m18 0h-1.5m-15 3.75H3m18 0h-1.5M8.25 19.5V21M12 3v1.5m0 15V21m3.75-18v1.5m0 15V21m-9-1.5h10.5a2.25 2.25 0 002.25-2.25V6.75a2.25 2.25 0 00-2.25-2.25H6.75A2.25 2.25 0 004.5 6.75v10.5a2.25 2.25 0 002.25 2.25zm.75-12h9v9h-9v-9z',
    },
    {
      title: 'Parcelles connectées', tag: 'GPS',
      desc: "Dessinez votre champ sur la carte : superficie, périmètre et centre sont calculés automatiquement.",
      points: ['Carte satellite interactive', 'Superficie en hectares'],
      grad: 'from-teal-500 to-cyan-600',
      path: 'M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z M15 11a3 3 0 11-6 0 3 3 0 016 0z',
    },
    {
      title: 'Alertes intelligentes', tag: 'Prévention',
      desc: "Repérez tôt les stress hydriques, sécheresses et risques climatiques avant qu'ils coûtent cher.",
      points: ['Suivi de l\'évolution', 'Recommandations claires'],
      grad: 'from-rose-500 to-red-600',
      path: 'M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z',
    },
  ];

  scrollTo(id: string): void {
    if (!this.isBrowser || !id) return;
    const el = document.getElementById(id);
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

}

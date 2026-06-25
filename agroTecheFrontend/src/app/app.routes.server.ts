import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  // Routes avec paramètres dynamiques → rendu côté serveur à la demande
  { path: 'parcelles/:id',      renderMode: RenderMode.Server },
  { path: 'parcelles/:id/edit', renderMode: RenderMode.Server },
  // Tout le reste → prerender statique
  { path: '**', renderMode: RenderMode.Prerender }
];

import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  // Pages dont le contenu dépend de l'authentification (localStorage, indisponible
  // côté serveur). Si on les prérend/SSR, le serveur (jamais "connecté") produit un
  // DOM différent de celui que décide le client → mismatch d'hydratation (NG0500) →
  // page blanche. On les rend donc UNIQUEMENT côté client.
  { path: 'login',             renderMode: RenderMode.Client },
  { path: 'register',          renderMode: RenderMode.Client },
  { path: 'dashboard',         renderMode: RenderMode.Client },
  { path: 'parcelles',         renderMode: RenderMode.Client },
  { path: 'parcelles/new',     renderMode: RenderMode.Client },
  { path: 'parcelles/:id',     renderMode: RenderMode.Client },
  { path: 'parcelles/:id/edit', renderMode: RenderMode.Client },
  // Page d'accueil (statique, sans guard) → prerender
  { path: '**', renderMode: RenderMode.Prerender }
];

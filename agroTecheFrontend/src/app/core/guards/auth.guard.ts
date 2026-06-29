import { inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  // Côté serveur, l'état d'auth (localStorage) est inconnu : on laisse passer,
  // le client tranchera. Évite tout mismatch d'hydratation.
  if (!isPlatformBrowser(inject(PLATFORM_ID))) return true;
  if (inject(AuthService).isLoggedIn()) return true;
  return router.createUrlTree(['/login']);
};

export const guestGuard: CanActivateFn = () => {
  const router = inject(Router);
  if (!isPlatformBrowser(inject(PLATFORM_ID))) return true;
  if (!inject(AuthService).isLoggedIn()) return true;
  return router.createUrlTree(['/dashboard']);
};

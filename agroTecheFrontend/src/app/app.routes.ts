import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/home/home.component').then(m => m.HomeComponent),
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/register/register.component').then(m => m.RegisterComponent),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent),
  },
  {
    path: 'parcelles',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/parcelles/list/parcelles-list.component').then(m => m.ParcellesListComponent),
  },
  {
    path: 'parcelles/new',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/parcelles/form/parcelle-form.component').then(m => m.ParcelleFormComponent),
  },
  {
    path: 'parcelles/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/parcelles/detail/parcelle-detail.component').then(m => m.ParcelleDetailComponent),
  },
  {
    path: 'parcelles/:id/edit',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/parcelles/form/parcelle-form.component').then(m => m.ParcelleFormComponent),
  },
  { path: '**', redirectTo: 'dashboard' },
];

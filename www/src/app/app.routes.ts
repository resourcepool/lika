import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/dashboard',
    pathMatch: 'full'
  },
  {
    path: 'signin',
    loadComponent: () => import('./signin/signin').then(m => m.SigninComponent),
    canActivate: [guestGuard]
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./dashboard/dashboard').then(m => m.DashboardComponent),
    canActivate: [authGuard]
  },
  {
    path: 'admin/domains',
    loadComponent: () => import('./admin/domain-management/domain-management').then(m => m.DomainManagementComponent),
    canActivate: [authGuard]
  },
  {
    path: 'admin/accounts',
    loadComponent: () => import('./admin/account-management/account-management').then(m => m.AccountManagementComponent),
    canActivate: [authGuard]
  },
  {
    path: 'admin/archive',
    loadComponent: () => import('./admin/archive-management/archive-management').then(m => m.ArchiveManagementComponent),
    canActivate: [authGuard]
  },
  {
    path: '**',
    redirectTo: '/dashboard'
  }
];

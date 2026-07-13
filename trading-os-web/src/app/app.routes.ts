import { Routes } from '@angular/router';
import { Dashboard } from './features/dashboard/pages/dashboard/dashboard';
import { Accounts } from './features/accounts/pages/accounts/accounts';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full',
  },

  {
    path: 'dashboard',
    component: Dashboard,
  },

  {
    path: 'accounts',
    component: Accounts,
  },
];

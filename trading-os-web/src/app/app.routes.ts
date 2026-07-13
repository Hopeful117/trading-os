import { Routes } from '@angular/router';
import { Dashboard } from './features/dashboard/pages/dashboard/dashboard';
import { Accounts } from './features/accounts/pages/accounts/accounts';
import {LoginComponent} from './features/auth/components/login/login'

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

  {
    path: 'login',
    component: LoginComponent,
  },
];

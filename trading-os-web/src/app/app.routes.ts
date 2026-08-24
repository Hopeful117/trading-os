import { Routes } from '@angular/router';
import { Dashboard } from './features/dashboard/pages/dashboard/dashboard';
import { Accounts } from './features/accounts/pages/accounts/accounts';
import { LoginComponent } from './features/auth/components/login/login';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { RegisterComponent } from './features/auth/components/register/register';
import { HomeComponent } from './features/home/home';
import { ErrorPage } from './features/errors/errors';
import { Markets } from './features/markets/markets';
import { MarketDetail } from './features/markets/markets-details/markets-details';

export const routes: Routes = [
  {
    path: '',
    component: HomeComponent,
  },

  {
    path: 'dashboard',
    component: Dashboard,
    canActivate: [authGuard],
  },

  {
    path: 'accounts',
    component: Accounts,
    canActivate: [authGuard],
  },

  {
    path: 'login',
    component: LoginComponent,
    canActivate: [guestGuard],
  },
  {
    path: 'register',
    component: RegisterComponent,
    canActivate: [guestGuard],
  },
  {
    path: 'error',
    component: ErrorPage,
  },
  {
    path: 'markets',
    component: Markets,
    canActivate: [authGuard],
  },
  {
    path: 'markets/:marketId',
    component: MarketDetail,
    canActivate: [authGuard],
  },
];

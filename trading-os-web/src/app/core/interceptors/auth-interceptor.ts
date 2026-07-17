import { HttpInterceptorFn } from '@angular/common/http';
import { TokenService } from '../services/token';
import {AuthService} from '../services/auth.service';
import { inject } from '@angular/core';
import { provideRouter, Router} from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenService = inject(TokenService);
  const authService = inject(AuthService);
  const router = inject(Router);


  if (req.url.includes('/login') || req.url.includes('/register')) {
    return next(req);
  }

  const token = tokenService.getToken();

  let request = req;

  if (token) {
    request = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    });
  }

  return next(request).pipe(
    catchError((error) => {
      if (error.status === 401) {
        authService.logout();

        router.navigate(['/']);
      }

      return throwError(() => error);
    }),
  );
};

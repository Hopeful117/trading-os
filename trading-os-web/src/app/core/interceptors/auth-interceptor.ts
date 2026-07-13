import { HttpInterceptorFn } from '@angular/common/http';
import { TokenService } from '../services/token';
import { inject } from '@angular/core';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenService = inject(TokenService);


  if (req.url.includes('/auth/login')) {
    return next(req);
  }

  const token = tokenService.getToken();

  if (!token) {
    return next(req);
  }

  const authenticatedRequest = req.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`,
    },
  });

  return next(authenticatedRequest);
};

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { LoginRequest } from '../models/login-request.model';
import { AuthenticationResponse } from '../models/authentication-response.model';
import { TokenService } from './token';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private http = inject(HttpClient);
  private tokenService = inject(TokenService);

  login(request: LoginRequest): Observable<AuthenticationResponse> {
    return this.http
      .post<AuthenticationResponse>(`${environment.gatewayUrl}v1/users/login`, request)
      .pipe(tap((response) => this.tokenService.saveToken(response.token)));
  }

  logout(): void {
    this.tokenService.removeToken();
  }

  isLoggedIn(): boolean {
    return this.tokenService.isLoggedIn();
  }

  getToken(): string | null {
    return this.tokenService.getToken();
  }
}

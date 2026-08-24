import { inject, Injectable } from '@angular/core';
import { RegisterRequest } from '../models/register-request.model';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class RegisterService {
  private http = inject(HttpClient);
  register(request: RegisterRequest): Observable<void> {
    return this.http.post<void>(`${environment.gatewayUrl}v1/users/register`, request);
  }
}

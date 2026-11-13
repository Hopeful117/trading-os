import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ExecutionAuthorizationResponse } from '../models/execution-authorization.model';

@Injectable({ providedIn: 'root' })
export class ExecutionAuthorizationService {
  private readonly http = inject(HttpClient);

  get(id: string): Observable<ExecutionAuthorizationResponse> {
    return this.http.get<ExecutionAuthorizationResponse>(
      `${environment.gatewayUrl}v1/execution-authorizations/${id}`,
    );
  }

  decide(id: string, action: 'approve' | 'reject' | 'revoke', password: string): Observable<ExecutionAuthorizationResponse> {
    return this.http.post<ExecutionAuthorizationResponse>(
      `${environment.gatewayUrl}v1/execution-authorizations/${id}/${action}`,
      { password },
    );
  }
}

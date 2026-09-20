import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { DecisionContextResponse } from '../models/decision-context.model';

@Injectable({
  providedIn: 'root',
})
export class DecisionContextService {
  private readonly http = inject(HttpClient);

  resolve(accountId: string): Observable<DecisionContextResponse> {
    return this.http.get<DecisionContextResponse>(
      `${environment.gatewayUrl}v1/intelligence/decision-context/${accountId}`,
    );
  }
}

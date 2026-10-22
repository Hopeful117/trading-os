import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DecisionHistoryItem } from '../models/decision-history.model';

@Injectable({ providedIn: 'root' })
export class DecisionHistoryService {
  private readonly http = inject(HttpClient);

  list(accountId: string): Observable<DecisionHistoryItem[]> {
    return this.http.get<DecisionHistoryItem[]>(
      `${environment.gatewayUrl}v1/accounts/${encodeURIComponent(accountId)}/decision-history`,
    );
  }
}

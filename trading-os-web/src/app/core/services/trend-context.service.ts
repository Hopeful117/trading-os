import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { TrendContextReadModel } from '../models/trend-context.model';

@Injectable({
  providedIn: 'root',
})
export class TrendContextService {
  private readonly http = inject(HttpClient);

  findTrendContext(marketId: string): Observable<TrendContextReadModel> {
    return this.http.get<TrendContextReadModel>(
      `${environment.gatewayUrl}v1/intelligence/trend-context/${marketId}`,
    );
  }
}

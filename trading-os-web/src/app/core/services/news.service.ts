import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { EconomicEventQuery, EconomicEventResponse } from '../models/economic-event.model';

@Injectable({
  providedIn: 'root',
})
export class NewsService {
  private readonly http = inject(HttpClient);

  findEconomicEvents(query: EconomicEventQuery): Observable<EconomicEventResponse> {
    let params = new HttpParams().set('from', query.from).set('to', query.to);

    if (query.currency) {
      params = params.set('currency', query.currency);
    }
    if (query.impact) {
      params = params.set('impact', query.impact);
    }
    if (query.limit !== undefined) {
      params = params.set('limit', query.limit);
    }

    return this.http.get<EconomicEventResponse>(`${environment.gatewayUrl}v1/news/events`, {
      params,
    });
  }
}

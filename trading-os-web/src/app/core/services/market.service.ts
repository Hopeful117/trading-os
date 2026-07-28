import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MarketResponse } from '../models/market-response';
import { MarketStreamRequest } from '../models/market-stream-request';
import {OhlcEvent} from '../models/ohlc-event.model';
import {OhlcInterval} from '../models/ohlc-interval';


@Injectable({
  providedIn: 'root',
})
export class MarketService {
  private http = inject(HttpClient);

  findAll(): Observable<MarketResponse[]> {
    return this.http.get<MarketResponse[]>(`${environment.gatewayUrl}v1/markets`);
  }

  findById(id: string): Observable<MarketResponse> {
    return this.http.get<MarketResponse>(`${environment.gatewayUrl}v1/markets/${id}`);
  }

  subscribe(marketId: string, request: MarketStreamRequest): Observable<void> {
    return this.http.post<void>(
      `${environment.gatewayUrl}v1/markets/${marketId}/subscriptions`,
      request,
    );
  }

  unsubscribe(marketId: string, request: MarketStreamRequest): Observable<void> {
    return this.http.delete<void>(`${environment.gatewayUrl}v1/markets/${marketId}/subscriptions`, {
      body: request,
    });
  }

  findOhlcHistory(marketId: string, interval: OhlcInterval, limit = 200): Observable<OhlcEvent[]> {
    return this.http.get<OhlcEvent[]>(`${environment.gatewayUrl}v1/markets/${marketId}/ohlc`, {
      params: {
        interval,
        limit,
      },
    });
  }
}


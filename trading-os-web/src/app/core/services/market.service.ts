import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MarketResponse } from '../models/market-response';


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
}

import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  PassiveAwarenessPageResponse,
  PassiveAwarenessQueryParams,
} from '../models/passive-awareness.model';

@Injectable({
  providedIn: 'root',
})
export class PassiveAwarenessService {
  private readonly http = inject(HttpClient);

  query(params: PassiveAwarenessQueryParams = {}): Observable<PassiveAwarenessPageResponse> {
    let httpParams = new HttpParams();
    if (params.instrument) httpParams = httpParams.set('instrument', params.instrument);
    if (params.provider) httpParams = httpParams.set('provider', params.provider);
    if (params.productType) httpParams = httpParams.set('productType', params.productType);
    if (params.currentOnly != null)
      httpParams = httpParams.set('currentOnly', String(params.currentOnly));
    if (params.page != null) httpParams = httpParams.set('page', String(params.page));
    if (params.size != null) httpParams = httpParams.set('size', String(params.size));
    return this.http.get<PassiveAwarenessPageResponse>(
      `${environment.gatewayUrl}v1/passive-awareness`,
      { params: httpParams },
    );
  }
}

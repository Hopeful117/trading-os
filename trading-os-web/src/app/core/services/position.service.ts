import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { OpenPositionDashboardView } from '../models/dashboard-summary.model';
import { PositionCloseRequest, PositionCloseResponse } from '../models/position-close.model';

@Injectable({
  providedIn: 'root',
})
export class PositionService {
  constructor(private readonly http: HttpClient) {}

  getPositions(accountId: string): Observable<OpenPositionDashboardView[]> {
    return this.http.get<OpenPositionDashboardView[]>(
      `${environment.gatewayUrl}v1/accounts/${accountId}/positions`,
    );
  }

  closePosition(accountId: string, brokerPositionReference: string, idempotencyKey: string): Observable<PositionCloseResponse> {
    const headers = { 'Idempotency-Key': idempotencyKey };
    const body: PositionCloseRequest = { brokerPositionReference };
    return this.http.post<PositionCloseResponse>(
      `${environment.gatewayUrl}v1/accounts/${accountId}/positions/close`,
      body,
      { headers },
    );
  }

  reconcileClose(accountId: string, commandId: string): Observable<PositionCloseResponse> {
    return this.http.post<PositionCloseResponse>(
      `${environment.gatewayUrl}v1/accounts/${accountId}/positions/close/${commandId}/reconcile`,
      {},
    );
  }
}
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CreateTradePlanResponse,
  RiskDecisionResponse,
  TradePlanResponse,
} from '../models/trade-plan.model';

@Injectable({
  providedIn: 'root',
})
export class TradePlanService {
  private http = inject(HttpClient);

  createFromOpportunity(
    opportunityId: string,
    accountId: string,
    idempotencyKey: string,
  ): Observable<CreateTradePlanResponse> {
    return this.http.post<CreateTradePlanResponse>(
      `${environment.gatewayUrl}v1/trade-plans/opportunities/${opportunityId}/trade-plans`,
      { accountId },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
  }

  getPlan(planId: string, version: number): Observable<TradePlanResponse> {
    return this.http.get<TradePlanResponse>(
      `${environment.gatewayUrl}v1/trade-plans/${planId}/versions/${version}`,
    );
  }

  decide(
    planId: string,
    version: number,
    decision: 'ACCEPT' | 'REJECT',
  ): Observable<TradePlanResponse> {
    return this.http.post<TradePlanResponse>(
      `${environment.gatewayUrl}v1/trade-plans/${planId}/versions/${version}/decisions`,
      { decision },
    );
  }

  evaluateRisk(
    planId: string,
    version: number,
    accountId: string,
    idempotencyKey: string,
  ): Observable<RiskDecisionResponse> {
    return this.http.post<RiskDecisionResponse>(
      `${environment.gatewayUrl}v1/trade-plans/${planId}/versions/${version}/risk-evaluations`,
      { accountId },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
  }
}

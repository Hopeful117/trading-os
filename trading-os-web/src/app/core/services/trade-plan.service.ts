import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CreateTradePlanResponse,
  ManualTradePlanRequest,
  RiskDecisionResponse,
  TradeScenarioProjection,
  TradePlanResponse,
} from '../models/trade-plan.model';

@Injectable({
  providedIn: 'root',
})
export class TradePlanService {
  private readonly http = inject(HttpClient);

  createFromOpportunity(
    opportunityId: string,
    opportunityVersion: number,
    accountId: string,
    idempotencyKey: string,
  ): Observable<CreateTradePlanResponse> {
    return this.http.post<CreateTradePlanResponse>(
      `${environment.gatewayUrl}v1/trade-plans/opportunities/${opportunityId}/trade-plans`,
      { accountId, opportunityVersion },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
  }

  recordNoTrade(
    accountId: string,
    opportunityId: string,
    opportunityVersion: number,
    thesisId: string,
    thesisVersion: number,
    idempotencyKey: string,
    rationale?: string,
  ): Observable<{ id: string; decision: 'NO_TRADE' }> {
    return this.http.post<{ id: string; decision: 'NO_TRADE' }>(
      `${environment.gatewayUrl}v1/accounts/${accountId}/opportunity-decisions`,
      { opportunityId, opportunityVersion, thesisId, thesisVersion, rationale },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
  }

  createManual(
    request: ManualTradePlanRequest,
    idempotencyKey: string,
  ): Observable<CreateTradePlanResponse> {
    return this.http.post<CreateTradePlanResponse>(
      `${environment.gatewayUrl}v1/trade-plans/manual`,
      request,
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

  getProjection(planId: string, version: number): Observable<TradeScenarioProjection> {
    return this.http.get<TradeScenarioProjection>(
      `${environment.gatewayUrl}v1/trade-plans/${planId}/versions/${version}/projection`,
    );
  }
}

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { Observable, of } from 'rxjs';

import { RiskDecisionResponse, TradePlanResponse } from '../../../core/models/trade-plan.model';
import { TradePlanService } from '../../../core/services/trade-plan.service';
import { PlanPage } from './plan-page';

function fakePlan(status: string): TradePlanResponse {
  return {
    id: 'tp-1',
    version: 1,
    previousVersion: null,
    status,
    planningContextId: 'ctx-1',
    planningContextVersion: 1,
    contextCapturedAt: '2025-01-01T00:00:00Z',
    instrument: 'BTC/EUR',
    direction: 'LONG',
    entryType: 'MARKET',
    entryPrice: 100,
    stopLoss: 95,
    takeProfits: [110, 120],
    quantity: 1,
    notional: 100,
    monetaryRisk: 5,
    riskReward: 2.0,
    expiresAt: '2025-01-02T00:00:00Z',
    thesis: 'Breakout above resistance',
    opportunityIds: [],
    observationIds: [],
    aiAnalysisIds: [],
    confirmationConditions: [],
    invalidationConditions: [],
    managementRules: [],
    createdAt: '2025-01-01T00:00:00Z',
    tradingAccountId: 'acc-1',
  };
}

function fakeRiskDecision(decision: string): RiskDecisionResponse {
  return {
    evaluationId: 'eval-1',
    tradePlanId: 'tp-1',
    tradePlanVersion: 1,
    accountId: 'acc-1',
    status: 'COMPLETED',
    decision: decision as RiskDecisionResponse['decision'],
    approved: decision !== 'REJECTED',
    reasons: [],
    warnings: [],
    evaluatedAt: '2025-01-01T00:00:00Z',
  };
}

function mockActivatedRoute(params: Record<string, string>) {
  return {
    paramMap: of({
      get: (key: string) => params[key] ?? null,
      has: (key: string) => key in params,
      getAll: () => [],
      keys: Object.keys(params),
    }),
  };
}

describe('PlanPage', () => {
  let fixture: ComponentFixture<PlanPage>;

  function configureMocks(
    plan$: Observable<TradePlanResponse> = of(fakePlan('PROPOSED')),
    decide$: Observable<TradePlanResponse> = of(fakePlan('ACCEPTED')),
    risk$: Observable<RiskDecisionResponse> = of(fakeRiskDecision('APPROVED')),
  ) {
    const tradePlanService = {
      getPlan: () => plan$,
      decide: () => decide$,
      evaluateRisk: () => risk$,
    };
    return TestBed.configureTestingModule({
      imports: [PlanPage],
      providers: [
        { provide: ActivatedRoute, useValue: mockActivatedRoute({ planId: 'tp-1', version: '1' }) },
        { provide: TradePlanService, useValue: tradePlanService },
      ],
    });
  }

  it('shows proposal state with accept/reject buttons', () => {
    configureMocks();
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    const card = fixture.nativeElement.querySelector('[data-testid="proposal-state"]');
    expect(card).toBeTruthy();
    expect(card.textContent).toContain('BTC/EUR');
    expect(fixture.nativeElement.querySelector('[data-testid="accept-button"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="reject-button"]')).toBeTruthy();
  });

  it('shows accepted state with evaluate risk button', () => {
    configureMocks(of(fakePlan('ACCEPTED')));
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="accepted-state"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="evaluate-risk-button"]'),
    ).toBeTruthy();
  });

  it('shows rejected state', () => {
    configureMocks(of(fakePlan('REJECTED')));
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="rejected-state"]')).toBeTruthy();
  });

  it('shows risk decision state for APPROVED', () => {
    configureMocks(
      of(fakePlan('ACCEPTED')),
      of(fakePlan('ACCEPTED')),
      of(fakeRiskDecision('APPROVED')),
    );
    fixture = TestBed.createComponent(PlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="evaluate-risk-button"]')?.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="risk-decision-state"]')).toBeTruthy();
  });
});

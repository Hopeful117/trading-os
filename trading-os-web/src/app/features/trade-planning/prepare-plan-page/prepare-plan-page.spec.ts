import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { Observable, of } from 'rxjs';

import { OpportunityResponse } from '../../../core/models/opportunity.model';
import { AccountService } from '../../../core/services/account.service';
import { OpportunityService } from '../../../core/services/opportunity.service';
import { TradePlanService } from '../../../core/services/trade-plan.service';
import { PreparePlanPage } from './prepare-plan-page';

function fakeOpportunity(status: string): OpportunityResponse {
  return {
    id: 'opp-1',
    version: 1,
    status: status as OpportunityResponse['status'],
    instrument: 'BTC/EUR',
    direction: 'LONG',
    scenario: 'Bullish breakout',
    timeframe: '1h',
    type: 'INTRADAY',
    origin: 'PASSIVE_SCAN',
    score: 82,
    explanation: 'Breakout above resistance',
    observationIds: [],
    aiAnalysisIds: [],
    evaluatedAt: '2025-01-01T00:00:00Z',
    validFrom: '2025-01-01T00:00:00Z',
    validUntil: null,
    createdAt: '2025-01-01T00:00:00Z',
    strategyMatchId: null,
  };
}

const fakeAccounts = [
  {
    accountId: 'acc-1',
    name: 'Test',
    baseCurrency: 'EUR',
    balances: { balances: {} },
    equity: 1000,
    peakEquity: 1000,
    rulesId: 'r1',
    userId: 'u1',
  },
];

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

describe('PreparePlanPage', () => {
  let fixture: ComponentFixture<PreparePlanPage>;

  function configureMocks(
    params: Record<string, string>,
    opportunity$: Observable<OpportunityResponse>,
    accounts$: Observable<unknown[]> = of(fakeAccounts),
  ) {
    const opportunityService = { findById: () => opportunity$ };
    const accountService = { getAccounts: () => accounts$ };
    const tradePlanService = {
      createFromOpportunity: () => of({ tradePlanId: 'tp-1', tradePlanVersion: 1 }),
    };
    return TestBed.configureTestingModule({
      imports: [PreparePlanPage],
      providers: [
        { provide: ActivatedRoute, useValue: mockActivatedRoute(params) },
        { provide: OpportunityService, useValue: opportunityService },
        { provide: AccountService, useValue: accountService },
        { provide: TradePlanService, useValue: tradePlanService },
      ],
    });
  }

  it('shows ready state for active opportunity', () => {
    configureMocks({ opportunityId: 'opp-1' }, of(fakeOpportunity('ACTIVE')));
    fixture = TestBed.createComponent(PreparePlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    const card = fixture.nativeElement.querySelector('[data-testid="prepare-form"]');
    expect(card).toBeTruthy();
    expect(card.textContent).toContain('BTC/EUR');
  });

  it('shows error for non-active opportunity', () => {
    configureMocks({ opportunityId: 'opp-1' }, of(fakeOpportunity('CONSUMED')));
    fixture = TestBed.createComponent(PreparePlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).toBeTruthy();
  });

  it('disables create button when no account selected', () => {
    configureMocks({ opportunityId: 'opp-1' }, of(fakeOpportunity('ACTIVE')));
    fixture = TestBed.createComponent(PreparePlanPage);
    fixture.detectChanges();
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('[data-testid="create-plan-button"]');
    expect(btn.disabled).toBe(true);
  });
});

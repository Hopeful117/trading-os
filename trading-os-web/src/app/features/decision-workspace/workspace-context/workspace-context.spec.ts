// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DecisionContextAccount } from '../../../core/models/decision-context.model';
import { MarketResponse } from '../../../core/models/market-response';
import { WorkspaceContext, WorkspaceContextView } from './workspace-context';

describe('WorkspaceContext', () => {
  let fixture: ComponentFixture<WorkspaceContext>;

  const account: DecisionContextAccount = {
    accountId: 'account-1',
    brokerAccountId: 'broker-1',
    name: 'Paper account',
    baseCurrency: 'USD',
    equity: 1000,
    peakEquity: 1000,
    rulesId: 'rules-1',
    userId: 'user-1',
    riskProfileId: 'risk-1',
    riskProfileSemanticVersion: '1.0.0',
    tradePlanningProfileId: 'planning-1',
    tradePlanningProfileVersion: 1,
  };

  const market: MarketResponse = {
    marketId: 'market-1',
    provider: 'KRAKEN',
    symbol: 'BTC/USD',
    baseAsset: 'BTC',
    quoteAsset: 'USD',
    productType: 'SPOT',
    marketState: {
      tradingStatus: 'OPEN',
      tradable: true,
      closureReason: '',
      lastUpdated: '2026-09-20T10:00:00Z',
    },
    marketConstraints: {
      minimumOrderSize: 0.001,
      minimumCost: 5,
      tickSize: 0.01,
      quantityPrecision: 8,
      pricePrecision: 2,
    },
  };

  const baseView: WorkspaceContextView = {
    account,
    accountState: 'selected',
    market,
    marketState: 'ready',
    opportunity: null,
    freshness: 'LIVE',
    workflowStage: 'Prepare Trade Plan',
    nextAction: 'Review the proposal before acceptance',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [WorkspaceContext] }).compileComponents();
    fixture = TestBed.createComponent(WorkspaceContext);
    fixture.componentRef.setInput('view', baseView);
    fixture.detectChanges();
  });

  it('renders account, market, tradability, freshness, and next action', () => {
    const element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).toContain('Paper account');
    expect(element.textContent).toContain('BTC/USD');
    expect(element.textContent).toContain('Tradable');
    expect(element.textContent).toContain('LIVE');
    expect(element.textContent).toContain('Review the proposal before acceptance');
  });

  it('keeps unknown context explicit', () => {
    fixture.componentRef.setInput('view', {
      ...baseView,
      account: null,
      accountState: 'unavailable',
      market: null,
      marketState: 'unselected',
      freshness: 'UNAVAILABLE',
      workflowStage: 'Select account',
      nextAction: 'Select an account',
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('No account selected');
    expect(element.textContent).toContain('No market selected');
    expect(element.textContent).toContain('UNKNOWN');
    expect(element.textContent).toContain('Select an account');
  });

  it('keeps Opportunity provenance separate from tradability and freshness', () => {
    fixture.componentRef.setInput('view', {
      ...baseView,
      opportunity: {
        id: 'opportunity-1',
        version: 2,
        status: 'ACTIVE',
        instrument: 'BTC/USD',
        direction: 'LONG',
        scenario: 'OHLC_TREND',
        timeframe: '15m',
        type: 'INTRADAY',
        origin: 'ACTIVE_SCAN',
        score: 0.8,
        explanation: 'Deterministic setup',
        observationIds: [],
        aiAnalysisIds: [],
        evaluatedAt: '2026-09-20T10:00:00Z',
        validFrom: '2026-09-20T10:00:00Z',
        validUntil: null,
        createdAt: '2026-09-20T10:00:00Z',
        strategyMatchId: null,
        marketId: 'market-1',
        setup: null,
      },
      workflowStage: 'Review opportunity',
      nextAction: 'Review evidence before preparation',
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Opportunity context');
    expect(element.textContent).toContain('LONG · OHLC_TREND');
    expect(element.textContent).toContain('LIVE');
    expect(element.textContent).not.toContain('Risk approval');
  });
});

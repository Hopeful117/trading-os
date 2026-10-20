// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DecisionContextAccount } from '../../../core/models/decision-context.model';
import { MarketResponse } from '../../../core/models/market-response';
import { WorkspaceContextStrip, WorkspaceContextView } from './workspace-context-strip';

describe('WorkspaceContextStrip', () => {
  let fixture: ComponentFixture<WorkspaceContextStrip>;

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
    await TestBed.configureTestingModule({ imports: [WorkspaceContextStrip] }).compileComponents();
    fixture = TestBed.createComponent(WorkspaceContextStrip);
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

  it.each([
    ['loading', 'Loading account context', 'Authoritative account data is being resolved.'],
    ['error', 'Unavailable', 'Account context could not be resolved.'],
  ] as const)('preserves the account %s state', (accountState, value, detail) => {
    fixture.componentRef.setInput('view', {
      ...baseView,
      account: null,
      accountState,
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain(value);
    expect(element.textContent).toContain(detail);
  });

  it.each([
    ['loading', 'Loading market', 'Market facts are being resolved after eligibility.'],
    ['unavailable', 'Unavailable', 'Market facts are currently unavailable.'],
    ['ineligible', 'Not eligible', 'This account context excludes the selected market.'],
    ['not-tradable', 'Not tradable', 'Market Data reports that this market cannot be used now.'],
    ['error', 'Unavailable', 'Market facts are currently unavailable.'],
  ] as const)('preserves the market %s state', (marketState, value, detail) => {
    fixture.componentRef.setInput('view', {
      ...baseView,
      market: null,
      marketState,
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain(value);
    expect(element.textContent).toContain(detail);
  });

  it('keeps a non-tradable ready market distinct from an ineligible market', () => {
    fixture.componentRef.setInput('view', {
      ...baseView,
      market: {
        ...market,
        marketState: { ...market.marketState, tradable: false, tradingStatus: 'HALTED' },
      },
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const badge = element.querySelector('app-status-badge .status-badge');
    expect(element.textContent).toContain('Not tradable');
    expect(element.textContent).toContain('HALTED');
    expect(badge?.getAttribute('data-tone')).toBe('neutral');
  });

  it.each([
    ['ready-tradable', { marketState: 'ready', market }, 'positive'],
    [
      'ready-non-tradable',
      {
        marketState: 'ready',
        market: {
          ...market,
          marketState: { ...market.marketState, tradable: false },
        },
      },
      'neutral',
    ],
    ['ineligible', { marketState: 'ineligible', market: null }, 'warning'],
    ['not-tradable', { marketState: 'not-tradable', market: null }, 'warning'],
    ['unknown', { marketState: 'unselected', market: null }, 'neutral'],
  ] as const)('uses the expected status tone for %s', (_state, marketContext, tone) => {
    fixture.componentRef.setInput('view', { ...baseView, ...marketContext });
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement)
        .querySelector('app-status-badge .status-badge')
        ?.getAttribute('data-tone'),
    ).toBe(tone);
  });

  it.each([
    ['RECENT', 'RECENT'],
    ['STALE', 'STALE'],
  ] as const)('renders %s freshness without collapsing it into LIVE', (freshness, label) => {
    fixture.componentRef.setInput('view', { ...baseView, freshness });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const freshnessLabel = element.querySelector('.freshness-indicator__label');
    expect(freshnessLabel?.textContent).toContain(label);
    expect(freshnessLabel?.textContent).not.toContain('LIVE');
  });

  it('exposes a labelled panel and visible state labels for assistive technologies', () => {
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('section.workspace-panel')?.getAttribute('aria-labelledby')).toBe(
      'workspace-context-title',
    );
    expect(element.querySelector('.metric-value__label')?.textContent).toContain('Account');
    expect(element.querySelector('.status-badge')?.textContent).toContain('Tradable');
    expect(element.querySelector('.freshness-indicator__label')?.textContent).toContain('LIVE');
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

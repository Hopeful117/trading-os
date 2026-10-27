// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { BehaviorSubject, NEVER, Observable, of, Subject, throwError } from 'rxjs';

import { Account } from '../../core/models/account.model';
import { DashboardSummary } from '../../core/models/dashboard-summary.model';
import { DecisionContextResponse } from '../../core/models/decision-context.model';
import { OpportunityResponse } from '../../core/models/opportunity.model';
import { AccountService } from '../../core/services/account.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { DecisionContextService } from '../../core/services/decision-context.service';
import {
  TrendAttention,
  TrendContextAssessment,
  TrendContextReadModel,
} from '../../core/models/trend-context.model';
import { TrendContextService } from '../../core/services/trend-context.service';
import { MarketDataStreamService } from '../../core/services/market-data-stream.service';
import { MarketService } from '../../core/services/market.service';
import { ActiveScanService } from '../../core/services/active-scan.service';
import { OpportunityService } from '../../core/services/opportunity.service';
import { TradePlanService } from '../../core/services/trade-plan.service';
import { OhlcInterval } from '../../core/models/ohlc-interval';
import { DecisionWorkspace } from './decision-workspace';

describe('DecisionWorkspace', () => {
  let fixture: ComponentFixture<DecisionWorkspace>;
  let component: DecisionWorkspace;
  let accountServiceMock: { getAccounts: ReturnType<typeof vi.fn> };
  let dashboardServiceMock: { findDashboard: ReturnType<typeof vi.fn> };
  let contextServiceMock: { resolve: ReturnType<typeof vi.fn> };
  let trendContextServiceMock: { findTrendContext: ReturnType<typeof vi.fn> };
  let marketServiceMock: {
    findById: ReturnType<typeof vi.fn>;
    findOhlcHistory: ReturnType<typeof vi.fn>;
    findAll: ReturnType<typeof vi.fn>;
    subscribe: ReturnType<typeof vi.fn>;
    unsubscribe: ReturnType<typeof vi.fn>;
  };
  let marketDataStreamServiceMock: {
    streamTicker: ReturnType<typeof vi.fn>;
    streamOhlc: ReturnType<typeof vi.fn>;
    streamOrderBook: ReturnType<typeof vi.fn>;
    streamRecentTrades: ReturnType<typeof vi.fn>;
  };
  let routerMock: { navigate: ReturnType<typeof vi.fn> };
  let tradePlanServiceMock: {
    createManual: ReturnType<typeof vi.fn>;
    createFromOpportunity: ReturnType<typeof vi.fn>;
    recordNoTrade: ReturnType<typeof vi.fn>;
  };
  let opportunityServiceMock: { findById: ReturnType<typeof vi.fn> };
  let activeScanServiceMock: {
    createScan: ReturnType<typeof vi.fn>;
    findScan: ReturnType<typeof vi.fn>;
    resolveScope: ReturnType<typeof vi.fn>;
  };
  let routeQueryParamMap: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  const candidateLineage = {
    version: 1,
    marketThesis: { thesisId: 'thesis-1', thesisVersion: 1 },
    thesis: { state: 'CANDIDATE' },
  } as unknown as Partial<OpportunityResponse>;

  const account: Account = {
    accountId: 'account-1',
    brokerAccountId: 'broker-1',
    name: 'Paper account',
    baseCurrency: 'USD',
    balances: {} as Account['balances'],
    equity: 1000,
    peakEquity: 1000,
    rulesId: 'rules-1',
    userId: 'user-1',
    riskProfileId: 'risk-1',
    riskProfileSemanticVersion: '1.0.0',
    tradePlanningProfileId: 'planning-1',
    tradePlanningProfileVersion: 1,
  };

  const context: DecisionContextResponse = {
    account: {
      accountId: account.accountId,
      brokerAccountId: account.brokerAccountId ?? null,
      name: account.name,
      baseCurrency: account.baseCurrency,
      equity: account.equity,
      peakEquity: account.peakEquity,
      rulesId: account.rulesId,
      userId: account.userId,
      riskProfileId: account.riskProfileId ?? null,
      riskProfileSemanticVersion: account.riskProfileSemanticVersion ?? null,
      tradePlanningProfileId: account.tradePlanningProfileId ?? null,
      tradePlanningProfileVersion: account.tradePlanningProfileVersion ?? null,
    },
    markets: [
      { marketId: 'market-1', symbol: 'BTC/USD', provider: 'KRAKEN', eligible: true, reasons: [] },
      {
        marketId: 'market-2',
        symbol: 'ETH/USD',
        provider: 'KRAKEN',
        eligible: false,
        reasons: ['MARKET_NOT_TRADABLE'],
      },
      { marketId: 'market-3', symbol: 'ETH/USD', provider: 'KRAKEN', eligible: true, reasons: [] },
    ],
    eligibleMarketIds: ['market-1', 'market-3'],
    resolvedAt: '2026-09-20T10:00:00Z',
  };

  const dashboardSummary: DashboardSummary = {
    account: {
      accountId: account.accountId,
      accountName: account.name,
      broker: 'KRAKEN',
      currency: account.baseCurrency,
      balance: 1000,
      equity: 1020,
      dailyPnl: 20,
      dailyPnlPercentage: 2,
      currentDrawdown: 80,
      currentDrawdownPercentage: 7.27,
      equitySource: 'BROKER',
    },
    risk: {
      status: 'SAFE',
      usedRiskAmount: 10,
      usedRiskPercentage: 1,
      remainingRiskAmount: 90,
      remainingRiskPercentage: 9,
      dailyLossAmount: 0,
      dailyLossPercentage: 0,
      maximumDailyLossPercentage: 5,
      totalDrawdownAmount: 80,
      totalDrawdownPercentage: 7.27,
      maximumDrawdownPercentage: 10,
      rules: [],
    },
    openPositions: [],
    alerts: [],
    watchedMarkets: [],
    freshness: {
      status: 'LIVE',
      brokerDataAt: '2026-09-20T10:00:00Z',
      marketDataAt: '2026-09-20T10:00:00Z',
      calculatedAt: '2026-09-20T10:00:00Z',
      brokerDataStale: false,
      marketDataStale: false,
      warnings: [],
    },
    generatedAt: '2026-09-20T10:00:00Z',
  };

  const market = {
    marketId: 'market-1',
    provider: 'KRAKEN',
    symbol: 'BTC/USD',
    baseAsset: 'BTC',
    quoteAsset: 'USD',
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

  const unavailableTrendContext: TrendContextReadModel = {
    marketId: 'market-1',
    operationalStatus: 'MISSING',
    assessmentPresent: false,
    assessmentValidity: 'NONE',
    observationId: null,
    lineageId: null,
    observationVersion: null,
    observationStatus: null,
    validFrom: null,
    validUntil: null,
    assessment: null,
    lastSuccessfulAssessment: null,
    analysisExecutionId: null,
    capabilityExecutionIds: [],
    diagnostics: [],
    sourceReferences: {},
  };

  beforeEach(async () => {
    accountServiceMock = { getAccounts: vi.fn().mockReturnValue(of([account])) };
    dashboardServiceMock = {
      findDashboard: vi.fn().mockReturnValue(of(dashboardSummary)),
    };
    contextServiceMock = { resolve: vi.fn().mockReturnValue(of(context)) };
    trendContextServiceMock = {
      findTrendContext: vi.fn().mockReturnValue(of(unavailableTrendContext)),
    };
    marketServiceMock = {
      findById: vi.fn().mockReturnValue(of(market)),
      findOhlcHistory: vi.fn().mockReturnValue(of([])),
      findAll: vi.fn().mockReturnValue(of([market])),
      subscribe: vi.fn().mockReturnValue(of(undefined)),
      unsubscribe: vi.fn().mockReturnValue(of(undefined)),
    };
    marketDataStreamServiceMock = {
      streamTicker: vi.fn().mockReturnValue(NEVER),
      streamOhlc: vi.fn().mockReturnValue(NEVER),
      streamOrderBook: vi.fn().mockReturnValue(NEVER),
      streamRecentTrades: vi.fn().mockReturnValue(NEVER),
    };
    routerMock = { navigate: vi.fn().mockResolvedValue(true) };
    tradePlanServiceMock = {
      createManual: vi.fn(),
      createFromOpportunity: vi
        .fn()
        .mockReturnValue(of({ tradePlanId: 'plan-1', tradePlanVersion: 1 })),
      recordNoTrade: vi.fn().mockReturnValue(of({ id: 'decision-1', decision: 'NO_TRADE' })),
    };
    opportunityServiceMock = { findById: vi.fn() };
    activeScanServiceMock = {
      createScan: vi.fn(),
      findScan: vi.fn(),
      resolveScope: vi.fn(),
    };
    routeQueryParamMap = new BehaviorSubject(convertToParamMap({}));
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe(): void {}
        unobserve(): void {}
        disconnect(): void {}
      },
    );
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({
        addListener: vi.fn(),
        addEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
        matches: false,
        media: '',
        onchange: null,
        removeListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    );

    await TestBed.configureTestingModule({
      imports: [DecisionWorkspace],
      providers: [
        { provide: AccountService, useValue: accountServiceMock },
        { provide: DashboardService, useValue: dashboardServiceMock },
        { provide: DecisionContextService, useValue: contextServiceMock },
        { provide: TrendContextService, useValue: trendContextServiceMock },
        { provide: MarketService, useValue: marketServiceMock },
        { provide: ActiveScanService, useValue: activeScanServiceMock },
        { provide: MarketDataStreamService, useValue: marketDataStreamServiceMock },
        { provide: OpportunityService, useValue: opportunityServiceMock },
        { provide: TradePlanService, useValue: tradePlanServiceMock },
        { provide: Router, useValue: routerMock },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: routeQueryParamMap.asObservable() },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DecisionWorkspace);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('does not resolve markets before account selection', () => {
    expect(contextServiceMock.resolve).not.toHaveBeenCalled();
    expect(
      fixture.nativeElement.querySelector('[data-testid="select-account-state"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="manual-trade-ticket"]')).toBeNull();
  });

  it('resolves account-scoped markets after account selection', async () => {
    component.selectAccount(account.accountId);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(contextServiceMock.resolve).toHaveBeenCalledWith(account.accountId);
    expect(fixture.nativeElement.querySelector('[data-testid="eligible-markets"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('BTC/USD');
    expect(fixture.nativeElement.querySelector('[data-testid="excluded-markets"]')).toBeNull();
  });

  it('renders a structured block when the Challenge has no compatible market universe', async () => {
    contextServiceMock.resolve.mockReturnValueOnce(
      of({
        ...context,
        markets: [],
        eligibleMarketIds: [],
        scopeStatus: 'NO_COMPATIBLE_MARKETS',
        scopeMessage: 'No market is available for the active Challenge provider',
      }),
    );

    component.selectAccount(account.accountId);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(
      fixture.nativeElement.querySelector('[data-testid="decision-context-scope-blocked"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('No market is available');
  });

  it('embeds one account-scoped market navigator without a second account selector', async () => {
    component.selectAccount(account.accountId);
    fixture.detectChanges();
    await fixture.whenStable();

    const navigator = fixture.nativeElement.querySelector('[data-testid="market-navigator"]');
    expect(navigator).toBeTruthy();
    expect(navigator.querySelector('[data-testid="account-select"]')).toBeNull();
  });

  it('loads the account cockpit from the authoritative dashboard contract', async () => {
    component.selectAccount(account.accountId);
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();

    expect(dashboardServiceMock.findDashboard).toHaveBeenCalledWith(account.accountId);
    expect(fixture.nativeElement.querySelector('[data-testid="cockpit-context"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('SAFE');
    expect(fixture.nativeElement.textContent).toContain('Balance');
    expect(fixture.nativeElement.textContent).toContain('Drawdown');
    expect(fixture.nativeElement.textContent).toContain('1,000');
    expect(fixture.nativeElement.textContent).toContain('7.27');
    expect(fixture.nativeElement.textContent).toContain('Open positions');
  });

  it('clears the previous cockpit while the next account is loading', () => {
    dashboardServiceMock.findDashboard.mockReturnValue(NEVER);

    component.selectAccount(account.accountId);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="cockpit-context-loading"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="cockpit-context"]')).toBeNull();
  });

  it('does not imply a currency when position exposure has no quote asset', async () => {
    dashboardServiceMock.findDashboard.mockReturnValue(
      of({
        ...dashboardSummary,
        openPositions: [
          {
            positionId: 'position-1',
            accountId: account.accountId,
            marketId: 'market-1',
            symbol: 'BTC/USD',
            side: 'BUY',
            quantity: 1,
            entryPrice: 100,
            currentPrice: 110,
            stopLoss: null,
            takeProfit: null,
            unrealizedPnl: 10,
            unrealizedPnlPercentage: 10,
            brokerUnrealizedPnl: 10,
            riskAmount: 5,
            riskPercentage: 0.5,
            exposure: 110,
            protectionStatus: 'PROTECTED',
            marketTradable: true,
            openedAt: null,
            priceOccurredAt: null,
            calculatedAt: '2026-09-20T10:00:00Z',
            source: 'BROKER',
            valuationStatus: 'FRESH',
            quoteAsset: null,
          },
        ],
      }),
    );

    component.selectAccount(account.accountId);
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('currency unavailable');
  });

  it('clears rendered cockpit facts immediately when the account changes', async () => {
    component.selectAccount(account.accountId);
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();

    dashboardServiceMock.findDashboard.mockReturnValue(NEVER);
    component.selectAccount('account-2');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="cockpit-context"]')).toBeNull();
    expect(
      fixture.nativeElement.querySelector('[data-testid="cockpit-context-loading"]'),
    ).toBeTruthy();
  });

  it('shows loading context after an account is selected while resolution is pending', () => {
    contextServiceMock.resolve.mockReturnValue(NEVER);

    component.selectAccount(account.accountId);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Loading account context');
    expect(text).not.toContain('No account selected');
  });

  it('shows the available market catalogue and filters it by search', async () => {
    component.selectAccount(account.accountId);
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="eligible-markets"]')).toBeTruthy();
    expect(element.textContent).toContain('BTC/USD');
    expect(element.textContent).toContain('ETH/USD');

    component.setMarketSearch('btc');
    fixture.detectChanges();
    expect(element.textContent).toContain('BTC/USD');
    expect(element.textContent).not.toContain('ETH/USD');
  });

  it('clears the selected market when the account changes', () => {
    component.selectedAccountId = 'account-1';
    component.selectedMarketId = 'market-1';

    component.selectAccount('account-2');

    expect(component.selectedMarketId).toBeNull();
  });

  it('does not select an excluded market', () => {
    component.selectMarket('market-2', context);

    expect(component.selectedMarketId).toBeNull();
  });

  it('synchronizes a Navigator Opportunity through the existing route context', () => {
    const opportunity = {
      id: 'opportunity-1',
      marketId: 'market-1',
    } as OpportunityResponse;

    component.selectNavigatorOpportunity(opportunity, context);

    expect(component.selectedMarketId).toBe('market-1');
    expect(routerMock.navigate).toHaveBeenLastCalledWith([], {
      queryParams: { marketId: 'market-1', opportunityId: 'opportunity-1' },
      queryParamsHandling: 'merge',
    });
  });

  it('ignores a Navigator Opportunity without an eligible market identity', () => {
    component.selectNavigatorOpportunity({ id: 'opportunity-1' } as OpportunityResponse, context);

    expect(component.selectedMarketId).toBeNull();
    expect(routerMock.navigate).not.toHaveBeenCalled();
  });

  it('creates an opportunity Trade Plan from the selected account', () => {
    component.selectedAccountId = account.accountId;
    component.opportunity = {
      ...candidateLineage,
      id: 'opportunity-1',
      status: 'ACTIVE',
      validFrom: new Date(Date.now() - 60_000).toISOString(),
      validUntil: null,
    } as OpportunityResponse;

    component.prepareFromOpportunity();

    expect(tradePlanServiceMock.createFromOpportunity).toHaveBeenCalledWith(
      'opportunity-1',
      1,
      account.accountId,
      expect.any(String),
    );
    expect(routerMock.navigate).toHaveBeenCalledWith([
      '/trade-planning',
      'plans',
      'plan-1',
      'versions',
      1,
    ]);
  });

  it('renders a preparation error when Trade Plan creation fails', () => {
    tradePlanServiceMock.createFromOpportunity.mockReturnValue(
      throwError(() => new Error('service unavailable')),
    );
    component.selectAccount(account.accountId);
    component.opportunity = {
      ...candidateLineage,
      id: 'opportunity-1',
      status: 'ACTIVE',
      validFrom: new Date(Date.now() - 60_000).toISOString(),
      validUntil: null,
    } as OpportunityResponse;

    component.prepareFromOpportunity();
    fixture.detectChanges();

    expect(component.opportunityPreparing).toBe(false);
    expect(component.opportunityPreparationError).toBe(true);
  });

  it('ignores a late Trade Plan response after the market context changes', () => {
    const response = new Subject<{ tradePlanId: string; tradePlanVersion: number }>();
    tradePlanServiceMock.createFromOpportunity.mockReturnValue(response.asObservable());
    component.selectedAccountId = account.accountId;
    component.selectedMarketId = 'market-1';
    component.opportunity = {
      ...candidateLineage,
      id: 'opportunity-1',
      status: 'ACTIVE',
      validFrom: new Date(Date.now() - 60_000).toISOString(),
      validUntil: null,
    } as OpportunityResponse;

    component.prepareFromOpportunity();
    component.selectMarket('market-3', context);
    response.next({ tradePlanId: 'plan-1', tradePlanVersion: 1 });

    expect(component.opportunityPreparing).toBe(false);
    expect(routerMock.navigate).not.toHaveBeenCalledWith([
      '/trade-planning',
      'plans',
      'plan-1',
      'versions',
      1,
    ]);
  });

  it('cancels preparation when the account context changes', () => {
    const response = new Subject<{ tradePlanId: string; tradePlanVersion: number }>();
    tradePlanServiceMock.createFromOpportunity.mockReturnValue(response.asObservable());
    component.selectedAccountId = account.accountId;
    component.opportunity = {
      ...candidateLineage,
      id: 'opportunity-1',
      status: 'ACTIVE',
      validFrom: new Date(Date.now() - 60_000).toISOString(),
      validUntil: null,
    } as OpportunityResponse;

    component.prepareFromOpportunity();
    component.selectAccount('account-2');
    response.next({ tradePlanId: 'plan-1', tradePlanVersion: 1 });

    expect(component.opportunityPreparing).toBe(false);
    expect(routerMock.navigate).not.toHaveBeenCalledWith(
      expect.arrayContaining(['/trade-planning', 'plans']),
    );
  });

  it('cancels preparation when the Opportunity route context changes', () => {
    const response = new Subject<{ tradePlanId: string; tradePlanVersion: number }>();
    tradePlanServiceMock.createFromOpportunity.mockReturnValue(response.asObservable());
    component.selectedAccountId = account.accountId;
    component.opportunity = {
      ...candidateLineage,
      id: 'opportunity-1',
      status: 'ACTIVE',
      validFrom: new Date(Date.now() - 60_000).toISOString(),
      validUntil: null,
    } as OpportunityResponse;

    component.prepareFromOpportunity();
    opportunityServiceMock.findById.mockReturnValue(NEVER);
    routeQueryParamMap.next(convertToParamMap({ opportunityId: 'opportunity-2' }));
    response.next({ tradePlanId: 'plan-1', tradePlanVersion: 1 });

    expect(component.opportunityPreparing).toBe(false);
    expect(routerMock.navigate).not.toHaveBeenCalledWith(
      expect.arrayContaining(['/trade-planning', 'plans']),
    );
  });

  it('cancels preparation when the workspace is destroyed', () => {
    const response = new Subject<{ tradePlanId: string; tradePlanVersion: number }>();
    tradePlanServiceMock.createFromOpportunity.mockReturnValue(response.asObservable());
    component.selectedAccountId = account.accountId;
    component.opportunity = {
      ...candidateLineage,
      id: 'opportunity-1',
      status: 'ACTIVE',
      validFrom: new Date(Date.now() - 60_000).toISOString(),
      validUntil: null,
    } as OpportunityResponse;

    component.prepareFromOpportunity();
    fixture.destroy();
    response.next({ tradePlanId: 'plan-1', tradePlanVersion: 1 });

    expect(routerMock.navigate).not.toHaveBeenCalledWith(
      expect.arrayContaining(['/trade-planning', 'plans']),
    );
  });

  it('records NO TRADE only in the current workspace', () => {
    component.selectedAccountId = account.accountId;
    component.opportunity = {
      ...candidateLineage,
      id: 'opportunity-1',
      status: 'ACTIVE',
    } as OpportunityResponse;

    component.chooseNoTrade();

    expect(component.opportunityDecision).toBe('NO_TRADE');
    expect(tradePlanServiceMock.createFromOpportunity).not.toHaveBeenCalled();
    expect(routerMock.navigate).not.toHaveBeenCalled();
  });

  it('renders the evidence-led Opportunity decision flow', async () => {
    const assessment = {
      ...minimalAssessment('market-1', 'WATCH'),
      findings: [
        {
          code: 'SUPPORT-1',
          ruleId: 'rule-1',
          role: 'BIAS',
          message: 'Bias supports the setup.',
          evidence: [],
        },
      ],
      contradictions: [
        { code: 'CONFLICT-1', message: 'Trigger conflicts with bias.', evidence: [] },
      ],
      invalidations: [
        {
          ruleId: 'INVALIDATION-1',
          thesisDirection: 'UP',
          protectedLevel: null,
          candle: null,
          level: 98,
          evidence: null,
        },
      ],
    } as TrendContextAssessment;
    trendContextServiceMock.findTrendContext.mockReturnValue(
      of({
        ...unavailableTrendContext,
        operationalStatus: 'AVAILABLE',
        assessmentValidity: 'VALID',
        validUntil: new Date(Date.now() + 60_000).toISOString(),
        diagnostics: ['Feed arrived late.'],
        assessment,
      }),
    );
    component.opportunity = {
      ...candidateLineage,
      id: 'opportunity-1',
      version: 1,
      status: 'ACTIVE',
      instrument: 'BTC/USD',
      direction: 'LONG',
      scenario: 'Breakout continuation',
      timeframe: '1h',
      type: 'INTRADAY',
      origin: 'ACTIVE_SCAN',
      score: 0.82,
      explanation: 'Price holds above the protected level.',
      observationIds: ['observation-1'],
      aiAnalysisIds: [],
      evaluatedAt: new Date().toISOString(),
      validFrom: new Date(Date.now() - 60_000).toISOString(),
      validUntil: null,
      createdAt: new Date(Date.now() - 60_000).toISOString(),
      strategyMatchId: 'strategy-1',
      marketId: 'market-1',
      setup: {
        referencePrice: 100,
        referencePriceAt: new Date().toISOString(),
        description: 'Wait for continuation above the level.',
        triggers: [{ condition: 'Close above 100', observedValue: '101' }],
        detectedAt: new Date().toISOString(),
      },
    };
    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="opportunity-decision"]')).toBeTruthy();
    expect(element.textContent).toContain('Understand');
    expect(element.textContent).toContain('Challenge');
    expect(element.textContent).toContain('Breakout continuation');
    expect(element.textContent).toContain('Close above 100');
    expect(element.textContent).toContain('Bias supports the setup.');
    expect(element.textContent).toContain('Trigger conflicts with bias.');
    expect(element.textContent).toContain('INVALIDATION-1');
    expect(element.textContent).toContain('Feed arrived late.');

    (element.querySelector('[data-testid="no-trade"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="opportunity-no-trade"]')).toBeTruthy();
  });

  it('clears NO TRADE when the selected market changes', () => {
    component.opportunity = { id: 'opportunity-1' } as OpportunityResponse;
    component.opportunityDecision = 'NO_TRADE';

    component.selectMarket('market-1', context);

    expect(component.opportunityDecision).toBeNull();
  });

  it.each([
    { status: 'EXPIRED' as const, validUntil: null, expected: 'EXPIRED' as const },
    { status: 'DETECTED' as const, validUntil: null, expected: 'NON_ACTIONABLE' as const },
    { status: 'CONSUMED' as const, validUntil: null, expected: 'NON_ACTIONABLE' as const },
    {
      status: 'ACTIVE' as const,
      validFrom: new Date(Date.now() + 60_000).toISOString(),
      validUntil: null,
      expected: 'NOT_YET_VALID' as const,
    },
    {
      status: 'ACTIVE' as const,
      validFrom: 'not-a-timestamp',
      validUntil: null,
      expected: 'UNKNOWN' as const,
    },
  ])('does not prepare an $expected Opportunity', ({ status, validFrom, validUntil, expected }) => {
    component.selectedAccountId = account.accountId;
    component.opportunity = {
      id: 'opportunity-1',
      status,
      validFrom: validFrom ?? new Date(Date.now() - 60_000).toISOString(),
      validUntil: validUntil ?? null,
    } as OpportunityResponse;

    expect(component.opportunityValidity()).toBe(expected);
    component.prepareFromOpportunity();

    expect(tradePlanServiceMock.createFromOpportunity).not.toHaveBeenCalled();
  });

  it('treats an Opportunity at its validity boundary as expired', () => {
    component.opportunity = {
      id: 'opportunity-1',
      status: 'ACTIVE',
      validFrom: new Date(Date.now() - 60_000).toISOString(),
      validUntil: new Date(Date.now()).toISOString(),
    } as OpportunityResponse;

    expect(component.opportunityValidity()).toBe('EXPIRED');
    expect(component.canPrepareOpportunity()).toBe(false);
  });

  it('renders Opportunity loading state while the request is in flight', () => {
    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    (component as unknown as { opportunityRequestInFlight: boolean }).opportunityRequestInFlight =
      true;
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="opportunity-loading"]')).toBeTruthy();
  });

  it('renders an unavailable Opportunity state without exposing decision actions', () => {
    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    (component as unknown as { opportunityError: boolean }).opportunityError = true;
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="opportunity-error"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="prepare-from-opportunity"]'),
    ).toBeNull();
  });

  it('loads only the selected eligible market', () => {
    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);

    let status: string | null = null;
    component.marketView$.subscribe((current) => (status = current.status));

    expect(marketServiceMock.findById).toHaveBeenCalledWith('market-1');
    expect(status).toBe('loaded');
  });

  it('opens all market streams only after an eligible market is selected', () => {
    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    const subscriptions = [
      component.ticker$.subscribe(),
      component.ohlc$.subscribe(),
      component.orderBook$.subscribe(),
      component.recentTrades$.subscribe(),
    ];

    expect(marketServiceMock.subscribe).toHaveBeenCalledTimes(4);

    subscriptions.forEach((subscription) => subscription.unsubscribe());
  });

  it('renders the selected market facts and live market sections', async () => {
    const occurredAt = new Date().toISOString();
    const candle = {
      occurredAt,
      openTime: occurredAt,
      closeTime: occurredAt,
      open: 99,
      high: 102,
      low: 98,
      close: 101,
    };
    marketServiceMock.findOhlcHistory.mockReturnValue(of([candle]));
    marketDataStreamServiceMock.streamTicker.mockReturnValue(
      of({ last: 101, bid: 100, ask: 102, volume: 12, occurredAt }),
    );
    marketDataStreamServiceMock.streamOhlc.mockReturnValue(of(candle));
    marketDataStreamServiceMock.streamOrderBook.mockReturnValue(
      of({ occurredAt, bids: [], asks: [] }),
    );
    marketDataStreamServiceMock.streamRecentTrades.mockReturnValue(
      of({ generatedAt: occurredAt, trades: [] }),
    );

    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="market-context"]')).toBeTruthy();
    expect(element.textContent).toContain('BTC/USD');
    expect(element.textContent).toContain('LIVE');
    expect(element.textContent).toContain('Minimum order size');
    expect(element.textContent).toContain('101');
  });

  it('opens the manual ticket inside the selected market context', async () => {
    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    component.openManualTrade();
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="manual-trade-ticket"]')).toBeTruthy();
    expect(element.textContent).toContain('Paper account');
    expect(element.textContent).toContain('BTC/USD');
    expect(element.querySelector('[data-testid="manual-account-select"]')).toBeNull();
    expect(element.querySelector('[data-testid="manual-market-select"]')).toBeNull();
  });

  it('renders market-data errors without fabricating values', async () => {
    marketServiceMock.findOhlcHistory.mockReturnValue(
      throwError(() => new Error('history failed')),
    );
    marketDataStreamServiceMock.streamTicker.mockReturnValue(
      throwError(() => new Error('ticker failed')),
    );
    marketDataStreamServiceMock.streamOhlc.mockReturnValue(
      throwError(() => new Error('ohlc failed')),
    );
    marketDataStreamServiceMock.streamOrderBook.mockReturnValue(
      throwError(() => new Error('order book failed')),
    );
    marketDataStreamServiceMock.streamRecentTrades.mockReturnValue(
      throwError(() => new Error('trades failed')),
    );

    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="market-context"]')).toBeTruthy();
    expect(element.textContent).toContain('Ticker data unavailable.');
    expect(element.textContent).toContain('OHLC history unavailable.');
    expect(element.textContent).toContain('Order book unavailable.');
    expect(element.textContent).toContain('Recent trades unavailable.');
    expect(element.textContent).not.toContain('Last price101');
  });

  it('renders an unavailable market when market details cannot be loaded', async () => {
    marketServiceMock.findById.mockReturnValue(throwError(() => new Error('market failed')));

    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[data-testid="market-error"]')).toBeTruthy();
    expect(marketServiceMock.subscribe).not.toHaveBeenCalled();
  });

  it('does not open streams when the loaded market is no longer tradable', async () => {
    marketServiceMock.findById.mockReturnValueOnce(
      of({ ...market, marketState: { ...market.marketState, tradable: false } }),
    );

    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[data-testid="market-unavailable"]')).toBeTruthy();
    expect(marketServiceMock.subscribe).not.toHaveBeenCalled();
  });

  it('updates timeframe and order-book depth only when the value changes', () => {
    component.selectOhlcInterval({ label: '5m', minutes: 5, interval: OhlcInterval.FIVE_MINUTES });
    component.selectOhlcInterval({ label: '5m', minutes: 5, interval: OhlcInterval.FIVE_MINUTES });
    component.selectOrderBookDepth(25);
    component.selectOrderBookDepth(25);

    let timeframe: unknown;
    let depth: unknown;
    component.selectedTimeframe$.subscribe((value) => (timeframe = value));
    component.selectedOrderBookDepth$.subscribe((value) => (depth = value));

    expect(timeframe).toEqual({ label: '5m', minutes: 5, interval: OhlcInterval.FIVE_MINUTES });
    expect(depth).toBe(25);
  });

  it('does not load or subscribe to an ineligible market from the URL state', () => {
    routeQueryParamMap.next(
      convertToParamMap({ accountId: account.accountId, marketId: 'market-2' }),
    );
    let status: string | null = null;
    component.marketView$.subscribe((view) => (status = view.status));

    expect(marketServiceMock.findById).not.toHaveBeenCalled();
    expect(marketServiceMock.subscribe).not.toHaveBeenCalled();
    expect(status).toBe('ineligible');
  });

  it('keeps ineligible markets distinct from Market Data tradability', async () => {
    routeQueryParamMap.next(
      convertToParamMap({ accountId: account.accountId, marketId: 'market-2' }),
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Not eligible');
    expect(text).not.toContain('Market Data reports that this market cannot be used now.');
    expect(text).toContain('Select market');
  });

  it('restores an eligible market from URL context after account resolution', async () => {
    routeQueryParamMap.next(
      convertToParamMap({ accountId: account.accountId, marketId: 'market-1' }),
    );
    fixture.detectChanges();
    await fixture.whenStable();

    expect(contextServiceMock.resolve).toHaveBeenCalledWith(account.accountId);
    expect(component.selectedAccountId).toBe(account.accountId);
    expect(component.selectedMarketId).toBe('market-1');
    expect(marketServiceMock.findById).toHaveBeenCalledWith('market-1');
  });

  it('preserves a market-first market when an account is selected', () => {
    component.selectedMarketId = 'market-1';

    component.selectAccount(account.accountId);

    expect(component.selectedMarketId).toBe('market-1');
    expect(routerMock.navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: expect.objectContaining({
          accountId: account.accountId,
          marketId: 'market-1',
        }),
      }),
    );
  });

  it('preserves Opportunity context when an account is selected from an opportunity entry', () => {
    component.selectedMarketId = 'market-1';
    component.opportunity = { id: 'opportunity-1' } as OpportunityResponse;

    component.selectAccount(account.accountId);

    expect(component.opportunity).toEqual({ id: 'opportunity-1' });
    expect(routerMock.navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: expect.objectContaining({
          accountId: account.accountId,
          marketId: 'market-1',
          opportunityId: 'opportunity-1',
        }),
      }),
    );
  });

  it('preserves opportunity context when the selected account changes', () => {
    component.selectedAccountId = account.accountId;
    component.opportunity = { id: 'opportunity-1' } as OpportunityResponse;

    component.selectAccount('account-2');

    expect(component.opportunity).toEqual({ id: 'opportunity-1' });
    expect(component.opportunityError).toBe(false);
  });

  it('preserves a pending opportunity response after the URL account changes', () => {
    const response = new Subject<OpportunityResponse>();
    opportunityServiceMock.findById.mockReturnValue(response.asObservable());

    routeQueryParamMap.next(
      convertToParamMap({ accountId: account.accountId, opportunityId: 'opportunity-1' }),
    );
    routeQueryParamMap.next(
      convertToParamMap({ accountId: 'account-2', opportunityId: 'opportunity-1' }),
    );

    response.next({ id: 'opportunity-1', marketId: 'market-1' } as OpportunityResponse);

    expect(component.opportunity).toEqual({ id: 'opportunity-1', marketId: 'market-1' });
    expect(component.opportunityError).toBe(false);
  });

  it('clears projected Opportunity context while a new Opportunity is loading', () => {
    const response = new Subject<OpportunityResponse>();
    opportunityServiceMock.findById.mockReturnValue(response.asObservable());

    routeQueryParamMap.next(convertToParamMap({ opportunityId: 'opportunity-1' }));
    response.next({ id: 'opportunity-1' } as OpportunityResponse);
    fixture.detectChanges();

    routeQueryParamMap.next(convertToParamMap({ opportunityId: 'opportunity-2' }));
    fixture.detectChanges();

    expect(component.opportunity).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('opportunity-1');
  });

  it('cancels a pending Opportunity request when context changes', () => {
    let cancellations = 0;
    opportunityServiceMock.findById.mockImplementation(
      () => new Observable<OpportunityResponse>(() => () => (cancellations += 1)),
    );

    routeQueryParamMap.next(convertToParamMap({ opportunityId: 'opportunity-1' }));
    routeQueryParamMap.next(
      convertToParamMap({ accountId: account.accountId, opportunityId: 'opportunity-1' }),
    );

    expect(opportunityServiceMock.findById).toHaveBeenCalledTimes(1);
    expect(cancellations).toBe(0);
  });

  it('does not project an opportunity onto a different market context', () => {
    const response = new Subject<OpportunityResponse>();
    opportunityServiceMock.findById.mockReturnValue(response.asObservable());

    routeQueryParamMap.next(
      convertToParamMap({ marketId: 'market-1', opportunityId: 'opportunity-1' }),
    );
    response.next({ id: 'opportunity-1', marketId: 'market-1' } as OpportunityResponse);
    expect(component.opportunity?.marketId).toBe('market-1');

    routeQueryParamMap.next(
      convertToParamMap({ marketId: 'market-3', opportunityId: 'opportunity-1' }),
    );

    expect(component.opportunity).toBeNull();
    expect(component.opportunityError).toBe(true);
  });

  it('keeps the resolved account context selected while the account catalogue reloads', () => {
    accountServiceMock.getAccounts.mockReturnValue(NEVER);
    component.selectAccount(account.accountId);
    (
      component as unknown as { accountsRefreshSubject: Subject<void> }
    ).accountsRefreshSubject.next();
    fixture.detectChanges();

    const context = fixture.nativeElement.querySelector('[data-testid="workspace-context"]');
    expect(context.textContent).toContain('Paper account');
    expect(context.textContent).not.toContain('Loading account context');
  });

  it('unsubscribes the previous market streams when switching markets', () => {
    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    const subscription = component.ticker$.subscribe();

    component.selectMarket('market-3', context);

    expect(marketServiceMock.unsubscribe).toHaveBeenCalled();
    subscription.unsubscribe();
  });

  it('unsubscribes market streams when the account changes', () => {
    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    const subscription = component.ticker$.subscribe();

    component.selectAccount('account-2');

    expect(marketServiceMock.unsubscribe).toHaveBeenCalled();
    subscription.unsubscribe();
  });

  it('unsubscribes market streams when the URL account changes', () => {
    routeQueryParamMap.next(
      convertToParamMap({ accountId: account.accountId, marketId: 'market-1' }),
    );
    const subscription = component.ticker$.subscribe();
    marketServiceMock.unsubscribe.mockClear();
    const cleanup = new Subject<void>();
    marketServiceMock.unsubscribe.mockReturnValue(cleanup);
    contextServiceMock.resolve.mockClear();

    routeQueryParamMap.next(convertToParamMap({ accountId: 'account-2', marketId: 'market-1' }));

    expect(marketServiceMock.unsubscribe).toHaveBeenCalled();
    expect(component.selectedMarketId).toBeNull();
    expect(contextServiceMock.resolve).not.toHaveBeenCalledWith('account-2');
    cleanup.next();
    cleanup.complete();
    expect(contextServiceMock.resolve).toHaveBeenCalledWith('account-2');
    subscription.unsubscribe();
  });

  it('waits for active stream cleanup before activating replacement streams', () => {
    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    const subscriptions = [
      component.ticker$.subscribe(),
      component.ohlc$.subscribe(),
      component.orderBook$.subscribe(),
      component.recentTrades$.subscribe(),
    ];
    const cleanup = new BehaviorSubject<void>(undefined);
    marketServiceMock.unsubscribe.mockReset();
    marketServiceMock.unsubscribe.mockReturnValue(cleanup);
    marketServiceMock.subscribe.mockClear();

    component.selectMarket('market-3', context);

    expect(marketServiceMock.subscribe).not.toHaveBeenCalled();
    cleanup.complete();
    expect(marketServiceMock.subscribe).toHaveBeenCalledTimes(4);
    subscriptions.forEach((subscription) => subscription.unsubscribe());
  });

  it('unsubscribes market streams when the URL removes the selected market', () => {
    routeQueryParamMap.next(
      convertToParamMap({ accountId: account.accountId, marketId: 'market-1' }),
    );
    const subscription = component.ticker$.subscribe();
    marketServiceMock.unsubscribe.mockClear();

    routeQueryParamMap.next(convertToParamMap({ accountId: account.accountId }));

    expect(marketServiceMock.unsubscribe).toHaveBeenCalled();
    subscription.unsubscribe();
  });

  it('classifies market freshness from provider timestamps', () => {
    expect(component.marketFreshness({ status: 'waiting' }, null)).toBe('UNAVAILABLE');
    expect(component.marketFreshness({ status: 'live', data: {} }, new Date().toISOString())).toBe(
      'LIVE',
    );
    expect(
      component.marketFreshness(
        { status: 'live', data: {} },
        new Date(Date.now() - 30_000).toISOString(),
      ),
    ).toBe('RECENT');
    expect(
      component.marketFreshness(
        { status: 'live', data: {} },
        new Date(Date.now() - 120_000).toISOString(),
      ),
    ).toBe('STALE');
  });

  it('exposes a periodic freshness projection for silent streams', () => {
    expect(component.freshnessTick$).toBeTruthy();
    expect(
      component.marketFreshness(
        { status: 'live', data: {} },
        new Date(Date.now() - 61_000).toISOString(),
      ),
    ).toBe('STALE');
  });

  it('does not expose a stale ticker as the manual reference price', () => {
    expect(
      component.manualReferencePrice({
        status: 'live',
        data: {
          marketId: 'market-1',
          provider: 'KRAKEN',
          symbol: 'BTC/USD',
          streamType: 'TICKER',
          bid: 100,
          ask: 101,
          last: 100.5,
          volume: 10,
          occurredAt: new Date(Date.now() - 120_000).toISOString(),
        },
      }),
    ).toBeNull();
  });

  it('renders a context error without fabricating markets', async () => {
    contextServiceMock.resolve.mockReturnValueOnce(throwError(() => new Error('unavailable')));

    component.selectAccount(account.accountId);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[data-testid="context-error"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="eligible-markets"]')).toBeNull();
  });

  it('does not request Trend Context before an eligible market is selected', () => {
    expect(trendContextServiceMock.findTrendContext).not.toHaveBeenCalled();

    component.selectAccount(account.accountId);
    component.selectMarket('market-2', context);

    expect(trendContextServiceMock.findTrendContext).not.toHaveBeenCalled();
  });

  it('requests Trend Context for the selected eligible market', async () => {
    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(trendContextServiceMock.findTrendContext).toHaveBeenCalledWith('market-1');
    expect(
      fixture.nativeElement.querySelector('[data-testid="trend-context-no-assessment"]'),
    ).toBeTruthy();
  });

  it('clears the assessment while changing markets', async () => {
    trendContextServiceMock.findTrendContext.mockReturnValue(
      of({
        ...unavailableTrendContext,
        operationalStatus: 'AVAILABLE',
        assessmentPresent: true,
        assessmentValidity: 'VALID',
        assessment: minimalAssessment('market-1', 'CONTEXTUALLY_ATTRACTIVE'),
      }),
    );

    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(
      fixture.nativeElement.querySelector('[data-testid="trend-context-assessment"]'),
    ).toBeTruthy();

    component.selectMarket('market-3', context);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="trend-context-assessment"]'),
    ).toBeNull();
  });

  it('renders backend operational and analytical states without translating them', async () => {
    trendContextServiceMock.findTrendContext.mockReturnValue(
      of({
        ...unavailableTrendContext,
        operationalStatus: 'STALE',
        assessmentValidity: 'HISTORICAL',
        lastSuccessfulAssessment: minimalAssessment('market-1', 'NO_SETUP'),
      }),
    );

    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="trend-context-status"]')?.textContent).toContain(
      'STALE',
    );
    expect(
      element.querySelector('[data-testid="trend-context-historical"]')?.textContent,
    ).toContain('NO_SETUP');
    expect(
      element.querySelector('[data-testid="trend-context-historical-provenance"]')?.textContent,
    ).toContain('Profile');
  });

  it.each([
    'NO_SETUP',
    'WATCH',
    'UNKNOWN',
    'CONTEXTUALLY_ATTRACTIVE',
    'CONTEXTUALLY_DANGEROUS',
  ] as const)('preserves the analytical attention outcome %s', async (attention) => {
    trendContextServiceMock.findTrendContext.mockReturnValue(
      of({
        ...unavailableTrendContext,
        operationalStatus: 'AVAILABLE',
        assessmentPresent: true,
        assessmentValidity: 'VALID',
        assessment: minimalAssessment('market-1', attention),
      }),
    );

    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain(attention);
  });

  it('renders request failure as unavailable rather than an analytical outcome', async () => {
    trendContextServiceMock.findTrendContext.mockReturnValue(
      throwError(() => new Error('trend context failed')),
    );

    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(
      fixture.nativeElement.querySelector('[data-testid="trend-context-unavailable"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.textContent).not.toContain('NO_SETUP');
    expect(fixture.nativeElement.textContent).not.toContain('UNKNOWN');
  });

  it('keeps a favorable assessment analytical and preserves the manual ticket workflow', async () => {
    trendContextServiceMock.findTrendContext.mockReturnValue(
      of({
        ...unavailableTrendContext,
        operationalStatus: 'AVAILABLE',
        assessmentPresent: true,
        assessmentValidity: 'VALID',
        assessment: minimalAssessment('market-1', 'CONTEXTUALLY_ATTRACTIVE'),
      }),
    );

    component.selectAccount(account.accountId);
    component.selectMarket('market-1', context);
    fixture.detectChanges();
    await fixture.whenStable();
    component.openManualTrade();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="trend-context-panel"]')).toBeTruthy();
    expect(component.manualTradeOpen).toBe(true);
    expect(element.textContent).toContain('Analytical evidence only');
    expect(element.textContent).toContain('Not Risk approval');
    expect(element.textContent).toContain('Not execution authorization');
    expect(element.querySelector('.trend-context-details .disclosure-section__content')).toBeNull();

    (
      element.querySelector('.trend-context-details .disclosure-button') as HTMLButtonElement
    ).click();
    fixture.detectChanges();

    expect(
      element.querySelector('.trend-context-details .disclosure-section__content'),
    ).toBeTruthy();
    expect(element.textContent).toContain('No optional trigger assessment was provided.');
  });
});

function minimalAssessment(marketId: string, attention: TrendAttention) {
  return {
    marketId,
    provider: 'KRAKEN',
    symbol: 'BTC/USD',
    assessmentAt: '2026-09-20T10:00:00Z',
    cutOffAt: '2026-09-20T09:55:00Z',
    inputFingerprint: 'input-fingerprint',
    profileId: 'CONSERVATIVE',
    profileVersion: '1.0.0',
    ruleVersion: '1.0.0',
    timeframes: {},
    alignment: 'INSUFFICIENT_DIRECTION' as const,
    direction: 'UNKNOWN' as const,
    regime: 'UNKNOWN' as const,
    phase: 'UNDETERMINED' as const,
    attention,
    findings: [],
    contradictions: [],
    exclusions: [],
    invalidations: [],
    fingerprint: 'assessment-fingerprint',
  };
}

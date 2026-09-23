// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { BehaviorSubject, NEVER, of, throwError } from 'rxjs';

import { Account } from '../../core/models/account.model';
import { DecisionContextResponse } from '../../core/models/decision-context.model';
import { AccountService } from '../../core/services/account.service';
import { DecisionContextService } from '../../core/services/decision-context.service';
import { MarketDataStreamService } from '../../core/services/market-data-stream.service';
import { MarketService } from '../../core/services/market.service';
import { TradePlanService } from '../../core/services/trade-plan.service';
import { OhlcInterval } from '../../core/models/ohlc-interval';
import { DecisionWorkspace } from './decision-workspace';

describe('DecisionWorkspace', () => {
  let fixture: ComponentFixture<DecisionWorkspace>;
  let component: DecisionWorkspace;
  let accountServiceMock: { getAccounts: ReturnType<typeof vi.fn> };
  let contextServiceMock: { resolve: ReturnType<typeof vi.fn> };
  let marketServiceMock: {
    findById: ReturnType<typeof vi.fn>;
    findOhlcHistory: ReturnType<typeof vi.fn>;
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
  let routeQueryParamMap: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

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

  beforeEach(async () => {
    accountServiceMock = { getAccounts: vi.fn().mockReturnValue(of([account])) };
    contextServiceMock = { resolve: vi.fn().mockReturnValue(of(context)) };
    marketServiceMock = {
      findById: vi.fn().mockReturnValue(of(market)),
      findOhlcHistory: vi.fn().mockReturnValue(of([])),
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
        { provide: DecisionContextService, useValue: contextServiceMock },
        { provide: MarketService, useValue: marketServiceMock },
        { provide: MarketDataStreamService, useValue: marketDataStreamServiceMock },
        { provide: TradePlanService, useValue: { createManual: vi.fn() } },
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
    expect(fixture.nativeElement.querySelector('[data-testid="excluded-markets"]')).toBeTruthy();
  });

  it('clears the selected market when the account changes', () => {
    component.selectedMarketId = 'market-1';

    component.selectAccount('account-2');

    expect(component.selectedMarketId).toBeNull();
  });

  it('does not select an excluded market', () => {
    component.selectMarket('market-2', context);

    expect(component.selectedMarketId).toBeNull();
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

  it('renders a context error without fabricating markets', async () => {
    contextServiceMock.resolve.mockReturnValueOnce(throwError(() => new Error('unavailable')));

    component.selectAccount(account.accountId);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[data-testid="context-error"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="eligible-markets"]')).toBeNull();
  });
});

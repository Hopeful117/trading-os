import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { convertToParamMap, ActivatedRoute } from '@angular/router';
import { NEVER, of, Subject, ReplaySubject } from 'rxjs';

import { MarketDataStreamService } from '../../../core/services/market-data-stream.service';
import { MarketService } from '../../../core/services/market.service';
import { MarketDetail } from './markets-details';
import { MarketChartComponent } from '../market-chart-component/market-chart-component';
import { OrderBookComponent } from '../order-book-component/order-book-component';
import { RecentTradesComponent } from '../recent-trades-component/recent-trades-component';
import { MarketResponse } from '../../../core/models/market-response';
import { OhlcInterval } from '../../../core/models/ohlc-interval';

@Component({ selector: 'app-market-chart', template: '', standalone: true })
class MockMarketChartComponent {
  history = input([]);
  liveCandle = input(null);
  resetKey = input(0);
}

@Component({ selector: 'app-order-book', template: '', standalone: true })
class MockOrderBookComponent {
  snapshot = input<any>(null);
}

@Component({ selector: 'app-recent-trades', template: '', standalone: true })
class MockRecentTradesComponent {
  snapshot = input<any>(null);
}

describe('MarketsDetails', () => {
  let component: MarketDetail;
  let fixture: ComponentFixture<MarketDetail>;
  let paramMapSubject: Subject<any>;
  let marketServiceMock: any;
  let streamServiceMock: any;

  const mockMarket: MarketResponse = {
    marketId: 'mkt-1',
    provider: 'KRAKEN',
    symbol: 'BTC/USD',
    baseAsset: 'BTC',
    quoteAsset: 'USD',
    marketState: {
      tradingStatus: 'OPEN',
      tradable: true,
      closureReason: '',
      lastUpdated: '2025-01-01T00:00:00Z',
    },
    marketConstraints: {
      minimumOrderSize: 0.0001,
      minimumCost: 5,
      tickSize: 0.01,
      quantityPrecision: 8,
      pricePrecision: 2,
    },
  };

  function createActivatedRoute(marketId: string | null) {
    paramMapSubject = new ReplaySubject(1);
    paramMapSubject.next(convertToParamMap(marketId ? { marketId } : {}));
    return { paramMap: paramMapSubject.asObservable() };
  }

  function createMarketService(overrides: Partial<typeof marketServiceMock> = {}) {
    return {
      findById: vi.fn().mockReturnValue(of(mockMarket)),
      subscribe: vi.fn().mockReturnValue(of(undefined)),
      unsubscribe: vi.fn().mockReturnValue(of(undefined)),
      findOhlcHistory: vi.fn().mockReturnValue(of([])),
      ...overrides,
    };
  }

  function createStreamService(overrides: Partial<typeof streamServiceMock> = {}) {
    return {
      streamTicker: vi.fn().mockReturnValue(NEVER),
      streamOhlc: vi.fn().mockReturnValue(NEVER),
      streamOrderBook: vi.fn().mockReturnValue(NEVER),
      streamRecentTrades: vi.fn().mockReturnValue(NEVER),
      ...overrides,
    };
  }

  async function setup(marketId: string | null = 'mkt-1') {
    marketServiceMock = createMarketService();
    streamServiceMock = createStreamService();

    await TestBed.configureTestingModule({
      imports: [
        MockMarketChartComponent,
        MockOrderBookComponent,
        MockRecentTradesComponent,
        MarketDetail,
      ],
      providers: [
        { provide: ActivatedRoute, useValue: createActivatedRoute(marketId) },
        { provide: MarketService, useValue: marketServiceMock },
        { provide: MarketDataStreamService, useValue: streamServiceMock },
      ],
    })
      .overrideComponent(MarketDetail, {
        remove: { imports: [MarketChartComponent, OrderBookComponent, RecentTradesComponent] },
        add: {
          imports: [MockMarketChartComponent, MockOrderBookComponent, MockRecentTradesComponent],
        },
      })
      .compileComponents();

    fixture = TestBed.createComponent(MarketDetail);
    component = fixture.componentInstance;
    await fixture.whenStable();
  }

  it('should create', async () => {
    await setup();
    expect(component).toBeTruthy();
  });

  it('should load market on init when marketId is present', async () => {
    await setup('mkt-1');
    let market: MarketResponse | undefined;
    component.market$.subscribe((m) => (market = m));

    expect(marketServiceMock.findById).toHaveBeenCalledWith('mkt-1');
    expect(market).toEqual(mockMarket);
  });

  it('should not call findById when no marketId in route', async () => {
    await setup(null);
    expect(marketServiceMock.findById).not.toHaveBeenCalled();
  });

  describe('timeframe selection', () => {
    it('should default to 5m timeframe (index 1)', async () => {
      await setup();
      let selected: any;
      component.selectedTimeframe$.subscribe((tf) => (selected = tf));
      expect(selected.label).toBe('5m');
      expect(selected.minutes).toBe(5);
    });

    it('should update state when selecting a different timeframe', async () => {
      await setup();
      let selected: any;
      component.selectedTimeframe$.subscribe((tf) => (selected = tf));

      const oneHourTimeframe = component.ohlcIntervals.find((t) => t.minutes === 60)!;
      component.selectOhlcInterval(oneHourTimeframe);

      expect(selected.minutes).toBe(60);
      expect(selected.label).toBe('1h');
    });

    it('should ignore selecting the same timeframe', async () => {
      await setup();
      const defaultTimeframe = component.ohlcIntervals[1];

      component.selectOhlcInterval(defaultTimeframe);

      let selected: any;
      component.selectedTimeframe$.subscribe((tf) => (selected = tf));
      expect(selected.minutes).toBe(5);
    });

    it('should emit chartReset$ when timeframe changes', async () => {
      await setup();
      const emissions: number[] = [];
      component.chartReset$.subscribe((v) => emissions.push(v));

      component.ohlc$.subscribe();
      await fixture.whenStable();

      const oneHourTimeframe = component.ohlcIntervals.find((t) => t.minutes === 60)!;
      component.selectOhlcInterval(oneHourTimeframe);
      await fixture.whenStable();

      expect(emissions.length).toBeGreaterThan(0);
    });

    it('should expose all timeframe options', async () => {
      await setup();
      expect(component.ohlcIntervals.length).toBe(7);
      expect(component.ohlcIntervals.map((t) => t.label)).toEqual([
        '1m',
        '5m',
        '15m',
        '30m',
        '1h',
        '4h',
        '1d',
      ]);
    });
  });

  describe('order book depth selection', () => {
    it('should default to depth 10', async () => {
      await setup();
      let selected: number | undefined;
      component.selectedOrderBookDepth$.subscribe((d) => (selected = d));
      expect(selected).toBe(10);
    });

    it('should update state when selecting a different depth', async () => {
      await setup();
      let selected: number | undefined;
      component.selectedOrderBookDepth$.subscribe((d) => (selected = d));

      component.selectOrderBookDepth(25);

      expect(selected).toBe(25);
    });

    it('should ignore selecting the same depth', async () => {
      await setup();
      const emissions: number[] = [];
      component.selectedOrderBookDepth$.subscribe((d) => emissions.push(d));

      component.selectOrderBookDepth(10);

      expect(emissions.length).toBe(1);
    });

    it('should expose available depth options', async () => {
      await setup();
      expect(component.orderBookDepths).toEqual([10, 25]);
    });
  });

  describe('cleanup on destroy', () => {
    it('should unsubscribe ticker on destroy when active', async () => {
      await setup('mkt-1');
      let market: any;
      component.market$.subscribe((m) => (market = m));
      await fixture.whenStable();

      (component as any).activeTickerSubscription = { marketId: 'mkt-1' };

      fixture.destroy();

      expect(marketServiceMock.unsubscribe).toHaveBeenCalledWith(
        'mkt-1',
        expect.objectContaining({ type: 'TICKER' }),
      );
    });

    it('should unsubscribe ohlc on destroy when active', async () => {
      await setup('mkt-1');
      let market: any;
      component.market$.subscribe((m) => (market = m));
      await fixture.whenStable();

      (component as any).activeOhlcSubscription = {
        marketId: 'mkt-1',
        request: { type: 'OHLC', parameters: { interval: 5, depth: 0 } },
      };

      fixture.destroy();

      expect(marketServiceMock.unsubscribe).toHaveBeenCalledWith(
        'mkt-1',
        expect.objectContaining({ type: 'OHLC' }),
      );
    });

    it('should unsubscribe order book on destroy when active', async () => {
      await setup('mkt-1');
      let market: any;
      component.market$.subscribe((m) => (market = m));
      await fixture.whenStable();

      (component as any).activeOrderBookSubscription = {
        marketId: 'mkt-1',
        request: { type: 'ORDER_BOOK', parameters: { interval: null, depth: 10 } },
      };

      fixture.destroy();

      expect(marketServiceMock.unsubscribe).toHaveBeenCalledWith(
        'mkt-1',
        expect.objectContaining({ type: 'ORDER_BOOK' }),
      );
    });

    it('should unsubscribe recent trades on destroy when active', async () => {
      await setup('mkt-1');
      let market: any;
      component.market$.subscribe((m) => (market = m));
      await fixture.whenStable();

      (component as any).activeRecentTradesSubscription = { marketId: 'mkt-1' };

      fixture.destroy();

      expect(marketServiceMock.unsubscribe).toHaveBeenCalledWith(
        'mkt-1',
        expect.objectContaining({ type: 'TRADES' }),
      );
    });

    it('should not attempt unsubscribe when no active subscriptions', async () => {
      await setup(null);
      fixture.destroy();

      expect(marketServiceMock.unsubscribe).not.toHaveBeenCalled();
    });
  });

  describe('stream subscriptions', () => {
    it('should subscribe to market streams on market load', async () => {
      await setup('mkt-1');
      let market: any;
      component.market$.subscribe((m) => (market = m));
      await fixture.whenStable();

      expect(marketServiceMock.subscribe).toHaveBeenCalledWith(
        'mkt-1',
        expect.objectContaining({ type: 'TICKER' }),
      );
      expect(streamServiceMock.streamTicker).toHaveBeenCalledWith('BTC/USD');
    });

    it('should call findOhlcHistory with market and timeframe', async () => {
      await setup('mkt-1');
      component.ohlcHistory$.subscribe();
      await fixture.whenStable();

      expect(marketServiceMock.findOhlcHistory).toHaveBeenCalledWith(
        'mkt-1',
        OhlcInterval.FIVE_MINUTES,
        200,
      );
    });
  });
});

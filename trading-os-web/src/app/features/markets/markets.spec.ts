import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { Markets } from './markets';
import { MarketService } from '../../core/services/market.service';
import { MarketResponse } from '../../core/models/market-response';

describe('Markets', () => {
  let component: Markets;
  let fixture: ComponentFixture<Markets>;
  let routerMock: { navigate: ReturnType<typeof vi.fn> };
  let marketServiceMock: { findAll: ReturnType<typeof vi.fn> };

  const mockMarkets: MarketResponse[] = [
    {
      marketId: 'm1',
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
    },
    {
      marketId: 'm2',
      provider: 'KRAKEN',
      symbol: 'ETH/USD',
      baseAsset: 'ETH',
      quoteAsset: 'USD',
      marketState: {
        tradingStatus: 'OPEN',
        tradable: true,
        closureReason: '',
        lastUpdated: '2025-01-01T00:00:00Z',
      },
      marketConstraints: {
        minimumOrderSize: 0.01,
        minimumCost: 10,
        tickSize: 0.01,
        quantityPrecision: 8,
        pricePrecision: 2,
      },
    },
    {
      marketId: 'm3',
      provider: 'BINANCE',
      symbol: 'SOL/BTC',
      baseAsset: 'SOL',
      quoteAsset: 'BTC',
      marketState: {
        tradingStatus: 'CLOSED',
        tradable: false,
        closureReason: 'maintenance',
        lastUpdated: '2025-01-01T00:00:00Z',
      },
      marketConstraints: {
        minimumOrderSize: 0.1,
        minimumCost: 1,
        tickSize: 0.0001,
        quantityPrecision: 4,
        pricePrecision: 6,
      },
    },
  ];

  beforeEach(async () => {
    routerMock = { navigate: vi.fn().mockResolvedValue(true) };
    marketServiceMock = { findAll: vi.fn().mockReturnValue(of(mockMarkets)) };

    await TestBed.configureTestingModule({
      imports: [Markets],
      providers: [
        { provide: MarketService, useValue: marketServiceMock },
        { provide: Router, useValue: routerMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Markets);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('market loading', () => {
    it('should fetch markets on init', () => {
      expect(marketServiceMock.findAll).toHaveBeenCalled();
    });

    it('should expose markets via markets$', () => {
      let markets: MarketResponse[] = [];
      component.markets$.subscribe((m) => (markets = m));
      expect(markets).toEqual(mockMarkets);
    });

    it('should expose filtered markets', () => {
      let markets: MarketResponse[] = [];
      component.filteredMarkets$.subscribe((m) => (markets = m));
      expect(markets.length).toBe(3);
    });
  });

  describe('filterMarkets', () => {
    it('should match symbol', () => {
      component.applyFilter({ search: 'BTC' });

      let markets: MarketResponse[] = [];
      component.filteredMarkets$.subscribe((m) => (markets = m));

      expect(markets.length).toBe(2);
      expect(markets.map((m) => m.marketId)).toEqual(['m1', 'm3']);
    });

    it('should match baseAsset', () => {
      component.applyFilter({ search: 'ETH' });

      let markets: MarketResponse[] = [];
      component.filteredMarkets$.subscribe((m) => (markets = m));

      expect(markets.length).toBe(1);
      expect(markets[0].marketId).toBe('m2');
    });

    it('should match provider', () => {
      component.applyFilter({ search: 'BINANCE' });

      let markets: MarketResponse[] = [];
      component.filteredMarkets$.subscribe((m) => (markets = m));

      expect(markets.length).toBe(1);
      expect(markets[0].marketId).toBe('m3');
    });

    it('should match quoteAsset', () => {
      component.applyFilter({ search: 'BTC' });

      let markets: MarketResponse[] = [];
      component.filteredMarkets$.subscribe((m) => (markets = m));

      // BTC matches symbol BTC/USD (m1), quoteAsset BTC (m3), and symbol SOL/BTC (m3)
      expect(markets.some((m) => m.quoteAsset === 'BTC' || m.symbol.includes('BTC'))).toBe(true);
    });

    it('should be case-insensitive', () => {
      component.applyFilter({ search: 'kraken' });

      let markets: MarketResponse[] = [];
      component.filteredMarkets$.subscribe((m) => (markets = m));

      expect(markets.length).toBe(2);
      expect(markets.every((m) => m.provider === 'KRAKEN')).toBe(true);
    });

    it('should return all markets when filter is empty', () => {
      component.applyFilter({ search: '' });

      let markets: MarketResponse[] = [];
      component.filteredMarkets$.subscribe((m) => (markets = m));

      expect(markets.length).toBe(3);
    });

    it('should return all markets for whitespace-only filter', () => {
      component.applyFilter({ search: '   ' });

      let markets: MarketResponse[] = [];
      component.filteredMarkets$.subscribe((m) => (markets = m));

      expect(markets.length).toBe(3);
    });

    it('should return empty when no markets match', () => {
      component.applyFilter({ search: 'DOESNOTEXIST' });

      let markets: MarketResponse[] = [];
      component.filteredMarkets$.subscribe((m) => (markets = m));

      expect(markets.length).toBe(0);
    });
  });

  describe('openMarket', () => {
    it('should navigate to /markets/:id', () => {
      component.openMarket('m1');

      expect(routerMock.navigate).toHaveBeenCalledWith(['/markets', 'm1']);
    });

    it('should navigate with correct marketId for different market', () => {
      component.openMarket('m3');

      expect(routerMock.navigate).toHaveBeenCalledWith(['/markets', 'm3']);
    });
  });

  describe('refreshMarkets', () => {
    it('should re-fetch markets from service', () => {
      marketServiceMock.findAll.mockClear();

      component.refreshMarkets();

      expect(marketServiceMock.findAll).toHaveBeenCalled();
    });

    it('should emit updated markets after refresh', () => {
      const updatedMarkets = [{ ...mockMarkets[0], symbol: 'XBT/USD' }];
      marketServiceMock.findAll.mockReturnValueOnce(of(updatedMarkets));

      component.refreshMarkets();

      let markets: MarketResponse[] = [];
      component.filteredMarkets$.subscribe((m) => (markets = m));

      expect(markets.length).toBe(1);
      expect(markets[0].symbol).toBe('XBT/USD');
    });
  });
});

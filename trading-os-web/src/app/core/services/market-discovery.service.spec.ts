import { MarketDiscoveryService } from './market-discovery.service';
import { MarketResponse } from '../models/market-response';

describe('MarketDiscoveryService', () => {
  const markets: MarketResponse[] = [
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
        lastUpdated: '2026-01-01T00:00:00Z',
      },
      marketConstraints: null as unknown as MarketResponse['marketConstraints'],
    },
    {
      marketId: 'm1',
      provider: 'BINANCE',
      symbol: 'BTC/USD',
      baseAsset: 'BTC',
      quoteAsset: 'USD',
      marketState: {
        tradingStatus: 'OPEN',
        tradable: true,
        closureReason: '',
        lastUpdated: '2026-01-01T00:00:00Z',
      },
      marketConstraints: null as unknown as MarketResponse['marketConstraints'],
    },
    {
      marketId: 'm3',
      provider: 'KRAKEN',
      symbol: 'SOL/BTC',
      baseAsset: 'SOL',
      quoteAsset: 'BTC',
      marketState: {
        tradingStatus: 'CLOSED',
        tradable: false,
        closureReason: 'maintenance',
        lastUpdated: '2026-01-01T00:00:00Z',
      },
      marketConstraints: null as unknown as MarketResponse['marketConstraints'],
    },
  ];

  let service: MarketDiscoveryService;

  beforeEach(() => {
    service = new MarketDiscoveryService();
  });

  it('applies text, provider, base, quote, status, and tradability filters with AND semantics', () => {
    const result = service.filterAndSort(markets, {
      search: 'btc',
      provider: 'BINANCE',
      baseAsset: 'BTC',
      quoteAsset: 'USD',
      tradingStatus: 'OPEN',
      tradable: true,
    });

    expect(result.map((market) => market.marketId)).toEqual(['m1']);
  });

  it('matches optional nullable identity values safely', () => {
    expect(service.matchesSearch({ symbol: null, provider: 'KRAKEN' }, 'kraken')).toBe(true);
    expect(service.matchesSearch({ symbol: null, provider: null }, 'btc')).toBe(false);
  });

  it('sorts deterministically and uses market id as a tie-breaker', () => {
    const tied = [markets[0], { ...markets[0], marketId: 'm0' }];

    expect(service.sortMarkets(tied).map((market) => market.marketId)).toEqual(['m0', 'm2']);
    expect(
      service
        .sortMarkets(markets, { field: 'PROVIDER', direction: 'DESC' })
        .map((market) => market.marketId),
    ).toEqual(['m2', 'm3', 'm1']);
  });

  it('does not mutate the catalogue input', () => {
    const source = [...markets];

    service.filterAndSort(source, { search: '' }, { field: 'SYMBOL', direction: 'DESC' });

    expect(source).toEqual(markets);
  });

  it('returns an empty result when no catalogue market matches', () => {
    expect(service.filterAndSort(markets, { search: 'missing' })).toEqual([]);
  });
});

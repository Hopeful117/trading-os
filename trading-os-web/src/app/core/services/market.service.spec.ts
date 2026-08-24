// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { MarketService } from './market.service';
import { environment } from '../../../environments/environment';
import { OhlcInterval } from '../models/ohlc-interval';

describe('MarketService', () => {
  let service: MarketService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    httpMock = TestBed.inject(HttpTestingController);
    service = TestBed.inject(MarketService);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('findAll sends GET to correct URL', () => {
    const mockMarkets = [{ marketId: '1', symbol: 'BTC/USD' }];

    service.findAll().subscribe((markets) => {
      expect(markets).toEqual(mockMarkets);
    });

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/markets`);
    expect(req.request.method).toBe('GET');
    req.flush(mockMarkets);
  });

  it('findById sends GET to correct URL with id', () => {
    const marketId = 'test-market-id';
    const mockMarket = { marketId, symbol: 'ETH/USD' };

    service.findById(marketId).subscribe((market) => {
      expect(market).toEqual(mockMarket);
    });

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/markets/${marketId}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockMarket);
  });

  it('subscribe sends POST to correct URL with request body', () => {
    const marketId = 'market-123';
    const request = { type: 'TICKER' as any, parameters: null };

    service.subscribe(marketId, request).subscribe();

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/markets/${marketId}/subscriptions`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(null);
  });

  it('unsubscribe sends DELETE to correct URL with request body', () => {
    const marketId = 'market-456';
    const request = { type: 'TICKER' as any, parameters: null };

    service.unsubscribe(marketId, request).subscribe();

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/markets/${marketId}/subscriptions`);
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toEqual(request);
    req.flush(null);
  });

  it('findOhlcHistory sends GET with interval and limit params', () => {
    const marketId = 'market-789';
    const interval = OhlcInterval.ONE_HOUR;
    const limit = 100;
    const mockOhlc = [{ timestamp: 1, open: 100 }];

    service.findOhlcHistory(marketId, interval, limit).subscribe((ohlc) => {
      expect(ohlc).toEqual(mockOhlc);
    });

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.gatewayUrl}v1/markets/${marketId}/ohlc`,
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('interval')).toBe(interval);
    expect(req.request.params.get('limit')).toBe(limit.toString());
    req.flush(mockOhlc);
  });

  it('findOhlcHistory uses default limit of 200', () => {
    const marketId = 'market-default';
    const interval = OhlcInterval.FIVE_MINUTES;

    service.findOhlcHistory(marketId, interval).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.gatewayUrl}v1/markets/${marketId}/ohlc`,
    );
    expect(req.request.params.get('limit')).toBe('200');
    req.flush([]);
  });
});

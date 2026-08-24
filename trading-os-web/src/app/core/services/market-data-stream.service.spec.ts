import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { AuthService } from './auth.service';
import { MarketDataStreamService } from './market-data-stream.service';

describe('MarketDataStreamService', () => {
  let service: MarketDataStreamService;
  let openedUrl: string | null;
  let lastSocket: any;

  class WebSocketStub {
    static readonly OPEN = 1;
    static readonly CONNECTING = 0;

    readonly readyState = WebSocketStub.OPEN;
    onmessage: ((message: MessageEvent<string>) => void) | null = null;
    onerror: (() => void) | null = null;
    onclose: (() => void) | null = null;

    constructor(url: string) {
      openedUrl = url;
      lastSocket = this;
    }

    close(): void {}
  }

  beforeEach(() => {
    openedUrl = null;
    lastSocket = null;

    vi.stubGlobal('WebSocket', WebSocketStub);

    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: {
            getToken: () => 'test-token',
          },
        },
      ],
    });
    service = TestBed.inject(MarketDataStreamService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('opens a ticker stream', () => {
    const subscription = service.streamTicker('BTC/EUR').subscribe();

    expect(openedUrl).toContain('symbol=BTC%2FEUR');
    expect(openedUrl).toContain('type=TICKER');
    expect(openedUrl).toContain('access_token=test-token');
    expect(openedUrl).not.toContain('marketId=');
    expect(openedUrl).not.toContain('interval=');
    expect(openedUrl).not.toContain('depth=');

    subscription.unsubscribe();
  });

  it('opens an OHLC stream with interval parameter', () => {
    const subscription = service.streamOhlc('market-id', 'BTC/EUR', 5).subscribe();

    expect(openedUrl).toContain('marketId=market-id');
    expect(openedUrl).toContain('symbol=BTC%2FEUR');
    expect(openedUrl).toContain('type=OHLC');
    expect(openedUrl).toContain('interval=5');
    expect(openedUrl).toContain('access_token=test-token');

    subscription.unsubscribe();
  });

  it('opens an order-book stream with market and depth parameters', () => {
    const subscription = service.streamOrderBook('market-id', 'BTC/EUR', 25).subscribe();

    expect(openedUrl).toContain('marketId=market-id');
    expect(openedUrl).toContain('symbol=BTC%2FEUR');
    expect(openedUrl).toContain('type=ORDER_BOOK');
    expect(openedUrl).toContain('depth=25');
    expect(openedUrl).toContain('access_token=test-token');

    subscription.unsubscribe();
  });

  it('opens a recent-trades stream without provider parameters', () => {
    const subscription = service.streamRecentTrades('market-id', 'BTC/EUR').subscribe();

    expect(openedUrl).toContain('marketId=market-id');
    expect(openedUrl).toContain('symbol=BTC%2FEUR');
    expect(openedUrl).toContain('type=TRADES');
    expect(openedUrl).not.toContain('depth=');
    expect(openedUrl).not.toContain('interval=');

    subscription.unsubscribe();
  });

  it('errors when no authentication token is available', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: { getToken: () => null },
        },
      ],
    });
    const noAuthService = TestBed.inject(MarketDataStreamService);

    const errorFn = vi.fn();
    noAuthService.streamTicker('BTC/EUR').subscribe({ error: errorFn });

    expect(errorFn).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'No authentication token available' }),
    );
  });

  it('completes when WebSocket closes', () => {
    const completeFn = vi.fn();
    service.streamTicker('BTC/EUR').subscribe({ complete: completeFn });

    lastSocket.onclose!();

    expect(completeFn).toHaveBeenCalledOnce();
  });

  it('errors when WebSocket encounters an error', () => {
    const errorFn = vi.fn();
    service.streamTicker('BTC/EUR').subscribe({ error: errorFn });

    lastSocket.onerror!();

    expect(errorFn).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'TICKER market data WebSocket error' }),
    );
  });

  it('parses incoming messages and forwards them', () => {
    const nextFn = vi.fn();
    service.streamTicker('BTC/EUR').subscribe({ next: nextFn });

    const data = { last: 50000, bid: 49999, ask: 50001 };
    lastSocket.onmessage!({ data: JSON.stringify(data) } as MessageEvent<string>);

    expect(nextFn).toHaveBeenCalledWith(data);
  });

  it('errors on malformed WebSocket message', () => {
    const errorFn = vi.fn();
    service.streamTicker('BTC/EUR').subscribe({ error: errorFn });

    lastSocket.onmessage!({ data: 'not-json' } as MessageEvent<string>);

    expect(errorFn).toHaveBeenCalled();
  });

  it('closes WebSocket on unsubscribe when OPEN', () => {
    const closeSpy = vi.fn();
    const openSocket = {
      readyState: WebSocketStub.OPEN,
      close: closeSpy,
      onmessage: null,
      onerror: null,
      onclose: null,
      constructor: () => {},
    };
    vi.stubGlobal(
      'WebSocket',
      class {
        static readonly OPEN = 1;
        static readonly CONNECTING = 0;
        readyState = WebSocketStub.OPEN;
        onmessage: any = null;
        onerror: any = null;
        onclose: any = null;
        close = closeSpy;
        constructor() {
          lastSocket = this;
        }
      },
    );

    const subscription = service.streamTicker('BTC/EUR').subscribe();
    subscription.unsubscribe();

    expect(closeSpy).toHaveBeenCalledOnce();
  });
});

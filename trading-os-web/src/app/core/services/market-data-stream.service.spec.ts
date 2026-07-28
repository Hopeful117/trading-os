import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { AuthService } from './auth.service';
import { MarketDataStreamService } from './market-data-stream.service';

describe('MarketDataStreamService', () => {
  let service: MarketDataStreamService;
  let openedUrl: string | null;

  beforeEach(() => {
    openedUrl = null;

    class WebSocketStub {
      static readonly OPEN = 1;
      static readonly CONNECTING = 0;

      readonly readyState = 3;
      onmessage: ((message: MessageEvent<string>) => void) | null = null;
      onerror: (() => void) | null = null;
      onclose: (() => void) | null = null;

      constructor(url: string) {
        openedUrl = url;
      }

      close(): void {}
    }

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

  it('opens an order-book stream with market and depth parameters', () => {
    const subscription = service
      .streamOrderBook('market-id', 'BTC/EUR', 25)
      .subscribe();

    expect(openedUrl).toContain('marketId=market-id');
    expect(openedUrl).toContain('symbol=BTC%2FEUR');
    expect(openedUrl).toContain('type=ORDER_BOOK');
    expect(openedUrl).toContain('depth=25');
    expect(openedUrl).toContain('access_token=test-token');

    subscription.unsubscribe();
  });

  it('opens a recent-trades stream without provider parameters', () => {
    const subscription = service
      .streamRecentTrades('market-id', 'BTC/EUR')
      .subscribe();

    expect(openedUrl).toContain('marketId=market-id');
    expect(openedUrl).toContain('symbol=BTC%2FEUR');
    expect(openedUrl).toContain('type=TRADES');
    expect(openedUrl).not.toContain('depth=');
    expect(openedUrl).not.toContain('interval=');

    subscription.unsubscribe();
  });
});

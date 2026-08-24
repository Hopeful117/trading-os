import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { TickerEvent } from '../models/ticker-event.model';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';
import { OhlcEvent } from '../models/ohlc-event.model';
import { MarketStreamQuery } from '../models/MarketStreamQuery';
import { MarketStreamType } from '../models/market-stream-type';
import { OrderBookSnapshot } from '../models/order-book-snapshot.model';
import { RecentTradesSnapshot } from '../models/recent-trades-snapshot.model';

@Injectable({
  providedIn: 'root',
})
export class MarketDataStreamService {
  private readonly authService = inject(AuthService);

  streamTicker(symbol: string): Observable<TickerEvent> {
    return this.createStream<TickerEvent>({
      symbol,
      type: MarketStreamType.TICKER,
    });
  }

  streamOhlc(marketId: string, symbol: string, interval: number): Observable<OhlcEvent> {
    return this.createStream<OhlcEvent>({
      marketId,
      symbol,
      type: MarketStreamType.OHLC,
      interval,
    });
  }

  streamOrderBook(marketId: string, symbol: string, depth: number): Observable<OrderBookSnapshot> {
    return this.createStream<OrderBookSnapshot>({
      marketId,
      symbol,
      type: MarketStreamType.ORDER_BOOK,
      depth,
    });
  }

  streamRecentTrades(marketId: string, symbol: string): Observable<RecentTradesSnapshot> {
    return this.createStream<RecentTradesSnapshot>({
      marketId,
      symbol,
      type: MarketStreamType.TRADES,
    });
  }

  private createStream<T>(parameters: MarketStreamQuery): Observable<T> {
    return new Observable<T>((subscriber) => {
      const token = this.authService.getToken();

      if (!token) {
        subscriber.error(new Error('No authentication token available'));
        return;
      }

      const query = new URLSearchParams();

      query.set('symbol', parameters.symbol);
      query.set('type', parameters.type);
      query.set('access_token', token);

      if (parameters.marketId !== undefined) {
        query.set('marketId', parameters.marketId);
      }

      if (parameters.interval !== undefined) {
        query.set('interval', parameters.interval.toString());
      }

      if (parameters.depth !== undefined) {
        query.set('depth', parameters.depth.toString());
      }

      const socket = new WebSocket(`${environment.marketDataWebSocketUrl}?${query.toString()}`);

      socket.onmessage = (message: MessageEvent<string>) => {
        try {
          subscriber.next(JSON.parse(message.data) as T);
        } catch (error) {
          subscriber.error(error);
        }
      };

      socket.onerror = () => {
        subscriber.error(new Error(`${parameters.type} market data WebSocket error`));
      };

      socket.onclose = () => {
        subscriber.complete();
      };

      return () => {
        if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
          socket.close();
        }
      };
    });
  }
}

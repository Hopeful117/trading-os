import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {TickerEvent} from '../models/ticker-event.model';
import { environment } from '../../../environments/environment';
import {AuthService} from './auth.service';


@Injectable({
  providedIn: 'root',
})
export class MarketDataStreamService {
  private readonly authService = inject(AuthService);

  streamTicker(symbol: string): Observable<TickerEvent> {
    return new Observable<TickerEvent>((subscriber) => {
      const token = this.authService.getToken();
      if (!token) {
        subscriber.error(new Error('No authentication token available'));
        return;
      }

      const socket = new WebSocket(
        `${environment.marketDataWebSocketUrl}?symbol=${encodeURIComponent(symbol)}&access_token=${encodeURIComponent(token)}`,
      );

      socket.onmessage = (event) => {
        try {
          subscriber.next(JSON.parse(event.data) as TickerEvent);
        } catch (error) {
          subscriber.error(error);
        }
      };

      socket.onerror = () => {
        subscriber.error(new Error('Market data WebSocket error'));
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

import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { distinctUntilChanged, filter, map, shareReplay, switchMap, tap } from 'rxjs';

import { MarketDataStreamService } from '../../../core/services/market-data-stream.service';
import { MarketService } from '../../../core/services/market.service';
import { MarketStreamRequest } from '../../../core/models/market-stream-request';
import { MarketStreamType } from '../../../core/models/market-stream-type';

@Component({
  selector: 'app-market-details',
  imports: [AsyncPipe, RouterLink, DatePipe],
  templateUrl: './markets-details.html',
  styleUrl: './markets-details.scss',
})
export class MarketDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly marketService = inject(MarketService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly marketDataStreamService = inject(MarketDataStreamService);

  private subscribedMarketId: string | null = null;
  private readonly ohlcInterval = 5;

  private readonly ohlcRequest: MarketStreamRequest = {
    type: MarketStreamType.OHLC,
    parameters: {
      interval: this.ohlcInterval,
      depth: 0,
    },
  };

  private readonly tickerRequest: MarketStreamRequest = {
    type: MarketStreamType.TICKER,
    parameters: null,
  };

  readonly marketId$ = this.route.paramMap.pipe(
    map((params) => params.get('marketId')),
    filter((marketId): marketId is string => marketId !== null),
    distinctUntilChanged(),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );

  readonly market$ = this.marketId$.pipe(
    switchMap((marketId) => this.marketService.findById(marketId)),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );

  /**
   * Un seul pipeline :
   *
   * marché chargé
   * → abonnement REST terminé
   * → ouverture du WebSocket frontend
   */
  readonly ticker$ = this.market$.pipe(
    tap((market) => {
      console.log('[TICKER] Market received', market);
    }),
    switchMap((market) => {
      console.log('[TICKER] Sending REST subscription', market.marketId, this.tickerRequest);

      return this.marketService.subscribe(market.marketId, this.tickerRequest).pipe(
        tap(() => {
          console.log('[TICKER] REST subscription completed');
        }),
        switchMap(() => {
          console.log('[TICKER] Opening frontend WebSocket');

          return this.marketDataStreamService.streamTicker(market.symbol);
        }),
      );
    }),
    tap((event) => {
      console.log('[TICKER] Event received', event);
    }),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );

  constructor() {
    this.destroyRef.onDestroy(() => {
      const marketId = this.subscribedMarketId;

      if (marketId === null) {
        return;
      }

      this.marketService
        .unsubscribe(marketId, this.tickerRequest)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          error: (error) => {
            console.error('Unable to unsubscribe from market ticker', error);
          },
        });
    });
  }
  readonly ohlc$ = this.market$.pipe(
    tap((market) => {
      console.log('[OHLC] Market received', market);
    }),
    switchMap((market) => {
      console.log('[OHLC] Sending REST subscription', market.marketId, this.ohlcRequest);

      return this.marketService.subscribe(market.marketId, this.ohlcRequest).pipe(
        tap(() => {
          console.log('[OHLC] REST subscription completed');
        }),
        switchMap(() => {
          console.log('[OHLC] Opening frontend WebSocket');

          return this.marketDataStreamService.streamOhlc(
            market.marketId,
            market.symbol,
            this.ohlcInterval,
          );
        }),
      );
    }),
    tap((event) => {
      console.log('[OHLC] Event received', event);
    }),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );
}

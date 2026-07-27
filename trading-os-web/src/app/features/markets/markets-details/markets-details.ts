import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  combineLatest,
  distinctUntilChanged,
  filter,
  map,
  of,
  shareReplay,
  switchMap,
  tap,
} from 'rxjs';

import { MarketDataStreamService } from '../../../core/services/market-data-stream.service';
import { MarketService } from '../../../core/services/market.service';
import { MarketStreamRequest } from '../../../core/models/market-stream-request';
import { MarketStreamType } from '../../../core/models/market-stream-type';
import { MarketChartComponent } from '../market-chart-component/market-chart-component';

@Component({
  selector: 'app-market-details',
  imports: [AsyncPipe, RouterLink, DatePipe, MarketChartComponent],
  templateUrl: './markets-details.html',
  styleUrl: './markets-details.scss',
})
export class MarketDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly marketService = inject(MarketService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly marketDataStreamService = inject(MarketDataStreamService);

  private subscribedMarketId: string | null = null;
  readonly ohlcIntervals = [
    { label: '1m', minutes: 1 },
    { label: '5m', minutes: 5 },
    { label: '15m', minutes: 15 },
    { label: '30m', minutes: 30 },
    { label: '1h', minutes: 60 },
    { label: '4h', minutes: 240 },
    { label: '1d', minutes: 1440 },
  ] as const;

  private readonly ohlcIntervalSubject = new BehaviorSubject<number>(5);

  readonly selectedOhlcInterval$ = this.ohlcIntervalSubject.pipe(
    distinctUntilChanged(),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );
  private activeOhlcSubscription: ActiveOhlcSubscription | null = null;

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
  private readonly chartResetSubject = new BehaviorSubject<number>(0);

  readonly chartReset$ = this.chartResetSubject.asObservable();

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
      const active = this.activeOhlcSubscription;

      if (active === null) {
        return;
      }

      this.marketService.unsubscribe(active.marketId, active.request).subscribe({
        error: (error) => {
          console.error('Unable to unsubscribe OHLC stream', error);
        },
      });
    });
  }
  selectOhlcInterval(interval: number): void {
    if (this.ohlcIntervalSubject.value === interval) {
      return;
    }

    this.ohlcIntervalSubject.next(interval);
  }
  readonly ohlc$ = combineLatest([this.market$, this.selectedOhlcInterval$]).pipe(
    switchMap(([market, interval]) => {
      const nextRequest: MarketStreamRequest = {
        type: MarketStreamType.OHLC,
        parameters: {
          interval,
          depth: 0,
        },
      };

      /*
       * switchMap vient déjà de fermer l’ancien
       * WebSocket Angular à ce stade.
       */
      const previousSubscription = this.activeOhlcSubscription;

      const unsubscribePrevious$ =
        previousSubscription === null
          ? of(undefined)
          : this.marketService
              .unsubscribe(previousSubscription.marketId, previousSubscription.request)
              .pipe(
                catchError((error) => {
                  console.error('Unable to unsubscribe previous OHLC stream', error);

                  /*
                   * On poursuit afin de ne pas bloquer
                   * définitivement le changement d’intervalle.
                   */
                  return of(undefined);
                }),
              );

      return unsubscribePrevious$.pipe(
        tap(() => {
          this.activeOhlcSubscription = null;

          /*
           * Vide immédiatement l’ancien graphique.
           */
          this.chartResetSubject.next(this.chartResetSubject.value + 1);
        }),

        switchMap(() => this.marketService.subscribe(market.marketId, nextRequest)),

        tap(() => {
          this.activeOhlcSubscription = {
            marketId: market.marketId,
            request: nextRequest,
          };
        }),

        switchMap(() =>
          this.marketDataStreamService.streamOhlc(market.marketId, market.symbol, interval),
        ),
      );
    }),

    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );
}
interface ActiveOhlcSubscription {
  marketId: string;
  request: MarketStreamRequest;

}

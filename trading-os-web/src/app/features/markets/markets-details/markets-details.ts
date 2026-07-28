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
import { OhlcInterval } from '../../../core/models/ohlc-interval';

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
  private readonly selectedTimeframeSubject = new BehaviorSubject<OhlcTimeframe>(
    OHLC_TIMEFRAMES[1],
  );

  readonly selectedTimeframe$ = this.selectedTimeframeSubject.pipe(
    distinctUntilChanged((previous, current) => previous.minutes === current.minutes),
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
  readonly ohlcIntervals = OHLC_TIMEFRAMES;
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
  selectOhlcInterval(timeframe: OhlcTimeframe): void {
    if (this.selectedTimeframeSubject.value.minutes === timeframe.minutes) {
      return;
    }

    this.selectedTimeframeSubject.next(timeframe);
  }
  readonly ohlcHistory$ = combineLatest([this.market$, this.selectedTimeframe$]).pipe(
    switchMap(([market, timeframe]) =>
      this.marketService.findOhlcHistory(market.marketId, timeframe.interval, 200),
    ),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );
  readonly ohlc$ = combineLatest([this.market$, this.selectedTimeframe$]).pipe(
    switchMap(([market, timeframe]) => {
      const nextRequest: MarketStreamRequest = {
        type: MarketStreamType.OHLC,
        parameters: {
          interval: timeframe.minutes,
          depth: 0,
        },
      };

      const previousSubscription = this.activeOhlcSubscription;

      const unsubscribePrevious$ =
        previousSubscription === null
          ? of(undefined)
          : this.marketService
              .unsubscribe(previousSubscription.marketId, previousSubscription.request)
              .pipe(
                catchError((error) => {
                  console.error('Unable to unsubscribe previous OHLC stream', error);

                  return of(undefined);
                }),
              );

      return unsubscribePrevious$.pipe(
        tap(() => {
          this.activeOhlcSubscription = null;

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
          this.marketDataStreamService.streamOhlc(
            market.marketId,
            market.symbol,
            timeframe.minutes,
          ),
        ),
      );
    }),

    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );
}


type OhlcTimeframe = (typeof OHLC_TIMEFRAMES)[number];

interface ActiveOhlcSubscription {
  marketId: string;
  request: MarketStreamRequest;
}

const OHLC_TIMEFRAMES = [
  {
    label: '1m',
    minutes: 1,
    interval: OhlcInterval.ONE_MINUTE,
  },
  {
    label: '5m',
    minutes: 5,
    interval: OhlcInterval.FIVE_MINUTES,
  },
  {
    label: '15m',
    minutes: 15,
    interval: OhlcInterval.FIFTEEN_MINUTES,
  },
  {
    label: '30m',
    minutes: 30,
    interval: OhlcInterval.THIRTY_MINUTES,
  },
  {
    label: '1h',
    minutes: 60,
    interval: OhlcInterval.ONE_HOUR,
  },
  {
    label: '4h',
    minutes: 240,
    interval: OhlcInterval.FOUR_HOURS,
  },
  {
    label: '1d',
    minutes: 1440,
    interval: OhlcInterval.ONE_DAY,
  },
] as const;

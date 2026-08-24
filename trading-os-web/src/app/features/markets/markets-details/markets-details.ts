import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, inject } from '@angular/core';
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
import { OrderBookComponent } from '../order-book-component/order-book-component';
import { RecentTradesComponent } from '../recent-trades-component/recent-trades-component';

@Component({
  selector: 'app-market-details',
  imports: [
    AsyncPipe,
    RouterLink,
    DatePipe,
    MarketChartComponent,
    OrderBookComponent,
    RecentTradesComponent,
  ],
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
  private readonly selectedOrderBookDepthSubject = new BehaviorSubject<OrderBookDepth>(
    ORDER_BOOK_DEPTHS[0],
  );

  readonly selectedTimeframe$ = this.selectedTimeframeSubject.pipe(
    distinctUntilChanged((previous, current) => previous.minutes === current.minutes),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );

  private activeOhlcSubscription: ActiveOhlcSubscription | null = null;
  private activeOrderBookSubscription: ActiveOrderBookSubscription | null = null;
  private activeTickerSubscription: ActiveTickerSubscription | null = null;
  private activeRecentTradesSubscription: ActiveRecentTradesSubscription | null = null;

  private readonly tickerRequest: MarketStreamRequest = {
    type: MarketStreamType.TICKER,
    parameters: null,
  };
  private readonly recentTradesRequest: MarketStreamRequest = {
    type: MarketStreamType.TRADES,
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
  readonly orderBookDepths = ORDER_BOOK_DEPTHS;
  readonly selectedOrderBookDepth$ = this.selectedOrderBookDepthSubject.pipe(
    distinctUntilChanged(),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );
  private readonly chartResetSubject = new BehaviorSubject<number>(0);

  readonly chartReset$ = this.chartResetSubject.asObservable();

  readonly ticker$ = this.market$.pipe(
    switchMap((market) => {
      const previousSubscription = this.activeTickerSubscription;
      const unsubscribePrevious$ =
        previousSubscription === null
          ? of(undefined)
          : this.marketService.unsubscribe(previousSubscription.marketId, this.tickerRequest).pipe(
              catchError((error) => {
                console.error('Unable to unsubscribe previous ticker stream', error);
                return of(undefined);
              }),
            );

      return unsubscribePrevious$.pipe(
        tap(() => {
          this.activeTickerSubscription = null;
        }),
        switchMap(() => this.marketService.subscribe(market.marketId, this.tickerRequest)),
        tap(() => {
          this.activeTickerSubscription = {
            marketId: market.marketId,
          };
        }),
        switchMap(() => this.marketDataStreamService.streamTicker(market.symbol)),
      );
    }),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );

  constructor() {
    this.destroyRef.onDestroy(() => {
      const activeTicker = this.activeTickerSubscription;

      if (activeTicker !== null) {
        this.marketService.unsubscribe(activeTicker.marketId, this.tickerRequest).subscribe({
          error: (error) => {
            console.error('Unable to unsubscribe ticker stream', error);
          },
        });
      }

      const active = this.activeOhlcSubscription;

      if (active !== null) {
        this.marketService.unsubscribe(active.marketId, active.request).subscribe({
          error: (error) => {
            console.error('Unable to unsubscribe OHLC stream', error);
          },
        });
      }

      const activeOrderBook = this.activeOrderBookSubscription;

      if (activeOrderBook !== null) {
        this.marketService
          .unsubscribe(activeOrderBook.marketId, activeOrderBook.request)
          .subscribe({
            error: (error) => {
              console.error('Unable to unsubscribe order-book stream', error);
            },
          });
      }

      const activeRecentTrades = this.activeRecentTradesSubscription;

      if (activeRecentTrades !== null) {
        this.marketService
          .unsubscribe(activeRecentTrades.marketId, this.recentTradesRequest)
          .subscribe({
            error: (error) => {
              console.error('Unable to unsubscribe recent-trades stream', error);
            },
          });
      }
    });
  }
  selectOhlcInterval(timeframe: OhlcTimeframe): void {
    if (this.selectedTimeframeSubject.value.minutes === timeframe.minutes) {
      return;
    }

    this.selectedTimeframeSubject.next(timeframe);
  }

  selectOrderBookDepth(depth: OrderBookDepth): void {
    if (this.selectedOrderBookDepthSubject.value === depth) {
      return;
    }

    this.selectedOrderBookDepthSubject.next(depth);
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

  readonly orderBook$ = combineLatest([this.market$, this.selectedOrderBookDepth$]).pipe(
    switchMap(([market, depth]) => {
      const nextRequest: MarketStreamRequest = {
        type: MarketStreamType.ORDER_BOOK,
        parameters: {
          interval: null,
          depth,
        },
      };
      const previousSubscription = this.activeOrderBookSubscription;
      const unsubscribePrevious$ =
        previousSubscription === null
          ? of(undefined)
          : this.marketService
              .unsubscribe(previousSubscription.marketId, previousSubscription.request)
              .pipe(
                catchError((error) => {
                  console.error('Unable to unsubscribe previous order-book stream', error);
                  return of(undefined);
                }),
              );

      return unsubscribePrevious$.pipe(
        tap(() => {
          this.activeOrderBookSubscription = null;
        }),
        switchMap(() => this.marketService.subscribe(market.marketId, nextRequest)),
        tap(() => {
          this.activeOrderBookSubscription = {
            marketId: market.marketId,
            request: nextRequest,
          };
        }),
        switchMap(() =>
          this.marketDataStreamService.streamOrderBook(market.marketId, market.symbol, depth),
        ),
      );
    }),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );

  readonly recentTrades$ = this.market$.pipe(
    switchMap((market) => {
      const previousSubscription = this.activeRecentTradesSubscription;
      const unsubscribePrevious$ =
        previousSubscription === null
          ? of(undefined)
          : this.marketService
              .unsubscribe(previousSubscription.marketId, this.recentTradesRequest)
              .pipe(
                catchError((error) => {
                  console.error('Unable to unsubscribe previous recent-trades stream', error);
                  return of(undefined);
                }),
              );

      return unsubscribePrevious$.pipe(
        tap(() => {
          this.activeRecentTradesSubscription = null;
        }),
        switchMap(() => this.marketService.subscribe(market.marketId, this.recentTradesRequest)),
        tap(() => {
          this.activeRecentTradesSubscription = {
            marketId: market.marketId,
          };
        }),
        switchMap(() =>
          this.marketDataStreamService.streamRecentTrades(market.marketId, market.symbol),
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

interface ActiveOrderBookSubscription {
  marketId: string;
  request: MarketStreamRequest;
}

interface ActiveTickerSubscription {
  marketId: string;
}

interface ActiveRecentTradesSubscription {
  marketId: string;
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

const ORDER_BOOK_DEPTHS = [10, 25] as const;
type OrderBookDepth = (typeof ORDER_BOOK_DEPTHS)[number];

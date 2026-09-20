import { AsyncPipe, DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, DestroyRef, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  combineLatest,
  distinctUntilChanged,
  map,
  Observable,
  of,
  shareReplay,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';

import { Account } from '../../core/models/account.model';
import { DecisionContextResponse } from '../../core/models/decision-context.model';
import { MarketResponse } from '../../core/models/market-response';
import { MarketStreamRequest } from '../../core/models/market-stream-request';
import { MarketStreamType } from '../../core/models/market-stream-type';
import { OhlcEvent } from '../../core/models/ohlc-event.model';
import { OhlcInterval } from '../../core/models/ohlc-interval';
import { OrderBookSnapshot } from '../../core/models/order-book-snapshot.model';
import { RecentTradesSnapshot } from '../../core/models/recent-trades-snapshot.model';
import { TickerEvent } from '../../core/models/ticker-event.model';
import { AccountService } from '../../core/services/account.service';
import { DecisionContextService } from '../../core/services/decision-context.service';
import { MarketDataStreamService } from '../../core/services/market-data-stream.service';
import { MarketService } from '../../core/services/market.service';
import { MarketChartComponent } from '../markets/market-chart-component/market-chart-component';
import { OrderBookComponent } from '../markets/order-book-component/order-book-component';
import { RecentTradesComponent } from '../markets/recent-trades-component/recent-trades-component';

export type DecisionWorkspaceContextView =
  | { status: 'select-account' }
  | { status: 'loading' }
  | { status: 'ready'; context: DecisionContextResponse }
  | { status: 'error' };

export type DecisionWorkspaceAccountsView =
  | { status: 'loading'; accounts: Account[] }
  | { status: 'loaded'; accounts: Account[] }
  | { status: 'error'; accounts: Account[] };

export type DecisionWorkspaceMarketView =
  | { status: 'none' }
  | { status: 'loading' }
  | { status: 'ineligible' }
  | { status: 'loaded'; market: MarketResponse }
  | { status: 'error' };

export type StreamView<T> =
  { status: 'waiting' } | { status: 'live'; data: T } | { status: 'error' };
export type MarketFreshness = 'LIVE' | 'RECENT' | 'STALE' | 'UNAVAILABLE';

export type HistoryView<T> =
  { status: 'loading'; data: T } | { status: 'loaded'; data: T } | { status: 'error'; data: T };

@Component({
  selector: 'app-decision-workspace',
  imports: [AsyncPipe, DatePipe, MarketChartComponent, OrderBookComponent, RecentTradesComponent],
  templateUrl: './decision-workspace.html',
  styleUrl: './decision-workspace.scss',
})
export class DecisionWorkspace {
  private readonly accountService = inject(AccountService);
  private readonly contextService = inject(DecisionContextService);
  private readonly marketService = inject(MarketService);
  private readonly marketDataStreamService = inject(MarketDataStreamService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly accountsRefreshSubject = new Subject<void>();
  private readonly selectedAccountSubject = new BehaviorSubject<string | null>(null);
  private readonly selectedMarketSubject = new BehaviorSubject<string | null>(null);
  private readonly selectedTimeframeSubject = new BehaviorSubject<OhlcTimeframe>(
    OHLC_TIMEFRAMES[1],
  );
  private readonly selectedOrderBookDepthSubject = new BehaviorSubject<OrderBookDepth>(
    ORDER_BOOK_DEPTHS[0],
  );

  private activeOhlcSubscription: ActiveSubscription | null = null;
  private activeOrderBookSubscription: ActiveSubscription | null = null;
  private activeTickerSubscription: ActiveSubscription | null = null;
  private activeRecentTradesSubscription: ActiveSubscription | null = null;

  selectedAccountId: string | null = null;
  selectedMarketId: string | null = null;

  readonly accounts$ = this.accountsRefreshSubject.pipe(
    startWith(undefined),
    switchMap(() =>
      this.accountService.getAccounts().pipe(
        map((accounts) => ({ status: 'loaded' as const, accounts })),
        catchError(() => of({ status: 'error' as const, accounts: [] as Account[] })),
        startWith({ status: 'loading' as const, accounts: [] as Account[] }),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly context$ = this.selectedAccountSubject.pipe(
    switchMap((accountId) => {
      if (accountId === null) {
        return of<DecisionWorkspaceContextView>({ status: 'select-account' });
      }

      return this.contextService.resolve(accountId).pipe(
        map((context) => ({ status: 'ready' as const, context })),
        catchError(() => of<DecisionWorkspaceContextView>({ status: 'error' })),
        startWith<DecisionWorkspaceContextView>({ status: 'loading' }),
      );
    }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly selectedTimeframe$ = this.selectedTimeframeSubject.pipe(
    distinctUntilChanged((previous, current) => previous.minutes === current.minutes),
    shareReplay({ bufferSize: 1, refCount: true }),
  );
  readonly selectedOrderBookDepth$ = this.selectedOrderBookDepthSubject.pipe(
    distinctUntilChanged(),
    shareReplay({ bufferSize: 1, refCount: true }),
  );
  readonly ohlcIntervals = OHLC_TIMEFRAMES;
  readonly orderBookDepths = ORDER_BOOK_DEPTHS;
  private readonly chartResetSubject = new BehaviorSubject<number>(0);
  readonly chartReset$ = this.chartResetSubject.asObservable();

  readonly marketView$ = combineLatest([this.context$, this.selectedMarketSubject]).pipe(
    switchMap(([contextView, marketId]) => {
      if (contextView.status !== 'ready' || marketId === null) {
        return of<DecisionWorkspaceMarketView>({ status: 'none' });
      }

      if (!contextView.context.eligibleMarketIds.includes(marketId)) {
        return of<DecisionWorkspaceMarketView>({ status: 'ineligible' });
      }

      return this.marketService.findById(marketId).pipe(
        map((market) => ({ status: 'loaded' as const, market })),
        catchError(() => of<DecisionWorkspaceMarketView>({ status: 'error' })),
        startWith<DecisionWorkspaceMarketView>({ status: 'loading' }),
      );
    }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  private readonly selectedMarket$ = this.marketView$.pipe(
    map((view) => (view.status === 'loaded' ? view.market : null)),
    distinctUntilChanged((previous, current) => previous?.marketId === current?.marketId),
    tap((market) => {
      if (market === null) {
        this.clearActiveSubscriptions();
      }
    }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly ohlcHistory$ = combineLatest([this.selectedMarket$, this.selectedTimeframe$]).pipe(
    switchMap(([market, timeframe]) => {
      if (market === null) {
        return of<HistoryView<OhlcEvent[]>>({ status: 'loading', data: [] });
      }

      return this.marketService.findOhlcHistory(market.marketId, timeframe.interval, 200).pipe(
        map((data) => ({ status: 'loaded' as const, data })),
        catchError(() => of<HistoryView<OhlcEvent[]>>({ status: 'error', data: [] })),
        startWith<HistoryView<OhlcEvent[]>>({ status: 'loading', data: [] }),
      );
    }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly ticker$ = this.selectedMarket$.pipe(
    switchMap((market) =>
      market === null
        ? of<StreamView<TickerEvent>>({ status: 'waiting' })
        : this.tickerStream(market),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly ohlc$ = combineLatest([this.selectedMarket$, this.selectedTimeframe$]).pipe(
    switchMap(([market, timeframe]) =>
      market === null
        ? of<StreamView<OhlcEvent>>({ status: 'waiting' })
        : this.ohlcStream(market, timeframe),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly orderBook$ = combineLatest([this.selectedMarket$, this.selectedOrderBookDepth$]).pipe(
    switchMap(([market, depth]) =>
      market === null
        ? of<StreamView<OrderBookSnapshot>>({ status: 'waiting' })
        : this.orderBookStream(market, depth),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly recentTrades$ = this.selectedMarket$.pipe(
    switchMap((market) =>
      market === null
        ? of<StreamView<RecentTradesSnapshot>>({ status: 'waiting' })
        : this.recentTradesStream(market),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const accountId = params.get('accountId');
      const marketId = params.get('marketId');

      if (accountId !== this.selectedAccountId) {
        this.selectedAccountId = accountId;
        this.selectedMarketId = null;
        this.selectedMarketSubject.next(null);
        this.selectedAccountSubject.next(accountId);
      }

      if (marketId !== this.selectedMarketId) {
        this.selectedMarketId = marketId;
        if (marketId !== null) {
          this.clearActiveSubscriptions();
        }
        this.selectedMarketSubject.next(marketId);
      }
    });

    this.destroyRef.onDestroy(() => {
      this.unsubscribeActive(this.activeTickerSubscription);
      this.unsubscribeActive(this.activeOhlcSubscription);
      this.unsubscribeActive(this.activeOrderBookSubscription);
      this.unsubscribeActive(this.activeRecentTradesSubscription);
    });
  }

  selectAccount(accountId: string): void {
    this.selectedAccountId = accountId || null;
    this.selectedMarketId = null;
    this.selectedMarketSubject.next(null);
    this.selectedAccountSubject.next(accountId || null);
    void this.router.navigate([], {
      queryParams: { accountId: accountId || null, marketId: null },
      queryParamsHandling: 'merge',
    });
  }

  refreshAccounts(): void {
    this.selectedAccountId = null;
    this.selectedMarketId = null;
    this.selectedMarketSubject.next(null);
    this.selectedAccountSubject.next(null);
    void this.router.navigate([], {
      queryParams: { accountId: null, marketId: null },
      queryParamsHandling: 'merge',
    });
    this.accountsRefreshSubject.next();
  }

  selectMarket(marketId: string, context: DecisionContextResponse): void {
    if (!context.eligibleMarketIds.includes(marketId)) {
      return;
    }

    this.selectedMarketId = marketId;
    this.clearActiveSubscriptions();
    this.selectedMarketSubject.next(marketId);
    void this.router.navigate([], {
      queryParams: { marketId },
      queryParamsHandling: 'merge',
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

  liveCandle(view: StreamView<OhlcEvent> | null): OhlcEvent | null {
    return view?.status === 'live' ? view.data : null;
  }

  marketFreshness<T>(view: StreamView<T> | null, occurredAt: string | null): MarketFreshness {
    if (view?.status !== 'live' || occurredAt === null) {
      return 'UNAVAILABLE';
    }

    const age = Date.now() - Date.parse(occurredAt);
    if (!Number.isFinite(age)) {
      return 'UNAVAILABLE';
    }

    if (age <= MARKET_FRESHNESS_LIVE_MS) {
      return 'LIVE';
    }

    if (age <= MARKET_FRESHNESS_RECENT_MS) {
      return 'RECENT';
    }

    return 'STALE';
  }

  private tickerStream(market: MarketResponse): Observable<StreamView<TickerEvent>> {
    const request: MarketStreamRequest = {
      type: MarketStreamType.TICKER,
      parameters: null,
    };
    const previousSubscription = this.activeTickerSubscription;

    return this.unsubscribePrevious(previousSubscription).pipe(
      tap(() => (this.activeTickerSubscription = null)),
      switchMap(() => this.marketService.subscribe(market.marketId, request)),
      tap(() => (this.activeTickerSubscription = { marketId: market.marketId, request })),
      switchMap(() => this.marketDataStreamService.streamTicker(market.symbol)),
      map((data) => ({ status: 'live' as const, data })),
      catchError(() => of<StreamView<TickerEvent>>({ status: 'error' })),
      startWith<StreamView<TickerEvent>>({ status: 'waiting' }),
    );
  }

  private ohlcStream(
    market: MarketResponse,
    timeframe: OhlcTimeframe,
  ): Observable<StreamView<OhlcEvent>> {
    const request: MarketStreamRequest = {
      type: MarketStreamType.OHLC,
      parameters: { interval: timeframe.minutes, depth: 0 },
    };
    const previousSubscription = this.activeOhlcSubscription;

    return this.unsubscribePrevious(previousSubscription).pipe(
      tap(() => {
        this.activeOhlcSubscription = null;
        this.chartResetSubject.next(this.chartResetSubject.value + 1);
      }),
      switchMap(() => this.marketService.subscribe(market.marketId, request)),
      tap(() => (this.activeOhlcSubscription = { marketId: market.marketId, request })),
      switchMap(() =>
        this.marketDataStreamService.streamOhlc(market.marketId, market.symbol, timeframe.minutes),
      ),
      map((data) => ({ status: 'live' as const, data })),
      catchError(() => of<StreamView<OhlcEvent>>({ status: 'error' })),
      startWith<StreamView<OhlcEvent>>({ status: 'waiting' }),
    );
  }

  private orderBookStream(
    market: MarketResponse,
    depth: OrderBookDepth,
  ): Observable<StreamView<OrderBookSnapshot>> {
    const request: MarketStreamRequest = {
      type: MarketStreamType.ORDER_BOOK,
      parameters: { interval: null, depth },
    };
    const previousSubscription = this.activeOrderBookSubscription;

    return this.unsubscribePrevious(previousSubscription).pipe(
      tap(() => (this.activeOrderBookSubscription = null)),
      switchMap(() => this.marketService.subscribe(market.marketId, request)),
      tap(() => (this.activeOrderBookSubscription = { marketId: market.marketId, request })),
      switchMap(() =>
        this.marketDataStreamService.streamOrderBook(market.marketId, market.symbol, depth),
      ),
      map((data) => ({ status: 'live' as const, data })),
      catchError(() => of<StreamView<OrderBookSnapshot>>({ status: 'error' })),
      startWith<StreamView<OrderBookSnapshot>>({ status: 'waiting' }),
    );
  }

  private recentTradesStream(market: MarketResponse): Observable<StreamView<RecentTradesSnapshot>> {
    const request: MarketStreamRequest = {
      type: MarketStreamType.TRADES,
      parameters: null,
    };
    const previousSubscription = this.activeRecentTradesSubscription;

    return this.unsubscribePrevious(previousSubscription).pipe(
      tap(() => (this.activeRecentTradesSubscription = null)),
      switchMap(() => this.marketService.subscribe(market.marketId, request)),
      tap(() => (this.activeRecentTradesSubscription = { marketId: market.marketId, request })),
      switchMap(() =>
        this.marketDataStreamService.streamRecentTrades(market.marketId, market.symbol),
      ),
      map((data) => ({ status: 'live' as const, data })),
      catchError(() => of<StreamView<RecentTradesSnapshot>>({ status: 'error' })),
      startWith<StreamView<RecentTradesSnapshot>>({ status: 'waiting' }),
    );
  }

  private unsubscribePrevious(subscription: ActiveSubscription | null): Observable<void> {
    if (subscription === null) {
      return of(undefined);
    }

    return this.marketService
      .unsubscribe(subscription.marketId, subscription.request)
      .pipe(catchError(() => of(undefined)));
  }

  private unsubscribeActive(subscription: ActiveSubscription | null): void {
    if (subscription === null) {
      return;
    }

    this.marketService.unsubscribe(subscription.marketId, subscription.request).subscribe({
      error: () => undefined,
    });
  }

  private clearActiveSubscriptions(): void {
    this.unsubscribeActive(this.activeTickerSubscription);
    this.unsubscribeActive(this.activeOhlcSubscription);
    this.unsubscribeActive(this.activeOrderBookSubscription);
    this.unsubscribeActive(this.activeRecentTradesSubscription);
    this.activeTickerSubscription = null;
    this.activeOhlcSubscription = null;
    this.activeOrderBookSubscription = null;
    this.activeRecentTradesSubscription = null;
  }
}

type OhlcTimeframe = (typeof OHLC_TIMEFRAMES)[number];
type OrderBookDepth = (typeof ORDER_BOOK_DEPTHS)[number];

interface ActiveSubscription {
  marketId: string;
  request: MarketStreamRequest;
}

const OHLC_TIMEFRAMES = [
  { label: '1m', minutes: 1, interval: OhlcInterval.ONE_MINUTE },
  { label: '5m', minutes: 5, interval: OhlcInterval.FIVE_MINUTES },
  { label: '15m', minutes: 15, interval: OhlcInterval.FIFTEEN_MINUTES },
  { label: '30m', minutes: 30, interval: OhlcInterval.THIRTY_MINUTES },
  { label: '1h', minutes: 60, interval: OhlcInterval.ONE_HOUR },
  { label: '4h', minutes: 240, interval: OhlcInterval.FOUR_HOURS },
  { label: '1d', minutes: 1440, interval: OhlcInterval.ONE_DAY },
] as const;

const ORDER_BOOK_DEPTHS = [10, 25] as const;
const MARKET_FRESHNESS_LIVE_MS = 15_000;
const MARKET_FRESHNESS_RECENT_MS = 60_000;

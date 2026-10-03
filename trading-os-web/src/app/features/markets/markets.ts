import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import {
  BehaviorSubject,
  combineLatest,
  combineLatestWith,
  map,
  shareReplay,
  startWith,
  Subject,
  switchMap,
} from 'rxjs';

import {
  DEFAULT_MARKET_SORT,
  MarketFilter,
  MarketSort,
} from '../../core/models/market-filter.model';
import { MarketDiscoveryService } from '../../core/services/market-discovery.service';
import { MarketService } from '../../core/services/market.service';
import { MarketToolbarComponent } from './market-toolbar-component/market-toolbar-component';

@Component({
  selector: 'app-markets',
  imports: [AsyncPipe, MarketToolbarComponent],
  templateUrl: './markets.html',
  styleUrl: './markets.scss',
})
export class Markets {
  private readonly marketService = inject(MarketService);
  private readonly marketDiscovery = inject(MarketDiscoveryService);
  private readonly router = inject(Router);

  private readonly refreshSubject = new Subject<void>();

  private readonly filterSubject = new BehaviorSubject<MarketFilter>({
    search: '',
  });
  private readonly sortSubject = new BehaviorSubject<MarketSort>(DEFAULT_MARKET_SORT);

  readonly filter$ = this.filterSubject.asObservable();
  readonly sort$ = this.sortSubject.asObservable();

  readonly markets$ = this.refreshSubject.pipe(
    startWith(undefined),
    switchMap(() => this.marketService.findAll()),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );

  readonly filteredMarkets$ = combineLatest([this.markets$, this.filter$]).pipe(
    combineLatestWith(this.sort$),
    map(([[markets, filter], sort]) => this.marketDiscovery.filterAndSort(markets, filter, sort)),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );

  readonly marketOptions$ = this.markets$.pipe(
    map((markets) => ({
      providers: this.uniqueValues(markets.map((market) => market.provider)),
      baseAssets: this.uniqueValues(markets.map((market) => market.baseAsset)),
      quoteAssets: this.uniqueValues(markets.map((market) => market.quoteAsset)),
      statuses: this.uniqueValues(markets.map((market) => market.marketState?.tradingStatus)),
    })),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  openMarket(marketId: string): void {
    void this.router.navigate(['/markets', marketId]);
  }

  applyFilter(filter: MarketFilter): void {
    this.filterSubject.next(filter);
  }

  applySort(sort: MarketSort): void {
    this.sortSubject.next(sort);
  }

  refreshMarkets(): void {
    this.refreshSubject.next();
  }

  private uniqueValues(values: readonly (string | null | undefined)[]): string[] {
    return [...new Set(values.filter((value): value is string => Boolean(value)))].sort((a, b) =>
      a.localeCompare(b),
    );
  }
}

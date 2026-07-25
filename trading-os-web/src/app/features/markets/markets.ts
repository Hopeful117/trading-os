import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import {
  BehaviorSubject,
  combineLatest,
  map,
  shareReplay,
  startWith,
  Subject,
  switchMap,
} from 'rxjs';

import { MarketFilter } from '../../core/models/market-filter.model';
import { MarketResponse } from '../../core/models/market-response';
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
  private readonly router = inject(Router);

  private readonly refreshSubject = new Subject<void>();

  private readonly filterSubject = new BehaviorSubject<MarketFilter>({
    search: '',
  });

  readonly filter$ = this.filterSubject.asObservable();

  readonly markets$ = this.refreshSubject.pipe(
    startWith(undefined),
    switchMap(() => this.marketService.findAll()),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );

  readonly filteredMarkets$ = combineLatest([this.markets$, this.filter$]).pipe(
    map(([markets, filter]) => this.filterMarkets(markets, filter)),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );

  openMarket(marketId: string): void {
    void this.router.navigate(['/markets', marketId]);
  }

  applyFilter(filter: MarketFilter): void {
    this.filterSubject.next(filter);
  }

  refreshMarkets(): void {
    this.refreshSubject.next();
  }

  private filterMarkets(markets: MarketResponse[], filter: MarketFilter): MarketResponse[] {
    const search = filter.search.trim().toLowerCase();

    if (!search) {
      return markets;
    }

    return markets.filter((market) =>
      [market.symbol, market.baseAsset, market.quoteAsset, market.provider].some((value) =>
        value?.toLowerCase().includes(search),
      ),
    );
  }
}

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
    switchMap((market) =>
      this.marketService.subscribe(market.marketId, this.tickerRequest).pipe(
        tap(() => {
          this.subscribedMarketId = market.marketId;
        }),
        switchMap(() => this.marketDataStreamService.streamTicker(market.symbol)),
      ),
    ),
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
}

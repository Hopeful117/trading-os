import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { AsyncPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { distinctUntilChanged, filter, map,shareReplay, switchMap, tap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import {MarketService} from '../../../core/services/market.service';
import {MarketStreamType} from '../../../core/models/market-stream-type';
import { MarketStreamRequest } from '../../../core/models/market-stream-request';
import {MarketDataStreamService} from '../../../core/services/market-data-stream.service';


@Component({
  selector: 'app-market-details',
  imports: [AsyncPipe, RouterLink, DatePipe],
  templateUrl: './markets-details.html',
  styleUrl: './markets-details.scss',
})
export class MarketDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly marketService = inject(MarketService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly marketDataStreamService = inject(MarketDataStreamService);

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

  ngOnInit(): void {
    this.marketId$
      .pipe(
        switchMap((marketId) =>
          this.marketService.subscribe(marketId, this.tickerRequest).pipe(map(() => marketId)),
        ),
        tap((marketId) => {
          this.destroyRef.onDestroy(() => {
            this.marketService.unsubscribe(marketId, this.tickerRequest).subscribe({
              error: (error) => {
                console.error('Unable to unsubscribe from market ticker', error);
              },
            });
          });
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        error: (error) => {
          console.error('Unable to subscribe to market ticker', error);
        },
      });
  }
  readonly ticker$ = this.market$.pipe(
    switchMap((market) => this.marketDataStreamService.streamTicker(market.symbol)),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );
}

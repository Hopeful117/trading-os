import { AsyncPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, map, Observable, of, shareReplay, startWith, Subject, switchMap } from 'rxjs';

import { MarketResponse, marketProductTypeLabel } from '../../core/models/market-response';
import { OpportunityResponse } from '../../core/models/opportunity.model';
import { MarketService } from '../../core/services/market.service';
import { OpportunityService } from '../../core/services/opportunity.service';
import { TradePlanService } from '../../core/services/trade-plan.service';
import {
  tradePreparationFailure,
  TradePreparationFailure,
} from '../../core/utils/trade-flow-error';
import { ScanPanel } from './scan-panel/scan-panel';

export type OpportunitiesView =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'loaded'; opportunities: OpportunityResponse[] };

@Component({
  selector: 'app-opportunities',
  imports: [AsyncPipe, DatePipe, DecimalPipe, ScanPanel],
  templateUrl: './opportunities.html',
  styleUrl: './opportunities.scss',
})
export class Opportunities {
  private readonly opportunityService = inject(OpportunityService);
  private readonly marketService = inject(MarketService);
  private readonly router = inject(Router);
  private readonly tradePlanService = inject(TradePlanService);

  selectedAccountId = '';
  openingOpportunityId: string | null = null;
  preparationErrors: Record<string, TradePreparationFailure> = {};

  private readonly refreshSubject = new Subject<void>();
  private readonly refresh$ = this.refreshSubject.pipe(startWith(undefined));

  readonly marketsById$: Observable<Map<string, MarketResponse>> = this.marketService.findAll().pipe(
    map((markets) => new Map(markets.map((market) => [market.marketId, market]))),
    catchError(() => of(new Map<string, MarketResponse>())),
    startWith(new Map<string, MarketResponse>()),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly view$ = this.refresh$.pipe(
    switchMap(() =>
      this.opportunityService.findActive().pipe(
        map((opportunities) => ({ status: 'loaded' as const, opportunities })),
        catchError(() => of({ status: 'error' as const })),
        startWith({ status: 'loading' as const }),
      ),
    ),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );

  openOpportunity(opportunity: OpportunityResponse): void {
    const accountId = this.selectedAccountId || opportunity.accountId || '';
    if (!accountId) {
      void this.router.navigate(['/opportunities', opportunity.id]);
      return;
    }

    this.openingOpportunityId = opportunity.id;
    this.tradePlanService
      .createFromOpportunity(opportunity.id, accountId, `${opportunity.id}:${accountId}`)
      .subscribe({
        next: (created) => {
          this.openingOpportunityId = null;
          const remainingErrors = { ...this.preparationErrors };
          delete remainingErrors[opportunity.id];
          this.preparationErrors = remainingErrors;
           const commands = [
             '/trade-planning',
             'plans',
             created.tradePlanId,
             'versions',
             created.tradePlanVersion,
           ];
           const navigation = opportunity.marketId
             ? { queryParams: { marketId: opportunity.marketId } }
             : undefined;
           void (navigation ? this.router.navigate(commands, navigation) : this.router.navigate(commands));
        },
        error: (error: unknown) => {
          this.openingOpportunityId = null;
          const failure = tradePreparationFailure(error);
          if (failure) {
            this.preparationErrors = {
              ...this.preparationErrors,
              [opportunity.id]: failure,
            };
          }
        },
      });
  }

  accountSelected(accountId: string): void {
    this.selectedAccountId = accountId;
  }

  refreshOpportunities(): void {
    this.refreshSubject.next();
  }

  metricEntries(failure: TradePreparationFailure): [string, number][] {
    return Object.entries(failure.metrics) as [string, number][];
  }

  marketProductType(marketId: string | null | undefined, marketsById: Map<string, MarketResponse>): string {
    return marketProductTypeLabel(marketId ? marketsById.get(marketId)?.productType : undefined);
  }
}

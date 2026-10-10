import { AsyncPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, combineLatest, map, of, shareReplay, startWith, switchMap } from 'rxjs';

import { OpportunityResponse } from '../../../core/models/opportunity.model';
import { OpportunityService } from '../../../core/services/opportunity.service';
import { TradePlanService } from '../../../core/services/trade-plan.service';
import {
  tradeFlowErrorMessage,
  tradePreparationFailure,
  TradePreparationFailure,
} from '../../../core/utils/trade-flow-error';

export type OpportunityDetailView =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'notFound' }
  | { status: 'loaded'; opportunity: OpportunityResponse; accountId: string | null };

@Component({
  selector: 'app-opportunity-details',
  imports: [AsyncPipe, DatePipe, DecimalPipe, RouterLink],
  templateUrl: './opportunity-details.html',
  styleUrl: './opportunity-details.scss',
})
export class OpportunityDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly opportunityService = inject(OpportunityService);
  private readonly tradePlanService = inject(TradePlanService);

  preparing = false;
  preparationError = false;
  preparationErrorMessage = '';
  preparationFailure: TradePreparationFailure | null = null;

  readonly view$ = combineLatest([this.route.paramMap, this.route.queryParamMap]).pipe(
    map(([params, queryParams]) => ({
      opportunityId: params.get('opportunityId'),
      accountId: queryParams.get('accountId'),
    })),
    switchMap(({ opportunityId, accountId }) => {
      if (opportunityId === null) {
        return of<OpportunityDetailView>({ status: 'notFound' });
      }

      return this.opportunityService.findById(opportunityId).pipe(
        map((opportunity) => ({
          status: 'loaded' as const,
          opportunity,
          accountId: opportunity.accountId ?? accountId,
        })),
        catchError((error: unknown) =>
          of<OpportunityDetailView>(
            error instanceof HttpErrorResponse && error.status === 404
              ? { status: 'notFound' }
              : { status: 'error' },
          ),
        ),
        startWith<OpportunityDetailView>({ status: 'loading' }),
      );
    }),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );

  prepareTradePlan(opportunityId: string, accountId: string): void {
    if (this.preparing || !accountId) {
      return;
    }

    this.preparing = true;
    this.preparationError = false;
    this.preparationFailure = null;
    this.preparationErrorMessage = '';
    this.tradePlanService
      .createFromOpportunity(opportunityId, accountId, `${opportunityId}:${accountId}`)
      .subscribe({
        next: (created) => {
          this.preparing = false;
          void this.router.navigate([
            '/trade-planning',
            'plans',
            created.tradePlanId,
            'versions',
            created.tradePlanVersion,
          ]);
        },
        error: (error: unknown) => {
          this.preparing = false;
          this.preparationError = true;
          this.preparationFailure = tradePreparationFailure(error);
          this.preparationErrorMessage = tradeFlowErrorMessage(
            error,
            'The Trade Plan could not be prepared. Try again later.',
          );
        },
      });
  }

  metricEntries(failure: TradePreparationFailure): [string, number][] {
    return Object.entries(failure.metrics) as [string, number][];
  }
}

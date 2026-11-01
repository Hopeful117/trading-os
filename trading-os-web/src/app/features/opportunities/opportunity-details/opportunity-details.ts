import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, combineLatest, map, of, shareReplay, startWith, switchMap, tap } from 'rxjs';

import { OpportunityResponse } from '../../../core/models/opportunity.model';
import { OpportunityService } from '../../../core/services/opportunity.service';

export type OpportunityDetailView = { status: 'loading' } | { status: 'error' };

@Component({
  selector: 'app-opportunity-details',
  imports: [AsyncPipe, RouterLink],
  templateUrl: './opportunity-details.html',
  styleUrl: './opportunity-details.scss',
})
export class OpportunityDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly opportunityService = inject(OpportunityService);

  readonly view$ = combineLatest([this.route.paramMap, this.route.queryParamMap]).pipe(
    map(([params, queryParams]) => ({
      opportunityId: params.get('opportunityId'),
      accountId: queryParams.get('accountId'),
    })),
    switchMap(({ opportunityId, accountId }) => {
      if (opportunityId === null) {
        return of<OpportunityDetailView>({ status: 'error' });
      }

      return this.opportunityService.findById(opportunityId).pipe(
        tap((opportunity) => this.openCockpit(opportunity, accountId)),
        map((): OpportunityDetailView => ({ status: 'loading' })),
        catchError(() => of<OpportunityDetailView>({ status: 'error' })),
        startWith<OpportunityDetailView>({ status: 'loading' }),
      );
    }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  private openCockpit(opportunity: OpportunityResponse, accountId: string | null): void {
    void this.router.navigate(['/decision-workspace'], {
      queryParams: {
        opportunityId: opportunity.id,
        opportunityVersion: opportunity.version,
        marketId: opportunity.marketId,
        ...(accountId || opportunity.accountId
          ? { accountId: accountId || opportunity.accountId }
          : {}),
      },
    });
  }
}

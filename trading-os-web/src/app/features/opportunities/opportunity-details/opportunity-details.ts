import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, map, of, shareReplay, startWith, switchMap } from 'rxjs';

import { OpportunityResponse } from '../../../core/models/opportunity.model';
import { OpportunityService } from '../../../core/services/opportunity.service';

export type OpportunityDetailView =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'notFound' }
  | { status: 'loaded'; opportunity: OpportunityResponse };

@Component({
  selector: 'app-opportunity-details',
  imports: [AsyncPipe, DatePipe, RouterLink],
  templateUrl: './opportunity-details.html',
  styleUrl: './opportunity-details.scss',
})
export class OpportunityDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly opportunityService = inject(OpportunityService);

  readonly view$ = this.route.paramMap.pipe(
    map((params) => params.get('opportunityId')),
    switchMap((opportunityId) => {
      if (opportunityId === null) {
        return of<OpportunityDetailView>({ status: 'notFound' });
      }

      return this.opportunityService.findById(opportunityId).pipe(
        map((opportunity) => ({ status: 'loaded' as const, opportunity })),
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
}

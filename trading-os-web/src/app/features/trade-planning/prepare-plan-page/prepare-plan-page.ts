import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  catchError,
  combineLatest,
  map,
  merge,
  Observable,
  of,
  shareReplay,
  startWith,
  Subject,
  switchMap,
} from 'rxjs';

import { Account } from '../../../core/models/account.model';
import { OpportunityResponse } from '../../../core/models/opportunity.model';
import { AccountService } from '../../../core/services/account.service';
import { OpportunityService } from '../../../core/services/opportunity.service';
import { TradePlanService } from '../../../core/services/trade-plan.service';

export type PreparePlanView =
  | { status: 'loading' }
  | { status: 'error' }
  | {
      status: 'ready';
      opportunity: OpportunityResponse;
      accounts: Account[];
    }
  | { status: 'creating' };

@Component({
  selector: 'app-prepare-plan-page',
  imports: [AsyncPipe, DatePipe, FormsModule, RouterLink],
  templateUrl: './prepare-plan-page.html',
  styleUrl: './prepare-plan-page.scss',
})
export class PreparePlanPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly opportunityService = inject(OpportunityService);
  private readonly accountService = inject(AccountService);
  private readonly tradePlanService = inject(TradePlanService);

  accountId = '';

  private readonly createSubject = new Subject<PreparePlanView>();

  readonly view$: Observable<PreparePlanView>;
  readonly busy$: Observable<boolean>;

  constructor() {
    const opportunityId$ = this.route.paramMap.pipe(
      map((params) => params.get('opportunityId')),
      shareReplay({ bufferSize: 1, refCount: true }),
    );

    const dataView$ = opportunityId$.pipe(
      switchMap((opportunityId) => {
        if (opportunityId === null) {
          return of<PreparePlanView>({ status: 'error' });
        }
        return combineLatest([
          this.opportunityService.findById(opportunityId),
          this.accountService.getAccounts().pipe(catchError(() => of([] as Account[]))),
        ]).pipe(
          map(([opportunity, accounts]) =>
            opportunity.status !== 'ACTIVE'
              ? ({ status: 'error' } as PreparePlanView)
              : ({ status: 'ready', opportunity, accounts } as PreparePlanView),
          ),
          catchError(() => of<PreparePlanView>({ status: 'error' })),
        );
      }),
      startWith<PreparePlanView>({ status: 'loading' }),
    );

    this.view$ = merge(dataView$, this.createSubject).pipe(
      shareReplay({ bufferSize: 1, refCount: true }),
    );

    this.busy$ = this.view$.pipe(
      map((view) => view.status === 'loading' || view.status === 'creating'),
    );
  }

  createPlan(opportunityId: string): void {
    if (!this.accountId) {
      return;
    }
    this.createSubject.next({ status: 'creating' } as PreparePlanView);

    this.tradePlanService
      .createFromOpportunity(opportunityId, this.accountId, crypto.randomUUID())
      .pipe(
        map((created) => {
          void this.router.navigate([
            '/trade-planning',
            'plans',
            created.tradePlanId,
            'versions',
            created.tradePlanVersion,
          ]);
        }),
        catchError(() => of(void 0)),
      )
      .subscribe();
  }
}

import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  catchError,
  combineLatest,
  finalize,
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
import { tradeFlowErrorMessage } from '../../../core/utils/trade-flow-error';

export type PreparePlanView =
  | { status: 'loading' }
  | {
      status: 'error';
      message: string;
      retryable: boolean;
      opportunityId: string | null;
      retryAction?: 'LOAD' | 'CREATE';
    }
  | {
      status: 'ready';
      opportunity: OpportunityResponse;
      accounts: Account[];
    }
  | { status: 'creating'; opportunityId: string };

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
  private readonly retryLoadSubject = new Subject<void>();
  private creating = false;

  readonly view$: Observable<PreparePlanView>;
  readonly busy$: Observable<boolean>;

  constructor() {
    const opportunityId$ = this.route.paramMap.pipe(
      map((params) => params.get('opportunityId')),
      shareReplay({ bufferSize: 1, refCount: true }),
    );

    const dataView$ = this.retryLoadSubject.pipe(
      startWith(void 0),
      switchMap(() => opportunityId$),
      switchMap((opportunityId) => {
        if (opportunityId === null) {
          return of<PreparePlanView>({
            status: 'error',
            message: 'The opportunity could not be identified.',
            retryable: false,
            opportunityId: null,
          });
        }
        return combineLatest([
          this.opportunityService.findById(opportunityId),
          this.accountService.getAccounts().pipe(catchError(() => of([] as Account[]))),
        ]).pipe(
          map(([opportunity, accounts]) =>
            opportunity.status !== 'ACTIVE'
              ? ({
                  status: 'error',
                  message: 'The opportunity must still be active to create a trade plan.',
                  retryable: false,
                  opportunityId,
                } as PreparePlanView)
              : ({ status: 'ready', opportunity, accounts } as PreparePlanView),
          ),
          catchError((error: unknown) =>
            of<PreparePlanView>({
              status: 'error',
              message: tradeFlowErrorMessage(error, 'The opportunity could not be loaded.'),
              retryable: true,
              opportunityId,
              retryAction: 'LOAD',
            }),
          ),
        );
      }),
      startWith<PreparePlanView>({ status: 'loading' }),
    );

    const create$ = this.createSubject.pipe(
      switchMap((view) => {
        if (view.status !== 'creating') return of(view);

        return this.tradePlanService
          .createFromOpportunity(view.opportunityId, this.accountId, crypto.randomUUID())
          .pipe(
            map((created) => {
              void this.router.navigate([
                '/trade-planning',
                'plans',
                created.tradePlanId,
                'versions',
                created.tradePlanVersion,
              ]);
              return view;
            }),
            catchError((error: unknown) =>
              of<PreparePlanView>({
                status: 'error',
                message: tradeFlowErrorMessage(error, 'The trade plan could not be created.'),
                retryable: true,
                opportunityId: view.opportunityId,
                retryAction: 'CREATE',
              }),
            ),
            startWith<PreparePlanView>(view),
            finalize(() => (this.creating = false)),
          );
      }),
    );

    this.view$ = merge(dataView$, create$).pipe(shareReplay({ bufferSize: 1, refCount: true }));

    this.busy$ = this.view$.pipe(
      map((view) => view.status === 'loading' || view.status === 'creating'),
    );
  }

  createPlan(opportunityId: string): void {
    if (!this.accountId || this.creating) {
      return;
    }
    this.creating = true;
    this.createSubject.next({ status: 'creating', opportunityId });
  }

  retryLoad(): void {
    this.retryLoadSubject.next();
  }

  retryCreate(opportunityId: string | null): void {
    if (opportunityId) this.createPlan(opportunityId);
  }
}

import { AsyncPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  catchError,
  map,
  merge,
  Observable,
  of,
  shareReplay,
  startWith,
  Subject,
  switchMap,
} from 'rxjs';

import { RiskDecisionResponse, TradePlanResponse } from '../../../core/models/trade-plan.model';
import { ExecutionDto } from '../../../core/models/execution.model';
import { TradePlanService } from '../../../core/services/trade-plan.service';
import { ExecutionService } from '../../../core/services/execution.service';

export type PlanView =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'proposal'; plan: TradePlanResponse }
  | { status: 'deciding' }
  | { status: 'accepted'; plan: TradePlanResponse }
  | { status: 'evaluatingRisk' }
  | { status: 'rejected'; plan: TradePlanResponse }
  | { status: 'riskDecision'; plan: TradePlanResponse; decision: RiskDecisionResponse }
  | { status: 'executionReady'; plan: TradePlanResponse; decision: RiskDecisionResponse }
  | { status: 'executionSubmitting' }
  | { status: 'executionResult'; execution: ExecutionDto };

@Component({
  selector: 'app-plan-page',
  imports: [AsyncPipe, DatePipe, DecimalPipe, RouterLink],
  templateUrl: './plan-page.html',
  styleUrl: './plan-page.scss',
})
export class PlanPage {
  private readonly route = inject(ActivatedRoute);
  private readonly tradePlanService = inject(TradePlanService);
  private readonly executionService = inject(ExecutionService);

  private readonly acceptSubject = new Subject<TradePlanResponse>();
  private readonly rejectSubject = new Subject<TradePlanResponse>();
  private readonly evaluateRiskSubject = new Subject<TradePlanResponse>();
  private readonly executeSubject = new Subject<{
    plan: TradePlanResponse;
    decision: RiskDecisionResponse;
  }>();

  readonly view$: Observable<PlanView>;
  readonly busy$: Observable<boolean>;

  constructor() {
    const plan$ = this.route.paramMap.pipe(
      switchMap((params) => {
        const planId = params.get('planId');
        const version = Number(params.get('version'));
        if (!planId || isNaN(version)) {
          return of<PlanView>({ status: 'error' });
        }
        return this.tradePlanService.getPlan(planId, version).pipe(
          map((plan) => this.toViewForPlan(plan)),
          catchError(() => of<PlanView>({ status: 'error' })),
        );
      }),
      startWith<PlanView>({ status: 'loading' }),
    );

    const accept$ = this.acceptSubject.pipe(
      switchMap((plan) =>
        this.tradePlanService.decide(plan.id, plan.version, 'ACCEPT').pipe(
          map((updated) => this.toViewForPlan(updated)),
          catchError(() => of<PlanView>({ status: 'error' })),
        ),
      ),
    );

    const reject$ = this.rejectSubject.pipe(
      switchMap((plan) =>
        this.tradePlanService.decide(plan.id, plan.version, 'REJECT').pipe(
          map((updated) => this.toViewForPlan(updated)),
          catchError(() => of<PlanView>({ status: 'error' })),
        ),
      ),
    );

    const evaluateRisk$ = this.evaluateRiskSubject.pipe(
      switchMap((plan) => {
        const accountId = plan.tradingAccountId;
        if (!accountId) {
          return of<PlanView>({ status: 'error' });
        }
        return this.tradePlanService
          .evaluateRisk(plan.id, plan.version, accountId, crypto.randomUUID())
          .pipe(
            map((decision): PlanView =>
              decision.approved
                ? { status: 'executionReady', plan, decision }
                : { status: 'riskDecision', plan, decision },
            ),
            catchError(() => of<PlanView>({ status: 'error' })),
          );
      }),
    );

    const execute$ = this.executeSubject.pipe(
      switchMap(({ plan, decision }) => {
        const idempotencyKey = crypto.randomUUID();
        const expiresAt = new Date(Date.now() + 3600_000).toISOString();
        return of<PlanView>({ status: 'executionSubmitting' }).pipe(
          switchMap(() =>
            this.executionService
              .validate(
                {
                  tradePlanId: plan.id,
                  tradePlanVersion: plan.version,
                  evaluationId: decision.evaluationId,
                  brokerAccountId: plan.tradingAccountId,
                  expiresAt,
                },
                idempotencyKey,
              )
              .pipe(
                switchMap((validated) =>
                  this.executionService.execute(validated.id).pipe(
                    map((execution) => ({
                      status: 'executionResult' as const,
                      execution,
                    })),
                    catchError(() => of<PlanView>({ status: 'error' })),
                  ),
                ),
                catchError(() => of<PlanView>({ status: 'error' })),
              ),
          ),
        );
      }),
    );

    this.view$ = merge(plan$, accept$, reject$, evaluateRisk$, execute$).pipe(
      shareReplay({ bufferSize: 1, refCount: true }),
    );

    this.busy$ = this.view$.pipe(
      map(
        (view) =>
          view.status === 'loading' ||
          view.status === 'deciding' ||
          view.status === 'evaluatingRisk' ||
          view.status === 'executionSubmitting',
      ),
    );
  }

  accept(plan: TradePlanResponse): void {
    this.acceptSubject.next(plan);
  }

  reject(plan: TradePlanResponse): void {
    this.rejectSubject.next(plan);
  }

  evaluateRisk(plan: TradePlanResponse): void {
    this.evaluateRiskSubject.next(plan);
  }

  execute(plan: TradePlanResponse, decision: RiskDecisionResponse): void {
    this.executeSubject.next({ plan, decision });
  }

  private toViewForPlan(plan: TradePlanResponse): PlanView {
    switch (plan.status) {
      case 'PROPOSED':
        return { status: 'proposal', plan };
      case 'ACCEPTED':
        return { status: 'accepted', plan };
      case 'REJECTED':
        return { status: 'rejected', plan };
      case 'DRAFT':
        return { status: 'proposal', plan };
      default:
        return { status: 'accepted', plan };
    }
  }
}

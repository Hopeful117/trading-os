import { AsyncPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnDestroy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  catchError,
  finalize,
  map,
  merge,
  Observable,
  of,
  shareReplay,
  startWith,
  Subject,
  switchMap,
  timer,
  takeWhile,
  take,
} from 'rxjs';

import { RiskDecisionResponse, TradePlanResponse } from '../../../core/models/trade-plan.model';
import {
  ExecutionDto,
  ExecutionStatus,
  isTerminal,
  shouldPoll,
} from '../../../core/models/execution.model';
import { TradePlanService } from '../../../core/services/trade-plan.service';
import { ExecutionService } from '../../../core/services/execution.service';
import { tradeFlowErrorMessage } from '../../../core/utils/trade-flow-error';

export type PlanView =
  | { status: 'loading' }
  | {
      status: 'error';
      message: string;
      retryable: boolean;
      plan?: TradePlanResponse;
      retryAction?: 'ACCEPT' | 'REJECT' | 'RISK' | 'EXECUTE';
      decision?: RiskDecisionResponse;
    }
  | { status: 'proposal'; plan: TradePlanResponse }
  | { status: 'deciding' }
  | { status: 'accepted'; plan: TradePlanResponse }
  | { status: 'evaluatingRisk' }
  | { status: 'rejected'; plan: TradePlanResponse }
  | { status: 'riskDecision'; plan: TradePlanResponse; decision: RiskDecisionResponse }
  | { status: 'executionReady'; plan: TradePlanResponse; decision: RiskDecisionResponse }
  | { status: 'executionSubmitting' }
  | { status: 'executionPolling'; execution: ExecutionDto }
  | { status: 'executionResult'; execution: ExecutionDto }
  | { status: 'riskValidated'; plan: TradePlanResponse }
  | { status: 'readyToExecute'; plan: TradePlanResponse }
  | { status: 'executed'; plan: TradePlanResponse }
  | { status: 'expired'; plan: TradePlanResponse };

const STATUS_LABELS: Record<ExecutionStatus, string> = {
  CREATED: 'Created',
  VALIDATED: 'Validated',
  SUBMISSION_IN_PROGRESS: 'Submitting to broker',
  SUBMISSION_OUTCOME_UNKNOWN: 'Submission outcome uncertain',
  RECONCILIATION_IN_PROGRESS: 'Checking broker status',
  COMPLETED: 'Accepted by broker',
  FAILED: 'Execution failed',
  RECOVERY_BLOCKED: 'Unable to confirm broker outcome',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
  RISK_REVALIDATION_REJECTED: 'Execution rejected by risk revalidation',
  RISK_REVALIDATION_UNAVAILABLE: 'Risk revalidation unavailable',
};

const BROKER_ORDER_LABELS: Record<string, string> = {
  ACKNOWLEDGED: 'Acknowledged',
  PARTIALLY_FILLED: 'Partially filled',
  FILLED: 'Filled',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
  UNKNOWN: 'Unknown',
};

@Component({
  selector: 'app-plan-page',
  imports: [AsyncPipe, DatePipe, DecimalPipe, RouterLink],
  templateUrl: './plan-page.html',
  styleUrl: './plan-page.scss',
})
export class PlanPage implements OnDestroy {
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
  private readonly retrySubject = new Subject<string>();
  private readonly retryT1Subject = new Subject<string>();
  private readonly reconcileSubject = new Subject<string>();
  private readonly reloadSubject = new Subject<void>();

  readonly view$: Observable<PlanView>;
  readonly busy$: Observable<boolean>;

  private destroyed = false;
  private commandInFlight = false;

  constructor() {
    const plan$ = this.reloadSubject.pipe(
      startWith(void 0),
      switchMap(() =>
        this.route.paramMap.pipe(
          take(1),
          switchMap((params) => {
            const planId = params.get('planId');
            const version = Number(params.get('version'));
            if (!planId || isNaN(version)) {
              return of<PlanView>({
                status: 'error',
                message: 'The trade plan reference is invalid.',
                retryable: false,
              });
            }
            return this.tradePlanService.getPlan(planId, version).pipe(
              map((plan) => this.toViewForPlan(plan)),
              catchError((error: unknown) =>
                of<PlanView>({
                  status: 'error',
                  message: tradeFlowErrorMessage(error, 'The trade plan could not be loaded.'),
                  retryable: true,
                }),
              ),
            );
          }),
        ),
      ),
      startWith<PlanView>({ status: 'loading' }),
    );

    const accept$ = this.acceptSubject.pipe(
      switchMap((plan) =>
        this.tradePlanService.decide(plan.id, plan.version, 'ACCEPT').pipe(
          map((updated) => this.toViewForPlan(updated)),
          catchError((error: unknown) =>
            of<PlanView>({
              status: 'error',
              message: tradeFlowErrorMessage(error, 'The decision could not be recorded.'),
              retryable: true,
              plan,
              retryAction: 'ACCEPT',
            }),
          ),
          startWith<PlanView>({ status: 'deciding' }),
          finalize(() => (this.commandInFlight = false)),
        ),
      ),
    );

    const reject$ = this.rejectSubject.pipe(
      switchMap((plan) =>
        this.tradePlanService.decide(plan.id, plan.version, 'REJECT').pipe(
          map((updated) => this.toViewForPlan(updated)),
          catchError((error: unknown) =>
            of<PlanView>({
              status: 'error',
              message: tradeFlowErrorMessage(error, 'The decision could not be recorded.'),
              retryable: true,
              plan,
              retryAction: 'REJECT',
            }),
          ),
          startWith<PlanView>({ status: 'deciding' }),
          finalize(() => (this.commandInFlight = false)),
        ),
      ),
    );

    const evaluateRisk$ = this.evaluateRiskSubject.pipe(
      switchMap((plan) => {
        const accountId = plan.tradingAccountId;
        if (!accountId) {
          this.commandInFlight = false;
          return of<PlanView>({
            status: 'error',
            message: 'The trade plan has no trading account.',
            retryable: false,
            plan,
          });
        }
        return this.tradePlanService
          .evaluateRisk(plan.id, plan.version, accountId, crypto.randomUUID())
          .pipe(
            map((decision): PlanView =>
              decision.approved
                ? { status: 'executionReady', plan, decision }
                : { status: 'riskDecision', plan, decision },
            ),
            catchError((error: unknown) =>
              of<PlanView>({
                status: 'error',
                message: tradeFlowErrorMessage(error, 'Risk evaluation could not be completed.'),
                retryable: true,
                plan,
                retryAction: 'RISK',
              }),
            ),
            startWith<PlanView>({ status: 'evaluatingRisk' }),
            finalize(() => (this.commandInFlight = false)),
          );
      }),
    );

    const execute$ = this.executeSubject.pipe(
      switchMap(({ plan, decision }) => {
        const idempotencyKey = crypto.randomUUID();
        const expiresAt = new Date(Date.now() + 3600_000).toISOString();
        const executionFlow$ = this.executionService
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
              this.executionService
                .execute(validated.id)
                .pipe(switchMap((execution) => this.pollOrResult(execution))),
            ),
            catchError((error: unknown) =>
              of<PlanView>({
                status: 'error',
                message: tradeFlowErrorMessage(error, 'The trade could not be submitted.'),
                retryable: true,
                plan,
                retryAction: 'EXECUTE',
                decision,
              }),
            ),
          );
        return executionFlow$.pipe(
          startWith<PlanView>({ status: 'executionSubmitting' }),
          finalize(() => (this.commandInFlight = false)),
        );
      }),
    );

    const retry$ = this.retrySubject.pipe(
      switchMap((executionId) =>
        this.executionService.retry(executionId).pipe(
          switchMap((execution) => this.pollOrResult(execution)),
          catchError(() =>
            of<PlanView>({
              status: 'error',
              message: 'The execution retry could not be completed.',
              retryable: true,
            }),
          ),
        ),
      ),
    );

    const reconcile$ = this.reconcileSubject.pipe(
      switchMap((executionId) =>
        this.executionService.reconcile(executionId).pipe(
          switchMap((execution) => this.pollOrResult(execution)),
          catchError(() =>
            of<PlanView>({
              status: 'error',
              message: 'The broker status could not be reconciled.',
              retryable: true,
            }),
          ),
        ),
      ),
    );

    const retryT1$ = this.retryT1Subject.pipe(
      switchMap((executionId) =>
        this.executionService.retryT1(executionId).pipe(
          switchMap((execution) => this.pollOrResult(execution)),
          catchError(() =>
            of<PlanView>({
              status: 'error',
              message: 'The execution risk revalidation could not be retried.',
              retryable: true,
            }),
          ),
        ),
      ),
    );

    this.view$ = merge(
      plan$,
      accept$,
      reject$,
      evaluateRisk$,
      execute$,
      retry$,
      reconcile$,
      retryT1$,
    ).pipe(shareReplay({ bufferSize: 1, refCount: true }));

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

  ngOnDestroy(): void {
    this.destroyed = true;
  }

  accept(plan: TradePlanResponse): void {
    if (this.commandInFlight) return;
    this.commandInFlight = true;
    this.acceptSubject.next(plan);
  }

  reject(plan: TradePlanResponse): void {
    if (this.commandInFlight) return;
    this.commandInFlight = true;
    this.rejectSubject.next(plan);
  }

  evaluateRisk(plan: TradePlanResponse): void {
    if (this.commandInFlight) return;
    this.commandInFlight = true;
    this.evaluateRiskSubject.next(plan);
  }

  execute(plan: TradePlanResponse, decision: RiskDecisionResponse): void {
    if (this.commandInFlight) return;
    this.commandInFlight = true;
    this.executeSubject.next({ plan, decision });
  }

  retry(executionId: string): void {
    this.retrySubject.next(executionId);
  }

  retryT1(executionId: string): void {
    this.retryT1Subject.next(executionId);
  }

  retryLoad(): void {
    this.reloadSubject.next();
  }

  retryCommand(view: Extract<PlanView, { status: 'error' }>): void {
    if (!view.plan || !view.retryAction) return;
    switch (view.retryAction) {
      case 'ACCEPT':
        this.accept(view.plan);
        break;
      case 'REJECT':
        this.reject(view.plan);
        break;
      case 'RISK':
        this.evaluateRisk(view.plan);
        break;
      case 'EXECUTE':
        if (view.decision) this.execute(view.plan, view.decision);
        break;
    }
  }

  reconcile(executionId: string): void {
    this.reconcileSubject.next(executionId);
  }

  statusLabel(status: ExecutionStatus): string {
    return STATUS_LABELS[status] ?? status;
  }

  brokerOrderLabel(status: string | null): string {
    return status ? (BROKER_ORDER_LABELS[status] ?? status) : '';
  }

  private pollOrResult(execution: ExecutionDto): Observable<PlanView> {
    if (!shouldPoll(execution.status)) {
      return of<PlanView>({ status: 'executionResult', execution });
    }
    const startTime = Date.now();
    const maxDuration = 5 * 60 * 1000;
    return timer(0, 2000).pipe(
      switchMap(() => {
        const elapsed = Date.now() - startTime;
        if (elapsed > 30_000) {
          return timer(0, 5000);
        }
        return of(null);
      }),
      switchMap(() =>
        this.executionService.getExecution(execution.id).pipe(catchError(() => of(execution))),
      ),
      takeWhile(
        (exec) =>
          !this.destroyed &&
          !isTerminal(exec.status) &&
          exec.status !== 'FAILED' &&
          Date.now() - startTime < maxDuration,
        true,
      ),
      map((exec) =>
        shouldPoll(exec.status) && !isTerminal(exec.status) && exec.status !== 'FAILED'
          ? { status: 'executionPolling' as const, execution: exec }
          : { status: 'executionResult' as const, execution: exec },
      ),
    );
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
      case 'RISK_VALIDATED':
        return { status: 'riskValidated', plan };
      case 'READY_TO_EXECUTE':
        return { status: 'readyToExecute', plan };
      case 'EXECUTED':
        return { status: 'executed', plan };
      case 'EXPIRED':
        return { status: 'expired', plan };
      default:
        return {
          status: 'error',
          message: 'This trade plan has an unsupported state.',
          retryable: false,
          plan,
        };
    }
  }
}

import { AsyncPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnDestroy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  catchError,
  EMPTY,
  expand,
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
      retryAction?:
        | 'ACCEPT'
        | 'ACCEPT_AND_EVALUATE_RISK'
        | 'REJECT'
        | 'RISK'
        | 'EXECUTE'
        | 'AUTHORIZED_EXECUTE'
        | 'RETRY'
        | 'RETRY_T1'
        | 'RECONCILE';
      execution?: ExecutionDto;
      decision?: RiskDecisionResponse;
    }
  | { status: 'proposal'; plan: TradePlanResponse }
  | { status: 'deciding' }
  | { status: 'accepted'; plan: TradePlanResponse }
  | { status: 'evaluatingRisk' }
  | { status: 'rejected'; plan: TradePlanResponse }
  | { status: 'riskDecision'; plan: TradePlanResponse; decision: RiskDecisionResponse }
  | { status: 'executionReady'; plan: TradePlanResponse; decision: RiskDecisionResponse }
  | { status: 'authorizedExecution'; plan: TradePlanResponse; execution: ExecutionDto }
  | { status: 'executionSubmitting' }
  | { status: 'executionPolling'; plan: TradePlanResponse; execution: ExecutionDto }
  | { status: 'executionResult'; plan: TradePlanResponse; execution: ExecutionDto }
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
  private readonly acceptAndEvaluateRiskSubject = new Subject<TradePlanResponse>();
  private readonly rejectSubject = new Subject<TradePlanResponse>();
  private readonly evaluateRiskSubject = new Subject<TradePlanResponse>();
  private readonly executeSubject = new Subject<{
    plan: TradePlanResponse;
    decision: RiskDecisionResponse;
  }>();
  private readonly authorizedExecutionSubject = new Subject<{
    plan: TradePlanResponse;
    execution: ExecutionDto;
  }>();
  private readonly retrySubject = new Subject<{
    executionId: string;
    plan: TradePlanResponse;
  }>();
  private readonly retryT1Subject = new Subject<{
    executionId: string;
    plan: TradePlanResponse;
  }>();
  private readonly reconcileSubject = new Subject<{
    executionId: string;
    plan: TradePlanResponse;
  }>();
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
              switchMap((plan) => this.loadPlanView(plan)),
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

    const acceptAndEvaluateRisk$ = this.acceptAndEvaluateRiskSubject.pipe(
      switchMap((plan) =>
        this.tradePlanService.decide(plan.id, plan.version, 'ACCEPT').pipe(
          switchMap((accepted) => {
            const accountId = accepted.tradingAccountId;
            if (!accountId) {
              return of<PlanView>({
                status: 'error',
                message: 'The accepted trade plan has no trading account.',
                retryable: false,
                plan: accepted,
              });
            }

            return this.tradePlanService
              .evaluateRisk(accepted.id, accepted.version, accountId, crypto.randomUUID())
              .pipe(
                map((decision): PlanView =>
                  decision.approved
                    ? { status: 'executionReady', plan: accepted, decision }
                    : { status: 'riskDecision', plan: accepted, decision },
                ),
                catchError((error: unknown) =>
                  of<PlanView>({
                    status: 'error',
                    message: tradeFlowErrorMessage(
                      error,
                      'Risk evaluation could not be completed after acceptance.',
                    ),
                    retryable: true,
                    plan: accepted,
                    retryAction: 'RISK',
                  }),
                ),
                startWith<PlanView>({ status: 'evaluatingRisk' }),
              );
          }),
          catchError((error: unknown) =>
            of<PlanView>({
              status: 'error',
              message: tradeFlowErrorMessage(error, 'The decision could not be recorded.'),
              retryable: true,
              plan,
              retryAction: 'ACCEPT_AND_EVALUATE_RISK',
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
                .pipe(switchMap((execution) => this.pollOrResult(plan, execution))),
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

    const authorizedExecution$ = this.authorizedExecutionSubject.pipe(
      switchMap(({ plan, execution }) =>
        this.executionService.execute(execution.id).pipe(
          switchMap((updated) => this.pollOrResult(plan, updated)),
          catchError((error: unknown) =>
            of<PlanView>({
              status: 'error',
              message: tradeFlowErrorMessage(error, 'The authorized trade could not be submitted.'),
              retryable: true,
              plan,
              retryAction: 'AUTHORIZED_EXECUTE',
              execution,
            }),
          ),
          finalize(() => (this.commandInFlight = false)),
        ),
      ),
    );

    const retry$ = this.retrySubject.pipe(
      switchMap(({ executionId, plan }) =>
        this.executionService.retry(executionId).pipe(
          switchMap((execution) => this.pollOrResult(plan, execution)),
          catchError((error: unknown) =>
            of<PlanView>({
              status: 'error',
              message: tradeFlowErrorMessage(error, 'The execution retry could not be completed.'),
              retryable: true,
              plan,
              retryAction: 'RETRY',
              execution: { id: executionId } as ExecutionDto,
            }),
          ),
          startWith<PlanView>({ status: 'executionSubmitting' }),
          finalize(() => (this.commandInFlight = false)),
        ),
      ),
    );

    const reconcile$ = this.reconcileSubject.pipe(
      switchMap(({ executionId, plan }) =>
        this.executionService.reconcile(executionId).pipe(
          switchMap((execution) => this.pollOrResult(plan, execution)),
          catchError((error: unknown) =>
            of<PlanView>({
              status: 'error',
              message: tradeFlowErrorMessage(error, 'The broker status could not be reconciled.'),
              retryable: true,
              plan,
              retryAction: 'RECONCILE',
              execution: { id: executionId } as ExecutionDto,
            }),
          ),
          startWith<PlanView>({ status: 'executionSubmitting' }),
          finalize(() => (this.commandInFlight = false)),
        ),
      ),
    );

    const retryT1$ = this.retryT1Subject.pipe(
      switchMap(({ executionId, plan }) =>
        this.executionService.retryT1(executionId).pipe(
          switchMap((execution) => this.pollOrResult(plan, execution)),
          catchError((error: unknown) =>
            of<PlanView>({
              status: 'error',
              message: tradeFlowErrorMessage(
                error,
                'The execution risk revalidation could not be retried.',
              ),
              retryable: true,
              plan,
              retryAction: 'RETRY_T1',
              execution: { id: executionId } as ExecutionDto,
            }),
          ),
          startWith<PlanView>({ status: 'executionSubmitting' }),
          finalize(() => (this.commandInFlight = false)),
        ),
      ),
    );

    this.view$ = merge(
      plan$,
      accept$,
      acceptAndEvaluateRisk$,
      reject$,
      evaluateRisk$,
      execute$,
      authorizedExecution$,
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

  acceptAndEvaluateRisk(plan: TradePlanResponse): void {
    if (this.commandInFlight) return;
    this.commandInFlight = true;
    this.acceptAndEvaluateRiskSubject.next(plan);
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

  executeAuthorized(plan: TradePlanResponse, execution: ExecutionDto): void {
    if (this.commandInFlight) return;
    this.commandInFlight = true;
    this.authorizedExecutionSubject.next({ plan, execution });
  }

  retry(executionId: string, plan: TradePlanResponse): void {
    if (this.commandInFlight) return;
    this.commandInFlight = true;
    this.retrySubject.next({ executionId, plan });
  }

  retryT1(executionId: string, plan: TradePlanResponse): void {
    if (this.commandInFlight) return;
    this.commandInFlight = true;
    this.retryT1Subject.next({ executionId, plan });
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
      case 'ACCEPT_AND_EVALUATE_RISK':
        this.acceptAndEvaluateRisk(view.plan);
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
      case 'AUTHORIZED_EXECUTE':
        if (view.execution) this.executeAuthorized(view.plan, view.execution);
        break;
      case 'RETRY':
        if (view.execution) this.retry(view.execution.id, view.plan);
        break;
      case 'RETRY_T1':
        if (view.execution) this.retryT1(view.execution.id, view.plan);
        break;
      case 'RECONCILE':
        if (view.execution) this.reconcile(view.execution.id, view.plan);
        break;
    }
  }

  reconcile(executionId: string, plan: TradePlanResponse): void {
    if (this.commandInFlight) return;
    this.commandInFlight = true;
    this.reconcileSubject.next({ executionId, plan });
  }

  statusLabel(status: ExecutionStatus): string {
    return STATUS_LABELS[status] ?? status;
  }

  brokerOrderLabel(status: string | null): string {
    return status ? (BROKER_ORDER_LABELS[status] ?? status) : '';
  }

  private pollOrResult(plan: TradePlanResponse, execution: ExecutionDto): Observable<PlanView> {
    if (
      !shouldPoll(execution.status) ||
      execution.status === 'SUBMISSION_OUTCOME_UNKNOWN' ||
      execution.status === 'RECOVERY_BLOCKED'
    ) {
      return of<PlanView>({ status: 'executionResult', plan, execution });
    }
    const startTime = Date.now();
    const maxDuration = 5 * 60 * 1000;
    return of(execution).pipe(
      expand((current) => {
        if (
          this.destroyed ||
          isTerminal(current.status) ||
          current.status === 'FAILED' ||
          current.status === 'SUBMISSION_OUTCOME_UNKNOWN' ||
          current.status === 'RECOVERY_BLOCKED' ||
          Date.now() - startTime >= maxDuration
        ) {
          return EMPTY;
        }
        const delay = Date.now() - startTime > 30_000 ? 5000 : 2000;
        return timer(delay).pipe(
          switchMap(() =>
            this.executionService.getExecution(execution.id).pipe(catchError(() => of(current))),
          ),
        );
      }),
      takeWhile(
        (exec) =>
          !this.destroyed &&
          !isTerminal(exec.status) &&
          exec.status !== 'FAILED' &&
          exec.status !== 'SUBMISSION_OUTCOME_UNKNOWN' &&
          exec.status !== 'RECOVERY_BLOCKED' &&
          Date.now() - startTime < maxDuration,
        true,
      ),
      map((exec) => {
        const timedOut =
          Date.now() - startTime >= maxDuration &&
          shouldPoll(exec.status) &&
          exec.status !== 'SUBMISSION_OUTCOME_UNKNOWN' &&
          exec.status !== 'RECOVERY_BLOCKED';
        return shouldPoll(exec.status) &&
          !isTerminal(exec.status) &&
          exec.status !== 'FAILED' &&
          !timedOut
          ? { status: 'executionPolling' as const, plan, execution: exec }
          : {
              status: 'executionResult' as const,
              plan,
              execution: timedOut
                ? {
                    ...exec,
                    status: 'RECOVERY_BLOCKED' as const,
                    failureReason:
                      'The broker outcome could not be confirmed within the polling window.',
                  }
                : exec,
            };
      }),
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

  private loadPlanView(plan: TradePlanResponse): Observable<PlanView> {
    if (plan.status === 'EXECUTED') return this.loadExecutedPlanView(plan);
    if (plan.status !== 'READY_TO_EXECUTE') return of(this.toViewForPlan(plan));

    return this.executionService.list().pipe(
      switchMap((summaries) => {
        const existing = summaries.find(
          (execution) =>
            execution.tradePlanId === plan.id &&
            execution.tradePlanVersion === plan.version &&
            execution.status !== 'CANCELLED',
        );
        return existing
          ? this.executionService
              .getExecution(existing.id)
              .pipe(
                map((execution) =>
                  !shouldPoll(execution.status) ||
                  execution.status === 'SUBMISSION_OUTCOME_UNKNOWN' ||
                  execution.status === 'RECOVERY_BLOCKED'
                    ? { status: 'executionResult' as const, plan, execution }
                    : { status: 'authorizedExecution' as const, plan, execution },
                ),
              )
          : of<PlanView>(this.toViewForPlan(plan));
      }),
      catchError(() => of<PlanView>(this.toViewForPlan(plan))),
    );
  }

  private loadExecutedPlanView(plan: TradePlanResponse): Observable<PlanView> {
    return this.executionService.list().pipe(
      switchMap((executions) => {
        const matching = executions.find(
          (execution) =>
            execution.tradePlanId === plan.id && execution.tradePlanVersion === plan.version,
        );
        return matching
          ? this.executionService
              .getExecution(matching.id)
              .pipe(map((execution) => ({ status: 'executionResult' as const, plan, execution })))
          : of<PlanView>(this.toViewForPlan(plan));
      }),
      catchError(() => of<PlanView>(this.toViewForPlan(plan))),
    );
  }
}

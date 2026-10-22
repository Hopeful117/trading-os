import { AsyncPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, catchError, forkJoin, map, of, startWith, switchMap } from 'rxjs';
import {
  ExecutionDto,
  ExecutionStatus,
  ExecutionSummaryDto,
} from '../../../../core/models/execution.model';
import { TradePlanResponse } from '../../../../core/models/trade-plan.model';
import { ExecutionService } from '../../../../core/services/execution.service';
import { TradePlanService } from '../../../../core/services/trade-plan.service';
import { AccountService } from '../../../../core/services/account.service';
import { DecisionHistoryService } from '../../../../core/services/decision-history.service';
import { DecisionHistoryItem } from '../../../../core/models/decision-history.model';

interface HistoryViewModel {
  executions: ExecutionSummaryDto[];
  loading: boolean;
  error: boolean;
}

interface DetailState {
  loading: boolean;
  error: boolean;
  execution: ExecutionDto | null;
  plan: TradePlanResponse | null;
}

interface DecisionHistoryViewModel {
  decisions: DecisionHistoryItem[];
  loading: boolean;
  error: boolean;
}

@Component({
  selector: 'app-execution-history',
  imports: [AsyncPipe, DatePipe, DecimalPipe, RouterLink],
  templateUrl: './execution-history.html',
  styleUrl: './execution-history.scss',
})
export class ExecutionHistory {
  private readonly executionService = inject(ExecutionService);
  private readonly tradePlanService = inject(TradePlanService);
  private readonly accountService = inject(AccountService, { optional: true });
  private readonly decisionHistoryService = inject(DecisionHistoryService);
  private readonly details = signal<Record<string, DetailState>>({});

  readonly viewModel$: Observable<HistoryViewModel> = this.executionService.list().pipe(
    map((executions) => ({ executions, loading: false, error: false })),
    catchError(() => of({ executions: [], loading: false, error: true })),
    startWith({ executions: [], loading: true, error: false }),
  );

  readonly decisionHistory$: Observable<DecisionHistoryViewModel> = this.accountService
    ? this.accountService.getAccounts().pipe(
        switchMap((accounts) =>
          accounts.length === 0
            ? of([] as DecisionHistoryItem[])
            : forkJoin(accounts.map((account) => this.decisionHistoryService.list(account.accountId))).pipe(
                map((histories) => histories.flat()),
              ),
        ),
        map((decisions) => ({ decisions, loading: false, error: false })),
        catchError(() => of({ decisions: [], loading: false, error: true })),
        startWith({ decisions: [], loading: true, error: false }),
      )
    : of({ decisions: [], loading: false, error: false });

  detailFor(executionId: string): DetailState | null {
    return this.details()[executionId] ?? null;
  }

  loadDetails(summary: ExecutionSummaryDto): void {
    const current = this.detailFor(summary.id);
    if (current?.loading || current?.execution) {
      return;
    }

    this.updateDetail(summary.id, {
      loading: true,
      error: false,
      execution: null,
      plan: null,
    });

    this.executionService
      .getExecution(summary.id)
      .pipe(
        switchMap((execution) => {
          if (!execution.tradePlanId || execution.tradePlanVersion < 1) {
            return of({ execution, plan: null as TradePlanResponse | null });
          }
          return this.tradePlanService
            .getPlan(execution.tradePlanId, execution.tradePlanVersion)
            .pipe(
              map((plan) => ({ execution, plan })),
              catchError(() => of({ execution, plan: null as TradePlanResponse | null })),
            );
        }),
        catchError(() => of({ execution: null, plan: null })),
      )
      .subscribe(({ execution, plan }) => {
        this.updateDetail(summary.id, {
          loading: false,
          error: execution === null,
          execution,
          plan,
        });
      });
  }

  statusLabel(status: ExecutionStatus): string {
    const labels: Record<ExecutionStatus, string> = {
      CREATED: 'Créée',
      VALIDATED: 'Validée',
      SUBMISSION_IN_PROGRESS: 'Soumission en cours',
      SUBMISSION_OUTCOME_UNKNOWN: 'Résultat incertain',
      RECONCILIATION_IN_PROGRESS: 'Réconciliation en cours',
      COMPLETED: 'Terminée',
      FAILED: 'Échouée',
      RECOVERY_BLOCKED: 'Récupération bloquée',
      CANCELLED: 'Annulée',
      EXPIRED: 'Expirée',
      RISK_REVALIDATION_REJECTED: 'Rejetée par le risque',
      RISK_REVALIDATION_UNAVAILABLE: 'Risque indisponible',
    };
    return labels[status];
  }

  statusClass(status: ExecutionStatus): string {
    if (this.isUncertain(status)) return 'uncertain';
    if (status === 'COMPLETED') return 'completed';
    if (status === 'FAILED' || status === 'RISK_REVALIDATION_REJECTED') return 'failed';
    if (status === 'CANCELLED' || status === 'EXPIRED') return 'closed';
    return 'pending';
  }

  isUncertain(status: ExecutionStatus): boolean {
    return (
      status === 'SUBMISSION_OUTCOME_UNKNOWN' ||
      status === 'RECONCILIATION_IN_PROGRESS' ||
      status === 'RECOVERY_BLOCKED' ||
      status === 'RISK_REVALIDATION_UNAVAILABLE'
    );
  }

  private updateDetail(executionId: string, state: DetailState): void {
    this.details.update((details) => ({ ...details, [executionId]: state }));
  }
}

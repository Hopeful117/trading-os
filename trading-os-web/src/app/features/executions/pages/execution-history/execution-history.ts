import { AsyncPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, catchError, map, of, startWith, switchMap } from 'rxjs';
import {
  ExecutionDto,
  ExecutionStatus,
  ExecutionSummaryDto,
} from '../../../../core/models/execution.model';
import { TradePlanResponse } from '../../../../core/models/trade-plan.model';
import { ExecutionService } from '../../../../core/services/execution.service';
import { TradePlanService } from '../../../../core/services/trade-plan.service';

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

@Component({
  selector: 'app-execution-history',
  imports: [AsyncPipe, DatePipe, DecimalPipe, RouterLink],
  templateUrl: './execution-history.html',
  styleUrl: './execution-history.scss',
})
export class ExecutionHistory {
  private readonly executionService = inject(ExecutionService);
  private readonly tradePlanService = inject(TradePlanService);
  private readonly details = signal<Record<string, DetailState>>({});

  readonly viewModel$: Observable<HistoryViewModel> = this.executionService.list().pipe(
    map((executions) => ({ executions, loading: false, error: false })),
    catchError(() => of({ executions: [], loading: false, error: true })),
    startWith({ executions: [], loading: true, error: false }),
  );

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

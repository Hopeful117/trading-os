import { AsyncPipe, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import {
  BehaviorSubject,
  catchError,
  combineLatest,
  map,
  Observable,
  of,
  shareReplay,
  startWith,
  switchMap,
  timer,
} from 'rxjs';
import { v4 as uuidv4 } from 'uuid';
import { Account } from '../../../../core/models/account.model';
import {
  OpenPositionDashboardView,
  PositionProtectionStatus,
} from '../../../../core/models/dashboard-summary.model';
import {
  PositionCloseResponse,
  PositionCloseStatus,
  ReconciliationResult,
} from '../../../../core/models/position-close.model';
import { AccountService } from '../../../../core/services/account.service';
import { PositionService } from '../../../../core/services/position.service';

interface AccountsState {
  accounts: Account[];
  loading: boolean;
  error: boolean;
}

interface PositionCloseState {
  status: PositionCloseStatus | null;
  externalOrderId: string | null;
  failureReason: string | null;
  resolvedMutationScope: string | null;
  reconciliationResult: ReconciliationResult | null;
  commandId: string | null;
  showConfirmation: boolean;
}

interface PositionsViewModel {
  accountsState: AccountsState;
  selectedAccountId: string | null;
  positions: OpenPositionDashboardView[];
  positionsLoading: boolean;
  positionsError: string | null;
  closeStates: Map<string, PositionCloseState>;
}

@Component({
  selector: 'app-positions',
  imports: [AsyncPipe, CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './positions.html',
  styleUrl: './positions.scss',
})
export class Positions {
  private readonly accountService = inject(AccountService);
  private readonly positionService = inject(PositionService);
  private readonly selectedAccountId = new BehaviorSubject<string | null>(null);
  private readonly lastPositionsByAccount = new Map<string, OpenPositionDashboardView[]>();
  private readonly closeStates = new Map<string, PositionCloseState>();

  private readonly accountsState$: Observable<AccountsState> = this.accountService
    .getAccounts()
    .pipe(
      map((accounts) => ({ accounts, loading: false, error: false })),
      catchError(() => of({ accounts: [], loading: false, error: true })),
      startWith({ accounts: [], loading: true, error: false }),
      shareReplay({ bufferSize: 1, refCount: true }),
    );

  readonly viewModel$: Observable<PositionsViewModel> = combineLatest([
    this.accountsState$,
    this.selectedAccountId,
  ]).pipe(
    map(([accountsState, selectedAccountId]) => {
      const accounts = accountsState.accounts;
      const effectiveId =
        selectedAccountId && accounts.some((a) => a.accountId === selectedAccountId)
          ? selectedAccountId
          : (accounts[0]?.accountId ?? null);
      return { accountsState, selectedAccountId: effectiveId };
    }),
    switchMap(({ accountsState, selectedAccountId }) => {
      if (!selectedAccountId) {
        return of({
          accountsState,
          selectedAccountId,
          positions: [] as OpenPositionDashboardView[],
          positionsLoading: false,
          positionsError: null,
          closeStates: new Map(),
        });
      }

      return timer(0, 10_000).pipe(
        switchMap(() =>
          this.positionService.getPositions(selectedAccountId).pipe(
            map((positions) => {
              this.lastPositionsByAccount.set(selectedAccountId, positions);
              return {
                accountsState,
                selectedAccountId,
                positions,
                positionsLoading: false,
                positionsError: null,
                closeStates: this.closeStates,
              };
            }),
            catchError(() =>
              of({
                accountsState,
                selectedAccountId,
                positions: this.lastPositionsByAccount.get(selectedAccountId) ?? [],
                positionsLoading: false,
                positionsError: 'Les données des positions sont temporairement indisponibles.',
                closeStates: this.closeStates,
              }),
            ),
          ),
        ),
        startWith({
          accountsState,
          selectedAccountId,
          positions: [] as OpenPositionDashboardView[],
          positionsLoading: true,
          positionsError: null,
          closeStates: new Map(),
        }),
      );
    }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  selectAccount(accountId: string): void {
    this.selectedAccountId.next(accountId);
  }

  getCloseState(positionId: string): PositionCloseState {
    let state = this.closeStates.get(positionId);
    if (!state) {
      state = {
        status: null,
        externalOrderId: null,
        failureReason: null,
        resolvedMutationScope: null,
        reconciliationResult: null,
        commandId: null,
        showConfirmation: false,
      };
      this.closeStates.set(positionId, state);
    }
    return state;
  }

  showCloseConfirmation(position: OpenPositionDashboardView): void {
    const state = this.getCloseState(position.positionId);
    state.showConfirmation = true;
  }

  cancelCloseConfirmation(positionId: string): void {
    const state = this.getCloseState(positionId);
    state.showConfirmation = false;
  }

  confirmFullExposureClose(accountId: string, position: OpenPositionDashboardView): void {
    const state = this.getCloseState(position.positionId);
    const idempotencyKey = uuidv4();

    this.positionService.closePosition(accountId, position.positionId, idempotencyKey).subscribe({
      next: (response) => {
        state.status = response.status as PositionCloseStatus;
        state.externalOrderId = response.externalOrderId;
        state.failureReason = response.failureReason;
        state.resolvedMutationScope = response.resolvedMutationScope;
        state.reconciliationResult = response.reconciliationResult;
        state.commandId = response.commandId;
        state.showConfirmation = false;
      },
      error: (err) => {
        state.status = 'REJECTED';
        state.failureReason = err.error?.message ?? 'Erreur lors de la fermeture';
        state.showConfirmation = false;
      },
    });
  }

  reconcile(accountId: string, positionId: string): void {
    const state = this.getCloseState(positionId);
    if (!state.commandId) return;

    this.positionService.reconcileClose(accountId, state.commandId).subscribe({
      next: (response) => {
        state.status = response.status as PositionCloseStatus;
        state.reconciliationResult = response.reconciliationResult;
      },
      error: () => {
        // Keep current state on error
      },
    });
  }

  pnlClass(value: number | null): string {
    if (value === null || value === 0) {
      return '';
    }
    return value > 0 ? 'positive' : 'negative';
  }

  protectionStatusLabel(status: PositionProtectionStatus): string {
    switch (status) {
      case 'PROTECTED':
        return 'Protégé';
      case 'MISSING_STOP_LOSS':
        return 'Stop loss manquant';
      case 'UNKNOWN':
        return 'Inconnu';
    }
  }

  protectionStatusClass(status: PositionProtectionStatus): string {
    switch (status) {
      case 'PROTECTED':
        return 'protected';
      case 'MISSING_STOP_LOSS':
        return 'missing-sl';
      case 'UNKNOWN':
        return 'unknown';
    }
  }

  closeStatusLabel(status: PositionCloseStatus | null): string {
    if (!status) return '';
    switch (status) {
      case 'CREATED':
        return 'Créée';
      case 'SUBMITTED':
        return 'Soumise';
      case 'ACKNOWLEDGED':
        return 'Reconnue';
      case 'REJECTED':
        return 'Rejetée';
      case 'UNKNOWN':
        return 'Incertain';
      case 'CLOSED':
        return 'Fermée';
      case 'NOT_SUBMITTED':
        return 'Non soumise';
    }
  }

  closeStatusClass(status: PositionCloseStatus | null): string {
    if (!status) return '';
    switch (status) {
      case 'CREATED':
      case 'SUBMITTED':
        return 'pending';
      case 'ACKNOWLEDGED':
        return 'acknowledged';
      case 'REJECTED':
        return 'rejected';
      case 'UNKNOWN':
        return 'unknown';
      case 'CLOSED':
        return 'closed';
      case 'NOT_SUBMITTED':
        return 'not-submitted';
    }
  }

  isActiveStatus(status: PositionCloseStatus | null): boolean {
    return (
      status === 'CREATED' ||
      status === 'SUBMITTED' ||
      status === 'ACKNOWLEDGED' ||
      status === 'UNKNOWN'
    );
  }

  isReconcilable(status: PositionCloseStatus | null): boolean {
    return status === 'ACKNOWLEDGED' || status === 'UNKNOWN';
  }

  reconciliationLabel(result: ReconciliationResult | null): string {
    if (!result) return '';
    switch (result) {
      case 'EXPOSURE_CONFIRMED_ABSENT':
        return 'Exposition confirmée absente';
      case 'COMMAND_CONFIRMED_NOT_EXECUTED':
        return 'Commande non exécutée';
      case 'RECONCILIATION_INCONCLUSIVE':
        return 'Réconciliation inconclusive';
    }
  }
}

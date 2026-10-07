import { AsyncPipe, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import {
  BehaviorSubject,
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
  timer,
} from 'rxjs';
import { v4 as uuidv4 } from 'uuid';
import { Account } from '../../../../core/models/account.model';
import {
  OpenPositionDashboardView,
  PositionProtectionStatus,
  PositionSource,
} from '../../../../core/models/dashboard-summary.model';
import {
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
  accountId: string | null;
  positionId: string;
  symbol: string | null;
  source: PositionSource | null;
  status: PositionCloseStatus | null;
  externalOrderId: string | null;
  failureReason: string | null;
  resolvedMutationScope: string | null;
  reconciliationResult: ReconciliationResult | null;
  commandId: string | null;
  showConfirmation: boolean;
  inFlight: boolean;
}

interface PositionsViewModel {
  accountsState: AccountsState;
  selectedAccountId: string | null;
  accountSelectionError: string | null;
  positions: OpenPositionDashboardView[];
  positionsLoading: boolean;
  positionsError: string | null;
  closeStates: Map<string, PositionCloseState>;
  closeResults: PositionCloseState[];
}

@Component({
  selector: 'app-positions',
  imports: [AsyncPipe, CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './positions.html',
  styleUrl: './positions.scss',
})
export class Positions {
  private readonly route = inject(ActivatedRoute);
  private readonly accountService = inject(AccountService);
  private readonly positionService = inject(PositionService);
  private readonly selectedAccountId = new BehaviorSubject<string | null>(
    this.route.snapshot.queryParamMap.get('accountId'),
  );
  private readonly refreshPositions = new Subject<void>();
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
      const requestedAccountIsInvalid =
        selectedAccountId !== null && !accounts.some((a) => a.accountId === selectedAccountId);
      const effectiveId =
        selectedAccountId && !requestedAccountIsInvalid
          ? selectedAccountId
          : selectedAccountId === null
            ? (accounts[0]?.accountId ?? null)
            : null;
      return {
        accountsState,
        selectedAccountId: effectiveId,
        accountSelectionError: requestedAccountIsInvalid
          ? 'Le compte demandé n’est pas disponible pour cet utilisateur.'
          : null,
      };
    }),
    switchMap(({ accountsState, selectedAccountId, accountSelectionError }) => {
      if (!selectedAccountId) {
        return of({
          accountsState,
          selectedAccountId,
          accountSelectionError,
          positions: [] as OpenPositionDashboardView[],
          positionsLoading: false,
          positionsError: null,
          closeStates: new Map(),
          closeResults: this.closeResultStates(selectedAccountId),
        });
      }

      return merge(timer(0, 10_000), this.refreshPositions).pipe(
        switchMap(() =>
          this.positionService.getPositions(selectedAccountId).pipe(
            map((positions) => {
              this.lastPositionsByAccount.set(selectedAccountId, positions);
              return {
                accountsState,
                selectedAccountId,
                accountSelectionError,
                positions,
                positionsLoading: false,
                positionsError: null,
                closeStates: this.closeStates,
                closeResults: this.closeResultStates(selectedAccountId),
              };
            }),
            catchError(() =>
              of({
                accountsState,
                selectedAccountId,
                accountSelectionError,
                positions: this.lastPositionsByAccount.get(selectedAccountId) ?? [],
                positionsLoading: false,
                positionsError: 'Les données des positions sont temporairement indisponibles.',
                closeStates: this.closeStates,
                closeResults: this.closeResultStates(selectedAccountId),
              }),
            ),
          ),
        ),
        startWith({
          accountsState,
          selectedAccountId,
          accountSelectionError,
          positions: [] as OpenPositionDashboardView[],
          positionsLoading: true,
          positionsError: null,
          closeStates: new Map(),
          closeResults: this.closeResultStates(selectedAccountId),
        }),
      );
    }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  selectAccount(accountId: string): void {
    this.selectedAccountId.next(accountId);
  }

  getCloseState(
    positionId: string,
    accountId: string | null = this.selectedAccountId.value,
  ): PositionCloseState {
    const key = this.closeStateKey(accountId, positionId);
    let state = this.closeStates.get(key);
    if (!state) {
      state = {
        accountId,
        positionId,
        symbol: null,
        source: null,
        status: null,
        externalOrderId: null,
        failureReason: null,
        resolvedMutationScope: null,
        reconciliationResult: null,
        commandId: null,
        showConfirmation: false,
        inFlight: false,
      };
      this.closeStates.set(key, state);
    }
    return state;
  }

  showCloseConfirmation(position: OpenPositionDashboardView): void {
    const state = this.getCloseState(position.positionId, position.accountId);
    state.symbol = position.symbol;
    state.source = position.source;
    state.showConfirmation = true;
  }

  cancelCloseConfirmation(positionId: string, accountId: string | null = this.selectedAccountId.value): void {
    const state = this.getCloseState(positionId, accountId);
    state.showConfirmation = false;
  }

  confirmFullExposureClose(accountId: string, position: OpenPositionDashboardView): void {
    const state = this.getCloseState(position.positionId, accountId);
    if (state.inFlight) return;
    state.symbol = position.symbol;
    state.source = position.source;
    state.inFlight = true;
    const idempotencyKey = uuidv4();

    const closeRequest =
      position.source === 'TRADING_CORE'
        ? this.positionService.closePaperPosition(accountId, position.positionId, idempotencyKey)
        : this.positionService.closePosition(accountId, position.positionId, idempotencyKey);
    closeRequest.pipe(finalize(() => (state.inFlight = false))).subscribe({
      next: (response) => {
        state.commandId = response.commandId;
        state.status = response.status as PositionCloseStatus;
        state.externalOrderId = response.externalOrderId;
        state.failureReason = response.failureReason;
        state.resolvedMutationScope = response.resolvedMutationScope;
        state.reconciliationResult = response.reconciliationResult;
        state.showConfirmation = false;
        this.refreshPositions.next();
      },
      error: (err) => {
        state.status = 'REJECTED';
        state.failureReason = err.error?.message ?? 'Erreur lors de la fermeture';
        state.showConfirmation = false;
        this.refreshPositions.next();
      },
    });
  }

  reconcile(accountId: string, positionId: string): void {
    const state = this.getCloseState(positionId, accountId);
    if (!state.commandId) return;

    const resolvedAccountId = state.accountId ?? accountId;
    this.positionService.reconcileClose(resolvedAccountId, state.commandId).subscribe({
      next: (response) => {
        state.commandId = response.commandId;
        state.status = response.status as PositionCloseStatus;
        state.externalOrderId = response.externalOrderId;
        state.failureReason = response.failureReason;
        state.resolvedMutationScope = response.resolvedMutationScope;
        state.reconciliationResult = response.reconciliationResult;
        this.refreshPositions.next();
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

  private closeResultStates(accountId: string | null): PositionCloseState[] {
    return Array.from(this.closeStates.values()).filter(
      (state) => state.accountId === accountId && state.status !== null,
    );
  }

  private closeStateKey(accountId: string | null, positionId: string): string {
    return `${accountId ?? 'unknown'}:${positionId}`;
  }
}

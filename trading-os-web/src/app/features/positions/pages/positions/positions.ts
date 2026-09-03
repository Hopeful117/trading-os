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
import { Account } from '../../../../core/models/account.model';
import {
  OpenPositionDashboardView,
  PositionProtectionStatus,
} from '../../../../core/models/dashboard-summary.model';
import { AccountService } from '../../../../core/services/account.service';
import { PositionService } from '../../../../core/services/position.service';

interface AccountsState {
  accounts: Account[];
  loading: boolean;
  error: boolean;
}

interface PositionsViewModel {
  accountsState: AccountsState;
  selectedAccountId: string | null;
  positions: OpenPositionDashboardView[];
  positionsLoading: boolean;
  positionsError: string | null;
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
              };
            }),
            catchError(() =>
              of({
                accountsState,
                selectedAccountId,
                positions: this.lastPositionsByAccount.get(selectedAccountId) ?? [],
                positionsLoading: false,
                positionsError: 'Les données des positions sont temporairement indisponibles.',
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
        }),
      );
    }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  selectAccount(accountId: string): void {
    this.selectedAccountId.next(accountId);
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
}

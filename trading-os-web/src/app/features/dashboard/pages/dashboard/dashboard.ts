import { AsyncPipe, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
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
  ActiveScanStatus,
  ActiveScanSummary,
  isActiveScanTerminal,
} from '../../../../core/models/active-scan.model';
import { DashboardSummary } from '../../../../core/models/dashboard-summary.model';
import { OpportunityResponse } from '../../../../core/models/opportunity.model';
import { AccountService } from '../../../../core/services/account.service';
import { ActiveScanService } from '../../../../core/services/active-scan.service';
import { DashboardService } from '../../../../core/services/dashboard.service';
import { OpportunityService } from '../../../../core/services/opportunity.service';

interface AccountsState {
  accounts: Account[];
  loading: boolean;
  error: boolean;
}

interface MiState {
  activeOpportunities: OpportunityResponse[];
  opportunitiesLoading: boolean;
  opportunitiesError: boolean;
  recentScans: ActiveScanSummary[];
  scanLoading: boolean;
  scanError: boolean;
}

interface DashboardViewModel {
  accountsState: AccountsState;
  selectedAccountId: string | null;
  dashboard: DashboardSummary | null;
  dashboardLoading: boolean;
  dashboardError: string | null;
  mi: MiState;
}

@Component({
  selector: 'app-dashboard',
  imports: [AsyncPipe, CurrencyPipe, DatePipe, DecimalPipe, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly accountService = inject(AccountService);
  private readonly dashboardService = inject(DashboardService);
  private readonly opportunityService = inject(OpportunityService);
  private readonly activeScanService = inject(ActiveScanService);
  private readonly selectedAccountId = new BehaviorSubject<string | null>(null);

  private readonly accountsState$: Observable<AccountsState> = this.accountService
    .getAccounts()
    .pipe(
      map((accounts) => ({ accounts, loading: false, error: false })),
      catchError(() => of({ accounts: [], loading: false, error: true })),
      startWith({ accounts: [], loading: true, error: false }),
      shareReplay({ bufferSize: 1, refCount: true }),
    );

  private readonly opportunitiesState$ = this.opportunityService.findActive().pipe(
    map((activeOpportunities) => ({ activeOpportunities, loading: false, error: false })),
    catchError(() => of({ activeOpportunities: [], loading: false, error: true })),
    startWith({ activeOpportunities: [], loading: true, error: false }),
  );

  private readonly scanState$ = this.activeScanService.findRecent(1).pipe(
    map((recentScans) => ({ recentScans, loading: false, error: false })),
    catchError(() => of({ recentScans: [], loading: false, error: true })),
    startWith({ recentScans: [], loading: true, error: false }),
  );

  private readonly miState$: Observable<MiState> = combineLatest([
    this.opportunitiesState$,
    this.scanState$,
  ]).pipe(
    map(([opp, scan]) => ({
      activeOpportunities: opp.activeOpportunities,
      opportunitiesLoading: opp.loading,
      opportunitiesError: opp.error,
      recentScans: scan.recentScans,
      scanLoading: scan.loading,
      scanError: scan.error,
    })),
  );

  readonly viewModel$: Observable<DashboardViewModel> = combineLatest([
    this.accountsState$,
    this.selectedAccountId,
    this.miState$,
  ]).pipe(
    map(([accountsState, selectedAccountId, mi]) => {
      const accounts = accountsState.accounts;
      const effectiveId =
        selectedAccountId && accounts.some((a) => a.accountId === selectedAccountId)
          ? selectedAccountId
          : (accounts[0]?.accountId ?? null);
      return { accountsState, selectedAccountId: effectiveId, mi };
    }),
    switchMap(({ accountsState, selectedAccountId, mi }) => {
      if (!selectedAccountId) {
        return of({
          accountsState,
          selectedAccountId,
          dashboard: null,
          dashboardLoading: false,
          dashboardError: null,
          mi,
        });
      }

      return timer(0, 5_000).pipe(
        switchMap(() =>
          this.dashboardService.findDashboard(selectedAccountId).pipe(
            map((dashboard) => ({
              accountsState,
              selectedAccountId,
              dashboard,
              dashboardLoading: false,
              dashboardError: null,
              mi,
            })),
            catchError(() =>
              of({
                accountsState,
                selectedAccountId,
                dashboard: null,
                dashboardLoading: false,
                dashboardError: 'Le Dashboard est temporairement indisponible.',
                mi,
              }),
            ),
          ),
        ),
        startWith({
          accountsState,
          selectedAccountId,
          dashboard: null,
          dashboardLoading: true,
          dashboardError: null,
          mi,
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

  equitySourceLabel(source: string): string {
    switch (source) {
      case 'BROKER':
        return 'Provenance : broker';
      case 'CALCULATED':
        return 'Provenance : calculé à partir du compte broker';
      default:
        return `Provenance : ${source}`;
    }
  }

  scanStatusLabel(status: ActiveScanStatus): string {
    switch (status) {
      case 'READY_TO_DISPATCH':
        return 'Prêt';
      case 'DISPATCH_REQUESTED':
        return 'En attente';
      case 'RUNNING':
        return 'En cours';
      case 'PARTIALLY_COMPLETED':
        return 'Partiellement terminé';
      case 'COMPLETED':
        return 'Terminé';
      case 'FAILED':
        return 'Échoué';
      case 'COMPLETED_NO_WORK':
        return 'Terminé (aucun résultat)';
    }
  }

  scanStatusClass(status: ActiveScanStatus): string {
    if (isActiveScanTerminal(status)) {
      return 'terminal';
    }
    return 'active';
  }
}

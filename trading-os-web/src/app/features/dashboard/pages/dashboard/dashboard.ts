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
import { DashboardSummary } from '../../../../core/models/dashboard-summary.model';
import { AccountService } from '../../../../core/services/account.service';
import { DashboardService } from '../../../../core/services/dashboard.service';

interface DashboardViewModel {
  accounts: Account[];
  selectedAccountId: string | null;
  dashboard: DashboardSummary | null;
  loading: boolean;
  error: string | null;
}

@Component({
  selector: 'app-dashboard',
  imports: [AsyncPipe, CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly accountService = inject(AccountService);
  private readonly dashboardService = inject(DashboardService);
  private readonly selectedAccountId = new BehaviorSubject<string | null>(null);

  private readonly accounts$ = this.accountService.getAccounts().pipe(
    catchError(() => of([])),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly viewModel$: Observable<DashboardViewModel> = combineLatest([
    this.accounts$,
    this.selectedAccountId,
  ]).pipe(
    map(([accounts, selectedAccountId]) => ({
      accounts,
      selectedAccountId:
        selectedAccountId && accounts.some((account) => account.accountId === selectedAccountId)
          ? selectedAccountId
          : (accounts[0]?.accountId ?? null),
    })),
    switchMap(({ accounts, selectedAccountId }) => {
      if (!selectedAccountId) {
        return of({
          accounts,
          selectedAccountId: null,
          dashboard: null,
          loading: false,
          error: null,
        });
      }

      return timer(0, 5_000).pipe(
        switchMap(() =>
          this.dashboardService.findDashboard(selectedAccountId).pipe(
            map((dashboard) => ({
              accounts,
              selectedAccountId,
              dashboard,
              loading: false,
              error: null,
            })),
            catchError(() =>
              of({
                accounts,
                selectedAccountId,
                dashboard: null,
                loading: false,
                error: 'Le Dashboard est temporairement indisponible.',
              }),
            ),
          ),
        ),
        startWith({
          accounts,
          selectedAccountId,
          dashboard: null,
          loading: true,
          error: null,
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
}

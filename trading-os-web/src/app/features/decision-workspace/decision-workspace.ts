import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  map,
  of,
  shareReplay,
  startWith,
  Subject,
  switchMap,
} from 'rxjs';

import { DecisionContextResponse } from '../../core/models/decision-context.model';
import { Account } from '../../core/models/account.model';
import { AccountService } from '../../core/services/account.service';
import { DecisionContextService } from '../../core/services/decision-context.service';

export type DecisionWorkspaceContextView =
  | { status: 'select-account' }
  | { status: 'loading' }
  | { status: 'ready'; context: DecisionContextResponse }
  | { status: 'error' };

export type DecisionWorkspaceAccountsView =
  | { status: 'loading'; accounts: Account[] }
  | { status: 'loaded'; accounts: Account[] }
  | { status: 'error'; accounts: Account[] };

@Component({
  selector: 'app-decision-workspace',
  imports: [AsyncPipe],
  templateUrl: './decision-workspace.html',
  styleUrl: './decision-workspace.scss',
})
export class DecisionWorkspace {
  private readonly accountService = inject(AccountService);
  private readonly contextService = inject(DecisionContextService);
  private readonly router = inject(Router);

  private readonly accountsRefreshSubject = new Subject<void>();
  private readonly selectedAccountSubject = new BehaviorSubject<string | null>(null);

  readonly accounts$ = this.accountsRefreshSubject.pipe(
    startWith(undefined),
    switchMap(() =>
      this.accountService.getAccounts().pipe(
        map((accounts) => ({ status: 'loaded' as const, accounts })),
        catchError(() => of({ status: 'error' as const, accounts: [] as Account[] })),
        startWith({ status: 'loading' as const, accounts: [] as Account[] }),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly context$ = this.selectedAccountSubject.pipe(
    switchMap((accountId) => {
      if (accountId === null) {
        return of<DecisionWorkspaceContextView>({ status: 'select-account' });
      }

      return this.contextService.resolve(accountId).pipe(
        map((context) => ({ status: 'ready' as const, context })),
        catchError(() => of<DecisionWorkspaceContextView>({ status: 'error' })),
        startWith<DecisionWorkspaceContextView>({ status: 'loading' }),
      );
    }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  selectedMarketId: string | null = null;
  selectedAccountId: string | null = null;

  selectAccount(accountId: string): void {
    this.selectedAccountId = accountId || null;
    this.selectedMarketId = null;
    void this.router.navigate([], {
      queryParams: { accountId },
      queryParamsHandling: 'merge',
    });
    this.selectedAccountSubject.next(accountId || null);
  }

  refreshAccounts(): void {
    this.selectedAccountId = null;
    this.selectedMarketId = null;
    this.selectedAccountSubject.next(null);
    this.accountsRefreshSubject.next();
  }

  selectMarket(marketId: string, context: DecisionContextResponse): void {
    if (!context.eligibleMarketIds.includes(marketId)) {
      return;
    }

    this.selectedMarketId = marketId;
    void this.router.navigate([], {
      queryParams: { marketId },
      queryParamsHandling: 'merge',
    });
  }

  openMarket(marketId: string): void {
    if (this.selectedMarketId === marketId) {
      void this.router.navigate(['/markets', marketId]);
    }
  }
}

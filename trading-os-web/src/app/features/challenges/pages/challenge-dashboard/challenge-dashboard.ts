import { AsyncPipe, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  combineLatest,
  concat,
  map,
  Observable,
  of,
  shareReplay,
  startWith,
  Subject,
  switchMap,
  takeWhile,
  tap,
  timer,
} from 'rxjs';
import { Account } from '../../../../core/models/account.model';
import {
  ChallengeDefinition,
  ChallengeInstance,
  ChallengeProgress,
  ChallengeStatus,
  challengeErrorCode,
  challengeErrorMessage,
} from '../../../../core/models/challenge.model';
import { AccountService } from '../../../../core/services/account.service';
import { ChallengeService } from '../../../../core/services/challenge.service';

const PROGRESS_POLL_INTERVAL_MS = 5_000;

interface AccountsState {
  accounts: Account[];
  loading: boolean;
  error: boolean;
}

interface CatalogState {
  definitions: ChallengeDefinition[];
  loading: boolean;
  error: boolean;
}

type ProgressState =
  | { kind: 'idle' | 'loading' | 'none'; message?: string }
  | { kind: 'available'; progress: ChallengeProgress }
  | { kind: 'error'; message: string };

type StartState =
  | { kind: 'idle'; accountId: string | null }
  | { kind: 'starting'; accountId: string }
  | { kind: 'success'; accountId: string; instance: ChallengeInstance }
  | { kind: 'error'; accountId: string; message: string };

interface ChallengeViewModel {
  accountsState: AccountsState;
  selectedAccountId: string | null;
  catalog: CatalogState;
  selectedDefinition: ChallengeDefinition | null;
  selectedDefinitionKey: string | null;
  progress: ProgressState;
  start: StartState;
}

interface StartRequest {
  accountId: string;
  definition: ChallengeDefinition;
}

@Component({
  selector: 'app-challenge-dashboard',
  imports: [AsyncPipe, CurrencyPipe, DatePipe, DecimalPipe, RouterLink],
  templateUrl: './challenge-dashboard.html',
  styleUrl: './challenge-dashboard.scss',
})
export class ChallengeDashboard {
  private readonly accountService = inject(AccountService);
  private readonly challengeService = inject(ChallengeService);
  private readonly selectedAccountId = new BehaviorSubject<string | null>(null);
  private readonly selectedDefinitionKey = new BehaviorSubject<string | null>(null);
  private readonly startRequests = new Subject<StartRequest>();
  private readonly progressRefresh = new Subject<void>();
  private accountsSnapshot: Account[] = [];
  private catalogSnapshot: ChallengeDefinition[] = [];

  private readonly accountsState$: Observable<AccountsState> = this.accountService
    .getAccounts()
    .pipe(
      tap((accounts) => {
        this.accountsSnapshot = accounts;
        if (!accounts.some((account) => account.accountId === this.selectedAccountId.value)) {
          this.selectedAccountId.next(accounts[0]?.accountId ?? null);
        }
      }),
      map((accounts) => ({ accounts, loading: false, error: false })),
      catchError(() => of({ accounts: [], loading: false, error: true })),
      startWith({ accounts: [], loading: true, error: false }),
      shareReplay({ bufferSize: 1, refCount: true }),
    );

  private readonly catalogState$: Observable<CatalogState> = this.challengeService
    .findCatalog()
    .pipe(
      tap((definitions) => {
        this.catalogSnapshot = definitions;
        if (
          !definitions.some(
            (definition) => this.definitionKey(definition) === this.selectedDefinitionKey.value,
          )
        ) {
          this.selectedDefinitionKey.next(
            definitions[0] ? this.definitionKey(definitions[0]) : null,
          );
        }
      }),
      map((definitions) => ({ definitions, loading: false, error: false })),
      catchError(() => of({ definitions: [], loading: false, error: true })),
      startWith({ definitions: [], loading: true, error: false }),
      shareReplay({ bufferSize: 1, refCount: true }),
    );

  private readonly effectiveAccountId$ = combineLatest([
    this.accountsState$,
    this.selectedAccountId,
  ]).pipe(
    map(([state, selectedId]) =>
      selectedId && state.accounts.some((account) => account.accountId === selectedId)
        ? selectedId
        : (state.accounts[0]?.accountId ?? null),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  private readonly progressState$: Observable<ProgressState> = combineLatest([
    this.effectiveAccountId$,
    this.progressRefresh.pipe(startWith(undefined)),
  ]).pipe(
    switchMap(([accountId]) => {
      if (!accountId) {
        return of<ProgressState>({ kind: 'idle' });
      }

      return timer(0, PROGRESS_POLL_INTERVAL_MS).pipe(
        switchMap(() =>
          this.challengeService.findProgress(accountId).pipe(
            map((progress) => ({ kind: 'available', progress }) as ProgressState),
            catchError((error: unknown) =>
              of<ProgressState>(
                challengeErrorCode(error) === 'CHALLENGE_NOT_STARTED'
                  ? { kind: 'none' }
                  : {
                      kind: 'error',
                      message: challengeErrorMessage(
                        error,
                        'La progression Challenge est temporairement indisponible.',
                      ),
                    },
              ),
            ),
          ),
        ),
        startWith<ProgressState>({ kind: 'loading' }),
        takeWhile(
          (state) =>
            state.kind === 'loading' ||
            (state.kind === 'available' && state.progress.status === 'ACTIVE'),
          true,
        ),
      );
    }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  private readonly startState$: Observable<StartState> = this.startRequests.pipe(
    switchMap(({ accountId, definition }) =>
      concat(
        of<StartState>({ kind: 'starting', accountId }),
        this.challengeService
          .startChallenge(accountId, {
            definitionId: definition.id,
            definitionVersion: definition.definitionVersion,
          })
          .pipe(
            tap(() => this.progressRefresh.next()),
            map((instance) => ({ kind: 'success', accountId, instance }) as StartState),
            catchError((error: unknown) =>
              of<StartState>({
                kind: 'error',
                accountId,
                message: challengeErrorMessage(error, 'Le Challenge n’a pas pu démarrer.'),
              }),
            ),
          ),
      ),
    ),
    startWith<StartState>({ kind: 'idle', accountId: null }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly viewModel$: Observable<ChallengeViewModel> = combineLatest({
    accountsState: this.accountsState$,
    selectedAccountId: this.effectiveAccountId$,
    catalog: this.catalogState$,
    selectedDefinitionKey: this.selectedDefinitionKey,
    progress: this.progressState$,
    start: this.startState$,
  }).pipe(
    map(
      ({ accountsState, selectedAccountId, catalog, selectedDefinitionKey, progress, start }) => ({
        accountsState,
        selectedAccountId,
        catalog,
        selectedDefinition: (() => {
          const activeDefinitionKey =
            progress.kind === 'available'
              ? this.definitionKeyFromParts(
                  progress.progress.definitionId,
                  progress.progress.definitionVersion,
                )
              : null;
          const effectiveDefinitionKey = activeDefinitionKey ?? selectedDefinitionKey;
          return (
            catalog.definitions.find(
              (definition) => this.definitionKey(definition) === effectiveDefinitionKey,
            ) ??
            catalog.definitions[0] ??
            null
          );
        })(),
        selectedDefinitionKey:
          progress.kind === 'available'
            ? this.definitionKeyFromParts(
                progress.progress.definitionId,
                progress.progress.definitionVersion,
              )
            : selectedDefinitionKey,
        progress,
        start:
          start.accountId === selectedAccountId || start.kind === 'idle'
            ? start
            : ({ kind: 'idle', accountId: selectedAccountId } as StartState),
      }),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  selectAccount(accountId: string): void {
    this.selectedAccountId.next(accountId);
  }

  selectDefinition(definition: ChallengeDefinition): void {
    this.selectedDefinitionKey.next(this.definitionKey(definition));
  }

  startSelectedChallenge(): void {
    const accountId = this.selectedAccountId.value ?? this.accountsSnapshot[0]?.accountId;
    const definition =
      this.catalogSnapshot.find(
        (candidate) => this.definitionKey(candidate) === this.selectedDefinitionKey.value,
      ) ?? this.catalogSnapshot[0];
    if (!accountId || !definition) {
      return;
    }
    this.startRequests.next({ accountId, definition });
  }

  definitionKey(definition: ChallengeDefinition): string {
    return this.definitionKeyFromParts(definition.id, definition.definitionVersion);
  }

  private definitionKeyFromParts(id: string, definitionVersion: string): string {
    return `${id}::${definitionVersion}`;
  }

  statusClass(status: ChallengeStatus): string {
    return status.toLowerCase();
  }

  isTerminal(status: ChallengeStatus): boolean {
    return status === 'PASSED' || status === 'BREACHED';
  }

  progressPercent(progress: ChallengeProgress): number | null {
    return progress.targetProgress === null
      ? null
      : Math.max(0, Math.min(100, progress.targetProgress * 100));
  }
}

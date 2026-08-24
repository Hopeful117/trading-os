import { AsyncPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  catchError,
  exhaustMap,
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

import { Account } from '../../../core/models/account.model';
import { ActiveScanResponse, isActiveScanTerminal } from '../../../core/models/active-scan.model';
import { AccountService } from '../../../core/services/account.service';
import { ActiveScanService } from '../../../core/services/active-scan.service';
import { SCAN_POLL_INTERVAL_MS } from './scan-poll-interval';

export type ScanSessionError = 'CONFLICT' | 'UNAUTHORIZED' | 'UNAVAILABLE';

export interface ScanAccountsState {
  loaded: boolean;
  accounts: Account[];
}

export type ScanPanelView =
  | { status: 'idle' }
  | { status: 'submitting' }
  | { status: 'running'; scan: ActiveScanResponse }
  | { status: 'terminal'; scan: ActiveScanResponse }
  | { status: 'error'; error: ScanSessionError };

@Component({
  selector: 'app-scan-panel',
  imports: [AsyncPipe, DatePipe, FormsModule],
  templateUrl: './scan-panel.html',
  styleUrl: './scan-panel.scss',
})
export class ScanPanel {
  private readonly activeScanService = inject(ActiveScanService);
  private readonly accountService = inject(AccountService);

  /** Emitted once when a tracked scan reaches any terminal backend status. */
  readonly scanCompleted = output<ActiveScanResponse>();

  accountId = '';
  objective = '';

  private readonly runSubject = new Subject<{ accountId: string; objective?: string }>();
  private readonly accountsRefreshSubject = new Subject<void>();

  readonly view$: Observable<ScanPanelView>;
  readonly busy$: Observable<boolean>;
  readonly accounts$: Observable<ScanAccountsState>;

  constructor() {
    const pollIntervalMs = inject(SCAN_POLL_INTERVAL_MS);

    this.accounts$ = this.accountsRefreshSubject.pipe(
      startWith(undefined),
      switchMap(() =>
        this.accountService.getAccounts().pipe(
          map((accounts) => ({ loaded: true, accounts })),
          catchError(() => of({ loaded: false, accounts: [] as Account[] })),
        ),
      ),
      shareReplay({
        bufferSize: 1,
        refCount: true,
      }),
    );

    this.view$ = this.runSubject.pipe(
      exhaustMap((command) => this.runSession(command, pollIntervalMs)),
      startWith<ScanPanelView>({ status: 'idle' }),
      shareReplay({
        bufferSize: 1,
        refCount: true,
      }),
    );

    this.busy$ = this.view$.pipe(
      map((view) => view.status === 'submitting' || view.status === 'running'),
    );
  }

  reloadAccounts(): void {
    this.accountsRefreshSubject.next();
  }

  runScan(): void {
    if (!this.accountId) {
      return;
    }

    this.runSubject.next({
      accountId: this.accountId,
      objective: this.objective.trim() || undefined,
    });
  }

  private runSession(
    command: { accountId: string; objective?: string },
    pollIntervalMs: number,
  ): Observable<ScanPanelView> {
    return this.activeScanService
      .createScan(
        {
          accountId: command.accountId,
          objective: command.objective,
        },
        crypto.randomUUID(),
      )
      .pipe(
        switchMap((created) =>
          this.pollUntilTerminal(created.scanId, pollIntervalMs).pipe(
            startWith(created),
            tap((scan) => {
              if (isActiveScanTerminal(scan.status)) {
                this.scanCompleted.emit(scan);
              }
            }),
            map((scan) => this.toView(scan)),
          ),
        ),
        catchError((error: unknown) => of(this.toErrorView(error))),
        startWith<ScanPanelView>({ status: 'submitting' }),
      );
  }

  private pollUntilTerminal(
    scanId: string,
    pollIntervalMs: number,
  ): Observable<ActiveScanResponse> {
    return timer(pollIntervalMs, pollIntervalMs).pipe(
      switchMap(() => this.activeScanService.findScan(scanId)),
      takeWhile((scan) => !isActiveScanTerminal(scan.status), true),
    );
  }

  private toView(scan: ActiveScanResponse): ScanPanelView {
    return isActiveScanTerminal(scan.status)
      ? { status: 'terminal', scan }
      : { status: 'running', scan };
  }

  private toErrorView(error: unknown): ScanPanelView {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 409) {
        return { status: 'error', error: 'CONFLICT' };
      }

      if (error.status === 401 || error.status === 403) {
        return { status: 'error', error: 'UNAUTHORIZED' };
      }
    }

    return { status: 'error', error: 'UNAVAILABLE' };
  }
}

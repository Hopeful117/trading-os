import { AsyncPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
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
import {
  ActiveScanMarketResult,
  ActiveScanResponse,
  CreateActiveScanRequest,
  isActiveScanTerminal,
} from '../../../core/models/active-scan.model';
import { MarketResponse } from '../../../core/models/market-response';
import { AccountService } from '../../../core/services/account.service';
import { ActiveScanService } from '../../../core/services/active-scan.service';
import { MarketService } from '../../../core/services/market.service';
import { SCAN_POLL_INTERVAL_MS } from './scan-poll-interval';

export type ScanSessionError = 'CONFLICT' | 'UNAUTHORIZED' | 'UNAVAILABLE';

export interface ScanAccountsState {
  loaded: boolean;
  accounts: Account[];
}

export type ScanMarketCatalogueState =
  | { status: 'loading'; markets: MarketResponse[] }
  | { status: 'loaded'; markets: MarketResponse[] }
  | { status: 'error'; markets: MarketResponse[] };

export type ScanScopeMode = 'SPECIFIC' | 'ALL_ELIGIBLE';

export type ScanResultFilter =
  'ALL' | 'OPPORTUNITY' | 'NO_OPPORTUNITY' | 'EXCLUDED' | 'FAILED' | 'PROCESSING';

export type ScanPanelView =
  | { status: 'idle' }
  | { status: 'submitting' }
  | { status: 'running'; scan: ActiveScanResponse }
  | { status: 'terminal'; scan: ActiveScanResponse }
  | { status: 'error'; error: ScanSessionError };

@Component({
  selector: 'app-scan-panel',
  imports: [AsyncPipe, DatePipe, FormsModule, RouterLink],
  templateUrl: './scan-panel.html',
  styleUrl: './scan-panel.scss',
})
export class ScanPanel {
  private readonly activeScanService = inject(ActiveScanService);
  private readonly accountService = inject(AccountService);
  private readonly marketService = inject(MarketService);

  /** Emitted once when a tracked scan reaches any terminal backend status. */
  readonly scanCompleted = output<ActiveScanResponse>();

  accountId = '';
  objective = '';
  scopeMode: ScanScopeMode | '' = '';
  selectedMarketIds: string[] = [];
  resultFilter: ScanResultFilter = 'ALL';
  catalogueAvailable = false;

  private readonly runSubject = new Subject<CreateActiveScanRequest>();
  private readonly accountsRefreshSubject = new Subject<void>();
  private readonly marketsRefreshSubject = new Subject<void>();
  private marketById = new Map<string, MarketResponse>();

  readonly view$: Observable<ScanPanelView>;
  readonly busy$: Observable<boolean>;
  readonly accounts$: Observable<ScanAccountsState>;
  readonly markets$: Observable<ScanMarketCatalogueState>;

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

    this.markets$ = this.marketsRefreshSubject.pipe(
      startWith(undefined),
      switchMap(() =>
        this.marketService.findAll().pipe(
          map((markets) => {
            this.catalogueAvailable = true;
            this.marketById = new Map(markets.map((market) => [market.marketId, market]));
            return { status: 'loaded' as const, markets };
          }),
          catchError(() => {
            this.catalogueAvailable = false;
            this.marketById.clear();
            return of({ status: 'error' as const, markets: [] as MarketResponse[] });
          }),
          startWith({ status: 'loading' as const, markets: [] as MarketResponse[] }),
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

  reloadMarkets(): void {
    this.marketsRefreshSubject.next();
  }

  canRun(): boolean {
    if (!this.accountId || !this.scopeMode) {
      return false;
    }
    return (
      this.scopeMode === 'ALL_ELIGIBLE' ||
      (this.catalogueAvailable && this.selectedMarketIds.length > 0)
    );
  }

  runScan(): void {
    if (!this.canRun()) {
      return;
    }

    this.runSubject.next({
      accountId: this.accountId,
      objective: this.objective.trim() || undefined,
      ...(this.scopeMode === 'SPECIFIC' ? { requestedMarketIds: [...this.selectedMarketIds] } : {}),
    });
  }

  scanFromView(view: ScanPanelView): ActiveScanResponse | null {
    return view.status === 'running' || view.status === 'terminal' ? view.scan : null;
  }

  filteredMarkets(scan: ActiveScanResponse): ActiveScanMarketResult[] {
    return scan.markets.filter((market) => {
      switch (this.resultFilter) {
        case 'OPPORTUNITY':
          return market.opportunities.length > 0;
        case 'NO_OPPORTUNITY':
          return market.outcome === 'COMPLETED_NO_OPPORTUNITY';
        case 'EXCLUDED':
          return market.outcome === 'EXCLUDED';
        case 'FAILED':
          return ['FAILED', 'CANCELLED', 'EXPIRED'].includes(market.outcome ?? '');
        case 'PROCESSING':
          return market.outcome === 'RUNNING' || market.outcome === null;
        default:
          return true;
      }
    });
  }

  marketLabel(marketId: string): string {
    return this.marketById.get(marketId)?.symbol ?? marketId;
  }

  marketProvider(marketId: string): string | null {
    return this.marketById.get(marketId)?.provider ?? null;
  }

  readable(value: string): string {
    return value.replaceAll('_', ' ').toLowerCase();
  }

  private runSession(
    command: CreateActiveScanRequest,
    pollIntervalMs: number,
  ): Observable<ScanPanelView> {
    return this.activeScanService.createScan(command, crypto.randomUUID()).pipe(
      switchMap((created) => {
        const scans$ = isActiveScanTerminal(created.status)
          ? of(created)
          : this.pollUntilTerminal(created.scanId, pollIntervalMs).pipe(startWith(created));
        return scans$.pipe(
          tap((scan) => {
            if (isActiveScanTerminal(scan.status)) {
              this.scanCompleted.emit(scan);
            }
          }),
          map((scan) => this.toView(scan)),
        );
      }),
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

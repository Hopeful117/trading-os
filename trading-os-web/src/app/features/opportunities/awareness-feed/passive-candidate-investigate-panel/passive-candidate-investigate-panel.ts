import { AsyncPipe } from '@angular/common';
import {
  Component,
  DestroyRef,
  inject,
  input,
  InjectionToken,
  OnInit,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, of, switchMap, takeWhile, timer } from 'rxjs';

export const PASSIVE_SCAN_POLL_INTERVAL_MS = new InjectionToken<number>(
  'PASSIVE_SCAN_POLL_INTERVAL_MS',
  { providedIn: 'root', factory: () => 2000 },
);

import { Account } from '../../../../core/models/account.model';
import {
  ActiveScanResponse,
  isActiveScanTerminal,
} from '../../../../core/models/active-scan.model';
import { PassiveAwarenessItem } from '../../../../core/models/passive-awareness.model';
import { AccountService } from '../../../../core/services/account.service';
import { ActiveScanService } from '../../../../core/services/active-scan.service';

type InvestigationView =
  | { status: 'idle' }
  | { status: 'scanning'; scanId: string }
  | { status: 'no-opportunity' }
  | {
      status: 'opportunity-found';
      opportunityId: string;
      opportunityVersion: number;
      marketId: string;
      accountId: string;
    }
  | { status: 'error'; code: string; message: string };

@Component({
  selector: 'app-passive-candidate-investigate-panel',
  imports: [AsyncPipe],
  templateUrl: './passive-candidate-investigate-panel.html',
  styleUrl: './passive-candidate-investigate-panel.scss',
})
export class PassiveCandidateInvestigatePanel implements OnInit {
  candidate = input.required<PassiveAwarenessItem>();
  closed = output<void>();

  private readonly accountService = inject(AccountService);
  private readonly activeScanService = inject(ActiveScanService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly pollIntervalMs = inject(PASSIVE_SCAN_POLL_INTERVAL_MS);

  readonly accounts$ = this.accountService.getAccounts().pipe(catchError(() => of<Account[]>([])));

  selectedAccountId = signal('');
  investigationView = signal<InvestigationView>({ status: 'idle' });

  private storedIdempotencyKey: string | null = null;

  ngOnInit(): void {
    this.storedIdempotencyKey = null;
  }

  get canStart(): boolean {
    return this.selectedAccountId() !== '' && this.investigationView().status === 'idle';
  }

  startInvestigation(): void {
    if (!this.canStart) return;

    if (!this.storedIdempotencyKey) {
      this.storedIdempotencyKey = crypto.randomUUID();
    }

    const candidate = this.candidate();
    const idempotencyKey = this.storedIdempotencyKey;

    this.activeScanService
      .createScan(
        {
          accountId: this.selectedAccountId(),
          scopeMode: 'SELECTED',
          requestedMarketIds: [candidate.marketId],
          sourcePassiveOpportunityId: candidate.opportunityId,
          sourcePassiveOpportunityVersion: candidate.opportunityVersion,
        },
        idempotencyKey,
      )
      .pipe(
        switchMap((scan) => {
          this.investigationView.set({ status: 'scanning', scanId: scan.scanId });
          return timer(0, this.pollIntervalMs).pipe(
            switchMap(() => this.activeScanService.findScan(scan.scanId)),
            takeWhile((s: ActiveScanResponse) => !isActiveScanTerminal(s.status), true),
            takeUntilDestroyed(this.destroyRef),
          );
        }),
        catchError((err: { status?: number; error?: { code?: string } }) => {
          const code = err.error?.code ?? `HTTP_${err.status ?? 0}`;
          this.investigationView.set({
            status: 'error',
            code,
            message: this.errorMessage(code),
          });
          return of(null);
        }),
      )
      .subscribe((scan) => {
        if (!scan || !isActiveScanTerminal(scan.status)) return;

        const opportunityResult = scan.markets
          ?.flatMap((m) => m.opportunities ?? [])
          .find((o) => !!o);

        if (opportunityResult) {
          this.investigationView.set({
            status: 'opportunity-found',
            opportunityId: opportunityResult.id,
            opportunityVersion: opportunityResult.version,
            marketId: opportunityResult.marketId ?? scan.markets?.[0]?.marketId ?? '',
            accountId: this.selectedAccountId(),
          });
        } else {
          this.investigationView.set({ status: 'no-opportunity' });
        }
      });
  }

  navigateToOpportunity(): void {
    const view = this.investigationView();
    if (view.status !== 'opportunity-found') return;
    void this.router.navigate(['/decision-workspace'], {
      queryParams: {
        opportunityId: view.opportunityId,
        opportunityVersion: view.opportunityVersion,
        marketId: view.marketId,
        accountId: view.accountId,
      },
    });
  }

  close(): void {
    this.storedIdempotencyKey = null;
    this.investigationView.set({ status: 'idle' });
    this.closed.emit();
  }

  private errorMessage(code: string): string {
    const messages: Record<string, string> = {
      PASSIVE_CANDIDATE_NOT_CURRENT: 'This candidate is no longer current.',
      PASSIVE_CANDIDATE_SUPERSEDED: 'This candidate has been superseded by a newer version.',
      PASSIVE_CANDIDATE_NOT_FOUND: 'This candidate could not be found.',
      PASSIVE_CANDIDATE_IDENTITY_UNAVAILABLE: 'Market data is temporarily unavailable.',
      PASSIVE_CANDIDATE_MARKET_MISMATCH: 'Market scope mismatch — please try again.',
      PASSIVE_CANDIDATE_INVALID_ORIGIN: 'Invalid candidate origin.',
    };
    return messages[code] ?? 'An error occurred. Please try again.';
  }
}

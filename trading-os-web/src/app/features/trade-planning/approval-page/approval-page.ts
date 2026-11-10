import { AsyncPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { catchError, EMPTY, finalize, map, of, startWith, Subject, switchMap } from 'rxjs';
import { TradePlanApprovalResponse } from '../../../core/models/trade-plan.model';
import { TradePlanService } from '../../../core/services/trade-plan.service';

@Component({
  selector: 'app-approval-page',
  standalone: true,
  imports: [AsyncPipe, DatePipe, DecimalPipe],
  template: `
    <main class="approval-shell">
      @if (view$ | async; as view) {
        @if (view.error) {
          <section class="panel error">
            <h1>Approval unavailable</h1>
            <p>{{ view.error }}</p>
          </section>
        } @else if (view.value; as approval) {
          <section class="panel">
            <div class="eyebrow">HUMAN VALIDATION REQUIRED</div>
            <h1>Review trade plan</h1>
            <p class="muted">No broker order will be submitted from this page.</p>
            <div class="summary">
              <div>
                <span>Instrument</span><strong>{{ approval.plan.instrument }}</strong>
              </div>
              <div>
                <span>Direction</span><strong>{{ approval.plan.direction }}</strong>
              </div>
              <div>
                <span>Quantity</span><strong>{{ approval.plan.quantity | number }}</strong>
              </div>
              <div>
                <span>Risk</span><strong>{{ approval.plan.monetaryRisk | number }}</strong>
              </div>
              <div>
                <span>Stop loss</span><strong>{{ approval.plan.stopLoss | number }}</strong>
              </div>
              <div>
                <span>Expires</span><strong>{{ approval.expiresAt | date: 'medium' }}</strong>
              </div>
            </div>
            <h2>Thesis</h2>
            <p>{{ approval.plan.thesis }}</p>
            <h2>Risk evaluation</h2>
            <p>{{ approval.risk.decision }} · {{ approval.risk.status }}</p>
            @if (approval.status === 'PENDING') {
              <div class="actions">
                <button class="reject" (click)="decide('reject')" [disabled]="busy">Reject</button>
                <button class="approve" (click)="decide('approve')" [disabled]="busy">
                  Approve plan
                </button>
              </div>
            } @else {
              <div class="result">
                Challenge status: <strong>{{ approval.status }}</strong>
              </div>
            }
            @if (busy) {
              <p class="muted">Submitting decision...</p>
            }
          </section>
        }
      }
    </main>
  `,
  styles: [
    `
      :host {
        display: block;
        min-height: 100vh;
        background: #020617;
        color: #e2e8f0;
      }
      .approval-shell {
        max-width: 760px;
        margin: 0 auto;
        padding: 72px 20px;
      }
      .panel {
        background: #0f172a;
        border: 1px solid #26344d;
        border-radius: 18px;
        padding: 32px;
        box-shadow: 0 20px 60px #0005;
      }
      .error {
        border-color: #7f1d1d;
      }
      .eyebrow {
        color: #60a5fa;
        font-size: 12px;
        letter-spacing: 0.16em;
        font-weight: 700;
      }
      h1 {
        margin: 10px 0 4px;
        font-size: 32px;
      }
      h2 {
        margin: 28px 0 8px;
        font-size: 16px;
      }
      .muted {
        color: #94a3b8;
      }
      .summary {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 14px;
        margin: 28px 0;
      }
      .summary div {
        background: #111d33;
        padding: 14px;
        border-radius: 10px;
      }
      .summary span {
        display: block;
        color: #94a3b8;
        font-size: 12px;
        margin-bottom: 6px;
      }
      .actions {
        display: flex;
        justify-content: flex-end;
        gap: 12px;
        margin-top: 32px;
      }
      button {
        border: 0;
        border-radius: 8px;
        padding: 12px 18px;
        font-weight: 700;
        cursor: pointer;
      }
      button:disabled {
        opacity: 0.5;
        cursor: wait;
      }
      .approve {
        background: #2563eb;
        color: white;
      }
      .reject {
        background: #1e293b;
        color: #fca5a5;
      }
      .result {
        margin-top: 28px;
        padding: 14px;
        background: #111d33;
        border-radius: 8px;
      }
      @media (max-width: 600px) {
        .summary {
          grid-template-columns: repeat(2, 1fr);
        }
        .panel {
          padding: 22px;
        }
      }
    `,
  ],
})
export class ApprovalPage {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(TradePlanService);
  private readonly refresh$ = new Subject<void>();
  busy = false;
  readonly view$ = this.refresh$.pipe(
    startWith(void 0),
    switchMap(() => this.route.paramMap.pipe(map((params) => params.get('challengeId')))),
    switchMap((id) =>
      id
        ? this.service.getApproval(id).pipe(
            map((value) => ({ value, error: null })),
            catchError(() =>
              of({
                value: null,
                error: 'The challenge is missing, expired, or no longer belongs to your account.',
              }),
            ),
          )
        : of({ value: null, error: 'Missing approval challenge.' }),
    ),
  );

  decide(decision: 'approve' | 'reject'): void {
    const id = this.route.snapshot.paramMap.get('challengeId');
    if (!id) return;
    this.busy = true;
    this.service
      .decideApproval(id, decision)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe(() => this.refresh$.next());
  }
}

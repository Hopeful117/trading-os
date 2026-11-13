import { AsyncPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { catchError, EMPTY, finalize, map, of, startWith, Subject, switchMap } from 'rxjs';
import { ExecutionAuthorizationResponse } from '../../../core/models/execution-authorization.model';
import { ExecutionAuthorizationService } from '../../../core/services/execution-authorization.service';

@Component({
  selector: 'app-execution-authorization-page',
  standalone: true,
  imports: [AsyncPipe, DatePipe, DecimalPipe],
  template: `
    <main class="authorization-shell">
      @if (view$ | async; as view) {
        @if (view.error) {
          <section class="panel error"><h1>Authorization unavailable</h1><p>{{ view.error }}</p></section>
        } @else if (view.value; as authorization) {
          <section class="panel">
            <div class="eyebrow">LIVE EXECUTION AUTHORIZATION</div>
            <h1>Confirm exact order</h1>
            <p class="warning">This confirmation is for one exact operation. It expires at
              {{ authorization.expiresAt | date: 'medium' }}.</p>
            <div class="summary">
              <div><span>Instrument</span><strong>{{ authorization.plan.instrument }}</strong></div>
              <div><span>Provider / product</span><strong>{{ authorization.plan.instrumentProvider }} / {{ authorization.plan.instrumentProductType }}</strong></div>
              <div><span>Trading account</span><strong>{{ authorization.tradingAccountId }}</strong></div>
              <div><span>Broker account</span><strong>{{ authorization.brokerAccountId }}</strong></div>
              <div><span>Order type</span><strong>{{ authorization.plan.entryType }}</strong></div>
              <div><span>Canonical market</span><strong>{{ authorization.canonicalMarketId }}</strong></div>
              <div><span>Direction</span><strong>{{ authorization.plan.direction }}</strong></div>
              <div><span>Quantity</span><strong>{{ authorization.plan.quantity | number }}</strong></div>
              <div><span>Entry</span><strong>{{ authorization.plan.entryPrice | number }}</strong></div>
              <div><span>Stop loss</span><strong>{{ authorization.plan.stopLoss | number }}</strong></div>
              <div><span>Take profit</span><strong>{{ authorization.plan.takeProfits.join(', ') }}</strong></div>
              <div><span>Risk</span><strong>{{ authorization.plan.monetaryRisk | number }}</strong></div>
              <div><span>Status</span><strong>{{ authorization.status }}</strong></div>
            </div>
            <p class="fingerprint">Fingerprint: {{ authorization.requestFingerprint }}</p>
            @if (authorization.status === 'PENDING_HUMAN_CONFIRMATION' || authorization.status === 'APPROVED') {
              <label for="password">Re-enter password to confirm this authorization action</label>
              <input id="password" type="password" #password autocomplete="current-password" />
              @if (authorization.status === 'PENDING_HUMAN_CONFIRMATION') {
                <div class="actions">
                  <button class="reject" (click)="decide(authorization, 'reject', password.value)" [disabled]="busy">Reject</button>
                  <button class="approve" (click)="decide(authorization, 'approve', password.value)" [disabled]="busy">Approve exact order</button>
                </div>
              } @else {
                <button class="reject" (click)="decide(authorization, 'revoke', password.value)" [disabled]="busy">Revoke authorization</button>
              }
            }
            @if (busy) { <p class="muted">Recording decision...</p> }
          </section>
        }
      }
    </main>
  `,
  styles: [`
    :host { display:block; min-height:100vh; background:#020617; color:#e2e8f0; }
    .authorization-shell { max-width:760px; margin:0 auto; padding:72px 20px; }
    .panel { background:#0f172a; border:1px solid #26344d; border-radius:18px; padding:32px; box-shadow:0 20px 60px #0005; }
    .error { border-color:#7f1d1d; } .eyebrow { color:#60a5fa; font-size:12px; letter-spacing:.16em; font-weight:700; }
    h1 { margin:10px 0 8px; font-size:32px; } .warning { color:#fbbf24; }
    .summary { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:14px; margin:26px 0; }
    .summary div { border:1px solid #26344d; border-radius:10px; padding:12px; } .summary span { display:block; color:#94a3b8; font-size:12px; margin-bottom:4px; }
    label { display:block; margin:18px 0 8px; color:#cbd5e1; } input { width:100%; box-sizing:border-box; padding:12px; border-radius:8px; border:1px solid #475569; background:#020617; color:#f8fafc; }
    .actions { display:flex; justify-content:flex-end; gap:12px; margin-top:20px; } button { border:0; border-radius:8px; padding:12px 16px; font-weight:700; cursor:pointer; } button:disabled { opacity:.5; cursor:not-allowed; }
    .approve { background:#2563eb; color:white; } .reject { background:#7f1d1d; color:white; } .muted,.fingerprint { color:#94a3b8; font-size:12px; overflow-wrap:anywhere; }
    @media (max-width:600px) { .summary { grid-template-columns:1fr; } .actions { flex-direction:column-reverse; } }
  `],
})
export class ExecutionAuthorizationPage {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(ExecutionAuthorizationService);
  private readonly decisionSubject = new Subject<{ id: string; action: 'approve' | 'reject' | 'revoke'; password: string }>();
  busy = false;

  readonly view$ = this.route.paramMap.pipe(
    map((params) => params.get('authorizationId')),
    switchMap((id) => id ? this.service.get(id) : EMPTY),
    switchMap((authorization) => this.decisionSubject.pipe(
      switchMap((decision) => this.service.decide(decision.id, decision.action, decision.password)
        .pipe(finalize(() => { this.busy = false; }))),
      startWith(authorization),
    )),
    map((value) => ({ value, error: null as string | null })),
    catchError((error) => of({ value: null, error: error?.error?.message ?? 'The authorization could not be loaded.' })),
  );

  decide(authorization: ExecutionAuthorizationResponse, action: 'approve' | 'reject' | 'revoke', password: string): void {
    if (!password.trim()) return;
    this.busy = true;
    this.decisionSubject.next({ id: authorization.authorizationId, action, password });
  }
}

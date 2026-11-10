import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { TradePlanService } from './trade-plan.service';

describe('TradePlanService', () => {
  let http: HttpTestingController;
  let service: TradePlanService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(TradePlanService);
  });

  afterEach(() => http.verify());

  it('creates from opportunity with idempotency key', () => {
    service.createFromOpportunity('opp-1', 1, 'acc-1', 'key-1').subscribe();
    const req = http.expectOne(
      `${environment.gatewayUrl}v1/trade-plans/opportunities/opp-1/trade-plans`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ accountId: 'acc-1', opportunityVersion: 1 });
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-1');
    req.flush({ tradePlanId: 'tp-1', tradePlanVersion: 1 });
  });

  it('gets plan by id and version', () => {
    service.getPlan('tp-1', 2).subscribe();
    const req = http.expectOne(`${environment.gatewayUrl}v1/trade-plans/tp-1/versions/2`);
    expect(req.request.method).toBe('GET');
    req.flush({ id: 'tp-1', version: 2, status: 'PROPOSED' });
  });

  it('decides on plan', () => {
    service.decide('tp-1', 1, 'ACCEPT').subscribe();
    const req = http.expectOne(`${environment.gatewayUrl}v1/trade-plans/tp-1/versions/1/decisions`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ decision: 'ACCEPT' });
    req.flush({ id: 'tp-1', version: 1, status: 'ACCEPTED' });
  });

  it('evaluates risk with idempotency key', () => {
    service.evaluateRisk('tp-1', 1, 'acc-1', 'risk-key').subscribe();
    const req = http.expectOne(
      `${environment.gatewayUrl}v1/trade-plans/tp-1/versions/1/risk-evaluations`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ accountId: 'acc-1' });
    expect(req.request.headers.get('Idempotency-Key')).toBe('risk-key');
    req.flush({ evaluationId: 'eval-1', status: 'COMPLETED', decision: 'APPROVED' });
  });

  it('gets a human approval challenge', () => {
    service.getApproval('challenge-1').subscribe();
    const req = http.expectOne(`${environment.gatewayUrl}v1/trade-plan-approvals/challenge-1`);
    expect(req.request.method).toBe('GET');
    req.flush({ challengeId: 'challenge-1', status: 'PENDING' });
  });

  it('submits a human approval decision', () => {
    service.decideApproval('challenge-1', 'approve').subscribe();
    const req = http.expectOne(
      `${environment.gatewayUrl}v1/trade-plan-approvals/challenge-1/approve`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ challengeId: 'challenge-1', status: 'APPROVED' });
  });

  it('records a no-trade decision with an idempotency key', () => {
    service
      .recordNoTrade('acc-1', 'opp-1', 2, 'thesis-1', 3, 'key-2', 'No confirmation')
      .subscribe();
    const req = http.expectOne(`${environment.gatewayUrl}v1/accounts/acc-1/opportunity-decisions`);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-2');
    expect(req.request.body).toEqual({
      opportunityId: 'opp-1',
      opportunityVersion: 2,
      thesisId: 'thesis-1',
      thesisVersion: 3,
      rationale: 'No confirmation',
    });
    req.flush({ id: 'decision-1', decision: 'NO_TRADE' });
  });

  it('creates a manual trade plan', () => {
    service.createManual({ accountId: 'acc-1' } as never, 'key-3').subscribe();
    const req = http.expectOne(`${environment.gatewayUrl}v1/trade-plans/manual`);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-3');
    req.flush({ tradePlanId: 'tp-2', tradePlanVersion: 1 });
  });

  it('gets a trade projection', () => {
    service.getProjection('tp-1', 2).subscribe();
    const req = http.expectOne(
      `${environment.gatewayUrl}v1/trade-plans/tp-1/versions/2/projection`,
    );
    expect(req.request.method).toBe('GET');
    req.flush({ tradePlanId: 'tp-1', tradePlanVersion: 2 });
  });
});

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
    service.createFromOpportunity('opp-1', 'acc-1', 'key-1').subscribe();
    const req = http.expectOne(
      `${environment.gatewayUrl}v1/trade-plans/opportunities/opp-1/trade-plans`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ accountId: 'acc-1' });
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
});

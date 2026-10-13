// @vitest-environment jsdom
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { ChallengeService } from './challenge.service';
import { ChallengeDefinition, ChallengeProgress } from '../models/challenge.model';

describe('ChallengeService', () => {
  let service: ChallengeService;
  let httpMock: HttpTestingController;

  const definition: ChallengeDefinition = {
    id: 'definition-1',
    definitionVersion: '1.0.0',
    provider: 'KRAKEN_PROP',
    product: 'EVALUATION',
    planCode: 'ADVANCED',
    capitalCurrency: 'USD',
    startingCapital: 50000,
    profitTargetRatio: 0.09,
    progressionValueSource: 'EQUITY',
    requiredSettlementModel: 'CASH',
    requiredInstrumentProductType: 'LINEAR_PERPETUAL',
    riskPolicyId: 'policy-1',
    riskPolicyVersion: '1.0.0',
    sourceUrl: 'https://example.test',
    retrievedAt: '2026-10-13T12:00:00Z',
    effectiveFrom: '2026-10-13T12:00:00Z',
    provenance: 'test',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(ChallengeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads the catalog', () => {
    service.findCatalog().subscribe((result) => expect(result).toEqual([definition]));

    const request = httpMock.expectOne(`${environment.gatewayUrl}v1/challenges`);
    expect(request.request.method).toBe('GET');
    request.flush([definition]);
  });

  it('starts a Challenge with the exact definition reference', () => {
    const body = { definitionId: definition.id, definitionVersion: definition.definitionVersion };
    service.startChallenge('account/1', body).subscribe();

    const request = httpMock.expectOne(
      `${environment.gatewayUrl}v1/accounts/account%2F1/challenge`,
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({});
  });

  it('loads progress and propagates typed backend errors', () => {
    let received: unknown;
    service.findProgress('account-1').subscribe({ error: (error) => (received = error) });

    const request = httpMock.expectOne(`${environment.gatewayUrl}v1/accounts/account-1/challenge`);
    expect(request.request.method).toBe('GET');
    request.flush(
      { code: 'CHALLENGE_NOT_STARTED', detail: 'No Challenge is active for this account' },
      { status: 404, statusText: 'Not Found' },
    );

    expect(received).toBeInstanceOf(HttpErrorResponse);
    expect((received as HttpErrorResponse).error.code).toBe('CHALLENGE_NOT_STARTED');
  });
});

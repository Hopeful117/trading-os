// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpErrorResponse } from '@angular/common/http';

import { OpportunityService } from './opportunity.service';
import { environment } from '../../../environments/environment';
import { OpportunityResponse } from '../models/opportunity.model';

describe('OpportunityService', () => {
  let service: OpportunityService;
  let httpMock: HttpTestingController;

  const mockOpportunity: OpportunityResponse = {
    id: 'o1',
    version: 3,
    status: 'ACTIVE',
    instrument: 'BTC/EUR',
    direction: 'LONG',
    scenario: 'OHLC_TREND',
    timeframe: '15m',
    type: 'INTRADAY',
    origin: 'ACTIVE_SCAN',
    score: 0.72,
    explanation: 'Upward trend conditions matched',
    observationIds: ['obs-1'],
    aiAnalysisIds: [],
    evaluatedAt: '2026-08-24T10:00:00Z',
    validFrom: '2026-08-24T10:00:00Z',
    validUntil: '2026-08-24T10:30:00Z',
    createdAt: '2026-08-24T09:55:00Z',
    strategyMatchId: 'match-1',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    httpMock = TestBed.inject(HttpTestingController);
    service = TestBed.inject(OpportunityService);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('findActive sends GET to the active opportunities URL', () => {
    const mockActive = [mockOpportunity];

    service.findActive().subscribe((opportunities) => {
      expect(opportunities).toEqual(mockActive);
    });

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/opportunities/active`);
    expect(req.request.method).toBe('GET');
    req.flush(mockActive);
  });

  it('findById sends GET to the opportunity URL with id', () => {
    service.findById('o1').subscribe((opportunity) => {
      expect(opportunity).toEqual(mockOpportunity);
    });

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/opportunities/o1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockOpportunity);
  });

  it('propagates backend errors to the caller', () => {
    let observedError: unknown = null;

    service.findById('missing').subscribe({ error: (error) => (observedError = error) });

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/opportunities/missing`);
    req.flush('not found', { status: 404, statusText: 'Not Found' });

    expect(observedError).toBeInstanceOf(HttpErrorResponse);
    expect((observedError as HttpErrorResponse).status).toBe(404);
  });
});

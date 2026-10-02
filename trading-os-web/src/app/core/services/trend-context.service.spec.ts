// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { TrendContextReadModel } from '../models/trend-context.model';
import { TrendContextService } from './trend-context.service';

describe('TrendContextService', () => {
  let service: TrendContextService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(TrendContextService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads the typed market-scoped Trend Context projection', () => {
    const marketId = 'market-1';
    const response = { marketId, operationalStatus: 'AVAILABLE' } as TrendContextReadModel;

    service.findTrendContext(marketId).subscribe((result) => expect(result).toEqual(response));

    const request = httpMock.expectOne(
      `${environment.gatewayUrl}v1/intelligence/trend-context/${marketId}`,
    );
    expect(request.request.method).toBe('GET');
    request.flush(response);
  });

  it('propagates backend errors without translating them into analytical outcomes', () => {
    let error: unknown;

    service.findTrendContext('market-1').subscribe({ error: (value) => (error = value) });

    httpMock
      .expectOne(`${environment.gatewayUrl}v1/intelligence/trend-context/market-1`)
      .flush('unavailable', { status: 503, statusText: 'Service Unavailable' });

    expect(error).toBeTruthy();
  });
});

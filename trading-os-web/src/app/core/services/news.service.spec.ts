// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { NewsService } from './news.service';

describe('NewsService', () => {
  let service: NewsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(NewsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('requests economic events through the Gateway with supported filters', () => {
    service
      .findEconomicEvents({
        from: '2026-10-15T00:00:00.000Z',
        to: '2026-10-22T23:59:59.999Z',
        currency: 'USD',
        impact: 'HIGH',
        limit: 100,
      })
      .subscribe();

    const request = httpMock.expectOne(
      (candidate) => candidate.url === `${environment.gatewayUrl}v1/news/events`,
    );

    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('from')).toBe('2026-10-15T00:00:00.000Z');
    expect(request.request.params.get('to')).toBe('2026-10-22T23:59:59.999Z');
    expect(request.request.params.get('currency')).toBe('USD');
    expect(request.request.params.get('impact')).toBe('HIGH');
    expect(request.request.params.get('limit')).toBe('100');
    request.flush({
      status: 'AVAILABLE',
      items: [],
      fetchedAt: null,
      message: null,
      attribution: null,
    });
  });
});

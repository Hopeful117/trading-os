// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { PassiveAwarenessService } from './passive-awareness.service';
import { environment } from '../../../environments/environment';
import { PassiveAwarenessPageResponse } from '../models/passive-awareness.model';

const BASE_URL = `${environment.gatewayUrl}v1/passive-awareness`;

function emptyPage(): PassiveAwarenessPageResponse {
  return { items: [], page: 0, size: 20, total: 0, truncated: false };
}

describe('PassiveAwarenessService', () => {
  let service: PassiveAwarenessService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(PassiveAwarenessService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('queries with no params', () => {
    service.query().subscribe();
    const req = httpMock.expectOne((r) => r.url === BASE_URL);
    expect(req.request.method).toBe('GET');
    req.flush(emptyPage());
  });

  it('passes currentOnly=true', () => {
    service.query({ currentOnly: true }).subscribe();
    const req = httpMock.expectOne((r) => r.url === BASE_URL);
    expect(req.request.params.get('currentOnly')).toBe('true');
    req.flush(emptyPage());
  });

  it('passes instrument filter', () => {
    service.query({ instrument: 'EURUSD' }).subscribe();
    const req = httpMock.expectOne((r) => r.url === BASE_URL);
    expect(req.request.params.get('instrument')).toBe('EURUSD');
    req.flush(emptyPage());
  });

  it('passes page and size', () => {
    service.query({ page: 2, size: 10 }).subscribe();
    const req = httpMock.expectOne((r) => r.url === BASE_URL);
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    req.flush(emptyPage());
  });

  it('omits undefined params', () => {
    service.query({ page: 0 }).subscribe();
    const req = httpMock.expectOne((r) => r.url === BASE_URL);
    expect(req.request.params.has('instrument')).toBe(false);
    expect(req.request.params.has('provider')).toBe(false);
    req.flush(emptyPage());
  });

  it('returns the page response on success', () => {
    const page: PassiveAwarenessPageResponse = { items: [], page: 0, size: 20, total: 5, truncated: false };
    let result: PassiveAwarenessPageResponse | undefined;
    service.query().subscribe((r) => (result = r));
    httpMock.expectOne((r) => r.url === BASE_URL).flush(page);
    expect(result).toEqual(page);
  });

  it('propagates 401 as an error', () => {
    let err: { status: number } | undefined;
    service.query().subscribe({ error: (e) => (err = e) });
    httpMock
      .expectOne((r) => r.url === BASE_URL)
      .flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    expect(err?.status).toBe(401);
  });

  it('propagates 503 as an error', () => {
    let err: { status: number } | undefined;
    service.query().subscribe({ error: (e) => (err = e) });
    httpMock
      .expectOne((r) => r.url === BASE_URL)
      .flush('Unavailable', { status: 503, statusText: 'Service Unavailable' });
    expect(err?.status).toBe(503);
  });
});

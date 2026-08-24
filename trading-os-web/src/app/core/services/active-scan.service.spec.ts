// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { ActiveScanService } from './active-scan.service';
import { environment } from '../../../environments/environment';
import { ActiveScanResponse } from '../models/active-scan.model';

describe('ActiveScanService', () => {
  let service: ActiveScanService;
  let httpMock: HttpTestingController;

  const mockScan: ActiveScanResponse = {
    scanId: 'scan-1',
    accountId: 'account-1',
    objective: null,
    status: 'RUNNING',
    requestedMarketIds: null,
    candidateMarketIds: ['m1'],
    effectiveMarketIds: ['m1'],
    resolvedAt: '2026-08-25T10:00:00Z',
    createdAt: '2026-08-25T09:59:59Z',
    updatedAt: '2026-08-25T10:00:01Z',
    progress: {
      totalCandidates: 1,
      eligible: 1,
      excluded: 0,
      running: 1,
      completed: 0,
      failed: 0,
      opportunitiesFound: 0,
    },
    markets: [],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    httpMock = TestBed.inject(HttpTestingController);
    service = TestBed.inject(ActiveScanService);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('createScan posts to the scans URL with the Idempotency-Key header', () => {
    const request = { accountId: 'account-1', objective: 'trend setups' };

    service.createScan(request, 'key-123').subscribe((scan) => {
      expect(scan).toEqual(mockScan);
    });

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/intelligence/scans`);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-123');
    expect(req.request.body).toEqual(request);
    req.flush(mockScan);
  });

  it('findScan gets the scan projection by id', () => {
    service.findScan('scan-1').subscribe((scan) => {
      expect(scan).toEqual(mockScan);
    });

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/intelligence/scans/scan-1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockScan);
  });
});

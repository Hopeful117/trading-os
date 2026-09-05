import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PositionService } from './position.service';
import { environment } from '../../../environments/environment';

describe('PositionService', () => {
  let service: PositionService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PositionService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PositionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should get positions', () => {
    service.getPositions('acc-1').subscribe();

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/accounts/acc-1/positions`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('should close position', () => {
    service.closePosition('acc-1', 'txid-123', 'idem-key').subscribe();

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/accounts/acc-1/positions/close`);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBe('idem-key');
    expect(req.request.body).toEqual({ brokerPositionReference: 'txid-123' });
    req.flush({});
  });

  it('should reconcile close', () => {
    service.reconcileClose('acc-1', 'cmd-1').subscribe();

    const req = httpMock.expectOne(
      `${environment.gatewayUrl}v1/accounts/acc-1/positions/close/cmd-1/reconcile`,
    );
    expect(req.request.method).toBe('POST');
    req.flush({});
  });
});

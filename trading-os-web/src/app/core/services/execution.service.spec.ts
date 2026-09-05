import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ExecutionService } from './execution.service';
import { environment } from '../../../environments/environment';

describe('ExecutionService', () => {
  let service: ExecutionService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ExecutionService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ExecutionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should validate execution', () => {
    const request = { tradePlanId: 'tp1', brokerAccountId: 'ba1' } as any;
    service.validate(request, 'key-1').subscribe();

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/executions/validate`);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-1');
    req.flush({});
  });

  it('should execute', () => {
    service.execute('exec-1').subscribe();

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/executions/exec-1/execute`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('should get execution', () => {
    service.getExecution('exec-1').subscribe();

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/executions/exec-1`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('should retry execution', () => {
    service.retry('exec-1').subscribe();

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/executions/exec-1/retry`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('should reconcile execution', () => {
    service.reconcile('exec-1').subscribe();

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/executions/exec-1/reconcile`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });
});

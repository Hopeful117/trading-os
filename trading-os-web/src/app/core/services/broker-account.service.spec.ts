import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BrokerAccountService } from './broker-account.service';

describe('BrokerAccountService', () => {
  let service: BrokerAccountService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [BrokerAccountService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BrokerAccountService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('list retrieves all broker accounts', () => {
    service.list().subscribe();

    const req = http.expectOne('/api/v1/broker-accounts');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('creates the business account before submitting write-only credentials', () => {
    service
      .createAndConnect({
        provider: 'KRAKEN',
        displayName: 'Kraken lecture seule',
        apiKey: 'FAKE_API_KEY',
        apiSecret: 'FAKE_SENTINEL_SECRET',
      })
      .subscribe();

    const accountRequest = http.expectOne('/api/v1/broker-accounts');
    expect(accountRequest.request.body).toEqual({
      provider: 'KRAKEN',
      displayName: 'Kraken lecture seule',
    });
    accountRequest.flush({ id: 'account-1' });

    const credentialsRequest = http.expectOne('/api/v1/broker-accounts/account-1/credentials');
    expect(credentialsRequest.request.method).toBe('POST');
    expect(credentialsRequest.request.body.apiSecret).toBe('FAKE_SENTINEL_SECRET');
    credentialsRequest.flush({
      outcome: 'VALID',
      connectionStatus: 'CONNECTED',
      missingPermissions: [],
      validatedAt: '2026-07-29T10:00:00Z',
      safeMessage: 'Broker credentials validated',
    });
  });

  it('createAndConnect passes passphrase when provided', () => {
    service
      .createAndConnect({
        provider: 'KRAKEN',
        displayName: 'Kraken',
        apiKey: 'KEY',
        apiSecret: 'SECRET',
        passphrase: 'pass123',
      })
      .subscribe();

    const accountRequest = http.expectOne('/api/v1/broker-accounts');
    accountRequest.flush({ id: 'account-2' });

    const credentialsRequest = http.expectOne('/api/v1/broker-accounts/account-2/credentials');
    expect(credentialsRequest.request.body.passphrase).toBe('pass123');
    credentialsRequest.flush({
      outcome: 'VALID',
      connectionStatus: 'CONNECTED',
      missingPermissions: [],
      validatedAt: '2026-07-29T10:00:00Z',
      safeMessage: 'ok',
    });
  });

  it('rotate replaces credentials for an account', () => {
    service.rotate('acc-1', { apiKey: 'new-key', apiSecret: 'new-secret' }).subscribe();

    const req = http.expectOne('/api/v1/broker-accounts/acc-1/credentials');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ apiKey: 'new-key', apiSecret: 'new-secret' });
    req.flush({
      outcome: 'VALID',
      connectionStatus: 'CONNECTED',
      missingPermissions: [],
      validatedAt: '2026-07-29T10:00:00Z',
      safeMessage: 'ok',
    });
  });

  it('disconnect terminates the broker connection', () => {
    service.disconnect('acc-1').subscribe();

    const req = http.expectOne('/api/v1/broker-accounts/acc-1/disconnect');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'acc-1', provider: 'KRAKEN', displayName: 'test', status: 'DISCONNECTED' });
  });

  it('revoke deletes the broker account', () => {
    service.revoke('acc-1').subscribe();

    const req = http.expectOne('/api/v1/broker-accounts/acc-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});

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

  it('retrieves the authenticated eligible risk profile catalog', () => {
    service.eligibleRiskProfiles().subscribe();

    const req = http.expectOne('/api/v1/risk-profiles/eligible');
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
      provider: '7f6f5f8e-3d42-4a0d-9e11-4d8f2a7c6b01',
      displayName: 'Kraken lecture seule',
      executionMode: 'LIVE',
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

  it('creates a PAPER account with initial capital without submitting credentials', () => {
    service
      .createPaper({
        provider: 'KRAKEN',
        displayName: 'Paper account',
        initialCapital: 10000,
        riskProfile: { profileId: 'profile-1', semanticVersion: '1.0.0' },
      })
      .subscribe();

    const request = http.expectOne('/api/v1/broker-accounts');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      provider: '7f6f5f8e-3d42-4a0d-9e11-4d8f2a7c6b01',
      displayName: 'Paper account',
      executionMode: 'PAPER',
      initialCapital: 10000,
      riskProfile: { profileId: 'profile-1', semanticVersion: '1.0.0' },
    });
    request.flush({ id: 'paper-account-1', executionMode: 'PAPER' });
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

  it('connects credentials for an existing account', () => {
    service.connectExisting('acc-1', { apiKey: 'new-key', apiSecret: 'new-secret' }).subscribe();

    const req = http.expectOne('/api/v1/broker-accounts/acc-1/credentials');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ apiKey: 'new-key', apiSecret: 'new-secret' });
    req.flush({
      outcome: 'VALID',
      connectionStatus: 'CONNECTED',
      missingPermissions: [],
      validatedAt: '2026-07-29T10:00:00Z',
      safeMessage: 'ok',
    });
  });

  it('starts and completes the cTrader authorization workflow', () => {
    service.createCTrader('FTMO cTrader').subscribe((account) => {
      service.startCTraderAuthorization(account.id).subscribe((authorization) => {
        service.getCTraderAuthorization(account.id, authorization.attemptId).subscribe();
        service.bindCTraderAccount(account.id, authorization.attemptId, '49289512').subscribe();
      });
    });

    const create = http.expectOne('/api/v1/broker-accounts');
    expect(create.request.body).toEqual({
      provider: '3d6c3f0a-8d2d-4f4d-9a4c-7a2e1b5c9012',
      displayName: 'FTMO cTrader',
      executionMode: 'LIVE',
    });
    create.flush({ id: 'ctrader-account-1' });

    const start = http.expectOne('/api/v1/broker-accounts/ctrader-account-1/ctrader/authorization');
    expect(start.request.method).toBe('POST');
    start.flush({
      attemptId: 'attempt-1',
      authorizationUrl: 'https://id.ctrader.com/authorize',
      status: 'INITIATED',
      discoveredAccountIds: [],
      selectedExternalAccountId: null,
      expiresAt: '2026-10-27T10:00:00Z',
      safeMessage: 'Authorization initiated',
    });

    const status = http.expectOne(
      '/api/v1/broker-accounts/ctrader-account-1/ctrader/authorization/attempt-1',
    );
    expect(status.request.method).toBe('GET');
    status.flush({});

    const binding = http.expectOne(
      '/api/v1/broker-accounts/ctrader-account-1/ctrader/authorization/attempt-1/binding',
    );
    expect(binding.request.body).toEqual({ externalAccountId: '49289512' });
    binding.flush({});
  });

  it('starts the separate Market Data cTrader authorization', () => {
    service.startCTraderMarketDataAuthorization().subscribe();

    const request = http.expectOne('/api/v1/ctrader/authorization');
    expect(request.request.method).toBe('GET');
    request.flush({ authorizationUrl: 'https://id.ctrader.com/authorize', redirectUri: 'callback', scope: 'accounts' });
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

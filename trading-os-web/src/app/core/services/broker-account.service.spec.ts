import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
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

    const credentialsRequest = http.expectOne(
      '/api/v1/broker-accounts/account-1/credentials',
    );
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
});

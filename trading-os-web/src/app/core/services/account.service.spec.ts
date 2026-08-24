// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { AccountService } from './account.service';
import { environment } from '../../../environments/environment';

describe('AccountService', () => {
  let service: AccountService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    httpMock = TestBed.inject(HttpTestingController);
    service = TestBed.inject(AccountService);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getAccounts sends GET to correct URL', () => {
    const mockAccounts = [
      { id: '1', name: 'Account 1' },
      { id: '2', name: 'Account 2' },
    ];

    service.getAccounts().subscribe((accounts) => {
      expect(accounts).toEqual(mockAccounts);
    });

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/accounts`);
    expect(req.request.method).toBe('GET');
    req.flush(mockAccounts);
  });

  it('getAccount sends GET to correct URL with id', () => {
    const accountId = 'test-account-id';
    const mockAccount = { id: accountId, name: 'Test Account' };

    service.getAccount(accountId).subscribe((account) => {
      expect(account).toEqual(mockAccount);
    });

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/accounts/${accountId}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockAccount);
  });

  it('synchronize sends POST to correct URL with text response', () => {
    service.synchronize().subscribe((response) => {
      expect(response).toBe('Synchronization completed');
    });

    const req = httpMock.expectOne(`${environment.gatewayUrl}v1/accounts/synchronize`);
    expect(req.request.method).toBe('POST');
    expect(req.request.responseType).toBe('text');
    req.flush('Synchronization completed');
  });
});

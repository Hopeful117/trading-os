// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { DecisionContextService } from './decision-context.service';
import { environment } from '../../../environments/environment';

describe('DecisionContextService', () => {
  let service: DecisionContextService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(DecisionContextService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('resolves the account-first decision context', () => {
    const accountId = 'account-1';
    const context = {
      account: { accountId },
      markets: [],
      eligibleMarketIds: [],
      resolvedAt: '2026-09-20T10:00:00Z',
    };

    service.resolve(accountId).subscribe((result) => expect(result).toEqual(context));

    const request = httpMock.expectOne(
      `${environment.gatewayUrl}v1/intelligence/decision-context/${accountId}`,
    );
    expect(request.request.method).toBe('GET');
    request.flush(context);
  });
});

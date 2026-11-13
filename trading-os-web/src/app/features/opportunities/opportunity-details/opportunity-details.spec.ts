import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { OpportunityResponse } from '../../../core/models/opportunity.model';
import { OpportunityService } from '../../../core/services/opportunity.service';
import { OpportunityDetail } from './opportunity-details';

describe('OpportunityDetail compatibility redirect', () => {
  let fixture: ComponentFixture<OpportunityDetail>;
  let opportunityServiceMock: { findById: ReturnType<typeof vi.fn> };
  let routerMock: { navigate: ReturnType<typeof vi.fn> };

  const opportunity: OpportunityResponse = {
    id: 'o1',
    version: 3,
    status: 'ACTIVE',
    instrument: 'BTC/EUR',
    direction: 'LONG',
    scenario: 'OHLC_TREND',
    timeframe: '15m',
    type: 'INTRADAY',
    origin: 'ACTIVE_SCAN',
    score: 0.72,
    scoreSemantics: 'BUSINESS_PRIORITY_NOT_PROBABILITY',
    explanation: 'Upward trend conditions matched',
    observationIds: [],
    aiAnalysisIds: [],
    evaluatedAt: '2026-08-24T10:00:00Z',
    validFrom: '2026-08-24T10:00:00Z',
    validUntil: null,
    createdAt: '2026-08-24T09:55:00Z',
    strategyMatchId: null,
    marketId: 'market-1',
  };

  async function createComponent(
    accountId: string | null = null,
    serviceOpportunity: OpportunityResponse = opportunity,
  ): Promise<void> {
    opportunityServiceMock = { findById: vi.fn().mockReturnValue(of(serviceOpportunity)) };
    routerMock = { navigate: vi.fn().mockResolvedValue(true) };

    await TestBed.configureTestingModule({
      imports: [OpportunityDetail],
      providers: [
        { provide: OpportunityService, useValue: opportunityServiceMock },
        { provide: Router, useValue: routerMock },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ opportunityId: 'o1' })),
            queryParamMap: of(convertToParamMap(accountId ? { accountId } : {})),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OpportunityDetail);
    await fixture.whenStable();
  }

  it('redirects a legacy opportunity URL to the Decision Cockpit', async () => {
    await createComponent('account-1');

    expect(routerMock.navigate).toHaveBeenCalledWith(['/decision-workspace'], {
      queryParams: {
        opportunityId: 'o1',
        opportunityVersion: 3,
        marketId: 'market-1',
        accountId: 'account-1',
      },
    });
  });

  it('uses the opportunity account when the legacy URL has no account context', async () => {
    await createComponent(null, { ...opportunity, accountId: 'account-from-opportunity' });

    expect(routerMock.navigate).toHaveBeenCalledWith(['/decision-workspace'], {
      queryParams: {
        opportunityId: 'o1',
        opportunityVersion: 3,
        marketId: 'market-1',
        accountId: 'account-from-opportunity',
      },
    });
  });

  it('renders a retryable error state when the legacy opportunity cannot load', async () => {
    opportunityServiceMock = {
      findById: vi.fn().mockReturnValue(throwError(() => new Error('unavailable'))),
    };
    routerMock = { navigate: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [OpportunityDetail],
      providers: [
        { provide: OpportunityService, useValue: opportunityServiceMock },
        { provide: Router, useValue: routerMock },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ opportunityId: 'o1' })),
            queryParamMap: of(convertToParamMap({})),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OpportunityDetail);
    await fixture.whenStable();

    expect(
      fixture.nativeElement.querySelector('[data-testid="opportunity-redirect-error"]'),
    ).not.toBeNull();
    expect(routerMock.navigate).not.toHaveBeenCalled();
  });
});

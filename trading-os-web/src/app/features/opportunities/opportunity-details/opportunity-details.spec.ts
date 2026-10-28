import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';

import { OpportunityDetail, OpportunityDetailView } from './opportunity-details';
import { OpportunityService } from '../../../core/services/opportunity.service';
import { TradePlanService } from '../../../core/services/trade-plan.service';
import { MarketService } from '../../../core/services/market.service';
import { OpportunityResponse, OpportunitySetup } from '../../../core/models/opportunity.model';

describe('OpportunityDetail', () => {
  let fixture: ComponentFixture<OpportunityDetail>;
  let opportunityServiceMock: { findById: ReturnType<typeof vi.fn> };
  let tradePlanServiceMock: { createFromOpportunity: ReturnType<typeof vi.fn> };
  let routerMock: {
    navigate: ReturnType<typeof vi.fn>;
    createUrlTree: ReturnType<typeof vi.fn>;
    serializeUrl: ReturnType<typeof vi.fn>;
  };

  const setup: OpportunitySetup = {
    referencePrice: 64120.5,
    referencePriceAt: '2026-08-24T09:59:00Z',
    description: 'Price broke resistance with momentum',
    triggers: [
      { condition: 'directional_price_change', observedValue: '12.5' },
      { condition: 'range_expansion', observedValue: null },
    ],
    detectedAt: '2026-08-24T10:00:00Z',
  };

  const mockOpportunity: OpportunityResponse = {
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
    explanation: 'Upward trend conditions matched on 15m candles',
    observationIds: ['obs-1', 'obs-2'],
    aiAnalysisIds: [],
    evaluatedAt: '2026-08-24T10:00:00Z',
    validFrom: '2026-08-24T10:00:00Z',
    validUntil: '2026-08-24T10:30:00Z',
    createdAt: '2026-08-24T09:55:00Z',
    strategyMatchId: 'match-1',
    marketId: 'market-1',
    marketThesis: { thesisId: 'thesis-1', thesisVersion: 1 },
    thesis: {
      thesisId: 'thesis-1',
      thesisVersion: 1,
      state: 'CANDIDATE',
    } as OpportunityResponse['thesis'],
    setup,
  };

  function configureComponent(opportunityId: string | null, accountId: string | null = null): void {
    TestBed.configureTestingModule({
      imports: [OpportunityDetail],
      providers: [
        { provide: OpportunityService, useValue: opportunityServiceMock },
        { provide: TradePlanService, useValue: tradePlanServiceMock },
        {
          provide: MarketService,
          useValue: {
            findById: vi.fn().mockReturnValue(of({ marketId: 'market-1', productType: 'SPOT' })),
          },
        },
        { provide: Router, useValue: routerMock },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(
              opportunityId === null ? convertToParamMap({}) : convertToParamMap({ opportunityId }),
            ),
            queryParamMap: of(accountId ? convertToParamMap({ accountId }) : convertToParamMap({})),
          },
        },
      ],
    });
  }

  async function createComponent(
    opportunityId: string | null,
    accountId: string | null = null,
  ): Promise<void> {
    configureComponent(opportunityId, accountId);
    await TestBed.compileComponents();

    fixture = TestBed.createComponent(OpportunityDetail);
    await fixture.whenStable();
  }

  beforeEach(() => {
    opportunityServiceMock = { findById: vi.fn().mockReturnValue(of(mockOpportunity)) };
    tradePlanServiceMock = {
      createFromOpportunity: vi
        .fn()
        .mockReturnValue(of({ tradePlanId: 'tp-1', tradePlanVersion: 1 })),
    };
    routerMock = {
      navigate: vi.fn().mockResolvedValue(true),
      createUrlTree: vi.fn((commands, extras) => ({ commands, extras })),
      serializeUrl: vi.fn((tree) => {
        const commands = tree.commands as string[];
        const params = new URLSearchParams(tree.extras?.queryParams ?? {}).toString();
        return `${commands.join('/')}${params ? `?${params}` : ''}`;
      }),
    };
  });

  it('should create', async () => {
    await createComponent('o1');

    expect(fixture.componentInstance).toBeTruthy();
  });

  describe('loaded state', () => {
    it('fetches the opportunity from the route parameter', async () => {
      await createComponent('o1');

      expect(opportunityServiceMock.findById).toHaveBeenCalledWith('o1');
    });

    it('renders identity and status information', async () => {
      await createComponent('o1');

      const element = fixture.nativeElement;
      expect(element.querySelector('[data-testid="opportunity-detail"]')).not.toBeNull();
      expect(element.textContent).toContain('BTC/EUR');
      expect(element.textContent).toContain('LONG');
      expect(element.textContent).toContain('ACTIVE');
      expect(element.textContent).toContain('INTRADAY');
      expect(element.textContent).toContain('15m');
    });

    it('renders the deterministic explanation', async () => {
      await createComponent('o1');

      expect(fixture.nativeElement.textContent).toContain(mockOpportunity.explanation);
    });

    it('renders the authoritative thesis decision and evidence categories', async () => {
      opportunityServiceMock.findById.mockReturnValue(
        of({
          ...mockOpportunity,
          thesis: {
            ...mockOpportunity.thesis,
            state: 'NO_TRADE',
            reasons: ['Contradicting evidence exceeds the tradeability threshold'],
            supportingEvidence: [
              { evidenceId: 'support-1', explanation: 'Trend context available' },
            ],
            contradictingEvidence: [
              { evidenceId: 'contra-1', explanation: 'Spread is too wide' },
            ],
            missingEvidence: [{ evidenceId: 'missing-1', explanation: 'No order book snapshot' }],
          },
        }),
      );

      await createComponent('o1');

      const element = fixture.nativeElement;
      expect(element.querySelector('[data-testid="market-thesis"]')).not.toBeNull();
      expect(element.textContent).toContain('NO TRADE');
      expect(element.textContent).toContain('Contradicting evidence exceeds the tradeability threshold');
      expect(element.textContent).toContain('Spread is too wide');
      expect(element.textContent).toContain('No order book snapshot');
    });

    it('does not expose Trade Plan preparation for a non-candidate thesis', async () => {
      opportunityServiceMock.findById.mockReturnValue(
        of({ ...mockOpportunity, thesis: { ...mockOpportunity.thesis, state: 'WATCH' } }),
      );

      await createComponent('o1', 'account-1');

      expect(fixture.nativeElement.querySelector('[data-testid="create-trade-plan-button"]')).toBeNull();
      expect(fixture.nativeElement.querySelector('[data-testid="preparation-blocked"]')).not.toBeNull();
    });

    it('renders incomplete Thesis payloads as unavailable', async () => {
      opportunityServiceMock.findById.mockReturnValue(
        of({
          ...mockOpportunity,
          thesis: { thesisId: 'thesis-1', thesisVersion: 1 } as OpportunityResponse['thesis'],
        }),
      );

      await createComponent('o1');

      expect(fixture.nativeElement.querySelector('[data-testid="market-thesis"]')).toBeNull();
      expect(
        fixture.nativeElement.querySelector('[data-testid="market-thesis-unavailable"]'),
      ).not.toBeNull();
    });

    it('blocks Trade Plan preparation for an incomplete candidate Thesis', async () => {
      opportunityServiceMock.findById.mockReturnValue(
        of({
          ...mockOpportunity,
          thesis: { state: 'CANDIDATE' } as OpportunityResponse['thesis'],
        }),
      );

      await createComponent('o1', 'account-1');

      expect(fixture.nativeElement.querySelector('[data-testid="create-trade-plan-button"]')).toBeNull();
      expect(fixture.nativeElement.querySelector('[data-testid="preparation-blocked"]')).not.toBeNull();
    });

    it('renders strategy provenance when a match exists', async () => {
      await createComponent('o1');

      const element = fixture.nativeElement;
      expect(element.textContent).toContain('match-1');
      expect(element.textContent).toContain('obs-1');
      expect(element.textContent).toContain('obs-2');
    });

    it('prepares directly when account context is available', async () => {
      await createComponent('o1', 'account-1');

      const button = fixture.nativeElement.querySelector(
        '[data-testid="create-trade-plan-button"]',
      ) as HTMLButtonElement;

      button.click();

      expect(tradePlanServiceMock.createFromOpportunity).toHaveBeenCalledWith(
        'o1',
        3,
        'account-1',
        expect.any(String),
      );
      expect(routerMock.navigate).toHaveBeenCalledWith(
        ['/trade-planning', 'plans', 'tp-1', 'versions', 1],
        { queryParams: { marketId: 'market-1' } },
      );
    });

    it('keeps the Decision Workspace as a secondary account-scoped action', async () => {
      await createComponent('o1', 'account-1');

      const link = fixture.nativeElement.querySelector(
        '[data-testid="decision-workspace-link"]',
      ) as HTMLAnchorElement;

      expect(link.getAttribute('href')).toContain('/decision-workspace');
      expect(link.getAttribute('href')).toContain('opportunityId=o1');
      expect(link.getAttribute('href')).toContain('marketId=market-1');
      expect(link.getAttribute('href')).toContain('accountId=account-1');
    });

    it('keeps the account-less direct-entry fallback', async () => {
      await createComponent('o1');

      const link = fixture.nativeElement.querySelector(
        '[data-testid="create-trade-plan-button"]',
      ) as HTMLAnchorElement;

      expect(link.getAttribute('href')).not.toContain('accountId=');
    });

    it('renders a retryable error when direct preparation fails', async () => {
      tradePlanServiceMock.createFromOpportunity.mockReturnValue(
        throwError(() => new Error('unavailable')),
      );
      await createComponent('o1', 'account-1');

      fixture.nativeElement.querySelector('[data-testid="create-trade-plan-button"]').click();
      fixture.detectChanges();

      expect(
        fixture.nativeElement.querySelector('[data-testid="preparation-error"]'),
      ).not.toBeNull();
      expect(fixture.componentInstance.preparing).toBe(false);
    });

    it('does not submit duplicate direct preparation commands while pending', async () => {
      tradePlanServiceMock.createFromOpportunity.mockReturnValue(new Observable(() => {}));
      await createComponent('o1', 'account-1');

      const button = fixture.nativeElement.querySelector(
        '[data-testid="create-trade-plan-button"]',
      ) as HTMLButtonElement;
      button.click();
      button.click();

      expect(tradePlanServiceMock.createFromOpportunity).toHaveBeenCalledTimes(1);
    });

    it('does not fabricate provenance when no match exists', async () => {
      opportunityServiceMock.findById.mockReturnValue(
        of({ ...mockOpportunity, strategyMatchId: null, observationIds: [] }),
      );
      await createComponent('o1');

      const element = fixture.nativeElement;
      expect(element.textContent).toContain('Not recorded');
      expect(element.textContent).toContain('None recorded');
    });
  });

  describe('setup snapshot', () => {
    it('renders the setup section with reference price, description and triggers', async () => {
      await createComponent('o1');

      const element = fixture.nativeElement;
      expect(element.querySelector('[data-testid="setup-section"]')).not.toBeNull();
      expect(element.querySelector('[data-testid="setup-reference-price"]').textContent).toContain(
        '64,120.5',
      );
      expect(element.querySelector('[data-testid="setup-triggers"]').textContent).toContain(
        'directional_price_change',
      );
      expect(element.textContent).toContain(setup.description);
      expect(element.textContent).toContain('Detected');
    });

    it('renders triggers without observed values honestly', async () => {
      await createComponent('o1');

      const triggers = fixture.nativeElement.querySelector(
        '[data-testid="setup-triggers"]',
      ).textContent;
      expect(triggers).toContain('range_expansion');
      expect(triggers).toContain('12.5');
    });

    it('hides the setup section for legacy opportunities without a snapshot', async () => {
      opportunityServiceMock.findById.mockReturnValue(of({ ...mockOpportunity, setup: null }));
      await createComponent('o1');

      expect(fixture.nativeElement.querySelector('[data-testid="setup-section"]')).toBeNull();
    });
  });

  describe('failure states', () => {
    it('renders the not-found state on HTTP 404', async () => {
      opportunityServiceMock.findById.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not Found' })),
      );
      await createComponent('o1');

      expect(fixture.nativeElement.querySelector('[data-testid="not-found-state"]')).not.toBeNull();
    });

    it('renders the error state on other backend failures', async () => {
      opportunityServiceMock.findById.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 503, statusText: 'Service Unavailable' })),
      );
      await createComponent('o1');

      expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).not.toBeNull();
    });
  });

  describe('view stream', () => {
    it('emits loading before the loaded value', () => {
      configureComponent('o1');
      const unstable = TestBed.createComponent(OpportunityDetail);

      const views: OpportunityDetailView[] = [];
      unstable.componentInstance.view$.subscribe((view) => views.push(view));

      expect(views[0].status).toBe('loading');
      expect(views.at(-1)?.status).toBe('loaded');
    });
  });
});

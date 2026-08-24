import { ComponentFixture, TestBed } from '@angular/core/testing';
import { convertToParamMap, ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { OpportunityDetail, OpportunityDetailView } from './opportunity-details';
import { OpportunityService } from '../../../core/services/opportunity.service';
import { OpportunityResponse } from '../../../core/models/opportunity.model';

describe('OpportunityDetail', () => {
  let fixture: ComponentFixture<OpportunityDetail>;
  let opportunityServiceMock: { findById: ReturnType<typeof vi.fn> };

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
  };

  function configureComponent(opportunityId: string | null): void {
    TestBed.configureTestingModule({
      imports: [OpportunityDetail],
      providers: [
        { provide: OpportunityService, useValue: opportunityServiceMock },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(
              opportunityId === null
                ? convertToParamMap({})
                : convertToParamMap({ opportunityId }),
            ),
          },
        },
      ],
    });
  }

  async function createComponent(opportunityId: string | null): Promise<void> {
    configureComponent(opportunityId);
    await TestBed.compileComponents();

    fixture = TestBed.createComponent(OpportunityDetail);
    await fixture.whenStable();
  }

  beforeEach(() => {
    opportunityServiceMock = { findById: vi.fn().mockReturnValue(of(mockOpportunity)) };
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

    it('renders strategy provenance when a match exists', async () => {
      await createComponent('o1');

      const element = fixture.nativeElement;
      expect(element.textContent).toContain('match-1');
      expect(element.textContent).toContain('obs-1');
      expect(element.textContent).toContain('obs-2');
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

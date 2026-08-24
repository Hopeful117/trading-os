import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { Opportunities, OpportunitiesView } from './opportunities';
import { OpportunityService } from '../../core/services/opportunity.service';
import { OpportunityResponse } from '../../core/models/opportunity.model';

describe('Opportunities', () => {
  let component: Opportunities;
  let fixture: ComponentFixture<Opportunities>;
  let routerMock: { navigate: ReturnType<typeof vi.fn> };
  let opportunityServiceMock: { findActive: ReturnType<typeof vi.fn> };

  const mockOpportunities: OpportunityResponse[] = [
    {
      id: 'o1',
      version: 2,
      status: 'ACTIVE',
      instrument: 'BTC/EUR',
      direction: 'LONG',
      scenario: 'OHLC_TREND',
      timeframe: '15m',
      type: 'INTRADAY',
      origin: 'ACTIVE_SCAN',
      score: 0.72,
      explanation: 'Upward trend conditions matched on 15m candles',
      observationIds: ['obs-1'],
      aiAnalysisIds: [],
      evaluatedAt: '2026-08-24T10:00:00Z',
      validFrom: '2026-08-24T10:00:00Z',
      validUntil: '2026-08-24T10:30:00Z',
      createdAt: '2026-08-24T09:55:00Z',
      strategyMatchId: 'match-1',
    },
    {
      id: 'o2',
      version: 1,
      status: 'ACTIVE',
      instrument: 'ETH/EUR',
      direction: 'SHORT',
      scenario: 'OHLC_TREND',
      timeframe: '15m',
      type: 'INTRADAY',
      origin: 'ACTIVE_SCAN',
      score: 0.55,
      explanation: 'Downward trend conditions matched on 15m candles',
      observationIds: ['obs-2'],
      aiAnalysisIds: [],
      evaluatedAt: '2026-08-24T10:05:00Z',
      validFrom: '2026-08-24T10:05:00Z',
      validUntil: null,
      createdAt: '2026-08-24T10:00:00Z',
      strategyMatchId: null,
    },
  ];

  beforeEach(async () => {
    routerMock = { navigate: vi.fn().mockResolvedValue(true) };
    opportunityServiceMock = { findActive: vi.fn().mockReturnValue(of(mockOpportunities)) };

    await TestBed.configureTestingModule({
      imports: [Opportunities],
      providers: [
        { provide: OpportunityService, useValue: opportunityServiceMock },
        { provide: Router, useValue: routerMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Opportunities);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('view stream', () => {
    it('emits loading then loaded with backend opportunities', () => {
      const fresh = TestBed.createComponent(Opportunities);

      const views: OpportunitiesView[] = [];
      fresh.componentInstance.view$.subscribe((view) => views.push(view));

      expect(views[0].status).toBe('loading');
      expect(views.at(-1)?.status).toBe('loaded');
      expect(opportunityServiceMock.findActive).toHaveBeenCalled();
    });

    it('replays the last state to late subscribers', () => {
      let views: OpportunitiesView[] = [];
      component.view$.subscribe((view) => views.push(view));

      expect(views).toHaveLength(1);
      expect(views[0].status).toBe('loaded');
    });

    it('exposes the loaded opportunities', () => {
      let loaded: OpportunityResponse[] = [];
      component.view$.subscribe((view) => {
        if (view.status === 'loaded') {
          loaded = view.opportunities;
        }
      });

      expect(loaded.map((opportunity) => opportunity.id)).toEqual(['o1', 'o2']);
    });
  });

  describe('rendered states', () => {
    it('renders one row per active opportunity', () => {
      const rows = fixture.nativeElement.querySelectorAll('.opportunity-row');
      expect(rows.length).toBe(2);
      expect(fixture.nativeElement.textContent).toContain('BTC/EUR');
      expect(fixture.nativeElement.textContent).toContain('ETH/EUR');
    });

    it('renders the empty state when no active opportunity exists', async () => {
      opportunityServiceMock.findActive.mockReturnValue(of([]));

      const second = TestBed.createComponent(Opportunities);
      await second.whenStable();

      expect(second.nativeElement.querySelector('[data-testid="empty-state"]')).not.toBeNull();
    });

    it('renders the error state when the backend fails', async () => {
      opportunityServiceMock.findActive.mockReturnValue(
        throwError(() => new Error('boom')),
      );

      const second = TestBed.createComponent(Opportunities);
      await second.whenStable();

      expect(second.nativeElement.querySelector('[data-testid="error-state"]')).not.toBeNull();
    });
  });

  describe('navigation', () => {
    it('navigates to /opportunities/:id when a row is opened', () => {
      component.openOpportunity('o1');

      expect(routerMock.navigate).toHaveBeenCalledWith(['/opportunities', 'o1']);
    });
  });

  describe('refresh', () => {
    it('re-fetches active opportunities from the service', () => {
      opportunityServiceMock.findActive.mockClear();

      component.refreshOpportunities();

      expect(opportunityServiceMock.findActive).toHaveBeenCalled();
    });
  });
});

// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { PassiveAwarenessFeed } from './passive-awareness-feed';
import { PassiveAwarenessService } from '../../../core/services/passive-awareness.service';
import { AccountService } from '../../../core/services/account.service';
import { ActiveScanService } from '../../../core/services/active-scan.service';
import { Router } from '@angular/router';
import { PassiveAwarenessPageResponse } from '../../../core/models/passive-awareness.model';

function emptyPage(): PassiveAwarenessPageResponse {
  return { items: [], page: 0, size: 20, total: 0, truncated: false };
}

function pageWithItem(): PassiveAwarenessPageResponse {
  return {
    items: [
      {
        opportunityId: 'opp-1',
        opportunityVersion: 1,
        current: true,
        diagnostics: [],
        lifecycleState: 'ACTIVE',
        presentationState: 'PASSIVE_CANDIDATE',
        instrument: 'EURUSD',
        direction: 'LONG',
        scenario: 'TREND_FOLLOWING',
        timeframe: '1H',
        origin: 'PASSIVE_SCAN',
        marketId: 'market-1',
        provider: 'IC Markets',
        productType: 'CFD',
        strategyMatchId: 'match-1',
        observationIds: ['obs-1'],
        observationTimes: ['2026-11-14T10:00:00Z'],
        thesisId: 'thesis-1',
        thesisVersion: 1,
        sourceScanId: 'scan-1',
        sourceScanMarketId: 'scan-market-1',
        analysisExecutionId: 'exec-1',
        evaluatedAt: '2026-11-14T10:00:00Z',
        sourceCutoff: '2026-11-14T09:50:00Z',
        validFrom: '2026-11-14T10:00:00Z',
        validUntil: null,
        createdAt: '2026-11-14T10:00:00Z',
      },
    ],
    page: 0,
    size: 20,
    total: 1,
    truncated: false,
  };
}

describe('PassiveAwarenessFeed', () => {
  let component: PassiveAwarenessFeed;
  let fixture: ComponentFixture<PassiveAwarenessFeed>;
  let awarenessServiceMock: { query: ReturnType<typeof vi.fn> };
  let accountServiceMock: { getAccounts: ReturnType<typeof vi.fn> };
  let activeScanServiceMock: {
    createScan: ReturnType<typeof vi.fn>;
    findScan: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    awarenessServiceMock = { query: vi.fn().mockReturnValue(of(emptyPage())) };
    accountServiceMock = { getAccounts: vi.fn().mockReturnValue(of([])) };
    activeScanServiceMock = {
      createScan: vi.fn(),
      findScan: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [PassiveAwarenessFeed],
      providers: [
        { provide: PassiveAwarenessService, useValue: awarenessServiceMock },
        { provide: AccountService, useValue: accountServiceMock },
        { provide: ActiveScanService, useValue: activeScanServiceMock },
        { provide: Router, useValue: { navigate: vi.fn().mockResolvedValue(true) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PassiveAwarenessFeed);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders empty state when total is 0 and no filters', () => {
    expect(fixture.nativeElement.querySelector('[data-testid="awareness-empty"]')).not.toBeNull();
  });

  it('renders loaded state with candidate items', async () => {
    awarenessServiceMock.query.mockReturnValue(of(pageWithItem()));
    fixture = TestBed.createComponent(PassiveAwarenessFeed);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[data-testid="awareness-loaded"]')).not.toBeNull();
  });

  it('renders filtered-empty state when total is 0 with active filters', async () => {
    awarenessServiceMock.query.mockReturnValue(of(emptyPage()));
    fixture = TestBed.createComponent(PassiveAwarenessFeed);
    component = fixture.componentInstance;
    component.currentOnly.set(true);
    component['refreshSubject'].next();
    await fixture.whenStable();

    expect(
      fixture.nativeElement.querySelector('[data-testid="awareness-filtered-empty"]'),
    ).not.toBeNull();
  });

  it('renders error state on 401', async () => {
    awarenessServiceMock.query.mockReturnValue(
      throwError(() => ({ status: 401, error: { code: 'UNAUTHORIZED' } })),
    );
    fixture = TestBed.createComponent(PassiveAwarenessFeed);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[data-testid="awareness-error"]')).not.toBeNull();
  });

  it('renders error state on 503', async () => {
    awarenessServiceMock.query.mockReturnValue(
      throwError(() => ({ status: 503, error: {} })),
    );
    fixture = TestBed.createComponent(PassiveAwarenessFeed);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[data-testid="awareness-error"]')).not.toBeNull();
  });

  it('calls query on refresh()', () => {
    awarenessServiceMock.query.mockClear();
    component.refresh();
    expect(awarenessServiceMock.query).toHaveBeenCalled();
  });

  it('loading awareness feed does NOT call createScan', () => {
    expect(activeScanServiceMock.createScan).not.toHaveBeenCalled();
  });

  it('pageEnd returns Math.min of (page+1)*size and total', () => {
    expect(component.pageEnd({ page: 0, size: 20, total: 15 })).toBe(15);
    expect(component.pageEnd({ page: 0, size: 20, total: 25 })).toBe(20);
    expect(component.pageEnd({ page: 1, size: 20, total: 25 })).toBe(25);
  });
});

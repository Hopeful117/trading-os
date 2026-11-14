// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { Router } from '@angular/router';

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

import { PassiveCandidateInvestigatePanel, PASSIVE_SCAN_POLL_INTERVAL_MS } from './passive-candidate-investigate-panel';
import { AccountService } from '../../../../core/services/account.service';
import { ActiveScanService } from '../../../../core/services/active-scan.service';
import { PassiveAwarenessItem } from '../../../../core/models/passive-awareness.model';
import { ActiveScanResponse } from '../../../../core/models/active-scan.model';

function makeCandidate(overrides: Partial<PassiveAwarenessItem> = {}): PassiveAwarenessItem {
  return {
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
    observationIds: [],
    observationTimes: [],
    thesisId: 'thesis-1',
    thesisVersion: 1,
    sourceScanId: 'scan-1',
    sourceScanMarketId: 'scan-market-1',
    analysisExecutionId: 'exec-1',
    evaluatedAt: '2026-11-14T10:00:00Z',
    sourceCutoff: null,
    validFrom: '2026-11-14T10:00:00Z',
    validUntil: null,
    createdAt: '2026-11-14T10:00:00Z',
    isCandidate: true,
    isExpired: false,
    isSuperseded: false,
    freshnessSummary: '2 min ago',
    diagnosticCategory: null,
    diagnosticLabels: [],
    ...overrides,
  };
}

function makeCompletedScan(withOpportunity: boolean): ActiveScanResponse {
  return {
    scanId: 'scan-new-1',
    accountId: 'acc-1',
    objective: null,
    status: withOpportunity ? 'COMPLETED' : 'COMPLETED_NO_WORK',
    requestedMarketIds: ['market-1'],
    candidateMarketIds: ['market-1'],
    effectiveMarketIds: ['market-1'],
    resolvedAt: '2026-11-14T10:00:00Z',
    createdAt: '2026-11-14T10:00:00Z',
    updatedAt: '2026-11-14T10:01:00Z',
    progress: { totalCandidates: 1, eligible: 1, excluded: 0, running: 0, completed: 1, failed: 0, opportunitiesFound: withOpportunity ? 1 : 0 },
    markets: withOpportunity
      ? [
          {
            scanMarketId: 'sm-1',
            ordinal: 0,
            marketId: 'market-1',
            eligible: true,
            analysisStatus: 'COMPLETED',
            resultQuality: 'COMPLETE',
            outcome: 'OPPORTUNITY_FOUND',
            analysisExecutionId: 'exec-new-1',
            exclusionReasons: [],
            diagnostic: null,
            opportunities: [{ id: 'new-opp-1', version: 1, marketId: 'market-1' } as never],
            strategy: null,
          },
        ]
      : [
          {
            scanMarketId: 'sm-1',
            ordinal: 0,
            marketId: 'market-1',
            eligible: true,
            analysisStatus: 'COMPLETED',
            resultQuality: 'COMPLETE',
            outcome: 'COMPLETED_NO_OPPORTUNITY',
            analysisExecutionId: 'exec-new-1',
            exclusionReasons: [],
            diagnostic: null,
            opportunities: [],
            strategy: null,
          },
        ],
  };
}

describe('PassiveCandidateInvestigatePanel', () => {
  let component: PassiveCandidateInvestigatePanel;
  let fixture: ComponentFixture<PassiveCandidateInvestigatePanel>;
  let accountServiceMock: { getAccounts: ReturnType<typeof vi.fn> };
  let activeScanServiceMock: {
    createScan: ReturnType<typeof vi.fn>;
    findScan: ReturnType<typeof vi.fn>;
  };
  let routerMock: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    accountServiceMock = {
      getAccounts: vi.fn().mockReturnValue(of([{ accountId: 'acc-1', name: 'Test Account' }])),
    };
    activeScanServiceMock = {
      createScan: vi.fn(),
      findScan: vi.fn(),
    };
    routerMock = { navigate: vi.fn().mockResolvedValue(true) };

    await TestBed.configureTestingModule({
      imports: [PassiveCandidateInvestigatePanel],
      providers: [
        { provide: AccountService, useValue: accountServiceMock },
        { provide: ActiveScanService, useValue: activeScanServiceMock },
        { provide: Router, useValue: routerMock },
        { provide: PASSIVE_SCAN_POLL_INTERVAL_MS, useValue: 25 },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PassiveCandidateInvestigatePanel);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('candidate', makeCandidate());
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows the panel with candidate instrument', () => {
    expect(fixture.nativeElement.textContent).toContain('EURUSD');
  });

  it('Start investigation button is disabled when no account selected', () => {
    expect(component.canStart).toBe(false);
    const btn = fixture.nativeElement.querySelector('[data-testid="start-investigation-btn"]');
    expect(btn.disabled).toBe(true);
  });

  it('Start investigation button is enabled when account selected', () => {
    component.selectedAccountId.set('acc-1');
    fixture.detectChanges();
    expect(component.canStart).toBe(true);
  });

  it('calls createScan with correct passive candidate fields on start', () => {
    const runningScan = { ...makeCompletedScan(false), status: 'RUNNING' } as ActiveScanResponse;
    const doneScan = makeCompletedScan(false);
    activeScanServiceMock.createScan.mockReturnValue(of(runningScan));
    activeScanServiceMock.findScan.mockReturnValue(of(doneScan));

    component.selectedAccountId.set('acc-1');
    component.startInvestigation();

    expect(activeScanServiceMock.createScan).toHaveBeenCalledWith(
      expect.objectContaining({
        accountId: 'acc-1',
        scopeMode: 'SELECTED',
        requestedMarketIds: ['market-1'],
        sourcePassiveOpportunityId: 'opp-1',
        sourcePassiveOpportunityVersion: 1,
      }),
      expect.any(String),
    );
  });

  it('shows no-opportunity state after scan with no results', async () => {
    const polls: Subject<ActiveScanResponse>[] = [];
    activeScanServiceMock.createScan.mockReturnValue(of({ scanId: 'scan-new-1', status: 'RUNNING' }));
    activeScanServiceMock.findScan.mockImplementation(() => {
      const poll = new Subject<ActiveScanResponse>();
      polls.push(poll);
      return poll.asObservable();
    });

    component.selectedAccountId.set('acc-1');
    component.startInvestigation();
    await wait(60);
    await fixture.whenStable();

    polls.at(-1)!.next(makeCompletedScan(false));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-opportunity-state"]')).not.toBeNull();
  });

  it('shows opportunity-found state and Open button when scan finds opportunity', async () => {
    const polls: Subject<ActiveScanResponse>[] = [];
    activeScanServiceMock.createScan.mockReturnValue(of({ scanId: 'scan-new-1', status: 'RUNNING' }));
    activeScanServiceMock.findScan.mockImplementation(() => {
      const poll = new Subject<ActiveScanResponse>();
      polls.push(poll);
      return poll.asObservable();
    });

    component.selectedAccountId.set('acc-1');
    component.startInvestigation();
    await wait(60);
    await fixture.whenStable();

    polls.at(-1)!.next(makeCompletedScan(true));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="opportunity-found-state"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="open-opportunity-btn"]')).not.toBeNull();
  });

  it('navigates to decision-workspace when Open opportunity is clicked', async () => {
    const polls: Subject<ActiveScanResponse>[] = [];
    activeScanServiceMock.createScan.mockReturnValue(of({ scanId: 'scan-new-1', status: 'RUNNING' }));
    activeScanServiceMock.findScan.mockImplementation(() => {
      const poll = new Subject<ActiveScanResponse>();
      polls.push(poll);
      return poll.asObservable();
    });

    component.selectedAccountId.set('acc-1');
    component.startInvestigation();
    await wait(60);
    await fixture.whenStable();

    polls.at(-1)!.next(makeCompletedScan(true));
    await fixture.whenStable();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="open-opportunity-btn"]').click();

    expect(routerMock.navigate).toHaveBeenCalledWith(
      ['/decision-workspace'],
      expect.objectContaining({
        queryParams: expect.objectContaining({
          opportunityId: 'new-opp-1',
          opportunityVersion: 1,
        }),
      }),
    );
  });

  it('shows error state on 409 PASSIVE_CANDIDATE_NOT_CURRENT', async () => {
    activeScanServiceMock.createScan.mockReturnValue(
      throwError(() => ({ status: 409, error: { code: 'PASSIVE_CANDIDATE_NOT_CURRENT' } })),
    );

    component.selectedAccountId.set('acc-1');
    component.startInvestigation();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="error-code"]').textContent).toContain(
      'PASSIVE_CANDIDATE_NOT_CURRENT',
    );
  });

  it('shows error state on 409 PASSIVE_CANDIDATE_SUPERSEDED', async () => {
    activeScanServiceMock.createScan.mockReturnValue(
      throwError(() => ({ status: 409, error: { code: 'PASSIVE_CANDIDATE_SUPERSEDED' } })),
    );

    component.selectedAccountId.set('acc-1');
    component.startInvestigation();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).not.toBeNull();
  });

  it('loading panel does NOT call createScan on mount', () => {
    expect(activeScanServiceMock.createScan).not.toHaveBeenCalled();
  });
});

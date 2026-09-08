import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { ScanPanel, ScanPanelView } from './scan-panel';
import { SCAN_POLL_INTERVAL_MS } from './scan-poll-interval';
import { AccountService } from '../../../core/services/account.service';
import { ActiveScanService } from '../../../core/services/active-scan.service';
import {
  ActiveScanMarketResult,
  ActiveScanProgress,
  ActiveScanResponse,
} from '../../../core/models/active-scan.model';
import { MarketService } from '../../../core/services/market.service';
import { OpportunityResponse } from '../../../core/models/opportunity.model';

describe('ScanPanel', () => {
  let fixture: ComponentFixture<ScanPanel>;
  let activeScanServiceMock: {
    createScan: ReturnType<typeof vi.fn>;
    findScan: ReturnType<typeof vi.fn>;
  };
  let accountServiceMock: { getAccounts: ReturnType<typeof vi.fn> };
  let marketServiceMock: { findAll: ReturnType<typeof vi.fn> };

  const accounts = [
    { accountId: 'a1', name: 'Main account' },
    { accountId: 'a2', name: 'Challenge account' },
  ];
  const markets = [
    {
      marketId: 'm1',
      provider: 'KRAKEN',
      symbol: 'BTC/EUR',
      baseAsset: 'BTC',
      quoteAsset: 'EUR',
      marketState: {
        tradingStatus: 'ONLINE',
        tradable: true,
        closureReason: '',
        lastUpdated: '2026-08-25T10:00:00Z',
      },
      marketConstraints: {
        minimumOrderSize: 0.0001,
        minimumCost: 10,
        tickSize: 0.1,
        quantityPrecision: 8,
        pricePrecision: 1,
      },
    },
    {
      marketId: 'm2',
      provider: 'KRAKEN',
      symbol: 'ETH/EUR',
      baseAsset: 'ETH',
      quoteAsset: 'EUR',
      marketState: {
        tradingStatus: 'ONLINE',
        tradable: true,
        closureReason: '',
        lastUpdated: '2026-08-25T10:00:00Z',
      },
      marketConstraints: {
        minimumOrderSize: 0.001,
        minimumCost: 10,
        tickSize: 0.01,
        quantityPrecision: 8,
        pricePrecision: 2,
      },
    },
  ];

  function progress(overrides: Partial<ActiveScanProgress> = {}): ActiveScanProgress {
    return {
      totalCandidates: 3,
      eligible: 3,
      excluded: 0,
      running: 0,
      completed: 3,
      failed: 0,
      opportunitiesFound: 2,
      ...overrides,
    };
  }

  function scan(overrides: Partial<ActiveScanResponse> = {}): ActiveScanResponse {
    return {
      scanId: 'scan-1',
      accountId: 'a1',
      objective: null,
      status: 'RUNNING',
      requestedMarketIds: null,
      candidateMarketIds: ['m1'],
      effectiveMarketIds: ['m1'],
      resolvedAt: '2026-08-25T10:00:00Z',
      createdAt: '2026-08-25T09:59:59Z',
      updatedAt: '2026-08-25T10:00:01Z',
      progress: progress(),
      markets: [],
      ...overrides,
    };
  }

  function opportunity(id: string, instrument: string): OpportunityResponse {
    return {
      id,
      version: 1,
      status: 'ACTIVE',
      instrument,
      direction: 'LONG',
      scenario: 'Breakout continuation',
      timeframe: '15m',
      type: 'INTRADAY',
      origin: 'PASSIVE_SCAN',
      score: 82.5,
      explanation: 'Confirmed setup',
      observationIds: [],
      aiAnalysisIds: [],
      evaluatedAt: '2026-08-25T10:00:00Z',
      validFrom: '2026-08-25T10:00:00Z',
      validUntil: null,
      createdAt: '2026-08-25T10:00:00Z',
      strategyMatchId: `match-${id}`,
    };
  }

  function marketResult(
    marketId: string,
    outcome: ActiveScanMarketResult['outcome'],
    opportunities: OpportunityResponse[] = [],
  ): ActiveScanMarketResult {
    return {
      scanMarketId: `scan-${marketId}`,
      ordinal: 0,
      marketId,
      eligible: outcome !== 'EXCLUDED',
      analysisStatus: outcome === 'EXCLUDED' ? null : 'COMPLETED',
      resultQuality: outcome === 'EXCLUDED' ? null : 'COMPLETE',
      outcome,
      analysisExecutionId: outcome === 'EXCLUDED' ? null : `execution-${marketId}`,
      exclusionReasons: outcome === 'EXCLUDED' ? ['MARKET_NOT_TRADABLE'] : [],
      diagnostic:
        outcome === 'FAILED' ? { code: 'ANALYSIS_FAILED', message: 'Analysis failed' } : null,
      opportunities,
      strategy: null,
    };
  }

  const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

  async function createComponent(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ScanPanel],
      providers: [
        { provide: ActiveScanService, useValue: activeScanServiceMock },
        { provide: AccountService, useValue: accountServiceMock },
        { provide: MarketService, useValue: marketServiceMock },
        { provide: SCAN_POLL_INTERVAL_MS, useValue: 25 },
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ScanPanel);
    await fixture.whenStable();
  }

  async function selectAccountAndRun(): Promise<void> {
    const component = fixture.componentInstance;
    component.accountId = 'a1';
    component.scopeMode = 'ALL_ELIGIBLE';
    component.runScan();
    await fixture.whenStable();
  }

  beforeEach(() => {
    activeScanServiceMock = {
      createScan: vi.fn().mockReturnValue(of(scan({ status: 'READY_TO_DISPATCH' }))),
      findScan: vi.fn().mockReturnValue(of(scan())),
    };
    accountServiceMock = { getAccounts: vi.fn().mockReturnValue(of(accounts)) };
    marketServiceMock = { findAll: vi.fn().mockReturnValue(of(markets)) };
  });

  it('should create', async () => {
    await createComponent();

    expect(fixture.componentInstance).toBeTruthy();
  });

  describe('account selection', () => {
    it('renders one option per account', async () => {
      await createComponent();

      const options = fixture.nativeElement.querySelectorAll(
        '[data-testid="account-select"] option',
      );
      expect(options.length).toBe(3);
      expect(fixture.nativeElement.textContent).toContain('Main account');
    });

    it('shows an inline error with retry when accounts fail to load', async () => {
      accountServiceMock.getAccounts.mockReturnValue(throwError(() => new Error('boom')));
      await createComponent();

      expect(fixture.nativeElement.querySelector('[data-testid="accounts-error"]')).not.toBeNull();
    });

    it('reloads accounts when retry is clicked', async () => {
      accountServiceMock.getAccounts.mockReturnValueOnce(throwError(() => new Error('boom')));
      await createComponent();

      accountServiceMock.getAccounts.mockClear();
      fixture.nativeElement.querySelector('.retry-button').click();
      await fixture.whenStable();

      expect(accountServiceMock.getAccounts).toHaveBeenCalled();
    });
  });

  describe('trigger protection', () => {
    it('disables the run button until account and explicit scope are selected', async () => {
      await createComponent();

      const button = fixture.nativeElement.querySelector('[data-testid="run-scan-button"]');
      expect(button.disabled).toBe(true);

      const accountSelect = fixture.nativeElement.querySelector('[data-testid="account-select"]');
      accountSelect.value = 'a1';
      accountSelect.dispatchEvent(new Event('change'));
      await fixture.whenStable();
      expect(button.disabled).toBe(true);
    });

    it('sends explicit all-eligible scope by omitting requestedMarketIds', async () => {
      await createComponent();
      fixture.componentInstance.objective = '  trend setups  ';
      await selectAccountAndRun();
      await wait(80);
      await fixture.whenStable();

      expect(activeScanServiceMock.createScan).toHaveBeenCalledTimes(1);
      const [request] = activeScanServiceMock.createScan.mock.calls[0];
      expect(request).toEqual({ accountId: 'a1', objective: 'trend setups' });
    });

    it('sends every specifically selected market id', async () => {
      await createComponent();
      fixture.componentInstance.accountId = 'a1';
      fixture.componentInstance.scopeMode = 'SPECIFIC';
      fixture.componentInstance.selectedMarketIds = ['m1', 'm2'];

      fixture.componentInstance.runScan();
      await fixture.whenStable();

      const [request] = activeScanServiceMock.createScan.mock.calls[0];
      expect(request).toEqual({ accountId: 'a1', requestedMarketIds: ['m1', 'm2'] });
    });

    it('ignores repeated triggers while a scan session is active', async () => {
      const never = new Subject<ActiveScanResponse>();
      activeScanServiceMock.createScan.mockReturnValue(never.asObservable());
      await createComponent();

      const views: ScanPanelView[] = [];
      fixture.componentInstance.view$.subscribe((view) => views.push(view));

      fixture.componentInstance.accountId = 'a1';
      fixture.componentInstance.scopeMode = 'ALL_ELIGIBLE';
      fixture.componentInstance.runScan();
      fixture.componentInstance.runScan();
      await fixture.whenStable();

      expect(activeScanServiceMock.createScan).toHaveBeenCalledTimes(1);
      expect(views.some((view) => view.status === 'submitting' || view.status === 'running')).toBe(
        true,
      );
    });
  });

  describe('market scope', () => {
    it('renders catalogue markets only after specific scope is chosen', async () => {
      await createComponent();
      fixture.nativeElement.querySelector('[data-testid="specific-scope"]').click();
      await fixture.whenStable();

      const options = fixture.nativeElement.querySelectorAll(
        '[data-testid="market-select"] option',
      );
      expect(options.length).toBe(2);
      expect(fixture.nativeElement.textContent).toContain('BTC/EUR');
    });

    it('keeps all-eligible available when catalogue loading fails and supports retry', async () => {
      marketServiceMock.findAll.mockReturnValueOnce(throwError(() => new Error('offline')));
      activeScanServiceMock.createScan.mockReturnValue(of(scan({ status: 'COMPLETED' })));
      await createComponent();

      const specific = fixture.nativeElement.querySelector('[data-testid="specific-scope"]');
      const allEligible = fixture.nativeElement.querySelector('[data-testid="all-eligible-scope"]');
      expect(specific.disabled).toBe(true);
      expect(allEligible.disabled).toBe(false);
      expect(fixture.nativeElement.querySelector('[data-testid="markets-error"]')).not.toBeNull();

      fixture.componentInstance.accountId = 'a1';
      fixture.componentInstance.scopeMode = 'ALL_ELIGIBLE';
      fixture.componentInstance.runScan();
      await fixture.whenStable();
      expect(activeScanServiceMock.createScan.mock.calls[0][0]).toEqual({ accountId: 'a1' });

      marketServiceMock.findAll.mockReturnValue(of(markets));
      fixture.nativeElement.querySelector('[data-testid="markets-error"] button').click();
      await fixture.whenStable();
      expect(marketServiceMock.findAll).toHaveBeenCalledTimes(2);
    });
  });

  describe('scan session', () => {
    it('shows the running state then the terminal result and stops polling', async () => {
      const polls: Subject<ActiveScanResponse>[] = [];
      activeScanServiceMock.findScan.mockImplementation(() => {
        const poll = new Subject<ActiveScanResponse>();
        polls.push(poll);
        return poll.asObservable();
      });

      await createComponent();
      await selectAccountAndRun();
      await fixture.whenStable();

      expect(fixture.nativeElement.querySelector('[data-testid="scan-running"]')).not.toBeNull();

      await wait(80);
      await fixture.whenStable();

      const pollsBeforeEmission = activeScanServiceMock.findScan.mock.calls.length;
      expect(pollsBeforeEmission).toBeGreaterThanOrEqual(1);

      polls.at(-1)!.next(scan({ status: 'COMPLETED' }));
      await fixture.whenStable();

      const terminal = fixture.nativeElement.querySelector('[data-testid="scan-terminal"]');
      expect(terminal).not.toBeNull();
      expect(terminal.getAttribute('data-status')).toBe('COMPLETED');

      const callsAtTerminal = activeScanServiceMock.findScan.mock.calls.length;
      await wait(100);
      await fixture.whenStable();

      expect(activeScanServiceMock.findScan.mock.calls.length).toBe(callsAtTerminal);
    });

    it('emits scanCompleted once when the scan reaches a terminal status', async () => {
      activeScanServiceMock.findScan.mockReturnValue(of(scan({ status: 'COMPLETED' })));
      await createComponent();

      const completions: ActiveScanResponse[] = [];
      fixture.componentInstance.scanCompleted.subscribe((scan) => completions.push(scan));

      await selectAccountAndRun();
      await wait(80);
      await fixture.whenStable();

      expect(completions.map((scan) => scan.status)).toEqual(['COMPLETED']);
    });

    it('does not poll when creation already returns a terminal scan', async () => {
      activeScanServiceMock.createScan.mockReturnValue(of(scan({ status: 'COMPLETED' })));
      await createComponent();

      await selectAccountAndRun();
      await wait(80);

      expect(activeScanServiceMock.findScan).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('[data-testid="scan-terminal"]')).not.toBeNull();
    });

    it('distinguishes a successful zero-opportunity scan from an error', async () => {
      activeScanServiceMock.findScan.mockReturnValue(
        of(scan({ status: 'COMPLETED', progress: progress({ opportunitiesFound: 0 }) })),
      );
      await createComponent();

      await selectAccountAndRun();
      await wait(80);
      await fixture.whenStable();

      const terminal = fixture.nativeElement.querySelector('[data-testid="scan-terminal"]');
      expect(terminal).not.toBeNull();
      expect(fixture.nativeElement.textContent).not.toContain('unavailable');
      expect(
        fixture.nativeElement.querySelector('[data-testid="zero-opportunity-note"]'),
      ).not.toBeNull();
    });

    it('renders completed-without-work as a normal outcome', async () => {
      activeScanServiceMock.findScan.mockReturnValue(of(scan({ status: 'COMPLETED_NO_WORK' })));
      await createComponent();

      await selectAccountAndRun();
      await wait(80);
      await fixture.whenStable();

      const terminal = fixture.nativeElement.querySelector('[data-testid="scan-terminal"]');
      expect(terminal.getAttribute('data-status')).toBe('COMPLETED_NO_WORK');
      expect(terminal.textContent).toContain('No eligible market to scan right now');
    });

    it('renders a failed scan as terminal failure without hiding the list refresh note', async () => {
      activeScanServiceMock.findScan.mockReturnValue(of(scan({ status: 'FAILED' })));
      await createComponent();

      await selectAccountAndRun();
      await wait(80);
      await fixture.whenStable();

      const terminal = fixture.nativeElement.querySelector('[data-testid="scan-terminal"]');
      expect(terminal.getAttribute('data-status')).toBe('FAILED');
      expect(terminal.textContent).toContain('Scan failed.');
    });
  });

  describe('error handling', () => {
    async function runExpectingError(error: unknown, expectedCode: string): Promise<void> {
      activeScanServiceMock.createScan.mockReturnValue(throwError(() => error));
      await createComponent();
      await selectAccountAndRun();

      const errorCard = fixture.nativeElement.querySelector('[data-testid="scan-error"]');
      expect(errorCard).not.toBeNull();
      expect(errorCard.getAttribute('data-error')).toBe(expectedCode);
    }

    it('maps HTTP 409 to a conflict message', async () => {
      await runExpectingError(new HttpErrorResponse({ status: 409 }), 'CONFLICT');
    });

    it('maps HTTP 401 to an unauthorized message', async () => {
      await runExpectingError(new HttpErrorResponse({ status: 401 }), 'UNAUTHORIZED');
    });

    it('maps other backend failures to an unavailable message', async () => {
      await runExpectingError(new HttpErrorResponse({ status: 503 }), 'UNAVAILABLE');
    });
  });

  describe('per-market results', () => {
    it('renders every opportunity as a distinct detail link', async () => {
      const first = opportunity('o1', 'BTC/EUR');
      const second = opportunity('o2', 'BTC/EUR');
      activeScanServiceMock.createScan.mockReturnValue(
        of(
          scan({
            status: 'COMPLETED',
            progress: progress({ opportunitiesFound: 2 }),
            markets: [marketResult('m1', 'OPPORTUNITY_FOUND', [first, second])],
          }),
        ),
      );
      await createComponent();
      const navigateByUrl = vi
        .spyOn(TestBed.inject(Router), 'navigateByUrl')
        .mockResolvedValue(true);

      await selectAccountAndRun();
      fixture.detectChanges();

      const links = fixture.nativeElement.querySelectorAll('.opportunity-chip');
      expect(links.length).toBe(2);
      expect(links[0].getAttribute('href')).toBe('/opportunities/o1');
      links[1].click();
      expect(navigateByUrl).toHaveBeenCalled();
    });

    it('filters outcomes without hiding successful siblings from the full result', async () => {
      activeScanServiceMock.createScan.mockReturnValue(
        of(
          scan({
            status: 'PARTIALLY_COMPLETED',
            markets: [
              marketResult('m1', 'OPPORTUNITY_FOUND', [opportunity('o1', 'BTC/EUR')]),
              marketResult('m2', 'FAILED'),
            ],
          }),
        ),
      );
      await createComponent();
      await selectAccountAndRun();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelectorAll('[data-testid="market-result"]').length).toBe(
        2,
      );
      const filter = fixture.nativeElement.querySelector('[data-testid="result-filter"]');
      filter.value = 'FAILED';
      filter.dispatchEvent(new Event('change'));
      await fixture.whenStable();

      const rows = fixture.nativeElement.querySelectorAll('[data-testid="market-result"]');
      expect(rows.length).toBe(1);
      expect(rows[0].getAttribute('data-outcome')).toBe('FAILED');
      expect(rows[0].textContent).toContain('Analysis failed');
    });

    it('classifies every supported result filter from backend outcomes', async () => {
      await createComponent();
      const component = fixture.componentInstance;
      const result = scan({
        markets: [
          marketResult('m1', 'OPPORTUNITY_FOUND', [opportunity('o1', 'BTC/EUR')]),
          marketResult('m2', 'COMPLETED_NO_OPPORTUNITY'),
          marketResult('m3', 'EXCLUDED'),
          marketResult('m4', 'FAILED'),
          marketResult('m5', 'RUNNING'),
        ],
      });

      component.resultFilter = 'OPPORTUNITY';
      expect(component.filteredMarkets(result).map((market) => market.marketId)).toEqual(['m1']);
      component.resultFilter = 'NO_OPPORTUNITY';
      expect(component.filteredMarkets(result).map((market) => market.marketId)).toEqual(['m2']);
      component.resultFilter = 'EXCLUDED';
      expect(component.filteredMarkets(result).map((market) => market.marketId)).toEqual(['m3']);
      component.resultFilter = 'FAILED';
      expect(component.filteredMarkets(result).map((market) => market.marketId)).toEqual(['m4']);
      component.resultFilter = 'PROCESSING';
      expect(component.filteredMarkets(result).map((market) => market.marketId)).toEqual(['m5']);
    });
  });
});

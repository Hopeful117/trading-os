import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';

import { ScanPanel, ScanPanelView } from './scan-panel';
import { SCAN_POLL_INTERVAL_MS } from './scan-poll-interval';
import { AccountService } from '../../../core/services/account.service';
import { ActiveScanService } from '../../../core/services/active-scan.service';
import { ActiveScanResponse, ActiveScanProgress } from '../../../core/models/active-scan.model';

describe('ScanPanel', () => {
  let fixture: ComponentFixture<ScanPanel>;
  let activeScanServiceMock: {
    createScan: ReturnType<typeof vi.fn>;
    findScan: ReturnType<typeof vi.fn>;
  };
  let accountServiceMock: { getAccounts: ReturnType<typeof vi.fn> };

  const accounts = [
    { accountId: 'a1', name: 'Main account' },
    { accountId: 'a2', name: 'Challenge account' },
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

  const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

  async function createComponent(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ScanPanel],
      providers: [
        { provide: ActiveScanService, useValue: activeScanServiceMock },
        { provide: AccountService, useValue: accountServiceMock },
        { provide: SCAN_POLL_INTERVAL_MS, useValue: 25 },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ScanPanel);
    await fixture.whenStable();
  }

  async function selectAccountAndRun(): Promise<void> {
    const component = fixture.componentInstance;
    component.accountId = 'a1';
    component.runScan();
    await fixture.whenStable();
  }

  beforeEach(() => {
    activeScanServiceMock = {
      createScan: vi.fn().mockReturnValue(of(scan({ status: 'READY_TO_DISPATCH' }))),
      findScan: vi.fn().mockReturnValue(of(scan())),
    };
    accountServiceMock = { getAccounts: vi.fn().mockReturnValue(of(accounts)) };
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
    it('disables the run button while no account is selected', async () => {
      await createComponent();

      const button = fixture.nativeElement.querySelector('[data-testid="run-scan-button"]');
      expect(button.disabled).toBe(true);
    });

    it('sends the selected account and objective with an Idempotency-Key', async () => {
      await createComponent();
      fixture.componentInstance.objective = '  trend setups  ';
      await selectAccountAndRun();
      await wait(80);
      await fixture.whenStable();

      expect(activeScanServiceMock.createScan).toHaveBeenCalledTimes(1);
      const [request] = activeScanServiceMock.createScan.mock.calls[0];
      expect(request).toEqual({ accountId: 'a1', objective: 'trend setups' });
    });

    it('ignores repeated triggers while a scan session is active', async () => {
      const never = new Subject<ActiveScanResponse>();
      activeScanServiceMock.createScan.mockReturnValue(never.asObservable());
      await createComponent();

      const views: ScanPanelView[] = [];
      fixture.componentInstance.view$.subscribe((view) => views.push(view));

      fixture.componentInstance.accountId = 'a1';
      fixture.componentInstance.runScan();
      fixture.componentInstance.runScan();
      await fixture.whenStable();

      expect(activeScanServiceMock.createScan).toHaveBeenCalledTimes(1);
      expect(views.some((view) => view.status === 'submitting' || view.status === 'running')).toBe(
        true,
      );
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
});

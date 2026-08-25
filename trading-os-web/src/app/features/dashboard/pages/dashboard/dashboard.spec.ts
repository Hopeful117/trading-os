import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Account } from '../../../../core/models/account.model';
import { ActiveScanSummary } from '../../../../core/models/active-scan.model';
import { DashboardSummary } from '../../../../core/models/dashboard-summary.model';
import { OpportunityResponse } from '../../../../core/models/opportunity.model';
import { AccountService } from '../../../../core/services/account.service';
import { ActiveScanService } from '../../../../core/services/active-scan.service';
import { DashboardService } from '../../../../core/services/dashboard.service';
import { OpportunityService } from '../../../../core/services/opportunity.service';
import { Dashboard } from './dashboard';

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let dashboardService: { findDashboard: ReturnType<typeof vi.fn> };
  let opportunityService: { findActive: ReturnType<typeof vi.fn> };
  let activeScanService: { findRecent: ReturnType<typeof vi.fn> };

  const accounts: Account[] = [
    {
      accountId: 'account-1',
      name: 'Primary',
      baseCurrency: 'USD',
      balances: { balances: { USD: 1000 } },
      equity: 1000,
      peakEquity: 1100,
      rulesId: 'rules-1',
      userId: 'user-1',
    },
    {
      accountId: 'account-2',
      name: 'Secondary',
      baseCurrency: 'USD',
      balances: { balances: { USD: 500 } },
      equity: 500,
      peakEquity: 500,
      rulesId: 'rules-2',
      userId: 'user-1',
    },
  ];

  const activeOpps: OpportunityResponse[] = [
    makeOpportunity('opp-1', 'BTC/USD'),
    makeOpportunity('opp-2', 'ETH/USD'),
  ];

  beforeEach(async () => {
    dashboardService = { findDashboard: vi.fn(() => of(summary())) };
    opportunityService = { findActive: vi.fn(() => of(activeOpps)) };
    activeScanService = { findRecent: vi.fn(() => of([])) };
    await configureTestingModule(
      { getAccounts: () => of(accounts) },
      dashboardService,
      opportunityService,
      activeScanService,
    );
  });

  it('displays main metrics and an account without position', async () => {
    await create();
    expect(text()).toContain('1,020');
    expect(text()).toContain('Aucune position ouverte');
    expect(text()).toContain('LIVE');
  });

  it('displays multiple positions and positive or negative pnl classes', async () => {
    const data = summary();
    data.openPositions = [pos('p1', 'BTC/USD', 25), pos('p2', 'ETH/USD', -15)];
    dashboardService.findDashboard.mockReturnValue(of(data));
    await create();
    expect(text()).toContain('BTC/USD');
    expect(text()).toContain('ETH/USD');
    expect(fixture.nativeElement.querySelectorAll('td.positive')).toHaveLength(1);
    expect(fixture.nativeElement.querySelectorAll('td.negative')).toHaveLength(1);
  });

  it('displays alerts and degraded status', async () => {
    const data = summary();
    data.freshness.status = 'DEGRADED';
    data.freshness.warnings = ['Market Data Service indisponible'];
    data.alerts = [
      {
        code: 'MISSING_STOP_LOSS',
        severity: 'WARNING',
        title: 'Position non protégée',
        message: 'Aucun stop loss',
        marketId: null,
        positionId: 'p1',
        occurredAt: new Date().toISOString(),
      },
    ];
    dashboardService.findDashboard.mockReturnValue(of(data));
    await create();
    expect(text()).toContain('DEGRADED');
    expect(text()).toContain('Position non protégée');
  });

  it('keeps an explicit error state', async () => {
    dashboardService.findDashboard.mockReturnValue(throwError(() => new Error('unavailable')));
    await create();
    expect(text()).toContain('Dashboard est temporairement indisponible');
  });

  it('returns empty string for null or zero pnl', () => {
    const comp = TestBed.createComponent(Dashboard).componentInstance;
    expect(comp.pnlClass(null)).toBe('');
    expect(comp.pnlClass(0)).toBe('');
    expect(comp.pnlClass(10)).toBe('positive');
    expect(comp.pnlClass(-10)).toBe('negative');
  });

  it('polls the selected account', async () => {
    await create();
    fixture.componentInstance.selectAccount('account-2');
    await nextTask();
    fixture.detectChanges();
    expect(dashboardService.findDashboard).toHaveBeenCalledWith('account-2');
  });

  it('shows empty state when no accounts exist', async () => {
    await reconfigure(
      { getAccounts: () => of([]) },
      dashboardService,
      opportunityService,
      activeScanService,
    );
    expect(text()).toContain('Aucun compte');
    expect(dashboardService.findDashboard).not.toHaveBeenCalled();
  });

  it('displays null fields as unavailable and renders risk rules', async () => {
    const data = summary();
    data.account.equity = null;
    data.account.dailyPnl = null;
    data.account.currentDrawdown = null;
    data.risk.rules = [
      {
        code: 'MAX_DAILY_LOSS',
        label: 'Perte max/jour',
        limit: 5,
        currentValue: 3.2,
        status: 'WARNING',
      },
    ];
    dashboardService.findDashboard.mockReturnValue(of(data));
    await create();
    const t = text();
    expect(t).toContain('Indisponible');
    expect(t).toContain('Perte max/jour');
    expect(t).toContain('3.20');
  });

  it('shows error state when accounts request fails', async () => {
    await reconfigure(
      { getAccounts: () => throwError(() => new Error('down')) },
      dashboardService,
      opportunityService,
      activeScanService,
    );
    expect(text()).toContain('Impossible de charger les comptes');
    expect(dashboardService.findDashboard).not.toHaveBeenCalled();
  });

  it('shows CALCULATED equity source label', async () => {
    const data = summary();
    data.account.equitySource = 'CALCULATED';
    dashboardService.findDashboard.mockReturnValue(of(data));
    await create();
    expect(text()).toContain('calculé à partir du compte broker');
  });

  it('shows BROKER equity source label', async () => {
    const data = summary();
    data.account.equitySource = 'BROKER';
    dashboardService.findDashboard.mockReturnValue(of(data));
    await create();
    expect(text()).toContain('Provenance : broker');
  });

  it('shows risk explanation when status is UNAVAILABLE', async () => {
    const data = summary();
    data.risk.status = 'UNAVAILABLE';
    data.risk.rules = [];
    dashboardService.findDashboard.mockReturnValue(of(data));
    await create();
    expect(text()).toContain('Aucune règle de risque configurée');
  });

  it('shows risk rules when status is not UNAVAILABLE', async () => {
    const data = summary();
    data.risk.status = 'SAFE';
    data.risk.rules = [
      {
        code: 'MAX_DAILY_LOSS',
        label: 'Perte max/jour',
        limit: 5,
        currentValue: 1.5,
        status: 'SAFE',
      },
    ];
    dashboardService.findDashboard.mockReturnValue(of(data));
    await create();
    expect(text()).toContain('Perte max/jour');
    expect(text()).not.toContain('Aucune règle de risque configurée');
  });

  it('shows active opportunities count', async () => {
    await create();
    expect(text()).toContain('Market Intelligence');
    expect(text()).toContain('Opportunités actives');
    expect(text()).toContain('2');
  });

  it('shows zero active opportunities', async () => {
    opportunityService.findActive.mockReturnValue(of([]));
    await create();
    expect(text()).toContain('Opportunités actives');
  });

  it('shows opportunities error without breaking scan', async () => {
    opportunityService.findActive.mockReturnValue(throwError(() => new Error('mi down')));
    activeScanService.findRecent.mockReturnValue(
      of([{ scanId: 's1', accountId: 'a1', status: 'RUNNING', objective: null, createdAt: '2025-01-01T00:00:00Z', updatedAt: '2025-01-01T00:00:00Z' }]),
    );
    await create();
    expect(text()).toContain('Opportunités temporairement indisponibles');
    expect(text()).toContain('En cours');
  });

  it('MI failure does not break account dashboard', async () => {
    opportunityService.findActive.mockReturnValue(throwError(() => new Error('mi down')));
    dashboardService.findDashboard.mockReturnValue(of(summary()));
    await create();
    expect(text()).toContain('1,020');
    expect(text()).toContain('LIVE');
    expect(text()).toContain('Opportunités temporairement indisponibles');
  });

  it('account dashboard failure does not fabricate MI values', async () => {
    dashboardService.findDashboard.mockReturnValue(throwError(() => new Error('tc down')));
    opportunityService.findActive.mockReturnValue(of(activeOpps));
    await create();
    expect(text()).toContain('Dashboard est temporairement indisponible');
    expect(text()).toContain('2');
  });

  it('shows performance not available message', async () => {
    await create();
    expect(text()).toContain("L'historique d'equity n'est pas encore disponible");
  });

  it('equitySourceLabel returns correct labels', () => {
    const comp = TestBed.createComponent(Dashboard).componentInstance;
    expect(comp.equitySourceLabel('BROKER')).toContain('broker');
    expect(comp.equitySourceLabel('CALCULATED')).toContain('calculé');
    expect(comp.equitySourceLabel('UNKNOWN')).toContain('UNKNOWN');
  });

  // --- Active Scan tests ---

  it('shows latest scan status', async () => {
    activeScanService.findRecent.mockReturnValue(
      of([{ scanId: 's1', accountId: 'a1', status: 'RUNNING', objective: 'test', createdAt: '2025-01-01T00:00:00Z', updatedAt: '2025-01-01T00:00:00Z' }]),
    );
    await create();
    expect(text()).toContain('Dernier scan');
    expect(text()).toContain('En cours');
  });

  it('shows "Aucun scan exécuté" when no scans', async () => {
    activeScanService.findRecent.mockReturnValue(of([]));
    await create();
    expect(text()).toContain('Aucun scan exécuté');
  });

  it('shows scan error state', async () => {
    activeScanService.findRecent.mockReturnValue(throwError(() => new Error('scan down')));
    await create();
    expect(text()).toContain('Dernier scan');
    expect(text()).toContain('Indisponible');
  });

  it('scan error does not break opportunities', async () => {
    activeScanService.findRecent.mockReturnValue(throwError(() => new Error('scan down')));
    opportunityService.findActive.mockReturnValue(of(activeOpps));
    await create();
    expect(text()).toContain('Opportunités actives');
    expect(text()).toContain('2');
    expect(text()).toContain('Dernier scan');
    expect(text()).toContain('Indisponible');
  });

  it('scan loading shows loading indicator', async () => {
    activeScanService.findRecent.mockReturnValue(new Observable(() => {}));
    await create();
    expect(text()).toContain('Dernier scan');
    expect(text()).toContain('…');
  });

  it('shows terminal scan statuses', async () => {
    activeScanService.findRecent.mockReturnValue(
      of([{ scanId: 's1', accountId: 'a1', status: 'COMPLETED', objective: null, createdAt: '2025-01-01T00:00:00Z', updatedAt: '2025-01-01T00:00:00Z' }]),
    );
    await create();
    expect(text()).toContain('Terminé');
  });

  it('shows FAILED scan status', async () => {
    activeScanService.findRecent.mockReturnValue(
      of([{ scanId: 's1', accountId: 'a1', status: 'FAILED', objective: null, createdAt: '2025-01-01T00:00:00Z', updatedAt: '2025-01-01T00:00:00Z' }]),
    );
    await create();
    expect(text()).toContain('Échoué');
  });

  it('scanStatusLabel returns correct labels', () => {
    const comp = TestBed.createComponent(Dashboard).componentInstance;
    expect(comp.scanStatusLabel('READY_TO_DISPATCH')).toBe('Prêt');
    expect(comp.scanStatusLabel('DISPATCH_REQUESTED')).toBe('En attente');
    expect(comp.scanStatusLabel('RUNNING')).toBe('En cours');
    expect(comp.scanStatusLabel('PARTIALLY_COMPLETED')).toBe('Partiellement terminé');
    expect(comp.scanStatusLabel('COMPLETED')).toBe('Terminé');
    expect(comp.scanStatusLabel('FAILED')).toBe('Échoué');
    expect(comp.scanStatusLabel('COMPLETED_NO_WORK')).toBe('Terminé (aucun résultat)');
  });

  it('scanStatusClass returns correct classes', () => {
    const comp = TestBed.createComponent(Dashboard).componentInstance;
    expect(comp.scanStatusClass('RUNNING')).toBe('active');
    expect(comp.scanStatusClass('COMPLETED')).toBe('terminal');
    expect(comp.scanStatusClass('FAILED')).toBe('terminal');
  });

  // --- Helpers ---

  async function configureTestingModule(
    accountProvider: { getAccounts: () => Observable<Account[]> },
    dashService: { findDashboard: ReturnType<typeof vi.fn> },
    oppService: { findActive: ReturnType<typeof vi.fn> },
    scanService: { findRecent: ReturnType<typeof vi.fn> } = { findRecent: vi.fn(() => of([])) },
  ): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideRouter([]),
        { provide: AccountService, useValue: accountProvider },
        { provide: DashboardService, useValue: dashService },
        { provide: OpportunityService, useValue: oppService },
        { provide: ActiveScanService, useValue: scanService },
      ],
    }).compileComponents();
  }

  async function reconfigure(
    accountProvider: { getAccounts: () => Observable<Account[]> },
    dashService: { findDashboard: ReturnType<typeof vi.fn> },
    oppService: { findActive: ReturnType<typeof vi.fn> },
    scanService: { findRecent: ReturnType<typeof vi.fn> } = { findRecent: vi.fn(() => of([])) },
  ): Promise<void> {
    await configureTestingModule(accountProvider, dashService, oppService, scanService);
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    await nextTask();
    fixture.detectChanges();
  }

  async function create(): Promise<void> {
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    await nextTask();
    fixture.detectChanges();
  }

  function nextTask(): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, 0));
  }

  function text(): string {
    return fixture.nativeElement.textContent;
  }

  function summary(): DashboardSummary {
    const now = new Date().toISOString();
    return {
      account: {
        accountId: 'account-1',
        accountName: 'Primary',
        broker: 'KRAKEN',
        currency: 'USD',
        balance: 1000,
        equity: 1020,
        dailyPnl: 20,
        dailyPnlPercentage: 2,
        currentDrawdown: 80,
        currentDrawdownPercentage: 7.27,
        equitySource: 'CALCULATED',
      },
      risk: {
        status: 'SAFE',
        usedRiskAmount: 10,
        usedRiskPercentage: 1,
        remainingRiskAmount: 10,
        remainingRiskPercentage: 1,
        dailyLossAmount: 0,
        dailyLossPercentage: 0,
        maximumDailyLossPercentage: 5,
        totalDrawdownAmount: 80,
        totalDrawdownPercentage: 7.27,
        maximumDrawdownPercentage: 10,
        rules: [],
      },
      openPositions: [],
      alerts: [],
      watchedMarkets: [],
      freshness: {
        status: 'LIVE',
        brokerDataAt: now,
        marketDataAt: now,
        calculatedAt: now,
        brokerDataStale: false,
        marketDataStale: false,
        warnings: [],
      },
      generatedAt: now,
    };
  }

  function pos(id: string, symbol: string, pnl: number) {
    const now = new Date().toISOString();
    return {
      positionId: id,
      accountId: 'account-1',
      marketId: 'market-1',
      symbol,
      side: 'BUY' as const,
      quantity: 1,
      entryPrice: 100,
      currentPrice: 100 + pnl,
      stopLoss: null,
      takeProfit: null,
      unrealizedPnl: pnl,
      unrealizedPnlPercentage: pnl,
      brokerUnrealizedPnl: pnl,
      riskAmount: 0,
      riskPercentage: 0,
      exposure: 100 + pnl,
      protectionStatus: 'MISSING_STOP_LOSS' as const,
      marketTradable: true,
      openedAt: now,
      priceOccurredAt: now,
      calculatedAt: now,
    };
  }

  function makeOpportunity(id: string, instrument: string): OpportunityResponse {
    const now = new Date().toISOString();
    return {
      id,
      version: 1,
      status: 'ACTIVE',
      instrument,
      direction: 'LONG',
      scenario: 'test',
      timeframe: '1h',
      type: 'INTRADAY',
      origin: 'ACTIVE_SCAN',
      score: 0.8,
      explanation: 'test',
      observationIds: [],
      aiAnalysisIds: [],
      evaluatedAt: now,
      validFrom: now,
      validUntil: null,
      createdAt: now,
      strategyMatchId: null,
    };
  }
});

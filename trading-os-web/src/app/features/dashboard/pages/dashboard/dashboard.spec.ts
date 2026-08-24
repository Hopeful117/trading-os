import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Account } from '../../../../core/models/account.model';
import { DashboardSummary } from '../../../../core/models/dashboard-summary.model';
import { AccountService } from '../../../../core/services/account.service';
import { DashboardService } from '../../../../core/services/dashboard.service';
import { Dashboard } from './dashboard';

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let dashboardService: { findDashboard: ReturnType<typeof vi.fn> };

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

  beforeEach(async () => {
    dashboardService = { findDashboard: vi.fn(() => of(summary())) };
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        { provide: AccountService, useValue: { getAccounts: () => of(accounts) } },
        { provide: DashboardService, useValue: dashboardService },
      ],
    }).compileComponents();
  });

  it('displays main metrics and an account without position', async () => {
    await create();

    expect(text()).toContain('1,020');
    expect(text()).toContain('Aucune position ouverte');
    expect(text()).toContain('LIVE');
  });

  it('displays multiple positions and positive or negative pnl classes', async () => {
    const data = summary();
    data.openPositions = [position('p1', 'BTC/USD', 25), position('p2', 'ETH/USD', -15)];
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
    expect(text()).toContain('Market Data Service indisponible');
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

  it('handles account service failure gracefully', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        {
          provide: AccountService,
          useValue: { getAccounts: () => throwError(() => new Error('service down')) },
        },
        { provide: DashboardService, useValue: dashboardService },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    await nextTask();
    fixture.detectChanges();

    expect(text()).toContain('Aucun compte');
  });

  it('shows empty state when no accounts exist', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        { provide: AccountService, useValue: { getAccounts: () => of([]) } },
        { provide: DashboardService, useValue: dashboardService },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    await nextTask();
    fixture.detectChanges();

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

    const text_ = text();
    expect(text_).toContain('Indisponible');
    expect(text_).toContain('Perte max/jour');
    expect(text_).toContain('3.20');
  });

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

  function position(id: string, symbol: string, pnl: number) {
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
});

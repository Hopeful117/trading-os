import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Account } from '../../../../core/models/account.model';
import { OpenPositionDashboardView } from '../../../../core/models/dashboard-summary.model';
import { AccountService } from '../../../../core/services/account.service';
import { PositionService } from '../../../../core/services/position.service';
import { Positions } from './positions';

describe('Positions', () => {
  let fixture: ComponentFixture<Positions>;
  let positionService: { getPositions: ReturnType<typeof vi.fn> };

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
      peakEquity: 600,
      rulesId: 'rules-2',
      userId: 'user-1',
    },
  ];

  const positions: OpenPositionDashboardView[] = [
    makePosition('p1', 'BTC/USD', 'BUY', 100),
    makePosition('p2', 'ETH/USD', 'SELL', -50),
  ];

  beforeEach(async () => {
    positionService = { getPositions: vi.fn(() => of(positions)) };
    await configureTestingModule({ getAccounts: () => of(accounts) }, positionService);
  });

  it('displays positions when data available', async () => {
    await create();
    expect(text()).toContain('BTC/USD');
    expect(text()).toContain('ETH/USD');
    expect(text()).toContain('2 position(s) ouverte(s)');
  });

  it('displays empty state when no positions', async () => {
    positionService.getPositions.mockReturnValue(of([]));
    await create();
    expect(text()).toContain('Aucune position ouverte.');
  });

  it('displays loading state during initial fetch', async () => {
    positionService.getPositions.mockReturnValue(new Observable(() => {}));
    await create();
    expect(text()).toContain('Chargement des positions…');
  });

  it('displays error state when retrieval fails', async () => {
    positionService.getPositions.mockReturnValue(throwError(() => new Error('unavailable')));
    await create();
    expect(text()).toContain('données des positions sont temporairement indisponibles');
  });

  it('preserves last known state on refresh failure', async () => {
    await create();
    expect(text()).toContain('BTC/USD');

    fixture.componentInstance.selectAccount('account-2');
    await nextTask();
    fixture.detectChanges();

    positionService.getPositions.mockReturnValue(throwError(() => new Error('refresh failed')));
    fixture.componentInstance.selectAccount('account-1');
    await nextTask();
    await nextTask();
    fixture.detectChanges();

    expect(text()).toContain('BTC/USD');
    expect(text()).toContain('données des positions sont temporairement indisponibles');
  });

  it('shows Long/Short labels correctly', async () => {
    await create();
    expect(text()).toContain('Long');
    expect(text()).toContain('Short');
  });

  it('displays protection status', async () => {
    await create();
    expect(text()).toContain('Stop loss manquant');
  });

  it('displays PnL with correct classes', async () => {
    await create();
    expect(fixture.nativeElement.querySelectorAll('.pnl-main.positive')).toHaveLength(1);
    expect(fixture.nativeElement.querySelectorAll('.pnl-main.negative')).toHaveLength(1);
  });

  it('shows empty state when no accounts exist', async () => {
    await reconfigure({ getAccounts: () => of([]) }, positionService);
    expect(text()).toContain('Aucun compte');
    expect(positionService.getPositions).not.toHaveBeenCalled();
  });

  it('shows error state when accounts request fails', async () => {
    await reconfigure({ getAccounts: () => throwError(() => new Error('down')) }, positionService);
    expect(text()).toContain('Impossible de charger les comptes');
    expect(positionService.getPositions).not.toHaveBeenCalled();
  });

  it('polls the selected account', async () => {
    await create();
    fixture.componentInstance.selectAccount('account-1');
    await nextTask();
    fixture.detectChanges();
    expect(positionService.getPositions).toHaveBeenCalledWith('account-1');
  });

  it('pnlClass returns correct classes', () => {
    const comp = TestBed.createComponent(Positions).componentInstance;
    expect(comp.pnlClass(null)).toBe('');
    expect(comp.pnlClass(0)).toBe('');
    expect(comp.pnlClass(10)).toBe('positive');
    expect(comp.pnlClass(-10)).toBe('negative');
  });

  it('protectionStatusLabel returns correct labels', () => {
    const comp = TestBed.createComponent(Positions).componentInstance;
    expect(comp.protectionStatusLabel('PROTECTED')).toBe('Protégé');
    expect(comp.protectionStatusLabel('MISSING_STOP_LOSS')).toBe('Stop loss manquant');
    expect(comp.protectionStatusLabel('UNKNOWN')).toBe('Inconnu');
  });

  it('protectionStatusClass returns correct classes', () => {
    const comp = TestBed.createComponent(Positions).componentInstance;
    expect(comp.protectionStatusClass('PROTECTED')).toBe('protected');
    expect(comp.protectionStatusClass('MISSING_STOP_LOSS')).toBe('missing-sl');
    expect(comp.protectionStatusClass('UNKNOWN')).toBe('unknown');
  });

  it('shows Close Exposure button for open position', async () => {
    await create();
    const button = fixture.nativeElement.querySelector('button.btn-close-exposure');
    expect(button).not.toBeNull();
    expect(button.textContent).toContain('Fermer l\'exposition');
  });

  it('no legacy close button exists', async () => {
    await create();
    expect(fixture.nativeElement.querySelector('[close]')).toBeNull();
  });

  // --- Helpers ---

  async function configureTestingModule(
    accountProvider: { getAccounts: () => Observable<Account[]> },
    posService: { getPositions: ReturnType<typeof vi.fn> },
  ): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [Positions],
      providers: [
        provideRouter([]),
        { provide: AccountService, useValue: accountProvider },
        { provide: PositionService, useValue: posService },
      ],
    }).compileComponents();
  }

  async function reconfigure(
    accountProvider: { getAccounts: () => Observable<Account[]> },
    posService: { getPositions: ReturnType<typeof vi.fn> },
  ): Promise<void> {
    await configureTestingModule(accountProvider, posService);
    fixture = TestBed.createComponent(Positions);
    fixture.detectChanges();
    await nextTask();
    fixture.detectChanges();
  }

  async function create(): Promise<void> {
    fixture = TestBed.createComponent(Positions);
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

  function makePosition(
    id: string,
    symbol: string,
    side: 'BUY' | 'SELL',
    pnl: number,
  ): OpenPositionDashboardView {
    const now = new Date().toISOString();
    return {
      positionId: id,
      accountId: 'account-1',
      marketId: 'market-1',
      symbol,
      side,
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
      protectionStatus: 'MISSING_STOP_LOSS',
      marketTradable: true,
      openedAt: now,
      priceOccurredAt: now,
      calculatedAt: now,
    };
  }
});

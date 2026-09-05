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
  let positionService: {
    getPositions: ReturnType<typeof vi.fn>;
    closePosition: ReturnType<typeof vi.fn>;
    reconcileClose: ReturnType<typeof vi.fn>;
  };

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
    positionService = {
      getPositions: vi.fn(() => of(positions)),
      closePosition: vi.fn(() => of({})),
      reconcileClose: vi.fn(() => of({})),
    };
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
    expect(button.textContent).toContain("Fermer l'exposition");
  });

  it('no legacy close button exists', async () => {
    await create();
    expect(fixture.nativeElement.querySelector('[close]')).toBeNull();
  });

  // --- Close workflow tests ---

  it('getCloseState creates default state', () => {
    const comp = TestBed.createComponent(Positions).componentInstance;
    const state = comp.getCloseState('pos-1');
    expect(state.status).toBeNull();
    expect(state.showConfirmation).toBe(false);
    expect(state.commandId).toBeNull();
  });

  it('getCloseState returns same instance for same positionId', () => {
    const comp = TestBed.createComponent(Positions).componentInstance;
    const s1 = comp.getCloseState('pos-1');
    s1.showConfirmation = true;
    const s2 = comp.getCloseState('pos-1');
    expect(s2.showConfirmation).toBe(true);
  });

  it('showCloseConfirmation toggles confirmation', () => {
    const comp = TestBed.createComponent(Positions).componentInstance;
    comp.showCloseConfirmation({ positionId: 'p1' } as any);
    expect(comp.getCloseState('p1').showConfirmation).toBe(true);
  });

  it('cancelCloseConfirmation hides confirmation', () => {
    const comp = TestBed.createComponent(Positions).componentInstance;
    comp.showCloseConfirmation({ positionId: 'p1' } as any);
    comp.cancelCloseConfirmation('p1');
    expect(comp.getCloseState('p1').showConfirmation).toBe(false);
  });

  it('confirmFullExposureClose calls service and updates state', async () => {
    const mockResponse = {
      commandId: 'cmd-1',
      status: 'CREATED',
      externalOrderId: null,
      failureReason: null,
      resolvedMutationScope: 'scope',
      reconciliationResult: null,
    };
    positionService.closePosition = vi.fn(() => of(mockResponse));
    await configureTestingModule({ getAccounts: () => of(accounts) }, positionService);
    const comp = TestBed.createComponent(Positions).componentInstance;
    comp.confirmFullExposureClose('account-1', { positionId: 'p1' } as any);

    const state = comp.getCloseState('p1');
    expect(state.status).toBe('CREATED');
    expect(state.commandId).toBe('cmd-1');
    expect(state.showConfirmation).toBe(false);
    expect(positionService.closePosition).toHaveBeenCalled();
  });

  it('confirmFullExposureClose handles error', async () => {
    positionService.closePosition = vi.fn(() =>
      throwError(() => ({ error: { message: 'Broker error' } })),
    );
    await configureTestingModule({ getAccounts: () => of(accounts) }, positionService);
    const comp = TestBed.createComponent(Positions).componentInstance;
    comp.confirmFullExposureClose('account-1', { positionId: 'p1' } as any);

    const state = comp.getCloseState('p1');
    expect(state.status).toBe('REJECTED');
    expect(state.failureReason).toBe('Broker error');
  });

  it('reconcile calls service when commandId exists', async () => {
    const mockResponse = { status: 'CLOSED', reconciliationResult: 'EXPOSURE_CONFIRMED_ABSENT' };
    positionService.reconcileClose = vi.fn(() => of(mockResponse));
    await configureTestingModule({ getAccounts: () => of(accounts) }, positionService);
    const comp = TestBed.createComponent(Positions).componentInstance;
    const state = comp.getCloseState('p1');
    state.commandId = 'cmd-1';
    comp.reconcile('account-1', 'p1');

    expect(state.status).toBe('CLOSED');
    expect(state.reconciliationResult).toBe('EXPOSURE_CONFIRMED_ABSENT');
    expect(positionService.reconcileClose).toHaveBeenCalledWith('account-1', 'cmd-1');
  });

  it('reconcile does nothing when no commandId', async () => {
    await configureTestingModule({ getAccounts: () => of(accounts) }, positionService);
    const comp = TestBed.createComponent(Positions).componentInstance;
    comp.reconcile('account-1', 'p1');
    expect(positionService.reconcileClose).not.toHaveBeenCalled();
  });

  it('reconcile handles error', async () => {
    positionService.reconcileClose = vi.fn(() => throwError(() => new Error('fail')));
    await configureTestingModule({ getAccounts: () => of(accounts) }, positionService);
    const comp = TestBed.createComponent(Positions).componentInstance;
    const state = comp.getCloseState('p1');
    state.commandId = 'cmd-1';
    state.status = 'ACKNOWLEDGED';
    comp.reconcile('account-1', 'p1');
    expect(state.status).toBe('ACKNOWLEDGED');
  });

  it('closeStatusLabel returns correct labels', () => {
    const comp = TestBed.createComponent(Positions).componentInstance;
    expect(comp.closeStatusLabel(null)).toBe('');
    expect(comp.closeStatusLabel('CREATED')).toBe('Créée');
    expect(comp.closeStatusLabel('SUBMITTED')).toBe('Soumise');
    expect(comp.closeStatusLabel('ACKNOWLEDGED')).toBe('Reconnue');
    expect(comp.closeStatusLabel('REJECTED')).toBe('Rejetée');
    expect(comp.closeStatusLabel('UNKNOWN')).toBe('Incertain');
    expect(comp.closeStatusLabel('CLOSED')).toBe('Fermée');
    expect(comp.closeStatusLabel('NOT_SUBMITTED')).toBe('Non soumise');
  });

  it('closeStatusClass returns correct classes', () => {
    const comp = TestBed.createComponent(Positions).componentInstance;
    expect(comp.closeStatusClass(null)).toBe('');
    expect(comp.closeStatusClass('CREATED')).toBe('pending');
    expect(comp.closeStatusClass('SUBMITTED')).toBe('pending');
    expect(comp.closeStatusClass('ACKNOWLEDGED')).toBe('acknowledged');
    expect(comp.closeStatusClass('REJECTED')).toBe('rejected');
    expect(comp.closeStatusClass('UNKNOWN')).toBe('unknown');
    expect(comp.closeStatusClass('CLOSED')).toBe('closed');
    expect(comp.closeStatusClass('NOT_SUBMITTED')).toBe('not-submitted');
  });

  it('isActiveStatus returns correct booleans', () => {
    const comp = TestBed.createComponent(Positions).componentInstance;
    expect(comp.isActiveStatus(null)).toBe(false);
    expect(comp.isActiveStatus('CREATED')).toBe(true);
    expect(comp.isActiveStatus('SUBMITTED')).toBe(true);
    expect(comp.isActiveStatus('ACKNOWLEDGED')).toBe(true);
    expect(comp.isActiveStatus('UNKNOWN')).toBe(true);
    expect(comp.isActiveStatus('CLOSED')).toBe(false);
    expect(comp.isActiveStatus('REJECTED')).toBe(false);
  });

  it('isReconcilable returns correct booleans', () => {
    const comp = TestBed.createComponent(Positions).componentInstance;
    expect(comp.isReconcilable(null)).toBe(false);
    expect(comp.isReconcilable('CREATED')).toBe(false);
    expect(comp.isReconcilable('ACKNOWLEDGED')).toBe(true);
    expect(comp.isReconcilable('UNKNOWN')).toBe(true);
    expect(comp.isReconcilable('CLOSED')).toBe(false);
  });

  it('reconciliationLabel returns correct labels', () => {
    const comp = TestBed.createComponent(Positions).componentInstance;
    expect(comp.reconciliationLabel(null)).toBe('');
    expect(comp.reconciliationLabel('EXPOSURE_CONFIRMED_ABSENT')).toBe(
      'Exposition confirmée absente',
    );
    expect(comp.reconciliationLabel('COMMAND_CONFIRMED_NOT_EXECUTED')).toBe(
      'Commande non exécutée',
    );
    expect(comp.reconciliationLabel('RECONCILIATION_INCONCLUSIVE')).toBe(
      'Réconciliation inconclusive',
    );
  });

  it('closeStatusLabel returns correct labels for all statuses', () => {
    const comp = TestBed.createComponent(Positions).componentInstance;
    expect(comp.closeStatusLabel(null)).toBe('');
    expect(comp.closeStatusLabel('CREATED')).toBe('Créée');
    expect(comp.closeStatusLabel('SUBMITTED')).toBe('Soumise');
    expect(comp.closeStatusLabel('ACKNOWLEDGED')).toBe('Reconnue');
    expect(comp.closeStatusLabel('REJECTED')).toBe('Rejetée');
    expect(comp.closeStatusLabel('UNKNOWN')).toBe('Incertain');
    expect(comp.closeStatusLabel('CLOSED')).toBe('Fermée');
    expect(comp.closeStatusLabel('NOT_SUBMITTED')).toBe('Non soumise');
  });

  // --- Helpers ---

  async function configureTestingModule(
    accountProvider: { getAccounts: () => Observable<Account[]> },
    posService: {
      getPositions: ReturnType<typeof vi.fn>;
      closePosition: ReturnType<typeof vi.fn>;
      reconcileClose: ReturnType<typeof vi.fn>;
    },
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
    posService: {
      getPositions: ReturnType<typeof vi.fn>;
      closePosition: ReturnType<typeof vi.fn>;
      reconcileClose: ReturnType<typeof vi.fn>;
    },
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

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Accounts } from './accounts';
import { AccountService } from '../../../../core/services/account.service';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import { BrokerAccountService } from '../../../../core/services/broker-account.service';
import { Account } from '../../../../core/models/account.model';
import { BrokerAccount } from '../../../../core/models/broker-account.model';

describe('Accounts', () => {
  let component: Accounts;
  let fixture: ComponentFixture<Accounts>;
  let accountService: {
    getAccounts: ReturnType<typeof vi.fn>;
    synchronize: ReturnType<typeof vi.fn>;
  };
  let brokerAccountService: {
    list: ReturnType<typeof vi.fn>;
    eligibleRiskProfiles: ReturnType<typeof vi.fn>;
    createPaper: ReturnType<typeof vi.fn>;
    createAndConnect: ReturnType<typeof vi.fn>;
    connectExisting: ReturnType<typeof vi.fn>;
    createCTrader: ReturnType<typeof vi.fn>;
    startCTraderAuthorization: ReturnType<typeof vi.fn>;
     getCTraderAuthorization: ReturnType<typeof vi.fn>;
     bindCTraderAccount: ReturnType<typeof vi.fn>;
     startCTraderMarketDataAuthorization: ReturnType<typeof vi.fn>;
     getCTraderMarketDataAuthorization: ReturnType<typeof vi.fn>;
    provisionTradingAccount: ReturnType<typeof vi.fn>;
    disconnect: ReturnType<typeof vi.fn>;
    revoke: ReturnType<typeof vi.fn>;
  };
  let accounts: BehaviorSubject<Account[]>;

  beforeEach(async () => {
    accounts = new BehaviorSubject<Account[]>([]);
    accountService = {
      getAccounts: vi.fn(() => accounts),
      synchronize: vi.fn(() => of('Accounts synchronized successfully')),
    };
    brokerAccountService = {
      list: vi.fn(() => of([])),
      eligibleRiskProfiles: vi.fn(() => of([])),
      createPaper: vi.fn(() => of({ executionMode: 'PAPER' })),
      createAndConnect: vi.fn(() => of({ outcome: 'VALID', safeMessage: 'Connexion réussie.' })),
      connectExisting: vi.fn(() => of({ outcome: 'VALID', safeMessage: 'Connexion réussie.' })),
      createCTrader: vi.fn(() => of({ id: 'ctrader-account-1', provider: 'CTRADER' })),
      startCTraderAuthorization: vi.fn(() => of({
        attemptId: 'attempt-1',
        authorizationUrl: 'https://id.ctrader.com/authorize',
        status: 'ACCOUNT_SELECTION_REQUIRED',
        discoveredAccountIds: ['49289512'],
        selectedExternalAccountId: null,
        expiresAt: '2026-10-27T10:00:00Z',
        safeMessage: 'Select an account',
      })),
      getCTraderAuthorization: vi.fn(),
      bindCTraderAccount: vi.fn(() => of({ status: 'BOUND' })),
      startCTraderMarketDataAuthorization: vi.fn(() => of({
        attemptId: 'market-data-attempt-1',
        authorizationUrl: 'https://id.ctrader.com/authorize',
      })),
      getCTraderMarketDataAuthorization: vi.fn(),
      provisionTradingAccount: vi.fn(() => of({ accountId: 'trading-account-1' })),
      disconnect: vi.fn(() => of(brokerAccount('account-1', 'DISCONNECTED'))),
      revoke: vi.fn(() => of(void 0)),
    };
    await TestBed.configureTestingModule({
      imports: [Accounts],
      providers: [
        { provide: AccountService, useValue: accountService },
        { provide: BrokerAccountService, useValue: brokerAccountService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Accounts);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load accounts on init', () => {
    expect(accountService.getAccounts).toHaveBeenCalled();
  });

  it('should load broker accounts on init', () => {
    expect(brokerAccountService.list).toHaveBeenCalled();
  });

  it('loads eligible risk profiles on init', () => {
    expect(brokerAccountService.eligibleRiskProfiles).toHaveBeenCalled();
  });

  it('shows an actionable empty state when no PAPER profile is available', () => {
    component.brokerForm.controls.executionMode.setValue('PAPER');
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="risk-profile-empty"]'),
    ).not.toBeNull();
    expect(
      (
        fixture.nativeElement.querySelector(
          '[data-testid="create-account-button"]',
        ) as HTMLButtonElement
      ).disabled,
    ).toBe(true);
  });

  it('shows an actionable catalog error state', () => {
    component.riskProfileState = of({ profiles: [], loading: false, error: true });
    component.brokerForm.controls.executionMode.setValue('PAPER');
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="risk-profile-error"]'),
    ).not.toBeNull();
  });

  it('shows visible success feedback in the DOM after connecting a broker', async () => {
    component.brokerForm.setValue({
      provider: 'KRAKEN',
      executionMode: 'LIVE',
      displayName: 'My Kraken',
      initialCapital: null,
      apiKey: 'a'.repeat(8),
      apiSecret: 'b'.repeat(16),
      passphrase: '',
    });

    component.connectBroker();
    await fixture.whenStable();
    fixture.detectChanges();

    const feedback = fixture.nativeElement.querySelector(
      '[data-testid="connection-feedback"]',
    ) as HTMLElement | null;
    expect(feedback).not.toBeNull();
    expect(feedback!.classList.contains('success')).toBe(true);
    expect(feedback!.textContent).toContain('Connexion Kraken réussie');
    expect(component.connecting()).toBe(false);
    expect(component.brokerForm.getRawValue()).toEqual({
      provider: 'KRAKEN',
      executionMode: 'LIVE',
      displayName: '',
      initialCapital: null,
      apiKey: '',
      apiSecret: '',
      passphrase: '',
    });
    expect(brokerAccountService.list).toHaveBeenCalledTimes(2);
  });

  it('starts the guided cTrader authorization without requesting API credentials', async () => {
    vi.spyOn(window, 'open').mockReturnValue(null);
    component.brokerForm.patchValue({
      provider: 'CTRADER',
      executionMode: 'LIVE',
      displayName: 'FTMO cTrader',
    });

    component.connectBroker();
    await fixture.whenStable();

    expect(brokerAccountService.createCTrader).toHaveBeenCalledWith('FTMO cTrader');
    expect(brokerAccountService.startCTraderAuthorization).toHaveBeenCalledWith('ctrader-account-1');
    expect(window.open).toHaveBeenCalledWith(
      'https://id.ctrader.com/authorize',
      'ctrader-oauth',
      'popup,width=720,height=800',
    );
  });

  it('shows discovered cTrader accounts for explicit selection', () => {
    component.cTraderFlowAccountId.set('ctrader-account-1');
    component.cTraderAuthorization.set({
      attemptId: 'attempt-1',
      authorizationUrl: null,
      status: 'ACCOUNT_SELECTION_REQUIRED',
      discoveredAccountIds: ['49289512'],
      selectedExternalAccountId: null,
      expiresAt: '2026-10-27T10:00:00Z',
      safeMessage: 'Select an account',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="ctrader-account-option"]'))
      .not.toBeNull();
  });

  it('shows visible error feedback and clears sensitive fields when connection fails', async () => {
    brokerAccountService.createAndConnect.mockReturnValue(throwError(() => new Error('fail')));

    component.brokerForm.setValue({
      provider: 'KRAKEN',
      executionMode: 'LIVE',
      displayName: 'Test',
      initialCapital: null,
      apiKey: 'a'.repeat(8),
      apiSecret: 'b'.repeat(16),
      passphrase: 'secret',
    });

    component.connectBroker();
    await fixture.whenStable();
    fixture.detectChanges();

    const feedback = fixture.nativeElement.querySelector(
      '[data-testid="connection-feedback"]',
    ) as HTMLElement | null;
    expect(feedback).not.toBeNull();
    expect(feedback!.classList.contains('error')).toBe(true);
    expect(component.brokerForm.controls.apiKey.value).toBe('');
    expect(component.brokerForm.controls.apiSecret.value).toBe('');
    expect(component.brokerForm.controls.passphrase.value).toBe('');
  });

  it('treats non-VALID outcome as visible error even when HTTP returns 200', async () => {
    brokerAccountService.createAndConnect.mockReturnValue(
      of({
        outcome: 'INVALID_CREDENTIALS',
        connectionStatus: 'INVALID_CREDENTIALS',
        missingPermissions: [],
        validatedAt: new Date().toISOString(),
        safeMessage: 'Broker rejected the credentials.',
      }),
    );

    component.brokerForm.setValue({
      provider: 'KRAKEN',
      executionMode: 'LIVE',
      displayName: 'Test',
      initialCapital: null,
      apiKey: 'a'.repeat(8),
      apiSecret: 'b'.repeat(16),
      passphrase: '',
    });

    component.connectBroker();
    await fixture.whenStable();
    fixture.detectChanges();

    const feedback = fixture.nativeElement.querySelector(
      '[data-testid="connection-feedback"]',
    ) as HTMLElement | null;
    expect(feedback).not.toBeNull();
    expect(feedback!.classList.contains('error')).toBe(true);
    expect(feedback!.textContent).toContain('Broker rejected the credentials.');
    expect(feedback!.textContent).not.toContain('réussie');
    expect(component.brokerForm.controls.apiKey.value).toBe('');
  });

  it('does not submit when form is invalid', () => {
    component.brokerForm.reset();
    component.connectBroker();

    expect(brokerAccountService.createAndConnect).not.toHaveBeenCalled();
    expect(component.connecting()).toBe(false);
  });

  it('prevents double submit while a connection request is in flight', () => {
    brokerAccountService.createAndConnect.mockReturnValue(
      new BehaviorSubject({ outcome: 'VALID', safeMessage: '' }),
    );

    component.brokerForm.setValue({
      provider: 'KRAKEN',
      executionMode: 'LIVE',
      displayName: 'Test',
      initialCapital: null,
      apiKey: 'a'.repeat(8),
      apiSecret: 'b'.repeat(16),
      passphrase: '',
    });

    component.connectBroker();
    component.connectBroker();

    expect(brokerAccountService.createAndConnect).toHaveBeenCalledTimes(1);
  });

  it('recovers an existing LIVE account through the existing-account endpoint', async () => {
    const account = brokerAccount('account-1', 'DISCONNECTED');

    component.startRecovery(account);
    component.brokerForm.controls.apiKey.setValue('a'.repeat(8));
    component.brokerForm.controls.apiSecret.setValue('b'.repeat(16));
    component.brokerForm.controls.passphrase.setValue('passphrase');

    component.connectBroker();
    await fixture.whenStable();

    expect(brokerAccountService.connectExisting).toHaveBeenCalledWith('account-1', {
      apiKey: 'a'.repeat(8),
      apiSecret: 'b'.repeat(16),
      passphrase: 'passphrase',
    });
    expect(brokerAccountService.createAndConnect).not.toHaveBeenCalled();
    expect(component.recoveryAccount()).toBeNull();
    expect(component.brokerForm.controls.apiSecret.value).toBe('');
    expect(component.connectionFeedback()?.kind).toBe('success');
  });

  it('cancels existing-account recovery without submitting credentials', () => {
    const account = brokerAccount('account-1', 'INVALID_CREDENTIALS');

    component.startRecovery(account);
    component.brokerForm.controls.apiKey.setValue('a'.repeat(8));
    component.cancelRecovery();

    expect(brokerAccountService.connectExisting).not.toHaveBeenCalled();
    expect(component.recoveryAccount()).toBeNull();
    expect(component.brokerForm.controls.apiKey.value).toBe('');
  });

  it('clears sensitive fields when existing-account recovery fails', async () => {
    brokerAccountService.connectExisting.mockReturnValue(throwError(() => new Error('fail')));
    component.startRecovery(brokerAccount('account-1', 'DISCONNECTED'));
    component.brokerForm.controls.apiKey.setValue('a'.repeat(8));
    component.brokerForm.controls.apiSecret.setValue('b'.repeat(16));
    component.brokerForm.controls.passphrase.setValue('secret');

    component.connectBroker();
    await fixture.whenStable();

    expect(component.brokerForm.controls.apiKey.value).toBe('');
    expect(component.brokerForm.controls.apiSecret.value).toBe('');
    expect(component.brokerForm.controls.passphrase.value).toBe('');
    expect(component.connectionFeedback()?.kind).toBe('error');
  });

  it('disconnects a connected account only after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const account = brokerAccount('account-1', 'CONNECTED');

    component.disconnectBrokerAccount(account);
    await fixture.whenStable();

    expect(brokerAccountService.disconnect).toHaveBeenCalledWith('account-1');
    expect(component.disconnectingAccountId()).toBeNull();
    expect(component.disconnectFeedback()).toEqual({
      kind: 'success',
      message: 'Le compte broker « Test account » a été déconnecté.',
    });
  });

  it('does not expose recovery or disconnect actions for revoked accounts', () => {
    const account = brokerAccount('account-1', 'REVOKED');

    expect(component.canRecover(account)).toBe(false);
    component.startRecovery(account);
    component.disconnectBrokerAccount(account);

    expect(brokerAccountService.connectExisting).not.toHaveBeenCalled();
    expect(brokerAccountService.disconnect).not.toHaveBeenCalled();
  });

  it('does not replace an active recovery form when another account is selected', () => {
    const first = brokerAccount('account-1', 'DISCONNECTED');
    const second = brokerAccount('account-2', 'INVALID_CREDENTIALS');

    component.startRecovery(first);
    component.brokerForm.controls.apiKey.setValue('a'.repeat(8));
    component.startRecovery(second);

    expect(component.recoveryAccount()?.id).toBe('account-1');
    expect(component.brokerForm.controls.apiKey.value).toBe('a'.repeat(8));
  });

  it('blocks conflicting lifecycle operations while one operation is pending', () => {
    const pending = new Subject<void>();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    brokerAccountService.disconnect.mockReturnValue(pending);
    const account = brokerAccount('account-1', 'CONNECTED');

    component.disconnectBrokerAccount(account);
    component.revokeBrokerAccount(account);
    component.startRecovery(account);

    expect(brokerAccountService.disconnect).toHaveBeenCalledTimes(1);
    expect(brokerAccountService.revoke).not.toHaveBeenCalled();
    expect(component.recoveryAccount()).toBeNull();
    pending.next();
    pending.complete();
  });

  it('shows visible success feedback and reloads accounts on sync success', async () => {
    component.sync();
    await fixture.whenStable();
    fixture.detectChanges();

    const feedback = fixture.nativeElement.querySelector(
      '[data-testid="sync-feedback"]',
    ) as HTMLElement | null;
    expect(feedback).not.toBeNull();
    expect(feedback!.classList.contains('success')).toBe(true);
    expect(accountService.synchronize).toHaveBeenCalled();
    expect(accountService.getAccounts).toHaveBeenCalledTimes(2);
  });

  it('shows visible error feedback on sync failure', async () => {
    accountService.synchronize.mockReturnValue(throwError(() => new Error('fail')));

    component.sync();
    await fixture.whenStable();
    fixture.detectChanges();

    const feedback = fixture.nativeElement.querySelector(
      '[data-testid="sync-feedback"]',
    ) as HTMLElement | null;
    expect(feedback).not.toBeNull();
    expect(feedback!.classList.contains('error')).toBe(true);
    expect(feedback!.textContent).toContain('Erreur lors de la synchronisation.');
  });

  it('does not revoke a broker account when confirmation is cancelled', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    component.revokeBrokerAccount(brokerAccount('account-1'));

    expect(brokerAccountService.revoke).not.toHaveBeenCalled();
    expect(component.revokingAccountId()).toBeNull();
  });

  it('revokes a confirmed broker account and reloads the list', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    component.revokeBrokerAccount(brokerAccount('account-1'));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(brokerAccountService.revoke).toHaveBeenCalledWith('account-1');
    expect(brokerAccountService.list).toHaveBeenCalledTimes(2);
    expect(component.revokingAccountId()).toBeNull();
    expect(component.revokeFeedback()).toEqual({
      kind: 'success',
      message: 'Le compte broker « Test account » a été révoqué.',
    });
  });

  it('shows safe feedback when broker-account revocation fails', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    brokerAccountService.revoke.mockReturnValue(throwError(() => new Error('callback failed')));

    component.revokeBrokerAccount(brokerAccount('account-1'));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.revokingAccountId()).toBeNull();
    expect(component.revokeFeedback()).toEqual({
      kind: 'error',
      message: 'La révocation n’a pas pu être confirmée. Vérifiez l’état du compte.',
    });
    expect(component.revokeFeedback()?.message).not.toContain('callback failed');
  });

  it('prevents concurrent broker-account revocations', () => {
    const pending = new Subject<void>();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    brokerAccountService.revoke.mockReturnValue(pending);
    const account = brokerAccount('account-1');

    component.revokeBrokerAccount(account);
    component.revokeBrokerAccount(account);

    expect(brokerAccountService.revoke).toHaveBeenCalledTimes(1);
    pending.next();
    pending.complete();
  });

  it('does not render a revoke action for an already revoked account', async () => {
    brokerAccountService.list.mockReturnValue(of([brokerAccount('account-1', 'REVOKED')]));
    fixture.destroy();
    fixture = TestBed.createComponent(Accounts);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="revoke-broker-account-button"]'),
    ).toBeNull();
  });

  it('disables the sync button and shows progress label while syncing', async () => {
    accountService.synchronize.mockReturnValue(new Subject<string>());

    component.sync();
    await fixture.whenStable();
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector(
      '[data-testid="synchronize-button"]',
    ) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain('Synchronisation…');
  });

  it('does not claim that no broker account exists when a synchronized account is present', async () => {
    accounts.next([
      {
        accountId: 'account-1',
      } as Account,
    ]);

    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).not.toContain('Aucun compte broker connecté.');
    expect(fixture.nativeElement.textContent).toContain(
      'Compte broker synchronisé via la configuration existante.',
    );
  });

  it('creates a PAPER account with initial capital without requesting credentials', async () => {
    component.riskProfileSelection.setValue('profile-1::1.0.0');
    component.brokerForm.setValue({
      provider: 'KRAKEN',
      executionMode: 'PAPER',
      displayName: 'Paper account',
      initialCapital: 10000,
      apiKey: '',
      apiSecret: '',
      passphrase: '',
    });

    component.connectBroker();
    await fixture.whenStable();

    expect(brokerAccountService.createPaper).toHaveBeenCalledWith({
      provider: 'KRAKEN',
      displayName: 'Paper account',
      initialCapital: 10000,
      riskProfile: { profileId: 'profile-1', semanticVersion: '1.0.0' },
    });
    expect(brokerAccountService.createAndConnect).not.toHaveBeenCalled();
    expect(component.connectionFeedback()?.kind).toBe('success');
  });

  function brokerAccount(
    id: string,
    connectionStatus: BrokerAccount['connectionStatus'] = 'CONNECTED',
  ): BrokerAccount {
    return {
      id,
      provider: 'KRAKEN',
      executionMode: 'LIVE',
      displayName: 'Test account',
      externalAccountId: null,
      connectionStatus,
      lastValidatedAt: null,
      lastSynchronizedAt: null,
      createdAt: '2026-07-29T10:00:00Z',
      updatedAt: '2026-07-29T10:00:00Z',
    };
  }

  it('requires explicit risk profile selection for PAPER accounts', () => {
    component.brokerForm.setValue({
      provider: 'KRAKEN',
      executionMode: 'PAPER',
      displayName: 'Paper account',
      initialCapital: 10000,
      apiKey: '',
      apiSecret: '',
      passphrase: '',
    });

    component.connectBroker();

    expect(brokerAccountService.createPaper).not.toHaveBeenCalled();
  });

  it('requires positive initial capital for a PAPER account', () => {
    component.brokerForm.setValue({
      provider: 'KRAKEN',
      executionMode: 'PAPER',
      displayName: 'Paper account',
      initialCapital: null,
      apiKey: '',
      apiSecret: '',
      passphrase: '',
    });

    component.connectBroker();

    expect(brokerAccountService.createPaper).not.toHaveBeenCalled();
  });
});

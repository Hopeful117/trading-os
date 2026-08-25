import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Accounts } from './accounts';
import { AccountService } from '../../../../core/services/account.service';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import { BrokerAccountService } from '../../../../core/services/broker-account.service';
import { Account } from '../../../../core/models/account.model';

describe('Accounts', () => {
  let component: Accounts;
  let fixture: ComponentFixture<Accounts>;
  let accountService: {
    getAccounts: ReturnType<typeof vi.fn>;
    synchronize: ReturnType<typeof vi.fn>;
  };
  let brokerAccountService: {
    list: ReturnType<typeof vi.fn>;
    createAndConnect: ReturnType<typeof vi.fn>;
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
      createAndConnect: vi.fn(() => of({ outcome: 'VALID', safeMessage: 'Connexion réussie.' })),
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

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load accounts on init', () => {
    expect(accountService.getAccounts).toHaveBeenCalled();
  });

  it('should load broker accounts on init', () => {
    expect(brokerAccountService.list).toHaveBeenCalled();
  });

  it('shows visible success feedback in the DOM after connecting a broker', async () => {
    component.brokerForm.setValue({
      provider: 'KRAKEN',
      displayName: 'My Kraken',
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
      displayName: '',
      apiKey: '',
      apiSecret: '',
      passphrase: '',
    });
    expect(brokerAccountService.list).toHaveBeenCalledTimes(2);
  });

  it('shows visible error feedback and clears sensitive fields when connection fails', async () => {
    brokerAccountService.createAndConnect.mockReturnValue(throwError(() => new Error('fail')));

    component.brokerForm.setValue({
      provider: 'KRAKEN',
      displayName: 'Test',
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
      displayName: 'Test',
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
      displayName: 'Test',
      apiKey: 'a'.repeat(8),
      apiSecret: 'b'.repeat(16),
      passphrase: '',
    });

    component.connectBroker();
    component.connectBroker();

    expect(brokerAccountService.createAndConnect).toHaveBeenCalledTimes(1);
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
});

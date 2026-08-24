import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Accounts } from './accounts';
import { AccountService } from '../../../../core/services/account.service';
import { BehaviorSubject, of, throwError } from 'rxjs';
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
      synchronize: vi.fn(() => of('ok')),
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

  it('should reset form and reload broker accounts on connectBroker success', () => {
    component.brokerForm.setValue({
      provider: 'KRAKEN',
      displayName: 'My Kraken',
      apiKey: 'a'.repeat(8),
      apiSecret: 'b'.repeat(16),
      passphrase: '',
    });

    component.connectBroker();

    expect(component.connecting).toBeFalsy();
    expect(component.connectionMessage).toBe('Connexion broker réussie · Connexion réussie.');
    expect(component.brokerForm.getRawValue()).toEqual({
      provider: 'KRAKEN',
      displayName: '',
      apiKey: '',
      apiSecret: '',
      passphrase: '',
    });
    expect(brokerAccountService.list).toHaveBeenCalled();
  });

  it('should show message and clear sensitive fields on connectBroker error', () => {
    brokerAccountService.createAndConnect.mockReturnValue(throwError(() => new Error('fail')));

    component.brokerForm.setValue({
      provider: 'KRAKEN',
      displayName: 'Test',
      apiKey: 'a'.repeat(8),
      apiSecret: 'b'.repeat(16),
      passphrase: 'secret',
    });

    component.connectBroker();

    expect(component.connectionMessage).toBe('La connexion broker n\u2019a pas pu être validée.');
    expect(component.brokerForm.controls.apiKey.value).toBe('');
    expect(component.brokerForm.controls.apiSecret.value).toBe('');
    expect(component.brokerForm.controls.passphrase.value).toBe('');
  });

  it('should reload accounts on sync success', () => {
    component.sync();

    expect(accountService.synchronize).toHaveBeenCalled();
    expect(accountService.getAccounts).toHaveBeenCalled();
  });

  it('should show error message on sync failure', () => {
    accountService.synchronize.mockReturnValue(throwError(() => new Error('fail')));

    component.sync();

    expect(component.errorMessage).toBe('Erreur lors de la synchronisation.');
  });

  it('should not submit when form is invalid', () => {
    component.brokerForm.reset();
    component.connectBroker();

    expect(brokerAccountService.createAndConnect).not.toHaveBeenCalled();
    expect(component.connecting).toBeFalsy();
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

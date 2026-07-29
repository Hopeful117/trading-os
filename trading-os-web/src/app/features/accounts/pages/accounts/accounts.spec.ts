import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Accounts } from './accounts';
import { AccountService } from '../../../../core/services/account.service';
import { BehaviorSubject, of } from 'rxjs';
import { BrokerAccountService } from '../../../../core/services/broker-account.service';
import { Account } from '../../../../core/models/account.model';

describe('Accounts', () => {
  let component: Accounts;
  let fixture: ComponentFixture<Accounts>;
  let accountService: { getAccounts: ReturnType<typeof vi.fn>; synchronize: ReturnType<typeof vi.fn> };
  let brokerAccountService: { list: ReturnType<typeof vi.fn> };
  let accounts: BehaviorSubject<Account[]>;

  beforeEach(async () => {
    accounts = new BehaviorSubject<Account[]>([]);
    accountService = {
      getAccounts: vi.fn(() => accounts),
      synchronize: vi.fn(() => of('ok')),
    };
    brokerAccountService = { list: vi.fn(() => of([])) };
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

  it('does not claim that no broker account exists when a synchronized account is present', async () => {
    accounts.next([{
      accountId: 'account-1',
    } as Account]);

    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).not.toContain('Aucun compte broker connecté.');
    expect(fixture.nativeElement.textContent).toContain(
      'Compte broker synchronisé via la configuration existante.',
    );
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AccountCard } from './account-card';

describe('AccountCard', () => {
  let component: AccountCard;
  let fixture: ComponentFixture<AccountCard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccountCard],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountCard);
    component = fixture.componentInstance;
    component.account = {
      accountId: 'account-1',
      name: 'Test account',
      baseCurrency: 'EUR',
      balances: { balances: {} },
      equity: 0,
      peakEquity: 0,
      rulesId: 'rules-1',
      userId: 'user-1',
    };
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

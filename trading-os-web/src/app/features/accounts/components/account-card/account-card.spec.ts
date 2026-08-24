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

  it('should return entries when balances exist', () => {
    component.account = {
      ...component.account,
      balances: { balances: { BTC: 1.5, EUR: 2500 } },
    };

    const result = component.balances;

    expect(result.length).toBe(2);
    expect(result).toEqual([
      { asset: 'BTC', amount: 1.5 },
      { asset: 'EUR', amount: 2500 },
    ]);
  });

  it('should return empty when balances object has no entries', () => {
    component.account = {
      ...component.account,
      balances: { balances: {} },
    };

    expect(component.balances).toEqual([]);
  });

  it('should return empty when account is absent', () => {
    component.account = undefined as unknown as typeof component.account;

    expect(component.balances).toEqual([]);
  });

  it('should convert string amounts to numbers', () => {
    component.account = {
      ...component.account,
      balances: { balances: { ETH: '2.5' as unknown as number } },
    };

    const result = component.balances;

    expect(result[0].amount).toBe(2.5);
    expect(typeof result[0].amount).toBe('number');
  });

  it('should correctly map asset names', () => {
    component.account = {
      ...component.account,
      balances: { balances: { SOL: 10, USD: 500 } },
    };

    const assets = component.balances.map((b) => b.asset);

    expect(assets).toEqual(['SOL', 'USD']);
  });
});

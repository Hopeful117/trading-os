// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { Account } from '../../core/models/account.model';
import { DecisionContextResponse } from '../../core/models/decision-context.model';
import { AccountService } from '../../core/services/account.service';
import { DecisionContextService } from '../../core/services/decision-context.service';
import { DecisionWorkspace } from './decision-workspace';

describe('DecisionWorkspace', () => {
  let fixture: ComponentFixture<DecisionWorkspace>;
  let component: DecisionWorkspace;
  let accountServiceMock: { getAccounts: ReturnType<typeof vi.fn> };
  let contextServiceMock: { resolve: ReturnType<typeof vi.fn> };
  let routerMock: { navigate: ReturnType<typeof vi.fn> };

  const account: Account = {
    accountId: 'account-1',
    brokerAccountId: 'broker-1',
    name: 'Paper account',
    baseCurrency: 'USD',
    balances: {} as Account['balances'],
    equity: 1000,
    peakEquity: 1000,
    rulesId: 'rules-1',
    userId: 'user-1',
    riskProfileId: 'risk-1',
    riskProfileSemanticVersion: '1.0.0',
    tradePlanningProfileId: 'planning-1',
    tradePlanningProfileVersion: 1,
  };

  const context: DecisionContextResponse = {
    account: {
      accountId: account.accountId,
      brokerAccountId: account.brokerAccountId ?? null,
      name: account.name,
      baseCurrency: account.baseCurrency,
      equity: account.equity,
      peakEquity: account.peakEquity,
      rulesId: account.rulesId,
      userId: account.userId,
      riskProfileId: account.riskProfileId ?? null,
      riskProfileSemanticVersion: account.riskProfileSemanticVersion ?? null,
      tradePlanningProfileId: account.tradePlanningProfileId ?? null,
      tradePlanningProfileVersion: account.tradePlanningProfileVersion ?? null,
    },
    markets: [
      { marketId: 'market-1', symbol: 'BTC/USD', provider: 'KRAKEN', eligible: true, reasons: [] },
      {
        marketId: 'market-2',
        symbol: 'ETH/USD',
        provider: 'KRAKEN',
        eligible: false,
        reasons: ['MARKET_NOT_TRADABLE'],
      },
    ],
    eligibleMarketIds: ['market-1'],
    resolvedAt: '2026-09-20T10:00:00Z',
  };

  beforeEach(async () => {
    accountServiceMock = { getAccounts: vi.fn().mockReturnValue(of([account])) };
    contextServiceMock = { resolve: vi.fn().mockReturnValue(of(context)) };
    routerMock = { navigate: vi.fn().mockResolvedValue(true) };

    await TestBed.configureTestingModule({
      imports: [DecisionWorkspace],
      providers: [
        { provide: AccountService, useValue: accountServiceMock },
        { provide: DecisionContextService, useValue: contextServiceMock },
        { provide: Router, useValue: routerMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DecisionWorkspace);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('does not resolve markets before account selection', () => {
    expect(contextServiceMock.resolve).not.toHaveBeenCalled();
    expect(
      fixture.nativeElement.querySelector('[data-testid="select-account-state"]'),
    ).toBeTruthy();
  });

  it('resolves account-scoped markets after account selection', async () => {
    component.selectAccount(account.accountId);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(contextServiceMock.resolve).toHaveBeenCalledWith(account.accountId);
    expect(fixture.nativeElement.querySelector('[data-testid="eligible-markets"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="excluded-markets"]')).toBeTruthy();
  });

  it('clears the selected market when the account changes', () => {
    component.selectedMarketId = 'market-1';

    component.selectAccount('account-2');

    expect(component.selectedMarketId).toBeNull();
  });

  it('does not select an excluded market', () => {
    component.selectMarket('market-2', context);

    expect(component.selectedMarketId).toBeNull();
  });

  it('renders a context error without fabricating markets', async () => {
    contextServiceMock.resolve.mockReturnValueOnce(throwError(() => new Error('unavailable')));

    component.selectAccount(account.accountId);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[data-testid="context-error"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="eligible-markets"]')).toBeNull();
  });
});

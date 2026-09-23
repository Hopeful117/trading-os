import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AccountService } from '../../../core/services/account.service';
import { BrokerAccountService } from '../../../core/services/broker-account.service';
import { MarketService } from '../../../core/services/market.service';
import { TradePlanService } from '../../../core/services/trade-plan.service';
import { ManualTradePage } from './manual-trade-page';

const accounts = [
  {
    accountId: 'paper-account',
    brokerAccountId: 'paper-broker',
    name: 'Paper account',
    baseCurrency: 'USD',
    balances: { balances: {} },
    equity: 10_000,
    peakEquity: 10_000,
    rulesId: 'rules',
    userId: 'user',
    tradePlanningProfileId: 'profile',
    tradePlanningProfileVersion: 1,
  },
];

const brokerAccounts = [
  {
    id: 'paper-broker',
    provider: 'KRAKEN' as const,
    executionMode: 'PAPER' as const,
    displayName: 'Paper',
    externalAccountId: null,
    connectionStatus: 'CONNECTED' as const,
    lastValidatedAt: null,
    lastSynchronizedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
];

const markets = [
  {
    marketId: 'market-1',
    provider: 'KRAKEN',
    symbol: 'BTC/USD',
    baseAsset: 'BTC',
    quoteAsset: 'USD',
    marketState: { tradingStatus: 'OPEN', tradable: true, closureReason: '', lastUpdated: '' },
    marketConstraints: {
      minimumOrderSize: 0.0001,
      minimumCost: 1,
      tickSize: 0.01,
      quantityPrecision: 8,
      pricePrecision: 2,
    },
  },
];

describe('ManualTradePage', () => {
  let fixture: ComponentFixture<ManualTradePage>;
  let createManual: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    createManual = vi.fn(() => of({ tradePlanId: 'plan-1', tradePlanVersion: 1 }));
    await TestBed.configureTestingModule({
      imports: [ManualTradePage],
      providers: [
        provideRouter([{ path: '**', redirectTo: '' }]),
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({})) } },
        { provide: AccountService, useValue: { getAccounts: () => of(accounts) } },
        { provide: BrokerAccountService, useValue: { list: () => of(brokerAccounts) } },
        { provide: MarketService, useValue: { findAll: () => of(markets) } },
        { provide: TradePlanService, useValue: { createManual } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ManualTradePage);
    fixture.detectChanges();
  });

  it('shows the manual form with the ready PAPER account and market', () => {
    expect(
      fixture.nativeElement.querySelector('[data-testid="manual-account-select"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('BTC/USD');
    expect(fixture.nativeElement.textContent).toContain('Paper account');
  });

  it('prefills the account and market passed by the decision workspace', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ManualTradePage],
      providers: [
        provideRouter([{ path: '**', redirectTo: '' }]),
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: of(
              convertToParamMap({ accountId: 'paper-account', marketId: 'market-1' }),
            ),
          },
        },
        { provide: AccountService, useValue: { getAccounts: () => of(accounts) } },
        { provide: BrokerAccountService, useValue: { list: () => of(brokerAccounts) } },
        { provide: MarketService, useValue: { findAll: () => of(markets) } },
        { provide: TradePlanService, useValue: { createManual } },
      ],
    }).compileComponents();

    const contextFixture = TestBed.createComponent(ManualTradePage);
    contextFixture.detectChanges();
    await contextFixture.whenStable();

    expect(contextFixture.componentInstance.selectionForm.controls.accountId.value).toBe(
      'paper-account',
    );
    expect(contextFixture.componentInstance.selectionForm.controls.marketId.value).toBe('market-1');
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { TradePlanService } from '../../../core/services/trade-plan.service';
import { ManualTradeTicket } from './manual-trade-ticket';

describe('ManualTradeTicket', () => {
  let fixture: ComponentFixture<ManualTradeTicket>;
  let createManual: ReturnType<typeof vi.fn>;

  const account = {
    accountId: 'paper-account',
    name: 'Paper account',
    baseCurrency: 'USD',
  };
  const market = {
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
  };

  beforeEach(async () => {
    createManual = vi.fn(() => of({ tradePlanId: 'plan-1', tradePlanVersion: 1 }));
    await TestBed.configureTestingModule({
      imports: [ManualTradeTicket],
      providers: [
        provideRouter([{ path: 'trade-planning/plans/:planId/versions/:version', children: [] }]),
        { provide: TradePlanService, useValue: { createManual } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ManualTradeTicket);
    fixture.componentRef.setInput('account', account);
    fixture.componentRef.setInput('market', market);
    fixture.componentRef.setInput('referencePrice', 100);
    fixture.detectChanges();
  });

  it('displays inherited account and market context without selectors', () => {
    expect(fixture.nativeElement.querySelector('[data-testid="manual-trade-ticket"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Paper account');
    expect(fixture.nativeElement.textContent).toContain('BTC/USD');
    expect(fixture.nativeElement.textContent).toContain('OPEN');
    expect(fixture.nativeElement.querySelector('[data-testid="manual-account-select"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="manual-market-select"]')).toBeNull();
  });

  it('creates a MANUAL plan through the existing service without planning context fields', () => {
    fixture.componentInstance.form.patchValue({
      referencePrice: 100,
      stopLoss: 90,
      stopRationale: 'Invalidation below support',
      quantity: 2,
      monetaryRisk: 20,
      takeProfit: 120,
      thesis: 'Manual setup',
      confirmationConditions: 'Price confirms',
      invalidationConditions: 'Support breaks',
    });

    fixture.componentInstance.submit();

    expect(createManual).toHaveBeenCalledWith(
      expect.objectContaining({
        accountId: 'paper-account',
        marketId: 'market-1',
        entryType: 'MARKET',
        entryPrice: null,
        takeProfits: [{ price: 120, allocationPercent: 100 }],
      }),
      expect.any(String),
    );
    expect(createManual.mock.calls[0][0]).not.toHaveProperty('planningContextId');
  });
});

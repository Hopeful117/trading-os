import { Component, Input, OnChanges, SimpleChanges, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { MarketResponse } from '../../../core/models/market-response';
import { ManualTradePlanRequest } from '../../../core/models/trade-plan.model';
import { TradePlanService } from '../../../core/services/trade-plan.service';
import { tradeFlowErrorMessage } from '../../../core/utils/trade-flow-error';

export interface ManualTradeAccountContext {
  accountId: string;
  name: string;
  baseCurrency: string;
}

@Component({
  selector: 'app-manual-trade-ticket',
  imports: [ReactiveFormsModule],
  templateUrl: './manual-trade-ticket.html',
  styleUrl: './manual-trade-ticket.scss',
})
export class ManualTradeTicket implements OnChanges {
  private readonly router = inject(Router);
  private readonly tradePlanService = inject(TradePlanService);

  @Input({ required: true }) account!: ManualTradeAccountContext;
  @Input({ required: true }) market!: MarketResponse;
  @Input() referencePrice: number | null = null;

  readonly state = signal<'idle' | 'submitting' | 'error'>('idle');
  readonly errorMessage = signal<string | null>(null);

  readonly form = new FormGroup({
    accountId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    marketId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    direction: new FormControl<'LONG' | 'SHORT'>('LONG', { nonNullable: true }),
    entryType: new FormControl<'MARKET' | 'LIMIT'>('MARKET', { nonNullable: true }),
    referencePrice: new FormControl<number | null>(null, [
      Validators.required,
      Validators.min(0.00000001),
    ]),
    entryPrice: new FormControl<number | null>(null, [Validators.min(0.00000001)]),
    stopLoss: new FormControl<number | null>(null, [
      Validators.required,
      Validators.min(0.00000001),
    ]),
    stopRationale: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    quantity: new FormControl<number | null>(null, [
      Validators.required,
      Validators.min(0.00000001),
    ]),
    monetaryRisk: new FormControl<number | null>(null, [
      Validators.required,
      Validators.min(0.00000001),
    ]),
    takeProfit: new FormControl<number | null>(null, [
      Validators.required,
      Validators.min(0.00000001),
    ]),
    thesis: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    confirmationConditions: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    invalidationConditions: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    managementRules: new FormControl('', { nonNullable: true }),
  });

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['account'] || changes['market']) {
      this.form.patchValue({
        accountId: this.account?.accountId ?? '',
        marketId: this.market?.marketId ?? '',
      });
    }

    if (
      changes['referencePrice'] &&
      this.referencePrice !== null &&
      !this.form.controls.referencePrice.dirty
    ) {
      this.form.controls.referencePrice.setValue(this.referencePrice);
    }
  }

  submit(): void {
    this.errorMessage.set(null);
    const value = this.form.getRawValue();
    const entryPrice = value.entryType === 'LIMIT' ? value.entryPrice : null;

    if (this.form.invalid || (value.entryType === 'LIMIT' && entryPrice === null)) {
      this.form.markAllAsTouched();
      this.state.set('error');
      this.errorMessage.set('Complete the required trade fields before continuing.');
      return;
    }

    if (
      value.referencePrice === null ||
      value.takeProfit === null ||
      value.stopLoss === null ||
      value.quantity === null ||
      value.monetaryRisk === null
    ) {
      this.state.set('error');
      this.errorMessage.set('The trade parameters are incomplete.');
      return;
    }

    const request: ManualTradePlanRequest = {
      accountId: value.accountId,
      marketId: value.marketId,
      direction: value.direction,
      entryType: value.entryType,
      entryPrice,
      referencePrice: value.referencePrice,
      stopLoss: value.stopLoss,
      stopRationale: value.stopRationale,
      takeProfits: [{ price: value.takeProfit, allocationPercent: 100 }],
      quantity: value.quantity,
      monetaryRisk: value.monetaryRisk,
      thesis: value.thesis,
      confirmationConditions: this.lines(value.confirmationConditions),
      invalidationConditions: this.lines(value.invalidationConditions),
      managementRules: this.lines(value.managementRules),
    };

    if (
      request.confirmationConditions.length === 0 ||
      request.invalidationConditions.length === 0
    ) {
      this.state.set('error');
      this.errorMessage.set('At least one confirmation and invalidation condition is required.');
      return;
    }

    this.state.set('submitting');
    this.tradePlanService.createManual(request, crypto.randomUUID()).subscribe({
      next: (created) => {
        void this.router.navigate([
          '/trade-planning',
          'plans',
          created.tradePlanId,
          'versions',
          created.tradePlanVersion,
        ]);
      },
      error: (error: unknown) => {
        this.state.set('error');
        this.errorMessage.set(
          tradeFlowErrorMessage(error, 'The manual Trade Plan could not be created.'),
        );
      },
    });
  }

  private lines(value: string): string[] {
    return value
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean);
  }
}

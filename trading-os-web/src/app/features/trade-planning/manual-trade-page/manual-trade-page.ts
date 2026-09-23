import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { combineLatest, Observable, catchError, map, of, shareReplay, startWith } from 'rxjs';

import { Account } from '../../../core/models/account.model';
import { BrokerAccount } from '../../../core/models/broker-account.model';
import { MarketResponse } from '../../../core/models/market-response';
import { AccountService } from '../../../core/services/account.service';
import { BrokerAccountService } from '../../../core/services/broker-account.service';
import { MarketService } from '../../../core/services/market.service';
import { tradeFlowErrorMessage } from '../../../core/utils/trade-flow-error';
import {
  ManualTradeAccountContext,
  ManualTradeTicket,
} from '../manual-trade-ticket/manual-trade-ticket';

type ManualAccount = Account & { brokerAccount: BrokerAccount };

export type ManualTradeView =
  | { status: 'loading' }
  | { status: 'ready'; accounts: ManualAccount[]; markets: MarketResponse[] }
  | { status: 'error'; message: string };

@Component({
  selector: 'app-manual-trade-page',
  imports: [AsyncPipe, ReactiveFormsModule, RouterLink, ManualTradeTicket],
  templateUrl: './manual-trade-page.html',
  styleUrl: './manual-trade-page.scss',
})
export class ManualTradePage {
  private readonly accountService = inject(AccountService);
  private readonly brokerAccountService = inject(BrokerAccountService);
  private readonly marketService = inject(MarketService);
  private readonly route = inject(ActivatedRoute);

  readonly selectionForm = new FormGroup({
    accountId: new FormControl('', { nonNullable: true }),
    marketId: new FormControl('', { nonNullable: true }),
  });

  readonly view$: Observable<ManualTradeView> = combineLatest({
    accounts: this.accountService.getAccounts(),
    brokerAccounts: this.brokerAccountService.list(),
    markets: this.marketService.findAll(),
  }).pipe(
    map(({ accounts, brokerAccounts, markets }) => ({
      status: 'ready' as const,
      accounts: accounts
        .map((account) => ({
          account,
          brokerAccount: brokerAccounts.find((broker) => broker.id === account.brokerAccountId),
        }))
        .filter(
          (value): value is { account: Account; brokerAccount: BrokerAccount } =>
            value.brokerAccount?.executionMode === 'PAPER' &&
            value.account.tradePlanningProfileId !== null &&
            value.account.tradePlanningProfileId !== undefined,
        )
        .map(({ account, brokerAccount }) => ({ ...account, brokerAccount })),
      markets: markets.filter((market) => market.marketState.tradable),
    })),
    map((view) => view as ManualTradeView),
    catchError((error: unknown) =>
      of<ManualTradeView>({
        status: 'error',
        message: tradeFlowErrorMessage(error, 'Trading accounts or markets could not be loaded.'),
      }),
    ),
    startWith<ManualTradeView>({ status: 'loading' }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  constructor() {
    this.route.queryParamMap.subscribe((params) => {
      this.selectionForm.patchValue({
        accountId: params.get('accountId') ?? '',
        marketId: params.get('marketId') ?? '',
      });
    });
  }

  selectedAccount(
    view: Extract<ManualTradeView, { status: 'ready' }>,
  ): ManualTradeAccountContext | null {
    const accountId = this.selectionForm.controls.accountId.value;
    return view.accounts.find((account) => account.accountId === accountId) ?? null;
  }

  selectedMarket(view: Extract<ManualTradeView, { status: 'ready' }>): MarketResponse | null {
    const marketId = this.selectionForm.controls.marketId.value;
    return view.markets.find((market) => market.marketId === marketId) ?? null;
  }
}

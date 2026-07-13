import { Component, Input } from '@angular/core';
import { Account } from '../../../../core/models/account.model';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-account-card',
  imports: [
    MatCardModule,
  ],
  templateUrl: './account-card.html',
  styleUrl: './account-card.scss',
})
export class AccountCard {
  @Input()
  account!: Account;

  getBalances(account: Account): { asset: string; amount: number }[] {
    return Object.entries(account.balances.balances).map(([asset, amount]) => ({
      asset,
      amount: Number(amount),
    }));
  }
}

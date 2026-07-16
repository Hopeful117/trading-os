import { Component } from '@angular/core';
import { AccountService } from '../../../../core/services/account.service';
import { Account } from '../../../../core/models/account.model';
import { AccountCard} from '../../components/account-card/account-card';
import { Observable } from 'rxjs';
import { AsyncPipe } from '@angular/common';



@Component({
  selector: 'app-accounts',
  imports: [AccountCard, AsyncPipe],
  templateUrl: './accounts.html',
  styleUrl: './accounts.scss',
})
export class Accounts {
  accounts!: Observable<Account[]>;
  loadingSync = false;
  errorMessage = '';

  constructor(private accountService: AccountService) {}

  ngOnInit(): void {
    this.loadAccounts();
  }

  private loadAccounts(): void {
    this.accounts = this.accountService.getAccounts();
  }

  sync(): void {
    this.loadingSync = true;

    this.accountService.synchronize().subscribe({
      next: () => {
        this.loadingSync = false;
        this.loadAccounts();
      },
      error: () => {
        this.loadingSync = false;
        this.errorMessage = 'Erreur lors de la synchronisation.';
      },
    });
  }
}

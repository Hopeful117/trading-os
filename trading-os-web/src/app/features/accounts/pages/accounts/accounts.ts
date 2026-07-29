import { Component } from '@angular/core';
import { AccountService } from '../../../../core/services/account.service';
import { Account } from '../../../../core/models/account.model';
import { AccountCard} from '../../components/account-card/account-card';
import { combineLatest, Observable, shareReplay } from 'rxjs';
import { AsyncPipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { BrokerAccountService } from '../../../../core/services/broker-account.service';
import { BrokerAccount } from '../../../../core/models/broker-account.model';



@Component({
  selector: 'app-accounts',
  imports: [AccountCard, AsyncPipe, ReactiveFormsModule],
  templateUrl: './accounts.html',
  styleUrl: './accounts.scss',
})
export class Accounts {
  accounts!: Observable<Account[]>;
  loadingSync = false;
  errorMessage = '';
  brokerAccounts!: Observable<BrokerAccount[]>;
  accountState!: Observable<{ accounts: Account[]; brokerAccounts: BrokerAccount[] }>;
  connectionMessage = '';
  connecting = false;
  readonly brokerForm = new FormGroup({
    provider: new FormControl<'KRAKEN'>('KRAKEN', { nonNullable: true }),
    displayName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(80)],
    }),
    apiKey: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(8), Validators.maxLength(256)],
    }),
    apiSecret: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(16), Validators.maxLength(512)],
    }),
    passphrase: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(256)] }),
  });

  constructor(
    private accountService: AccountService,
    private brokerAccountService: BrokerAccountService,
  ) {}

  ngOnInit(): void {
    this.loadAccounts();
    this.loadBrokerAccounts();
  }

  connectBroker(): void {
    if (this.brokerForm.invalid || this.connecting) {
      this.brokerForm.markAllAsTouched();
      return;
    }
    this.connecting = true;
    this.connectionMessage = '';
    this.brokerAccountService.createAndConnect(this.brokerForm.getRawValue()).subscribe({
      next: (result) => {
        this.connecting = false;
        this.connectionMessage = result.safeMessage;
        this.brokerForm.reset({
          provider: 'KRAKEN',
          displayName: '',
          apiKey: '',
          apiSecret: '',
          passphrase: '',
        });
        this.loadBrokerAccounts();
      },
      error: () => {
        this.connecting = false;
        this.connectionMessage = 'La connexion broker n’a pas pu être validée.';
        this.clearSensitiveFields();
      },
    });
  }

  private loadBrokerAccounts(): void {
    this.brokerAccounts = this.brokerAccountService.list().pipe(
      shareReplay({ bufferSize: 1, refCount: true }),
    );
    this.refreshAccountState();
  }

  private clearSensitiveFields(): void {
    this.brokerForm.controls.apiKey.setValue('');
    this.brokerForm.controls.apiSecret.setValue('');
    this.brokerForm.controls.passphrase.setValue('');
  }

  private loadAccounts(): void {
    this.accounts = this.accountService.getAccounts().pipe(
      shareReplay({ bufferSize: 1, refCount: true }),
    );
    this.refreshAccountState();
  }

  private refreshAccountState(): void {
    if (!this.accounts || !this.brokerAccounts) {
      return;
    }
    this.accountState = combineLatest({
      accounts: this.accounts,
      brokerAccounts: this.brokerAccounts,
    }).pipe(shareReplay({ bufferSize: 1, refCount: true }));
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

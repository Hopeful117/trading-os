import { Component, signal } from '@angular/core';
import { AccountService } from '../../../../core/services/account.service';
import { Account } from '../../../../core/models/account.model';
import { AccountCard } from '../../components/account-card/account-card';
import { combineLatest, Observable, shareReplay } from 'rxjs';
import { AsyncPipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { BrokerAccountService } from '../../../../core/services/broker-account.service';
import { BrokerAccount } from '../../../../core/models/broker-account.model';

export type ConnectionFeedback =
  { kind: 'success'; message: string } | { kind: 'error'; message: string } | null;

@Component({
  selector: 'app-accounts',
  imports: [AccountCard, AsyncPipe, ReactiveFormsModule],
  templateUrl: './accounts.html',
  styleUrl: './accounts.scss',
})
export class Accounts {
  accounts!: Observable<Account[]>;
  brokerAccounts!: Observable<BrokerAccount[]>;
  accountState!: Observable<{ accounts: Account[]; brokerAccounts: BrokerAccount[] }>;

  readonly connecting = signal(false);
  readonly connectionFeedback = signal<ConnectionFeedback>(null);
  readonly syncing = signal(false);
  readonly syncFeedback = signal<ConnectionFeedback>(null);

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
    if (this.brokerForm.invalid || this.connecting()) {
      this.brokerForm.markAllAsTouched();
      return;
    }
    this.connecting.set(true);
    this.connectionFeedback.set(null);
    this.brokerAccountService.createAndConnect(this.brokerForm.getRawValue()).subscribe({
      next: (result) => {
        this.connecting.set(false);
        if (result.outcome === 'VALID') {
          const detail = result.safeMessage || 'Credentials validés';
          this.connectionFeedback.set({
            kind: 'success',
            message: `Connexion Kraken réussie — ${detail}. Vous pouvez maintenant synchroniser vos comptes.`,
          });
          this.brokerForm.reset({
            provider: 'KRAKEN',
            displayName: '',
            apiKey: '',
            apiSecret: '',
            passphrase: '',
          });
          this.loadBrokerAccounts();
        } else {
          this.connectionFeedback.set({
            kind: 'error',
            message:
              result.safeMessage ||
              "La connexion broker n'a pas pu être validée. Vérifiez vos clés API puis réessayez.",
          });
          this.clearSensitiveFields();
        }
      },
      error: () => {
        this.connecting.set(false);
        this.connectionFeedback.set({
          kind: 'error',
          message:
            "La connexion broker n'a pas pu être validée. Vérifiez vos clés API puis réessayez.",
        });
        this.clearSensitiveFields();
      },
    });
  }

  sync(): void {
    if (this.syncing()) {
      return;
    }
    this.syncing.set(true);
    this.syncFeedback.set(null);

    this.accountService.synchronize().subscribe({
      next: () => {
        this.syncing.set(false);
        this.syncFeedback.set({ kind: 'success', message: 'Synchronisation réussie.' });
        this.loadAccounts();
      },
      error: () => {
        this.syncing.set(false);
        this.syncFeedback.set({
          kind: 'error',
          message: 'Erreur lors de la synchronisation.',
        });
      },
    });
  }

  private loadBrokerAccounts(): void {
    this.brokerAccounts = this.brokerAccountService
      .list()
      .pipe(shareReplay({ bufferSize: 1, refCount: true }));
    this.refreshAccountState();
  }

  private clearSensitiveFields(): void {
    this.brokerForm.controls.apiKey.setValue('');
    this.brokerForm.controls.apiSecret.setValue('');
    this.brokerForm.controls.passphrase.setValue('');
  }

  private loadAccounts(): void {
    this.accounts = this.accountService
      .getAccounts()
      .pipe(shareReplay({ bufferSize: 1, refCount: true }));
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
}

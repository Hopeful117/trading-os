import { Component, signal } from '@angular/core';
import { AccountService } from '../../../../core/services/account.service';
import { Account } from '../../../../core/models/account.model';
import { AccountCard } from '../../components/account-card/account-card';
import { catchError, combineLatest, map, Observable, of, shareReplay, startWith } from 'rxjs';
import { AsyncPipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { BrokerAccountService } from '../../../../core/services/broker-account.service';
import { BrokerAccount } from '../../../../core/models/broker-account.model';
import { RiskProfileCatalogEntry } from '../../../../core/models/risk-profile.model';

type RiskProfileState = {
  profiles: RiskProfileCatalogEntry[];
  loading: boolean;
  error: boolean;
};

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
  riskProfileState!: Observable<RiskProfileState>;

  readonly connecting = signal(false);
  readonly connectionFeedback = signal<ConnectionFeedback>(null);
  readonly syncing = signal(false);
  readonly syncFeedback = signal<ConnectionFeedback>(null);
  readonly riskProfileSelection = new FormControl('', { nonNullable: true });

  readonly brokerForm = new FormGroup({
    provider: new FormControl<'KRAKEN'>('KRAKEN', { nonNullable: true }),
    executionMode: new FormControl<'LIVE' | 'PAPER'>('LIVE', { nonNullable: true }),
    displayName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(80)],
    }),
    initialCapital: new FormControl<number | null>(null, {
      validators: [Validators.min(0.01)],
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
    this.loadRiskProfiles();
  }

  connectBroker(): void {
    if (this.connecting()) {
      return;
    }

    const command = this.brokerForm.getRawValue();
    if (command.executionMode === 'PAPER') {
      if (
        !command.displayName ||
        !command.initialCapital ||
        command.initialCapital <= 0 ||
        !this.riskProfileSelection.value
      ) {
        this.brokerForm.controls.displayName.markAsTouched();
        this.brokerForm.controls.initialCapital.markAsTouched();
        this.riskProfileSelection.markAsTouched();
        return;
      }
      this.createPaperAccount(command);
      return;
    }

    if (this.brokerForm.invalid) {
      this.brokerForm.markAllAsTouched();
      return;
    }
    this.connecting.set(true);
    this.connectionFeedback.set(null);
    this.brokerAccountService.createAndConnect(command).subscribe({
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
            executionMode: 'LIVE',
            displayName: '',
            initialCapital: null,
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

  isPaper(): boolean {
    return this.brokerForm.controls.executionMode.value === 'PAPER';
  }

  private createPaperAccount(command: {
    provider: 'KRAKEN';
    executionMode: 'LIVE' | 'PAPER';
    displayName: string;
    initialCapital: number | null;
    apiKey: string;
    apiSecret: string;
    passphrase: string;
  }): void {
    const [profileId, semanticVersion] = this.riskProfileSelection.value.split('::');
    this.connecting.set(true);
    this.connectionFeedback.set(null);
    this.brokerAccountService
      .createPaper({
        provider: command.provider,
        displayName: command.displayName,
        initialCapital: command.initialCapital!,
        riskProfile: { profileId, semanticVersion },
      })
      .subscribe({
        next: () => {
          this.connecting.set(false);
          this.connectionFeedback.set({
            kind: 'success',
            message: 'Compte PAPER créé. Le capital initial est disponible pour le trading simulé.',
          });
          this.brokerForm.reset({
            provider: 'KRAKEN',
            executionMode: 'LIVE',
            displayName: '',
            initialCapital: null,
            apiKey: '',
            apiSecret: '',
            passphrase: '',
          });
          this.riskProfileSelection.reset('');
          this.loadBrokerAccounts();
          this.loadAccounts();
        },
        error: () => {
          this.connecting.set(false);
          this.connectionFeedback.set({
            kind: 'error',
            message:
              'Le compte PAPER n’a pas pu être créé. La politique sélectionnée est peut-être devenue indisponible. Rechargez les politiques puis réessayez.',
          });
        },
      });
  }

  private loadRiskProfiles(): void {
    this.riskProfileState = this.brokerAccountService.eligibleRiskProfiles().pipe(
      map((profiles) => ({ profiles, loading: false, error: false })),
      startWith({ profiles: [], loading: true, error: false }),
      catchError(() => of({ profiles: [], loading: false, error: true })),
      shareReplay({ bufferSize: 1, refCount: true }),
    );
  }

  riskProfileKey(profile: RiskProfileCatalogEntry): string {
    return `${profile.profileId}::${profile.semanticVersion}`;
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

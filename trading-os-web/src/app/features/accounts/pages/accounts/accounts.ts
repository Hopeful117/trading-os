import { Component, signal } from '@angular/core';
import { AccountService } from '../../../../core/services/account.service';
import { Account } from '../../../../core/models/account.model';
import { AccountCard } from '../../components/account-card/account-card';
import {
  catchError,
  combineLatest,
  map,
  Observable,
  of,
  shareReplay,
  startWith,
  Subscription,
  switchMap,
  takeWhile,
  timer,
} from 'rxjs';
import { AsyncPipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { BrokerAccountService } from '../../../../core/services/broker-account.service';
import {
  BrokerAccount,
  BrokerProvider,
  CTraderAuthorization,
  CTraderMarketDataAuthorizationState,
  CTRADER_PROVIDER_REGISTRATION_REFERENCE,
} from '../../../../core/models/broker-account.model';
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
  readonly recoveryAccount = signal<BrokerAccount | null>(null);
  readonly revokingAccountId = signal<string | null>(null);
  readonly revokeFeedback = signal<ConnectionFeedback>(null);
  readonly disconnectingAccountId = signal<string | null>(null);
  readonly disconnectFeedback = signal<ConnectionFeedback>(null);
  readonly provisioningAccountId = signal<string | null>(null);
  readonly provisioningFeedback = signal<ConnectionFeedback>(null);
  readonly syncing = signal(false);
  readonly syncFeedback = signal<ConnectionFeedback>(null);
  readonly cTraderAuthorization = signal<CTraderAuthorization | null>(null);
  readonly cTraderFlowAccountId = signal<string | null>(null);
  readonly cTraderMarketDataUrl = signal<string | null>(null);
  readonly riskProfileSelection = new FormControl('', { nonNullable: true });
  private cTraderPoll?: Subscription;
  private cTraderMarketDataPoll?: Subscription;

  readonly brokerForm = new FormGroup({
    provider: new FormControl<BrokerProvider>('KRAKEN', { nonNullable: true }),
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
    this.restoreCTraderAuthorization();
  }

  connectBroker(): void {
    if (this.lifecycleBusy()) {
      return;
    }

    const command = this.brokerForm.getRawValue();
    if (command.provider === 'CTRADER') {
      this.connectCTrader(command.displayName);
      return;
    }
    const recovery = this.recoveryAccount();
    if (recovery) {
      if (this.brokerForm.invalid) {
        this.brokerForm.markAllAsTouched();
        return;
      }
      this.connecting.set(true);
      this.connectionFeedback.set(null);
      this.brokerAccountService.connectExisting(recovery.id, this.credentials(command)).subscribe({
        next: (result) => this.handleCredentialResult(result, true),
        error: () => this.handleCredentialError(),
      });
      return;
    }

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
      next: (result) => this.handleCredentialResult(result, false),
      error: () => this.handleCredentialError(),
    });
  }

  bindCTraderAccount(externalAccountId: string): void {
    const authorization = this.cTraderAuthorization();
    const accountId = this.cTraderFlowAccountId();
    if (this.lifecycleBusy() || !authorization || !accountId || !externalAccountId) return;

    this.provisioningAccountId.set(accountId);
    this.provisioningFeedback.set(null);
    this.brokerAccountService
      .bindCTraderAccount(accountId, authorization.attemptId, externalAccountId)
      .pipe(
        switchMap((bound) => {
          this.cTraderAuthorization.set(bound);
          return this.brokerAccountService.provisionTradingAccount(accountId);
        }),
      )
      .subscribe({
        next: () => {
          this.provisioningAccountId.set(null);
          this.provisioningFeedback.set({
            kind: 'success',
            message: 'Compte cTrader lié et compte Trading Core provisionné.',
          });
          this.loadBrokerAccounts();
          this.loadAccounts();
        },
        error: () => {
          this.provisioningAccountId.set(null);
          this.provisioningFeedback.set({
            kind: 'error',
            message: 'Le binding cTrader ou le provisionnement Trading Core a échoué.',
          });
          this.loadBrokerAccounts();
        },
      });
  }

  startCTraderMarketDataAuthorization(): void {
    if (this.lifecycleBusy()) return;
    this.brokerAccountService.startCTraderMarketDataAuthorization().subscribe({
      next: (start) => {
        this.cTraderMarketDataUrl.set(start.authorizationUrl);
        window.open(
          start.authorizationUrl,
          'ctrader-market-data-oauth',
          'popup,width=720,height=800',
        );
        this.pollCTraderMarketDataAuthorization(start.attemptId);
      },
      error: () =>
        this.connectionFeedback.set({
          kind: 'error',
          message: 'L’autorisation Market Data cTrader n’a pas pu être démarrée.',
        }),
    });
  }

  private pollCTraderMarketDataAuthorization(attemptId: string): void {
    this.cTraderMarketDataPoll?.unsubscribe();
    this.cTraderMarketDataPoll = timer(1500, 1500)
      .pipe(
        switchMap(() => this.brokerAccountService.getCTraderMarketDataAuthorization(attemptId)),
        takeWhile((current) => current.status === 'INITIATED', true),
      )
      .subscribe({
        next: (current: CTraderMarketDataAuthorizationState) => {
          if (current.status !== 'INITIATED') {
            this.connectionFeedback.set({
              kind: current.status === 'COMPLETED' ? 'success' : 'error',
              message:
                current.status === 'COMPLETED'
                  ? 'L’autorisation Market Data cTrader est terminée.'
                  : 'L’autorisation Market Data cTrader a échoué ou a expiré.',
            });
          }
        },
        error: () =>
          this.connectionFeedback.set({
            kind: 'error',
            message: 'L’état de l’autorisation Market Data cTrader n’a pas pu être récupéré.',
          }),
      });
  }

  startRecovery(account: BrokerAccount): void {
    if (
      this.lifecycleBusy() ||
      this.recoveryAccount() !== null ||
      account.connectionStatus === 'REVOKED' ||
      account.connectionStatus === 'PENDING_VALIDATION' ||
      account.executionMode !== 'LIVE'
    ) {
      return;
    }

    this.recoveryAccount.set(account);
    this.connectionFeedback.set(null);
    this.brokerForm.controls.displayName.disable();
    this.brokerForm.controls.executionMode.disable();
    this.brokerForm.controls.initialCapital.disable();
    this.brokerForm.reset({
      provider: 'KRAKEN',
      executionMode: account.executionMode,
      displayName: account.displayName,
      initialCapital: null,
      apiKey: '',
      apiSecret: '',
      passphrase: '',
    });
  }

  cancelRecovery(): void {
    if (this.connecting()) {
      return;
    }
    this.resetBrokerForm();
    this.connectionFeedback.set(null);
  }

  disconnectBrokerAccount(account: BrokerAccount): void {
    if (this.lifecycleBusy() || account.connectionStatus !== 'CONNECTED') {
      return;
    }

    if (!window.confirm(`Déconnecter le compte broker « ${account.displayName} » ?`)) {
      return;
    }

    this.disconnectingAccountId.set(account.id);
    this.disconnectFeedback.set(null);
    this.brokerAccountService.disconnect(account.id).subscribe({
      next: () => {
        this.disconnectingAccountId.set(null);
        this.disconnectFeedback.set({
          kind: 'success',
          message: `Le compte broker « ${account.displayName} » a été déconnecté.`,
        });
        this.loadBrokerAccounts();
      },
      error: () => {
        this.disconnectingAccountId.set(null);
        this.disconnectFeedback.set({
          kind: 'error',
          message: 'La déconnexion n’a pas pu être confirmée. Vérifiez l’état du compte.',
        });
      },
    });
  }

  provisionTradingAccount(brokerAccount: BrokerAccount, accounts: Account[]): void {
    if (
      this.lifecycleBusy() ||
      this.provisioningAccountId() !== null ||
      brokerAccount.connectionStatus !== 'CONNECTED' ||
      this.hasTradingAccount(brokerAccount, accounts)
    ) {
      return;
    }

    this.provisioningAccountId.set(brokerAccount.id);
    this.provisioningFeedback.set(null);
    this.brokerAccountService.provisionTradingAccount(brokerAccount.id).subscribe({
      next: () => {
        this.provisioningAccountId.set(null);
        this.provisioningFeedback.set({
          kind: 'success',
          message: `Le compte Trading Core « ${brokerAccount.displayName} » a été créé et synchronisé.`,
        });
        this.loadAccounts();
      },
      error: () => {
        this.provisioningAccountId.set(null);
        this.provisioningFeedback.set({
          kind: 'error',
          message: 'Le compte Trading Core n’a pas pu être créé. Vérifiez le snapshot broker.',
        });
      },
    });
  }

  hasTradingAccount(brokerAccount: BrokerAccount, accounts: Account[]): boolean {
    return accounts.some((account) => account.brokerAccountId === brokerAccount.id);
  }

  canRecover(account: BrokerAccount): boolean {
    return (
      account.executionMode === 'LIVE' &&
      account.connectionStatus !== 'REVOKED' &&
      account.connectionStatus !== 'PENDING_VALIDATION'
    );
  }

  isPaper(): boolean {
    return this.brokerForm.controls.executionMode.value === 'PAPER';
  }

  isCTrader(): boolean {
    return this.brokerForm.controls.provider.value === 'CTRADER';
  }

  connectExistingCTrader(account: BrokerAccount): void {
    if (this.lifecycleBusy() || !this.isCTraderAccount(account)) return;
    this.startCTraderAuthorization(account.id);
  }

  isCTraderAccount(account: BrokerAccount): boolean {
    return account.provider === CTRADER_PROVIDER_REGISTRATION_REFERENCE;
  }

  lifecycleBusy(): boolean {
    return (
      this.connecting() ||
      this.revokingAccountId() !== null ||
      this.disconnectingAccountId() !== null ||
      this.provisioningAccountId() !== null
    );
  }

  revokeBrokerAccount(account: BrokerAccount): void {
    if (this.lifecycleBusy() || account.connectionStatus === 'REVOKED') {
      return;
    }

    if (!window.confirm(`Révoquer le compte broker « ${account.displayName} » ?`)) {
      return;
    }

    this.revokingAccountId.set(account.id);
    this.revokeFeedback.set(null);
    this.brokerAccountService.revoke(account.id).subscribe({
      next: () => {
        this.revokingAccountId.set(null);
        this.revokeFeedback.set({
          kind: 'success',
          message: `Le compte broker « ${account.displayName} » a été révoqué.`,
        });
        this.loadBrokerAccounts();
      },
      error: () => {
        this.revokingAccountId.set(null);
        this.revokeFeedback.set({
          kind: 'error',
          message: 'La révocation n’a pas pu être confirmée. Vérifiez l’état du compte.',
        });
      },
    });
  }

  private createPaperAccount(command: {
    provider: BrokerProvider;
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
          this.resetBrokerForm();
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

  private connectCTrader(displayName: string): void {
    if (!displayName.trim()) {
      this.brokerForm.controls.displayName.markAsTouched();
      return;
    }
    this.connecting.set(true);
    this.connectionFeedback.set(null);
    this.brokerAccountService
      .createCTrader(displayName.trim())
      .pipe(
        switchMap((account) => {
          this.cTraderFlowAccountId.set(account.id);
          return this.brokerAccountService.startCTraderAuthorization(account.id);
        }),
      )
      .subscribe({
        next: (authorization) => {
          this.connecting.set(false);
          this.handleCTraderAuthorization(authorization);
        },
        error: () => {
          this.connecting.set(false);
          this.connectionFeedback.set({
            kind: 'error',
            message: 'Le compte cTrader n’a pas pu être créé ou l’autorisation n’a pas démarré.',
          });
        },
      });
  }

  private startCTraderAuthorization(accountId: string): void {
    if (this.lifecycleBusy()) return;
    this.connecting.set(true);
    this.connectionFeedback.set(null);
    this.cTraderFlowAccountId.set(accountId);
    this.brokerAccountService.startCTraderAuthorization(accountId).subscribe({
      next: (authorization) => {
        this.connecting.set(false);
        this.handleCTraderAuthorization(authorization);
      },
      error: () => {
        this.connecting.set(false);
        this.connectionFeedback.set({
          kind: 'error',
          message: 'L’autorisation cTrader n’a pas pu être démarrée.',
        });
      },
    });
  }

  private handleCTraderAuthorization(authorization: CTraderAuthorization): void {
    this.cTraderAuthorization.set(authorization);
    localStorage.setItem('ctrader-flow-account-id', this.cTraderFlowAccountId() ?? '');
    localStorage.setItem('ctrader-attempt-id', authorization.attemptId);
    if (authorization.authorizationUrl) {
      window.open(authorization.authorizationUrl, 'ctrader-oauth', 'popup,width=720,height=800');
    }
    if (authorization.status === 'INITIATED') {
      this.pollCTraderAuthorization(authorization);
    }
  }

  private restoreCTraderAuthorization(): void {
    const accountId = localStorage.getItem('ctrader-flow-account-id');
    const attemptId = localStorage.getItem('ctrader-attempt-id');
    if (!accountId || !attemptId) return;
    this.cTraderFlowAccountId.set(accountId);
    const status$ = this.brokerAccountService.getCTraderAuthorization(accountId, attemptId);
    if (!status$) return;
    status$.subscribe({
      next: (authorization) => {
        this.handleCTraderAuthorization(authorization);
      },
      error: () => {
        localStorage.removeItem('ctrader-flow-account-id');
        localStorage.removeItem('ctrader-attempt-id');
      },
    });
  }

  private pollCTraderAuthorization(authorization: CTraderAuthorization): void {
    const accountId = this.cTraderFlowAccountId();
    if (!accountId) return;
    this.cTraderPoll?.unsubscribe();
    this.cTraderPoll = timer(1500, 1500)
      .pipe(
        switchMap(() =>
          this.brokerAccountService.getCTraderAuthorization(accountId, authorization.attemptId),
        ),
        takeWhile((current) => current.status === 'INITIATED', true),
      )
      .subscribe({
        next: (current) => {
          this.cTraderAuthorization.set(current);
          if (current.status !== 'ACCOUNT_SELECTION_REQUIRED') {
            this.connectionFeedback.set({
              kind: current.status === 'BOUND' ? 'success' : 'error',
              message: current.safeMessage,
            });
          }
        },
        error: () =>
          this.connectionFeedback.set({
            kind: 'error',
            message: 'L’état de l’autorisation cTrader n’a pas pu être récupéré.',
          }),
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
    this.brokerAccounts = this.brokerAccountService.list().pipe(
      map((accounts) => accounts.filter((account) => account.connectionStatus !== 'REVOKED')),
      shareReplay({ bufferSize: 1, refCount: true }),
    );
    this.refreshAccountState();
  }

  private clearSensitiveFields(): void {
    this.brokerForm.controls.apiKey.setValue('');
    this.brokerForm.controls.apiSecret.setValue('');
    this.brokerForm.controls.passphrase.setValue('');
  }

  private credentials(command: { apiKey: string; apiSecret: string; passphrase: string }): {
    apiKey: string;
    apiSecret: string;
    passphrase?: string;
  } {
    return {
      apiKey: command.apiKey,
      apiSecret: command.apiSecret,
      passphrase: command.passphrase || undefined,
    };
  }

  private handleCredentialResult(
    result: { outcome: string; safeMessage: string },
    recovery: boolean,
  ): void {
    this.connecting.set(false);
    if (result.outcome === 'VALID') {
      this.connectionFeedback.set({
        kind: 'success',
        message: recovery
          ? `Connexion Kraken rétablie — ${result.safeMessage || 'Credentials validés'}.`
          : `Connexion Kraken réussie — ${result.safeMessage || 'Credentials validés'}. Vous pouvez maintenant synchroniser vos comptes.`,
      });
      this.resetBrokerForm();
      this.loadBrokerAccounts();
      return;
    }
    this.connectionFeedback.set({
      kind: 'error',
      message:
        result.safeMessage ||
        "La connexion broker n'a pas pu être validée. Vérifiez vos clés API puis réessayez.",
    });
    this.clearSensitiveFields();
  }

  private handleCredentialError(): void {
    this.connecting.set(false);
    this.connectionFeedback.set({
      kind: 'error',
      message: "La connexion broker n'a pas pu être validée. Vérifiez vos clés API puis réessayez.",
    });
    this.clearSensitiveFields();
  }

  private resetBrokerForm(): void {
    this.recoveryAccount.set(null);
    this.brokerForm.enable();
    this.brokerForm.reset({
      provider: 'KRAKEN',
      executionMode: 'LIVE',
      displayName: '',
      initialCapital: null,
      apiKey: '',
      apiSecret: '',
      passphrase: '',
    });
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

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  BrokerAccount,
  BrokerProvider,
  CredentialValidation,
  CTraderAuthorization,
  CTraderMarketDataAuthorizationState,
  CTraderMarketDataAuthorization,
  CTRADER_PROVIDER_REGISTRATION_REFERENCE,
  KRAKEN_PROVIDER_REGISTRATION_REFERENCE,
} from '../models/broker-account.model';
import { RiskProfileCatalogEntry } from '../models/risk-profile.model';

@Injectable({ providedIn: 'root' })
export class BrokerAccountService {
  private readonly baseUrl = `${environment.gatewayUrl}v1/broker-accounts`;

  constructor(private readonly http: HttpClient) {}

  list(): Observable<BrokerAccount[]> {
    return this.http.get<BrokerAccount[]>(this.baseUrl);
  }

  provisionTradingAccount(accountId: string): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/${accountId}/provision-account`, {});
  }

  eligibleRiskProfiles(): Observable<RiskProfileCatalogEntry[]> {
    return this.http.get<RiskProfileCatalogEntry[]>(
      `${environment.gatewayUrl}v1/risk-profiles/eligible`,
    );
  }

  createPaper(command: {
    provider: BrokerProvider;
    displayName: string;
    initialCapital: number;
    riskProfile: { profileId: string; semanticVersion: string };
  }): Observable<BrokerAccount> {
    return this.http.post<BrokerAccount>(this.baseUrl, {
      provider: this.providerReference(command.provider),
      displayName: command.displayName,
      executionMode: 'PAPER',
      initialCapital: command.initialCapital,
      riskProfile: command.riskProfile,
    });
  }

  createAndConnect(command: {
    provider: BrokerProvider;
    displayName: string;
    apiKey: string;
    apiSecret: string;
    passphrase?: string;
  }): Observable<CredentialValidation> {
    return this.http
      .post<BrokerAccount>(this.baseUrl, {
        provider: this.providerReference(command.provider),
        displayName: command.displayName,
        executionMode: 'LIVE',
      })
      .pipe(
        switchMap((account) =>
          this.http.post<CredentialValidation>(`${this.baseUrl}/${account.id}/credentials`, {
            apiKey: command.apiKey,
            apiSecret: command.apiSecret,
            passphrase: command.passphrase || undefined,
          }),
        ),
      );
  }

  createCTrader(displayName: string): Observable<BrokerAccount> {
    return this.http.post<BrokerAccount>(this.baseUrl, {
      provider: CTRADER_PROVIDER_REGISTRATION_REFERENCE,
      displayName,
      executionMode: 'LIVE',
    });
  }

  startCTraderAuthorization(accountId: string): Observable<CTraderAuthorization> {
    return this.http.post<CTraderAuthorization>(
      `${this.baseUrl}/${accountId}/ctrader/authorization`,
      {},
    );
  }

  getCTraderAuthorization(accountId: string, attemptId: string): Observable<CTraderAuthorization> {
    return this.http.get<CTraderAuthorization>(
      `${this.baseUrl}/${accountId}/ctrader/authorization/${attemptId}`,
    );
  }

  bindCTraderAccount(
    accountId: string,
    attemptId: string,
    externalAccountId: string,
  ): Observable<CTraderAuthorization> {
    return this.http.post<CTraderAuthorization>(
      `${this.baseUrl}/${accountId}/ctrader/authorization/${attemptId}/binding`,
      { externalAccountId },
    );
  }

  startCTraderMarketDataAuthorization(): Observable<CTraderMarketDataAuthorization> {
    return this.http.get<CTraderMarketDataAuthorization>(
      `${environment.gatewayUrl}v1/ctrader/authorization`,
    );
  }

  getCTraderMarketDataAuthorization(attemptId: string): Observable<CTraderMarketDataAuthorizationState> {
    return this.http.get<CTraderMarketDataAuthorizationState>(
      `${environment.gatewayUrl}v1/ctrader/authorization/${attemptId}`,
    );
  }

  rotate(
    accountId: string,
    credentials: { apiKey: string; apiSecret: string; passphrase?: string },
  ): Observable<CredentialValidation> {
    return this.http.put<CredentialValidation>(
      `${this.baseUrl}/${accountId}/credentials`,
      credentials,
    );
  }

  connectExisting(
    accountId: string,
    credentials: { apiKey: string; apiSecret: string; passphrase?: string },
  ): Observable<CredentialValidation> {
    return this.http.post<CredentialValidation>(
      `${this.baseUrl}/${accountId}/credentials`,
      credentials,
    );
  }

  disconnect(accountId: string): Observable<BrokerAccount> {
    return this.http.post<BrokerAccount>(`${this.baseUrl}/${accountId}/disconnect`, {});
  }

  private providerReference(provider: BrokerProvider): string {
    if (provider === 'KRAKEN') {
      return KRAKEN_PROVIDER_REGISTRATION_REFERENCE;
    }
    if (provider === 'CTRADER') {
      return CTRADER_PROVIDER_REGISTRATION_REFERENCE;
    }
    throw new Error(`Unsupported broker provider: ${provider}`);
  }

  revoke(accountId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${accountId}`);
  }
}

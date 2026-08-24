import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  BrokerAccount,
  BrokerProvider,
  CredentialValidation,
} from '../models/broker-account.model';

@Injectable({ providedIn: 'root' })
export class BrokerAccountService {
  private readonly baseUrl = `${environment.gatewayUrl}v1/broker-accounts`;

  constructor(private readonly http: HttpClient) {}

  list(): Observable<BrokerAccount[]> {
    return this.http.get<BrokerAccount[]>(this.baseUrl);
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
        provider: command.provider,
        displayName: command.displayName,
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

  rotate(
    accountId: string,
    credentials: { apiKey: string; apiSecret: string; passphrase?: string },
  ): Observable<CredentialValidation> {
    return this.http.put<CredentialValidation>(
      `${this.baseUrl}/${accountId}/credentials`,
      credentials,
    );
  }

  disconnect(accountId: string): Observable<BrokerAccount> {
    return this.http.post<BrokerAccount>(`${this.baseUrl}/${accountId}/disconnect`, {});
  }

  revoke(accountId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${accountId}`);
  }
}

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Account, BrokerAccountSynchronizationResult } from '../models/account.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class AccountService {
  constructor(private readonly http: HttpClient) {}
  getAccounts(): Observable<Account[]> {
    return this.http.get<Account[]>(`${environment.gatewayUrl}v1/accounts`);
  }
  getAccount(id: string): Observable<Account> {
    return this.http.get<Account>(`${environment.gatewayUrl}v1/accounts/${id}`);
  }

  synchronizeAccount(brokerAccountId: string): Observable<BrokerAccountSynchronizationResult> {
    return this.http.post<BrokerAccountSynchronizationResult>(
      `${environment.gatewayUrl}v2/accounts/${encodeURIComponent(brokerAccountId)}/synchronize`,
      {},
    );
  }

  synchronize(): Observable<string> {
    return this.http.post(
      `${environment.gatewayUrl}v1/accounts/synchronize`,
      {},
      {
        responseType: 'text',
      },
    );
  }
}

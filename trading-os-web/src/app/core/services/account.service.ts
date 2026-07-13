import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Account } from '../models/account.model'
import { environment} from '../../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class AccountService {
  constructor( private http: HttpClient) {}
  getAccounts(): Observable<Account[]> {
    return this.http.get<Account[]>(`${environment.gatewayUrl}/accounts`);
  }
}

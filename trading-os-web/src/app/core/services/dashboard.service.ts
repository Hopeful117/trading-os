import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DashboardSummary } from '../models/dashboard-summary.model';

@Injectable({
  providedIn: 'root',
})
export class DashboardService {
  constructor(private readonly http: HttpClient) {}

  findDashboard(accountId: string): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(
      `${environment.gatewayUrl}v1/accounts/${accountId}/dashboard`,
    );
  }
}

import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { OpenPositionDashboardView } from '../models/dashboard-summary.model';

@Injectable({
  providedIn: 'root',
})
export class PositionService {
  constructor(private readonly http: HttpClient) {}

  getPositions(accountId: string): Observable<OpenPositionDashboardView[]> {
    return this.http.get<OpenPositionDashboardView[]>(
      `${environment.gatewayUrl}v1/accounts/${accountId}/positions`,
    );
  }
}

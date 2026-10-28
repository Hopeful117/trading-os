import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, defer, expand, map, Observable, of, switchMap, timer } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DashboardSummary } from '../models/dashboard-summary.model';

export interface DashboardPollEvent {
  dashboard: DashboardSummary | null;
  error: unknown | null;
  deferred: boolean;
}

export const DASHBOARD_POLL_INTERVAL_MS = 5_000;
// Keep retries outside cTrader's configured snapshot safety window.
export const DASHBOARD_DEFERRED_BACKOFF_MS = 20 * 60_000;

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

  pollDashboard(accountId: string): Observable<DashboardPollEvent> {
    const request = (): Observable<DashboardPollEvent> =>
      this.findDashboard(accountId).pipe(
        map((dashboard) => ({ dashboard, error: null, deferred: false })),
        // Keep unrelated failures visible to the existing consumers; only the
        // machine-readable broker safety response changes the retry cadence.
        catchError((error: unknown) =>
          of({
            dashboard: null,
            error,
            deferred:
              (error as { status?: number; error?: { code?: string } })?.status === 409 &&
              (error as { error?: { code?: string } })?.error?.code === 'BROKER_SNAPSHOT_DEFERRED',
          }),
        ),
      );

    return defer(request).pipe(
      expand((event) =>
        timer(event.deferred ? DASHBOARD_DEFERRED_BACKOFF_MS : DASHBOARD_POLL_INTERVAL_MS).pipe(
          switchMap(request),
        ),
      ),
    );
  }
}

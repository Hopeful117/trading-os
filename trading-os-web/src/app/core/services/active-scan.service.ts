import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ActiveScanResponse,
  ActiveScanSummary,
  CreateActiveScanRequest,
  ActiveScanScopeResolutionResponse,
  ResolveActiveScanScopeRequest,
} from '../models/active-scan.model';

@Injectable({
  providedIn: 'root',
})
export class ActiveScanService {
  private readonly http = inject(HttpClient);

  createScan(
    request: CreateActiveScanRequest,
    idempotencyKey: string,
  ): Observable<ActiveScanResponse> {
    return this.http.post<ActiveScanResponse>(
      `${environment.gatewayUrl}v1/intelligence/scans`,
      request,
      {
        headers: { 'Idempotency-Key': idempotencyKey },
      },
    );
  }

  resolveScope(
    request: ResolveActiveScanScopeRequest,
  ): Observable<ActiveScanScopeResolutionResponse> {
    return this.http.post<ActiveScanScopeResolutionResponse>(
      `${environment.gatewayUrl}v1/intelligence/scans/scope`,
      request,
    );
  }

  findScan(scanId: string): Observable<ActiveScanResponse> {
    return this.http.get<ActiveScanResponse>(
      `${environment.gatewayUrl}v1/intelligence/scans/${scanId}`,
    );
  }

  findRecent(limit: number = 10): Observable<ActiveScanSummary[]> {
    return this.http.get<ActiveScanSummary[]>(`${environment.gatewayUrl}v1/intelligence/scans`, {
      params: { limit: limit.toString() },
    });
  }
}

import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ActiveScanResponse, CreateActiveScanRequest } from '../models/active-scan.model';

@Injectable({
  providedIn: 'root',
})
export class ActiveScanService {
  private http = inject(HttpClient);

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

  findScan(scanId: string): Observable<ActiveScanResponse> {
    return this.http.get<ActiveScanResponse>(
      `${environment.gatewayUrl}v1/intelligence/scans/${scanId}`,
    );
  }
}

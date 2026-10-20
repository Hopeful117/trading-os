import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ResearchIntegrity, ResearchReadiness } from '../models/research-readiness.model';

@Injectable({
  providedIn: 'root',
})
export class ResearchService {
  private readonly http = inject(HttpClient);

  getReadiness(): Observable<ResearchReadiness> {
    return this.http.get<ResearchReadiness>(
      `${environment.gatewayUrl}v1/intelligence/research/readiness`,
    );
  }

  getIntegrity(): Observable<ResearchIntegrity> {
    return this.http.get<ResearchIntegrity>(
      `${environment.gatewayUrl}v1/intelligence/research/integrity`,
    );
  }
}

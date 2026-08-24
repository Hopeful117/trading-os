import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { OpportunityResponse } from '../models/opportunity.model';

@Injectable({
  providedIn: 'root',
})
export class OpportunityService {
  private http = inject(HttpClient);

  findActive(): Observable<OpportunityResponse[]> {
    return this.http.get<OpportunityResponse[]>(`${environment.gatewayUrl}v1/opportunities/active`);
  }

  findById(id: string): Observable<OpportunityResponse> {
    return this.http.get<OpportunityResponse>(`${environment.gatewayUrl}v1/opportunities/${id}`);
  }
}

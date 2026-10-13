import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ChallengeDefinition,
  ChallengeInstance,
  ChallengeProgress,
  StartChallengeRequest,
} from '../models/challenge.model';

@Injectable({
  providedIn: 'root',
})
export class ChallengeService {
  private readonly http = inject(HttpClient);

  findCatalog(): Observable<ChallengeDefinition[]> {
    return this.http.get<ChallengeDefinition[]>(`${environment.gatewayUrl}v1/challenges`);
  }

  startChallenge(accountId: string, request: StartChallengeRequest): Observable<ChallengeInstance> {
    return this.http.post<ChallengeInstance>(
      `${environment.gatewayUrl}v1/accounts/${encodeURIComponent(accountId)}/challenge`,
      request,
    );
  }

  findProgress(accountId: string): Observable<ChallengeProgress> {
    return this.http.get<ChallengeProgress>(
      `${environment.gatewayUrl}v1/accounts/${encodeURIComponent(accountId)}/challenge`,
    );
  }
}

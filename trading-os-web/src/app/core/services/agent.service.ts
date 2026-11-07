import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  Agent,
  AgentCredential,
  AgentGrant,
  AgentPermission,
  AgentType,
} from '../models/agent.model';

@Injectable({ providedIn: 'root' })
export class AgentService {
  private readonly baseUrl = `${environment.gatewayUrl}v1/agents`;

  constructor(private readonly http: HttpClient) {}

  list(): Observable<Agent[]> {
    return this.http.get<Agent[]>(this.baseUrl);
  }

  create(displayName: string, agentType: AgentType): Observable<Agent> {
    return this.http.post<Agent>(this.baseUrl, { displayName, agentType });
  }

  issueCredential(agentId: string): Observable<AgentCredential> {
    return this.http.post<AgentCredential>(`${this.baseUrl}/${agentId}/credentials`, {});
  }

  connectToOpenCode(agentId: string): Observable<void> {
    const bridgeUrl = environment.mcpBridgeUrl;
    return this.issueCredential(agentId).pipe(
      switchMap(({ credential }) =>
        this.http
          .get<{ challenge: string }>(`${bridgeUrl}/v1/challenge`)
          .pipe(
            switchMap(({ challenge }) =>
              this.http.post<void>(`${bridgeUrl}/v1/connect`, { challenge, credential }),
            ),
          ),
      ),
    );
  }

  suspend(agentId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${agentId}/suspend`, {});
  }

  activate(agentId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${agentId}/activate`, {});
  }

  revoke(agentId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${agentId}/revoke`, {});
  }

  listGrants(agentId: string): Observable<AgentGrant[]> {
    return this.http.get<AgentGrant[]>(`${this.baseUrl}/${agentId}/grants`);
  }

  createGrant(
    agentId: string,
    permission: AgentPermission,
    resourceId: string,
  ): Observable<AgentGrant> {
    return this.http.post<AgentGrant>(`${this.baseUrl}/${agentId}/grants`, {
      permission,
      resourceType: 'BROKER_ACCOUNT',
      resourceId,
    });
  }

  revokeGrant(agentId: string, grantId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${agentId}/grants/${grantId}/revoke`, {});
  }
}

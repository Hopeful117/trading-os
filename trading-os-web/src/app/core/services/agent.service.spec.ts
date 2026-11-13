import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AgentService } from './agent.service';

describe('AgentService', () => {
  let service: AgentService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AgentService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AgentService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('issues a new credential and sends it directly to the loopback bridge', () => {
    service.connectToOpenCode('agent-1').subscribe();

    const credentialRequest = http.expectOne('/api/v1/agents/agent-1/credentials');
    expect(credentialRequest.request.method).toBe('POST');
    credentialRequest.flush({ credential: 'opaque-test-credential' });

    const challengeRequest = http.expectOne('http://127.0.0.1:17891/v1/challenge');
    expect(challengeRequest.request.method).toBe('GET');
    challengeRequest.flush({ challenge: 'one-time-challenge' });

    const connectRequest = http.expectOne('http://127.0.0.1:17891/v1/connect');
    expect(connectRequest.request.body).toEqual({
      challenge: 'one-time-challenge',
      credential: 'opaque-test-credential',
    });
    connectRequest.flush(null);
  });

  it('reconnects through the bridge without issuing a credential', () => {
    service.reconnectToOpenCode().subscribe();

    const request = http.expectOne('http://127.0.0.1:17891/v1/reconnect');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush(null);
    http.expectNone('/api/v1/agents/agent-1/credentials');
  });

  it('uses the gateway contracts for agent lifecycle, grants, and operation approval', () => {
    service.list().subscribe();
    let request = http.expectOne('/api/v1/agents');
    expect(request.request.method).toBe('GET');
    request.flush([]);

    service.create('Research assistant', 'OPENCODE').subscribe();
    request = http.expectOne('/api/v1/agents');
    expect(request.request.body).toEqual({
      displayName: 'Research assistant',
      agentType: 'OPENCODE',
    });
    request.flush({ id: 'agent-1' });

    service.suspend('agent-1').subscribe();
    request = http.expectOne('/api/v1/agents/agent-1/suspend');
    expect(request.request.method).toBe('POST');
    request.flush(null);

    service.activate('agent-1').subscribe();
    request = http.expectOne('/api/v1/agents/agent-1/activate');
    request.flush(null);

    service.revoke('agent-1').subscribe();
    request = http.expectOne('/api/v1/agents/agent-1/revoke');
    request.flush(null);

    service.listGrants('agent-1').subscribe();
    request = http.expectOne('/api/v1/agents/agent-1/grants');
    request.flush([]);

    service.createGrant('agent-1', 'accounts:read', 'BROKER_ACCOUNT', 'broker-1').subscribe();
    request = http.expectOne('/api/v1/agents/agent-1/grants');
    expect(request.request.body).toEqual({
      permission: 'accounts:read',
      resourceType: 'BROKER_ACCOUNT',
      resourceId: 'broker-1',
    });
    request.flush({ id: 'grant-1' });

    service.listResearchScopes().subscribe();
    request = http.expectOne('/api/v1/research-scopes');
    expect(request.request.method).toBe('GET');
    request.flush([]);

    service.createResearchScope('broker-1').subscribe();
    request = http.expectOne('/api/v1/research-scopes');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ brokerAccountId: 'broker-1' });
    request.flush({ id: 'scope-1' });

    service.revokeGrant('agent-1', 'grant-1').subscribe();
    request = http.expectOne('/api/v1/agents/agent-1/grants/grant-1/revoke');
    request.flush(null);

    service.operationRequests().subscribe();
    request = http.expectOne('/api/v1/agent/operation-requests');
    request.flush([]);

    service.approveOperation('operation-1').subscribe();
    request = http.expectOne('/api/v1/agent/operation-requests/operation-1/approve');
    request.flush({ id: 'operation-1' });

    service.rejectOperation('operation-2').subscribe();
    request = http.expectOne('/api/v1/agent/operation-requests/operation-2/reject');
    request.flush({ id: 'operation-2' });
  });
});

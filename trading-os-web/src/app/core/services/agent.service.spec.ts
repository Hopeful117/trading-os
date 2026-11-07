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
});

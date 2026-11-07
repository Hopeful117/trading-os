import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AgentService } from '../../../../core/services/agent.service';
import { BrokerAccountService } from '../../../../core/services/broker-account.service';
import { Agents } from './agents';

describe('Agents', () => {
  let component: Agents;
  let fixture: ComponentFixture<Agents>;
  let agentService: {
    list: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    issueCredential: ReturnType<typeof vi.fn>;
    connectToOpenCode: ReturnType<typeof vi.fn>;
    listGrants: ReturnType<typeof vi.fn>;
    createGrant: ReturnType<typeof vi.fn>;
    suspend: ReturnType<typeof vi.fn>;
    activate: ReturnType<typeof vi.fn>;
    revoke: ReturnType<typeof vi.fn>;
    revokeGrant: ReturnType<typeof vi.fn>;
    operationRequests: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    agentService = {
      list: vi.fn(() => of([])),
      create: vi.fn(),
      issueCredential: vi.fn(),
      connectToOpenCode: vi.fn(),
      listGrants: vi.fn(() => of([])),
      createGrant: vi.fn(),
      suspend: vi.fn(),
      activate: vi.fn(),
      revoke: vi.fn(),
      revokeGrant: vi.fn(),
      operationRequests: vi.fn(() => of([])),
    };

    await TestBed.configureTestingModule({
      imports: [Agents],
      providers: [
        { provide: AgentService, useValue: agentService },
        { provide: BrokerAccountService, useValue: { list: vi.fn(() => of([])) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Agents);
    component = fixture.componentInstance;
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('loads agents and broker accounts on initialization', () => {
    expect(agentService.list).toHaveBeenCalledOnce();
  });

  it('requires a broker account and a display name before provisioning', () => {
    component.createAgent();

    expect(agentService.create).not.toHaveBeenCalled();
    expect(component.form.controls.displayName.touched).toBe(true);
    expect(component.form.controls.brokerAccountId.touched).toBe(true);
  });

  it('creates only the supported read-only grants for the selected broker account', () => {
    component.form.setValue({
      displayName: 'Research assistant',
      agentType: 'OPENCODE',
      brokerAccountId: 'broker-1',
      accountsRead: true,
      positionsRead: true,
    });
    const agent = { id: 'agent-1' };
    agentService.create.mockReturnValue(of(agent));
    agentService.connectToOpenCode.mockReturnValue(of(undefined));
    agentService.createGrant.mockImplementation((_id, permission, resourceId) =>
      of({ permission, resourceId }),
    );

    component.createAgent();

    expect(agentService.createGrant).toHaveBeenCalledTimes(2);
    expect(agentService.createGrant).toHaveBeenNthCalledWith(
      1,
      'agent-1',
      'accounts:read',
      'broker-1',
    );
    expect(agentService.createGrant).toHaveBeenNthCalledWith(
      2,
      'agent-1',
      'positions:read',
      'broker-1',
    );
  });

  it('connects an existing active OpenCode agent without exposing a credential', () => {
    const agent = {
      id: 'agent-1',
      displayName: 'Research assistant',
      agentType: 'OPENCODE',
      status: 'ACTIVE',
    } as never;
    agentService.connectToOpenCode.mockReturnValue(of(undefined));

    component.connectAgent(agent);

    expect(agentService.connectToOpenCode).toHaveBeenCalledWith('agent-1');
    expect(fixture.nativeElement.textContent).not.toContain('credential');
  });
});

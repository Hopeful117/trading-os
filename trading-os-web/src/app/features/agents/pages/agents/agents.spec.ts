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
    reconnectToOpenCode: ReturnType<typeof vi.fn>;
    rotateOpenCodeCredential: ReturnType<typeof vi.fn>;
    recoverLostOpenCodeCredential: ReturnType<typeof vi.fn>;
    listGrants: ReturnType<typeof vi.fn>;
    createGrant: ReturnType<typeof vi.fn>;
    listResearchScopes: ReturnType<typeof vi.fn>;
    createResearchScope: ReturnType<typeof vi.fn>;
    suspend: ReturnType<typeof vi.fn>;
    activate: ReturnType<typeof vi.fn>;
    revoke: ReturnType<typeof vi.fn>;
    revokeGrant: ReturnType<typeof vi.fn>;
    operationRequests: ReturnType<typeof vi.fn>;
    approveOperation: ReturnType<typeof vi.fn>;
    rejectOperation: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    agentService = {
      list: vi.fn(() => of([])),
      create: vi.fn(),
      issueCredential: vi.fn(),
      connectToOpenCode: vi.fn(),
      reconnectToOpenCode: vi.fn(),
      rotateOpenCodeCredential: vi.fn(),
      recoverLostOpenCodeCredential: vi.fn(),
      listGrants: vi.fn(() => of([])),
      createGrant: vi.fn(),
      listResearchScopes: vi.fn(() => of([])),
      createResearchScope: vi.fn(),
      suspend: vi.fn(),
      activate: vi.fn(),
      revoke: vi.fn(),
      revokeGrant: vi.fn(),
      operationRequests: vi.fn(() => of([])),
      approveOperation: vi.fn(),
      rejectOperation: vi.fn(),
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

  it('defaults the operation filter to the pending request status', () => {
    const requests = [
      {
        id: 'operation-1',
        operationType: 'ACCOUNT_SYNCHRONIZATION',
        status: 'PENDING_APPROVAL',
      },
    ];
    agentService.operationRequests.mockReturnValue(of(requests));

    component.reload();

    expect(component.operationStatusFilter()).toBe('PENDING_APPROVAL');
    expect(component.filteredOperationRequests()).toEqual(requests);
  });

  it('exposes every operation status and can display all statuses', () => {
    const requests = [
      {
        id: 'operation-1',
        operationType: 'ACCOUNT_SYNCHRONIZATION',
        status: 'PENDING_APPROVAL',
      },
      {
        id: 'operation-2',
        operationType: 'ACCOUNT_SYNCHRONIZATION',
        status: 'REJECTED',
      },
    ];
    agentService.operationRequests.mockReturnValue(of(requests));

    component.reload();
    component.operationStatusFilter.set('ALL');

    expect(component.operationStatuses).toEqual([
      'PENDING_APPROVAL',
      'APPROVED',
      'EXECUTING',
      'SUCCEEDED',
      'FAILED',
      'OUTCOME_UNKNOWN',
      'REJECTED',
      'EXPIRED',
    ]);
    expect(component.filteredOperationRequests()).toEqual(requests);
  });

  it('requires a display name before provisioning', () => {
    component.createAgent();

    expect(agentService.create).not.toHaveBeenCalled();
    expect(component.form.controls.displayName.touched).toBe(true);
  });

  it('creates only the supported read-only grants for the selected broker account', () => {
    component.form.setValue({
      displayName: 'Research assistant',
      agentType: 'OPENCODE',
      brokerAccountId: 'broker-1',
      accountsRead: true,
      positionsRead: true,
      scansRun: false,
      researchInspect: false,
      researchExport: false,
      plansCreate: false,
      tradePlanRiskEvaluate: false,
      tradePlanApprovalStatusRead: false,
    });
    const agent = { id: 'agent-1' };
    agentService.create.mockReturnValue(of(agent));
    agentService.connectToOpenCode.mockReturnValue(of(undefined));
    agentService.createGrant.mockImplementation((_id, permission, resourceType, resourceId) =>
      of({ permission, resourceType, resourceId }),
    );

    component.createAgent();

    expect(agentService.createGrant).toHaveBeenCalledTimes(2);
    expect(agentService.createGrant).toHaveBeenNthCalledWith(
      1,
      'agent-1',
      'accounts:read',
      'BROKER_ACCOUNT',
      'broker-1',
    );
    expect(agentService.createGrant).toHaveBeenNthCalledWith(
      2,
      'agent-1',
      'positions:read',
      'BROKER_ACCOUNT',
      'broker-1',
    );
  });

  it('adds a research inspection grant to the linked broker account', () => {
    agentService.createGrant.mockReturnValue(of({ id: 'grant-1' }));
    component.grantForm.setValue({
      agentId: 'agent-1',
      permission: 'research:inspect',
    });
    component.grants.set({
      'agent-1': [
        {
          permission: 'accounts:read',
          resourceType: 'BROKER_ACCOUNT',
          resourceId: 'broker-1',
          status: 'ACTIVE',
        } as never,
      ],
    });
    component.researchScopes.set([
      { id: 'scope-1', brokerAccountId: 'broker-1', status: 'ACTIVE' } as never,
    ]);

    component.addGrant();

    expect(agentService.createGrant).toHaveBeenCalledWith(
      'agent-1',
      'research:inspect',
      'BROKER_ACCOUNT',
      'broker-1',
    );
  });

  it('adds trade-plan permissions to the linked broker account', () => {
    agentService.createGrant.mockReturnValue(of({ id: 'grant-1' }));
    component.grantForm.setValue({
      agentId: 'agent-1',
      permission: 'trade-plan-risk:evaluate',
    });
    component.grants.set({
      'agent-1': [
        {
          permission: 'accounts:read',
          resourceType: 'BROKER_ACCOUNT',
          resourceId: 'broker-1',
          status: 'ACTIVE',
        } as never,
      ],
    });

    component.addGrant();

    expect(agentService.createGrant).toHaveBeenCalledWith(
      'agent-1',
      'trade-plan-risk:evaluate',
      'BROKER_ACCOUNT',
      'broker-1',
    );
  });

  it('reconnects an existing active OpenCode agent without issuing a credential', () => {
    const agent = {
      id: 'agent-1',
      displayName: 'Research assistant',
      agentType: 'OPENCODE',
      status: 'ACTIVE',
    } as never;
    agentService.reconnectToOpenCode.mockReturnValue(of(undefined));

    component.reconnectAgent(agent);

    expect(agentService.reconnectToOpenCode).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).not.toContain('opaque-test-credential');
  });

  it('keeps rotation and lost-credential recovery as explicit actions', () => {
    const agent = {
      id: 'agent-1',
      displayName: 'Research assistant',
      agentType: 'OPENCODE',
      status: 'ACTIVE',
    } as never;
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    agentService.rotateOpenCodeCredential.mockReturnValue(of(undefined));
    agentService.recoverLostOpenCodeCredential.mockReturnValue(of(undefined));

    component.rotateAgent(agent);
    expect(agentService.rotateOpenCodeCredential).toHaveBeenCalledWith('agent-1');

    component.recoverAgent(agent);
    expect(agentService.recoverLostOpenCodeCredential).toHaveBeenCalledWith('agent-1');
  });

  it('approves and rejects pending operation requests through the human workflow', () => {
    const request = { id: 'operation-1', status: 'PENDING_APPROVAL' } as never;
    agentService.approveOperation.mockReturnValue(of(request));
    agentService.rejectOperation.mockReturnValue(of(request));

    component.approveOperation(request);
    expect(agentService.approveOperation).toHaveBeenCalledWith('operation-1');
    expect(component.feedback()?.message).toContain('approuvée');

    component.rejectOperation(request);
    expect(agentService.rejectOperation).toHaveBeenCalledWith('operation-1');
    expect(component.feedback()?.message).toContain('rejetée');
  });
});

import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin, Observable, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { BrokerAccount } from '../../../../core/models/broker-account.model';
import {
  Agent,
  AgentGrant,
  AgentOperationRequest,
  AgentOperationStatus,
  AgentPermission,
  AgentType,
  ResearchScope,
} from '../../../../core/models/agent.model';
import { AgentService } from '../../../../core/services/agent.service';
import { BrokerAccountService } from '../../../../core/services/broker-account.service';

type Feedback = { kind: 'success' | 'error'; message: string } | null;
type OperationStatusFilter = AgentOperationStatus | 'ALL';

const OPERATION_STATUSES: AgentOperationStatus[] = [
  'PENDING_APPROVAL',
  'APPROVED',
  'EXECUTING',
  'SUCCEEDED',
  'FAILED',
  'OUTCOME_UNKNOWN',
  'REJECTED',
  'EXPIRED',
];

@Component({
  selector: 'app-agents',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './agents.html',
  styleUrl: './agents.scss',
})
export class Agents {
  private readonly agentService = inject(AgentService);
  private readonly brokerAccountService = inject(BrokerAccountService);

  readonly agents = signal<Agent[]>([]);
  readonly grants = signal<Record<string, AgentGrant[]>>({});
  readonly brokerAccounts = signal<BrokerAccount[]>([]);
  readonly researchScopes = signal<ResearchScope[]>([]);
  readonly operationRequests = signal<AgentOperationRequest[]>([]);
  readonly operationStatusFilter = signal<OperationStatusFilter>('ALL');
  readonly operationStatuses = OPERATION_STATUSES;
  readonly filteredOperationRequests = computed(() => {
    const filter = this.operationStatusFilter();
    return filter === 'ALL'
      ? this.operationRequests()
      : this.operationRequests().filter((request) => request.status === filter);
  });
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly feedback = signal<Feedback>(null);

  readonly form = new FormGroup({
    displayName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(80)],
    }),
    agentType: new FormControl<AgentType>('OPENCODE', { nonNullable: true }),
    brokerAccountId: new FormControl('', { nonNullable: true }),
    accountsRead: new FormControl(true, { nonNullable: true }),
    positionsRead: new FormControl(true, { nonNullable: true }),
    scansRun: new FormControl(false, { nonNullable: true }),
    researchInspect: new FormControl(false, { nonNullable: true }),
    researchExport: new FormControl(false, { nonNullable: true }),
    plansCreate: new FormControl(false, { nonNullable: true }),
    tradePlanRiskEvaluate: new FormControl(false, { nonNullable: true }),
    tradePlanApprovalStatusRead: new FormControl(false, { nonNullable: true }),
  });

  readonly grantForm = new FormGroup({
    agentId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    permission: new FormControl<AgentPermission>('research:inspect', { nonNullable: true }),
  });

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    forkJoin({
      agents: this.agentService.list(),
      brokerAccounts: this.brokerAccountService.list(),
      researchScopes: this.agentService.listResearchScopes(),
      operationRequests: this.agentService.operationRequests(),
    }).subscribe({
      next: ({ agents, brokerAccounts, researchScopes, operationRequests }) => {
        this.agents.set(agents);
        this.brokerAccounts.set(brokerAccounts);
        this.researchScopes.set(researchScopes);
        this.operationRequests.set(operationRequests);
        this.setDefaultOperationStatusFilter(operationRequests);
        this.loadGrants(agents);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.feedback.set({
          kind: 'error',
          message: 'Les agents ou les comptes broker sont indisponibles.',
        });
      },
    });
  }

  setDefaultOperationStatusFilter(requests: AgentOperationRequest[]): void {
    const pendingRequest = requests.find((request) => request.status === 'PENDING_APPROVAL');
    this.operationStatusFilter.set(pendingRequest?.status ?? 'ALL');
  }

  createAgent(): void {
    if (this.busy() || this.form.controls.displayName.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const permissions: AgentPermission[] = [
      ...(value.accountsRead ? ['accounts:read' as const] : []),
      ...(value.positionsRead ? ['positions:read' as const] : []),
      ...(value.scansRun ? ['scans:run' as const] : []),
      ...(value.researchInspect ? ['research:inspect' as const] : []),
      ...(value.researchExport ? ['research:export' as const] : []),
      ...(value.plansCreate ? ['plans:create' as const] : []),
      ...(value.tradePlanRiskEvaluate ? ['trade-plan-risk:evaluate' as const] : []),
      ...(value.tradePlanApprovalStatusRead ? ['trade-plan-approval:status:read' as const] : []),
    ];
    if (permissions.length === 0) {
      this.feedback.set({
        kind: 'error',
        message: 'Sélectionnez au moins une permission.',
      });
      return;
    }
    const needsResearchScope = permissions.includes('research:export');

    if (permissions.length > 0 && !value.brokerAccountId) {
      this.form.controls.brokerAccountId.markAsTouched();
      this.feedback.set({
        kind: 'error',
        message: 'Sélectionnez un compte broker lié à cet agent.',
      });
      return;
    }

    this.busy.set(true);
    this.feedback.set(null);
    this.agentService
      .create(value.displayName.trim(), value.agentType)
      .pipe(
        switchMap((agent) =>
          this.ensureResearchScope(value.brokerAccountId, needsResearchScope).pipe(
            switchMap((scope) =>
              forkJoin({
                agent: of(agent),
                grants: forkJoin(
                  permissions.map((permission) => {
                    const resourceType = this.resourceTypeFor(permission);
                    return this.agentService.createGrant(
                      agent.id,
                      permission,
                      resourceType,
                      resourceType === 'RESEARCH_SCOPE' ? scope!.id : value.brokerAccountId,
                    );
                  }),
                ),
              }),
            ),
          ),
        ),
      )
      .subscribe({
        next: ({ agent }) => {
          this.form.reset({
            displayName: '',
            agentType: 'OPENCODE',
            brokerAccountId: value.brokerAccountId,
            accountsRead: true,
            positionsRead: true,
            scansRun: false,
            researchInspect: false,
            researchExport: false,
            plansCreate: false,
            tradePlanRiskEvaluate: false,
            tradePlanApprovalStatusRead: false,
          });
          this.agentService.connectToOpenCode(agent.id).subscribe({
            next: () => {
              this.busy.set(false);
              this.feedback.set({
                kind: 'success',
                message: `Agent ${agent.displayName} créé et connecté à OpenCode.`,
              });
              this.reload();
            },
            error: () => {
              this.busy.set(false);
              this.feedback.set({
                kind: 'error',
                message:
                  'Agent créé, mais le bridge OpenCode est indisponible. Redémarrez OpenCode puis émettez un nouveau credential.',
              });
              this.reload();
            },
          });
        },
        error: () => {
          this.busy.set(false);
          this.feedback.set({ kind: 'error', message: 'La création de l’agent n’a pas abouti.' });
          this.reload();
        },
      });
  }

  connectAgent(agent: Agent): void {
    if (this.busy() || agent.status !== 'ACTIVE' || agent.agentType !== 'OPENCODE') return;
    this.busy.set(true);
    this.feedback.set(null);
    this.agentService.connectToOpenCode(agent.id).subscribe({
      next: () => this.finishAction(`Agent ${agent.displayName} connecté à OpenCode.`),
      error: () =>
        this.failAction(
          'La récupération OpenCode a échoué. La reconnexion automatique reste fail-closed; utilisez cette action uniquement pour une rotation ou une récupération explicite.',
        ),
    });
  }

  toggleStatus(agent: Agent): void {
    if (this.busy() || agent.status === 'REVOKED') return;
    this.busy.set(true);
    const operation =
      agent.status === 'ACTIVE'
        ? this.agentService.suspend(agent.id)
        : this.agentService.activate(agent.id);
    operation.subscribe({
      next: () => this.finishAction('Statut de l’agent mis à jour.'),
      error: () => this.failAction('La mise à jour du statut a échoué.'),
    });
  }

  revokeAgent(agent: Agent): void {
    if (this.busy() || agent.status === 'REVOKED') return;
    this.busy.set(true);
    this.agentService.revoke(agent.id).subscribe({
      next: () => this.finishAction('Agent révoqué.'),
      error: () => this.failAction('La révocation de l’agent a échoué.'),
    });
  }

  revokeGrant(agentId: string, grantId: string): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.agentService.revokeGrant(agentId, grantId).subscribe({
      next: () => this.finishAction('Grant révoqué.'),
      error: () => this.failAction('La révocation du grant a échoué.'),
    });
  }

  addGrant(): void {
    if (this.busy() || this.grantForm.invalid) {
      this.grantForm.markAllAsTouched();
      return;
    }
    const value = this.grantForm.getRawValue();
    const resourceType = this.resourceTypeFor(value.permission);
    const brokerAccountId = this.linkedBrokerAccountId(value.agentId);
    if (!brokerAccountId) {
      this.feedback.set({
        kind: 'error',
        message: 'Aucun compte broker n’est lié à cet agent.',
      });
      return;
    }
    this.busy.set(true);
    this.feedback.set(null);
    this.ensureResearchScope(brokerAccountId, resourceType === 'RESEARCH_SCOPE')
      .pipe(
        switchMap((scope) =>
          this.agentService.createGrant(
            agentIdOrThrow(value.agentId),
            value.permission,
            resourceType,
            resourceType === 'RESEARCH_SCOPE' ? scope!.id : brokerAccountId,
          ),
        ),
      )
      .subscribe({
        next: () => {
          this.grantForm.reset({
            agentId: value.agentId,
            permission: 'research:inspect',
          });
          this.finishAction('Permission ajoutée à l’agent.');
        },
        error: () => this.failAction('L’ajout de la permission a échoué.'),
      });
  }

  resourceTypeFor(permission: AgentPermission): 'BROKER_ACCOUNT' | 'RESEARCH_SCOPE' {
    return permission === 'research:export' ? 'RESEARCH_SCOPE' : 'BROKER_ACCOUNT';
  }

  approveOperation(request: AgentOperationRequest): void {
    if (this.busy() || request.status !== 'PENDING_APPROVAL') return;
    this.busy.set(true);
    this.agentService.approveOperation(request.id).subscribe({
      next: () => this.finishAction('Opération approuvée. L’agent doit encore la déclencher.'),
      error: () => this.failAction('L’approbation de l’opération a échoué.'),
    });
  }

  rejectOperation(request: AgentOperationRequest): void {
    if (this.busy() || request.status !== 'PENDING_APPROVAL') return;
    this.busy.set(true);
    this.agentService.rejectOperation(request.id).subscribe({
      next: () => this.finishAction('Opération rejetée.'),
      error: () => this.failAction('Le rejet de l’opération a échoué.'),
    });
  }

  accountLabel(accountId: string): string {
    const account = this.brokerAccounts().find((item) => item.id === accountId);
    return account ? `${account.displayName} · ${account.provider}` : accountId;
  }

  grantLabel(grant: AgentGrant): string {
    if (grant.resourceType === 'RESEARCH_SCOPE') {
      const scope = this.researchScopes().find((item) => item.id === grant.resourceId);
      return `${grant.permission} · univers de ${this.accountLabel(scope?.brokerAccountId ?? grant.resourceId)}`;
    }
    return `${grant.permission} · ${this.accountLabel(grant.resourceId)}`;
  }

  permissions(grants: AgentGrant[]): string {
    return grants
      .filter((grant) => grant.status === 'ACTIVE')
      .map((grant) => grant.permission)
      .join(', ');
  }

  activeGrants(grants: AgentGrant[]): AgentGrant[] {
    return grants.filter((grant) => grant.status === 'ACTIVE');
  }

  private loadGrants(agents: Agent[]): void {
    if (agents.length === 0) {
      this.grants.set({});
      return;
    }
    forkJoin(agents.map((agent) => this.agentService.listGrants(agent.id))).subscribe({
      next: (grantLists) =>
        this.grants.set(
          Object.fromEntries(agents.map((agent, index) => [agent.id, grantLists[index]])),
        ),
      error: () =>
        this.feedback.set({ kind: 'error', message: 'Les grants des agents sont indisponibles.' }),
    });
  }

  private finishAction(message: string): void {
    this.busy.set(false);
    this.feedback.set({ kind: 'success', message });
    this.reload();
  }

  private failAction(message: string): void {
    this.busy.set(false);
    this.feedback.set({ kind: 'error', message });
  }

  private linkedBrokerAccountId(agentId: string): string | null {
    return (
      this.grants()[agentId]?.find(
        (grant) =>
          grant.status === 'ACTIVE' &&
          (grant.permission === 'accounts:read' || grant.permission === 'positions:read') &&
          grant.resourceType === 'BROKER_ACCOUNT',
      )?.resourceId ?? null
    );
  }

  private ensureResearchScope(
    brokerAccountId: string,
    required: boolean,
  ): Observable<ResearchScope | null> {
    if (!required) {
      return of(null);
    }
    const existing = this.researchScopes().find(
      (scope) => scope.status === 'ACTIVE' && scope.brokerAccountId === brokerAccountId,
    );
    return existing ? of(existing) : this.agentService.createResearchScope(brokerAccountId);
  }
}

function agentIdOrThrow(agentId: string): string {
  if (!agentId) {
    throw new Error('An agent is required');
  }
  return agentId;
}

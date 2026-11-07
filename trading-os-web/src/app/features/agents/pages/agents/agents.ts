import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { BrokerAccount } from '../../../../core/models/broker-account.model';
import { Agent, AgentGrant, AgentPermission, AgentType } from '../../../../core/models/agent.model';
import { AgentService } from '../../../../core/services/agent.service';
import { BrokerAccountService } from '../../../../core/services/broker-account.service';

type Feedback = { kind: 'success' | 'error'; message: string } | null;

@Component({
  selector: 'app-agents',
  imports: [ReactiveFormsModule],
  templateUrl: './agents.html',
  styleUrl: './agents.scss',
})
export class Agents {
  private readonly agentService = inject(AgentService);
  private readonly brokerAccountService = inject(BrokerAccountService);

  readonly agents = signal<Agent[]>([]);
  readonly grants = signal<Record<string, AgentGrant[]>>({});
  readonly brokerAccounts = signal<BrokerAccount[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly feedback = signal<Feedback>(null);

  readonly form = new FormGroup({
    displayName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(80)],
    }),
    agentType: new FormControl<AgentType>('OPENCODE', { nonNullable: true }),
    brokerAccountId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    accountsRead: new FormControl(true, { nonNullable: true }),
    positionsRead: new FormControl(true, { nonNullable: true }),
  });

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    forkJoin({
      agents: this.agentService.list(),
      brokerAccounts: this.brokerAccountService.list(),
    }).subscribe({
      next: ({ agents, brokerAccounts }) => {
        this.agents.set(agents);
        this.brokerAccounts.set(brokerAccounts);
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

  createAgent(): void {
    if (this.busy() || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const permissions: AgentPermission[] = [
      ...(value.accountsRead ? ['accounts:read' as const] : []),
      ...(value.positionsRead ? ['positions:read' as const] : []),
    ];
    if (permissions.length === 0) {
      this.feedback.set({
        kind: 'error',
        message: 'Sélectionnez au moins une permission read-only.',
      });
      return;
    }

    this.busy.set(true);
    this.feedback.set(null);
    this.agentService
      .create(value.displayName.trim(), value.agentType)
      .pipe(
        switchMap((agent) =>
          forkJoin({
            agent: of(agent),
            grants: forkJoin(
              permissions.map((permission) =>
                this.agentService.createGrant(agent.id, permission, value.brokerAccountId),
              ),
            ),
          }),
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
          'La connexion OpenCode a échoué. Aucun credential n’a été affiché ni modifié dans l’interface.',
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

  accountLabel(accountId: string): string {
    const account = this.brokerAccounts().find((item) => item.id === accountId);
    return account ? `${account.displayName} · ${account.provider}` : accountId;
  }

  permissions(grants: AgentGrant[]): string {
    return grants
      .filter((grant) => grant.status === 'ACTIVE')
      .map((grant) => grant.permission)
      .join(', ');
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
}

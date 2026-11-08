# Story 0219 - Authorize Agent Research End-to-End

## Metadata

**ID:** `0219`
**Title:** Authorize Agent Research End-to-End
**Status:** Repository Analysis pending human approval
**Priority:** P1 - Agent Platform Security
**Predecessors:** Stories `0214`, `0215`, `0216`, `0217`, `0218`
**Related ADRs:** `ADR-044`, `ADR-088`, `ADR-089`, `ADR-090`

## Goal

Allow an authenticated and explicitly authorized external agent to initiate,
monitor and retrieve the existing bounded Trading OS research workflow through
the MCP adapter and Gateway, without changing research ownership, deterministic
execution authority or report advisory semantics.

## Scope

- Reuse the existing agent credential exchange, short-lived agent JWT and
  `AgentPrincipal` model.
- Define and enforce an exact, validated research resource scope for
  `research:run`.
- Preserve human research access and `ResearchApplicationService` lifecycle,
  idempotency, budgets and persistence.
- Allow authorized agent start, status and report retrieval through public
  Gateway contracts.
- Preserve agent/owner/resource provenance and audit correlation.
- Keep the MCP adapter a thin Gateway-backed anti-corruption layer.
- Add focused Gateway, Market Intelligence, Trading Core and MCP security tests,
  including an isolated end-to-end authorization path.

## Explicit Non-Goals

- No new generic agent framework or authorization server.
- No broker synchronization, order execution, position mutation or Risk bypass.
- No direct MCP access to Market Intelligence internals, AI Engine, providers,
  repositories or databases.
- No changes to adaptive research algorithms or research budgets unless required
  to carry authorization provenance.
- No paid AI calls, live brokers, shared runtime database writes, deployment,
  commit, push or merge.

## Security Invariants

- Agent identity originates only from validated agent authentication.
- Owner identity is resolved server-side from persisted `AgentIdentity`.
- A matching active, unexpired `research:run` grant is required before start.
- The grant target is an exact validated resource, never a wildcard or owner-wide
  implicit scope.
- Status and report reads verify the same agent, owner, scope and grant policy.
- Human principals retain the existing ownership path.
- Agent principals cannot satisfy human-only management or service-only routes.
- Client-controlled owner, actor, agent and delegation headers are not trusted.
- Grant expiry/revocation fails closed for new and read operations according to
  the approved lifecycle policy.
- Research remains advisory and cannot authorize a TradePlan, Risk decision or
  broker operation.

## Required Decision

The repository currently has no stable `ResearchScope` aggregate or validator.
Story 0215 explicitly rejects `RESEARCH_SCOPE` grants until such a contract
exists. R1 must either:

1. approve the smallest durable owner-scoped research-scope resource and its
   client-visible resolution contract; or
2. defer R1 until an existing stable scope contract is introduced.

An agent ID, owner ID, fixed UUID or unvalidated request value must not be used as
a substitute for a research scope.

## Acceptance Criteria

- [ ] Existing agent authentication and token validation are reused.
- [ ] An active exact research grant is required for agent research start.
- [ ] Human research start/status/report behavior remains functional.
- [ ] Agent ownership and resource scope are checked on start, status and report.
- [ ] Cross-owner, wrong-scope, missing, expired and revoked grants are denied.
- [ ] Agent identity, owner, grant/scope and research execution are traceable.
- [ ] Existing idempotency and bounded research lifecycle semantics remain intact.
- [ ] MCP tools work through the Gateway with agent credentials and no business
      authorization logic in Python.
- [ ] No broker, execution, Risk or model-training authority is introduced.
- [ ] Security regression and isolated end-to-end tests pass.
- [ ] Documentation reflects the implemented authorization contract.

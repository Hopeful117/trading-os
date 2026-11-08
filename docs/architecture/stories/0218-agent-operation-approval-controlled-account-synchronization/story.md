# Story 0218 - Agent Operation Approval and Controlled Account Synchronization

## Metadata

**ID:** `0218`
**Title:** Agent Operation Approval and Controlled Account Synchronization
**Status:** Approved - implementation completed; validation partial
**Priority:** P1 - Agent Platform Security
**Predecessors:** Stories `0214`, `0215`, `0216`, `0217`
**Related ADRs:** `ADR-044`, `ADR-073`, `ADR-074`, `ADR-088`, `ADR-089`, `ADR-090`

## Goal

Introduce a secure, auditable and human-approved command workflow for sensitive
agent operations. The first supported operation is
`ACCOUNT_SYNCHRONIZATION`. The design must be extensible to other sensitive
operations, but no other operation type becomes executable in this Story.

An `AgentGrant` authorizes an agent to request an operation. A single-use
operation approval authorizes one specific execution attempt. Existing
deterministic Trading Core and Broker Service safety rules remain authoritative.

## Scope

- Add an agent-authenticated request command for account synchronization.
- Require an active, exact-resource `accounts:sync` AgentGrant.
- Persist a short-lived operation request and its human decision.
- Add a human-only approval and rejection workflow owned by the account owner.
- Keep approval and execution as separate actions.
- Add an agent-authenticated execution command for one approved request.
- Atomically claim an approval before dispatching the existing synchronization
  orchestration.
- Reuse the existing Trading Core to Broker Service service-JWT boundary and
  Broker Service cTrader synchronization path.
- Preserve cooldown, lease, budget, snapshot, freshness, completeness,
  ownership and credential safety controls.
- Add durable lifecycle audit events without credentials or tokens.
- Extend the existing Agents UI with a minimal approval and outcome view.
- Add narrowly scoped MCP request, status and execute tools.
- Add focused authorization, lifecycle, concurrency, integration and regression
  tests.
- Update MCP and Story documentation according to repository conventions.

## Explicit Non-Goals

- No permanent, recurring, standing or wildcard operation approvals.
- No operation type other than `ACCOUNT_SYNCHRONIZATION` is executable.
- No order placement, modification, cancellation or position management.
- No agent-initiated trading execution.
- No automatic execution when a human approves a request.
- No automatic retry after an uncertain broker outcome.
- No generic workflow engine, event broker, scheduler or notification platform.
- No new broker integration, credential system or agent runtime.
- No live cTrader synchronization during implementation or testing.
- No deployment, service restart, shared runtime migration or production change.
- No Git commit, push or merge by the Story implementation.

## Trust and Authorization Model

Every execution requires all three layers:

1. An active `accounts:sync` AgentGrant for the exact BrokerAccount.
2. A valid, unexpired human approval for the exact request.
3. Successful deterministic Trading Core and Broker Service safety checks.

The server derives agent and owner identities from validated principals. Client
headers, payload owner identifiers and agent identifiers are never authoritative.
Agents cannot approve or reject their own requests. Service JWTs cannot approve
operations. Human approval does not bypass ownership, grant, cooldown, lease,
snapshot, freshness, completeness or broker safety rules.

## Domain and Lifecycle

The persisted model must represent, at minimum:

- Request identifier.
- Agent identity and owner identity.
- Operation type and target resource type.
- Exact target resource identifier.
- Creation and expiration timestamps.
- Lifecycle status and request correlation identifier.
- Human decision timestamp and deciding human identity.
- Execution outcome and uncertainty when applicable.

The implementation may use different class and table names, but must provide an
explicit finite-state lifecycle. The expected states are:

```text
PENDING_APPROVAL -> APPROVED -> EXECUTING -> SUCCEEDED
PENDING_APPROVAL -> REJECTED
PENDING_APPROVAL -> EXPIRED
APPROVED -> FAILED
APPROVED -> OUTCOME_UNKNOWN
EXECUTING -> OUTCOME_UNKNOWN
```

Terminal states cannot be reopened. Rejected and expired requests cannot be
executed. Approval cannot be transferred to another agent, owner, account or
operation. Repeated execution requests must not dispatch a second broker call.

Approval expiration is enforced by trusted backend time with a short,
configurable default. Frontend timers are informational only.

The transition from approved to executing must use database-backed atomic
compare-and-set or equivalent transactional locking. In-memory locks are not
sufficient.

## Request and Approval API

The final paths must follow existing repository conventions. The conceptual
agent request contract is:

```http
POST /api/v1/agent/operation-requests
```

```json
{
  "operationType": "ACCOUNT_SYNCHRONIZATION",
  "resourceType": "BROKER_ACCOUNT",
  "resourceId": "<broker-account-uuid>"
}
```

Request creation validates agent lifecycle, credential validity, exact grant,
resource ownership, supported operation type and conflicting active requests.
It must not call Broker Service, acquire a synchronization lease or modify
broker safety state.

Human-only operations must list, read, approve and reject requests belonging to
the authenticated account owner. Approval and rejection revalidate agent
lifecycle, grant, ownership, scope, pending status and expiry.

Execution must be agent-authenticated, reference one request identifier and
revalidate agent, credential, grant, ownership, request ownership, operation,
approval, expiry and requesting-agent identity immediately before atomic claim.

All REST controllers use `ResponseEntity` consistently with the repository.

## External Execution Safety

Do not hold a database transaction open across a slow broker call. Persist the
execution claim before dispatch. A timeout or caller disconnect must not release
the claim or trigger an automatic retry. If broker outcome is uncertain, retain
an explicit unresolved outcome and require reconciliation rather than guessing
failure or repeating the synchronization.

Crash handling must be safe for interruption after approval, before dispatch,
during dispatch, after timeout and during Trading Core restart. At most one
execution claim may dispatch the external synchronization request.

The existing account synchronization application service must be reused. Do not
route an internal application call through a public controller or create a
second synchronization implementation.

## MCP and Gateway

Extend the existing local MCP adapter with narrowly scoped tools equivalent to:

- `request_account_synchronization`
- `get_agent_operation_status`
- `execute_approved_agent_operation`

Names and contracts must follow current MCP conventions. The request tool only
creates a pending request. The status tool exposes only requests authorized for
the authenticated agent and owner context. The execute tool performs no replay
after `401`, timeout or network failure.

Preserve `TRADING_OS_AGENT_CREDENTIAL`, short-lived in-memory JWT caching,
GET-only reauthentication after `401`, no human JWT fallback and no
client-controlled delegated actor.

Gateway routing and filters must distinguish human, agent and internal service
principals. Human approval endpoints reject agent principals. Internal Broker
Service endpoints remain service-authenticated and default deny remains active.

## Human Approval UI

Extend the existing Agents UI. The user must be able to see:

- Requesting agent.
- Requested operation.
- Target broker account.
- Creation and expiration timestamps.
- Current lifecycle status.
- Approve and reject actions.
- Execution result and uncertainty.

The UI must distinguish permission to request, human approval and actual broker
execution. Approval must not be presented as a guarantee of synchronization
success. Credentials, tokens and unnecessary broker secrets must not be shown.

## Audit Requirements

Persist significant lifecycle events for request creation, policy rejection,
human approval, human rejection, expiry, execution claim, broker invocation,
completion, failure and uncertain outcome.

Each event contains request ID, agent ID, owner ID, operation type, resource ID,
timestamp, correlation ID and decision or outcome. Raw credentials, JWTs and
broker credentials are prohibited from audit payloads.

Reuse existing audit infrastructure where possible. Denied or failed operations
must not disappear silently across transaction boundaries.

## Safety Restrictions

- Do not synchronize a live or demo cTrader account during this Story.
- Do not call the cTrader synchronization endpoint.
- Do not place, modify or cancel orders.
- Do not modify positions, cooldowns, leases or broker safety records.
- Do not provision grants or approvals in shared runtime data.
- Do not use, extract or request a human JWT.
- Do not expose credentials or access tokens in logs.
- Do not hardcode the reference account identifiers into authorization logic.

Reference validation account only:

- Internal BrokerAccount: `7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456`
- External cTrader account: `49411967`

## Acceptance Criteria

- [ ] An agent with an exact active `accounts:sync` grant can create a pending
      account-synchronization request without invoking Broker Service.
- [ ] Agents without `accounts:sync`, with an ungranted account or with another
      owner's account are denied.
- [ ] Human account owners can list, inspect, approve and reject their own
      pending requests through human-only APIs and the Agents UI.
- [ ] Agents, service principals and unrelated users cannot approve or reject.
- [ ] Approval is short-lived, request-specific, account-specific,
      operation-specific and non-transferable.
- [ ] Approval and execution remain separate actions.
- [ ] Execution requires current agent, credential, grant, owner, scope, status
      and expiry validation.
- [ ] Approved-to-executing consumption is atomic and single-use under
      concurrent requests.
- [ ] Duplicate requests cannot dispatch duplicate broker synchronization calls.
- [ ] Uncertain broker outcomes are preserved without unsafe automatic retry.
- [ ] Existing Broker Service cooldown, lease, budget, snapshot, freshness,
      completeness and ownership checks remain authoritative.
- [ ] Only `ACCOUNT_SYNCHRONIZATION` is executable in V1.
- [ ] MCP request, status and execute tools preserve agent authentication and
      read/write retry boundaries.
- [ ] Lifecycle audit events are durable, traceable and secret-free.
- [ ] PostgreSQL concurrency behavior is covered by integration tests.
- [ ] No order, position, cTrader synchronization or broker safety mutation is
      performed by tests or validation.
- [ ] Relevant Java, Python, frontend and `git diff --check` validations pass.
- [ ] Documentation and runtime validation plan are updated.

## Validation Expectations

- Trading Core agent authorization, operation lifecycle and API tests.
- PostgreSQL persistence, constraints and concurrent execution-claim tests.
- Gateway principal and route security tests.
- Broker Service delegation and synchronization safety regression tests.
- MCP adapter schema, authentication and duplicate-call tests.
- Angular Agents UI approval and status tests.
- Existing account synchronization regression tests.
- Full Trading Core suite with a realistic timeout.
- Applicable module test suites and `git diff --check`.
- No live cTrader or shared runtime execution.

## Definition of Done

- [ ] Repository Analysis reviewed.
- [ ] Architecture and security design reviewed.
- [ ] Implementation completed within this scope.
- [ ] Required migrations and isolated integration tests completed.
- [ ] Documentation reconciled.
- [ ] Implementation report completed.
- [ ] No commit, push, merge, deployment or shared runtime migration performed
      by this Story implementation.

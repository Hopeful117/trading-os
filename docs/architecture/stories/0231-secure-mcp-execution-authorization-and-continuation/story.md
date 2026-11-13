# Story 0231 - Secure MCP Execution Authorization and Continuation

## Metadata

**ID:** `0231`
**Title:** Secure MCP Execution Authorization and Continuation
**Status:** Proposed - implementation approval required
**Priority:** P0 - Trading Workflow Evolution
**Related ADRs:** `ADR-029`, `ADR-041`, `ADR-044`, `ADR-048`, `ADR-088`, `ADR-090`, `ADR-092`, `ADR-098`

## Product Goal

Allow OpenCode/MCP to initiate a strong human authorization flow for one exact
TradePlan execution and, after human approval, continue that exact operation
through Trading Core's existing `ExecutionIntent`, T1 Risk, Execution Domain,
Broker Service and reconciliation path.

The target account is the existing `FTMO Free cTrader` account and remains
`ExecutionMode.LIVE`. This Story must not create a PAPER or DEMO execution
environment.

## User Scenario

```text
Human: Prepare the EUR/USD long trade based on this opportunity.

OpenCode: The immutable TradePlan is ready. T0 Risk is approved. The exact
account, market, quantity, entry, stop-loss and take-profit are shown. Should I
request human execution authorization?

Human: Yes, request authorization.

OpenCode: Review and confirm this exact order using the secure confirmation step:
<trusted confirmation URL>

Human: Approves the exact operation in the authenticated confirmation surface.

OpenCode: Authorization is confirmed for this exact operation. I can continue
the approved execution.

OpenCode: Executes the continuation through MCP.

OpenCode: Reports the authoritative Execution Domain state, including UNKNOWN or
reconciliation-required states when applicable.
```

Conversational agreement alone never authorizes a financial command.

## Scope

### Human authorization

- Add a server-owned exact execution authorization lifecycle.
- Generate a short-lived opaque confirmation URL initiated by an MCP request.
- Add a focused human confirmation surface launched from that URL.
- Require a human-authenticated principal for approve/reject/revoke actions.
- Bind human decision to a complete server-generated execution fingerprint.
- Enforce expiry, single-use consumption, optimistic concurrency and audit.

### MCP and Gateway

- Add public Gateway contracts for authorization request/status/continuation and
  execution status.
- Add bounded MCP mappings:
  - `request_trade_execution_authorization`;
  - `get_trade_execution_authorization_status`;
  - `execute_human_authorized_trade`;
  - `get_trade_execution_status`.
- Preserve local stdio, agent token exchange, HTTPS policy, bounded responses and
  Gateway-only access.
- Return durable identifiers and sanitized status projections, never broker
  credentials or reusable approval secrets.

### Trading Core continuation

- Resolve agent owner, exact broker-account grant and operation permission.
- Load the authoritative TradePlan, Risk evaluation and execution parameters.
- Calculate and validate the exact fingerprint server-side.
- Atomically claim/consume the authorization and create or reuse one
  `ExecutionIntent` by immutable idempotency key.
- Invoke the existing `ExecuteTradeService` and its T1 Risk/Execution pipeline.
- Expose execution state and reconciliation information through an
  agent-scoped read contract.

### Permission model

Add only narrowly scoped, resource-bound permissions after security review:

- `executions:request` - request human authorization; no broker side effect;
- `executions:continue-approved` - continue one approved exact authorization;
- `executions:status:read` - read the exact authorized execution state.

These permissions never become standing order authority. No `orders:execute`
permission is introduced.

## Non-Goals

- No direct MCP-to-Broker Service or MCP-to-cTrader route.
- No new execution engine or duplicate idempotency model.
- No autonomous trading or approval inferred from chat.
- No standing agent order-execution permission.
- No modification, cancellation or position-close capability in this Story.
- No change to deterministic Risk rules or T1 semantics.
- No change to the Research milestone, research scopes or AI authority.
- No PAPER/DEMO execution environment.
- No broker configuration, credential change or grant administration by agents.
- No runtime order submission during implementation or automated validation.
- No deployment or restart.

## Dependencies

- Accepted ADR-098 and its narrow supersession of ADR-090 for exact human-approved
  MCP execution continuation.
- Explicit supersession or amendment of ADR-090 before enabling execution
  continuation.
- Existing agent identity, token exchange and resource-scoped grant model from
  Stories 0214-0217.
- Existing `TradePlanApprovalService` and human authentication infrastructure.
- Existing `ValidateAndCreateService` and authoritative TradePlan parameter
  derivation.
- Existing `ExecutionIntent`, `ExecuteTradeService`, T1 Risk and reconciliation.
- Existing Gateway routing and MCP adapter.
- Approved step-up policy: password reauthentication on the existing human
  session for every LIVE execution authorization.
- Approved authorization policy: five-minute TTL, revocable before consumption,
  and fail-closed audit persistence for approval and consumption.

## Affected Modules

| Module | Expected role |
|---|---|
| `trading-core` | Execution authorization aggregate, human/agent controllers, fingerprint, atomic consume, permission enforcement and execution status projection |
| `risk-domain` | No business-rule change; existing deterministic Risk engine is reused |
| `gateway` | Route new public agent/human authorization and execution-status contracts |
| `mcp-adapter` | Thin typed Gateway mappings and bounded error/status handling |
| `trading-os-web` | Focused human confirmation page launched from OpenCode; no full workflow rewrite |
| `broker-service` | No execution contract change unless approved audit delegation extension is necessary |
| `ctrader` adapter | No provider logic change in this Story unless a contract gap is proven |
| `docs/architecture/adr` | ADR-098 is accepted; ADR-090 remains historical for its retained identity/grant decisions |

## Domain Changes

### New or extended authorization model

Prefer a new `ExecutionAuthorization` aggregate rather than changing the
semantics of ordinary `TradePlanApprovalChallenge`.

It must preserve:

- human user ID;
- agent ID and grant/version;
- broker/trading account IDs;
- provider identity and canonical market/product identity;
- TradePlan ID/version;
- Risk evaluation ID and decision;
- direction, order type, quantity, price constraints and protections;
- execution idempotency key;
- immutable request fingerprint;
- creation/expiry/decision/consumption timestamps;
- human decision identity;
- execution intent linkage;
- lifecycle and optimistic version.

### Lifecycle

```text
REQUESTED
  -> PENDING_HUMAN_CONFIRMATION
  -> APPROVED | REJECTED | EXPIRED | REVOKED
  -> CONSUMING
  -> CONSUMED
  -> execution status observed through linked ExecutionIntent
```

`CONSUMING` must be protected against concurrent claims. A failed database
transaction before durable claim completion remains retryable with the same
fingerprint and idempotency key. An ambiguous broker outcome is represented by
the existing Execution Domain `UNKNOWN` state and is reconciled, never blindly
resubmitted.

## API and MCP Contracts

### Proposed Gateway contracts

```text
POST /api/v1/agent/execution-authorizations
GET  /api/v1/agent/execution-authorizations/{authorizationId}
POST /api/v1/agent/execution-authorizations/{authorizationId}/continue
GET  /api/v1/agent/executions/{executionId}

GET  /api/v1/execution-authorizations/{authorizationId}
POST /api/v1/execution-authorizations/{authorizationId}/approve
POST /api/v1/execution-authorizations/{authorizationId}/reject
POST /api/v1/execution-authorizations/{authorizationId}/revoke
```

The exact paths may be refined during implementation planning. The public
contract must preserve the human/agent route separation.

### Proposed MCP tools

```text
request_trade_execution_authorization(
    trade_plan_id,
    trade_plan_version,
    broker_account_id,
    risk_evaluation_id,
    execution_idempotency_key
)

get_trade_execution_authorization_status(authorization_id)

execute_human_authorized_trade(authorization_id, execution_idempotency_key)

get_trade_execution_status(execution_id, broker_account_id)
```

MCP inputs identify targets only. Trading Core derives ownership, provider,
market and order parameters from authoritative persisted records.

## Security Requirements

1. Human approval endpoints accept only an authenticated human principal.
2. Agent endpoints accept only a validated `AgentPrincipal`.
3. Human owner is resolved from the authenticated session, never caller input.
4. Agent owner is resolved from persisted `AgentIdentity`.
5. Exact broker-account grant is checked at request, status and continuation.
6. No existing grant automatically acquires execution continuation authority.
7. Approval is short-lived, opaque, single-use and exact-operation bound.
8. Approval URL does not contain a reusable secret or broker credential.
9. Human approval mutations require CSRF/session/origin protection.
10. Parameters are derived from the immutable TradePlan and compared with the
    authorization fingerprint.
11. Changed plan version, quantity, protections, account, market, provider,
    Risk ID or idempotency key invalidates the continuation.
12. T1 Risk remains mandatory immediately before broker submission.
13. The agent cannot approve, revoke or broaden its own authorization.
14. Audit events contain identity, scope, fingerprint reference, decision,
    outcome, correlation and timestamps but no secrets or raw broker payloads.
15. Revoked/expired credentials and grants deny new authorization operations.
16. All failures default deny and do not reveal foreign resource existence.

## Persistence and Migration Requirements

- Add a reviewed migration for execution authorization state and indexes.
- Enforce uniqueness for authorization ID and consumed ExecutionIntent linkage.
- Enforce a safe lookup/uniqueness strategy for agent plus execution
  idempotency key and request fingerprint.
- Persist optimistic version for concurrent human and agent transitions.
- Store decision/consumption audit fields durably.
- Preserve old `TradePlanApprovalChallenge` rows without treating them as new
  execution authorizations.
- Do not apply migrations to shared runtime databases during implementation.
- Do not persist confirmation secrets, access tokens, broker credentials or raw
  provider payloads.

## Failure Semantics

| Failure | Required behavior |
|---|---|
| Wrong owner/agent/account/grant | Deny without revealing foreign resource |
| Rejected human decision | Terminal authorization; no continuation |
| Expired authorization | Terminal `EXPIRED`; new authorization required |
| Revoked authorization | Terminal `REVOKED`; no continuation |
| Changed plan/version/order parameters | Invalidate or reject fingerprint; new version/authorization required |
| Duplicate request with same fingerprint/key | Return existing authorization or intent |
| Same key with changed fingerprint | Idempotency conflict; no new intent |
| Concurrent continuation | One claim and one intent; other caller receives existing state/conflict |
| Agent restart | Re-read authorization and intent; no client-local authority is lost |
| T1 Risk rejected | No Broker Service call; durable intent rejection state |
| T1 Risk unavailable | No Broker Service call; explicit retry only under existing human-controlled semantics |
| Broker timeout/ambiguous response | Existing `UNKNOWN` and reconciliation flow; no blind retry |
| Database claim transaction rollback | Authorization remains unconsumed; same request may retry safely |
| Commit outcome uncertain | Read authorization/idempotency/intent before any retry |
| Audit write failure | Human/security policy must define fail-closed behavior; default sensitive authorization is fail-closed |

## Test Plan

### Authorization and identity

- Authorized exact operation succeeds.
- Rejected, expired and revoked authorization cannot continue.
- Wrong human user cannot approve or inspect the challenge.
- Wrong agent cannot inspect or continue another agent's authorization.
- Wrong broker account is denied.
- Cross-user account is denied.
- Revoked/expired grant is denied.
- Agent cannot call human approval endpoints.
- Human cannot use an agent-only continuation identity.

### Exact binding

- Changed TradePlan version is rejected.
- Changed quantity is rejected.
- Changed direction/order type is rejected.
- Changed entry price constraint is rejected.
- Changed stop-loss is rejected.
- Changed take-profit/protection parameters are rejected.
- Changed provider, market or product identity is rejected.
- Changed Risk evaluation ID or decision is rejected.
- Changed execution idempotency key is rejected.

### Concurrency and idempotency

- Duplicate MCP request with same fingerprint returns the existing record.
- Same idempotency key with changed fingerprint returns conflict.
- Concurrent continuation calls produce one authorization consumption and one
  `ExecutionIntent`.
- Authorization consumption and intent creation roll back together on database
  failure.
- Agent process restart safely re-reads the server-owned state.
- Existing `ExecutionIntent` is reused instead of creating a duplicate.

### Risk and execution

- Stale/rejected T0 Risk cannot create authorization.
- T1 Risk runs before Broker Service submission.
- T1 rejection produces no broker call.
- T1 unavailable produces no broker call and explicit recoverable state.
- Broker timeout becomes `UNKNOWN`.
- Unknown outcome cannot trigger blind retry.
- Reconciliation returns the authoritative broker result.
- Execution status is scoped to the authorized owner/account.

### Boundary and security

- No MCP route calls Broker Service directly.
- No MCP route accepts broker credentials or provider payloads.
- CSRF and wrong-origin approval attempts fail.
- Approval-link leakage cannot authorize a different authenticated user.
- Prompt-injected or misleading tool output cannot create approval.
- Audit records contain no secrets, tokens or raw broker payloads.
- Gateway and Trading Core reject forged owner/agent/delegated-actor headers.

## Acceptance Criteria

1. OpenCode can request a human authorization for an existing immutable
   TradePlan with an approved T0 Risk evaluation.
2. The request returns an opaque confirmation URL and exact bounded summary.
3. The human can approve/reject from a focused authenticated surface launched
   from OpenCode without navigating the full Angular trading workflow.
4. Approval records human identity, decision, timestamp, expiry and exact
   fingerprint.
5. An agent cannot approve, broaden, replay or transfer the authorization.
6. Only the exact authorized broker account, provider, market/product and order
   parameters are accepted.
7. The MCP continuation atomically consumes the authorization and creates or
   reuses one `ExecutionIntent`.
8. Existing T1 Risk executes before any Broker Service call.
9. Existing Execution Domain, Broker Service and reconciliation semantics are
   reused unchanged for the financial side effect.
10. Duplicate and concurrent continuation calls cannot create duplicate broker
    submissions.
11. Broker timeout/unknown outcome is visible and requires reconciliation; no
    blind retry exists.
12. Execution status and authorized positions can be read through scoped MCP
    contracts without exposing provider credentials.
13. Automated tests cover all required authorization, exact-binding,
    concurrency, Risk, unknown-outcome and boundary cases.
14. No order, position, balance, grant, credential or runtime account state is
    mutated during automated validation.
15. ADR-090 remains authoritative for retained identity and grant decisions;
    execution continuation remains feature-disabled until implementation approval.

## Rollout and Validation Plan

1. Run unit and contract tests for authorization lifecycle, fingerprinting and
   state transitions.
2. Run Trading Core integration tests with provider fakes and no live broker.
3. Run Gateway/MCP security and authorization tests.
4. Validate MCP -> Gateway -> Trading Core in a non-submitting execution path,
   including confirmation UI and authorization consumption with broker calls
   disabled.
5. Perform authenticated read-only verification of the existing
   `FTMO Free cTrader` account, provider mapping, market identity, positions,
   account facts and readiness.
6. Obtain explicit human approval for a narrowly scoped real execution
   validation only after all LIVE prerequisites in ADR-098 are satisfied.
7. If and only if approved, execute one exact controlled validation and inspect
   broker outcome/reconciliation; do not broaden scope automatically.

No step in this Story authorizes an order during implementation or test
planning.

## Rollback Considerations

- Keep the feature flag disabled to remove the new MCP continuation route.
- Revoke new execution-continuation grants before disabling routes.
- Pending authorizations can be marked `REVOKED` or allowed to expire; never
  delete them if audit retention requires preservation.
- Already consumed authorizations and linked ExecutionIntents must not be
  rolled back as if no broker side effect occurred.
- Broker `UNKNOWN` states require reconciliation, not database deletion.
- Existing human execution endpoints remain unchanged throughout rollback.

## ADR Dependencies

- **ADR-029:** reuse ExecutionIntent, attempts, idempotency and reconciliation;
  no direct broker path.
- **ADR-041:** preserve human authority, T1 Risk, unknown and audit semantics.
- **ADR-044:** preserve trusted service delegation and actor propagation.
- **ADR-048:** AI interpretation remains non-authoritative; Risk and execution
  retain financial authority.
- **ADR-088:** MCP remains a thin Gateway-facing protocol boundary.
- **ADR-090:** must be explicitly superseded/amended before enabling the new
  continuation; no implicit execution permission.
- **ADR-092:** bounded, auditable MCP commands and safe failure semantics remain.
- **ADR-098:** proposed architecture for exact human-authorized continuation.

## Architectural Friction Decisions

| Constraint | Decision | Rationale |
|---|---|---|
| Agent grants | `SIMPLIFY` through three exact execution permissions, not a standing order grant | Separates request, status and continuation while retaining resource scope |
| Research scopes | `KEEP` | Research authorization is unrelated to financial execution authority |
| Existing approval challenge | `REPLACE` as execution authority only through a new explicit model or approved extension | Current challenge is a plan decision and lacks consumption semantics |
| Full Angular execution workflow | `SIMPLIFY` with a focused confirmation surface launched from OpenCode | Removes navigation friction without removing trusted human auth |
| ExecutionIntent creation | `KEEP` in Trading Core; expose only a controlled continuation | Existing domain already owns idempotency and audit |
| MCP tool granularity | `SIMPLIFY` with bounded high-level tools | OpenCode should not reconstruct internal orchestration |
| Workflow grant | `REMOVE` as a standing financial authorization concept | A workflow identity must never authorize future orders |
| Account selection | `KEEP` exact internal broker-account identity and owner checks | Prevents provider/account substitution |
| Idempotency | `KEEP` and bind it into the human-approved fingerprint | It is a financial safety invariant, not optional transport metadata |

## Definition of Done

- [x] ADR-098 human architecture approval recorded.
- [x] ADR-090 narrow supersession decision recorded.
- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved.
- [ ] Implementation completed within approved scope.
- [ ] Security and authorization tests pass.
- [ ] MCP/Gateway/Trading Core contract tests pass.
- [ ] Human confirmation surface and CSRF/session controls validated.
- [ ] No live broker order submitted during implementation validation.
- [ ] Documentation reconciliation completed.
- [ ] Code Review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created.

## Delivery Constraint

This proposed Story does not authorize implementation, migration application,
credential/grant changes, deployment, restart, broker calls or execution. Human
approval is required for the ADR, the Repository Analysis, the Implementation
Plan and any future LIVE validation.

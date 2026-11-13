# Implementation Plan - Story 0231

## Overview

Implement a Trading Core-owned, exact-operation execution authorization that is
requested through MCP, approved by an authenticated human in a focused web
surface, and consumed once before entering the existing `ExecutionIntent`, T1
Risk, Execution Domain and Broker Service path.

The implementation must not create a second execution engine, direct MCP-to-
Broker Service route or standing agent execution permission. It must remain
feature-disabled until the human security decisions listed under Risks are
resolved and the implementation plan is approved.

The first increment is a broker-fake/non-submitting vertical slice. No live
cTrader order, shared database migration, credential change, grant change,
deployment or restart is part of implementation validation.

Approved security decisions:

- Every LIVE execution authorization requires a password reauthentication
  step-up on the existing authenticated human session.
- Each authorization expires five minutes after creation, whether pending or
  approved, and may be revoked before consumption.
- Audit persistence is fail-closed for human approval and authorization
  consumption; no financial transition becomes durable without its audit event.

## Planned Changes

### Step 1 - Establish the execution authorization model

**Component:** `trading-core` trade-planning/execution application and
persistence.

Create a separate `ExecutionAuthorization` aggregate rather than changing the
meaning of `TradePlanApprovalChallenge`. Persist the exact server-derived
operation tuple, fingerprint, human decision, expiry, optimistic version,
consumption state and linked `ExecutionIntent`.

Preserve the existing TradePlan approval lifecycle for plan decisions. Create an
execution authorization only after the immutable TradePlan and approved T0 Risk
evaluation have been reloaded and exact broker/provider/market/order facts have
been resolved server-side.

**Constraints:** no MCP-provided order fields become authoritative; old approval
rows remain valid and are never treated as execution authorizations.

### Step 2 - Implement canonical fingerprinting and lifecycle transitions

**Component:** `trading-core` domain/application services.

Add one canonical fingerprint service over owner, agent, grant/version, broker
and trading accounts, provider/product identity, TradePlan/Risk references,
order parameters, protections and execution idempotency key.

Implement the transitions `REQUESTED`, `PENDING_HUMAN_CONFIRMATION`, `APPROVED`,
`REJECTED`, `EXPIRED`, `REVOKED`, `CONSUMING`, `CONSUMED` and the required
retry/unknown projections. Use optimistic locking or an equivalent database
claim so concurrent continuation requests produce at most one linked intent.

**Constraints:** the authorization and `ExecutionIntent` claim/creation are one
Trading Core transaction; broker submission remains outside that transaction.

### Step 3 - Add human authorization endpoints and confirmation surface

**Components:** `trading-core`, `gateway`, `trading-os-web`.

Add human-only read, approve, reject and revoke routes. The confirmation URL
contains only an opaque authorization identifier. The page resolves the owner
from the authenticated human session, displays the exact bounded summary and
uses CSRF/origin/session protections for mutations.

Add reauthentication or the selected step-up mechanism only after the required
identity decision is confirmed. Do not create a parallel identity provider.

**Constraints:** agent principals cannot call human mutation routes; URL values
never establish owner identity or contain reusable approval secrets.

### Step 4 - Add agent Gateway contracts and MCP mappings

**Components:** Gateway public route configuration/controllers and
`mcp-adapter/app/client.py`, `mcp-adapter/app/server.py`, `mcp-adapter/app/bridge.py`.

Expose bounded contracts for:

- requesting an exact execution authorization;
- reading authorization status;
- continuing one approved authorization;
- reading account-scoped execution status.

The MCP adapter remains a thin Gateway client using the existing agent token
exchange and bounded response/error conventions. Add exact resource-bound
permissions for request, continuation and status read. Do not add
`orders:execute` or provider-specific payloads.

**Constraints:** no direct Broker Service, cTrader, repository or database access
from MCP; foreign resource existence must not be disclosed.

### Step 5 - Integrate continuation with the existing execution domain

**Component:** `trading-core` execution API/application services.

Add an agent-scoped continuation application service that atomically validates
the authorization, grant, owner, expiry, fingerprint and idempotency key, then
creates or reuses exactly one `ExecutionIntent`. Continue through the existing
execution service so T1 Risk, broker-neutral delegation, provider outcome
classification and reconciliation remain authoritative.

Expose a sanitized execution projection scoped to the authorized owner and
broker account. Preserve `UNKNOWN` semantics and prohibit blind broker retry.

**Constraints:** no changes to deterministic Risk rules or Broker Service
provider contracts unless an independently justified audit-context gap is found.

### Step 6 - Add tests and non-submitting validation

Add unit, persistence, controller, Gateway and MCP contract tests for exact
binding, principal separation, replay prevention, concurrency, idempotency,
CSRF/route boundaries, T1 Risk ordering and unknown broker outcomes.

Run the end-to-end continuation with provider fakes and broker submission
disabled. Validate that no live account or external broker state changes.

## Files to Modify

- `docs/architecture/adr/ADR-090-agent-identity-permissions-delegated-authorization.md` and
  `docs/architecture/adr/ADR-098-human-authorized-mcp-trading-execution-continuation.md`:
  preserve the accepted narrow supersession record and implementation status.
- `docs/architecture/stories/0231-secure-mcp-execution-authorization-and-continuation/story.md`:
  update implementation checklist and status only as stages are actually completed.
- `trading-core/src/main/java/com/hope/trading/trading_core/tradeplanning/approval/*`:
  reuse existing approval boundaries without changing plan-approval semantics.
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/*`:
  add continuation orchestration and reuse existing intent/status services.
- `trading-core/src/main/java/com/hope/trading/trading_core/security/*`:
  extend agent policy only with the approved resource-bound permissions.
- `gateway/src/main/java/com/hope/trading/gateway/security/*` and the existing
  Gateway route/controller configuration: separate human and agent contracts.
- `mcp-adapter/app/client.py`, `mcp-adapter/app/server.py` and
  `mcp-adapter/app/bridge.py`: add typed bounded mappings and error handling.
- `trading-os-web/src/app/features/trade-planning/approval-page/*` and related
  execution services/models: add the focused confirmation surface without
  rewriting the existing decision workspace.
- Relevant module test directories: add regression and integration coverage
  described in the Test Plan.

Exact Java class names and the next migration number must be confirmed from the
current module conventions before implementation; this plan intentionally does
not invent them.

## Files to Create

- A new Trading Core `ExecutionAuthorization` domain/application/persistence
  slice and its DTO/projection types.
- A reviewed database migration for execution authorization state and unique
  constraints, using the next repository-approved migration number.
- Gateway/controller contract types where existing DTO conventions require them.
- MCP adapter tests for authorization request, status, continuation and scoped
  execution status.
- Focused web confirmation component/specification if the existing approval page
  cannot safely host the new exact-operation review.

No new external dependency is planned.

## Dependencies

- Accepted ADR-098 and its narrow supersession of ADR-090.
- Existing `TradePlan`, T0 Risk, `TradePlanApprovalChallenge`, `ExecutionIntent`,
  T1 Risk, Broker Service and reconciliation contracts.
- Existing human authentication/session and Gateway CSRF/origin protections.
- Existing agent identity, token exchange and resource-scoped grant model.
- Existing password reauthentication and session controls must be confirmed in
  the current human identity implementation.
- Implementation ordering: persistence/domain first, then human routes/UI,
  agent contracts/MCP, continuation integration, and finally non-submitting
  integration validation.

## Test Plan

### Authorization and identity

- exact authorized operation can be created and approved;
- wrong human, agent, owner, account or grant is denied without foreign-resource
  disclosure;
- agent cannot approve, reject or revoke through human routes;
- expired, rejected and revoked authorizations cannot continue;
- approval URL leakage cannot authorize another authenticated user;
- CSRF and wrong-origin approval mutations fail.

### Exact binding and idempotency

- changed plan/version, Risk ID, account, provider/product, market, direction,
  order type, quantity, price constraint, protection or idempotency key fails;
- duplicate same-key requests return the existing authorization/intent;
- changed fingerprint with an existing key returns conflict;
- concurrent continuation creates one consumption and one `ExecutionIntent`;
- database rollback leaves the authorization retryable;
- ambiguous commit is resolved by reading durable authorization/idempotency state.

### Risk and broker boundary

- T1 Risk executes before Broker Service submission;
- T1 rejection or unavailable facts cause no broker call;
- broker timeout becomes `UNKNOWN` and cannot be blindly retried;
- provider fake outcome and reconciliation remain broker-neutral;
- MCP has no direct Broker Service or cTrader route.

### Commands

```bash
cd trading-core && ./mvnw -q test
cd gateway && ./mvnw -q test
cd trading-os-web && npm test -- --watch=false
mcp-adapter/.venv/bin/python -m pytest mcp-adapter/tests
./scripts/quality-verify.sh
git diff --check
```

Expected success means targeted authorization/security/contract tests pass,
existing module regressions pass, and the non-submitting continuation path
produces no external broker mutation. Commands must be adjusted to the actual
repository scripts if their current interfaces differ.

## Risks

- **Password step-up implementation gap:** the existing human identity flow must
  expose a secure recent-password verification without logging credentials or
  weakening session protections.
- **Authorization/intent race:** an incorrect transaction or lock could produce
  duplicate financial submissions. Mitigate with one durable claim protocol,
  unique constraints and concurrent integration tests.
- **Fingerprint incompleteness:** omitted provider/product or protection facts
  could permit parameter substitution. Derive the fingerprint from canonical
  server-owned values and test every listed field.
- **Existing challenge semantic drift:** reusing the plan challenge could make
  old approvals executable. Keep a separate aggregate unless an explicit
  migration decision changes that conclusion.
- **MCP status overclaiming:** sanitized projections could report approval as
  execution. Model each lifecycle state explicitly and test `UNKNOWN` and
  reconciliation states.
- **LIVE validation leakage:** an integration test could reach cTrader. Use fake
  providers, disabled submission and explicit no-live-mutation assertions.

## Validation Checklist

- [ ] ADR-098 and narrow ADR-090 supersession remain consistent.
- [ ] Separate execution authorization model exists; old plan approvals retain
      their original meaning.
- [ ] Fingerprint includes every exact-operation field and is server-derived.
- [ ] Human and agent routes enforce separate principals.
- [ ] Authorization is expiring, immutable, single-use and replay-resistant.
- [ ] Claim and `ExecutionIntent` creation are recoverable and idempotent.
- [ ] Existing T1 Risk, Execution Domain, Broker Service and reconciliation paths
      remain authoritative.
- [ ] MCP remains Gateway-only and provider-neutral.
- [ ] No standing `orders:execute` permission exists.
- [ ] Security, concurrency, contract and unknown-outcome tests pass.
- [ ] Non-submitting validation proves no live broker mutation.
- [ ] No credentials, grants, shared migrations, deployments or restarts are
      changed during implementation validation.

## Recommendation

Ready for implementation

The required LIVE-boundary security decisions have been explicitly resolved.
This is a technical recommendation only and does not authorize implementation.

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.

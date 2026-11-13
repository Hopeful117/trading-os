# Story 0232 - Persistent OpenCode Agent Access and MCP Reliability

## Metadata

**ID:** `0232`
**Title:** Persistent OpenCode Agent Access and MCP Reliability
**Status:** Implementation locally present - deterministic validation passed;
runtime validation partial; review pending
**Priority:** P0 - Agent Platform Reliability
**Related ADRs:** `ADR-088`, `ADR-090`, `ADR-098`, `ADR-099`

## User Problem

An already enrolled and authorized OpenCode agent currently requires repeated
human reconnection after OpenCode or the local MCP bridge restarts. MCP read
failures also surface without stable diagnostic information, making it
difficult to distinguish authentication, authorization, bounded response,
upstream and account-readiness failures.

## Goal

Allow an ACTIVE, non-revoked OpenCode agent to reconnect and renew short-lived
authentication automatically while preserving strict human authorization for
each exact LIVE trade execution. Make permitted MCP read failures actionable,
sanitized and traceable.

## Scope

- Reuse persistent agent identities, hashed opaque credentials and grants.
- Load and validate the existing credential on bridge startup.
- Renew short-lived agent JWTs safely, including concurrent refresh control.
- Remove mandatory ordinary reconnect approval from the Angular flow.
- Preserve UI controls for enrollment, rotation, recovery, status, grants and
  revocation.
- Add structured sanitized MCP diagnostics and correlation propagation.
- Keep market discovery bounded and fix authorized position-read diagnostics.
- Review account-readiness checks so incomplete execution facts do not block
  unrelated permitted reads unless the domain contract requires it.
- Validate the existing FTMO Free cTrader account through the standard LIVE
  Gateway without broker mutations.

## Non-Goals

- No new agent identity system or authorization server.
- No PAPER environment, synthetic broker or parallel execution pipeline.
- No broker credential changes or grant broadening.
- No order placement, amendment, cancellation or position mutation.
- No bypass of T0/T1 Risk or human execution authorization.
- No weakening of revocation, expiry, idempotency or reconciliation rules.
- No unrelated trading-domain refactoring.

## Existing Contracts

- Trading Core owns `AgentIdentity`, `AgentCredential`, `AgentGrant` and agent
  token exchange.
- Gateway is the only external ingress for MCP operations.
- MCP Adapter is a thin anti-corruption client over public Gateway contracts.
- `ExecutionMode.LIVE` applies to FTMO Free cTrader.
- ADR-098 governs exact human-authorized execution continuation.
- `CONNECTED_BUT_FACTS_INCOMPLETE` remains distinct from read authorization,
  risk eligibility and execution eligibility.

## Security Invariants

1. Initial enrollment and grant changes remain human-controlled.
2. ACTIVE, revoked and suspended states fail closed.
3. Credential rotation never creates a second agent identity.
4. Access JWTs are short-lived and never persisted as durable credentials.
5. Automatic renewal never replays a POST or financial operation.
6. The agent can access only existing grants and exact account resources.
7. FTMO Free cTrader remains LIVE.
8. Exact LIVE trade execution still requires independent, short-lived,
   single-use human authorization bound to an immutable execution fingerprint.

## Implementation Tasks

- [x] Confirm and implement bridge startup credential recovery.
- [x] Coordinate token renewal and classify revoked versus expired access.
- [x] Add structured MCP error and correlation contracts.
- [x] Preserve bounded `list_markets` filtering and response-size protection.
- [x] Diagnose and correct `get_authorized_positions` mapping/read behavior.
- [x] Separate read authorization from account facts completeness.
- [x] Remove only redundant ordinary reconnect UI behavior; retain explicit
      rotation/recovery.
- [x] Persist non-secret authority binding metadata and fail closed on mismatch.
- [x] Separate Reconnect from explicit Rotate and Recover operations.
- [x] Add unit, integration and regression tests.
- [ ] Run read-only LIVE validation and record evidence.

## Acceptance Criteria

- [ ] An existing ACTIVE agent reconnects after OpenCode restart without UI
      approval or creation of a new identity. Deterministic recovery and a
      post-Connect restart passed; uninterrupted standard-runtime continuity
      with the current credential was not established.
- [x] MCP Adapter restart reloads the valid opaque credential and exchanges a
      fresh short-lived JWT in deterministic bridge tests.
- [x] JWT expiry renews automatically for permitted GET operations.
- [x] Concurrent refresh does not create a refresh storm or duplicate identity.
- [x] Revoked agents, credentials and grants fail closed.
- [x] `list_markets` succeeds for bounded EUR/USD filtering after the standard
      service rebuild.
- [x] `get_authorized_positions` succeeds or returns a meaningful sanitized
      domain-specific error with status and correlation information.
- [x] Account details and status remain readable when permitted even if risk or
      execution facts are incomplete, where the contract allows it.
- [x] `executionAllowed` and `riskEvaluationAllowed` are not loosened.
- [x] No broker mutation or execution authorization is created by validation.
- [x] Diagnostics contain no credential, JWT, cookie, stack trace or provider
      secret.
- [x] Independent installations use separate local authority bindings; multiple
      active credentials per identity remain unsupported.

## Validation and Rollback

Run targeted MCP Adapter, Trading Core, Gateway and Angular tests, then the
repository-defined quality checks applicable to changed modules. Perform the
runtime validation only through the standard Gateway and the existing FTMO
Free cTrader account. Roll back code/configuration changes without deleting
credentials or changing broker/account state.

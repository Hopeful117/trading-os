# ADR-099 - Persistent Agent Authorization and Automatic MCP Session Recovery

## Status

Proposed - Repository Analysis pending human approval

## Date

2026-11-12

## Decision Makers

- Trading OS Architecture
- Human Engineer
- Project Owner

## Decision Type

Agent credential lifecycle, MCP session recovery and sanitized diagnostics

## Related ADRs

- ADR-044 - Service-to-Service Trust and Actor Propagation
- ADR-073 - cTrader Authentication and Explicit External Account Binding
- ADR-078 - PAPER and LIVE Execution Boundary
- ADR-084 - Live Financial Facts and Provider Preflight Readiness
- ADR-088 - MCP-First Agent Interaction Boundary
- ADR-090 - Agent Identity, Permissions and Delegated Authorization
- ADR-098 - Human-Authorized MCP Trading Execution Continuation

## 1. Context

Trading OS already has persistent `AgentIdentity`, resource-scoped grants,
hashed opaque credentials and short-lived agent JWTs. The local MCP bridge,
however, treats pairing state as process-local. The Angular Connect action also
issues a new credential and performs a new pairing for an already active agent.
After an OpenCode or bridge restart, this creates avoidable human friction.

Recent read-only validation also showed that the MCP boundary exposes only the
generic `Error executing tool` text. The underlying client and Trading Core
contracts contain useful status distinctions, but status, stable code,
correlation and retry guidance are not preserved at the MCP tool boundary.

## 2. Decision

Persist the agent authorization state server-side and reuse the existing opaque
credential mechanism. The local bridge may load the previously persisted opaque
credential on startup and automatically exchange it for a short-lived agent JWT.
The JWT is renewed before expiry and once after an authenticated GET receives a
`401`. Concurrent renewal is coordinated so one bridge process performs one
exchange at a time. POST requests are never replayed automatically.

The bridge and MCP client must distinguish active authorization, expiration,
revocation, permission denial, account access, domain readiness, upstream
availability, invalid requests and internal failures. Errors crossing the MCP
boundary contain only sanitized structured diagnostics.

The Connect UI remains for initial enrollment, explicit credential rotation,
recovery and operator inspection. It is not required for ordinary reconnect of
an ACTIVE, non-revoked agent whose credential and grants remain valid.

## 3. Authority and Security Boundaries

- Initial agent enrollment and grant creation remain human-controlled.
- Agent identity, credentials and grants remain owned by Trading Core.
- Credentials are stored only as hashes in Trading Core and as an opaque,
  owner-only local bridge file; access JWTs remain short-lived and in memory.
- Revocation, suspension, credential rotation and grant removal take effect on
  the next token exchange and on downstream token validation.
- Agent access never authorizes a financial operation.
- FTMO Free cTrader remains `ExecutionMode.LIVE` because it crosses an external
  broker boundary.
- Exact LIVE execution still requires ADR-098 human authorization, immutable
  fingerprint binding, T1 Risk revalidation, single-use consumption,
  idempotency and existing reconciliation behavior.

## 4. Alternatives Considered

### 4.1 Require the Connect UI after every restart

Rejected. It repeats enrollment-adjacent work for a credential and grant that
are already persistent and valid.

### 4.2 Persist a long-lived access JWT

Rejected. JWTs remain short-lived bearer access tokens and must be renewed from
the opaque credential.

### 4.3 Create a new agent identity when recovery fails

Rejected. Recovery must never bypass revocation or create duplicate identities.

### 4.4 Increase the global MCP response limit

Rejected as the primary market fix. Market discovery must remain bounded; the
existing `instrument` filter should be used and preserved.

### 4.5 Add a second authorization server

Deferred. The existing agent credential exchange and grant model is sufficient
for the local MCP boundary.

## 5. Error Contract

The MCP client returns a sanitized error object with:

- stable `code`;
- safe `message`;
- HTTP `status` when available;
- `correlationId` when supplied by Gateway or Trading Core;
- `retryable`;
- safe `nextAction`.

The initial categories are `AUTHENTICATION_FAILED`, `TOKEN_EXPIRED`,
`AGENT_REVOKED`, `PERMISSION_DENIED`, `ACCOUNT_ACCESS_DENIED`,
`ACCOUNT_FACTS_INCOMPLETE`, `MARKET_DATA_UNAVAILABLE`,
`UPSTREAM_UNAVAILABLE`, `INVALID_REQUEST` and `INTERNAL_ERROR`.

JWTs, opaque credentials, cookies, broker credentials, stack traces and raw
provider payloads never cross this boundary.

## 6. Migration and Rollout

1. Reuse the existing credential table, identity lifecycle and grant model.
2. Make bridge startup load and validate the existing local credential.
3. Add client-side structured error parsing and correlation propagation.
4. Fix bounded market reads and preserve the response-size guard.
5. Separate account-read authorization from completeness and execution
   eligibility.
6. Validate revocation, restart, expiry and concurrent refresh in tests.
7. Validate the existing FTMO Free cTrader account read-only through Gateway.

Rollback is code/configuration rollback only. Do not delete credentials,
change broker credentials, alter grants, recreate the FTMO account or apply a
new PAPER environment as part of this decision.

## 7. Consequences

The first reconnect after deployment may require a legitimate credential
rotation if no valid local opaque credential exists. Thereafter, OpenCode and
the bridge can restart without a repeated human pairing step. Revocation and
grant changes remain fail-closed. Runtime diagnostics become actionable without
exposing secrets.

# ADR-099 - Persistent Agent Authorization and Automatic MCP Session Recovery

## Status

Accepted - human architecture approval completed 2026-11-13; bounded
implementation authorized

## Date

2026-11-12 (reconciled 2026-11-13)

## Decision Makers

- Trading OS Architecture
- Human Engineer
- Project Owner

## Approval Record

The project owner approved the bounded ADR-099 direction on 2026-11-13 with
these clarifications:

- Prefer one logical agent identity per independent OpenCode/bridge
  installation and environment.
- Distinguish Reconnect, Recover and Rotate operations explicitly.
- Bind credentials to a stable authentication authority identity when available;
  otherwise use the conservative configured-authority binding described here.

This approval does not authorize privileged agent self-provisioning, automatic
root credential rotation, broader grants, broker mutations or financial
execution. Material changes to the trust boundary require a new approval.

## Decision Type

Agent credential lifecycle, environment binding, MCP session recovery and
sanitized diagnostics

## Related ADRs

- ADR-044 - Service-to-Service Trust and Actor Propagation
- ADR-073 - cTrader Authentication and Explicit External Account Binding
- ADR-078 - PAPER and LIVE Execution Boundary
- ADR-084 - Live Financial Facts and Provider Preflight Readiness
- ADR-088 - MCP-First Agent Interaction Boundary
- ADR-090 - Agent Identity, Permissions and Delegated Authorization
- ADR-098 - Human-Authorized MCP Trading Execution Continuation
- Story 0232 - Persistent OpenCode Agent Access and MCP Reliability

## 1. Context

Trading Core owns persistent `AgentIdentity`, `AgentCredential` and
`AgentGrant` records. Credentials are issued through a human-authenticated
management flow, stored as hashes in Trading Core, and returned only at
issuance. The local MCP bridge stores the opaque credential in an owner-only
file. The Gateway exchanges that credential for a short-lived agent JWT.

Story 0232 added local credential persistence, startup exchange, coordinated
token exchange, structured diagnostics and safe GET reauthentication. The
current implementation and tests support normal recovery when the persisted
credential still matches the configured Gateway's Trading Core authority.

The confirmed operational failure was different: the local file contained a
17-byte stale value whose digest was absent from the checked standard and E2E
databases. The standard Trading Core volume was persistent and contained active
OpenCode identities and credentials; database recreation was not demonstrated.
The Gateway correctly rejected the root credential with `401`. Human Connect
issued a new credential, after which bridge restart recovery succeeded.

This establishes two different cases:

1. A normal process restart with valid trust must not require Connect.
2. A missing, invalid, revoked or environment-mismatched root credential must
   not be silently repaired by an agent process.

The current Angular Connect action issues a new credential and revokes the
previous active credential. It is therefore a rotation/recovery action, not a
normal reconnect operation. The single-active-credential behavior also means
that rotating one logical agent invalidates another bridge using that agent.

## 2. Decision Drivers

- Preserve ADR-090 identity, grant and default-deny boundaries.
- Make normal OpenCode and bridge restarts independent of UI pairing.
- Distinguish process restart, environment switch and lost trust.
- Prevent silent recreation or reauthorization of revoked trust.
- Keep JWTs short-lived and never persist access tokens.
- Keep POST requests and financial operations out of automatic retry.
- Make failures actionable without exposing secrets or enabling credential
  enumeration.
- Avoid a second authorization server or new privileged infrastructure.

## 3. Existing Behavior To Preserve

- Trading Core remains the authority for agent identity, credential status and
  grants.
- Agent credentials remain opaque to the MCP adapter and hashed at rest in
  Trading Core.
- The bridge persists only the opaque credential in an owner-only local store.
- Gateway and Trading Core validate agent JWT claims and live agent/credential
  state.
- Access JWTs remain short-lived and in memory.
- Credential exchange is coordinated within one bridge process.
- An authenticated GET may retry once after `401`; POST requests are never
  replayed automatically.
- Revoked, suspended, expired and unknown credentials fail closed.
- Agent permissions remain explicit, resource-scoped and default-deny.
- Agent authentication never authorizes a financial operation.
- FTMO Free cTrader remains `ExecutionMode.LIVE`; no agent path bypasses Risk,
  human authorization or reconciliation.

## 4. Selected Design

### 4.1 Identity and environment binding

An agent installation is the combination of:

```text
configured Gateway authority
    + local bridge installation
    + Trading Core AgentIdentity
    + current AgentCredential version
    + server-side AgentGrant set
```

The bridge must treat the configured Gateway authority as part of its local
installation configuration. A normal restart reuses the persisted credential
only against that same configured authority. A change of Gateway URL,
environment label, expected issuer or audience is an environment switch, not a
restart.

The approved implementation target is a non-secret local binding record stored
alongside the credential. It may contain only normalized Gateway authority,
environment label and non-secret credential/agent identifiers when the server
contract exposes them. It must never contain a credential, digest, JWT or
provider secret. A mismatch blocks automatic recovery and displays a safe
human-recovery instruction; it does not try another environment and does not
create an identity.

The first implementation phase may use the configured Gateway authority as the
binding if the current deployment has no explicit environment identifier. A
separate environment registry or authorization server is not required.

### 4.2 Credential lifecycle

The conceptual lifecycle is:

```text
UNPROVISIONED
    -- human enrollment and initial Connect --> ACTIVE
ACTIVE
    -- process restart, same authority, valid root credential --> ACTIVE
ACTIVE
    -- access JWT expiry --> ACTIVE (new short-lived JWT)
ACTIVE
    -- explicit human rotation --> REVOKED old credential + ACTIVE new credential
ACTIVE
    -- agent suspension/revocation or credential revocation --> BLOCKED
BLOCKED / lost local credential / authority mismatch
    -- human-authenticated recovery --> ACTIVE with a new credential
```

Required behavior:

- Initial pairing issues one credential and binds it to the selected bridge
  authority.
- A valid persisted credential is exchanged automatically at bridge startup.
- JWT expiry renews from the root credential; it never rotates the root
  credential.
- Explicit rotation revokes the prior credential before or atomically with
  issuing the replacement according to the existing Trading Core contract.
- Suspension and revocation remain fail-closed on token exchange and
  downstream validation.
- A missing local credential, unknown credential, revoked credential or absent
  server-side hash requires human recovery.
- Database replacement or environment switch requires explicit re-pairing; no
  local agent process may infer that trust should be recreated.

### 4.3 Multiple bridge instances

The current single-active-credential model is retained for this decision. It is
predictable and limits the blast radius and audit ambiguity of rotation.

Separate machines or incompatible environments should use separate logical
agent identities. Multiple bridge processes sharing one logical identity and
credential are supported only when they intentionally share the same authority
and accept that explicit rotation invalidates all of them.

Supporting multiple simultaneously active credentials for one identity is
deferred. It would require decisions about revocation scope, audit attribution,
credential inventory, grant inheritance and rotation blast radius. It must not
be introduced as an incidental recovery fix.

### 4.4 Human-controlled recovery

The UI must distinguish these actions:

- **Connect:** initial enrollment or first binding of an installation.
- **Reconnect:** no credential issuance; only a local status/recovery probe for
  a valid existing binding.
- **Rotate credential:** deliberate issuance of a new credential and
  invalidation of the previous credential.
- **Recover lost credential:** human-authenticated rotation after missing,
  invalid or environment-mismatched root trust.
- **Revoke access:** agent or credential revocation; it must not be undone by an
  agent process.

The current Connect flow is permitted to remain the explicit recovery path, but
its documentation and UI semantics must not describe rotation as ordinary
reconnect. Recovery preserves existing grants; it does not broaden them.

## 5. Observability and Error Contract

The MCP boundary exposes sanitized structured diagnostics with:

- stable code;
- safe message;
- HTTP status when available;
- bounded correlation ID;
- retryability;
- safe next action.

The minimum diagnostic distinctions are:

| Condition | Boundary | Safe code/action |
|---|---|---|
| Missing local credential | Adapter | `CREDENTIAL_MISSING`; use human Connect/recovery |
| Wrong Gateway authority or environment | Adapter | `ENVIRONMENT_MISMATCH`; inspect authority and recover explicitly |
| Unknown/invalid root credential | Gateway exchange | `AUTHENTICATION_FAILED`; use human recovery |
| Revoked or suspended agent/credential | Gateway/Trading Core | non-enumerating authentication failure; do not retry automatically |
| Expired access JWT | Adapter/request | renew from valid root credential; never rotate root credential |
| Insufficient grant | Authorized API | `PERMISSION_DENIED` / `403`; change grants only through human management |
| Upstream or service failure | Adapter/API | `UPSTREAM_UNAVAILABLE`; retry only when marked safe |
| Stale bridge configuration | Adapter | `CONFIGURATION_INVALID`; correct configuration, then recover explicitly |

Authentication failures must remain distinct from authorization failures. The
system must not disclose whether a supplied root credential corresponds to a
particular agent, credential version or grant set. No raw credential, digest,
JWT, cookie, stack trace or provider secret may appear in logs, reports, UI
diagnostics or MCP responses.

## 6. Security Invariants

1. A revoked credential never regains access automatically.
2. A lost root credential is never recreated through an unauthenticated agent
   request.
3. Agent permissions remain least-privilege, explicit and default-deny.
4. Human authorization remains separate from agent authentication.
5. Agent JWTs remain short-lived and in memory only.
6. Mutating POST operations are never automatically replayed.
7. Environment binding cannot weaken identity, credential or grant validation.
8. Recovery, rotation, revocation and denial events remain auditable without
   secrets.
9. Normal restarts never trigger credential rotation.
10. LIVE trading, Risk, approval, reconciliation and broker safety boundaries
    remain unchanged.

## 7. Options and Rejected Alternatives

### 7.1 Selected: same-authority local recovery plus explicit human recovery

This is the smallest design. It preserves the existing credential model,
supports normal restarts, and makes environment changes and lost trust explicit.

### 7.2 Rejected: silently issue a replacement after `401`

Rejected. It would turn an invalid or revoked root credential into implicit
reauthorization and weaken the human trust boundary.

### 7.3 Rejected: persist a long-lived access JWT

Rejected. JWTs are bearer access tokens and must remain short-lived and
renewable from the opaque root credential.

### 7.4 Deferred: multiple active credentials per AgentIdentity

Deferred until revocation scope, audit attribution and rotation blast radius are
explicitly designed and approved.

### 7.5 Rejected: second authorization server or agent self-provisioning

Rejected. Trading Core already owns identity, credentials and grants. A second
authority or privileged self-provisioning path would expand the attack surface.

## 8. Migration and Backward Compatibility

1. Keep existing `AgentIdentity`, `AgentCredential`, `AgentGrant` and token
   contracts.
2. Keep existing owner-only credential files readable for the current authority.
3. Add non-secret binding metadata only after a successful human-controlled
   pairing or explicit recovery.
4. Treat an existing credential without binding metadata as legacy: validate it
   only against the configured authority, then bind metadata after successful
   exchange if the approved implementation supports that operation.
5. Do not migrate, recreate, revoke or rotate credentials automatically.
6. Keep standard and E2E authorities isolated; never use one environment's
   credential as evidence for another.
7. Rollback is code/configuration rollback only; do not delete credentials,
   alter grants or change broker state.

## 9. Implementation Phases After Approval

1. Reconcile bridge configuration and add non-secret authority-binding metadata
   with atomic permissions and safe mismatch handling.
2. Separate UI Connect, reconnect/status, rotation and lost-credential recovery
   semantics without changing server authorization.
3. Add deterministic adapter and UI tests for authority mismatch and recovery
   guidance.
4. Preserve and expand token expiry, concurrent refresh, revocation, POST
   non-replay and sanitization tests.
5. Validate standard and isolated E2E authorities separately with synthetic
   credentials only in E2E.
6. Perform one read-only standard-runtime validation with a human-provisioned
   credential, including controlled bridge/OpenCode/Gateway restarts.

## 10. Acceptance Criteria For Future Implementation

- Valid same-authority credentials recover after MCP, bridge, OpenCode and
  Gateway restarts without Connect or credential rotation.
- Access JWT expiry renews without root credential rotation.
- Invalid, revoked, suspended and unknown credentials fail closed with safe
  diagnostics.
- Authority/environment mismatch blocks automatic recovery and requires human
  recovery.
- Explicit rotation invalidates the previous credential predictably and is
  auditable.
- Multiple bridge behavior is documented and tested under the single-active
  credential model.
- Existing grants persist unchanged through recovery and remain default-deny.
- Credential loss and database replacement require human-controlled recovery.
- Authentication `401` and authorization `403` remain distinguishable.
- No secret appears in logs, reports, UI diagnostics or MCP responses.
- No broker mutation, TradePlan, Risk evaluation, approval or execution is
  created by validation.

## 11. Validation Strategy

### Deterministic tests

- Credential file permissions, atomic persistence and legacy loading.
- Same-authority restart recovery.
- Authority/environment mismatch.
- Missing, malformed, invalid, revoked and suspended credentials.
- JWT expiry and coordinated renewal.
- Multiple concurrent GET refreshes.
- POST non-replay after `401`.
- Explicit rotation and old-credential rejection.
- Grant preservation and default-deny authorization.
- Database-replacement/lost-credential recovery guidance.
- Authentication versus authorization status/code mapping.
- Secret sanitization in logs and responses.

### Operational validation

- Human-provision a credential in the standard runtime.
- Verify MCP, bridge, OpenCode, Gateway and Trading Core restarts independently.
- Verify filtered read-only account and market calls.
- Run a separate synthetic E2E environment with a separate authority and
  credential set.
- Record only IDs, statuses, timestamps and safe diagnostics.
- Do not use operational validation to rotate credentials silently or perform
  broker, TradePlan, Risk, approval or execution operations.

## 12. Approved Scope Clarifications

1. One logical agent identity is preferred per independent bridge installation
   and environment. Multiple active credentials per identity remain deferred.
2. Reconnect performs status/session restoration only; Recover and Rotate are
   explicit human actions that issue a replacement credential.
3. A stable server-side authority identifier is preferred. It is not currently
   returned by the token exchange contract, so the implementation uses the
   normalized Gateway authority and optional configured issuer/audience metadata
   as a conservative migration-compatible binding.
4. Legacy credentials without binding metadata may be exchanged once through
   the existing trusted authority, then receive binding metadata.
5. Unknown, revoked and suspended credentials remain non-enumerating
   authentication failures.

## 13. Final Decision Status

```text
ADR-099_STATUS = ACCEPTED
HUMAN_ARCHITECTURE_APPROVAL = COMPLETED_2026-11-13
PRODUCTION_IMPLEMENTATION = AUTHORIZED_WITHIN_APPROVED_SCOPE
AUTOMATIC_ROOT_CREDENTIAL_ROTATION = REJECTED
AGENT_SELF_PROVISIONING = REJECTED
MULTIPLE_ACTIVE_CREDENTIALS_PER_AGENT = DEFERRED
AGENT_ORDER_EXECUTION = NOT_AUTHORIZED
```

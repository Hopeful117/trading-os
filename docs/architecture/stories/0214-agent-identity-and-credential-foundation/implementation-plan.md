# Implementation Plan - Story 0214

## Status

Completed - awaiting human approval

## Objective

Implement the Trading Core foundation for user-owned technical agents and their
credential lifecycle without introducing agent JWT authentication, grants, MCP
changes or broker access.

The implementation remains limited to Trading Core persistence, owner-scoped
lifecycle application contracts, one-time credential issuance/rotation/revocation
and sanitized lifecycle audit records.

## Design Decisions

### Persistence ownership

Add a new `agent` bounded package in Trading Core with explicit domain,
application, API and persistence types. Keep agent lifecycle separate from the
existing `UserService` and from broker credential entities.

Use scalar `ownerUserId` values with database foreign-key constraints to
`users(user_id)` rather than exposing a mutable JPA user graph from agent DTOs.
Every owner-scoped repository method takes both the resource ID and the owner
ID where an individual resource is addressed.

### Agent lifecycle

`AgentIdentity` states:

```text
ACTIVE -> SUSPENDED -> ACTIVE
ACTIVE -> REVOKED
SUSPENDED -> REVOKED
```

`REVOKED` is terminal. Registration creates an `ACTIVE` agent owned by the
authenticated user. Suspending or revoking an agent revokes or invalidates its
active credentials according to the same transaction. No lifecycle method
accepts an owner ID from the request body.

### Credential material

Generate a high-entropy opaque credential with `SecureRandom`, encoded using a
URL-safe representation. Store only:

- credential ID;
- agent ID;
- credential prefix/fingerprint for support and audit lookup;
- SHA-256 digest of the full random credential for exact verification by the
  future token exchange boundary;
- status, version and lifecycle timestamps.

The raw credential is returned only in the successful issue/rotate response and
is never mapped into an entity, read DTO, log, audit payload or exception.
Because the secret is generated randomly with sufficient entropy, a digest is
appropriate for V1 without adding a second password-style work-factor
configuration. The token-exchange Story must review whether a server-side
pepper or a different protected verifier is required before production remote
agents.

Credential rotation creates a new active credential version and revokes the
previous active credential in one transaction. Credential revocation is
idempotent for an owner and credential that is already revoked, but foreign
credentials are not revealed.

### Audit persistence

Add a dedicated append-only `agent_audit_event` table rather than reusing
`execution_event`. The event stores agent/owner/credential references,
operation, outcome, reason code, correlation ID and timestamp. It stores no raw
credential, token, password, broker secret or unrestricted request payload.

Agent lifecycle mutation and its audit event are committed in one transaction.
If the required audit insert fails, the lifecycle mutation fails closed. Read
operations do not create lifecycle audit events in this Story unless the
existing application convention requires a separate read audit path.

### API boundary

Add owner-authenticated Trading Core endpoints under `/api/v1/agents`:

- `POST /api/v1/agents` - register an agent.
- `GET /api/v1/agents` - list the authenticated user's agents.
- `GET /api/v1/agents/{agentId}` - read one owned agent without credentials.
- `POST /api/v1/agents/{agentId}/suspend` - suspend an owned agent.
- `POST /api/v1/agents/{agentId}/activate` - reactivate a suspended agent.
- `POST /api/v1/agents/{agentId}/revoke` - terminally revoke an owned agent.
- `POST /api/v1/agents/{agentId}/credentials` - issue or rotate a credential;
  return raw material only in this response.
- `POST /api/v1/agents/{agentId}/credentials/{credentialId}/revoke` - revoke
  an owned credential without exposing whether a foreign credential exists.

These endpoints accept only the authenticated human user principal. They do not
accept agent JWTs, service principals, owner IDs or actor headers. Gateway
routing is not changed in this Story unless the current public route contract
requires a narrowly documented route addition; direct Trading Core tests remain
the primary contract validation.

The response DTOs must expose safe metadata only. Credential list/read
responses must never contain the raw credential, digest or private verifier.

## Planned File Changes

### New Trading Core production files

- `trading-core/src/main/java/com/hope/trading/trading_core/agent/model/AgentIdentity.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/model/AgentCredential.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/model/AgentStatus.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/model/AgentCredentialStatus.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/repository/AgentIdentityRepository.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/repository/AgentCredentialRepository.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/application/AgentLifecycleService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/application/AgentCredentialGenerator.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/api/AgentController.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/api/AgentRequest.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/api/AgentResponse.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/api/AgentCredentialResponse.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/api/AgentExceptionHandler.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/infrastructure/AgentAuditEventEntity.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/infrastructure/AgentAuditEventRepository.java`

The exact split between `model` and `infrastructure` must follow existing
Trading Core conventions during implementation. No raw credential field may
appear in an API DTO or log statement.

### Database migration

Add:

- `trading-core/src/main/resources/db/migration/common/V54__agent_identity_and_credential_foundation.sql`

The migration must create owner-scoped agent and credential tables plus the
append-only audit table, foreign keys to `users`, status/version columns,
timestamps, uniqueness constraints and lookup indexes. It must not change
existing user, account, broker-account or execution tables.

Update only the migration assertions required by the new version:

- `trading-core/src/test/java/com/hope/trading/trading_core/migration/PostgresFreshInstallMigrationTest.java`

## Transaction and Concurrency Rules

- Registration, credential issue, rotation, suspension and revocation are
  transactional.
- Credential rotation locks or version-checks the owned agent/credential so
  concurrent rotations cannot leave multiple active credentials contrary to the
  selected invariant.
- Lifecycle methods use optimistic versioning or an equivalent deterministic
  conflict result.
- Audit event insertion occurs in the same transaction as the lifecycle change.
- No external broker, cTrader, MCP or network call occurs in these transactions.

## Security Rules

- Derive `ownerUserId` exclusively from `Authentication` principal `UserDto`.
- Reject or ignore client-supplied owner/actor/agent identity fields according
  to the existing API error policy; never use them for authorization.
- Query individual agents and credentials with owner scope.
- Do not expose whether a credential ID belongs to another user.
- Use sanitized reason codes in errors and audit records.
- Do not log raw request bodies for credential issuance or rotation.
- Do not create any token issuer or agent security filter in this Story.
- Preserve existing service JWT and user JWT behavior unchanged.

## Test Plan

### Domain and application tests

- registration assigns the authenticated owner and rejects null/foreign owner
  attempts;
- status transitions allow only the defined transitions;
- revoked agents cannot be reactivated;
- credential issue returns a raw value once but safe mappings omit it;
- rotation revokes the previous credential and creates one active replacement;
- repeated revocation is safe and foreign resources are not disclosed;
- audit event is created for each lifecycle mutation;
- audit failure rolls back the lifecycle mutation.

### Repository and migration tests

- owner-scoped lookup cannot return another user's agent or credential;
- database foreign keys, uniqueness and status/version constraints hold;
- fresh-install Flyway migration reaches version `54`;
- upgrade from the existing version-53 schema succeeds;
- migration does not mutate existing user/account/broker data.

### API and security tests

- unauthenticated lifecycle calls are rejected;
- User A cannot list, read, rotate, suspend, revoke or activate User B's agent;
- owner IDs and actor headers supplied by the client do not change the owner;
- credential digest, raw secret and token-like values are absent from response
  DTOs and captured logs;
- credential lifecycle endpoints do not accept service or future agent
  principals.

## Validation Commands

Run after implementation:

```text
cd trading-core
mvn -q test
git diff --check
```

If repository scripts require broader validation, run the applicable Trading Core
quality command and report it separately. No deployment or live-provider
validation is applicable.

## Risks and Mitigations

- **Credential disclosure:** one-time response, no raw persistence, safe DTOs,
  sanitized logs and tests.
- **Cross-user access:** owner-scoped repository queries and controller tests.
- **Credential race:** transaction plus version/locking rules and concurrency
  tests.
- **Future token ambiguity:** no agent token implementation here; reserve clear
  credential metadata and distinct future token semantics.
- **Audit loss:** lifecycle mutation and audit event share a transaction and
  fail closed on required audit persistence failure.
- **Migration drift:** extend the existing fresh-install and upgrade tests.

## Implementation Sequence

1. Add domain enums/value validation and persistence entities.
2. Add V54 schema migration and migration assertions.
3. Add repositories with owner-scoped methods and uniqueness constraints.
4. Add secure credential generation/digesting and lifecycle application service.
5. Add sanitized audit persistence in the same transaction.
6. Add user-authenticated owner-scoped controllers and response DTOs.
7. Add domain, persistence, API and security tests.
8. Run targeted validation and inspect the complete diff.

## Human Approval Gate

This plan is complete and requires explicit human approval before implementation.

```text
Story 0214                 APPROVED
Repository Analysis        APPROVED
Implementation Plan        COMPLETED
Implementation Plan Gate    HUMAN APPROVAL REQUIRED
Production Implementation  NOT AUTHORIZED
```

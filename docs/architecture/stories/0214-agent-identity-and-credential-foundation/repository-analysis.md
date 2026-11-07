# Repository Analysis - Story 0214

## Status

Completed - awaiting human approval

## Scope Reviewed

The analysis covers the current Trading Core identity, persistence, ownership,
security and migration conventions relevant to the Agent Identity and Credential
Foundation. No production code, migration, deployment, service restart,
credential operation or cTrader request was performed.

The current Story is:

- `docs/architecture/stories/0214-agent-identity-and-credential-foundation/story.md`

The governing architecture is:

- `docs/architecture/adr/ADR-090-agent-identity-permissions-delegated-authorization.md`

## Existing Implementation

### User identity and ownership

Trading Core persists users as JPA entities in:

- `trading-core/src/main/java/com/hope/trading/trading_core/model/User.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/repository/UserRepository.java`

Users have UUID identifiers, unique username/email values, BCrypt-backed
passwords and a persisted role. Accounts are associated through the existing
user relationship.

User lifecycle behavior is currently concentrated in:

- `trading-core/src/main/java/com/hope/trading/trading_core/service/UserServiceImpl.java`

The current user service is not an appropriate home for agent lifecycle logic:
agent ownership and credential lifecycle should remain a separate security
capability with explicit owner-scoped application methods.

### Authentication boundary

User requests are authenticated by:

- `trading-core/src/main/java/com/hope/trading/trading_core/security/JwtAuthenticationFilter.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/security/SpringSecurityConfig.java`

The current filter builds a `UserDto` principal from the user JWT. Service JWTs
are handled separately. This Story must not merge agent principals into the
existing user principal path. Agent authentication is a later Story and must
use the distinct token type and audience defined by ADR-090.

### Ownership pattern

The strongest existing ownership pattern is explicit owner-scoped application
service and repository access. For example:

- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/application/BrokerAccountService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/application/BrokerAccountRepository.java`

The pattern distinguishes a missing resource from a resource belonging to a
different owner. Agent lifecycle operations should follow the same rule:
derive the owner from the authenticated human principal and query agents by
agent ID plus owner ID for every owner-scoped operation.

### Persistence and migrations

Trading Core uses Flyway migrations under:

- `trading-core/src/main/resources/db/migration/common/`
- `trading-core/src/main/resources/db/migration/postgresql/`

The current fresh-install migration contract is asserted by:

- `trading-core/src/test/java/com/hope/trading/trading_core/migration/PostgresFreshInstallMigrationTest.java`

The current migration history reaches version `53`. A production implementation
will therefore require a new reviewed migration and corresponding migration
test updates. This Story must not apply a migration during the planning stage.

The migration must enforce database-level invariants where practical, including
non-null owner references, unique credential identifiers/fingerprints as
appropriate, valid status representation and indexes for owner-scoped lookup.
The exact schema requires an Implementation Plan because it affects credential
rotation semantics, retention and future token exchange.

### Existing audit/event patterns

Trading Core has append-only execution events:

- `trading-core/src/main/java/com/hope/trading/trading_core/execution/infrastructure/persistence/ExecutionEventEntity.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/infrastructure/persistence/JpaExecutionEventRepository.java`
- `trading-core/src/main/resources/db/migration/common/V1__trading_core_clean_install_baseline.sql`

There are also durable outbox patterns, including risk and challenge
reevaluation outboxes. No generic agent security audit abstraction exists.

The Story requires lifecycle auditability, but the correct audit model must be
chosen deliberately. Reusing `execution_event` would conflate execution
history with security lifecycle events and is not recommended. A dedicated,
append-only agent lifecycle audit table or a narrowly scoped security-event
boundary is likely required, subject to the Implementation Plan.

## Proposed Foundation Boundary

The smallest safe implementation boundary is Trading Core only:

1. `AgentIdentity` persistence/domain model owned by `User.userId`.
2. `AgentCredential` metadata model owned through `AgentIdentity`.
3. Owner-scoped application services and repositories.
4. User-authenticated lifecycle operations only.
5. One-time credential issuance with no later secret recovery.
6. Hash/fingerprint verification material only; no raw credential persistence.
7. Explicit lifecycle statuses and timestamps.
8. Append-only sanitized lifecycle audit events.
9. Unit, repository and security tests for ownership and secrecy.

The following must remain unchanged in this Story:

- user JWT issuance and validation;
- service-to-service JWT delegation;
- Gateway routes and filters;
- MCP adapter authentication;
- AgentGrant evaluation;
- agent token exchange;
- Broker Service and cTrader contracts;
- account synchronization;
- order, position, risk and execution behavior.

## Security Findings and Risks

### Confirmed constraints

- Owner identity must come from the authenticated human principal.
- Client-supplied owner, actor and agent IDs cannot establish authority.
- New agents must have no implicit grants.
- Raw credentials must not be persisted, logged or returned after issue.
- Credential lifecycle must be independently revocable.

### Implementation risks

- A credential lookup design must avoid storing a raw bearer secret while still
  allowing reliable verification and rotation.
- Credential issuance must be atomic with its audit record or fail according to
  an explicit security policy.
- Agent status and credential status must be checked consistently by the future
  token-exchange boundary.
- Physical deletion could destroy security provenance; revocation/retention is
  safer unless a separate data-retention policy requires deletion.
- Public lifecycle endpoints must not be added without defining the current
  user-authenticated controller contract and error semantics.
- The current shared user JWT and issuer-validation weaknesses are outside this
  Story and must not be copied into the agent-token design.

## Required Implementation Plan Decisions

An Implementation Plan is required before code because the Story introduces
persistent security state and a new credential lifecycle. It must settle:

- exact package/domain/application/infrastructure placement;
- entity versus aggregate boundaries;
- database schema, indexes, constraints and migration version;
- credential generation, hashing, fingerprinting and one-time response rules;
- lifecycle transition matrix and optimistic concurrency behavior;
- lifecycle endpoint surface and response/error contracts;
- audit event schema, durability and failure policy;
- retention and revocation semantics;
- test strategy, including migration and secret-safety assertions;
- compatibility with later agent token exchange without issuing tokens in this
  Story.

## Validation Plan

The implementation should include, at minimum:

- domain tests for status transitions and immutable ownership;
- repository tests for owner-scoped reads and cross-user denial;
- credential secrecy tests proving raw values are absent from persistence,
  DTOs, logs and later read responses;
- rotation/revocation tests proving old credentials cannot remain active;
- migration tests extending the current version-53 fresh-install contract;
- concurrency tests for duplicate registration/rotation where relevant;
- authenticated controller tests if public lifecycle endpoints are included;
- Trading Core targeted Maven tests;
- `git diff --check`.

No live broker, cTrader, MCP or Gateway E2E validation is applicable to this
foundation Story.

## DevLog Context

DevLog was queried for Story 0214 context. The response did not provide usable
Story-specific evidence and reported a grounding failure for the bounded file
scope. Repository sources and accepted ADRs were therefore treated as
authoritative. This does not block the local Repository Analysis, but the
limitation should be retained for traceability.

## Analysis Conclusion

Story 0214 is technically coherent and can proceed to an Implementation Plan.
Implementation must not begin yet.

Current workflow state:

```text
Story 0214                 DRAFT
Repository Analysis        COMPLETED
Repository Analysis Gate   HUMAN APPROVAL REQUIRED
Implementation Plan        NOT STARTED
Production Implementation  NOT AUTHORIZED
```

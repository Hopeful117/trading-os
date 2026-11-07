# Repository Analysis - Story 0215

## Status

Completed - approved by human engineer

## Baseline

- Repository: `Hopeful117/trading-os`
- Branch: `main`
- HEAD: `d1582d570bd29b6a330bfdd89ae9d8c9797d5f2a`
- Worktree: clean at analysis time
- Predecessor: Story `0214`, merged at the baseline commit
- Next available Story number: `0215`
- Current Trading Core migration: V54

No production code, migration, runtime database, deployment, restart, broker
request or cTrader request was performed while preparing this analysis.

## Governing Architecture

The accepted source of architecture is:

- `docs/architecture/adr/ADR-090-agent-identity-permissions-delegated-authorization.md`

ADR-090 defines `AgentGrant` as durable explicit authorization containing an
agent, permission, resource type, resource scope, status, timestamps, granting
user and grant version. It requires default deny, explicit resource scope,
independent resource ownership checks and fail-closed behavior. It explicitly
excludes agent self-granting, cross-user administration and execution
permissions.

Story 0214 is the predecessor and explicitly excludes `AgentGrant`, permission
evaluation, MCP changes and agent authentication.

DevLog was queried for bounded context. The request failed with
`GROUNDING_REQUESTED_FILE_SCOPE`, so no usable Story-specific DevLog evidence
was available. Current repository sources and ADR-090 remain authoritative.

## Current Identity and Ownership Model

### Human authentication

Trading Core authenticates human JWTs through:

- `trading-core/src/main/java/com/hope/trading/trading_core/security/JwtAuthenticationFilter.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/security/SpringSecurityConfig.java`

Controllers derive the owner from the authenticated `UserDto` principal. This
Story must preserve the existing user principal and must not introduce an agent
principal or token validation.

### Agent identity

Story 0214 provides:

- `agent.model.AgentIdentity`
- `agent.model.AgentStatus`
- `agent.repository.AgentIdentityRepository`
- `agent.application.AgentLifecycleService`
- `agent.api.AgentController`
- `agent.infrastructure.AgentAuditEventEntity`

`AgentIdentity.ownerUserId` is immutable. The existing owner-scoped repository
methods and controller pattern are suitable for grant administration.

Relevant lifecycle states are `ACTIVE`, `SUSPENDED` and `REVOKED`. Suspended
and revoked agents must have no effective grants. Revoked is terminal.

### Broker accounts

`BrokerAccount` is Trading Core-owned and contains an immutable `ownerId` and
internal UUID. `BrokerAccountRepository` already supports:

- `findByIdAndOwnerId`
- `findByIdAndOwnerIdForUpdate`
- owner-scoped listing

Account grants must use this internal UUID, not an external cTrader account
number or provider registration reference.

### Markets and research

Trading Core already uses canonical UUID market IDs through
`MarketDataClient.findById`. Market records are not user-owned, so market scope
must still require an explicit canonical market ID and validate target
existence through a narrow application port/client rather than treating a UUID
as proof of existence.

The current repository contains research execution and research workflow
contracts, but no single Trading Core-owned `ResearchScope` aggregate was found.
The implementation must not invent a wildcard research scope. `RESEARCH_SCOPE`
may therefore be represented as a typed explicit ID only if an existing stable
scope contract can validate it; otherwise grant creation must reject that
resource type and preserve `research:run` in the catalog for a later contract.

## Persistence and Migration

Trading Core uses Flyway migrations in:

- `trading-core/src/main/resources/db/migration/common/`
- `trading-core/src/main/resources/db/migration/postgresql/`

The next migration is V55. It must be additive and must not modify legacy
provider migrations or apply to the shared runtime database.

The existing V54 audit table is append-only in intent but currently stores only
lifecycle fields. Grant audit data will require either additive nullable grant
columns or a dedicated grant audit table. A dedicated table is safer for
preserving existing lifecycle contracts and allows permission/resource fields
without weakening V54 constraints.

## Security and API Conventions

Existing management controllers:

- use `/api/v1/...` routes;
- derive ownership from `Authentication` and `UserDto`;
- return `ResponseEntity`;
- use owner-scoped service/repository methods;
- map foreign or missing resources to sanitized not-found responses.

Grant endpoints must follow the same conventions and must not accept owner IDs,
actor IDs or agent credentials as authority. No public endpoint may evaluate an
arbitrary supplied agent ID as an authenticated agent.

## Audit Boundary

`AgentAuditEventEntity` and `AgentAuditEventRepository` provide a suitable
existing security audit boundary. The current schema does not expose permission,
resource or decision fields. The plan should extend the audit record in a
backward-compatible way or use a dedicated append-only authorization audit
entity. Grant mutation and required audit insertion must share a transaction;
authorization denial audit failure must not turn an authorization denial into an
allow decision.

## Proposed Domain Boundary

Trading Core only:

1. Stable `AgentPermission` catalog.
2. Typed `AgentResourceType` and explicit `AgentResourceScope` validation.
3. `AgentGrant` domain/entity and repository.
4. Owner-scoped grant management service.
5. Trusted internal `AgentAuthorizationService`.
6. Human-authenticated grant controller and safe DTOs.
7. Migration, constraints, indexes and security audit persistence.
8. Unit, application, persistence, migration and controller security tests.

The following remain unchanged:

- human JWT issuance and validation;
- service JWT delegation;
- agent credential issuance and token semantics;
- MCP adapter and Gateway;
- Broker Service and cTrader contracts;
- account synchronization behavior;
- order, position, risk and execution behavior.

## Risks and Controls

- **Cross-user grants:** resolve the agent and target resource with owner-scoped
  queries inside the application transaction.
- **Cross-agent leakage:** every authorization query includes the exact agent
  ID and permission/resource tuple.
- **Wildcard escalation:** require non-null explicit scope and reject unsupported
  scope representations.
- **Duplicate grants:** enforce a database uniqueness key for active effective
  authorizations and handle conflict deterministically.
- **Stale grants:** evaluate status and expiration against an injected clock on
  every authorization decision.
- **Agent lifecycle drift:** authorization checks current agent status and does
  not rely on cached token claims.
- **Audit leakage:** store identifiers and sanitized reason codes only; never
  store credentials, JWTs or broker payloads.
- **Market/research ambiguity:** validate only resource types with existing
  target semantics; deny unsupported scope requests rather than inventing
  wildcard behavior.

## Required Plan Decisions

The Implementation Plan must settle:

- exact grant aggregate/entity and package placement;
- permission/resource compatibility matrix;
- canonical scope validation ports for market and research resources;
- V55 schema, foreign keys, indexes and active-grant uniqueness strategy;
- grant status transitions and expiration semantics;
- optimistic/pessimistic concurrency behavior;
- grant management endpoint and error contracts;
- authorization decision and denial-reason model;
- audit schema and failure policy;
- migration and isolated concurrency test strategy.

## Workflow State

```text
Story 0215                 DRAFT
Repository Analysis        COMPLETED
Repository Analysis Gate   HUMAN APPROVAL REQUIRED
Implementation Plan        NOT STARTED
Production Implementation  NOT AUTHORIZED
```

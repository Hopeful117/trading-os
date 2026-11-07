# Implementation Plan - Story 0215

## Status

Approved by human engineer

## Objective

Implement the Trading Core authorization foundation for explicit, resource-scoped
agent grants while preserving the existing human JWT and service JWT boundaries.
The implementation does not authenticate agents, issue agent tokens, alter MCP,
delegate to Broker Service or execute broker operations.

## Design Decisions

### Permission catalog

Add a typed `AgentPermission` enum with stable persisted identifiers:

```text
ACCOUNTS_READ   -> accounts:read
ACCOUNTS_SYNC   -> accounts:sync
POSITIONS_READ  -> positions:read
MARKETS_READ    -> markets:read
RISK_READ       -> risk:read
RESEARCH_RUN    -> research:run
PLANS_CREATE    -> plans:create
```

The enum is the only source of accepted V1 permission identifiers. Execution,
credential-management and self-administration identifiers are not accepted.

### Resource types and compatibility

Add a typed `AgentResourceType`:

- `BROKER_ACCOUNT`
- `MARKET`
- `RESEARCH_SCOPE`

Use this compatibility matrix:

| Permission | Resource type | Scope |
| --- | --- | --- |
| `accounts:read` | `BROKER_ACCOUNT` | exact internal broker-account UUID |
| `accounts:sync` | `BROKER_ACCOUNT` | exact internal broker-account UUID |
| `positions:read` | `BROKER_ACCOUNT` | exact internal broker-account UUID |
| `risk:read` | `BROKER_ACCOUNT` | exact internal broker-account UUID |
| `plans:create` | `BROKER_ACCOUNT` | exact internal broker-account UUID |
| `markets:read` | `MARKET` | exact canonical market UUID |
| `research:run` | `RESEARCH_SCOPE` | exact validated research-scope UUID |

The request must provide a non-null resource ID. No wildcard, owner-wide scope,
provider account number or missing scope is accepted.

`BrokerAccount` target validation is local and owner-scoped. Market target
validation uses a narrow existing `MarketDataClient` lookup/application port.
Research scope creation is enabled only if a stable existing scope validator is
identified during implementation; otherwise the typed permission remains
catalogued and the grant request is rejected with an unsupported-scope reason.

### Grant model

Add `AgentGrant` as an immutable-owner JPA entity/domain model with:

- UUID `id`;
- UUID `agentId`;
- `AgentPermission permission`;
- `AgentResourceType resourceType`;
- UUID `resourceId`;
- `AgentGrantStatus status`;
- `Instant createdAt`;
- `Instant expiresAt`;
- `Instant revokedAt`;
- sanitized `revocationReason`;
- UUID `grantedByUserId`;
- positive `grantVersion`.

Owner identity is derived from `AgentIdentity.ownerUserId`; `grantedByUserId`
records the authenticated human actor for audit/provenance and must equal the
resolved owner for this Story.

### Lifecycle and effective authorization

Supported states are `ACTIVE`, `REVOKED` and `EXPIRED`. Creation is active
unless the supplied expiration is already invalid, in which case it is
rejected. A grant is effective only when:

```text
agent exists
AND agent belongs to the owner resolved by the trusted application boundary
AND agent status == ACTIVE
AND grant status == ACTIVE
AND grant.expiresAt is null OR grant.expiresAt > clock.instant()
AND permission/resource type match
AND resource ID is exact
AND resource belongs to the agent owner when ownership applies
```

Authorization returns a typed decision containing `allowed` and a sanitized
internal denial reason. It accepts a trusted internal agent identity reference,
not an external caller-provided principal.

### Concurrency

The database will enforce one active effective grant per:

```text
(agent_id, permission, resource_type, resource_id, active_marker)
```

Use an `active_marker` that is `1` for active grants and `NULL` for revoked or
expired grants, combined with a unique index. The application handles unique
constraint conflicts as a deterministic duplicate-grant conflict. Revocation
updates the status and clears the active marker in the same transaction.

Grant versioning and JPA optimistic locking protect concurrent lifecycle updates.

### Audit

Extend the existing agent audit boundary with a dedicated
`agent_authorization_audit_event` entity/table rather than changing existing
lifecycle event semantics. Store:

- event ID;
- agent ID and owner user ID;
- granting actor user ID when applicable;
- permission;
- resource type and resource ID;
- operation/decision/outcome;
- sanitized reason code;
- correlation ID;
- timestamp;
- grant ID when applicable.

Grant mutations and their required audit event are one transaction. Authorization
denials may attempt an audit write but must always remain denied if audit writing
fails.

## API Design

Add under the existing `AgentController` boundary:

- `POST /api/v1/agents/{agentId}/grants`
- `GET /api/v1/agents/{agentId}/grants`
- `GET /api/v1/agents/{agentId}/grants/{grantId}`
- `POST /api/v1/agents/{agentId}/grants/{grantId}/revoke`

Use request/response records with bean validation. The create request contains
only permission, resource type, resource ID and optional expiration/revocation
metadata. It does not contain owner ID, actor ID, agent credential or token.

Foreign agent/grant/resource lookups return the existing sanitized not-found
contract. Invalid compatibility, scope, expiry and duplicate requests return
the established validation/conflict shape without exposing another user's
resource existence.

No endpoint evaluates arbitrary agent permissions or accepts agent principals.

## Planned Files

### New production files

- `trading-core/src/main/java/com/hope/trading/trading_core/agent/model/AgentPermission.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/model/AgentResourceType.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/model/AgentGrantStatus.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/model/AgentGrant.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/repository/AgentGrantRepository.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/application/AgentAuthorizationService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/application/AgentGrantService.java`
- grant request/response and exception classes under `agent/api`
- grant authorization audit entity/repository under `agent/infrastructure`
- `trading-core/src/main/resources/db/migration/common/V55__agent_permissions_and_grants.sql`

### Existing files to update

- `agent/api/AgentController.java`
- `agent/api/AgentExceptionHandler.java`
- `agent/repository/AgentIdentityRepository.java` if lifecycle locking/query
  support is required
- `market_data/apiClient/MarketDataClient.java` only if a narrow target
  existence method is required by the existing contract
- `migration/PostgresFreshInstallMigrationTest.java` for V55 assertions

No Gateway, MCP, Broker Service, cTrader or Angular files are in scope.

## Test Plan

### Domain

- permission identifiers and compatibility matrix;
- explicit scope and invalid scope rejection;
- expiration and revocation transitions;
- terminal agent lifecycle denial;
- stable mapping and safe DTOs.

### Application

- owner grant creation/list/inspect/revoke;
- cross-user agent and resource denial;
- cross-agent isolation;
- wrong-account denial;
- duplicate and concurrent creation;
- fixed-clock expiration;
- authorization denial reasons;
- audit failure does not allow access.

### Persistence

- V55 fresh-install and upgrade migration;
- foreign keys to agent identity, users and broker accounts where feasible;
- indexes for owner, agent and effective lookup;
- active uniqueness and revocation persistence;
- isolated PostgreSQL concurrency test.

### REST security

- owner success;
- anonymous denial;
- foreign agent/resource not-found behavior;
- invalid grant request rejection;
- revoked/expired grant lifecycle;
- no agent or service principal grant administration.

### Regression

- Story 0214 agent identity, credential, lifecycle, controller and migration
  tests;
- relevant Trading Core JWT and broker synchronization security tests.

## Validation Commands

```text
cd trading-core
./mvnw -q -Dtest=<focused grant and regression tests> test
./mvnw -q test
cd ..
git diff --check
```

If the full Trading Core suite is blocked by unrelated pre-existing failures,
report the exact command and failure without weakening or rewriting tests.

## Implementation Sequence

1. Add Story 0215, repository analysis and this plan.
2. Add typed permission/resource/status models and compatibility validation.
3. Add V55 schema, constraints and indexes with isolated migration assertions.
4. Add grant entity/repository and owner/resource validation ports.
5. Add grant lifecycle service and trusted authorization service.
6. Add sanitized authorization audit persistence.
7. Add human owner-scoped REST endpoints and error handling.
8. Add domain, application, persistence, concurrency and REST security tests.
9. Run focused and broader validation, inspect the complete diff and reconcile
   documentation.

## Non-Goals Preserved

This plan does not implement agent authentication, token exchange, MCP
authorization, Broker Service delegation, synchronization delegation, execution
permissions, credential provisioning or shared-runtime changes.

## Approval Gate

```text
Repository Analysis        APPROVED
Repository Analysis Gate   SATISFIED
Implementation Plan        APPROVED
Implementation              AUTHORIZED
```

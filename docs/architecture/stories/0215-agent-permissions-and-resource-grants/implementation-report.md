# Implementation Report - Story 0215

## Delivered

- Added the typed V1 agent permission catalog with stable persisted string
  identifiers.
- Added explicit `BROKER_ACCOUNT`, `MARKET` and `RESEARCH_SCOPE` resource
  types with compatibility and ownership validation.
- Added durable `AgentGrant` lifecycle state with active, revoked and expired
  states, fixed-clock expiration evaluation and optimistic versioning.
- Added owner-scoped human grant management endpoints:
  - `POST /api/v1/agents/{agentId}/grants`;
  - `GET /api/v1/agents/{agentId}/grants`;
  - `GET /api/v1/agents/{agentId}/grants/{grantId}`;
  - `POST /api/v1/agents/{agentId}/grants/{grantId}/revoke`.
- Added the trusted internal `AgentAuthorizationService` with default-deny
  evaluation for unknown, inactive, revoked, expired and incompatible scopes.
- Added sanitized grant-change and authorization audit persistence without
  credentials, JWTs, private keys or broker secrets.
- Added Flyway migration `V55__agent_permissions_and_grants.sql` with foreign
  keys, lifecycle constraints, active-scope uniqueness and audit indexes.
- Added unit, controller, migration, concurrency and Story 0214 regression
  coverage, including fail-closed unauthenticated grant-management checks.

## Validation

- `./mvnw -q -DskipTests compile test-compile`: passed.
- `./mvnw -q test`: passed with the extended validation timeout; the suite
  completed successfully. Expected negative-path integration-test logs were
  emitted during the run, but Maven returned success.
- Focused Story 0215 grant, authorization, controller and migration tests:
  passed, including the final unauthenticated grant-management test.
- Story 0214 agent lifecycle regression tests: passed.
- PostgreSQL fresh-install migration validation reached V55 successfully.
- `git diff --check`: passed.
- No commit, push, merge or deployment was performed.
- No shared runtime database, deployment, restart, broker, cTrader or
  credential operation was performed.

## Deliberate Limitations

- `RESEARCH_SCOPE` remains catalogued but unsupported because Trading Core does
  not currently expose a stable user-owned research-scope aggregate.
- Authorization is an internal trusted service only. Agent authentication,
  Spring Security agent principals, token exchange, MCP authorization and
  OpenCode configuration remain outside this Story.
- Broker delegation, order execution and position mutation permissions remain
  explicitly excluded.
- The migration was validated in isolated test databases only and was not
  applied to any shared runtime.

## Known Follow-Up Work

- Integrate the authorization service into future agent-facing read workflows
  once an authenticated agent principal and trusted internal caller contract
  are approved.
- Add a durable research-scope resource contract before enabling
  `research:run` grants.
- Perform a separate human code review and commit the implementation.

## Safety

This implementation does not authenticate agents, issue tokens, call MCP or
OpenCode, access broker credentials, place orders, mutate positions or override
deterministic risk controls.

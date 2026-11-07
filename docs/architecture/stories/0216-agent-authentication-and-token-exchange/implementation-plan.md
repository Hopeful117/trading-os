# Implementation Plan - Story 0216

## Status

Completed - awaiting human approval

## Objective

Implement a Trading Core-owned agent credential exchange and strict agent JWT
validation in Trading Core and Gateway without changing human or service JWT
semantics, adding MCP authorization, or provisioning runtime secrets.

## Design Decisions

### Token exchange boundary

Add `POST /api/v1/agents/token` to Trading Core and expose only that path through
the Gateway's existing public route table. The request contains only the opaque
credential. The endpoint is unauthenticated at the HTTP layer because the
credential is the authentication factor; it never accepts a human JWT, owner ID,
agent ID, role, audience or lifetime.

The exchange service will:

1. Validate request shape without logging the body.
2. Digest the supplied credential using the same verifier used by
   `AgentCredentialGenerator`.
3. Find the credential by digest and return one generic failure for all unknown,
   malformed, revoked or expired cases.
4. Resolve `AgentIdentity` from the credential's persisted `agentId`.
5. Require an `ACTIVE` agent and an active, unexpired credential.
6. Issue the agent JWT from trusted persisted identity and configuration.
7. Record a sanitized authentication outcome with a correlation ID.

The endpoint returns an explicit access token, token type, expiration timestamp
and lifetime. It never returns credential metadata or a JWT claim projection.

### Agent JWT contract

Use a dedicated HMAC signing secret distinct from `security.jwt.secret` and all
service JWT secrets. This is the smallest compatible mechanism because Gateway
and Trading Core currently share configured symmetric JWT infrastructure. The
secret is configuration-only and is not generated, committed, provisioned or
printed by this Story.

Configuration properties in both services:

- `security.agent-jwt.secret`
- `security.agent-jwt.issuer`, defaulting only in tests, not production
- `security.agent-jwt.audience`
- `security.agent-jwt.expiration-seconds`, constrained to a short maximum of
  five minutes

Issued claims:

```text
iss             configured dedicated agent issuer
sub             agent identity UUID
aud             configured Gateway/application audience
iat             issuance instant
exp             issuance plus configured short lifetime
jti             random UUID per token
type            agent
agent_id       persisted agent identity UUID
owner_id       persisted owner UUID
credential_id  persisted credential UUID
credential_version persisted credential version
```

No role, permission, grant, service identity or client-provided claim is copied
into the token. The owner claim is informational context derived from trusted
persistence; authorization still resolves the owner from `AgentIdentity`.

### Principal separation

Add `AgentPrincipal(agentId, ownerUserId, credentialId, credentialVersion,
tokenId)` in Trading Core and Gateway equivalents where required. Agent
authentication must not use `UserDto`, `UserAuthenticationDto`, human roles,
`ServicePrincipal` or `ROLE_SERVICE`.

Add dedicated validation services/filters ahead of the human filters. They must:

- parse only with the dedicated agent key;
- require issuer, audience, `type=agent`, subject, agent ID, owner ID, UUID
  formats, issued-at, expiration, token ID and credential metadata;
- reject human tokens, service tokens and forged claims;
- never fall through to the human filter after an agent-token candidate fails;
- remove/ignore client identity headers rather than trusting them.

The Trading Core validator performs a live lookup of agent and credential status,
expiration and credential version before establishing an agent principal. This
makes agent or credential revocation effective for subsequent protected
operations after issuance. Gateway performs strict cryptographic validation but
does not gain a database dependency; Trading Core remains the live revocation
authority.

### Access policy

Agent authentication is not authorization. Existing human management endpoints,
service-only endpoints and broker/execution endpoints remain unavailable to
agent principals. Add an explicit deny-by-default agent access policy in the
affected security boundary so that no existing endpoint becomes agent-capable
merely because it accepts an authenticated principal.

The policy will provide an explicit extension point for later stories to opt in
agent-capable read workflows. No current endpoint is opted in by this Story.
`AgentAuthorizationService` remains the source of current resource grants; no
permissions are embedded in tokens and no MCP authorization is added.

### Authentication audit

Add migration `V56__agent_authentication_audit.sql` with an append-only audit
table whose agent, credential and owner references are nullable for unknown or
unresolvable failures. Store only safe UUIDs where known, credential version,
operation, outcome, sanitized reason code, correlation ID and timestamp.

Successful exchange, rejected revoked/expired credentials, disabled-agent
rejection and generic invalid exchange outcomes are auditable without raw
credential, token, request body or claim material. Authentication failure audit
writes are bounded and must not turn credential guessing into unbounded durable
write amplification. A failed audit write must not reveal details externally.

### Anti-brute-force boundary

No distributed rate-limiter infrastructure exists in the repository. Add a
bounded, configurable per-process exchange limiter as defense in depth and
document that production deployment must additionally enforce ingress-level
rate limiting. The limiter keys on a coarse client/request identity and never
stores the raw credential. It is not presented as a distributed revocation or
security guarantee.

## Planned File Changes

### Trading Core

- Agent token request/response DTOs and generic exchange exception handling.
- Agent exchange application service.
- Dedicated agent JWT properties, issuer and validator.
- `AgentPrincipal` and live lifecycle validation support.
- Agent authentication filter and deny-by-default access policy.
- Authentication audit entity/repository and V56 migration.
- Spring Security chain updates with token endpoint exception and filter order.
- Unit, controller, persistence, JWT and revocation tests.

### Gateway

- Dedicated agent JWT properties, validator and principal.
- Reactive agent authentication filter and deny-by-default policy.
- Removal of client-controlled actor identity for agent principals.
- Explicit `/api/v1/agents/token` route to Trading Core.
- Gateway JWT separation, route and principal regression tests.

### Documentation

- Update the Story implementation report with the exact endpoint, claims,
  revocation policy, configuration prerequisites and validation results.
- Update the relevant OpenAPI/controller documentation if generated or
  repository-authored API documentation exposes the endpoint.
- Record that the MCP adapter remains on its existing human JWT contract and is
  intentionally deferred.

## Transaction and Failure Rules

- Token issuance reads credential/agent state transactionally and updates only
  safe `lastUsedAt` metadata if the existing lifecycle policy supports it.
- Authentication failures use one external response shape and status; no
  unknown-versus-revoked distinction is exposed.
- No raw credential, token, password, signing secret or unrestricted request
  payload reaches logs, exceptions, audit records or response DTOs.
- Audit failure is observable internally but does not disclose credential state.
- No broker, cTrader, MCP, service-to-service or external network call occurs in
  token exchange.

## Test Plan

### Credential and exchange tests

- valid active credential exchanges successfully;
- missing, malformed and unknown credentials are generic failures;
- revoked and expired credentials fail;
- suspended and revoked agents fail;
- credentials belonging to another agent cannot be selected by request input;
- correct subject, owner, agent type, audience, issuer, expiration, JTI and
  credential version are issued;
- roles, permissions, arbitrary audience and arbitrary lifetime are absent or
  rejected;
- raw credentials and JWT values do not appear in logs, errors or audit rows.

### JWT and principal tests

- valid agent JWT is accepted by dedicated validators;
- expired, invalid-signature, wrong-issuer, wrong-audience, missing-type,
  forged-owner and forged-agent claims are rejected;
- human token cannot enter the agent path;
- agent token cannot become a human principal;
- agent token cannot become a service principal;
- revoked agent or credential after issuance is rejected by Trading Core live
  validation;
- current human and service JWT tests remain passing.

### Security integration tests

- token endpoint requires no human JWT but accepts only the credential body;
- human agent management rejects agent principals;
- service-only and broker/execution endpoints reject agent principals;
- client `X-Actor-Id`, `X-Owner-Id` and `X-Agent-Id` headers cannot establish
  identity;
- Gateway routes only the intended exchange path and does not expose internal
  routes;
- grant revocation remains authoritative through existing authorization tests.

### Persistence and regression tests

- V56 fresh-install and upgrade migration tests pass in isolated databases;
- authentication audit supports unknown failures without secret material;
- Story 0214 and 0215 tests pass unchanged;
- applicable Trading Core and Gateway security tests pass.

## Validation Commands

```text
cd trading-core && ./mvnw -q test
cd gateway && ./mvnw -q test
./scripts/quality-verify.sh   # when applicable to changed modules
git diff --check
```

No deployment, service restart, shared database migration, credential
provisioning, JWT extraction/injection, cTrader call, broker order or MCP agent
authorization operation is permitted.

## Documentation and Security Limitations

- Dedicated HMAC key distribution is a deployment prerequisite and must be
  supplied by an approved secret-management process outside this Story.
- Asymmetric signing/JWKS remains a future hardening path and is not silently
  introduced because the current services have no shared key-publication
  boundary.
- Gateway stateless validation does not claim immediate revocation; Trading Core
  live lifecycle validation provides the effective revocation boundary for
  protected operations.
- MCP migration from `TRADING_OS_JWT` to agent credentials is deferred.

## Human Approval Gate

This plan is complete and requires explicit human approval before implementation.

```text
Story 0216                 APPROVED
Repository Analysis        APPROVED
Implementation Plan         COMPLETED
Implementation Plan Gate    HUMAN APPROVAL REQUIRED
Production Implementation   NOT AUTHORIZED
```

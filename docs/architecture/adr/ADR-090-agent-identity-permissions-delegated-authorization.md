# ADR-090 - Agent Identity, Permissions and Delegated Authorization

## Status

Accepted - human architecture approval completed

## Date

2026-11-07

## Decision Makers

- Trading OS Architecture
- Human Engineer
- Project Owner

## Decision Type

Multi-user agent identity, machine authentication and resource-scoped delegated authorization

## Related ADRs

- ADR-001 - Trading OS Vision and Product Philosophy
- ADR-024 - Broker Credential Management and Account Connection
- ADR-029 - Execution Domain Architecture
- ADR-030 - Broker Service Architecture
- ADR-041 - Human-Controlled Trade Execution and Financial Command Safety
- ADR-044 - Service-to-Service Trust and Actor Propagation
- ADR-073 - cTrader Authentication and Explicit External Account Binding
- ADR-074 - cTrader Authoritative READ-ONLY Synchronization Boundary
- ADR-078 - PAPER and LIVE Execution Boundary
- ADR-085 - Broker Equity Resolution and Provenance Contract
- ADR-088 - MCP-First Agent Interaction Boundary
- ADR-089 - Native Trading Research Agent and MCP Responsibility Boundary

---

## 1. Context

Trading OS already supports human users, user-owned accounts, Spring Security,
JWT authentication, Gateway routing, service-to-service JWT delegation and a
local stdio MCP adapter used by OpenCode.

The current MCP adapter receives a short-lived human JWT through
`TRADING_OS_JWT` and forwards it to public Gateway contracts. This is suitable
for the current local read-only prototype, but it is not a durable identity
model for multiple user-owned agents. It also creates an operational failure
mode: OpenCode cannot perform an authorized user operation when no legitimate
user JWT is available.

The current synchronization path is:

```text
OpenCode / MCP adapter
        -> Gateway
        -> Trading Core
        -> Broker Service
        -> cTrader
```

The V2 account synchronization endpoint is currently user-authenticated in
Trading Core. Trading Core derives the user from the authenticated principal,
checks the broker-account owner, and delegates the provider snapshot request to
Broker Service using a service JWT with a delegated actor. Broker Service owns
provider connectivity and the cTrader request safety guard.

The existing architecture already establishes important boundaries:

- Gateway is the supported external ingress.
- Trading Core owns users, accounts, ownership and user-specific workflows.
- Broker Service owns broker credentials, provider connectivity and broker
  facts.
- MCP is an anti-corruption layer over public Gateway contracts and must not
  access internal endpoints, repositories, credentials or providers.
- AI and agent clients are advisory or orchestration clients, not risk or
  execution authorities.
- Human authority, deterministic risk controls, broker safety, idempotency and
  reconciliation remain mandatory.

The new model must extend these boundaries rather than create a privileged
OpenCode exception.

## 2. Problem Statement

Trading OS needs to authenticate and authorize technical agents as distinct
principals while preserving the identity of the user who owns them. The model
must support multiple users and multiple agents per user, explicit resource
scope, credential rotation and revocation, auditable authorization decisions,
local OpenCode use and future remote agents.

A valid agent credential must not imply access to every resource owned by its
user. A valid service credential must not imply user ownership. A client-
controlled owner, actor or resource identifier must never establish authority.

The design must also preserve the distinction between:

```text
authenticated agent       Which technical principal is calling?
resource owner            Which user owns the target resource?
calling service            Which Trading OS service is making the hop?
human approver             Which human explicitly approved a sensitive action?
business authorization     May this operation occur under domain rules?
provider safety            May this broker request occur under cooldown/lease rules?
```

## 3. Goals

This ADR establishes a first-class architecture for:

1. Multiple users and multiple agents per user.
2. Explicit ownership of every agent.
3. Cryptographically authenticated agent requests.
4. Default-deny, permission-based authorization.
5. Resource-scoped grants, especially broker-account scope.
6. Agent credential rotation and revocation without persisting raw secrets.
7. Agent-specific auditability and correlation.
8. MCP integration without giving MCP direct domain authority.
9. Safe delegation from Gateway/MCP through Trading Core to Broker Service.
10. Human approval for sensitive non-trading actions where appropriate.
11. Compatibility with the existing service JWT and actor propagation model.
12. A future migration path to a standards-based authorization server if the
    deployment model requires one.

## 4. Non-Goals

This ADR does not authorize or implement:

- order placement, modification or cancellation by an agent;
- position mutation or broker execution by an agent;
- an agent risk override or risk-engine decision;
- agent self-registration or self-granting permissions;
- cross-user administration;
- direct MCP access to Broker Service, cTrader or internal APIs;
- remote MCP deployment or MCP OAuth implementation;
- a new general-purpose authorization server in V1;
- replacement of the existing user authentication flow;
- replacement of the cTrader account-binding or snapshot safety model;
- production code, migrations, deployments or credentials in this ADR.

## 5. Existing Architecture Audit

### 5.1 User identity and JWTs

Trading Core issues user JWTs from:

- `trading-core/src/main/java/com/hope/trading/trading_core/controller/UserController.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/security/JwtServiceImpl.java`

Gateway and Trading Core validate user JWTs locally through their respective
security filters and configurations. Broker Service validates user and service
credentials through:

- `broker-service/src/main/java/com/hope/trading/broker_service/security/BrokerJwtAuthenticationFilter.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/security/ServiceJwtAuthenticationFilter.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/security/BrokerPrincipal.java`

The current user JWT contains a user subject and user claims. Existing user JWT
validation uses a shared symmetric secret across several services. The parser
does not currently require the configured issuer during claim parsing. These
are existing security risks and are not silently changed by this ADR, but the
agent-token design must use a distinct token type and audience so an agent
credential cannot be confused with a user credential.

### 5.2 Ownership and service delegation

Trading Core owns account and broker-account ownership checks. The V2
synchronization controller derives the owner from the authenticated principal:

- `trading-core/src/main/java/com/hope/trading/trading_core/controller/AccountSynchronizationV2Controller.java`

Trading Core calls Broker Service through:

- `trading-core/src/main/java/com/hope/trading/trading_core/config/BrokerServiceFeignConfiguration.java`

The service JWT carries a delegated actor. Broker Service validates the service
caller and constructs a `BrokerPrincipal` from the trusted delegated actor.
This follows ADR-044 and remains the internal hop model.

### 5.3 MCP integration

The current MCP adapter is:

- `mcp-adapter/app/client.py`
- `mcp-adapter/app/server.py`
- `mcp-adapter/README.md`

It runs over local stdio, sends `TRADING_OS_JWT` to public Gateway endpoints,
does not persist the token, and exposes bounded market and research tools. It
does not call internal endpoints, cTrader, brokers, repositories or databases.

The adapter currently has no independent agent identity. Its effective
authorization is exactly the authorization of the supplied human JWT.

### 5.4 Synchronization authority

The V2 synchronization path is:

```text
POST /api/v2/accounts/{brokerAccountId}/synchronize
    -> Trading Core owner and account checks
    -> Broker Service internal snapshot contract
    -> cTrader provider observation
    -> Broker Service snapshot result
    -> Trading Core completeness and freshness validation
```

The smallest safe extension is therefore to authenticate the agent at the
external Gateway/MCP boundary, resolve the owner and grant in Trading Core,
and preserve the existing Trading Core to Broker Service service delegation.
Broker Service must not become the owner of agent grants.

## 6. Alternatives Considered

### 6.1 Reuse a human user JWT

Rejected. It makes an agent indistinguishable from a human session, prevents
independent agent revocation, does not support a durable agent audit identity,
and encourages unsafe browser-token borrowing. It also conflicts with the
requirement that OpenCode is a client type rather than a privileged user.

### 6.2 OAuth 2.0 Client Credentials immediately

Deferred. This is a strong future option for remote agents and centralized
scopes, but Trading OS currently has no authorization server or resource-server
deployment boundary for it. Introducing one before the ownership and grant
model exists would add a new availability-critical platform dependency.

The conceptual model remains compatible with OAuth scopes and audiences. A
future authorization server may replace the V1 token issuer without changing
AgentIdentity, AgentGrant or resource authorization semantics.

### 6.3 Give OpenCode an agent signing key

Rejected. OpenCode must never receive a Trading OS token-signing key or service
signing key. Possession of a signing key would permit minting arbitrary agent
tokens and would collapse issuer trust into the local client.

### 6.4 Asymmetric agent key pair with direct signed requests

Deferred for the first local integration. It provides strong proof-of-possession
and can be appropriate for remote agents, but requires nonce/replay handling,
key discovery, canonical request signing and more operational complexity than
the current local stdio boundary needs.

The credential lifecycle is designed so that this option can be added later.

### 6.5 Dedicated opaque machine credential exchanged for a short-lived token

Selected for V1. A user registers an agent and receives a high-entropy opaque
credential once. Trading OS stores only a hash/fingerprint and metadata. The
MCP adapter presents the credential to a dedicated token-exchange boundary and
receives a short-lived, audience-bound agent JWT. The agent JWT is then used at
the Gateway like any other authenticated principal.

This avoids a new authorization server, avoids user-token reuse, permits
rotation and revocation, supports local OpenCode, and creates a distinct token
type that downstream services can validate.

### 6.6 Network location or MCP tool availability as authorization

Rejected. Local stdio, a Gateway route, a registered MCP tool or a Docker
network location does not grant access. Backend authorization remains mandatory
for every operation.

## 7. Chosen Identity Model

### 7.1 AgentIdentity

`AgentIdentity` is a persistent Trading Core security/domain record representing
a technical agent owned by exactly one user.

Required conceptual fields:

- `agentId` - immutable opaque identifier.
- `ownerUserId` - immutable owner relationship derived from the authenticated
  provisioning user, never from request input.
- `displayName` - user-visible name.
- `agentType` - extensible value such as `OPENCODE`, not a privileged role.
- `status` - at least `ACTIVE`, `SUSPENDED`, `REVOKED`.
- `createdAt` and `updatedAt`.
- `lastActivityAt` - operational metadata, not an authorization input.

An agent has no implicit account scope and no implicit permission. Newly created
agents are denied by default.

### 7.2 AgentCredential

`AgentCredential` is a persistent credential metadata record associated with an
agent. It is not the raw credential.

Required conceptual fields:

- `credentialId`.
- `agentId`.
- `credentialType` - initially opaque bearer exchange credential.
- `publicIdentifier` or credential prefix for lookup and support.
- `credentialFingerprint` or one-way hash for verification.
- `createdAt`, `expiresAt`, `lastUsedAt`.
- `status` - at least `ACTIVE`, `REVOKED`, `EXPIRED`.
- `revokedAt` and revocation reason where applicable.
- `credentialVersion` or rotation sequence.

Raw secrets, access tokens, private keys and signing keys are never persisted
in the domain database or returned by an API. The one-time credential response
is the only time the raw opaque credential is displayed to the provisioning
user. Credential issuance, rotation and revocation are user-management actions,
not agent actions.

### 7.3 AgentGrant

`AgentGrant` is a persistent explicit authorization from an owner to an agent.
It is the authoritative scope for agent access.

Required conceptual fields:

- `grantId`.
- `agentId`.
- `permission`.
- `resourceType`.
- `resourceId` or an explicitly constrained resource scope.
- `status` - `ACTIVE`, `REVOKED`, `EXPIRED`.
- `createdAt`, `expiresAt`, `revokedAt` and reason.
- `grantedByUserId`.
- `grantVersion` for audit and cache invalidation.

The grant resource must be resolved by Trading Core. For broker account access,
the grant targets the internal `BrokerAccount.id`, not an external cTrader
account number supplied by a caller. External account identity remains a
provider fact behind the Trading Core-owned mapping.

V1 does not permit arbitrary unvalidated conditions. If conditions are needed,
they must be typed, bounded and evaluated deterministically. A missing resource
scope is denial for resource-scoped permissions.

### 7.4 AgentRole

`AgentRole` is an application/security-layer permission preset, not a required
persistent entity in V1. Initial presets are versioned configuration:

- `VIEWER` - bounded read permissions.
- `ANALYST` - viewer plus approved research capabilities.
- `OPERATOR` - only future non-execution operational capabilities explicitly
  approved by architecture.

Roles are convenience bundles. A role never grants access without a concrete
resource-scoped `AgentGrant`, and a role must never include execution
permissions in V1. If user-defined reusable roles become a product need, a
separate ADR or amendment must define their lifecycle and isolation.

### 7.5 AgentAuditEvent

`AgentAuditEvent` is a persistent security audit record for authentication,
authorization and sensitive agent operations. It is distinct from ordinary
business logs and must not contain credentials or full broker payloads.

Required conceptual fields:

- `eventId`.
- `agentId`, `ownerUserId` and, when applicable, human approver ID.
- `callingService` and `targetService`.
- `permission` and `resourceType/resourceId`.
- `decision` - `ALLOWED`, `DENIED`, `CHALLENGED`, `REVOKED`.
- `outcome` - sanitized result classification.
- `timestamp`.
- `correlationId` and request/idempotency identity where applicable.
- `credentialId` or credential fingerprint reference, never the secret.
- denial reason code where applicable.

Audit writes must not be allowed to change the business authorization result.
Failure policy for audit persistence must be explicitly chosen per operation;
sensitive agent commands should fail closed if their required security audit
cannot be durably recorded.

## 8. Permission and Resource-Scoping Model

The authorization model is hybrid RBAC plus resource-scoped grants:

```text
AgentIdentity
    + authenticated AgentCredential
    + explicit AgentGrant(permission, resource scope, validity)
    + Trading Core ownership check
    + operation-specific business and safety checks
```

Initial permission vocabulary:

- `accounts:read` - read explicitly granted account metadata and synchronized
  account state.
- `accounts:sync` - request synchronization for explicitly granted broker
  accounts, subject to cooldown, lease, freshness, completeness and all
  existing Trading Core/Broker Service safeguards.
- `positions:read` - read positions for explicitly granted accounts.
- `markets:read` - read public or explicitly authenticated market contracts;
  public market data remains governed by its existing endpoint policy.
- `risk:read` - read only the risk facts and decisions the owning service
  exposes to the agent; this does not authorize a risk decision.
- `research:run` - start only the bounded, user-owned research workflow
  already approved by its application boundary.
- `plans:create` - create a proposal/trade plan only if the existing Trading
  Core workflow supports it; it never authorizes approval or execution.

Explicitly excluded in V1:

- `orders:execute`.
- `orders:modify`.
- `orders:cancel`.
- position mutation.
- broker credential management.
- agent registration, credential rotation or revocation by an agent.
- agent self-granting or grant escalation.
- cross-user administration.

Authorization is evaluated as a conjunction:

```text
valid agent credential
AND active agent identity
AND active, unexpired grant
AND requested permission matches
AND target resource is explicitly scoped
AND target resource belongs to the agent owner
AND endpoint/business rules pass
AND provider/risk safety rules pass
```

Any absent, ambiguous or contradictory input results in denial. Resource IDs
are targets, not proof of ownership.

## 9. Authentication Mechanism

### 9.1 V1 credential exchange

The first agent integration uses a dedicated opaque machine credential and a
short-lived agent JWT:

```text
User-authenticated management operation
    -> register AgentIdentity for authenticated owner
    -> issue opaque AgentCredential once

OpenCode / MCP adapter
    -> present opaque AgentCredential to token exchange
    -> receive short-lived agent JWT
    -> call Gateway with agent JWT
```

The token exchange boundary validates the credential hash, agent status,
credential status, expiration and revocation state. It issues no user JWT and
does not accept a user JWT as an agent credential.

The agent JWT must include, at minimum:

- `iss` - dedicated Trading OS agent issuer.
- `sub` - `agentId`, not `ownerUserId`.
- `agent_id` - explicit agent identity claim if distinct claim naming is needed.
- `owner_id` - owner identity resolved from persisted AgentIdentity, never copied
  from request input.
- `typ` or equivalent token-type marker identifying an agent token.
- `aud` - intended Gateway/application audience.
- `iat`, `exp` and `jti`.
- credential/version or authorization-context version where needed for cache
  invalidation and audit correlation.

The token lifetime must be short and configurable. V1 must not use a long-lived
JWT as the revocation mechanism. Revoking the credential or agent prevents new
token exchange; already issued tokens are bounded by their short expiry and
must be rejected immediately for high-sensitivity operations if a live
revocation check is required.

### 9.2 Signing and validation

The agent token issuer must use a signing key distinct from the existing user
JWT secret and service JWT keys. OpenCode receives neither the private signing
key nor a service signing key. Gateway validates agent tokens by the configured
agent issuer, signature, audience, type, expiration and required claims.

Trading Core validates the agent principal independently for sensitive
operations and resolves the persisted AgentIdentity. Broker Service does not
accept an agent token as a substitute for the Trading Core service credential.

V1 may use a locally configured asymmetric key pair with public-key
distribution to Gateway and Trading Core. A future OAuth2 authorization server
or JWKS provider may replace the local issuer without changing the claims and
authorization concepts. The exact key-storage implementation requires an
implementation Story and security review.

### 9.3 Local OpenCode integration

The MCP adapter remains local stdio. It must be changed in a later Story from
requiring `TRADING_OS_JWT` to using a dedicated agent credential reference,
preferably from the OpenCode process environment or an OS keyring integration.
The adapter exchanges the credential for a short-lived token and keeps the
token in process memory only. It must never print, persist, return or place the
credential/token in query parameters.

The adapter remains a protocol boundary. It does not evaluate grants and does
not call internal services. Backend authorization is authoritative.

## 10. Delegation Architecture

The end-to-end path is:

```text
OpenCode
    -> local MCP adapter
    -> agent credential exchange
    -> Gateway with agent JWT
    -> Trading Core agent principal
    -> AgentGrant and BrokerAccount ownership checks
    -> Trading Core operation and audit
    -> Broker Service service JWT with signed delegation
    -> Broker Service owner/scope defense-in-depth checks
    -> cTrader only when the operation is authorized
```

### 10.1 Gateway

Gateway authenticates the agent JWT and removes client-supplied identity or
delegation headers. It may forward a verified agent context to Trading Core,
but downstream services must authenticate the caller independently. No
client-controlled `X-Actor-Id`, `X-Owner-Id`, `X-Agent-Id` or future equivalent
header is trusted.

### 10.2 Trading Core

Trading Core is the authority for:

- AgentIdentity ownership.
- AgentCredential lifecycle metadata.
- AgentGrant evaluation.
- user and broker-account ownership.
- application operation authorization.
- human approval state where required.
- agent audit decisions for Trading Core operations.

Trading Core derives the owner from the authenticated AgentIdentity. It must not
accept an owner ID in the request body, query or path as caller identity.

For account synchronization, Trading Core must verify the agent has an active
`accounts:sync` grant for the exact internal `BrokerAccount.id`, then run the
existing owner, LIVE-mode, idempotency, cooldown, lease, snapshot completeness
and freshness checks.

### 10.3 Trading Core to Broker Service

The existing service-to-service model from ADR-044 remains. Trading Core sends
a short-lived Broker Service service JWT with:

- caller service identity and target audience;
- owner/delegated actor identity;
- explicit agent context reference where needed for audit;
- operation/correlation identity;
- no raw agent credential and no user JWT requirement at Broker Service.

Broker Service validates the Trading Core service token and does not trust
client-supplied delegation headers. It may perform defense-in-depth checks that
the delegated owner matches the broker-account relationship and that the
operation is an allowed internal contract. It must not independently invent or
expand the agent grant.

### 10.4 Principal separation

The effective security context must distinguish at least:

- `AgentPrincipal(agentId, ownerUserId, credentialId, token metadata)`.
- `UserPrincipal(userId, user claims)`.
- `ServicePrincipal(serviceName, audience, token metadata)`.
- `DelegatedActor(ownerUserId, optional agentId, provenance)`.
- `HumanApprover(userId, approval identity)`.

An agent is not represented as a user, and an agent owner is not substituted for
the agent in audit or authentication claims. When Trading Core delegates to
Broker Service, the service identity authenticates the hop and the signed
delegated actor identifies the owner and agent context.

## 11. Human Approval and Sensitive Operations

Persistent permission and per-operation approval are separate controls:

- A grant is durable authorization configured by the owner.
- A one-time approval is a human decision for a specific operation, resource,
  intent and time window.
- A deterministic business/risk validation evaluates whether the operation is
  valid under current domain facts and safety rules.

V1 policy:

- Read-only market and research operations may use active persistent grants.
- Account reads require active resource-scoped grants.
- Account synchronization requires an active `accounts:sync` grant, exact
  broker-account ownership and all existing cooldown/lease protections.
- Because synchronization creates an external cTrader request and updates
  provider-authoritative local state, V1 also requires a one-time human
  approval for an agent-triggered synchronization. This approval is not trade
  authorization and does not authorize orders.
- The approval must be issued by the account owner through an authenticated
  human flow, bind to `agentId`, permission, exact `BrokerAccount.id`, request
  fingerprint, expiration and a single-use nonce, and be atomically consumed.
- An agent cannot create, approve, replay, broaden or transfer the approval.
- No agent order-execution approval flow is introduced by this ADR.

The existing Broker Service cooldown, lease, idempotency and reconciliation
controls remain mandatory even after an approval is accepted. Human approval
cannot bypass them.

## 12. Credential Lifecycle and Revocation

### Provisioning

Only an authenticated human owner or separately approved management authority
may register an agent. The owner is derived from the authentication context.
The request cannot choose another owner. The raw credential is displayed once
and never recoverable from Trading OS.

### Rotation

An owner may issue a replacement credential. Rotation creates a new credential
version, marks the previous credential revoked or grace-period expired
according to an explicitly chosen policy, and records an audit event. Existing
agent tokens remain bounded by their token lifetime.

### Revocation

Revoking an AgentCredential prevents new token exchange. Revoking an
AgentIdentity invalidates all associated credentials and grants. Revoking a
grant prevents new operations even if the agent token is still valid.

Sensitive operations must perform a live active-state/grant check or use a
bounded cache with version invalidation; token claims alone are insufficient
for immediate grant revocation.

### Expiration and inactivity

Credential expiration and grant expiration deny new operations. `lastActivityAt`
is informational and must not silently extend a credential or grant.

### Deletion and retention

Security records should be revoked/retained rather than physically deleted when
needed for audit and incident investigation. Personal-data retention and
account deletion requirements require explicit product/legal validation.

## 13. Audit and Observability

Every agent authentication exchange, denied authorization, grant change,
credential lifecycle event, human approval, synchronization request and
delegated service call must have a correlation ID and an audit outcome.

Audit records must identify:

- agent and owner;
- calling and target service;
- permission and resource scope;
- decision and reason code;
- human approver where applicable;
- request idempotency/correlation identity;
- timestamp and outcome.

Logs and metrics must not contain raw credentials, JWT values, private keys,
broker credentials or unrestricted provider payloads. Audit events should be
structured and queryable, while normal application logs may contain only safe
references and sanitized reason codes.

## 14. Multi-User Isolation and Security Invariants

The following invariants are mandatory:

1. Agent identity is cryptographically authenticated.
2. Every agent belongs to exactly one explicit user.
3. The owner is resolved from persisted identity, never selected by the caller.
4. Permissions are denied by default.
5. Every protected operation requires an effective active grant.
6. Resource ownership is checked independently of authentication.
7. Agent A cannot access User B's resources, even if it knows their IDs.
8. A same-user agent cannot access an ungranted account.
9. Expired or revoked grants deny new operations.
10. Revoked credentials cannot obtain new tokens.
11. Agent permissions cannot exceed resource and operation scope.
12. MCP tool registration never substitutes for backend authorization.
13. Trading Core remains authoritative for account ownership and business rules.
14. Broker Service accepts only validated service delegation for internal calls.
15. Agent-originated requests remain distinguishable from human requests.
16. Missing or ambiguous principal, owner, grant or resource mapping denies.
17. Risk, execution, idempotency, reconciliation, cooldown and lease controls
    cannot be bypassed by agent context.
18. Client-controlled identity headers and payload actor fields are never
    authorization authorities.
19. Human approval is single-use, bound and non-transferable where required.
20. No agent permission authorizes order execution in V1.

## 15. Migration and Compatibility

The migration is additive and staged:

1. Preserve current user JWT behavior for browsers and existing clients.
2. Preserve ADR-044 service JWT delegation for internal service calls.
3. Add agent principals and token validation as a separate token type/audience.
4. Add agent-aware authorization at Gateway and Trading Core without trusting
   new client headers.
5. Keep existing MCP tools functional under an explicit compatibility policy;
   migrate the adapter from user JWT input to agent credential exchange only
   after the new agent boundary is validated.
6. Add account-scoped agent capabilities only through new public application
   contracts or explicitly extended contracts, not by exposing `/internal/**`.
7. Keep the cTrader provider and Broker Service contracts unchanged except for
   signed delegation/audit context required by the existing internal boundary.
8. Introduce database tables through reviewed migrations only in implementation
   Stories after this ADR is accepted.

Existing user-token weaknesses, including issuer enforcement and shared-secret
blast radius, should be addressed in a separate security-hardening Story. They
must not be made worse by accepting agent claims through the user-token path.

## 16. Consequences and Tradeoffs

### Benefits

- OpenCode becomes one agent client among many, not a privileged exception.
- User JWTs are not reused as machine identity.
- Agents can be independently revoked and audited.
- Account access is explicit and resource-scoped.
- Trading Core and Broker Service responsibilities remain intact.
- Local stdio remains practical without deploying an authorization server.
- The model can evolve toward OAuth2/JWKS or proof-of-possession credentials.

### Costs and risks

- New identity, credential, grant and audit lifecycle code is required.
- Token exchange and revocation state introduce operational complexity.
- Per-operation synchronization approval adds user workflow friction.
- Existing mixed user/service JWT paths require careful principal separation.
- Dedicated asymmetric key management is required for a safe agent issuer.
- Audit retention and privacy policy need explicit product decisions.
- Existing issuer-validation and shared-secret weaknesses remain until separately
  remediated.

## 17. Open Questions

1. Should V1 agent JWT issuance be hosted by Trading Core or a small dedicated
   identity component within the Gateway boundary?
2. What secure local storage is available for the OpenCode credential: process
   environment, OS keyring or an existing secret-management facility?
3. What exact agent JWT lifetime and revocation-check policy is acceptable?
4. Is a one-time human approval required for every synchronization, or can a
   future account-level policy permit grant-only synchronization?
5. What audit retention and user deletion policy applies to agent security
   records?
6. Should `plans:create` be included in the first agent release, or deferred
   until the plan approval workflow is explicitly agent-aware?
7. Which existing user/service JWT issuer and key-hardening work must precede
   production remote-agent support?
8. When should remote MCP transport and OAuth resource-server semantics be
   reconsidered?

## 18. Implementation Phases and Proposed Engineering Stories

The following Stories must be created only after this ADR receives explicit
human architecture approval. They are proposals, not approval or implementation
authorization.

### Story A - Agent Identity and Credential Foundation

**Scope:** Trading Core persistence and owner-controlled lifecycle for
`AgentIdentity` and `AgentCredential`.

**Dependencies:** ADR-090 accepted; existing user ownership and secret-storage
review completed.

**Acceptance criteria:**

- A user can register an agent only for themselves.
- Agent type, status and lifecycle are persisted with immutable ownership.
- Raw credentials are never persisted, logged or returned after one-time issue.
- Credential rotation and revocation are auditable and deny new exchange.
- Agent self-provisioning and cross-user references are rejected.
- Multi-user isolation tests cover registration, rotation and revocation.

### Story B - Agent Token Exchange and Principal Validation

**Scope:** Dedicated agent credential exchange, short-lived agent JWTs,
Gateway/Trading Core validation and principal separation.

**Dependencies:** Story A; key-management decision; issuer/audience decision.

**Acceptance criteria:**

- Only an active credential can obtain an agent token.
- Agent tokens use a distinct issuer/type/audience and signing key from user and
  service tokens.
- Gateway and Trading Core validate required claims independently.
- User tokens cannot enter the agent-token path and agent tokens cannot be
  interpreted as user tokens.
- Revoked credentials and identities cannot obtain new tokens.
- Tokens are short-lived and never appear in logs or query parameters.
- Forged owner, actor and agent headers are ignored or rejected.

### Story C - Grants, Resource Authorization and Audit

**Scope:** `AgentGrant`, permission vocabulary, role presets, authorization
decisions, human approval records and `AgentAuditEvent`.

**Dependencies:** Stories A and B; resource ownership contracts in Trading Core.

**Acceptance criteria:**

- Grants are default-deny, explicit, resource-scoped and owner-controlled.
- Each protected operation evaluates agent status, grant status, expiration and
  resource ownership.
- User A's agent cannot access User B's resources or User A's ungranted
  resources.
- Revocation takes effect according to the documented live-check/cache policy.
- Denials and approvals are durably auditable without secrets.
- One-time approvals are bound, expiring, single-use and non-replayable.

### Story D - MCP Agent Authentication and Read-Only Capabilities

**Scope:** Replace the current MCP user-JWT dependency with agent credential
exchange while preserving local stdio and Gateway-only access.

**Dependencies:** Stories A-C; ADR-088/089 compatibility review.

**Acceptance criteria:**

- OpenCode authenticates as a registered agent, not as a copied user session.
- MCP remains local stdio and calls only public Gateway contracts.
- Every tool call is authorized server-side and carries agent audit context.
- Existing market/research tools remain bounded and non-executing.
- No credential, token, internal endpoint, broker or provider access leaks into
  the adapter.
- Two agents of one user can have different effective permissions.

### Story E - Controlled Account Synchronization Delegation

**Scope:** Add the smallest public application capability for an authorized
agent to request synchronization of one explicitly granted broker account.

**Dependencies:** Stories A-D; ADR-074/085 compatibility; human approval policy
decision; existing synchronization validation.

**Acceptance criteria:**

- Trading Core authorizes exact `BrokerAccount.id` scope before any Broker
  Service call.
- The owner is derived from the authenticated agent identity.
- The request cannot select or override its owner, grant or delegated actor.
- A valid one-time human approval is required under the V1 policy.
- Existing cooldown, lease, idempotency, freshness, completeness and fail-closed
  behavior remain unchanged.
- Broker Service receives only validated Trading Core service delegation.
- Exactly one logical synchronization request is auditable end to end.
- No order, position mutation or execution capability is exposed.
- User A/B, ungranted account, revoked grant, duplicate and concurrent requests
  are denied or safely deduplicated.

### Story F - Agent Management Interface and Operations

**Scope:** Angular management for agent list, registration, grant configuration,
credential rotation/revocation and audit visibility.

**Dependencies:** Stories A-C; finalized product/retention decisions.

**Acceptance criteria:**

- The UI can manage only the authenticated user's agents.
- Resource selectors cannot grant another user's account.
- Sensitive credential material is shown only at issuance and never in history.
- Revocation and grant changes show explicit confirmation and audit outcome.
- The UI does not expose execution permissions or bypass approval.

## 19. Security Test Matrix

The implementation Stories must cover at least:

- User A agent reads User A permitted account: allowed.
- User A agent reads User A unpermitted account: denied.
- User A agent accesses User B account: denied.
- Two same-user agents with different grants: effective scopes differ.
- Expired grant: denied.
- Revoked grant: denied.
- Revoked credential: token exchange denied.
- Missing resource scope: denied.
- Forged delegated actor: denied.
- Forged owner ID: denied.
- Missing permission: denied.
- Duplicate synchronization: idempotent/rejected according to existing contract.
- Concurrent synchronization: lease protection remains effective.
- Cooldown active: denied without bypass.
- Agent invoking execution tools: unavailable and backend-denied.
- MCP direct internal endpoint attempt: unavailable and denied.
- Agent token presented as user token: denied.
- User token presented as agent credential: denied.
- Expired/replayed human approval: denied.
- Audit persistence failure on a sensitive operation: fail-closed according to
  the approved policy.

## 20. Acceptance Criteria for This ADR

This ADR is ready for human architecture review when:

- the current identity, MCP, synchronization and delegation boundaries are
  accurately represented;
- AgentIdentity, AgentCredential, AgentGrant and AgentAuditEvent ownership is
  explicit;
- AgentRole is intentionally kept as a non-persistent preset for V1;
- authentication, delegation, resource authorization and human approval are
  separate controls;
- the model denies by default and prevents cross-user access;
- order execution remains explicitly excluded;
- the roadmap is split into independently reviewable Stories;
- open security and product decisions are visible;
- no production code, migration, credential, deployment or runtime policy was
  changed by this ADR.

This ADR must not be marked `Accepted` by an agent. Human architecture review
is required before any dependent Story begins.

## 21. Final Decision Status

```text
ADR-090_STATUS = ACCEPTED
HUMAN_ARCHITECTURE_APPROVAL = COMPLETED
PRODUCTION_IMPLEMENTATION = NOT_AUTHORIZED
USER_JWT_REUSE_FOR_AGENTS = REJECTED
OPEN_CODE_PRIVILEGED_EXCEPTION = REJECTED
AGENT_ORDER_EXECUTION = NOT_AUTHORIZED
REMOTE_MCP = NOT_AUTHORIZED
CONTROLLED_AGENT_ACCOUNT_SYNC = DEFERRED_TO_FOLLOWING_STORIES
```

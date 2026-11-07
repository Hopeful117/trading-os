# Repository Analysis - Story 0216

## Baseline

- Branch: `main`
- HEAD: `fe746776de4fe192d1f46475e2c76d49c883c449`
- Worktree: clean and synchronized with `origin/main`
- Next available Story number: `0216`
- Current Story artifact: `story.md` in this directory

No implementation files were modified during this analysis.

## Authoritative Context

- `docs/architecture/adr/ADR-090-agent-identity-permissions-delegated-authorization.md`
  is accepted and governs the identity, credential, token, principal and
  delegation boundaries.
- Story 0214 is merged and provides `AgentIdentity`, `AgentCredential`,
  owner-scoped lifecycle operations and one-time opaque credential issuance.
- Story 0215 is merged and provides explicit `AgentGrant` evaluation with
  default deny. Authentication must not imply grants.
- ADR-044 preserves service JWT and delegated-actor semantics for internal
  service calls; an agent token must not replace the Trading Core service token.
- ADR-088 and ADR-089 preserve the local stdio MCP boundary and prohibit direct
  agent access to internal services or broker infrastructure.

DevLog was queried for the `trading-os` project with a targeted Story 0216
context request. It returned completed but truncated, low-confidence context.
It confirmed only the already-established separation of agent identity from
human JWTs, default-deny grants and secret handling. The current repository and
accepted ADRs remain authoritative.

The Obsidian vault was queried selectively for agent authentication and token
revocation context; no relevant notes were returned. No vault action is
required for this Story at analysis stage.

## Existing Credential Foundation

Trading Core owns the agent lifecycle under
`trading-core/src/main/java/com/hope/trading/trading_core/agent`:

- `AgentIdentity` stores immutable owner, type, lifecycle status and timestamps.
- `AgentCredential` stores only SHA-256 digest, fingerprint, version, status and
  lifecycle metadata. The raw value exists only in the issue/rotation response.
- `AgentCredentialGenerator` uses `SecureRandom` and URL-safe 32-byte material.
- `AgentCredentialRepository.findByCredentialDigest` is the future exchange
  lookup boundary.
- `AgentLifecycleService` revokes active credentials when an agent is suspended
  or revoked and uses owner-scoped persistence operations.
- `AgentAuditEventEntity` stores sanitized lifecycle events.

The existing credential model supports exchange without adding raw-secret
fields. The implementation must review whether a server-side pepper or another
verifier is required, but must not weaken the current one-way storage model.

## Existing Authentication Audit

### Human JWT

Trading Core and Gateway each have a local `JwtServiceImpl`, `JwtProperties` and
JWT authentication filter. Human tokens use the `security.jwt` secret, issuer,
user UUID subject, username, email and role claims. The filters construct
`UserDto`/`UserAuthenticationDto` principals and role authorities.

The current parser verifies the configured HMAC key and expiration but does not
strictly require issuer, audience or a token-type claim. This behavior must not
be copied into agent validation. Human behavior remains unchanged except that
agent tokens must not be accepted by the human path.

### Service JWT

Trading Core has a separate `ServiceJwtService`, `ServiceJwtProperties` and
`ServiceJwtAuthenticationFilter` for `/internal/**`. Service tokens use a
separate configured secret, issuer, audience, `type=service`, service subject
and optional delegated actor. Agent tokens must never satisfy this filter or
replace this internal trust boundary.

### Gateway

Gateway currently validates bearer tokens through the reactive human JWT filter,
requires authentication for non-public routes, and propagates `X-Actor-Id`
only for Market Intelligence routes when the principal is a human DTO. The
Gateway route table currently has no explicit `/api/v1/agents/**` route, though
the token exchange endpoint will need a targeted public route to Trading Core.

The Gateway must validate the dedicated agent signature, issuer, audience, type,
required claims and expiration. It cannot perform Trading Core lifecycle lookup
without creating an unwanted data dependency, so immediate revocation must be
enforced by Trading Core on agent-protected operations rather than claimed from
Gateway cryptographic validation alone.

### Trading Core

Trading Core's servlet security chain applies the human filter and service filter
and requires authentication for all non-login/register endpoints. The token
exchange endpoint must be the only new credential-authenticated public endpoint.
Human agent-management controller methods already require `UserDto`, which is a
useful boundary: an `AgentPrincipal` must not satisfy those methods.

## Proposed Smallest Compatible Design

1. Add a dedicated token exchange application service in Trading Core that
   accepts only the opaque credential body, resolves the credential digest,
   checks credential and agent status/expiration, resolves the owner from
   `AgentIdentity`, records a sanitized authentication event, and issues a
   short-lived agent JWT.
2. Add a dedicated agent JWT properties/issuer component using a distinct
   configured signing secret and issuer/audience values. The secret must be
   supplied by deployment configuration later; this Story must not provision or
   expose it. A dedicated HMAC key is the smallest implementation compatible
   with the current Gateway/Trading Core symmetric JWT infrastructure; asymmetric
   signing/JWKS remains a future hardening path unless the approved plan selects
   otherwise.
3. Add an `AgentPrincipal` containing agent ID, owner ID, credential ID/version
   and token metadata, without human roles. Add dedicated validation paths in
   Gateway and Trading Core that require strict issuer, audience, type, subject,
   owner and expiration claims.
4. Keep human JWT validation and service JWT validation separate. A token must
   not be accepted merely because it is signed by a valid key.
5. Add a Trading Core live lifecycle validator for agent principals. It checks
   the persisted agent status and credential status/version before an agent
   operation can proceed. The existing `AgentAuthorizationService` remains the
   grant authority; this Story does not add MCP authorization or grant claims.
6. Add an explicit Gateway route for the exchange endpoint and do not add a
   general-purpose signing route or permissive CORS behavior.
7. Use generic authentication failures externally and safe operation/reason
   codes in audit events. Never log request bodies, raw credentials or JWTs.

## Open Decisions For Implementation Plan

- Exact endpoint path and request/response DTO names, following the existing
  `/api/v1/agents` convention.
- Dedicated agent JWT property names, issuer and audience values.
- Exact short token lifetime and whether credential version is included as a
  revocation-context claim.
- Whether the Gateway should create an `AgentPrincipal` only, or forward a
  sanitized verified context while Trading Core independently validates the
  token.
- Exact audit failure policy for authentication: failed exchange must not create
  unbounded writes or leak enumeration information; successful exchange should
  be durably auditable where the existing audit boundary permits.
- Anti-brute-force controls compatible with the current repository. No runtime
  rate limiter or external infrastructure is currently identified.

## Risks

- Existing human JWT parsing has issuer/audience weaknesses; reusing that path
  for agent tokens would be a security defect and is prohibited.
- A shared dedicated HMAC agent key must be configured identically in Gateway and
  Trading Core, increasing operational key-distribution responsibility. No key
  may be committed or provisioned in this Story.
- Gateway cannot establish immediate revocation from its current stateless
  architecture. Trading Core live lifecycle validation is required for the
  protected operations introduced by this Story.
- The current public MCP adapter still uses a human JWT. Migrating it is an
  explicit later Story and is out of scope here.
- Adding an agent-aware filter to all protected endpoints could accidentally
  broaden access. Endpoints must remain human-only unless explicitly opted into
  agent access in a later capability Story.

## Validation Plan

- Unit tests for credential lookup, generic failure behavior, claim issuance and
  strict claim validation.
- Token exchange integration tests for valid, unknown, revoked, expired and
  disabled credentials/agents.
- Principal separation tests for human, agent and service tokens.
- Gateway route and filter tests for agent token validation and removal of
  client-controlled actor headers.
- Trading Core tests for live revocation after token issuance and human-only
  management endpoints.
- Story 0214/0215 regression tests and existing JWT/service security tests.
- Isolated database tests for audit and lifecycle state; no shared migration.
- `./mvnw -q test` in affected Java modules and `git diff --check`.

## Conclusion

The Story is technically coherent and can proceed to an Implementation Plan.
There is no current security-critical contradiction between the prompt, ADR-090
and the merged 0214/0215 implementation. Implementation must not begin until
this Repository Analysis is explicitly approved.

```text
Story 0216                 DRAFT
Repository Analysis        COMPLETED
Repository Analysis Gate   HUMAN APPROVAL REQUIRED
Implementation Plan         NOT STARTED
Production Implementation   NOT AUTHORIZED
```

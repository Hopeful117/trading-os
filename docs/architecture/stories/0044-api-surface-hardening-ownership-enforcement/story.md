# Story 0044 - API Surface Hardening and Ownership Enforcement

## Metadata

**ID:** `0044`

**Title:** API Surface Hardening and Ownership Enforcement

**Status:** IMPLEMENTED - RUNTIME VALIDATED; HUMAN CLOSURE REVIEWED

**Baseline:** `main` at `8eb75431749c3ca5c8b6dd9384b3fe1d9f61b3be`

**Related ADRs:** ADR-001, ADR-014, ADR-029, ADR-030, ADR-041, ADR-042, ADR-043, ADR-044

**Related Investigation:** `docs/investigations/api-security-surface-audit.md`

**Implementation status:** Runtime-validated security boundary implemented for the Core, Broker Service, and Market Intelligence slice. Explicit deferred debt remains recorded below.

**Final Market Intelligence refinement:** [visibility decision](market-intelligence-visibility-decision.md), [endpoint authorization matrix](market-intelligence-endpoint-authorization-matrix.md), and [identity migration matrix](market-intelligence-identity-migration-matrix.md). The selected policy is `AUTHENTICATED_SHARED`; implementation remains subject to explicit human authorization.

## 1. Context

Trading OS uses an API Gateway as the intended product ingress. Trading Core and
Broker Service already validate user JWTs locally. Market Data and Market
Intelligence do not currently expose an equivalent local application-security
boundary.

The completed API/security audit identified a mixed surface of public, legacy,
internal, and management endpoints. Current service calls use Feign. The current
Feign configuration forwards an incoming `Authorization` header when an HTTP
request context exists, but it does not establish a caller service identity.

The audit also confirmed that some legacy operations accept or act on identifiers
without complete ownership checks. In particular, several Trade operations load
or mutate a Trade without receiving the authenticated actor. Market Intelligence
contains actor values in request bodies, query parameters, and `X-Actor-Id`.

ADR-044 has been accepted. It establishes the following model:

```text
USER IDENTITY       = locally validated user JWT principal
SERVICE IDENTITY    = short-lived signed service JWT
DELEGATED ACTOR     = signed actor context derived from validated user context
OWNERSHIP           = server-side authorization by the resource authority
```

This Story implements only the minimum current V1 controls needed to establish
that boundary and close the confirmed high-impact application-security findings.

## 2. Problem

The current architecture does not consistently prove all of the following:

- that a caller is the service it claims to be;
- that an actor identifier originated from trusted authentication context;
- that an authenticated user owns the requested Account, Trade, Plan, Execution,
  Position, RiskConfiguration, or credential resource;
- that sensitive response objects do not expose persistence secrets;
- that production-like runtime configuration fails safely when required JWT
  secrets are missing;
- that the external execution contract reaches the actual downstream mapping;
- that public Market Data reads are deliberately separated from protected
  internal operations.

The result is an unjustified attack surface and a security boundary that depends
too heavily on Gateway routing or network placement.

## 3. Goal

Reduce Trading OS's unjustified API attack surface, enforce authenticated actor
and resource ownership boundaries, align exposed API contracts, and implement the
minimum V1 trust controls required by ADR-044 before validating the complete
normal-user PAPER journey.

This is a security-hardening Story, not a product-feature Story.

## 4. Scope

### 4.1 Blocking application-security scope

- Enforce server-side ownership for all confirmed legacy Trade read and mutation
  paths.
- Remove or refactor the audited User endpoint so a password or password hash
  cannot appear in an HTTP response.
- Remove the predictable Trading Core JWT fallback for production-like runtime
  profiles while preserving explicit test/development configuration.
- Add the smallest V1 service-principal validation/generation mechanism required
  for current sensitive internal calls, using short-lived signed service JWTs.
- Add receiver-side service identity, audience, and endpoint authorization for
  the internal calls listed in this Story.
- Migrate current user-scoped Market Intelligence operations away from client
  actor authority.
- Protect current internal Market Data operations while preserving public bounded
  market reads.
- Sanitize identity headers at the Gateway and reject actor mismatches.
- Align the external Execution route with the actual Trading Core controller and
  prove the complete Gateway-to-downstream mapping.

### 4.2 Bounded API-surface work

- Verify the current consumer and replacement status of unrouted or legacy
  endpoints.
- Keep supported endpoints only under their intended trust class.
- Remove only endpoints with high-confidence evidence of being dead and having
  no infrastructure consumer.
- Restrict or defer ambiguous legacy endpoints rather than deleting them
  speculatively.

### 4.3 Security test and validation scope

- Add two-user ownership and IDOR tests.
- Add serialization tests for secret-bearing entities.
- Add service JWT negative and positive tests.
- Add actor delegation and mismatch tests for calls migrated in this Story.
- Add Gateway header sanitation, internal-route, and real downstream mapping
  tests.
- Add public-versus-internal Market Data tests.
- Execute runtime checks for each changed trust boundary.

## 5. Non-goals

- Complete normal-user PAPER journey acceptance.
- New PAPER product behavior, financial semantics, or position-model changes.
- Research Lab, AI agents, News, macro, scanners, or background monitoring
  implementation.
- OAuth2 authorization server or general IAM service.
- Enterprise RBAC, ABAC, multi-tenant organizations, or SSO.
- Kubernetes, service mesh, Zero Trust platform, or cloud IAM deployment.
- mTLS implementation.
- Vault or cloud secret-management platform.
- LIVE broker redesign, provider authority changes, or credential model redesign.
- General DTO cleanup unrelated to a confirmed security-sensitive exposure.
- Deleting endpoints solely because Angular does not currently consume them.
- Automatic retries or changes to broker unknown-outcome semantics.
- Angular feature work except a contract-only URL correction if a test proves it
  is unavoidable. Current frontend behavior should remain unchanged.

## 6. ADR Dependencies and Constraints

ADR-044 is accepted and is a hard architectural dependency. Its constraints are
not reopened by this Story:

- API Gateway is the supported production external ingress.
- Direct service ports are development convenience only and internal by default
  in production.
- User identity, service identity, delegated actor, and ownership remain
  separate.
- Client-controlled `actorId`, `X-Actor-Id`, and caller-identity path values are
  never authorization authorities.
- Sensitive services retain local security.
- `/internal/**` is naming/routing semantics, not security.
- Public bounded Market Data reads may remain unauthenticated.
- Internal/expensive/provider/management operations require stronger trust.
- Background operations use a service principal with no actor or `SYSTEM`.
- Production-like runtimes fail fast when required secrets are missing.

ADR-042 and ADR-043 remain unchanged:

```text
LIVE_POSITION_AUTHORITY  = BROKER
PAPER_POSITION_AUTHORITY = TRADING_CORE
```

Deterministic risk authority and explicit human execution authority remain
unchanged.

## 7. Confirmed Findings and Destination

| Finding | Severity | Destination | Story classification | Rationale |
|---|---|---|---|---|
| Trade reads and SL/TP mutations lack ownership checks | Critical | Story0044 | `STORY0044_BLOCKING` | Confirmed application IDOR risk. |
| User entity can serialize password | Critical | Story0044 | `STORY0044_BLOCKING` | Confirmed secret-bearing HTTP response risk. |
| Predictable Trading Core JWT fallback | Critical | Story0044 | `STORY0044_BLOCKING` | Directly weakens user authentication. |
| Market Intelligence actor supplied by request data | Critical | Story0044 | `STORY0044_BLOCKING` | Directly violates ADR-044 actor invariant. |
| Market Intelligence lacks local security boundary | Critical | Story0044 | `STORY0044_BLOCKING` | Required for current user/service calls. |
| Internal Market Data operations lack explicit service auth | High | Story0044 | `STORY0044_BLOCKING` | Required for current valuation/snapshot calls. |
| Execution Gateway/downstream mapping mismatch | High | Story0044 | `STORY0044_BLOCKING` | Contract defect blocks reliable execution flow. |
| Client-controlled identity headers | High | Story0044 | `STORY0044_BLOCKING` | Required Gateway trust-boundary control. |
| Unrouted legacy Trading Core APIs | High | Story0044 matrix, selective restriction | `STORY0044_SMALL_FIX` or `FOLLOW_UP_API_CLEANUP` | Classification depends on consumer evidence; do not route automatically. |
| Broad WebSocket origin policy | High | Story0044 minimum policy | `STORY0044_SMALL_FIX` | Current public WebSocket needs bounded origins and inputs. |
| Direct Compose service ports | Medium/High | Production policy documentation and runtime acceptance; deployment follow-up | `FOLLOW_UP_INFRASTRUCTURE` unless production config is demonstrated | Compose is local convenience; removing ports now could damage debugging. |
| Actuator/Eureka exposure | Medium | Verify and restrict only dangerous current exposure | `FOLLOW_UP_INFRASTRUCTURE` unless confirmed product exposure | No broad observability platform is needed. |
| Missing fees, slippage, partial fills, ledger realism | Medium | Later reliability/product Stories | `FOLLOW_UP_RELIABILITY` | Not an API trust-boundary defect. |
| History/analytics UI gap | Low/Medium | Later product Story | `TRADER_HISTORY_ANALYTICS` | No security boundary requires implementing the UI. |
| Research Lab prerequisites | Out of scope | Later Research Lab prerequisite | `RESEARCH_LAB_PREREQUISITE` | No Research Lab work enters this Story. |

All audit findings have a destination. No finding is silently discarded.

## 8. Endpoint and Trust Matrix

The matrix uses the current repository mapping and current consumers. `KEEP`
means the contract remains; `RESTRICT` means it must require the stated trust
class; `REFACTOR` means the contract must be made safe; `INTERNALIZE` means it
must not be a public product route; `REMOVE` is allowed only where removal
confidence is high.

| SERVICE | METHOD | PATH | CURRENT TRUST | TARGET TRUST CLASS | OWNERSHIP REQUIRED | ACTION | STORY0044_OR_FOLLOWUP | EVIDENCE |
|---|---|---|---|---|---|---|---|---|
| Trading Core | POST | `/api/v1/users/register` | Gateway public, local public | `PUBLIC` | No | KEEP | Story0044 regression only | `UserController`, security config |
| Trading Core | POST | `/api/v1/users/login` | Gateway public, local public | `PUBLIC` | No | KEEP | Story0044 regression only | `UserController`, security config |
| Trading Core | GET | `/api/v1/users/{id}` | User-authenticated, returns entity | Absent; self-view only if a consumer is demonstrated | User self-access if retained | REMOVE | Story0044 | `UserController`, `User`, no current Angular/Feign consumer found |
| Trading Core | GET | `/api/v1/accounts/{accountId}` | User-authenticated | `USER_AUTHENTICATED` | Yes | KEEP | Story0044 regression | `AccountController` |
| Trading Core | GET | `/api/v1/accounts` | User-authenticated | `USER_AUTHENTICATED` | User-scoped query | KEEP | Story0044 regression | `AccountController` |
| Trading Core | POST | `/api/v1/accounts/synchronize` | User-authenticated | `USER_AUTHENTICATED` | User-scoped | KEEP | Story0044 regression | `AccountController` |
| Trading Core | GET | `/api/v1/accounts/{accountId}/dashboard` | User-authenticated | `USER_AUTHENTICATED` | Yes | KEEP | Story0044 regression | `DashboardController` |
| Trading Core | GET | `/api/v1/accounts/{accountId}/positions` | User-authenticated | `USER_AUTHENTICATED` | Yes | KEEP | Story0044 regression | `PositionController` |
| Trading Core | POST | `/api/v1/accounts/{accountId}/positions/close` | User-authenticated | `USER_AUTHENTICATED` | Yes | KEEP | Story0044 regression | `PositionCloseController`, Angular position service |
| Trading Core | POST | `/api/v1/accounts/{accountId}/positions/close/{commandId}/reconcile` | User-authenticated | `USER_AUTHENTICATED` | Yes | KEEP | Story0044 regression | `PositionCloseController` |
| Trading Core | POST | `/api/v1/trades` | User-authenticated | `USER_AUTHENTICATED` | Yes | KEEP and audit | Story0044 | `TradeController` |
| Trading Core | GET | `/api/v1/trades/{tradeId}` | Authenticated only in controller | `USER_AUTHENTICATED` | Yes | REFACTOR | Story0044 | `TradeController`, `TradingServiceImpl` |
| Trading Core | GET | `/api/v1/trades?accountId=...` | Authenticated only in controller | `USER_AUTHENTICATED` | Yes | REFACTOR | Story0044 | `TradeController`, `TradingServiceImpl` |
| Trading Core | POST | `/api/v1/trades/{tradeId}/close` | User-authenticated | `USER_AUTHENTICATED` | Yes | KEEP and verify | Story0044 | `TradeController` |
| Trading Core | POST | `/api/v1/trades/{tradeId}/partial-close` | User-authenticated | `USER_AUTHENTICATED` | Yes | KEEP and verify | Story0044 | `TradeController` |
| Trading Core | PATCH | `/api/v1/trades/{tradeId}/stop-loss` | Authenticated only in controller | `USER_AUTHENTICATED` | Yes | REFACTOR | Story0044 | `TradeController`, `TradingServiceImpl` |
| Trading Core | PATCH | `/api/v1/trades/{tradeId}/take-profit` | Authenticated only in controller | `USER_AUTHENTICATED` | Yes | REFACTOR | Story0044 | `TradeController`, `TradingServiceImpl` |
| Trading Core | POST/GET | `/api/v1/rules/**` | User-authenticated locally, not Gateway-routed | `USER_AUTHENTICATED` or `KEEP_NOT_PUBLIC` | Must be verified | RESTRICT | Follow-up API cleanup unless a current consumer is proven | `RulesController`, no current Angular consumer found |
| Trading Core | GET | `/api/analytics/{accountId}/statistics` | User-authenticated locally, not Gateway-routed | `USER_AUTHENTICATED` | Yes | KEEP_NOT_PUBLIC pending route decision | Follow-up API cleanup | `TradeAnalyticsController` |
| Trading Core | POST | `/api/v1/risk-profiles/{...}` catalog route | User-authenticated locally | `USER_AUTHENTICATED` | No account ownership | KEEP | Story0044 regression | `RiskProfileCatalogController` |
| Trading Core | POST/GET | `/api/v1/trade-plans/**` | Gateway-authenticated | `USER_AUTHENTICATED` | Yes | KEEP and verify | Story0044 | Trade-plan controllers, Angular service |
| Trading Core | POST/GET | `/api/v1/trade-planning-profiles/**` | User-authenticated locally, not Gateway-routed | `USER_AUTHENTICATED` | Yes | RESTRICT, do not auto-route | Follow-up API cleanup | `TradePlanningProfileController` |
| Trading Core | POST/GET | `/api/v1/broker-accounts/**` | Gateway/local JWT | `USER_AUTHENTICATED` | Yes | KEEP and verify | Story0044 | `BrokerAccountController` |
| Trading Core | POST/GET | `/api/v1/executions/**` expected public contract | Gateway route exists | `USER_AUTHENTICATED` | Yes | REFACTOR mapping | Story0044 | Gateway, Angular, `ExecutionController` |
| Trading Core | GET | `/internal/executions/metrics` | Local authenticated route, no public Gateway route | `MANAGEMENT` | No user actor | RESTRICT | Story0044 only if current exposure is confirmed; otherwise follow-up | `ExecutionOperationsController` |
| Trading Core | GET/POST | `/internal/v1/broker-accounts/**` | Internal local service call | `SERVICE_INTERNAL` | Resource/service authorization | RESTRICT | Story0044 | `InternalBrokerAccountController` |
| Broker Service | POST/PUT/DELETE | `/api/v1/broker-accounts/{id}/credentials` | Local user JWT | `USER_AUTHENTICATED` | Yes | KEEP and verify | Story0044 | `BrokerCredentialController` |
| Broker Service | POST/GET | `/api/v1/broker-accounts/{id}/validate`, `/connection-status`, `/technical-disconnect` | Local user JWT | `USER_AUTHENTICATED` | Yes | KEEP and verify | Story0044 | `BrokerCredentialController` |
| Broker Service | GET | `/api/v1/broker/**` | Local user JWT, no current Gateway route | `USER_AUTHENTICATED` or `KEEP_NOT_PUBLIC` | Yes | RESTRICT, do not expose automatically | Follow-up API cleanup | `BrokerController` |
| Broker Service | POST | `/internal/v1/executions/**` | Local JWT, current Feign call | `SERVICE_INTERNAL` | BrokerAccount/service authorization | RESTRICT | Story0044 | `ExecutionController`, Trading Core Feign client |
| Broker Service | POST | `/internal/v1/positions/**` | Local JWT, current Feign call | `SERVICE_INTERNAL` | BrokerAccount/service authorization | RESTRICT | Story0044 | `PositionManagementController` |
| Broker Service | GET | `/internal/v1/broker-accounts/{id}/risk-snapshot` | Local JWT | `SERVICE_INTERNAL` | Yes | RESTRICT | Story0044 | `BrokerQueryControllers` |
| Broker Service | GET | `/internal/v1/broker-accounts/{id}/positions`, `/orders`, account | Local JWT | `SERVICE_INTERNAL` | Yes | RESTRICT | Story0044 | `BrokerQueryControllers` |
| Broker Service | GET | `/actuator/health` | Public local exception | `MANAGEMENT` | No | KEEP restricted to health use | Story0044 runtime check | `BrokerSecurityConfiguration` |
| Market Intelligence | GET | `/api/v1/opportunities/**` | Gateway route, no local security boundary | `USER_AUTHENTICATED` | Resource visibility policy | RESTRICT | Story0044 | `OpportunityController`, Gateway |
| Market Intelligence | POST/GET | `/api/v1/intelligence/analyses/**` | Gateway route, actor absent on some paths | `USER_AUTHENTICATED` | Execution/actor where applicable | REFACTOR | Story0044 | `MarketIntelligenceController` |
| Market Intelligence | POST/GET | `/api/v1/intelligence/scans/**` | Gateway-derived `X-Actor-Id` | `USER_AUTHENTICATED` | Actor-owned scan | REFACTOR | Story0044 | `MarketIntelligenceController`, Gateway actor filter |
| Market Intelligence | POST/GET | `/trade-plans/**` | Legacy direct service path, request `actorId` | `SERVICE_INTERNAL` or removed | Yes if retained | RESTRICT/INTERNALIZE; no automatic public route | Story0044 boundary, cleanup follow-up | `TradePlanController`, no current product consumer found |
| Market Intelligence | POST | `/internal/v1/intelligence/analyses/**/trade-plans` | Feign internal, actor in body | `SERVICE_INTERNAL` + delegated actor | Yes | REFACTOR | Story0044 | `InternalAnalysisTradePlanController`, Trading Core Feign |
| Market Intelligence | POST | `/internal/v1/intelligence/opportunities/**/trade-plans` | Feign internal, actor in body | `SERVICE_INTERNAL` + delegated actor | Yes | REFACTOR | Story0044 | `InternalOpportunityTradePlanController` |
| Market Intelligence | GET/POST | `/internal/v1/trade-plans/**/risk-validation-*` | Feign internal, no service auth boundary | `SERVICE_INTERNAL` + delegated actor when owner-scoped | Yes | RESTRICT | Story0044 | `InternalTradePlanRiskController` |
| Market Intelligence | GET/POST | `/internal/v1/trade-plans/**/decisions` | Feign internal, actor body/query | `SERVICE_INTERNAL` + delegated actor | Yes | REFACTOR | Story0044 | `InternalTradePlanDecisionController` |
| Market Intelligence | GET | `/internal/trade-planning/metrics` | Internal naming only | `MANAGEMENT` | No user actor | RESTRICT | Follow-up unless current management exposure confirmed | `TradePlanningOperationsController` |
| Market Data | GET | `/api/v1/markets`, `/{marketId}`, `/{marketId}/ohlc` | Public through Gateway | `PUBLIC` | No user ownership | KEEP | Story0044 regression | `MarketController`, Gateway |
| Market Data | WebSocket | `/ws/market-data` | Gateway/public, all origins | `PUBLIC` bounded read | No user ownership | RESTRICT policy | Story0044 | WebSocket config/handler |
| Market Data | POST | `/api/v1/markets/synchronize` and subscriptions | Public route family, operation-sensitive | `USER_AUTHENTICATED` or `SERVICE_INTERNAL` after consumer check | Resource/operation authorization | RESTRICT | Story0044 classification and tests | `MarketController` |
| Market Data | POST | `/internal/markets/prices/snapshot` | Feign/network trust | `SERVICE_INTERNAL` | Caller service authorization | RESTRICT | Story0044 | `InternalMarketController`, Core/MI Feign |
| Market Data | POST | `/internal/v1/valuation-snapshots/batch` | Feign/network trust | `SERVICE_INTERNAL` | Caller service authorization | RESTRICT | Story0044 | `InternalValuationController`, Core Feign |
| Eureka | GET/POST | Eureka registry | Infrastructure access | `MANAGEMENT` | No product actor | INTERNALIZE | Follow-up infrastructure/runtime policy | Compose/Eureka config |

The position-close routes are covered by Gateway's existing `/api/v1/accounts/**`
route. They are not treated as an unrouted finding after current verification.

## 9. Ownership Model

For every user-owned resource operation, the implementation must follow:

```text
validated caller context
    -> actor resolution
    -> resource resolution
    -> resource owner/control resolution
    -> allow or explicit denial
```

The authenticated user does not automatically own every identifier supplied in
the request. At minimum, the following must be enforced:

- `Account.user.userId == actorId`;
- `BrokerAccount.ownerId == actorId` and its relation to Account is valid;
- `Trade.account.user.userId == actorId`;
- `TradePlan` owner/context owner matches actor where the operation is user-scoped;
- `ExecutionIntent` owner matches actor;
- `Position` is resolved through the correct authority and account ownership;
- `RiskConfiguration` and RiskProfile assignment belong to the authorized
  account/actor;
- broker credentials are scoped to the authorized BrokerAccount owner.

For the confirmed Trade IDOR paths, the required behavior is:

```text
USER_A + TRADE_OWNED_BY_USER_B
    -> no response data and no mutation
    -> existing repository error semantics, preferably 403 or indistinguishable
       not-found where resource enumeration must be prevented
```

The exact HTTP status must follow existing service exception conventions and be
covered by tests. The implementation must not rely on Angular-provided account
or trade IDs.

## 10. User API and Persistence Exposure

Current repository search found no Angular or Feign consumer for
`GET /api/v1/users/{id}`. Story0044 therefore plans to remove this arbitrary user
lookup. If a previously unknown legitimate product consumer is demonstrated
before implementation, the change must return to human review; the only allowed
alternative is a narrowly scoped authenticated self view.

The replacement, if exceptionally approved, must be a DTO that cannot contain
`password`, `passwordHash`, credentials, or persistence relationships that are
not part of the contract.

Acceptance must assert that the response body cannot contain `password` or
`passwordHash`, including serialized null/empty fields and error paths where
relevant.

## 11. Minimal V1 Service-Authentication Model

Story0044 shall implement no token server and no general IAM abstraction. The
minimum concrete model is:

- each calling service has externally configured signing material;
- service signing material is distinct per service identity;
- a caller creates a short-lived signed service JWT for a specific target;
- the token identifies the caller service principal and target audience;
- the receiver validates signature, issuer/service identity, audience, expiry,
  and any endpoint permission required by the operation;
- missing production-like signing material fails fast;
- no client-controlled header can supply or override the service identity;
- tests use explicit deterministic test keys/configuration.

The token should contain, conceptually, only the claims required by current V1:

```text
caller service principal
target audience
issued-at
short expiration
token id where replay protection is relevant
allowed service operation/scope where needed
optional delegated actor
```

The exact algorithm and claim names are implementation details unless they alter
the trust guarantees. A single shared secret with a self-declared caller name
is not acceptable because it cannot distinguish services. Key rotation details
remain governed by ADR-044's deferred decisions, but the implementation must
not make rotation impossible.

## 12. Current Service-Call Requirements

The following current calls require explicit `SERVICE_INTERNAL` credentials:

| Caller | Target | Current operation | Actor context | Ownership authority |
|---|---|---|---|---|
| Trading Core | Broker Service | account/position queries, risk snapshots, execution, cancellation, reconciliation, credential commands | User actor for user-driven account operations; absent/SYSTEM for background work | Broker Service for broker resources; Trading Core for user/account relation |
| Trading Core | Market Data | internal price snapshots and valuation batches | Usually no actor; operation is service-owned | Market Data validates caller and request bounds |
| Trading Core | Market Intelligence | plan generation, decision, plan load, risk snapshot/acknowledgment | Delegated actor for user-driven plan/risk operations | Trading Core validates account ownership; Market Intelligence validates plan/delegation |
| Market Intelligence | Trading Core | account lookup used by active scope/planning | Delegated user actor when initiated by user | Trading Core validates Account ownership |
| Market Intelligence | Market Data | internal price snapshots; public catalogue/OHLC reads remain public if bounded | Usually no actor for market facts | Market Data validates caller for internal operations |
| Broker Service | Trading Core | broker-account lookup and connection-status callbacks | Service actor or explicit user delegation only if needed | Trading Core validates BrokerAccount relation |

The implementation must inspect each Feign client and apply the smallest
credential policy for the actual operation. Public Market Data reads do not need
a service JWT merely because they are called by a service, but internal writes,
snapshots, and valuation operations do.

## 13. Delegated Actor Implementation

Delegated actor context is implemented only for existing user-driven calls that
need the target service to enforce user ownership.

Current required delegated flows are:

| Caller service | Actor source | Target service | Target operation | Ownership authority |
|---|---|---|---|---|
| Trading Core | locally validated user principal from Gateway/user JWT | Market Intelligence | generation from analysis/opportunity; plan decision/load; risk handoff where target ownership is checked | Trading Core account ownership plus Market Intelligence plan/context ownership |
| Market Intelligence | locally validated user context or signed delegation received from Gateway/Core | Trading Core | owned account lookup | Trading Core |
| Trading Core | locally validated user principal | Broker Service | user-driven broker account/position operations where Broker Service checks owner | Broker Service and canonical account relation |

For each migrated call, the implementation must record:

```text
CALLER_SERVICE
ACTOR_SOURCE
TARGET_SERVICE
TARGET_OPERATION
OWNERSHIP_AUTHORITY
```

The actor must be represented in verifiable signed service context, not in an
arbitrary request `actorId`. If no user is involved, the actor is absent or
`SYSTEM`; a fake user UUID is forbidden.

## 14. X-Actor-Id Migration

Current producer/consumer status:

| Location | Current role | Classification | Story action |
|---|---|---|---|
| Gateway `AuthenticatedActorHeaderFilter` | Derives header for `/api/v1/intelligence/**` | `TEMPORARY_COMPATIBILITY` | Strip client value, overwrite from authenticated principal, retain only while migration tests pass |
| Market Intelligence `MarketIntelligenceController` | Reads header for scans | `REMOVE_NOW` as trust protocol | Derive actor from local authenticated/delegated context |
| Request DTOs with `actorId` | Business/request identity field in planning internals | `REMOVE_NOW` as authority; retain only if it is a distinct business target | Reject mismatch or remove field from security contract |
| `InternalTradePlanDecisionController` query/body actor | Internal authorization input | `REMOVE_NOW` as authority | Use signed delegated actor context |
| Angular | No confirmed direct producer in current search | `NO_LONGER_USED` unless runtime proves otherwise | Add negative test for client header |

Migration rules:

- Gateway strips client-supplied `X-Actor-Id` and overwrites any compatibility
  value derived from validated context.
- Receivers authenticate the caller independently.
- Receivers compare compatibility identity with authenticated/delegated identity.
- Any mismatch is rejected, never silently resolved.
- No new endpoint may depend on `X-Actor-Id`.

## 15. Market Intelligence Security Model

Classifications:

- opportunities and user-facing scans/analysis are `USER_AUTHENTICATED`;
- existing Core-to-Intelligence planning and risk handoffs are
  `SERVICE_INTERNAL`, with signed delegated actor when user ownership is needed;
- the legacy `/trade-plans/**` surface is not a public product contract until a
  current consumer is proven. It must be restricted/internalized during the
  migration or removed later after high-confidence review.

Market Intelligence must add a local receiver-side security boundary for the
classes it exposes. Gateway authentication remains useful but is not sufficient.
The controller must no longer accept an arbitrary body/query actor as the
authorization authority.

The Story does not decide deletion of every legacy Market Intelligence endpoint;
it defines their trust class and prevents them from acting as unauthenticated
actor authorities.

## 16. Market Data Security Model

The following remain deliberately public when bounded and justified:

- market catalogue;
- ticker/current market reads;
- OHLC;
- order book;
- recent trades;
- public market WebSocket data, subject to origin and resource controls.

The following require `SERVICE_INTERNAL` or `MANAGEMENT` trust:

- internal price snapshot batch operations;
- internal valuation snapshot batches;
- provider operations;
- expensive or unbounded operations;
- management endpoints.

The Story must not put the entire Market Data service behind user JWTs. It must
instead protect the current internal operations and preserve public-read
compatibility.

For the WebSocket, first verify Angular/browser origins. Then apply the minimum
compatible allowlist/configured-origin policy, parameter bounds, and connection
cleanup controls. User authentication is not required solely because the stream
exists if it carries only deliberately public market data.

## 17. Gateway and Execution Contract

The Gateway remains responsible for ingress authentication, header sanitation,
and routing. It is not responsible for downstream object ownership.

The Execution contract must be aligned so that:

```text
Angular / external contract
    /api/v1/executions/**
        -> Gateway
        -> Trading Core controller with the same public prefix
```

The implementation must add a test that reaches an actual downstream controller
or a contract-faithful integration stub. A test that only checks the Gateway
RouteLocator is insufficient.

The existing `/api/v1/accounts/**` Gateway route already covers the position
close endpoints. This must be preserved and tested; no duplicate route is
required solely for position close.

## 18. Production and Local Topology

Story0044 does not alter Docker Compose behavior in this documentation pass and
should not remove local debugging ports automatically.

Production acceptance must document and verify:

- Gateway is externally reachable;
- service ports are internal unless explicitly justified;
- Eureka is internal/restricted;
- databases are internal;
- management endpoints are internal/restricted;
- public Market Data is exposed only by deliberate route/policy;
- local Compose port publication is not a production security control.

If the repository contains a production-specific configuration that exposes a
sensitive management endpoint, the minimal configuration correction may be
included. Broad deployment hardening remains `FOLLOW_UP_INFRASTRUCTURE`.

## 19. Failure Behavior and Logging

Security failures should use existing service error conventions and distinguish
where useful:

- unauthenticated request;
- invalid service credential;
- invalid audience;
- unauthorized caller service;
- actor mismatch;
- forbidden ownership;
- resource not found.

The implementation must avoid unnecessary resource enumeration and must not leak
passwords, password hashes, JWTs, broker credentials, or service signing keys.

Minimal diagnosable events may include endpoint, caller service class, target
service, actor-presence classification, result, and correlation ID. Bearer token
contents and secret material must never be logged.

## 20. Acceptance Criteria

ADR-044 is Accepted before implementation begins.

- [ ] For two users `USER_A` and `USER_B`, `USER_A` cannot read `TRADE_B` through `GET /api/v1/trades/{tradeId}`.
- [ ] For two users, `USER_A` cannot list or infer `TRADE_B` through the account-filtered Trade endpoint.
- [ ] For two users, `USER_A` cannot modify `TRADE_B` stop-loss or take-profit.
- [ ] Trade ownership checks are server-side and do not trust Angular/account IDs as authorization.
- [ ] `GET /api/v1/users/{id}` is removed when no legitimate current consumer exists, or is replaced by a safe DTO contract when a consumer is proven.
- [ ] No audited User response contains `password` or `passwordHash`.
- [ ] Production-like Trading Core startup fails when the required JWT secret is missing.
- [ ] Explicit test/development configuration remains usable without introducing a predictable production fallback.
- [ ] Every sensitive internal call covered by this Story authenticates a valid service principal.
- [ ] Service credentials validate signature, caller identity, target audience, expiration, and endpoint authorization.
- [ ] Missing credentials, invalid signatures, wrong audiences, and unauthorized callers are rejected.
- [ ] No new endpoint uses client-controlled `actorId` or `X-Actor-Id` as an authorization authority.
- [ ] Client-supplied identity headers are stripped or overwritten at Gateway ingress.
- [ ] Migrated delegated calls derive actor identity from validated user context and reject actor mismatches.
- [ ] Market Intelligence user operations use trusted authenticated/delegated context and its sensitive internal operations authenticate service callers.
- [ ] Internal Market Data snapshot and valuation operations authenticate trusted service callers.
- [ ] Deliberately public bounded Market Data reads remain functional without user JWT.
- [ ] Market Data WebSocket origins and input/resource bounds are compatible with current Angular usage and no longer use an unrestricted origin policy.
- [ ] The external `/api/v1/executions/**` path reaches the actual Trading Core controller mapping.
- [ ] `/internal/**` is not routed as public Gateway product traffic.
- [ ] Ambiguous legacy endpoints are not exposed or deleted without consumer evidence and an explicit matrix decision.
- [ ] Any endpoint removed by this Story has high-confidence dead-endpoint evidence and no current infrastructure consumer.
- [ ] Trading Core and Broker Service retain local security boundaries.
- [ ] LIVE position authority remains Broker and PAPER position authority remains Trading Core.
- [ ] Deterministic RiskEngine behavior and human execution authority remain unchanged.
- [ ] Security-sensitive regression tests pass, including login, PAPER onboarding, market reads, intelligence, planning, risk, execution, positions, and PAPER exit where affected.
- [ ] Runtime security acceptance is executed for each changed Gateway, service-auth, Market Intelligence, Market Data, and ownership boundary.
- [ ] No JWT, password, password hash, broker credential, or service signing key is logged or returned.

The complete normal-user PAPER journey is explicitly not an acceptance criterion
for this Story. It belongs to the next product Story.

## 21. Testing Strategy

### 21.1 User authentication

- missing token is rejected on protected routes;
- invalid token is rejected;
- expired token is rejected where the existing JWT infrastructure supports it;
- public login/register and bounded public Market Data reads remain functional.

### 21.2 Ownership and IDOR

Use persisted or integration fixtures containing:

```text
USER_A, USER_B
ACCOUNT_A, ACCOUNT_B
TRADE_A, TRADE_B
```

Verify cross-user reads, lists, stop-loss changes, take-profit changes, close
operations, and any affected aggregate access fail with the repository's
approved status semantics.

### 21.3 Serialization

- serialize the audited user response;
- assert absence of `password` and `passwordHash`;
- verify no persistence relationship accidentally exposes secret-bearing data.

### 21.4 Service identity

For every protected internal controller or contract:

- missing service credential -> reject;
- invalid signature -> reject;
- wrong audience -> reject;
- expired credential -> reject;
- unauthorized caller service -> reject;
- valid caller and allowed operation -> accepted;
- valid caller and disallowed operation -> reject.

### 21.5 Delegation

For each migrated delegated operation:

- valid signed delegated actor -> accepted where ownership allows;
- forged actor -> rejected;
- body/query actor conflict -> rejected;
- client `X-Actor-Id` conflict -> rejected;
- absent actor on an operation that requires a user -> rejected;
- service-only operation with no actor -> accepted only where explicitly allowed.

### 21.6 Gateway and contract tests

- client-supplied identity headers are removed or overwritten;
- `/internal/**` has no public Gateway route;
- Execution external path reaches downstream mapping;
- route tests and downstream controller tests use the same public contract.

### 21.7 Market Data

- public catalogue/ticker/OHLC/order-book/trades remain accessible as intended;
- internal snapshot and valuation endpoints reject untrusted callers;
- WebSocket allowed origins and parameter bounds are tested.

## 22. Runtime Validation Strategy

Implementation must run the relevant independent service tests and at least one
runtime integration path for every changed boundary:

- Gateway -> Trading Core authenticated request;
- Gateway -> Market Intelligence authenticated request with derived actor;
- Trading Core -> Broker Service service-authenticated request;
- Trading Core -> Market Data internal snapshot/valuation request;
- Trading Core -> Market Intelligence delegated request;
- public Gateway -> Market Data read without user JWT;
- rejected direct or untrusted internal request;
- Execution Gateway -> actual Trading Core controller mapping.

Runtime validation must use non-production credentials and must verify that no
secret or token appears in logs. The normal PAPER journey must not be claimed as
complete unless executed by its own Story.

## 23. Implementation Plan (Do Not Execute During Story Refinement)

1. Freeze the endpoint/trust matrix and add RED tests for the confirmed Trade
   ownership and User serialization findings.
2. Implement Trade actor resolution and server-side ownership checks, then make
   the two-user tests pass.
3. Remove the User entity response or replace it with a proven safe DTO contract.
4. Make production-like JWT configuration fail fast while preserving explicit
   test/development profiles.
5. Add the minimal service JWT primitives: externally configured per-service
   signing material, short-lived token creation, receiver validation, audience
   validation, and endpoint caller allowlists.
6. Add controlled Feign service-auth propagation for the current internal call
   inventory. Do not copy arbitrary incoming actor headers.
7. Add signed delegated actor context only to the current user-driven calls that
   need target-side ownership checks.
8. Add Gateway header sanitation and negative tests for client-controlled
   identity headers.
9. Add the Market Intelligence local security boundary and migrate its current
   user/internal endpoints according to the matrix.
10. Add Market Data internal endpoint protection while preserving public reads.
11. Align the Execution controller prefix and prove real downstream routing.
12. Apply only high-confidence legacy endpoint restrictions/removals supported by
   the matrix; defer ambiguous cleanup.
13. Apply the minimum WebSocket origin/input policy and confirm management/Eureka
   exposure without changing the platform architecture.
14. Run service, contract, negative, ownership, and affected product regression
   tests.
15. Execute runtime security acceptance and report any unvalidated boundary.

The sequence deliberately establishes ownership and identity primitives before
changing cross-service trust behavior.

## 24. Implementation Subtask Matrix

| SUBTASK | SECURITY_INVARIANT | SERVICES | DEPENDENCIES | TESTS | MODE |
|---|---|---|---|---|---|
| Trade owner resolution and IDOR protection | Authenticated user plus resource ownership is mandatory | Trading Core | Existing Account/User/Trade model and exception semantics | Two-user read/list/mutation tests | LEARN |
| Safe User response/removal | Persistence secrets never cross HTTP boundary | Trading Core | Consumer verification | JSON contract tests | LEARN |
| JWT fail-fast configuration | Missing production secret fails startup | Trading Core, Gateway as relevant | Existing profiles and test configuration | Context startup tests | LEARN |
| Service JWT token validation | Internal caller identity is explicit and receiver-validated | Shared small security code plus sensitive services | ADR-044, key configuration | signature/audience/expiry/caller tests | LEARN then PAIR |
| Feign service credential propagation | Feign calls do not rely on network trust or arbitrary headers | Trading Core, Broker Service, Market Intelligence, Market Data | Service JWT primitive | client/interceptor and integration tests | PAIR |
| Delegated actor context | Actor derives from validated user context and mismatch rejects | Trading Core, Market Intelligence, Broker Service where required | Service JWT primitive, ownership model | valid/forged/conflict tests | PAIR |
| Gateway header sanitation | External client cannot choose trusted identity headers | Gateway | Existing authenticated principal | header overwrite/removal tests | PAIR |
| Market Intelligence local boundary | User and service trust classes are enforced locally | Market Intelligence | Service JWT and principal model | public/user/internal/mismatch tests | PAIR |
| Market Data internal protection | Internal operations are stronger than public reads | Market Data, callers | Service JWT primitive | public versus internal tests | PAIR |
| Execution route alignment | External route reaches actual downstream contract | Gateway, Trading Core | Existing route/controller contract | real downstream routing test | PAIR |
| Legacy endpoint decisions | No unjustified public surface; no speculative deletion | Gateway, affected services | Consumer evidence and matrix | route/404/restriction tests | PAIR |
| WebSocket policy | Public stream is bounded and not unrestricted-origin | Market Data, Gateway, Angular contract only | Actual browser origin evidence | origin/input/cleanup tests | PAIR |
| Repetitive contract/test updates | Accepted trust classes remain covered | All affected modules | Approved patterns | regression suite | DELEGATE after pattern review |
| Documentation and matrix maintenance | Every finding has a visible destination | Repository docs | Review decisions | markdown/diff checks | DELEGATE |

The human engineer should personally drive at least the complete Trade ownership
vertical slice and the service JWT receiver validation before delegating routine
repetition.

## 25. Scope Size and Split Decision

The Story is **LARGE but coherent**, not a small Story. Its core shares one
security invariant: every sensitive request must have a trustworthy caller and a
server-authorized actor/resource relationship.

No artificial split is required before human review because splitting Trade
ownership from the service trust boundary would leave the accepted ADR only
partially actionable. However, implementation must use the sequenced subtasks
and may require a follow-up if runtime integration reveals an independent
deployment boundary.

The bounded core is:

- Trade ownership;
- User secret exposure;
- JWT fail-fast;
- service identity for current sensitive calls;
- delegated actor migration where currently required;
- Gateway sanitation;
- Market Intelligence and internal Market Data protection;
- Execution contract alignment;
- deterministic tests.

The following are intentionally not blocking implementation scope unless direct
evidence changes:

- removal of ambiguous legacy APIs;
- production infrastructure port removal;
- full Actuator/Eureka hardening;
- complete WebSocket abuse/rate-limiting platform.

## 26. Follow-ups

| FOLLOW-UP | CATEGORY | Reason |
|---|---|---|
| Remove/rework ambiguous legacy APIs after consumer confirmation | `API_CLEANUP` | Tests and historical docs are not proof of current supported consumers. |
| Production deployment port/network hardening | `INFRASTRUCTURE_HARDENING` | Compose is currently local convenience; production deployment policy needs its own validation. |
| Full Actuator/Eureka operational restriction | `INFRASTRUCTURE_HARDENING` | Include only confirmed dangerous exposure in Story0044. |
| Broker unknown outcomes, partial fills, fees, slippage, ledger | `BROKER_RELIABILITY` | Financial reliability, not this trust-boundary Story. |
| Trader history/analytics product surface | `TRADER_HISTORY_ANALYTICS` | No need to expose unrouted analytics merely to complete hardening. |
| Normal-user PAPER journey acceptance | `PAPER_JOURNEY_ACCEPTANCE` | Explicit next product Story; not part of security hardening. |
| Research datasets, replay, experiments, Research isolation | `RESEARCH_LAB_PREREQUISITE` | Research Lab is explicitly excluded. |
| OAuth2 client credentials or mTLS | `SECURITY_FOLLOW_UP` | ADR-044 preserves a migration path but V1 avoids premature infrastructure. |
| Full system/AI-agent permission model | `SECURITY_FOLLOW_UP` | Background operations use service/system identity now; detailed agent policy is deferred. |

## 27. Readiness Validation

- No unresolved architecture decision blocks the core implementation: ADR-044
  selects the trust model.
- Endpoint trust classes and ownership rules are explicit.
- Every critical audit finding has a Story0044 destination.
- Findings intentionally excluded have explicit follow-ups.
- Market Data public reads remain deliberately separate from internal operations.
- No Research Lab concern has entered the implementation scope.
- No LIVE/PAPER authority change has entered the scope.
- No deterministic risk or human execution authority change has entered the scope.
- Security behavior can be tested deterministically with negative cases.
- Story size is large but can be implemented safely in sequenced vertical slices.

`STORY0044_READY_FOR_IMPLEMENTATION = YES`, subject to human approval of the
large scope and endpoint-removal decisions. No production implementation is
authorized by this document.

## 28. Architectural Decision Status

`ADR-044 = ACCEPTED` exactly as reviewed by the human engineer. Acceptance was a
status transition only; the decision text was not reinterpreted.

`NEW_ADR_REQUIRED = NO` for Story0044. A new ADR is required only if
implementation proposes changing the selected service-auth model, introducing a
new trust authority, or changing domain/financial authority.

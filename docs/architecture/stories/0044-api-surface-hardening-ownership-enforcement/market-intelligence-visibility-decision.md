# Story 0044 - Market Intelligence Visibility Decision

## Decision

```text
OPPORTUNITY_VISIBILITY_POLICY = AUTHENTICATED_SHARED
```

An Opportunity is a persisted, versioned Market Intelligence aggregate that
describes a coherent market situation. It is synthesized from deterministic
observations and optional AI analyses, carries market/setup evidence,
explanation, score, strategy-match provenance, and lifecycle state, and is
independent from any one user's account or session.

The repository has no actor, user, account, broker, position, risk budget, or
preference field on `TradingOpportunity` or its persistence entity. Opportunity
creation is deterministic from `StrategyMatch` and evidence. Its origin enum
explicitly includes `PASSIVE_SCAN`, `ACTIVE_SCAN`, `USER_REQUEST`, and
`SYSTEM_REEVALUATION`, which is incompatible with mandatory per-user ownership.
The deterministic lineage identity is derived from the match, not from an
actor. These are domain signals, not consequences of the current unauthenticated
controller.

The Opportunity aggregate is therefore not user-owned and not personalized.
The current target is authenticated-shared rather than anonymous-public because
no current product requirement asks an unauthenticated external client to read
trading opportunities. Authentication controls exposure; it does not create
resource ownership.

## Object Ownership Matrix

| DOMAIN_OBJECT | CREATED_BY | REQUESTED_BY | OWNER | VISIBILITY | PERSONALIZED | RESOURCE_OWNERSHIP_REQUIRED | AUTHORITY_FOR_OWNERSHIP |
|---|---|---|---|---|---|---|---|
| Scan | MI active-scan application service | Authenticated user for active scans | Requesting actor, with selected account relationship | User-authenticated only | Yes, through objective/scope/account context | Yes | MI scan repository plus Trading Core account ownership |
| Opportunity | MI `OpportunityEngine` from `StrategyMatch`/evidence | Pipeline, active scan, passive/system flow; requester is provenance | No individual user; shared MI aggregate | Authenticated-shared | No | No user ownership; apply shared visibility policy | MI opportunity registry/lifecycle |
| TradePlan | MI trade-planning application service | Authenticated user through Core or authorized service delegation | Planning-context owner and trading account owner | User-authenticated or delegated service flow | Yes | Yes | MI context checks plus Trading Core account authority |
| Decision | Trade-plan decision service | User acting on a plan, transported through Core service | Owner of the TradePlan/version | User-authenticated/delegated service flow | Yes | Yes | MI TradePlan/context ownership |

## Scan Semantics

The implemented Active Scan is a user-requested, account-aware execution, not a
shared market-analysis fact. It stores actor and account IDs, fingerprints
those values for idempotency, resolves an account-aware market scope, persists
the scope snapshot, creates child active analysis executions, and exposes
history filtered by actor. The scan requester owns the scan resource and its
state.

That ownership does not transfer to the Opportunity created from downstream
market evidence. Two users may logically consume the same Opportunity, and a
future passive scanner can create one without inventing a user. An active scan
requester owns the computation request and its audit trail, while the resulting
market intelligence remains shared.

## Personalization Boundary

```text
FIRST_USER_SPECIFIC_DOMAIN_BOUNDARY = ActiveScan scope/account context for
  user-requested scans; unambiguously user-specific trading semantics begin at
  TradePlanningContext, where owner/account/risk budget/preferences are stored.
```

The Opportunity itself does not contain personalized data. Current strategy
matching and opportunity setup do not consult risk, broker, ranking, or user
preferences. The account-aware scan scope is a relevance/eligibility input to
the user-requested computation, not evidence that the resulting Opportunity is
owned by that user. Deterministic risk authority remains Trading Core's
`RiskEngine`; opportunity existence never means a trade is permitted.

## Candidate Policy Evaluation

| MODEL | Assessment |
|---|---|
| Global opportunities | Fits the aggregate and passive scanner, but anonymous exposure is not required by current product evidence. |
| Authenticated-shared | Preferred. Preserves shared domain semantics while keeping a deliberate authenticated product boundary. |
| User-scoped opportunities | Conflicts with actor-free persistence, deterministic match-derived identity, passive/system origins, and multiple-user reuse. Would duplicate identical intelligence per user without evidence of need. |
| Shared intelligence plus user projection | Not required now. The existing `TradePlanningContext`, `TradePlan`, and user-owned decisions already provide the observed personalized boundary; no current Opportunity field demonstrates a missing projection. |

## Generation and Mutation

- Authenticated users may trigger active scans, subject to account ownership.
- Authorized MI pipeline/system work may generate or refresh opportunities.
- Trading Core may request user-driven plan generation from a shared opportunity
  using a signed service identity and delegated actor.
- No arbitrary user may directly create, update, delete, or invalidate an
  Opportunity through the current public API.
- Lifecycle and expiration remain Market Intelligence-owned operations.

## Answers to Open Questions

- Anonymous opportunity access is not required: `NO`.
- Reading opportunities requires a locally validated user principal under the
  current `AUTHENTICATED_SHARED` policy.
- A passive scanner is compatible because no fake user is needed.
- An active scan requester owns the scan, not necessarily the opportunities.
- Multiple users can consume one shared opportunity and create separate plans.
- Angular's current authenticated route usage and lack of actor production are
  transport evidence, not the domain decision; the legacy backend header is
  not product policy.
- No opportunity field currently leaks user/account/broker/position/risk state.

## Architecture Decision Check

`ADR_REQUIRED = NO`. ADR-026 already defines Opportunity as market intelligence
distinct from TradePlan, ADR-033 distinguishes active contextual scanning from
passive market awareness, and ADR-044 defines the identity/ownership boundary.
This Story refinement applies those decisions to the current endpoint surface;
it does not introduce a durable new architectural concept.

## Implementation Readiness

An implementation agent can now implement MI security without deciding who owns
or may see an Opportunity, which Core calls need delegation, which service
callers are authorized, how legacy actor inputs are treated, or where ownership
checks belong. The standalone analysis endpoint needs an explicit implementation
association to a user if retained as a user route, but this is already recorded
as a contract dependency in the endpoint matrix and is not an unresolved domain
policy.

```text
MARKET_INTELLIGENCE_SECURITY_IMPLEMENTATION = READY_FOR_HUMAN_AUTHORIZATION
```

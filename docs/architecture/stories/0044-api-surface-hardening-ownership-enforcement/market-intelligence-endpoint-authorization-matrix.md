# Story 0044 - Market Intelligence Endpoint Authorization Matrix

This is the final Market Intelligence security-boundary refinement artifact.
It records domain policy separately from current transport behavior. It does
not authorize or implement runtime security changes.

## Domain Policy

`TradingOpportunity` is shared market intelligence. It is created from a
deterministic `StrategyMatch`, observations, and optional AI-analysis
references. The persisted aggregate contains market/setup evidence, score,
explanation, provenance, and lifecycle/version data. It contains no actor,
user, account, broker, position, risk budget, or user preference.

The selected visibility policy is:

```text
OPPORTUNITY_VISIBILITY_POLICY = AUTHENTICATED_SHARED
OPPORTUNITY_USER_OWNED = NO
OPPORTUNITY_PERSONALIZED = NO
```

This means all authenticated users may consume the same opportunity, subject to
the future visibility policy of the product. It does not make anonymous access
necessary. No current product requirement or Angular consumer justifies public
anonymous opportunity reads.

`ActiveScan` is different: it is a user-requested, account-aware orchestration
with persisted actor/account identity, idempotency, scope snapshot, child
analysis executions, and actor-filtered history. The requester owns the scan
resource and its execution state. The requester does not thereby own the
resulting shared opportunity.

Trade planning introduces the first clearly user-specific decision context:
`TradePlanningContext` contains `ownerId`, `tradingAccountId`, risk budget, and
planning preferences. `TradePlan` is therefore user/account-owned. Multiple
users may create distinct plans from one shared opportunity. A plan decision
is user-owned and applies to that plan/version, not to the shared opportunity.

## Required Columns

| METHOD | PATH | TRUST_CLASS | AUTHORIZED_USER_PRINCIPAL | AUTHORIZED_SERVICE_CALLERS | DELEGATED_ACTOR_REQUIRED | RESOURCE_OWNERSHIP_REQUIRED | OWNERSHIP_AUTHORITY | SYSTEM_CALL_ALLOWED | CURRENT_CONSUMER | CURRENT_IDENTITY_SOURCE | TARGET_IDENTITY_SOURCE | MIGRATION_REQUIRED |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| POST | `/api/v1/intelligence/analyses` | `USER_AUTHENTICATED` | Yes | No | No for direct user request | Yes if execution is user-associated | MI execution/scan association | Only via a separate internal system contract | No current Angular consumer found | Gateway JWT only; no actor in MI request | MI-local user principal | Bind user/scan provenance or explicitly classify this route as non-user service work |
| GET | `/api/v1/intelligence/analyses/{executionId}` | `USER_AUTHENTICATED` | Yes | No | No for direct user request | Yes when execution is user-associated | MI execution/scan association | No | No current Angular consumer found | None | MI-local user principal | Add ownership association/check |
| GET | `/api/v1/intelligence/analyses/{executionId}/result` | `USER_AUTHENTICATED` | Yes | No | No for direct user request | Yes when execution is user-associated | MI execution/scan association | No | No current Angular consumer found | None | MI-local user principal | Add ownership association/check |
| POST | `/api/v1/intelligence/analyses/{executionId}/cancel` | `USER_AUTHENTICATED` | Yes | No | No for direct user request | Yes | MI execution/scan association | No | No current Angular consumer found | None | MI-local user principal | Add ownership check |
| POST | `/api/v1/intelligence/scans/scope` | `USER_AUTHENTICATED` | Yes | No | No | Yes for supplied account | Trading Core account authority | No | No current Angular call found | `accountId` body only | MI principal plus Core-owned account lookup | Remove account as authority; retain as target resource |
| POST | `/api/v1/intelligence/scans` | `USER_AUTHENTICATED` | Yes | No | No | Yes, scan/account | MI scan plus Trading Core account authority | No | Angular `ActiveScanService.createScan` | Gateway-derived `X-Actor-Id`, `accountId` body | MI-local user principal; account validated by Core | Remove actor header authority; retain only request account target |
| GET | `/api/v1/intelligence/scans` | `USER_AUTHENTICATED` | Yes | No | No | Yes, scan | MI `ActiveScanRepository` | No | Angular `ActiveScanService.findRecent` | Gateway-derived `X-Actor-Id` | MI-local user principal | Remove header authority |
| GET | `/api/v1/intelligence/scans/{scanId}` | `USER_AUTHENTICATED` | Yes | No | No | Yes, scan | MI `ActiveScanRepository` | No | Angular `ActiveScanService.findScan` | Gateway-derived `X-Actor-Id` | MI-local user principal | Remove header authority |
| GET | `/api/v1/opportunities` | `USER_AUTHENTICATED` | Yes | No | No | No, shared aggregate | MI opportunity registry for lifecycle/read policy | No | Angular opportunity list | None | MI-local user principal for access, no owner lookup | Add local user authentication only |
| GET | `/api/v1/opportunities/active` | `USER_AUTHENTICATED` | Yes | No | No | No, shared aggregate | MI opportunity registry | No | Angular `OpportunityService.findActive` | None | MI-local user principal for access | Add local user authentication only |
| GET | `/api/v1/opportunities/{id}` | `USER_AUTHENTICATED` | Yes | No | No | No, shared aggregate | MI opportunity registry | No | Angular opportunity detail | None | MI-local user principal for access | Add local user authentication only |
| GET | `/api/v1/opportunities/history/{id}` | `USER_AUTHENTICATED` | Yes | No | No | No, shared aggregate | MI opportunity registry | No | No current Angular consumer found | None | MI-local user principal for access | Add local user authentication only |
| POST | `/trade-plans` | `SERVICE_INTERNAL` | No | Explicitly authorized internal caller only | Yes if user-driven | Yes, planning context | MI context owner; Trading Core account authority | No | No current consumer found | `actorId` body | Signed service JWT plus delegated actor | Restrict/internalize legacy endpoint; remove actor authority |
| GET | `/trade-plans/{id}` | `SERVICE_INTERNAL` | No | Explicitly authorized internal caller only | Yes if user-driven | Yes, plan/context | MI plan/context owner | No | No current consumer found | None | Signed service JWT plus delegated actor | Restrict/internalize legacy endpoint |
| GET | `/trade-plans/{id}/versions` | `SERVICE_INTERNAL` | No | Explicitly authorized internal caller only | Yes if user-driven | Yes, plan/context | MI plan/context owner | No | No current consumer found | None | Signed service JWT plus delegated actor | Restrict/internalize legacy endpoint |
| POST | `/trade-plans/{id}/replan` | `SERVICE_INTERNAL` | No | Explicitly authorized internal caller only | Yes | Yes, plan/context | MI plan/context owner | No | No current consumer found | `actorId` body | Signed service JWT plus delegated actor | Remove body actor authority |
| POST | `/internal/v1/intelligence/analyses/{analysisExecutionId}/trade-plans` | `SERVICE_INTERNAL` | No | Trading Core only | Yes | Yes, account/context; opportunity is shared | Trading Core account authority and MI context authority | No | Trading Core Feign client | `actorId`, `accountId`, context owner IDs in body | Core service JWT audience `market-intelligence` plus signed delegated actor | Replace body actor authority and add receiver validation |
| POST | `/internal/v1/intelligence/opportunities/{opportunityId}/trade-plans` | `SERVICE_INTERNAL` | No | Trading Core only | Yes | Yes, account/context; opportunity read is shared | Trading Core account authority and MI context authority | No | Trading Core Feign client | `actorId`, `accountId`, context owner IDs in body | Core service JWT audience `market-intelligence` plus signed delegated actor | Replace body actor authority and add receiver validation |
| POST | `/internal/v1/trade-plans/{planId}/versions/{version}/decisions` | `SERVICE_INTERNAL` | No | Trading Core only | Yes | Yes, plan/context | MI plan/context authority | No | Trading Core Feign client | `actorId` body | Core service JWT plus signed delegated actor | Remove body actor authority |
| GET | `/internal/v1/trade-plans/{planId}/versions/{version}` | `SERVICE_INTERNAL` | No | Trading Core only | Yes | Yes, plan/context | MI plan/context authority | No | Trading Core Feign client | `actorId` query | Core service JWT plus signed delegated actor | Remove query actor authority |
| GET | `/internal/v1/trade-plans/{tradePlanId}/versions/{version}/risk-validation-snapshot` | `SERVICE_INTERNAL` | No | Trading Core only | No | Yes, exact plan/version state | MI plan/risk handoff authority | No | Trading Core risk Feign client | None | Core service JWT audience `market-intelligence` | Add receiver service authorization |
| POST | `/internal/v1/trade-plans/{tradePlanId}/versions/{version}/risk-validation-acknowledgments` | `SERVICE_INTERNAL` | No | Trading Core only | No | Yes, exact plan/version/evaluation state | MI plan/risk handoff authority | No | Trading Core risk Feign client | None | Core service JWT audience `market-intelligence` | Add receiver service authorization |
| GET | `/internal/trade-planning/metrics` | `MANAGEMENT` | No | Explicit management principal only | No | No | Operations authority | No | No current consumer found | None | Management identity/configuration | Restrict; never route through Gateway |

## Mutation Policy

Opportunity visibility is not mutation authority:

- `OpportunityEngine` is the sole application creation/versioning entry point.
- Generation is performed by Market Intelligence pipeline/system components,
  active-scan orchestration, or explicitly authorized internal callers, not by
  arbitrary authenticated readers.
- Lifecycle transitions, expiration, invalidation, and refresh remain MI-owned
  domain operations and require service/management authorization according to
  the initiating flow.
- No current public endpoint grants a user direct opportunity mutation.
- Active-scan creation is user-authenticated and account-scoped; scan state
  inspection is requester-owned; no public scan cancellation endpoint currently
  exists.

## Final Core-to-MI Trust Definition

```text
MI_SERVICE_AUDIENCE = market-intelligence
CORE_AUTHORIZED_AS_MI_CALLER = YES
CORE_TO_MI_DELEGATED_ACTOR_ENDPOINTS =
  - analysis trade-plan generation
  - opportunity trade-plan generation
  - trade-plan decision
  - trade-plan load
CORE_TO_MI_SYSTEM_CALLS = none in the currently observed Core Feign clients
MI_SYSTEM_CALL_ENDPOINTS = opportunity lifecycle/background expiration and
  pipeline work, without a fake user actor; management metrics use management
  authority
```

The audience uses the existing service discovery/application identifier
`market-intelligence`; no alias is introduced. The service identity is
receiver-scoped and does not copy arbitrary incoming user authorization.

## Readiness

The domain ownership, visibility, user/service classes, delegated-actor needs,
ownership authorities, legacy identity treatment, and Core caller policy are
now explicit. The only implementation dependency is associating standalone
analysis executions with a user when exposed as a user route; this is an
implementation contract gap, not an unresolved product policy.

```text
MARKET_INTELLIGENCE_SECURITY_IMPLEMENTATION = READY_FOR_HUMAN_AUTHORIZATION
```

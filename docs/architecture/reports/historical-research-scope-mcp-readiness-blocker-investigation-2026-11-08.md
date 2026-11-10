# Historical ResearchScope and MCP Readiness Blocker Investigation

**Date:** 2026-11-08
**Scope:** read-only root-cause investigation for Story 0222
**Repository baseline:** `main` at `c7954b40` plus the current worktree migrations
**Runtime baseline:** Trading Core Flyway `v63`, Market Intelligence 25 migrations,
OpenCode bridge paired and connected
**Mutations:** none; no source code, grant, credential, scope, database record,
migration, container, volume, export or market data was changed

## 1. Executive Summary

The previous MCP readiness call failed for a confirmed authorization reason:

```text
HISTORICAL_SCOPE_CONSTRAINT_VIOLATION
```

The active scope used by the agent has `historicalConstraints = null`. That is
not interpreted as unrestricted access. `AgentAuthorizationService` deliberately
denies every `research:inspect` and `research:export` request when the historical
constraint aggregate is absent. The authorization denial is therefore correct
and fail-closed.

The runtime evidence is conclusive. At `2026-11-08T19:19:42.607Z`, Market
Intelligence logged a 403 from its internal Trading Core authorization call with
the exact denial reason above. The readiness service was not reached.

There is a second, independent defect in diagnostics propagation. The historical
readiness application path does not map the Feign 403 into the existing bounded
`HistoricalResearchException` response. The Feign exception escapes the
controller, and the MCP client discards all non-success response bodies and
exposes only a generic HTTP failure. This explains why the agent saw no useful
structured diagnostics even though the backend generated a safe denial reason.

The smallest safe operational correction is to create a **new** owner-scoped
ResearchScope through the existing public REST creation API, including explicit
historical constraints, then grant `research:inspect` to that new scope through
the existing human-controlled Agent Management workflow. The current scope has
no update endpoint and must not be mutated in place. The Angular UI does not
expose historical constraint fields, so the supported backend capability is not
currently represented by a complete user-facing workflow.

The replay `Forbidden` and feature-snapshot identity warnings are separate
Market Intelligence pipeline issues. They occur for the configured market in a
different scope and are not the cause of the authorization refusal.

## 2. Baseline and Evidence Classification

| Evidence | Result | Classification |
|---|---|---|
| Current Git branch/HEAD | `main` / `c7954b40` | `OBSERVED` |
| Trading Core runtime | Flyway `v63` | `OBSERVED` from existing operational evidence |
| Market Intelligence runtime | 25 migrations | `OBSERVED` from existing operational evidence |
| Bridge | `127.0.0.1:17891`, `connected: true` | `OBSERVED` |
| Agent authentication | Token exchange and authenticated Gateway reads previously succeeded | `OBSERVED` |
| Active grant | `research:inspect` on scope `87a98c16-2db5-4f0c-af41-da7539b7d817` | `OBSERVED` |
| Scope historical constraints | `null` | `OBSERVED` from the application response |
| Internal authorization result | HTTP 403, `HISTORICAL_SCOPE_CONSTRAINT_VIOLATION` | `OBSERVED` in current Market Intelligence logs |
| Readiness computation | Not reached for the failed request | `CODE_CONFIRMED` and `OBSERVED` from the log chain |
| Exact external Gateway status seen by MCP | Not captured at the MCP boundary | `UNVERIFIED` |

DevLog was consulted for Story 0222 context. Its result was truncated and did
not provide additional authorized runtime evidence; current repository code and
runtime logs are the authority for this investigation.

## 3. Actual Readiness Call Chain

```text
OpenCode
  -> MCP tool: mcp-adapter/app/server.py:166-187
  -> TradingOsClient.post(): mcp-adapter/app/client.py:99-157
  -> public Gateway POST
     /api/v1/intelligence/research/{scopeId}/readiness
  -> Gateway route "market-intelligence":
     gateway/.../GatewayRouteConfig.java:150-153
  -> Market Intelligence AgentHistoricalResearchController:
     market-intelligence/.../AgentHistoricalResearchController.java:35-46
  -> HistoricalReadinessApplicationService.inspect(): lines 20-30
  -> Feign POST to Trading Core:
     /internal/v1/agent/research/authorize
  -> InternalResearchAuthorizationController:
     trading-core/.../InternalResearchAuthorizationController.java:24-49
  -> AgentAuthorizationService.authorizeHistoricalResearch():
     trading-core/.../AgentAuthorizationService.java:100-148
  -> DENY: HISTORICAL_SCOPE_CONSTRAINT_VIOLATION
  -> readiness.inspect(...) is not called
```

### Failure points by layer

| Layer | Possible result | Evidence |
|---|---|---|
| MCP schema | Local validation error for malformed UUIDs, more than two markets, blank fields or limits outside `1..100000` | `mcp-adapter/app/server.py:166-186` |
| MCP HTTP client | Generic `GatewayClientError` for any non-2xx response; GET 401 is retried, POST 401 is not | `mcp-adapter/app/client.py:103-157` |
| Gateway authentication | 401 when no valid agent token reaches the route; current authentication is separately verified | `gateway/.../AgentAuthenticationFilter.java:27-46`, authentication report |
| Market Intelligence agent policy | Agent route allow-list can deny an unauthorized path | `MiAgentAccessPolicyFilter`, existing route tests |
| Trading Core grant/ownership | 403 for missing grant, wrong owner, inactive scope, invalid credential or out-of-scope market | `AgentAuthorizationService.java:64-89, 121-145` |
| Historical constraint check | 403 with `HISTORICAL_SCOPE_CONSTRAINT_VIOLATION` when constraints are absent or parameters exceed them | `AgentAuthorizationService.java:138-145` |
| Market Intelligence request validation | 400 `INVALID_READINESS_REQUEST` for invalid market count, cutoff order, blank profile or observation bound | `HistoricalReadinessService.java:42-48` |
| Market Intelligence readiness | Structured `READY`, `INSUFFICIENT`, `BLOCKED` or `UNKNOWN` response only after authorization succeeds | `HistoricalReadinessService.java:52-87` |

## 4. ResearchScope Domain Contract

`ResearchScope` requires an owner, a non-empty market set, creation time and an
active/revoked/expired lifecycle. A broker account is required for newly created
scopes by `ResearchScope.create(owner, brokerAccountId, ...)`, while the older
factory remains available for legacy compatibility. Historical constraints are
optional at domain construction time (`ResearchScope.java:80-123`).

`HistoricalResearchConstraints` contains:

- earliest and latest permitted cutoffs;
- maximum observations;
- maximum export rows;
- maximum export bytes;
- maximum execution duration;
- allowed dataset profiles.

Each numeric bound is optional but, when present, must be positive. Profile names
must be non-blank. A non-null constraint object with an empty profile set means
that no profile allow-list restriction is applied by `permits`; a null constraint
object has a different meaning because historical authorization rejects it before
calling `permits` (`HistoricalResearchConstraints.java:15-41` and
`AgentAuthorizationService.java:138-145`).

The historical readiness operation uses only the observation bound. Export row,
byte and duration values are sent as zero for inspection by
`HistoricalReadinessApplicationService.java:22-25`.

## 5. Persistence and Migration Behavior

### Migrations

`V59__research_scopes.sql` created the base scope and market tables without
historical columns or dataset profile rows.

`V60__historical_research_scope_constraints.sql` added the nullable historical
cutoff and limit columns and created the separate
`research_scope_dataset_profile(scope_id, dataset_profile)` table.

`V61__broker_account_market_universe.sql` added `broker_account_id` to
`research_scope` and a foreign key, together with a temporary broker-market
table.

`V62__remove_broker_account_market_universe.sql` removed that temporary table.

`V63__extend_agent_research_permissions.sql` expanded the grant check constraint
to include `research:inspect` and `research:export`.

There is no migration that backfills historical constraints or dataset profiles
for existing scopes. The migrations are additive and preserve null legacy rows.

### Loading and mapping

`ResearchScope` loads the scalar constraint columns and eagerly loads profile rows
into `historicalDatasetProfiles`. `historicalConstraints()` returns `Optional.empty()`
when all scalar fields are null and the profile set is empty (`ResearchScope.java:
153-160`). `ResearchScopeResponse.from(...)` serializes that empty Optional as
JSON `historicalConstraints: null` (`ResearchScopeResponse.java:24-30`).

Therefore:

- allowed profiles are physically stored independently in their own table;
- they are logically part of the same historical constraint aggregate;
- they are not exposed as a separate response field;
- an old scope with no V60 values and no profile rows naturally returns null;
- null is not an unrestricted default for historical operations.

### Legacy semantics

An existing pre-historical scope remains valid for ordinary `research:run`, whose
authorization path does not require historical constraints. The same scope is
intentionally invalid for `research:inspect` and `research:export` because those
paths require an explicit historical constraint object. This is a safe legacy
compatibility boundary, not evidence of a persistence defect.

## 6. API and Operator Workflow Audit

### Backend API

`POST /api/v1/research-scopes` accepts:

```json
{
  "brokerAccountId": "<owner-owned-account>",
  "expiresAt": "<optional-future-instant>",
  "historicalConstraints": {
    "earliestCutoff": "<optional-instant>",
    "latestCutoff": "<optional-instant>",
    "maxObservations": "<positive-limit>",
    "maxExportRows": "<positive-limit>",
    "maxExportBytes": "<positive-limit>",
    "maxExecutionDurationSeconds": "<positive-limit>",
    "allowedDatasetProfiles": ["feature-projection-v1"]
  }
}
```

The controller exposes creation, listing and revocation only. There is no

Scope creation resolves the complete market universe server-side from the
selected broker provider (`ResearchScopeService.java:29-59`). The caller does

### Angular workflow

The Agents page only captures display name, agent type, broker account and
permissions (`agents.ts:39-50`, `agents.html:18-64`). When a research permission
is selected, it calls `createResearchScope(brokerAccountId)` with only the broker
account ID (`agents.ts:122-139, 356-366`; `agent.service.ts:78-85`). That service
does not send `historicalConstraints`.

The UI can therefore create an ordinary provider-universe scope and grant it,
but cannot create a bounded historical scope or edit one. The backend API has the

### Answer to the operator workflow questions

| Capability | Current result |
|---|---|
| Create historical-capable scope | Yes, through public REST creation DTO; not exposed in Angular |
| Edit an existing scope | No supported API |
| Select canonical markets | Provider universe is resolved server-side at creation |
| Configure historical cutoffs/limits/profiles | Backend DTO supports it; UI does not |
| Associate grant | Yes, through existing human-controlled grant workflow |
| Review resulting constraints | Scope list response exposes them when present |

The safe no-code path is therefore creation of a new constrained scope through
the existing public API, followed by a human-approved grant. It is not safe or
supported to update the current scope through SQL or to reactivate the revoked
scope's grant.

## 7. Current Scope and Request Audit

The active scope with the active `research:inspect` grant is:

```text
scope: 87a98c16-2db5-4f0c-af41-da7539b7d817
status: ACTIVE
markets:
  8b336ec3-1557-4432-b052-ae569e041dac
  f345a888-031d-48e6-a5fb-0e49d499a2eb
  ba1576b7-7bd6-4260-a309-7e5a156cbce8
  f3974ba8-c74a-4517-b9ba-001423ce028e
historicalConstraints: null
```

The requested market `8b336ec3-1557-4432-b052-ae569e041dac` is a member of this
scope. It is therefore in scope for authorization, but current historical
population availability for that market was not measured because authorization
failed first. It must not be conflated with configured runtime market
`e23ba62a-995f-4755-8deb-11a71ab3788b`, which belongs to another scope whose

The requested profile `feature-projection-v1` is code-supported: it is the fixed
projection version used by `HistoricalReadinessService.java:24` and by the
population query at lines 52-54. It is not currently authorized by the active
scope because the scope has no historical constraint aggregate.

The requested `maxObservations=10000` is within the readiness service's hard
maximum of 100,000 (`HistoricalReadinessService.java:24-48`). It is not possible
to prove that it satisfies the scope-specific maximum because that maximum is
absent. The requested cutoff order is syntactically valid, but its permission
against this scope cannot be evaluated because the scope is rejected before

## 8. Exact Refusal Evidence

### Runtime correlation

The current Market Intelligence log contains this sanitized sequence:

```text
2026-11-08T19:19:42.607Z ... FeignException$Forbidden: [403] during [POST]
to [http://trading-core/internal/v1/agent/research/authorize]
...
[{"allowed":false,
  "denialReason":"HISTORICAL_SCOPE_CONSTRAINT_VIOLATION",
  "grantId":null,
  "grantVersion":null,
  "agentVersion":null,
  "ownerUserId":null,
  "credentialId":null,
  "credentialVersion":null,
  "agentId":null}]
```

The log then identifies `HistoricalReadinessApplicationService.inspect` and

This proves the rejection component and reason:

1. Gateway routed the public request to Market Intelligence.
2. Market Intelligence authenticated the agent route and called Trading Core.
3. Trading Core evaluated the active grant, scope, credential and constraints.
4. Trading Core denied the historical request because the scope constraints were
   absent.
5. Market Intelligence did not execute readiness data queries.

No correlation ID was present in the retained log entry, so request-level
correlation to the exact MCP invocation is not fully proven. The timestamp,
endpoint and exact parameters from the previous MCP attempt make the denial
strongly attributable to that call, but the missing correlation ID remains an
observability gap.

## 9. Null Constraint Semantics

The implementation establishes the following semantics:

```text
ordinary research: null historical constraints may remain compatible
historical inspect/export: null historical constraints => deny
```

This is explicitly default-deny in `AgentAuthorizationService.java:138-145`:

```java
var constraints = scope.historicalConstraints().orElse(null);
if (constraints == null || ... || !constraints.permits(...)) {
    return deny(..., "HISTORICAL_SCOPE_CONSTRAINT_VIOLATION");
}
```

The behavior is consistent with ADR-090's default-deny, resource-scoped agent
authorization model and with the Story 0222 requirement that historical scope,
market, interval, cutoff, profile and resource limits be checked server-side.

Conclusion: `historicalConstraints = null` means the scope is not configured for
historical inspect/export. It is not an unrestricted scope, and the current
behavior is not a scope persistence defect.

## 10. MCP Error Propagation Analysis

Two safe diagnostics are lost.

### Market Intelligence boundary

`HistoricalReadinessApplicationService.inspect` directly invokes the Feign
authorization client and only handles a returned `allowed=false` response. It
does not catch `FeignException.Forbidden`. The existing generic helper in
`MarketIntelligenceController` catches Feign 403 for other paths, but it is not
used by the historical readiness application service.

The current Feign 403 therefore escapes to Spring's default error handling rather
than becoming the bounded `HistoricalResearchException` response. This is a
confirmed historical-readiness error-mapping gap.

### MCP boundary

`TradingOsClient._request` reads the response body only to enforce its byte limit,
then calls `raise_for_status()` and raises:

```text
Gateway request failed with HTTP <status>
```

It does not preserve a safe `code`, `error` or correlation ID from a JSON error
body. The MCP tool consequently returns a generic tool error rather than a
structured refusal category.

The adapter's behavior is safe from a secret-disclosure perspective, but too
opaque for operational diagnosis. The agent should receive a bounded, safe
classification such as `HISTORICAL_SCOPE_CONSTRAINT_VIOLATION`, while never
receiving stack traces, credentials, internal headers or database details.

## 11. Controlled Reproduction and Test Evidence

No live mutation or temporary shared-runtime fixture was used.

| Check | Result | Classification |
|---|---|---|
| Runtime historical authorization | Reproduced in current logs as HTTP 403 with exact denial reason | `OBSERVED` |
| `AgentAuthorizationServiceTest` | Passed | `OBSERVED` |
| `HistoricalReadinessServiceTest` | Passed; covers insufficient population after authorization | `OBSERVED` |
| Null historical-constraint authorization test | No dedicated test found | `UNVERIFIED` test coverage gap |
| MCP adapter pytest suite | Not run; `pytest` unavailable in system interpreter | `BLOCKED` |

The existing tests prove adjacent behavior but do not cover the complete
historical authorization matrix requested here: valid constrained scope,
legacy null scope, revoked grant, unauthorized market, invalid profile, cutoff
outside scope and observation limit exceeded. The runtime log supplies the
legacy-null-scope case.

## 12. Secondary Runtime Warnings

### Replay artifact population `Forbidden`

The warning is emitted by
`ResearchArtifactPopulationService.populate()` when it calls the Market Data
`OrderBookRetentionClient` for the configured market. The client uses the
internal order-book replay/retention boundary and service authentication
(`MarketDataFeignConfiguration.java`). The warning is emitted by the scheduled
background lifecycle, not by the MCP readiness authorization path.

It affects replay artifact availability and therefore can reduce S1 replay
coverage. It does not cause the current MCP refusal because the refusal occurred
earlier in Trading Core authorization for a different scope/market request.

Classification: `HISTORICAL_DATA_PIPELINE`, current cause `UNVERIFIED`, separate
follow-up. No replay authorization or data was changed.

### Feature snapshot identity conflict

The warning is emitted by `JpaFeatureSnapshotRepository.saveIfAbsent` or
different content (`JpaFeatureSnapshotRepository.java:96-148`). This protects
ADR-060's immutable identity and conflict-detection invariant.

It affects scheduled research feature projection for the configured market and
prevents a clean population update. It does not cause the current MCP refusal;
the readiness endpoint was not reached. It is a separate data/projection
integrity issue.

Classification: `HISTORICAL_DATA_PIPELINE`, cause of the conflicting persisted
content `UNVERIFIED`, separate follow-up. No snapshot was repaired or deleted.

## 13. Confirmed Root Causes

### Root Cause A: Legacy scope has no historical contract

- **Classification:** `LEGACY_SCOPE_CONFIGURATION`
- **Evidence:** active granted scope returns `historicalConstraints: null`; V60 is additive with no backfill; authorization explicitly denies null constraints; runtime log contains the exact denial code.
- **Affected component:** Trading Core `ResearchScope` loading and `AgentAuthorizationService` historical branch.
- **Root cause:** scope was created as an ordinary provider-universe scope without historical bounds/profiles.
- **Severity:** High for Story 0222 readiness; correct security behavior.
- **Code change required:** No for the existing backend creation capability.
- **Human authorization required:** Yes, to create a new scope and grant it.
- **Minimum correction:** create a new constrained scope through the public API; grant `research:inspect` to that exact scope; repeat the bounded MCP inspection.
- **Verification:** scope list returns non-null constraints and the requested market/cutoff/profile/limit are within them; then MCP reaches a structured readiness result.

### Root Cause B: Historical readiness Feign denial is not mapped

- **Classification:** `MCP_ERROR_PROPAGATION_DEFECT`
- **Evidence:** Feign 403 escapes from `HistoricalReadinessApplicationService`; the existing controller helper does not cover this path; runtime log contains the raw Feign exception.
- **Affected component:** Market Intelligence historical readiness application/controller boundary.
- **Root cause:** historical readiness calls the authorization client without mapping Feign authorization failures to the bounded historical error contract.
- **Severity:** Medium; it obscures a correct security denial but does not grant access.
- **Code change required:** Yes, if structured diagnostics are required for this path.
- **Human authorization required:** No for a future code change approval process, but implementation approval is required before changes.
- **Minimum correction:** map authorization 401/403/5xx to bounded `HistoricalResearchException` codes/statuses, preserving safe denial categories and correlation IDs.
- **Verification:** a legacy-scope MCP call returns a structured safe denial; a valid constrained scope still reaches readiness; no secrets or stack traces are exposed.

### Root Cause C: MCP client discards safe error bodies

- **Classification:** `MCP_ERROR_PROPAGATION_DEFECT`
- **Evidence:** `TradingOsClient._request` raises only `Gateway request failed with HTTP <status>` after consuming the body.
- **Affected component:** `mcp-adapter/app/client.py`.
- **Root cause:** the adapter's error contract is status-only and does not parse bounded public error JSON.
- **Severity:** Medium; it prevents the agent from distinguishing safe operational denials.
- **Code change required:** Yes, if improved MCP diagnostics are in scope.
- **Human authorization required:** No runtime authorization change; code change requires normal engineering approval.
- **Minimum correction:** preserve an allow-listed error code/message/correlation field with strict size and schema limits.
- **Verification:** malformed or sensitive error bodies remain sanitized; known authorization errors are distinguishable.

### Separate warnings, not current refusal causes

- Replay `Forbidden`: `HISTORICAL_DATA_PIPELINE`, `UNVERIFIED` cause.
- Feature snapshot conflict: `HISTORICAL_DATA_PIPELINE`, `UNVERIFIED` cause.

## 14. Minimal Correction Options

### Option A: Existing backend workflow, no code change

Use `POST /api/v1/research-scopes` as the owner-authenticated human workflow,
including explicit historical constraints. Then use the existing Agent
Management flow to grant `research:inspect` to the newly returned scope.

This is the smallest safe correction if a trusted operator is permitted to use
the public API directly. It creates a new scope and leaves the legacy scope,
grants and market data untouched.

The current Angular UI cannot perform the constrained creation because it sends
only `brokerAccountId`. No SQL or direct database workaround is justified.

### Option B: Complete the application workflow

Add a human-facing historical-scope configuration flow and an explicit scope
selection/edit policy. This is a product/API integration change, not an urgent
authorization repair. It should preserve immutable or replacement semantics and
must not silently upgrade all legacy scopes.

### Option C: Improve error contracts

Independently map the historical Feign authorization failure in Market
Intelligence and preserve safe public error codes in MCP. This does not make the
legacy scope valid; it only makes the correct refusal diagnosable.

No option should treat null constraints as unrestricted, reactivate the revoked
scope, broaden the current grant, bypass Gateway or call internal endpoints from
MCP.

## 15. Status Matrix

| Dimension | Status | Evidence |
|---|---|---|
| Agent authentication | `PASS` | Existing token exchange and bridge pairing evidence |
| Agent authorization | `PASS / DENY CORRECTLY` | Active grant reached Trading Core; denial reason is constraint violation |
| ResearchScope validity | `BLOCKED` for historical operations | Active scope is valid ordinary scope but lacks historical contract |
| MCP request validity | `PASS` syntactically | UUIDs, one market, ordered cutoffs, supported profile string and 10,000 limit satisfy MCP schema |
| Historical readiness execution | `NOT REACHED` | Trading Core denied before `HistoricalReadinessService.inspect` |
| MCP error reporting | `DEFECTIVE / OPAQUE` | Feign and client layers discard or fail to map safe diagnostics |
| Replay warning | `SEPARATE / UNVERIFIED` | Scheduled retention call returned Forbidden |
| Snapshot warning | `SEPARATE / UNVERIFIED` | Immutable identity conflict detected by repository |

## 16. Explicitly Unresolved Questions

1. The exact external HTTP status returned by Market Intelligence/Gateway to the
   MCP client was not captured for the failed request; the internal Trading Core
   403 and denial body are confirmed.
2. No correlation ID was present in the retained refusal log, so the exact MCP
   request-to-log linkage is timestamp-based rather than correlation-based.
3. The complete historical authorization test matrix is not present in the
   repository.
4. The cause of the replay retention `Forbidden` is not established from the
   sanitized scheduled log alone.
5. The conflicting feature snapshot's differing semantic fields were not exposed
   by the runtime log; inspecting or repairing persisted rows was outside scope.
6. The four markets in the active granted scope have not been runtime-measured
   for historical population because authorization failed first.

## 17. Final Verdict

```text
MULTIPLE_CONFIRMED_CAUSES
```

The primary operational blocker is a correctly enforced legacy scope
configuration boundary: the active scope is not historical-capable because its
historical constraint aggregate is absent. A new explicitly bounded scope is
required; the current scope must not be broadened or mutated.

The investigation also confirms an independent MCP contract/error-propagation
defect that hides the safe denial reason. It should be corrected separately, but
it is not a reason to weaken authorization.

No new Engineering Story is required solely to obtain the first readiness result
if the human operator can use the existing public scope-creation API and grant
workflow. A new implementation Story is justified for the Angular historical
scope workflow and/or structured historical MCP error reporting if those product
capabilities are required.

The next safe milestone is: human-created constrained scope, exact
`research:inspect` grant on that scope, one authorized MCP readiness call, then
evaluation of the structured readiness response. No export should be attempted
before that result and the storage/data-quality checks are reviewed.

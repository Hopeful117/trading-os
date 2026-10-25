# Implementation Plan

## Overview

Implement a Trading Core-owned, read-only Position Intelligence query for one
authorized open position and expose it through a focused Angular detail surface.

The implementation will compose existing mode-aware position facts, Market Data
freshness, explicit execution lineage, TradePlan context, and deterministic Risk
scenario calculations. It will not introduce a universal Position aggregate,
persist monitoring snapshots, create events, interpret free-text thesis rules,
or perform any financial mutation.

The existing list endpoint and close workflow remain compatible. The new query
must use the same ownership and execution-mode authorities already established
by ADR-042.

## Planned Changes

### 1. Define the position intelligence read contract

**Component:** Trading Core dashboard/position read model.

Create a mode-neutral response composed of bounded sections:

- current position facts;
- account and position risk facts;
- entry/current comparable values;
- Stop/Target/Challenge consequences;
- TradePlan and Opportunity lineage;
- original rationale as historical context;
- source timestamps and freshness/status.

Use explicit availability semantics for `AVAILABLE`, `PARTIAL`, `UNKNOWN`,
`UNAVAILABLE`, `STALE`, and `NOT_APPLICABLE` as appropriate to the existing
repository conventions. Do not collapse missing facts into zero, unchanged, or
safe values.

The position identity remains the existing neutral `positionId` string. The
implementation must not create a universal persisted Position identity.

### 2. Add an authorized Trading Core query use case

**Component:** Trading Core application/dashboard layer.

Add a read-only application service that:

1. authenticates and verifies account ownership;
2. resolves the account execution mode;
3. obtains the selected open position from the existing mode-aware position
   projection;
4. returns an explicit unavailable/degraded result when the authoritative source
   is unavailable;
5. loads lineage only through canonical identifiers;
6. composes current facts and deterministic comparisons;
7. never invokes a position mutation or execution command.

For PAPER, the local `Trade` identity remains authoritative. For LIVE, the
opaque broker position reference remains a read identity only. No matching by
symbol, side, price, quantity, or timestamp is permitted.

### 3. Resolve explicit execution and TradePlan lineage

**Components:** Trading Core execution read ports and Market Intelligence
internal TradePlan read contract, only where required.

Use existing `TradeOutcome`, `ExecutionIntent`, and provenance relationships to
resolve:

```text
position -> trade/outcome -> execution intent -> trade plan/version
```

Extend an existing read port or adapter only when the current repository ports
cannot retrieve the required explicit relationship. The read path must preserve
missing lineage as a truthful state.

For a resolved TradePlan, use the existing immutable plan ID/version and expose
the existing origin and rationale. If the current internal contract does not
provide the rationale fields needed by the view, add a minimal authenticated
internal read projection in Market Intelligence rather than parsing provider or
database representations in Trading Core.

Opportunity references must be account-validated according to ADR-050. Manual
TradePlans must remain valid without an Opportunity.

### 4. Compose current facts and comparable entry/current values

**Component:** Trading Core position intelligence application service.

Reuse `PositionQueryService` and its Market Data valuation behavior for current
position facts, including price freshness, P/L, exposure, protection status,
tradability, and timestamps.

Add only deterministic comparisons supported by both sides of the data:

- entry price versus current price;
- current P/L;
- distance to Stop;
- distance to first Target;
- supported current risk and exposure values.

Do not expose Trend, Structure, Volatility, or thesis-change conclusions when an
auditable comparable entry context is absent. Return unknown or unavailable
states with a reason.

### 5. Adapt deterministic Stop/Target/Challenge projections

**Component:** Trading Core risk application adapter, using `risk-domain`.

Reuse the `TradeScenarioProjectionEngine` and Story 0160 semantics through a
position-specific adapter. The adapter must distinguish:

- current position context from planned entry context;
- current protection and quantity from TradePlan parameters;
- available versus unavailable monetary facts;
- gross scenario economics from unsupported net economics.

The adapter must not duplicate Risk Domain formulas and must not mutate Risk,
Account, Challenge, TradePlan, Trade, or broker state. Missing `lossAtStop`,
conversion, protection, or account facts must produce unavailable/partial
consequences instead of fabricated values.

### 6. Add the public read endpoint

**Component:** Trading Core `PositionController` and API contract.

Add a versioned read endpoint for one account position, using the existing
account-scoped API convention. The endpoint must:

- require JWT authentication;
- enforce account ownership;
- accept the existing neutral position identity;
- return the typed Position Intelligence response;
- distinguish not found, forbidden, unavailable, stale, and degraded results;
- use `ResponseEntity` consistently with repository conventions.

The existing list and close endpoints must not be changed unless the new read
contract requires a compatibility-preserving shared extraction.

The Gateway wildcard account route should be verified; no new route should be
added unless the current route configuration does not cover the endpoint.

### 7. Add the reactive frontend detail surface

**Components:** `trading-os-web` position service, models, routes, and position
detail component.

Add a focused Position Intelligence detail view reachable from the existing
positions page. The view should:

- load by account identity and position identity;
- use `PositionService` and Observables;
- preserve current loading, stale, unavailable, and error conventions;
- show current facts, lineage, historical rationale, comparisons, and scenario
  consequences in separate sections;
- avoid financial calculations in templates or component code;
- preserve the existing explicit close action without coupling read insight to
  mutation behavior;
- support narrow/mobile layouts.

No global frontend store or continuous background monitoring is introduced.

### 8. Add targeted tests and runtime validation

Implement tests in dependency order, beginning with deterministic backend
composition and ending with frontend/runtime validation. Preserve existing
position list and close behavior.

## Files to Modify

The following paths are expected candidates; exact names may be refined during
implementation without changing scope:

- `trading-core/src/main/java/com/hope/trading/trading_core/controller/PositionController.java`
  - expose the new authenticated position intelligence read endpoint.
- `trading-core/src/main/java/com/hope/trading/trading_core/dashboard/service/PositionQueryService.java`
  - reuse or extend single-position resolution without changing mode authority.
- `trading-core/src/main/java/com/hope/trading/trading_core/dashboard/model/`
  - register or compose neutral position intelligence response types if the
    implementation keeps them in the existing dashboard boundary.
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/domain/repository/TradeOutcomeRepositoryPort.java`
  - add only the minimum explicit lineage read operation if current queries are
    insufficient.
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/infrastructure/persistence/`
  - implement the corresponding read adapter/query if required.
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/`
  - add the position-context adapter around existing deterministic projection
    semantics.
- `trading-core/src/main/java/com/hope/trading/trading_core/tradeplanning/infrastructure/`
  - extend an existing internal TradePlan read client only if rationale/origin
    data is not already available through current ports.
- `trading-os-web/src/app/core/models/`
  - add typed Position Intelligence API models and availability states.
- `trading-os-web/src/app/core/services/position.service.ts`
  - add the read method for one position.
- `trading-os-web/src/app/features/positions/`
  - add navigation and the reactive detail presentation while preserving the
    existing list and close workflow.
- `trading-os-web/src/app/app.routes.ts`
  - register the authenticated detail route if a dedicated route is used.

## Files to Create

- `trading-core/src/main/java/com/hope/trading/trading_core/dashboard/model/PositionIntelligenceView.java`
  - typed backend response, or equivalent package-local response records.
- `trading-core/src/main/java/com/hope/trading/trading_core/dashboard/service/PositionIntelligenceQueryService.java`
  - authenticated read composition and status handling, if separation from
    `PositionQueryService` is required by existing conventions.
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/PositionScenarioProjectionAdapter.java`
  - position-to-Risk projection adaptation, if the existing service cannot
    safely expose the required read-only operation.
- `trading-core/src/test/java/com/hope/trading/trading_core/dashboard/`
  - focused ownership, source-selection, lineage, degradation, and no-mutation
    tests.
- `trading-core/src/test/java/com/hope/trading/trading_core/risk/application/`
  - position scenario adapter tests if a new adapter is required.
- `trading-os-web/src/app/features/positions/pages/position-intelligence/`
  - standalone detail component, template, styles, and tests if the existing
    page structure does not support a bounded detail surface.

No database migration or persisted monitoring entity is expected.

## Dependencies

### Internal dependencies

- Existing authenticated account ownership checks.
- Existing mode-aware `PositionQueryService` and Market Data contracts.
- Existing `TradeOutcome` and execution provenance persistence.
- Existing `TradePlanRiskPort` and Market Intelligence internal read contracts.
- Existing Risk Domain `TradeScenarioProjectionEngine`.
- Existing Challenge progress and account risk facts.
- Existing Angular `PositionService`, account selection, async pipe, and route
  conventions.

### External dependencies

None expected.

No new npm, Maven, broker, provider, or AI dependency is authorized by this
plan.

### Ordering dependencies

1. Define backend response/status semantics.
2. Implement and test authorized position resolution.
3. Implement and test explicit lineage resolution.
4. Implement and test deterministic comparisons and scenario adapter.
5. Expose and test the API endpoint.
6. Add frontend models/service/detail surface.
7. Execute module, build, and authenticated PAPER validation.

## API and Compatibility

The Story adds one read capability under the existing versioned account-scoped
API. Existing list and close contracts remain stable.

The response must be provider-neutral and must not expose Kraken txids, cTrader
mutation semantics, or provider payloads as business fields. An opaque broker
read reference may remain an identity field only when already present in the
existing neutral position contract.

Error and degraded-state semantics must distinguish:

- unauthenticated or forbidden account access;
- position not found or no longer open;
- authoritative source unavailable;
- stale current market data;
- incomplete lineage;
- unsupported or unavailable scenario facts.

No API version migration is expected beyond adding the new read endpoint.

## Database

No database schema change is planned.

Do not add:

- Position Intelligence snapshots;
- monitoring event tables;
- alert tables;
- universal Position persistence;
- TradePlan mutation fields.

Existing provenance queries may be extended read-only if necessary. Any query
extension must preserve existing indexes, ownership filtering, and immutable
lineage semantics.

## Test Plan

### Trading Core backend

Create or update tests covering:

- authenticated account ownership;
- cross-account and cross-user rejection;
- PAPER source selection from local `Trade`;
- LIVE source selection from broker-neutral facts;
- missing position and closed position behavior;
- broker unavailable/degraded behavior;
- fresh, stale, and unavailable Market Data;
- explicit lineage present;
- incomplete or absent lineage without inferred matches;
- manual TradePlan without fabricated Opportunity;
- account-scoped Opportunity ownership;
- current price/P&L/distance calculations;
- missing Stop/Target and unsupported currency;
- missing `lossAtStop` without zero substitution;
- Challenge present, absent, and unavailable;
- no mutation of any financial or execution aggregate.

### Risk Domain and projection compatibility

Create or update tests for:

- LONG and SHORT current position inputs;
- current, Stop, and Target scenario consequences;
- gross economics basis;
- missing account, conversion, protection, and risk facts;
- Challenge headroom availability;
- deterministic output and provenance;
- adapter behavior without changing `TradeScenarioProjectionEngine` semantics.

### Market Intelligence contracts

If the internal read contract changes, add contract tests for:

- exact TradePlan ID/version;
- owner/account validation;
- origin type;
- rationale preservation;
- Opportunity references and manual-plan absence;
- unavailable or expired Opportunity context.

### Angular

Create or update tests for:

- detail loading and account/position identity;
- current facts and lineage rendering;
- `UNKNOWN`, `UNAVAILABLE`, `STALE`, `PARTIAL`, and `NOT_APPLICABLE` states;
- absent TradePlan or Opportunity;
- no financial calculations in the frontend;
- navigation from the existing position list;
- preservation of the existing close flow;
- narrow/mobile rendering.

### Commands

Targeted validation should run first:

```text
mvn -f trading-core/pom.xml test
mvn -f risk-domain/pom.xml test
npm --prefix trading-os-web run test:ci
npm --prefix trading-os-web run build
git diff --check
```

These commands follow the independent Maven modules and the scripts defined in
`trading-os-web/package.json`.

Authenticated PAPER runtime validation must cover fresh data, stale data,
unavailable data, missing protection, missing lineage, and no Challenge.

## Risks

### LIVE lineage is incomplete

**Risk:** A LIVE broker position may have no explicit local relation to a
TradePlan or execution outcome.

**Mitigation:** Expose lineage only from canonical IDs and render missing
lineage explicitly. Never correlate by market data or timestamps.

### Current and entry contexts are not atomic

**Risk:** Broker, Market Data, TradePlan, and Intelligence facts may have
different observation times.

**Mitigation:** Preserve source timestamps and freshness per section. Do not
label the composite response as one atomic market snapshot.

### Risk projection lacks per-position Stop loss in some paths

**Risk:** Missing `lossAtStop` could be represented incorrectly as zero.

**Mitigation:** Treat missing protection or Risk facts as unavailable/partial and
reuse Risk Domain calculations only when required inputs are complete.

### Textual rationale is mistaken for executable rules

**Risk:** UI or service code could infer thesis invalidation from free text.

**Mitigation:** Present rationale as historical context only. Keep invalidation,
recommendations, alerts, and automatic management out of scope.

### Read surface is coupled to close actions

**Risk:** A detail view could accidentally turn an insight request into a
financial command.

**Mitigation:** Add a read-only endpoint and preserve the existing explicit close
boundary as a separate action.

### Cross-service contract expansion

**Risk:** Adding lineage could create a new authority or broad synchronous
dependency.

**Mitigation:** Prefer existing read ports and minimal internal projections. Stop
and request clarification if a new persistent authority or lifecycle is needed.

## Validation Checklist

- [ ] Only Story 0162-scoped files are modified.
- [ ] No database migration or persisted monitoring state is introduced.
- [ ] No universal Position aggregate is introduced.
- [ ] PAPER authority remains Trading Core.
- [ ] LIVE authority remains Broker Service/provider facts.
- [ ] Account and position ownership are checked server-side.
- [ ] Current facts include freshness and source timestamps.
- [ ] Missingness is explicit and never treated as zero or unchanged.
- [ ] Lineage uses canonical identifiers only.
- [ ] Manual plans do not receive fabricated Opportunities.
- [ ] TradePlan rationale remains historical and immutable.
- [ ] Trend/Structure/Volatility/thesis comparison is unavailable when entry
      evidence is not comparable.
- [ ] Stop/Target/Challenge consequences reuse deterministic Risk semantics.
- [ ] No execution, close, protection modification, alert, or recommendation is
      triggered by the read path.
- [ ] Frontend uses typed reactive data and contains no financial formulas.
- [ ] Existing position list and close workflows remain compatible.
- [ ] Ownership, degraded-state, no-lineage, and no-mutation tests pass.
- [ ] Affected Maven tests pass.
- [ ] Angular tests and production build pass.
- [ ] Formatting and `git diff --check` pass.
- [ ] Authenticated PAPER runtime validation passes for required degraded states.
- [ ] Documentation reconciliation records whether any API or user-facing docs
      require updates.

## Recommendation

Ready for implementation

This is a technical recommendation only. It does not approve the plan or
authorize implementation.

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.

# Implementation Plan - Story 0164

## Overview

Implement one bounded Market Intelligence composition capability that turns
existing Market Structure and Trend Context evidence into typed Market Thesis
level roles:

- confirmed structural level;
- protected level;
- thesis invalidation level;
- next structural objective;
- opposing obstacle.

The implementation will not change the Market Structure or Trend Context
engines. It will enrich the existing Market Thesis scenario and evidence
projection using already persisted provider-neutral evidence.

No cTrader Market Data, AI, Momentum, Volume, Order Book, News, Session,
reference-market data, Risk behavior, executable Stop/Target logic, or
`FeatureProjectionV1` semantics will be added or changed.

## Decisions

### 1. Reuse the existing Market Thesis level boundary

Extend the existing thesis level representation rather than create a generic
level service or a second structure model. The representation must preserve the
current `reference`, `role`, `timeframe`, and `value` semantics while adding only
the source metadata required by Story 0164:

- source capability/authority;
- source reference or evidence fingerprint;
- pivot and confirmation timestamps when available;
- observed/cutoff validity information where available;
- optional structured distance/reference-price context.

Any compatibility constructor required by existing tests or serialized legacy
payloads remains provider-neutral and does not introduce alternate semantics.

### 2. Use the existing observation close price as reference price

`ProductionIntelligencePipeline` already extracts the latest Observation
evidence measurement named `closePrice` for detection-time Opportunity context.
The Story will reuse this same immutable observation evidence and its
`observedAt` timestamp for objective/obstacle ordering.

The composer will not perform a retroactive Market Data lookup. If no valid
`closePrice` measurement exists, objective distance and obstacle ordering will
remain explicitly unavailable. No structural level will be used as a synthetic
reference price.

### 3. Directional objective policy

For a `LONG` scenario:

- eligible objectives are authoritative opposing HIGH levels strictly above the
  reference price;
- the nearest eligible level is selected as the next structural objective;
- lower protected LOW levels remain invalidation candidates, not objectives.

For a `SHORT` scenario:

- eligible objectives are authoritative opposing LOW levels strictly below the
  reference price;
- the nearest eligible level is selected as the next structural objective;
- higher protected HIGH levels remain invalidation candidates, not objectives.

If no reference price exists, the implementation may preserve eligible levels as
context but must not select an ordered objective or obstacles. If no eligible
level exists, the scenario records explicit objective absence rather than a
synthetic target.

### 4. Obstacle policy

Obstacles are eligible opposing structural levels that lie strictly between the
reference price and the selected objective in the scenario direction.

The first implementation will preserve obstacles as structured contextual
evidence. It will not automatically make every obstacle blocking or replace a
numeric Risk calculation. A material contradiction/state effect requires an
explicit existing Market Thesis policy predicate and must be tested separately.

### 5. Invalidation policy

An existing `TrendInvalidation` or compatible protected structural level is
projected as a thesis invalidation role and structured condition. It is never
copied into an executable Stop field and never passed to Risk as a financial
authorization decision.

An active blocking invalidation continues to prevent `CANDIDATE` under the
existing Market Thesis invariant. A pending invalidation condition remains
distinct from an active invalidation finding.

### 6. Provenance compatibility

Only evidence with compatible market, instrument, provider, interval/timeframe,
cutoff, algorithm/rule, policy, and freshness semantics may be composed.
Incompatible or stale evidence remains explicit missingness and cannot support
an objective or obstacle claim.

## Planned Changes

### 1. Add thesis-level structural value semantics

**Components:** `market-intelligence` thesis domain.

Refine the existing `MarketThesisLevel` contract or introduce the smallest
focused thesis value type needed to represent source provenance and structural
roles. The resulting contract must remain provider-neutral and serializable by
the existing persistence payload.

Add structured role values for:

- `STRUCTURAL`;
- `PROTECTED`;
- `INVALIDATION`;
- `OBJECTIVE`;
- `OBSTACLE`.

Preserve existing level role compatibility where required by current persisted
payloads. Do not introduce a generic reusable level authority.

### 2. Add a deterministic level/objective composer

**Component:** `market-intelligence` application/domain thesis composition.

Add a focused component, preferably under the existing thesis package, that:

- receives Trend Context assessment, structural level candidates, scenario
  direction, cutoff, and optional observation reference price;
- validates source compatibility;
- maps existing source levels to thesis roles;
- derives protected/invalidation roles from existing protected and invalidation
  evidence;
- selects the next objective using the directional policy;
- derives intervening obstacles;
- emits structured absence/missingness reasons;
- produces deterministic output and an input fingerprint.

The component must not call Market Data, Risk, execution, StrategyMatch, or
TradingOpportunity services.

### 3. Integrate the composer into Market Thesis composition

**Component:** `MarketThesisComposer`.

Replace the current generic level projection with the focused structural
composition boundary. Preserve the existing scenario direction and thesis
state model unless a specific acceptance criterion requires a state predicate
change.

The composer must:

- preserve current supporting, contradicting, and missing evidence behavior;
- attach structural roles and provenance to scenario levels;
- preserve active invalidation as blocking contradiction;
- expose objective/obstacle absence explicitly;
- include the structural composition policy/version in the thesis fingerprint.

### 4. Preserve the existing reference-price provenance

Use the latest valid Observation evidence measurement named `closePrice`, with
its `observedAt`, as the composition reference price. Do not perform a new
Market Data request.

The input boundary must preserve whether the reference price was:

- available and cutoff-valid;
- unavailable;
- stale or otherwise unusable.

The implementation must not infer a reference price from a swing, level,
timestamp, or executable TradePlan.

### 5. Preserve persistence and read compatibility

**Components:** existing Market Thesis persistence and read response.

Ensure the immutable serialized thesis payload preserves the new structural
roles, objective, obstacles, timestamps, source references, and missingness.

Add a migration only if the current serialized payload or query schema requires
one. Do not add a mutable current-level table or duplicate raw provider payloads.

Existing thesis versions remain readable. New versions carry the new composition
policy and fingerprint. Historical thesis content is never rewritten.

### 6. Add focused tests

Add tests in dependency order:

1. structural role and provenance value validation;
2. bullish objective and obstacle selection;
3. bearish objective and obstacle selection;
4. protected level and active invalidation behavior;
5. missing reference price and objective absence;
6. stale/incompatible source evidence;
7. deterministic fingerprint and replay;
8. persistence serialization and immutable version behavior;
9. pipeline compatibility and no-authorization boundaries.

## Expected Files

Expected implementation files, subject to repository conventions:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/thesis/MarketThesisComposer.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/thesis/StructuralLevelObjectiveComposer.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/thesis/MarketThesisLevel.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/thesis/MarketThesisScenario.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/thesis/MarketThesisEvidence.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/thesis/MarketThesisComposerTest.java`
- focused structural level/objective domain or application tests;
- persistence/contract tests only if serialization changes require them.

No `market-data`, `risk-domain`, `trading-core`, frontend, cTrader, or research
implementation file should change unless validation identifies a concrete
provider-neutral contract compatibility issue within Story scope.

## API and Persistence Compatibility

The existing authenticated Market Thesis read contract remains the read
boundary. The response may expose richer structured level metadata through the
existing scenario payload.

No new public endpoint is required.

No account, challenge, balance, equity, drawdown, exposure, position, Risk, or
execution data may be added to the thesis payload.

No raw provider payloads are duplicated. Source IDs, fingerprints, timestamps,
and deterministic role metadata are sufficient.

## Validation Plan

Run:

```text
mvn -f market-intelligence/pom.xml test
git diff --check
```

Also verify:

- objective selection is directionally correct;
- obstacles are strictly between reference and objective;
- invalidation is not exposed as executable Stop;
- objective is not exposed as executable Target;
- missing reference price does not produce fabricated geometry;
- stale/incompatible evidence cannot support current thesis levels;
- identical inputs produce identical output;
- existing Story 0163 tests remain valid;
- `FeatureProjectionV1` and S1 behavior are unchanged;
- no Risk or execution path is called.

No cTrader runtime validation is required for this provider-neutral Story.

## Documentation Reconciliation

Implementation must verify whether the enriched read contract or architecture
documentation requires a focused update. ADR-076 must not be modified by this
Story.

If no canonical documentation change is required beyond the Story and its
implementation contract, the Implementation Report must state:

```text
Documentation update: Not required.
```

## Implementation Stop Conditions

Stop and request renewed human guidance if implementation requires:

- a new Market Structure authority or changed structural semantics;
- cTrader Market Data or provider-specific thesis fields;
- a reference-market mapping decision;
- a new external data dependency;
- executable Stop/Target or Risk changes;
- changes to `FeatureProjectionV1` or S1 semantics;
- AI-generated canonical thesis evidence;
- a universal confidence/confluence score;
- a new microservice;
- mutation of historical thesis or downstream TradePlan context.

## Approval Status

This Implementation Plan is complete and awaits explicit human approval before
implementation begins.

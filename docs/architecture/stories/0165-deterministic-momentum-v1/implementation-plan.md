# Implementation Plan

## Overview

Implement Story 0165 as one provider-neutral deterministic OHLC Momentum
capability and compose its result into the existing Market Thesis path.

The plan reuses the existing capability/artifact framework, historical OHLC
context, observation envelope, Market Thesis evidence model, and persistence
model. It adds no external dependency, provider adapter, database migration, or
new service.

The main integration concern is that the production pipeline currently builds
the Thesis from an `OhlcTrendObservationRule` observation whose typed payload is
not carrying the complete Trend Context plus a future Momentum result. The plan
therefore adds a backward-compatible combined observation payload and wires the
new capability into the active and strict-observed execution plans.

The Momentum policy is deliberately small and deterministic:

- use closed OHLC closes and true ranges only;
- use a versioned bounded lookback split into a recent and preceding window;
- expose signed normalized displacement/slope, directional close persistence,
  and recent-versus-preceding directional movement for acceleration or
  deceleration;
- classify the movement without a universal score;
- compare the movement with the existing Thesis direction only during Thesis
  composition;
- classify unavailable or insufficient input as missing, never as support or
  contradiction.

The initial policy constants should be represented by a versioned immutable
policy object and frozen as part of implementation. The proposed minimal
starting shape is an eight-candle lookback divided into two four-candle
windows, majority directional persistence, and strict comparison of recent
versus preceding normalized movement. No volume or external provider input is
used.

## Planned Changes

### 1. Define the Momentum domain contract and policy

Affected component: `market-intelligence` domain Momentum package.

Create the smallest immutable policy and result model needed to represent:

- input market/instrument and interval identity;
- cutoff and observed range;
- normalized displacement/slope;
- directional movement and persistence;
- recent movement relative to the preceding window;
- `SUPPORTING`, `WEAKENING`, `CONTRADICTING`, or unavailable Momentum state;
- diagnostic/status information;
- algorithm, policy, input, and result fingerprints.

The result must describe price movement, not create Trend direction or a Market
Thesis scenario. The policy must reject invalid lookback/window parameters and
must have a stable version included in fingerprints.

Constraint preserved: Momentum remains independent of Trend/Structure and does
not consume volume, cTrader data, Risk, or an external score.

### 2. Implement the pure OHLC Momentum calculator

Affected component: domain-level deterministic calculator.

Implement validation and calculation over an ordered `HistoricalOhlcContext`:

- reject null, empty, malformed, non-positive, or incorrectly ordered candles;
- reject any candle after the evaluation cutoff;
- use only closed cutoff-eligible candles;
- calculate signed close displacement and a price-normalized slope;
- calculate directional close persistence over the configured window;
- calculate normalized recent and preceding movement from OHLC true ranges;
- classify acceleration, deceleration, continuation, neutral movement, or
  insufficient evidence using strict deterministic comparisons;
- return explicit unavailable/insufficient status rather than fabricated
  values.

The calculator must not call Trend Context, Market Structure, Risk, or any
provider client. Appended post-cutoff candles must not change an earlier result.

### 3. Expose Momentum through the capability/artifact framework

Affected component: deterministic capability layer.

Add an `OhlcMomentumAnalysisCapability` that:

- requires the existing normalized historical OHLC artifact;
- produces a versioned Momentum artifact/content payload;
- records capability execution, source artifact, freshness, source cutoff, and
  evidence fingerprints;
- exposes typed metrics only as supporting diagnostics, not as a score;
- reports complete, partial, degraded, and insufficient outcomes using the
  existing capability conventions.

Register the new artifact type and polymorphic content in the existing
artifact/Jackson configuration. No new dependency or transport contract is
introduced.

### 4. Wire Momentum into analysis execution and observation composition

Affected components:

- `ActiveAnalysisStrategy`;
- `PassiveAnalysisStrategy`;
- observation payload and consolidation components;
- polymorphic serialization configuration.

Add the Momentum capability to the existing OHLC/Trend execution plans where
historical OHLC is available, including strict observed analysis. Preserve the
existing optional/required context policy and avoid making News, Spread,
Order Book, or cTrader data prerequisites.

Extend the observation payload boundary with a backward-compatible combined
payload carrying:

- the existing `TrendContextCapabilityContent`;
- the Momentum capability content when available;
- explicit absence/status when Momentum is not available.

Update `OhlcTrendObservationRule` to select the existing Trend Context and new
Momentum artifacts from completed capability results, while preserving the
existing OHLC evidence and legacy observation behavior. Existing persisted
`TrendContextObservationPayload` values must remain deserializable and must map
to explicit missing Momentum when recomposed under the new policy.

### 5. Compose Momentum into Market Thesis

Affected component: `MarketThesisComposer` and Thesis domain composition.

Extend Thesis extraction to support the new combined payload and legacy Trend
payloads. For a directional Trend scenario:

- add available Momentum as supporting evidence when it confirms the scenario;
- add available Momentum as weakening or blocking contradiction according to
  the deterministic policy when it materially deteriorates or opposes the
  scenario;
- add missing/stale/unknown Momentum evidence without treating it as a market
  fact;
- create explicit Momentum confirmation and deterioration conditions;
- make `WATCH`, `CANDIDATE`, and `NO_TRADE` reasons identify the Momentum state;
- prevent candidate promotion solely from `TREND_DIRECTIONAL` when the
  composition policy requires current Momentum confirmation.

Momentum must not create a scenario when Trend direction is absent. Structural
invalidation remains authoritative and remains distinct from Momentum
deterioration, executable Stop, and Risk rejection.

Preserve deterministic Thesis fingerprints, immutable lineage, existing
structural levels/objectives, and historical payload compatibility.

### 6. Preserve persistence and read-only API compatibility

Affected components: Thesis serialization and persistence tests.

Use the existing JSON payload for scenario/evidence/reasons. No schema or
database migration is expected because the current repository persists typed
Thesis evidence inside the existing payload.

Verify that:

- old Thesis payloads still deserialize;
- new Momentum evidence round-trips through JPA;
- fingerprints and lineage remain stable for identical inputs;
- `MarketThesisResponse` requires no endpoint or version change.

If implementation discovers that a new persisted column or public contract is
required, stop and request scope/architecture review rather than silently
adding it.

### 7. Validate boundaries and deterministic behavior

Add focused tests first, then execute the affected Market Intelligence Maven
suite and repository formatting checks. Validation must prove the new capability
does not invoke or modify Risk, execution, broker, FeatureProjectionV1, S1,
StrategyMatch, Opportunity, or TradePlan behavior.

## Files to Modify

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/capability/ProductionArtifactTypes.java`
  - register the versioned Momentum artifact type.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/strategy/ActiveAnalysisStrategy.java`
  - include Momentum in applicable OHLC/Trend execution plans.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/strategy/PassiveAnalysisStrategy.java`
  - include Momentum in applicable OHLC/Trend execution plans.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/pipeline/OhlcTrendObservationRule.java`
  - preserve existing OHLC observation evidence and carry Trend/Momentum typed
    payload content.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/thesis/MarketThesisComposer.java`
  - consume Momentum content and apply deterministic evidence/state policy.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/artifact/ArtifactContent.java`
  - register the Momentum artifact content subtype.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/observation/ObservationPayload.java`
  - register the combined observation payload subtype while preserving the
    existing Trend payload subtype.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/configuration/JacksonPolymorphismConfiguration.java`
  - register new artifact and observation payload aliases.
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/pipeline/OhlcTrendObservationRuleTest.java`
  - verify payload and evidence composition with and without Momentum.
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/thesis/MarketThesisComposerTest.java`
  - verify Momentum supporting, contradicting, missing, and state-transition
    behavior.
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/adapter/persistence/MarketThesisJpaPersistenceTest.java`
  - verify new Momentum evidence round-trip and legacy compatibility.
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/strategy/AnalysisExecutionStrategyTest.java`
  - verify Active and Passive plans include Momentum without adding unrelated
    context requirements.

Exact package-level names for the new domain classes remain subject to the
existing naming conventions, but the capability, calculator, content, policy,
and combined observation payload responsibilities are fixed by this plan.

## Files to Create

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/momentum/MomentumPolicy.java`
  - immutable versioned lookback/window and classification policy.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/momentum/MomentumAssessment.java`
  - immutable normalized Momentum result, status, measurements, state,
    provenance references, and fingerprints.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/momentum/MomentumCalculator.java`
  - pure deterministic OHLC calculation and validation.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/momentum/MomentumCapabilityContent.java`
  - durable typed artifact content following the Trend Context content pattern.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/observation/MarketThesisObservationPayload.java`
  - backward-compatible combined Trend Context/Momentum observation payload.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/capability/OhlcMomentumAnalysisCapability.java`
  - capability adapter around the pure Momentum calculator.
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/domain/momentum/MomentumCalculatorTest.java`
  - pure calculation, symmetry, cutoff, invalid input, and deterministic replay
    tests.
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/capability/OhlcMomentumAnalysisCapabilityTest.java`
  - capability requirements, artifact, freshness, provenance, and degraded
    result tests.
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/domain/momentum/MomentumAssessmentSerializationTest.java`
  - typed content serialization and fingerprint consistency tests.

## Dependencies

Internal dependencies:

- existing `HistoricalOhlcContext` and `OhlcPoint`;
- existing `ArtifactContent`, `StoredArtifact`, `ArtifactProvenance`, and
  `CapabilityMetadata` contracts;
- existing `ObservationBuilder`, `ObservationPayload`, and capability result
  trace;
- existing `TrendContextCapabilityContent` and Market Thesis domain classes;
- existing `ProductionIntelligencePipeline` indirectly through its analysis
  strategy and observation rule.

External dependencies:

- None.

Repository prerequisites:

- the Story 0165 artifact and approved Repository Analysis;
- current `main` baseline with Story 0164 merged;
- existing normalized historical OHLC artifact contract;
- existing Market Thesis persistence and Jackson registrations.

Ordering dependencies:

1. Domain policy, result, and calculator contracts must exist before the
   capability adapter.
2. The capability artifact/content registration must exist before execution
   plan and observation wiring.
3. Observation payload wiring must exist before Market Thesis composition can
   consume Momentum.
4. Thesis state integration must be validated with persistence and legacy
   payload tests before broader module validation.

No cTrader, News Service, Risk, broker, database, or external data dependency
is required.

## Test Plan

### Pure Momentum tests

Create tests for:

- bullish and bearish symmetry;
- directional persistence at majority, neutral, and opposing boundaries;
- positive and negative normalized slope;
- recent movement acceleration;
- recent movement deceleration;
- neutral movement;
- insufficient history;
- invalid OHLC and ordering;
- cutoff exclusion and no-look-ahead;
- deterministic replay and fingerprint stability;
- policy validation and version changes.

These tests cover the deterministic, cutoff, provider-neutral, and independent
Momentum acceptance criteria.

### Capability tests

Verify:

- exact historical OHLC artifact requirement;
- deterministic capability metadata and version;
- complete, partial, and degraded outcomes;
- artifact freshness and provenance;
- source and result fingerprints;
- no volume or provider-specific input dependency.

### Observation and pipeline tests

Verify:

- active and passive plans include Momentum where OHLC is available;
- the observation retains existing OHLC evidence;
- combined Trend/Momentum payload is created when both are available;
- missing Momentum remains explicit when unavailable;
- legacy Trend-only payloads remain readable;
- no unrelated capability becomes required.

### Market Thesis tests

Verify:

- Momentum supporting evidence for aligned continuation;
- weakening or contradicting evidence for deterioration/opposition;
- explicit pending/satisfied Momentum conditions;
- `WATCH` for required missing/stale Momentum;
- `WATCH` or `NO_TRADE` for deterministic weakening/blocking policy;
- no scenario creation from Momentum without structural direction;
- structural invalidation remains blocking and distinct;
- deterministic Thesis fingerprints and reasons;
- immutable re-evaluation behavior.

### Persistence and serialization tests

Verify:

- new combined observation payload round-trip;
- new Momentum artifact content round-trip;
- new Thesis evidence round-trip through JPA;
- old Trend-only observations and old Thesis payloads remain readable;
- no database migration is needed.

### Validation commands

Expected commands for implementation validation:

```text
mvn -f market-intelligence/pom.xml -Dtest=MomentumCalculatorTest,OhlcMomentumAnalysisCapabilityTest,MarketThesisComposerTest,OhlcTrendObservationRuleTest,MarketThesisJpaPersistenceTest test
mvn -f market-intelligence/pom.xml test
git diff --check
```

Expected success conditions:

- all targeted tests pass;
- the complete Market Intelligence test suite passes;
- no formatting or whitespace errors are reported;
- no unrelated module or boundary changes appear in the diff.

## Risks

### Momentum policy overfitting

Risk: arbitrary parameters could make the capability look precise while merely
encoding a strategy preference.

Mitigation: keep the policy versioned, minimal, OHLC-only, transparent, and
free of confidence scoring. Validate only deterministic boundary behavior in
this Story.

### Candidate-state behavior change

Risk: existing directional cases may change from `CANDIDATE` to `WATCH` or
`NO_TRADE`.

Mitigation: make the policy and reason codes explicit, test old and new paths,
preserve structural invalidation authority, and keep Risk/execution semantics
unchanged.

### Observation payload compatibility

Risk: changing the typed observation payload could break persisted observations.

Mitigation: add a new subtype, retain the existing Trend payload subtype, and
test legacy deserialization and new round-trip behavior.

### Production wiring mismatch

Risk: adding a capability without updating the observation consolidation path
could leave Market Thesis unaware of Momentum.

Mitigation: update execution plans, artifact selection, combined payload
construction, and Thesis extraction together, with integration tests covering
the production observation path.

### Accidental Trend coupling

Risk: Momentum could become a renamed Trend or EMA slope calculation.

Mitigation: calculate movement directly from OHLC windows, preserve signed
movement and persistence fields, and test that Momentum cannot create Trend
direction or structure.

### Persistence or API expansion

Risk: implementation may attempt an unnecessary schema or public API change.

Mitigation: use the existing Thesis JSON payload and read-only response model;
stop for human review if a migration or incompatible contract becomes
necessary.

## Validation Checklist

- [ ] Story scope remains limited to one OHLC-based Momentum capability.
- [ ] No Volume, News, Session, Spread, Liquidity, Order Book, cTrader, AI,
      Risk, FeatureProjectionV1, or S1 changes are present.
- [ ] Momentum uses only valid closed cutoff-eligible OHLC data.
- [ ] Momentum policy and algorithm versions are fingerprinted.
- [ ] Directional persistence, normalized slope, and acceleration/deceleration
      are explicit and deterministic.
- [ ] Missing, stale, insufficient, invalid, and not-applicable states remain
      distinguishable.
- [ ] Momentum cannot create Trend direction or Market Structure.
- [ ] Momentum evidence is composed as supporting, contradicting, or missing.
- [ ] Confirmation and deterioration conditions are structured.
- [ ] `CANDIDATE` cannot be produced solely from `TREND_DIRECTIONAL` when the
      required Momentum policy is unavailable or contradictory.
- [ ] Structural invalidation remains authoritative and separate from Momentum,
      executable Stop, and Risk.
- [ ] Active and passive execution plans include the capability where OHLC is
      available.
- [ ] Observation payloads carry Trend/Momentum content and preserve legacy
      payloads.
- [ ] New artifacts and payloads are registered for Jackson serialization.
- [ ] Existing Thesis persistence round-trips new and legacy content.
- [ ] No database migration or public API version change is introduced.
- [ ] Focused tests pass.
- [ ] Full `market-intelligence` Maven tests pass.
- [ ] `git diff --check` passes.
- [ ] The final diff contains no unauthorized artifacts or unrelated changes.

## Recommendation

Ready for implementation

The implementation strategy is sufficiently defined, uses existing repository
abstractions, has identifiable tests, and preserves the approved Story and
Repository Analysis boundaries. The proposed policy constants and exact
classification predicates must remain explicit in the implementation diff and
are subject to this plan's human approval.

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.

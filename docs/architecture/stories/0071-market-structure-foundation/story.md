# Story 0071 - Market Structure Foundation

## Metadata

**ID:** `0071`

**Title:** Market Structure Foundation

**Status:** READY_FOR_IMPLEMENTATION

**Size:** Large

**Risk:** High

**Predecessor:** Story 0070 - Market Eligibility Filtering

**ADR:** ADR-049 - Reusable Deterministic Market Structure Capability

**Investigation:** `docs/architecture/reports/market-structure-foundation-investigation.md`

**Related Stories:** Stories 0062, 0063, 0064, 0065, 0067, 0069, and 0070

**Related Validation Evidence:**
`docs/architecture/stories/0067-paper-validate-trend-context/validation-report.md`
and
`docs/architecture/stories/0067-paper-validate-trend-context/runtime-reconciliation.md`

This Story is an implementation design artifact. It does not authorize
implementation, commit, push, or creation of additional Story 0071 artifacts.

## Goal

Extract the existing deterministic confirmed-swing calculation from Trend
Context into a reusable neutral Market Structure capability inside Market
Intelligence.

After implementation, the same structural point authority must be used by
Market Structure and Trend Context:

```text
Market Data evidence
        |
        v
Market Structure
        |
        v
confirmed neutral SwingPoints
        |
        v
Trend Context
        |
        v
existing contextual interpretation
```

This Story is an extraction and reuse Story. It does not introduce a new swing
algorithm, optimize parameters, or redefine trading semantics.

## Context

ADR-049 authorizes Market Structure as a reusable lower-level deterministic
capability inside Market Intelligence. The existing implementation is the
semantic baseline and already lives in the current Trend Context domain.

The current physical implementation is:

- `TrendContextEngine` calculates strict fixed-window high/low candidates;
- the right-side candle supplies the confirmation timestamp;
- candidates after the input cutoff are excluded;
- gaps intersecting a candidate window exclude that candidate;
- same-type candidates inside `minimumSeparationBars` are deterministically
  suppressed or replaced;
- the retained swing sequence feeds `SwingRelation`, protected levels,
  structural breaks, transitions, pullback, levels, and Trend Context
  fingerprints;
- `ConfirmedSwing` preserves role, type, index, price, pivot/confirmation
  timestamps, source IDs, suppression state, and
  `TrendContextEvidenceReference`;
- `TrendContextRoleSeries` exposes only closed, non-synthetic candles at or
  before the cutoff;
- `TrendContextAssessmentInput` preserves market identity, cutoff, profile,
  rule version, role input, validation findings, and input fingerprint;
- `TrendContextInputMapper` performs source mapping, ordering, duplicate
  validation, synthetic/open/cutoff findings, and role-series construction;
- `TrendContextRoleHistoryContextContributor` acquires configured role history
  under one clock boundary;
- `TrendContextAnalysisCapability` executes the engine through the existing
  capability and artifact architecture.

The existing capability path is already integrated with `AnalysisExecution`,
`Capability`, `CapabilityResult`, `StoredArtifact`, `ArtifactCacheKey`,
`ArtifactProvenance`, `CapabilityRegistry`, and `ExecutionPlanner`. The Story
must reuse those boundaries.

## Problem

Neutral confirmed structural evidence is currently private to Trend Context.
Future deterministic consumers would either depend on Trend Context's higher
level interpretation or duplicate pivot logic. Duplication would create a
second authority and allow semantic drift.

The extraction must also account for same-type suppression. The current engine
does not calculate relations from all geometric candidates; it calculates them
from the retained sequence after deterministic suppression. Returning raw
candidates to Trend Context without preserving this policy would change
relations, protected levels, breaks, transitions, pullback, and fingerprints.

## Product and Architecture Flow

The target execution flow is:

```text
explicit Active Scan scope
    -> Market Eligibility
    -> one AnalysisExecution per eligible market
    -> existing Trend Context history contributor
    -> provider-neutral OHLC evidence
    -> Market Structure capability
    -> confirmed SwingPoints for each requested interval
    -> Trend Context consumes the same retained points
    -> existing relations, levels, breaks, transitions, and context semantics
    -> existing Observation Builder when a Trend Context observation is needed
    -> existing StrategyEvaluation boundary
```

Market Structure remains inside the existing Market Intelligence service. It
does not create another scanner, `AnalysisExecution` model, service, or generic
capability framework.

Market Structure is requested through capability planning when a selected
analysis needs it. Within one `AnalysisExecution`, an identical structural
result should be reused by declared consumers through existing artifact
identity and provenance conventions. This Story must not introduce broad cache
or catalogue-wide structure infrastructure.

## Semantic Definitions

### Confirmed SwingPoint

A confirmed SwingPoint is an immutable deterministic structural fact describing
one strict local high or low under an explicit policy and complete eligible
evidence window.

The neutral fact must preserve, directly or through an equivalent evidence
reference:

- market identity;
- interval;
- type `HIGH` or `LOW`;
- price taken from the referenced candle high or low;
- `pivotTimestamp`;
- `confirmationTimestamp`;
- pivot source ID;
- confirmation source ID;
- evidence window/source references;
- algorithm identity and rule version;
- policy/profile identity and version;
- canonical parameter identity or fingerprint;
- input evidence fingerprint.

The neutral fact must not contain role meaning such as BIAS or SETUP,
direction, regime, phase, protected-level meaning, strategy criteria, account
state, or financial conclusions.

### Pivot and confirmation timestamps

`pivotTimestamp` is the timestamp of the candle containing the extremum. In the
current implementation this is the candidate candle close time.

`confirmationTimestamp` is the earliest timestamp at which the configured
algorithm had sufficient eligible closed right-side evidence to confirm the
candidate. In the current symmetric window it is the close time of the final
right-side candle.

They may differ and normally do differ. A point must not be treated as known
before its confirmation timestamp.

For cutoff `T`, every returned confirmed point must satisfy:

```text
confirmationTimestamp <= T
```

No evidence after `T` may influence the returned result.

### Valid zero-point result

A valid, complete evidence sequence may contain zero confirmed SwingPoints.
That is an available structural result with an empty point collection, not an
error and not a trading conclusion.

Insufficient, stale, unavailable, or invalid evidence must remain explicitly
distinguishable from a valid zero-point result. Missing evidence must not be
represented as an empty successful structure result.

### Structural authority

The authoritative reusable output of this Story is confirmed SwingPoint
evidence only. Suppressed candidates may be retained as compatibility and
diagnostic evidence for Trend Context, but they are not a second public point
authority.

HH, HL, LH, LL, EQ_HIGH, EQ_LOW, protected levels, structural breaks,
transitions, direction, regime, and phase remain Trend Context behavior in
this Story. They are not new Market Structure authorities.

## Current Implementation Boundaries

The implementation work must start from these actual repository boundaries.

### Neutral calculation seam

The current neutral extraction seam is the structural portion of
`market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/trendcontext/TrendContextEngine.java`:

- `swings(...)` at the current pivot calculation boundary;
- `swing(...)` at confirmed point construction;
- `suppressed(...)` at same-type suppression representation;
- `windowIntersectsGap(...)` at candidate-window gap handling.

The extracted seam includes:

- strict high and low comparisons over the configured fixed window;
- complete left and right window requirement;
- right-side confirmation timestamp;
- cutoff enforcement;
- eligible closed and non-synthetic candle requirement;
- gap-window exclusion;
- deterministic candidate ordering;
- same-type minimum-separation retention/replacement;
- exact high/low comparison and earlier-candidate tie behavior;
- source and evidence references.

The extraction seam does not include:

- `relation(...)` or `SwingRelation`;
- `direction(...)`;
- `protectedLevel(...)` or `ProtectedLevel`;
- `breaks(...)`, `StructuralBreak`, or failed-break reclaim;
- `replayBeforeBreak(...)` or transitions;
- `pullback(...)`;
- `ema(...)` or EMA evidence;
- `atr(...)` or ATR evidence;
- `extension(...)`;
- `invalidation(...)`;
- `alignment(...)` or MTF composition;
- `outcome(...)` or attention semantics.

### Current profile and configuration

`TrendContextProfile` currently owns `pivotRadius` and
`minimumSeparationBars` together with other Trend Context configuration.
`TrendContextProfile.conservativeSwingV1(...)` currently supplies the existing
profile values, including radius 2 and separation 2. These values are not
universal Market Structure constants.

`TrendContextConfiguration` supplies role intervals and history limits from
configuration properties and creates the current profile. Story 0071 must
preserve explicit policy/configuration identity and may continue selecting the
existing values for compatibility. It must not promote those values to a new
universal default.

### Current evidence and mapping

Market Data owns `OhlcEvent`, `OhlcInterval`, `MarketDataReadiness`,
`MarketFactsResponse`, and `OhlcHistoryNormalizer`. `OhlcEvent` preserves
market/provider/symbol/interval, OHLCV, open/close times, closure, source
timestamps, synthetic state, source ID, and fetch time. The normalizer sorts,
deduplicates, rejects conflicting duplicates, and creates explicitly synthetic
continuity candles.

Market Facts readiness is not a replacement for the OHLC sequence. Structure
requires the typed candle evidence and explicit cutoff.

On the current Market Intelligence side, `TrendContextRoleHistoryContextContributor`
uses `MarketDataClient.findOhlc(...)` under one clock boundary and creates
`TrendContextRoleHistory`. `TrendContextInputMapper` maps that history to
`TrendContextAssessmentInput` and `TrendContextRoleSeries`, preserving source,
closure, synthetic, gap, ordering, duplicate, and cutoff semantics.

The Story may extract a neutral candle/input mapping seam from these existing
types. It must not make Market Structure depend on Kraken DTOs, provider
symbols as algorithm semantics, or a bare price list.

### Current capability and artifact boundaries

`TrendContextAnalysisCapability` implements `DeterministicAnalysisCapability`
and `Capability`. It currently consumes the `TREND_CONTEXT_HISTORY` artifact,
maps it to `TrendContextAssessmentInput`, calls `TrendContextEngine`, and
produces `TREND_CONTEXT_ASSESSMENT` through `CapabilityResult`.

`CapabilityRegistry` and `ExecutionPlanner` register capabilities, select them
according to execution policy, and connect artifact requirements and produced
artifacts. `AnalysisExecution` remains the aggregate for one analysis.

`StoredArtifact`, `ArtifactCacheKey`, `ArtifactFreshness`, and
`ArtifactProvenance` already provide the artifact identity, parameter/input
fingerprints, freshness, producer version, producing execution, and reuse
boundary needed by this Story.

`ObservationBuilder` and the existing Trend Context observation integration
remain unchanged in authority. Market Structure must not call the Observation
Builder directly to create an Observation.

## Target Architecture

The target domain shape is conceptually:

```text
neutral provider-independent candle evidence
        |
        v
MarketStructureInput
        |
        v
MarketStructureEngine
        |
        v
MarketStructureAnalysis
    retained confirmed SwingPoints
    compatibility suppressed candidates
    explicit status/findings
    cutoff and provenance
        |
        +--> TrendContextEngine contextual calculations
        |
        +--> future deterministic structural consumers
```

The names above are proposed Story 0071 design names, not existing repository
types. The implementation may choose equivalent names, but it must preserve the
specified contracts and must not reuse Trend Context role semantics as the
neutral Market Structure domain model.

### Proposed new contracts

The implementation may introduce equivalent new types in a neutral
`domain.marketstructure` boundary. The following responsibilities are
required:

- `MarketStructureCandle`: immutable provider-neutral OHLC evidence with market,
  interval, timestamps, OHLC, closed/synthetic state, source IDs, occurrence
  and fetch timestamps;
- `MarketStructureInput`: one market, one interval, one explicit cutoff,
  ordered evidence, gap/readiness findings, algorithm identity/version, and
  policy/parameter identity;
- `SwingPoint`: neutral confirmed high/low point with price, pivot and
  confirmation timestamps, source references, and structural provenance;
- `MarketStructureAnalysis`: immutable status, retained confirmed points,
  compatibility suppression evidence, cutoff, source/readiness provenance,
  input fingerprint, algorithm/policy identity, findings, and result
  fingerprint;
- `MarketStructureEngine`: pure calculation component for one input and one
  interval.

These are proposed new Story types, not claims about current repository types.
The current `ConfirmedSwing` may be used by an adapter during migration only if
that does not make the neutral engine depend on `TrendContextRole`. The target
neutral authority must not require BIAS, SETUP, or TRIGGER.

### Status and completeness

Use existing status conventions where possible and do not create a new generic
platform status taxonomy. The structural result must nevertheless distinguish
semantically equivalent outcomes:

- `AVAILABLE`: valid evidence was evaluated, including a valid empty point
  list;
- `INSUFFICIENT_DATA`: the supplied sequence cannot satisfy the policy's
  required evidence window;
- `STALE`: evidence exists but fails the applicable freshness requirement;
- `UNAVAILABLE`: required source evidence was not available;
- `INVALID`: validation rejected the evidence or conflicting evidence exists.

The exact enum/type may follow repository naming, but the distinction must be
typed and preserved in the analysis content/findings. `CapabilityCompleteness`
may remain `COMPLETE`, `PARTIAL`, or `DEGRADED` at the orchestration boundary.
An unavailable or invalid structure result must not be reported as
`AVAILABLE` with an empty point list.

## Extraction Boundary and Same-Type Suppression

### Decision

Story 0071 chooses the equivalent of model B from the investigation:

```text
Market Structure
    confirmed retained SwingPoints
    deterministic suppressed-candidate diagnostics
        |
        v
Trend Context
    existing relations, protected levels, breaks, transitions, and context
```

Same-type suppression is part of the extracted neutral SwingPoint policy for
this Story because the current Trend Context retained sequence is the input to
all existing structural interpretation. It must not be reimplemented in a
Trend Context adapter.

The extracted policy must preserve current behavior:

- candidates are ordered by pivot time and type;
- candidates of different types are not suppressed;
- one candle may qualify as both a high and a low;
- a same-type candidate inside `minimumSeparationBars` replaces the retained
  point only when it is more extreme;
- equal same-type prices retain the earlier point;
- the non-retained candidate is recorded with the existing suppression reason
  semantics;
- retained points are ordered by pivot time and type;
- the retained point sequence is the sequence consumed by Trend Context.

The reusable public point collection contains retained confirmed SwingPoints.
Suppressed candidates are compatibility/provenance diagnostics and must not
become an alternate point collection that consumers can treat as authoritative.

Trend Context must adapt the retained neutral points into the current
Trend Context-compatible representation needed by existing code, including role
and any sequence-local data needed by existing structural calculations. The
adapter must not recalculate pivot candidates or suppression.

## Algorithm and Policy

Story 0071 preserves the existing accepted algorithm family:

```text
strict fixed-window pivot/fractal detection
+ complete eligible left/right evidence
+ right-side confirmation
```

For candidate index `i` and configured radius `N`, preserve the current strict
comparisons:

```text
high[i] > high[j] for every j in [i-N, i-1] and [i+1, i+N]
low[i]  < low[j]  for every j in [i-N, i-1] and [i+1, i+N]
```

The Story must not introduce ZigZag, ATR-based qualification, prominence,
percentage reversal, ML, or AI detection. It must not claim profitability or
universal optimality.

Radius, minimum separation, lookback, freshness, and readiness remain explicit
policy/configuration inputs. The existing `CONSERVATIVE_SWING_V1` values may be
reused for semantic compatibility, but no new universal numeric constant may
be introduced.

Any change to strict comparisons, confirmation, suppression, equality,
synthetic handling, or missing-evidence rules requires a new algorithm or
policy identity and explicit regression evidence. This Story must not silently
change those semantics.

## Temporal Honesty and Replay

The extracted engine must enforce:

- `confirmationTimestamp >= pivotTimestamp`;
- the pivot timestamp identifies the extremum candle;
- the confirmation timestamp identifies earliest knowability;
- no point is returned as confirmed before its confirmation timestamp;
- every point visible at cutoff `T` has confirmation timestamp at or before `T`;
- no candle after cutoff influences the result;
- a later analysis does not rewrite an earlier cutoff result.

Required regression scenario:

```text
cutoff T1:
    pivot candle exists
    required right-side confirmation candle is absent or not closed
    -> no confirmed SwingPoint

cutoff T2:
    required right-side confirmation candle is closed and available
    -> confirmed SwingPoint exists
    -> pivotTimestamp remains the extremum timestamp
    -> confirmationTimestamp reflects the right-side confirmation candle
```

The same point may therefore appear in Analysis(T2) with an earlier pivot
timestamp and a confirmation timestamp at T2 while being absent from
Analysis(T1). This is not a historical rewrite.

## Input Evidence Rules

The reusable calculation consumes provider-neutral typed OHLC evidence with:

- market identity;
- one interval;
- explicit cutoff;
- OHLC values;
- open and close timestamps;
- closed/open state;
- synthetic state;
- stable source IDs;
- source occurrence and fetch timestamps where available;
- gap/readiness findings;
- input evidence fingerprint.

The input must retain enough information for deterministic replay and source
lineage. It must not consume Kraken DTOs, provider-specific trading concepts,
or only `MarketFactsResponse`.

### Open candles

Open candles cannot become SwingPoints, confirm SwingPoints, or influence the
authoritative structural result. A later closed version is a new eligible
evidence state.

### Synthetic candles

Synthetic candles remain visible in provenance/readiness findings but cannot
create or confirm SwingPoints. A candidate window intersecting synthetic
evidence is not evaluable under the current conservative policy.

### Gaps and duplicates

A gap affecting required pivot or confirmation evidence excludes the candidate
and preserves an explicit finding. Conflicting duplicate evidence must reject
or mark the input invalid; input order must not select a winner. Identical
duplicates may retain the current upstream deduplication behavior.

Missing, stale, unavailable, or invalid evidence must never be converted into
neutral zero/default structure.

## Output Identity and Provenance

Each structural result must preserve identity equivalent to:

- market;
- interval;
- explicit cutoff;
- algorithm identity and rule version;
- policy/profile identity and version;
- canonical parameter identity/fingerprint;
- input evidence fingerprint/source digest;
- pivot and confirmation source references;
- evidence window boundaries;
- readiness and exclusion findings;
- capability/producer identity and version where the existing artifact model
  supports it;
- material result fingerprint.

Reuse existing conventions from `TrendContextEvidenceReference`,
`TrendContextAssessmentInput`, `ArtifactCacheKey`, `ArtifactProvenance`, and
artifact fingerprints where compatible. Do not create parallel provenance
mechanisms without a concrete gap.

The artifact identity must distinguish at least market, interval, cutoff,
algorithm/policy parameters, and input fingerprint. A result calculated at T1
must not be overwritten or relabeled as a result calculated at T2 or under a
new algorithm version.

## Capability and Artifact Integration

The extracted pure engine must be usable independently of Spring and external
services, like the current `TrendContextEngine`.

The production integration should use the existing capability boundary. The
Story may introduce a new deterministic capability equivalent to
`MarketStructureAnalysisCapability` that:

1. consumes the existing normalized/typed Trend Context history artifact or an
   extracted neutral history artifact;
2. invokes the pure Market Structure engine once per requested market/interval/
   cutoff/policy identity;
3. produces a versioned Market Structure artifact through `CapabilityResult`;
4. records status, findings, provenance, and fingerprints;
5. makes the retained points available to Trend Context;
6. allows identical results to be reused within the same `AnalysisExecution`
   through existing artifact identity and provenance.

The exact capability class name is a proposed Story type and must follow the
existing `Capability`, `DeterministicAnalysisCapability`, `CapabilityMetadata`,
`ProductionArtifactTypes`, `CapabilityRegistry`, and `ExecutionPlanner`
conventions.

Because the basic result is one market plus one interval, a capability handling
BIAS, SETUP, and optional TRIGGER history must represent them as separate
single-interval structural analyses or separate artifacts. It must not create
one neutral result that silently mixes intervals. Role labels may remain in the
Trend Context adapter or orchestration layer; they must not become Market
Structure domain semantics.

`TrendContextAnalysisCapability` must require or otherwise resolve the
extracted Market Structure artifact and pass its retained points to the
Trend Context calculation. It may continue consuming the history artifact for
EMA, ATR, pullback, break, and other contextual inputs. It must not call the
old private swing calculation or recalculate pivots.

No second `AnalysisExecution`, scanner, service, or generic capability
framework is allowed.

## Persistence and Observation

No standalone Market Structure database, SwingPoint table, read model,
event-sourcing stream, or catalogue-wide maintained structure state is in
scope.

The result may be calculated on demand or retained as a capability artifact in
the existing `AnalysisExecution` for reuse and lineage. Existing artifact
persistence may retain the result when required by the current execution and
observation lifecycle, but this Story must not introduce a new durable
projection merely for convenience.

Market Structure does not create an Observation. `ObservationBuilder` remains
the sole Observation creator under ADR-025. Story 0071 only makes structural
artifacts available to Trend Context or a future composition capability.

## Strategy, Risk, Execution, and AI Boundaries

Story 0071 does not modify `StrategyDefinition`, `StrategyEvaluation`,
`StrategyMatch`, `TradingOpportunity`, `TradePlan`, Risk, broker execution, or
execution authorization.

A SwingPoint is evidence. It is not a setup, opportunity, trade
recommendation, financial authorization, or execution command.

No AI or ML calculation, fallback, validation, or authority is introduced.
Future AI may consume the artifact, but may not confirm, rewrite, relabel, or
override deterministic structural facts.

## Scope

### Included

- Extract the neutral confirmed SwingPoint calculation from the current
  `TrendContextEngine` seam.
- Preserve strict fixed-window high/low detection and right-side confirmation.
- Preserve current cutoff, open, synthetic, gap, duplicate, and provenance
  semantics.
- Extract same-type suppression with the retained sequence because current
  Trend Context semantics depend on it.
- Define neutral typed input/output contracts as new Story types where existing
  Trend Context types cannot provide an independent boundary.
- Preserve explicit algorithm, policy, parameter, input, and source identity.
- Integrate through existing `AnalysisExecution`, `Capability`,
  `CapabilityResult`, artifact, planner, and provenance conventions.
- Make Trend Context consume the extracted retained SwingPoint authority.
- Remove the old independent SwingPoint calculation from Trend Context after
  the consumer adaptation is integrated.
- Preserve existing relation, equality, protected-level, break, transition,
  pullback, EMA, ATR, extension, invalidation, MTF, attention, and observation
  semantics by leaving those rules in Trend Context.
- Add focused deterministic Market Structure tests and Trend Context regression
  tests.
- Add the explicit T1/T2 lookahead regression.
- Validate existing Market Intelligence tests and the repository-required
  runtime/replay path when applicable.

### Output of this Story

The authoritative reusable output is confirmed retained SwingPoint evidence
for one market, one interval, one cutoff, and one algorithm/policy identity.
Suppressed candidates may be included only as compatibility diagnostics and
provenance needed by existing Trend Context behavior.

## Out of Scope

- Provisional or repainting SwingPoints.
- ZigZag, prominence, ATR-based, percentage-reversal, hybrid, ML, or AI swing
  detection.
- Universal radius or minimum-separation values.
- Reusable HH, HL, LH, LL, EQ_HIGH, or EQ_LOW authority migration.
- Protected-level migration.
- Structural-break migration.
- BOS/CHOCH terminology or semantics.
- Transition migration.
- Aggregate bullish, bearish, ranging, direction, regime, or phase structure
  state.
- Multi-timeframe structural interpretation.
- Trend-line or chart implementation.
- StrategyDefinition or StrategyEvaluation changes.
- StrategyMatch, TradingOpportunity, or TradePlan changes.
- Risk, broker, execution, or financial authorization changes.
- AI calculation, fallback, or authority.
- Direct Observation creation or a generic `MARKET_STRUCTURE` Observation.
- Standalone Market Structure persistence or a maintained passive projection.
- Catalogue-wide scanning or a second scanner.
- Broad caching infrastructure.
- Rewriting accepted Trend Context semantics.

## Acceptance Criteria

### AC1 - Single structural authority

- The extracted Market Structure engine is the only authoritative SwingPoint
  calculation.
- Trend Context consumes the extracted retained points.
- No independent pivot detection or same-type suppression remains in the
  Trend Context execution path.
- Temporary comparison execution, if used, invokes the same extracted kernel
  and is not business logic duplication.

### AC2 - Neutral reusable result

- A confirmed SwingPoint result is reusable without BIAS, SETUP, TRIGGER,
  direction, regime, phase, or strategy semantics.
- The result is scoped to one market, one interval, one cutoff, and one
  algorithm/policy identity.
- The reusable result contains only confirmed points as authoritative
  structural facts.

### AC3 - Existing algorithm preserved

- Strict fixed-window high/low comparison remains unchanged.
- Complete eligible left and right evidence is required.
- Right-side confirmation remains the source of `confirmationTimestamp`.
- Current candidate ordering, cross-type coexistence, and deterministic tie
  behavior remain unchanged.
- No alternate swing algorithm is introduced.

### AC4 - Same-type suppression preserved

- The current `minimumSeparationBars` policy is applied by the extracted
  structural kernel, not independently by Trend Context.
- Retained points match the current Trend Context retained sequence.
- Stronger same-type candidates replace weaker retained candidates under the
  existing rules.
- Equal same-type candidates retain the earlier point.
- Suppressed candidates and reasons remain available where required for current
  Trend Context evidence/fingerprint compatibility.

### AC5 - Temporal honesty and cutoff

- `pivotTimestamp` remains the extremum candle timestamp.
- `confirmationTimestamp` remains the earliest availability timestamp from the
  right-side confirmation evidence.
- Every returned point satisfies `confirmationTimestamp <= cutoff`.
- No evidence after cutoff affects the result.
- The T1/T2 lookahead regression passes both at Market Structure output and
  after Trend Context consumes the extracted points.
- A later cutoff does not rewrite an earlier result.

### AC6 - Evidence eligibility

- Open candles cannot create or confirm authoritative points.
- Synthetic candles cannot create or confirm authoritative points.
- A gap in required evidence excludes the affected candidate explicitly.
- Conflicting duplicates are invalid/unusable and never resolved by input
  order.
- Identical duplicates preserve the current deterministic deduplication
  behavior.
- Missing, stale, unavailable, and invalid evidence remain distinguishable
  from a valid empty point result.

### AC7 - Provider neutrality

- The pure engine consumes typed provider-neutral evidence.
- Kraken DTOs, provider credentials, provider error payloads, and provider
  trading concepts do not cross into the Market Structure domain.
- Source IDs, closure, synthetic state, timestamps, readiness/gaps, and input
  fingerprints remain available for replay and provenance.

### AC8 - Provenance and versioning

- Results preserve market, interval, cutoff, algorithm/rule version,
  policy/profile or canonical parameter identity, input fingerprint, source
  references, evidence boundaries, and producer/capability version where
  supported by existing artifacts.
- Pivot and confirmation source references are retained for every point.
- A semantic algorithm or policy change is distinguishable from historical
  results.
- Existing evidence/fingerprint conventions are reused rather than duplicated.

### AC9 - Trend Context compatibility

- `TrendContextEngine` no longer independently calculates SwingPoints.
- Existing relation, equality, protected-level, break, transition, pullback,
  EMA, ATR, extension, invalidation, MTF, attention, and evidence semantics
  remain unchanged.
- Existing retained swing sequences, prices, timestamps, confirmation times,
  suppression behavior, and downstream Trend Context results remain
  semantically equivalent for accepted fixtures.
- Representation-only fingerprint changes are documented and covered by tests.

### AC10 - Capability and artifact integration

- Market Structure is integrated through the existing `Capability`/
  `DeterministicAnalysisCapability`, `CapabilityResult`, artifact, registry,
  planner, and `AnalysisExecution` boundaries.
- Identical market/interval/cutoff/algorithm/policy/input requests within one
  execution can reuse the same structural artifact through existing artifact
  identity/provenance behavior.
- No second execution aggregate, scanner, service, or generic capability
  framework is introduced.
- One structural calculation does not silently mix BIAS, SETUP, and TRIGGER
  intervals.

### AC11 - Persistence and Observation boundaries

- No standalone Market Structure database, table, projection, or event stream
  is introduced.
- Results are on-demand or existing `AnalysisExecution` capability artifacts.
- Market Structure does not create an Observation.
- `ObservationBuilder` remains the sole Observation creator.

### AC12 - Strategy, Risk, execution, and AI boundaries

- No StrategyDefinition, StrategyEvaluation, StrategyMatch,
  TradingOpportunity, TradePlan, Risk, broker, or execution behavior changes.
- No financial side effect is produced by structural calculation.
- No AI or ML component participates in calculation or validation authority.

### AC13 - Explicit non-goals remain absent

- No reusable relation/event authority, aggregate state, BOS/CHOCH, trend-line,
  provisional, or universal numeric-parameter contract is created.

### AC14 - Test and validation evidence

- Focused Market Structure tests pass.
- Existing Trend Context engine, mapper, contributor, capability, observation,
  StrategyEvaluation, and relevant Active Scan regression tests pass.
- `git diff --check` passes.
- Any required runtime/replay validation is executed and reported, or clearly
  marked not executed with the reason.

## Test Strategy

Use deterministic candle fixtures, fixed clocks, explicit cutoffs, and stable
source IDs. Reuse the current `TrendContextEngineTest` and
`TrendContextTestFixtures` patterns rather than replacing the existing fixture
baseline.

### Market Structure pure-engine tests

Cover at minimum:

1. confirmed strict SwingHigh;
2. confirmed strict SwingLow;
3. valid sequence with no confirmed pivot;
4. monotonic up sequence;
5. monotonic down sequence;
6. equal neighboring highs;
7. equal neighboring lows;
8. insufficient left window;
9. insufficient right window;
10. candidate before right-side confirmation;
11. same candidate after confirmation;
12. exact cutoff boundary;
13. candle after cutoff ignored;
14. open candle ignored;
15. synthetic candle cannot create a pivot;
16. synthetic candle cannot confirm a pivot;
17. gap intersecting a required window;
18. identical duplicate evidence;
19. conflicting duplicate evidence;
20. deterministic same input and policy produce the same material result;
21. algorithm/policy provenance and parameter fingerprint;
22. different intervals remain independent;
23. same-type suppression and replacement compatibility;
24. both-high-and-low candle behavior remains unchanged if retained by the
    existing kernel;
25. valid evidence with zero points is distinct from unusable evidence.

### Trend Context extraction regression tests

Add or adapt tests proving:

- Trend Context receives the retained points from Market Structure;
- no private pivot calculation remains on the Trend Context path;
- current `SwingRelation` results remain unchanged;
- equality relation behavior remains unchanged;
- protected levels remain unchanged;
- wick-only and confirmed structural breaks remain unchanged;
- failed-break/reclaim and transition behavior remain unchanged;
- pullback, EMA, ATR, extension, invalidation, MTF alignment, and attention
  remain unchanged;
- existing evidence references and cutoff behavior remain semantically
  compatible;
- accepted canonical fixtures continue to produce equivalent Trend Context
  assessments.

The current regression baseline includes:

- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/domain/trendcontext/TrendContextEngineTest.java`;
- `TrendContextCanonicalScenarioTest`;
- `TrendContextAssessmentInputTest`;
- `TrendContextTestFixtures`;
- `TrendContextInputMapperTest`;
- `TrendContextRoleHistoryContextContributorTest`;
- `TrendContextAnalysisCapabilityTest`;
- `TrendContextObservationIntegrationTest`;
- `TrendContextReadServiceTest`;
- `TrendContextJpaObservationPersistenceTest`;
- `Story0065TrendContextStrategyTest`;
- `TrendContextEvidenceSelectorTest`;
- relevant `ProductionIntelligencePipelineTest` and Active Scan tests.

### Invariant tests

Prove:

```text
confirmationTimestamp >= pivotTimestamp
confirmed point at cutoff T -> confirmationTimestamp <= T
no source candle after confirmation contributes to that point
no source candle after cutoff contributes to the result
SwingHigh.price == referenced candle high
SwingLow.price == referenced candle low
open evidence cannot create or confirm a point
synthetic evidence cannot create or confirm a point

same evidence
+ same cutoff
+ same algorithm
+ same parameters
    -> same material result/fingerprint
```

Also prove that no structural execution creates a StrategyMatch,
TradingOpportunity, TradePlan, Risk result, or execution command.

## Implementation Plan

The implementation engineer should follow this order and keep the changes
inside the existing Market Intelligence architecture.

1. Inventory all `ConfirmedSwing`, `SwingRelation`, pivot, suppression,
   protected-level, structural-break, confirmation-time, and pivot-time usages
   before editing.
2. Add focused characterization assertions around the current
   `TrendContextEngineTest`, canonical scenario fixtures, input mapper tests,
   capability tests, and observation/strategy provenance tests.
3. Identify the exact neutral evidence shape currently represented by
   `TrendContextCandle`, `TrendContextRoleSeries`, and
   `TrendContextEvidenceReference`.
4. Define the smallest neutral input/output contracts needed for one market,
   one interval, one cutoff, and one policy identity. Proposed names are
   `MarketStructureCandle`, `MarketStructureInput`, `SwingPoint`, and
   `MarketStructureAnalysis`; final names must follow repository conventions.
5. Extract the pure pivot, confirmation, gap-window, candidate ordering, and
   same-type retention/suppression logic from `TrendContextEngine` into a
   neutral Market Structure engine/policy. Do not copy the logic into a second
   implementation.
6. Preserve current profile-supplied `pivotRadius` and
   `minimumSeparationBars` through explicit policy identity and parameter
   fingerprint. Do not introduce universal values.
7. Preserve source IDs, evidence windows, rule/profile versions, input
   fingerprints, pivot timestamps, confirmation timestamps, suppression reasons,
   and cutoff semantics.
8. Extract or reuse the current provider-neutral mapping/validation seam from
   `TrendContextInputMapper` and `TrendContextRoleSeries` without making the
   neutral engine depend on Trend Context roles.
9. Add the Market Structure artifact type and deterministic capability using
   the existing `Capability`, `CapabilityMetadata`, `CapabilityResult`,
   `CapabilityRegistry`, and `ExecutionPlanner` contracts.
10. Configure the capability to consume the existing role-history evidence or
    an equivalent neutral history artifact and calculate separate
    single-interval structural results for configured intervals.
11. Adapt `TrendContextAnalysisCapability` and the Trend Context engine input
    path so Trend Context consumes the retained points produced by Market
    Structure while retaining the existing history for non-structural
    indicators and context rules.
12. Remove the old independent `swings`, `swing`, `suppressed`, and gap-window
    execution from the Trend Context path after the adapter is integrated.
13. Keep `SwingRelation`, protected levels, structural breaks, transitions,
    pullback, EMA, ATR, extension, invalidation, MTF alignment, attention, and
    Observation/Strategy integration in their current Trend Context boundaries.
14. Add capability/artifact reuse assertions for identical structural identity
    within one `AnalysisExecution`; do not build broad caching.
15. Add the pure Market Structure test matrix and temporal invariants.
16. Add the T1/T2 lookahead regression through both Market Structure and Trend
    Context consumption.
17. Run the existing Market Intelligence focused Trend Context and capability/
    observation/strategy regression suite, then the module test suite.
18. Run relevant Market Data normalization/history tests without moving
    structural ownership into Market Data.
19. Execute repository-required runtime or replay validation when the current
    workflow requires it, and report unexecuted paths explicitly.
20. Perform independent code review focused on single authority, semantic
    compatibility, lookahead, evidence provenance, and forbidden side effects.
21. Obtain human approval before any commit or integration.

## Security Considerations

- Market Structure consumes public market evidence only and must not access
  credentials, account secrets, private keys, or broker authorization data.
- Provider-specific DTOs, credentials, and error payloads must remain inside
  existing Market Data adapters.
- Diagnostics and provenance must not log complete provider payloads when stable
  source references are sufficient.
- No new public endpoint or authentication bypass is required by this Story.
- No structural result may be treated as Risk or execution authorization.

## Risks

- Extraction may alter Trend Context behavior if retained versus suppressed
  points are not preserved exactly.
- `ConfirmedSwing` contains Trend Context role semantics; moving it directly
  into a neutral package could leak BIAS/SETUP/TRIGGER into Market Structure.
- The current `TrendContextProfile` combines structural and contextual
  parameters; the extraction must preserve configuration identity without
  promoting the profile's numeric values to universal defaults.
- The current history contributor acquires multiple role intervals under one
  boundary; an artifact design that silently mixes intervals would violate
  ADR-049.
- Existing artifact serialization and fingerprints may need explicit
  representation migration if new content types are introduced.
- A valid empty point list could be confused with unavailable evidence unless
  status and completeness are tested separately.
- Runtime Active Scan validation may reveal acquisition or deployment issues
  that unit tests cannot prove.
- Confirmation latency remains inherent to confirmed-only evidence.

## Human Decisions and Runtime Configuration

### Resolved by ADR-049

- Market Structure is owned by Market Intelligence.
- The existing Trend Context structural kernel is extracted and reused.
- There is exactly one authoritative SwingPoint calculation.
- Trend Context consumes Market Structure points.
- V1 is confirmed-only.
- Market Data remains the factual OHLC evidence owner.
- Observation Builder remains the Observation authority.
- No standalone Market Structure persistence is introduced.
- Strategy, Opportunity, TradePlan, Risk, execution, and AI are outside the
  authority of this Story.
- Migration is incremental and must preserve accepted semantics.

### Implementation detail

- Exact neutral type names and package placement.
- Whether the artifact contains one per-interval result or an adapter collection
  of single-interval results while preserving one-interval domain identity.
- Exact adapter shape from `TrendContextRoleHistory`/`TrendContextRoleSeries` to
  neutral evidence.
- Exact mapping from neutral `SwingPoint` back to the current Trend Context
  representation.
- Whether existing evidence references are generalized or wrapped at the
  adapter boundary.

These details must not alter the semantic contract.

### Runtime/profile configuration

- Existing `pivotRadius` and `minimumSeparationBars` values supplied by the
  existing profile.
- Requested candle limits and minimum eligible history.
- Role intervals and optional trigger configuration.
- Freshness/readiness boundaries.
- Algorithm, rule, and profile version strings.

These are runtime/configuration concerns, not architectural blockers. They must
remain explicit and versioned.

### Genuine human decisions remaining

None for the approved Story scope. Implementation may proceed without another
domain or architecture investigation, provided it does not change the
ADR-049 semantics. Any intentional change to suppression, equality,
eligibility, confirmation, persistence, or consumer authority requires a new
decision before implementation.

## ADR Assessment

**ADR_REQUIRED: NO**

ADR-049 already authorizes Market Structure ownership, extraction/reuse,
confirmed-only evidence, temporal honesty, single authority, existing
capability/artifact integration, persistence boundaries, Observation Builder
authority, and Strategy/Risk/AI separation.

This Story must stop and report a contradiction rather than create another ADR
if implementation discovers that satisfying the scope requires changing one of
those approved decisions.

## Definition of Done

- [ ] Story 0071 scope and this design are approved by the human engineer.
- [ ] Repository Analysis is accepted if required by the project workflow.
- [ ] Implementation Plan is accepted if required by the project workflow.
- [ ] The neutral confirmed SwingPoint extraction is implemented from the
      existing `TrendContextEngine` kernel; no parallel pivot engine exists.
- [ ] Same-type suppression and retained-sequence behavior are preserved by the
      extracted authority.
- [ ] Trend Context consumes extracted points and no longer recalculates them.
- [ ] One-market/one-interval/explicit-cutoff identity is enforced.
- [ ] Strict fixed-window and right-side confirmation semantics are preserved.
- [ ] Open, synthetic, gap, duplicate, invalid, stale, unavailable, and
      insufficient evidence behavior is explicit and fail-closed.
- [ ] Pivot and confirmation timestamps, source references, fingerprints, and
      algorithm/policy provenance are preserved.
- [ ] T1/T2 no-lookahead regression passes.
- [ ] Existing Trend Context fixtures remain semantically equivalent.
- [ ] Capability and artifact integration uses existing `AnalysisExecution`
      boundaries and supports reuse without broad caching.
- [ ] No standalone persistence or direct Observation creation is introduced.
- [ ] No Strategy, Opportunity, TradePlan, Risk, execution, or AI authority is
      introduced.
- [ ] Focused Market Structure, Trend Context, capability, observation,
      strategy, and relevant Market Data tests pass.
- [ ] Required runtime/replay validation is completed or explicitly reported as
      not executed.
- [ ] Independent code review is completed.
- [ ] Human approval is obtained before commit.

## Final Verdict

**READY_FOR_IMPLEMENTATION**

ADR-049 resolves the prior ownership blocker. The current repository provides a
clear extraction seam, a tested semantic baseline, existing capability/artifact
orchestration, and explicit profile configuration. No additional semantic
decision is required for the narrow confirmed SwingPoint extraction defined by
this Story.

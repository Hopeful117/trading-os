# Market Structure Foundation Investigation

**Investigation:** Next Story after Story 0070

**Working concept:** Story 0071 - Market Structure Foundation

**Date:** 2026-10-03

**Mode:** Focused read-only architecture, domain, and algorithm investigation

**Implementation:** None

**Story 0071:** Not created

**Verdict:** `ARCHITECTURAL_DECISION_REQUIRED`

## 1. Executive Summary

Trading OS already contains a deterministic structural model inside the Trend
Context implementation. The repository has `ConfirmedSwing`, strict
radius-based pivot detection, confirmation timestamps, same-type suppression,
`SwingRelation` values (`HH`, `HL`, `LH`, `LL`, and equal relations), protected
levels, structural breaks, transitions, and extensive replay/cutoff tests.

This is the central finding. A new Story 0071 that independently introduces
`SwingPoint`, HH/HL/LH/LL, or BOS/CHOCH would create a second structural
authority and would conflict with Stories 0062-0065 and ADR-048. The next Story
must first decide whether the existing Trend Context structural kernel is:

- the first implementation of the Market Structure foundation to extract and
  reuse;
- intentionally scoped to Trend Context and therefore not a reusable Market
  Structure capability; or
- to be replaced by a new neutral structural capability, with an explicit
  migration plan.

The smallest technically appropriate standalone foundation is:

```text
one market + one completed OHLC interval + one cutoff
    -> validated real closed candles
    -> confirmed strict local pivots
    -> immutable confirmed SwingPoints with evidence and provenance
```

The recommended algorithm family is a strict fixed-window pivot/fractal with
right-side confirmation. It is already the repository's accepted structural
primitive and is preferable to introducing ZigZag, ATR thresholds, percentage
reversals, or prominence heuristics into a foundation Story. The recommendation
does not approve the current profile's numeric defaults for a new product
contract; those remain profile/configuration and PAPER-validation hypotheses.

The recommended standalone V1 contains confirmed SwingPoints only. It does not
contain provisional points, aggregate bullish/bearish/ranging state, BOS/CHOCH,
trend lines, strategy triggers, opportunities, or AI conclusions. HH/HL/LH/LL
relations are useful deterministic facts, but they should be a follow-up layer
unless the human decision is to extract the existing Trend Context relation
rules as part of the same migration. They must not be implemented twice.

The investigation therefore cannot safely authorize Story 0071 design yet.
The blocking decision is architectural ownership and migration, not a missing
algorithm comparison.

## 2. Current Repository State

### Repository and history

- Current branch at inspection: `main`, tracking `origin/main`.
- Worktree was clean before this report was created.
- Stories 0062 through 0070 and their reports are present.
- Story 0070 is implemented and committed on the dedicated story branch in
  repository history; this investigation does not modify that implementation.
- No Story 0071 artifact existed before this report.
- No ADR was modified.
- No production code or tests were modified.
- DevLog Story Agent applicability was attempted for project `trading-os`, but
  returned `400 VALIDATION_FAILED` with no usable context. No DevLog identifier
  was invented and no conclusion below depends on DevLog.

### Authoritative artifacts inspected

- `AGENTS.md`.
- Stories 0062, 0063, 0064, 0065, 0066, 0067, 0068, 0069, and 0070.
- Story 0067 validation, closure, and runtime reconciliation evidence.
- `ADR-014` decision pipeline.
- `ADR-020` Market Intelligence architecture.
- `ADR-025` Observation model.
- `ADR-033` Active and Passive Market Intelligence orchestration.
- `ADR-034` Strategy, StrategyMatch, and Trading Opportunity boundaries.
- `ADR-048` Intelligence Evidence and Authority.
- Market Facts and Market Eligibility investigation reports.
- The accepted deterministic Trend Context domain design.

### Important repository contradiction

The working concept says Market Structure is the next missing foundation, while
the current repository already calculates structure as part of Trend Context.
The repository is authoritative for current physical state. Historical design
artifacts explain that the current structure rules were intentionally accepted
as Trend Context V1 rules, not as a separate public Market Structure capability.
This is a material architectural contradiction that must be resolved explicitly.

## 3. Existing OHLC / Market Facts Capabilities

### Market Data ownership

`market-data` owns `OhlcEvent`, interval semantics, provider identity, OHLCV,
closure, occurred/fetched timestamps, `synthetic`, and `sourceId`. Provider
mapping remains in the Kraken adapter. `OhlcHistoryNormalizer` sorts,
deduplicates, rejects conflicting duplicates, and can fill missing intervals
with explicitly synthetic candles.

The normalized candle is therefore not equivalent to provider-observed evidence.
That distinction is already explicit and must remain visible to structure
analysis.

### Market Facts

Story 0069 and the current implementation provide bounded Market Activity and
`MarketDataReadiness` facts. Readiness distinguishes at least:

- `AVAILABLE`;
- `INSUFFICIENT_DATA`;
- `STALE`;
- `UNAVAILABLE`;
- `UNSUPPORTED`.

The facts response is compact and does not contain the complete OHLC sequence.
The current `MarketFactsRequest` uses the service `Clock` to establish its
boundary. It is suitable for readiness and activity gating, but it is not by
itself a sufficient historical structure input because a replay needs the raw
or normalized candle evidence at an explicit cutoff.

### Consequence for Market Structure

Market Structure should consume a typed provider-neutral OHLC evidence contract,
not provider DTOs and not only `MarketFactsResponse`. It may consume readiness
as an input gate or provenance section, but it must receive the candle sequence
and explicit cutoff required to calculate and replay structure.

The structure algorithm must not independently infer data completeness from a
synthetic-filled list. It should either consume the existing Trend Context input
contract or a future equivalent that preserves raw/synthetic status, closure,
gaps, source references, and timestamps.

## 4. Current Trend Context Relationship

### Existing domain calculation

`TrendContextEngine` currently performs all of the following in one pure engine:

- strict fixed-radius swing-high and swing-low candidates;
- right-side confirmation;
- cutoff filtering;
- gap-window exclusion;
- same-type minimum-separation suppression;
- HH/LH and HL/LL/equal relations;
- directional structure;
- protected levels;
- confirmed and unconfirmed structural breaks;
- failed-break reclaim and transition state;
- pullback, EMA, ATR, extension, invalidation, and MTF alignment.

`ConfirmedSwing` already preserves role, type, index, pivot time, price,
confirmation time, pivot/confirmation source IDs, suppression state/reason, and
evidence. `TrendContextEvidenceReference` carries rule/profile versions,
cutoff, source IDs, and evidence boundaries.

### Existing input semantics

`TrendContextAssessmentInput` preserves market/provider/symbol identity,
assessment time, cutoff, profile, rule version, role series, validation
findings, and a deterministic fingerprint. `TrendContextRoleSeries` creates a
calculation-ready view containing only closed, non-synthetic candles at or
before cutoff.

### Existing temporal semantics

The current engine defines the pivot timestamp as the candidate candle close
time and confirmation timestamp as the close time of the last right-window
candle. It rejects candidates whose confirmation is after the cutoff. This is
the correct temporal shape for a non-repainting confirmed pivot.

### Existing architectural issue

The structural rules are currently private implementation details of
`TrendContextEngine` and `ConfirmedSwing` is in the `trendcontext` package. A
new independent Market Structure capability would duplicate them. A safe
Story 0071 must either:

1. extract/rehome the neutral confirmed-pivot kernel and let Trend Context
   consume it;
2. formally declare that structure is intentionally Trend Context-internal;
3. replace the current kernel through an approved migration.

Option 1 is the most reusable design, but it is a responsibility and package
boundary decision, not a mechanical refactor that should be hidden in Story
0071.

## 5. Architectural Ownership

The current ADRs support this ownership model:

| Concern | Owner | Rationale |
|---|---|---|
| Provider OHLC and normalized evidence | Market Data | ADR-014, Story 0062, Story 0069 |
| Readiness, gaps, synthetic/source metadata | Market Data | Source/freshness authority |
| Deterministic structural interpretation | Market Intelligence | ADR-020, ADR-048 |
| Structure capability orchestration and reuse | Market Intelligence | Existing capability/artifact architecture |
| Trend Context composition and attention semantics | Market Intelligence | Stories 0063-0065 |
| Strategy setup conditions | StrategyEvaluation | ADR-034 |
| Account financial authorization | Risk Domain / Trading Core | ADR-028 and ADR-048 |
| Interpretation and explanation | AI, later | ADR-003 and ADR-048; no deterministic authority |

Market Structure must not be placed in Market Data merely because the source is
OHLC. Market Data owns the source fact, not the structural interpretation.
Market Intelligence must not allow structure to create a StrategyMatch,
TradingOpportunity, TradePlan, Risk result, or execution command.

### Boundary decision

The preferred long-term classification is **a separate deterministic analytical
capability and lower-level reusable structural primitive consumed by Trend
Context**. It is not a strategy and not an aggregate trend-state authority.

However, that preferred classification conflicts with the current package and
capability ownership. Human approval is required before Story 0071 can safely
design the extraction/migration.

## 6. Temporal Honesty Model

### Definitions

`pivotTimestamp` is the timestamp of the candidate candle whose high or low is
the structural extremum. It answers: "Which market candle is represented by
this point?"

`confirmationTimestamp` is the timestamp at which the algorithm had enough
eligible evidence to confirm the candidate. For a symmetric radius `N`, it is
the close time of the final right-side candle in the `[i-N, i+N]` window. It
answers: "When could this point first be known under this algorithm?"

They may and normally will differ. A confirmed SwingPoint must never be treated
as known before `confirmationTimestamp`.

### Cutoff invariant

For `cutoff = T`, a point is confirmed only if:

```text
confirmationTimestamp <= T
```

No candle with an evidence timestamp after `T` may affect the result. The
candidate's pivot may be earlier than `T`; this does not make it knowable at the
pivot time.

### Live analysis

Live analysis exposes only points whose confirmation evidence has closed and is
available. The newest candidate may exist internally as an unconfirmed
calculation opportunity, but V1 should not publish it as a structural fact.

### Historical replay and backtesting

Replay at `T1` must omit a point whose right-side confirmation closes after
`T1`. Replay at `T2`, after that evidence closes, may include the same point.
The historical result at `T1` is not rewritten; the `T2` result is a new
assessment/version.

### Rendering

Chart rendering may draw a confirmed point at `pivotTimestamp`, but must show
its confirmation state/time and must not imply that it was available at the
pivot candle. A visual line connecting future-confirmed points to past candles
without that distinction would be a lookahead UX defect.

## 7. Swing Detection Algorithm Comparison

| Family | Determinism | Temporal honesty | Noise/volatility behavior | Live/replay | Parameters | Assessment |
|---|---|---|---|---|---|---|
| Fixed-window fractal | Strong | Strong if right window is closed | Sensitive to radius and timeframe | Simple incremental confirmation; exact replay | Low | Best first primitive |
| Prominence extrema | Strong if prominence is formalized | Strong only after future prominence is known | Better noise filtering, but threshold semantics are harder | More state and delayed confirmation | Medium/high | Later significance layer |
| ATR-adjusted swing | Strong with fixed ATR policy | Strong if ATR window is cutoff-safe | Adapts to volatility regime | Requires ATR warmup and versioned source | Medium/high | Useful later, not neutral V1 |
| Percentage/ZigZag | Strong when threshold and tie policy are fixed | High risk of moving/repainting last leg | Volatility independent but regime-sensitive | Historical output attractive; live endpoint provisional | Low/medium | Not appropriate as confirmed-only V1 |
| Hybrid | Potentially strong | Depends on all subrules | Can be robust but opaque | Highest replay/test burden | High | Premature for foundation |
| Simple adjacent local extrema | Strong | Strong, but weak significance | Very noisy | Easy | Very low | Too weak for reusable structure |

### Evaluation details

#### Fixed-window fractal

This is the only family already specified and tested in the repository. A
candidate requires strict comparison against a complete left and right window.
It has transparent confirmation latency of `N` bars and supports exact source
evidence. Its primary weakness is that `N` is a scale choice, not a universal
market truth.

#### Prominence

Prominence can reject a small local high that is geometrically valid but not
material relative to surrounding valleys. The term is not self-defining: it
needs a reference baseline, measurement direction, minimum distance, and
confirmation rule. Adding it to the base fact would mix geometry with
significance and increase configuration/provenance burden.

#### ATR/volatility-adjusted detection

ATR-relative reversal or prominence can adapt across regimes, but ATR itself
requires a warmup policy, real-candle policy, interval-specific parameters, and
cutoff-safe calculation. It would make a structural point depend on another
analytical model. That is suitable for a later significance assessment or
strategy-specific filter, not the neutral base fact.

#### ZigZag

ZigZag is useful as a visual compression of price movement, but the latest leg
and often the latest pivot can move when a stronger reversal occurs. A chart can
look historically clean while the live algorithm has not yet confirmed the
same point. V1 should not publish that mutable endpoint as an immutable
SwingPoint. A future provisional/repainting projection would need a distinct
status and correction semantics.

#### Hybrid

A hybrid can combine strict pivots, minimum reversal, ATR, and separation, but
it makes it difficult to identify whether a change came from geometry,
volatility, or significance. It is not the smallest deterministic foundation.

## 8. Fixed-Window Pivot Analysis

The current repository's accepted conceptual rule is:

```text
high[i] > high[j] for all j in [i-N, i-1] and [i+1, i+N]
low[i]  < low[j]  for all j in [i-N, i-1] and [i+1, i+N]
```

The rule is strict and symmetric. The candidate is calculated only over a full
window of eligible real closed candles. A candle may qualify as both a high and
low if both strict predicates hold. That is unusual but deterministic and
should not be removed without an explicit domain decision.

### Required edge semantics

- Symmetric and asymmetric windows are both technically possible; the current
  accepted design is symmetric. Asymmetric windows should not be added without
  a use case because they change confirmation semantics.
- Strict `>` and `<` are required by current design. `>=` and `<=` would select
  plateau edges arbitrarily and create tie-order dependence.
- Equal neighboring highs prevent both from being strict swing highs. Equal
  lows behave symmetrically.
- Same-type candidates closer than configured minimum separation are resolved
  deterministically by extreme price, then earlier pivot on equality.
- Cross-type candidates are not suppressed. One candle may be both types.
- The first and last incomplete windows produce no confirmed pivot.
- A candidate whose right-side window is incomplete is not confirmed.
- An open/current candle cannot create confirmation.
- Synthetic candles cannot participate in a pivot window.
- A gap intersecting the required evidence window invalidates that candidate.
- Duplicate identical source evidence may be deduplicated upstream; conflicting
  duplicates must make the input invalid or unusable, not be order-selected.
- Provider-observed and normalized/synthetic candles must remain distinguishable.

### What remains deliberately undecided

- Numeric radius `N` for a standalone structure policy.
- Numeric minimum separation.
- Whether same-type suppression belongs in the raw pivot primitive or in a
  retained-points policy. The current Trend Context design includes it in the
  retained sequence.
- Whether a both-high-and-low candle is allowed in every future consumer.
- Whether equal extrema should later support explicit equal-area facts.

The existing `CONSERVATIVE_SWING_V1` profile uses radius 2 and separation 2.
Those values are current Trend Context/PAPER hypotheses, not evidence of a
universal Market Structure default.

## 9. Prominence / ATR Analysis

The cleanest decomposition is:

```text
raw confirmed geometric pivot
    -> optional deterministic significance assessment
    -> structural relations/events
```

The raw pivot should answer only whether the candle is a confirmed local
extremum under an explicit geometric policy. It should not claim that the move
is important, tradeable, or a trend.

Minimum reversal distance, ATR-relative movement, price prominence, and minimum
separation are not interchangeable:

- minimum separation limits event density in time;
- minimum reversal distance limits price movement;
- ATR-relative movement normalizes movement by recent volatility;
- prominence compares an extremum with surrounding structure.

The current Trend Context engine uses minimum separation but not prominence or
ATR to create the pivot itself. That is a defensible boundary. V1 should avoid
making the base SwingPoint "smart" and should defer significance until a
consumer demonstrates the need.

## 10. ZigZag Analysis

ZigZag-style output must distinguish at least:

- provisional current leg;
- confirmed historical pivot;
- a later replacement or extension of the provisional leg.

Without those states, historical chart output is misleading for live analysis
and backtesting. The last pivot may repaint because a future reversal changes
which extreme best represents the leg. A percentage threshold also has no
provider-neutral meaning until precision, price basis, and threshold ownership
are defined.

Recommendation: do not use ZigZag as the immutable V1 SwingPoint source. If a
future chart-only projection is desired, it should be a separate explicitly
provisional analytical output and must not feed StrategyEvaluation as confirmed
evidence.

## 11. Confirmed vs Provisional Points

### Confirmed-only V1

Advantages:

- smallest truthful model;
- no correction events or mutable historical facts;
- straightforward live/replay equivalence;
- safe StrategyEvaluation input;
- simple persistence and provenance;
- no false certainty from the current open candle.

Cost:

- confirmation latency;
- no immediate visual marker for a developing extremum.

### Confirmed plus provisional

Advantages:

- richer chart UX;
- earlier indication of a possible turn;
- potential use for non-authoritative monitoring.

Costs:

- explicit status/correction/replacement model;
- event ordering and persistence complexity;
- different live and historical representations;
- high risk that a consumer accidentally treats provisional as confirmed;
- additional strategy and replay test burden.

Recommendation: V1 contains only confirmed points. Do not model provisional
points until a concrete chart or monitoring use case is approved and the
consumer contract guarantees it cannot enter StrategyEvaluation as confirmed
evidence.

## 12. SwingPoint Domain Model

The minimum neutral model should preserve:

```text
SwingPoint
    marketId
    interval
    type: HIGH | LOW
    price
    pivotTimestamp
    confirmationTimestamp
    pivotSourceId
    confirmationSourceId
    evidenceReference
    ruleVersion
    policy/profile version
```

The following are not required in the minimum standalone fact unless an
approved contract needs them:

- strategy role such as BIAS or SETUP;
- protected-level meaning;
- trend direction;
- strength score;
- prominence score;
- ATR multiplier;
- provider payload;
- mutable provisional status;
- calculated wall-clock time as a determinant.

`calculatedAt` may be operational metadata, but it must not change the
calculated result and should not be confused with `confirmationTimestamp`.
`sourceCandleReference` should be a stable source ID or evidence reference,
not an array of copied provider payloads.

If the model is extracted from Trend Context, existing role and evidence fields
may be retained at the adapter boundary while the neutral core removes role-
specific meaning.

## 13. HH / HL / LH / LL Analysis

Relations are deterministic when the comparable swing sequence is explicit:

```text
latest high > previous high -> HH
latest high < previous high -> LH
latest high = previous high -> EQ_HIGH

latest low > previous low -> HL
latest low < previous low -> LL
latest low = previous low -> EQ_LOW
```

The current Trend Context implementation compares the latest two retained
same-type swings. It does not compare arbitrary cross-type or non-adjacent
points. That is a strong rule because it is easy to explain and replay.

For a new standalone foundation, HH/HL/LH/LL should be deferred to a separate
structural-relations layer. The first useful reusable fact is a confirmed
SwingPoint, not a directional interpretation. This avoids duplicating Trend
Context's existing relation semantics while the ownership decision is pending.

If the human chooses extraction of the existing Trend Context kernel, the
relations may move with it, but they must retain the exact current comparison,
equality, cutoff, and evidence rules. They must not be reimplemented under a
second enum or service.

## 14. Equality Semantics

Exact `BigDecimal.compareTo` equality is deterministic and is the current
repository rule. It produces `EQ_HIGH` or `EQ_LOW` in the existing Trend
Context layer and does not count as directional structure.

An arbitrary percentage or pip tolerance is not justified by current repository
evidence. A tolerance would need:

- price precision/tick semantics;
- provider/instrument ownership;
- interval and asset-class behavior;
- rounding policy;
- configuration identity/version;
- evidence of why the tolerance is meaningful.

Recommendation for a standalone SwingPoint foundation: do not classify equality
as a separate pivot point; retain strict geometry. If relations are later
implemented, represent equal relations explicitly as `EQ_HIGH` and `EQ_LOW`,
with exact decimal comparison in the initial contract. Tolerance remains a
human algorithmic decision, not an inferred default.

## 15. Structure State

Do not derive `BULLISH_STRUCTURE`, `BEARISH_STRUCTURE`, or `RANGING` in the
SwingPoint foundation. These are aggregate interpretations and overlap with
Trend Context's `direction`, `regime`, and attention semantics.

The current Trend Context deliberately uses `NEUTRAL` / `NON_DIRECTIONAL` and
does not claim a rich categorical range when its strict relations do not agree.
That conservative behavior should be preserved. A future structure state
would require a distinct semantic contract and must not silently become a
duplicate of Trend Context.

## 16. Market Structure vs Trend Context

### Current Trend Context

Trend Context computes structural swings and relations, then adds protected
levels, breaks, transitions, pullbacks, EMA/ATR supporting evidence, MTF
alignment, and attention outcomes. It is already a higher-level composition,
not only a pivot detector.

### Preferred future relationship

```text
Market Data OHLC evidence
        |
        v
Market Structure capability
  confirmed SwingPoints
  later structural relations/events
        |
        v
Trend Context capability
  direction/regime/phase/alignment/attention
        |
        v
Observation -> StrategyEvaluation
```

This separation gives structure independent reuse for charting, later trend
lines, other deterministic analyses, and AI context while keeping Trend Context
as the owner of contextual interpretation.

### Immediate integration recommendation

Do not rewrite Trend Context as part of a foundation Story unless the Story is
explicitly approved as an extraction/migration. A narrow first implementation
could preserve current Trend Context behavior and expose the neutral structural
artifact for reuse. That requires a decision about package/API ownership and
regression guarantees.

## 17. Multi-Timeframe Boundary

Structure should be calculated per:

```text
marketId + interval + cutoff + algorithm/policy version
```

One structure analysis must not silently mix M15, H1, and H4. The current
Trend Context profile has BIAS, SETUP, and optional TRIGGER role series and
combines their assessments in a higher layer. That is a suitable composition
boundary, but the lower-level structure result should remain interval-explicit.

MTF composition belongs in Trend Context or a later explicit MTF analysis, not
inside the basic structure engine. A structure result for H1 must be reusable
without inventing a relationship to H4.

## 18. Cutoff Semantics

The input contract must include an explicit `cutoff`. The result must be a
function of:

```text
market + interval + candles <= cutoff + policy/version
```

The current Trend Context contributor establishes a boundary from its injected
`Clock` and passes it into the input mapper. The pure engine separately filters
close times against cutoff and requires pivot confirmation at or before it.
This is suitable for live execution but a future replay API must pass a fixed
cutoff rather than call a wall-clock-based acquisition path.

The current Market Facts service's internal `Clock` boundary cannot substitute
for a historical structure cutoff. A future adapter must request or reconstruct
evidence for the explicit cutoff and preserve the requested boundary in
provenance.

## 19. Open Candle Policy

The current and recommended policy is:

- open candles are excluded from calculation-ready structure evidence;
- an open candle cannot be a pivot;
- an open candle cannot confirm a pivot;
- an open candle cannot establish HH/HL/LH/LL;
- an open candle cannot confirm a future structural event;
- a later closed version is a new eligible evidence state.

This prevents a moving current candle from creating an immutable historical
fact. It also matches Stories 0062/0063 and the current `TrendContextRoleSeries`
filter.

## 20. Synthetic / Missing Evidence

### Synthetic candles

Synthetic candles should remain visible as provenance/gap evidence but must not
participate in confirmed structural calculation. A synthetic candle that would
otherwise be a pivot invalidates the candidate window rather than producing a
degraded confident pivot.

This reuses the current Trend Context policy and Story 0069's raw-versus-
normalized distinction. It is safer than silently allowing flat continuity
candles to create artificial extrema.

### Gaps and conflicting evidence

A gap intersecting the required pivot window means that the candidate is not
evaluable. Conflicting duplicates must not be resolved by list ordering.
Incomplete evidence should produce a typed unavailable/insufficient result or
an explicit exclusion finding, not a neutral price or guessed point.

### Status reuse

Market Data statuses (`AVAILABLE`, `INSUFFICIENT_DATA`, `STALE`, `UNAVAILABLE`,
`UNSUPPORTED`) should describe source/readiness. Market Structure may have a
small result status such as `AVAILABLE`, `INSUFFICIENT_DATA`, `STALE`,
`UNAVAILABLE`, or `INVALID`, but it must preserve the underlying Market Data
status and not create a competing universal status taxonomy without need.

## 21. Input Contract

Preferred conceptual input:

```text
MarketStructureRequest
    marketId
    interval
    cutoff
    candle evidence
    readiness/provenance
    policy identity/version
    algorithm/rule version
```

The algorithm should consume the existing typed Trend Context candle semantics
or an extracted neutral equivalent. It should not consume:

- Kraken DTOs;
- raw provider symbols as algorithm semantics;
- mutable in-memory hidden state;
- AI output;
- a bare list that has lost synthetic/open/source metadata;
- only a compact Market Facts response with no candles.

The adapter owns acquisition and provider mapping. The pure structure engine
owns validation of the supplied analytical input and calculation.

## 22. Output Contract

Smallest useful output:

```text
MarketStructureAnalysis
    marketId
    interval
    cutoff
    status
    confirmedSwingPoints[]
    source/readiness provenance
    policy/algorithm version
    input fingerprint
    findings/exclusions
    assessment fingerprint
```

It should not include direction, regime, attention, strategy criteria, or
financial conclusions. Later structural relations can be an explicit output
extension or separate capability result, but should not be conflated with the
raw confirmed point list.

## 23. Persistence / Observation Integration

### Persistence recommendation

Do not create a standalone Market Structure projection for V1. Calculate it on
demand or as a capability artifact inside an existing `AnalysisExecution`.
Persist it only when it is needed by an existing immutable Observation or scan
provenance boundary. This follows Story 0069/0070's avoidance of new standalone
read models and ADR-048's append-oriented evidence model.

A later passive scanner may justify compact durable structural snapshots, but
that is a separate scale and lifecycle decision.

### Observation recommendation

The structure capability should not directly create an Observation. ADR-025
assigns Observation creation to the Observation Builder, after capability
results are consolidated with evidence. A future Trend Context observation may
consume the structure artifact. A standalone structure Observation is only
justified if a concrete consumer needs a durable, user-visible, reusable
structural statement and its type/lineage is approved.

## 24. Active Scanner Integration

The expected placement is:

```text
explicit Active Scan scope
    -> Market Eligibility / readiness
    -> AnalysisExecution per eligible market
    -> requested deterministic capabilities
    -> Market Structure artifact
    -> Trend Context and/or other consumers
    -> Observation Builder
    -> StrategyEvaluation
```

Structure should be requested by capability planning when a selected analysis
needs it, rather than always calculated for every eligible market. Within one
`AnalysisExecution`, the artifact should be reused by Trend Context and any
other declared consumers. It must not create a second scan, bypass scope
resolution, or change the effective eligible universe.

The current Active Scan registers one `AnalysisExecution` per eligible market.
That is the correct orchestration boundary. Structure belongs inside the child
analysis, not in `ActiveScanScopeResolutionService` or the eligibility policy.

## 25. Strategy Boundary

The exact boundary is:

```text
confirmed swing high at price P
    = structural evidence

latest high relation is HH
    = structural relation evidence, if that layer exists

buy after a higher low
    = StrategyDefinition criterion

breakout entry after a structural high breaks
    = StrategyDefinition criterion

high-probability setup
    = Strategy/AI interpretation
```

Structure must not know account state, position sizing, stop/target policy,
broker capabilities, or Risk. A structure result must never directly become a
StrategyMatch or opportunity. ADR-034 and ADR-048 require the explicit
StrategyEvaluation boundary.

## 26. BOS / CHOCH Boundary

`BOS` and `CHOCH` are not sufficiently stable domain terms by themselves.
Different methodologies disagree on:

- wick versus close;
- which protected point is relevant;
- whether a break must be retested;
- whether an opposite swing must be confirmed;
- how equal highs/lows behave;
- whether a break changes trend state immediately.

The current Trend Context design has explicit versioned break and transition
rules, including close confirmation and protected levels. Those rules are
valid within that profile, but the labels BOS/CHOCH should not be promoted as
unqualified platform semantics.

Recommendation: defer BOS/CHOCH in Story 0071. A later structural-event Story
may define neutral names such as `STRUCTURAL_HIGH_BROKEN` and
`STRUCTURAL_LOW_BROKEN`, with explicit policy/version and evidence. Strategy
definitions may then consume those events. The current Trend Context break
rules must be reused or explicitly migrated, not silently duplicated.

## 27. Trend-Line Future Compatibility

Trend lines are out of scope. Future construction only requires that points
retain:

- interval;
- type;
- price;
- pivot timestamp;
- confirmation timestamp;
- stable source references;
- policy and algorithm versions;
- evidence window/fingerprint.

A future trend-line algorithm can then select confirmed points without needing
to reconstruct provider payloads or infer whether a point was knowable at the
time. No line-fitting or support/resistance clustering belongs in the
foundation.

## 28. AI Boundary

AI is not required for any deterministic structure fact. It may later:

- explain a confirmed point sequence;
- summarize multi-timeframe evidence;
- surface contradictions;
- compare deterministic evidence with other context;
- propose a question or interpretation for human review.

AI must not create, correct, relabel, or rewrite confirmed structural facts. It
must not turn provisional or stale evidence into confirmed evidence and must not
override StrategyEvaluation or Risk.

## 29. Algorithm Versioning

Every result must preserve:

- algorithm identity;
- algorithm/rule version;
- policy/profile version;
- parameter set or canonical parameter fingerprint;
- market and interval;
- cutoff;
- input evidence fingerprint/source digest;
- findings and exclusions;
- producer/capability version.

The current Trend Context evidence already preserves rule/profile versions and
input fingerprints. A neutral extracted structure result should make those
fields first-class or preserve an equivalent evidence reference.

If the radius, equality rule, suppression rule, or synthetic policy changes,
the new calculation is a new algorithm/policy version. It must not overwrite a
historical result and claim semantic identity with the old version.

## 30. Parameter Ownership

### Domain semantics

- What a pivot means.
- What confirmation means.
- Which evidence is eligible.
- Strict comparison and equality semantics.
- How cutoff and source timestamps constrain the result.

### Algorithm/policy configuration

- left/right window or radius;
- minimum same-type separation;
- whether cross-type coexistence is permitted;
- maximum lookback and warmup requirements;
- evidence tolerance policy, if ever approved.

### Consumer configuration

Trend Context profile, scanner capability selection, chart display, or strategy
may select a policy/version, but they must not alter domain semantics silently.

### Deliberately undecided values

- universal pivot radius;
- universal minimum separation;
- ATR/prominence threshold;
- percentage reversal threshold;
- equality tolerance;
- lookback size beyond provider/request bounds;
- passive refresh/freshness windows;
- any profitability-oriented parameter.

The current profile values are not automatically promoted to universal defaults.

## 31. Determinism / Replay

Required invariant:

```text
same candle evidence
+ same cutoff
+ same algorithm version
+ same policy parameters
    -> byte-equivalent material structure result/fingerprint
```

The result must not depend on:

- current wall-clock time, except as explicit metadata;
- provider call timing;
- unordered map/set iteration;
- current cache state;
- AI output;
- mutable hidden state.

For a cutoff replay:

- a point confirmed at `T2` but not at `T1` is absent at `T1`;
- the same point may appear at `T2` with the same pivot timestamp and a later
  confirmation timestamp;
- a later result supersedes current consumption but does not rewrite the T1
  historical evidence.

## 32. Test Strategy

The future implementation must use deterministic candle fixtures and fixed
clocks. Minimum cases:

- obvious strict high;
- obvious strict low;
- monotonic up and down sequences;
- range/no pivot;
- equal neighboring highs/lows;
- noisy sequence;
- large spike and crash candle;
- insufficient left and right windows;
- final candidate not yet confirmed;
- same candidate after confirmation;
- open candle;
- synthetic candle in a required window;
- gap through a required window;
- duplicate identical evidence;
- conflicting duplicate evidence;
- duplicate timestamps with deterministic rejection;
- exact cutoff boundary;
- candle immediately after cutoff;
- different intervals with no implicit mixing;
- same evidence repeated in different input order;
- algorithm version change;
- same candle as both high and low if that rule is retained.

### Required lookahead regression

```text
cutoff T1:
    pivot candle exists
    right-side confirmation candle does not exist
    -> no confirmed SwingPoint

cutoff T2:
    right-side confirmation evidence is closed and available
    -> confirmed SwingPoint exists
    -> pivotTimestamp != confirmationTimestamp
```

### Invariants

- `confirmationTimestamp >= pivotTimestamp`;
- no source candle after confirmation contributes to that point;
- no source candle after cutoff contributes to the result;
- retained high price equals the referenced candle high;
- retained low price equals the referenced candle low;
- synthetic/open evidence never creates a confirmed point;
- identical inputs produce identical outputs;
- results do not create StrategyMatch, Opportunity, TradePlan, Risk, or
  execution side effects.

## 33. Performance

A fixed-window scan over a bounded history is `O(n * N)` in the direct form,
which is effectively `O(n)` for a configured bounded radius. Deques or rolling
max/min structures could optimize larger windows, but are not needed for V1.

Candidate suppression is bounded by the candidate sequence. The input should
remain bounded by existing OHLC request limits and role lookback policies. An
incremental live implementation can emit a point when the right-side candle
closes, but streaming infrastructure is not required for Story 0071.

ZigZag and hybrid prominence models would add state and correction complexity
without improving the minimum foundation's semantic truth.

## 34. V1 Options

### Option A - Confirmed SwingPoints only

**Usefulness:** Supports future relations, chart references, trend lines, and
deterministic evidence while keeping meaning neutral.

**Complexity:** Lowest.

**Coupling:** Low if extracted; high only during migration from current Trend
Context internals.

**Risk:** Confirmation latency and limited immediate chart feedback.

**Assessment:** Smallest coherent standalone foundation.

### Option B - SwingPoints plus HH/HL/LH/LL

**Usefulness:** Immediately supports directional structural evidence.

**Complexity:** Moderate, especially equality and sequence semantics.

**Coupling:** Directly overlaps the existing Trend Context engine.

**Risk:** Creates a second relation authority unless implemented as an explicit
extraction of existing rules.

**Assessment:** Appropriate only if extraction/migration is the approved scope.

### Option C - SwingPoints, relations, and aggregate state

**Usefulness:** Higher-level consumer convenience.

**Complexity:** High.

**Coupling:** Duplicates Trend Context direction/regime semantics.

**Risk:** Premature bullish/bearish/ranging authority and ambiguous boundaries.

**Assessment:** Reject for foundation.

### Option D - Provisional/ZigZag or significance-rich foundation

**Usefulness:** Strong visual appeal and possible earlier signals.

**Complexity/risk:** Highest replay, correction, parameter, and false-certainty
burden.

**Assessment:** Reject for V1.

## 35. Recommended V1

The recommended conceptual scope is Option A:

```text
Story 0071 = immutable confirmed SwingPoint evidence for one market/interval/cutoff
```

Included:

- typed input with explicit cutoff;
- completed real closed candles only;
- strict fixed-window local high/low candidates;
- right-side confirmation timestamp;
- exact source/evidence references;
- deterministic invalid/insufficient/stale/unavailable semantics;
- algorithm/policy/version/fingerprint provenance;
- bounded on-demand or AnalysisExecution artifact output;
- no standalone persistence projection;
- no direct Observation creation.

Excluded:

- provisional points;
- HH/HL/LH/LL in a new parallel contract;
- aggregate structure state;
- BOS/CHOCH;
- trend lines;
- prominence or ATR-adjusted pivot qualification;
- strategy triggers;
- StrategyEvaluation changes;
- opportunities, TradePlans, Risk, execution;
- AI calculation or authority;
- passive scanner infrastructure;
- universal numeric defaults.

This scope is designable only after the ownership/migration decision below.

## 36. Future Story Decomposition

The likely sequence, subject to the ownership decision, is:

1. **0071 Market Structure Foundation:** extract or formalize confirmed
   SwingPoint evidence without adding a second authority.
2. **Structural Relations:** HH/HL/LH/LL and equality semantics, either as a
   reusable capability or as an explicitly migrated part of the structure
   kernel.
3. **Structural Events:** versioned neutral break/protected-level events; defer
   methodology-specific BOS/CHOCH names.
4. **Trend Context integration/migration:** make Trend Context consume the
   reusable structural artifacts without behavior drift.
5. **Trend Line Analysis:** construct lines from confirmed points only.
6. **Strategy integration:** declare strategy-specific use of structure through
   StrategyEvaluation.

If the human decides that structure remains Trend Context-internal, Story 0071
should not be created as a duplicate capability. The correct follow-up would
instead be a Trend Context extraction or documentation story with a different
scope.

## 37. ADR Assessment

**ADR_REQUIRED: YES, conditionally.**

No new ADR is required for a small isolated deterministic capability that stays
inside the existing Market Intelligence boundary and reuses ADR-048 evidence
semantics.

An ADR or explicit amendment is required before implementation if Story 0071
does any of the following:

- changes ownership of structural facts from Trend Context to a reusable
  capability;
- introduces a new shared structural abstraction used by multiple capabilities;
- changes Observation authority or persistence semantics;
- introduces provisional/revisable structural facts;
- defines a durable structural projection or event-sourcing model;
- changes the meaning of existing Trend Context swings, relations, or breaks.

Because the current repository already has structural authority inside Trend
Context, the ownership/extraction decision is material. The safest current
classification is that an ADR decision is needed unless the human explicitly
accepts an implementation confined to existing Trend Context ownership.

## 38. Human Decisions

### Architectural decisions

- Is Market Structure a reusable lower-level capability, or deliberately an
  internal part of Trend Context?
- If reusable, should the existing Trend Context swing kernel be extracted and
  rehomed rather than duplicated?
- Must current Trend Context outputs remain byte/semantic compatible during
  extraction?
- Should structure be an artifact only, or may it become an Observation later?
- Is a new ADR required for the ownership/migration decision?

### Algorithmic decisions

- Confirm strict fixed-window pivots as the first family, or choose another
  family explicitly.
- Confirm confirmed-only V1 versus provisional output.
- Confirm whether same-type suppression is part of the base point policy.
- Confirm exact equality semantics and whether tolerance is forbidden in V1.
- Confirm whether HH/HL/LH/LL is deferred or migrated with the existing kernel.
- Confirm synthetic/gap handling as fail-closed for affected windows.

### Runtime configuration

- Pivot radius.
- Minimum separation.
- Lookback depth and bounded request size.
- Freshness/readiness requirement.
- Policy/profile and rule versions.
- Any future tolerance or significance parameters.

No numerical value is justified as a universal default by this investigation.

### Empirical/trading hypotheses

These are not architectural decisions and must not be smuggled into the
foundation:

- whether a radius or separation improves trading usefulness;
- whether ATR/prominence reduces noise;
- whether HH/HL sequences predict profitable continuation;
- whether a structural break is a useful setup trigger;
- whether a trend-line or ZigZag representation improves outcomes.

These require PAPER/backtest validation and do not authorize strategy or Risk
changes.

## 39. Risks / Open Questions

- The existing `TrendContextEngine` already mixes structural and contextual
  calculations; extraction could cause behavior drift.
- The current `ConfirmedSwing` model has useful provenance but also carries
  Trend Context role semantics that a neutral model should reconsider.
- The current Market Facts API does not expose a full explicit-cutoff candle
  snapshot, so historical structure replay needs a dedicated input path or
  reuse of the existing Trend Context input contract.
- A chart consumer may request provisional points later, but mixing them into
  immutable evidence would be unsafe.
- Equality tolerance may be attractive for noisy prices but lacks repository-
  defined tick/precision semantics.
- Existing Trend Context profile values are paper hypotheses, not profitability
  evidence or universal structure defaults.
- Current runtime validation proves deterministic fixtures and parts of the
  PAPER flow, but does not prove profitability or justify thresholds.
- A new capability could be technically correct yet still fail to execute in
  the deployed Active Scan path if service acquisition/configuration is not
  validated.

## 40. Explicit Answers

1. **Where should Market Structure live?** In deterministic Market Intelligence,
   preferably as a reusable capability over Market Data evidence. The current
   implementation lives inside Trend Context, so extraction ownership must be
   decided before Story 0071.
2. **Should it be independent from Trend Context?** Preferably yes as a lower-
   level capability, while Trend Context consumes it. It must not be a second
   independently implemented engine.
3. **What is the smallest useful deterministic structural fact?** A confirmed
   high/low point tied to a candle, price, interval, cutoff, confirmation time,
   source evidence, and algorithm/policy version.
4. **Should V1 use confirmed SwingPoints?** Yes, if V1 is approved as the
   standalone foundation.
5. **Should provisional SwingPoints exist in V1?** No.
6. **Which swing family is best suited technically?** Strict fixed-window
   fractal/pivot detection with closed right-side confirmation, because it is
   explicit, already accepted and tested, provider-neutral, replayable, and
   incrementally calculable.
7. **Strongest arguments against it?** It is lagging, radius-sensitive,
   timeframe-sensitive, noise-sensitive, strict equality rejects plateaus, and
   fixed parameters do not adapt automatically to volatility regimes.
8. **How is lookahead prevented?** Require a complete real closed window and
   include a point only when `confirmationTimestamp <= cutoff`; never use future
   candles in a cutoff snapshot.
9. **What is `pivotTimestamp`?** The timestamp of the candidate extremum candle.
10. **What is `confirmationTimestamp`?** The close timestamp of the final
    right-side evidence candle that confirms the pivot.
11. **Can they differ?** Yes, normally.
12. **Can a point be known before confirmation?** No.
13. **Should the OPEN candle participate?** No, not in V1 pivot detection,
    confirmation, relations, or structural events.
14. **How should synthetic candles be handled?** Preserve them as evidence of
    normalization/gaps but exclude them from calculation; an affected pivot
    window is not evaluable.
15. **How should gaps/incomplete evidence be handled?** Return explicit
    insufficient/unavailable/invalid findings and do not manufacture structure.
16. **Should V1 include HH/HL/LH/LL?** No for a new standalone foundation.
17. **If yes, how should equal highs/lows be represented?** In the existing
    relation layer, exact decimal comparison yields `EQ_HIGH`/`EQ_LOW`; no
    tolerance is justified yet.
18. **If no, what Story should add relations?** A future Structural Relations
    Story, or an explicitly approved extraction/migration of the existing Trend
    Context relation rules.
19. **Should V1 derive aggregate structure state?** No.
20. **How does this avoid Trend Context duplication?** Structure emits neutral
    point evidence; Trend Context remains the composition layer for direction,
    regime, phase, alignment, and attention, and consumes the shared kernel.
21. **Is structure per market + interval + cutoff?** Yes, with algorithm/policy
    version also part of identity.
22. **Where should MTF composition happen?** Trend Context or a later explicit
    MTF layer, not the one-interval structure primitive.
23. **What input evidence should it consume?** Provider-neutral typed OHLC
    candles with close/open times, OHLC values, closed/synthetic flags, source
    IDs, gaps/readiness, and an explicit cutoff.
24. **What minimum provenance is required?** Market, interval, cutoff,
    pivot/confirmation source IDs, evidence window, input fingerprint,
    algorithm/rule version, policy parameters/version, and readiness findings.
25. **Should results be persisted?** Not as a standalone projection in V1;
    calculate on demand or store as an existing AnalysisExecution artifact when
    needed for lineage.
26. **Should Market Structure create an Observation?** No. Observation Builder
    remains the creator; Trend Context may later consume the artifact.
27. **Where does it execute in Active Scan?** Inside each eligible market's
    `AnalysisExecution`, after eligibility and before consumers that request it.
28. **How is it versioned?** Algorithm identity/version, policy/profile version,
    canonical parameters, cutoff, input fingerprint, and producer version.
29. **Which parameters are configuration inputs?** Radius, separation, bounded
    lookback, readiness/freshness, and any explicitly approved equality or
    significance policy.
30. **Which values remain undecided?** All universal numeric thresholds,
    tolerance, ATR/prominence, ZigZag reversal, and profitability-oriented
    values.
31. **Does Story 0071 require a new ADR?** Yes conditionally: not for an
    isolated capability under existing ownership, but yes for extraction from
    Trend Context or any new durable/provisional authority.
32. **What is the smallest coherent Story 0071?** Confirmed immutable
    SwingPoint evidence for one market, one interval, and one explicit cutoff,
    with no parallel Trend Context relation engine.
33. **What should not be included?** Provisional points, relations in a new
    authority, aggregate state, BOS/CHOCH, trend lines, strategy triggers,
    opportunities, Risk, execution, AI calculation, and universal thresholds.
34. **What should the likely next Story be?** Structural Relations, after the
    ownership/migration decision, followed by versioned Structural Events and
    Trend Context reuse/integration.
35. **Which decisions are empirical hypotheses?** Parameter usefulness, noise
    reduction, volatility adaptation, structural break value, trend-line value,
    and any profitability claim.

## 41. Final Verdict

**`ARCHITECTURAL_DECISION_REQUIRED`**

Story 0071 design is blocked by one material decision: whether the existing
Trend Context structural kernel becomes a reusable Market Intelligence
capability or remains intentionally internal to Trend Context. A second
independent SwingPoint/HH/HL implementation is not safe.

If the decision is to extract and reuse the current kernel, the narrow Story
scope should be confirmed SwingPoints only, with strict cutoff/confirmation
semantics, existing raw/synthetic/open evidence rules, immutable provenance,
and no new strategy or aggregate-state behavior. If the decision is to keep
structure internal to Trend Context, Story 0071 should not be created under the
working concept; a different Trend Context maintenance/extraction scope is
needed.

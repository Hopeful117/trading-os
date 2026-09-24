# Story 0063 - Repository Analysis

## Scope

Story `0063` implements the pure deterministic Trend Context engine on top of
the immutable input contract delivered by Story `0062`. This analysis is based
on the current repository, the accepted `ADR-048`, the hardened deterministic
Trend Context design, and the actual Story `0062` implementation.

The analysis was completed before implementation. The implementation and
validation status are recorded in `implementation-report.md` and
`engineering-report.md`.

## Sources Reviewed

- `docs/architecture/stories/0063-deterministic-trend-context-engine/story.md`
- `docs/architecture/adr/ADR-048.md`
- `docs/investigations/deterministic-trend-context-domain-design-2026-09-23.md`
- `docs/architecture/stories/0062-trend-context-input-contract/story.md`
- `docs/architecture/stories/0062-trend-context-input-contract/repository-analysis.md`
- `docs/architecture/stories/0062-trend-context-input-contract/implementation-plan.md`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/trendcontext/`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/TrendContextInputMapper.java`
- Story `0062` input, mapper, wire, validation, fingerprint, and provenance tests
- Existing deterministic capabilities, artifact fingerprints, strategy digests,
  and Market Intelligence domain tests

The current `0062` Story artifact still marks human review and the Engineering
Report as pending, although the task context states that human review is now
complete. This is a documentation freshness discrepancy only; the committed
input implementation and automated validation are present and are the physical
repository authority for this analysis. This Story does not modify `0062`
artifacts.

## Current Story 0062 Boundary

### Public input

`TrendContextAssessmentInput.accept(...)` creates an immutable input containing:

- market UUID, provider and symbol;
- `assessmentAt` and `cutOffAt`, with `cutOffAt <= assessmentAt`;
- immutable `TrendContextProfile` and its profile version;
- explicit `ruleVersion`;
- an immutable map of `BIAS`, `SETUP`, and optional `TRIGGER` role series;
- input validation findings;
- a deterministic SHA-256 input fingerprint.

`TrendContextAssessmentInput` rejects blocking validation findings before the
engine receives the value. Non-blocking findings remain visible, including:

- `INSUFFICIENT_HISTORY`;
- `CUTOFF_EXCLUDED`;
- `OPEN_CANDLE_EXCLUDED`;
- `SYNTHETIC_DATA_EXCLUDED`.

### Role series

`TrendContextRoleSeries` contains:

- role and configured interval;
- normalized source candles;
- `calculationReadyCandles` filtered to `closed`, non-synthetic candles whose
  close is at or before the input cut-off;
- exclusion findings and gap findings;
- source reference and freshness metadata;
- total, calculation-ready, and excluded candle counts.

The engine must use `calculationReadyCandles` for all calculations. It may read
the original candles, exclusions, gaps, source reference, and freshness to
explain a result, but it must not promote excluded candles into calculations.

### Candle and provenance facts

`TrendContextCandle` preserves provider, symbol, interval, open/close times,
OHLCV, closure, synthetic status, source ID, source occurrence time, and fetch
time. `TrendContextSourceReference` preserves role-level source identity,
range, timestamps, snapshot reference, and content digest. `TrendContextFreshness`
assessment time, role availability, and eligible-evidence presence.

### Profile facts

`TrendContextProfile.conservativeSwingV1(...)` already freezes the provisional
`CONSERVATIVE_SWING_V1` values:

| Parameter | Value |
| --- | ---: |
| pivot radius | 2 |
| same-type separation | 2 bars |
| minimum confirmed swings | 2 |
| EMA period / warmup / slope lookback | 50 / 10 / 5 |
| EMA slope threshold | `0.10` |
| ATR period / baseline length | 14 / 20 |
| abnormal ATR ratio | `3.0` |
| extension ATR multiple | `3.0` |
| pullback / reclaim window | 2 / 2 bars |
| freshness multiplier | 2 intervals |

The engine must consume these values and not optimize or redefine them.

### Mapper boundary

`TrendContextInputMapper` is a Spring adapter and is outside the pure engine.
It maps role-keyed `OhlcResponse` values into the input, checks cross-role
market/provider/symbol identity, collapses identical duplicates, rejects
conflicting duplicates, preserves source metadata, and records synthetic/open/
cut-off exclusions. Story `0063` must not move those responsibilities into the
engine or change `HistoricalOhlcContext`.

## Existing Domain Conventions

The repository provides useful patterns but no generic rules framework that
fits this domain:

- immutable domain values are represented by records or final classes with
  defensive copies and constructor invariants;
- existing deterministic capabilities return typed capability results but are
  infrastructure-aware Spring components, so they are not suitable as the
  engine boundary;
- `ArtifactFingerprint` and `StrategyEvaluationContext` use canonical ordered
  content and SHA-256 digests;
- existing decimal calculations use `BigDecimal`, explicit division precision,
  and `RoundingMode.HALF_UP` in application capabilities;
- existing deterministic findings use `IntelligenceFinding`, but that type has
  wall-clock `generatedAt`, generic context-section references, and confidence
  semantics, so it must not be reused for the pure assessment result;
- existing tests use JUnit 5, AssertJ, Mockito, ArchUnit, and parameterized
  tests where symmetry or contract matrices benefit from them;
- no property-testing library is established in the repository.

The Trend Context result therefore needs a dedicated immutable domain model in
the existing `domain.trendcontext` package. It must not depend on Spring,
`IntelligenceFinding`, capability execution IDs, persistence entities, or
`Instant.now()`.

## Proposed Engine Boundary

Recommended public API:

```text
com.hope.trading.market_intelligence.domain.trendcontext.TrendContextEngine

TrendContextAssessment assess(TrendContextAssessmentInput input)
```

The engine should be a pure final class with no injected services and no mutable
run state. It may delegate to package-private pure calculators for swings,
structure, indicators, alignment, and outcome derivation. A generic rule engine
or Spring bean is not justified.

The engine receives only an accepted input. It must:

- read role calculation-ready candles;
- replay each role from the bounded chronological series;
- apply the versioned rule catalogue in a fixed order;
- retain typed evidence and findings;
- derive factual state independently from conservative attention;
- return an immutable assessment with deterministic content and lineage.

It must not fetch, persist, orchestrate, authorize, create capabilities,
create observations, evaluate strategies, or produce financial results.

## Proposed Assessment Model

All types below belong in `com.hope.trading.market_intelligence.domain.trendcontext`
unless implementation review identifies a smaller package split. The names are
proposed domain names, not an authorization to add unrelated abstractions.

### Root result

`TrendContextAssessment` should contain:

- market ID, provider, symbol;
- assessment time and cut-off;
- input fingerprint, profile ID/version, and rule version;
- immutable per-role `TrendContextTimeframeAssessment` values;
- explicit MTF alignment;
- factual direction, regime, phase, and attention outcome;
- typed findings, contradictions, exclusions, invalidations, and evidence
  references in deterministic order;
- optional deterministic assessment fingerprint.

It must contain no StrategyMatch, TradingOpportunity, TradePlan, Risk,
ExecutionIntent, broker, account, probability, score, or confidence field.

### Factual enums

- `TrendDirection`: `UP`, `DOWN`, `NEUTRAL`, `UNKNOWN`.
- `TrendRegime`: `TRENDING`, `TRANSITIONING`, `NON_DIRECTIONAL`, `UNKNOWN`.
- `TrendPhase`: `DIRECTIONAL`, `PULLBACK`, `EXTENDED`, `TRANSITION`,
  `UNDETERMINED`.
- `TrendAttention`: `UNKNOWN`, `NO_SETUP`, `WATCH`,
  `CONTEXTUALLY_ATTRACTIVE`, `CONTEXTUALLY_DANGEROUS`.

These are deliberately separate. Direction is not attention, and attractive
attention is not a trade decision.

### Per-timeframe evidence

`TrendContextTimeframeAssessment` should contain:

- role and interval;
- direction, regime, phase;
- retained swings and suppressed candidate evidence;
- latest same-type relations;
- structural state and confirmed individual levels;
- protected level, if break-eligible;
- break/transition/reclaim state;
- pullback, extension, EMA, ATR, and invalidation evidence;
- freshness result and role-level findings;
- ordered evidence references.

The role result is factual evidence. It does not independently represent an
attention outcome for a financial workflow.

### Swing model

- `ConfirmedSwing`: role, type, pivot index/time/price, confirmation time,
  pivot candle reference, confirmation candle reference, full window reference,
  rule/profile version, and suppression metadata.
- `SwingType`: `HIGH`, `LOW`.
- `SwingRelation`: `HH`, `LH`, `EQ_HIGH`, `HL`, `LL`, `EQ_LOW`.
- `TrendSwingCandidate` may remain package-private if candidates are only
  intermediate evidence; suppressed candidates still need a typed trace in the
  final result.

### Structure and breaks

- `TrendStructure`: latest high/low relations, directional state, and evidence
  references.
- `ProtectedLevel`: protected high/low, price, source swing, role, and evidence.
- `StructuralLevel`: individual retained high/low level, type, price, pivot and
  confirmation references, and protected marker.
- `StructuralBreak`: `CONFIRMED_BEARISH_BREAK`,
  `CONFIRMED_BULLISH_BREAK`, `UNCONFIRMED_BEARISH_BREAK`,
  `UNCONFIRMED_BULLISH_BREAK`, or `NONE`, with breaking candle and protected
  level references.
- `BreakStatus`: the status enum above, with `FAILED_BREAK` represented as a
  separate reclaim/result finding rather than conflated with a wick.

### Supporting indicators and phase

- `TrendPullbackAssessment`: direction, qualifying pivot, post-pivot close
  window, bar count, and qualified/not-qualified evidence.
- `TrendExtensionAssessment`: availability, directional side, distance, ATR
  multiple, and extended boolean.
- `TrendEmaEvidence`: period, seed window, current EMA, slope lookback,
  normalized slope, `RISING`/`FALLING`/`FLAT` classification, and evidence.
- `TrendAtrEvidence`: period, current ATR, baseline ATR, ratio, and optional
  `ABNORMAL_VOLATILITY` finding. It must not expose position sizing or stop data.
- `TrendInvalidation`: `INVALIDATION_UP_V1` or `INVALIDATION_DOWN_V1`, thesis
  direction, protected setup level, invalidating candle, and evidence.

### Alignment and findings

- `TrendTimeframeAlignment`: `ALIGNED_UP`, `ALIGNED_DOWN`,
  `PULLBACK_WITHIN_UP_BIAS`, `PULLBACK_WITHIN_DOWN_BIAS`, `BIAS_TRANSITION`,
  `CONFLICTING`, `INSUFFICIENT_DIRECTION`, `INSUFFICIENT_BIAS`,
  `TRIGGER_CONTRADICTION`, `TRIGGER_UNAVAILABLE`.
- `TrendContextFinding`: rule ID/version, role/interval, code, severity or
  blocking category, deterministic message, and evidence references.
- `TrendContextContradiction`: typed conflict between role results or supporting
  evidence, with no numeric averaging.
- `TrendContextExclusion`: validity/data-quality or valid-market exclusion,
  preserving the distinction between `UNKNOWN`, `NO_SETUP`, and dangerous
  conditions.
- `TrendContextEvidenceReference`: role, interval, source IDs, candle indices or
  times, evidence-window bounds, input fingerprint, profile/rule versions, and a
  stable local evidence key.

The final model should use immutable lists sorted by rule order, role priority,
event time, and stable evidence key. It must not copy complete raw histories
into each finding.

## Rule-to-Responsibility Matrix

| Rule ID | Proposed responsibility | Primary output | Failure/attention effect |
| --- | --- | --- | --- |
| `CANDLE_VALIDATION_V1` | Consume `input.validationFindings()` and role exclusions; do not revalidate 0062 | input-quality exclusions | unsafe input -> `UNKNOWN` |
| `PROFILE_VALIDATION_V1` | Rely on accepted profile invariants; snapshot parameters | profile evidence | impossible for accepted input; defensive failure -> `UNKNOWN` |
| `SWING_HIGH_V1` | strict N-radius high pivot calculator | high candidates/retained swings | no full eligible window -> no candidate |
| `SWING_LOW_V1` | strict N-radius low pivot calculator | low candidates/retained swings | no full eligible window -> no candidate |
| `SWING_SUPPRESSION_V1` | same-type chronological suppression | retained and suppressed swings | invalid comparison -> `UNKNOWN` |
| `RELATIONS_V1` | latest two retained same-type comparisons | HH/LH/EQ and HL/LL/EQ | fewer than two -> insufficient relation |
| `STRUCTURE_UP_V1` | HH + HL and no active bearish break | UP/TRENDING | otherwise not up |
| `STRUCTURE_DOWN_V1` | LH + LL and no active bullish break | DOWN/TRENDING | otherwise not down |
| `PROTECTED_LEVEL_V1` | supporting swing before latest HH/LL | protected level | missing level -> break ineligible |
| `BREAK_CONFIRM_V1` | eligible close strictly beyond protected level | confirmed/unconfirmed break | wick only warning |
| `TRANSITION_V1` | chronological break/reclaim/opposite-structure replay | transition/failure state | transition is adverse, no instant reversal |
| `PULLBACK_UP_V1` | resolved UP setup with strict declining post-high closes | up pullback | no qualification -> no pullback |
| `PULLBACK_DOWN_V1` | mirrored down pullback | down pullback | no qualification -> no pullback |
| `EMA_V1` | SMA seed, EMA recurrence, normalized slope | EMA evidence | unavailable -> cannot be attractive |
| `ATR_V1` | true range, Wilder smoothing, baseline ratio | ATR evidence | unavailable -> cannot be attractive |
| `EXTENSION_V1` | same-side EMA distance divided by ATR | extension | true -> dangerous |
| `LEVEL_V1` | individual retained highs/lows | structural levels | no clustering |
| `INVALIDATION_UP_V1` | SETUP close below protected setup low | up thesis invalidation | analytical only |
| `INVALIDATION_DOWN_V1` | SETUP close above protected setup high | down thesis invalidation | analytical only |
| `MTF_ALIGNMENT_V1` | fixed BIAS > SETUP > TRIGGER matrix | alignment/contradictions | lower role never reverses BIAS |
| `FRESHNESS_V1` | latest eligible close age <= 2 intervals | role freshness | required stale -> `NO_SETUP` |
| `OUTCOME_V1` | fixed conservative precedence | attention | no score or averaging |

`PROVENANCE_V1` and `OBSERVATION_LINEAGE_V1` remain result-lineage concerns in
the pure assessment. They prepare references for `0064`; they do not persist an
observation.

## Swing Model

For each role, process only ascending `calculationReadyCandles` whose close is
at or before the cut-off. A candidate at index `i` is eligible only when the
full `[i-N, i+N]` window exists. A high requires strict `>` against every
neighbor high; a low requires strict `<` against every neighbor low. Equal
prices prevent that same-type pivot. One candle may be both types.

The candidate confirmation time is the right-window candle close at `i+N`. The
candidate cannot be retained if that confirmation close is after the cut-off.
This makes the confirmation rule explicit and prevents look-ahead.

Candidates of the same type are processed by pivot time. Within
`minimumSeparationBars`, highs retain the greater price and lows retain the
lower price. Exact price ties retain the earlier pivot. Cross-type candidates
are never suppressed.

Every retained or suppressed candidate must be reconstructible from its pivot,
confirmation, full window, profile radius, separation rule, and source IDs.

## Structure Model

Only the latest two retained highs and latest two retained lows are comparable.
The latest pair produces exactly one strict relation or an equal relation. UP
requires `HH + HL`; DOWN requires `LH + LL`. If neither condition holds, the
role is `NEUTRAL`/`NON_DIRECTIONAL` when evidence exists, or `UNKNOWN` when
required evidence is unavailable. No `RANGE` enum is introduced.

The transition replay may temporarily hold a previously resolved direction, but
the public result only exposes the accepted factual direction/regime/phase.
Opposite structure must be confirmed after the break confirmation candle and
must itself satisfy the two-pair relation rule.

## Protected Level / Break Model

For UP, select the latest retained low before the latest HH pivot. For DOWN,
select the latest retained high before the latest LL pivot. The level is a
single exact decimal price and carries the source swing reference.

After the protected level is established, a single eligible close strictly
beyond it confirms the adverse break. A wick beyond the level is only an
unconfirmed warning. A confirmed adverse break produces `TRANSITIONING` and
`TRANSITION`, not the opposite direction.

The engine must replay subsequent eligible candles and retained swings to resolve
the transition. Opposite confirmed structure resolves to the opposite trend.
A reclaim at/through the broken level inside `reclaimWindowBars` restores the
prior factual direction but emits `FAILED_BREAK_RECLAIM` and forces dangerous
attention. No event outside the window may be treated as that reclaim.

## Failed Break / Reclaim Model

The break/reclaim state is chronological and derived entirely from the input
series. It is not mutable across engine calls. The result retains:

- original protected level;
- confirmed break candle and close;
- number of bars after break inspected;
- reclaim candle, if any;
- prior direction and restored direction;
- `FAILED_BREAK_RECLAIM` finding.

The failed reclaim is not a new trade state and cannot become attractive in the
same assessment. A later opposite structure may establish an opposite trend
only after the explicit post-break structure requirements.

## EMA Model

Use eligible real closed closes only. Seed with the arithmetic mean of the first
`P` closes, then use `alpha = 2/(P+1)` and the canonical recurrence. Require the
profile period plus warmup. Calculate normalized slope from the configured
lookback and current ATR. Classify only `RISING`, `FALLING`, or `FLAT`.

EMA is supporting evidence. It cannot create direction, structure, pullback,
break, or an attractive outcome. Missing EMA or its ATR normalization prevents a
fully acceptable attractive result rather than being replaced by neutral data.

## ATR Model

Use eligible real closed OHLC only. Compute the first true range as high-low,
then the maximum of high-low and both previous-close gaps. Seed with the mean of
the first `atrPeriod` true ranges and apply Wilder smoothing thereafter. Compute
the baseline as the mean of the latest `atrBaselineLength` available ATR values.

Expose current ATR, baseline, and ratio. Emit only the categorical
`ABNORMAL_VOLATILITY` when the ratio reaches the configured threshold. ATR does
not calculate quantity, stops, margin, Risk, or direction.

## Pullback Model

Replay the SETUP role to identify the last resolved UP or DOWN state. For UP,
the latest retained event must be a high with no later retained low, at least
`pullbackMinimumBars` eligible bars must follow it, and the latest close must be
lower than both the preceding close and the post-high starting reference. The
protected low must not have been broken. DOWN mirrors the same rules with a low
and higher closes.

The prior resolved state is derived by replaying the bounded sequence; no hidden
mutable engine state or external state is allowed. No Fibonacci, percentage
retracement, deep-pullback, or pullback-quality concept is introduced.

## Extension Model

Extension is calculable only when current EMA and ATR are available. It is true
when `abs(close - EMA) / ATR >= extensionAtrMultiple` and the close is on the
same directional side of EMA. It represents poor location in an otherwise
directional context. It is not reversal prediction and does not create an
opposite direction.

## Analytical Invalidation

For an UP SETUP thesis, an eligible SETUP close strictly below the protected
SETUP low emits `INVALIDATION_UP_V1`. DOWN mirrors this against the protected
SETUP high. The result names this a candidate analytical thesis invalidation and
stores the candle and level references. It is not a stop loss, order, Risk
verdict, position size, or execution instruction.

## Multi-Timeframe Alignment

Evaluate BIAS first, SETUP second, and optional TRIGGER last:

- matching BIAS/SETUP directions produce `ALIGNED_UP` or `ALIGNED_DOWN`;
- a controlled SETUP pullback inside BIAS produces the corresponding pullback
  alignment;
- SETUP transition against BIAS produces `BIAS_TRANSITION`;
- opposite BIAS/SETUP produces `CONFLICTING`;
- insufficient SETUP direction produces `INSUFFICIENT_DIRECTION`;
- insufficient BIAS produces `INSUFFICIENT_BIAS`;
- optional trigger absence/staleness produces `TRIGGER_UNAVAILABLE`;
- confirmed trigger opposition or transition produces
  `TRIGGER_CONTRADICTION`;
- trigger neutral is non-reversing and trigger wick-only evidence is a warning.

TRIGGER cannot reverse BIAS. Its contradiction is preserved as evidence and is
handled by conservative outcome precedence.

## Freshness

For each role, compute:

```text
assessmentAt - latestEligibleClosedCandleClose <= freshnessMultiplier * interval
```

The default multiplier is `2`. Future latest closes are invalid input; missing
eligible closes are unavailable evidence. BIAS or SETUP stale means `NO_SETUP`.
Optional TRIGGER stale is treated as unavailable and caps the result at `WATCH`.
No exchange schedule, weekend, holiday, or session inference is permitted.

## Conservative Outcome

Derive attention only after factual and evidence calculations, in this fixed
order:

1. unsafe/uncomputable input or defensive calculation failure -> `UNKNOWN`;
2. missing, stale, insufficient, or non-directional required evidence ->
   `NO_SETUP`;
3. valid but materially adverse transition, conflict, failed reclaim, extension,
   abnormal ATR, or confirmed trigger contradiction ->
   `CONTEXTUALLY_DANGEROUS`;
4. valid direction/pullback with non-blocking warning, optional trigger missing,
   or EMA contradiction -> `WATCH`;
5. aligned or controlled-pullback context with complete required EMA/ATR,
   no exclusion, and no contradiction -> `CONTEXTUALLY_ATTRACTIVE`.

The implementation must encode this as an ordered decision table, not as a
weighted score or confluence calculation. `CONTEXTUALLY_ATTRACTIVE` means only
worth human or strategy review.

## Evidence and Explainability

Every material result must point to:

- input fingerprint, profile ID/version, rule ID/version, cut-off;
- role and interval;
- exact source IDs and candle times;
- relevant evidence-window bounds;
- protected levels and breaking/reclaim candles;
- indicator input/output windows;
- freshness inputs;
- findings, contradictions, and exclusions.

Evidence references should be compact and typed. They should identify source
candles rather than duplicate full role histories. Deterministic ordering is
part of the output contract.

## Assessment Fingerprint

An assessment fingerprint is **REQUIRED** for this Story because the output is
deterministic derived evidence that Story `0064` will later carry into immutable
observation lineage. It should be a SHA-256 canonical digest over:

- input fingerprint;
- profile ID/version and rule version;
- factual role results;
- alignment, findings, exclusions, invalidations, and attention;
- canonical evidence keys and decimal values.

No runtime timestamp, UUID, Spring execution ID, or map iteration order may
affect it. The assessment should also expose the input fingerprint separately so
future lineage can distinguish source/input identity from output identity.

The no-look-ahead test must compare the calculation/evidence/outcome projection
for a fixed cut-off. Because `0062` includes cut-off-excluded candles in its
input fingerprint, appending a future source candle may legitimately change
lineage identity while leaving all bounded analytical content unchanged. This is
an explicit test and documentation distinction, not permission to consume the
future candle.

## Numerical Determinism

Structural price comparisons use exact `BigDecimal.compareTo` with zero
tolerance. EMA, ATR, means, and ratios must use explicit decimal arithmetic;
binary floating point is forbidden.

The implementation plan should standardize intermediate division and
multiplication on `MathContext.DECIMAL128`, with no implicit scale-dependent
comparison. Any display/output scale must be explicit and must not alter strict
structural comparisons. Canonical digest values use
`stripTrailingZeros().toPlainString()`, matching the existing input fingerprint
convention.

Tests must cover non-terminating division, equal values with different scales,
and strict threshold boundary values.

## No-Look-Ahead and Symmetry

The engine must filter by the input cut-off before every calculation, not only
once at the top-level. Tests must verify that later candles cannot affect:

- pivots or confirmation times;
- suppression and relations;
- break/reclaim/transition;
- pullback;
- EMA/ATR/extension;
- invalidation, alignment, or attention.

Parameterized tests should mirror bullish and bearish fixtures for high/low
relations, direction, protected levels, breaks, pullbacks, invalidation, and
alignment.

## Canonical 24 Scenarios

| # | Fixture and primary rules | Expected factual result | Expected attention/evidence |
| ---: | --- | --- | --- |
| 1 | aligned HH+HL, complete indicators, supportive trigger | UP, TRENDING, DIRECTIONAL, `ALIGNED_UP` | ATTRACTIVE; protected SETUP low |
| 2 | aligned LH+LL, complete indicators, supportive trigger | DOWN, TRENDING, DIRECTIONAL, `ALIGNED_DOWN` | ATTRACTIVE; protected SETUP high |
| 3 | resolved UP then strict post-high decline | UP, TRENDING, PULLBACK | ATTRACTIVE; pullback evidence |
| 4 | resolved DOWN then strict post-low rise | DOWN, TRENDING, PULLBACK | ATTRACTIVE; mirrored pullback evidence |
| 5 | UP same-side EMA distance at extension threshold | UP, TRENDING, EXTENDED | DANGEROUS; `EXTENDED_LOCATION` |
| 6 | DOWN same-side EMA distance at extension threshold | DOWN, TRENDING, EXTENDED | DANGEROUS; mirrored extension |
| 7 | UP close below protected low | UP, TRANSITIONING, TRANSITION | DANGEROUS; break and up invalidation |
| 8 | DOWN close above protected high | DOWN, TRANSITIONING, TRANSITION | DANGEROUS; mirrored break/invalidation |
| 9 | post-break opposite LH+LL | DOWN, TRENDING, DIRECTIONAL | default WATCH; prior transition and new protected high |
| 10 | wick beyond protected level, close does not breach | original direction/regime unchanged | outcome unchanged; unconfirmed-break warning |
| 11 | confirmed break then reclaim within window | prior direction restored, transition history retained | DANGEROUS; `FAILED_BREAK_RECLAIM` |
| 12 | reactions without strict directional pairs | NEUTRAL, NON_DIRECTIONAL, UNDETERMINED | NO_SETUP; `NO_DIRECTIONAL_STRUCTURE` |
| 13 | equal neighboring highs/lows | no corresponding pivot/relations | NO_SETUP when structure is insufficient |
| 14 | BIAS UP and SETUP DOWN | role facts preserved independently | NO_SETUP; `CONFLICTING` |
| 15 | BIAS UP and controlled SETUP pullback with supportive trigger | UP, TRENDING, PULLBACK | ATTRACTIVE; pullback alignment |
| 16 | BIAS UP pullback and trigger DOWN/transition | BIAS/SETUP facts remain; trigger adverse | DANGEROUS; `TRIGGER_CONTRADICTION` |
| 17 | SETUP stale beyond two intervals | setup not current | NO_SETUP; `ROLE_STALE` |
| 18 | optional TRIGGER absent | BIAS/SETUP facts remain | WATCH; `TRIGGER_UNAVAILABLE`, never attractive |
| 19 | BIAS missing | no required context | UNKNOWN; `INSUFFICIENT_BIAS` |
| 20 | fewer than two retained highs/lows | unknown or neutral structure | NO_SETUP; `INSUFFICIENT_SWINGS` |
| 21 | synthetic candle would be a pivot | synthetic excluded from structure/indicators | NO_SETUP if required history affected |
| 22 | gap removes a pivot-window candle | no candidate across gap | NO_SETUP; `GAP_IN_HISTORY` |
| 23 | abnormal ATR ratio with aligned structure | directional facts preserved | DANGEROUS; `ABNORMAL_VOLATILITY` |
| 24 | identical replay | byte-equivalent deterministic output | identical evidence, outcome, and assessment fingerprint |

All 24 scenarios are representable by the `0062` input contract. Scenarios 1,
2, 3, 4, 7, 8, 9, 11, 15, and 16 require multi-candle role fixtures; they
must not be reduced to single-candle mapper tests.

## Property and Invariant Test Strategy

The implementation must add focused pure-domain tests for:

- same accepted input/profile/rules/cut-off -> equal assessment and fingerprint;
- input map/candle order changes do not alter output;
- appended post-cutoff evidence does not alter bounded analytical content;
- synthetic/open candles never become pivots, indicators, breaks, or levels;
- equal highs/lows never create same-type pivots;
- wick-only breaches never confirm breaks;
- trigger contradiction never reverses BIAS;
- EMA never creates direction or structure;
- ATR never creates direction or Risk data;
- extension never creates the opposite direction;
- attractive output never creates downstream strategy, opportunity, Risk, or
  execution objects;
- bullish and bearish parameterized fixtures remain mirror-equivalent.

JUnit 5 parameterized tests and ordinary invariant assertions are sufficient;
no new property-testing dependency is required.

## Scope Boundaries

### Story 0064 preserved

The engine does not acquire role data, register a production capability, create
`AnalysisExecution`, persist or supersede `IntelligenceObservation`, expose a
REST endpoint, or manage orchestration. `0064` owns those responsibilities.

### Story 0065 preserved

The engine does not evaluate `StrategyDefinition`, create `StrategyEvaluation`,
`StrategyMatch`, or `TradingOpportunity`. A favorable assessment is reusable
deterministic evidence only.

### Risk and execution preserved

The engine has no account, position, margin, Risk, TradePlan, stop, quantity,
ExecutionIntent, broker, or human-authorization semantics. Analytical
invalidation is explicitly not a stop loss.

### Deferred concepts absent

This Story must not add categorical `RANGE`, LOW/NORMAL/HIGH volatility,
momentum, strength, score, probability, RSI, MACD, VWAP, ADX, volume profile,
order flow, liquidity/level clustering, ML, LLM, agent, or strategy semantics.

## Risks

- The largest implementation risk is accidentally turning a factual role result
  into an attention or trading conclusion.
- Transition/reclaim chronology can become look-ahead-prone if calculated from
  final swings without replaying the bounded event sequence.
- Profile thresholds are explicitly paper-validation hypotheses and must not be
  presented as validated strategy parameters.
- The input fingerprint includes excluded post-cutoff evidence; output lineage
  and no-look-ahead analytical equality must remain separately testable.
- Existing generic `IntelligenceFinding` contains wall-clock generation time and
  is unsuitable for a deterministic output identity.

## Blockers

None for the pure deterministic implementation scope. No ADR contradiction or
Story scope change is required. The `0062` documentation freshness discrepancy
should be corrected in its own documentation workflow, but does not change the
accepted input implementation or block planning `0063`.

## Readiness

Implementation ready after human review of this analysis and the accompanying
plan. The next implementation pass must remain limited to Story `0063` pure
domain types, calculators, engine, and focused tests.

## Files

Created:

- `docs/architecture/stories/0063-deterministic-trend-context-engine/repository-analysis.md`
- `docs/architecture/stories/0063-deterministic-trend-context-engine/implementation-plan.md`

Modified:

- None outside the two Story `0063` planning artifacts.

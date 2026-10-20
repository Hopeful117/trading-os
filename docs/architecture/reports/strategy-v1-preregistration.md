# Trading OS Research V1: First Deterministic Strategy Pre-Registration

## 1. Executive Decision

**FINAL DECISION: A — S1_PREREGISTERED_DATA_ACCUMULATION_REQUIRED**

The first deterministic hypothesis is pre-registered below. The current
population is not large or diverse enough for a meaningful strategy evaluation.
No S1 economic evaluation was executed.

## 2. Research Scope

This document freezes one deterministic signal before any S1 outcome or
economic evaluation. It is a research baseline, not a production strategy.

- Registration date: 2026-10-19
- Feature schema: `feature-projection-v1`
- Market scope: market `e23ba62a-995f-4755-8deb-11a71ab3788b`, instrument `PF_XBTUSD`, `LINEAR_PERPETUAL`
- Research population: canonical versioned `ResearchSample` bindings
- Current persisted population: 75 schema-valid FeatureSnapshots, 74 distinct cutoffs
- Current feature-only span: `2026-10-16T22:00:00Z` through `2026-10-19T21:00:00Z`

Q1 was used only to validate the research machinery and was not used to select
this hypothesis, its direction, its features, or any parameter.

## 3. Repository State

- `REPOSITORY_READY = YES`
- Branch: `main`
- `HEAD = origin/main = 5cfa75896c9b38d0bbba48467abba58b94df4383`
- Story 0131 merged work is present: versioned `ResearchSample` binding and
  experiment-level feature eligibility.
- `git diff --check` passed before this artifact was created.
- Pre-existing untracked file preserved and not modified:
  `docs/architecture/reports/v1-functional-audit.md`

## 4. Q1 Confirmation

Q1 demonstrated reproducible paired LONG/SHORT processing through the
canonical PT1H historical economic path, including historical commission
resolution and partially-costed results. Funding remained unavailable.

- `Q1_USED_FOR_STRATEGY_SELECTION = NO`
- No Q1 P&L, winning direction, cutoff, or subgroup was used for S1 design.
- No live order, position mutation, broker mutation, or account mutation was
  performed for this work.

## 5. Anti-Data-Snooping Check

- Q1 P&L used to select S1: **NO**
- Feature profitability slicing performed: **NO**
- Threshold optimization performed: **NO**
- Direction selected from Q1 performance: **NO**
- Holding-period optimization performed: **NO**
- Parameter search performed: **NO**
- ML used: **NO**
- S1 rules frozen before outcome evaluation: **YES**

## 6. FeatureProjection V1 State

`FeatureProjectionV1` defines 43 features:

- OHLC: 2 numeric
- Market structure: 15 features for each of bias, setup, and trigger
- Trend context: 26 features across alignment, attention, and the three roles
- Numeric: 14
- Categorical: 26
- Boolean: 3

The implementation groups are `OHLC`, `STRUCTURE`, and `TREND`. The role
semantics are `bias`, `setup`, and `trigger`. The projection implementation
was not changed.

## 7. Feature Availability

Counts are across 75 persisted `feature-projection-v1` snapshots. `PRESENT` is
the domain availability state used by the feature model. First/latest values
are UTC cutoffs.

| Feature | Type | Present | Missing | Availability | First available | Latest available |
|---|---:|---:|---:|---:|---|---|
| `ohlc.range_fraction` | numeric | 31 | 44 | 41.3% | 2026-10-18 16:45 | 2026-10-19 21:00 |
| `ohlc.simple_return` | numeric | 31 | 44 | 41.3% | 2026-10-18 16:45 | 2026-10-19 21:00 |
| `structure.bias.high_relation` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `structure.bias.low_relation` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `structure.bias.confirmed_high_count` | numeric | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `structure.bias.confirmed_low_count` | numeric | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `structure.bias.suppressed_swing_count` | numeric | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `structure.setup.high_relation` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `structure.setup.low_relation` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `structure.setup.confirmed_high_count` | numeric | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `structure.setup.confirmed_low_count` | numeric | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `structure.setup.suppressed_swing_count` | numeric | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `structure.trigger.high_relation` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `structure.trigger.low_relation` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `structure.trigger.confirmed_high_count` | numeric | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `structure.trigger.confirmed_low_count` | numeric | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `structure.trigger.suppressed_swing_count` | numeric | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.alignment` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.attention` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.bias.direction` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.bias.regime` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.bias.phase` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.bias.ema_slope` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.bias.atr_ratio` | numeric | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.bias.pullback_qualified` | boolean | 0 | 75 | 0.0% | never | never |
| `trend.bias.extension_state` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.bias.break_status` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.setup.direction` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.setup.regime` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.setup.phase` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.setup.ema_slope` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.setup.atr_ratio` | numeric | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.setup.pullback_qualified` | boolean | 11 | 64 | 14.7% | 2026-10-17 21:00 | 2026-10-19 21:00 |
| `trend.setup.extension_state` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.setup.break_status` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.trigger.direction` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.trigger.regime` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.trigger.phase` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.trigger.ema_slope` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.trigger.atr_ratio` | numeric | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.trigger.pullback_qualified` | boolean | 30 | 45 | 40.0% | 2026-10-17 02:00 | 2026-10-19 17:00 |
| `trend.trigger.extension_state` | categorical | 74 | 1 | 98.7% | 2026-10-16 22:00 | 2026-10-19 21:00 |
| `trend.trigger.break_status` | categorical | 75 | 0 | 100.0% | 2026-10-16 22:00 | 2026-10-19 21:00 |

## 8. Feature Variation

Variation was characterized without joining outcomes or calculating P&L.

### Numeric Features

| Feature | N | Min | Max | Mean | Median | Std dev |
|---|---:|---:|---:|---:|---:|---:|
| `ohlc.range_fraction` | 31 | 0.018299 | 0.031136 | 0.026034 | 0.027338 | 0.005323 |
| `ohlc.simple_return` | 31 | -0.014699 | 0.016486 | 0.005368 | 0.007720 | 0.009176 |
| `structure.bias.confirmed_high_count` | 75 | 8 | 9 | 8.173333 | 8 | 0.381084 |
| `structure.bias.confirmed_low_count` | 75 | 9 | 10 | 9.160000 | 9 | 0.369075 |
| `structure.bias.suppressed_swing_count` | 75 | 0 | 0 | 0.000000 | 0 | 0.000000 |
| `structure.setup.confirmed_high_count` | 75 | 5 | 11 | 7.866667 | 8 | 1.621922 |
| `structure.setup.confirmed_low_count` | 75 | 6 | 7 | 6.533333 | 7 | 0.502247 |
| `structure.setup.suppressed_swing_count` | 75 | 0 | 0 | 0.000000 | 0 | 0.000000 |
| `structure.trigger.confirmed_high_count` | 75 | 6 | 9 | 7.480000 | 7 | 0.905837 |
| `structure.trigger.confirmed_low_count` | 75 | 4 | 10 | 6.880000 | 7 | 1.413831 |
| `structure.trigger.suppressed_swing_count` | 75 | 0 | 0 | 0.000000 | 0 | 0.000000 |
| `trend.bias.atr_ratio` | 75 | 0.797889 | 1.087037 | 0.894781 | 0.867858 | 0.080927 |
| `trend.setup.atr_ratio` | 75 | 0.646538 | 1.274818 | 0.990237 | 1.035093 | 0.176848 |
| `trend.trigger.atr_ratio` | 75 | 0.656287 | 1.474979 | 1.005769 | 1.017404 | 0.178039 |

### Categorical and Boolean Features

The notation is `value: count (percentage of PRESENT values)`. Boolean
missingness is reported separately.

| Feature | Observed values |
|---|---|
| `structure.bias.high_relation` | HH: 55 (73.3%), LH: 20 (26.7%) |
| `structure.bias.low_relation` | LL: 68 (90.7%), HL: 7 (9.3%) |
| `structure.setup.high_relation` | LH: 47 (62.7%), HH: 28 (37.3%) |
| `structure.setup.low_relation` | LL: 43 (57.3%), HL: 32 (42.7%) |
| `structure.trigger.high_relation` | LH: 41 (54.7%), HH: 31 (41.3%), EQ_HIGH: 3 (4.0%) |
| `structure.trigger.low_relation` | HL: 44 (58.7%), LL: 31 (41.3%) |
| `trend.alignment` | BIAS_TRANSITION: 48 (64.0%), CONFLICTING: 18 (24.0%), TRIGGER_CONTRADICTION: 6 (8.0%), ALIGNED_DOWN: 2 (2.7%), ALIGNED_UP: 1 (1.3%) |
| `trend.attention` | CONTEXTUALLY_DANGEROUS: 56 (74.7%), NO_SETUP: 18 (24.0%), CONTEXTUALLY_ATTRACTIVE: 1 (1.3%) |
| `trend.bias.direction` | UP: 50 (66.7%), DOWN: 25 (33.3%) |
| `trend.bias.regime` | TRENDING: 60 (80.0%), TRANSITIONING: 15 (20.0%) |
| `trend.bias.phase` | TRANSITION: 63 (84.0%), DIRECTIONAL: 12 (16.0%) |
| `trend.bias.ema_slope` | FALLING: 66 (88.0%), FLAT: 9 (12.0%) |
| `trend.bias.extension_state` | NOT_EXTENDED: 75 (100.0%) |
| `trend.bias.break_status` | CONFIRMED_BEARISH_BREAK: 50 (66.7%), CONFIRMED_BULLISH_BREAK: 13 (17.3%), NONE: 12 (16.0%) |
| `trend.setup.direction` | DOWN: 50 (66.7%), UP: 25 (33.3%) |
| `trend.setup.regime` | TRANSITIONING: 48 (64.0%), TRENDING: 27 (36.0%) |
| `trend.setup.phase` | TRANSITION: 53 (70.7%), DIRECTIONAL: 14 (18.7%), PULLBACK: 8 (10.7%) |
| `trend.setup.ema_slope` | FALLING: 30 (40.0%), FLAT: 25 (33.3%), RISING: 20 (26.7%) |
| `trend.setup.pullback_qualified` | true: 8 (72.7%), false: 3 (27.3%); missing: 64 |
| `trend.setup.extension_state` | NOT_EXTENDED: 75 (100.0%) |
| `trend.setup.break_status` | CONFIRMED_BULLISH_BREAK: 35 (46.7%), NONE: 21 (28.0%), CONFIRMED_BEARISH_BREAK: 18 (24.0%), UNCONFIRMED_BULLISH_BREAK: 1 (1.3%) |
| `trend.trigger.direction` | DOWN: 38 (50.7%), UP: 36 (48.0%), NEUTRAL: 1 (1.3%) |
| `trend.trigger.regime` | TRENDING: 56 (74.7%), TRANSITIONING: 18 (24.0%), NON_DIRECTIONAL: 1 (1.3%) |
| `trend.trigger.phase` | TRANSITION: 25 (33.3%), DIRECTIONAL: 22 (29.3%), EXTENDED: 17 (22.7%), PULLBACK: 10 (13.3%), UNDETERMINED: 1 (1.3%) |
| `trend.trigger.ema_slope` | FALLING: 34 (45.3%), RISING: 29 (38.7%), FLAT: 12 (16.0%) |
| `trend.trigger.extension_state` | NOT_EXTENDED: 57 (77.0%), EXTENDED: 17 (23.0%); missing: 1 |
| `trend.trigger.pullback_qualified` | false: 19 (63.3%), true: 11 (36.7%); missing: 45 |
| `trend.trigger.break_status` | NONE: 46 (61.3%), CONFIRMED_BEARISH_BREAK: 13 (17.3%), CONFIRMED_BULLISH_BREAK: 13 (17.3%), UNCONFIRMED_BULLISH_BREAK: 2 (2.7%), UNCONFIRMED_BEARISH_BREAK: 1 (1.3%) |

## 9. Unsuitable Features

- `trend.bias.pullback_qualified`: never available.
- `ohlc.range_fraction`, `ohlc.simple_return`: currently available only 31/75
  snapshots; not suitable as required S1 inputs without more acquisition.
- `trend.setup.pullback_qualified`: 11/75 available; too rare for the first
  required feature.
- `trend.trigger.pullback_qualified`: 30/75 available; currently too sparse
  for a first required feature.
- `structure.bias.suppressed_swing_count`,
  `structure.setup.suppressed_swing_count`, and
  `structure.trigger.suppressed_swing_count`: constant zero.
- `trend.bias.extension_state` and `trend.setup.extension_state`: constant
  `NOT_EXTENDED`.
- `trend.alignment`: semantically useful but currently near-constant for
  aligned states: only 3/75 observations are `ALIGNED_UP` or `ALIGNED_DOWN`.
- Several structure count features have narrow ranges and are secondary
  candidates, not first-hypothesis inputs.

These features remain part of `FeatureProjectionV1`; none were removed or
changed.

## 10. Candidate Hypothesis 1

**Trend alignment continuation**

- Market observation: the domain already exposes an aggregate multi-timeframe
  alignment state.
- Why this could affect PT1H movement: aligned context may indicate that
  multiple deterministic trend roles point in one direction.
- Required deterministic features: `trend.alignment`.
- Expected direction: `ALIGNED_UP` -> LONG; `ALIGNED_DOWN` -> SHORT.
- Main failure mode: aligned states are currently too rare, producing an
  underpowered experiment.

## 11. Candidate Hypothesis 2

**Three-role trend direction consensus**

- Market observation: `bias`, `setup`, and `trigger` each expose a categorical
  direction with meaningful UP/DOWN variation.
- Why this could affect PT1H movement: agreement across context roles may
  represent a simpler, explicit continuation condition.
- Required deterministic features: `trend.bias.direction`,
  `trend.setup.direction`, `trend.trigger.direction`.
- Expected direction: all UP -> LONG; all DOWN -> SHORT.
- Main failure mode: requiring complete agreement may sharply reduce signal
  frequency and may select only unusually coherent states.

## 12. Candidate Hypothesis 3

**Confirmed trigger-break continuation**

- Market observation: the trigger timeframe exposes a categorical structural
  break status with confirmed bullish and bearish values.
- Why this could affect PT1H movement: a confirmed structural break is a
  deterministic event that plausibly carries short-horizon continuation
  information.
- Required deterministic feature: `trend.trigger.break_status`.
- Expected direction: `CONFIRMED_BULLISH_BREAK` -> LONG;
  `CONFIRMED_BEARISH_BREAK` -> SHORT.
- Main failure mode: a confirmed break may be late, exhausted, or represent a
  false break in the following hour.

## 13. Candidate Comparison

| Criterion | C1 alignment | C2 direction consensus | C3 trigger break |
|---|---|---|---|
| Domain plausibility | high | high | high |
| Required features | 1 | 3 | 1 |
| Required availability | 75/75 | 75/75 | 75/75 |
| Observed signal frequency | 3/75 | 11/75 | 26/75 |
| Simplicity | high | medium | highest |
| Categorical semantics | yes | yes | yes |
| Directional symmetry | yes | yes | yes |
| PT1H compatibility | yes | yes | yes |
| Main readiness concern | too rare | low frequency | needs more time span |

The comparison uses only domain semantics, feature coverage, variation, and
expected frequency. It uses no outcome, P&L, win rate, or economic result.

## 14. Selected S1 Hypothesis

### FIRST DETERMINISTIC STRATEGY HYPOTHESIS

- Experiment ID: `S1_TRIGGER_BREAK_CONTINUATION_PT1H_V1`
- Observation: a confirmed structural break is available as a domainized
  trigger-timeframe categorical feature.
- Market rationale: a confirmed break may indicate that the market has moved
  through a retained structural level and may continue in that direction over
  the next PT1H interval.
- Hypothesis: when a confirmed trigger break is present at T0, the signed
  PT1H forward return in the break direction is positive on average relative
  to the direction-neutral no-information baseline.
- Null hypothesis: the selected deterministic signal provides no useful
  directional information for PT1H future movement; mean signed forward return
  is not positive relative to the pre-registered baseline.

This was selected because it is the smallest categorical, symmetric,
domain-interpretable rule with complete current availability and meaningful
signal frequency. It was not selected because it produced favorable historical
economic results.

## 15. Required Features

Exactly one required feature is declared:

1. `trend.trigger.break_status`

Only this feature controls S1 feature eligibility. Other missing FeatureProjection
values do not exclude a schema-valid S1 sample.

## 16. Exact Signal

```text
if snapshot schema is invalid:
    INELIGIBLE
else if trend.trigger.break_status is unavailable:
    INELIGIBLE
else if trend.trigger.break_status == CONFIRMED_BULLISH_BREAK:
    LONG
else if trend.trigger.break_status == CONFIRMED_BEARISH_BREAK:
    SHORT
else:
    NO_TRADE
```

There is no fallback feature, threshold, score, parameter, time filter, or
direction substitution.

## 17. Long Rule

`LONG` exactly when the required feature value equals
`CONFIRMED_BULLISH_BREAK`.

## 18. Short Rule

`SHORT` exactly when the required feature value equals
`CONFIRMED_BEARISH_BREAK`.

## 19. No-Trade Rule

An eligible sample is `NO_TRADE` when the required feature is present but its
value is any state other than the two confirmed break values, including
`NONE` and unconfirmed break states.

## 20. Eligibility Rule

- The selected FeatureSnapshot must be schema-valid and bound by its exact
  immutable fingerprint.
- The selected ObservedMarketOutcome must be bound by its exact immutable
  fingerprint.
- `trend.trigger.break_status` must have status `PRESENT`.
- Missing required evidence is `INELIGIBLE`, never a signal substitution.
- A false signal condition with the required feature present is eligible
  `NO_TRADE`, not ineligible.

## 21. Holding Horizon

`PT1H`, using the existing `ObservedMarketOutcome` horizon and maturity
semantics.

## 22. Quantity Policy

Fixed research quantity `1`, matching Q1. No sizing optimization or risk-based
quantity is part of S1.

## 23. Execution Model

`MARKET`, using the existing deterministic historical visible-depth replay.
No limit, maker, spread, or execution optimization is included.

## 24. Cost Model

- Historical commission is required and resolved from the applicable historical
  fee schedule.
- Historical funding is used when available.
- If funding is unavailable, the result remains explicitly
  `DEGRADED/PARTIALLY_COSTED`.
- Partially-costed results must not be described as fully-costed net
  profitability.

## 25. Primary Metric

**Mean signed PT1H forward return** over eligible signal observations:

```text
LONG:  +forward_return
SHORT: -forward_return
```

This metric is selected because it tests directional information before
execution costs and is already represented by `ObservedMarketOutcome`.

## 26. Secondary Metrics

- Median signed PT1H forward return
- Directional hit rate, defined as signed forward return > 0
- Signal count
- LONG and SHORT counts
- `NO_TRADE` count
- Ineligible count

These metrics are descriptive and must not replace the primary metric.

## 27. Economic Metrics

When the economic path is eventually run, record canonical
`EconomicResearchResult` fields:

- gross strategy P&L
- execution-adjusted P&L
- commission amount and evidence
- funding status and amount when available
- cost completeness
- execution status and residual exposure

No partially-costed result may be reported as net profitability.

## 28. Baseline

The baseline is the direction-neutral no-information baseline: expected signed
forward return `0`. It does not use Q1 LONG or SHORT performance and does not
select a preferred direction.

An eventual report may additionally describe unconditional market forward
return, but it must not replace the frozen primary baseline after seeing S1
results.

## 29. Failure Criterion

Before evaluation, S1 is considered **not supported** when the eligible signal
sample has a mean signed PT1H forward return less than or equal to `0`.

If the minimum observation target below has not been met, the result is
**inconclusive and not support**, rather than evidence for or against a durable
edge. No post-hoc threshold, direction, horizon, or exclusion adjustment is
permitted.

Formal statistical significance is not promised for the initial deterministic
baseline.

## 30. Population Policy

Use the canonical versioned population. Each sample must bind one exact
FeatureSnapshot fingerprint and one exact PT1H ObservedMarketOutcome
fingerprint. No raw cutoff join, latest-row selection, creation-time selection,
or replacement version is allowed.

## 31. Temporal Validation Policy

Preserve chronological order. Do not randomize samples. The initial evaluation
may be exploratory in-sample only if the readiness gate is met. A later
chronologically subsequent out-of-sample period is required before treating a
surviving result as a durable hypothesis.

No train/test split is implemented now because the current span is too short.

## 32. Current Feature-Only Signal Frequency

This section intentionally contains no outcome or P&L values.

- Total persisted FeatureSnapshots: 75
- Schema-valid snapshots: 75
- Required feature count: 1
- Snapshots with required feature available: 75
- Eligible: 75
- LONG: 13
- SHORT: 13
- NO_TRADE: 49
- Ineligible: 0
- Earliest eligible cutoff: `2026-10-16T22:00:00Z`
- Latest eligible cutoff: `2026-10-19T21:00:00Z`
- Elapsed span: 2 days 23 hours

## 33. Current Data Sufficiency

`CURRENT_POPULATION_FOR_S1 = INSUFFICIENT`

The current feature population has only 26 signal observations across less than
three days, with no demonstrated regime diversity. Only five paired samples
have been confirmed through the complete Q1 economic replay path. This is not
enough for an honest initial S1 economic interpretation.

`RUN_S1_NOW = NO`.

## 34. Data Accumulation Target

Accumulate before the first S1 evaluation:

- at least 60 eligible signal observations;
- at least 30 LONG and 30 SHORT signals;
- at least 14 chronological days;
- at least two visibly different market regimes in the deterministic feature
  context;
- trusted Order Book replay coverage for at least 90% of signal T0/T1 pairs;
- historical commission coverage for all evaluated executions;
- explicit accounting of funding availability without treating missing funding
  as zero.

This is a research-readiness target, not a claim of statistical power or a
guarantee of significance.

## 35. Acquisition Health

`S1_DATA_ACCUMULATION_HEALTH = PASS`

The enabled passive lifecycle is configured for the canonical market with PT1H
outcomes and continues to persist immutable FeatureSnapshots and objective
outcomes. Current evidence confirms:

- FeatureSnapshots: 75 persisted, increasing lifecycle population;
- PT1H outcomes: available, incomplete, and unavailable states are persisted;
- Order Book evidence: 29,758,958 rows across 86 sequences;
- historical fee schedule: one applicable schedule is persisted;
- funding evidence: currently zero rows.

Order Book acquisition health: **PASS** for the current persisted evidence,
subject to per-sample replay eligibility. Historical fee acquisition health:
**PASS**. Historical funding availability: **NO**.

## 36. Funding Priority

- `HISTORICAL_FUNDING_NEXT_PRIORITY = NOT_YET`
- Funding blocks signal research: **NO**
- Funding blocks fully-costed profitability conclusion: **YES**

Funding should be addressed before any claim of fully-costed perpetual
profitability, but it is not the next blocker for determining whether this
signal contains directional information.

## 37. Additional Architecture Assessment

`ADDITIONAL_RESEARCH_ARCHITECTURE_REQUIRED = NO`.

The existing versioned population binding, experiment-level feature
eligibility, objective PT1H outcomes, historical execution replay, and economic
result contracts are sufficient for the future initial evaluation. No
production strategy path, live execution integration, scanner automation, or
new database schema is required for this pre-registration.

## 38. Notebook Plan

The eventual exploratory evaluation should use a Jupyter notebook only for
inspection and interpretation after Java has produced canonical facts and
economic results. The notebook may:

- load exported versioned samples and outcomes;
- verify chronological ordering and fingerprints;
- apply the already-frozen signal mechanically;
- calculate the pre-registered signal and baseline metrics;
- visualize signal counts and signed forward returns;
- display cost completeness and replay coverage;
- preserve an immutable input manifest.

The notebook must not become an authority for market facts, feature
calculation, execution replay, fee resolution, or funding semantics.

## 39. ML Boundary

No ML, scikit-learn, PyTorch, model training, feature selection, or learned
threshold is introduced. S1 establishes a deterministic baseline and may later
serve as a supervised-learning comparator after sufficient chronological data
exists.

## 40. Pre-Registration Artifact

This document is the version-controlled pre-registration artifact:

`docs/architecture/reports/strategy-v1-preregistration.md`

It contains the experiment identity, rationale, exact signal, eligibility,
metrics, baseline, failure criterion, temporal policy, cost semantics, and
prohibited post-hoc changes. It contains no S1 strategy performance.

## 41. Tests / Build

- Repository sanity commands executed.
- FeatureProjection implementation and persisted payload audited.
- Feature-only availability and variation queries executed against the current
  persisted database.
- No S1 economic evaluation executed.
- No production strategy code added.
- `git diff --check` to be rerun after this document is staged for review.
- Full Maven verification is not required for this documentation-only change;
  no Java source or test was modified.

## 42. Repository Protection

- `docs/architecture/reports/v1-functional-audit.md` remains untouched and
  untracked.
- No secrets, credentials, tokens, or runtime temporary files are added to the
  repository.
- No live order or broker mutation was performed.

## 43. Required Research-Readiness Summary

```text
S1 FEATURE READINESS

Total persisted FeatureSnapshots = 75
Schema-valid snapshots = 75
Required feature count = 1
Snapshots with all required features available = 75

SIGNAL FREQUENCY

Eligible = 75
LONG = 13
SHORT = 13
NO_TRADE = 49
Ineligible = 0

Earliest eligible cutoff = 2026-10-16T22:00:00Z
Latest eligible cutoff = 2026-10-19T21:00:00Z
Elapsed span = 2 days 23 hours
```

```text
S1 RESEARCH READINESS

Current population classification = INSUFFICIENT
Run S1 now = NO
More data required = YES
Data accumulation target = 60 signals, balanced 30/30, 14 days, two regimes,
                           90% trusted replay coverage, complete fee coverage
Passive acquisition healthy = YES
Order Book acquisition healthy = YES
Historical fee acquisition healthy = YES
Historical funding available = NO

Funding blocks signal research = NO
Funding blocks fully-costed profitability conclusion = YES

Additional architecture required = NO
```

```text
ANTI-DATA-SNOOPING

Q1 PnL used to select S1 = NO
Feature profitability slicing performed = NO
Threshold optimization performed = NO
Direction selected from Q1 performance = NO
Holding-period optimization performed = NO
Parameter search performed = NO
ML used = NO

S1 rules frozen before outcome evaluation = YES
```

## 44. Final Decision

```text
FINAL DECISION = A — S1_PREREGISTERED_DATA_ACCUMULATION_REQUIRED
```

## 45. Exact Next Action

Continue passive acquisition only. Do not run S1 economic evaluation until the
data accumulation target and replay-coverage gate are met. Then generate a
canonical versioned S1 population using the frozen signal and evaluate it once
without changing the pre-registration; any changed rule must receive a new V2
registration.

# Deterministic Trend Context Domain Design

**Date:** 2026-09-23
**Status:** Design-hardened; proposed for human approval
**Scope:** V1 deterministic multi-timeframe Trend Context Assessment for a conservative trend-following swing trader.

## 1. Purpose and Decision

This document freezes the smallest coherent V1 rule set that can later be implemented independently by Java developers without requiring additional trading-domain interpretation.

The answer to the implementation-readiness question is:

> **YES for the pure deterministic domain contract and rules below.** Every retained V1 conclusion has explicit inputs, parameters, ordering, and failure behavior. Complex range classification, momentum classification, aggregate strength, and clustered zones are explicitly deferred rather than left subjective.

The design does not reopen the accepted conservative-trader product direction or layered intelligence architecture. It narrows the first slice to reproducible evidence and conservative attention guidance.

## 2. Authority Pipeline

The assessment is analytical evidence, not a strategy match or trading opportunity:

```text
Market Data
    ↓
Trend Context Assessment
    ↓
IntelligenceObservation
    ↓
StrategyEvaluation
    ↓
StrategyMatch
    ↓
TradingOpportunity
    ↓
Human review / Trade Plan
    ↓
Risk
    ↓
Human execution authorization
    ↓
Execution
```

The following distinctions are mandatory:

- `TrendContextAssessment`: reproducible market-context evidence.
- `IntelligenceObservation`: durable lineage and reusable observation memory.
- `StrategyEvaluation`: evaluation of one versioned strategy against the observation/context.
- `StrategyMatch`: immutable fact that a specific strategy version matched.
- `TradingOpportunity`: trader-facing candidate derived from a `StrategyMatch`.
- `TradePlan`: concrete human-reviewable proposal.
- `RiskEvaluation`: account-specific financial authorization.

A favorable assessment **must not** create a `StrategyMatch`, `TradingOpportunity`, `TradePlan`, Risk result, or execution command. No V1 rule has financial or execution authority.

## 3. Current Repository Contracts and Required Boundary

The repository already provides reusable concepts:

- Market Data `OhlcEvent` contains market/provider identity, interval, open/close times, OHLCV, `closed`, and `occurredAt`.
- `OhlcInterval` supports 1m, 5m, 15m, 30m, 1h, 4h, and 1d.
- Market Intelligence `HistoricalOhlcContext` currently contains one market ID, one interval string, and `OhlcPoint` candles.
- `OhlcPoint` currently lacks `closed`, provider, source identity, and synthetic-candle status.
- The current historical context contributor requests only 200 fifteen-minute candles.
- `ContextSection` and `ContextProvenance` already represent section status and source/fetch timestamps.
- `Observation`, `ObservationEvidence`, `AnalysisExecution`, `StrategyDefinition`, `StrategyEvaluation`, and `StrategyMatch` already provide lineage and versioning patterns.

### 3.1 OHLC input-contract decision

**Decision: introduce a dedicated deterministic analysis input contract. Do not extend `HistoricalOhlcContext` as the long-term domain contract.**

`HistoricalOhlcContext` is a generic context section and currently represents one interval without candle-quality metadata. The deterministic engine needs role-specific series, immutable source identity, closure state, synthetic status, gap information, and an assessment cut-off. Extending the generic payload would couple future capabilities to this use case and still leave ambiguity about which fields are required for deterministic analysis.

The adapter may temporarily map from `HistoricalOhlcContext` only if it can prove the missing metadata by an explicit source contract. Otherwise it must return an invalid assessment rather than infer it.

The dedicated logical input is:

```text
TrendContextAssessmentInput
  marketId
  symbol / provider identity when available
  assessmentAt
  cutOffAt
  profileId and profileVersion
  roleSeries[BIAS, SETUP, TRIGGER]
  sourceReferences per role
  freshness metadata per role
  input fingerprint
```

Each candle in `roleSeries` must contain:

```text
interval, openTime, closeTime, open, high, low, close, volume
closed, synthetic, sourceId, sourceOccurredAt, fetchedAt
```

The engine remains pure: no network, persistence, broker, account, Risk, or LLM access.

### 3.2 Synthetic-candle decision

**Decision: synthetic candles may not participate in any V1 derived calculation.**

They may be retained in the input for evidence and gap reporting, but are excluded from:

| Calculation | Synthetic-candle policy |
| --- | --- |
| EMA | Exclude; if minimum history then fails, EMA is unavailable |
| ATR | Exclude; if minimum history then fails, ATR is unavailable |
| Swing high/low | Never allowed |
| HH/HL/LH/LL | Never allowed indirectly because swings exclude them |
| Structural break | Never allowed indirectly |
| Phase/pullback/extension | Never allowed as direct evidence; a gap finding remains |
| Multi-timeframe alignment | A role containing synthetic candles may not be eligible for an attractive result |

This is conservative and avoids treating normalization artifacts as market behavior. A gap does not automatically make the whole input malformed, but if it affects required structural history or prevents minimum calculations, the result becomes `NO_SETUP` or `INVALID_ASSESSMENT` according to the exclusion matrix.

## 4. V1 Scope Freeze

`REQUIRED` means the assessment cannot produce a directional attention result without it. `MINIMAL` means a bounded supporting or descriptive form is retained. `DEFERRED` means no V1 field or conclusion is produced; later work must define it independently.

| Concept | V1 status | Frozen scope |
| --- | --- | --- |
| Candle validation | REQUIRED | Exact OHLC, ordering, duplicate, closure, cut-off, and gap rules |
| Provenance | REQUIRED | Source identity, range, timestamps, digest, profile/rule versions |
| Timeframe profile | REQUIRED | Versioned BIAS/SETUP/optional TRIGGER roles |
| Swing detection | REQUIRED | Strict N-radius pivots with deterministic suppression |
| HH / HL / LH / LL | REQUIRED | Strict comparison of latest two same-type retained swings |
| Structure | REQUIRED | Directional structure or `NO_DIRECTIONAL_STRUCTURE` |
| Structural break | REQUIRED | One closed close beyond protected level, zero tolerance |
| Transition | REQUIRED | State machine with break, opposite structure, reclaim, and persistence |
| Range | DEFERRED | No categorical RANGE detection in V1; expose no-directional structure |
| Direction | REQUIRED | `UP`, `DOWN`, `NEUTRAL`, `UNKNOWN` |
| Regime | REQUIRED | `TRENDING`, `TRANSITIONING`, `NON_DIRECTIONAL`, `UNKNOWN` |
| EMA | MINIMAL | One EMA, exact formula, normalized slope, supporting evidence only |
| ATR | MINIMAL | Wilder ATR and baseline-relative raw ratio |
| Volatility classification | DEFERRED | No LOW/NORMAL/HIGH categorical state; expose ATR and abnormal flag |
| Momentum | DEFERRED | No categorical momentum model in V1 |
| Strength | DEFERRED | No aggregate strong/moderate/weak label or score |
| Pullback | REQUIRED | Minimal confirmed-structure pullback state, mirrored by direction |
| Extension | REQUIRED | EMA/ATR distance threshold as conservative location exclusion |
| Phase | MINIMAL | `DIRECTIONAL`, `PULLBACK`, `EXTENDED`, `TRANSITION`, `UNDETERMINED` |
| Structural areas | MINIMAL | Individual confirmed structural levels only; clustering deferred |
| Invalidation | REQUIRED | Candidate condition against protected setup swing |
| Multi-timeframe alignment | REQUIRED | Explicit BIAS/SETUP/TRIGGER matrix |
| Contradictions | REQUIRED | Typed, non-averaged findings |
| Hard exclusions | REQUIRED | Explicit validity/result matrix |
| Conservative outcome | REQUIRED | Separate factual context and attention outcome |
| Freshness | REQUIRED | Role-specific interval-based validity |
| Persistence evidence | REQUIRED | Immutable input/rule/provenance references |

## 5. Common Numeric and Ordering Conventions

1. Prices, ATR values, distances, and thresholds use decimal arithmetic, not binary floating-point comparison.
2. Candle order is ascending by `(openTime, closeTime, sourceId)` only after duplicate validation. `sourceId` is not a tie-breaker for conflicting duplicate candles; conflicting duplicates are invalid.
3. All comparisons in V1 are strict unless a rule explicitly says otherwise.
4. V1 comparison tolerance is zero price units. Tick-size and ATR-relative comparison tolerances are deferred because the current domain contract does not establish a universal tick model.
5. A candle is eligible only when `closed = true`, `closeTime <= cutOffAt`, and `closeTime > 0`.
6. A candle must satisfy `high >= max(open, close)`, `low <= min(open, close)`, `high >= low`, and all prices must be finite and positive. Volume may be absent for calculations that do not use it.
7. An open candle is excluded from calculations, not converted to a closed candle.
8. Results are immutable for a given input fingerprint, profile version, rule version, and cut-off.

## 6. Timeframe Profile

The profile assigns semantic roles:

| Role | Meaning | V1 default |
| --- | --- | --- |
| `BIAS` | Broad swing structure and protected direction | 4h |
| `SETUP` | Pullback, extension, and setup structure | 1h |
| `TRIGGER` | Recent lower-timeframe confirmation, optional | 15m |

The role intervals must be distinct and ordered from coarser to finer. A profile with duplicate intervals or `SETUP` coarser than `BIAS` is invalid. `TRIGGER` may be absent only when `triggerRequired = false`.

The BIAS and SETUP roles are always required. The default profile makes TRIGGER optional so missing trigger data does not make the market-data assessment technically invalid; it limits the attention outcome to `WATCH`.

## 7. Candle Validation and Provenance Rules

### 7.1 Role-series validation

For each role, validate in this order:

1. Role and interval match the profile.
2. Every candle has valid OHLC and timestamps.
3. No candle is after `cutOffAt`.
4. No two candles share the same `(interval, openTime)`.
5. `openTime < closeTime` and adjacent real candles have the expected interval boundary unless a gap is recorded.
6. Only closed, non-synthetic candles enter the calculation series.
7. The calculation series is ascending and has the profile minimum history.

Failure behavior:

- malformed OHLC, future candle, conflicting duplicate, or impossible timestamp: `INVALID_ASSESSMENT`;
- missing interval or synthetic gap: valid data-quality finding, but `NO_SETUP` if it affects required structural/calculation evidence;
- open candles only: `NO_SETUP` when no eligible closed series remains;
- insufficient history: `NO_SETUP` with `INSUFFICIENT_HISTORY`.

### 7.2 Evidence and provenance

Every rule output records:

- market and role/interval;
- input first/last candle and exact evidence window;
- accepted closed-candle count;
- excluded open/synthetic/gap/duplicate findings;
- source/provider IDs and source/fetch timestamps;
- assessment cut-off;
- profile ID/version;
- rule ID/version;
- input fingerprint.

`ContextProvenance` remains useful at section level, but the dedicated input must preserve candle-level metadata for audit-grade structural conclusions.

## 8. Exact Swing Detection

### 8.1 Pivot candidates

For radius `N >= 1`, a closed, real candle at index `i` is a swing-high candidate if:

```text
high[i] > high[j] for every j in [i-N, i-1] and [i+1, i+N]
```

It is a swing-low candidate if:

```text
low[i] < low[j] for every j in [i-N, i-1] and [i+1, i+N]
```

The full window must exist and contain only eligible real candles. Equal highs prevent both candles from being swing highs; equal lows prevent both from being swing lows. A candle may qualify as both a swing high and swing low when both strict predicates hold.

The pivot candle is the candidate candle. Its confirmation timestamp is `closeTime[i+N]`, the close time of the final right-side candle. It is usable only when that timestamp is at or before the assessment cut-off.

### 8.2 Minimum separation and overlapping pivots

`minimumSeparationBars` applies only to candidates of the same type. Process candidates by pivot time ascending. If two same-type candidates are closer than the configured separation, retain exactly one:

- for swing highs, retain the candidate with the greater high;
- for swing lows, retain the candidate with the lower low;
- if prices are equal, retain the earlier pivot;
- if candidate prices are incomparable due to invalid data, the role is invalid.

No “greater excursion” formula is used. Cross-type candidates are not suppressed; the same candle may retain both types.

### 8.3 Required evidence

Directional structure requires at least two retained swing highs and two retained swing lows. Fewer than two of either type produces `UNKNOWN` direction and `NO_DIRECTIONAL_STRUCTURE`, not a guessed trend.

### 8.4 Swing evidence

Every retained swing records its pivot candle, confirmation candle, price, type, role, full `[i-N, i+N]` evidence window, suppression status of nearby candidates, and `SWING_HIGH_V1` or `SWING_LOW_V1` rule version.

## 9. HH / HL / LH / LL and Structure

### 9.1 Comparable swings

For each type, the comparable pair is the latest two retained swings of that type by pivot time. No cross-type or non-adjacent selection is allowed. The latest high is compared to the immediately previous retained high; the latest low is compared to the immediately previous retained low.

Relations are strict:

```text
latestHigh > previousHigh => HH
latestHigh < previousHigh => LH
latestHigh = previousHigh => EQ_HIGH

latestLow > previousLow => HL
latestLow < previousLow => LL
latestLow = previousLow => EQ_LOW
```

Equal relations do not count toward directional structure. No tolerance is applied.

### 9.2 Directional structure

`STRUCTURE_UP_V1` is true when the latest comparable high relation is `HH`, the latest comparable low relation is `HL`, and no confirmed bearish break is active.

`STRUCTURE_DOWN_V1` is true when the latest comparable high relation is `LH`, the latest comparable low relation is `LL`, and no confirmed bullish break is active.

If both are false, the role is `NO_DIRECTIONAL_STRUCTURE`. A malformed alternating sequence does not receive a special interpretation; the latest same-type pairs remain factual evidence, while the role stays non-directional unless both required relations agree.

Role outputs:

```text
direction: UP | DOWN | NEUTRAL | UNKNOWN
regime: TRENDING | TRANSITIONING | NON_DIRECTIONAL | UNKNOWN
```

`NEUTRAL + NON_DIRECTIONAL` is not a categorical RANGE claim. Rich range detection is deferred.

## 10. Structural Break and Transition State Machine

### 10.1 Protected structural levels

For an upward role, `protectedLow` is the latest retained swing low whose pivot time is before the pivot time of the latest retained `HH`. If no such low exists, the upward structure is not break-eligible.

For a downward role, `protectedHigh` is the latest retained swing high whose pivot time is before the pivot time of the latest retained `LL`. If no such high exists, the downward structure is not break-eligible.

The protected level is a price, not an area. Tick/ATR tolerances are not applied.

### 10.2 Break definitions

For an active `UP` role:

- `CONFIRMED_BEARISH_BREAK`: an eligible closed candle after the protected-low pivot has `close < protectedLow`.
- `UNCONFIRMED_BEARISH_BREAK`: a candle has `low < protectedLow` but `close >= protectedLow`.
- `FAILED_BREAK`: a confirmed bearish break is followed within `reclaimWindowBars` by an eligible close `>= protectedLow`.

For an active `DOWN` role, mirror the rules:

- `CONFIRMED_BULLISH_BREAK`: `close > protectedHigh`.
- `UNCONFIRMED_BULLISH_BREAK`: `high > protectedHigh` but `close <= protectedHigh`.
- `FAILED_BREAK`: a confirmed bullish break is followed within `reclaimWindowBars` by a close `<= protectedHigh`.

The default confirmation is one closed candle. The broken candle itself is the confirmation candle. No wick confirms a break. No tolerance is applied.

### 10.3 Transition

`TRANSITION_V1` is a state machine:

```text
UPTREND + CONFIRMED_BEARISH_BREAK -> TRANSITION
DOWNTREND + CONFIRMED_BULLISH_BREAK -> TRANSITION

TRANSITION + opposite confirmed structure -> opposite TRENDING state
TRANSITION + reclaim within reclaimWindowBars -> previous state with FAILED_BREAK finding
TRANSITION + neither event -> TRANSITIONING / insufficient resolution
```

Opposite structure requires the same two-comparable-swing rule as ordinary structure, formed after the break confirmation candle. A confirmed break never immediately creates the opposite trend. A failed break reclaim returns the prior factual direction but forces `NO_SETUP` attention for the current assessment. Transition persistence ends only on the opposite structure or reclaim; otherwise it remains `TRANSITIONING`.

## 11. Pullback, Phase, and Extension

### 11.1 Minimal phase model

V1 retains only:

```text
DIRECTIONAL
PULLBACK
EXTENDED
TRANSITION
UNDETERMINED
```

`BASE_OR_RANGE`, `BREAKOUT_ATTEMPT`, `EMERGING`, `WEAKENING`, and `CONTINUATION` are deferred labels.

### 11.2 Pullback

`PULLBACK_UP_V1` requires all of the following on the SETUP role:

1. The last resolved SETUP state is `UPTREND`.
2. The latest retained swing event is a swing high, or no later confirmed swing low exists.
3. At least `pullbackMinimumBars` eligible closed candles exist after that high pivot.
4. The latest close is lower than the close of the preceding eligible candle.
5. The latest close is lower than the close at the start of the post-high window.
6. No confirmed bearish break has occurred.

`PULLBACK_DOWN_V1` mirrors the rule with higher closes after the latest swing low and no confirmed bullish break.

V1 does not classify `DEEP_PULLBACK` or `STRUCTURAL_DETERIORATION`. A break produces `TRANSITION`, not a deeper pullback label. Pullback does not require an EMA cross because that would add a lagging subjective boundary.

### 11.3 Extension

`EXTENSION_V1` is true only when EMA and ATR are available and:

```text
abs(latestClose - latestEMA) / latestATR >= extensionAtrMultiple
```

and the latest close is on the same side of EMA as the role direction. This means the current location is poor for conservative attention; it does not predict reversal. Extension is a hard `NO_SETUP` attention condition, not an invalid input.

Displacement since impulse and distance to clustered areas are deferred.

## 12. EMA and ATR Minimal Evidence

### 12.1 EMA

EMA is supporting evidence only. For period `P`, seed with the arithmetic mean of the first `P` eligible real closed closes, then use:

```text
k = 2 / (P + 1)
EMA[t] = close[t] * k + EMA[t-1] * (1-k)
```

No synthetic candles are included. The profile requires `P + emaWarmupBars` eligible candles. Slope uses:

```text
slopeNormalized = (EMA[t] - EMA[t-L]) / ATR[t]
```

where `L` is the profile lookback. Classification is:

```text
RISING if slopeNormalized > +emaSlopeThreshold
FALLING if slopeNormalized < -emaSlopeThreshold
FLAT otherwise
```

Period, warmup, lookback, and threshold are paper-validation hypotheses. EMA may strengthen or weaken evidence, identify extension, and be displayed. EMA may never create direction, regime, a swing, a break, a pullback, an opportunity, or Risk authority.

### 12.2 ATR

For period `P`, true range is:

```text
TR[0] = high[0] - low[0]
TR[t] = max(high[t]-low[t], abs(high[t]-close[t-1]), abs(low[t]-close[t-1]))
```

Seed ATR with the arithmetic mean of the first `P` true ranges, then use Wilder smoothing:

```text
ATR[t] = (ATR[t-1] * (P-1) + TR[t]) / P
```

The baseline is the arithmetic mean of the latest `B` available ATR values. `atrRatio = ATR[t] / baseline`. If ATR or baseline is unavailable, volatility evidence is unknown and extension cannot be proved false; the attention result cannot be attractive.

V1 exposes ATR, baseline, ratio, and `ABNORMAL_VOLATILITY` when `atrRatio >= abnormalAtrRatioThreshold`. LOW/NORMAL/HIGH/EXPANDING categorical volatility is deferred. ATR never determines position size, stop placement, Risk, or direction.

## 13. Structural Levels and Invalidation

### 13.1 Structural levels

V1 exposes individual confirmed levels only:

- every retained swing high is a resistance-level candidate;
- every retained swing low is a support-level candidate;
- the active protected swing is marked separately;
- each level retains type, price, pivot time, confirmation time, role, and evidence window.

ATR-normalized clustering, center/width calculation, reaction counts, merging, and expiry of zones are deferred. This avoids pretending that an approximate chart zone is an objective fact.

### 13.2 Candidate invalidation

`INVALIDATION_UP_V1` is:

```text
SETUP has a valid upward thesis
AND an eligible SETUP candle closes strictly below protectedSetupLow
```

`INVALIDATION_DOWN_V1` is the mirror:

```text
SETUP has a valid downward thesis
AND an eligible SETUP candle closes strictly above protectedSetupHigh
```

The protected setup level uses the same protected-level algorithm as Section 10. The event is recorded with candle/time/price/level evidence. It is an analytical thesis invalidation, not a stop loss, order, or account decision.

## 14. Multi-Timeframe Alignment Matrix

BIAS has priority over SETUP; SETUP has priority over TRIGGER. TRIGGER can confirm or weaken but cannot reverse BIAS.

| BIAS | SETUP | TRIGGER | Alignment | Contradiction / result |
| --- | --- | --- | --- | --- |
| UP | UP | supportive/neutral | `ALIGNED_UP` | None; eligible for attractive only if all gates pass |
| UP | PULLBACK | supportive/neutral | `PULLBACK_WITHIN_UP_BIAS` | None; attractive candidate if not extended |
| UP | TRANSITION | any | `BIAS_TRANSITION` | Contradiction; `NO_SETUP` |
| UP | DOWN | any | `CONFLICTING` | BIAS/SETUP contradiction; `NO_SETUP` |
| DOWN | DOWN | supportive/neutral | `ALIGNED_DOWN` | None; eligible for attractive only if all gates pass |
| DOWN | PULLBACK | supportive/neutral | `PULLBACK_WITHIN_DOWN_BIAS` | None; attractive candidate if not extended |
| DOWN | TRANSITION | any | `BIAS_TRANSITION` | Contradiction; `NO_SETUP` |
| DOWN | UP | any | `CONFLICTING` | BIAS/SETUP contradiction; `NO_SETUP` |
| UP/DOWN | NON_DIRECTIONAL | any | `INSUFFICIENT_DIRECTION` | No directional structure; `NO_SETUP` |
| UNKNOWN | any | any | `INSUFFICIENT_BIAS` | Required role insufficient; `NO_SETUP` |
| any | any | supportive | unchanged | Supportive finding only |
| any | any | contradictory | `TRIGGER_CONTRADICTION` | Does not reverse BIAS; `NO_SETUP` or `WATCH` per outcome gates |
| any | any | unavailable and optional | `TRIGGER_UNAVAILABLE` | Context may remain `WATCH`, never attractive |

The TRIGGER is supportive only when its direction equals BIAS direction or it is `NEUTRAL`. It is contradictory when its confirmed direction is opposite BIAS or it has an active transition against BIAS. An unconfirmed wick is a warning, not a contradiction.

## 15. Factual Context and Attention Outcome

V1 does not use one enum for both facts and trader guidance.

### 15.1 Factual fields

```text
direction: UP | DOWN | NEUTRAL | UNKNOWN
regime: TRENDING | TRANSITIONING | NON_DIRECTIONAL | UNKNOWN
phase: DIRECTIONAL | PULLBACK | EXTENDED | TRANSITION | UNDETERMINED
alignment: explicit matrix state
```

### 15.2 Attention outcome

```text
UNKNOWN
NO_SETUP
WATCH
CONTEXTUALLY_ATTRACTIVE
CONTEXTUALLY_DANGEROUS
```

Exact derivation order:

1. Invalid input or unsafe calculation => `UNKNOWN`.
2. Required role stale/missing/insufficient, no directional structure, or range-like non-directional evidence => `NO_SETUP`.
3. Transition, BIAS/SETUP conflict, trigger contradiction, failed reclaim, abnormal volatility, or extension => `CONTEXTUALLY_DANGEROUS` if data is otherwise valid and the condition is materially adverse; a pure absence of evidence remains `NO_SETUP`.
4. Valid aligned direction or controlled pullback, complete EMA/ATR evidence, no exclusions, no extension, and supportive/neutral required findings => `CONTEXTUALLY_ATTRACTIVE`.
5. Optional trigger unavailable, or a non-blocking warning exists => `WATCH`.

`CONTEXTUALLY_ATTRACTIVE` means worth human review only. It does not mean BUY, SELL, approved, rejected, authorized, profitable, or high probability. A context assessment never directly becomes a TradingOpportunity.

## 16. Hard Exclusion Matrix

| Condition | Data validity | Finding | Attention effect |
| --- | --- | --- | --- |
| Market non-tradable | Valid market fact | `MARKET_NOT_TRADABLE` | `NO_SETUP` |
| Required timeframe missing | Invalid for current assessment | `REQUIRED_ROLE_MISSING` | `UNKNOWN` |
| Required timeframe stale | Valid but unusable current context | `ROLE_STALE` | `NO_SETUP` |
| Insufficient candles | Valid but insufficient | `INSUFFICIENT_HISTORY` | `NO_SETUP` |
| Open candles only | Valid source, no closed evidence | `NO_CLOSED_CANDLES` | `NO_SETUP` |
| Synthetic candle in required history | Valid source with gap | `SYNTHETIC_DATA_EXCLUDED` | `NO_SETUP` when required calculation is affected |
| Missing interval/gap | Valid source with incomplete history | `GAP_IN_HISTORY` | `NO_SETUP` when structural window is affected |
| Duplicate/conflicting candle | Invalid input | `DUPLICATE_CANDLE` | `UNKNOWN` |
| Invalid OHLC/timestamp/future candle | Invalid input | `INVALID_CANDLE` | `UNKNOWN` |
| Insufficient confirmed swings | Valid but inconclusive | `INSUFFICIENT_SWINGS` | `NO_SETUP` |
| BIAS/SETUP conflict | Valid market condition | `TIMEFRAME_CONTRADICTION` | `NO_SETUP` |
| Non-directional structure | Valid market condition | `NO_DIRECTIONAL_STRUCTURE` | `NO_SETUP` |
| Transition | Valid market condition | `TRANSITION_ACTIVE` | `CONTEXTUALLY_DANGEROUS` |
| Failed break/reclaim | Valid market condition | `FAILED_BREAK_RECLAIM` | `CONTEXTUALLY_DANGEROUS` |
| Excessive extension | Valid market condition | `EXTENDED_LOCATION` | `CONTEXTUALLY_DANGEROUS` |
| Abnormal ATR ratio | Valid market condition | `ABNORMAL_VOLATILITY` | `CONTEXTUALLY_DANGEROUS` |
| Optional trigger unavailable | Valid partial context | `TRIGGER_UNAVAILABLE` | At most `WATCH` |
| EMA contradiction | Valid supporting conflict | `EMA_CONTRADICTION` | At most `WATCH`; never reverses structure |

Invalid data and legitimate market conditions remain separate. `UNKNOWN` denotes unsafe or uncomputable assessment; `NO_SETUP` denotes usable data with no selective context; `CONTEXTUALLY_DANGEROUS` denotes usable but materially adverse context.

## 17. Freshness Model

Freshness is role-specific. For role interval duration `D`, the default maximum age is `2 * D` from the latest accepted closed candle close to `assessmentAt`:

```text
fresh(role) = latestClosedClose <= assessmentAt
              AND assessmentAt - latestClosedClose <= 2D
```

The multiplier `2` is a `PAPER VALIDATION HYPOTHESIS`. A candle close in the future is invalid. A source fetch delay is recorded but does not change the formula.

Freshness behavior:

- BIAS stale: `NO_SETUP`, `INSUFFICIENT_BIAS`.
- SETUP stale: `NO_SETUP`, `ROLE_STALE`.
- TRIGGER stale and optional: treat as unavailable and cap at `WATCH`.
- BIAS fresh + SETUP stale + TRIGGER fresh: no partial attractive result.
- A newly accepted closed candle changes the input fingerprint and starts a new assessment; prior evidence is not mutated.
- Market schedules are not inferred by this engine. If Market Data reports the market unavailable/non-tradable, the exclusion matrix applies.

## 18. Default Profile: `CONSERVATIVE_SWING_V1`

Every value below is explicitly provisional unless it is part of an algorithm definition.

| Parameter | Value | Status |
| --- | --- | --- |
| BIAS interval | 4h | PAPER VALIDATION HYPOTHESIS |
| SETUP interval | 1h | PAPER VALIDATION HYPOTHESIS |
| TRIGGER interval | 15m | PAPER VALIDATION HYPOTHESIS |
| triggerRequired | false | PAPER VALIDATION HYPOTHESIS |
| pivotRadius | 2 candles each side | PAPER VALIDATION HYPOTHESIS |
| minimumSeparationBars | 2 same-type candidate bars | PAPER VALIDATION HYPOTHESIS |
| minimum confirmed highs/lows | 2 of each | PAPER VALIDATION HYPOTHESIS |
| EMA period | 50 | PAPER VALIDATION HYPOTHESIS |
| emaWarmupBars | 10 | PAPER VALIDATION HYPOTHESIS |
| EMA slope lookback | 5 bars | PAPER VALIDATION HYPOTHESIS |
| emaSlopeThreshold | 0.10 ATR per lookback | PAPER VALIDATION HYPOTHESIS |
| ATR period | 14 | PAPER VALIDATION HYPOTHESIS |
| ATR baseline length | 20 ATR values | PAPER VALIDATION HYPOTHESIS |
| abnormalAtrRatioThreshold | 3.0 | PAPER VALIDATION HYPOTHESIS |
| extensionAtrMultiple | 3.0 | PAPER VALIDATION HYPOTHESIS |
| pullbackMinimumBars | 2 | PAPER VALIDATION HYPOTHESIS |
| reclaimWindowBars | 2 | PAPER VALIDATION HYPOTHESIS |
| freshness multiplier | 2 intervals | PAPER VALIDATION HYPOTHESIS |
| gap policy | no synthetic structural evidence | V1 policy |

Minimum history must be the maximum needed by the retained calculations and swing evidence, including the full EMA/ATR warmup and pivot windows. The adapter should request more history than the minimum so early warmup does not consume the entire decision window.

## 19. Executable Rule Catalogue

Each retained rule has one canonical row. `V1` means the formula and behavior are part of this design; profile values remain provisional where marked above.

| Rule ID | Inputs | Preconditions | Algorithm / parameters | Output | Failure or unknown | Evidence | Version |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `CANDLE_VALIDATION_V1` | Role candles, cut-off | Input contract present | Validate OHLC, timestamps, future candles, duplicates, closure, synthetic status, ordering | Valid role series and findings | Malformed/future/conflicting duplicate => invalid | Rejected IDs and reason | V1 |
| `PROVENANCE_V1` | Source metadata and accepted candles | Candle validation | Copy source/provider IDs, times, range, digest, profile/rule versions | Immutable provenance | Missing required source identity => invalid or untraceable finding | Source and evidence window | V1 |
| `PROFILE_VALIDATION_V1` | Profile | Profile supplied | Require BIAS/SETUP, distinct ordered intervals, valid positive parameters | Valid profile | Invalid profile => invalid | Parameter snapshot | V1 |
| `SWING_HIGH_V1` | Closed real candles, `N` | Full left/right window | Strict high greater-than all `2N` neighbors; confirm at `closeTime[i+N]` | Candidate/retained swing high | Insufficient window => no candidate | `[i-N, i+N]` | V1 |
| `SWING_LOW_V1` | Closed real candles, `N` | Full left/right window | Strict low less-than all `2N` neighbors; same suppression rule | Candidate/retained swing low | Insufficient window => no candidate | `[i-N, i+N]` | V1 |
| `SWING_SUPPRESSION_V1` | Same-type candidates | Candidate list valid | Within separation: high keeps greater price, low keeps lower price, equal keeps earlier | Retained swings | Invalid comparison => invalid | Suppressed candidate IDs | V1 |
| `RELATIONS_V1` | Latest two retained highs/lows | At least two each | Strict pair comparison => HH/LH/EQ and HL/LL/EQ | Relation facts | Fewer than two => unknown relation | Both swing refs | V1 |
| `STRUCTURE_UP_V1` | Relations, breaks | HH + HL, no bearish break | Set direction UP and regime TRENDING | Up structure | Missing relation => non-directional | Relation refs | V1 |
| `STRUCTURE_DOWN_V1` | Relations, breaks | LH + LL, no bullish break | Set direction DOWN and regime TRENDING | Down structure | Missing relation => non-directional | Relation refs | V1 |
| `PROTECTED_LEVEL_V1` | Swing sequence and direction | Directional structure | Latest same-type supporting swing before latest HH/LL | Protected high/low | Missing level => break ineligible | Pivot ref and price | V1 |
| `BREAK_CONFIRM_V1` | Closed candles, protected level | Active directional structure | One close strictly beyond protected level; wick alone unconfirmed | Confirmed/unconfirmed break | No eligible candle => none | Break candle and level | V1 |
| `TRANSITION_V1` | Break, later swings, reclaim window | Break event | Confirmed adverse break starts transition; opposite HH/HL or LH/LL resolves; reclaim within 2 bars returns prior direction with failed finding | Transition state | Neither event => transition persists | Break/reclaim/opposite swings | V1 |
| `PULLBACK_UP_V1` | SETUP swings/closes | Prior resolved UP, no break | At least 2 post-high bars, latest close lower than prior and post-high start | PULLBACK phase | Otherwise not pullback | High pivot and close window | V1 |
| `PULLBACK_DOWN_V1` | SETUP swings/closes | Prior resolved DOWN, no break | Mirrored higher-close rule | PULLBACK phase | Otherwise not pullback | Low pivot and close window | V1 |
| `EMA_V1` | Closed real closes | `P + warmup` | SMA seed then recurrence; slope `(EMA[t]-EMA[t-L])/ATR[t]` | EMA, slope, relation | Unknown if history/ATR unavailable | Close and EMA windows | V1 |
| `ATR_V1` | Closed real OHLC | `P + B` history | Wilder TR/ATR; baseline mean of latest B ATRs | ATR, baseline, ratio | Unknown if insufficient | OHLC and ATR window | V1 |
| `EXTENSION_V1` | Close, EMA, ATR, direction | EMA/ATR available | Same-side `abs(close-EMA)/ATR >= 3.0` | Extended boolean | Unknown extension prevents attractive outcome | Close/EMA/ATR refs | V1 |
| `LEVEL_V1` | Retained swings | Swing exists | Expose each high as resistance and low as support; no clustering | Structural levels | No swings => none | Swing ref | V1 |
| `INVALIDATION_UP_V1` | SETUP protected low, closed candle | Up thesis and level | Close strictly below protected setup low | Candidate invalidation | Missing level => unknown/NO_SETUP | Candle and level | V1 |
| `INVALIDATION_DOWN_V1` | SETUP protected high, closed candle | Down thesis and level | Close strictly above protected setup high | Candidate invalidation | Missing level => unknown/NO_SETUP | Candle and level | V1 |
| `MTF_ALIGNMENT_V1` | BIAS/SETUP/TRIGGER states | Required roles valid/fresh | Apply Section 14 matrix in fixed priority order | Alignment and contradiction list | Missing required role => insufficient | Role result refs | V1 |
| `FRESHNESS_V1` | Latest close, interval, assessmentAt | Valid timestamps | Age `<= 2D` per role | Fresh/stale | Stale required => NO_SETUP | Latest close and D | V1 |
| `OUTCOME_V1` | Validity, structure, alignment, EMA/ATR, findings | All prior rules complete | Apply Section 15 ordered derivation | Attention outcome | Unsafe => UNKNOWN | All blocking findings | V1 |
| `OBSERVATION_LINEAGE_V1` | Assessment output and source refs | Output complete | Persist immutable observation link, digest, validity, profile/rule versions | Evidence-ready observation | Missing lineage => not promotable | IDs and digest | V1 |

## 20. Acceptance Scenarios

Notation: `V` = data validity; `S` = swing/structure; `D/R/P` = direction/regime/phase; `E/A` = EMA/ATR evidence; `M` = MTF relation; `C` = contradictions; `X` = exclusions; `O` = attention outcome; `I` = invalidation.

| # | Input condition | Expected result |
| --- | --- | --- |
| 1 | BIAS and SETUP each contain two retained highs/lows with latest `HH+HL`; no breaks; EMA/ATR available; optional trigger supportive | `V=VALID; S=HH+HL; D=UP; R=TRENDING; P=DIRECTIONAL; E/A=available and non-abnormal; M=ALIGNED_UP; C=none; X=none; O=CONTEXTUALLY_ATTRACTIVE; I=SETUP protected low` |
| 2 | BIAS and SETUP each contain latest `LH+LL`; no breaks; EMA/ATR available; trigger supportive | `V=VALID; S=LH+LL; D=DOWN; R=TRENDING; P=DIRECTIONAL; E/A=available; M=ALIGNED_DOWN; C=none; X=none; O=CONTEXTUALLY_ATTRACTIVE; I=SETUP protected high` |
| 3 | Up SETUP, latest event swing high, two declining post-high closes, protected low intact | `V=VALID; S=up structure; D=UP; R=TRENDING; P=PULLBACK; E/A=available; M=PULLBACK_WITHIN_UP_BIAS; C=none; X=none; O=CONTEXTUALLY_ATTRACTIVE; I=protected low` |
| 4 | Down SETUP, latest event swing low, two rising post-low closes, protected high intact | `V=VALID; S=down structure; D=DOWN; R=TRENDING; P=PULLBACK; E/A=available; M=PULLBACK_WITHIN_DOWN_BIAS; C=none; X=none; O=CONTEXTUALLY_ATTRACTIVE; I=protected high` |
| 5 | Up structure, close is same-side of EMA and `abs(close-EMA)/ATR >= 3.0` | `V=VALID; S=up; D=UP; R=TRENDING; P=EXTENDED; E=extension true; A=available; M=aligned; C=none; X=EXTENDED_LOCATION; O=CONTEXTUALLY_DANGEROUS; I=protected low` |
| 6 | Down structure, close is same-side of EMA and distance ratio >= 3.0 | Mirror of #5 with `D=DOWN`, protected high, `O=CONTEXTUALLY_DANGEROUS` |
| 7 | Active up structure, closed candle closes strictly below protected low | `V=VALID; S=prior up plus confirmed bearish break; D=UP; R=TRANSITIONING; P=TRANSITION; E/A=normal if available; M=BIAS_TRANSITION; C=confirmed adverse break; X=TRANSITION_ACTIVE; O=CONTEXTUALLY_DANGEROUS; I=up thesis candidate invalidated` |
| 8 | Active down structure, closed candle closes strictly above protected high | Mirror of #7 with confirmed bullish break and down thesis invalidation |
| 9 | Transition after #7 forms two post-break lower highs/lows satisfying LH+LL | `V=VALID; S=opposite LH+LL; D=DOWN; R=TRENDING; P=DIRECTIONAL; E/A=available; M=opposite resolved structure; C=prior transition recorded; X=none beyond transition history; O=WATCH or ATTRACTIVE only if profile allows newly resolved structure, default WATCH; I=new protected high` |
| 10 | Wick crosses protected level but close remains on original side; no confirmed break | `V=VALID; S=prior structure; D/R unchanged; P=DIRECTIONAL or PULLBACK; E/A unchanged; M=unchanged; C=UNCONFIRMED_BREAK warning; X=none; O=unchanged by break rule; I=not triggered` |
| 11 | Confirmed break followed within two bars by close back at/through broken level | `V=VALID; S=prior structure; D/R=prior direction/TRENDING; P=TRANSITION; M=failed reclaim; C=FAILED_BREAK_RECLAIM; X=failed break; O=CONTEXTUALLY_DANGEROUS; I=failed thesis event recorded` |
| 12 | Repeated bounded reactions but no strict HH+HL or LH+LL sequence | `V=VALID; S=NO_DIRECTIONAL_STRUCTURE; D=NEUTRAL; R=NON_DIRECTIONAL; P=UNDETERMINED; E/A may be available; M=INSUFFICIENT_DIRECTION; C=none; X=NO_DIRECTIONAL_STRUCTURE; O=NO_SETUP; I=none` |
| 13 | Candidate highs or lows have equal neighboring prices | `V=VALID; S=no pivot for equal side; D/R not upgraded; P=UNDETERMINED; E/A independent; M=insufficient if relations fail; C=none; X=INSUFFICIENT_SWINGS; O=NO_SETUP; I=none` |
| 14 | BIAS UP/TRENDING and SETUP DOWN/TRENDING | `V=VALID; S=both independently valid; D=BIAS UP/SETUP DOWN; R=TRENDING per role; P=SETUP DIRECTIONAL; E/A available; M=CONFLICTING; C=TIMEFRAME_CONTRADICTION; X=conflict; O=NO_SETUP; I=BIAS protected low and SETUP protected high as evidence` |
| 15 | BIAS UP, SETUP controlled pullback, trigger UP/NEUTRAL | `V=VALID; S=valid up plus pullback; D=UP; R=TRENDING; P=PULLBACK; E/A available; M=PULLBACK_WITHIN_UP_BIAS; C=none; X=none; O=CONTEXTUALLY_ATTRACTIVE; I=SETUP protected low` |
| 16 | BIAS UP, SETUP pullback, trigger DOWN/TRANSITIONING | `V=VALID; S=BIAS valid, trigger opposite; D=UP; R=TRENDING/trigger transition; P=PULLBACK; E/A available; M=TRIGGER_CONTRADICTION; C=trigger contradiction; X=trigger adverse; O=CONTEXTUALLY_DANGEROUS; I=SETUP protected low` |
| 17 | BIAS fresh, SETUP latest close older than `2 * setupInterval` | `V=VALID source; S=not promoted; D/R unknown at assessment; P=UNDETERMINED; E/A may exist historically; M=insufficient freshness; C=none; X=ROLE_STALE; O=NO_SETUP; I=none` |
| 18 | BIAS/SETUP valid, optional TRIGGER absent | `V=VALID partial; S/D/R/P valid; E/A available; M=TRIGGER_UNAVAILABLE; C=none; X=none; O=WATCH, never ATTRACTIVE; I=normal protected level` |
| 19 | BIAS missing entirely | `V=INVALID current assessment; S/D/R/P unavailable; E/A irrelevant; M=INSUFFICIENT_BIAS; C=missing role; X=REQUIRED_ROLE_MISSING; O=UNKNOWN; I=none` |
| 20 | Fewer than two retained highs or lows on required role | `V=VALID but insufficient; S=insufficient relations; D=UNKNOWN/NEUTRAL; R=UNKNOWN or NON_DIRECTIONAL; P=UNDETERMINED; E/A may exist; M=INSUFFICIENT_DIRECTION; C=none; X=INSUFFICIENT_SWINGS; O=NO_SETUP; I=none` |
| 21 | Synthetic candle would otherwise be a pivot | `V=VALID source with excluded gap; S=pivot absent; D/R not upgraded; P=UNDETERMINED; E/A excludes synthetic; M=insufficient if affected; C=SYNTHETIC_DATA_EXCLUDED; X=synthetic structural evidence; O=NO_SETUP; I=none` |
| 22 | Gap removes a required candle in a pivot window | `V=VALID source but incomplete; S=no candidate across gap; D/R unknown/non-directional; P=UNDETERMINED; E/A only if independently enough real candles; M=insufficient; C=GAP_IN_HISTORY; X=gap affects structure; O=NO_SETUP; I=none` |
| 23 | ATR/baseline ratio >= 3.0 with otherwise aligned structure | `V=VALID; S/D/R/P otherwise directional; E=available; A=ABNORMAL_VOLATILITY; M=aligned; C=none; X=ABNORMAL_VOLATILITY; O=CONTEXTUALLY_DANGEROUS; I=normal protected level` |
| 24 | Same input bytes, profile, cut-off, and rule versions replayed twice | Both outputs have identical swings, state, evidence IDs/references, exclusions, outcome, invalidation, and input fingerprint; no current-time drift is allowed |

## 21. Implementation-Readiness Gate

| Concept | V1 status | Executable rule? | Blocker |
| --- | --- | --- | --- |
| Candle validation | REQUIRED | Yes: `CANDLE_VALIDATION_V1` | None |
| Timeframe profile | REQUIRED | Yes: `PROFILE_VALIDATION_V1` and Section 6 | None |
| Swing high | REQUIRED | Yes: `SWING_HIGH_V1` | None |
| Swing low | REQUIRED | Yes: `SWING_LOW_V1` | None |
| HH/HL/LH/LL | REQUIRED | Yes: `RELATIONS_V1` | None |
| Bullish structure | REQUIRED | Yes: `STRUCTURE_UP_V1` | None |
| Bearish structure | REQUIRED | Yes: `STRUCTURE_DOWN_V1` | None |
| Structural break | REQUIRED | Yes: `BREAK_CONFIRM_V1` | None |
| Transition | REQUIRED | Yes: `TRANSITION_V1` | None |
| Range | DEFERRED | No, intentionally | Deferred; use `NO_DIRECTIONAL_STRUCTURE` |
| Direction | REQUIRED | Yes: structure rules | None |
| Regime | REQUIRED | Yes: structure/transition rules | None |
| EMA | MINIMAL | Yes: `EMA_V1` | Threshold is paper hypothesis |
| ATR | MINIMAL | Yes: `ATR_V1` | Threshold is paper hypothesis |
| Volatility | DEFERRED | No categorical state; raw ratio exists | Deferred |
| Momentum | DEFERRED | No | Deferred |
| Strength | DEFERRED | No | Deferred |
| Pullback | REQUIRED | Yes: `PULLBACK_UP/DOWN_V1` | None |
| Extension | REQUIRED | Yes: `EXTENSION_V1` | Threshold is paper hypothesis |
| Phase | MINIMAL | Yes: finite phase derivation | None |
| Structural areas | MINIMAL | Yes for individual levels; clustering deferred | None |
| Invalidation | REQUIRED | Yes: `INVALIDATION_UP/DOWN_V1` | None |
| MTF alignment | REQUIRED | Yes: Section 14 matrix | None |
| Contradictions | REQUIRED | Yes: typed findings and matrix | None |
| Hard exclusions | REQUIRED | Yes: Section 16 matrix | None |
| Attention outcome | REQUIRED | Yes: `OUTCOME_V1` | None |
| Freshness | REQUIRED | Yes: `FRESHNESS_V1` | Multiplier is paper hypothesis |
| Persistence evidence | REQUIRED | Yes: `PROVENANCE_V1` and `OBSERVATION_LINEAGE_V1` | Storage implementation remains future work |

**IMPLEMENTATION READY = YES** for a future implementation Story, subject to human approval of this design and explicit paper validation of provisional profile thresholds. No unresolved deterministic rule remains in the retained scope.

## 22. ADR Assessment

Do not create an ADR in this task.

### Intelligence Evidence and Authority ADR

This ADR remains justified. It should formalize durable architectural boundaries:

- source fact ownership versus deterministic evidence versus interpretation;
- `IntelligenceContext`, `IntelligenceObservation`, `MarketContextAssessment`, `StrategyEvaluation`, `StrategyMatch`, `TradingOpportunity`, `TradePlan`, and Risk relationships;
- immutable provenance, validity, and evidence-reference requirements;
- prohibition on ML/LLM override of deterministic exclusions, Risk, or execution;
- human review and execution authority.

This ADR should not contain EMA/ATR formulas, pivot parameters, or paper thresholds.

### Dedicated Deterministic Trend Context ADR

**Not justified yet.** The retained algorithms and provisional profile values belong in the domain specification, tests, and versioned profile. A dedicated ADR becomes justified only if implementation requires a durable architectural choice that cannot be expressed by the existing Market Intelligence/Observation/Strategy boundaries, such as a separate persistence boundary, service boundary, or cross-domain authority rule.

## 23. Next Implementation Boundary

The next Story may define:

1. The dedicated `TrendContextAssessmentInput` mapping from Market Data.
2. Pure Java calculators matching this catalogue.
3. Deterministic unit and property tests for Sections 20 and 24.
4. Typed assessment output plus generic `IntelligenceObservation` lineage.
5. Separate StrategyEvaluation/StrategyMatch integration, without automatic opportunity creation.

It must not add deferred range clustering, momentum classification, strength scoring, ML, agent reasoning, Risk duplication, or execution behavior.

## 24. End Report

**DESIGN HARDENING**
COMPLETE

**V1 SCOPE FROZEN**
YES

**IMPLEMENTATION READY**
YES, for the pure deterministic domain and a future implementation Story

**PIPELINE CORRECTED**
YES

**OHLC INPUT CONTRACT**
Introduce a dedicated `TrendContextAssessmentInput`; do not use generic `HistoricalOhlcContext` as the long-term engine contract.

**SYNTHETIC CANDLE POLICY**
Exclude synthetic candles from EMA, ATR, swings, structure, breaks, phase, and alignment; retain them only as gap/provenance evidence.

**SWING RULE**
Strict N-radius high/low pivots, full real closed window, confirmation at the right-window close, same-type minimum-separation suppression by extreme price then earlier time, cross-type coexistence allowed, two highs and two lows required for direction.

**STRUCTURE RULE**
Latest two same-type retained swings produce strict HH/HL/LH/LL relations. Only HH+HL or LH+LL creates directional structure; otherwise the role is neutral/unknown and no range claim is made.

**BREAK / TRANSITION RULE**
One eligible closed close strictly beyond the protected swing level confirms a break. Wick-only breaches are unconfirmed. Adverse breaks start transition; opposite confirmed structure resolves it; reclaim within two bars returns the prior direction with a failed-break finding; no instant opposite trend.

**RANGE**
DEFERRED

**EMA**
MINIMAL

**ATR**
MINIMAL

**MOMENTUM**
DEFERRED

**STRENGTH**
DEFERRED

**PHASE**
MINIMAL

**PULLBACK**
REQUIRED

**EXTENSION**
REQUIRED

**STRUCTURAL AREAS**
MINIMAL as individual confirmed levels; clustered areas deferred

**MULTI_TIMEFRAME**
BIAS dominates SETUP, SETUP dominates optional TRIGGER. Explicit matrix states alignment, pullback, transition, conflict, insufficient direction, and trigger availability. TRIGGER never reverses BIAS.

**OUTCOME MODEL**
Separate factual direction/regime/phase/alignment from attention outcome: `UNKNOWN`, `NO_SETUP`, `WATCH`, `CONTEXTUALLY_ATTRACTIVE`, `CONTEXTUALLY_DANGEROUS`. No opportunity or authorization semantics.

**HARD EXCLUSION MODEL**
Malformed input produces `UNKNOWN`; missing/stale/insufficient evidence produces `NO_SETUP`; adverse but valid market conditions such as transition, contradiction, extension, and abnormal ATR produce `CONTEXTUALLY_DANGEROUS`; optional trigger absence caps at `WATCH`.

**FRESHNESS MODEL**
Per role, latest accepted close must be no older than two interval durations at assessment time; BIAS/SETUP staleness blocks attention, optional TRIGGER staleness becomes unavailable.

**DEFAULT PROFILE**
`CONSERVATIVE_SWING_V1`: 4h/1h/15m BIAS/SETUP/TRIGGER, optional trigger, pivot radius 2, separation 2, EMA 50, ATR 14, 20-value ATR baseline, 3 ATR extension/abnormal thresholds, two-bar pullback/reclaim windows. Numerical thresholds are paper-validation hypotheses.

**EXECUTABLE RULE CATALOGUE**
COMPLETE

**ACCEPTANCE SCENARIOS**
COMPLETE

**ADR REQUIRED**
YES for the broader Intelligence Evidence and Authority ADR; NO dedicated Trend Context ADR at this stage.

**PROPOSED ADR ACTION**
Create the Intelligence Evidence and Authority ADR before or alongside implementation to formalize authority and lineage boundaries. Keep algorithms and thresholds in this design, versioned profile, and tests.

**BLOCKERS**
NONE for the pure domain design. Human approval and paper validation of provisional thresholds remain governance/validation prerequisites, not unresolved rule ambiguities.

**RECOMMENDED NEXT STEP**
Human review of this hardened design, then define the implementation Story for the dedicated input contract, pure calculators, tests, and observation lineage.

**NEXT STORY READY TO DEFINE**
YES after human approval of this document and the authority-ADR scope.

**FILES CREATED**
None

**FILES MODIFIED**
- `docs/investigations/deterministic-trend-context-domain-design-2026-09-23.md`

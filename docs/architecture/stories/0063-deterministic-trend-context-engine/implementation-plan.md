# Story 0063 - Implementation Plan

## Plan Status

Planning complete; implementation is now present and remains subject to human
review. This plan covers only the pure deterministic Trend Context
engine and its domain tests. It does not authorize Story `0064` orchestration,
observation persistence, production capability registration, or Story `0065`
strategy evaluation.

## Implementation Constraints

- Use `TrendContextAssessmentInput` as the only engine input.
- Keep all production classes in the Market Intelligence domain package and free
  from Spring, HTTP, Feign, repositories, persistence, accounts, Risk, TradePlan,
  brokers, ML, LLMs, agents, and strategy/opportunity types.
- Use `calculationReadyCandles()` for calculations and retain source findings for
  explanations. Never recalculate Story `0062` validation or include excluded
  candles as market behavior.
- Bound every calculation by `cutOffAt` and use only closed, non-synthetic
  candles.
- Preserve separate factual state and conservative attention.
- Use exact strict `BigDecimal.compareTo` for price semantics and explicit
  `MathContext.DECIMAL128` for arithmetic requiring division.
- Do not create a generic rules framework or a new testing dependency.
- Do not modify existing production code or tests outside the approved Story
  `0063` implementation scope.

## Proposed Package

`market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/trendcontext/`

The package already owns the input boundary and is the smallest coherent home
for the output model and pure calculators. New public types should be limited to
the result contract and types needed by Story `0064` lineage. Intermediate
candidate/state objects should be package-private where possible.

## Step 1 - Assessment and Result Domain

### Goal

Define immutable result types before implementing calculations so every rule has
an explicit, evidence-linked destination.

### Types

- `TrendContextEngine` with `assess(TrendContextAssessmentInput)`.
- `TrendContextAssessment` root result.
- `TrendContextTimeframeAssessment` per-role result.
- `TrendDirection`, `TrendRegime`, `TrendPhase`, `TrendAttention`.
- `TrendTimeframeAlignment`.
- `TrendContextEvidenceReference`.
- `TrendContextFinding`, `TrendContextContradiction`,
  `TrendContextExclusion`.
- Result records for swings, relations, structure, protected levels, breaks,
  levels, pullback, extension, EMA, ATR, and invalidation.

### Rules

- All collections are defensive immutable copies.
- All result ordering is deterministic.
- No generated-at wall-clock field, random ID, capability execution ID, or
  confidence/score field is introduced.
- Attention is not inferred by enum conversion from direction.

### Tests

- constructor invariants and immutable collection behavior;
- factual enum and attention separation;
- evidence reference always carries role, rule/profile version, cut-off, and
  input fingerprint;
- no dependency from the trendcontext package to Spring or downstream strategy,
  opportunity, Risk, or execution packages.

### Completion condition

The result model can represent every required factual state, exclusion,
contradiction, and attention label in the 24 scenarios without a score or
deferred concept.

## Step 2 - Bounded Role Replay and Swing Detection

### Goal

Implement strict N-radius pivot detection and same-type suppression using only
eligible candles.

### Components

- package-private `TrendContextSwingCalculator`;
- package-private candidate and suppression trace records;
- public `ConfirmedSwing`, `SwingType`, and suppression evidence as needed.

### Rules

- Consume a role's `calculationReadyCandles`, defensively filter by
  `closeTime <= cutOffAt` before calculations.
- Require a full `[i-N, i+N]` window.
- High: strict greater-than every neighboring high.
- Low: strict less-than every neighboring low.
- Equal same-type prices prevent a pivot.
- A candle may be both high and low.
- Confirmation time is `closeTime[i+N]`; confirmation must be cut-off eligible.
- Process candidates in pivot-time order.
- Suppress only same-type candidates within minimum separation.
- High keeps greater price, low keeps lower price, exact tie keeps earlier pivot.
- Never use a greater-excursion formula or suppress cross-type candidates.

### Tests

- strict high and low pivots;
- equal-high/equal-low suppression;
- both pivot types on one candle;
- missing left/right window;
- confirmation only after the right window closes;
- cut-off removes unconfirmed pivots;
- same-type separation for high, low, and exact ties;
- cross-type coexistence;
- synthetic/open candles cannot be candidates;
- no-look-ahead append test.

### Completion condition

The retained swing sequence and suppression trace are deterministic and explain
each retained/suppressed candidate with its complete evidence window.

## Step 3 - Relations and Directional Structure

### Goal

Derive strict same-type relations and role-level factual direction/regime.

### Components

- package-private `TrendContextStructureCalculator`;
- `SwingRelation`, `TrendStructure`, and relation evidence.

### Rules

- Compare only the latest two retained highs and latest two retained lows.
- Highs map to HH/LH/EQ_HIGH; lows map to HL/LL/EQ_LOW.
- UP requires HH + HL and no active bearish break.
- DOWN requires LH + LL and no active bullish break.
- Otherwise expose neutral/non-directional or unknown according to evidence
  availability; never expose categorical RANGE.
- Regime is TRENDING only for confirmed directional structure at this stage.

### Tests

- all six strict relation values;
- fewer than two same-type swings;
- equal values with different decimal scales;
- HH+HL and LH+LL;
- mixed/contradictory relations remain non-directional;
- parameterized bullish/bearish symmetry;
- EMA/ATR are not consulted by structure.

### Completion condition

The same retained swing sequence always gives the same relations and structure,
with no indicator-based direction inference.

## Step 4 - Protected Levels, Breaks, Transition, and Reclaim

### Goal

Replay structural events chronologically and preserve transition semantics.

### Components

- package-private `TrendContextBreakCalculator`;
- `ProtectedLevel`, `StructuralBreak`, `BreakStatus`, and transition evidence.

### Rules

- UP protected low is the latest retained low before latest HH.
- DOWN protected high is the latest retained high before latest LL.
- Confirm break only on one eligible close strictly beyond the protected level.
- Wick-only breach is unconfirmed and never changes regime.
- Confirmed adverse break changes regime to TRANSITIONING and phase to TRANSITION.
- Replay post-break evidence rather than deciding from final aggregate state.
- Reclaim within `reclaimWindowBars` restores prior factual direction but emits
  `FAILED_BREAK_RECLAIM` and forces dangerous attention.
- Opposite structure must be formed after the break and satisfy its own two-pair
  relation rule before resolving to the opposite trend.
- A break cannot directly produce the opposite trend.

### Tests

- protected level selection for both directions;
- no protected level means no break eligibility;
- close breach versus wick-only breach;
- transition persistence without resolution;
- reclaim on each allowed bar and just outside the window;
- opposite structure resolves only after the break;
- failed reclaim is dangerous and never immediately attractive;
- mirrored UP/DOWN parameterized cases;
- appended post-cutoff break data cannot alter an earlier assessment.

### Completion condition

Break, transition, failed reclaim, and opposite-structure outcomes are
chronological, replayable, evidence-linked, and symmetrical.

## Step 5 - EMA and ATR Calculators

### Goal

Implement exact supporting indicators with explicit decimal arithmetic.

### Components

- package-private `TrendContextEmaCalculator`;
- package-private `TrendContextAtrCalculator`;
- `TrendEmaEvidence`, `TrendAtrEvidence`, and slope/volatility enums.

### EMA rules

- Use real eligible closed closes only.
- Require period plus warmup.
- Seed with the arithmetic mean of the first period.
- `alpha = 2 / (P + 1)`.
- Apply the exact recurrence from the design.
- Normalize slope with current ATR and configured lookback.
- `RISING` only above threshold, `FALLING` only below negative threshold,
  otherwise `FLAT`.
- EMA cannot create direction, structure, pullback, or attention by itself.

### ATR rules

- First TR is high-low.
- Later TR is the maximum of high-low and both previous-close gaps.
- Seed with the mean of the first ATR period.
- Apply Wilder smoothing.
- Baseline is the mean of the latest configured ATR values.
- Expose current ATR, baseline, ratio, and abnormal flag only.
- Abnormal when ratio reaches the profile threshold.
- ATR cannot size positions, set stops, determine direction, or authorize Risk.

### Arithmetic convention

- exact `compareTo` for thresholds and price equality;
- `MathContext.DECIMAL128` for division/multiplication chains;
- no implicit double conversion;
- canonical output decimals use stripped trailing zeros;
- test non-terminating division and threshold equality explicitly.

### Tests

- EMA seed and recurrence against hand-calculated decimal fixtures;
- slope rising/falling/flat boundaries;
- insufficient warmup and unavailable ATR;
- true range gap cases;
- Wilder seed and smoothing;
- baseline length and abnormal ratio boundary;
- synthetic/open exclusion;
- EMA/ATR independence from direction and structure;
- no-look-ahead indicator fixtures.

### Completion condition

EMA and ATR outputs match the canonical formulas with reproducible decimal
results and cannot create authority outside supporting evidence.

## Step 6 - Pullback, Extension, Levels, and Invalidation

### Goal

Complete SETUP-specific phase and location evidence without introducing deferred
retracement semantics.

### Components

- package-private `TrendContextPullbackCalculator`;
- package-private `TrendContextLocationCalculator`;
- `TrendPullbackAssessment`, `TrendExtensionAssessment`, `StructuralLevel`,
  `TrendInvalidation`.

### Rules

- Derive prior resolved direction by replaying the SETUP event sequence.
- Apply strict mirrored pullback conditions from the domain design.
- Extension requires both EMA and ATR, same-side price, and threshold ratio.
- Expose every retained swing as an individual structural level.
- Mark active protected levels; do not cluster levels or create zones.
- Detect analytical invalidation against the protected SETUP level.

### Tests

- UP/DOWN pullback symmetry;
- latest event and post-pivot bar requirements;
- close comparisons are strict;
- break prevents pullback;
- extension missing EMA/ATR is unknown, not false;
- extension threshold and side-of-EMA tests;
- levels preserve swing evidence without clustering;
- invalidation mirrors direction and is not an order/Risk result.

### Completion condition

Phase, location, individual levels, and analytical invalidation are deterministic
and contain no Fibonacci, depth, quality score, stop, or execution semantics.

## Step 7 - Per-Timeframe Assessment

### Goal

Compose the pure calculators into one deterministic factual result for each
configured role.

### Components

- package-private `TrendContextTimeframeAssessor`;
- `TrendContextTimeframeAssessment`.

### Rules

- Run validation-finding consumption, freshness, swings, structure, transition,
  indicators, pullback, extension, levels, and invalidation in fixed order.
- Preserve valid data-quality exclusions separately from unsafe invalidity.
- Required insufficient/stale/non-directional evidence remains explicit.
- Sort findings and evidence by stable rule/role/time/source order.

### Tests

- isolated BIAS, SETUP, and optional TRIGGER role results;
- required versus optional role absence;
- freshness boundary at exactly two intervals;
- source exclusions remain visible;
- role result is immutable and deterministic;
- per-role no-look-ahead and symmetry checks.

### Completion condition

Each role has a complete factual assessment that can be consumed by the MTF
alignment step without infrastructure or downstream business objects.

## Step 8 - Multi-Timeframe Alignment

### Goal

Apply the accepted BIAS/SETUP/TRIGGER matrix with fixed authority priority.

### Components

- package-private `TrendContextAlignmentCalculator`;
- `TrendTimeframeAlignment` and contradiction records.

### Rules

- BIAS dominates SETUP; SETUP dominates optional TRIGGER.
- Map aligned UP/DOWN and controlled pullbacks.
- Map transitions, conflicts, insufficient direction, and insufficient bias.
- Treat optional trigger absence/staleness as unavailable.
- Treat trigger neutral as supportive/non-reversing.
- Treat confirmed opposite trigger/transition as contradiction.
- Treat wick-only trigger evidence as warning, not contradiction.

### Tests

- complete matrix rows from the design;
- optional trigger absent, stale, neutral, supportive, opposite, and transition;
- BIAS cannot be reversed by SETUP or TRIGGER;
- contradiction evidence remains typed and ordered;
- mirrored UP/DOWN alignment scenarios.

### Completion condition

One explicit alignment state and typed contradiction set are produced for every
accepted role combination; no role can manufacture missing evidence.

## Step 9 - Freshness, Exclusions, and Conservative Outcome

### Goal

Derive one immutable attention outcome from facts and exclusions using a fixed
closed decision table.

### Components

- package-private `TrendContextOutcomeCalculator`;
- `TrendAttention`, exclusions, contradictions, and root assessment assembly.

### Outcome precedence

1. unsafe or defensive calculation failure -> `UNKNOWN`;
2. required missing/stale/insufficient/non-directional evidence -> `NO_SETUP`;
3. transition, conflict, failed reclaim, extension, abnormal ATR, or confirmed
   trigger contradiction -> `CONTEXTUALLY_DANGEROUS`;
4. optional trigger unavailable, EMA contradiction, or non-blocking warning ->
   `WATCH`;
5. complete aligned/pullback context with no exclusion ->
   `CONTEXTUALLY_ATTRACTIVE`.

The implementation must document precedence when multiple findings coexist. It
must never average contradictions or allow one positive indicator to cancel a
hard exclusion.

### Tests

- every hard exclusion matrix row;
- multiple simultaneous findings prove precedence;
- optional trigger caps at WATCH;
- malformed/defensive failures are UNKNOWN;
- valid unattractive context is not treated as invalid input;
- attractive never creates downstream types.

### Completion condition

All 24 scenarios map to the exact factual and attention semantics from the
accepted design, with no weighted score.

## Step 10 - Assessment Fingerprint and Evidence Finalization

### Goal

Make the complete assessment replayable and ready for future `0064` lineage.

### Components

- deterministic canonicalizer inside `TrendContextAssessment` or a dedicated
  package-private helper;
- SHA-256 assessment fingerprint.

### Canonical content

Include input fingerprint, market identity, cut-off, profile/rule versions,
role results, alignment, findings, exclusions, invalidations, attention, stable
evidence references, and canonical decimal values. Sort every map/set/list that
does not have domain order. Preserve chronological order where the domain order
is meaningful.

Do not include runtime timestamps, random identifiers, Spring objects, or full
unordered object `toString()` values.

### Tests

- identical replay gives identical assessment fingerprint;
- map/candle input ordering does not change bounded result;
- material price, profile, rule, cutoff, or evidence changes change the output
  fingerprint;
- decimal scale-only equivalence follows the chosen canonical convention;
- future appended evidence leaves analytical projection unchanged while the
  input lineage reference may differ as specified in the Repository Analysis.

### Completion condition

The assessment is immutable, content-addressable, and ready to be referenced by
an observation without persistence or orchestration code.

## Step 11 - Canonical, Invariant, and Regression Validation

### Goal

Prove the complete Story contract without broadening scope.

### Test organization

- `TrendContextSwingCalculatorTest` or equivalent focused domain test;
- `TrendContextStructureTest`;
- `TrendContextBreakTransitionTest`;
- `TrendContextIndicatorTest`;
- `TrendContextPullbackAndLocationTest`;
- `TrendContextAlignmentTest`;
- `TrendContextOutcomeTest`;
- `TrendContextAssessmentTest` for replay/fingerprint/invariants;
- one canonical scenario fixture suite covering all 24 scenarios.

Tests remain pure JUnit/AssertJ tests with no Spring context, database, network,
broker, account, Risk, ML, LLM, or agent dependencies.

### Required invariant coverage

- deterministic same-input replay;
- cut-off isolation;
- synthetic/open exclusion;
- strict equality and wick-only break behavior;
- TRIGGER cannot reverse BIAS;
- EMA/ATR cannot independently create structure;
- extension cannot create opposite direction;
- attractive result has no downstream authority;
- bullish/bearish parameterized symmetry;
- no deferred concept appears as an output field or derived state.

### Completion condition

All Story acceptance criteria, all 24 scenarios, and all listed invariants have
executable focused coverage; the module's standard `clean verify` and
`git diff --check` pass after implementation.

## Review Gates

Before implementation:

- human approves this analysis and plan;
- no new ADR is required;
- Story `0062` input boundary remains unchanged.

During implementation:

- review each step as a small coherent change;
- reject any Spring or downstream authority dependency in the pure package;
- verify every new field has a rule/evidence owner;
- keep profile thresholds versioned and labeled as paper hypotheses.

After implementation:

- compare implementation against all 24 scenario rows;
- inspect the complete diff and `git diff --check`;
- run focused tests and module `clean verify`;
- perform human code review;
- produce the implementation and engineering reports in the established Story
  workflow before any later Story is started.

## Explicitly Deferred

The implementation must not add:

- Market Data acquisition or role orchestration;
- production capability registration;
- `AnalysisExecution` creation;
- `IntelligenceObservation` persistence or supersession;
- REST or Feign contracts;
- StrategyDefinition, StrategyEvaluation, StrategyMatch, or
  TradingOpportunity integration;
- Risk, TradePlan, execution, broker, account, or human authorization;
- RANGE, volatility categories beyond abnormal ATR, momentum, strength, scores,
  probabilities, RSI, MACD, VWAP, ADX, volume profile, order-flow, liquidity,
  clustered support/resistance, ML, LLM, or agents.

## Files Expected During Implementation

The coding Story may add or modify only the pure Trend Context domain classes and
their focused tests under the Market Intelligence module, plus the Story
implementation report artifacts required by the established workflow after
human review. It must not modify the `0062` input contract, Market Data
contracts, orchestration, persistence, or unrelated worktree changes.

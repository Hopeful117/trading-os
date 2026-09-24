# Story 0063 - Implement the Deterministic Trend Context Engine

## Metadata

**ID:** `0063`

**Title:** Implement the Deterministic Trend Context Engine

**Status:** IMPLEMENTED - HUMAN REVIEW PENDING

**Size:** LARGE

**Implementation Risk:** HIGH

## Goal

Implement the pure deterministic engine for the retained Trend Context V1 rule
catalogue so the same accepted input, cut-off, profile, and rule versions always
produce the same factual assessment, evidence, exclusions, invalidation, and
attention outcome.

## Context

Story 0062 provides the dedicated typed input and provenance boundary. No
Trend Context engine currently exists. The accepted domain design is the
canonical specification for the retained rules and deliberately defers rich
range, momentum, strength, clustered areas, ML, and agent behavior.

## Problem

Trading OS has raw OHLC and permissive legacy trend semantics, but no
reproducible multi-timeframe structure model suitable for capital-preserving
decision support.

## Scope

### Included rule IDs

- `CANDLE_VALIDATION_V1` and `PROFILE_VALIDATION_V1` consumption from Story
  0062.
- `SWING_HIGH_V1`, `SWING_LOW_V1`, and `SWING_SUPPRESSION_V1`.
- `RELATIONS_V1`, `STRUCTURE_UP_V1`, and `STRUCTURE_DOWN_V1`.
- `PROTECTED_LEVEL_V1` and `BREAK_CONFIRM_V1`.
- `TRANSITION_V1`, including failed-break reclaim.
- `PULLBACK_UP_V1` and `PULLBACK_DOWN_V1`.
- `EMA_V1`, `ATR_V1`, and `EXTENSION_V1`.
- `LEVEL_V1`, `INVALIDATION_UP_V1`, and `INVALIDATION_DOWN_V1`.
- `MTF_ALIGNMENT_V1`, `FRESHNESS_V1`, and `OUTCOME_V1`.
- Immutable factual assessment, evidence references, findings, contradictions,
  hard exclusions, and deterministic fingerprint.

### Required result semantics

The result must separate:

```text
direction: UP | DOWN | NEUTRAL | UNKNOWN
regime: TRENDING | TRANSITIONING | NON_DIRECTIONAL | UNKNOWN
phase: DIRECTIONAL | PULLBACK | EXTENDED | TRANSITION | UNDETERMINED
alignment: explicit role relation
attention: UNKNOWN | NO_SETUP | WATCH |
           CONTEXTUALLY_ATTRACTIVE | CONTEXTUALLY_DANGEROUS
```

## Out of Scope

- Spring, HTTP, repositories, persistence, Market Data calls, account state,
  Risk, TradePlan, broker, execution, ML, LLM, and agents.
- Rich `RANGE` classification; use `NO_DIRECTIONAL_STRUCTURE` as specified.
- Categorical LOW/NORMAL/HIGH volatility state.
- Momentum classification, aggregate strength, RSI, MACD, VWAP, ADX, volume
  profiling, order flow, or probabilistic ranking.
- Complex clustered structural areas; expose confirmed individual levels only.
- Automatic opportunity, StrategyMatch, TradePlan, Risk, or execution creation.
- Changing any accepted formula or threshold from the canonical design.

## Architectural Constraints

- The accepted deterministic Trend Context design and its rule catalogue are
  authoritative; this Story must not redefine the rules.
- Use decimal arithmetic and the exact comparison/ordering semantics specified
  by the design.
- No look-ahead: no calculation may read a candle after the assessment cut-off.
- Synthetic and open candles cannot create deterministic calculations.
- The engine must be a pure domain component independently testable without
  Spring or external services.
- Preserve ADR-048 authority boundaries and `NO_SETUP`/`UNKNOWN` semantics.

## Acceptance Criteria

- [x] The engine calculates strict N-radius pivots with right-side confirmation,
      equal-high/equal-low handling, same-type suppression, and evidence windows.
- [x] The same retained swing sequence always produces the same HH/HL/LH/LL
      relations and directional structure.
- [x] Protected levels, wick-only breaches, confirmed breaks, failed reclaims,
      transition persistence, and opposite-structure resolution follow the
      canonical rules exactly.
- [x] Pullback detection is mirrored for upward/downward bias and does not
      require deferred indicator semantics.
- [x] EMA uses the canonical seed, recurrence, slope normalization, and
      supporting-only behavior.
- [x] ATR uses the canonical Wilder calculation and baseline ratio without
      creating Risk sizing or stop-placement decisions.
- [x] Extension, individual structural levels, analytical invalidation,
      multi-timeframe alignment, contradictions, and hard exclusions are
      deterministic and evidence-linked.
- [x] Factual context and attention outcome are separate outputs.
- [x] Missing, stale, insufficient, synthetic, open, malformed, contradictory,
      and abnormal inputs produce the specified safe outcomes.
- [x] A lower timeframe or trigger cannot reverse BIAS by itself.
- [x] Identical input/profile/rule/cut-off combinations produce identical output
      and fingerprint.
- [x] No engine API can create a StrategyMatch, TradingOpportunity, TradePlan,
      Risk result, ExecutionIntent, or broker command.
- [x] All 24 canonical acceptance scenarios from the domain design are covered
      by focused tests, including replay and no-look-ahead invariants.
- [x] `git diff --check` passes.

## Test Requirements

- Unit tests for every retained rule ID.
- Parameterized tests for mirrored upward/downward semantics.
- Property/invariant tests for deterministic replay, cut-off isolation,
  synthetic-candle exclusion, and wick-only break behavior.
- Tests proving deferred concepts are not accidentally represented as derived
  authority or weighted scores.
- No network, database, broker, account, Risk, ML, or LLM dependencies in the
  pure engine test fixture.

## Observability and Provenance Requirements

- Every material result references role, interval, candle/evidence window, rule
  ID/version, profile/version, input fingerprint, and cut-off.
- Findings distinguish data invalidity from a valid but unattractive market.
- Evidence must make a `NO_SETUP` result explainable without prose inference.

## Dependencies

- Story 0062 dedicated input contract and profile.
- Accepted deterministic Trend Context domain design.
- ADR-048.

## Likely Files/Components Affected

- New `market-intelligence/.../trendcontext/` domain package.
- Pure input/output, profile, calculator, rule, evidence, and assessment types.
- New deterministic engine unit/property tests.

## Risks

- This is the highest domain-complexity Story; ambiguous implementations would
  create incompatible trading semantics.
- Thresholds are paper-validation hypotheses, not validated strategy
  parameters.
- A convenient composite score or indicator classifier could accidentally
  reintroduce deferred scope.

## Definition of Done

- [ ] Story scope approved by the human engineer.
- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [x] Pure engine and rule tests implemented and passing.
- [x] All canonical scenarios and invariants have executable coverage.
- [x] No infrastructure or financial authority dependency introduced.
- [ ] Human code review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created.

# Story 0225 - Implementation Plan

## Executive Summary

Implement the accepted, provider-neutral historical continuity contract in
Market Data and consume it from the ML-01 preparation path in Market
Intelligence. The implementation will support deterministic synthetic closure
evidence and remain fail-closed when evidence is absent, contradictory, stale or
outside the historical knowledge boundary.

No closure-data provider, calendar integration, persistence migration or real
data backfill will be introduced by this plan. Real-data closure crossing will
remain diagnostic-only until a source authority and validation scope are
approved.

## Objectives

- Represent scoped, versioned and fingerprinted verified closure evidence.
- Separate Market Data base transition classification from Market Intelligence
  consumer acceptance.
- Preserve actual timestamps, elapsed duration and reopening price gaps.
- Allow ML-01's 33-candle lookback and H+4/H+12/H+24 horizons to cross only
  `VERIFIED_CLOSURE` transitions.
- Reject unexplained gaps, unknown closure evidence and invalid observations.
- Include continuity policy and evidence fingerprints in ML-01 diagnostics and
  derived dataset fingerprints.
- Preserve existing contiguous-input mathematical results.

## Architecture Impact

### Market Data

Add provider-neutral value objects and enums in the existing historical OHLC
model package:

- `HistoricalClosureEvidence`: scoped closure interval, canonical market and
  venue/provider scope, closure type/reason, source identity/version, effective
  bounds, `knownAt`, verification state and fingerprint.
- `HistoricalContinuityPolicy`: policy version, interval, evidence cutoff rule,
  valid-observation requirements and consumer closure-crossing mode.
- `HistoricalContinuityTransition`: previous/next observations, expected
  cadence, actual elapsed duration, semantic classification, applicable closure
  evidence and reason.
- A semantic transition classification covering contiguous, verified closure,
  unexplained gap, invalid observation and unknown closure evidence.

Reuse the existing continuity report and fingerprint concepts where possible.
The exact record names may be adjusted during implementation if existing package
conventions reveal a better fit, without changing the ADR contract.

### Market Intelligence

Add an ML-01 continuity adapter/policy at the historical preparation boundary.
The adapter will:

- evaluate transitions between ordered valid H1 candles through the Market Data
  contract;
- accept contiguous transitions;
- accept verified closures for ML-01;
- reject all other non-contiguous transitions;
- select H+4/H+12/H+24 by valid future H1 observation count rather than elapsed
  time;
- pass accepted candles to existing calculators without timestamp compression.

The existing formulas in `VolatilityCalculator` and
`MarketOutcomeCalculator` remain unchanged. Their callers will provide the
accepted observation sequence and preserve reopening price changes naturally in
true range and close-to-close calculations.

## Implementation Strategy

### 1. Extend Market Data continuity contracts

- Add immutable closure evidence and policy records with constructor validation.
- Add deterministic fingerprint material for all closure and policy fields.
- Map legacy classifications explicitly to the new semantic classifications.
- Keep legacy cTrader weekend heuristics as non-verified/uncertain input; do not
  promote them to closure evidence.

### 2. Implement base transition assessment

- Extend `HistoricalOhlcContinuityValidator` with an assessment path that accepts
  scoped closure evidence and an evidence cutoff.
- Validate market, interval, venue/provider scope, closure bounds, effective
  interval, `knownAt`, source version and contradictory evidence before accepting
  a closure.
- Emit `VERIFIED_CLOSURE` only when every expected missing interval in the
  transition is covered by applicable non-contradictory evidence.
- Emit `UNKNOWN_CLOSURE_EVIDENCE` for missing, stale, contradictory or
  out-of-boundary evidence.
- Keep invalid, duplicate-conflicted and malformed observations separate from
  closure classifications.
- Preserve the existing fail-closed behavior for callers that do not provide
  verified closure evidence.

### 3. Integrate the ML-01 lookback policy

- Add a policy-aware history selector in `Ml01HistDataPreparation`.
- Build the 33-candle history from valid H1 observations and evaluate every
  transition with the Market Data assessment.
- Permit verified closures for the four ML-01 features only under the explicit
  ML-01 policy.
- Preserve actual observation times and do not create a synthetic candle at the
  closure boundary.
- Pass the resulting observations to `VolatilityCalculator` through the smallest
  compatible policy-aware entry point. The calculator must not independently
  reinterpret closure evidence.

### 4. Integrate ML-01 outcome horizons

- Replace exact adjacency as the only outcome gate with transition assessment
  over the next 4, 12 and 24 valid H1 candles.
- Keep the current end-of-dataset truncation result separate from continuity
  failures.
- Preserve reopening price gaps in realized volatility, directional efficiency
  and absolute future return.
- Record the transition classifications and exclusion reason in each future
  outcome and readiness diagnostic.

### 5. Fingerprints and diagnostics

- Include continuity policy version, closure evidence fingerprints and transition
  classifications in ML-01 dataset fingerprint material.
- Extend quality/readiness output with counts for contiguous transitions,
  verified closures, unexplained gaps, unknown evidence, invalid observations and
  end-of-dataset truncation.
- Ensure a change to closure evidence or policy changes derived fingerprints.
- Do not alter unrelated generated-at or source-file behavior.

### 6. Tests

Add or extend tests in the existing module test locations:

- Market Data: contiguous transition, recurring closure, holiday closure,
  exceptional closure, unknown evidence, unexplained gap, stale/contradictory
  evidence, invalid candle, duplicate conflict, scope mismatch and cutoff
  boundary.
- Market Intelligence: 33-candle lookback across an accepted closure, rejection
  for unknown/unexplained transitions, H+4/H+12/H+24 valid-candle counting,
  reopening price-gap preservation, end truncation and contiguous regression.
- Fingerprint tests: same facts produce the same result; changed policy or
  closure evidence produces a different result.

## Technical Tasks

1. Add and validate Market Data closure/policy/transition models.
2. Extend Market Data continuity assessment and semantic mapping.
3. Add deterministic Market Data fingerprints for policy and closure evidence.
4. Add ML-01 policy-aware history and outcome selection.
5. Extend ML-01 artifact diagnostics and fingerprint material.
6. Add synthetic and regression tests.
7. Run targeted module tests and inspect the complete diff.
8. Reconcile documentation only if implementation changes documented behavior
   beyond the already updated Story and ADR.

## Validation Plan

- `market-data` targeted continuity and fingerprint tests.
- `market-intelligence` targeted ML-01 preparation and calculator tests.
- Full `market-data` and `market-intelligence` test suites if targeted tests
  pass and runtime permits.
- `git diff --check`.
- No live-provider mutation, database mutation, model training or MCP execution.
- Real-data validation is blocked unless closure evidence authority and bounded
  output scope are explicitly approved.

## Risks

- Adding a closure-aware path without preserving legacy fail-closed behavior could
  make existing callers permissive. Tests must cover no-evidence behavior.
- Counting valid candles across closures can accidentally change elapsed-time
  semantics. Actual timestamps and transition provenance must remain in the
  result.
- Provider-specific weekend heuristics may be mistaken for verified evidence.
  They must remain explicitly uncertain unless represented by an approved source
  record.
- The current ML-01 input is local CSV data without a closure-evidence source.
  Synthetic tests can prove deterministic behavior but cannot prove real-data
  readiness.

## Rollback Strategy

- Keep changes confined to Market Data continuity models/services/tests and the
  ML-01 preparation/tests.
- Do not modify source CSV files or persisted production schemas.
- If closure-aware behavior fails validation, remove only the new policy-aware
  path and retain the existing strict adjacency behavior.
- Existing artifacts remain immutable; new policy/evidence fingerprints create
  new derived artifacts.

## Expected Deliverables

- Updated Market Data continuity contracts and validator.
- Updated ML-01 preparation and diagnostics.
- Deterministic tests covering ADR-094 classifications and ML-01 crossing rules.
- Updated Story implementation report after implementation and validation.
- Documentation outcome recorded before code review.

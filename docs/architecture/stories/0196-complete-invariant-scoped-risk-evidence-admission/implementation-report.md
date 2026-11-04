# Implementation Report - Story 0196

## Summary

Implemented the invariant-scoped evidence admission slice of ADR-086. Trading
Core now carries invariant-specific admission failures into the deterministic
Risk Domain instead of rejecting every policy-dependent incomplete snapshot as a
global context failure.

## Implemented Changes

- Added immutable invariant admission diagnostics to `RiskEvaluationContext`.
- Kept the existing context builder overload and added an explicit admission
  map overload for T0 and T1.
- Updated `DeterministicRiskEngine` to emit `INCOMPLETE` for an invariant with
  an admission failure before evaluating that rule.
- Added `InvariantEvidenceAdmission` in Trading Core for policy-specific checks
  covering balance, equity, positions, protective stops, starting balance and
  closed deals.
- Removed global equity completeness/provenance rejection from T0 and T1 while
  preserving structural snapshot rejection and explicit global incomplete
  observation rejection.
- Allowed the normalized `AccountSnapshot` to carry unavailable equity through
  T0 and T1 without a balance or zero fallback.
- Added regression coverage proving position-risk diagnostics remain available
  while equity-dependent exposure is incomplete.
- Added structural admission checks for global observation incompleteness,
  malformed balance/currency shape and canonical account identity mismatch.
- Added explicit stale/incomplete authoritative observation reasons instead of
  treating every incomplete snapshot as policy-scoped.
- Added T1 incomplete no-submission coverage and resolver parity coverage.
- Added an explicit broker-submission guard requiring T1 approval and evaluation
  identity for entry orders, with direct regression coverage.
- Classified generic `OBSERVATION_INCOMPLETE` as non-structural when an explicit
  invariant-scoped reason is present, while retaining fail-closed behavior for
  generic incompleteness without scoped evidence.
- Added persisted T1 approval verification before broker submission, including
  intent identity, completed status and approved decision checks.
- Corrected composite-key lookup for persisted T1 evaluations.
- Updated the existing Trading Core expectation so missing equity yields an
  incomplete risk result rather than a pre-admission context failure.

## Safety Guarantees

- No `equity = balance` fallback exists.
- No zero, stale or inadmissible equity is treated as available.
- An incomplete mandatory invariant cannot produce an executable decision.
- Explicit structural snapshot failures remain context-unavailable.
- T0 and T1 use the same invariant admission resolver.
- No broker-specific protocol or policy threshold was added to the Risk Domain.
- No cTrader LIVE call, order, mutation or safety-flag change was performed.

## Validation

Passed:

- `mvn test` in `risk-domain`: 47 tests.
- Focused `risk-domain` context and integration tests: 23 tests.
- Focused Trading Core admission and provider tests: 24 tests.
- Focused `TradePlanRiskEvaluationServiceTest`: 30 tests.
- Focused execution pipeline, provider and admission regression tests: 50 tests.
- `mvn -DskipTests compile` in `risk-domain`.
- `mvn -DskipTests compile` in `trading-core`.
- `git diff --check`.

- The first full Trading Core run exposed two expectations for the old global
  rejection reason. They were updated to the explicit structural observation
  reason. The first full Trading Core run also exposed five direct broker-step
  fixtures that did not provide the now-required T1 approval; those fixtures
  were updated and the final full suite was rerun successfully: 999 tests.
- Integration coverage for paper and CFD execution paths passed after wiring the
  persisted T1 verifier in test contexts: 18 tests.
- Final independent review: PASS. No material findings remained; one optional
  residual gap is a single production-wiring test covering a persisted approved
  T1 row through to broker submission.

## Working Tree

The pre-existing cTrader/0194 modifications remain untouched and uncommitted.
No commit, push, merge, reset, stash or cleanup operation was performed.

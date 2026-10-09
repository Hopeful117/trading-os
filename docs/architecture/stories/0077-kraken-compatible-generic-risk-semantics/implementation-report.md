# Story 0077 - Implementation Report

## Result

Implemented provider-neutral daily and total drawdown semantics in Risk Domain
and propagated the required immutable baselines through Trading Core.

## Evidence

- Implemented in commit `33217c3`.
- Added the daily baseline and account starting-balance concepts.
- Added `MAX_TOTAL_DRAWDOWN` while preserving `DAILY_DRAWDOWN`.
- Kept observed and projected metrics distinct.
- Added persistence migrations, provenance propagation, and focused tests.

## Validation

Passed:

```text
./mvnw -q -f ../risk-domain/pom.xml test
./mvnw -q test                         # trading-core
git diff --check
```

The complete Risk Domain and Trading Core test suites passed. The runs emitted
existing framework and intentionally exercised failure-path warnings, but no
test failure.

Not executed:

- live broker or Kraken sandbox validation;
- independent code review.

## Closure State

Implementation and automated module validation are present. Human review,
acceptance, and lifecycle closure were completed on 2026-10-06. Broker and
Kraken sandbox validation remain intentionally out of scope. No new
implementation, commit, push, or merge was performed by the coding agent.

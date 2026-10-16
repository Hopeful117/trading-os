# Implementation Report

## Scope

Implemented Story 0107C objective future market outcomes in Market Intelligence.
The implementation remains internal and does not modify the historical T0
analysis pipeline, Gateway routes, frontend, scheduler, provider fallback, or
trade/P&L semantics.

## Temporal Semantics

- Reference evidence is selected with `closeTime <= T0` and `knownAt <= T0`.
- Future evidence is requested only after reference evidence is available.
- Future observations are bounded by `openTime >= T0` and `closeTime <= T0 + H`.
- Evaluation is `PENDING` while `now < maturityAt`; maturity is exact at
  `now >= maturityAt`.
- V1 uses captured evidence and fifteen-minute OHLC observations.

## Statuses and Metrics

- `PENDING` contains no finalized future metrics.
- `UNAVAILABLE` is returned for missing reference or unavailable future data.
- `INCOMPLETE` is returned for incomplete future coverage.
- `AVAILABLE` computes final close, high, low, forward return, high return,
  low return, and future range using `BigDecimal`.
- No label, target, strategy result, trade result, or P&L is produced.

## Provenance and Fingerprints

Outcome fingerprints include definition version, market, cutoff, horizon,
reference/future evidence fingerprints, normalization versions, status, and
all finalized metrics. Operational `computedAt` is not included.

Reference and future normalization versions are validated at the Market Data
adapter boundary and persisted with the immutable outcome.

## Persistence and Corrections

Outcomes are persisted append-only with a unique outcome fingerprint.
Repeated identical evaluations return the existing semantic artifact. A
corrected future evidence fingerprint produces a new immutable row and does
not overwrite the previous outcome. Missing future evidence is allowed for
non-available outcomes.

The V14 migration creates the outcome table and indexes by market, cutoff,
horizon, and analysis execution.

## Internal API and Validation

The evaluation endpoint is `/internal/v1/market-outcomes/evaluate`. Invalid
required fields and horizons exceeding the Market Data range capability return
HTTP 400 through the existing internal service security boundary. No Gateway
route is added.

## Validation

- `mvn -q -DskipTests compile`
- Targeted outcome, service, persistence, horizon, and controller tests
- `mvn -q test`
- `mvn -q verify`
- `git diff --check`

All executed Maven validations pass.

Runtime validation was executed on the local Docker Compose stack with
PostgreSQL 16, Eureka, Market Data, and Market Intelligence:

- unauthenticated internal request returned `401`;
- invalid service credential returned `401`;
- valid `trading-core` service JWT was accepted;
- Market Data was reached through Eureka/Feign;
- missing strict reference returned `200 UNAVAILABLE` with no future metrics;
- invalid JSON returned `400`;
- an eight-day horizon beyond the 720-candle capability returned `400`;
- repeated evaluation returned the same fingerprint and original `computedAt`;
- after restarting Market Intelligence, the same artifact was reconstructed
  from PostgreSQL and the table still contained one row.

The selected runtime cutoff was `2026-10-15T20:00:00Z`. The strict reference
rule requires a captured, closed candle with both `closeTime <= cutoff` and
`knownAt <= cutoff`; the local dataset had no such observation because the
loaded candles were captured/known later, so evaluation correctly stopped at
`REFERENCE_EVIDENCE_UNAVAILABLE`. Consequently the future-range stage was not
reached and an end-to-end `AVAILABLE` calculation was not demonstrated. This
prevents look-ahead leakage; it is not evidence that the calculator cannot
produce `AVAILABLE`. No production runtime was used.

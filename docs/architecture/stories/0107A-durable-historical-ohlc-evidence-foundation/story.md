# Story 0107A - Durable Historical OHLC Evidence Foundation

## Goal

Persist provider-neutral closed OHLC evidence in Market Data and expose bounded
repository-first strict as-of reads for future historical Market Intelligence.

## Scope

- Immutable/versioned historical OHLC evidence model.
- Explicit `knownAt` and captured/backfill origin semantics.
- Flyway/PostgreSQL persistence with idempotent fingerprints.
- Bounded strict observed as-of query.
- Configurable bounded forward capture for selected markets and intervals.
- Temporal, correction, persistence, and compatibility tests.

## Out of Scope

- Historical Market Intelligence replay.
- Objective outcomes and labels.
- ML, LLM, feature datasets, Risk, execution simulation, and order-flow history.
- General historical synchronization or unbounded provider backfill.

## Architecture

`OhlcEvent` remains acquisition data. Market Data maps accepted closed,
non-synthetic events to immutable `HistoricalOhlcEvidence`. Strict observed
queries filter by closure, close time, known-at time, market, and interval.

## Limitations

Backfilled provider data is represented for future use but is not implemented as
a strict replay fallback. Forward capture is disabled by default and requires
explicit configured market IDs.

## Validation

Run Market Data unit/integration tests, migration compatibility tests,
Market-Intelligence compatibility tests if contracts change, `git diff --check`,
and inspect the complete diff before human review.

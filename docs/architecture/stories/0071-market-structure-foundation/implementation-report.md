# Story 0071 - Implementation Report

## Result

Implemented the Market Structure foundation extraction inside Market
Intelligence. The existing confirmed-swing semantics remain the baseline, while
Trend Context now consumes the extracted result on the production capability
path.

## Changes

- Added neutral Market Structure contracts for candles, gaps, inputs, swings,
  results, evidence status, availability, identity, fingerprints, and durable
  artifact content. Trend Context roles are kept outside the neutral result.
- Added `MarketStructureEngine` with the retained structural kernel:
  - strict fixed-window high/low detection;
  - closed, non-synthetic candle filtering;
  - cutoff enforcement;
  - gap-window exclusion;
  - deterministic ordering;
  - same-type minimum-separation suppression and replacement.
- Removed the duplicated pivot/suppression implementation from
  `TrendContextEngine`.
- Added a Trend Context engine overload that consumes precomputed Market
  Structure results. The existing convenience entry point still computes the
  same extracted engine result for direct domain callers and existing tests.
- Added an independent `market-structure-analysis` capability producing one
  interval-scoped `market-structure` artifact per requested Trend Context
  interval.
- Updated the production coordinator to plan Market Structure whenever Trend
  Context is selected through generic artifact dependency resolution, and
  updated Trend Context to consume the resolved structural artifacts rather
  than recalculating pivots.
- Removed the artifact-free Trend Context structural fallback from production;
  direct domain tests now build the same extracted structural artifacts before
  invoking Trend Context.
- Propagated unavailable, stale, insufficient, and invalid structural evidence
  without treating it as a valid empty-point result.
- Preserved Trend Context relation, direction, protected-level, break,
  transition, pullback, EMA, ATR, extension, alignment, and attention logic.
- Added focused tests for confirmation timing, cutoff behavior, gap exclusion,
  open/synthetic evidence, valid empty versus unavailable results, identity,
  evidence windows, and deterministic same-type suppression.
- Updated capability tests for the explicit Market Structure requirement and
  artifact consumption, planner dependency resolution, and evidence lineage.

## Validation

Passed:

```text
./trading-core/mvnw -q -f market-intelligence/pom.xml test
git diff --check
```

The full Market Intelligence test suite passed. Test execution produced known
environment warnings from Mockito, Flyway/H2, Spring configuration, and
asynchronous test fixtures; none caused a test failure.

Not executed:

- live or replayed Kraken validation;
- deployed end-to-end validation;
- frontend validation.

## Scope Notes

No new service, scanner, generic capability framework, persistence migration,
relation model, break model, strategy behavior, risk behavior, execution
behavior, or AI behavior was added.

No commit, push, merge, or branch operation was performed.

## Remaining Risk

Runtime evidence reconciliation against real Market Data remains outside the
executable local test suite and requires human-approved sandbox or replay
validation. Conflicting duplicate rejection remains enforced at the existing
Trend Context input-mapping boundary before Market Structure execution.

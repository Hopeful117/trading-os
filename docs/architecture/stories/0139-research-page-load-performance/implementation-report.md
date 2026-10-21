# Implementation Report - Story 0139

## Decision

**PARTIAL IMPLEMENTATION - STEPS 1 AND 2**

Implemented the frontend concurrency step, bounded OHLC lookup execution, and
readiness phase instrumentation. Steps 3 and 4 remain deferred because the
measurements do not justify persistence projections or a shared projection.

## Changes

- Changed `ResearchOverview` so readiness and integrity HTTP streams start from
  the same refresh cycle instead of nesting integrity after readiness.
- Preserved progressive rendering: readiness becomes usable before integrity
  completes.
- Preserved independent integrity failure handling and readiness retry behavior.
- Preserved one request per endpoint per refresh and outer `shareReplay`
  behavior.
- Added coverage for concurrent request creation, integrity completing first,
  readiness rendering while integrity is pending, and readiness failure while
  integrity is also in flight.
- Added the Story-local performance baseline artifact.
- Changed historical OHLC evidence lookup to use the existing bounded
  `readinessRemoteConcurrency` limit with one isolated future per configured
  market.
- Preserved per-market failure isolation: one unavailable OHLC market does not
  hide evidence from other markets.
- Added tests proving the configured concurrency bound and partial failure
  behavior.
- Added structured phase logs for cache hits/misses, snapshot loading, outcome
  loading, OHLC evidence, S1 replay/fee checks, remote budget usage, and total
  readiness computation.

## Remaining Deferred Steps

- Step 3: targeted persistence projections. Deferred pending query plans, row
  volume, deserialization measurements, and semantic-equivalence evidence.
- Step 4: shared backend projection or precomputation. Deferred; no ADR was
  required or created because no architectural Step 4 implementation was
  justified.

## Files Changed

- `trading-os-web/src/app/features/research/research-overview.ts`
- `trading-os-web/src/app/features/research/research-overview.spec.ts`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/ResearchReadinessService.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/research/ResearchReadinessServiceTest.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/config/ResearchLifecycleProperties.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/config/ResearchLifecyclePropertiesTest.java`
- `market-intelligence/src/main/resources/application.properties`
- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookReplayService.java`
- `docs/architecture/stories/0139-research-page-load-performance/story.md`
- `docs/architecture/stories/0139-research-page-load-performance/performance-baseline.md`
- `docs/architecture/stories/0139-research-page-load-performance/implementation-report.md`

The Story, Repository Analysis, and Implementation Plan remain in the same
Story-local directory. No public API, Gateway route, migration, or architectural
boundary was changed.

## Semantics and Protection

- S1 thresholds, blockers, signal semantics, replay semantics, fee semantics,
  evidence policy, and fail-closed behavior were not changed.
- Readiness remains the authoritative gate for the usable page state.
- Integrity remains an independent optional projection and does not affect
  readiness truth.
- No unsafe retries, unbounded fan-out, or new cache authority was introduced.
- Replay and fee remote-check budgets remain unchanged.
- No trading results, performance metrics, or strategy interpretation was added.

## Documentation Reconciliation

Documentation update: required and completed.

The Story-local baseline and implementation report record the implemented steps,
deferred steps, validation evidence, runtime measurement gap, and residual
risks. No broader repository documentation or ADR update is required because
the bounded OHLC execution reuses the existing service boundary and configuration
property.

## Vault Outcome

- Vault consulted: no.
- Outcome: no vault action.
- Rationale: repository Story, current source, tests, and accepted ADRs provided
  sufficient authoritative context; no curated note was required or modified.

## Validation

- Baseline targeted command before implementation:
  `npm run test:ci -- --include src/app/features/research/research-overview.spec.ts`:
  passed, 6 tests.
- Targeted command after implementation:
  `npm run test:ci -- --include src/app/features/research/research-overview.spec.ts`:
  passed, 7 tests.
- Full frontend suite:
  `npm run test:ci`: passed, 54 test files and 505 tests.
- Targeted Market Intelligence tests:
  `mvn -q -Dtest=ResearchReadinessServiceTest,ResearchLifecyclePropertiesTest test`:
  passed, including explicit OHLC timeout, fail-closed behavior, property
  validation, and equivalent-input S1 determinism.
- Targeted Market Data replay tests:
  `mvn -q -Dtest=OrderBookReplayServiceTest test`: passed.
- Full Market Data suite:
  `mvn -q test`: passed. Existing websocket diagnostic stack traces are emitted
  by tests that verify malformed-message handling; no test failure was reported.
- Full Market Intelligence suite:
  `mvn -q test`: passed. Existing test warnings were emitted, but no test
  failure was reported.
- Production build:
  `npm run build`: passed. Existing bundle and stylesheet budget warnings
  remain, including the Research stylesheet warning.
- Prettier check on touched TypeScript files: passed.
- `git diff --check`: passed.
- Instrumented runtime: snapshot/outcome/OHLC phases remain below `0.2 s`, while
  the S1 replay/fee phase measured `32-36 s` on the current 47-signal dataset.
- Instrumented runtime confirmed the configured 30-second cache can expire its
  result immediately when computation exceeds the TTL because expiry is based on
  the pre-computation timestamp. The cache expiry now starts after computation,
  with a regression test covering a computation longer than the configured TTL.
- Authenticated local browser runtime measurement completed through Gateway:
  readiness ranged from approximately `22.97 s` to `40.58 s`; integrity ranged
  from approximately `35 ms` to `227 ms`.
- After rebuilding the `market-intelligence` image with Step 2, a successful
  authenticated request measured approximately `30.39 s` readiness and `0.33 s`
  integrity. The configured single-market runtime cannot demonstrate OHLC
  parallelism gain.
- After rebuilding `market-intelligence` with the cache-expiry correction, two
  authenticated Gateway readiness requests measured `26.20 s` cold-cache and
  `0.18 s` warm-cache. Both returned HTTP 200 with the same `47` signals and
  expected `ready=false` blockers.
- After rebuilding the frontend image, the authenticated Research page started
  readiness and integrity within `1 ms` of each other. The browser measured
  approximately `29.98 s` readiness and `45 ms` integrity; both responses were
  HTTP 200. This confirms the frontend concurrency change in the served image.
- The two frontend requests started concurrently with less than `1 ms` start-time
  separation. Readiness is the dominant runtime bottleneck.
- No effective warm-cache response was observed during the controlled refresh
  sequence before the cache-expiry correction. The post-fix authenticated
  runtime measurement confirmed the cache hit behavior.
- Replay/fee instrumentation completed on the rebuilt runtime. A warm dependency
  run measured `5` replay calls in `97 ms` cumulative and `68` fee calls in
  `1.317 s` cumulative, with `354 ms` wall-clock S1 time. A subsequent
  post-TTL cold-cache run measured `54` replay calls in `172.486 s` cumulative
  and `68` fee calls in `0.915 s` cumulative, with `27.010 s` wall-clock S1
  time. The cold-cache bottleneck is the remote replay dependency; SQL,
  snapshots, outcomes, and OHLC remained below `60 ms` combined.
- Market Data replay profiling completed without changing the replay contract.
  The repository lookup accounted for essentially all observed replay latency
  (`6-813 ms` per call in the latest run), while replay assembly remained near
  zero. No inter-request replay cache was introduced: ADR-022 requires explicit
  freshness and invalidation semantics, and Market Data can invalidate active
  order-book sequences.
- PostgreSQL plan validation covered the active replay dataset (`49 M` evidence
  rows). A temporary sequence-oriented snapshot index reduced one lookup from
  approximately `45 ms` to below `1 ms` on warm pages, but added `3.6 GB`; a
  temporary ordinal-oriented replay index removed the sort but was slower because
  the query still had to read the full delta sequence. Both temporary indexes
  were removed and no migration was added. The dominant structural cost is
  reconstructing a replay from tens of thousands of deltas; precomputed replay
  state would require a separate Market Data design decision and invalidation
  policy.

## Repository Protection

- Pre-existing untracked architecture reports remain untouched.
- No commit, push, merge, migration, or destructive operation was performed.

## Review Status

Implementation is ready for human diff review and independent code review.
No final human implementation approval or commit was performed by this agent.

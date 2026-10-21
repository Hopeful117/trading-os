# Story 0139 - Performance Baseline and Step 1 Measurement

## Scope

This artifact records the validation evidence available for the first frontend
step and the authenticated local runtime measurements. The measurements use the
local Docker stack and are not representative of a deployed production
environment.

## Pre-change Frontend Test Baseline

- Command:
  `npm run test:ci -- --include src/app/features/research/research-overview.spec.ts`
- Result: `1` test file passed, `6` tests passed.
- Reported test-runner duration: `2.88s`.
- Coverage: sequential readiness then integrity request expectations; no test
  asserted that integrity starts before readiness completes.

## Step 1 Change

- Readiness and integrity HTTP streams are now subscribed from the same refresh
  cycle.
- Readiness remains the gate for the usable Research view.
- Integrity can complete before readiness without being lost.
- Integrity failure remains isolated and renders as unknown after readiness is
  available.
- Readiness failure remains retryable.
- One request per endpoint is asserted for each refresh.

## Post-change Frontend Validation

- Command:
  `npm run test:ci -- --include src/app/features/research/research-overview.spec.ts`
- Result: `1` test file passed, `7` tests passed.
- Reported test-runner duration: `1.22s`.
- The timing is not treated as a page-latency comparison because build/cache
  state differed between runs.

- Command: `npm run test:ci`
- Result: `54` test files passed, `505` tests passed.
- Reported Vitest duration: `9.38s`; environment setup is reported separately by
  the runner.

- Command: `npm run build`
- Result: production build completed successfully.
- Existing Angular budget warnings remain, including the pre-existing initial
  bundle and Research stylesheet budget warnings.

- Command:
  `npx prettier --check src/app/features/research/research-overview.ts src/app/features/research/research-overview.spec.ts`
- Result: passed.

- Command: `git diff --check`
- Result: passed.

## Runtime Measurement Status

Authenticated local runtime measurements were captured through the browser at
`http://127.0.0.1:4200/research`, through Gateway, with the existing local
services running.

### Initial page load

- Readiness: `27,998 ms`.
- Integrity: `53 ms`.
- Request start separation: less than `1 ms`.
- Both responses returned HTTP `200`.

### Subsequent refreshes

| Refresh | Readiness | Integrity | Observation |
|---|---:|---:|---|
| 1 | `28,900 ms` | `54 ms` | Readiness dominates the critical path |
| 2 | `39,860 ms` | `35 ms` | Readiness variance increased |
| 3 | `40,581 ms` | `37 ms` | Readiness variance persisted |
| 4 | `22,975 ms` | `227 ms` | Still no sub-second readiness response |

The frontend network trace confirms that readiness and integrity requests start
concurrently. Integrity is not the page-load bottleneck.

The local service snapshot during measurement showed high `market-intelligence`
CPU while readiness was being computed. A separate snapshot also showed high
Market Data CPU, but this observation does not prove which readiness sub-check
caused the load.

### Cache observation

No warm-cache response below the readiness computation time was observed during
the refresh sequence. The runtime therefore does not provide sufficient evidence
that the configured readiness cache is effective for these requests. This must be
verified with backend instrumentation or a controlled service restart/cache test
before changing cache policy.

### Backend and database detail

- Backend readiness sub-phase timings: not exposed by current logs/headers.
- Backend integrity sub-phase timings: not exposed by current logs/headers.
- Database query count and row volume: not exposed by current runtime metrics.
- Replay, fee, and OHLC call counts/timings: not exposed by current runtime
  metrics.
- Gateway responses include `Cache-Control: no-cache, no-store`; this is HTTP
  response policy and does not by itself prove or disprove the process-local
  readiness cache.

### Step 2 runtime validation

The `market-intelligence` image was rebuilt and recreated without restarting
databases or other services. The successful authenticated request after service
readiness returned:

- Readiness: `30,385 ms`, HTTP `200`.
- Integrity: `332 ms`, HTTP `200`.
- The local runtime has one configured research market, so OHLC lookup fan-out
  is one task and cannot produce a latency reduction from parallelism.
- The implementation therefore validates bounded execution and per-market
  failure isolation, but no runtime gain is expected until multiple configured
  markets exist or another independent historical lookup dimension is measured.

### Instrumented readiness runtime

After rebuilding with phase instrumentation, the current single-market runtime
reported:

| Measurement | First observed computation | Subsequent observed computation |
|---|---:|---:|
| Total readiness computation | `1,317 ms` | `32,407-36,242 ms` |
| Snapshot load | `53 ms` | `24-76 ms` |
| Outcome load | `58 ms` | `25-95 ms` |
| Latest OHLC evidence | `178 ms` | `11-53 ms` |
| S1 replay/fee phase | `997 ms` | `32,332-35,993 ms` |

The S1 phase is the dominant bottleneck. Database reads and OHLC evidence are
not candidates for the next optimization based on this evidence.

The logs also showed that the configured 30-second cache did not produce a cache
hit after a 32-36 second computation. The implementation calculated the cache
expiry from the timestamp captured before computation, so a computation longer
than the TTL could expire its result immediately on publication. This has been
corrected by starting the TTL after computation, with a regression test covering
the longer-than-TTL case. The post-fix runtime measurement confirmed the
expected warm-cache behavior.

## Decision

Step 1 is implemented and validated at the frontend contract level. Runtime
measurements show that Readiness is the dominant bottleneck, with a range of
approximately `23-41 seconds`, while Integrity is normally below `0.25 seconds`.
Step 2 is implemented as bounded, isolated OHLC lookup execution. Its runtime
gain is neutral for the current one-market configuration. Step 3 SQL projection
work is not justified by the measurements. Cache expiry has been corrected; the
post-fix authenticated Gateway measurement reduced the immediate second
readiness request to `177 ms` after a `26.20 s` cold computation. The rebuilt
frontend also confirmed concurrent request start (`1 ms` separation), with
approximately `29.98 s` readiness and `45 ms` integrity. Replay/fee
instrumentation then showed that the cold-cache bottleneck is remote replay:
`54` replay calls consumed `172.486 s` cumulative and `27.010 s` wall-clock,
while `68` fee calls consumed only `0.915 s`. SQL, snapshots, outcomes, and
OHLC remained below `60 ms` combined. Step 4 remains conditional.

Market Data replay profiling then showed that repository lookup accounts for the
replay latency (`6-813 ms` per call in the latest run), while reconstruction is
negligible. A replay cache was not added: ADR-022 requires explicit freshness
and invalidation semantics, and replay sequences can be invalidated by Market
Data.

PostgreSQL plan validation on the active `49 M`-row evidence table showed that
the replay query reads tens of thousands of deltas to reconstruct the requested
state. A temporary snapshot index improved one lookup but added `3.6 GB`; a
temporary ordinal index removed sorting but was slower. Both were removed. The
next meaningful optimization would therefore be precomputed replay state owned
by Market Data, which is outside the current Story scope and requires an
explicit invalidation policy.

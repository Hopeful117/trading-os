# Story 0069 Runtime Validation

**Date:** 2026-10-03  
**Mode:** Read-only facts-only runtime validation  
**Repository revision:** `4df802406b00454508cb35913e4d31e4182d77f2`  
**Branch:** `main`  
**Verdict:** `RUNTIME_FIX_REQUIRED`

## 1. Executive Summary

The remediated Market Facts endpoint was rebuilt from the current working tree
and exercised against live Kraken public OHLC data through the local Market Data
service.

Provider identity, market identity, quote-domain preservation, activity
arithmetic for three of four comparable reads, activity freshness, security, and
absence of trading side effects behaved as expected.

Runtime validation found a correctness defect in readiness: the current open
Kraken candle is counted as a cadence violation even though it is excluded from
the completed-candle set. For all four tested markets, the response reported
`INSUFFICIENT_DATA` with `missingIntervalCount=0`, `syntheticCandleCount=0`, and
`cadenceViolationCount=1`.

This contradicts the intended temporal coverage semantics and blocks closure.
No production fix was applied during validation.

## 2. Environment / Revision

- Branch: `main`.
- HEAD: `4df802406b00454508cb35913e4d31e4182d77f2`.
- The worktree was already dirty with pre-existing Story 0067/0069 changes;
  those changes were preserved.
- DevLog applicability lookup failed with `Error invoking method: execute`.
- Docker services were already running. The original Market Data image returned
  `404` for the Story 0069 endpoint because it predated the current endpoint
  implementation.
- Only the related `market-data` image was rebuilt and its container restarted.
  No trading service, database, broker, account, or execution workflow was
  started or modified.
- Kraken public `GET /0/public/SystemStatus` returned HTTP 200.
- Market Data was reached directly at `http://127.0.0.1:17083`.
- Gateway access was not used for the internal endpoint because it requires the
  separate user JWT boundary.

## 3. Safety Boundary

Only these operations were performed:

- Market catalogue reads;
- Market Data OHLC reads;
- authenticated Market Facts reads;
- unauthenticated authorization checks;
- application log inspection;
- focused and full Market Data tests.

No Active Scan, analysis execution, strategy match, opportunity, trade plan,
risk evaluation, execution intent, broker order, account mutation, position
mutation, or credential mutation was performed.

## 4. Validation Configuration

Comparable runtime requests used:

```text
interval                 = ONE_MINUTE
activityWindow           = 15 minutes
readinessLookbackCandles = 15
minimumCompletedCandles  = 1
maximumObservationAge    = 300 seconds
```

The request boundary was taken from each response's `generatedAt` and therefore
varied naturally by request time. Because the boundary was not aligned to the
minute, the implementation expected 14 fully completed one-minute candle opens
inside the 15-minute wall-clock window.

A separate freshness check used `maximumObservationAge=1 second` without
altering provider timestamps or system clocks.

## 5. Markets Tested

The requested `BTC/*` catalogue symbols were not present. Trading OS exposes
Kraken's canonical `XBT/*` symbols, so `XBT/USD` and `XBT/EUR` were used.

| Market | Market ID | Provider | Base | Quote |
|---|---|---|---|---|
| XBT/USD | `62a46ee0-ee10-42fa-8855-6e43724bb3e5` | KRAKEN | XBT | USD |
| ETH/USD | `a6ef86d6-a518-46ff-b84e-4270227f31ff` | KRAKEN | ETH | USD |
| PEPE/USD | `60f24baa-ba33-4e2b-aacf-fa86943d4604` | KRAKEN | PEPE | USD |
| XBT/EUR | `4fc9fca9-dbb8-401d-967a-4a9f84985fb9` | KRAKEN | XBT | EUR |

## 6. Provider Routing Results

All selected catalogue entries reported `provider=KRAKEN`. Each Market Facts
response preserved the matching market ID, provider, symbol, base asset, and
quote asset. The runtime path was configured with a Kraken base URL and returned
Kraken-mapped OHLC evidence.

The provider registry and non-Kraken unsupported behavior are additionally
`CODE_AND_TEST_PROVEN` by `MarketHistoryServiceTest`.

## 7. Provider-Observed Evidence

The runtime OHLC endpoint returned mapped provider evidence, not raw Kraken
payloads. The evidence was distinguished from normalized continuity evidence by
the `synthetic` field.

For each selected market:

- requested interval: `ONE_MINUTE`;
- observed completed candles: 14;
- expected completed candles: 14;
- synthetic candles: 0;
- duplicate candles: 0;
- conflicting duplicates: 0;
- missing intervals: 0;
- latest eligible evidence: the most recently completed minute reported by the
  provider.

## 8. Temporal Coverage Results

| Market | Expected | Observed | Missing | Synthetic | Cadence violations | Status | Reason |
|---|---:|---:|---:|---:|---:|---|---|
| XBT/USD | 14 | 14 | 0 | 0 | 1 | `INSUFFICIENT_DATA` | Requested completed candle coverage is incomplete |
| ETH/USD | 14 | 14 | 0 | 0 | 1 | `INSUFFICIENT_DATA` | Requested completed candle coverage is incomplete |
| PEPE/USD | 14 | 14 | 0 | 0 | 1 | `INSUFFICIENT_DATA` | Requested completed candle coverage is incomplete |
| XBT/EUR | 14 | 14 | 0 | 0 | 1 | `INSUFFICIENT_DATA` | Requested completed candle coverage is incomplete |

The `cadenceViolationCount=1` was caused by the current open provider candle
being considered by the cadence-violation scan. It was not part of the
completed observed count, but it still forced the non-available status.

This is a runtime semantic mismatch, not an absence of provider coverage.

## 9. Normalized Continuity

`NO_REAL_GAP_OBSERVED`.

All four runtime samples had zero synthetic candles and zero missing intervals.
No provider gap was manipulated or fabricated. Synthetic-continuity behavior
remains covered by automated regression tests rather than this runtime sample.

## 10. Market Activity Arithmetic

Independent recomputation used the eligible completed mapped OHLC observations
from the adjacent Market Data OHLC read, with exact decimal accumulation via
`bc`, excluding open, synthetic, missing, negative, and unusable observations.

| Market | Quote | Eligible | Recomputed | Market Facts | Difference |
|---|---|---:|---:|---:|---:|
| XBT/USD | USD | 14 | 338863.497172920 | 338863.497172920 | 0 |
| ETH/USD | USD | 14 | 135745.5674692497 | 135745.5674692497 | 0 |
| PEPE/USD | USD | 14 | 10359.38987340206921 | 10413.38987340206921 | 54.00000000000000 |
| XBT/EUR | EUR | 14 | 64629.833232515 | 64629.833232515 | 0 |

The PEPE mismatch was reproduced across separate adjacent provider reads, but
the current contract does not expose the individual mapped observations used by
the Market Facts calculation. Therefore the exact source of the 54-unit
difference cannot be isolated between provider snapshot timing and the fact
acquisition snapshot. It is retained as an unresolved runtime finding rather
than silently normalized.

## 11. Zero / Unusable Evidence

- PEPE/USD contained zero-volume candles with zero VWAP; these were observed as
  zero-contribution evidence.
- Positive-volume zero-VWAP candles: not observed.
- Null volume/VWAP: not observed.
- Negative volume/VWAP: not observed.

The corrected zero/unusable rules remain covered by automated tests and code
inspection.

## 12. Activity Freshness

With `maximumObservationAge=300 seconds`, activity returned `AVAILABLE` for all
four markets. Its `latestEligibleEvidenceTime` was the latest completed candle
close and was within the configured freshness boundary.

With `maximumObservationAge=1 second` on XBT/USD, activity returned `STALE`.
The latest completed evidence was older than one second at validation time. This
was a natural freshness calculation against the provider timestamp, not a
modified timestamp.

## 13. Readiness Freshness

With the normal five-minute freshness boundary, readiness evidence was fresh but
reported `INSUFFICIENT_DATA` because of the cadence issue described above.

With `maximumObservationAge=1 second`, XBT/USD readiness returned `STALE` with
the reason `Latest completed OHLC evidence is outside the freshness boundary`.
The status matched the observed close time and configured age.

## 14. Cache Reuse

Runtime cache reuse for an `AVAILABLE` Market Facts response was **not proven**.
The readiness cadence defect caused the normal requests to be non-available, and
the implementation intentionally does not cache non-available results. Repeated
identical requests therefore recalculated and returned new boundaries.

Automated regression tests prove reuse for an available response and prove that
failed/non-available acquisition is not cached.

## 15. Cache Freshness Boundary

`NOT_RUNTIME_PROVEN` for an available cached fact. The controlled one-second
freshness request naturally returned `STALE`, but no available result could be
warmed into the cache while the runtime readiness defect remained present.

The deterministic clock-based regression test covers evidence-time expiry.

## 16. New Provider Data / Cache Refresh

`NOT_RUNTIME_PROVEN`. The runtime sequence observed newer latest evidence across
separate requests, but no available cached fact existed from which to validate
cache invalidation and refresh behavior.

## 17. Quote-Domain Separation

XBT/USD preserved `quoteAsset=USD` and XBT/EUR preserved `quoteAsset=EUR`.
Their quote-notional values were not converted, ranked, or treated as directly
interchangeable. This identity/domain separation behaved correctly.

## 18. Cross-Market Activity Observations

Within the same USD quote domain and same measurement configuration, the sampled
facts showed materially different observed quote-notional activity:

- XBT/USD: `338863.497172920`;
- ETH/USD: `135745.5674692497`;
- PEPE/USD: `10413.38987340206921`.

These are factual observations only. No opportunity, quality, probability, or
selection conclusion was drawn.

## 19. Provider Failure / Unsupported Provider Evidence

- Natural provider failure: `NOT_OBSERVED_IN_RUNTIME`.
- Provider failure cannot become a fabricated available zero fact:
  `CODE_AND_TEST_PROVEN` by automated regression tests.
- Unsupported non-Kraken provider routing: `CODE_AND_TEST_PROVEN` by
  `MarketHistoryServiceTest` and current provider registry inspection.

## 20. Internal Authorization State

- `trading-core` trusted Market Data service JWT: `CONFIGURED`.
- `trading-core` Market Facts caller: `CONFIGURED` by default behavior.
- `market-intelligence` Market Data service JWT secret in the running Market
  Data container: `NOT_CONFIGURED`.
- `MARKET_DATA_MARKET_FACTS_AUTHORIZED_CALLERS`: `NOT_CONFIGURED`; the default
  is therefore `trading-core` only.
- Authenticated trading-core Market Facts request: HTTP 200.
- Unauthenticated Market Facts request: HTTP 401.

This is not a Story 0069 failure. Before Story 0070 integration, deployment
must explicitly configure the Market Intelligence trusted secret and caller
allowlist without broadening other internal endpoints.

## 21. No-Side-Effects Verification

No unexpected trading side effects were observed. No matching execution,
opportunity, trade-plan, risk, order, or active-scan log entries were present in
the inspected trading service logs during validation.

**Unexpected side effects: NO.**

## 22. Cross-Market Result Matrix

| Market | Provider | Quote | Activity | Readiness | Expected | Observed | Synthetic | Missing | Dup | Conflict | Freshness | Quote Notional | Arithmetic |
|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---|---:|---|
| XBT/USD | KRAKEN | USD | AVAILABLE | INSUFFICIENT_DATA | 14 | 14 | 0 | 0 | 0 | 0 | fresh | 338863.497172920 | match |
| ETH/USD | KRAKEN | USD | AVAILABLE | INSUFFICIENT_DATA | 14 | 14 | 0 | 0 | 0 | 0 | fresh | 135745.5674692497 | match |
| PEPE/USD | KRAKEN | USD | AVAILABLE | INSUFFICIENT_DATA | 14 | 14 | 0 | 0 | 0 | 0 | fresh | 10413.38987340206921 | mismatch: 54 |
| XBT/EUR | KRAKEN | EUR | AVAILABLE | INSUFFICIENT_DATA | 14 | 14 | 0 | 0 | 0 | 0 | fresh | 64629.833232515 | match |

## 23. Automated Regression Results

All of the following passed against the current working tree:

```text
mvn -Dtest=MarketFactsServiceTest,MarketHistoryServiceTest test
mvn -Dtest=MarketDataSecurityIntegrationTest test
mvn test
```

The tests cover temporal gaps, duplicates/conflicts, provider routing, failure
recovery, cache freshness/reuse, metadata invalidation, unsupported routing, and
internal authorization. The runtime cadence defect was not represented by the
existing test fixtures because they did not include a current open candle in the
cadence-violation scan.

## 24. Runtime Findings

### HIGH - Current open candle falsely invalidates readiness

- Expected behavior: a current open candle is excluded from completed coverage
  and must not count as a cadence violation.
- Actual behavior: live Kraken requests reported one cadence violation while
  completed coverage was otherwise exact.
- Evidence: all four markets returned expected 14, observed 14, missing 0,
  synthetic 0, and cadence violations 1 with `INSUFFICIENT_DATA`.
- Likely component: `MarketFactsService.calculateReadiness()` cadence-violation
  filtering over mapped evidence.
- Impact: valid live readiness can be falsely reported non-available; cache reuse
  and downstream readiness consumption cannot be runtime validated reliably.

### MEDIUM - PEPE activity cannot be independently reconciled from adjacent reads

- Expected behavior: independent `sum(volume * vwap)` must equal
  `MarketActivity.quoteNotional`.
- Actual behavior: adjacent PEPE provider reads differed by exactly 54 quote
  units while XBT/USD, ETH/USD, and XBT/EUR matched exactly.
- Evidence: repeated read pairs produced the same 54-unit discrepancy.
- Likely area: provider snapshot timing/response consistency or insufficient
  exposure of the exact mapped evidence snapshot used by Market Facts.
- Impact: the runtime arithmetic path is not fully independently reproducible
  for PEPE from the currently exposed endpoints. No production fix was applied.

## 25. Remaining Unproven Cases

- Available-fact runtime cache reuse.
- Runtime evidence-time cache expiry after an available fact crosses its age
  boundary.
- Runtime cache refresh with newly completed provider data.
- Natural provider failure and unsupported-provider HTTP behavior.
- Real provider gap and synthetic continuity behavior; no gap occurred.
- Market Intelligence authorization in deployment; it is intentionally disabled
  in the current runtime.

These cases remain covered where applicable by deterministic code/tests, but are
not claimed as runtime-proven.

## 26. Story 0069 Closure Assessment

Provider routing, identity preservation, quote-domain separation, freshness
classification, security boundary, and no-side-effect behavior are supported by
runtime evidence. The core runtime readiness result is not truthful for the
normal live boundary because the current open candle creates a false cadence
violation. Activity arithmetic also has one unresolved cross-read discrepancy.

Story 0069 is not safe for human closure or Story 0070 consumption until the
runtime findings are reviewed and resolved.

## 27. Final Verdict

`RUNTIME_FIX_REQUIRED`

No Story 0070 implementation, Candidate Selection, ranking, Active Scan change,
commit, or push was performed.

## 28. Revalidation - 2026-10-03

This section preserves the original failed evidence above. It records the
corrective pass and must not be read as rewriting the first validation.

### Correction and deterministic evidence

The defect was reproduced before the fix with fourteen completed one-minute
candles, one provider current open candle, and an unaligned boundary. The
regression reported `cadenceViolationCount=1` before the correction and passed
with `cadenceViolationCount=0` after the correction. The production change is
local to completed-cadence validation: open provider evidence remains available
elsewhere but is not eligible for completed cadence.

The provider-shaped integration test also passed through the real Kraken
response mapping, normalization, history service, and Market Facts calculation.

### Live revalidation

The rebuilt `market-data` container started successfully. The public catalogue
read returned HTTP 200 and confirmed the same four Kraken market IDs used by the
original validation. The facts-only requests returned HTTP 401 for all four:

| Market | Facts-only request | Result |
|---|---:|---|
| XBT/USD | authenticated internal read | HTTP 401 |
| ETH/USD | authenticated internal read | HTTP 401 |
| PEPE/USD | authenticated internal read | HTTP 401 |
| XBT/EUR | authenticated internal read | HTTP 401 |

The running container has the expected
`TRADING_CORE_MARKET_DATA_SERVICE_JWT_SECRET` environment key, but its value is
empty in this runtime. No security bypass or secret fabrication was attempted.
Consequently, completed coverage, readiness, activity, cache reuse/expiry,
PEPE arithmetic reconciliation, and provider timing could not be revalidated
live. The deterministic test evidence is complete for the correction, but the
live verdict remains `RUNTIME_VALIDATION_INCOMPLETE`.

No authenticated facts request reached the calculation path, so no trading
side effects were possible or observed during this attempt. The original
no-side-effect evidence remains preserved in Sections 3 and 21.

## 29. Authenticated Revalidation - 2026-10-03

This section is the latest runtime evidence. Sections 28 and earlier preserve
the preceding blocked attempt and its historical verdict.

### Authentication recovery

The local runtime was revalidated with the existing service JWT configuration.
The secret value was not displayed or changed. The authenticated `trading-core`
caller received HTTP 200 from the Market Facts endpoint for all four selected
markets. Unauthenticated requests remained HTTP 401, and a token from an
unknown service remained HTTP 401.

The earlier HTTP 401 result was classified as a runtime configuration
propagation/stale-container issue, not as a code-level JWT contract failure.

### Runtime results

All four catalogue markets returned `expected=14`, `observed=14`,
`missing=0`, `synthetic=0`, duplicate/conflict counts of `0`, and no cadence
violations. Activity and readiness were `AVAILABLE` under the normal five-minute
freshness boundary. The current open candle was excluded from completed
coverage and did not invalidate readiness.

The one-second freshness check naturally returned `STALE`. Repeated identical
XBT/USD requests reused the same cached result, including its `generatedAt`
and activity values. After a newly completed provider candle, the runtime
returned newer evidence and a refreshed fact.

Quote-domain separation remained correct: XBT/USD used USD and XBT/EUR used EUR.
No execution, order, position, opportunity, or other trading side effect was
observed.

### PEPE arithmetic classification

XBT/USD, ETH/USD, and XBT/EUR independently reconciled exactly. PEPE/USD
produced a smaller but non-zero difference in a later adjacent-read comparison
(9 quote units, compared with the earlier 54-unit observation). Because the
Market Facts contract does not expose the exact mapped snapshot used for its
calculation, the discrepancy cannot be attributed deterministically to the
calculation path rather than provider snapshot timing. It remains
`STILL_INCONCLUSIVE`, not a confirmed production arithmetic defect.

### Latest assessment

The open-candle readiness defect is resolved and live authentication/runtime
validation is complete for the defined facts-only scope. Story 0069 is
`READY_FOR_HUMAN_CLOSURE`, with the PEPE cross-read discrepancy retained as an
explicit follow-up observation. No Story 0070 implementation, commit, or push
was performed.

# Story 0069 Independent Deep Read-Only Audit

**Date:** 2026-10-03  
**Scope:** Current repository implementation of Story 0069 - Market Facts Foundation  
**Mode:** Independent deep read-only audit  
**Implementation changes:** None  
**Tests/code changes:** None  
**Git integration:** None

## 1. Executive Summary

The implementation is structurally bounded and mathematically uses
`sum(volume * VWAP)` over completed, non-synthetic events. Its provider mapping,
record-based contracts, one-market request scope, and internal security boundary
are understandable and mostly deterministic.

It is not safe yet as the factual foundation for Story 0070. The most important
defect is in readiness: `requestedLookbackCandles` is used to select events and
to size acquisition, but not to require complete temporal coverage. A request
with a large lookback and `minimumCompletedCandles = 1` can therefore return
`AVAILABLE` for one fresh candle and no detected synthetic gap. A future
Candidate Selector could treat materially incomplete evidence as ready.

Additional high-risk issues are present:

- cache expiry is based on fact calculation time, not the age of the latest
  evidence, so an `AVAILABLE` fact can remain cached after it should be stale;
- activity has no freshness status of its own and can remain `AVAILABLE` while
  the aggregate readiness is `STALE`;
- Kraken's observed snapshot is pre-normalization and can retain duplicate
  candles that are counted twice in activity while the normalized side dedupes;
- provider dispatch is not selected from `Market.provider`; the current
  `MarketDataProvider` injection resolves the Kraken implementation, allowing a
  non-Kraken market to be queried through Kraken and relabeled with the market's
  provider;
- the internal security configuration authorizes only the configured
  `trading-core` caller, which is incompatible with a future
  `market-intelligence` consumer unless deployment configuration and trust
  boundaries are changed.

**Final verdict:** `FIXES_REQUIRED_BEFORE_0070`

Runtime facts-only validation may still be useful, but it must not be treated as
evidence that the contract is ready for candidate selection.

## 2. Audit Scope

Reviewed directly:

- Story 0069 and all Story 0069 artifacts;
- Story 0068 and the Market Facts investigation;
- ADR-006, ADR-010, ADR-014, ADR-033, and ADR-048;
- `InternalMarketFactsController`;
- `MarketFactsRequest`, `MarketFactsService`, and all Market Facts records;
- `MarketHistoryService`, `MarketDataProvider`, `KrakenMarketData`,
  `KrakenRestOhlcMapper`, and `OhlcHistoryNormalizer`;
- Market Data security configuration and service JWT validation;
- Market Facts tests and relevant OHLC normalization tests;
- current worktree state.

The DevLog applicability check was attempted for `trading-os` and failed with
`Error invoking method: execute`. No DevLog context was available. The
repository and accepted ADRs were used as authoritative sources for current
state and architecture.

The existing reports were treated as claims to verify, not as correctness
evidence.

## 3. Actual Runtime Path

The current path is:

```text
GET /internal/v1/market-facts/{marketId}
  + interval
  + activityWindowMinutes
  + readinessLookbackCandles
  + minimumCompletedCandles
  + maxObservationAgeSeconds
        |
        v
InternalMarketFactsController.find()
        |
        v
new MarketFactsRequest(...)
        |
        v
MarketFactsService.find() [synchronized]
        |
        +--> Clock.instant() and CacheKey.from(request)
        |
        +--> cache lookup / expiry by generatedAt + maxObservationAge
        |
        +--> MarketService.findById(marketId)
        |
        +--> historyLimit = max(activity candles, readiness lookback) + 1,
        |    capped at 720
        |
        +--> MarketHistoryService.findOhlcHistorySnapshot()
                    |
                    +--> MarketRepository.findById()
                    |
                    +--> MarketDataProvider.findOhlcHistorySnapshot()
                              |
                              +--> default compatibility method, or
                              |    KrakenMarketData override
                              |
                              +--> KrakenHttpClient.findOhlcHistory()
                              |
                              +--> KrakenRestOhlcMapper.extract()
                              |      raw JSON arrays -> DTO entries
                              |
                              +--> KrakenMarketData.mapEntries()
                              |      DTO entries -> OhlcEvent
                              |      closed = index < last index
                              |      fetchedAt = clock.instant()
                              |
                              +--> OhlcHistoryNormalizer.fillMissingIntervals()
                              |      sort, deduplicate, fill synthetic gaps
                              |
                              +--> MarketHistorySnapshot(
                                     mappedEvents,
                                     normalizedEvents
                                  )
        |
        +--> calculateActivity(observedEvents)
        +--> calculateReadiness(observedEvents, normalizedEvents)
        |
        +--> cache insertion / LRU eviction
        |
        v
ResponseEntity.ok(MarketFactsResponse)
```

The important correction to the implementation report's terminology is that
`observedEvents` are not raw provider payloads. They are mapped domain events
created before the continuity normalizer. They already contain local closed
state, source IDs, timestamps, and parsed numeric values.

## 4. Market Activity Audit

### Arithmetic

The calculation is genuinely:

```text
total = BigDecimal.ZERO
for each eligible event:
    total = total.add(event.volume().multiply(event.vwap()))
```

Evidence: `MarketFactsService.java:107-117`.

The same `OhlcEvent` supplies both volume and VWAP. There is no average-volume,
last-candle, or `sum(volume) * average(vwap)` substitution.

### Numeric behavior

- Numeric type: `BigDecimal`.
- Provider parsing: `new BigDecimal(value.asString())` in
  `KrakenRestOhlcMapper.java:127-142`.
- Rounding: none.
- Aggregation: sequential exact decimal addition/multiplication.
- Scale: inherited from parsed values and multiplication; no canonical scale is
  imposed.
- Overflow: ordinary `BigDecimal` overflow is not expected, but there is no
  domain precision/scale limit against pathological provider input.
- Null volume/VWAP: excluded from the numerator.
- Negative volume/VWAP: excluded from the numerator.
- Zero volume or zero VWAP: accepted because the predicate uses `signum() >= 0`.

The zero rule is under-specified. Zero volume can represent a valid no-trade
candle, but a positive volume with zero VWAP is not a plausible usable price.
The current implementation would report that event as eligible with zero
notional. This is a **MEDIUM** semantic risk because the Story requires an
explicit rule for zero/unusable values and the contract does not expose the
distinction.

### Evidence metadata

The activity fact contains market/provider/symbol/assets, interval, duration,
window start, observation boundary, counts, status, reason, and calculation
version. It does not contain source IDs, latest fetched timestamp, or an
explicit activity freshness status. This is insufficient provenance for a
consumer that wants to validate the activity fact independently of readiness.

## 5. Activity Window Audit

`activityWindow` is a wall-clock `Duration`, supplied as integer minutes over
HTTP. It is not a candle count and not a provider-native rolling ticker window.

Included activity events satisfy all of:

```text
!event.synthetic()
event.closed()
event.openTime() >= boundary - activityWindow
event.openTime() < boundary
```

Evidence: `MarketFactsService.java:99-105`.

The implementation therefore means:

> quote-notional summed over completed candles whose opening times fall inside
> the requested wall-clock interval.

It does not mean “the last N available candles.” This is the safer semantic for
gaps, but the boundary is based on candle open time rather than close time.

Boundary behavior:

| Event | Activity result |
|---|---|
| `openTime == windowStart` | Included if closed and non-synthetic |
| `openTime < windowStart` | Excluded, even if the candle closes inside the window |
| `openTime == boundary` | Excluded |
| `openTime > boundary` | Excluded |
| `closeTime == boundary` | Included when `openTime < boundary` and `closed` |
| `closeTime > boundary` with `openTime < boundary` | Included if `closed` is true; malformed/future close is not rejected |
| open candle | Excluded by `closed` |
| synthetic candle | Excluded by `synthetic` |
| duplicate mapped candle | Counted once per entry in `observedEvents`; see finding H3 |
| missing middle interval | Not included; activity may become incomplete only through count, not direct gap analysis |

For an interval-aligned boundary, open-time selection approximates complete
candles within the preceding duration. For an unaligned boundary, the first
partially overlapping candle is excluded and the current partially elapsed
candle is excluded. The fact can therefore have fewer candles than the
duration-derived expected count and become `INSUFFICIENT_DATA`.

## 6. Closed/Synthetic Candle Audit

### Closed candles

For Kraken REST history, `KrakenMarketData.mapEntries()` assigns:

```text
closed = index < entries.size() - 1
```

Evidence: `KrakenMarketData.java:140-154`.

This is a local inference based on Kraken's documented last-current-candle
behavior, not a provider field preserved in the REST response. The mapper
constructs `closeTime` from `openTime + interval`, and normalization preserves
the flag for existing events.

The normalizer creates synthetic events with `closed = true` and
`synthetic = true` (`OhlcHistoryNormalizer.java:133-160`). Market Activity
checks both flags, so synthetic candles cannot enter the observed numerator even
though their generated volume is zero.

### Edge risks

- A provider response with a reordered or malformed last entry can cause the
  local index rule to mark the wrong candle closed.
- A mapped event with `closed = true` and `closeTime > boundary` is accepted by
  both activity/readiness if its open time is before the boundary.
- No validation enforces `closeTime == openTime + interval` in Market Facts.

The synthetic exclusion invariant itself is correct:

```text
synthetic == true -> excluded
```

It does not rely merely on synthetic volume being zero.

## 7. Raw vs Normalized Evidence Audit

The Kraken snapshot is built as follows:

1. Kraken JSON arrays are parsed into `KrakenRestOhlcEntry`.
2. Entries are mapped to `OhlcEvent` objects.
3. The mapped list is retained as `observedEvents`.
4. The same mapped list is passed to `OhlcHistoryNormalizer`.
5. The normalized list is retained as `normalizedEvents`.
6. Both lists are truncated independently with `takeLastEvents()`.

Therefore `observedEvents` means:

> mapped provider response entries before sort, duplicate handling, and gap
> filling.

It does not mean raw provider bytes or an immutable provider semantic record.
The mapping has already:

- parsed numeric values;
- selected the first non-`last` Kraken result property;
- assigned `closed` by list position;
- assigned `fetchedAt` using the local clock;
- assigned `occurredAt` from Kraken's `last` response timestamp;
- created a deterministic source ID.

The separation is useful, but the name `observedEvents` overstates rawness and
the derived activity calculation does not apply the normalizer's duplicate
policy to that list.

## 8. Readiness Audit

The exact status precedence is:

1. No closed, non-synthetic observed events in the lookback -> `UNAVAILABLE`.
2. Latest observed `closeTime` older than `maxObservationAge` -> `STALE`.
3. Observed count below `minimumCompletedCandles` -> `INSUFFICIENT_DATA`.
4. Any normalized synthetic candle in the lookback -> `INSUFFICIENT_DATA`.
5. Otherwise -> `AVAILABLE`.

Evidence: `MarketFactsService.java:152-218`.

This precedence is deterministic. It gives `STALE` precedence over insufficient
count and synthetic gaps whenever at least one observed candle exists.

### Main correctness defect

`requestedLookbackCandles` is not itself a completeness requirement. It is used
to calculate the retrieval limit and the start time, but the status only checks
`observed.size() < minimumCompletedCandles`.

Example:

```text
requestedLookbackCandles = 120
minimumCompletedCandles = 1
one fresh observed candle
no normalized synthetic candle in the returned slice
```

Result: `AVAILABLE`.

This violates the Story requirement that readiness evaluate requested lookback,
temporal coverage, and raw completeness. It is a **HIGH** finding because a
future Candidate Selector can interpret an incomplete historical window as
ready.

### Other readiness issues

- `normalizedCandleCount` counts normalized events without filtering
  `closed`; it can include the current open candle.
- `missingIntervalCount` is assigned the synthetic count. It is not an
  independently calculated missing-interval count and does not detect leading
  gaps or all forms of duplicate loss.
- A middle gap is detected only when normalization creates a synthetic event and
  that synthetic event survives the final `takeLastEvents()` and lookback
  filters.
- A leading gap before the first provider event is not filled by the normalizer
  and can be missed by readiness.
- Duplicate mapped events can inflate `observedCompletedCandles` while
  normalization removes them, so the two counts can disagree materially.
- Provider failure and empty source both use `UNAVAILABLE`; the reason string
  differs, but there is no typed failure category.

## 9. Freshness Audit

The authoritative status timestamp is the maximum `closeTime` of filtered,
closed, non-synthetic observed events:

```text
boundary.isAfter(latestClose + maxObservationAge) -> STALE
```

`fetchedAt` is returned as metadata but does not control freshness. This is a
defensible data-recency choice for historical evidence: fetching old candles
now does not make the candles current.

Boundary behavior:

- age exactly equal to max: `AVAILABLE` because the comparison is strictly
  `isAfter`;
- age greater than max: `STALE`;
- empty evidence: `UNAVAILABLE`, not `STALE`;
- future `latestClose`: accepted as fresh;
- future or malformed close time with open time before the boundary: not
  rejected;
- synthetic latest candle: not used for latest observed close;
- clock source: injected `Clock` in `MarketFactsService` and Kraken adapter;
- hidden `Instant.now()` in the Market Facts path: none found.

The major freshness defect is cache interaction. The cache expiry is:

```text
generatedAt + maxObservationAge
```

not:

```text
latestObservedCloseTime + maxObservationAge
```

If the newest candle is already four minutes old at calculation time and the
maximum age is five minutes, the `AVAILABLE` result can be reused for nearly
four additional minutes while the evidence is already stale. This is a
**HIGH** finding.

## 10. Cache Key Audit

### Actual key

`CacheKey` contains exactly:

- `marketId`;
- `interval`;
- `activityWindow`;
- `readinessLookbackCandles`;
- `minimumCompletedCandles`;
- `maxObservationAge`.

Evidence: `MarketFactsService.java:273-286`.

### Result-affecting input assessment

| Input | Key state | Assessment |
|---|---|---|
| market ID | IN_CACHE_KEY | Correct for stable catalogue identity |
| interval | IN_CACHE_KEY | Correct |
| activity window | IN_CACHE_KEY | Correct |
| readiness lookback | IN_CACHE_KEY | Correct as a request parameter |
| minimum completed candles | IN_CACHE_KEY | Correct |
| maximum observation age | IN_CACHE_KEY | Correct for policy separation |
| provider metadata | OMITTED | **BUG_IF_OMITTED** if a market's provider changes while process lives |
| quote asset metadata | OMITTED | **BUG_IF_OMITTED** if quote metadata changes while process lives |
| base/symbol metadata | OMITTED | **BUG_IF_OMITTED** if catalogue metadata changes while process lives |
| observation boundary | OMITTED intentionally | Safe only if cache expiry is based on latest evidence; current expiry makes it unsafe |
| latest provider data | OMITTED intentionally | Safe only while the fact is fresh; current freshness expiry is too weak |
| calculation version | Constant in code, omitted | Safe across one process version; risky for hot reload/versioned long-lived caches |
| normalization behavior | Omitted | Safe only for process-lifetime immutable code/config |
| provider implementation | Omitted | Safe only if market identity/provider routing is stable |
| clock boundary | Omitted intentionally | Required for temporal reuse, but current expiry is insufficient |

The metadata omissions violate Story 0069's explicit invalidation requirement if
catalogue/provider metadata can change without process restart. This is a
**HIGH** cache correctness finding.

## 11. Cache Temporal Correctness

The cache is an access-ordered `LinkedHashMap` with a maximum of 256 entries.
An entry is accepted while:

```text
currentBoundary < generatedAt + request.maxObservationAge
```

This creates these behaviors:

| Scenario | Actual behavior | Assessment |
|---|---|---|
| AVAILABLE fact, evidence becomes stale before cache TTL | Old AVAILABLE result returned | **HIGH defect** |
| STALE result, provider recovers before cache TTL | Old STALE result returned | Medium availability risk |
| transient provider failure | `UNAVAILABLE` result cached for TTL | Medium recovery delay |
| unsupported provider | `UNSUPPORTED` result cached for TTL | Usually acceptable, but config changes are not observed |
| different request policy | Different key | Correct |
| time moves backwards | Entry may remain reusable longer | Clock operations are assumed monotonic; not guarded |
| new provider data arrives before TTL | Not observed | Expected cache behavior, but freshness TTL must be evidence-based |

The stale-AVAILABLE case is the primary temporal safety failure.

## 12. Cache Concurrency / Immutability

### Concurrency

`find()` is `synchronized` on the service instance. This makes cache lookup,
provider acquisition, calculation, and insertion mutually exclusive for all
markets and all request keys.

Consequences:

- cache map access is thread-safe;
- duplicate concurrent loads for the same key are prevented;
- cache bounds cannot be corrupted by concurrent insertion;
- provider calls for unrelated markets are globally serialized;
- one slow provider call blocks all other Market Facts requests;
- the implementation avoids a cache stampede by imposing a global lock rather
  than by using per-key coordination.

This is safe for memory correctness but a significant scalability risk for a
future 100-market Candidate Selection request.

### Immutability

- Facts and responses are Java records.
- `MarketHistorySnapshot` copies both input lists with `List.copyOf`.
- Kraken normalized lists are immutable `List.copyOf` results.
- `OhlcEvent` is a record with immutable value fields.
- The cache stores response records, not mutable collections.

No direct post-cache mutation path was found. The returned facts are effectively
immutable.

## 13. Request Bounds

The request constructor enforces:

- non-null market ID and interval;
- positive activity window;
- readiness lookback from 1 through 720 candles;
- minimum completed candles from 1 through the lookback;
- positive maximum observation age;
- activity window no longer than 720 requested interval candles.

The service then calculates:

```text
max(activityExpectedCandles, readinessLookback) + 1
```

and caps provider history at 720.

The one-request provider call bound is one in the current Kraken path. No hidden
retry, recursive acquisition, all-market lookup, or automatic timeframe
expansion was found in the Market Facts path.

Remaining bound weaknesses:

- `maxObservationAgeSeconds` has no upper bound;
- `Duration.ofSeconds(Long.MAX_VALUE)` is accepted by the controller;
- cache expiry can then overflow at `generatedAt.plus(maxObservationAge)` on a
  later request;
- `Duration.ofMinutes(Long.MAX_VALUE)` can overflow in the controller before
  the request constructor can reject it;
- a 720-candle lookback plus the provider's always-current final candle can
  yield at most 719 completed provider candles, but readiness does not require
  requested lookback completeness anyway.

These are **MEDIUM** bound/validation risks. The core provider request remains
capped at 720.

## 14. Provider Failure Semantics

Acquisition handling in `MarketFactsService` is:

- `UnsupportedOperationException` -> a 200 response containing
  `UNSUPPORTED` activity/readiness;
- any other `RuntimeException` -> a 200 response containing `UNAVAILABLE`;
- empty successful snapshot -> `UNAVAILABLE` from calculation;
- partial successful snapshot -> `INSUFFICIENT_DATA`, `STALE`, or `AVAILABLE`
  depending on readiness conditions.

Provider-specific exception messages are not exposed. Logs contain only market,
interval, and exception type.

The distinction between provider failure and empty provider response exists only
through the free-form `reason` string and not a typed failure category. The
status is `UNAVAILABLE` for both. This satisfies the minimum non-success safety
property but leaves brittle consumer observability. **MEDIUM**.

Unsupported status is also convention-based: providers must throw exactly
`UnsupportedOperationException`. An adapter that reports unsupported interval
using another exception becomes `UNAVAILABLE`.

## 15. Provider Neutrality

### Preserved neutrality

- Market Facts imports only `Market`, `OhlcEvent`, `OhlcInterval`, and generic
  domain/provider enum types.
- No Kraken DTO, pair syntax, array index, or Kraken error appears in the fact
  records or service.
- Kraken-specific parsing remains in `KrakenRestOhlcMapper`.

### Provider routing defect

`MarketHistoryService` injects one `MarketDataProvider` instance and calls it
with the `Market` object. The current repository has `KrakenMarketData` as the
provider implementation, while `MarketProvider` contains KRAKEN, FTMO, BINANCE,
COINBASE, and BYBIT. No provider registry or provider-name dispatch was found.

For a non-Kraken `Market` reaching this service, the current wiring can invoke
Kraken acquisition and then map the returned events using `market.getProvider()`.
That can produce a fact labeled as another provider while sourced from Kraken.

This is a **HIGH** future-provider neutrality finding. It may not affect a
deployment whose catalogue contains only Kraken markets, but the domain model
and contract currently imply broader provider support than the acquisition
boundary actually enforces.

## 16. Quote-Domain / Comparability Audit

`baseAsset` and `quoteAsset` come directly from persisted `Market` metadata, not
from symbol-string parsing. This is correct and avoids pair syntax assumptions.

The activity fact includes:

- provider;
- symbol;
- base asset;
- quote asset;
- interval;
- duration;
- observation boundary;
- calculation version.

A future consumer can distinguish `KRAKEN BTC/USD` from `KRAKEN BTC/EUR` and
from a different provider, at least on the activity fact. Readiness itself does
not include provider or quote asset and must be interpreted with the enclosing
response/activity/market metadata.

No comparison or ranking is performed in Story 0069. No currency conversion is
performed. The contract is therefore provider-local and quote-local in intent.

The contract does not explicitly expose a measurement unit or `measurementKind`.
`quoteNotional` plus `quoteAsset` is understandable for this one formula, but a
future provider with different volume semantics could be represented too
loosely. **MEDIUM** future extensibility risk.

If market metadata changes after a cache entry is created, the cached fact can
retain an old quote domain while the same market ID now represents new metadata.
This is part of the cache invalidation finding.

## 17. HTTP Contract Audit

Endpoint:

```text
GET /internal/v1/market-facts/{marketId}
```

| Parameter | Type | Required/default | Unit | Validation | Meaning |
|---|---|---|---|---|---|
| `marketId` | UUID path | Required | Identifier | UUID parsing | One explicit market |
| `interval` | `OhlcInterval` enum | Required | Enum | Enum binding | OHLC candle interval |
| `activityWindowMinutes` | long | Required | Minutes | Positive and <= 720 intervals after conversion | Activity wall-clock window |
| `readinessLookbackCandles` | int | Required | Candles | 1..720 | Readiness selection window |
| `minimumCompletedCandles` | int | Required | Candles | 1..lookback | Minimum readiness evidence |
| `maxObservationAgeSeconds` | long | Required | Seconds | Positive, no upper bound | Freshness policy |

GET is semantically defensible because the operation is a deterministic read and
the request is cacheable. The query is not side-effecting apart from provider
acquisition and local cache population.

`ResponseEntity` is used. Provider failure and unsupported outcomes are returned
as HTTP 200 typed fact results. This is defensible for a fact query but should be
documented for consumers.

Concrete HTTP weaknesses:

- request-constructor `IllegalArgumentException` is not mapped by a local
  `@ControllerAdvice`; invalid ranges are likely 500 rather than 400;
- unknown market is converted to `IllegalArgumentException` by
  `MarketFactsService`, also lacking an explicit 404 mapping;
- invalid duration overflow can fail before semantic validation;
- there are no HTTP tests for invalid request status, unknown market, security,
  unsupported provider, or provider failure serialization.

These are **MEDIUM** contract risks.

## 18. Security Audit

`MarketDataSecurityConfiguration` requires `ROLE_SERVICE` for `/internal/**`.
`ServiceJwtAuthenticationFilter` requires an `X-Service-Authorization: Bearer`
header, validates the service JWT, and rejects callers other than the configured
`authorizedCaller`.

Current application configuration sets:

```text
security.service-jwt.authorized-caller=trading-core
```

Therefore:

- anonymous request -> 401;
- user JWT without service header -> 401;
- invalid service token -> 401;
- valid service token from `trading-core` -> authorized;
- valid service token from `market-intelligence` -> 403 under current config;
- disabling JWT does not make the endpoint public because validation then fails.

No security weakening was introduced. However, the future consumer named by the
Story is Market Intelligence, while the current endpoint configuration authorizes
only Trading Core. Unless deployment configuration and service trust are
changed, the intended consumer cannot call the endpoint. This is **HIGH** for
AC5/future consumption, though it is a deployment-contract issue rather than an
anonymous access vulnerability.

## 19. Test Coverage Matrix

The six focused tests cover only a subset of dangerous semantics.

| Concern | Coverage | Evidence / gap |
|---|---|---|
| Basic `sum(volume * VWAP)` | COVERED | `MarketFactsServiceTest:51-67` |
| BigDecimal scale/rounding | NOT COVERED | No assertions on scale or high precision |
| Window start boundary | NOT COVERED | No equality/just-before cases |
| Cutoff boundary | NOT COVERED | No close/open boundary cases |
| Open candle exclusion | NOT COVERED | No `closed=false` test |
| Synthetic exclusion by flag | COVERED | `MarketFactsServiceTest:69-90` |
| Missing volume/VWAP | NOT COVERED | No null metric fixture |
| Zero volume/VWAP | NOT COVERED | Current ambiguity untested |
| Negative values | NOT COVERED | Predicate exists but no test |
| Middle raw gap | PARTIALLY_COVERED | Synthetic list is manually supplied; provider normalizer path is not exercised |
| Leading/trailing gap | NOT COVERED | No test |
| Exact duplicate | NOT COVERED | No test |
| Conflicting duplicate | NOT COVERED | No Market Facts test; normalizer test does not cover it |
| Stale boundary age == max | NOT COVERED | Only clearly stale evidence tested |
| Future timestamp | NOT COVERED | No test |
| Provider failure | COVERED | `MarketFactsServiceTest:124-134` |
| Empty provider response | NOT COVERED | No empty snapshot test |
| Unsupported provider | NOT COVERED | No `UnsupportedOperationException` test |
| Cache hit | COVERED | `MarketFactsServiceTest:106-122` |
| Cache expiry | NOT COVERED | Fixed clock cannot advance |
| Clock advancement | NOT COVERED | Clock is fixed in all service tests |
| Cache-key collision | NOT COVERED | No metadata/version/time collision test |
| Concurrent requests | NOT COVERED | No multithreaded test |
| 720-candle bound | NOT COVERED | No assertion of max request |
| Quote-domain distinction | NOT COVERED | Only one XBT/EUR market fixture |
| Cross-provider distinction | NOT COVERED | Only KRAKEN fixture |
| HTTP invalid input/status | NOT COVERED | Only successful request test |
| HTTP security | NOT COVERED | Standalone MockMvc bypasses security filter |
| Mutable cached response | PARTIALLY_COVERED | Records/copies inspected, no mutation test |

The green suite and JaCoCo result therefore do not establish semantic readiness
for Story 0070.

## 20. Story Acceptance Criteria Matrix

| AC | Requirement | Evidence | Result | Risk |
|---|---|---|---|---|
| AC1 | Deterministic quote-notional activity | `MarketFactsService:107-117`, BigDecimal arithmetic | PARTIAL | Zero/unusable rule is incomplete; duplicate events can double count |
| AC1 | Provider/market/quote/interval/window/boundary/version metadata | `MarketActivityFact` fields | PARTIAL | Source IDs/fetch freshness are absent |
| AC1 | Synthetic candles excluded | `MarketFactsService:100-102` | PASS | Explicit flag exclusion is correct |
| AC1 | Missing/zero/unusable values handled explicitly | `usableActivityValue()` | PARTIAL | Zero VWAP accepted; no documented semantic policy |
| AC1 | No cross-domain comparison | No comparison code; provider/quote fields present | PASS | Consumer still must enforce homogeneous groups |
| AC1 | Repeatability | Fixed Clock injection and pure calculation | PARTIAL | Cache/time behavior not tested across boundaries |
| AC2 | Request interval/lookback evaluated | Request fields and filtering | PARTIAL | Lookback is not a completeness requirement |
| AC2 | Completed candle count evaluated | `observed.size()` | PARTIAL | Compared only to caller minimum, not requested lookback |
| AC2 | Temporal coverage/gaps evaluated | Synthetic count only | FAIL | Leading gaps and some missing coverage can report AVAILABLE |
| AC2 | Freshness evaluated | Latest close versus max age | PASS | Cache can bypass recalculation after evidence becomes stale |
| AC2 | Synthetic continuity represented | `normalizedCandleCount`, `syntheticCandleCount` | PARTIAL | Normalized count includes open events; missing count aliases synthetic count |
| AC2 | Raw completeness separate from normalized continuity | Snapshot has two lists | PARTIAL | Raw list is mapped pre-normalization and duplicate counting is unsafe |
| AC2 | Typed statuses | `MarketFactStatus` and precedence | PASS | Unsupported convention and failure granularity remain weak |
| AC2 | Explainable structured reason | `reason` string | PARTIAL | Failure categories are not typed |
| AC3 | Existing bounded path | `MarketHistoryService`, provider port | PASS | One provider call in current path |
| AC3 | Explicit limits | Request validation and 720 cap | PARTIAL | Duration/age overflow paths exist |
| AC3 | No catalogue-wide scan | One market path | PASS | No hidden fan-out found |
| AC3 | Provider failure not successful empty fact | Typed non-available response | PASS | Error and empty both use UNAVAILABLE |
| AC3 | Ambiguous source remains diagnosable | Generic log/reason | PARTIAL | No typed failure category |
| AC4 | Equivalent reuse | Cache key and LRU cache | PASS | Reuse exists within current policy |
| AC4 | No incompatible reuse | Request parameters in key | PARTIAL | Market/provider/quote metadata changes are not keyed/invalidation-triggered |
| AC4 | Freshness/provenance visible | Readiness timestamps, activity boundary | PARTIAL | Activity lacks freshness/source IDs |
| AC4 | Expired/invalidated/missing behavior | TTL and LRU eviction | FAIL | TTL can outlive evidence freshness; no metadata invalidation |
| AC5 | Domain facts/statuses only | Records contain domain fields | PASS | No Kraken payloads |
| AC5 | No provider-specific leakage | Controller/service/model inspection | PARTIAL | Provider routing can mislabel non-Kraken markets |
| AC5 | Versioned internal ResponseEntity contract | Controller path and return type | PASS | Invalid error mapping remains |
| AC5 | Usable by Market Intelligence | HTTP shape is callable in principle | PARTIAL | Current auth allows only configured Trading Core caller; no client integration |
| AC6 | Non-goals preserved | No selection/trading code | PASS | No scope leakage found |
| AC7 | Required semantic tests | Six focused tests | FAIL | Duplicate, boundaries, 720, concurrency, key collisions, and unsupported cases absent |
| AC7 | Cost-bound tests | No explicit 720/provider-call assertion | FAIL | Bound exists in code but is not regression-tested |
| AC7 | Module validation | Existing report records 103 tests and `mvn verify` | PASS | Not independently rerun in this read-only audit |

## 21. ADR Compliance

### ADR-006 - Market Data responsibilities

Mostly compliant: Market Data owns provider-normalized public market information,
and no provider DTO leaves the adapter. Market Facts is a derived public fact,
not a trading decision. The ADR's statement that Market Data does not expose
business calculations creates a terminology boundary, but ADR-048 explicitly
assigns normalized public facts and source/freshness metadata to Market Data.
No unexpected new service boundary was introduced.

### ADR-010 - Market State

Compliant. Market Facts does not modify or replace `MarketState` and does not
make tradability decisions.

### ADR-014 - Trading Decision Pipeline

Compliant. No Market Facts path reaches Risk, TradePlan, Broker Service, or
execution. The facts remain upstream evidence.

### ADR-033 - Scanner orchestration

Compliant for current scope. No active/passive scanner orchestration or
catalogue-wide scope authority was added. The synchronized one-market endpoint
is not itself a scanner.

### ADR-048 - Evidence and authority

Partially compliant. Ownership and deterministic calculation boundaries are
correct, but provenance/freshness are not sufficiently complete for activity and
the readiness contract can overstate completeness. The evidence hierarchy is
not violated, but its source contract is not yet safe enough for Candidate
Selection.

### ADR requirement assessment

No unexpected ADR is required solely for the current bounded in-memory cache.
An ADR would become necessary if the project chooses a durable/distributed fact
projection, changes provider ownership/routing, or authorizes Market Intelligence
as a co-owner of facts.

## 22. Story 0070 Consumer Threat Model

| Scenario | Classification | Reason |
|---|---|---|
| Fresh complete homogeneous candles | SAFE_BY_CONTRACT | Arithmetic and basic status are deterministic |
| Requested lookback 120, minimum 1, one fresh candle | FOUNDATION_DEFECT | Readiness can be AVAILABLE without requested coverage |
| Middle missing candles represented by synthetic continuity | CONSUMER_MUST_HANDLE | Synthetic count exposes non-availability when retained |
| Leading missing candles | FOUNDATION_DEFECT | No leading coverage check can yield AVAILABLE |
| Duplicate provider candles | FOUNDATION_DEFECT | Activity can double count pre-normalized duplicates |
| Activity data stale but aggregate readiness ignored | FOUNDATION_DEFECT | Activity status can remain AVAILABLE without freshness |
| Activity data stale and consumer checks readiness | CONSUMER_MUST_HANDLE | Aggregate readiness exposes stale status, but contract does not enforce coupling |
| Provider outage | SAFE_BY_CONTRACT | Non-success typed status, though failure category is coarse |
| Empty provider result | CONSUMER_MUST_HANDLE | `UNAVAILABLE`, not a zero activity value, must be excluded |
| Unsupported source | CONSUMER_MUST_HANDLE | Depends on adapter throwing exact unsupported exception |
| USD versus EUR | SAFE_BY_CONTRACT | Quote asset is present on activity fact; consumer must group locally |
| Different providers | FOUNDATION_DEFECT | Current provider routing can use Kraken for non-Kraken metadata |
| Mixed observation boundaries | FOUNDATION_DEFECT | Cache reuse hides boundary changes until TTL expiry |
| Cache eviction | SAFE_BY_CONTRACT | Causes recomputation, not semantic mutation |
| Concurrent requests | CONSUMER_MUST_HANDLE | Results are safe but globally serialized and may time out |
| Process restart | CONSUMER_MUST_HANDLE | Cache loss causes provider reacquisition and possible status changes |
| Market metadata change | FOUNDATION_DEFECT | Cache key is market ID only for catalogue identity |
| Zero activity | CONSUMER_MUST_HANDLE | Zero can be valid; consumer must not treat it as unavailable |
| Zero VWAP with positive volume | FOUNDATION_DEFECT | Current value validation accepts questionable evidence |

## 23. Performance Assessment

No load test was run. Static cost assessment:

### One market

- Cold request: one provider REST call, up to 720 entries, one normalization pass,
  and one fact calculation.
- Normalization duplicate detection is effectively O(n^2) because each event
  scans the growing deduplicated list. At n=720 this is bounded but unnecessary
  work.
- Warm request: no provider call while the cache entry is accepted.

### Ten markets

- Cold: up to ten provider calls.
- Because `MarketFactsService.find()` is synchronized, calls are serialized in
  one service instance.
- One slow provider request blocks all other Market Facts callers.

### One hundred markets

- Cold: up to one hundred sequential provider calls and up to one hundred
  normalization operations.
- Warm: only keys still present and within the current cache policy avoid calls;
  256 entries can hold this cardinality for one request shape, but not many
  combinations or multiple processes.
- Response payload size is small relative to OHLC acquisition cost.

The process-local cache stops being a suitable foundation when Candidate
Selection needs repeated catalogue-wide scans, multiple application instances,
restart persistence, or predictable latency for tens/hundreds of markets. At
that point a maintained scheduled/batched fact projection with explicit
provenance and freshness is justified. Story 0069 intentionally does not create
that projection, but Story 0070 must not hide the fan-out behind a loop.

## 24. Live Validation Plan

No live provider validation was executed in this audit.

The smallest safe later validation should be facts-only:

1. Select 2-3 available Kraken catalogue markets, preferably BTC/USD, ETH/USD,
   and PEPE/USD; add BTC/EUR only if present and explicitly treat it as a
   separate quote group.
2. Use one interval, for example `FIFTEEN_MINUTES`, with an explicit activity
   window and readiness lookback that fit under 720 candles.
3. Call the internal endpoint using a service JWT from an explicitly authorized
   caller. Do not use a user token and do not invoke Active Scan.
4. Compare the returned activity to a separately captured provider OHLC response:
   verify completed events only, same-candle `volume * VWAP`, and no current
   candle contribution.
5. Verify normalized synthetic count against deliberately selected markets or a
   controlled fixture with a known gap.
6. Verify freshness by querying a known old observation policy and confirming
   `STALE`; repeat at the exact age boundary.
7. Repeat the same request and verify provider-call/cache behavior using service
   logs or a test harness. The current endpoint does not expose a cache-hit field.
8. Verify BTC/USD and BTC/EUR remain separate comparison domains and are never
   ranked against each other by this endpoint.
9. Simulate empty, malformed, unsupported, and provider-error responses in a
   test double. Do not trigger orders, TradePlans, Risk, execution, or scanner
   side effects.

## 25. Findings by Severity

### CRITICAL

None identified.

### HIGH

**H1 - Readiness can report AVAILABLE for an incomplete requested lookback.**  
Location: `MarketFactsService.java:184-199`. `requestedLookbackCandles` is not
compared to observed coverage; only `minimumCompletedCandles` controls count.
Leading gaps can also evade synthetic detection. This can directly mislead
Candidate Selection.

**H2 - Cache expiry can return AVAILABLE after evidence is stale.**  
Location: `MarketFactsService.java:44-50`. Expiry uses `generatedAt`, not
`latestObservedCloseTime`. A fact calculated from already-aged evidence can be
reused beyond its actual freshness boundary.

**H3 - Duplicate mapped provider events can be double-counted in activity.**  
Locations: `KrakenMarketData.java:114-126`,
`MarketFactsService.java:100-117`. The normalized list deduplicates, but the
activity calculation uses the pre-normalization mapped list. Exact duplicate
candles can inflate quote notional and observed count.

**H4 - Provider routing can mislabel non-Kraken facts.**  
Locations: `MarketHistoryService.java:22-23,47-51`,
`MarketDataProvider.java:13-31`. One injected provider is called without
provider-name dispatch, while `MarketProvider` contains multiple providers. A
non-Kraken market can be acquired through Kraken and labeled with its catalogue
provider.

**H5 - Activity has no freshness status of its own.**  
Locations: `MarketActivityFact.java:10-27`, `MarketFactsService.java:132-149`.
Activity can be `AVAILABLE` while readiness is `STALE`; a consumer that uses the
activity sub-fact without coupling to readiness can select on stale notional.

**H6 - Current security configuration blocks the intended future consumer.**  
Locations: `application.properties:29-32`,
`ServiceJwtAuthenticationFilter.java:37-47`. Only the configured
`authorizedCaller=trading-core` is accepted. A `market-intelligence` caller is
forbidden under current configuration, despite AC5 naming that adapter as a
consumer.

**H7 - Cache invalidation is incomplete for result-affecting market metadata.**  
Location: `MarketFactsService.java:273-286`. Provider, quote asset, symbol, and
other market metadata are not in the key and no explicit invalidation exists.
The Story explicitly requires invalidation/recomputation when provider or quote
domain changes.

### MEDIUM

**M1 - Zero VWAP and zero/unusable semantics are not explicitly defined.**  
Location: `MarketFactsService.java:243-247`. Non-negative zero values are
accepted without distinguishing valid zero-volume candles from unusable price
data.

**M2 - Snapshot terminology and provenance are weaker than claimed.**  
Location: `MarketHistorySnapshot.java:5-13`,
`KrakenMarketData.java:114-126`. The observed side is mapped pre-normalization,
not raw provider data, and activity does not retain source IDs/fetch timestamp.

**M3 - Readiness coverage fields overstate what is measured.**  
Location: `MarketFactsService.java:166-210`. Normalized count includes open
events, and `missingIntervalCount` is just synthetic count; leading gaps,
duplicates, and exact expected cadence are not represented independently.

**M4 - Failed and stale facts are cached for the same policy TTL.**  
Location: `MarketFactsService.java:61-77`. A transient outage can keep returning
`UNAVAILABLE`, and recovered data can remain hidden until expiry.

**M5 - Invalid HTTP requests and unknown markets lack explicit error mapping.**  
Location: `InternalMarketFactsController.java:24-40` and absence of a controller
advice. Range violations and unknown markets are likely 500 instead of typed
400/404 responses.

**M6 - Duration and age overflow paths are not bounded.**  
Locations: `InternalMarketFactsController.java:28-39`,
`MarketFactsRequest.java:14-40`. Very large long values can overflow duration
construction or cache expiry.

**M7 - Unsupported classification depends on one exception type.**  
Location: `MarketFactsService.java:61-77`. Adapter-specific unsupported errors
using another runtime exception become generic `UNAVAILABLE`.

**M8 - Global synchronization serializes unrelated markets.**  
Location: `MarketFactsService.java:41`. This is memory-safe but creates a
latency bottleneck for future multi-market consumption.

**M9 - Focused tests omit most temporal, duplicate, bound, security, and
concurrency hazards.**  
Location: `MarketFactsServiceTest.java:51-170` and
`InternalMarketFactsControllerTest.java:1-55`. See the coverage matrix.

### LOW

None classified as merely low. The untested items are material because the
component is intended to become a selection foundation.

### INFO

- BigDecimal arithmetic itself is sound and has no rounding step.
- Records and copied lists provide effective immutability.
- No hidden N-times-timeframe or catalogue-wide fan-out was found inside one
  endpoint request.
- No security weakening, order path, TradePlan path, Risk bypass, commit, or
  live action was found.

## 26. Required Fixes Before 0070

1. Make readiness require and report the requested temporal coverage, not only a
   caller-selected minimum count. Detect leading, middle, trailing, duplicate,
   and cadence gaps deterministically.
2. Make cache reuse respect the age of the latest evidence, or otherwise prove
   that a cached status cannot outlive its freshness contract.
3. Prevent duplicate observed candles from inflating activity while retaining
   enough provenance to explain deduplication/conflicts.
4. Make provider routing explicit and fail closed when no provider implementation
   matches `Market.provider`.
5. Couple activity freshness to the fact contract so Candidate Selection cannot
   consume an `AVAILABLE` stale activity sub-fact accidentally.
6. Resolve the service-to-service authorization contract for the actual future
   Market Intelligence caller.
7. Add explicit cache invalidation or keying for provider/quote-domain metadata
   changes.
8. Define zero/unusable metric semantics and expose sufficient source/freshness
   provenance in the activity fact.
9. Add regression tests for the uncovered matrix before accepting the contract
   as a Candidate Selection dependency.

## 27. Recommended Non-Blocking Improvements

- Map invalid request and unknown-market failures to explicit HTTP statuses.
- Bound maximum freshness duration and guard duration arithmetic overflow.
- Replace free-form failure reasons with typed diagnostic categories while
  retaining safe human-readable explanations.
- Replace the global service lock with per-key coordination if throughput
  requirements justify it.
- Add explicit measurement kind/unit metadata before supporting additional
  providers or volume semantics.
- Add observability for cache hit/miss, provider-call count, fact status, and
  evidence age.
- Consider a maintained batched fact projection before scanning tens or hundreds
  of markets repeatedly.

## 28. Final Verdict

**FIXES_REQUIRED_BEFORE_0070**

Story 0069 is not yet a sufficiently safe factual foundation for Candidate
Selection. The implementation can be runtime-validated in isolation, but the
current readiness, cache freshness, duplicate handling, provider routing, and
future-consumer authorization issues must be resolved and regression-tested
before Story 0070 consumes these facts for deterministic selection decisions.

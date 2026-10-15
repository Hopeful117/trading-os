# STORY 0107A - DURABLE HISTORICAL OHLC EVIDENCE FOUNDATION

DATE: 2026-10-15
REPOSITORY HEAD: `557fdeaf5c6ef3e8754fcdbf94ad47e994c64042`
ORIGIN/MAIN: `557fdeaf5c6ef3e8754fcdbf94ad47e994c64042`
BRANCH: `main`

This investigation produced documentation only. No production code, migration,
repository, endpoint, provider adapter, job, commit, push, merge, or pull
request was created.

## 1. Executive Summary

Market Data currently normalizes provider OHLC responses into `OhlcEvent`, but
history is acquired on demand from Kraken and is limited to the provider's
current response. There is no durable normalized OHLC evidence repository or
as-of query.

The smallest trustworthy foundation is a new Market Data historical-evidence
model backed by PostgreSQL. It should store immutable provider-neutral candle
observations, retain source and acquisition provenance, distinguish captured
and backfilled origin, and expose a bounded internal as-of query. `OhlcEvent`
should remain the acquisition/transport model and be mapped into the durable
model rather than becoming persistence authority by accident.

The foundation can proceed with explicit limitations. It should support
bounded backfill plus forward capture, but must distinguish reconstructed
provider history from evidence Trading OS actually observed at the historical
time. Strict observed replay must never silently call a live provider.

## 2. Repository State

The required commands were executed after `git fetch origin`:

| Item | Result |
|---|---|
| Branch | `main` |
| `HEAD` | `557fdeaf5c6ef3e8754fcdbf94ad47e994c64042` |
| `origin/main` | `557fdeaf5c6ef3e8754fcdbf94ad47e994c64042` |
| Relationship | Local branch up to date with `origin/main` |
| Worktree | Existing untracked Story 0107 report preserved; no production modifications |
| Fetch | Succeeded |

The untracked directory `docs/architecture/stories/0107-historical-replay-and-objective-outcomes/` existed before this investigation and was not modified.

## 3. Previous Story 0107 Findings Verification

The previous investigation remains accurate against current code:

| Finding | Verification |
|---|---|
| No durable general normalized OHLC repository | Market Data has repositories for markets, price observations, and valuation snapshots; none for OHLC. |
| Current history provider-backed and bounded | `MarketHistoryService` delegates to a provider and caps requests at 720. |
| MI reads interval plus limit | `MarketDataClient.findOhlc` accepts market ID, interval, and limit only. |
| No general cutoff/from/to contract | Market Data service and public controller expose no as-of or range parameter. |
| Futures client may support ranges | `KrakenFuturesChartsHttpClient` accepts optional `from` and `to`, but current caller passes null. |
| `OhlcEvent` is richer than `OhlcPoint` | `OhlcEvent` carries closure, occurrence, synthetic, source, provider, and fetched metadata; `OhlcPoint` does not. |
| Market Data should own facts | Current service/provider boundary already places provider acquisition and market identity in Market Data. |
| MI should own analysis | MI contributors convert responses into context payloads and deterministic capabilities consume them. |

## 4. Relevant ADRs

- ADR-020: Market Intelligence ownership and shared context.
- ADR-021: Analysis execution and retention boundaries.
- ADR-035: Live and backtest evaluator compatibility.
- ADR-048: Evidence authority, immutability, lineage, and status semantics.
- ADR-049: Confirmed Market Structure and confirmation-time honesty.
- ADR-056: Normalized facts, deterministic features, versioned analyses, and future interpretation.

These ADRs support Market Data ownership of source facts, explicit temporal
boundaries, immutable lineage, and reuse of deterministic analysis semantics.

## 5. Current OHLC Architecture

The current path is:

```text
Kraken REST/WebSocket or Futures Charts
    -> Kraken DTO
    -> Kraken mapper
    -> OhlcEvent
    -> MarketHistoryService / OhlcEventPublisher
    -> MarketController or MarketDataClient
    -> OhlcResponse
    -> HistoricalOhlcContextContributor
    -> OhlcPoint / IntelligenceContext
```

The path has no durable OHLC write stage. A normal historical request reaches a
provider each time.

## 6. Provider Acquisition Path

Spot REST uses `KrakenHttpClient.findOhlcHistory(pair, interval)`. Kraken's
response includes a provider `last` timestamp and an array of candle arrays.
The client does not expose a requested start or end time.

Linear perpetual futures use `KrakenFuturesChartsHttpClient.getCandles` with
`count`, `from`, and `to`. `KrakenMarketData` currently supplies `count` and
passes null for both range parameters.

The WebSocket path is spot-only in the inspected handler. It maps each message
with the message timestamp and determines closure by comparing the entry
timestamp with the message timestamp.

## 7. Normalization Path

`KrakenRestOhlcMapper` converts provider arrays to `KrakenRestOhlcEntry`,
derives `closeTime` from `openTime + interval.duration`, and maps values to
`OhlcEvent`.

`KrakenFuturesOhlcMapper` converts epoch-millisecond chart candles to
`OhlcEvent`, derives close time, and determines closure from the injected clock.

`OhlcHistoryNormalizer` sorts, checks interval consistency, detects duplicate
open times, rejects conflicting duplicate content, and creates synthetic
continuity events for gaps. `MarketHistorySnapshot` intentionally separates
provider-observed events from normalized continuity events.

## 8. OHLC Model Inventory

| Class / record | Module | Purpose | Provider-neutral | Persisted | Temporal/provenance fields |
|---|---|---|---|---|---|
| `OhlcEvent` | Market Data | Normalized provider event | Mostly; includes provider | No | open, close, occurred, fetched, closed |
| `MarketHistorySnapshot` | Market Data | Observed vs normalized history | Yes | No | Inherited from events |
| `OhlcInterval` | Market Data | Supported interval enum | Yes | No | Duration and provider minutes |
| `MarketDataReadiness` | Market Data | Computed series readiness | Yes | No | boundary, latest close/fetch, gap counters |
| `MarketActivityFact` | Market Data | Computed activity fact | Yes | No | window and observation boundary |
| `OhlcResponse` | MI adapter | Feign wire DTO | Yes at boundary | No | Same fields as event, string interval |
| `OhlcPoint` | MI domain | Compact analytical candle | Yes | No | open/close and OHLCV only |
| `HistoricalOhlcContext` | MI domain | Context section payload | Yes | Serialized as part of some artifacts/results | Candle list and market ID |
| `MarketStructureCandle` | MI domain | Structure-specific projection | Yes | No | Structure input projection |
| `TrendContextCandle` | MI domain | Trend role projection | Yes | No | Trend-specific projection |
| Kraken DTOs | Market Data adapter | Provider payloads | No | No | Provider-specific timestamps/fields |

## 9. OhlcEvent Analysis

`OhlcEvent` contains market ID, provider, symbol, interval, open/close time,
OHLCV, vwap, trade count, closed, occurredAt, synthetic, sourceId, and
fetchedAt. It requires market, provider, symbol, interval, open/close time,
source ID, and fetchedAt, but does not validate price relationships or interval
consistency in its constructor.

Classification: **B - close to the correct source-evidence shape, but not yet
the durable evidence authority**.

It is a useful normalized acquisition event. It lacks explicit origin
(`LIVE_CAPTURE` versus backfill), a semantic `knownAt`, normalization version,
observation identity/version, and an immutable correction policy. Its
`occurredAt` meaning also varies by adapter: WebSocket message time, REST
response `last` time, or futures fetch-derived timing.

## 10. OhlcPoint Analysis

`OhlcPoint` exists to provide a compact Market Intelligence calculation input.
It intentionally removes provider and acquisition concerns from deterministic
analysis.

The conversion currently drops:

- `closed`;
- `occurredAt`;
- `fetchedAt`;
- `synthetic`;
- `sourceId`;
- provider identity;
- vwap and trade count;
- readiness, gap, duplicate, and conflict information;
- normalization version.

That loss is acceptable after an upstream contributor has established temporal
eligibility for a bounded live-shaped context. It is unsafe as the persisted
historical source evidence because the source cannot later prove why a candle
was eligible or which observation/version produced it.

`OhlcPoint` must not be persisted as the historical source model.

## 11. Current Persistence Inventory

Market Data currently persists:

- `Market` in the `markets` table;
- immutable `PriceObservation` in `price_observations`;
- immutable `ValuationSnapshot` and facts in valuation tables.

There is no OHLC entity, OHLC repository, OHLC migration, or historical OHLC
query. The in-memory `OhlcEventPublisher` is a multicast stream and deliberately
does not provide retention.

## 12. Historical Retention Gap

The current public endpoint `GET /api/v1/markets/{marketId}/ohlc` always calls
the provider-backed `MarketHistoryService`. Its only historical controls are
interval and limit, with a maximum of 720.

Market Intelligence therefore cannot request durable evidence as of a cutoff.
Provider fallback is currently implicit because provider acquisition is the only
path.

## 13. Temporal Field Semantics

The concepts should remain distinct:

| Concept | Meaning |
|---|---|
| `openTime` | Start boundary of the candle interval. |
| `closeTime` | End boundary implied by provider/open time and interval. |
| `occurrenceTime` | Time supplied by the source as the event/update observation time; current code maps this inconsistently by adapter. |
| `sourceUpdatedAt` | Provider/source correction or publication timestamp; not currently present in `OhlcEvent`. |
| `fetchedAt` | Time Market Data acquired the provider response/event; current `OhlcEvent` requires it. |
| `capturedAt` | Time the durable evidence writer accepted the normalized observation; not currently present. |
| `knownAt` | Earliest defensible time the evidence may be used by the selected temporal policy. |
| `closed` | Whether the source observation is treated as a completed candle. |

`fetchedAt` and `capturedAt` may be close but are not semantically identical.
`knownAt` should not be inferred from candle time alone.

## 14. Closed-Candle Semantics

The current spot REST path marks all returned entries except the last as closed.
The last entry is treated as forming because Kraken's endpoint includes the
current interval. The WebSocket path marks an entry closed when its provider
entry timestamp is before the message timestamp. Futures derives closure from
`closeTime <= clock.instant()`.

These are useful current-provider policies, but they should be normalized into a
durable source observation rather than re-derived later from today's clock.

## 15. Known-At Investigation

`knownAt` is required for strict observed replay. Current `OhlcEvent.fetchedAt`
is the closest available candidate, but it is not always sufficient because:

- REST mapping uses a clock instant after response acquisition;
- WebSocket mapping uses message timestamp as occurrence and a separate clock
  instant as fetched time;
- backfill acquisition today must not be represented as knowledge in the past;
- source corrections may have a source-update time distinct from fetch time.

The durable model should store explicit `knownAt`, with the writer deriving it
from the origin policy. For current captured data, `knownAt` should be no earlier
than the durable capture/acquisition instant and should conservatively use the
later defensible acquisition boundary. For backfill, `knownAt` is the backfill
acquisition time.

## 16. Proposed Eligibility-at-T Predicate

For strict observed replay, a provider-observed candle is eligible at cutoff T
when:

```text
eligibleObserved(c, T) =
    c.closed
    AND c.closeTime <= T
    AND c.knownAt <= T
    AND c.synthetic = false
```

The `closeTime <= T` condition prevents a forming interval from contributing
future high, low, close, or volume. `knownAt <= T` prevents a later acquisition
from being presented as historical knowledge.

For a generic evidence query, synthetic rows should not be silently discarded
by Market Data; however, the recommended source-evidence table does not persist
generated synthetic rows. Analyzer-specific rejection remains in Market
Intelligence.

## 17. Captured History Semantics

Captured history means Market Data received and durably accepted the normalized
evidence during operation. Its provenance should identify capture origin and
the acquisition/known-at boundary.

For a candle closing at 11:00 and fetched at 11:00:02, it is eligible no earlier
than 11:00:02 under strict observed replay. It is not eligible at 10:30 or
automatically at 11:00 unless the system has a defensible earlier source event
that proves final closure was known then.

## 18. Provider Backfill Semantics

A provider backfill acquired on Friday for a candle from Monday has:

```text
event interval: Monday
knownAt: Friday acquisition boundary
origin: provider historical backfill
```

It can support historical market-series research under a reconstructed-history
policy. It cannot support a claim that Trading OS literally knew the candle on
Monday.

## 19. Historical Truth vs Observed Truth

The system should retain both immutable observations when a provider later
returns a corrected value. A Monday captured C1 must not be overwritten by a
Friday provider C2.

Recommended policy: retain the original immutable observation and append a new
observation/version for the correction, with source/acquisition provenance and
an explicit relation to the same logical candle. A query must select a policy
explicitly rather than silently mutating audit history.

## 20. Candle Identity

The smallest provider-neutral logical candle identity is:

```text
marketId + interval + openTime
```

`closeTime` is derivable from interval and open time but should be stored or
validated as a consistency field. Provider symbol must not participate in the
canonical identity. Provider/source is provenance and may distinguish
observations for the same logical candle.

## 21. Observation / Version Identity

Each immutable evidence row needs a distinct observation identity, preferably a
UUID, plus a deterministic content/provenance fingerprint for idempotence.

The logical identity identifies the candle. The observation identity identifies
one accepted observation/version of that candle. A content fingerprint should
cover logical identity, OHLCV/provider fields, closure/synthetic state, source
identity, origin, normalization version, and the acquisition boundary needed to
distinguish meaningful observations.

## 22. Provider Provenance

The historical model should retain provider-neutral source fields such as:

- provider enum or source system identifier;
- provider/source identity;
- canonical market ID;
- provider symbol only as provenance;
- origin classification;
- source update time when available;
- fetched/captured/known-at timestamps;
- normalization version.

No raw headers, credentials, signatures, or provider request secrets belong in
the evidence record.

## 23. Market Identity

`Market.marketId` is a stable UUID and the entity has a unique provider/symbol/
product-type constraint. It also retains provider symbol, base/quote assets, and
product type.

This is sufficient for Story 0107A. `marketId` should be the foreign-key-like
identity in historical evidence. Provider symbols remain adapter/provenance
fields.

## 24. Interval Identity

Reuse the existing provider-neutral `OhlcInterval` enum. It defines supported
minutes and duration and prevents 1-minute and 15-minute rows from colliding.

Persist its stable enum representation or an equivalent versioned value object;
do not persist arbitrary provider interval strings as the authoritative value.

## 25. Numeric Semantics

Current authoritative OHLCV values use `BigDecimal`. PostgreSQL columns should
preserve exact decimal values with an explicit precision/scale policy compatible
with current market data entities, likely `numeric(38,18)` unless a reviewed
market-precision decision requires otherwise.

Do not convert persisted facts to `double`. Null volume should remain distinct
from zero volume. Zero volume is a valid possible no-trade fact when the source
contract permits it; it is not evidence of missing data.

## 26. Validation Rules

Before durable acceptance, validate:

- market ID, provider/source, interval, open time, and source identity present;
- `openTime < closeTime`;
- close time matches the interval policy;
- positive OHLC prices where the provider contract requires prices;
- `low <= open <= high` and `low <= close <= high`;
- non-negative volume and trade count when supplied;
- fetched/captured/known-at ordering is coherent;
- source observation is not future-dated relative to its capture boundary;
- provider/source identity is present;
- origin and normalization version are explicit.

Reuse `OhlcHistoryNormalizer` for ordering, interval checks, duplicate/conflict
detection, and gap inspection, but do not persist its synthetic output as if it
were provider evidence.

## 27. Synthetic Candle Policy

Synthetic candles are generated by `OhlcHistoryNormalizer` to represent
continuity gaps, using previous close and `synthetic=true`. They are useful for
some operational calculations but are not provider-observed facts.

V1 recommendation: do not persist synthetic candles in the durable source
evidence table. Preserve absence as a gap and let query/analysis readiness
derive the gap. If a future operational requirement needs persisted continuity,
store it as a separately identified derived artifact, never as source evidence.

## 28. Gap Policy

Do not fabricate missing provider candles. The source table should represent
observed/accepted evidence only. A historical series query may return bounded
evidence plus completeness metadata or allow Market Intelligence to calculate
gaps with the existing interval rules.

Explicit gap rows are not required for V1; gap detection from expected interval
boundaries is sufficient and avoids a second source-of-truth table.

## 29. Duplicate Policy

Repeated identical provider observations should be idempotent. The database
should enforce a unique deterministic observation fingerprint, not rely only on
application checks.

Repeated retrieval of the same immutable closed payload should not create an
unbounded duplicate history. A changed acquisition timestamp alone should not
create a new logical version when the source payload and relevant provenance are
identical.

## 30. Conflict / Correction Policy

Same logical candle plus different OHLCV is a conflict or correction, not an
ordinary duplicate. The durable writer must append a new observation/version and
retain the original. It must not silently overwrite.

Selection policy should be explicit:

- observed replay selects the observation whose known-at policy makes it
  eligible at T, preserving what was available then;
- reconstructed research may select a declared provider-latest or source
  version policy and must retain that policy in the dataset/replay lineage.

## 31. Forming Candle Policy

Forming observations contain mutable future high, low, close, and volume. They
must never satisfy strict historical analysis eligibility.

V1 should persist only closed provider-observed evidence for the durable replay
dataset. The live path may continue to publish forming `OhlcEvent`s in memory.
If future audit requirements need forming captures, they may be added as
append-only observations with `closed=false`, but that is not required for the
smallest historical deterministic foundation.

## 32. Normalization Versioning

The durable model should retain a normalization version. Current normalization
has explicit behavior for ordering, duplicate conflicts, gap synthesis, and
activity calculation basis, but no general persisted OHLC normalization version
was found.

An explicit version is required so later changes can distinguish historical
rows produced under different validation or mapping semantics. It should be
part of observation identity and lineage, not used to rewrite old rows.

## 33. Database Ownership

Market Data PostgreSQL should own durable OHLC evidence. Market Data owns market
identity, provider adapters, normalized source facts, and historical retention.

Market Intelligence should not duplicate raw OHLC storage. Trading Core,
Broker Service, News, and the Market Intelligence database are incorrect owners
for this fact domain.

## 34. Persistence Architecture Options

### Option A - Persist `OhlcEvent` directly

Small code change and preserves many existing fields. It incorrectly makes an
operational event record the durable authority, has no origin/known-at/version
semantics, and couples persistence to acquisition concerns.

### Option B - Dedicated `HistoricalOhlcEvidence`

Clear ownership and explicit temporal/provenance contract. One row per accepted
immutable observation can remain simple. Requires a mapping from `OhlcEvent` and
new schema/query code.

### Option C - Candle plus observation/version

Strong logical-candle and correction semantics, but two aggregates/tables and
more joins than V1 needs. Useful if correction selection or many source versions
becomes a high-volume requirement.

### Option D - Append-only observation events only

Excellent audit history and correction traceability, but query-time logical
candle reconstruction is more complex and can make ordinary as-of reads
expensive.

## 35. Recommended Persistence Architecture

Choose **Option B with observation/version semantics inside the dedicated
historical model**. Use one append-only `HistoricalOhlcEvidence` row per
accepted immutable provider observation, with logical candle dimensions and an
observation fingerprint.

Do not create a separate logical candle table in V1. Add a second table only if
future evidence shows that correction/version query volume or relationships
justify it.

## 36. Conceptual PostgreSQL Schema

Conceptual fields for `historical_ohlc_evidence`:

```text
observation_id              UUID primary key
market_id                   UUID not null
interval                    VARCHAR not null
open_time                   TIMESTAMPTZ not null
close_time                  TIMESTAMPTZ not null
open                        NUMERIC not null
high                        NUMERIC not null
low                         NUMERIC not null
close                       NUMERIC not null
volume                      NUMERIC nullable
vwap                        NUMERIC nullable
trades                      INTEGER nullable
closed                      BOOLEAN not null
synthetic                   BOOLEAN not null
provider                    VARCHAR not null
provider_symbol             VARCHAR nullable
source_id                   VARCHAR not null
source_updated_at           TIMESTAMPTZ nullable
fetched_at                  TIMESTAMPTZ not null
captured_at                 TIMESTAMPTZ not null
known_at                    TIMESTAMPTZ not null
origin                      VARCHAR not null
normalization_version       VARCHAR not null
observation_fingerprint     VARCHAR(64) not null
```

`closed` and `synthetic` remain explicit even if V1 only writes closed,
non-synthetic evidence. They make the contract future-safe and preserve source
validation semantics.

## 37. Constraints and Indexes

Required conceptual constraints:

- primary key on `observation_id`;
- unique observation fingerprint;
- non-null market, interval, temporal, numeric, source, origin, and version
  fields;
- check constraints for positive/ordered values where PostgreSQL expressions
  remain maintainable;
- foreign-key-like validation through the existing market identity boundary;
- `open_time < close_time`;
- `known_at <= captured_at` only if the chosen capture semantics guarantee that
  ordering; otherwise document and validate the actual ordering policy.

Required query indexes:

- `(market_id, interval, open_time)`;
- `(market_id, interval, known_at, close_time)`;
- optionally `(market_id, interval, open_time, known_at)` for version selection.

Do not add provider-symbol indexes as the canonical query path.

## 38. Write Path

Recommended future path:

```text
provider response/event
    -> adapter mapping to OhlcEvent
    -> provider-neutral validation
    -> origin/known-at assignment
    -> closed/non-synthetic evidence selection
    -> immutable persistence with fingerprint constraint
    -> return or publish event
```

Persistence should happen in Market Data after normalization and before a
historical response is considered durable. A provider HTTP call should not be
held open inside a large database transaction.

## 39. Historical Read Path

The future internal read path should be repository-first:

```text
historical repository
    -> enough eligible evidence?
        -> yes: return bounded result
        -> no: return explicit incomplete/unavailable
```

It must not automatically call a live provider to fill a strict replay query.
Backfill should be an explicit acquisition operation, not a hidden read-side
fallback.

## 40. As-Of Query Contract

Smallest future internal contract:

```text
marketId
interval
asOf
limit
origin/replay policy
```

The response should eventually include evidence plus completeness/provenance
metadata, not only a bare list. `from` and `to` are not required for the first
bounded latest-N query; add them only when a concrete replay requirement needs
range selection.

Recommended strict observed semantics:

```text
latest N evidence rows for marketId + interval
where closed = true
  and synthetic = false
  and closeTime <= asOf
  and knownAt <= asOf
ordered by openTime ascending
```

If multiple versions are eligible for one logical candle, version selection
must be explicit and deterministic.

## 41. Strict Replay Provider-Fallback Policy

Strict historical reads: **provider fallback is forbidden**. A missing range
must be reported as incomplete or unavailable.

Explicit research backfill may call the provider, persist the result with
backfill provenance, and then serve a reconstructed-history policy. It must not
silently change a strict observed replay into a provider query.

## 42. Current Kraken Historical Capability

The repository verifies:

- spot REST accepts pair and interval only;
- spot responses include a `last` timestamp and provider candle arrays;
- spot mapping marks the final returned row forming and earlier rows closed;
- futures charts accepts optional count, from, and to epoch-second parameters;
- current futures code uses count and passes null for from/to;
- futures mapper derives close time from interval and uses the current clock for
  closure;
- provider-specific historical pagination/synchronization is not implemented.

The repository does not establish that the current spot endpoint can retrieve
arbitrary historical ranges. Any future backfill support must remain bounded and
provider-capability-aware.

## 43. Backfill Options

### A - Capture forward only

Best observed-truth semantics and smallest temporal ambiguity. It delays useful
research history and depends on reliable capture scheduling.

### B - Bounded backfill plus forward capture

Provides useful initial data while preserving explicit reconstructed-history
semantics. It requires a bounded provider acquisition command and provenance.

### C - General historical synchronization

Potentially broad coverage but introduces pagination, quotas, retries, correction
reconciliation, scheduling, and operational complexity outside the smallest
foundation.

## 44. Recommended Backfill Policy

Choose **B**, but keep backfill bounded and explicit. Story 0107A should define
the evidence contract and persistence path, and may include one bounded
configured acquisition path if implementation scope permits. It must not build a
general synchronization platform.

Backfill rows use `knownAt` equal to acquisition time and origin indicating
reconstructed provider history. Forward capture is the only origin eligible for
strict observed replay at its historical capture time.

## 45. Research-Time Semantics

Trading OS should eventually support two auditable policies without conflating
them:

- observed replay: facts that Trading OS had accepted by T;
- reconstructed market-history research: provider historical facts whose candle
  intervals are closed and whose acquisition is later than T.

The second policy can bootstrap research without waiting years, but its outputs
must never be described as what the historical deployment knew.

## 46. Captured vs Reconstructed Replay Implications

Captured evidence supports deployment/audit replay and strict no-lookahead
claims. Backfilled evidence supports market-series characterization and
algorithm research under a different provenance policy.

Both can share the same persisted model if origin, known-at, source identity,
and selection policy are explicit. They must not share an implicit eligibility
claim.

## 47. Retention / Storage Estimate

Approximate candle rows per market per year:

| Interval | Rows/year |
|---|---:|
| 1m | 525,600 |
| 5m | 105,120 |
| 15m | 35,040 |
| 1h | 8,760 |
| 4h | 2,190 |
| 1d | 365 |
| All listed intervals | 677,075 |

All listed intervals produce approximately 6.77 million rows for 10 markets
per year and 33.85 million rows for 50 markets per year. Three years at 50
markets is approximately 101.5 million rows before indexes and correction
versions.

PostgreSQL is sufficient for a curated V1 set with bounded writes, indexes,
retention, and possibly future time/range partitioning. A time-series database
is not justified by current evidence.

## 48. Interval Retention Strategy

Do not persist every provider-supported interval by default. V1 should retain
the existing deterministic-analysis set: 15-minute, 1-hour, and 4-hour candles,
subject to configured market scope.

1-minute history should be added only when a concrete scanner/research
requirement justifies its approximately 15x storage cost over 15-minute data.

## 49. Aggregation Considerations

Deriving larger candles from 1-minute data is not automatically equivalent to
provider candles. Missing minutes, boundary conventions, volume basis,
synthetic handling, and provider correction semantics affect reproducibility.

Story 0107A should not implement a generic aggregation engine. Persist the
curated intervals required by current consumers. A later Story may establish a
versioned aggregation policy if research needs it.

## 50. Batch / Transaction Boundaries

Do not hold a database transaction across provider HTTP calls. Acquire a bounded
page, map and validate it, then persist a bounded batch in a short transaction.

Exact duplicate conflicts should be handled at the database constraint boundary
and reported as idempotent or conflicting according to the fingerprint policy.

## 51. Scheduling / Capture Strategy

The foundation needs a forward capture mechanism to become useful for observed
replay. The smallest approach is a configured Market Data capture process for
tracked markets and curated intervals, reusing provider-neutral acquisition and
the same durable writer.

A general scheduler fleet is not required in the investigation. User-triggered
reads alone are insufficient for reliable historical coverage.

## 52. Passive Scanner Compatibility

Historical persistence should remain a Market Data capability. A passive scanner
may consume the evidence or indirectly ensure tracked markets are captured, but
the evidence writer must not depend on AI agents or scanner strategy semantics.

## 53. Market Scope

V1 should use an explicit configured tracked-market scope, preferably reusing
existing subscriptions/watchlists/scanner scope when a canonical source is
identified during implementation. It must not attempt to capture every market
returned by synchronization.

## 54. Readiness / Completeness Metadata

Do not duplicate every readiness count on every candle. Existing
`MarketDataReadiness` already models normalized count, synthetic count, missing
intervals, cadence violations, duplicates, conflicts, latest close/fetch, and
status.

V1 should return or compute bounded series readiness at query time from stored
evidence. A future ingestion-batch metadata record may be justified for
operational diagnostics, but is not required for the source foundation.

## 55. Market Intelligence Boundary

Future MI should consume a provider-neutral internal contract such as:

```text
HistoricalOhlcSeriesRequest(marketId, interval, asOf, limit, evidencePolicy)
HistoricalOhlcSeries(
    marketId, interval, requestedAsOf, observations,
    completeness, gaps, provenance, normalizationVersion
)
```

The conceptual contract must not expose Kraken URLs, DTOs, pagination, or
database entities. MI can then convert eligible evidence into an
`IntelligenceContext @ T` and reuse existing deterministic analyzers.

## 56. Future Outcome Boundary

The evidence model is neutral enough to support future analysis facts at or
before T and future outcome facts after T. Story 0107A must only retain source
evidence and must not calculate MFE, MAE, forward return, or labels.

## 57. Future ML Boundary

No feature vector, training example, label, model ID, probability, or prediction
column belongs in the OHLC schema. Future ML consumes replayed deterministic
evidence and separately versioned outcomes through a feature-projection boundary.

## 58. Risk / Execution Boundary

Historical OHLC evidence must contain no account, balance, drawdown, position,
TradePlan, risk result, order, fill, fee, slippage, funding, or execution
simulation state.

## 59. Security

OHLC evidence is public market data, but provenance storage must exclude API
keys, credentials, authorization headers, signatures, and raw sensitive request
metadata. Provider symbols are acceptable as non-secret provenance. Access to
future research/backfill controls should follow internal service security
conventions and not become a public unauthenticated data-management API.

## 60. Observability

Future implementation should emit bounded structured metrics/logs for:

- provider candles fetched;
- normalized/accepted/rejected candles;
- exact duplicates;
- conflicts/corrections;
- gaps detected;
- invalid candles;
- persistence batch duration;
- oldest/newest stored open time;
- backfill duration and outcome;
- query completeness/unavailable status.

Avoid raw high-cardinality market IDs in metric labels if repository conventions
discourage them; bounded structured logs may include a market identifier.

## 61. Test Strategy

Future implementation requires unit and integration tests for:

- valid and invalid OHLC relationships;
- interval and close-time consistency;
- closed versus forming events;
- known-at and close-time boundaries;
- exact duplicate idempotence;
- conflicting observation append/version policy;
- captured versus backfill provenance;
- synthetic exclusion/preservation policy;
- market and interval isolation;
- deterministic ordering and bounded limits;
- strict reads that do not invoke providers;
- explicit incomplete results for missing ranges.

## 62. Golden Temporal Fixture

The proposed fixture is appropriate and should be part of implementation tests:

```text
C1: 09:45-10:00, knownAt 10:00:02
C2: 10:00-10:15, knownAt 10:15:03
C3: 10:15-10:30, knownAt 10:30:02
```

Expected strict observed results:

```text
asOf 10:14 -> C1
asOf 10:20 -> C1, C2
asOf 10:30 -> C1, C2
asOf 10:31 -> C1, C2, C3
```

The fixture must also include a forming C3 observation and a later corrected
version to prove no future or mutation leakage.

## 63. ADR Recommendation

A new ADR is justified because this Story creates durable cross-cutting
decisions about Market Data ownership, known-at semantics, captured versus
backfilled truth, immutable correction handling, and strict provider fallback.

Proposed title: **Historical Market Evidence, Known-At Semantics, and Source
Retention**.

Do not assign a number or mark it accepted until repository numbering and human
architecture approval are performed.

## 64. Story Sizing

Recommended size: **LARGE**.

Likely implementation work:

- domain evidence model and mapping: medium;
- PostgreSQL entity/migration/repository: medium;
- durable writer and idempotence: medium;
- as-of query contract: medium;
- bounded forward capture: medium;
- bounded backfill: medium/large depending provider capability;
- temporal and PostgreSQL integration tests: medium;
- observability and documentation: small/medium.

The full capture plus backfill plus query scope is larger than a small Story,
but coherent if bounded tightly.

## 65. Story Split Analysis

Option 1, one coherent Story for persistence plus as-of reads, is acceptable if
backfill is explicitly bounded and capture scope is limited.

Option 2, separate storage, acquisition/backfill, and query Stories, reduces
implementation risk but delays an end-to-end usable foundation.

Recommendation: split only if the approved scope includes both forward capture
and provider backfill. The first implementation Story should be durable storage
plus strict as-of reads and forward capture. Bounded backfill can follow as a
small additive Story if provider limitations or scheduling make the first Story
too large.

## 66. Persistence Decision Matrix

Scores are 1 to 5, weighted by the requested criteria. Higher is better; the
implementation-size criterion is scored for smaller effort.

| Architecture | Temporal 20 | Repro 15 | Provider 10 | Origin 10 | Replay 10 | ML 10 | Simple 10 | Storage 5 | Query 5 | Size 5 | Weighted |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Persist `OhlcEvent` | 2 | 2 | 3 | 1 | 2 | 2 | 5 | 4 | 4 | 5 | 2.40 |
| Dedicated evidence | 5 | 4 | 5 | 5 | 5 | 5 | 4 | 4 | 4 | 4 | 4.65 |
| Candle + version tables | 5 | 5 | 5 | 5 | 5 | 5 | 2 | 3 | 4 | 2 | 4.45 |
| Append-only observations | 5 | 5 | 5 | 5 | 5 | 5 | 2 | 2 | 2 | 2 | 4.20 |

The dedicated evidence model provides the best V1 balance. It retains the
important semantics without prematurely introducing two aggregates or a generic
event-sourcing system.

## 67. Acquisition Decision Matrix

Scores are 1 to 5, with higher better and temporal honesty/reproducibility
weighted above time-to-dataset.

| Acquisition | Useful quickly | Temporal honesty | Quota control | Complexity | Research usefulness | Reproducibility | Recommendation |
|---|---:|---:|---:|---:|---:|---:|---|
| Capture forward only | 1 | 5 | 5 | 5 | 2 | 5 | Too slow for bootstrapping |
| Bounded backfill + capture | 5 | 4 | 4 | 3 | 5 | 4 | Recommended |
| General synchronization | 5 | 3 | 1 | 1 | 5 | 3 | Defer |

Bounded backfill is useful only when its reconstructed-history semantics remain
explicit. It must not be used as a hidden strict-replay source.

## 68. Blocking Findings

No stop condition requiring abandonment was found:

- canonical market identity exists;
- provider closure semantics exist, although adapter meanings must be normalized;
- current normalization preserves enough source fields before MI compaction;
- the Market Data ownership boundary is stable;
- a bounded as-of predicate can be defined.

Important limitations remain:

- current spot provider does not expose arbitrary range parameters;
- current futures range capability is unused;
- current `OhlcEvent` occurrence semantics are adapter-dependent;
- no current source normalization version exists;
- no current durable OHLC schema exists;
- current captured WebSocket stream is not persisted.

These are implementation constraints, not reasons to invent a different owner
or downstream replay model.

## 69. Recommended Implementation Plan

1. Obtain human approval for the historical evidence ADR and Story scope.
2. Define the provider-neutral immutable evidence contract, origin, known-at,
   correction, and normalization semantics.
3. Add the Market Data PostgreSQL schema, constraints, and indexes.
4. Add mapping from normalized `OhlcEvent` to evidence observations.
5. Persist closed, non-synthetic forward-captured evidence with idempotence.
6. Add a bounded internal repository-first as-of query returning explicit
   completeness/unavailable results.
7. Add configured tracked-market forward capture using bounded batches.
8. Add bounded provider backfill only if the approved Story includes it, with
   reconstructed-history provenance and no strict replay fallback.
9. Add golden temporal fixtures, correction tests, and PostgreSQL integration
   tests.
10. Document the boundary consumed by future Story 0107B.

No step should implement IntelligenceContext reconstruction, deterministic
analysis orchestration, outcomes, ML, risk, execution simulation, or order-flow
history.

## 70. Final Decision

**B - PROCEED WITH STORY 0107A WITH EXPLICIT LIMITATIONS.**

The architecture is sufficiently clear for a bounded Market Data foundation.
Implementation must use a dedicated immutable evidence model, strict as-of
semantics, explicit captured/backfill provenance, repository-first historical
reads, and no silent provider fallback. General historical synchronization,
forming-candle retention, aggregation, and downstream replay remain deferred.

## 71. Repository Mutation Check

This investigation created one documentation artifact:

```text
docs/architecture/stories/0107A-durable-historical-ohlc-evidence-foundation/investigation-report.md
```

No production files, migrations, tests, configuration, provider code, or
existing Story 0107 files were modified. No commit, push, pull request, merge,
reset, stash, clean, revert, discard, or overwrite operation was performed.

Final `git status` and `git diff --check` are required after report creation.

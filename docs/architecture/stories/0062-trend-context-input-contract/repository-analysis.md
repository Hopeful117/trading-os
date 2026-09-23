# Repository Analysis - Story 0062

## Scope

Story 0062 establishes the immutable semantic input boundary for the future
deterministic Trend Context engine. It does not calculate pivots, structure,
direction, regime, breaks, EMA, ATR, phase, extension, alignment, or attention
outcomes. It also does not persist observations or change Strategy, Opportunity,
Risk, execution, or UI behavior.

The approved Story is consistent with the repository and with:

- `docs/architecture/adr/ADR-048.md`;
- `docs/investigations/deterministic-trend-context-domain-design-2026-09-23.md`;
- `docs/investigations/trading-intelligence-layers-architecture-2026-09-23.md`;
- `docs/investigations/conservative-trend-swing-trader-v1-2026-09-23.md`.

The repository convention requires this artifact at the Story directory and
does not require an index update.

## Sources Read

### Canonical domain sources

- Story 0062.
- Conservative trend-following swing trader investigation.
- Trading Intelligence layers architecture investigation.
- Hardened deterministic Trend Context domain design.
- ADR-048, accepted.

### Relevant ADR boundaries

- ADR-006: Market Data owns normalized public market facts.
- ADR-012: provider DTOs remain inside provider adapters and domain contracts are
  provider-independent.
- ADR-014: the decision pipeline is deterministic, explainable, and human
  terminated.
- ADR-020: Market Intelligence assembles context and coordinates analysis; it is
  not the source-data owner.
- ADR-021: context contributions preserve classification, source, freshness,
  execution boundaries, and source-service ownership; raw OHLC is not duplicated
  by default.
- ADR-025: observations are immutable, evidence-backed, and lineage-aware. This
  Story stops before observation creation.
- ADR-034: generic observations remain upstream of StrategyEvaluation and
  StrategyMatch. A favorable future assessment cannot directly create an
  opportunity.
- ADR-044: Market Data public OHLC reads may remain public; service identity is
  relevant for future internal acquisition but is not changed by this Story.

ADR-027, ADR-028, ADR-029, ADR-030, ADR-031, ADR-033, ADR-041, ADR-042, ADR-045,
and ADR-047 were not needed to determine this input boundary. Their Risk,
execution, planning, scanner, broker, and position-authority responsibilities
remain outside Story 0062.

## Current Repository Evidence

### Market Data domain contract

`market-data/.../model/OhlcEvent.java` is a Java record containing:

- `marketId`;
- `MarketProvider provider`;
- provider-neutral `symbol`;
- `OhlcInterval interval`;
- `openTime` and `closeTime`;
- OHLCV plus `vwap` and trade count;
- `closed`;
- `occurredAt`.

`OhlcInterval` is an enum with 1m, 5m, 15m, 30m, 1h, 4h, and 1d durations.
It exposes `fromMinutes` and the interval `Duration`, which is sufficient for
interval-boundary validation and role ordering.

`Market` is the persisted source identity owned by Market Data. It carries a
UUID, provider enum, provider-normalized symbol, base/quote assets, state, and
constraints. An OHLC event receives the market UUID, provider, and symbol from
this entity.

### Provider history path

The current REST path is:

```text
MarketController
  -> MarketHistoryService
  -> MarketDataProvider
  -> KrakenMarketData
  -> KrakenHttpClient
  -> KrakenRestOhlcMapper
  -> OhlcHistoryNormalizer
  -> takeLastEvents(limit)
  -> List<OhlcEvent>
```

The exact behavior is:

1. `GET /api/v1/markets/{marketId}/ohlc` accepts `OhlcInterval` and a default
   limit of 200.
2. `MarketHistoryService` resolves the Market entity and accepts limits from 1
   through 720.
3. `KrakenMarketData` calls Kraken with the provider symbol and interval
   minutes. Kraken's provider limit is also 720.
4. `KrakenRestOhlcMapper.extract` validates the response envelope, extracts the
   provider symbol and `last`, and maps the raw arrays into
   `KrakenRestOhlcEntry`.
5. `KrakenMarketData.mapEntries` maps each entry to an `OhlcEvent`. Every entry
   except the final Kraken entry is marked `closed=true`; the final entry is
   marked `closed=false`.
6. The current `occurredAt` passed to every REST event is
   `KrakenOhlcResult.last`, which is the provider response's last timestamp. It
   is not a local fetch timestamp and is not the individual candle occurrence
   timestamp.
7. `OhlcHistoryNormalizer.fillMissingIntervals` sorts by `openTime`, inserts
   flat candles for gaps, and returns the normalized list.
8. `takeLastEvents` truncates only after normalization, so a requested limit can
   include synthetic candles created outside the raw provider response.
9. `MarketController` returns `List<OhlcEvent>` directly. There is no dedicated
   history response DTO at this endpoint.

The websocket path is separate. `KrakenDataStreamProvider` maps websocket OHLC
entries through `KrakenOhlcMapper`. It sets `closed` by comparing the entry
timestamp with the websocket message timestamp and sets `occurredAt` to the
websocket message timestamp. Websocket events are published through
`OhlcEventPublisher`; they do not pass through the historical normalizer.

### Raw provider candle creation

Raw Kraken REST candles are created in `KrakenRestOhlcMapper.mapEntry` from the
provider's positional array:

```text
open time, open, high, low, close, vwap, volume, trades
```

The provider mapper validates array shape and required numeric values but does
not validate OHLC relationships, duplicate identity, interval boundaries, or
future/cut-off eligibility.

The provider's `last` timestamp is retained in `KrakenOhlcResult` and then copied
to every mapped event's `occurredAt`. The raw payload contains no provider candle
ID, source version, fetch timestamp, synthetic flag, or explicit correction
version.

### Normalization

`OhlcHistoryNormalizer` currently:

- returns an empty list for null or empty input;
- sorts by `openTime` only;
- retains duplicate open times;
- does not reject conflicting duplicate values;
- compares only successive `openTime` values against the requested interval;
- creates a synthetic candle for each missing interval;
- copies market ID, provider, symbol, and interval from the previous candle;
- uses the previous close for open/high/low/close and VWAP;
- sets volume to zero, trades to zero, and `closed=true`;
- copies the previous event's `occurredAt`;
- returns an immutable list.

There is no normalization result object and no gap list. The inserted candles are
ordinary `OhlcEvent` instances, so the current contract cannot distinguish a
synthetic candle from a provider candle with the same flat OHLC values.

### History and cache/freshness behavior

There is no history cache in the traced path. The request is fetched from the
provider on each call. The only history limit is the Market Data service/provider
maximum of 720, with the public endpoint defaulting to 200.

Generic Market Intelligence context freshness is separate from historical role
freshness. `MarketDataSectionFactory` uses a configurable default stale-after
duration of 30 seconds and creates `ContextProvenance` with source occurrence and
fetch timestamps. This classification is currently applied to the generic
historical section after the contributor picks the maximum event `occurredAt`.

## Current OHLC Flow

```text
Kraken REST positional OHLC payload
  -> KrakenRestOhlcMapper (shape/numeric mapping)
  -> OhlcEvent (market/provider/interval/OHLC/closed/occurredAt/provenance)
  -> OhlcHistoryNormalizer (sort + duplicate validation + explicit synthetic gap fill)
  -> KrakenMarketData.takeLastEvents (max 720, requested tail)
  -> MarketHistoryService (market lookup and limit validation)
  -> MarketController JSON List<OhlcEvent>
  -> MarketDataClient List<OhlcResponse>
  -> HistoricalOhlcContextContributor
  -> OhlcPoint list in generic HistoricalOhlcContext
  -> ContextSection provenance/artifact materialization
  -> legacy OHLC capabilities
```

## Current Implementation State

The worktree now contains the first implementation slice:

* `OhlcEvent` carries `synthetic`, deterministic `sourceId`, and `fetchedAt`.
* Kraken REST and WebSocket OHLC mappers populate the additive metadata.
* `OhlcHistoryNormalizer` sorts history, collapses identical duplicates, rejects
  conflicting duplicates, and marks generated candles as synthetic.
* Market Intelligence has immutable Trend Context value objects and an initial
  `TrendContextInputMapper`.
* `OhlcResponse` carries the additive metadata required by the mapper.

The mapper is intentionally not connected to a production multi-role
acquisition or orchestration path in this Story. Production orchestration is
owned by Story 0064; the existing generic contributor remains unchanged.

The websocket path is independent after provider mapping:

```text
Kraken websocket OHLC message
  -> KrakenOhlcMapper
  -> OhlcEventPublisher
  -> websocket consumers
```

## Synthetic Candle Baseline Before Story 0062 Implementation

The following description records the baseline that motivated Story 0062. The
current implementation state is described above in `Current Implementation
State`.

Synthetic candles reuse `OhlcEvent`. They currently have no explicit marker.
They receive:

- the previous event's market UUID, provider, symbol, and interval;
- the missing interval's calculated open and close timestamps;
- the previous close copied to all OHLC fields and VWAP;
- zero volume and zero trades;
- `closed=true`;
- the previous event's `occurredAt`.

Downstream consumers therefore cannot safely distinguish them. They are exposed
through the public `/ohlc` JSON contract and are mapped into Market Intelligence
without any synthetic metadata. `HistoricalOhlcContext` also discards `closed`,
provider, source identity, `occurredAt`, and any future synthetic marker because
`OhlcPoint` contains only timestamps, OHLCV.

This is unsafe for Trend Context. A real flat candle and a normalized gap are
currently indistinguishable, and synthetic candles are marked closed even though
they are not source evidence.

## Synthetic Candle Recommendation

### Recommended option: Option A, additive normalized `OhlcEvent` metadata

Extend the existing normalized `OhlcEvent` contract with explicit additive
metadata rather than introducing a second history result type:

- `boolean synthetic`, always `false` for provider-created candles and `true`
  only for normalizer-created candles;
- deterministic `String sourceId` for candle identity;
- `Instant fetchedAt` for local acquisition/normalization time.

The existing `occurredAt` must be documented and mapped as source occurrence or
provider response watermark, not fetch time. For REST, the current Kraken
`last` value remains source metadata; the local fetch clock supplies
`fetchedAt`. For websocket events, the message timestamp remains source
occurrence and the injected clock supplies `fetchedAt`.

The normalizer should receive the acquisition timestamp and create synthetic
events with `synthetic=true`, a deterministic source ID derived from the
provider-independent identity tuple, and the same fetch timestamp as the
normalization operation. Provider events receive `synthetic=false` and a
deterministic source ID. A source ID is an identity key, not a provider revision
number; the current provider contract cannot distinguish correction/replacement
versions.

Why Option A fits the repository:

- `OhlcEvent` is already the normalized Market Data boundary consumed by the
  provider port, REST controller, tests, and websocket publisher.
- The public endpoint directly serializes this record; additive JSON fields are
  backward-compatible for consumers that ignore unknown fields.
- It keeps synthetic status and source facts in Market Data, as required by
  ADR-006, ADR-012, and ADR-048.
- It avoids maintaining two parallel candle models and a wrapper-to-legacy
  conversion path.
- It allows existing generic `HistoricalOhlcContext` and `OhlcPoint` consumers
  to remain unchanged until a dedicated Trend Context mapper consumes the richer
  response.

Required implementation caution: the added fields will change Java record
constructors. All existing constructor call sites and focused tests must be
updated in the implementation pass. The serialized endpoint gains fields but
does not remove or rename existing fields.

### Option B: dedicated normalized-history wrapper

This would introduce a result containing candles, source response metadata,
fetch time, and gap findings. It would model history-level facts cleanly, but it
would require changing `MarketDataProvider`, `MarketHistoryService`, the public
controller mapping, Kraken tests, and likely websocket/history distinctions. It
would also require either extending the candle type or carrying a separate
synthetic-status map. The current repository has no history result abstraction,
so Option B is a larger boundary change without a concrete need.

### Rejected for this Story

Inferring synthetic status from zero volume, flat OHLC, or zero trades is
rejected. Those values can be legitimate source values and the accepted design
explicitly prohibits value-based inference.

## Information Currently Lost

The current path loses or cannot establish:

- synthetic versus source candle identity;
- local fetch/ingestion time;
- provider candle/source ID;
- provider correction or revision identity;
- response-level source snapshot identity;
- explicit gap findings;
- duplicate semantics;
- candle closure and provider metadata once mapped into `OhlcPoint`;
- per-candle source occurrence once mapped into `OhlcPoint`;
- interval-specific role identity because the generic context carries one string
  interval only;
- a cut-off because the current history request has no cut-off parameter.

## Duplicate Semantics

The current normalizer does not handle duplicates. It sorts by `openTime` and
retains all entries. There is no provider sequence, revision, or source ID from
which a correction can be identified.

Story 0062 should define only the semantics supported by this contract:

- **Identical duplicate:** same interval/open-time identity and identical
  normalized candle content. Collapse deterministically to one candle and retain
  a `DUPLICATE_IDENTICAL` normalization finding.
- **Conflicting duplicate:** same interval/open-time identity with different
  normalized content. Fail the normalized history/input explicitly with
  `DUPLICATE_CONFLICT`; do not select first, last, or highest-volume data.
- **Provider correction/replacement:** not distinguishable with the current
  provider contract. A same-key differing value is therefore treated as a
  conflicting duplicate, not silently accepted as a correction.

The source ID is stable for the candle identity but does not pretend to encode a
provider revision. A later source version can produce a new assessment and
fingerprint once a future provider contract supplies version information.

## Market Intelligence Historical Context

### Current adapter path

`MarketDataClient.findOhlc` calls the public Market Data endpoint with a market
UUID, interval string, and limit. Its `OhlcResponse` currently contains nearly
all `OhlcEvent` JSON fields except an explicit synthetic marker, source ID, and
fetch time.

`HistoricalOhlcContextContributor` currently hardcodes:

- interval `FIFTEEN_MINUTES`;
- limit `200`.

It maps each response to `OhlcPoint`, retaining only open/close times, OHLC, and
volume. It computes section `sourceOccurredAt` as the maximum response
`occurredAt` and delegates `fetchedAt` to `MarketDataSectionFactory`, which uses
`Instant.now()` when no fetch timestamp is supplied. It creates one generic
`HistoricalOhlcContext` with one interval.

`IntelligenceContextAssembler` loads contributors based on generic
`ContextRequirement`; missing contributors become `MISSING`, and runtime errors
become `UNAVAILABLE`. The current historical contributor is used by
`OhlcRangeAnalysisCapability`, which reads the generic payload and calculates a
legacy high/low/range/price-change measurement. Existing artifacts and
`CapabilityAnalysisCoordinator` also identify the generic historical section and
its one interval.

### Metadata reaching Market Intelligence today

Today the generic path receives:

- market ID in the `OhlcResponse` and `HistoricalOhlcContext`;
- provider and symbol in the wire response, but discards them during mapping;
- interval as a string on the generic context;
- candle timestamps and OHLCV only in `OhlcPoint`;
- section-level source string `market-data`;
- section-level source occurrence timestamp from the maximum response
  `occurredAt`;
- section-level fetch timestamp from the context factory.

It discards `closed`, provider, symbol, vwap, trade count, source identity,
synthetic status, and per-candle occurrence/fetch metadata.

### Compatibility boundary

`HistoricalOhlcContext` must remain unchanged for legacy consumers. The dedicated
Trend Context mapper should live in a new adapter/domain seam, not by expanding
`OhlcPoint` or adding Trend Context semantics to `ContextSectionType.HISTORICAL_OHLC`.

The clean 0062 seam is a role-aware mapper that accepts already-fetched richer
Market Data response lists grouped by role and produces a validated
`TrendContextAssessmentInput`. It does not own production acquisition or
capability registration. Story 0064 can later call Market Data for BIAS, SETUP,
and optional TRIGGER and pass the responses through this mapper.

## Proposed Trend Context Input Contract

The following is the minimum contract to implement in the Market Intelligence
domain. It is a semantic domain model, not an HTTP DTO, JPA entity, Spring bean,
or Market Data implementation type.

### `TrendContextAssessmentInput`

Package: `com.hope.trading.market_intelligence.domain.trendcontext`.

Use a final immutable class with a validated static factory, following the
complex immutable/value-object approach already used by
`StrategyEvaluationContext`. The class should expose:

- `UUID marketId`;
- provider/source-neutral provider identity string and symbol;
- `Instant assessmentAt`;
- `Instant cutOffAt`;
- `TrendContextProfile profile`;
- immutable role-series map keyed by `TrendContextRole`;
- immutable input-level validation findings/excluded evidence summary;
- `String ruleVersion`;
- `String fingerprint` or `ArtifactFingerprint`-compatible SHA-256 value.

The factory must canonicalize role order and candle order before computing the
fingerprint. It must reject an invalid contract rather than manufacture a
partial required role.

### `TrendContextRole`

Enum with exactly `BIAS`, `SETUP`, and `TRIGGER`. `BIAS` and `SETUP` are required;
`TRIGGER` is present only when supplied and is optional under the default profile.

### `TrendContextRoleDefinition`

Immutable profile value object containing:

- role;
- `OhlcInterval` or a domain interval value;
- required/optional status;
- minimum eligible history requirement;
- request history target, when the profile supplies one.

It represents configuration only. It performs no candle calculations.

### `TrendContextRoleSeries`

Immutable value object containing:

- role and configured interval;
- all normalized input candles for the role, including open and synthetic
  candles;
- calculation-ready candles, containing only valid, closed, non-synthetic,
  cut-off-eligible candles;
- immutable exclusion/normalization findings;
- immutable `TrendContextGapFinding` list;
- `TrendContextSourceReference`;
- `TrendContextFreshness` facts.

The two candle lists must be derived together by the mapper/factory. The future
engine consumes `calculationReadyCandles`; it does not decide whether an event is
synthetic or open. The all-candle list remains available for explanation and
replay.

### `TrendContextCandle`

Immutable record/value object containing:

- role/interval identity as needed by the series;
- `openTime`, `closeTime`;
- decimal `open`, `high`, `low`, `close`, and optional `volume`;
- `boolean closed`;
- `boolean synthetic`;
- stable `sourceId`;
- provider/source identity;
- `sourceOccurredAt`;
- `fetchedAt`.

It is a Market Intelligence domain copy of source facts, not a reference to
`OhlcEvent` or `OhlcResponse`.

### `TrendContextProfile`

Immutable versioned configuration containing:

- profile ID `CONSERVATIVE_SWING_V1`;
- profile version;
- BIAS/SETUP/optional TRIGGER role definitions;
- `triggerRequired=false` for the accepted default;
- pivot radius 2;
- same-type minimum separation 2;
- minimum confirmed swing counts 2/2;
- EMA period 50, warmup 10, slope lookback 5, slope threshold 0.10 ATR;
- ATR period 14 and baseline length 20;
- abnormal ATR ratio threshold 3.0;
- extension ATR multiple 3.0;
- pullback minimum bars 2;
- reclaim window 2;
- freshness multiplier 2 intervals;
- synthetic gap policy excluding synthetic structural evidence.

Numerical values and defaults remain explicitly `PAPER VALIDATION HYPOTHESES`
where the hardened design labels them that way. The profile does not calculate
EMA, ATR, pivots, or any assessment outcome.

The implementation must make the history requirement explicit rather than
silently request 200 candles. The hardened design defines the required formula
windows and says to request more than the minimum, but does not publish one
single numeric request size. This is a contract configuration detail, not a
repository contradiction: the implementation can represent minimum/requested
history as profile fields and preserve their version, while Story 0063 later
uses the same profile requirements for calculation readiness.

### `TrendContextGapFinding`

Immutable finding containing:

- role;
- expected interval;
- missing range or expected open time;
- finding code such as `GAP_IN_HISTORY`, `SYNTHETIC_DATA_EXCLUDED`, or
  `DUPLICATE_IDENTICAL`;
- related source IDs where available.

It is provenance/evidence, not a structure or trading conclusion.

### `TrendContextFreshness`

Immutable facts, not a freshness conclusion, containing:

- expected interval and duration;
- latest eligible closed candle close time;
- source occurrence/watermark time;
- fetch time;
- assessment time/cut-off reference;
- role availability and whether an eligible candle exists.

The `2 * interval` freshness conclusion belongs to Story 0063's deterministic
assessment rules. Story 0062 must not turn it into a Trend Context outcome.

### `TrendContextSourceReference`

Immutable source reference containing:

- source owner `market-data`;
- provider identity;
- market and symbol identity;
- role and interval;
- first/last source candle times;
- source occurrence/watermark and fetch time;
- source snapshot/version string where available;
- content digest or input digest.

It should reference normalized source data rather than duplicate the complete
provider payload.

### `TrendContextInputValidation`

Use a stateless domain validator/factory component for contract validation. It
should own validation failures and canonicalization, but not calculate any
Trend Context rule. Invalid input must fail explicitly with typed/domain errors
or a clearly structured validation result; it must not produce an accepted input
with hidden invalid state.

## Source Versus Calculation-Ready Evidence

The mapper must preserve all normalized candles first, then derive the eligible
series:

```text
normalized source candles
  -> retain all for provenance
  -> reject malformed/future/conflicting duplicate input
  -> classify open/synthetic/excluded findings
  -> calculation-ready = closed && !synthetic && closeTime <= cutOffAt
  -> validate canonical order and minimum eligible history
```

An open candle is retained with `closed=false` and an exclusion finding. It is
not converted to closed and is not passed to calculations. A synthetic candle is
retained with `synthetic=true` and a gap/exclusion finding. It is never passed to
calculations. A candle crossing the cut-off is retained as source evidence but is
excluded because its `closeTime` is after the cut-off; it is not truncated or
rewritten.

The future engine receives only `calculationReadyCandles` and therefore never
has to guess source quality. It can still access role findings and source
references through the input for deterministic explanations in Story 0063.

## Role and Timeframe Model

The accepted profile is:

| Role | Meaning | Default interval | Required |
| --- | --- | --- | --- |
| `BIAS` | Broad swing structure and protected direction | 4h | Yes |
| `SETUP` | Pullback, extension, and setup structure | 1h | Yes |
| `TRIGGER` | Optional lower-timeframe confirmation | 15m | No |

Validation rules owned by Story 0062:

- BIAS and SETUP must be present.
- Role intervals must be distinct.
- Intervals must be ordered from coarser to finer.
- SETUP cannot be coarser than BIAS.
- TRIGGER may be absent only when `triggerRequired=false`.
- Each supplied role series interval must equal its profile interval.
- Each role must satisfy its profile minimum eligible history requirement.

No role is renamed to the older investigation vocabulary (`CONTEXT` or
`REFINEMENT`) because the accepted hardened design and Story use BIAS/SETUP/
TRIGGER.

## Multi-Timeframe Acquisition

### Story 0062 responsibility

Story 0062 should establish:

- the role/profile/value-object representation;
- richer Market Data response fields needed for mapping;
- a pure role-aware mapper/assembler accepting already acquired per-role
  responses;
- explicit absence and validation behavior;
- profile and history requirement representation;
- tests proving role/interval/source preservation.

It should not add a production Trend Context capability, AnalysisExecution,
observation persistence, or a full contributor lifecycle.

The mapper can accept a role-to-history input such as an immutable map of role to
normalized history response. That lets focused mapping tests prove the contract
without coupling 0062 to orchestration.

### Story 0064 responsibility

Story 0064 should own production acquisition/orchestration: resolving the
selected profile, issuing bounded role-specific Market Data requests, handling
missing optional TRIGGER, assembling the `TrendContextAssessmentInput`, and
integrating the capability/observation lifecycle.

It must not use uncontrolled N+1 requests. The acquisition boundary should
make the three bounded role requests explicit and preserve each role's provider,
interval, source/fetch metadata, and response status. No role may silently fall
back to 15m/200. The current generic contributor remains available to legacy
capabilities.

## Validation Ownership Matrix

| Validation | Owner | Story 0062 behavior |
| --- | --- | --- |
| Provider response envelope/entry shape | Market Data provider mapper | Reject malformed provider payload before normalized contract |
| Numeric decimal parsing | Market Data provider mapper | Reject invalid/missing numeric fields |
| OHLC relationship and positive/finite prices | Market Data normalized boundary, rechecked by MI input validator | Reject invalid source/input explicitly; duplicate recheck protects the service boundary |
| Timestamp presence and `openTime < closeTime` | Market Data normalized boundary, rechecked by MI input validator | Reject impossible timestamps |
| Interval boundary and ordering | Market Data normalizer canonicalizes; MI validator requires canonical ascending order | Reject interval mismatch or non-canonical input |
| Gap detection and synthetic creation | Market Data normalizer | Create explicit synthetic candles and findings; never infer from values |
| Identical duplicate | Market Data normalizer | Collapse deterministically and retain a finding |
| Conflicting duplicate | Market Data normalizer and MI validator | Fail explicitly; no first/last selection |
| Future candle relative to assessment cut-off | MI input validator | Retain for provenance if desired, exclude or reject according to contract; accepted input contains no future calculation evidence |
| Open candle calculation eligibility | MI mapper/factory | Retain source candle, exclude from calculation-ready list |
| Synthetic calculation eligibility | MI mapper/factory | Retain source candle/finding, exclude from calculation-ready list |
| Required roles | MI input factory | BIAS/SETUP missing is invalid; optional TRIGGER absence is explicit |
| Profile identity/parameters | Trend Context domain profile | Reject malformed profile, duplicate/unordered intervals, invalid positive parameters |
| Insufficient eligible history | Trend Context input validator | Reject/return explicit insufficient-history finding before engine input is accepted |
| Role interval compatibility | Trend Context input validator | Reject mismatched role series |
| Role-level freshness facts | MI mapper/factory records facts | Story 0063 owns the 2-interval freshness conclusion |
| Fingerprint | Trend Context input factory | Canonicalize accepted evidence and compute stable SHA-256 |

Validation is repeated at the Market Intelligence boundary only where it is a
concrete trust-boundary defense. It must not duplicate future calculations.

## Assessment Cut-Off and No Look-Ahead

`cutOffAt` is an immutable input field and participates in the fingerprint. The
input validator applies the accepted design's eligibility rule:

```text
closed == true
closeTime <= cutOffAt
closeTime > 0
```

The candle's `closeTime`, not `occurredAt`, determines whether its market
evidence belongs to the assessment window. A candle whose interval crosses the
cut-off is retained as excluded source evidence and is not truncated into a
partial candle. An open candle remains open and excluded.

Source occurrence/watermark and local fetch time are provenance facts. They must
not be substituted for candle close time. The current Kraken REST `last` value
is a response watermark, not an individual candle close, and must not be used as
the candle eligibility timestamp. The implementation should preserve it under
source metadata while using each candle's own `closeTime` for eligibility.

Replay uses the same normalized candles, profile version, rule version, and
cut-off. It must produce the same eligible series and fingerprint without reading
wall-clock time. Any local fetch time used for provenance must be an explicit
input, not obtained during fingerprinting or validation.

The hardened design's freshness rule uses the latest eligible closed candle and
the interval duration. Story 0062 supplies those facts; Story 0063 calculates
freshness and attention outcomes.

## Freshness Model

The repository's current generic context uses a 30-second stale-after setting,
which is appropriate for generic active analysis but not the accepted role-based
Trend Context rule. Story 0062 must not reuse that value as a Trend Context
conclusion or mutate generic section semantics.

The input records, per role:

- expected interval duration;
- latest eligible closed candle close;
- source occurrence/watermark;
- local fetch time;
- assessment/cut-off time;
- whether the role has eligible closed evidence;
- gap/open/synthetic exclusion findings.

Story 0063 applies the accepted `assessmentAt - latestClosedClose <= 2 * D`
hypothesis and returns `NO_SETUP`, `WATCH`, or another assessment result as
defined by its scope. Fetch delay remains visible and is not silently converted
into a candle occurrence time.

## Deterministic Fingerprint Strategy

Reuse the repository's established SHA-256 conventions. `ArtifactFingerprint`
already validates 64-character lowercase SHA-256 values and canonicalizes sorted
input identities. `StrategyEvaluationContext` also computes a SHA-256 digest from
an explicitly ordered canonical representation and has tests for order-independent
equivalence. Story 0062 should use one dedicated Trend Context canonicalizer,
not Java `hashCode`, object identity, Jackson default field ordering, or random
IDs.

Fingerprint components, in canonical order:

1. contract/rule name and rule version;
2. market UUID, provider identity, and symbol;
3. assessmentAt and cutOffAt;
4. profile ID and profile version;
5. role definitions in fixed enum order `BIAS`, `SETUP`, `TRIGGER`;
6. role interval, required flag, and history requirement;
7. role source reference and source/fetch timestamps;
8. all normalized candle records in ascending `(openTime, closeTime, sourceId)`
   order, including `closed`, `synthetic`, source ID, source occurrence,
   fetchedAt, OHLCV, interval, and identity;
9. all gap, duplicate, open, synthetic, and cut-off exclusion findings in
   canonical code/time/source order;
10. the calculation-ready membership or a deterministic derivation of it.

Role order is fixed, so semantically equivalent role-map insertion order does
not change the digest. Candle order is canonicalized only after duplicate
validation. Conflicting duplicate candles cannot be made equivalent by sorting.
Decimal values must use a canonical decimal representation that preserves value
semantics without binary floating-point conversion. Instants use ISO-8601 UTC
format. Null optional volume/source fields use an explicit canonical token.

The tests must prove:

- replaying the same accepted inputs produces the same digest;
- role-map insertion order does not change it;
- allowable candle input ordering does not change it;
- one material OHLC, timestamp, synthetic/closed, source, cut-off, profile, or
  rule-version change changes it;
- random UUIDs, wall-clock timestamps, and persistence IDs are not included.

## Provenance Strategy

ADR-048 requires facts to remain attributable to their owning source and
derived evidence to retain immutable inputs, cut-off, versions, completeness,
and fingerprint. Story 0062 should therefore preserve references/digests rather
than full provider payloads.

The input should answer:

- which market/provider/symbol was used;
- which role and interval each candle belonged to;
- which source response/watermark and local fetch time were involved;
- which exact candle range was retained;
- which candles were open, synthetic, duplicate, malformed, future, or outside
  the cut-off;
- which profile and rule version were active;
- which calculation-ready candle set was passed forward;
- which fingerprint identifies that accepted input.

The input itself is not persisted by Story 0062. It is a domain value passed to
the future pure engine. Story 0064/0065 decide how the later assessment and
observation reference it.

## Backward Compatibility

| Existing consumer | Classification | Impact |
| --- | --- | --- |
| `KrakenRestOhlcMapper` | REQUIRES MODIFICATION | Set explicit source/synthetic/fetch fields when creating provider candles |
| `KrakenOhlcMapper` websocket path | REQUIRES MODIFICATION | Set `synthetic=false`, stable source ID, and ingestion fetch time |
| `OhlcHistoryNormalizer` | REQUIRES MODIFICATION | Mark synthetic candles, detect duplicates, preserve metadata, retain ordering/gap findings as needed |
| `MarketDataProvider` | COMPATIBILITY ADAPTER | Keep `List<OhlcEvent>` if Option A is used; no wrapper migration |
| `MarketHistoryService` | UNCHANGED or narrow clock/contract wiring | Preserve market lookup and 1..720 limit |
| `MarketController` | UNCHANGED contract shape, additive JSON fields | Existing OHLC endpoint remains usable; controller test should assert legacy fields and metadata |
| Existing frontend OHLC consumers | COMPATIBILITY ADAPTER | Existing fields remain; additive fields are ignored unless later used |
| `OhlcEventPublisher`/websocket consumers | COMPATIBILITY ADAPTER | Existing event behavior remains; new metadata is additive |
| `HistoricalOhlcContextContributor` | UNCHANGED for legacy path | Continue mapping existing fields into `OhlcPoint`; do not make it Trend Context-specific |
| `HistoricalOhlcContext`/`OhlcPoint` | UNCHANGED | Generic legacy payload remains single-interval and metadata-light |
| `OhlcRangeAnalysisCapability` | UNCHANGED | Existing range behavior remains separate from Story 0062 |
| `CapabilityAnalysisCoordinator` | UNCHANGED | Existing generic artifact path remains intact |
| Kraken REST mapper/history tests | REQUIRES MODIFICATION | Assert explicit metadata and duplicate/gap behavior |

The additive `OhlcEvent` fields are the main compatibility impact. Existing
Java construction sites must use the new canonical constructor or a compatibility
factory if the implementation team chooses one. No unrelated tests or behavior
should be modified.

## Package and Dependency Direction

The target direction is:

```text
Market Data provider adapter
  -> normalized Market Data OhlcEvent facts
  -> Market Intelligence HTTP response adapter
  -> TrendContextAssessmentInput domain values
  -> future pure deterministic engine
```

Market Data owns provider normalization, synthetic status, source/fetch facts,
interval semantics, and normalized event identity.

Market Intelligence owns the semantic Trend Context input model and mapping
adapter. The input model must not import `OhlcEvent`, `OhlcResponse`, Spring,
Feign, JPA, HTTP, repositories, account state, Risk, brokers, ML, LLMs, or
execution.

The existing `HistoricalOhlcContext` remains a generic context payload. The new
types should be placed under `market-intelligence/.../domain/trendcontext` and
the mapper under `.../adapter/marketdata` or a narrowly named application
adapter package. The mapper may depend on the adapter response records and the
domain factory; the domain must not depend on the adapter.

## Risks and Open Questions

### Resolved for implementation

- Synthetic status must be explicit, not inferred from values.
- Option A is the smallest compatible normalized boundary.
- Identical duplicates can be collapsed; conflicting duplicates must fail.
- Provider correction identity is unavailable and must not be invented.
- Generic `HistoricalOhlcContext` remains unchanged.
- 0062 defines mapping and contracts; 0064 owns production role acquisition.
- The engine receives calculation-ready candles and does not inspect source
  quality itself.

### Implementation risks

- `OhlcEvent` is used by both REST history and websocket events, so constructor
  changes must be applied consistently.
- The current `occurredAt` name hides different semantics across REST and
  websocket paths. The implementation must document it and add fetch time rather
  than silently reinterpret it.
- The current REST `last` response watermark is copied to every candle. It must
  not be treated as an individual candle occurrence in cut-off validation.
- `MarketHistoryService` and Kraken both cap history at 720; a future profile
  request must fail explicitly if its required/requested history exceeds this
  contract rather than silently truncate.
- The hardened design does not publish one numeric minimum/requested history
  count. The profile must carry an explicit versioned requirement without
  introducing a new algorithm.
- The public controller returns the domain record directly. Additive JSON fields
  are safe for tolerant clients, but contract tests must protect existing fields.
- There is no existing historical contributor test. The implementation must add
  focused mapping tests without changing generic capability tests unnecessarily.

### No blocker

None of these risks requires an ADR or Story scope change. They are concrete
implementation details inside the approved contract/mapping boundary.

## Architectural Assessment

The repository supports Story 0062 without changing the accepted architecture.
The correct boundary is an additive normalized Market Data metadata extension
plus a dedicated, immutable Market Intelligence domain input and mapper.

The implementation must stop before:

- deterministic Trend Context calculations;
- `IntelligenceObservation` persistence or supersession;
- StrategyEvaluation/StrategyMatch integration;
- TradingOpportunity creation;
- risk, execution, frontend, ML, or agent work.

There is no ADR contradiction and no Story scope change required.

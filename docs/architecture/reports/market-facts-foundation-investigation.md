# Market Facts Foundation Investigation

**Date:** 2026-10-03
**Branch:** `main`
**Mode:** Deep read-only repository, architecture, domain, and provider investigation
**Implementation:** None
**Status:** Investigation only; recommendation requires human approval

This report prepares a possible future deterministic Market Candidate Selection
capability. It does not create a Story or ADR and does not define thresholds,
weights, scoring formulas, trading rules, or runtime actions.

## 1. Executive Summary

Trading OS has a useful separation already:

```text
Market Data
    -> public market facts and source metadata
Market Intelligence
    -> deterministic analysis, observations, and future candidate selection
Trading Core / Risk
    -> account-specific authority and financial authorization
```

The smallest fact set that appears necessary before a future Candidate Selection
V1 is:

1. Existing `MarketState` hard gating, especially `tradable` and its freshness;
2. One explicitly defined, provenance-bearing activity fact based on completed
   OHLC data or an explicitly selected provider-native window;
3. Reusable per-timeframe Market Data Readiness facts that preserve raw
   completeness separately from normalized calculation input.

Normalized activity and readiness are **ESSENTIAL_FOR_V1**. Spread is
**USEFUL_LATER**. Liquidity is **NOT_JUSTIFIED_YET** with current infrastructure.
Generic volatility is **NOT_JUSTIFIED_YET** for Candidate Selection V1 and must
not duplicate Trend Context ATR semantics.

The recommended ownership is a bounded Market Facts capability/module inside
Market Data, not a new microservice. It should expose normalized public facts,
source/freshness metadata, and readiness evidence. Future Candidate Selection
should remain in Market Intelligence and consume those facts without redefining
their source semantics. A composed candidate-input projection may exist at the
Market Intelligence boundary, but it should not become the owner of raw or
normalized market facts.

The most important blocker is operational rather than conceptual: current OHLC
history is fetched per market and timeframe, normalized in memory, and not
persisted as a catalogue-wide history/readiness projection. Running current
Trend Context acquisition for approximately 1,436 markets would require at least
one request per market per configured timeframe and may retry each role. A
Candidate Selector must not cause that N x timeframe provider-call explosion.

The recommended sequence is:

```text
0069 candidate: Market Facts contract + bounded activity/readiness foundation
0070 candidate: deterministic Candidate Selection over explicit user scope
later: spread if a concrete selection need is demonstrated
later: liquidity, generic volatility, and Market Structure as separate evidence
```

`0069` is only the next apparently unused repository number. No Story is created
or assigned by this investigation. Human approval is required for the exact
scope and numbering.

## 2. Repository State and Sources Reviewed

### Repository state

- Current branch at investigation start: `story/0068-user-market-discovery`.
- Story 0068 is implemented and committed on this branch before this read-only
  investigation began.
- Existing unrelated Story 0067 worktree changes were present and were not
  modified.
- No production code, tests, Story, or ADR was modified for this investigation.
- No provider runtime, Active Scan, TradePlan, RiskEvaluation, or execution was
  run.

### Engineering context

- `AGENTS.md`
- `docs/architecture/stories/0067-paper-validate-trend-context/story.md`
- `docs/architecture/stories/0067-paper-validate-trend-context/validation-report.md`
- `docs/architecture/stories/0068-user-market-discovery/story.md`
- `docs/architecture/reports/two-level-market-filtering-investigation.md`
- `docs/architecture/adr/ADR-006.md` — Market Data Service Responsibilities
- `docs/architecture/adr/ADR-010.md` — Market State Domain Model
- `docs/architecture/adr/ADR-014.md` — Trading Decision Pipeline
- `docs/architecture/adr/ADR-033.md` — Active and Passive Market Intelligence
- `docs/architecture/adr/ADR-048.md` — Intelligence Evidence and Authority
- `docs/architecture/stories/README.md`

### Repository source paths reviewed

- `market-data` Market, MarketState, MarketConstraints, DTO, controller, provider
  ports, Kraken REST adapter, Kraken WebSocket adapter, event publishers,
  subscription service, price snapshot service, valuation service, migrations,
  and OHLC normalizer.
- `market-intelligence` Market Data Feign port, OHLC response contract,
  historical OHLC contributor, Trend Context role contributor, input mapper,
  input validation, freshness model, role series, and Trend Context engine.
- `trading-os-web` Market Discovery implementation from Story 0068 and existing
  market-selection consumers.
- Market Data and Market Intelligence tests related to OHLC normalization,
  provider mapping, order books, recent trades, ticker publishing, and Trend
  Context evidence.

### External provider documentation

Authoritative Kraken documentation was consulted for:

- REST `Get Ticker Information`;
- REST `Get OHLC Data`;
- REST `Get Recent Trades`;
- Spot WebSocket v2 `ticker`;
- Spot WebSocket v2 `ohlc`;
- Spot WebSocket v2 `trade`.

The official documentation states that REST ticker arrays expose today and last
24-hour values, WebSocket v2 ticker volume is 24-hour base-currency volume, and
WebSocket v2 OHLC volume is base-currency volume within the candle interval.

### DevLog applicability

The required DevLog Story Agent applicability check was attempted with project
slug `trading-os`. It failed with `Error invoking method: execute` and returned
no context. Historical conclusions in this report therefore come from the
repository and official provider documentation, with the limitation explicitly
recorded here.

## 3. Market Facts Definition

The following distinction is required before selecting a model:

| Layer | Meaning | Current or future authority |
|---|---|---|
| Raw provider data | Provider payload and provider-specific field semantics | Kraken adapter only |
| Normalized market data | Provider-independent market data with preserved source meaning | Market Data |
| Derived Market Fact | Deterministic calculation from identified normalized inputs, with status and provenance | Prefer a bounded Market Facts capability; ownership requires approval |
| Analytical evidence | Deterministic analysis such as Trend Context or structural findings | Market Intelligence |
| Candidate-selection decision | Scope-limited ordering or prioritization of markets for deeper analysis | Market Intelligence |
| Trading decision | Strategy, TradePlan, Risk, and human execution authorization | Existing downstream boundaries |

A Market Fact must not silently become a trading recommendation. For example,
`24-hour base volume` is a normalized fact only after its unit, window, source,
and freshness are explicit. `rank 1` is a candidate-selection output, not an
intrinsic market fact. `BUY` is a trading interpretation and is out of scope.

## 4. Ownership Boundary

### Existing authority

ADR-006 makes Market Data the authoritative source for market catalogue,
metadata, constraints, market state, realtime streams, and provider-normalized
events. ADR-010 assigns `MarketState` to Market Data. ADR-048 states that Market
Data owns normalized public market facts and source/freshness metadata, while
Market Intelligence owns deterministic derived evidence and orchestration.

### Options

#### A. Facts inside the existing Market Data service

Advantages:

- preserves the accepted owner of public market facts;
- reuses provider adapters, normalized events, freshness, and subscriptions;
- avoids another network hop and service lifecycle;
- keeps provider-neutral contracts near the source boundary.

Risks:

- Market Data could accidentally acquire candidate-selection or strategy policy;
- a broad service module could become an unbounded analytics container.

#### B. Dedicated Market Facts capability/module inside Market Data

Advantages:

- preserves service ownership while making the boundary explicit;
- allows separate fact contracts, calculation versions, statuses, and caching;
- keeps candidate policy outside the source service.

Risks:

- requires careful distinction between reusable facts and Market Intelligence
  analytics;
- may need a compact read model and update orchestration.

#### C. Market Intelligence owns all facts

Advantages:

- candidate selection can consume and calculate in one place;
- reuse of existing deterministic analysis infrastructure.

Risks:

- duplicates Market Data ownership;
- encourages provider or transport knowledge in analysis code;
- makes public fact reuse by frontend, Passive Scanner, and other consumers less
  direct;
- conflicts with ADR-048's normalized public fact ownership boundary.

#### D. New Market Facts microservice

Advantages:

- independent scaling and lifecycle;
- explicit service boundary.

Risks:

- another service, database, contract, deployment, and failure boundary;
- no evidence that current volume requires a service split;
- likely premature architectural purity.

### Recommendation requiring human approval

Choose **B**, implemented within the existing Market Data service. The bounded
module should own normalized fact contracts and fact-specific acquisition/cache
coordination, but must not own candidate ranking, user intent, account
eligibility, strategy, Risk, or execution. A new microservice is not justified
by current evidence.

Candidate Selection should consume these facts in Market Intelligence, apply only
human-approved selection policy, and preserve the explicit user scope.

## 5. Existing Market Data Inventory

### Catalogue and hard state

`Market` persists:

- `marketId`;
- provider;
- symbol;
- base asset;
- quote asset;
- `MarketConstraints`;
- embedded `MarketState`.

`MarketState` contains `tradingStatus`, `tradable`, `closureReason`, and
`lastUpdated`. ADR-010 defines OPEN as immediately executable, not merely inside
an exchange session.

The catalogue endpoint is `GET /api/v1/markets`. It returns the complete
catalogue; there is no server-side discovery query, pagination, or catalogue-wide
activity/readiness projection.

### Ticker

`TickerEvent` contains:

```text
marketId, provider, symbol, bid, ask, last, volume, occurredAt
```

The domain model does not carry volume unit, window, VWAP, trade count, source
timestamp, or provider source identifier.

The in-memory `TickerEventPublisher` stores the latest event by symbol and market
ID. It also persists only bid/ask/last as `PriceObservation`; ticker volume is
not persisted in `price_observations`.

### OHLC

`OhlcEvent` contains:

```text
marketId, provider, symbol, interval, openTime, closeTime,
open, high, low, close, volume, vwap, trades, closed,
occurredAt, synthetic, sourceId, fetchedAt
```

OHLC is available from REST history and WebSocket streams. No OHLC repository or
OHLC table was found. History is returned after in-memory normalization.

### Recent trades

`TradeEvent` contains trade ID, side, price, quantity, notional, and occurrence
time. The Kraken mapper calculates `notional = price * quantity`. The
`RecentTradesStateService` keeps at most 100 trades per market in memory and
does not define a time window. A 100-event buffer is not a canonical interval
activity fact.

### Order book

`OrderBookSnapshot` contains configured depth, levels, best bid, best ask,
absolute spread, bid/ask quantity totals, imbalance, and occurrence time. State
is reconstructed in memory per market and configured depth. It is not persisted.

The quantity totals are depth-limited quantities, not a statement of executable
liquidity across the market.

### Price snapshots and valuation

`MarketPriceSnapshotService` uses the latest ticker or acquires a per-market REST
ticker snapshot. It classifies current prices as FRESH, STALE, or UNAVAILABLE
using the configured `market-data.snapshot.stale-after`, default 30 seconds.

`PriceObservation` persists bid/ask/last with effective and capture times. The
valuation subsystem can convert quote currencies through direct or inverse
price-observation legs, with provenance and a configured maximum observation age,
default five minutes. This is useful evidence that conversion provenance exists,
but it does not automatically make market activity comparable across currencies.

### Subscription model

Market Data deliberately manages dynamic subscriptions rather than subscribing
to every market. Ticker, OHLC, order book, and trade streams are in-memory
publishers. Subscription validation requires a current tradable market state.
This is appropriate for detail pages and targeted analysis but is not a
catalogue-wide fact acquisition strategy.

## 6. Kraken Provider Semantics

### Repository fact

The Kraken REST client calls:

```text
GET /0/public/AssetPairs
GET /0/public/Ticker?pair=<provider pair>
GET /0/public/OHLC?pair=<provider pair>&interval=<minutes>
```

The current REST ticker mapper reads the **first** value of Kraken's `v` array
into `TickerEvent.volume`. The mapper does not read the second value, REST VWAP,
or REST trade-count fields because the current DTO does not model them.

The current WebSocket ticker DTO maps `volume` but omits the provider timestamp,
so `KrakenTickerMapper` uses `Instant.now()` as `occurredAt`.

The current REST OHLC mapper maps all eight legacy array values into OHLC,
VWAP, volume, and trades. The current WebSocket OHLC mapper maps interval begin,
timestamp, OHLC, volume, VWAP, and trades.

### Provider documentation fact: REST ticker

Kraken's official REST Ticker documentation defines:

- `v`: volume `[today, last 24 hours]`;
- `p`: VWAP `[today, last 24 hours]`;
- `t`: number of trades `[today, last 24 hours]`;
- `h` and `l`: high/low `[today, last 24 hours]`;
- `o`: today's opening price;
- today's values begin at midnight UTC.

Therefore the current repository mapping of `v[0]` is the provider's **today
since midnight UTC** value, not rolling 24-hour volume.

### Provider documentation fact: WebSocket v2 ticker

The official Spot WebSocket v2 ticker page defines:

- `volume`: 24-hour traded volume in base-currency terms;
- `vwap`: 24-hour volume-weighted average price;
- `high`, `low`, `change`, and `change_pct`: 24-hour values;
- updates are generated on trade events by default, or BBO events when the
  subscription requests `event_trigger: bbo`;
- the feed includes a provider `timestamp`.

The repository currently maps the volume but discards the provider timestamp and
does not map WebSocket VWAP.

### Provider documentation fact: WebSocket v2 OHLC

The official Spot WebSocket v2 OHLC page defines:

- `interval_begin`: start timestamp of the interval;
- `open`, `high`, `low`, `close`: trade prices within the interval;
- `vwap`: volume-weighted average trade price within the interval;
- `trades`: number of trades within the interval;
- `volume`: total traded volume in base-currency terms within the interval;
- updates are generated on trade events;
- `timestamp` is deprecated in favor of `interval_begin`.

The repository uses `intervalBegin` as the open time and provider message time as
`occurredAt`, while it uses the entry timestamp as the close time. The mapper
does not yet preserve an explicit provider message/source version beyond the
deterministic OHLC source ID.

### Provider documentation fact: REST OHLC

The official REST OHLC response is an array:

```text
[time, open, high, low, close, vwap, volume, count]
```

The endpoint states that the last entry is the current, not-yet-committed
timeframe and is always present. It returns at most 720 recent entries. The
official REST schema does not attach an explicit unit label to the `volume`
array position in the same way the WebSocket v2 page does. The repository also
does not attach one. The base-volume interpretation is strongly supported by
the provider's corresponding OHLC contract, but the REST adapter should not
pretend that this unit is explicit in the current Trading OS domain contract.

### Provider documentation fact: REST trades and WebSocket trades

REST recent trades return up to 1,000 entries, each containing price, volume,
time, side, order type, miscellaneous data, and trade ID. WebSocket v2 trade
events expose `qty`, `price`, side, order type, trade ID, and timestamp. The
repository maps quantity and price and derives a base-quantity times price
notional in the pair's quote currency.

## 7. Normalized Activity Investigation

### Field audit

| Repository field | Provider source | Unit / denomination | Window | Timestamp | Current state | Comparability |
|---|---|---|---|---|---|---|
| `TickerEvent.volume` via REST | Kraken REST `Ticker.v[0]` | Provider volume; effectively base quantity under Kraken semantics | Today since midnight UTC | Adapter assigns `Instant.now()` | In memory; not persisted | Not comparable to WS path or quote notional without more metadata |
| `TickerEvent.volume` via WebSocket | Kraken WS v2 ticker `volume` | Base currency | Rolling 24 hours | Provider timestamp exists but is discarded; adapter assigns `Instant.now()` | Latest event in memory; ticker price observation excludes volume | Not comparable to REST-mapped value because window differs |
| `OhlcEvent.volume` via REST | Kraken REST OHLC position 6 | Provider OHLC volume; corresponding Kraken OHLC contract is base currency | Candle interval | Candle open/close plus response-derived occurrence | Returned in memory after normalization | Comparable only when interval and provider semantics agree |
| `OhlcEvent.volume` via WebSocket | Kraken WS v2 OHLC `volume` | Base currency | Candle interval | Interval begin and stream message time | Stream publisher only; not persisted | Comparable within same provider/interval after explicit contract |
| `OhlcEvent.vwap` | Kraken OHLC `vwap` | Quote currency per base unit | Candle interval | Same candle/source timestamps | Mapped but not used by current activity fact | Can convert base volume to quote notional for the same candle, subject to semantic completeness |
| `OhlcEvent.trades` | Kraken OHLC count/trades | Count of trades | Candle interval | Same candle/source timestamps | Mapped | Count is not liquidity and not comparable as value magnitude |
| `TradeEvent.quantity` | Kraken trade volume/qty | Base currency quantity | Individual trade | Provider trade timestamp | In-memory recent 100 events | Not a windowed fact without aggregation |
| `TradeEvent.notional` | Repository derives `price * quantity` | Quote currency per trade | Individual trade | Provider trade timestamp | In-memory recent 100 events | Comparable only within same quote or after FX conversion |
| `RecentTradesSnapshot` | In-memory `TradeEvent` buffer | Mixed individual trade units | Last 100 retained events, not time-based | `generatedAt` plus trade times | In memory | Not a deterministic fixed-window activity fact |
| `OrderBookSnapshot.bidVolume` / `askVolume` | Kraken order book levels | Level quantity, normally base-side quantity | Current configured depth | Provider order-book timestamp | In memory | Not liquidity and not activity |

### Key gaps

The current `TickerEvent` loses:

- volume window;
- volume denomination metadata;
- provider source timestamp;
- VWAP and trade count;
- whether the value is REST or WebSocket source.

The current `OhlcEvent` retains more useful evidence, but still lacks an explicit
volume unit/semantic type. It does retain interval, candle boundaries,
closed/open state, synthetic state, source ID, occurrence, and fetch times.

### Cheapest deterministic approximation supported today

The cheapest honest approximation is **provider-local, quote-local ranking of
completed OHLC base volume aggregated over an explicitly configured closed-candle
window**, provided the adapter contract explicitly records that the source is
Kraken OHLC and the expected unit is base currency. It avoids relying on the
inconsistent REST ticker mapping and avoids collecting individual trade events.

A less expensive catalogue-wide alternative may be a provider-specific bulk REST
ticker request because Kraken permits an omitted pair to return all tradeable
assets, but the current `KrakenHttpClient` always requests one pair and current
TickerEvent semantics do not preserve the needed 24-hour/today distinction. This
is an adapter opportunity, not an approved implementation decision.

### `baseVolume * VWAP`

For one OHLC interval, if:

- volume is confirmed to be base-currency volume;
- VWAP is quote-currency price per base unit;
- both fields cover the same candle interval;
- the candle is complete and non-synthetic;

then `baseVolume * VWAP` is a sound approximation of quote notional traded in
that interval. It is not an exact reconstruction of every trade notional under
rounding, fees, or provider aggregation details, but it has a clear semantic
basis.

It must not be applied to the current REST `TickerEvent` because the current
REST mapper does not retain the matching 24-hour VWAP and maps today's volume.

## 8. Cross-Market Comparability

### Base volume

Base volume cannot be compared directly between BTC/USD, ETH/USD, and PEPE/USD.
One unit of BTC, ETH, and PEPE has different economic scale. Even within a
single quote currency, base quantity is not a meaningful cross-instrument
activity magnitude.

### Quote notional

Quote notional from `baseVolume * VWAP` is more meaningful within one provider,
one quote currency, one observation window, and one compatible market universe.
It is still not automatically globally comparable:

- USD and EUR are different currencies;
- quote currency can itself be volatile;
- stablecoins and fiat-like quotes may not be interchangeable;
- provider venues and market availability differ;
- symbols can have different tick/lot conventions and reporting quality;
- data completeness and window alignment can differ.

### Quote conversion

Conversion to a reference currency would require a time-aligned conversion fact,
source, price side, freshness, and conversion path. Trading Core's valuation
subsystem already demonstrates direct/inverse conversion legs with source
provenance and a maximum observation age. Reusing its exact financial valuation
boundary for market activity would be a responsibility mismatch, but its
provenance requirements are instructive.

### Safer initial alternatives

1. Provider-local and quote-local ranking;
2. ranking only within a homogeneous user-selected universe;
3. percentile/rank within a declared comparison group;
4. reference-currency conversion only after explicit policy and conversion
   provenance are approved.

This report does not select one policy. The first two avoid pretending that
cross-quote values are economically identical.

## 9. Activity Window Analysis

| Window approach | Responsiveness | Stability | Cost / availability | Main risk |
|---|---|---|---|---|
| 15-minute closed candles | Fast; aligns with trigger context | Low to medium; spikes dominate | Existing interval; history request per market | Short-lived bursts and sparse markets |
| 1-hour closed candles | More stable; aligns with setup context | Medium | Existing interval; available from Kraken | Can hide short-lived changes |
| 4-hour closed candles | Stable; aligns with bias context | Higher | Existing interval; fewer aggregation points | Slow to respond and may be too coarse |
| 24-hour closed aggregation | Broad context | High but slow | Requires enough closed candles and more history | Calendar/rolling-window ambiguity |
| Kraken WS ticker 24-hour value | Very cheap after subscription | Provider-defined rolling window | Real-time stream; not currently catalogue-wide | Dynamic subscriptions and current REST/WS semantic split |
| Rolling trade/quote window | Exact desired responsiveness | Depends on sampling | Requires trade event retention/aggregation | Storage, restart, and subscription cost |

The technically easiest reproducible window is a **closed OHLC aggregation** over
an explicitly specified interval/window because the repository already has OHLC
intervals, candle boundaries, `closed`, `synthetic`, `sourceId`, and
`fetchedAt`. It is easier to replay than a rolling in-memory trade buffer or a
provider ticker whose REST and WebSocket paths differ.

No product default duration is selected here. A first implementation should
prefer one configured closed-candle window rather than a vector of short,
medium, and long metrics. A vector becomes justified only when Candidate
Selection policy demonstrates that one window cannot express the intended
selection question.

## 10. Activity Domain Model Options

### Option A: one scalar

```text
activityValue
```

Rejected as insufficient. It loses unit, window, source, completeness, and the
difference between no activity, unavailable data, and stale data.

### Option B: `MarketActivity` fact

Conceptually:

```text
MarketActivity
    marketId
    provider
    baseAsset
    quoteAsset
    measurementKind
    quantity
    unit
    observedFrom
    observedTo
    source
    sourceReferences
    completeness
    freshnessStatus
    calculatedAt
    calculationVersion
```

This is the smallest useful direction, subject to human approval of exact names.
`measurementKind` is important for future Forex/tick-volume compatibility; a
central domain field called simply `volume` would overclaim semantic equivalence.

### Option C: activity vector

```text
shortTermActivity
mediumTermActivity
relativeActivity
```

Defer initially. The vector introduces multiple windows, comparison universes,
and policy semantics before the first fact contract is validated.

### Required status semantics

The fact should distinguish at least:

- `AVAILABLE`: calculation completed and evidence meets the declared contract;
- `INSUFFICIENT_DATA`: source returned some evidence but not enough for the
  requested window/interval;
- `STALE`: evidence exists but exceeds the fact-specific freshness policy;
- `UNAVAILABLE`: provider failure, unsupported source, or no usable source.

`UNKNOWN` may be useful at the candidate-input boundary when the selector cannot
classify a market without converting source failure into a negative value. The
fact model and selection model do not need identical status enums.

## 11. Market Data Readiness

### Existing retrieval path

```text
Market Intelligence role request
    -> MarketDataClient.findOhlc(marketId, interval, limit)
    -> Market Data MarketHistoryService
    -> provider MarketDataProvider.findOhlcHistory
    -> Kraken REST /0/public/OHLC
    -> map raw entries to OhlcEvent
    -> fill missing intervals in memory
    -> return last requested events
    -> Trend Context input mapping and validation
```

The Market Data history endpoint accepts one market, one interval, and one
limit. It does not expose a readiness summary.

### Existing validation and readiness evidence

The current Trend Context path already evaluates:

- required role present;
- interval matches profile;
- candle timestamps are valid;
- OHLC values are valid;
- duplicate candle conflicts;
- candle close is not after the cut-off;
- candle is closed;
- candle is not synthetic;
- calculation-ready candle count reaches the profile minimum;
- latest eligible close freshness against the role interval and configured
  freshness multiplier;
- source occurrence and fetched-at timestamps;
- normalized candle count and excluded count;
- synthetic gap findings;
- source reference and content/provenance fields.

These are valuable ingredients but are embedded in Trend Context's
consumer-specific input model. They do not yet form a generic Market Data
readiness contract.

## 12. Raw vs Normalized Readiness

### Normalizer behavior

`OhlcHistoryNormalizer`:

1. sorts by open time, close time, and source ID;
2. validates the requested interval;
3. deduplicates equal open times;
4. rejects conflicting duplicate candle content;
5. inserts synthetic candles for gaps between known candles;
6. carries the previous close into synthetic OHLC;
7. sets synthetic volume and trade count to zero;
8. marks synthetic candles closed but leaves `synthetic=true`;
9. returns a normalized list.

### Information preserved

After normalization, consumers can still see:

- `synthetic`;
- deterministic synthetic `sourceId`;
- candle boundaries;
- `closed`;
- `occurredAt`;
- `fetchedAt`;
- source identity;
- Trend Context exclusion and gap findings for synthetic candles.

### Information obscured or lost

The current returned model does not preserve a first-class raw-history summary:

- raw count before gap filling;
- expected count versus provider-returned count;
- raw missing interval ranges independently of synthetic output;
- provider pagination/cursor metadata in the domain result;
- whether the provider omitted a range because no trades occurred or because the
  request/history was incomplete;
- provider error category attached to a specific readiness result;
- an explicit raw completeness state;
- a durable raw input snapshot after the request completes.

`synthetic=true` is evidence of a gap, but it is not a complete raw history
contract. A future readiness fact must preserve both the raw acquisition summary
and the normalized calculation summary.

### Conceptual states to support

Names require approval, but the semantics should distinguish:

- raw history complete;
- raw history gapped;
- normalized series continuous with synthetic candles;
- stale latest evidence;
- insufficient depth;
- provider unavailable;
- unsupported interval/provider;
- calculation-ready subset below the consumer requirement.

The system must not report `complete` merely because normalized timestamps are
continuous.

## 13. Multi-Timeframe Readiness

Current Trend Context uses configurable roles whose current defaults are:

```text
BIAS    FOUR_HOURS       required
SETUP   ONE_HOUR         required
TRIGGER FIFTEEN_MINUTES  optional
```

The profile currently requests 60 candles and requires 60 eligible candles by
default. The contributor can retry a role up to five times, increasing the
requested limit until it reaches the provider maximum of 720.

Generic Market Data readiness should be represented per requested interval,
not hardcoded to BIAS, SETUP, and TRIGGER. A consumer should supply required
intervals and minimum depth. Conceptually:

```text
MarketDataReadinessRequest
    marketId
    requested intervals
    required depth per interval
    cut-off

MarketDataReadiness
    marketId
    provider
    per-interval raw and normalized evidence
    statuses and provenance
```

Trend Context can then adapt generic readiness to its profile and retain its
own analytical exclusions. This avoids coupling a public Market Facts contract
to one strategy or profile.

## 14. Readiness Cost Model

### Current cost

Current Trend Context acquisition has approximately:

```text
number of markets x number of configured roles
```

provider history requests as a minimum. With 1,436 markets and the current
three-role profile, that is approximately 4,308 provider calls before retries.
The contributor may perform up to five acquisition attempts per analysis role,
so the worst case is materially higher. The REST OHLC endpoint is pair-specific
and capped at 720 recent entries.

### Scale implications

| Universe | Current per-analysis OHLC cost | Practical implication |
|---|---:|---|
| 10 markets | Approximately 30 role requests before retries | Possible for bounded exploratory work |
| 100 markets | Approximately 300 role requests before retries | Needs batching, caching, or scheduled acquisition |
| ~1,436 markets | Approximately 4,308 role requests before retries | Not acceptable as an incidental Candidate Selection pre-step |
| Larger multi-provider universe | N x providers x intervals | Requires provider-aware incremental architecture |

### Existing cost reducers

- OHLC requests are bounded to 720 entries;
- in-process subscriptions can stream selected markets;
- current price snapshots coalesce concurrent ticker acquisitions per market;
- Market Data has provider ports that can later support bulk or scheduled paths.

### Missing cost reducers

- no persisted OHLC history;
- no persisted readiness summary;
- no catalogue-wide fact cache;
- no bulk OHLC provider contract;
- no passive fact-maintenance job;
- no provider rate-limit model in the Market Data domain;
- no incremental closed-candle aggregation store.

Candidate Selection must not fetch deep history synchronously for every market.
Readiness must be maintained or evaluated through a bounded fact pipeline before
selection, or Candidate Selection must explicitly return `UNKNOWN` for markets
without facts rather than silently performing expensive analysis.

## 15. Spread Investigation

### Current sources

1. REST/stream ticker bid and ask;
2. order-book best bid and ask;
3. `OrderBookSnapshot.spread` as absolute ask minus bid;
4. persisted `PriceObservation` bid and ask.

### Absolute versus relative

Absolute spread is denominated in quote price units and cannot be compared across
BTC/USD, PEPE/USD, or USD/EUR instruments. A relative spread could use a formula
such as spread divided by midpoint, but no formula is selected here.

### Source trade-offs

| Source | Cost | Freshness | Coverage | Limitations |
|---|---|---|---|---|
| Ticker snapshot | One provider acquisition per missing/stale market | Current snapshot policy, default 30 seconds | Potentially broad if acquired per market | Current timestamp/volume semantics are incomplete |
| Ticker WebSocket | Low after subscription | Trade/BBO driven | Only subscribed markets | Dynamic subscriptions are not catalogue-wide |
| Order book | Requires subscription and reconstruction | Provider timestamp | Only subscribed market/depth | Depth-specific and in-memory |
| PriceObservation | Persisted bid/ask | Effective/captured timestamps | Markets that produced ticker observations | Snapshot history is not a full quote stream |

### Classification

**USEFUL_LATER**, not ESSENTIAL_FOR_V1.

Spread may help avoid markets with poor execution conditions, but `MarketState`,
market constraints, and tradability can provide the first hard gate. Current
spread sources are not cheaply catalogue-wide, and a spread fact requires an
explicit relative formula, freshness policy, and source/depth choice. Include it
only when a concrete Candidate Selection policy demonstrates that activity and
readiness leave a material selection ambiguity.

## 16. Liquidity Investigation

The repository has:

- order-book quantities at a configured depth;
- bid/ask total quantities at that depth;
- imbalance;
- recent trade quantity and derived notional;
- OHLC volume, VWAP, and trade count;
- minimum order cost and size constraints.

None is, by itself, a complete liquidity fact:

```text
depth quantity != liquidity
trade count != liquidity
volume != liquidity
```

A defensible liquidity measure would need at least an explicit depth/cost
universe, side semantics, quote conversion or notional semantics, snapshot
freshness, book completeness, and likely a market-impact or executable-size
definition. Current order-book state is limited to subscribed depth and is
in-memory. No such product contract exists.

**Classification: NOT_JUSTIFIED_YET.** Do not introduce `LiquidityScore` or
rename current depth/imbalance values as liquidity.

## 17. Volatility Investigation

### Existing calculations

`TrendContextEngine` calculates profile-specific ATR from true range, an ATR
baseline, an ATR ratio, abnormal-volatility findings, EMA relationships, and
extension multiples. These values are:

- calculated per Trend Context role;
- governed by the Trend Context profile;
- included as Trend Context evidence;
- part of the existing analytical rule version.

`OhlcRangeAnalysisCapability` derives range and `rangePercentage` for a specific
analysis capability and strategy input. It is not a generic Market Data fact
contract.

### Boundary

Do not duplicate the Trend Context ATR implementation into Market Data or a new
generic volatility fact without a distinct semantic requirement. A future generic
realized-volatility or normalized-range fact would need its own input window,
formula, units, source, and version. It must not be presented as equivalent to
Trend Context ATR.

### Classification

**NOT_JUSTIFIED_YET** for Candidate Selection V1. Volatility may be useful to a
future strategy-aware selection policy, but a volatile market is not inherently
more deserving of deeper analysis. Existing Trend Context analysis can consume
its own volatility evidence after a market has been selected.

## 18. Market State Hard Gating

The correct conceptual order is:

```text
Market Catalogue
    ↓
Explicit User Market Scope
    ↓
MarketState hard gate
    ↓
Cheap Market Facts
    ↓
Candidate Selection policy
    ↓
Active Scan effective-scope resolution
```

`MarketState.tradable` is already the deterministic execution-availability gate.
`tradingStatus`, `closureReason`, and `lastUpdated` explain the state. Candidate
Selection should not spend expensive history or order-book calls on a market
already known to be non-tradable, unless a future passive-awareness requirement
explicitly says otherwise.

This gate must not replace backend Active Scan eligibility. ADR-033 requires the
frontend to express requested scope while Market Intelligence remains
authoritative for candidate/effective scope resolution.

The missing concern is a fact-specific policy for stale `MarketState`. The
repository has `lastUpdated` but no generic state-readiness evaluator. A future
implementation should not invent a universal TTL; it should declare the
consumer's state freshness requirement and preserve `UNKNOWN` when it cannot be
met.

## 19. Freshness Model

Facts age differently:

| Fact | Existing freshness evidence | Observation |
|---|---|---|
| MarketState | `lastUpdated` | No universal evaluator found |
| Ticker/price | `occurredAt`, `capturedAt`, 30-second default for current snapshot | Fast-changing |
| Activity | Depends on selected window and close/computation time | Window-specific |
| Spread | Quote/order-book occurrence time | Usually shorter than activity |
| Readiness | Latest eligible candle close, source occurrence, fetch time | Per interval and consumer requirement |
| Liquidity | Would depend on book snapshot/depth | Not currently a fact |
| Volatility | Depends on candle window and formula | Existing Trend Context profile-specific |

Future facts should carry, as applicable:

- `observedAt` or source interval boundaries;
- `calculatedAt`;
- `sourceCutoff` or assessment cutoff;
- `freshnessStatus`;
- source fetch time;
- calculation/rule version;
- fact-specific policy reference.

`validUntil` can be derived from an approved policy but should not be stored as
an arbitrary universal TTL shared by all facts. A fact can be present and
stale; staleness must not be collapsed into absence or low value.

## 20. Provenance Requirements

To explain why one market was ranked above another, the future fact boundary
needs enough information to replay or audit the comparison:

### Minimum likely required

- Trading OS `marketId`;
- provider and provider symbol;
- base and quote assets;
- fact type and measurement kind;
- numeric value with explicit unit/denomination;
- observation window or per-interval boundaries;
- source adapter/path or source contract;
- source identifiers where available;
- source occurrence/fetch timestamps;
- input cutoff;
- completeness/status;
- calculation version;
- calculated timestamp;
- comparison-group identity if a relative rank is later produced.

### Useful but not always necessary

- raw provider request parameters;
- provider pagination cursor;
- content digest of the input snapshot;
- individual source record IDs;
- adapter build/version.

The exact set should follow the existing ADR-048 and Trend Context pattern:
immutable meaning, source identity, occurrence/fetch timestamps, freshness,
validity, and deterministic versioning. Do not persist raw provider payloads by
default if a compact immutable source reference is sufficient for the approved
audit requirement.

## 21. Storage and Caching Options

### On demand only

Pros:

- smallest initial implementation;
- no new persistence schema;
- naturally fresh at request time.

Cons:

- unsuitable for hundreds or thousands of markets;
- repeated provider calls;
- no restart continuity;
- difficult to explain a previous selection after source data changes.

### In-memory cache

Pros:

- low latency after warm-up;
- simple bounded experimentation;
- no database migration.

Cons:

- lost on restart;
- process-local and not shared across instances;
- weak historical reproducibility;
- cannot support reliable passive awareness alone.

### Durable compact fact/read model

Pros:

- supports catalogue-wide selection without re-fetching everything;
- restart continuity;
- explicit provenance and fact status;
- supports future Passive Scanner and debugging;
- avoids storing every raw event.

Cons:

- schema and retention decisions;
- stale-data management;
- update and invalidation complexity.

### Recommendation requiring approval

Use a hybrid:

- do not persist every ticker, trade, order-book delta, or raw OHLC payload in
  the first fact Story;
- maintain compact, versioned activity/readiness fact records or a read model
  once catalogue-wide Candidate Selection is in scope;
- allow bounded on-demand calculation for an explicit small user scope;
- retain source references and raw/normalized completeness metadata.

If the human chooses to defer persistence, Candidate Selection V1 must be
explicitly bounded to warmed/available fact scope and must not imply complete
coverage.

## 22. Update Mechanisms

Different facts should use different update mechanisms:

| Fact | Natural update mechanism | Reason |
|---|---|---|
| MarketState | Market synchronization and provider state updates | Reference/state lifecycle |
| Activity from closed OHLC | Candle-close event or scheduled aggregation | Avoids open-candle instability |
| Provider 24-hour ticker activity | REST bulk polling or subscribed ticker stream | Provider-native rolling window |
| Spread | Ticker/BBO or order-book stream | Fast-changing quote fact |
| Readiness | History acquisition completion and incremental candle maintenance | Changes when coverage/quality changes |
| Liquidity | Future depth snapshot schedule/stream | Requires explicit depth policy |
| Volatility | Future closed-candle calculation | Formula/window dependent |

Do not force all facts through a single refresh loop. A Passive Scanner may later
orchestrate updates, but it should consume Market Data facts rather than become
the only owner of their semantics.

## 23. Provider Neutrality

The core contract should describe provider-neutral semantics, not Kraken fields:

```text
activity measurement kind
base/quote or reference denomination
observation window
source and provider
completeness
freshness
unsupported/unavailable status
```

Kraken should implement the adapter mapping from `v`, OHLC volume/VWAP/trades,
and trade events to that contract. Kraken-specific fields, API array positions,
REST/WS window differences, and symbol identifiers remain inside the adapter.

Unsupported providers must return an explicit unsupported/unavailable status, not
zero. A provider may expose a different measurement kind, such as Forex tick
volume, without pretending it is centralized traded base volume.

The current `MarketProvider` enum already includes KRAKEN, FTMO, BINANCE,
COINBASE, and BYBIT, but only the Kraken market-data adapter was found in this
area. Enum presence is not provider capability evidence.

## 24. Future Forex Compatibility

FTMO/Forex integration is out of scope. The boundary should still allow it:

- crypto exchanges can provide centralized traded base volume;
- Forex providers may provide tick volume or broker-local volume;
- spread may be more useful than activity for some Forex venues;
- quote currencies and contract conventions differ;
- provider session and market-state semantics differ.

The future fact must therefore identify `measurementKind` and unit rather than
expose a universal `volume` that lies about equivalence. Cross-provider ranks
should only compare compatible measurement kinds and declared comparison groups.
Unsupported comparability should produce `UNKNOWN` or `UNAVAILABLE`, not a
fabricated normalized number.

## 25. Candidate Selection Minimum Inputs

Candidate Selection V1 should answer only:

> Inside the explicit user scope, which markets deserve deeper analysis first?

The minimum defensible inputs are:

1. `MarketState` hard gate;
2. one comparable activity fact with explicit unit/window/completeness;
3. per-requested-interval readiness sufficient to avoid dispatching deep analysis
   into obviously unusable data.

The selector must not claim:

- opportunity probability;
- strategy match;
- trade quality;
- Risk approval;
- execution readiness;
- profitability.

Adding spread, volatility, and liquidity to the first input model would increase
complexity and missing-data combinations without repository evidence that they
solve the current problem. They should be added only through separate approved
policy Stories.

## 26. Failure Semantics

Future facts and selection must distinguish:

| Condition | Fact meaning | Candidate behavior |
|---|---|---|
| No usable source | `UNAVAILABLE` | Preserve as unavailable/unknown; do not call it low value |
| Provider timeout/error | `UNAVAILABLE` with provider error provenance | Do not convert to zero |
| Some history but below requested depth | `INSUFFICIENT_DATA` | Do not rank as fully comparable |
| Evidence exists but exceeds policy age | `STALE` | Exclude from positive fresh ranking or surface as unknown per approved policy |
| Provider does not support semantic | `UNSUPPORTED` or equivalent | Do not compare with incompatible providers |
| Partial timeframe set | partial/insufficient readiness | Preserve which intervals are available; do not fabricate completeness |
| Activity value genuinely low | `AVAILABLE` with a low measured value | Distinguish from missing/unavailable |
| Calculation error | explicit calculation failure | Preserve error/version and fail safe |

`UNKNOWN` is not necessarily a fact status. It is a useful candidate-selection
outcome when evidence cannot justify a positive or negative classification.

Candidate Selection must preserve unknown markets in an explainable result or
explicitly report exclusion reason. It must not assign a default neutral score
or make missing data look like poor activity.

## 27. Future Market Structure Integration

Market Structure remains out of scope. No SwingPoint, HH/HL/LH/LL, BOS, CHOCH,
or TrendLineCandidate is proposed here.

The recommended boundary remains compatible with future structural facts because
Candidate Selection can consume a separate deterministic evidence family later:

```text
Market Facts
    + Market Structure evidence
    + Trend Context evidence
    -> policy-specific Candidate Selection input
```

Structural clarity, multi-timeframe alignment, trend quality, and pullback state
should remain versioned analytical evidence owned by deterministic Market
Intelligence, not be inserted into the foundational Market Data fact contract.

## 28. AI Boundary

These facts should remain deterministic because they describe measurable source
data, data quality, or reproducible calculations:

```text
provider data
    -> normalized Market Data
    -> deterministic Market Facts
    -> deterministic Candidate Selection
    -> deeper deterministic analysis
    -> optional AI interpretation
```

AI may later explain why a candidate was prioritized or summarize uncertainty. It
must not be required to determine basic activity, readiness, spread, or data
quality, and it must not override hard MarketState, deterministic exclusions,
Risk, or human authority.

## 29. Performance Considerations

### 10 markets

On-demand per-market facts are acceptable for exploratory development and tests.
Three OHLC timeframes are still approximately 30 calls before retries; a bounded
batch may be practical.

### 100 markets

Per-market OHLC history becomes provider-rate and latency-sensitive. A cache,
scheduled maintenance, or provider bulk path becomes important. Frontend should
not receive raw histories for every market.

### ~1,436 markets

Catalogue-wide readiness cannot be calculated by synchronously invoking current
Trend Context acquisition. It requires incremental maintenance, cached compact
facts, provider-specific bulk capability, or a bounded subset. The candidate
selector must operate on fact availability rather than trigger thousands of
deep requests.

### Larger multi-provider universe

The dominant costs will be provider calls/rate limits, WebSocket subscriptions,
database writes for compact fact snapshots, aggregation CPU, and cross-provider
comparability. A per-provider capability and declared coverage model will scale
better than one universal polling loop.

### Frontend payload

The frontend should receive catalogue fields plus compact fact summaries only
when needed. It should not receive raw OHLC, order-book, or trade streams for a
full candidate universe.

## 30. Observability

No metrics are implemented by this investigation. Future implementation should
make these counters and timings available:

- facts calculated, available, stale, insufficient, unavailable, and unsupported;
- provider error counts by provider/endpoint/fact type;
- fact calculation latency;
- source completeness and synthetic-candle counts;
- number of markets skipped by non-tradable hard gate;
- number of markets lacking readiness before deep analysis;
- Candidate Selection duration and evaluated/unknown counts;
- provider request count and rate-limit/backoff events;
- cache hit/miss and fact age distributions.

These observability points materially explain why a market was or was not
considered without exposing secrets or raw sensitive payloads.

## 31. Security / Authority Boundary

Market Facts are public market information and should not contain:

- broker credentials;
- account balances;
- user-specific risk profiles;
- account eligibility decisions;
- private positions;
- execution intent.

Account-scoped market eligibility remains contextual and downstream. A public
Market Fact may say that a market is tradable according to Market Data; it must
not say that a particular account is authorized to trade it.

The Market Data service already has service JWT protection for internal callers.
Any future fact endpoint must preserve the established public/internal security
classification rather than making internal fact acquisition public by accident.

## 32. Domain Model Alternatives

### Option A: independent typed facts

```text
MarketActivity
MarketDataReadiness
MarketSpread
MarketVolatility
```

Advantages:

- clear semantic ownership;
- independent freshness and versioning;
- partial availability is natural;
- provider support can evolve per fact;
- tests stay focused.

Risks:

- candidate selection needs multiple reads;
- composition must preserve consistent cutoff and provenance.

### Option B: one aggregate `MarketFactsSnapshot`

Advantages:

- one candidate-selection read;
- one consistent assessment snapshot;
- simple projection for a batch.

Risks:

- mixed freshness windows become awkward;
- one unavailable fact can obscure available facts;
- versioning and partial support become coupled;
- aggregate may become a disguised scoring object.

### Option C: hybrid

```text
independent typed facts
    -> composed candidate-input projection
```

Advantages:

- facts remain independently owned and versioned;
- selection receives a consistent cutoff-scoped view;
- partial availability and provenance remain visible;
- aggregate is a consumer projection, not the source model.

Risks:

- more explicit orchestration;
- requires a clear snapshot/cutoff contract.

### Recommendation requiring human approval

Choose **Option C**. Model independent typed facts for activity and readiness,
then compose a candidate-input projection in Market Intelligence using one
selection cutoff and explicit fact statuses. Do not create spread/liquidity/
volatility fact types until their separate Stories are approved.

## 33. Recommended V1 Market Facts

### Essential

#### Existing MarketState gate

Reuse `tradable`, `tradingStatus`, `closureReason`, and `lastUpdated`. Define
consumer-specific state freshness rather than inventing a universal TTL.

#### Normalized activity fact

The first fact should be completed-window activity with:

- market/provider identity;
- base/quote assets;
- measurement kind and unit;
- explicit window boundaries;
- source path and source references;
- completeness and freshness status;
- calculation version and calculated time.

The default comparison universe should initially be provider-local and
quote-local, unless human policy explicitly approves conversion or another
comparison group.

#### Generic per-interval readiness

The readiness fact should preserve:

- requested interval and required depth;
- raw provider coverage summary;
- raw gap/duplicate/error state;
- normalized count and synthetic count;
- calculation-ready count;
- latest eligible close;
- source occurred/fetched times;
- cut-off and freshness status;
- provider support and failure status.

It should accept consumer requirements instead of embedding Trend Context roles.

### Not included in V1

- candidate score or rank formula;
- AnalysisPriority;
- spread;
- liquidity;
- generic volatility;
- Market Structure;
- account eligibility;
- strategy or opportunity semantics.

## 34. Deferred Facts

### Spread — USEFUL_LATER

Defer until a policy demonstrates that relative spread adds selection value beyond
MarketState and activity/readiness, and until source/freshness/coverage are
defined.

### Liquidity — NOT_JUSTIFIED_YET

Defer until Trading OS defines executable-size or market-impact semantics. Do not
rename depth volume, trade count, or raw volume as liquidity.

### Generic volatility — NOT_JUSTIFIED_YET

Defer until a Candidate Selection use case requires it. Do not duplicate
TrendContextEngine ATR or silently treat strategy-specific volatility as a
generic market fact.

### Market Structure — later

Keep as independent deterministic analytical evidence after the foundation.

## 35. Recommended Story Slicing

### Option A: three slices

```text
0069 Activity
0070 Readiness
0071 Candidate Selection
```

Pros:

- smallest technical boundaries;
- activity semantics can be validated before readiness complexity;
- readiness can be tested independently;
- Candidate Selection consumes both after their contracts stabilize.

Cons:

- Activity alone does not yet provide a complete selection capability;
- duplicate provenance/status scaffolding may occur;
- more workflow overhead.

### Option B: two slices

```text
0069 Activity + Readiness foundation
0070 Candidate Selection
```

Pros:

- one coherent foundation contract;
- Candidate Selection begins only after both required fact families exist;
- shared cutoff/provenance/status model is designed once.

Cons:

- larger first Story;
- readiness cost and storage choices must be settled together;
- risk of expanding into spread/liquidity/volatility.

### Recommendation requiring human approval

Prefer **Option B**, but keep the first Story narrowly limited to:

- activity and readiness contracts;
- one provider implementation path for the current Kraken universe;
- source/raw-versus-normalized provenance;
- fact status/freshness;
- bounded cache or compact read model sufficient to avoid N x timeframe calls;
- no candidate ranking.

Then make Candidate Selection a separate Story. If the human cannot approve the
storage/cost boundary in one coherent Story, use Option A and make Activity a
small contract-first slice rather than a ranking feature.

No Story has been created. No Story number has been assigned.

## 36. Risks / Technical Debt

1. REST ticker currently maps today's volume while WebSocket ticker maps 24-hour
   volume into the same domain field.
2. WebSocket ticker provider timestamps are discarded and replaced with local
   `Instant.now()`.
3. REST ticker VWAP and trade count are not mapped.
4. Ticker volume is not persisted in `PriceObservation`.
5. OHLC volume unit is not represented in the domain contract.
6. OHLC history is not persisted and is normalized only in memory.
7. Raw history completeness is not retained as a first-class result.
8. Synthetic candles are visible but can make normalized continuity look like
   raw completeness.
9. Candidate-wide readiness would cause provider-call explosion.
10. Dynamic subscriptions do not provide catalogue-wide coverage.
11. Order-book facts are depth-specific and process-local.
12. Recent trades are capped by event count, not a time window.
13. `MarketProvider` enum entries do not imply implemented provider capabilities.
14. No generic provider rate-limit or fact-maintenance policy is present.
15. Cross-quote conversion exists for valuation but is not automatically valid
   for activity ranking.

## 37. Open Human Decisions

| Decision | Repository evidence | Options | Recommended default | Consequence if deferred |
|---|---|---|---|---|
| First fact families | Activity and readiness are missing; MarketState exists | Activity only; readiness only; both | Both in a bounded foundation | Candidate Selection remains unsafe or expensive |
| Activity semantic | REST today and WS 24h are conflated; OHLC is interval-based | REST ticker; WS ticker; closed OHLC | Closed OHLC with explicit semantics | Continued incomparable values |
| Activity window | Supported OHLC intervals include 15m, 1h, 4h, 1d | One configured window; vector; provider-native 24h | One closed-candle window | Scope remains ambiguous |
| Cross-quote comparability | USD/EUR values differ; valuation conversion exists | provider-local; quote-local; reference currency; rank groups | Provider-local/quote-local first | Global ranking must remain unavailable |
| Readiness ownership | Market Data owns normalized public data; MI owns analysis | Market Data; MI; new service | Market Data bounded capability | Boundary drift and duplicate acquisition |
| Raw versus normalized readiness | Normalizer inserts synthetic candles | normalized only; dual raw/normalized | Dual representation | False completeness risk |
| Readiness consumer contract | Trend Context has profile roles | generic intervals; Trend-specific roles | Consumer-supplied generic intervals | Coupling to one strategy |
| Persistence | No OHLC/readiness persistence exists | on demand; memory; durable compact facts | Hybrid compact fact/read model when catalogue-wide | Restart/cost/reproducibility limitations |
| Spread inclusion | Bid/ask exists but not cheap catalogue-wide | V1; later; never | Later | Candidate V1 remains less execution-aware but simpler |
| Liquidity inclusion | Only depth-limited quantities and buffers | V1; later; defer | Defer | No honest liquidity selection today |
| Volatility inclusion | ATR is Trend Context-specific | generic V1; later; defer | Defer | Avoid duplicated semantics |
| Fact freshness | Existing policies are fact-specific | universal TTL; per-fact policy | Per-fact policy | Stale facts may be misused |
| Provenance | ADR-048 and Trend Context preserve lineage | scalar; compact provenance; raw payloads | Compact immutable provenance | Ranking explanation becomes weak |
| Candidate unknown handling | Existing analysis fails explicitly on missing/stale evidence | zero score; exclusion; unknown | Preserve UNKNOWN/UNAVAILABLE | Selection can fabricate certainty |
| New ADR | Existing ADR-006, 010, 014, 033, 048 cover ownership and authority | none; new ADR first | No ADR for a narrow implementation if boundaries remain unchanged | Create ADR if ownership/storage becomes a new long-term decision |

## 38. Final Questions

### Q1. What does Kraken ticker volume actually mean in every currently used API path?

REST `/0/public/Ticker`: `v[0]` is today's volume since midnight UTC and
`v[1]` is last 24-hour volume. The repository maps only `v[0]` into
`TickerEvent.volume`.

Spot WebSocket v2 ticker: `volume` is 24-hour traded volume in base-currency
terms. The repository maps it into the same `TickerEvent.volume` field but
discards the provider timestamp and uses local `Instant.now()`.

These paths are not semantically equivalent today.

### Q2. What does Kraken OHLC volume mean?

The official Spot WebSocket v2 OHLC documentation explicitly defines it as total
traded volume in base-currency terms within the candle interval. The REST OHLC
schema returns the corresponding OHLCV position but does not explicitly carry a
unit label in the current documented schema; the repository also does not record
the unit. Treat the base-volume interpretation as provider-supported but not yet
explicit in the Trading OS domain contract.

### Q3. Can current ticker volume safely compare PEPE/USD and BTC/USD?

No. Even if both values are from the same Kraken path and same USD quote, base
units of PEPE and BTC are not comparable economic magnitudes. Quote-notional
conversion or a homogeneous ranking policy is required.

### Q4. Can current ticker volume safely compare BTC/USD and BTC/EUR?

No, not as a global numeric comparison. Same-base volume helps, but USD and EUR
notionals are different quote currencies. A time-aligned reference-currency
conversion with provenance or quote-local ranking is required.

### Q5. What is the cheapest deterministic provider-neutral approximation of market activity supported by current data?

Completed, non-synthetic OHLC base volume aggregated over one explicit closed
candle window, compared only within a declared compatible universe. It reuses
existing OHLC history and avoids individual trade aggregation. The fact contract
must add unit/window/source metadata.

### Q6. Is quote-notional activity a sound V1 direction?

Yes, as a direction for same-window, same-quote, base-volume OHLC evidence:
`baseVolume * VWAP` produces approximate quote notional. It is not safe as a
global cross-quote metric without explicit conversion and provenance.

### Q7. Which observation window is technically easiest to reproduce?

A completed OHLC window is easiest to reproduce. It has explicit candle
boundaries, existing intervals, closed state, synthetic state, source IDs, and
fetch time. The product's exact duration remains a human decision.

### Q8. Should activity initially be provider-local / quote-local rather than globally normalized?

Yes, as the recommended conservative default. It avoids unsupported currency
conversion and provider-semantic equivalence claims. Human approval is still
required.

### Q9. What exact readiness information already exists?

Per OHLC event: interval, open/close time, OHLC, volume, VWAP, trade count,
closed, synthetic, source ID, occurred-at, fetched-at. In Trend Context:
deduplicated/sorted candles, calculation-ready subset, synthetic exclusions,
cutoff/open-candle exclusions, synthetic gap findings, required-role validation,
minimum eligible count, latest eligible close, source occurrence, fetched-at,
and role freshness.

### Q10. What readiness information is lost or obscured by OHLC normalization?

There is no first-class raw count, expected-versus-returned coverage, raw gap
range, provider error status, pagination/cursor provenance, or durable raw input
snapshot. Synthetic candles reveal some gaps but do not establish raw
completeness or why a provider range is absent.

### Q11. Can readiness be calculated across ~1,436 markets without making thousands of expensive provider requests?

Not with the current on-demand per-market/per-timeframe OHLC path. Three current
Trend Context roles imply about 4,308 requests before retries. A bulk endpoint,
incremental maintained cache/read model, or bounded scope is required.

### Q12. Where should MarketDataReadiness live?

In a bounded Market Facts capability/module inside Market Data, because it
describes public source coverage and normalized data quality. Trend Context should
adapt it to its own profile requirements; Market Intelligence should not own raw
provider acquisition semantics.

### Q13. Is spread useful enough for Candidate Selection V1?

Not essential for the first fact foundation. It is useful later, particularly for
execution-aware screening, but current coverage is subscription-dependent and
requires an approved relative-spread formula and freshness policy.

### Q14. Is liquidity measurable honestly with current infrastructure?

No, not as a general liquidity fact. Current depth quantities, imbalance, trade
count, and volume are narrower measurements. No `LiquidityScore` is justified.

### Q15. Is generic volatility needed before Candidate Selection V1?

No evidence requires it. Defer it under YAGNI.

### Q16. Which existing Trend Context volatility logic must NOT be duplicated?

`TrendContextEngine` ATR true-range calculation, ATR baseline, ATR ratio,
abnormal-volatility threshold finding, EMA/ATR extension multiple, and their
profile/rule-version semantics must not be copied into a generic Market Fact.
`OhlcRangeAnalysisCapability` range semantics must also remain distinct unless a
separate reusable-fact decision is approved.

### Q17. What are the minimum Market Facts required for Candidate Selection V1?

Existing current `MarketState` hard gate, one explicit comparable activity fact,
and consumer-specified per-interval readiness with raw-versus-normalized
completeness. No spread, liquidity, volatility, structure, or strategy score is
required.

### Q18. Should Market Facts be independent typed facts or one aggregate snapshot?

Independent typed facts plus a composed candidate-input projection is the strongest
direction. It preserves independent freshness and partial availability while
giving Candidate Selection a consistent cutoff-scoped view.

### Q19. Should Market Facts initially be persisted, cached, or computed on demand?

Bounded on-demand calculation is acceptable for small explicit scope. Catalogue-
wide Candidate Selection needs a compact maintained cache/read model, preferably
durable for restart, provenance, and debugging. Raw event/history persistence is
not required initially.

### Q20. How should UNKNOWN / STALE / UNAVAILABLE facts affect future selection?

They must not become zero or a hidden low score. Preserve status and reason;
exclude from positive comparable ranking or return `UNKNOWN` according to approved
policy. `STALE` means evidence exists but cannot satisfy the current freshness
requirement; `UNAVAILABLE` means it cannot be evaluated.

### Q21. How does the design remain valid for future Forex/prop providers?

By carrying measurement kind, unit, provider, source, and comparability group.
Crypto traded volume and Forex tick volume must not be silently treated as one
metric. Provider adapters can expose supported facts and explicit unsupported
states.

### Q22. What is the smallest next implementation Story after 0068?

A narrowly scoped Market Facts foundation covering MarketState gating, one
provenance-bearing closed-OHLC activity fact, and generic per-interval readiness
with a bounded cost/cache strategy. Candidate Selection should be a separate
following Story. The repository shows `0069` as the next unused number, but no
Story is assigned here.

### Q23. Which decisions require human approval before that Story can begin?

Activity semantic and window, comparison universe, quote conversion policy,
readiness ownership, raw-versus-normalized contract, storage/cache boundary,
unknown handling, provider support semantics, and whether the first Story must
cover both activity and readiness.

### Q24. Which facts should explicitly be deferred under YAGNI?

Liquidity scoring, generic volatility, spread unless a concrete selection need is
shown, Market Structure, asset-class inference, global cross-quote normalization,
candidate scoring/weights, and any AI-derived priority.

### Q25. Does any finding require a new ADR before implementation?

Not necessarily for a narrow implementation that stays inside ADR-006 and
ADR-048's existing Market Data ownership. A new ADR is required if the human
approves a new long-term responsibility boundary, a new service, a durable
cross-service fact authority, or a storage/update architecture that materially
changes those decisions.

## Conclusion

Trading OS should not jump directly from User Market Discovery to a ranking score.
The next foundation should make source semantics and data quality explicit first:
MarketState hard gating, completed-window activity, and reusable raw/normalized
readiness. The implementation must remain deterministic, provider-neutral,
provenance-bearing, cost-bounded, and honest about unknown data. Candidate
Selection can then be designed as a separate Market Intelligence policy over an
explicit user scope.

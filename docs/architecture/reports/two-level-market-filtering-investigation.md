# Two-Level Market Filtering Investigation

**Date:** 2026-10-03  
**Branch:** `main`  
**Mode:** Read-only repository and architecture investigation  
**Scope:** User Market Discovery and deterministic Market Candidate Selection  
**Implementation:** None

This report records repository facts and recommendations for future Stories. It
does not create an architecture decision, assign a Story number, define
thresholds or weights, or change existing Stories.

## 1. Executive Summary

Story 0067 exposed a real product problem: a high-volume market can produce a
valid but conflicting Trend Context and correctly result in `NO_SETUP`. High
volume is therefore not equivalent to analytical interest.

Trading OS should distinguish two levels:

```text
User Market Scope
    ↓
Market Candidate Selection
    ↓
Active Scan
    ↓
Market Intelligence
    ↓
Trend Context
    ↓
StrategyEvaluation
    ↓
TradingOpportunity
    ↓
TradePlan
    ↓
Risk
    ↓
Human execution authority
```

**Level 1, User Market Discovery**, should express explicit trader intent and
reduce the visible or requested universe using facts already owned by Market
Data. The immediate safe slice is provider, symbol/base/quote text search, and
market tradability/status. Current catalogue data does not support asset class,
volume, liquidity, spread, volatility, readiness, or watchlist filters without
new contracts or deterministic derivations.

**Level 2, Market Candidate Selection**, should be a deterministic,
explainable Market Intelligence application concern above deep analysis and
before Active Scan dispatch. It may prioritize markets only inside the user
scope. It must never expand that scope and must not claim trade probability,
opportunity quality, StrategyMatch, or Risk approval.

The smallest useful first implementation Story is a Level-1 user discovery
slice based only on existing catalogue fields. Candidate Selection should be a
separate later Story after the required normalized activity/readiness facts and
human policy are approved. Market Structure should be a later enrichment, not a
prerequisite for the first Level-1 filters or the initial candidate-selector
boundary.

## 2. Current Market Catalogue

### 2.1 Catalogue path

The current catalogue path is:

```text
Kraken asset-pair synchronization
    -> KrakenMarketData.getMarkets()
    -> KrakenMarketMapper.toDomain()
    -> Market Data Market entity
    -> MarketRepository.findAll()
    -> MarketService.findAll()
    -> MarketController GET /api/v1/markets
    -> Gateway /api/v1/markets/** route
    -> Angular MarketService.findAll()
    -> Markets page / Active Scan selectors
```

Relevant evidence:

- `Market` persists provider, symbol, base asset, quote asset,
  `MarketConstraints`, and `MarketState`.
- `MarketSynchronizationImpl` obtains provider markets and merges them by
  `(provider, symbol)`, updating assets, constraints, and market state.
- `MarketController.findAll()` returns the complete repository catalogue in
  `MarketResponse` form.
- Gateway routes `/api/v1/markets/**` to Market Data.
- Angular `MarketService.findAll()` calls `GET .../v1/markets` through the
  Gateway.
- There is no catalogue pagination, query filtering, server-side sorting, or
  discovery-specific response in the current contract.
- The current runtime evidence recorded approximately 1,436 Kraken markets for
  an Active Scan scope. That is large enough to make an unfiltered manual list
  inconvenient but still small enough for a bounded V1 catalogue read.

### 2.2 Current catalogue fields

`MarketResponse` exposes:

| Field | Source | Meaning | Classification |
|---|---|---|---|
| `marketId` | Market entity | Trading OS market identity | `CATALOGUE_FIELD` |
| `provider` | Market entity / provider enum | Provider identity; current enum includes Kraken, FTMO, Binance, Coinbase, Bybit | `CATALOGUE_FIELD` |
| `symbol` | Provider mapping | Provider-normalized display/trading symbol | `CATALOGUE_FIELD` |
| `baseAsset` | Provider mapping | Base asset parsed from provider symbol | `CATALOGUE_FIELD` |
| `quoteAsset` | Provider mapping | Quote asset parsed from provider symbol | `CATALOGUE_FIELD` |
| `marketState.tradingStatus` | Provider synchronization | `OPEN`, `CLOSED`, or `HALTED` | `CATALOGUE_FIELD` and current state |
| `marketState.tradable` | Market State builder/provider state | Whether an order can currently be executed under Trading OS semantics | `CATALOGUE_FIELD` and current state |
| `marketState.closureReason` | Market State builder/provider state | Provider/domain reason for unavailable state | `CATALOGUE_FIELD` and current state |
| `marketState.lastUpdated` | Market State builder | State update time | `CATALOGUE_FIELD` and freshness metadata |
| `marketConstraints.minimumOrderSize` | Provider asset-pair metadata | Minimum order quantity | `CATALOGUE_FIELD` |
| `marketConstraints.minimumCost` | Provider asset-pair metadata | Minimum order notional/cost constraint | `CATALOGUE_FIELD` |
| `marketConstraints.tickSize` | Provider asset-pair metadata | Price increment | `CATALOGUE_FIELD` |
| `marketConstraints.quantityPrecision` | Provider asset-pair metadata | Quantity precision | `CATALOGUE_FIELD` |
| `marketConstraints.pricePrecision` | Provider asset-pair metadata | Price precision | `CATALOGUE_FIELD` |

The catalogue does **not** expose a first-class asset class. `baseAsset` and
`quoteAsset` are symbols, not a canonical classification such as CRYPTO, FOREX,
EQUITY, or COMMODITY. Inferring asset class from symbol names would create
provider and product semantics that do not currently exist.

### 2.3 Other market-data paths

Market Data also exposes or maintains runtime facts outside the catalogue DTO:

- ticker events: bid, ask, last, volume, occurrence time;
- OHLC history/events: prices, volume, VWAP, trade count, interval, closed flag,
  synthetic flag, source ID, occurrence time, and fetch time;
- order-book snapshots: levels, best bid/ask, absolute spread, bid/ask depth
  volume, imbalance, occurrence time;
- recent trades: trade side, price, quantity, notional, trade ID, occurrence
  time, with an in-memory maximum of 100 trades per market;
- market price snapshots: last, bid, ask, tradability, occurrence time,
  freshness status, and source snapshot identity/version;
- active stream state: in-memory subscription reference counts and stream
  buffers.

These facts are not included in `GET /api/v1/markets` and are not currently a
catalogue-wide read model.

## 3. Current User Market Selection UX

### 3.1 Markets page

The Markets page loads the complete catalogue once through `MarketService.findAll`
and uses a reactive `combineLatest` of:

- the catalogue observable;
- a `BehaviorSubject<MarketFilter>`.

The toolbar provides a debounced text search. Current implementation searches
case-insensitively across symbol, base asset, quote asset, and provider. The
`MarketFilter` model already contains optional `provider` and `tradable`
properties, but `Markets.filterMarkets()` currently uses only `search`; those
properties are not rendered or applied.

The page has no sorting, pagination, watchlists, volume display, realtime data,
or server-side filtering. A row navigates to the market detail page.

### 3.2 Market detail page

The market detail page loads one market by ID and subscribes on demand to:

- ticker;
- OHLC;
- order book at supported depth;
- recent trades.

It is a market inspection surface, not a reusable market-discovery control. Its
stream subscriptions are selected for the one open market and are not a
catalogue-wide screening mechanism.

### 3.3 Decision Workspace

Decision Workspace is account-first. It resolves a `DecisionContext` for the
selected account. The backend returns all catalogued markets with eligibility
and reasons, plus `eligibleMarketIds`. Current eligibility is only:

- market exists;
- market is tradable.

The frontend searches eligible context markets by symbol, provider, or market ID,
requires at least two characters, and shows at most 40 matches. The full context
market list remains in memory. Unavailable markets can be disclosed separately
with backend reasons.

This is already an account-scoped market picker, but it is not a general
discovery model. It must not become the owner of cross-feature market facts or
candidate-ranking semantics.

### 3.4 Active Scan market selection

The Active Scan panel independently loads the full catalogue through
`MarketService.findAll()` when the panel initializes. It offers:

- `SPECIFIC`: a native multi-select over the complete catalogue;
- `ALL_ELIGIBLE`: explicit broad scope, resolved authoritatively by Market
  Intelligence.

For a specific scan, selected market IDs are sent as `requestedMarketIds`. For
all-eligible, they are omitted. The backend resolves candidate and effective
scope and applies the current hard eligibility rule. The panel does not apply
provider, asset, tradability, volume, or readiness filters before submission.

The scan result filter is about scan outcomes, not market discovery:
opportunity, no opportunity, excluded, failed, and processing.

### 3.5 Trade Planning market selection

The manual TradePlan path resolves a selected market through Trading Core's
market service and validates account/market constraints before planning. It does
not expose a reusable catalogue-discovery control. Market selection must remain
distinct from candidate ranking because a user may manually plan a market that
automatic candidate selection did not prioritize.

### 3.6 Canonical reusable location

The canonical reusable boundary should be a shared **Market Discovery read
model/service**, with these responsibilities:

- obtain and expose catalogue facts from Market Data;
- represent explicit user filters and sort intent;
- provide reactive frontend state for Markets, Decision Workspace, and Active
  Scan;
- preserve account eligibility as a backend authority where required.

The first V1 can reuse `MarketService.findAll()` and extract a shared Angular
filter/view-model helper without a new backend contract. A future bounded or
paginated discovery API should be owned by Market Data for catalogue facts, not
by individual pages. Decision Workspace may add account eligibility as a
contextual layer; Active Scan remains authoritative for effective scope.

## 4. Available Filter Data Matrix

| Candidate filter/sort | Existing fact | Current location | Classification | Notes |
|---|---|---|---|---|
| Provider | Yes | `MarketResponse.provider` | `READY_NOW` | Exact provider enum value is available. |
| Symbol text | Yes | `MarketResponse.symbol` | `READY_NOW` | Existing Markets search. |
| Base asset | Yes | `MarketResponse.baseAsset` | `READY_NOW` | Existing Markets search. |
| Quote asset/currency | Yes | `MarketResponse.quoteAsset` | `READY_NOW` | Existing Markets search. |
| Market ID | Yes | `MarketResponse.marketId` | `READY_NOW` | Useful for stable selection, not a trader-facing discovery dimension. |
| Trading status | Yes | `MarketState.tradingStatus` | `READY_NOW` | Static/current catalogue state. |
| Tradable/open | Yes | `MarketState.tradable` | `READY_NOW` | Consistent with ADR-010 execution semantics. Backend remains authoritative. |
| Closure reason | Yes | `MarketState.closureReason` | `READY_NOW` | Useful for explaining exclusion, not normally a positive ranking signal. |
| Market-state freshness | Yes | `MarketState.lastUpdated` | `READY_NOW` | Can be displayed or used as a staleness guard, subject to a human-approved policy. |
| Minimum order size/cost | Yes | `MarketConstraints` | `READY_NOW` for display/filtering | Filtering by account affordability would require account and currency semantics; do not infer that in Level 1. |
| Price/quantity precision | Yes | `MarketConstraints` | `READY_NOW` for display | Not an analytical-interest metric. |
| Asset class | No | None | `NOT_AVAILABLE` | Do not infer from asset symbols or provider. Requires canonical classification. |
| Watchlist membership | No | None | `NOT_AVAILABLE` | No watchlist model or persistence exists. |
| Ticker bid/ask | Yes | `TickerEvent` / stream snapshot | `REALTIME_FIELD` | Not in catalogue response; on-demand per market. |
| Ticker last price | Yes | `TickerEvent` / price snapshot | `REALTIME_FIELD` | Not in catalogue response; freshness is explicit in snapshots/streams. |
| Ticker volume | Yes, semantics incomplete | `TickerEvent.volume` | `REALTIME_FIELD` | Raw provider value has no domain unit/window metadata. Not safe as universal cross-market ranking. |
| Relative ticker spread | Derivable | Ticker bid/ask | `DERIVABLE` | Needs mid-price, null/zero handling, timestamp, and a comparable snapshot policy. |
| Order-book best bid/ask | Yes | `OrderBookSnapshot` | `REALTIME_FIELD` | Requires active subscription and in-memory state. |
| Absolute order-book spread | Yes | `OrderBookSnapshot.spread` | `REALTIME_FIELD` | Absolute price units are not comparable across markets. |
| Relative order-book spread | Derivable | Best bid/ask | `DERIVABLE` | Potentially comparable only with explicit formula/window/freshness semantics. |
| Depth volume | Yes | `bidVolume`, `askVolume` | `REALTIME_FIELD` | Limited to subscribed depth; not a durable full-book liquidity fact. |
| Order-book imbalance | Yes | `OrderBookSnapshot.imbalance` | `REALTIME_FIELD` | Existing deterministic derivation, but stream-local and depth-specific. |
| Recent trade count | Derivable only for buffer | `RecentTradesSnapshot.trades` | `DERIVABLE` | Represents retained recent trades, not a canonical interval trade count. |
| Recent notional activity | Derivable only for buffer | `TradeEvent.notional` | `DERIVABLE` | Limited to the in-memory recent-trade window and provider stream availability. |
| OHLC volume/VWAP/trades | Yes | `OhlcEvent` | `REALTIME_FIELD` / historical field | Interval-specific and available through on-demand history or active subscriptions. |
| OHLC range | Derivable | OHLC high/low/close | `DERIVABLE` | Existing range analysis derives this for a specific capability, not a reusable catalogue fact. |
| ATR | Yes inside Trend Context | Trend Context role assessment | `REALTIME_FIELD` in analysis output | Not exposed as a standalone market-data metric; do not duplicate Trend Context logic. |
| Realized volatility | No reusable model | None | `NOT_AVAILABLE` | Requires an explicitly owned deterministic derivation. |
| OHLC history availability | Observable per request | `MarketHistoryService` result | `DERIVABLE` | No catalogue-wide readiness projection or cheap status endpoint exists. |
| OHLC history depth | Observable per response | Returned event count | `DERIVABLE` | Requires a requested interval/limit and must distinguish provider history from normalized output. |
| OHLC freshness | Partial | `occurredAt`, `fetchedAt`, candle close | `DERIVABLE` | Needs a readiness policy per interval. |
| OHLC gaps | Yes during normalization | `OhlcHistoryNormalizer` | `DERIVABLE` | Normalizer fills gaps with synthetic candles; raw-gap and safe-analysis semantics must remain distinct. |
| Synthetic candles | Yes | `OhlcEvent.synthetic` | `REALTIME_FIELD` | Available after normalization; Trend Context already treats synthetic data as relevant evidence. |
| Active stream/subscription | Yes in process | Subscription maps/state services | `REALTIME_FIELD` | Not a durable provider-health or catalogue-readiness contract. |
| Provider health | No reusable catalogue fact | None | `NOT_AVAILABLE` | Requires explicit health/readiness ownership. |

## 5. Volume Semantics

### 5.1 Current mappings

The canonical `TickerEvent` contains one `BigDecimal volume` field. Kraken REST
maps the provider ticker `v` array to the first value. Kraken WebSocket ticker
data maps its provider volume field directly. The domain field has no:

- unit declaration;
- base-volume or quote-volume designation;
- aggregation window;
- provider interval metadata;
- normalization/currency conversion metadata.

The current code therefore preserves a provider value but does not establish its
meaning as a Trading OS-wide metric.

`OhlcEvent.volume` is interval-specific provider OHLC volume. It is accompanied
by `vwap` and `trades`, but it also has no explicit unit field. OHLC volume
cannot be assumed to have the same semantics as ticker volume merely because
both are named `volume`.

`TradeEvent.notional` is a per-trade notional value supplied by the provider
mapping. It is not currently aggregated into a durable activity metric.

### 5.2 Comparability conclusion

Raw ticker volume is **not semantically comparable enough** to be an
authoritative cross-market filtering or sorting metric in its current form.

The repository does not prove:

- whether the selected Kraken ticker value is 24-hour volume, today volume, or
  another provider-defined period;
- whether it is base-asset or quote-asset volume;
- whether all future providers will use the same unit/window;
- whether PEPE/USD and BTC/USD values can be compared directly;
- whether a current value is fresh enough for a catalogue-wide sort.

Displaying the raw provider value with provider/window provenance may be useful
later, but treating it as a universal `volume` rank would invent semantics.

### 5.3 Required future activity metric

The safest candidate for a future comparable activity metric is a normalized
**quote-notional activity over an explicit common observation window**, for
example an aggregation of closed OHLC volume converted using the corresponding
VWAP, or an aggregation of trade notionals where the provider contract makes
that possible.

The metric would need to preserve:

- quote currency and conversion basis;
- observation start/end time;
- source interval and provider;
- completeness and freshness;
- whether the value is measured, converted, or unavailable.

This is a recommendation for a future domain decision, not a selected formula.
The window, source hierarchy, treatment of missing data, and cross-quote
comparison policy require human approval. Until then, raw volume is a display
fact or provider-local filter only, not a universal AnalysisPriority input.

## 6. Liquidity / Spread Capabilities

### 6.1 Facts already present

Trading OS currently knows the following when a stream is active:

- ticker bid and ask;
- order-book best bid and ask;
- order-book levels up to supported depth 10 or 25;
- absolute spread;
- bid-side and ask-side depth quantities;
- a deterministic depth imbalance value;
- recent trades with side, price, quantity, notional, ID, and timestamp;
- OHLC VWAP and trade count for each returned candle.

The `OrderBookStateService` reconstructs order-book state in memory and derives
best prices, absolute spread, depth totals, and imbalance. The recent-trades
service retains at most 100 trades per market in memory. These are useful
inspection facts but are not persisted catalogue-wide discovery facts.

### 6.2 What can support discovery

Immediately available for a selected market inspection:

- bid/ask display;
- absolute spread display;
- depth display;
- recent trade notional/count display;
- OHLC VWAP/trades display if history is requested.

Not immediately safe as cross-market discovery filters:

- absolute spread, because price scales differ;
- raw depth quantity, because asset units differ;
- current depth imbalance, because it depends on selected depth and a transient
  stream;
- retained recent-trade count, because the buffer is capped and subscription
  dependent;
- ticker volume, because its semantics are incomplete.

A relative spread such as spread divided by a defined mid-price could be a
future comparable metric, but it requires a deterministic null/zero policy,
observation timestamp, provider normalization, and a freshness window. No
liquidity score should be introduced from these facts without an explicit
domain decision.

There is no current VWAP aggregation across a common discovery window, no
durable depth snapshot read model, and no provider-neutral liquidity measure.

## 7. Volatility Capabilities

### 7.1 Existing deterministic volatility information

Trend Context calculates ATR inside `TrendContextEngine` for each configured role.
It derives true range, current ATR, a baseline, an ATR ratio, and an abnormal
volatility finding. The result is part of the Trend Context assessment and role
evidence. It is not a reusable Market Data catalogue field.

The same engine uses ATR for EMA slope normalization and extension detection.
This makes the current ATR implementation specific to Trend Context profile and
rule semantics. Copying it into Market Data or a candidate selector would risk
duplicating strategy-adjacent logic.

The existing OHLC range capability derives high-to-low range and
`rangePercentage` for its own analysis input. It is also not a general market
volatility projection.

OHLC candles expose enough raw facts to derive other volatility measures, but no
reusable realized-volatility model exists.

### 7.2 Ownership boundary

Market Data should continue to own normalized candles and their provenance:
interval, timestamps, closed/synthetic state, source ID, and fetch time.

A future reusable volatility metric should be a deterministic Market Data or
dedicated market-facts capability built on those facts, with an explicit
interval/window/version contract. Market Intelligence may consume that fact for
Candidate Selection. Trend Context remains the owner of its own ATR/profile
semantics and should not be duplicated by the selector.

The clean boundary is therefore:

```text
Market Data facts
    -> reusable, provider-neutral volatility fact (future, if approved)
    -> Candidate Selection analysis priority
```

This investigation does not choose ATR, realized volatility, range percentage,
or any threshold for that future fact.

## 8. Market Data Readiness

### 8.1 Existing readiness evidence

Trading OS has pieces of a readiness contract:

- `MarketState.tradable` and `lastUpdated`;
- `OhlcEvent.occurredAt` and `fetchedAt`;
- `OhlcEvent.closed`;
- `OhlcEvent.synthetic`;
- OHLC normalization that sorts, deduplicates, and fills missing intervals with
  explicit synthetic candles;
- Trend Context validation that can expose stale, missing, synthetic, gapped,
  and incomplete evidence;
- ticker snapshot freshness with a configurable `stale-after` duration;
- active subscription state and in-memory order-book/trade buffers.

### 8.2 Existing gap

There is no reusable `MarketDataReadiness` concept or catalogue-wide endpoint
that cheaply answers, for a market:

- whether 4H, 1H, and 15m history exists;
- how many raw and usable candles are available;
- whether each interval is fresh enough;
- whether raw gaps occurred;
- whether the returned history contains synthetic candles;
- whether the provider is healthy;
- whether an active subscription is currently producing data.

`GET /api/v1/markets/{marketId}/ohlc` performs an on-demand provider request.
It is not a cheap readiness probe across 1,436 markets. The normalizer can make
gapped history appear continuous by inserting synthetic candles, so readiness
must preserve raw completeness separately from normalized analysis input.

### 8.3 Future concept

`MarketDataReadiness` or an equivalent read model would be useful for Candidate
Selection. It should be a factual, timestamped, interval-aware result rather
than a trade or opportunity score. It should distinguish:

- available and fresh;
- available but stale;
- incomplete;
- gapped;
- synthetic-contaminated;
- provider unavailable;
- not measured.

The concept should be owned by Market Data or a dedicated market-facts
capability. Market Intelligence should consume it rather than reimplementing
history inspection. Its computation and caching policy require a separate Story
and human decision.

## 9. Level 1 — User Market Discovery

Level 1 is a user-controlled reduction of the market universe. It must not
silently become a candidate-ranking or trade recommendation layer.

### 9.1 Filter classification

| Filter | Classification | Repository basis |
|---|---|---|
| Provider | `READY_NOW` | `MarketResponse.provider` exists and the Markets page already searches it. |
| Symbol text | `READY_NOW` | Existing reactive search. |
| Base asset | `READY_NOW` | Existing catalogue field and search. |
| Quote currency/asset | `READY_NOW` | Existing catalogue field and search. |
| Open/tradable | `READY_NOW` | `MarketState.tradingStatus` and `tradable`; backend remains authoritative for execution/scan eligibility. |
| Closure/status | `READY_NOW` | Existing state and closure reason. |
| Minimum order/cost | `DERIVABLE_WITH_SMALL_CHANGE` | Existing constraints can be displayed or filtered, but account currency/affordability semantics must not be inferred. |
| Minimum volume/activity | `REQUIRES_NEW_DOMAIN_CAPABILITY` | Raw ticker/OHLC volume lacks a universal unit/window contract. |
| Spread | `REQUIRES_NEW_DOMAIN_CAPABILITY` | Runtime bid/ask/order-book facts exist, but no catalogue-wide comparable snapshot/read model exists. |
| Liquidity/depth | `REQUIRES_NEW_DOMAIN_CAPABILITY` | Depth is subscription-local and asset-unit dependent. |
| Volatility | `REQUIRES_NEW_DOMAIN_CAPABILITY` | ATR is Trend Context-specific; no reusable volatility fact exists. |
| Data readiness | `REQUIRES_NEW_DOMAIN_CAPABILITY` | No interval-aware readiness contract exists. |
| Asset class | `NOT_RECOMMENDED` until canonical taxonomy exists | Symbol inference would create unsupported semantics. |
| Watchlist | `NOT_AVAILABLE` | No watchlist domain or persistence exists. |

### 9.2 Sorting classification

Safe current sorting dimensions:

- symbol;
- provider;
- base asset;
- quote asset;
- trading status/tradability;
- stable catalogue order using provider, symbol, and market ID.

Potential later sorting dimensions:

- normalized quote-notional activity;
- relative spread;
- data readiness state;
- normalized volatility;
- watchlist order.

Those later dimensions require explicit metric contracts and freshness rules.
Raw ticker volume, raw absolute spread, raw depth quantity, and current
OpportunityScore should not be presented as cross-market ranking semantics.

### 9.3 User-scope invariant

The user-selected scope must be represented explicitly and preserved through
the scan request. Candidate Selection may narrow it, but must never expand it:

```text
User scope
    ∩ deterministic account/broker eligibility
    ∩ candidate-selection inputs
    = effective analysis scope
```

For example, a user scope limited to Forex cannot receive a crypto candidate
because the crypto market has a higher analysis priority.

## 10. Level 2 — Market Candidate Selection

### 10.1 Responsibility

Candidate Selection interprets inexpensive, normalized market facts to decide
which allowed markets deserve deeper analysis first or at all. It is not a
market-data ownership concern because it interprets facts. It is not the deep
Market Intelligence analysis itself because it should run before expensive
Trend Context and strategy evaluation.

The cleanest boundary is a deterministic Market Intelligence application/domain
capability adjacent to Active Scan scope resolution:

```text
Market Data catalogue/facts
    ↓
User Market Scope
    ↓
Account/broker hard eligibility
    ↓
Candidate Selection / Analysis Priority
    ↓
ActiveScan effective analysis scope
    ↓
single-market AnalysisExecution children
```

Market Data remains responsible for facts. Market Intelligence owns the
contextual decision to prioritize analysis. Active Scan owns orchestration,
persistence, dispatch, reconciliation, and result projection.

### 10.2 Candidate selection must not become eligibility or Risk

Candidate Selection may produce:

- candidate membership;
- deterministic analysis priority;
- component explanations;
- data-quality exclusions or deferrals.

It must not produce:

- a StrategyMatch;
- a TradingOpportunity;
- a TradePlan;
- Risk approval;
- a buy/sell signal;
- expected profitability;
- an instruction to execute.

Hard eligibility remains distinct from relevance/priority. A low-priority market
must remain manually analyzable if it is otherwise allowed and the user
explicitly selects it.

### 10.3 Initial inputs

The initial selector should use only approved factual inputs. The first useful
set is likely:

- tradability;
- provider/market identity;
- normalized activity, only after its semantics are approved;
- data readiness/freshness, only after a readiness contract exists.

Liquidity, volatility, and Market Structure should be added as independently
versioned facts later. This keeps the selector explainable and avoids embedding
Trend Context or future structural algorithms into a first ranking formula.

## 11. Scanner Integration

### 11.1 Existing scanner concepts

ADR-033 and current code already provide the required orchestration concepts:

- Active Scanner is intention-driven;
- Passive Scanner is batch-driven and remains architectural direction only;
- `ActiveScan` is a persisted multi-market orchestration aggregate;
- `requestedMarketIds` represents user scope;
- `candidateMarketIds` represents the resolver's candidate universe;
- `effectiveMarketIds` represents the eligible analysis scope;
- `MarketEligibilityDecision` preserves deterministic exclusions;
- one `AnalysisExecution` remains single-market;
- one `ActiveScan` may orchestrate many child executions;
- `PipelineRun` remains child-analysis provenance, not scan aggregate state;
- current Active Scan dispatch is asynchronous and results are reconciled per
  market;
- specific-market and explicit all-eligible modes are already supported.

### 11.2 Recommended relationship

Candidate Selection should eventually feed Active Scan, but should not become
the Active Scan itself:

```text
User intent and selected scope
    -> candidate universe
    -> hard eligibility
    -> candidate selection / priority
    -> effective selected candidates
    -> ActiveScan persistence and dispatch
```

The Active Scan should snapshot the candidate-selection inputs/result used for
that run so that the scope and priority are reconstructable. This is a future
contract decision, not an implementation in this investigation.

The current on-demand manual Active Scan must remain intact. A trader must be
able to explicitly select and analyze a low-priority market, subject to the
same deterministic hard eligibility and safety checks. Automatic priority must
not become a hidden exclusion.

### 11.3 Passive Scanner

The future Passive Scanner can use a configured universe and recurring batches,
independent of the selected account. It may maintain reusable market awareness
that later reduces Active Scan work when freshness and compatibility are proven.

Candidate Selection should not assume Passive Scanner exists. The shared
boundary should accept current Market Data facts now and optionally consume
fresh passive observations later. Passive batch scheduling, watchlist
universes, and passive-to-active reuse remain separate concerns.

## 12. Analysis Priority Semantics

A future deterministic `AnalysisPriority` model should be a factual,
explainable prioritization result, not a trading output.

Minimum properties:

- market identity and provider;
- calculation timestamp and input cut-off;
- model/rule version;
- deterministic output representation;
- component contributions or reasons;
- input provenance for every component;
- freshness and completeness state;
- explicit unavailable/insufficient-data outcome;
- stable tie behavior without implying semantic superiority;
- whether the result is advisory ordering or an eligibility exclusion.

The model must preserve these non-equivalences:

```text
AnalysisPriority != TradingOpportunity
AnalysisPriority != StrategyMatch
AnalysisPriority != expected profitability
AnalysisPriority != OpportunityScore
AnalysisPriority != BUY/SELL recommendation
AnalysisPriority != Risk approval
```

No formula, weights, thresholds, or cross-market normalization are selected by
this investigation. ADR-033 already rejects treating current OpportunityScore
values as a globally calibrated cross-market ranking.

## 13. Future Market Structure Integration

Market Structure is not part of this investigation or the first Level-1 Story.
The filtering architecture should nevertheless allow future facts to enrich
Candidate Selection through a versioned input boundary:

```text
Market Data facts
    + activity/liquidity/volatility/readiness facts
    + optional Market Structure facts
    -> Candidate Selection inputs
    -> explainable AnalysisPriority
```

Future facts may include:

- `SwingPoint`;
- HH/HL/LH/LL classifications;
- structural trend;
- multi-timeframe structural alignment;
- `TrendLineCandidate`.

They should arrive as deterministic, provenance-bearing analytical facts. The
selector should be able to consume them as optional versioned components rather
than hardcoding structural algorithms into Market Data, the Angular UI, or the
Active Scan dispatcher.

The absence of Market Structure must not delay useful Level-1 filters based on
provider, assets, and tradability. It also must not be silently approximated by
Trend Context fields or raw volume.

## 14. API / Performance Considerations

### 14.1 Current full-catalogue behavior

`GET /api/v1/markets` returns the full catalogue and is currently reused by the
Markets page, Active Scan specific selection, Trading Core clients, and
Market Intelligence scope resolution. The observed Kraken universe of roughly
1,436 markets is manageable for a V1 static catalogue response and frontend
filtering, subject to normal response-size monitoring.

The same approach becomes weaker when:

- Forex or additional providers expand the universe materially;
- discovery needs runtime metrics per market;
- sorting requires fresh provider data;
- the UI requires pagination or virtualized results;
- Active Scan must evaluate candidate facts across the full universe;
- different users need account/provider-specific catalogues.

### 14.2 Options

| Option | Strengths | Risks | Assessment |
|---|---|---|---|
| A. Frontend filtering over current catalogue | Smallest change; reuses current API and reactive patterns; suitable for current static fields | Full payload; duplicated page-specific filtering unless centralized; cannot safely filter on runtime metrics | Best immediate V1 for Level 1 |
| B. Backend query parameters on `/markets` | Reduces payload; centralizes catalogue filtering; supports future pagination/sort | Query contract grows; runtime metrics still need a separate source; account eligibility remains a separate concern | Good next step when catalogue size or filter count justifies it |
| C. Dedicated market-discovery read model | Can combine normalized facts, readiness, pagination, caching, and provenance | New ownership, storage, refresh, and consistency lifecycle; premature before metric semantics exist | Future option after normalized facts are approved |
| D. Hybrid | Static catalogue query server-side plus reactive client filtering; separate candidate/readiness service for runtime facts | More than one contract; requires clear boundaries | Long-term direction, not first Story |

### 14.3 Recommendation

Use Option A for the first Level-1 Story, but centralize filter semantics in a
shared Angular discovery model rather than duplicating predicates in each page.
Keep `GET /api/v1/markets` as the static catalogue source.

Move to a hybrid contract when one of these becomes real:

- catalogue size makes full reads expensive;
- users require server-side pagination;
- runtime activity/readiness facts become supported discovery inputs;
- candidate selection needs a bounded, cached, provider-neutral read model.

Do not add ticker/order-book/OHLC fields directly to `MarketResponse` merely to
avoid a new read model. Their freshness and subscription semantics differ from
catalogue identity.

## 15. Angular Integration

The frontend already uses the preferred patterns in the Markets and Decision
Workspace surfaces:

- `Observable` catalogue streams;
- `BehaviorSubject` filter/selection state;
- `combineLatest`, `map`, `switchMap`, and `shareReplay`;
- discriminated loading/loaded/error views;
- `async` pipe;
- URL query parameters for Decision Workspace account and market selection;
- Angular reactive forms for the Markets search toolbar.

The reusable boundary should be a Market Discovery feature/service that exposes
typed streams such as:

- catalogue state;
- user filter state;
- filtered/sorted market state;
- selected scope state;
- optional backend eligibility state.

Markets page, Decision Workspace, and Active Scan should compose this boundary
with their own semantics:

| Surface | Owns | Reuses |
|---|---|---|
| Markets page | broad catalogue exploration and display | Market Discovery catalogue/filter streams |
| Decision Workspace | selected account, eligible/unavailable display, one-market inspection | Discovery search and market identity model |
| Active Scan | explicit specific/all-eligible command and scan lifecycle | Discovery selection, labels, and catalogue state |
| Trade Planning | selected market validation and plan context | Market identity/details only; no automatic candidate ranking |

The current pages have duplicated selection logic and different limits/search
behavior. A future shared boundary should remove that duplication without
making the frontend authoritative for account eligibility or scan scope.

Runtime ticker, order book, recent trades, and OHLC streams should remain tied to
the selected market. They should not be opened for the entire catalogue as a
side effect of rendering discovery results.

## 16. Filter Persistence Options

Repository evidence does not establish a saved user market-scope preference
model. The smallest useful V1 is therefore:

1. transient reactive UI state for ordinary Markets browsing;
2. URL/query state for a reproducible selected market or explicit discovery
   state where a route already exists, following the Decision Workspace pattern;
3. request-local `requestedMarketIds` for an Active Scan, persisted as part of
   the existing `ActiveScan` scope snapshot.

Do not introduce backend user preference persistence or watchlists in the first
discovery Story. A later saved-preferences/watchlist Story can define ownership,
versioning, provider changes, deleted markets, and cross-device behavior.

The choice between transient state and URL state for a new Markets-page filter
is a product/UX decision. The repository supports both patterns but does not
mandate one for that page.

## 17. Architecture Constraints

The following constraints are binding for future implementation:

- Market Data owns market facts, catalogue identity, constraints, state, and
  provider normalization.
- Market Data must not own trade relevance, opportunity ranking, or strategy
  decisions.
- User Market Scope is explicit and must never be expanded by Candidate
  Selection.
- Account/broker hard eligibility remains deterministic and authoritative.
- Candidate Selection is analysis prioritization, not a trade recommendation.
- Active Scanner remains intention-driven and persists requested, candidate, and
  effective scopes.
- Passive Scanner remains batch-driven and account-independent where appropriate.
- `AnalysisExecution` remains single-market.
- `PipelineRun` remains scoped to one analysis execution.
- Trend Context remains analytical evidence and must not be duplicated or
  bypassed by filtering.
- StrategyEvaluation/StrategyMatch, TradingOpportunity, TradePlan, Risk, and
  execution boundaries remain downstream and separate.
- Manual market selection remains possible even when automatic priority is low.
- No raw provider metric may be presented as cross-provider comparable without a
  declared semantic contract.
- No thresholds, weights, or scoring formula may be introduced without human
  approval and deterministic provenance.
- No Market Structure algorithm is introduced in the filtering foundation.

## 18. Gaps / Technical Debt

Confirmed repository gaps:

- `MarketFilter.provider` and `MarketFilter.tradable` exist but are not applied
  by the Markets page.
- Markets and Active Scan each load and manage catalogue selection separately.
- Decision Workspace has another account-scoped search and result-limit policy.
- The catalogue response has no pagination, query filters, or discovery-specific
  read model.
- Asset class is not a canonical Market field.
- Watchlists do not exist.
- Ticker volume has no unit/window metadata.
- There is no provider-neutral normalized activity metric.
- There is no catalogue-wide comparable liquidity or relative-spread snapshot.
- Order-book and recent-trade state is in memory and subscription-dependent.
- ATR exists inside Trend Context but is not a reusable volatility fact.
- There is no reusable `MarketDataReadiness` contract.
- OHLC normalization inserts synthetic candles, so raw data completeness must be
  preserved if readiness is introduced.
- Provider health is not exposed as a market discovery fact.
- Active Scan currently performs only hard existence/tradability eligibility;
  contextual relevance and ranking remain deferred.
- Passive Scanner orchestration, configured universes, recurring execution, and
  persisted market awareness are not implemented.

Known non-goals rather than defects:

- current OpportunityScore is not a global market ranking;
- no automatic selection should replace explicit manual Active Scan scope;
- Market Structure is not required for Level-1 discovery;
- no user preference or watchlist persistence is implied by this investigation.

## 19. Recommended Story Slicing

The repository has Story artifacts through `0067` and no later canonical Story
number. No number is assigned here.

### Story A — User Market Discovery V1

Scope:

- centralize reusable Angular catalogue/filter state;
- support provider, symbol, base asset, quote asset, and tradable/status filters;
- support stable catalogue sorting only;
- reuse `GET /api/v1/markets`;
- use reactive streams and `async` pipe;
- preserve account-scoped Decision Workspace eligibility;
- preserve explicit Active Scan specific/all-eligible semantics;
- no volume, liquidity, volatility, readiness, watchlist, or asset-class
  inference.

This is the recommended first implementation Story because it improves manual
market discovery without inventing new market semantics.

### Story B — Market Discovery Contract Hardening

Only if catalogue size or UX evidence requires it:

- define backend query filtering and pagination for static catalogue facts;
- define provider/base/quote/status sort semantics;
- preserve stable IDs and explicit user scope;
- update Gateway and Angular contracts together;
- establish caching/refresh behavior.

This Story is optional for the current approximately 1,436-market universe.

### Story C — Normalized Activity and Market-Data Readiness

Before Candidate Selection uses volume, liquidity, volatility, or readiness,
define and validate the provider-neutral factual contracts separately:

- activity unit/window/quote currency and source provenance;
- relative spread/depth semantics if approved;
- reusable volatility fact ownership;
- interval-aware raw/normalized OHLC readiness;
- freshness, gaps, synthetic data, and provider health semantics.

No scoring or candidate ranking belongs in this Story.

### Story D — Deterministic Market Candidate Selection

After human approval of the facts and policy:

- intersect user scope with hard eligibility;
- compute a deterministic, versioned AnalysisPriority or equivalent;
- preserve component contributions and source freshness;
- snapshot candidate decisions in Active Scan scope/provenance;
- feed selected candidates to Active Scan without changing single-market
  analysis;
- preserve explicit manual market selection;
- keep priority distinct from opportunities, StrategyMatch, profitability, and
  Risk.

The formula, weights, thresholds, and behavior for insufficient data must be
specified by the Story and approved before implementation.

### Story E — Optional Passive/Candidate Reuse

Only after Passive Scanner product scope exists:

- define configured universes and cadence;
- persist reusable market awareness;
- define freshness/compatibility rules for Active Scan reuse;
- preserve account-independent passive scope.

### Market Structure timing

Market Structure should occur after the Level-1 discovery foundation and can
occur before or after Candidate Selection depending on product priority. It
should not block Story A. If Candidate Selection is implemented before Market
Structure, its input contract should allow optional future structural facts so
that the selector can be versioned/enriched rather than redesigned.

## 20. Open Human Decisions

The following require explicit human/product/architecture approval before
implementation:

- whether Level-1 V1 is frontend-only over the current full catalogue;
- whether provider/base/quote fields are sufficient for the first scope UX;
- whether a canonical asset-class taxonomy is needed before Forex-only or
  crypto-only filters are promised;
- whether filter state should be transient, URL-backed, or both;
- the semantic contract for normalized activity, including quote currency,
  aggregation window, source, and missing-data behavior;
- whether relative spread, order-book depth, recent trades, or VWAP belong in
  discovery and how they become comparable;
- ownership and contract for reusable volatility facts;
- ownership and lifecycle of `MarketDataReadiness`;
- whether Candidate Selection narrows Active Scan automatically or only orders
  candidates for bounded dispatch;
- whether low-priority markets remain manually analyzable in every scan mode;
- whether and how candidate decisions are persisted/snapshotted;
- the name and semantics of `AnalysisPriority`;
- the first candidate inputs and deterministic policy;
- whether the selector can exclude markets for data quality or only order them;
- pagination/caching requirements for future provider and Forex growth;
- the sequencing of Market Structure relative to Candidate Selection;
- whether Passive Scanner work is required before passive facts can be reused.

## Final Questions

**Q1. Which useful Level-1 filters can Trading OS implement immediately from
existing canonical data?** Provider, symbol text, base asset, quote asset, and
trading status/tradability. Closure reason and static constraint display can be
added without new market semantics. The existing Markets page already implements
text search; provider/tradable filter fields need wiring.

**Q2. Is current volume semantically comparable enough to use for filtering and
sorting across markets?** No. The current ticker volume is an unlabelled raw
provider value with no domain unit, base/quote designation, window, or
cross-provider normalization.

**Q3. What metric should represent "activity" if raw volume is insufficient?** A
future normalized quote-notional activity metric over an explicit common window
is the strongest candidate, using documented OHLC/VWAP or trade-notional sources.
The exact formula and window require approval and are not selected here.

**Q4. Which liquidity/spread information already exists?** Ticker bid/ask,
order-book best bid/ask, absolute spread, depth levels, bid/ask depth volume,
imbalance, recent trades, trade notionals, and OHLC VWAP/trade counts. These are
runtime/subscription-local or interval-specific, not a durable comparable
catalogue metric.

**Q5. Can current Market Data cheaply expose data readiness?** Not currently
across the catalogue. It exposes ingredients such as timestamps, closed and
synthetic flags, fetch time, and history requests, but no cheap interval-aware
readiness contract. A future `MarketDataReadiness` fact would be useful.

**Q6. Where should User Market Discovery live architecturally?** As a shared
Market Data catalogue/read contract with a reusable Angular Market Discovery
service/view model. Pages compose it; Decision Workspace and Active Scan retain
their own account/scope authority.

**Q7. Where should deterministic Market Candidate Selection live?** In Market
Intelligence, adjacent to scope/relevance resolution and before Active Scan deep
analysis. Market Data supplies facts; Active Scan owns orchestration.

**Q8. Should Candidate Selection feed Active Scan while preserving manual market
selection?** Yes. It should narrow/order only within explicit user scope and
manual specific-market selection must remain available subject to hard
eligibility.

**Q9. What existing scanner/scope concepts can be reused?** ADR-033, the
`ActiveScan` aggregate, `requestedMarketIds`, `candidateMarketIds`,
`effectiveMarketIds`, `MarketEligibilityDecision`, exclusion reasons,
account-aware scope resolution, explicit specific/all-eligible UI, asynchronous
dispatch, and one `AnalysisExecution` per market.

**Q10. What is the smallest useful AnalysisPriority model without inventing
trading semantics?** A versioned deterministic market-level ordering/exclusion
record containing market identity, calculation time, input cut-off, component
facts/reasons, provenance, freshness/completeness, and an explicit unavailable
state. It must not contain trade direction, opportunity probability, StrategyMatch,
or Risk meaning.

**Q11. Should the initial Candidate Selection depend on Market Structure?** No.
Useful Level-1 catalogue filters and a future fact-based selector should not wait
for structural algorithms that do not exist.

**Q12. How can Market Structure enrich it later without redesigning the filtering
architecture?** Add optional, versioned structural facts to the Candidate
Selection input/provenance boundary. Keep SwingPoint and structural algorithms
owned by their own deterministic capability; the selector consumes their facts
as explainable components.

**Q13. Should initial filtering happen client-side, server-side, or through a
dedicated read model?** Client-side over the current static catalogue for Story
A. Introduce backend query/pagination when catalogue size requires it. Add a
dedicated/hybrid read model only when normalized runtime metrics and readiness
need caching, provenance, and bounded queries.

**Q14. What is the first implementation Story?** A new, separately numbered
Story for User Market Discovery V1 using current catalogue fields and reactive
Angular reuse. The number must be assigned through the repository's human Story
workflow; this investigation does not guess or create it.

**Q15. What decisions require human approval before implementation?** The
approved Level-1 filter scope, asset-class taxonomy, persistence/URL behavior,
normalized activity and liquidity semantics, readiness ownership, Candidate
Selection ownership and narrowing policy, AnalysisPriority semantics and policy,
snapshot/provenance requirements, and Market Structure sequencing.

## Sources Reviewed

Repository source and architecture evidence reviewed:

- `market-data` Market entity, DTO, state, constraints, synchronization,
  controller, repository, provider mappers, ticker/OHLC/order-book/trade models,
  snapshot and subscription services;
- `market-intelligence` market client, Active Scan scope request/resolution,
  eligibility decisions, effective scope, and scan command/projection models;
- Angular `MarketService`, Markets page/toolbar/detail, Decision Workspace,
  Active Scan panel, and related frontend models;
- ADR-008, ADR-010, ADR-014, ADR-033;
- Stories 0005, 0022, 0025, and 0035;
- Story 0067 closure and validation evidence for the PEPE/USD product learning.

DevLog applicability was attempted through the Story Agent for `trading-os`, but
the request failed with no usable result. This report does not depend on DevLog
context.

No production code, tests, existing Stories, ADRs, thresholds, weights, or
Market Structure artifacts were modified.

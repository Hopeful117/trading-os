# STORY 0106 - DETERMINISTIC MARKET INTELLIGENCE BASELINE INVESTIGATION

DATE: 2026-10-15
REPOSITORY HEAD: `2c7779c19d220f859a544124f6426d49f034fcae`
ORIGIN/MAIN: `2c7779c19d220f859a544124f6426d49f034fcae`
BRANCH: `main`
WORKTREE STATUS: `main...origin/main`; seven pre-existing untracked runtime/investigation reports are preserved and were not modified.

Repository inspection commands were executed from the actual repository. `git fetch origin` succeeded. Story 0105 is merged in `origin/main` by merge commit `2c7779c1`. No reset, stash, clean, revert, discard, or overwrite operation was performed.

==================================================
1. EXECUTIVE SUMMARY
==================================================

Trading OS already has deterministic analytical implementation, but it is not yet a single canonical baseline. The current deterministic capabilities are:

- `spread-analysis`: deterministic bid/ask spread and percentage from `MARKET_SNAPSHOT`.
- `ohlc-range-analysis`: deterministic high/low range and price change from a bounded `HISTORICAL_OHLC` artifact.
- `market-structure-analysis`: deterministic confirmed swing extraction from Trend Context history.
- `trend-context-analysis`: a larger deterministic, versioned multi-timeframe assessment using market structure, EMA, ATR, pullback, extension, invalidation and timeframe alignment rules.

The actual `IntelligenceContext` can currently provide market identity, a quote snapshot, bounded 15-minute OHLC, Trend Context history, and combined financial-news/economic-event context when those contributors are requested. It does not currently provide an order-book or recent-trades context section, although `ORDER_FLOW` exists in the section enum and is requested by the active strategy. There is no durable historical analytical dataset or context snapshot store.

The most important prerequisite is temporal: `IntelligenceContextAssembler` establishes a cutoff, but `TrendContextRoleHistoryContextContributor` acquires and maps history using a separate `Clock` boundary and does not use the request cutoff as its acquisition boundary. Therefore the canonical no-lookahead guarantee is confirmed for the 0105 market snapshot, OHLC and News paths, but only partial for Trend Context until this discrepancy is corrected.

The smallest trustworthy future baseline should not add a score or a collection of popular indicators. It should formalize and measure three atomic, typed capabilities:

1. Quote quality / spread measurements.
2. Bounded temporal OHLC measurements, initially range and returns, with volatility only when explicit historical completeness rules are satisfied.
3. Existing versioned Trend Context / market-structure evidence, treated as a separate structured deterministic family rather than rebuilt as generic indicators.

Economic-event proximity is a useful fourth candidate, but the current context contract exposes it only through the combined News payload and string-valued event measurements. It should remain optional and follow a small contract clarification. Order-book imbalance, liquidity depth, absorption, and temporal order flow are not ready because the canonical context does not expose the required data.

This investigation recommends a reduced baseline rather than a broad indicator catalogue.

==================================================
2. STORY 0105 FOUNDATION
==================================================

Analysis Context:

The merged implementation uses `IntelligenceContext` as the canonical immutable envelope. It contains an `EnumMap` copied into an unmodifiable map, context sections, `ContextQuality`, diagnostics, a schema version, an `ArtifactFingerprint`, market ID, analysis mode, objective and evaluation cutoff. Section payloads are typed `ContextPayload` records.

Market identity:

The envelope carries a `UUID marketId`. The `MARKET_IDENTITY` section is contributed by Market Data and the snapshot payload carries the provider-neutral market ID and symbol. The contract does not yet carry a complete canonical instrument identity object in the envelope, such as base asset, quote asset, provider-neutral instrument type and constraints. It is sufficient for current market-scoped work but should not be treated as a complete cross-venue instrument key without further evidence.

Analysis time:

`evaluationCutoff` is the canonical temporal boundary produced by `IntelligenceContextAssembler`. It is request-supplied when present, otherwise obtained from the assembler clock. There is no separate immutable `analysisEvaluatedAt` in `IntelligenceContext`; some derived payloads and capabilities use their own `Instant.now()`, which is unsuitable as reproducibility metadata.

Immutability:

The envelope and section records are immutable in the normal Java sense. Maps and lists are copied. `NewsContext` and `HistoricalOhlcContext` copy their lists. The contract is not durable and does not protect against future changes in every possible non-record payload type; the implementation report explicitly identifies that future non-record payloads need a canonical serializer.

Freshness:

Section status and `ContextProvenance` preserve available/stale/incomplete/unavailable semantics, source occurrence time and fetch/capture time. Freshness policy is partly section-specific and is not a single analyzer eligibility policy.

Availability:

`ContextSectionStatus` distinguishes `AVAILABLE`, `STALE`, `MISSING`, `UNAVAILABLE`, `UNSUPPORTED` and `INCOMPLETE`. Required failures degrade the assembled context; optional failures remain explicit.

Provenance:

Market Data provenance retains source snapshot references and source versions where supplied. OHLC retains source IDs and provider/version information. News retains source event/item IDs, source timestamps, fetched timestamps and normalization versions.

No-lookahead:

Market snapshot, identity, OHLC and News contributors reject, exclude or classify facts newer than the request cutoff. Trend Context is only partially covered because `TrendContextRoleHistoryContextContributor` uses its own `clock.instant()` boundary rather than `request.evaluationCutoff()` and does not apply the same contributor-level filtering contract as `HistoricalOhlcContextContributor`.

Versioning:

`IntelligenceContext` has schema version `canonical-context-v1` through `ContextFingerprintFactory`; the fingerprint includes request metadata, requirements, section status, provenance and payload representations. Trend Context separately includes `profileId`, `profileVersion`, `ruleVersion`, input fingerprints and assessment fingerprints. There is no durable context snapshot table.

Persistence:

No new canonical raw-context persistence was introduced in Story 0105. Market Data persists some price/valuation facts, while OHLC history is acquired through provider-backed service calls. Market Intelligence artifacts and executions have existing persistence, but there is no general historical Analysis Context store suitable as a replay dataset.

0105 invariants:

| Invariant | Status | Evidence / limitation |
|---|---|---|
| Canonical market identity | PARTIAL | UUID and identity section exist; complete provider-neutral instrument identity is not one explicit envelope value. |
| Explicit analysis time | PARTIAL | `evaluationCutoff` exists; no separate stable evaluation timestamp and some capabilities call `Instant.now()`. |
| Bounded context | CONFIRMED | Requirements, bounded contributors, limits and section selection exist. |
| Immutable context | CONFIRMED | Defensive copies and immutable records are used; future non-record payloads need explicit canonical serialization. |
| Provider neutrality | CONFIRMED | Domain context payloads are normalized; provider details remain in adapters/provenance. |
| Facts separated from analyses | PARTIAL | Typed fact sections and deterministic findings are distinguishable, but `DETERMINISTIC_FINDINGS` is also a possible context section and the broader capability/artifact model is not one unified baseline contract. |
| Missing versus unavailable | CONFIRMED | Separate section statuses and diagnostics are implemented. |
| Freshness preserved | PARTIAL | Status/provenance are preserved, but analyzer-specific eligibility is not centralized and Trend Context has a cutoff discrepancy. |
| Provenance preserved | CONFIRMED | Source references, occurrence/fetch timestamps and normalization/provider versions are retained where supplied. |
| No-lookahead semantics | PARTIAL | Confirmed for snapshot, identity, OHLC and News; not globally confirmed for Trend Context. |
| Deterministic ordering | CONFIRMED | News/OHLC ordering and fingerprint canonicalization are explicit; order-flow is not yet in context. |
| Reproducible context identity/versioning | PARTIAL | SHA-256 fingerprint exists, but no durable context snapshot and no complete explicit analyzer/configuration identity in the envelope. |

The Trend Context cutoff issue is a prerequisite for any baseline that treats Trend Context as replayable evidence. Story 0106 must not silently compensate for it inside an analyzer.

==================================================
3. AVAILABLE INPUT DATA
==================================================

| Input | Contract available | Populated today | Historical | Timestamp semantics | Freshness / limitations |
|---|---|---|---|---|---|
| Market identity | YES | YES when Market Data knows market | Current catalogue, not analysis history | Market Data response; section provenance | UUID/symbol available; complete canonical instrument envelope is partial. |
| Market state / tradability | YES | YES in snapshot and Market Data contracts | No durable state history in Market Intelligence | Snapshot `occurredAt` and `capturedAt` | `tradable` is execution/tradability state, not market regime. |
| Last price | YES | YES when snapshot available | `PriceObservation` is persisted, but context uses current snapshot | Effective/occurred and capture timestamps | Snapshot may be unavailable or incomplete. |
| Bid / ask | YES | YES when provider snapshot supplies them | Persisted price observations can retain them | Effective/occurred and capture timestamps | Raw quote data is enough for spread; no historical quote series exposed to context. |
| Spread | DERIVED | Existing `spread-analysis` calculates it | Replayable only when bid/ask snapshot is retained/reconstructable | Inherits snapshot occurrence | No justified NORMAL/WIDE/EXTREME thresholds exist. |
| Candles | YES | YES through OHLC and Trend Context contributors | Provider-backed bounded history, not durable MI history | Open/close, occurred, fetched; closed/synthetic flags | General OHLC contributor requests fixed `FIFTEEN_MINUTES` and 200 candles; Trend Context requests up to 720 per role. |
| Volume | YES in OHLC DTO | YES in `OhlcResponse`, but `OhlcPoint` retains only volume without provider semantics | Same bounded provider history | Candle timestamps and fetched time | Volume meaning is provider/instrument dependent; no normalized volume contract for cross-market comparison. |
| Order book | Provider/model contract exists | NOT in `IntelligenceContext` | No persistent order-book snapshot history | `OrderBookSnapshot.occurredAt` | In-memory reconstruction exists in Market Data, but no MI contributor/client path. |
| Recent trades | Provider/model contract exists | NOT in `IntelligenceContext` | In-memory buffer capped at 100 trades per market | Trade `occurredAt`; buffer generated time | No durable trade tape or acquisition cutoff contract for MI. |
| Economic events | News Service contract and persistence exist | Available only through combined News context response when populated | News Service stores events | Scheduled, actual, source-updated and fetched timestamps | MI client maps numeric values as strings; no dedicated economic section or typed comparability guarantee. |
| Financial news | News Service contract and persistence exist | Available only through combined News context response when populated | News Service persistence exists | Published, source-updated and fetched timestamps | Marketaux remains pending; provider coverage and duplication affect counts. |
| Provenance | YES | YES for canonical contributors | References/digests vary by source | Source/fetch timestamps and versions | No universal immutable raw-context snapshot. |

Data classification:

AVAILABLE AND POPULATED:

- Market identity and current tradability snapshot.
- Bid, ask and last price when the current Market Data snapshot is available.
- Bounded OHLC candles, including OHLCV fields, under the current provider-backed contracts.
- Existing Trend Context history and deterministic assessment path when enough candles are returned.
- News and economic event records when News Service returns a usable context.

AVAILABLE CONTRACT BUT DATA MAY BE ABSENT:

- Bid or ask individually.
- Historical candles and complete temporal metadata.
- Economic event actual/forecast/previous values.
- News articles, event lists, and source metadata.
- Complete Trend Context roles.

FOUNDATION ONLY:

- Order-book models and in-memory reconstruction.
- Recent-trades models and in-memory buffer.
- Market Facts readiness/activity summaries, which are useful operational facts but are not currently canonical Analysis Context sections.

NOT AVAILABLE:

- Canonical order-book context for analyzers.
- Durable quote, trade or order-book time series for general replay.
- A typed, dedicated economic-event context in Market Intelligence.
- A general historical context/result dataset.

==================================================
4. DETERMINISTIC ANALYSIS PRINCIPLES
==================================================

Facts:

Direct normalized observations owned by Market Data or News Service, such as bid, ask, candle OHLCV, event scheduled time, event actual value, article publication time and source metadata.

Features:

Mechanical transformations of facts with explicit input window and numeric semantics, such as absolute spread, spread basis points, simple return, log return, range, realized volatility, depth ratio or event distance.

Analyses:

Versioned rule results with explicit semantics and evidence, such as confirmed swing structure, Trend Context direction/regime/phase, or an input-quality finding. A classification is only deterministic if its thresholds and configuration are declared, versioned and justified.

Interpretations:

Bullish/bearish claims without a declared rule, opportunity quality, sentiment, confidence, probability, expected edge, trade recommendation and semantic headline meaning. These belong to future strategy, ML, LLM or human interpretation boundaries.

Explicit boundaries:

- No generic `MarketScore`, `BullishScore`, `OpportunityQuality` or confidence number.
- No conversion of missing data to zero, neutral or false.
- No prediction or profitability claim.
- No TradePlan, Opportunity, Signal or Risk authorization.
- No account, balance, challenge, open PnL or broker credentials in market analyses.
- No provider-specific analyzer logic.
- No static order-book snapshot interpreted as absorption.
- No LLM recalculation of deterministic mathematics.

==================================================
5. PRICE / QUOTE ANALYSIS
==================================================

Candidates:

- `midpoint = (bid + ask) / 2`, only when both sides are valid.
- `absoluteSpread = ask - bid`, only for `0 < bid <= ask`.
- `relativeSpread = absoluteSpread / midpoint`.
- `spreadBps = relativeSpread * 10_000`.
- Last-price versus midpoint discrepancy, if a defined quote-time relationship is available.
- Price change or return, only from two observations with an explicit horizon.
- Distance from recent extrema, only from a defined historical OHLC window.

Data readiness:

Current bid/ask/last values exist in `MarketSnapshotContext`. The existing `SpreadAnalysisCapability` calculates absolute spread and percentage with `BigDecimal`, but not basis points and not a dedicated typed baseline result. One current observation is insufficient for returns, extrema distance or volatility.

Recommendation:

Retain quote quality as a V1 atomic analyzer. Expose raw measurements plus explicit unavailable/invalid states. Do not classify spread regimes until asset-specific threshold policy exists. Validate `bid <= ask`, positive midpoint and timestamp freshness at the analyzer boundary or through a shared eligibility policy.

==================================================
6. SPREAD ANALYSIS
==================================================

Candidates:

- Absolute spread.
- Relative spread.
- Spread in basis points.
- Bid/ask validity and quote completeness finding.

Formula:

For valid positive quotes:

```text
midpoint = (ask + bid) / 2
absoluteSpread = ask - bid
relativeSpread = absoluteSpread / midpoint
spreadBps = relativeSpread * 10_000
```

`BigDecimal` is preferable for domain calculation and exact test fixtures. Scale and rounding must be part of the algorithm version.

Edge cases:

- Missing bid or ask: `INSUFFICIENT_DATA`.
- Non-positive quote: `INVALID_INPUT`.
- `bid > ask`: `INVALID_INPUT`; do not return a negative spread.
- `bid == ask`: valid zero spread if the quote timestamp is valid.
- Zero midpoint: `INVALID_INPUT`, not zero spread percentage.
- Stale or future quote: `STALE_INPUT` or `INVALID_INPUT` according to shared policy.
- Different quote and last-price timestamps: preserve the distinction; do not combine silently.

Recommendation:

V1 candidate. Raw spread measurements are deterministic and immediately useful for quote quality and later liquidity analysis. `NORMAL`, `WIDE` and `EXTREME` classifications are deferred because no approved threshold profile exists and thresholds would vary by instrument and market condition.

==================================================
7. RETURNS / TREND / MOMENTUM
==================================================

Historical readiness:

The context contains bounded OHLC history. The general historical contributor currently uses 15-minute candles and a fixed limit of 200. The Trend Context contributor uses configurable role intervals, defaults to 4-hour, 1-hour and 15-minute roles, requests 60 candles and can expand to 720. This is enough for bounded calculations when temporal validity and completeness pass, but it is not durable historical retention and is not yet a general horizon abstraction.

Candidates:

- Simple return between two explicitly selected closed candles.
- Log return when positive prices and downstream statistical use justify it.
- High-low range and price change over the loaded window, already present in `ohlc-range-analysis`.
- Existing confirmed swing structure and Trend Context assessment, already versioned and tested.
- EMA/SMA relationships only as part of the existing Trend Context profile, not as an unbounded indicator catalogue.

Rejected indicators for the initial baseline:

- RSI: adds an arbitrary period and normalization with no demonstrated need beyond returns/structure.
- MACD: duplicates multi-window trend information and introduces additional periods and signal-line configuration.
- Bollinger Bands: requires a selected mean, standard deviation window and multiplier; no current baseline need.
- Generic momentum score: opaque and likely arbitrary.

Recommendation:

Use simple returns and existing structure/Trend Context. Prefer one canonical configurable horizon model over hardcoded 1m/5m/15m/1h/4h/24h outputs. Start with the intervals already present in context: `FIFTEEN_MINUTES`, `ONE_HOUR`, `FOUR_HOURS` where the requested role history is complete. Do not create a new generic `trend = BULLISH` result; preserve `TrendContextAssessment` semantics and provenance.

==================================================
8. VOLATILITY
==================================================

Data readiness:

OHLC history has enough fields for high-low range, true range and rolling return volatility when enough closed, non-synthetic and cadence-valid candles are present. `MarketDataReadiness` exposes normalized count, synthetic count, missing intervals, cadence violations, duplicates and conflicts. The canonical `HistoricalOhlcContext` currently does not carry those readiness diagnostics directly, so a future volatility analyzer must not infer completeness from list size alone.

Candidates:

- Per-candle high-low range.
- Normalized range, only with a valid positive reference price.
- True range and ATR, already present in the existing Trend Context engine under explicit profile rules.
- Rolling standard deviation of returns, if the context exposes sufficient clean history.
- Realized volatility over a declared interval and lookback.

Lookback requirements:

- Exact sample count and interval must be part of the input/configuration.
- Closed candles only; exclude synthetic candles unless an explicit future contract says otherwise.
- Missing or duplicate/conflicting candles must produce an explicit degraded result.
- Irregular observations cannot be treated as regular returns without a declared policy.
- The cutoff must apply to both candle occurrence and capture/fetch time.

Recommendation:

V1 candidate only as a bounded `realized-volatility` or range measurement with explicit readiness. The minimum implementation should reuse the existing Trend Context ATR semantics where it is already authoritative, rather than add a second incompatible ATR. A generic rolling standard deviation can be a later extension after a reusable historical-input contract exists. Do not produce HIGH/MEDIUM/LOW volatility regimes without a versioned threshold profile.

==================================================
9. VOLUME
==================================================

Data readiness:

OHLC DTOs contain `volume`, and Market Data provider models contain `volume`, `vwap` and trade count. The canonical `OhlcPoint` retains volume but drops vwap and trade count. No common semantic contract says whether volume is base volume, quote volume, exchange-reported volume or another provider measure.

Semantics:

Crypto exchange volume may be materially different from FX tick volume or equity consolidated volume. A cross-market relative-volume feature must therefore carry the measurement basis and provider-neutral normalization version.

Recommendation:

Do not make volume a mandatory V1 analyzer. A factual current candle volume may remain available as input. Rolling average/relative volume is `FUTURE` until volume basis, retention, clean cadence and a comparable history contract are explicit. Do not treat `MarketActivityFact.quoteNotional` as candle volume; it is a separate derived activity contract.

==================================================
10. ORDER BOOK / LIQUIDITY
==================================================

Current data:

Market Data has `OrderBookSnapshot`, `OrderBookLevel`, delta models and an in-memory `OrderBookStateService`. The snapshot contains depth, bids, asks, best bid/ask, spread, total bid/ask volume, imbalance and `occurredAt`. `IntelligenceContext` has `ORDER_FLOW`, but no `ContextContributor` or `MarketDataClient` method maps order-book snapshots into it. `ActiveAnalysisStrategy` requests `ORDER_FLOW` optionally, so the requirement currently resolves to missing context.

Snapshot capabilities:

If a normalized snapshot were exposed, deterministic calculations could include best-level spread, depth totals, depth within an explicit price window, microprice and a defined imbalance. A single snapshot can describe current displayed liquidity only.

Historical capabilities:

No durable order-book snapshot history or replay query is present. `OrderBookStateService` is in-memory and stateful. It is suitable as a current reconstruction source, not as a historical baseline input.

Candidate analyses:

- Top-of-book spread: overlaps quote quality.
- Bid/ask depth totals: possible after context exposure.
- Depth imbalance: possible after window and denominator semantics are fixed.
- Weighted imbalance or microprice: future, requires explicit price-distance and level policy.
- Slippage estimate: blocked until hypothetical size comes from a domain/config source; do not invent account-specific order size.

Recommendation:

`BLOCKED BY DATA` for Story 0106 canonical baseline. First establish an approved order-flow context contract, including cutoff, capture timestamp, depth, stale-book policy, crossed/one-sided-book handling and source fingerprint. Do not treat the existing in-memory snapshot model as durable replay evidence.

==================================================
11. IMBALANCE / ABSORPTION
==================================================

Imbalance:

PARTIAL, but BLOCKED for the canonical V1 baseline. The Market Data order-book snapshot already computes a ratio equivalent to `bidVolume / (bidVolume + askVolume)` with neutral `0.5` for zero total. This is not currently available through `IntelligenceContext`, is tied to the configured depth, and the zero-total neutral value would need explicit semantic treatment rather than being mistaken for factual balance. A future contract should decide whether to expose signed imbalance, ratio imbalance or both, and how to handle crossed, one-sided, stale and partial books.

Absorption:

BLOCKED.

Rationale:

Absorption requires temporal evidence such as repeated aggressive trades, executed volume, price non-progression and book replenishment. A static snapshot or the current capped in-memory recent-trades buffer is insufficient. No approximation from a snapshot should be introduced.

==================================================
12. ECONOMIC CONTEXT FEATURES
==================================================

Candidates:

- Time until the next relevant event.
- Time since the last relevant event.
- Count of relevant events in a declared window.
- Maximum scheduled impact in a window.
- Event status and scheduled/actual timestamps.
- Actual minus consensus or actual minus previous only when values are normalized numeric quantities with compatible units.

Temporal correctness:

News Service `EconomicEvent` contains scheduled, actual, source-updated and fetched timestamps, currencies, market IDs, impact, previous/consensus/actual values, unit and normalization version. Market Intelligence currently maps this through `NewsContext` and string fields, not a dedicated typed economic context. The contributor excludes future event facts against the evaluation cutoff and preserves temporal metadata.

Recommendation:

Event proximity is a strong optional candidate after a small contract clarification that identifies event relevance, impact semantics and the event time used for each calculation. `actual - consensus` is only a candidate for specific normalized numeric event types. Generic surprise across arbitrary strings, percentages, revisions and qualitative values is unsafe and deferred. “The event is bullish/bearish for BTC” is interpretation and excluded.

==================================================
13. FINANCIAL NEWS FEATURES
==================================================

Candidates:

- Article count in an explicit time window.
- Time since latest relevant article.
- Distinct publisher count.
- Category/currency/entity frequency from normalized fields.

Provider limitations:

News context is acquired from the existing News Service endpoint. Marketaux contractual work is pending; provider coverage, duplicate/syndicated articles, acquisition delay, free-tier caps and provider outage can dominate article counts. A count may measure provider behavior rather than market activity. No raw provider response should enter the analyzer.

Rejected semantic analyses:

- Headline keyword sentiment.
- Bullish/bearish article classification.
- “Market-moving” or “important” inferred from text.
- News confidence or sentiment scores without a versioned objective model.

Recommendation:

News density is `FUTURE` or an optional low-priority candidate after the relevance window and duplicate semantics are explicit. It must never be a required baseline input. Existing deterministic analyses must remain usable when News is unavailable. Marketaux independence is mandatory.

==================================================
14. CROSS-DOMAIN FEATURES
==================================================

Candidates:

- Volatility measurement plus a scheduled event within a declared interval.
- Spread measurement plus an event proximity fact.
- Candle volume or activity change after a released event.

Complexity:

These combinations are mechanically calculable but quickly become composite rule systems with multiple horizons, thresholds and missing-data policies. They are not needed to establish a trustworthy first baseline and risk blurring atomic evidence with opportunity logic.

Recommendation:

Story 0106 should produce atomic typed analyses first. Composite findings belong in a later explicit rule/strategy story after the atomic outputs are measured. No composite opportunity score should be introduced.

==================================================
15. HISTORICAL DATA READINESS
==================================================

| Data | Current state | Readiness |
|---|---|---|
| Candles | Provider-backed OHLC history with bounded limits; normalized/synthetic/gap diagnostics exist in Market Data; no general durable MI replay store. | PARTIAL |
| Quotes | Current snapshots and immutable `PriceObservation` persistence; no historical quote-series query in MI. | PARTIAL |
| Trades | In-memory recent-trades buffer capped at 100; no durable replay stream. | MISSING for replay |
| Order book | In-memory reconstructed state and typed snapshots; no MI context contributor or durable history. | MISSING for replay |
| News | News Service persistence and normalized context response; provider coverage and pending Marketaux contract remain limitations. | PARTIAL |
| Economic events | News Service persistence and normalized event fields; MI exposes them only in combined News context and numeric semantics remain strings. | PARTIAL |

Overall:

PARTIAL. Snapshot and bounded OHLC deterministic analyses can run today. Historical replay for general features, order flow and outcome-linked ML is not ready.

==================================================
16. NO-LOOKAHEAD ASSESSMENT
==================================================

| Candidate | Assessment | Blocking issue |
|---|---|---|
| Quote/spread | PARTIAL/SAFE for current snapshot | Requires valid occurrence/capture cutoff and bid/ask consistency. Existing snapshot path applies the cutoff. |
| OHLC range/return | PARTIAL/SAFE | General OHLC path filters occurrence and fetch time; replay is limited by provider-backed history and fixed interval/limit. |
| Realized volatility | PARTIAL | Requires readiness diagnostics and explicit closed-candle/cadence policy in the analyzer input. |
| Market structure | PARTIAL | Engine enforces cutoff, but upstream Trend Context contributor uses its own clock boundary. |
| Trend Context assessment | PARTIAL | Same shared-cutoff defect; must be fixed before claiming canonical replayability. |
| Order-book snapshot | BLOCKED | No canonical context path, no persisted historical snapshots and no cutoff/freshness contract in MI. |
| Economic proximity | PARTIAL | News contributor filters future timestamps, but relevance/time semantics and dedicated typed event context are incomplete. |
| News density | PARTIAL | Temporal filtering exists; provider coverage/duplication and bounded query semantics are not a market fact. |

Blocking issues:

- Trend Context contributor must consume the request cutoff and preserve one analysis boundary.
- Any future analyzer must receive immutable context evidence, not call providers or clocks.
- Historical replay cannot be claimed where source data is not retained or reconstructable.

==================================================
17. CANDIDATE V1 ANALYZERS
==================================================

Rank 1:

Name: Quote Quality / Spread Measurements

Inputs: `MARKET_SNAPSHOT`, bid, ask, optional last price, occurrence/capture timestamps.

Output: Typed raw spread result containing absolute spread, relative spread, basis points, midpoint when valid, input status and evidence reference.

Algorithm: `BigDecimal` midpoint and spread formulas with explicit scale/rounding; no regime classification.

Why: Immediately available, objective, easy to test and useful for monitoring execution conditions without becoming a trade decision.

Replayable: PARTIAL to REPLAYABLE when the immutable `PriceObservation` or snapshot reference can be resolved at the historical cutoff.

ML value: High as a clean microstructure/input-quality feature.

Complexity: SMALL.

Rank 2:

Name: Bounded OHLC Temporal Measurements

Inputs: closed, non-synthetic, temporally valid OHLC context with explicit interval, lookback and completeness diagnostics.

Output: Typed range and return results; realized volatility only when the requested sample is complete and the algorithm contract is satisfied.

Algorithm: simple return and range first; optional log return/rolling volatility only under an explicit profile.

Why: Uses already available candles and provides interpretable temporal market measurements without strategy semantics.

Replayable: PARTIAL. The algorithm can replay fixed candles, but the current source/retention path is not a general historical dataset.

ML value: High, provided features preserve horizon, lookback and input quality.

Complexity: MEDIUM.

Rank 3:

Name: Market Structure / Trend Context Evidence

Inputs: `TREND_CONTEXT` history, explicit profile, cutoff, rule version, source fingerprints and valid structure artifacts.

Output: Existing typed `MarketStructureResult` and `TrendContextAssessment`, including swings, relations, direction, regime, phase, alignment, findings, exclusions and attention state.

Algorithm: Existing `CONFIRMED_SWING_V1`, EMA/ATR and Trend Context V1 rules; do not duplicate as generic indicators.

Why: Already implemented, versioned and tested, and materially more informative than an arbitrary trend label.

Replayable: PARTIAL until the contributor cutoff defect and durable historical source limitations are addressed.

ML value: High as structured evidence, especially with exclusions and evidence references.

Complexity: MEDIUM/LARGE if changed; low for baseline reuse but high for governance and validation.

Rank 4:

Name: Scheduled Economic Event Proximity

Inputs: typed relevant events, scheduled/actual/fetched timestamps, market/currency relevance, impact and cutoff.

Output: Time-to-event/time-since-event and event-count measurements with explicit unavailable state.

Algorithm: temporal distance and counts over configured windows; no market-direction interpretation.

Why: Objective contextual fact that can explain market conditions without claiming economic causality.

Replayable: PARTIAL with News Service event persistence and a typed MI event contract.

ML value: Medium/high.

Complexity: MEDIUM.

Status: Optional/reduced-baseline candidate, not mandatory until the contract gap is addressed.

Maximum recommended implementation: 3 analyzers for the first coherent baseline, with Rank 4 optional only if event relevance and typed semantics are approved. Do not exceed 3-4 baseline families.

==================================================
18. REJECTED / DEFERRED ANALYSES
==================================================

| Analysis | Status | Reason | Prerequisite |
|---|---|---|---|
| Generic MarketScore/BullishScore | REJECT | Arbitrary weights and opaque semantics; creates false authority. | None; do not add. |
| Spread regime NORMAL/WIDE/EXTREME | DEFER | Asset-specific thresholds are not justified. | Versioned threshold/profile policy. |
| RSI | DEFER | Additional magic period and redundancy with returns/structure. | Evidence that it adds measurable baseline value. |
| MACD | DEFER | More periods and signal-line semantics than needed for first baseline. | Explicit profile and evaluation evidence. |
| Bollinger Bands | DEFER | Multiple arbitrary parameters and no current requirement. | Explicit profile and evaluation evidence. |
| Generic trend bullish/bearish | REJECT | Subjective collapse of existing richer Trend Context semantics. | None; preserve typed assessment. |
| Support/resistance | DEFER | Many parameter choices and risk of future-information leakage around pivots. | Dedicated deterministic domain specification. |
| Market regime independent of Trend Context | DEFER | Current data may support some states, but thresholds/semantics are not approved. | Separate versioned regime profile. |
| Order-book imbalance | BLOCKED BY DATA | No canonical order-flow context or replay history. | Order-flow context contract and persistence/replay decision. |
| Absorption | BLOCKED BY DATA | Requires temporal trades and book replenishment. | Durable order flow and event sequencing. |
| Slippage estimate | BLOCKED | Requires hypothetical size and depth-window policy. | Domain/config source for size and order-book contract. |
| News sentiment | REJECT | Keyword/semantic classification is not deterministic evidence. | Future versioned statistical/LLM model, not 0106. |
| News importance/bullishness | REJECT | Provider/editorial semantics are not reproducible market facts. | Future governed interpretation model. |
| Event surprise across all events | DEFER | Values are strings, units/revisions/qualitative fields differ. | Typed event value semantics by event family. |
| Composite volatility + event rule | DEFER | Atomic baseline should be measured first. | Later rule/strategy story. |
| Opportunity/signal/setup | OUT OF SCOPE | Market analysis must not become a trader-facing candidate. | StrategyEvaluation/StrategyMatch boundary. |

==================================================
19. OUTPUT MODEL
==================================================

Recommended structure:

Use independent typed deterministic results collected by an orchestration bundle, conceptually:

```text
DeterministicAnalysisBundle
  contextFingerprint
  marketId
  evaluationCutoff
  analyses
    QuoteQualityAnalysis
    OhlcTemporalAnalysis
    TrendContextAssessment
    optional EconomicEventProximityAnalysis
  bundleMetadata
```

Each result should carry its own:

- analysis type;
- algorithm/rule version;
- configuration/profile reference;
- input section references;
- evidence window;
- status (`AVAILABLE`, `INSUFFICIENT_DATA`, `STALE_INPUT`, `INVALID_INPUT`, `UNAVAILABLE` or domain-equivalent);
- typed values;
- explanation and source references.

Typed vs generic:

Typed Java domain results are recommended. `Map<String, Double>` loses units, status, input lineage, numeric semantics and evolution safety. A single God-object with every possible analysis field is also undesirable.

Recommendation:

Option B, independent typed results grouped by a small bundle. A future ML projection may flatten them later, but the domain model must not be a flat ML vector.

Versioning:

Minimum result metadata: `analysisType`, `algorithmVersion`, `configurationVersion` or profile reference, `contextFingerprint`, `marketId`, `evaluationCutoff`, `computedAt` only as operational metadata, and result status. `computedAt` must not substitute for the evidence cutoff.

==================================================
20. ANALYZER ARCHITECTURE
==================================================

Interface:

Future implementation should use a small stateless contract such as `DeterministicAnalyzer<I, O>` or a repository-consistent equivalent, with explicit applicability and typed result status. Avoid making every analyzer depend on the complete context if it needs only one section.

Input granularity:

- Spread analyzer: a narrow immutable quote input derived from `MARKET_SNAPSHOT`.
- OHLC analyzer: a narrow immutable candle-series input plus interval/lookback/readiness evidence.
- Trend Context analyzer: its existing dedicated `TrendContextAssessmentInput` and typed history/profile contracts.
- Economic analyzer: a typed event collection, not generic News text.

Purity:

Pure calculations should receive all facts and policy values as inputs. No repository access, HTTP, clock, mutable global state or provider client inside calculation functions.

Statelessness:

Analyzers should be stateless and reusable by passive scans, active scans, replay and future feature generation. Caching/persistence belongs to orchestration/artifact boundaries, not calculation semantics.

Orchestration:

An eventual deterministic analysis orchestrator may select applicable analyzers, execute independently, collect typed results and preserve each failure/status. It must not create opportunities, choose a strategy, or make Risk decisions.

Dependencies:

Prefer narrow context component projections. Explicitly declare dependencies and do not rely on Spring bean order. Trend Context may have explicit market-structure dependency; spread must not depend on news; event proximity must not depend on price calculations.

Failure semantics:

One optional analyzer failing should not make unrelated analyzers unavailable. Required context failure should produce a degraded/unknown result with diagnostics. Never fabricate zero, false or neutral values.

==================================================
21. MISSING / STALE DATA
==================================================

Missing:

- `MISSING` when an optional section or source has no evidence.
- `UNAVAILABLE` when a source/contributor failed or cannot be reached.
- `UNSUPPORTED` when the capability/data type is not provided.

Stale:

`STALE_INPUT` when facts exist but exceed the analyzer/profile freshness policy. Staleness is not equivalent to missing and should preserve the evidence reference.

Invalid:

`INVALID_INPUT` for impossible or contradictory facts such as negative price, `bid > ask`, crossed order book where the contract forbids it, duplicate conflict, future fact or malformed temporal relationship.

Partial context:

Independent analyzers should proceed when their own required sections are valid. Missing News must not prevent spread or OHLC measurements. Missing order flow must not make quote analysis invalid.

Recommended semantics:

Eligibility should be decided once by an explicit shared context/input policy, while each analyzer owns mathematical validity. The result must preserve both status and diagnostic reason. No missing value should be encoded as zero, NaN without status, false or NEUTRAL unless that value is mathematically valid.

==================================================
22. CONFIGURATION
==================================================

Horizons:

Use an explicit `AnalysisHorizon` or equivalent request/profile value. Do not emit an unlabelled collection of 1m/5m/15m/1h/4h/1d values. Existing Trend Context roles and intervals are the closest current convention.

Lookbacks:

Make lookback interval and count explicit in a versioned analyzer configuration. Existing `TrendContextProfile` and role definitions demonstrate the required pattern, including profile and rule versions. General OHLC currently has hardcoded `FIFTEEN_MINUTES` and `ACTIVE_LIMIT = 200`, which should not become hidden baseline semantics.

Thresholds:

Thresholds belong in a typed domain profile only when they define a meaningful rule. Avoid a generic configurable rules platform and avoid threshold classifications until evidence supports them.

Versioning:

Configuration must contribute to result identity and fingerprint. A result with the same context but changed horizon/lookback/threshold must have a different configuration identity.

Recommended approach:

Reuse the existing versioned Trend Context profile pattern for rules requiring thresholds. Introduce no generic configuration framework in the investigation or automatically in Story 0106.

==================================================
23. NUMERIC PRECISION
==================================================

Price: `BigDecimal`, consistent with `MarketSnapshotContext`, `OhlcPoint` and Market Data persistence precision.

Ratios: `BigDecimal` for domain calculations and deterministic rounding; carry scale/rounding in algorithm version.

Returns: `BigDecimal` for calculation and serialization; log return only where a future statistical contract explicitly accepts it.

Volatility: `BigDecimal` for deterministic Java domain output; do not use `double` merely for future Python consumption.

Volume: `BigDecimal`, with explicit unit/basis metadata. Do not compare values across providers without normalization.

`double` may be used at a future feature projection boundary if that contract explicitly defines conversion and precision loss. It should not be the authoritative deterministic domain representation.

==================================================
24. PERSISTENCE
==================================================

On-demand:

Suitable for current quote and bounded calculations. Lowest storage cost, but insufficient alone for historical replay and feature datasets.

Persisted:

Useful for auditability and ML dataset generation, but requires a durable context/result contract, retention and storage-volume decision. No migration should be designed in this investigation.

Hybrid:

Recommended direction eventually: compute deterministic analyses on demand from immutable source/context references, persist selected immutable results and lineage for scans/replay, and retain source facts according to owning-service policies.

Recommendation:

Do not add persistence in Story 0106 unless the approved Story explicitly scopes a narrow result lineage requirement. Current baseline implementation may be on-demand with versioned result identity, but a separate historical feature/outcome foundation is required before ML claims.

==================================================
25. REPLAY / BACKTEST READINESS
==================================================

| V1 analyzer | Replay status | Reason |
|---|---|---|
| Quote Quality / Spread | PARTIAL | Pure calculation is replayable; historical quote retrieval and immutable snapshot selection are not a complete MI dataset contract. |
| OHLC range/return | PARTIAL | Fixed candles can replay exactly; provider-backed bounded retrieval and no durable general history limit coverage. |
| Existing Market Structure / Trend Context | PARTIAL | Engine has fingerprints, profiles, rule versions and cutoff checks; upstream contributor cutoff defect and source retention remain. |
| Economic event proximity | PARTIAL | News Service persistence helps; combined/string-valued MI contract and relevance semantics need clarification. |
| Order-book imbalance | NOT REPLAYABLE | No canonical context path or durable order-book history. |

Overall:

PARTIAL. A deterministic calculation is not automatically a replayable feature. Replay requires the exact historical facts, cutoff, configuration, algorithm version and source identity to be recoverable without a live provider refetch.

==================================================
26. BASELINE MEASUREMENT
==================================================

How to characterize baseline:

- Coverage: percentage of requested contexts for which each analyzer is `AVAILABLE`.
- Data quality: stale, incomplete, invalid, missing and future-fact counts.
- Stability: same context/configuration yields identical fingerprint and exact result.
- Distribution: values by market, interval, time and source quality.
- Cross-market behavior: compare only after units and volume semantics permit it.
- Temporal behavior: latency and cutoff-to-latest-evidence age.
- Relationship to future returns: an exploratory characterization only after labels exist, never a parameter-fitting loop in Story 0106.
- Replay agreement: live-context result versus fixed-fixture recomputation.
- Scanner reuse: identical analyzer output in passive, active and replay paths.

Metrics:

- analyses attempted/completed;
- `INSUFFICIENT_DATA`, `STALE_INPUT`, `INVALID_INPUT`, `UNAVAILABLE` counts;
- calculation latency;
- input sample/candle counts;
- context/result fingerprint mismatch count;
- source freshness and completeness distributions;
- result version/configuration distribution.

What NOT to claim:

No profitability, edge, win probability, good signal, high confidence or causal effect. Baseline outputs are measurements and deterministic evidence, not validated trading strategies.

==================================================
27. FUTURE ML PROJECTION
==================================================

Typed analyses:

Remain authoritative Java domain evidence with source references, statuses, units, horizons, lookbacks and versions.

Feature projection:

Use a future explicit `FeatureProjection`/`FeatureExtractor` boundary:

```text
Typed deterministic analyses
        -> versioned feature projection
        -> numeric FeatureVector
        -> Python ML boundary
```

Feature version:

The projection needs its own schema/version, independent of individual analyzer versions. It must identify context fingerprint, feature availability/missingness semantics, horizon, label relationship and normalization.

Python boundary:

Python should own statistical/ML training and inference. Java should own normalized facts, deterministic calculations and deterministic analyses. Do not move arithmetic to Python merely for future ML convenience.

No unused feature-store or Python transport infrastructure should be added by Story 0106.

==================================================
28. ML READINESS GATE
==================================================

| Prerequisite | Status | Evidence |
|---|---|---|
| Versioned deterministic features | PARTIAL | Existing capabilities are versioned, but no unified baseline result contract exists. |
| Historical source data | MISSING/PARTIAL | OHLC and News have bounded/persisted pieces; quotes/order flow are not a general replay dataset. |
| No-lookahead semantics | PARTIAL | 0105 paths mostly enforce cutoff; Trend Context contributor must be corrected. |
| Replayable contexts | PARTIAL | Fingerprints exist but no durable raw context snapshot and incomplete source retention. |
| Outcome labels | MISSING | No Story 0106 implementation should create them. |
| Sufficient sample size | UNKNOWN | No dataset characterization exists. |
| Cost/slippage-aware evaluation | MISSING | Risk/execution outcome linkage is not a baseline input. |
| Selection/survivorship controls | PARTIAL | ADR-048 defines the requirement; implementation coverage is not a completed ML dataset. |

Can ML follow 0106 directly:

NO.

If NO:

Required prerequisite Story: a historical feature and outcome foundation, after the Trend Context cutoff correction and baseline result contract. It must define replayable source snapshots/references, outcome horizon/labels, no-lookahead checks and evaluation population before an ML Story.

==================================================
29. LLM READINESS GATE
==================================================

| Prerequisite | Status |
|---|---|
| Structured Analysis Context | PARTIAL/READY for current sections; no order-flow/economic typed section. |
| Deterministic baseline | PARTIAL; existing capabilities are not one canonical bundle. |
| Source provenance | READY for current contributors, subject to Trend cutoff issue. |
| Bounded news/macro context | PARTIAL; combined News context exists and Marketaux remains pending. |
| Structured output schema | PARTIAL; AI capability is currently disabled and no production LLM result contract exists. |
| Traceability | PARTIAL/READY in existing artifacts and fingerprints. |
| Model/prompt versioning | MISSING for production LLM. |
| Timeout/error policy | PARTIAL; analysis execution has policies, but no LLM provider contract. |
| Cost controls | MISSING. |
| Prompt-injection policy | MISSING. |
| Non-authority and human boundary | READY by ADR-021/048 and current disabled AI path. |

Can LLM follow 0106 directly:

NO for production integration. The LLM can consume future structured deterministic evidence only after a separate governed LLM contract and security/readiness story.

Recommended prerequisite:

A structured Market Intelligence/LLM evidence contract and governance story, after deterministic baseline measurement. It must preserve provenance, missing data, model/prompt versions, prompt-injection handling and non-authority.

==================================================
30. RISK / OPPORTUNITY BOUNDARY
==================================================

Market Intelligence responsibilities:

- Normalize/use bounded market evidence supplied by owning services.
- Produce deterministic measurements and versioned analytical evidence.
- Preserve temporal validity, source lineage and degraded states.
- Publish observations or evidence without financial authority.

Risk responsibilities:

- Trading Core/Risk owns account-specific authorization, max drawdown, challenge constraints, position sizing and TradePlan permission.
- Market volatility or liquidity measurements do not authorize or reject account risk by implication.

Opportunity responsibilities:

- Strategy evaluation and StrategyMatch precede `TradingOpportunity` under ADR-034/048.
- Baseline analysis must not create `Opportunity`, `Signal`, `Setup` or `TradePlan`.

==================================================
31. PASSIVE / ACTIVE SCANNER COMPATIBILITY
==================================================

Passive:

Use lightweight quote/spread and available OHLC measurements, preserving stale/missing status and avoiding repeated provider calls inside analyzers.

Active:

Reuse the same analyzers with a broader explicit history/profile, including existing Trend Context and optional economic proximity.

Historical replay:

Invoke the same pure calculations against fixed context fixtures and historical source references. No scanner-specific formulas.

Shared analyzers:

Achievable if analyzers consume immutable typed inputs and configuration rather than active/passive mode internals. Orchestration selects depth; calculations do not.

==================================================
32. OBSERVABILITY
==================================================

Recommended metrics:

- analyzer attempts/completions;
- result status counts by analyzer type and algorithm version;
- calculation latency;
- input age/freshness outcome;
- insufficient-history and invalid-input counts;
- context/result fingerprint mismatches;
- bounded output size.

Logging:

Log analyzer type, version, status, reason code, market scope where permitted, context fingerprint and latency. Do not log entire Analysis Context, news titles/summaries unnecessarily, credentials or raw order books.

Sensitive/high-cardinality exclusions:

Avoid raw market IDs, article IDs, source payloads or arbitrary feature values as unbounded metric labels. Use bounded status/version dimensions and trace/correlation fields according to current observability standards.

==================================================
33. TEST STRATEGY
==================================================

Unit:

Each analyzer should support fixed immutable input and exact expected output, including version/configuration and provenance assertions.

Parameterized:

Cover valid, missing, stale, future, invalid and partial inputs for each analyzer.

Property-style:

- Valid `bid <= ask` implies non-negative spread.
- Identical valid prices produce zero return.
- Valid signed imbalance, if later adopted, remains within its declared bounds.
- Reordering equivalent input facts does not change result fingerprint.
- Changing cutoff, source fact, rule or configuration changes identity when effective evidence changes.

Do not introduce a property-testing library unless existing repository conventions justify it; parameterized JUnit tests are sufficient initially.

Fixtures:

Use deterministic provider-neutral records already used by Market Intelligence tests. Include explicit source/fetch timestamps, source IDs, profile versions, missing sections, stale sections, gaps, duplicates and future facts. No live provider is required for unit tests.

Runtime:

Validate against actual fixed/normalized Analysis Context construction where possible. Live News acquisition is not required; Marketaux must not be a test prerequisite. Add a focused regression fixture for one cutoff used consistently by all contributors, especially Trend Context.

==================================================
34. ADR REQUIREMENT
==================================================

Required:

YES, if Story 0106 formalizes the durable analyzer/result architecture and typed facts/features/analysis separation. Existing ADR-020 and ADR-048 establish the boundary but do not define the concrete deterministic baseline result contract, analyzer granularity or feature projection boundary.

Suggested title:

`ADR-049 - Deterministic Market Intelligence Evidence and Analyzer Boundary`

Decisions:

- Typed independent deterministic results grouped by an analysis bundle.
- Facts, deterministic features and interpretations remain separate.
- One explicit cutoff and versioned configuration identify each result.
- Pure/stateless Java calculations consume narrow typed inputs.
- Feature projection to Python is a later boundary, not the domain model.
- Missing/stale/invalid evidence remains explicit.
- No generic intelligence score or financial authority.

Do not write the ADR as part of this investigation unless the repository workflow explicitly requests the approved architecture artifact.

==================================================
35. IMPLEMENTATION SIZE
==================================================

MEDIUM, if limited to the reduced three-family baseline and cutoff correction. LARGE if Story 0106 includes order flow, durable history, economic typed contracts, persistence, ML projection or scanner integration.

Recommended Story decomposition:

- Keep one coherent reduced Story 0106 for quote quality, bounded OHLC measurements and formal reuse/measurement of existing Trend Context, including the shared-cutoff prerequisite.
- Create a later historical feature/outcome Story before ML.
- Create a separate order-flow/history Story before imbalance or absorption.
- Create a separate structured LLM governance Story before production LLM integration.

Do not split the reduced baseline unless implementation scope expands beyond these boundaries.

==================================================
36. DECISION MATRIX
==================================================

Scores are 0-5. Weighted score is calculated against the requested weights. A score does not override a no-lookahead or data-readiness blocker.

| Candidate | Determinism 15 | Data 15 | Trading 15 | No-lookahead 15 | Explain 10 | Replay 10 | ML 10 | Provider 5 | Simplicity 5 | Weighted / 5 | Decision |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Quote quality / spread | 5 | 5 | 4 | 4 | 5 | 3 | 4 | 5 | 5 | 4.35 | V1 |
| Bounded OHLC range/return | 5 | 4 | 4 | 4 | 5 | 3 | 5 | 4 | 4 | 4.15 | V1 |
| Existing Trend Context / structure | 5 | 4 | 5 | 3 | 5 | 3 | 5 | 4 | 3 | 4.15 | V1 after cutoff fix |
| Economic event proximity | 5 | 3 | 3 | 4 | 5 | 3 | 4 | 4 | 3 | 3.75 | Optional/defer |
| Order-book imbalance | 5 | 1 | 4 | 2 | 4 | 1 | 4 | 4 | 3 | 2.85 | Blocked |
| Absorption | 3 | 1 | 4 | 1 | 2 | 1 | 3 | 3 | 1 | 2.15 | Blocked |

Overall reduced baseline recommendation: `4.22 / 5` across the three V1 families. This reflects high deterministic value but does not claim the baseline is historically replay-ready or profitable.

==================================================
37. RECOMMENDED ROADMAP
==================================================

Story 0106:

Reduced deterministic baseline: quote quality/spread, bounded OHLC temporal measurements, and existing Trend Context/market-structure evidence under a corrected shared cutoff.

Next prerequisite:

Correct the Trend Context contributor cutoff behavior and define the typed deterministic result bundle/configuration contract. Then establish historical source/context retention sufficient for replay.

First ML Story:

Historical Feature and Outcome Foundation, only after replayable deterministic context, outcome horizons/labels, no-lookahead validation and sufficient dataset characterization exist. This should be the next story only if those gates are met.

First LLM Story:

Structured Market Intelligence Assessment and LLM Governance, after deterministic evidence is versioned and inspectable. It must consume evidence rather than recalculate it and must preserve non-authority.

Recommended sequence:

```text
0106 reduced deterministic baseline
    -> historical feature/context/outcome foundation
    -> ML baseline experiment
    -> structured LLM evidence/governance
```

Do not advance to ML merely because Story numbering permits it.

==================================================
38. FINAL DECISION
==================================================

DECISION B
PROCEED WITH STORY 0106 WITH A REDUCED BASELINE

Exact Story 0106 analyzers if approved:

1. Quote Quality / Spread Measurements.
2. Bounded OHLC Temporal Measurements: range and simple returns first; realized volatility only where explicit readiness is satisfied.
3. Existing Market Structure / Trend Context Evidence, after the shared evaluation-cutoff defect is corrected.

Scheduled Economic Event Proximity is an optional follow-up, not mandatory for the reduced baseline. Order-book imbalance, absorption, volume regimes, news semantics, composite scores and opportunity logic are excluded.

==================================================
39. REPOSITORY MUTATIONS
==================================================

Application source modified: NO

Configuration modified: NO

Database modified: NO

Git history modified: NO

Unrelated files preserved: YES

One documentation-only investigation report was added at:

`docs/architecture/stories/0106-deterministic-market-intelligence-baseline/investigation-report.md`

==================================================
FINAL VERDICT
==================================================

Trading OS can support a trustworthy first deterministic baseline today, but only in reduced form. The baseline should formalize clean quote measurements, bounded OHLC measurements and the existing versioned Trend Context evidence. It must first close the Trend Context cutoff inconsistency before claiming complete no-lookahead semantics. Historical replay, order-flow analysis, broad volume/news semantics and ML readiness require separate data and governance work. No application implementation of Story 0106 was performed.

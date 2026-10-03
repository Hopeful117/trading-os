# Market Eligibility Filtering Investigation

**Date:** 2026-10-03
**Mode:** Focused read-only architecture and domain investigation
**Implementation:** None
**Story 0070:** Not created
**Git:** No commit or push performed

This report evaluates a possible successor to Story 0069, currently described as
Defensive Market Filtering / Market Eligibility Filtering. It does not create a
Story, ADR, production code, test, or configuration change.

## 1. Executive Summary

The elimination-first hypothesis is preferable to a weighted
`AnalysisPriorityScore`/Top-N model for the first V1 slice, but only if its
meaning is kept narrow:

```text
ELIGIBLE = no applicable deterministic exclusion rule rejected the market
```

`ELIGIBLE` must not mean interesting, profitable, bullish, strategy-compatible,
recommended, Risk-approved, or selected for execution.

The repository already supports a small, defensible hard-gate slice:

- current Market Data `MarketState.tradable` / market existence;
- Market Facts `MarketDataReadiness` statuses and evidence counters;
- provider capability represented by `UNSUPPORTED` rather than fabricated zero;
- freshness and completed-history checks from the existing Market Facts contract.

The first implementation should not add a default `LOW_ACTIVITY`, spread,
liquidity, generic volatility, historical-drawdown, or Market Structure rule.
`MarketActivityFact` now makes a future absolute activity policy technically
possible, but the threshold, comparison domain, user semantics, and empirical
false-exclusion behavior are not established.

The recommended V1 is therefore:

```text
explicit user scope
    -> existing account/market hard eligibility
    -> required Market Facts readiness gates
    -> EXCLUDED / NOT_EVALUABLE / ELIGIBLE
    -> bounded Active Scan analysis
```

This is a defensive gate, not a ranking replacement for all future relevance
work. A later relevance or priority policy may still order eligible markets, but
it should not be smuggled into the first eligibility Story.

## 2. Current Repository Architecture

### 2.1 Authoritative context

The investigation checked:

- `AGENTS.md`;
- Story 0068 and its implementation report;
- Story 0069, its investigation, runtime validation, and remediation report;
- `two-level-market-filtering-investigation.md`;
- ADR-006, ADR-010, ADR-014, ADR-033, and ADR-048;
- current Market Data and Market Intelligence implementations;
- current Active Scan scope resolution and persistence.

DevLog was queried for current project context. It returned general microservice,
deterministic-testing, and architecture context, but no Story-specific decision
that changes the repository evidence. Its freshness state was `UNKNOWN`; the
repository remains authoritative for current code.

The Story 0069 header still contains historical `RUNTIME_FIX_REQUIRED` wording,
while its latest runtime-validation section records `READY_FOR_HUMAN_CLOSURE`.
The user supplied that 0069 is now closed. The old wording was not changed by
this investigation.

### 2.2 Current implemented flow

The requested high-level flow is directionally compatible with the repository,
but several stages are future or not currently connected:

```text
Market Data catalogue
    -> Story 0068 explicit/user-selected catalogue scope
    -> Trading Core account ownership and current Active Scan market eligibility
    -> Market Intelligence ActiveScanScopeResolutionService
    -> one AnalysisExecution per currently eligible market
    -> Market Intelligence capabilities and context contributors
    -> Trend Context / other deterministic analysis
    -> observations and opportunity orchestration
    -> TradePlan
    -> Trading Core Risk / human authorization / execution
```

The proposed future insertion is:

```text
explicit user scope
    -> existing account/market eligibility
    -> Market Facts hard eligibility
    -> eligibility assessment
    -> Active Scan effective scope
    -> AnalysisExecution
```

Current code evidence:

- `market-intelligence/.../ActiveScanScopeResolutionService` loads the complete
  catalogue, derives candidate IDs, and currently rejects only missing or
  non-tradable markets.
- `MarketEligibilityDecision` already has a boolean eligibility value and a list
  of `MarketEligibilityReason` values.
- `ActiveScanApplicationService` persists the resolved requested, candidate, and
  effective scopes and dispatches one `AnalysisExecution` per eligible market.
- `MarketDataClient` currently exposes catalogue, price snapshot, and ordinary
  OHLC calls. It does not yet expose the internal Market Facts contract.
- `MarketFactsService` exists in `market-data`, but no current Active Scan path
  consumes it.
- `TrendContextEngine` owns profile-specific ATR and trend evidence. It is not a
  market eligibility engine.

### 2.3 Existing architectural boundaries

- Market Data owns the catalogue, market metadata, constraints, state, normalized
  provider data, and public market facts.
- Market Intelligence owns deterministic interpretation, analysis orchestration,
  observations, opportunities, and trade-plan preparation.
- Active Scan owns multi-market orchestration and scope snapshots.
- Trading Core and Risk own account-specific financial authority.
- AI may interpret or explain structured evidence but cannot override hard
  deterministic exclusions, Risk, or execution authority.

These boundaries are supported by ADR-006, ADR-010, ADR-014, ADR-033, and
ADR-048. No existing ADR mandates an `AnalysisPriorityScore` or Top-N model.

## 3. Elimination-First vs Prioritization

### 3.1 Approach A: AnalysisPriorityScore / Top-N

```text
user scope -> facts -> weighted score -> ranking -> Top N
```

Advantages:

- controls work when the eligible universe is still large;
- produces an order for scanner scheduling;
- can later combine relevance, activity, fresh observations, or user intent;
- may improve resource utilization after the evidence and comparison domain are
  mature.

Risks:

- requires arbitrary weights, thresholds, normalization, and tie semantics;
- creates false precision across markets, quote currencies, and providers;
- turns incomplete facts into a potentially authoritative order unless uncertainty
  is explicit;
- tends to make rank 1 look like the best trade;
- can hide why a market was not analyzed;
- can accidentally override explicit user scope or manual selection;
- requires broad fact acquisition before ranking is meaningful;
- conflicts with ADR-033's warning that current opportunity scores are not a
  calibrated global cross-market ranking.

### 3.2 Approach B: elimination-first

```text
user scope -> explicit exclusion rules -> EXCLUDED / ELIGIBLE
```

Advantages:

- a rule can be explained independently;
- deterministic gates do not require cross-market weights;
- missing, stale, unsupported, and insufficient evidence can remain distinct;
- tests can target each invariant and exclusion reason;
- provider neutrality is easier because rules consume typed facts and statuses;
- it preserves user control better than hidden Top-N selection;
- it is compatible with later deterministic and AI interpretation;
- it matches ADR-033's distinction between eligibility and relevance;
- it avoids presenting eligibility as a trade-quality score.

Disadvantages:

- does not solve the volume problem when many markets remain eligible;
- a bad threshold can exclude a valid market more severely than a low rank;
- defensive policies can become hidden strategy if their rationale is not
  documented;
- multiple rules still need explicit `NOT_EVALUABLE` semantics;
- a full eligible universe can still overload Active Scan;
- it does not determine which eligible market should be analyzed first;
- it may encourage adding every attractive metric as an exclusion rule.

### 3.3 Verdict for V1

Elimination-first is safer and simpler for V1 **for technical eligibility and a
small number of explicitly approved defensive policies**. It is not a complete
replacement for future relevance or scheduling. V1 should defer weighted ranking
and Top-N quality claims, while introducing a separate operational budget if the
eligible set exceeds what Active Scan can process.

## 4. Hard Gates vs Defensive Policies

The distinction is architecturally useful and should be explicit.

### A. Hard technical/analytical eligibility gates

These answer:

> Can Trading OS reliably perform the intended deeper analysis for this market?

Examples:

- `NOT_TRADABLE`;
- `DATA_UNAVAILABLE`;
- `STALE_DATA`;
- `INSUFFICIENT_HISTORY`;
- `UNSUPPORTED_DATA`;
- excessive missing or synthetic evidence when the consumer requires complete
  provider evidence.

These are system-controlled. A user must not weaken a required freshness or
data-integrity gate merely to make a scan proceed.

### B. Defensive market policies

These answer:

> Given that the market is analyzable, should deeper-analysis resources be spent
> on it under an explicit policy?

Examples:

- `LOW_ACTIVITY`;
- `LOW_24H_ACTIVITY`;
- future relative spread;
- future explicitly defined liquidity;
- future empirical abnormal-condition policies.

These may be user-configurable or system-configured within safe bounds, but they
must not be confused with technical safety. A disabled defensive policy must not
disable a hard technical gate.

### Current semantic boundary

`MarketState.tradable` is already an execution-availability fact, not a strategy
decision. `MarketDataReadiness` is a consumer-specific evidence fact. A future
eligibility policy may interpret both, but Market Data must not own the policy
threshold or opportunity meaning.

## 5. Current Fact Inventory

The classifications below distinguish facts modeled in Trading OS from data that
a provider may expose but the repository does not currently model truthfully.

| Fact | Owner / class | Source and semantics | Unit / window / freshness | Provider and quote scope | Persistence / cost | Catalogue-wide / exclusion use |
|---|---|---|---|---|---|---|
| Market identity | Market Data `Market` / `MarketResponse` | Stable Trading OS ID, provider, symbol, base, quote | Identity, not time-series | Provider and quote explicit | JPA catalogue; one catalogue read | Yes; identity prerequisite, not an exclusion by itself |
| Market state | Market Data `MarketState` | `tradingStatus`, `tradable`, `closureReason`, `lastUpdated` | Current state; no universal TTL | Provider-normalized | JPA embedded state; catalogue synchronization cost | Yes; `tradable=false` is a hard exclusion |
| Constraints | Market Data `MarketConstraints` | Minimum order size/cost, tick and precision fields | Static/reference metadata | Provider-specific values normalized into domain | JPA catalogue | Yes; not a generic analytical eligibility rule without account context |
| Activity | `MarketActivityFact` | Sum of completed observed `volume * vwap`; synthetic excluded | Configured OHLC interval and rolling duration; latest evidence and max age are explicit | Provider, base, quote, interval, window are explicit | In-memory LRU cache only, max 256 successful responses; one provider history call on cold path | Per market; value can support future policy, not default V1 |
| Readiness | `MarketDataReadiness` | Completed count, expected count, gaps, synthetic, cadence, duplicates, conflicts, latest close/fetch | Request interval, lookback, boundary, max age | Market ID and normalized provider evidence; quote is not carried in this record | Same in-memory Market Facts cache; one provider history call on cold path | Per market; suitable for hard gates |
| OHLC history snapshot | `MarketHistorySnapshot` / `OhlcEvent` | Observed history kept separate from normalized continuity history | Candle interval, open/close, `closed`, `synthetic`, `occurredAt`, `fetchedAt` | Provider and symbol on each event | In-memory request result; bounded provider request, max 720 candles | Not catalogue-wide; source for facts |
| Ticker volume | `TickerEvent` / Kraken ticker mapper | Provider volume field; current REST mapper uses Kraken `v[0]` | REST is today-since-midnight under provider semantics; WS is rolling 24h; domain loses window/unit/source timestamp | Provider-local; quote/base unit not modeled | Latest in-memory event; price observations persist bid/ask/last, not volume | No; unsuitable as a universal exclusion metric |
| Bid/ask and last | `TickerEvent`, price snapshots | Current quote facts with freshness in snapshot path | Fast-changing; snapshot default stale-after is 30 seconds | Provider/market-local | Runtime stream and some price observation persistence; per-market acquisition | Not cheaply catalogue-wide; possible future spread fact |
| Order book | `OrderBookSnapshot` | Best bid/ask, absolute spread, depth quantities, imbalance | Point-in-time, configured depth, `occurredAt` | Provider/market/depth-local | In-memory and subscription-dependent | No; absolute spread/depth is not generic liquidity |
| Recent trades | `TradeEvent`, `RecentTradesSnapshot` | Trade quantity, price, side, derived notional, trade ID | At most 100 retained trades, not a time window | Provider/market-local | In-memory subscription buffer | No; not a canonical activity window |
| Price observation | `PriceObservation` | Persisted bid/ask/last with effective/capture times | Snapshot freshness and configured age | Market/provider implied by source identity | Durable observation path; on-demand/coalesced ticker | Not sufficient alone for eligibility |
| Trend Context ATR | `TrendContextEngine` | Profile-specific ATR, baseline, ratio, abnormal-volatility finding | Role/timeframe/profile-specific | Analysis input, not generic catalogue fact | Analysis artifacts/observations, not Market Data fact | No; wrong layer for generic V1 filtering |
| Market Structure | None | No current `SwingPoint`, HH/HL/LH/LL, BOS, CHOCH, or trend-line contract found | Not available | Not available | Not available | No |
| ATH / lifetime high | None | Provider may expose recent highs, but no lifetime-history model exists | No complete window or coverage | Quote-specific history would be required | Not available; would require bounded or maintained history | No |

### 5.1 Fact status semantics

`MarketFactStatus` currently defines `AVAILABLE`, `INSUFFICIENT_DATA`, `STALE`,
`UNAVAILABLE`, and `UNSUPPORTED`. This is sufficient to avoid translating a
provider failure into zero activity, but an eligibility layer still needs to
preserve whether the status was a hard-gate failure or an optional policy's
missing input.

### 5.2 Facts available from provider but not modeled

The Kraken provider exposes ticker today/24-hour volume, VWAP, trade count,
high/low, and WebSocket timestamp semantics. The current repository does not
preserve enough of those distinctions in `TickerEvent` to use them as a neutral
24-hour fact. Kraken REST OHLC exposes at most 720 recent entries and always
includes the current uncommitted entry; it does not provide lifetime history.

## 6. Rule Feasibility Matrix

| Criterion | Classification | Existing source | Missing data / decision | Acquisition cost | Semantic risk | Timing |
|---|---|---|---|---|---|---|
| `NOT_TRADABLE` | `READY_WITH_EXISTING_FACTS` | `MarketState.tradable`, `tradingStatus`, `closureReason` | None for current gate | Catalogue read | Do not confuse with account permission | V1 hard gate |
| `DATA_UNAVAILABLE` | `READY_WITH_EXISTING_FACTS` | `MarketDataReadiness.UNAVAILABLE`, activity status | Eligibility contract/client mapping | One bounded facts request per cold market | Must not become zero or low activity | V1 hard gate |
| `STALE_DATA` | `READY_WITH_EXISTING_FACTS` | Readiness/activity `STALE`, explicit max age | Consumer freshness policy | Same facts request | TTL must be fact-specific, not universal | V1 hard gate |
| `INSUFFICIENT_HISTORY` | `READY_WITH_EXISTING_FACTS` | Readiness counts/status | Required interval/lookback policy | Same facts request; bounded <=720 | Normalized synthetic candles must not hide raw gaps | V1 hard gate |
| `UNSUPPORTED_DATA` | `READY_WITH_EXISTING_FACTS` | `MarketFactStatus.UNSUPPORTED` | Provider capability mapping | Same facts request | Provider-specific details must stay behind contract | V1 hard gate |
| Low activity | `REQUIRES_EMPIRICAL_VALIDATION` | `MarketActivityFact.quoteNotional` | Threshold, domain, false-exclusion evidence | One history request per market unless cached | Activity is not liquidity or quality | LATER / optional policy |
| Low 24h activity | `REQUIRES_SMALL_FACT_EXTENSION` plus empirical validation | Existing configurable activity fact | Approved 24h interval/window and threshold | One request/market, usually 24 hourly candles | Rolling vs calendar/ticker semantics | LATER |
| Extreme distance from historical high | `REQUIRES_NEW_MARKET_FACT` | No lifetime high fact | Coverage-aware historical high fact | Potentially maintained history; not on-demand 720-candle proof | ATH can be misstated as provider-window high | RESEARCH |
| Excessive spread | `REQUIRES_NEW_MARKET_FACT` | `OrderBookSnapshot.spread`, ticker bid/ask | Relative formula, freshness, depth/source policy | Subscription or one quote request per market | Absolute spread not comparable | LATER |
| Low liquidity | `NOT_JUSTIFIED` | Depth, volume, trades exist separately | Executable-size/slippage/depth contract | Likely stream/read model | Generic score would invent semantics | RESEARCH |
| Abnormal volatility | `WRONG_LAYER` for current V1 | Trend Context ATR/ratio | Generic volatility semantics if ever needed | Additional OHLC fact | Duplicates profile-specific Trend Context | LATER / separate fact |
| Abnormal market state | `NOT_JUSTIFIED` | `MarketState` only says current tradability/status | Definition of abnormal beyond state | Catalogue | Could become strategy policy | NOT_JUSTIFIED |
| Insufficient recent trading activity | Same as low activity | `MarketActivityFact` | Explicit window, threshold, evidence policy | Per-market OHLC request | "Recent" is undefined without window | LATER |
| Excessive missing data | `READY_WITH_EXISTING_FACTS` | readiness missing/cadence/synthetic counters | Which counters are hard vs optional | Same facts request | Must distinguish raw missing from normalized continuity | V1 hard gate when required |
| Excessive synthetic normalization | `READY_WITH_EXISTING_FACTS` | readiness synthetic count and status | Consumer tolerance policy | Same facts request | Synthetic continuity is not provider completeness | V1 hard gate when required |
| Provider-specific unsupported condition | `READY_WITH_EXISTING_FACTS` for generic status | provider registry and `UNSUPPORTED` | Capability matrix if conditions proliferate | Same facts request | Do not leak Kraken-specific rule names | V1 hard gate |

## 7. LOW_ACTIVITY Investigation

`MarketActivityFact` is the strongest current candidate for a non-technical
defensive policy, but the fact and the policy must remain separate.

Current semantics:

- `MarketFactsService` aggregates completed, non-synthetic OHLC events;
- the numerator is `volume * vwap` in the quote domain;
- zero-volume candles are valid zero-contribution evidence;
- unusable volume/VWAP makes the fact incomplete;
- activity carries interval, duration, observation boundary, latest evidence,
  max age, expected/observed/eligible counts, duplicate/conflict counts, status,
  and calculation version;
- the measure is only comparable within compatible provider, quote, interval,
  window, and completeness domains;
- it does not mean liquidity, tradability, volatility, trend, or quality.

### 7.1 Can current MarketActivity represent 24 hours?

Yes, technically, as a configurable closed-OHLC window, subject to the existing
720-candle bound. It does not currently represent a provider-native ticker
24-hour value. This distinction must not be hidden.

Feasible examples:

| Interval | Candles for 24 hours | Current request outcome |
|---|---:|---|
| 1 minute | 1,440 | Rejected by the 720-candle bound |
| 5 minutes | 288 | Feasible; request needs a small fetch cushion |
| 15 minutes | 96 | Feasible; request needs a small fetch cushion |
| 1 hour | 24 | Feasible; the clearest initial 24h approximation |
| 4 hours | 6 | Feasible but coarse |
| 1 day | 1 | A calendar-day candle, not an unambiguous rolling 24h measure |

The request validation allows the configured activity duration when it is no
larger than `interval.duration * 720`. `historyLimit()` requests the larger of
the required activity/readiness candles, plus one, capped at 720. Therefore a
normal 24h/1h request asks for approximately 25 OHLC entries per market, while
24h/5m asks for approximately 289 and 24h/15m approximately 97. Exact expected
completed counts vary with the unaligned observation boundary and the provider's
current open candle.

### 7.2 Does it cost one provider request per market?

Yes on a cold cache path. `MarketFactsService.find()` calls one
`MarketHistoryService.findOhlcHistorySnapshot()` for one market, interval, and
history limit. `MarketHistoryService` resolves one provider and delegates one
market-specific request. There is no batch Market Facts endpoint, bulk OHLC
provider port, or maintained catalogue-wide aggregate.

The facts service is `synchronized`, so calls through one service instance are
serialized. The LRU cache has at most 256 entries, stores only responses where
both activity and readiness are `AVAILABLE`, and is process-local and lost on
restart. Equivalent warm requests may avoid the provider call, but they still
need an internal facts call unless a higher-level batch/read model exists.

### 7.3 Is it practical for Story 0068 scope?

For a small explicit scope, yes. For 500 markets or the approximately 1,436
market catalogue, not as an incidental synchronous filter. A 24h OHLC pass is a
provider workload, not a cheap catalogue predicate. Story 0068's full catalogue
read is acceptable for client-side identity filtering, but it does not make
catalogue-wide time-series acquisition cheap.

No provider rate limit is asserted here because the repository does not model or
document one. Operational limits still require explicit budgeting and measured
validation before broad use.

## 8. Activity Window / 24h Options

| Option | Semantic clarity | Cost | Neutrality | Freshness/cacheability | Assessment |
|---|---|---|---|---|---|
| Current configurable OHLC MarketActivity | High when interval/window are explicit | One bounded call per cold market | Strongest current option | Closed-candle based; existing cache | Smallest supported fact path |
| Explicit 24h OHLC quote-notional | Clear if 1h/5m and completed-candle policy are fixed | 24/288 candles per market plus cushion | Provider-neutral contract, provider adapter remains hidden | Cacheable under current key | Feasible later, not a default policy yet |
| Provider-native 24h ticker | Cheap only if bulk/stream coverage exists | Current adapter path is not bulk and semantics differ | Weak current neutrality | Rolling provider window; source timestamp currently incomplete | Do not select now |
| Shorter recent OHLC window | Lower cost and more responsive | Fewer candles per market | Same clear semantics | Existing cacheable fact | Useful for research, not proof of 24h activity |
| Maintained aggregate/read model | Lowest repeated request cost at scale | New maintenance, storage, invalidation | Can preserve a neutral contract | Best for catalogue-scale operation | Required before broad passive/campaign use |

The smallest viable future activity path is one explicitly configured completed
OHLC window, probably hourly candles for a 24-hour approximation, with a declared
provider/quote comparison domain. It is not sufficient to label it simply
`volume24h`.

## 9. Absolute vs Relative Activity

### Absolute threshold

```text
activity < configured quote-notional threshold -> exclude
```

This is the only form that remains an eligibility policy rather than a ranking
operation. The configuration would need at least:

- provider;
- quote asset/currency;
- measurement interval and activity window;
- minimum quote-notional;
- freshness and completeness requirements;
- policy/rule version.

It must not compare USD and EUR blindly, or combine incompatible providers and
measurement kinds. No threshold value is justified by the repository.

### Relative threshold

```text
bottom X percent inside a comparison universe -> exclude
```

This reintroduces distribution calculation, comparison-group definition,
catalogue-wide acquisition, and a ranking-like interpretation. It is not wrong
forever, but it is not a simple V1 exclusion and would require explicit scope and
operational policy.

### V1 decision

Do not include activity exclusion in the default V1. If product evidence requires
it, use an absolute, provider/quote/window-scoped policy after empirical
validation. Defer relative thresholds and any percentile-based Top-N behavior.

## 10. Historical High / Drawdown Investigation

The repository cannot truthfully implement "more than 90% below ATH" today.

It currently has:

- recent OHLC acquisition capped at 720 entries;
- no lifetime OHLC repository;
- no provider listing-history coverage contract;
- no canonical all-time-high field;
- no distinction between provider-history and available-history highs;
- no symbol migration or redenomination lineage model;
- no neutral historical-price-position fact.

Kraken recent high fields or a bounded OHLC maximum would not prove an all-time
high. It would at most prove:

> the highest observed price in this provider response/evidence window.

Newly listed markets, pre-provider history, quote-currency changes, token
migrations, redenominations, and future non-crypto corporate/action-like events
all invalidate an unqualified ATH claim.

A neutral future fact could contain, subject to design approval:

- observed high and timestamp;
- current/reference price and timestamp;
- drawdown from observed high;
- history start and end;
- coverage type (`AVAILABLE_HISTORY`, `PROVIDER_HISTORY`, or equivalent);
- quote/provider/market identity;
- status and completeness;
- calculation and source versions.

The fact must say what was observed, not claim global historical truth. The policy
"exclude when drawdown exceeds 90%" is separate and requires empirical validation
before it becomes a default. It could systematically reject legitimate newly
listed or regime-shifted markets and encode an untested product belief.

Classification: new fact, research, and empirical policy validation. Not V1.

## 11. Spread Investigation

Spread-related data exists:

- ticker bid/ask;
- order-book best bid/ask;
- `OrderBookSnapshot.spread` as absolute ask minus bid;
- persisted price observations for some bid/ask snapshots.

Current limitations:

- absolute spread is not comparable across instruments with different prices;
- relative spread formula is not specified;
- order-book data requires a dynamic subscription and is in memory;
- freshness/source/depth policy is not a catalogue-wide contract;
- `MarketDataClient` has no reusable spread fact endpoint;
- subscribing to every market would contradict dynamic-subscription architecture.

Classification: `LATER`, not justified for V1. A future spread fact needs a
relative formula, quote snapshot source, freshness boundary, null/zero policy,
and bounded acquisition/read-model behavior.

## 12. Liquidity Investigation

The repository exposes volume, trade count, depth quantities, imbalance, spread,
and per-trade notional, but none is a generic liquidity fact:

```text
volume != liquidity
trade count != liquidity
imbalance != liquidity
spread != liquidity
```

A defensible liquidity fact would need an explicit executable-size or impact
question, depth and side semantics, quote/notional denomination, snapshot
freshness, book completeness, and likely a slippage or market-impact model.

Classification: `RESEARCH`; do not create `LiquidityScore` in 0070.

## 13. Volatility Boundary

`TrendContextEngine` already calculates profile-specific ATR, ATR baseline, ATR
ratio, abnormal-volatility findings, EMA relationships, and extension measures.
These values belong to Trend Context role assessments and their versioned rules.

Adding a generic Market Volatility exclusion now would either:

- duplicate Trend Context semantics;
- move strategy-adjacent evidence into the filtering layer;
- create conflicting windows, formulas, and thresholds;
- make "volatile" look like technically ineligible.

Classification: `WRONG_LAYER` for V1. A future reusable realized-volatility fact
would require its own window, formula, unit, freshness, and owner. It must not be
called ATR by implication.

## 14. Market Structure Boundary

Swing highs/lows, HH/HL/LH/LL, BOS, CHOCH, and trend-line candidates are deeper
analytical evidence, not cheap defensive facts. They require lookback, pivot,
timeframe, confirmation, and rule-version semantics. They belong to a separate
deterministic Market Intelligence capability and should feed later strategy or
relevance evaluation.

Market Structure should not participate in 0070 V1 and should not be approximated
with MarketState, activity, or Trend Context fields.

## 15. Eligibility Result Model

The repository already has a smaller precedent:

```text
MarketEligibilityDecision
    marketId
    symbol
    provider
    eligible
    reasons[]
```

`ActiveScanScopeSnapshot` persists the decisions used by an Active Scan. This is
strong evidence that a separate durable eligibility database is not required for
the first Story.

A future assessment should preserve at least:

```text
assessment
    marketId
    status: ELIGIBLE | EXCLUDED | NOT_EVALUABLE
    evaluatedAt
    assessmentCutoff
    policyName
    policyVersion
    ruleResults[]
    exclusionReasons[]
    fact references/statuses
```

Per-rule statuses should be semantically explicit:

```text
PASS | EXCLUDE | NOT_EVALUABLE | NOT_APPLICABLE
```

These are conceptual names, not an authorization to add code in this
investigation. Existing `MarketEligibilityDecision` and reason conventions
should be reused where possible rather than creating a parallel authority model.

## 16. NOT_EVALUABLE Semantics

Missing evidence must never become zero, PASS, or EXCLUDE implicitly.

Recommended deterministic aggregation:

1. If any applicable rule returns `EXCLUDE`, aggregate status is `EXCLUDED`.
2. If no rule excludes but a required hard gate returns `NOT_EVALUABLE`, aggregate
   status is `NOT_EVALUABLE`, not `ELIGIBLE`.
3. `ELIGIBLE` is valid only when every applicable required rule returns `PASS`.
4. A rule that is intentionally disabled is `NOT_APPLICABLE`, not
   `NOT_EVALUABLE`.
5. An optional defensive rule that cannot evaluate should remain visible as
   `NOT_EVALUABLE`; it must not silently pass. Whether it blocks the aggregate is
   a declared policy choice, not a default.

For the proposed V1, all technical readiness gates should fail closed for scan
eligibility. A later optional user defensive rule may be configured as either a
blocking policy or an informational policy, but that distinction must be in the
policy contract and UI explanation.

## 17. Multiple Exclusion Reasons

Evaluate all cheap, applicable rules and preserve all known reasons. Do not stop
at the first exclusion when the additional rules are already available from the
same catalogue/facts response.

Example:

```text
NOT_TRADABLE
STALE_DATA
LOW_ACTIVITY
```

Benefits:

- better user explanation;
- useful debugging and operational metrics;
- no order-dependent reason output;
- easier policy migration and future analytics;
- aligns with existing `reasons` lists and persisted Active Scan decisions.

Do not acquire expensive independent facts after a definitive hard gate has
already excluded a market. The evaluator should use a cheap-first order:

1. catalogue existence and state;
2. already available/readiness metadata;
3. bounded Market Facts only for markets still eligible;
4. optional defensive facts only within a declared budget.

## 18. User Configuration Boundary

System hard gates:

- market does not exist;
- current market is not tradable under Market Data semantics;
- required provider capability is unsupported;
- required data is unavailable, stale, or insufficient;
- required raw completeness/synthetic tolerance is not met.

These must not be weakened by an ordinary user threshold. A user may choose not
to run a defensive policy, but cannot make unavailable or stale required evidence
authoritative.

Potential user-configurable defensive preferences:

- enable or disable `LOW_ACTIVITY`;
- activity window, within supported bounded values;
- minimum quote-notional, scoped by provider/quote/window;
- future spread/drawdown policies after those facts are approved.

Configuration may tighten a safe system boundary, but it must not claim that a
failed technical gate is acceptable. Account-specific eligibility and Risk remain
outside this policy and authoritative elsewhere.

## 19. Policy Versioning

Policy versioning is justified even if no separate persistence is introduced.
An eligibility result can change because:

- facts changed;
- freshness boundary changed;
- rule logic changed;
- user configuration changed;
- provider capability changed.

The result should therefore preserve policy name/version and rule versions, in
addition to the Market Facts calculation version already present in 0069. When
an Active Scan persists its scope snapshot, it should snapshot the assessment
reasons and policy identity used for that run. Historical results must not be
rewritten when a later policy version is applied, consistent with ADR-048.

Persisting every ephemeral evaluation is premature. Persisting the decision used
by a durable Active Scan is already required for reconstruction.

## 20. Persistence Assessment

### V1 recommendation

Do not introduce a standalone durable `MarketEligibilityAssessment` store for the
first Story. Use:

- ephemeral deterministic evaluation for a request;
- existing `ActiveScanScopeSnapshot` / `ActiveScanDecisionSnapshot` persistence
  for the decision actually used by a scan;
- existing Market Facts cache for bounded reuse, with its current limitations
  explicitly retained.

This supports debugging of actual scans without creating a second policy/read
model lifecycle.

### Later need

A compact persisted eligibility projection becomes justified when:

- Passive Scanner maintains a recurring universe;
- hundreds of markets are evaluated repeatedly;
- the UI needs historical eligibility explanations outside an Active Scan;
- fact refresh is scheduled independently of user actions;
- policy-version history must be queried across scans.

That would be a material persistence/read-model decision and should be revisited
for an ADR if it creates a new authoritative boundary.

## 21. Cost Model

### 21.1 Current Market Facts behavior

For one cold `MarketFactsService` request:

- one Market Data internal request;
- one market lookup;
- one provider-neutral history call;
- one provider-specific market history request;
- bounded history of at most 720 candles;
- in-memory normalization and calculation;
- cache insertion only when both facts are `AVAILABLE`.

The current implementation does not perform a batch facts request or persist a
catalogue-wide projection.

### 21.2 24h/1h qualitative and quantitative estimate

Assuming a cold cache, one request per market, and approximately 25 returned
OHLC entries per market:

| User scope | Internal facts calls | Provider history calls | Approx. OHLC entries requested | Operational assessment |
|---:|---:|---:|---:|---|
| 10 | 10 | 10 | 250 | Reasonable for bounded explicit scope |
| 50 | 50 | 50 | 1,250 | Possible, but latency and provider behavior need validation |
| 100 | 100 | 100 | 2,500 | Needs explicit budget, batching/queueing, or warm facts |
| 500 | 500 | 500 | 12,500 | Not suitable as synchronous incidental filtering |
| Full catalogue, approximately 1,436 | approximately 1,436 | approximately 1,436 | approximately 35,900 | Requires maintained projection or bounded scheduling |

These are repository-derived request counts, not provider-rate-limit claims.
They exclude retries, serialization overhead, and concurrent deployments. Cache
hits can reduce provider calls, but the process-local 256-entry cache is smaller
than the current catalogue and is lost on restart.

### 21.3 User scope safety

Story 0068 bounds the frontend catalogue display but does not guarantee that an
Active Scan request is small. Current `ActiveScanScopeResolutionService` loads
the full catalogue and treats null/empty requested IDs as all catalogue IDs.
That behavior is in tension with ADR-033, which requires a full-universe scan to
be an explicit product/user choice.

This existing scope-contract discrepancy must not be hidden by 0070. The new
filter must intersect a verified explicit scope and must never expand it.

## 22. Budgeted Evaluation

An explicit evaluation budget is required before 0070 can perform per-market
Market Facts acquisition across an unbounded effective scope.

The exact model is not selected, but the policy needs an equivalent of:

- maximum markets evaluated;
- maximum provider requests;
- maximum evaluation duration;
- maximum fact age;
- behavior when the budget is exhausted;
- explicit distinction between deferred/not-evaluable and excluded.

For the hard-gate-only V1, a budget is less important because `MarketState` is
catalogue data and no history call is necessary. The moment readiness or activity
is acquired per market, the budget becomes a safety requirement, not an
optimization.

A budget limit is not a quality ranking:

```text
max 50 evaluations per cycle = operational bound
best 50 markets = relevance claim
```

If the budget is exhausted, the system must preserve the unevaluated markets as
`NOT_EVALUABLE` or `DEFERRED`, never silently call them low quality or claim they
were excluded by a rule.

## 23. Active Scanner Integration

The clean integration point is inside or immediately around
`ActiveScanScopeResolutionService`, after candidate IDs are derived from the
explicit request and before `ActiveScanScopeSnapshot` is built.

The responsibilities remain distinct:

```text
ActiveScanScopeResolutionService
    -> identify requested candidate IDs
    -> account/market hard eligibility
    -> MarketEligibilityPolicy evaluation
    -> produce decisions and effective IDs

ActiveScanApplicationService
    -> persist scope/decision snapshot
    -> register one AnalysisExecution per effective market
    -> dispatch and reconcile child results
```

The future eligibility policy must not acquire provider-specific payloads itself.
It should consume a typed Market Data contract through the existing internal
client boundary. Market Data remains the owner of facts; Active Scan remains the
owner of orchestration.

Manual specific-market selection must remain possible, subject to hard technical
and account eligibility. A low-priority or policy-disabled market must not become
impossible to inspect merely because an automatic scanner would not select it.

## 24. Top-N Assessment

Top-N can be removed from V1 as a quality/ranking concept, but not necessarily
as an eventual operational scheduling concern.

If elimination leaves:

- 10 markets: current one-execution-per-market orchestration is plausible;
- 50 markets: possible but requires latency and provider-cost validation;
- 300 markets: current Active Scan would register and dispatch 300 child
  analyses, with no investigated operational bound here.

The correct V1 response to 300 markets is not to call them the "best 50". It is
to add an explicit operational budget/queue/deferred status or to require an
explicitly bounded scope. A later relevance ranking may be justified by product
evidence, but it should be a separately named policy with provenance and no
trading interpretation.

## 25. Architectural Ownership

Recommended ownership:

| Concern | Owner |
|---|---|
| Market catalogue, MarketState, normalized OHLC, MarketActivityFact, MarketDataReadiness | Market Data |
| Provider acquisition and provider-specific mapping | Market Data provider adapters |
| Eligibility policy interpretation of typed facts | Market Intelligence |
| Eligibility assessment used by a scan | Market Intelligence scope/application boundary |
| Active Scan persistence, child registration, dispatch, reconciliation | Market Intelligence Active Scan |
| Account permissions and financial Risk | Trading Core / Risk Domain |

`MarketEligibilityPolicy`, `EligibilityRule`, and `MarketEligibilityAssessment`
should therefore live in Market Intelligence, likely adjacent to the existing
`application.scope` boundary, not in `market-data` and not in Trading Core.

This does not require moving provider acquisition into Market Intelligence. The
existing `MarketDataClient` would need a narrowly typed internal Market Facts
method in a future implementation, with explicit deployment authorization for
the `market-intelligence` caller. Story 0069 currently defaults the facts
allowlist to `trading-core`; that deployment decision must be handled explicitly.

## 26. AI Boundary

No AI capability is justified for 0070.

Eligibility answers deterministic questions about tradability, data integrity,
freshness, support, and explicitly configured defensive policies. AI cannot
repair missing evidence, remove a hard exclusion, or decide that stale data is
acceptable for a required analysis.

AI may later consume an assessment to explain why a market was excluded or to
interpret eligible-market analysis, but it must remain non-authoritative under
ADR-033 and ADR-048.

## 27. UI Transparency Requirements

No frontend implementation is proposed. A future UI can be supported if the
backend preserves a compact, typed explanation:

```text
marketId
assessmentStatus
evaluatedAt / cutoff
policyVersion
ruleResults: ruleId, status, reason, fact status/reference
exclusionReasons
```

This is sufficient for examples such as:

```text
XBT/USD
ELIGIBLE

XYZ/USD
EXCLUDED
- market not tradable
- readiness insufficient
```

The UI must not label `ELIGIBLE` as "good", "recommended", "opportunity", or
"safe to trade". It should distinguish `EXCLUDED` from `NOT_EVALUABLE` and avoid
presenting a missing fact as low activity.

## 28. Recommended V1

The smallest coherent Story 0070 should contain:

1. A deterministic eligibility policy in Market Intelligence.
2. Explicit preservation of Story 0068 user scope; no scope broadening.
3. Existing market existence/tradability hard gates, reusing current
   `MarketEligibilityDecision` and reason conventions.
4. Market Facts readiness hard gates for the specific analysis requirement:
   `UNAVAILABLE`, `UNSUPPORTED`, `STALE`, insufficient completed history, and
   unacceptable missing/synthetic/conflicting evidence.
5. Typed aggregate outcomes `ELIGIBLE`, `EXCLUDED`, and `NOT_EVALUABLE`, or
   semantically equivalent existing conventions.
6. All cheap applicable exclusion reasons, not only the first reason.
7. Policy/rule/calculation version and assessment cutoff in scan provenance.
8. A bounded evaluation policy when facts require per-market acquisition.
9. Deterministic tests and no trading-side effects.

### V1 hard gates

- `NOT_TRADABLE` / missing market;
- `DATA_UNAVAILABLE`;
- `UNSUPPORTED_DATA`;
- `STALE_DATA` when required by the analysis contract;
- `INSUFFICIENT_HISTORY`;
- excessive missing, conflicting, or synthetic evidence when the requested
  analysis requires provider-complete evidence.

### V1 defensive rules

No default quantitative defensive rule is justified yet. `LOW_ACTIVITY` should
be a later, opt-in or separately approved policy after threshold and empirical
validation work. There is no V1 case for ATH drawdown, spread, liquidity,
generic volatility, or Market Structure.

### V1 non-goals

- no score;
- no ranking;
- no Top-N quality claim;
- no StrategyEvaluation or StrategyMatch;
- no opportunity, TradePlan, Risk, or execution decision;
- no AI override;
- no new provider-specific facts in Market Intelligence;
- no standalone persistence projection unless scope is expanded and approved.

## 29. Future Rule Roadmap

### NEXT

- explicit 24h or other configured completed-OHLC activity policy, if user value
  is demonstrated;
- absolute activity threshold scoped by provider/quote/window;
- empirical validation of false exclusions and threshold stability;
- bounded facts acquisition/read model if repeated scopes exceed cache capacity.

### LATER

- relative spread with explicit formula and freshness;
- a provider-neutral reusable volatility fact distinct from Trend Context;
- maintained activity/readiness projection for Passive Scanner reuse;
- explicit relevance/priority ordering after eligibility is proven useful.

### RESEARCH

- coverage-aware historical observed-high/drawdown fact;
- executable-size/slippage-based liquidity fact;
- Market Structure evidence;
- provider bulk acquisition and rate-limit/backoff strategy;
- passive-to-active fact reuse and historical eligibility projections.

## 30. Test Strategy

Future Story 0070 should reuse the deterministic and provenance lessons of
Story 0069.

### Rule-level tests

- tradable and non-tradable states;
- every Market Facts status;
- stale boundary equality and just-over-boundary behavior;
- insufficient completed candles;
- missing intervals, synthetic candles, duplicate and conflicting evidence;
- unsupported provider capability;
- no conversion of failure/unknown to zero or PASS;
- deterministic repeatability with a fixed `Clock`.

### Aggregate policy tests

- one exclusion produces `EXCLUDED`;
- multiple exclusions preserve every cheap applicable reason;
- required `NOT_EVALUABLE` produces aggregate `NOT_EVALUABLE`;
- optional not-evaluable rules remain visible and do not silently pass;
- no rule exclusion plus all required PASS produces `ELIGIBLE`;
- policy and rule versions are retained;
- explicit disabled rules are `NOT_APPLICABLE`, not missing.

### Integration and scope tests

- provider-shaped Market Facts contract through the internal client;
- explicit user scope is an upper bound;
- a market outside requested scope is never added by eligibility;
- null/empty/all-scope behavior follows an explicit product contract;
- existing account and Active Scan scope authority remains intact;
- maximum provider-request and history-size bounds are enforced;
- cache hits and expired facts behave deterministically;
- budget exhaustion yields deferred/not-evaluable results, not implicit exclusion;
- 10/50/100/500 scope tests verify no accidental catalogue crawler.

### Safety tests

- no Opportunity, TradePlan, Risk, or execution side effect is created by
  eligibility evaluation;
- `ELIGIBLE` is never mapped to a recommendation or trading signal;
- manual specific-market selection remains distinct from automatic relevance;
- AI and interpretation paths cannot override hard exclusions.

## 31. ADR Assessment

**ADR required for the recommended narrow V1: NO.**

Existing decisions already establish:

- Market Data ownership of market facts and state (ADR-006, ADR-010);
- layered deterministic analysis and human-terminated decision flow (ADR-014);
- Active Scan scope resolution, eligibility/relevance distinction, and
  pre-analysis gating (ADR-033);
- provenance, safe degradation, immutable versioned evidence, and AI non-authority
  (ADR-048).

The recommendation does not require a new service, new source-fact owner, or
new financial authority. It also does not require replacing an approved ranking
architecture: the earlier ranking concept is an investigation recommendation,
not an accepted ADR mandate.

A new ADR would become appropriate if implementation introduces:

- a durable standalone eligibility policy/read-model authority;
- a new Active Scan scope contract that resolves the current empty-scope
  contradiction by architectural change;
- catalogue-wide scheduled facts as a new Market Data persistence lifecycle;
- a cross-provider or cross-currency comparison authority;
- a policy engine shared across Market Intelligence, Trading Core, or Risk.

## 32. Open Human Decisions

Before implementation, the human must decide:

- whether 0070 V1 is hard gates only, or includes an opt-in activity policy;
- the exact analysis requirement that determines readiness interval/lookback;
- whether `NOT_EVALUABLE` blocks all scanner progression or only blocks the
  affected rule/policy;
- how optional defensive-rule uncertainty is surfaced to the user;
- the explicit representation of broad `ALL_ELIGIBLE` scope;
- how to reconcile current empty/null scope behavior with ADR-033's explicit
  full-universe requirement;
- whether the first facts integration is bounded to explicit small scopes;
- the maximum evaluation/provider-request/time budget;
- whether low-priority but technically eligible markets remain manually
  analyzable in every scan mode;
- whether an activity policy is user-configurable and what safe lower/upper
  bounds apply;
- whether the existing Active Scan decision snapshot is sufficient provenance;
- whether the `market-intelligence` service JWT and Market Facts allowlist are
  configured in the target deployment.

## 33. Story 0070 Readiness

**`READY_TO_DESIGN_STORY_0070`**, with the Story constrained to the hard-gate
V1 described in Section 28 and with the open decisions above made explicit in
the Story acceptance criteria.

The repository contains enough evidence to define that small Story without
inventing a score, threshold, ATH claim, liquidity model, spread model,
volatility model, or Market Structure algorithm. A Story that includes default
`LOW_ACTIVITY`, relative ranking, full-catalogue 24h acquisition, or a new
persistent policy engine would require further investigation and likely a
separate architectural decision.

## 34. Final Recommendation

Proceed with an elimination-first Story 0070 only as a deterministic defensive
eligibility gate:

```text
explicit user scope
    -> hard account/market state gates
    -> required Market Facts readiness gates
    -> explainable EXCLUDED / NOT_EVALUABLE / ELIGIBLE
    -> bounded Active Scan
```

Do not create a Top-N ranking or `AnalysisPriorityScore` in this V1. Do not add
LOW_ACTIVITY by default until its absolute threshold and empirical behavior are
approved. Current MarketActivity can represent a 24h closed-OHLC fact using a
feasible interval such as 1 hour, at approximately one provider request and 25
OHLC entries per cold market, but that is not yet a reason to scan every market.

Do not implement "90% below ATH", spread, generic liquidity, generic volatility,
or Market Structure filtering in V1. Keep the policy in Market Intelligence,
facts in Market Data, scan persistence in Active Scan, and Risk/execution
authority downstream. No new ADR is required for this narrow design.

**Report path:**
`docs/architecture/reports/market-eligibility-filtering-investigation.md`

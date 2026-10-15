# Story 0100 Runtime Valuation Investigation

Date: 2026-10-14

## Scope and Guardrails

This report investigates the runtime failure preventing the manual PAPER
`CASH + LINEAR_PERPETUAL` lifecycle from passing T0 Risk. No production code,
configuration, migration, Risk policy, Challenge rule, or database row was
modified. No execution was attempted and no price was injected.

The DevLog Story Agent was checked for relevant project memory, but DevLog was
unavailable because the provider quota was exhausted (`429 insufficient_quota`).
Repository state, persisted runtime evidence, and the live provider read were
used instead.

## Repository Identity

The requested `git fetch origin` was attempted first and failed because the
configured SSH agent refused the `id_ed25519_sk` signature. No Git refs were
changed by that command.

```text
branch:       main
HEAD:         dafc1dbdd9014a20d0b93a0d7d9a1450ae50d874
origin/main:  dafc1dbdd9014a20d0b93a0d7d9a1450ae50d874
```

The worktree had no tracked modifications. These pre-existing untracked
runtime reports were preserved untouched:

```text
docs/architecture/stories/0098-futures-market-facts-without-provider-vwap/runtime-validation-report.md
docs/architecture/stories/0099-execution-validation-transaction-boundary/runtime-continuation-report.md
docs/architecture/stories/0099-execution-validation-transaction-boundary/runtime-resumed-lifecycle-report.md
docs/architecture/stories/0100-non-blocking-post-settlement-challenge-reevaluation/runtime-final-paper-derived-lifecycle-report.md
```

## Incident Summary

The authoritative manual validation reached the following boundary:

```text
TradePlan ACCEPTED
  -> T0 Risk
  -> CONTEXT_UNAVAILABLE
  -> CURRENT_MARKET_VALUATION_UNAVAILABLE
```

Runtime identifiers:

```text
account:       2402dd0c-8ff9-4b71-8af8-734b6cfed67c
challenge:     fcb3f03c-c22f-4650-afeb-e5fd572b3b90
trade plan:    a893c780-a037-3de2-a1d8-071e333283e4
T0 evaluation: 6b9fec4b-72a6-4732-b2cb-4a0fce80f26b
market:        AAPLx/USD
product:       LINEAR_PERPETUAL
provider:      KRAKEN
```

No `ExecutionIntent`, position, settlement, or Challenge reevaluation outbox
event was created.

## T0 Call Graph

The public endpoint is:

```text
POST /api/v1/trade-plans/{tradePlanId}/versions/{version}/risk-evaluations
```

The exact application path is:

```text
TradePlanRiskEvaluationController.evaluate
  trading-core/risk/api/TradePlanRiskEvaluationController.java:38-47
  -> TradePlanRiskEvaluationService.evaluate
  -> TradePlanRiskEvaluationService.evaluateOfficial(command, false)
  -> TradePlanRiskEvaluationService.evaluateAvailable
```

Inside `evaluateAvailable`:

| Step | Class and method | Input | Output | Failure behavior |
|---|---|---|---|---|
| Account and configuration | `TradePlanRiskEvaluationService.evaluateAvailable` | account ID, actor | owned account, risk configuration, broker account, profile | `ContextUnavailable` with a specific code |
| Broker facts | `RiskFactsProvider.load` implementation selected by `ModeAwareRiskFactsProvider` | account, broker, risk day | broker snapshot with balances, positions, equity, `observedAt` | broker context failure is fail-closed |
| Risk-day valuation | `MarketValuationPort.value` through `MarketValuationClient.value` | reporting currency, risk-day start, assets | valuation snapshot | incomplete snapshot maps to `RISK_DAY_START_VALUATION_UNAVAILABLE` |
| Current instruments | `TradePlanRiskEvaluationService.evaluateAvailable` | positions plus proposed instrument, assets | current valuation snapshot | `requireComplete(current, "CURRENT_MARKET_VALUATION_UNAVAILABLE")` |
| Risk context | `RiskEvaluationContextBuilder.build` | account, portfolio, market, proposed trade | immutable Risk Domain context | missing proposed price or market fact remains fail-closed |
| Deterministic risk | `DeterministicRiskEngine.evaluate` | immutable context | risk result | deterministic violations or approval |
| Response mapping | `TradePlanRiskEvaluationService.evaluateOfficial` | `ContextUnavailable` | persisted `CONTEXT_UNAVAILABLE` response | HTTP remains `200` with `approved=false` |

The current valuation call is made with `brokerSnapshot.observedAt()` as the
requested timestamp. `MarketValuationClient` refreshes requested market IDs
through Market Data before asking for the batch valuation and then uses the
current clock time for the valuation request when a refresh is required.

The same current valuation path is used by
`ExecutionTimeRiskRevalidationService.evaluateAvailable` for T1. T1 therefore
has the same required valuation semantics, although it should normally use a
tighter execution-time freshness policy if that policy is formally introduced.

## Authoritative Valuation Model

Trading Core consumes `MarketValuationPort.Snapshot`, not a catalogue row, OHLC
candle, or a raw provider ticker. The snapshot contains:

```text
snapshotId
sourceVersion
reportingCurrency
valuationTimestamp
capturedAt
policyVersion
maxObservationAge
complete
facts
sourcePayload
```

Each `MarketValuationPort.Fact` contains the fact type, requested ID, market,
selected price use, converted value, source price, conversion rate, status, and
source provenance.

For an instrument, `ValuationSnapshotService.instrumentFact` selects the price
according to `PriceUse`:

```text
LAST                 -> last
CONSERVATIVE_SELL    -> bid
CONSERVATIVE_BUY     -> ask
```

The manual LONG plan therefore requires the Futures `ask`, not the last trade.
The valuation value is the selected source price multiplied by any quote to
reporting-currency conversion rate.

Market Data persists `ValuationSnapshot` and `ValuationFact`. A snapshot is
`COMPLETE` only when every requested fact is `AVAILABLE`.

## Freshness Semantics

There are two distinct freshness gates:

1. `MarketPriceSnapshotService` uses `market-data.snapshot.stale-after`,
   default `30s`, to classify an on-demand ticker as `FRESH`, `STALE`, or
   `UNAVAILABLE` and to decide whether the in-memory ticker is reusable.
2. `ValuationSnapshotService` uses `valuation.max-observation-age`, default
   `PT5M`, to validate the persisted `PriceObservation` used by authoritative
   valuation.

The authoritative five-minute rule is implemented as:

```java
observation.getEffectiveAt()
    .isBefore(valuationTimestamp.minus(maxObservationAge))
```

An observation is stale when it is strictly before the lower boundary. The
observation must also satisfy both no-lookahead predicates in the repository
query:

```text
effectiveAt <= valuationTimestamp
capturedAt  <= valuationTimestamp
```

The T0 valuation snapshot persisted for the incident proves the precise
failure:

```text
valuation timestamp:  2026-10-14T21:51:22.437899Z
captured at:          2026-10-14T21:51:22.470761Z
policy:               CONSERVATIVE_MULTI_HOP_FX_NO_LOOKAHEAD_V3
max observation age:  PT5M
snapshot status:      INCOMPLETE
fact status:          STALE
source price:         342.910000000000000000 (ask)
source effective at:  2026-10-14T21:27:25.027957Z
source captured at:   2026-10-14T21:51:22.431320Z
observation age:      PT23M57.40994225S
```

`TradePlanRiskEvaluationService.requireComplete` treats a null snapshot, an
incomplete snapshot, an invalid source version, any non-`AVAILABLE` fact, or a
fact without a value as unavailable. It maps this snapshot to
`CURRENT_MARKET_VALUATION_UNAVAILABLE`; the code does not expose the more
specific underlying `STALE` fact status in the Risk response.

Increasing the five-minute TTL would not acquire a price. It would only accept
an older source observation, increase execution risk, and hide a broken or
unavailable refresh source. It is therefore not the recommended fix.

## Complete AAPLx/USD Price Path

The current implementation is:

```text
Kraken Futures REST / WebSocket
  -> KrakenMarketData
  -> KrakenProviderSymbolResolver.toRestPair
  -> KrakenFuturesTickerMapper / KrakenDataStreamProvider
  -> TickerEvent
  -> TickerEventPublisher.store
  -> PriceObservation persistence and in-memory latest ticker
  -> MarketPriceSnapshotService
  -> InternalMarketController /internal/markets/prices/snapshot
  -> MarketValuationClient
  -> InternalValuationController /internal/v1/valuation-snapshots/batch
  -> ValuationSnapshotService
  -> MarketValuationPort.Snapshot
  -> TradePlanRiskEvaluationService.requireComplete
```

For the selected market, the identity boundary is:

```text
canonical symbol: AAPLx/USD
product type:     LINEAR_PERPETUAL
provider:         KRAKEN
provider symbol:  PF_AAPLXUSD
```

The persisted Market row and the live Kraken instrument endpoint agree:

```text
Kraken instrument symbol: PF_AAPLXUSD
Kraken pair:              AAPLx:USD
type:                     flexible_futures
isExpired:                false
```

The Futures mapper creates the canonical `AAPLx/USD` symbol while retaining
`PF_AAPLXUSD` as `providerSymbol`. `KrakenDataStreamProvider` uses
`providerSymbol` for Futures subscriptions, and `KrakenMarketData` uses it for
the Futures REST ticker request. There is no evidence of a Spot/Futures symbol
identity collision in this incident.

## SPOT Versus LINEAR_PERPETUAL

| Concern | SPOT | LINEAR_PERPETUAL |
|---|---|---|
| Discovery | Kraken Spot AssetPairs | Kraken Futures instruments, status, tickers |
| Product routing | `Market.productType=SPOT` | `Market.productType=LINEAR_PERPETUAL` |
| REST ticker | Kraken Spot ticker, mapped with canonical/rest pair | Kraken Futures `/derivatives/api/v3/tickers`, matched by `providerSymbol` |
| WebSocket | Kraken Spot WebSocket | Kraken Futures WebSocket |
| Subscription symbol | canonical/provider Spot symbol | persisted `providerSymbol`, e.g. `PF_AAPLXUSD` |
| Normalized object | generic `TickerEvent` | same generic `TickerEvent` |
| Persistence | `PriceObservation` bid/ask/last | same `PriceObservation` bid/ask/last |
| OHLC | Spot OHLC with provider VWAP | Futures Charts REST; no Futures OHLC WebSocket; VWAP may be null |
| Current snapshot | on-demand Spot REST acquisition or dynamic stream | on-demand Futures REST ticker acquisition or dynamic Futures stream |
| Valuation selection | same valuation service | same valuation service |
| Risk consumption | same `MarketValuationPort` contract | same `MarketValuationPort` contract |

The divergence is in provider acquisition and subscription, not in the Risk
valuation contract. This preserves ADR-053's provider-neutral boundary.

## Futures Coverage and Subscription Lifecycle

The runtime database currently contains 204 tradable `LINEAR_PERPETUAL` markets.
At the investigation time:

```text
fresh valuation: 0
stale valuation: 2
no valuation:    202
```

The stale examples were:

| Market | Provider symbol | Latest effective time | Age at query | State |
|---|---|---:|---:|---|
| AAPLx/USD | PF_AAPLXUSD | 2026-10-14T21:27:25.027957Z | about 40m | stale |
| 1INCH/USD | PF_1INCHUSD | 2026-10-14T16:34:31.021874Z | about 5h33m | stale |

These are observations, not continuously maintained valuations. A catalogue
synchronization only discovers and persists markets; it does not subscribe all
markets to ticker streams.

Subscriptions are explicit and dynamic:

```text
MarketController.subscribe
  -> MarketSubscriptionServiceImpl.subscribe
  -> MarketDataStreamProvider.subscribe
  -> KrakenDataStreamProvider.sendByProduct
  -> Futures WebSocket connection and subscribe event
```

The first consumer for a market/stream creates the provider subscription.
Futures ticker subscriptions are sent as:

```json
{"event":"subscribe","feed":"ticker","product_ids":["PF_AAPLXUSD"]}
```

Subscriptions are kept in memory, replayed after a reconnect, and removed on
the final unsubscribe. There is no startup subscription to every tradable
derivative and no scanner-wide subscription established by catalogue discovery.

After the latest container restart, runtime logs showed normal Market Data
startup, synchronization, and Eureka registration, but no Futures WebSocket
subscription activity. The previous container logs were removed by the
requested container recreation, so historical connection/acknowledgement logs
from the exact T0 attempt are not available anymore. The database snapshot and
provider response remain authoritative evidence for the failure.

## Why the Two Observations Became Stale

The two derivative observations are strongly explained by prior targeted
on-demand requests:

- `MarketPriceSnapshotService` first checks its in-memory latest ticker.
- If it is absent or older than 30 seconds, it calls
  `MarketDataProvider.acquireCurrentSnapshot` for that one market.
- `KrakenMarketData.acquireFuturesSnapshot` calls the Futures REST tickers
  endpoint, selects the matching perpetual `providerSymbol`, maps it to a
  `TickerEvent`, and records the event.
- `TickerEventPublisher` persists the event as a `PriceObservation`.

For AAPLx, the T0 refresh captured a new database row at

At investigation time, Kraken Futures REST returned:

```text
PF_AAPLXUSD: lastTime=2026-10-14T21:27:25.0279574Z
PF_XBTUSD:   lastTime=2026-10-14T22:07:23.084961459Z
```

Therefore:

```text
PROVEN:
- Only two derivative markets have persisted observations.
- Both are stale under the PT5M valuation policy.
- AAPLx's on-demand refresh persisted an observation with an old provider
  effective timestamp.
- Kraken Futures REST was reachable and returned a valid but stale AAPLx ticker.
- No catalogue-wide Futures subscription exists in the current architecture.

STRONGLY INDICATED:
- The two persisted observations were produced by earlier targeted runtime
  probes or T0/on-demand valuation requests, rather than a global stream.
- AAPLx was not receiving a fresh provider ticker at the time of T0.

UNKNOWN:
- Why Kraken's AAPLx source timestamp stopped advancing.
- Whether a dedicated Futures WebSocket subscription for AAPLx would have
  produced a newer event at that exact time.
- Whether the prior container instance had a connection failure before the
  container recreation removed its logs.
```

## Push, Pull, and Current T0 Contract

The current architecture is **hybrid**:

```text
push:
  optional dynamic Kraken WebSocket subscription
  -> TickerEventPublisher
  -> persisted observations

pull:
  T0 MarketValuationClient
  -> Market Data on-demand snapshot endpoint
  -> provider REST ticker acquisition when cache is absent/stale

valuation:
  persisted observation
  -> five-minute authoritative valuation gate
```

T0 does not call Kraken and Risk does not know provider details. Trading Core
requests a valuation from Market Data; Market Data may acquire a current
snapshot and then returns an authoritative batch valuation. The current
contract is effectively:

```text
request latest provider-backed observation for requested markets
-> build valuation at the requested timestamp
-> reject the batch if any required fact is missing or older than PT5M
```

This is not a blind `GET latest known valuation`: the MarketValuationClient
requests an on-demand refresh. It is also not a guarantee that the provider
returned a fresh timestamp. A reachable provider response can still become a
`STALE` fact.

There is already a bounded per-market refresh mechanism. There is no separate
`ensureFresh` contract that guarantees success or distinguishes provider
source staleness from a missing observation before the valuation batch is
created.

## Observability

Existing useful evidence includes:

- persisted `PriceObservation.effectiveAt` and `capturedAt`;
- persisted valuation snapshot status and `maxObservationAge`;
- valuation fact status, source price, source symbol, source timestamps, and
  observation age;
- Market Data logs for failed on-demand acquisition;
- provider subscription success/failure control messages;
- provider parsing and ignored-symbol warnings;
- public Market Data snapshot status `FRESH`, `STALE`, or `UNAVAILABLE`.

Missing incident-level observability includes:

- a structured reason when the provider returns a valid but old ticker;
- a correlation ID linking T0, refresh request, provider acquisition, and
  valuation fact;
- explicit metrics for derivative markets with no active ticker subscription;
- a durable distinction between `no observation`, `provider stale`, and
  `subscription absent` in the Risk-facing failure response;
- a durable record of Futures subscription acknowledgements by market.

These are observability gaps, not reasons to weaken the existing fail-closed
valuation rule.

## Architecture Options

### Model A: Passive latest-known valuation

Market Data continuously maintains a configured set of prices; Risk reads the
latest observation and fails if it is stale.

Advantages: low T0 latency, simple Risk contract, good for passive scanners.
Risks: requires an explicit market universe and subscription lifecycle; a
catalogue of 204 derivatives cannot be treated as live merely because rows
exist; provider stream loss must be visible and recoverable.

This model fits scanners only if the scanner's universe is explicitly
subscribed and readiness is tracked. It is insufficient by itself for a user
initiating execution on an arbitrary market.

### Model B: On-demand fresh valuation

Trading Core requests a fresh authoritative valuation for the exact market;
Market Data obtains or confirms a provider observation and returns only when
the freshness contract is satisfied.

Advantages: strong PRE_TRADE semantics, no need to stream every market, and
provider-specific acquisition remains inside Market Data.

Risks: provider latency and rate limits occur on the execution path; a provider
that returns an old ticker still fails; the contract needs bounded timeout and
an explicit source-staleness failure.

The repository already has a partial version of this model through the
on-demand REST snapshot service, but it does not guarantee a fresh provider
timestamp.

### Model C: Hybrid

Use continuous streams for configured scanner/active universes, preserve the
freshness gate, and add a bounded provider-neutral ensure-fresh operation for
interactive PRE_TRADE/T1 requests when the cached observation is stale.

This best fits the current architecture:

- Market Data owns provider routing, subscriptions, normalization, and
  freshness;
- passive scanners use maintained stream coverage;
- active scanners use an explicit configured universe;
- PRE_TRADE can request a bounded fresh confirmation;
- T1 can require an even tighter observation age or a new observation;
- Risk remains deterministic, provider-neutral, and fail-closed.

The operation must return a structured result such as fresh, stale-provider,
unavailable, or timed-out without exposing Kraken symbols to Trading Core.

## T0, T1, Scanner, and Quant Implications

T0 should require a fresh valuation before human approval can become executable;
analysis freshness is not automatically execution freshness.

T1 should normally use the same provider-neutral contract with stricter
freshness and bounded timing. T1 should not silently reuse a T0 price when the
market has become stale.

Passive scanners may consume continuously maintained observations and reject
markets outside their declared coverage/freshness window. Active scanners and
interactive TradePlan flows need an explicit ensure-fresh boundary.

Historical OHLC and Futures Charts candles remain research/activity inputs.
They are not substitutes for current bid/ask valuation. Quant Research may use
historical and derived data, but must not weaken live T0/T1 valuation semantics.

## Root Cause Classification

Primary classification:

```text
E. Market Data refresh architecture gap
```

Contributing factor:

```text
The reachable Kraken Futures ticker for PF_AAPLXUSD returned a provider
effective timestamp 23m57s old at T0, and therefore the existing on-demand
refresh could only persist a stale observation.
```

This is not classified as a provider connectivity defect: the Futures REST
endpoint was reachable and returned a valid response. It is not a provider
symbol/identity defect: `AAPLx/USD` maps to `PF_AAPLXUSD` consistently in the
database, adapter, and provider response. It is not a freshness-policy defect:
the five-minute gate is appropriate for PRE_TRADE and correctly rejected the
23m57s-old source.

## Root Cause Statement

```text
T0 cannot obtain an authoritative fresh LINEAR_PERPETUAL valuation because
Market Data has no catalogue-wide Futures refresh/subscription coverage and the
on-demand Kraken Futures acquisition for PF_AAPLXUSD returned a valid ticker
whose provider effective timestamp was already 23m57s old, causing
ValuationSnapshotService to classify the instrument fact as STALE and Risk to
correctly fail closed with CURRENT_MARKET_VALUATION_UNAVAILABLE.
```

## Recommended Architecture

Recommend the smallest architectural direction represented by **Model C,
Hybrid**:

- **Responsible service:** Market Data Service owns all provider acquisition,
  Futures subscription coverage, provider symbols, normalization, and
  freshness classification.
- **Contract semantics:** expose a provider-neutral bounded fresh-valuation
  result that distinguishes `FRESH`, `STALE_PROVIDER`, `UNAVAILABLE`, and
  `TIMEOUT`; do not expose Kraken payloads or symbols to Trading Core.
- **Freshness ownership:** Market Data owns source timestamps and the
  `PriceObservation`/valuation freshness gate; Trading Core consumes the
  result and Risk remains a deterministic read-only evaluator.
- **Failure semantics:** fail closed for T0/T1 when required facts are stale or
  unavailable; preserve the distinction in diagnostics and provenance.
- **Timeout:** use a bounded acquisition wait and never retry unsafe execution
  operations from Risk.
- **Provider neutrality:** implement the contract behind the existing
  `MarketValuationPort`; Kraken REST/WebSocket decisions remain in the adapter.
- **T0 impact:** request or confirm a fresh source observation for the proposed
  market before approval.
- **T1 impact:** require a tighter or newly confirmed observation before
  execution authorization.
- **Scanner impact:** maintain explicit subscription/readiness coverage for
  passive and active scanner universes instead of treating the market catalogue
  as live price coverage.

The first implementation step should be a design decision and contract test,
not a TTL increase or a Risk bypass.

## ADR and Story Decision

```text
ADR REQUIRED: YES
```

Proposed ADR title:

```text
Fresh Provider-Neutral Market Valuation Contract for PRE_TRADE and T1
```

Decision scope: ownership of fresh valuation, hybrid stream/on-demand
acquisition, freshness and timeout semantics, scanner coverage, and the
T0/T1 contract.

```text
STORY 0101 JUSTIFIED: YES
```

Proposed Story title:

```text
Provide bounded fresh provider-neutral valuation for derivative PRE_TRADE and T1
```

The Story is justified only after ADR approval. No Story 0101 was created by
this investigation.

## Final Investigation Verdict

```text
TESTED SHA: dafc1dbdd9014a20d0b93a0d7d9a1450ae50d874
FAILURE: CONTEXT_UNAVAILABLE / CURRENT_MARKET_VALUATION_UNAVAILABLE
ROOT CAUSE CLASSIFICATION: E. Market Data refresh architecture gap
PRODUCTION CODE MODIFIED: NO
CONFIGURATION MODIFIED: NO
DATABASE MANUAL MUTATIONS: NO
EXECUTION ATTEMPTED: NO
```

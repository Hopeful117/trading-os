# Repository Analysis - Story 0098

## Analysis Status

`APPROVED`

## Scope Assessment

The Story is a focused Market Data and Market Intelligence contract change. It
must not be implemented as a frontend workaround or by writing a fabricated
value into `OhlcEvent.vwap`.

The runtime defect is reproducible:

- Kraken Futures Charts returns complete-looking hourly OHLCV candles;
- the Futures payload omits `vwap` and `trades`;
- `KrakenFuturesOhlcMapper` therefore maps `vwap` to `null`;
- `MarketFactsService` rejects the candles as unusable activity evidence;
- Active Scan maps the resulting `INSUFFICIENT_DATA` activity status to
  `INSUFFICIENT_HISTORY`.

The selected Story direction is to add an explicit provider-neutral activity
calculation basis for Futures rather than claim that `close` is a true VWAP.

## Repository Evidence

### Futures Provider Boundary

`market-data/src/main/java/com/hope/trading/market_data/kraken/brokerClient/KrakenMarketData.java`
acquires Futures history through:

```text
GET /api/charts/v1/trade/{providerSymbol}/{resolution}
```

`KrakenFuturesChartCandle` declares optional `volume`, `vwap`, and `trades`, but
the live payload observed for `PF_1INCHUSD` contains only `time`, OHLC, and
`volume`. No mapper defect was found: the provider field is absent.

`KrakenFuturesOhlcMapper` correctly preserves the provider omission as
`OhlcEvent.vwap = null`. This behavior must remain unchanged.

### Market Facts Calculation

`MarketFactsService` currently:

- uses completed, non-synthetic observed events;
- calculates quote notional as `sum(volume * vwap)`;
- treats missing volume or VWAP as an incomplete activity candle;
- returns `INSUFFICIENT_DATA` when the activity window is incomplete;
- keeps readiness and activity statuses separate.

`MarketActivityFact` exposes `quoteNotional` and calculation version, but no
calculation-basis field. The internal Market Facts response exposes activity
status and calculation version to Market Intelligence, but not the activity
price basis.

### Eligibility and Scanner

`ActiveScanScopeResolutionService` requests Market Facts and
`MarketEligibilityPolicy` rejects a market when activity or readiness is not
available. `MarketEligibilityPolicy` maps `INSUFFICIENT_DATA` to
`INSUFFICIENT_HISTORY`.

No provider-specific logic should be added to this layer. The scanner should
receive a typed fact whose calculation basis is already explicit and valid.

### Existing Tests

Relevant coverage exists for:

- Futures OHLC mapping and interval routing;
- true VWAP-based Market Facts calculations;
- missing/incomplete activity evidence;
- zero-volume and unusable metric semantics;
- Market Facts to Active Scan eligibility mapping.

The missing coverage is a provider-shaped Futures candle with absent VWAP that
is evaluated through the selected close-basis activity path, including its
provenance and contract version.

## Responsibility Boundary

- Kraken adapter owns provider payload interpretation and preserves missing
  provider VWAP as missing.
- Market Data owns the normalized OHLC and derived activity fact contracts.
- Market Facts owns deterministic calculation and calculation-basis provenance.
- Market Intelligence consumes the typed facts and applies eligibility policy.
- Frontend remains unchanged except for any necessary display of the explicit
  diagnostic or calculation basis.
- Trading Core, Risk, Broker Service, and execution remain outside scope.

## Recommended Implementation Shape

The implementation should introduce an explicit activity calculation basis,
for example a provider-neutral enum or equivalent versioned contract field, and
support at least:

```text
VWAP                 -> volume * provider VWAP
CLOSE_PRICE_FALLBACK -> volume * completed candle close
```

The exact type and public/internal serialization location belong to the
Implementation Plan. The following invariants are already established:

- Spot remains on `VWAP` without behavior change;
- Futures may use `CLOSE_PRICE_FALLBACK` only when the provider omits VWAP and
  all required close/volume/temporal evidence is valid;
- no fallback value is written into `OhlcEvent.vwap`;
- the calculation version changes when the semantic contract changes;
- consumers can distinguish the fallback from a true VWAP calculation;
- missing evidence remains non-available.

If changing `MarketActivityFact` or its internal response creates a durable
generic contract decision not covered by the accepted ADRs, an ADR must be
prepared and approved before implementation proceeds.

## Main Risks

- A close-price basis is deterministic but is not a true VWAP and must not be
  presented as one.
- Existing consumers may assume `quoteNotional` always means `volume * vwap`.
- Contract/version changes must be propagated consistently between Market Data,
  Market Intelligence, persistence snapshots, and tests.
- A provider may return zero volume with a missing VWAP; the implementation must
  preserve the existing zero-volume semantics without masking missing evidence
  for positive-volume candles.
- Relaxing the activity gate without explicit provenance could make incomplete
  Futures data appear eligible.
- Runtime validation depends on the live Kraken Futures payload and rate limits.

## Required Implementation Planning Questions

Before implementation, the plan must resolve:

- whether the calculation basis belongs on `MarketActivityFact`, the internal
  `MarketFactsResponse`, or both;
- whether `quoteNotional` remains a compatible name or needs a clearer semantic
  contract/version;
- how zero-volume candles with missing VWAP are classified under the Futures
  basis;
- which calculation version and provenance fields are persisted in scan
  snapshots, if any;
- whether the frontend should show the basis or only the existing eligibility
  reason;
- how to prove Spot behavior remains unchanged.

## Architectural Assessment

The change can likely remain inside existing Market Data and Market Intelligence
boundaries. It does not require a new provider, a trade-history store, or a
frontend scanner redesign.

However, changing the meaning of `MarketActivityFact.quoteNotional` is a
material semantic contract change. The Implementation Plan must explicitly
confirm whether ADR-048, ADR-053, Story 0069, and Story 0070 are sufficient. If
not, implementation must stop for an ADR decision rather than silently change
the generic fact semantics.

## Validation Assessment

Applicable validation includes:

- `market-data` unit and integration tests for provider mapping and Market Facts;
- `market-intelligence` eligibility contract tests;
- full independent module test suites for affected Maven services;
- `git diff --check`;
- runtime call to the Market Facts scope path for at least one Spot and one
  Futures market;
- frontend scanner verification after the backend contract is deployed.

The current repository does not provide a trustworthy way to calculate a true
historical Futures VWAP from the available public candle payload. The Story
therefore must be implemented and validated as an explicit derived activity
basis, not as VWAP reconstruction.

## Context Limitations

The DevLog applicability check for project `trading-os` was attempted but the
Story Agent provider returned an exhausted-quota error. Current repository
state, accepted Stories, ADRs, runtime evidence, and official Kraken API
documentation were used as authoritative sources for this analysis.

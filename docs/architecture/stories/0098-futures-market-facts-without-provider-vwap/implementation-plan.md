# Story 0098 - Implementation Plan

## Plan Status

`IMPLEMENTED - AWAITING RUNTIME VALIDATION`

This plan preserves the distinction between provider VWAP and a derived
activity price basis. It does not change scanner ranking, strategy, Risk,
Challenge, or execution behavior.

## Design

### 1. Confirm the contract decision before coding

The implementation must not write `close`, typical price, or another proxy into
`OhlcEvent.vwap`.

The activity calculation will expose an explicit provider-neutral basis:

```text
VWAP                 -> sum(volume * provider VWAP)
CLOSE_PRICE_FALLBACK -> sum(volume * completed candle close)
```

The basis and calculation version must be visible in the Market Facts contract
consumed by Market Intelligence. If the existing `quoteNotional` contract
cannot safely carry both meanings, prepare and obtain approval for an ADR and
version the affected contract before implementation continues.

### 2. Add the calculation-basis contract

Extend the Market Data domain and internal Market Facts response with a typed
calculation-basis value. Keep the change provider-neutral; Kraken-specific
classes must not appear in the contract.

The contract must:

- represent true provider VWAP and the explicit close-price fallback;
- preserve the existing calculation version semantics and change the version
  when the calculation meaning changes;
- be propagated to the `market-intelligence` Feign response model;
- remain available to eligibility diagnostics without requiring Market
  Intelligence to inspect OHLC events.

### 3. Select the basis at the Market Data boundary

Reuse the existing provider registry and product-aware Market Data boundary.
The provider capability or equivalent boundary must select
`CLOSE_PRICE_FALLBACK` only for supported Futures markets whose provider
history omits VWAP. Spot must continue to select `VWAP`.

Do not add Kraken-specific conditionals to Market Intelligence. Do not use the
fallback when the provider supplies a VWAP but the value is malformed; malformed
provider evidence remains non-available.

### 4. Calculate activity deterministically

Update `MarketFactsService` to calculate each completed candle according to the
selected basis:

- `VWAP`: require valid volume and VWAP, then add `volume * vwap`;
- `CLOSE_PRICE_FALLBACK`: require valid volume and close, then add
  `volume * close`;
- preserve zero-volume semantics and reject missing/invalid required values;
- preserve raw-versus-normalized evidence and all existing temporal gates;
- retain explicit `INSUFFICIENT_DATA`, `STALE`, `UNAVAILABLE`, and
  `UNSUPPORTED` outcomes.

The implementation must not change readiness semantics or treat synthetic
candles as provider-observed activity evidence.

### 5. Propagate and consume provenance

Update the internal Market Facts response and Market Intelligence mapping so the
calculation basis is retained in the eligibility provenance or equivalent
diagnostic context.

`MarketEligibilityPolicy` should continue to decide eligibility from typed fact
statuses and readiness. It must not calculate or reinterpret quote notional.

The frontend does not need a new workflow branch for this Story. If the basis
is displayed, it must be presented as an activity calculation basis, never as a
provider VWAP.

### 6. Preserve unsupported and incomplete behavior

Keep the current fail-safe behavior for:

- missing close or volume under the selected fallback basis;
- missing VWAP under the VWAP basis;
- stale or incomplete temporal coverage;
- synthetic candles;
- duplicate conflicts;
- provider acquisition failures;
- unsupported products or provider capabilities.

No authentication or authorization rule changes are allowed.

## Expected Production Files

Likely changes are limited to:

- `market-data/src/main/java/com/hope/trading/market_data/model/` for the typed
  calculation basis;
- `market-data/src/main/java/com/hope/trading/market_data/service/` for generic
  deterministic basis-aware activity calculation;
- `market-data/src/main/java/com/hope/trading/market_data/brokerClient/` and
  `market-data/src/main/java/com/hope/trading/market_data/kraken/` for the
  provider capability selection;
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/`
  for internal response propagation;
- relevant Market Data and Market Intelligence tests;
- an ADR only if the existing generic fact contract cannot safely express the
  new semantic distinction;
- this Story documentation if the final contract or runtime procedure differs
  from the approved Story.

No frontend implementation is planned unless runtime validation proves that an
existing user-facing diagnostic becomes misleading.

## Expected Tests

### Provider and Mapping

- Futures payload without `vwap` maps to `OhlcEvent.vwap = null`;
- Futures provider capability selects `CLOSE_PRICE_FALLBACK`;
- Spot provider capability selects `VWAP`;
- malformed provider values remain non-available;
- provider-specific symbols and payloads remain inside the adapter.

### Market Facts

- complete Futures OHLCV history produces deterministic close-basis activity;
- complete Spot history produces unchanged VWAP-based activity;
- activity basis and calculation version are propagated;
- missing close/volume remains incomplete under the fallback;
- missing VWAP remains incomplete under the VWAP basis;
- zero-volume behavior remains explicit and deterministic;
- stale, synthetic, missing, and conflicting evidence remains rejected;
- repeated evaluation produces the same result.

### Market Intelligence

- Feign response deserializes the new basis field;
- eligibility preserves the fact status and provenance;
- complete Futures facts become eligible;
- incomplete Futures facts remain `NOT_EVALUABLE` with
  `INSUFFICIENT_HISTORY`;
- Spot eligibility behavior remains unchanged.

Retain the complete existing test suites for both affected modules.

## Runtime Acceptance

After focused tests and module validation:

1. Rebuild and restart `market-data` and `market-intelligence`.
2. Select one live tradable Kraken `LINEAR_PERPETUAL` market.
3. Verify the raw Futures OHLC payload still has no provider VWAP and the
   normalized event keeps `vwap=null`.
4. Request Market Facts through the protected internal path.
5. Verify the response reports `CLOSE_PRICE_FALLBACK`, a calculation version,
   complete activity evidence, and a deterministic quote notional.
6. Resolve Active Scan scope for the same Futures market and verify it becomes
   eligible when all other gates pass.
7. Resolve scope for an equivalent Spot market and verify existing VWAP-based
   behavior remains unchanged.
8. Verify missing/incomplete evidence still produces explicit non-eligible
   results.

Do not claim true Futures VWAP support from this Story. Do not proceed into
Risk, Challenge progression, or derivative execution validation.

## Validation Commands

Run focused tests first, then the affected module suites:

```bash
mvn -q -Dtest=MarketFactsServiceTest,MarketHistoryServiceTest,KrakenFuturesOhlcRoutingTest,KrakenFuturesOhlcMapperTest test
mvn -q test
```

For `market-intelligence`, run the focused eligibility and Market Facts client
tests followed by its full module suite. Also run:

```bash
git diff --check
```

Adjust class names to the tests added during implementation. Runtime acceptance
must use the rebuilt services and live provider-shaped responses, not fixtures
alone.

## Explicit Exclusions

- no true historical Futures VWAP reconstruction claim;
- no historical trade replay or durable tick store;
- no provider-specific public contract;
- no Spot behavior change;
- no scanner ranking or strategy change;
- no Risk, Challenge, leverage, margin, funding, settlement, or execution
  change.

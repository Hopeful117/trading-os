# Repository Analysis - Story 0177

## Status

`RECORDED - HUMAN ANALYSIS APPROVAL REQUIRED`

This analysis is an engineering artifact. Its status does not grant approval for
implementation.

## Baseline

- Branch: `feat/ctrader-read-only-quote`
- HEAD: `b47755ff19b0fb6f967756c444a029f25914be0f`
- Worktree: intentionally dirty with pre-existing cTrader, product-identity,
  runtime-artifact, and test changes; none were reverted.
- `git diff --check`: passed.
- DevLog: unavailable because authentication is required.
- Obsidian vault: not consulted; repository ADRs, Story 0177, and current source
  provide the required project context.

## Authority and Constraints

ADR-081 is accepted and establishes the governing direction:

- cTrader provider identity does not determine product type;
- classification is provider-symbol specific;
- absent, invalid, or ambiguous product identity must fail closed;
- all cTrader symbols currently receiving `SPOT` through fallback are in scope;
- product identity must not infer Account `SettlementModel`;
- CFD settlement remains unsupported and fail-closed.

ADR-051 remains authoritative for the current compatibility policy:

```text
INVENTORY + SPOT              -> compatible
CASH + LINEAR_PERPETUAL       -> compatible
INVENTORY + CFD               -> incompatible
CASH + CFD                    -> incompatible
```

The Story therefore changes identity classification, not account settlement or
CFD financial behavior.

## Current Classification Path

The current cTrader path is:

```text
cTrader symbol metadata
  -> JsonWebSocketCTraderOpenApiClient.mapSymbol
  -> CTraderSymbol(id, name, digits, minVolume, maxVolume, stepVolume)
  -> CTraderMarketData.toMarket
  -> symbolMappings[providerSymbol]
  -> productType(providerSymbol)
  -> Market.productType
  -> MarketSynchronizationImpl
  -> persisted Market row
```

`CTraderMarketData.productType` currently performs:

```java
String configured = properties.symbolProductTypes().get(providerSymbol);
return configured == null || configured.isBlank()
        ? InstrumentProductType.SPOT
        : InstrumentProductType.valueOf(configured.trim().toUpperCase());
```

This is the confirmed defect. Missing evidence is converted into `SPOT`.

Current configuration facts:

```text
EURUSD       -> EUR/USD, no product-type mapping
GBPUSD       -> GBP/USD, no product-type mapping
XAUUSD       -> XAU/USD, no product-type mapping
US30.cash    -> US30/USD, explicit CFD mapping
```

Docker runtime inspection confirmed that EURUSD has a canonical symbol mapping,
but no explicit EURUSD product-type environment variable. The current runtime
market is therefore `EUR/USD / CTRADER / SPOT` through the fallback.

## Affected Boundaries

### Market Data

Primary implementation boundary:

- `market-data/.../ctrader/CTraderMarketData.java`
- `market-data/.../ctrader/CTraderOpenApiClient.java`
- `market-data/.../ctrader/JsonWebSocketCTraderOpenApiClient.java`
- `market-data/.../ctrader/authentication/CTraderMarketDataProperties.java`
- `market-data/src/main/resources/application.properties`
- `docker-compose.yml`

`Market` persists `productType` and uses provider/symbol/product type as a
unique identity. `MarketSynchronizationImpl` merges rows by that three-part
identity. A product-type change can therefore create a new identity rather than
updating an existing row.

### Trading Core

`MarketResponse.productType` deserializes the canonical enum and is consumed by:

- product-aware market lookup;
- PAPER settlement compatibility;
- challenge instrument compatibility;
- Risk handoff provenance;
- execution validation paths.

Trading Core does not currently classify cTrader instruments itself and must not
become the classification owner.

### Market Intelligence

Market Intelligence receives the provider and product type from canonical market
resolution. Manual TradePlan creation rejects missing or ambiguous identity and
propagates the resolved values into `ExecutionParameters`.

Relevant boundary:

```text
ManualTradePlanController
  -> canonical market lookup
  -> ManualTradePlanningRequest(provider, productType)
  -> TradePlan.ExecutionParameters
```

Existing historical TradePlans retain their persisted product identity. The
Story must not silently rewrite those records.

### Broker Service

The cTrader execution adapter does not consume Trading OS product type or
settlement model. It resolves the provider symbol by instrument name, obtains
provider symbol ID and execution constraints, converts quantity to cTrader
centi-units, and constructs provider payloads.

No Broker Service order protocol change is indicated by this Story. Read-only
provider metadata may be extended only if required to establish classification
evidence and only behind the existing adapter boundary.

### Risk and Settlement

The Risk Domain does not currently select rules based on `InstrumentProductType`.
Product identity is carried through the Risk handoff and provenance, while
financial calculations use the assembled account, portfolio, market, and rule
snapshots.

`PaperSettlementService` does use canonical market product identity before any
financial mutation. A CFD classification would therefore be rejected under the
current policy. This is expected and must remain fail-closed.

`PaperDerivativeAccountValuationService` currently supports only
`CASH + LINEAR_PERPETUAL`; it does not support CFD valuation.

## Runtime and Historical Evidence

The current cTrader integration exposes only limited symbol metadata to Market
Data: provider symbol ID, name, price precision, and volume constraints. The
Broker Service read-only contract additionally exposes provider execution facts
such as lot size and protection-distance constraints.

The existing evidence is insufficient to prove that every unconfigured cTrader
symbol is economically `SPOT`. It is also insufficient to authorize a generic
provider-wide `CFD` rule.

The implementation therefore needs an explicit reviewed classification source
for each supported provider symbol. The classification inventory must cover all
fallback-derived cTrader markets, including at least EURUSD, GBPUSD, and XAUUSD.

## Current Gaps

- Missing cTrader product mapping defaults to `SPOT`.
- Market Data does not preserve a classification-evidence provenance field.
- `InstrumentProductType` has no `UNKNOWN` value.
- Existing persistence and public contracts assume a non-null product type.
- CFD has no compatible settlement model.
- CFD-specific margin, leverage, funding, valuation, and challenge semantics are
  not implemented.
- Existing cTrader market rows may already be persisted as `SPOT`.
- Existing TradePlans may contain the fallback-derived `SPOT` identity.

## Implementation Options

### Option A - Explicit configuration and fail-closed ingestion

Add a required product-type mapping for every supported cTrader provider symbol.
Reject or omit any symbol without a valid mapping. This is the smallest change
compatible with the current enum and persistence contracts.

### Option B - Add `InstrumentProductType.UNKNOWN`

Introduce an explicit unknown market identity and propagate it through
persistence, APIs, compatibility, TradePlans, and challenges. This is more
expressive but broadens the Story into a domain contract and migration change.

### Recommendation

Use Option A for this Story unless the Implementation Plan demonstrates that the
existing domain can safely introduce `UNKNOWN` without expanding scope. Keep
the classification mapping instrument-specific and review all fallback-derived
symbols together.

The implementation plan must still determine, using approved evidence, whether
each symbol is `CFD`, `SPOT`, or rejected. It must not convert every cTrader
symbol to `CFD` by provider rule.

## Migration Risks

Before changing persisted identity, the implementation must inventory:

- provider symbol;
- provider symbol ID;
- current Market row and product type;
- affected TradePlans;
- opportunities, observations, and subscriptions;
- references that use market ID;
- rollback and duplicate-identity behavior.

Changing a persisted market from `SPOT` to `CFD` can create a new market identity
because uniqueness is provider + canonical symbol + product type. Existing
historical references must not be orphaned or silently rebound.

No database migration can be safely specified until this inventory is complete.

## Validation Plan

The Implementation Plan should require:

- unit tests for explicit SPOT, explicit CFD, missing, blank, invalid, and
  ambiguous cTrader product mappings;
- complete fallback inventory coverage, not only EURUSD;
- Market Data synchronization and persistence tests;
- Trading Core MarketResponse and product-aware lookup tests;
- Market Intelligence manual/opportunity propagation tests;
- historical TradePlan identity preservation tests;
- settlement compatibility regression tests;
- challenge product compatibility regression tests;
- cTrader provider symbol ID and quantity conversion regression tests;
- zero broker mutations and zero position mutations;
- focused affected-module suites and `git diff --check`.

## Scope Conclusion

The repository confirms a real implementation defect: absent cTrader product
classification is treated as `SPOT`. The defect is global to the fallback
behavior and must not be fixed through a EURUSD-only mapping.

The repository does not yet justify changing all affected symbols to `CFD`
without an explicit per-symbol evidence inventory. The safest implementation
boundary is explicit instrument mapping plus fail-closed ingestion, while CFD
settlement and valuation remain separate work.

## Approval Gate

Repository Analysis is complete and awaits explicit human approval before an
Implementation Plan may be produced.

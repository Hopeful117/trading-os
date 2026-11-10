# Repository Analysis - Story 0224

## Scope

This analysis covers only the cTrader EUR/USD financial-contract resolution
and Trading Core synchronization persistence boundary. Gateway, Eureka, JWT,
quantity conversion, risk-policy and provider-mutation investigations are
outside this analysis because they were already validated before Story 0224.

## Current Runtime Evidence

The authenticated application path is operational:

- cTrader connection status returns `200` and reports `CONNECTED` for external
  account `49411967`.
- Authenticated EURUSD validation returns `200`.
- Broker snapshot acquisition reaches Broker Service and returns a fresh
  `INCOMPLETE` snapshot.
- Trading Core fails during persistence with
  `financial contract market is unavailable`.
- No trade, risk evaluation, approval, intent or broker mutation was created.

The Market Data API currently returns two rows for `CTRADER / EUR/USD`:

| Market ID | Provider symbol | Product | Quantity unit | Multiplier | Source ID | Source version |
|---|---|---|---|---|---|---|
| `82852969-d309-481f-b6b8-078ff29d81e3` | `1` | `CFD` | missing | missing | missing | missing |
| `ba1576b7-7bd6-4260-a309-7e5a156cbce8` | `1` | `SPOT` | missing | missing | missing | missing |

The active Market Data container has the EURUSD canonical mapping and CFD
product mapping, but no configured contract multiplier or contract source
version. The reviewed contract-reference block in
`market-data/src/main/resources/application.properties` remains commented out.

## Resolution Trace

### Failing method

`BrokerSynchronizationServiceImpl.applyFinancialContractIfRequired(...)`

Location:
`trading-core/src/main/java/com/hope/trading/trading_core/broker/service/BrokerSynchronizationServiceImpl.java:406`

The method is called from `replaceOpenPositions(...)` during
`persistSynchronization(...)`, after Broker Service snapshot acquisition and
before `accountRepository.save(account)`.

### Requested market identity

The broker position supplies the instrument string `EUR/USD` to:

```java
financialContracts.resolveIfCfd(instrument)
```

The provider symbol `EURUSD` and provider symbol ID `1` are not passed to the
Trading Core resolver. They are normalized upstream by Market Data.

### Lookup key

`InstrumentFinancialContractResolver.uniqueMarket(...)` calls
`MarketDataClient.findAll()` and filters only:

```java
instrument.equalsIgnoreCase(market.getSymbol())
```

It does not use provider, provider symbol, market ID, or product type as part
of the lookup key. A second matching row is rejected as ambiguous.

### Expected identity

The active cTrader market must resolve as:

```text
provider: CTRADER
canonical instrument: EUR/USD
product: CFD
quantity unit: UNDERLYING_UNITS
contract multiplier: authoritative configured value, verified as 1 by the existing contract tests/runtime context
```

The historical Trade Plan `0cc15278-53a3-3fc4-8b52-54e419df369b`, version 2,
is a separate `SPOT` identity and must remain unchanged.

### Authoritative source

Market Data owns the canonical market and contract projection. The cTrader
adapter constructs it in `CTraderMarketData.toMarket(...)` from:

- canonical symbol mapping;
- explicit product mapping or an accepted `ContractReference`;
- accepted contract-reference quantity unit, multiplier, source ID, source
  version and effective time.

Trading Core consumes the provider-neutral `MarketResponse` through
`MarketDataClient` and maps it with `InstrumentFinancialContractMapper`.

### Actual failure

The current catalogue has an EUR/USD CFD row, but its required contract fields
are null. It also contains an EUR/USD SPOT row. Therefore the current resolver
cannot safely produce one complete CFD contract:

1. Symbol-only resolution sees multiple `EUR/USD` rows and is ambiguous; or
   when only the incomplete CFD row is visible, the mapper rejects missing
   financial metadata.
2. `InstrumentFinancialContractMapper.fromMarket(...)` requires contract
   multiplier, source ID, source version, observation time, quantity unit and
   constraints.
3. Synchronization aborts inside the transaction before the account projection
   is successfully committed.

## Root Cause

The defect is not a cTrader transport failure. It is a canonical market-data
configuration and resolution-boundary defect:

- the accepted EUR/USD CFD contract reference is not configured, so the Market
  Data projection has no financial contract metadata; and
- Trading Core resolves a CFD position by canonical symbol alone, so a
  historical/parallel EUR/USD SPOT row can make the identity ambiguous.

The first failing boundary is Trading Core financial-contract resolution during
open-position reconciliation. The missing source metadata originates in the
Market Data cTrader contract configuration.

## Existing Safety Semantics

The current implementation already provides the required fail-closed behavior:

- `InstrumentFinancialContractMapper` rejects missing contract metadata,
  non-CFD product types, unsupported quantity units and missing constraints.
- `InstrumentFinancialContractResolver` rejects no-match and duplicate symbol
  identities.
- Broker snapshot completeness remains independent from contract resolution.
- Risk consumers require complete admissible broker facts and must not treat
  missing margin or equity as zero.

These semantics must be preserved. The implementation must not add a SPOT
fallback or force an incomplete snapshot to `COMPLETE`.

## Minimal Implementation Direction

Subject to Implementation Plan approval:

1. Configure the reviewed canonical EUR/USD CFD contract reference through the
   existing Market Data configuration/source-of-truth path, including the
   authoritative multiplier, source identity/version, effective time and
   `UNDERLYING_UNITS` quantity unit. Do not fabricate values; use the existing
   verified contract definition and provenance conventions.
2. Correct the Trading Core resolver so `resolveIfCfd(...)` selects the unique
   canonical CFD market rather than conflating a separate SPOT row, while
   retaining deterministic rejection for multiple CFD candidates or incomplete
   metadata.
3. Add only focused tests at the Market Data mapping/configuration and Trading
   Core resolver/synchronization boundaries.
4. Build and redeploy only Market Data and Trading Core as necessary, preserve
   database volumes, then run one authenticated read-only synchronization.

The exact implementation choice between configuration-only correction and a
resolver correction must be confirmed against the current market catalogue
and existing tests in the approved Implementation Plan. No database deletion,
historical plan migration or risk-policy change is justified.

## Validation Plan

- Verify the Market Data API exposes exactly one usable `CTRADER / EUR/USD /
  CFD` financial contract with complete metadata.
- Verify the separate historical SPOT row remains unchanged and is not selected
  for the cTrader CFD synchronization.
- Run focused Market Data cTrader/market-contract tests.
- Run focused Trading Core mapper/resolver/synchronization tests.
- Run `git diff --check`.
- Execute one authenticated read-only synchronization through the Gateway.
- Record contract identity/version, snapshot identity, completeness, freshness,
  persistence result and unavailable financial fields.
- Confirm no risk evaluation or provider mutation occurs.

## Risks and Open Decisions

- The authoritative contract source ID and source version must come from the
  existing reviewed configuration convention; they must not be invented from
  the runtime row.
- The existing duplicate SPOT row must not be deleted or relabeled as part of
  this Story.
- If the active snapshot contains positions whose instruments lack a valid
  contract, persistence must retain the existing fail-closed behavior rather
  than silently persisting an economically uninterpretable position.

## Approval State

This Repository Analysis is complete and awaits explicit human approval before
an Implementation Plan or code changes are produced.

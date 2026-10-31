# Story 0180 - Repository Analysis

## Workflow State

- Story `0180` was explicitly approved by the human engineer for Repository
  Analysis.
- This artifact is the result of the Repository Analysis stage only.
- Implementation Planning is not approved by this artifact.

## Baseline

- Repository: `trading-os`
- Branch: `main`
- HEAD: `138698cd10749c3ba393ae001204c1df37326a90`
- The worktree contains the untracked Story 0180 directory created before this
  analysis. No implementation files were changed.
- `git diff --check` passes.
- DevLog context was requested for Story 0180 but could not be retrieved because
  the DevLog service returned `Authentication is required`. The analysis
  therefore relies on the current repository and accepted ADRs.
- No workspace `TOOLS.md` was found. No Obsidian vault note was required for this
  repository-local analysis.

## Story Understanding

Story 0180 requests a read-only adapter that assembles the complete facts needed
to value one persisted CFD exposure and delegates arithmetic to the existing
pure `CfdFinancialCalculator`.

The Story specifically requires:

- persisted provider-neutral CFD contract and exposure reconstruction;
- explicit timestamped mark price and mark policy;
- explicit quote-to-reporting currency conversion when required;
- fail-closed behavior for missing, stale, invalid, incomplete or contradictory
  facts;
- explicit PAPER versus LIVE authority behavior;
- no Trade, Account, settlement, execution or valuation-storage mutation;
- preservation of existing SPOT and `LINEAR_PERPETUAL` behavior.

The Story explicitly excludes settlement compatibility changes, PAPER CFD entry
or exit, LIVE reconciliation, Risk Domain changes, public APIs, database
migrations, provider mutation and automatic trade execution.

## Business Ownership

The affected capability is Trading Core CFD valuation orchestration.

- Market Data owns canonical current marks, FX facts, freshness evidence and
  provider-specific normalization.
- Trading Core owns the persisted CFD exposure, account reporting currency,
  execution-mode authority selection and read-only orchestration.
- The shared financial domain owns deterministic CFD arithmetic and explicit
  currency conversion validation.
- Risk Domain remains provider-neutral and deterministic. It is a regression
  consumer, not an implementation owner for this Story.
- Broker Service remains the authority for current external LIVE positions and
  balances, but the Story does not implement LIVE reconciliation.

No ownership conflict was found in the accepted ADRs. The current implementation
does contain a boundary risk described below because Market Data valuation
acquisition and valuation persistence are currently coupled.

## Governing Architecture

### ADR-082

ADR-082 requires Trading Core to assemble complete provider-neutral facts before
deterministic risk or financial interpretation. Its formulas are:

```text
grossPnlQuote = signedQuantity * (markPrice - entryPrice) * contractMultiplier
```

It requires explicit quote-to-account conversion, valuation provenance and
fail-closed handling for incomplete facts. It also states that dynamic marks
must not mutate persisted position identity on every tick and that PAPER uses
Trading Core as the authority for local state while Market Data supplies marks
and conversions.

ADR-082 explicitly keeps `CASH + CFD` and `INVENTORY + CFD` incompatible until a
separate approved compatibility change.

### ADR-078 and execution mode

`ExecutionMode` describes the Trading OS execution boundary, not the provider's
economic environment. `PAPER` must remain entirely local to Trading OS. `LIVE`
does not make a local Trade projection authoritative for current external
position state.

### ADR-081, ADR-051, ADR-043, ADR-042 and ADR-028

These preserve provider-neutral product identity, settlement compatibility,
mode-specific account facts, position authority and deterministic Risk
interpretation. No provider payload or cTrader type may cross the financial
domain boundary.

## Existing Implementation

### Existing CFD contract and exposure

- `InstrumentFinancialContract` is an immutable provider-neutral value object
  containing product type, currencies, quantity unit, multiplier, precision,
  quantity constraints and provenance.
- `InstrumentFinancialContractResolver` and
  `InstrumentFinancialContractMapper` already resolve and map Market Data
  contract facts.
- `Trade.applyFinancialContract(...)` persists product type, quote currency,
  contract multiplier and related contract snapshot fields.
- Story 0179 established the persisted provider-neutral CFD projection and
  restart reconstruction boundary.
- `BrokerSynchronizationServiceImpl` applies a CFD contract during the existing
  provider-neutral synchronization path; this path must not be expanded into
  PAPER broker reconciliation.

Relevant files:

- `trading-core/src/main/java/com/hope/trading/trading_core/model/Trade.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/shared/domain/model/InstrumentFinancialContract.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/market_data/service/InstrumentFinancialContractResolver.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/broker/service/BrokerSynchronizationServiceImpl.java`

### Existing deterministic calculator

`CfdFinancialCalculator` already provides pure methods for CFD notional, gross
P&L, stop-loss risk and reporting-currency conversion. It validates CFD product
type, underlying-unit quantity, contract constraints and positive prices.

`CurrencyConversionFact` is explicitly directed and requires a positive rate and
provenance. The calculator refuses a missing or wrongly directed cross-currency
fact and treats same-currency conversion as identity.

Relevant files:

- `trading-core/src/main/java/com/hope/trading/trading_core/shared/domain/financial/CfdFinancialCalculator.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/shared/domain/financial/CurrencyConversionFact.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/shared/domain/financial/CfdFinancialCalculatorTest.java`

### Existing Market Data valuation boundary

`MarketValuationPort` exposes provider-neutral instruments, assets, price-use
policy, freshness requirements, snapshots, facts, source provenance and
acquisition outcomes. Supported price-use values include `LAST`,
`CONSERVATIVE_SELL` and `CONSERVATIVE_BUY`.

`MarketValuationClient`:

- resolves instruments through the Market Data catalogue;
- requests refreshes for required markets and conversion markets;
- requests a valuation snapshot through the internal Market Data API;
- preserves the transport payload and acquisition outcomes in the returned
  provider-neutral snapshot.

Relevant files:

- `trading-core/src/main/java/com/hope/trading/trading_core/market_data/valuation/MarketValuationPort.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/client/MarketValuationClient.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/port/MarketValuationPort.java`

### Existing Market Data persistence path

`InternalValuationController` exposes
`POST /internal/v1/valuation-snapshots/batch`. `ValuationSnapshotService.create`
loads observations, validates freshness and conversions, creates a
`ValuationSnapshot`, adds facts and calls `snapshotRepository.saveAndFlush(...)`
on every request.

Relevant files:

- `market-data/src/main/java/com/hope/trading/market_data/controller/InternalValuationController.java`
- `market-data/src/main/java/com/hope/trading/market_data/service/ValuationSnapshotService.java`
- `market-data/src/main/java/com/hope/trading/market_data/repository/ValuationSnapshotRepository.java`

This is the main implementation boundary to resolve: Story 0180 requires a
read-only valuation operation and forbids valuation-storage mutation, while the
current `MarketValuationClient` path uses a persistence-backed snapshot API.

### Existing account valuation services

`PaperDerivativeAccountValuationService` and
`PositionValuationService` are existing valuation-related services, but they are
not a drop-in implementation for Story 0180. The former mutates account state
and is tied to the existing PAPER derivative accounting path; neither service
provides the requested persisted CFD contract plus explicit provenance result.

The existing `SettlementCompatibilityPolicy` continues to reject `CASH + CFD`
and `INVENTORY + CFD`, as required by the Story.

## Dependencies and Data Flow

The intended read-side flow is:

```text
Trade CFD projection + BrokerAccount mode/base currency
    -> Trading Core valuation orchestration
    -> provider-neutral Market Data mark and FX facts
    -> immutable valuation context
    -> CfdFinancialCalculator
    -> immutable gross PnL result or fail-closed unavailable outcome
```

Relevant dependencies are:

- Trading Core persistence for `Trade` and `BrokerAccount`;
- Market Data catalogue, observations and provider-neutral valuation facts;
- synchronous internal HTTP/Feign communication through the existing Market Data
  client boundary;
- `CfdFinancialCalculator`, `CurrencyConversionFact` and
  `InstrumentFinancialContract` in Trading Core;
- existing JPA valuation snapshot persistence, which is a constraint rather
  than an allowed dependency for the new read-only path.

No new public API, Gateway route, Broker Service write operation or event flow is
required by the Story.

## Tests and Current Coverage

Existing relevant tests include:

- `CfdFinancialCalculatorTest`: long/short P&L, multiplier, exact conversion,
  directed conversion, invalid contracts and invalid facts;
- `MarketValuationClientTest`: Trading Core valuation transport boundary;
- `ValuationSnapshotServiceTest`: Market Data valuation facts, freshness and
  conversion behavior;
- `RequiredMarginCurrencyNormalizerTest` and
  `TradePlanRiskEvaluationServiceTest`: current Market Valuation snapshot
  contract usage in Risk-related orchestration;
- `MarketFinancialContractMigrationTest`, contract mapper tests and cTrader
  market tests: provider-neutral CFD contract metadata and classification;
- `PaperDerivativeAccountValuationServiceTest` and
  `PositionValuationServiceTest`: existing non-Phase-4 valuation behavior.

Coverage appears missing for:

- a complete persisted CFD exposure reconstructed and valued without mutation;
- an immutable result carrying mark policy, timestamps, conversion and
  provenance together;
- PAPER zero-Broker-fact authority behavior;
- LIVE fail-closed behavior when only a local persisted exposure is available;
- read-only Market Data acquisition that does not save valuation snapshots;
- cross-module no-mutation and restart reconstruction integration coverage.

Detailed test sequencing belongs to the Implementation Plan.

## Risks and Open Implementation Questions

### High: current valuation endpoint mutates storage

The current client calls a Market Data endpoint whose service saves a valuation
snapshot. Reusing it directly violates the Story's no-valuation-storage-mutation
criterion. The Implementation Plan must choose a minimal read-only boundary or
another approved reuse path without weakening existing snapshot behavior.

### High: conversion policy is broader than the Story's explicit direction

`ValuationSnapshotService` supports inverse and two-hop conversion discovery,
while `CurrencyConversionFact` requires an explicit directed fact. The plan must
define how the read-only adapter consumes the existing provider-neutral output
without silently introducing an unapproved inversion or chained conversion.

### Medium: freshness authority spans service boundaries

Market Data evaluates observation age against the requested valuation timestamp,
while Trading Core must retain source timestamps and provenance in the final
result. The adapter must not replace missing provider timestamps with local
capture time or treat a complete transport response as fresh without checking
fact status.

### Medium: LIVE authority must not be inferred from persisted Trade data

The repository has both persisted Trading Core projections and provider-backed
LIVE synchronization. A local Trade contract snapshot is sufficient to interpret
the instrument, but not sufficient to assert current LIVE position authority.
The implementation must fail closed rather than accidentally valuing stale local
LIVE exposure as current provider state.

### Medium: preserving existing consumers

`MarketValuationPort` is used by existing Risk and dashboard-related paths. Any
contract change must preserve SPOT and `LINEAR_PERPETUAL` callers and avoid
adding CFD-specific branching to Risk Domain code.

## Recommended Readiness

The Story is ready for Implementation Planning. The repository contains the
required CFD contract, persistence projection, deterministic calculator and
provider-neutral valuation concepts. Planning must resolve the two material
design boundaries before implementation begins:

1. how to obtain Market Data facts without valuation-storage mutation;
2. how to preserve explicit directed FX provenance while reusing the existing
   valuation infrastructure.

No implementation code was changed during this analysis.

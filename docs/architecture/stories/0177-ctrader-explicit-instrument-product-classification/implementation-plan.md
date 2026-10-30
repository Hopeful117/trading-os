# Implementation Plan - Story 0177

## Decision Boundary

The approved Repository Analysis identifies one concrete defect: cTrader
product identity defaults to `SPOT` when the provider symbol has no explicit
product-type mapping.

This plan implements the smallest architecture-consistent correction:

```text
explicit provider-symbol classification
        +
fail-closed catalogue ingestion
```

The plan does not introduce `InstrumentProductType.UNKNOWN` unless an
implementation constraint proves that rejection/omission cannot preserve the
current contracts safely. It does not implement CFD settlement, change account
settlement models, or rewrite historical TradePlans.

Implementation is not authorized until this plan is explicitly approved.

## Governing Decisions

- ADR-081 is the primary decision for cTrader classification.
- ADR-051 remains authoritative for Account/Product compatibility.
- Market Data owns canonical product identity.
- Trading Core owns Account `SettlementModel` and financial orchestration.
- Broker Service remains the cTrader provider boundary.
- Missing or ambiguous product identity fails closed.
- The scope covers every cTrader symbol currently receiving `SPOT` from the
  fallback, not only `EURUSD`.
- `INVENTORY + CFD` and `CASH + CFD` remain rejected by the current policy.

## Implementation Sequence

### 1. Build the read-only classification inventory

Before changing runtime behavior, inspect the current Market Data catalogue and
database without mutation.

Record for every cTrader market:

- persisted market ID;
- provider symbol and canonical symbol;
- provider symbol ID;
- current product type;
- whether the current type came from explicit configuration or fallback;
- related TradePlans, Opportunities, Observations, and active subscriptions;
- available provider metadata and classification evidence.

The inventory must include at least:

```text
EURUSD
GBPUSD
XAUUSD
US30.cash
```

The inventory is a required implementation artifact/report section, not a new
long-lived ad-hoc repository document. Any unresolved symbol remains rejected
or omitted until its classification is explicitly decided.

### 2. Make cTrader classification explicit

Update the Market Data cTrader configuration and binding path so that every
supported provider symbol has an explicit product type.

Expected configuration shape, with values filled only from the approved
classification inventory:

```properties
ctrader.market-data.symbol-product-types.EURUSD=<reviewed-product-type>
ctrader.market-data.symbol-product-types.GBPUSD=<reviewed-product-type>
ctrader.market-data.symbol-product-types.XAUUSD=<reviewed-product-type>
ctrader.market-data.symbol-product-types[US30.cash]=CFD
```

The exact values for each symbol must be taken from the approved classification
inventory. The implementation must not assign `CFD` solely because the provider
is cTrader.

The application and deployment configuration must have the same semantics:

- no product-type mapping means unsupported/unclassified;
- blank mapping means unsupported/unclassified;
- invalid enum value is a configuration failure;
- no implicit `SPOT` default remains.

### 3. Fail closed in `CTraderMarketData`

Change `CTraderMarketData.productType(...)` so it either returns a validated
explicit `InstrumentProductType` or rejects the provider symbol from the
canonical catalogue.

The failure must:

- identify the provider symbol;
- distinguish missing, blank, invalid, and ambiguous classification where the
  existing error model permits;
- avoid credentials and raw sensitive provider payloads;
- prevent an unclassified symbol from being persisted or exposed as `SPOT`.

Preserve the existing explicit `US30.cash -> CFD` behavior and canonical symbol
mapping behavior.

### 4. Preserve service boundaries and propagation

Do not add cTrader classification logic to Trading Core or Market Intelligence.

Verify and preserve this path:

```text
Market Data Market.productType
  -> MarketResponse.productType
  -> Trading Core market lookup
  -> Market Intelligence canonical identity
  -> TradePlan execution.instrumentProductType
```

Update contracts only if the fail-closed behavior exposes an existing null or
empty-state assumption. Do not introduce provider-specific fields into public
Trading Core or Market Intelligence contracts.

### 5. Define persisted-market reconciliation

After the inventory is complete, choose one behavior per existing fallback row:

- retain the existing row only when the explicit classification is confirmed
  unchanged;
- update/reconcile the row when the persisted identity can safely be changed;
- create a new product-scoped market identity when required by the existing
  uniqueness model;
- preserve historical references to the old market identity where rebinding
  would change historical meaning;
- quarantine or leave unsupported rows unavailable when evidence is missing.

The implementation must not blindly run a bulk `SPOT -> CFD` SQL update.

If safe reconciliation cannot be implemented without orphaning or silently
rebinding historical references, stop and report the required migration decision
instead of guessing.

No Account settlement migration is part of this step.

### 6. Preserve historical TradePlan provenance

Existing TradePlans must retain the product type recorded at their creation.

New TradePlans must receive only the current canonical market classification.
Existing TradePlan reads, Risk handoffs, and execution validation must continue
to use their persisted identity without silently looking up and replacing it.

### 7. Validate downstream fail-closed behavior

Confirm that a newly classified `CFD` market reaches the existing compatibility
guard and is rejected for unsupported PAPER settlement before financial
mutation.

Do not weaken the compatibility matrix to make cTrader CFD plans pass. Do not
map CFD to `CASH` as part of this Story.

## Expected Affected Files

Likely implementation areas:

- `market-data/src/main/java/com/hope/trading/market_data/ctrader/CTraderMarketData.java`
- `market-data/src/main/java/com/hope/trading/market_data/ctrader/authentication/CTraderMarketDataProperties.java`
- `market-data/src/main/resources/application.properties`
- `docker-compose.yml`
- `market-data/src/test/java/com/hope/trading/market_data/ctrader/CTraderMarketDataTest.java`
- market synchronization/persistence tests under `market-data/src/test`
- Trading Core market contract or lookup tests only if current behavior needs
  regression coverage
- Market Intelligence TradePlan propagation tests only if current behavior needs
  regression coverage

Potential migration files must not be added until the inventory establishes that
a schema or reference migration is necessary. The current `product_type` column
and provider/symbol/product uniqueness constraint are expected to remain.

## API and Contract Impact

No public API redesign is planned.

Expected behavior change:

- previously unconfigured cTrader symbols appeared as canonical `SPOT` markets;
- after implementation, they are explicitly classified or absent/rejected.

The existing Market Data, Trading Core, and Market Intelligence product-type
fields remain provider-neutral enums.

If a new error response is required for catalogue ingestion, keep it internal to
the Market Data synchronization/diagnostic boundary unless an existing public
contract already exposes provider classification errors.

## Database and Migration Plan

Persistence must be treated as a controlled reconciliation problem, not as a
configuration-only change.

Before any migration:

1. read and record all cTrader markets currently persisted as `SPOT`;
2. resolve provider symbols and IDs;
3. identify dependent records and references;
4. determine whether each row is confirmed SPOT, confirmed CFD, or unresolved;
5. define preservation and rollback behavior;
6. add a focused migration test if a schema/data migration is required.

The implementation may use explicit configuration to correct newly synchronized
markets without rewriting historical records. Any persisted identity migration
requires separate evidence in the Implementation Report and must preserve
historical TradePlan identity.

## Tests

### Market Data

- explicit `SPOT` mapping returns `SPOT`;
- explicit `CFD` mapping returns `CFD`;
- missing mapping rejects or omits the symbol;
- blank mapping rejects or omits the symbol;
- invalid mapping fails with an actionable configuration error;
- all current fallback symbols are covered, not only EURUSD;
- `US30.cash` remains explicitly `CFD`;
- unmapped symbols do not enter persisted synchronization output;
- canonical symbol mappings remain unchanged;
- provider symbol ID and volume constraints remain unchanged.

### Trading Core

- `MarketResponse` deserializes explicit `CFD`;
- product-aware market lookup remains scoped correctly;
- `INVENTORY + CFD` remains rejected before PAPER mutation;
- challenge product compatibility receives the canonical product type;
- historical TradePlan product identity is not overwritten.

### Market Intelligence

- manual TradePlan propagation preserves explicit provider/product identity;
- opportunity TradePlan propagation preserves explicit provider/product identity;
- missing or ambiguous canonical identity remains rejected;
- persisted TradePlan identity remains stable on read.

### Broker Service

- no product-type field is added to cTrader order payloads;
- existing provider symbol resolution and quantity conversion remain green;
- safety-disabled execution remains uncallable during validation.

### Migration/Runtime

- read-only inventory query or fixture covers every existing fallback market;
- any migration/reconciliation test preserves historical references;
- runtime validation performs zero broker and position mutations.

## Validation Commands

Run targeted tests first, then broader affected suites:

```text
mvn -f market-data/pom.xml test
mvn -f trading-core/pom.xml test
mvn -f market-intelligence/pom.xml test
mvn -f broker-service/pom.xml test
git diff --check
```

Run broader tests when the implementation changes shared contracts or
persistence. Do not activate cTrader execution or submit any provider request.

Required runtime safety facts:

```text
CTRADER_SAFETY_EXECUTION_ENABLED=false
ORDER_SUBMITTED=NO
BROKER_MUTATIONS=0
POSITION_MUTATIONS=0
TRADEPLAN_ACCEPTED=NO
```

## Risks and Mitigations

### Risk: Incorrect bulk CFD classification

Mitigation: classify each provider symbol from the inventory and reviewed
evidence; never infer CFD from provider name alone.

### Risk: Existing references are rebound to a new market identity

Mitigation: inventory all dependent records first and preserve historical
TradePlan identity; stop if safe reconciliation is not established.

### Risk: CFD reaches unsupported PAPER settlement

Mitigation: preserve ADR-051 fail-closed compatibility and test rejection before
financial mutation.

### Risk: Runtime configuration differs from application defaults

Mitigation: update the canonical properties and Docker environment mapping with
the same explicit-symbol semantics and test both binding paths.

### Risk: Unclassified markets disappear unexpectedly

Mitigation: emit actionable diagnostics, report the complete rejected-symbol
inventory, and do not silently treat omission as successful support.

## Non-Changes

- No Account `SettlementModel` change.
- No CFD settlement or valuation implementation.
- No `InstrumentProductType.UNKNOWN` unless separately justified during
  implementation and approved through the current Story scope.
- No generic `CTRADER -> CFD` rule.
- No broker protocol or order payload change.
- No cTrader order, cancel, amend, close, or position mutation.
- No TradePlan acceptance or creation for validation.
- No historical TradePlan rewrite without an explicit migration decision.
- No Kraken behavior change.

## Plan Status

Implementation Plan is complete and awaits explicit human approval before code
changes or migration work may begin.

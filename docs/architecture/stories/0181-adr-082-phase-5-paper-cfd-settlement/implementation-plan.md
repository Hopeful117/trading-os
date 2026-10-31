# Implementation Plan - Story 0181

## Objective

Implement a dedicated PAPER CFD accounting capability for ADR-082 Phase 5 while
leaving the global settlement compatibility matrix closed and preserving the
existing SPOT and `LINEAR_PERPETUAL` paths.

## Design

### 1. Dedicated accounting component

Introduce the smallest application service appropriate to the current execution
architecture, expected to be named `PaperCfdSettlementService` unless source
inspection during implementation proves an existing naming boundary is better.

The service will accept the existing PAPER execution context (`ExecutionIntent`,
`ExecutionAttempt`, and `BrokerOrder`) plus repository dependencies. It will be
an internal accounting capability, not a public controller and not a change to
`SettlementCompatibilityPolicy`.

Responsibilities:

- require `ExecutionMode.PAPER` and reject LIVE;
- resolve exactly one complete provider-neutral CFD `MarketResponse` by symbol;
- construct and validate `InstrumentFinancialContract` from Market Data facts;
- create a signed persisted CFD `Trade` on entry;
- locate the exact target exposure on full exit;
- mutate only account cash/equity, trade state, and settlement provenance;
- execute within one local transaction.

`PaperSettlementService` will retain SPOT and `LINEAR_PERPETUAL` behavior. If a
small delegation seam is needed, it will delegate only when explicitly called
by a CFD-specific internal path; it will not make `CASH + CFD` compatible or
route ordinary execution through the closed compatibility gate.

### 2. CFD entry

For an entry fill:

- convert BUY to positive quantity and SELL to negative quantity;
- validate fill quantity and price through the canonical contract;
- persist all contract snapshot fields already supported by `Trade.applyFinancialContract`;
- persist the signed quantity, entry price, product type, and PAPER account;
- leave all `AccountBalance` rows unchanged;
- initialize/reuse the cash baseline without debiting notional;
- record entry settlement provenance using the existing unique
  `(broker_order_id, fill_id)` boundary.

Duplicate fills will return the already settled trade when durable provenance
exists. No new parallel idempotency store will be introduced.

### 3. Read-only mark-to-market equity

Extend the Phase 4 valuation boundary only where required to expose a reusable
explicit-price/conversion operation for a full exit. The regular MTM path will
reuse `CfdValuationService.value` unchanged for current marks.

Add a dedicated PAPER CFD equity projection service or method at the existing
valuation application boundary. It will:

- load open persisted CFD trades from the account;
- reconstruct each contract from persisted fields;
- call `CfdValuationService`/`ReadOnlyMarketValuationPort` for conservative marks;
- aggregate only available, complete reporting-currency PnL;
- calculate `equity = realized cash balance + unrealized PnL - supported unsettled known costs`;
- return unavailable/fail-closed when any required exposure fact is unavailable;
- update account valuation projection only at the explicit accounting projection
  boundary, never on every market tick or by changing contract identity.

Existing `PaperDerivativeAccountValuationService` remains restricted to
`LINEAR_PERPETUAL`.

### 4. Full exit

For an exit fill:

- require the target trade to be open and CFD;
- require the exit side to oppose the signed exposure;
- require exact absolute quantity equality; reject partial reduction and
  reversal;
- calculate quote realized PnL with `CfdFinancialCalculator` using the signed
  persisted quantity, persisted entry price, fill exit price, and multiplier;
- obtain explicit quote-to-reporting conversion facts when currencies differ;
- apply only supported deterministic costs;
- add realized PnL to `Account.realizedBalance`, recompute projected equity with
  remaining open CFD exposures, close the trade, and persist all state atomically;
- never mutate base-asset inventory.

The exit operation will record durable execution provenance before completion.
Replay will first consult provenance and then also reject an already closed
exposure. The existing unique fill constraint and transaction/optimistic-lock
behavior will be tested for duplicate and concurrent replay scenarios.

### 5. Persistence and schema

Reuse `V47` CFD contract fields, `Account.realizedBalance`, valuation metadata,
and `V39` execution provenance. No migration is expected initially.

If implementation proves that exit provenance cannot be represented safely by
the existing table, stop and report the exact missing invariant rather than
adding a speculative schema. Any additive migration would require explicit
review in the implementation report.

### 6. Compatibility and safety

Do not modify `SettlementCompatibilityPolicy`.

Add explicit tests that both `CASH + CFD` and `INVENTORY + CFD` remain rejected.
The dedicated component must reject LIVE and must not call Broker Service.
`CTRADER_SAFETY_EXECUTION_ENABLED` remains false; tests use simulated fills and
verify zero broker/provider mutations.

## Test Plan

### Dedicated CFD accounting

- positive LONG entry and negative SHORT entry;
- no inventory mutation and no full-notional cash debit;
- complete/incomplete contract handling;
- non-CFD and LIVE rejection;
- duplicate entry idempotence;
- profitable/adverse full LONG and SHORT exits;
- multiplier and explicit FX conversion;
- wrong direction, quantity, partial close, and reversal rejection;
- duplicate exit replay and rollback atomicity.

### MTM/equity

- EURUSD `+1000`, `1.10000 -> 1.10100` gives `+1 USD`;
- EURUSD `-1000`, `1.10000 -> 1.09900` gives `+1 USD`;
- adverse long and short losses;
- identity and cross-currency conversion;
- missing, stale, incomplete, invalid, and wrongly directed facts;
- provenance and timestamps;
- multiple open exposures where existing account loading supports it;
- persistence-context clear/reload restart reconstruction;
- closed exposures excluded from MTM.

### Regression

- `PaperSettlementExitTest`;
- `PaperCashDerivativeSettlementTest`;
- `SettlementCompatibilityPolicyTest`;
- `CfdFinancialCalculatorTest`;
- persisted CFD projection tests;
- `CfdValuationServiceTest` and `MarketValuationClientTest`;
- execution provenance/idempotency tests;
- existing `PaperDerivativeAccountValuationServiceTest` and relevant account
  equity tests.

## Validation

- compile and focused tests in `trading-core`;
- relevant Market Data tests if production Market Data code changes;
- `trading-core/./mvnw -q test`;
- `git diff --check`;
- review the complete diff and verify no compatibility, LIVE, Risk Domain, or
  provider execution changes.

## Stop Conditions

Stop before implementation if:

- the existing provenance table cannot provide exactly-once exit settlement;
- required FX/cost semantics cannot be represented without invention;
- the accounting component can only work by enabling `CASH + CFD` globally;
- restart reconstruction requires transient state;
- a migration or architectural boundary outside ADR-082 becomes necessary.

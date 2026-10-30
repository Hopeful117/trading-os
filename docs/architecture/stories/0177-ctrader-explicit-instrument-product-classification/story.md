# Story 0177 - cTrader Explicit Instrument Product Classification

## Metadata

**ID:** `0177`
**Title:** cTrader Explicit Instrument Product Classification
**Status:** Draft
**Related ADR:** `ADR-081`
**Related Stories:** `0167`, `0084`, `0088`

---

## Goal

Replace the unsafe cTrader product-identity fallback that interprets a missing
product-type mapping as `SPOT`.

Every supported cTrader provider symbol must receive an explicit,
instrument-specific, provider-neutral `InstrumentProductType` classification.
The implementation must cover the complete set of cTrader instruments currently
classified as `SPOT` solely because no product-type mapping exists, not only
`EURUSD`.

The system must fail closed when provider evidence and reviewed configuration do
not establish a safe classification.

---

## Context

Story 0167 added cTrader Market Data mappings for:

```text
EURUSD      -> EUR/USD
GBPUSD      -> GBP/USD
XAUUSD      -> XAU/USD
US30.cash   -> US30/USD
```

`US30.cash` has an explicit `CFD` product mapping. The other symbols currently
inherit `SPOT` because their product-type mapping is absent. That fallback is
not evidence of spot ownership or inventory settlement.

ADR-081 establishes that:

- cTrader is not itself a product type;
- classification is specific to the provider symbol;
- missing or ambiguous classification must fail closed;
- product identity must not infer account settlement semantics;
- unsupported CFD settlement remains rejected until separately approved.

---

## Problem

An unconfigured cTrader derivative can currently enter the canonical catalogue
as `SPOT`, then propagate as if it were an inventory-settled instrument through
Trading Core, Market Intelligence, TradePlan persistence, challenge checks, and
future settlement paths.

Correcting only EUR/USD would leave the same defect for every other cTrader
symbol relying on the fallback.

---

## Scope

- Inventory all cTrader market rows and runtime catalogue entries currently
  classified as `SPOT`.
- Identify the provider symbol and provider symbol ID for every inventory entry.
- Establish the available provider evidence and reviewed classification for each
  supported cTrader symbol.
- Add explicit product-type mappings or an equivalent reviewed classification
  source for every supported cTrader symbol.
- Classify confirmed cTrader CFD instruments as `CFD`, including all currently
  fallback-classified CFD instruments, not only EURUSD.
- Preserve explicit `SPOT` only where spot/inventory semantics are positively
  evidenced.
- Remove the cTrader default-to-`SPOT` behavior.
- Reject, omit, or otherwise fail closed for missing, invalid, or ambiguous
  product classification according to the approved implementation plan.
- Propagate the resulting product identity through Market Data, Trading Core,
  Market Intelligence, and TradePlan persistence without provider leakage.
- Define and execute the approved migration/reconciliation behavior for existing
  cTrader market rows and references, if required.
- Preserve historical TradePlan product provenance.
- Add regression tests for the complete fallback inventory and for explicit
  SPOT/CFD mappings.

---

## Out of Scope

- Automatically changing an Account `SettlementModel`.
- Implementing CFD settlement or cash-equity accounting.
- Implementing margin, leverage, funding, swap, liquidation, or mark-to-market
  behavior for CFD instruments.
- Reclassifying every cTrader instrument to `CFD` solely because its provider is
  cTrader.
- Introducing `InstrumentProductType.UNKNOWN` unless the approved
  implementation plan establishes that the existing domain contracts can be
  changed safely as part of this Story.
- Rewriting historical TradePlans without an explicit migration policy.
- Changing Broker Service order payloads or cTrader provider execution protocol.
- Accepting or creating a TradePlan for validation purposes.
- Submitting, cancelling, amending, or closing broker orders or positions.
- FTMO-specific challenge rules or provider coupling.

---

## Acceptance Criteria

- [ ] No supported cTrader symbol receives `SPOT` solely because its product
      mapping is absent.
- [ ] The implementation inventory includes every existing cTrader market
      currently classified as `SPOT` by fallback, not only `EURUSD`.
- [ ] The inventory includes at least `EURUSD`, `GBPUSD`, and `XAUUSD`, and
      verifies the already explicit `US30.cash` classification.
- [ ] Every supported cTrader provider symbol has an explicit, reviewed
      product-type classification or is rejected from the canonical catalogue.
- [ ] Confirmed CFD instruments are classified as `CFD` independently of their
      textual symbol name or provider-wide assumptions.
- [ ] `SPOT` is retained only for instruments with positive spot/inventory
      evidence.
- [ ] Missing, blank, invalid, and ambiguous product classification fails closed
      with an actionable diagnostic that does not expose credentials.
- [ ] Product identity remains owned by Market Data and remains provider-neutral
      across service boundaries.
- [ ] Product identity propagates consistently through Market Data, Trading
      Core, Market Intelligence, and persisted TradePlans.
- [ ] Existing TradePlans retain their historical product identity unless an
      explicitly approved migration policy says otherwise.
- [ ] `INVENTORY + CFD` remains rejected by the current compatibility policy.
- [ ] No Account settlement model is changed by this Story.
- [ ] No broker order or position mutation occurs during automated or runtime
      validation.
- [ ] `CTRADER_SAFETY_EXECUTION_ENABLED=false` remains the default.
- [ ] Focused Market Data, Trading Core, Market Intelligence, and applicable
      migration tests pass.
- [ ] `git diff --check` passes.

---

## Constraints

- Preserve ADR-081 and ADR-051.
- Preserve the separation between Account `SettlementModel` and Market Data
  `InstrumentProductType`.
- Do not infer product identity from cTrader provider, endpoint, account
  environment, execution mode, or account `isLive`.
- Do not use passing compatibility tests as evidence of economic correctness.
- Keep cTrader transport and provider DTOs inside the adapter boundary.
- Do not expose credentials or sensitive provider payloads in diagnostics.
- Preserve existing local and untracked worktree changes.
- Keep broker execution safety disabled during all validation.

---

## Relevant ADRs

- `ADR-028` - Deterministic Risk Engine Architecture
- `ADR-040` - Position Management Capability Boundary
- `ADR-042` - Position Authority and Close Semantics by Execution Mode
- `ADR-051` - Account Settlement Semantics and Instrument Product Identity
- `ADR-071` - FTMO Challenge Target and cTrader Broker Boundary
- `ADR-074` - cTrader Authoritative READ-ONLY Synchronization Boundary
- `ADR-077` - cTrader Market Data Transport Boundary
- `ADR-078` - PAPER/LIVE Execution Boundary
- `ADR-081` - cTrader Instrument Product Classification

---

## Relevant Modules

- `market-data`
- `trading-core`
- `market-intelligence`
- `broker-service` only where read-only provider evidence or contract tests are
  required

---

## Validation Expectations

Validation must cover:

- explicit EURUSD, GBPUSD, XAUUSD, and US30.cash classification;
- all persisted cTrader fallback-classified market rows;
- missing and ambiguous mapping rejection;
- Market Data catalogue synchronization;
- Trading Core market deserialization and product-aware lookup;
- Market Intelligence manual and opportunity TradePlan propagation;
- historical TradePlan identity preservation;
- settlement compatibility regression;
- challenge product compatibility regression;
- cTrader provider symbol ID and quantity conversion regression;
- zero broker mutations and zero position mutations;
- focused module suites, broader affected suites where justified, and
  `git diff --check`.

---

## Required Implementation Plan Questions

The Implementation Plan must answer before code changes begin:

- Which provider metadata is authoritative for each cTrader symbol?
- Which current fallback-classified symbols are confirmed CFD, confirmed SPOT,
  or unresolved?
- Does the current domain need `InstrumentProductType.UNKNOWN`, or is rejected
  catalogue ingestion sufficient?
- How are existing Market rows migrated or reconciled?
- How are existing TradePlans, Opportunities, Observations, and subscriptions
  affected?
- How is rollback handled if provider classification changes?
- Which future CFD settlement decision is required before any CFD PAPER path is
  enabled?

---

## Safety

```text
CTRADER_SAFETY_EXECUTION_ENABLED=false
ORDER_SUBMITTED=NO
BROKER_MUTATIONS=0
POSITION_MUTATIONS=0
TRADEPLAN_ACCEPTED=NO
```

---

## Workflow

This Story is currently `Draft`. Repository Analysis, human approval,
Implementation Planning, and implementation approval are required before code
changes or data migration.

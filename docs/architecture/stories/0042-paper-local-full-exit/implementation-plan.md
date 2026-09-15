# Implementation Plan - Story 0042

This plan implements only a local PAPER full exit. It does not redesign LIVE
close, add partial close, or introduce a universal Position aggregate.

## Step 1 - Exit Authorization Contract

Define the explicit `ENTRY | EXIT` meaning and a local target reference. Decide
whether to generalize `ExecutionIntent` with an optional target/purpose or add a
small exit-specific authorization that reuses common attempt/order/fill/audit
records.

**Invariants**

- EXIT has a target `Trade` and full target quantity.
- Side is derived from the target and cannot be trusted from the client.
- Exit does not require the entry TradePlan/T0 approval.
- Persistence remains idempotent and reload-safe.

**Learning mode:** `PAIR`

## Step 2 - Neutral Close Application Routing

Add the mode-neutral `ClosePosition` application boundary. Resolve
`Account.accountId`, canonical linked `BrokerAccount`, ownership, and execution
mode before choosing the mutation path. Preserve the existing LIVE
`PositionCloseService` path and route PAPER to the new local exit flow.

**Likely components**

- position close controller/application service
- Account and BrokerAccount repositories
- execution authorization/request DTOs
- frontend position service/page only if required by the existing contract

**Invariants**

- PAPER never reaches Broker Service close or reconciliation.
- LIVE behavior and provider reference semantics remain unchanged.
- No owner/provider lookup substitutes for the canonical relation.

**Learning mode:** `PAIR`

## Step 3 - Target Claim And Concurrency

Reload the target Trade inside the local transaction, require OPEN state, and
claim it against competing closes. Use the smallest durable mechanism that
prevents double settlement, such as an explicit execution target state/unique
lineage or optimistic locking. Do not rely on an in-memory lock.

**Invariants**

- Exactly one close can settle a target.
- A retry with the same idempotency key observes the original result.
- A different request receives a conflict/already-closed result.

**Learning mode:** `PAIR`

## Step 4 - Simulated Exit Fill

Reuse the existing simulation boundary and create an opposite-side order/fill.
For LONG use SELL at bid; for SHORT use BUY at ask. Preserve timestamp, status,
fee, and order/fill audit data. Make any required adapter change explicit rather
than encoding exit behavior as an opposite entry.

**Invariants**

- Valid executable-side quote is required.
- No `lastPrice` fallback or invented quote.
- Rejected exit produces no local financial mutation.
- PAPER reconciliation is not used as an external authority.

**Learning mode:** `PAIR`

## Step 5 - Atomic Local Settlement

Extend or split `PaperSettlementService` so an EXIT validates the target again,
applies the documented LONG/SHORT cash effects, computes realized PnL from
entry basis and fill price, sets exit fields and CLOSED status, and saves the
Account atomically.

**Invariants**

- Full quantity only; no residual or opposite Trade is created.
- Trade and Account changes commit together.
- Existing entry settlement behavior remains compatible.
- Equity treatment for realized PnL and fees is explicit and tested.

**Accounting contract**

- Keep raw per-asset `AccountBalance` amounts; do not introduce a scalar
  balance conversion or ledger redesign.
- Preserve entry accounting: BUY quote decreases by notional plus fee and base
  increases; SELL base decreases and quote increases by notional minus fee;
  persisted equity decreases by the fee only.
- For EXIT, LONG/SELL removes base and adds quote proceeds; SHORT/BUY adds base
  and removes quote cost.
- Store gross direction-aware PnL on `Trade`; subtract each fill fee exactly
  once from Account-level equity/cash effects.
- After a complete round trip, base holdings return to their pre-entry amount,
  while quote and persisted equity equal their initial values plus gross PnL
  less entry and exit fees.
- Current simulated fees are zero, but tests must pass fee-bearing fills at the
  settlement boundary so the no-double-charge rule is protected.

**Learning mode:** `PAIR`

## Step 6 - API And UI Safety

Evolve the close request/response toward the neutral product action while
preserving LIVE compatibility. PAPER requests must carry a local target
semantics, not `brokerPositionReference`. Present local CLOSED/pending/failure
states without Kraken-specific reconciliation language.

**Learning mode:** `DELEGATE` after Steps 1-5 are approved

## Step 7 - Validation

Run the focused Trading Core unit and integration matrix, including repository
reload, duplicate replay, concurrent close, LONG/SHORT accounting, and zero
Broker Service calls for PAPER. Run relevant frontend tests/build if UI changes
are included, plus `git diff --check`.

**Learning mode:** `PAIR` for accounting, authority, and concurrency failures;
`DELEGATE` for repetitive fixture repair.

## Files Likely To Change

- `trading-core` execution domain/application/persistence and PAPER settlement
- `trading-core` position-close API/application adapters
- database migration only if the chosen durable target/idempotency lineage
  requires a schema change
- focused Trading Core tests
- `trading-os-web` position model/service/page/tests only for the neutral action

These are likely areas, not authorization to expand the Story. Any new
cross-service contract or accounting architecture requires review before
implementation.

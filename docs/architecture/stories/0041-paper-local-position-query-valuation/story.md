# Story 0041 - PAPER Local Position Query and Valuation

## Metadata

**ID:** `0041`

**Status:** Approved

**Baseline:** `main` at `3e89646` (Story 0040 merged)

**Related ADRs:** ADR-042, ADR-043

## Goal

Make a persisted PAPER position observable through the normal product position
query while preserving Broker/provider authority for LIVE accounts.

## Context

Trading Core now persists PAPER entry fills and local OPEN `Trade` state. The
current positions endpoint and dashboard still obtain positions from Broker
Service, which is not authoritative for PAPER and may not exist at all.
ADR-042 explicitly requires mode-specific position query authority while
retaining `Trade` as the minimal first-milestone PAPER projection.

The frontend currently renders a close action for every position, sends the
position ID as `brokerPositionReference`, and displays a Kraken FIFO message.
Because PAPER close is a later story with different semantics, this Story must
make the mode visible enough to suppress or disable those LIVE-only controls
for PAPER. It must not implement PAPER close as a side effect of querying.

## Scope

- Resolve the financial Account and linked BrokerAccount before querying.
- Select position facts by `ExecutionMode`.
- Use local Account/Trade state for PAPER positions.
- Use Market Data only for current price and deterministic valuation inputs.
- Preserve the existing Broker Service/provider path for LIVE positions.
- Return a mode-neutral position view with a mode/source marker and explicit
  unavailable/stale valuation information.
- Neutralize or isolate the current broker-shaped position fact contract so
  local PAPER facts are not semantically represented as provider facts.
- Adapt the positions page to avoid LIVE close/reconcile actions and provider
  messaging for PAPER positions.
- Prove reload-safe PAPER position visibility and zero Broker Service position
  calls.

## Out of Scope

- PAPER close or exit execution.
- Realized PnL settlement changes.
- Partial close, reversal, hedging, or multiple-position redesign.
- Universal persistent Position aggregate.
- New risk rules or completion of PAPER Risk Facts.
- Fees, slippage, pending orders, or simulation realism.
- LIVE position authority or Broker Service redesign.
- Dashboard aggregation rewrite for PAPER balances, equity, and freshness.
- Frontend redesign beyond the minimum mode-aware safety adaptation.

## Acceptance Criteria

- [x] A persisted PAPER OPEN `Trade` is returned by
      `GET /api/v1/accounts/{accountId}/positions` after repository reload.
- [x] PAPER position querying does not call Broker Service account or position
      facts.
- [x] PAPER position identity is local and does not require a broker txid,
      provider position ID, or mutation scope.
- [x] The position fact/application contract is neutral or uses explicit
      source-specific adapters; local `Trade` state is not labeled as broker
      authority.
- [x] The response exposes execution mode or source metadata sufficient for the
      client to distinguish PAPER from LIVE.
- [x] PAPER current valuation uses Market Data with timestamp/status metadata.
- [x] The response distinguishes at least fresh, stale, unavailable, and
      unresolved-price states; `currentPrice: null` alone is not the complete
      freshness contract.
- [x] Missing or stale Market Data is represented as unavailable valuation and
      never replaced with a fabricated price or PnL.
- [x] LIVE position querying continues to use Broker Service/provider facts.
- [x] Account ownership, canonical relation, and execution mode are validated
      server-side.
- [x] The positions page does not offer the LIVE close/reconcile action or
      Kraken-specific FIFO messaging for PAPER positions.
- [x] Existing dashboard and LIVE position behavior remains compatible.
- [x] Tests cover distinct Account and BrokerAccount identifiers.

## Failure Semantics

- Missing Account or canonical relation: reject or return the existing typed
  account/domain failure; do not infer by owner/provider.
- PAPER Market Data unavailable: return the local position with unavailable
  valuation if the response contract supports it; otherwise fail the valuation
  portion explicitly, never fabricate zero risk or PnL.
- LIVE Broker Service unavailable: preserve current unavailable/stale behavior;
  do not fall back to local PAPER state.

## Technical Contract

The implementation may choose different class names, but it must preserve this
authority matrix:

| Concern | PAPER | LIVE |
|---|---|---|
| Account identity | `Account.accountId` | `Account.accountId` |
| Mode and routing | linked local `BrokerAccount` | linked local `BrokerAccount` |
| Position authority | persisted OPEN `Trade` state | Broker Service/provider facts |
| Current price | Market Data snapshot | existing Market Data snapshot |
| Position valuation | local Trade plus Market Data | existing broker facts plus Market Data |
| Provider mutation reference | not applicable | provider reference |

For PAPER, `positionId` must be a stable local identifier, normally the
persisted Trade ID. The response must not manufacture a broker position ID or
mutation scope. The implementation must either add explicit mode/source and
price-status fields to the response or provide an equivalent documented
representation that the frontend can safely consume.

PAPER positions must be derived from OPEN local Trades. The Story must state
whether same-symbol/same-side Trades are returned separately or aggregated; it
must not merge opposite-side Trades or imply reversal/exit behavior. Existing
`Trade.currentPrice` and `Trade.pnl` are persisted state, not a live valuation
source, and must not override the selected Market Data snapshot.

## Test Intent

- Unit-test mode-specific source selection.
- Test PAPER local position mapping and current valuation.
- Test missing/stale Market Data behavior.
- Test fresh, stale, unavailable, and unresolved-price response states.
- Test PAPER has no Broker Service account/position calls.
- Test LIVE continues to use Broker Service facts.
- Test persisted PAPER entry, reload, and position query as one integration
  scenario.
- Test the positions page hides or disables LIVE-only close controls for PAPER.
- Preserve existing dashboard and position regression tests.

## Architectural Decision

No new ADR is required. ADR-042 and ADR-043 already establish the required
authority and identity boundaries. If implementation proposes a new universal
position aggregate or changes risk semantics, stop and create a separate ADR
before expanding this Story.

The dashboard has a separate broker-first aggregation path and is explicitly
deferred from this Story. A later dashboard Story must define local PAPER
balance/equity and freshness semantics rather than silently reusing broker
values.

## Definition of Done

- [x] Repository Analysis approved.
- [x] Implementation Plan approved if required.
- [x] PAPER and LIVE source-selection behavior implemented.
- [x] Reload-safe PAPER query tests pass.
- [x] No-Broker-Service PAPER query test passes.
- [x] Existing relevant Trading Core validation passes.
- [x] Human diff review completed.
- [x] Code Review approved.

## Product Capability

For a PAPER account, the product must answer:

> Which positions are currently open, and what is their current financial
> valuation?

This is a read capability only. The intended progression is:

```text
PAPER entry -> persisted local position -> Story 0041 query and valuation
             -> Story 0042 EXIT intent and full close -> realized state
```

Story 0041 does not establish a general PAPER accounting system.

## Query And Authority

```text
authenticated Account.accountId
    -> Account ownership check
    -> Account.brokerAccountId
    -> linked BrokerAccount.id and executionMode
    -> LIVE: Broker Service position facts
    -> PAPER: local Account.trades where TradeStatus = OPEN
    -> Market Data batch snapshots for current valuation
    -> neutral position response
```

The first milestone needs all open positions for one financial Account. It does
not need single-position lookup, filtering, historical positions, or a generic
query framework. No owner/provider lookup or UUID equality may substitute for
the canonical relation.

## Position And Valuation Contract

| Value | Classification | Authority/semantics |
|---|---|---|
| Position ID | REQUIRED | persisted local `Trade.tradeId` for PAPER |
| Account ID | REQUIRED | requested financial `Account.accountId` |
| Symbol, side, quantity | REQUIRED | persisted local `Trade` |
| Entry price | REQUIRED | persisted local entry basis |
| Opened timestamp | REQUIRED | persisted local timestamp |
| Current mark | REQUIRED when valued | fresh executable-side Market Data quote |
| Unrealized PnL | REQUIRED when valued | existing deterministic PnL calculator |
| Unrealized PnL percentage | REQUIRED when PnL is valued | PnL / absolute entry notional |
| Price status/timestamp | REQUIRED | Market Data status and `occurredAt` |
| Current exposure/market value | OPTIONAL | only with explicit currency and null semantics |
| Stop/take-profit display | OPTIONAL compatibility | persisted Trade fields, not broker authority |
| Margin, fees, liquidation, protection | OUT OF SCOPE | unavailable or not authoritative here |

Each OPEN local `Trade` is one returned PAPER position view. The query does not
merge opposite-side Trades or add a new aggregation policy.

## Market Data Semantics

Trading Core must reuse `MarketDataClient.findAll()` and the existing batched
`findPriceSnapshots()` boundary. `MarketPriceSnapshotDto` provides bid, ask,
last price, tradability, `occurredAt`, and status values `FRESH`, `STALE`,
`UNAVAILABLE`, and `UNKNOWN_MARKET`.

The executable-side mark for this Story is:

```text
BUY / long   -> bid
SELL / short -> ask
```

The selected quote must be positive and have `FRESH` status and a non-null
timestamp. `lastPrice` must not silently replace a missing bid or ask. The
response must expose status, or an equivalent explicit representation, so a
missing or stale quote cannot look current. The Market Data service's configured
staleness policy is authoritative; its current default is 30 seconds. Trading
Core must not add a competing hard-coded threshold.

The existing `MarketValuationPort` is a risk-context valuation boundary with
reporting-currency conversion and price-use policies. Story 0041 should not
introduce a second client or silently mix its semantics into the existing
position projection. Direct snapshot use is sufficient only for instruments
whose quote currency equals the Account base currency.

## Supported Valuation

For the currently supported linear Trade model, with quote currency equal to
the Account base currency:

```text
long unrealizedPnl  = (markPrice - entryPrice) * quantity
short unrealizedPnl = (entryPrice - markPrice) * quantity
unrealizedPnlPct    = unrealizedPnl / abs(entryPrice * quantity) * 100
```

`TradingCalculatorService.calculatePnL()` owns signed PnL calculation. The
query layer must not duplicate it. Percentage rounding follows the existing
valuation policy (`HALF_UP`, scale 4) unless a shared policy is introduced.
No `double` or `float` is permitted for financial calculations.

Cross-currency valuation is unsupported in this Story because the position
query snapshot client has no conversion contract. If Market metadata shows a
quote currency different from the Account base currency, position existence
may still be returned but monetary valuation must be explicitly unavailable.
No FX subsystem is added.

## Persistence And Consistency

Persisted authority is local Trade identity, Account link, symbol, side,
quantity, entry price, opened timestamp, status, and persisted protection
display fields. Current mark, price timestamp/status, unrealized PnL, and
percentage are derived on each query. The query must not write `currentPrice`,
`pnl`, or any other valuation result.

The query promises a best-effort composed snapshot, not serializable
consistency. Materialize the local OPEN Trade snapshot in a read-only database
transaction, then call Market Data and calculate valuation outside that
transaction. A concurrent close may make one response briefly stale; Story
0042 owns close-time revalidation and target claiming.

## Failure Model

- Missing Account, canonical relation, owner mismatch, or unknown mode: explicit
  account/integrity failure, never an empty list.
- No OPEN local positions: successful empty result.
- Missing or invalid persisted position state: explicit invalid-state failure or
  invalid result according to the existing API error convention; never silently
  treat it as no position.
- Missing, stale, unavailable, unknown, or incomplete Market Data: return the
  authoritative position with valuation fields absent and explicit status/reason
  where the response contract permits; never use persisted current price, last
  price, zero, or another fallback as current valuation.
- Unsupported instrument mapping or quote currency: position remains observable,
  monetary valuation is unavailable with a deterministic reason.
- LIVE Broker Service failure: preserve current unavailable behavior and never
  fall back to local PAPER state.

## Security And UI Boundary

JWT and Account ownership checks remain mandatory. The linked BrokerAccount must
belong to the same owner and be resolved server-side. PAPER local querying must
not bypass authorization merely because no broker credentials are involved.

The existing positions page needs only a safety adaptation in this Story: it
must distinguish PAPER from LIVE and hide/disable the LIVE close/reconcile
controls and Kraken FIFO message for PAPER. It must continue using Observables
and the async pipe. PAPER close is not implemented here.

The separate broker-first dashboard aggregation path is deferred. A later
dashboard Story must define local PAPER balance/equity/freshness semantics
rather than reusing broker values silently.

## Implementation Subtasks

1. Establish a neutral position read contract or explicit source adapters.
2. Resolve canonical Account/BrokerAccount identity and execution mode.
3. Materialize local PAPER OPEN Trade snapshots without remote calls in the
   database transaction.
4. Reuse the Market Data catalogue and batch snapshot boundary.
5. Select bid for long and ask for short only from fresh snapshots.
6. Use the existing deterministic PnL calculator and explicit unavailable
   valuation semantics.
7. Extend the API and minimum positions-page safety behavior.
8. Add the full test matrix and run relevant module validation.

## LEARN / PAIR / DELEGATE

| Subtask | Mode | Reason |
|---|---|---|
| Identity and mode resolution | PAIR | ADR-043 safety boundary; do not guess financial versus routing identity. |
| Neutral authority routing | PAIR | Must preserve LIVE behavior while adding local PAPER authority. |
| Bid/ask mark and BigDecimal valuation | LEARN | Core financial semantics and calculator ownership. |
| JPA read-only snapshot and transaction boundary | LEARN | Useful consistency and lazy-loading concept. |
| DTO mapping and fixture plumbing | DELEGATE | Mechanical after semantics are fixed. |
| Minimum Angular field/control adaptation | DELEGATE | Mechanical reactive contract change after API semantics are approved. |
| Failure and isolation tests | PAIR | Empty, unavailable, and integrity failure are safety decisions. |

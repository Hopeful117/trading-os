# Repository Analysis - Story 0041

## Repository State

| Field | Value |
|---|---|
| Branch | `main` |
| HEAD | `3e89646c4a9cd80dea8cdad31de0fcbb4f8ea486` |
| Baseline | Story 0040 merged |
| Target module | `trading-core` |
| Implementation performed | No |
| New ADR required | No |

```text
HEAD = 3e89646c4a9cd80dea8cdad31de0fcbb4f8ea486
BRANCH = main
WORKTREE_STATUS = dirty with pre-existing unrelated changes; Story 0041 docs are untracked
STORY_0041_PRESENT = yes
```

The local branch matches the known `origin/main` commit. A remote refresh was
attempted but could not authenticate through the configured SSH key. The
working tree contains pre-existing unrelated changes; this investigation did
not modify them.

## Governing Decisions

- ADR-042: LIVE position authority is Broker/provider; PAPER position authority
  is Trading Core.
- ADR-042: the first PAPER position representation remains the existing
  persisted `Trade`, not a universal `Position` aggregate.
- ADR-043: `Account.accountId` is the financial identity and
  `BrokerAccount.id` is the routing identity.
- Story 0040: T0/T1 use the canonical identity and mode-aware Risk Facts
  boundary; incomplete required PAPER risk facts remain fail-closed.

## Current PAPER Evidence

### Entry and Settlement

The common execution pipeline routes by `BrokerAccount.executionMode()`.
`SimulatedExecutionAdapter` obtains a current Market Data price and returns a
synthetic acknowledged fill. `ExecutionFinalizationStep` invokes
`PaperSettlementService` for PAPER acknowledgements.

`PaperSettlementService` persists local balances and creates or aggregates an
OPEN `Trade`. It currently:

- uses the canonical BrokerAccount-to-Account lookup;
- applies BUY/SELL balance effects;
- updates equity for fees;
- aggregates only same-symbol, same-side OPEN trades;
- does not reduce an opposite-side trade into a close;
- does not provide a complete local exit settlement path.

### Position Query

`PositionController` calls `BrokerApiClient.getAccount()` before invoking
`PositionQueryService`. It supplies broker positions and a broker-derived
base-currency balance. If Broker Service is unavailable, the controller returns
an empty position list.

`DashboardQueryService` has the same broker-first assumption. It calls
`BrokerApiClient.getAccount()`, derives balance/equity from the broker response,
and passes broker positions to `PositionQueryService`. A broker failure returns
an unavailable dashboard instead of a local PAPER dashboard.

`PositionQueryService` accepts `BrokerPositionFact`, even though its valuation
logic is otherwise generic. That makes the service broker-shaped. A PAPER
implementation must not pass local `Trade` objects through an API that
semantically claims provider facts; it needs a neutral position fact or
explicit mode-specific adapters.

`OpenPositionDashboardView` is mostly neutral, but has no execution mode or
source marker. The frontend position page therefore cannot distinguish a local
PAPER position from a provider position.

Therefore a PAPER account with a valid local OPEN `Trade` is not currently
observable through either the positions page or dashboard without
broker-oriented data. This violates the already accepted PAPER authority
boundary, even though it does not change risk authorization semantics.

The frontend currently renders a close action for every returned position,
sends `positionId` as `brokerPositionReference`, and displays a Kraken FIFO
warning. Leaving this unchanged after local PAPER positions are returned would
route a local identifier into the LIVE-oriented close command and misrepresent
provider semantics. Story 0041 must expose enough mode/source information to
suppress or disable those LIVE-only controls for PAPER. It must not implement
PAPER close as a side effect of position querying.

### Close

`PositionCloseService` is broker-oriented: it resolves an opaque broker
position reference, executes through `BrokerPositionClosePort`, and supports
provider reconciliation. No PAPER adapter exists for this boundary.

ADR-042 explicitly requires PAPER close to use a local target and an EXIT
execution intent, not `PositionCloseCommand`. This is a subsequent story, not
part of the position-query slice.

### Risk Facts

`ModeAwareRiskFactsProvider` builds PAPER facts from local Account balances and
OPEN/CLOSED Trades, but reports the snapshot incomplete because local ledger,
margin, and protection facts are not available. Required missing facts remain
fail-closed. Story 0041 must not invent those facts or make position display
depend on successful risk authorization.

## Gap Map

| Capability | Current state | Story 0041 disposition |
|---|---|---|
| PAPER account identity | Implemented | Reuse |
| PAPER entry simulation | Implemented | Reuse |
| Local OPEN Trade projection | Implemented | Reuse, validate |
| PAPER position source selection | Missing in positions endpoint and dashboard | In scope for positions endpoint |
| Market Data valuation for local PAPER Trade | Missing in public position path | In scope |
| Broker/provider LIVE position source | Implemented | Preserve |
| PAPER full close | Missing | Follow-up Story 0042 |
| Realized PnL on PAPER close | Incomplete for execution path | Follow-up Story 0042 |
| Complete PAPER risk facts | Intentionally incomplete | Separate dependency; remain fail-closed |
| Universal Position aggregate | Not required | Out of scope |

## Recommended Boundary

Add execution-mode selection above the existing position projection:

```text
Position query
    -> resolve Account and linked BrokerAccount
    -> LIVE: Broker Service positions + Market Data valuation
    -> PAPER: local Account/Trade positions + Market Data valuation
    -> neutral position view
```

The public position view may expose a source or mode marker if needed for
explanation, but PAPER must not require broker position references or Broker
Service availability. The marker is explanatory server output, not a client
authority.

The endpoint and application service should use the financial Account ID to load
the Account, resolve the canonical BrokerAccount, validate ownership, and read
`ExecutionMode`. They must not infer mode from the legacy `Account.broker`
string, UUID equality, or the presence of risk configuration.

For PAPER, local `Account.trades` must be filtered to `TradeStatus.OPEN`. The
mapping must preserve `tradeId`, symbol, side, quantity, entry price, stop-loss,
take-profit, and opened time. `Trade.currentPrice` and `Trade.pnl` must not be
treated as authoritative current valuation because they are not maintained on
every Market Data tick. Current price, unrealized PnL, exposure, and related
timestamps must be computed from the selected Market Data snapshot.

The current `PositionValuationService` has useful deterministic formulas but
accepts `BrokerPositionFact`; its contract must be neutralized or isolated
behind a local-to-neutral adapter. For missing/stale prices it supports null
PnL while retaining fallback exposure, but Story 0041 must define the same
behavior for local PAPER positions and preserve price status/timestamp.

The current response has `priceOccurredAt` but no explicit price status. A null
`currentPrice` alone cannot distinguish a missing snapshot, stale snapshot, or
an unresolved market. The contract must therefore add an explicit valuation
status, or an equivalent documented status representation, if the frontend is
expected to explain freshness correctly. `marketTradable` must not be used as
a replacement for quote freshness.

The first slice should update the positions page enough to avoid misleading
PAPER actions: expose the mode/source marker and hide or disable the current
LIVE close/reconcile controls for PAPER. Implementing PAPER close is a later
story.

The dashboard has a second broker-first aggregation path. Full PAPER dashboard
support would require local balance/equity and freshness semantics in addition
to position projection. It is explicitly deferred from this Story to keep the
slice coherent; a later dashboard Story must define those semantics.

The Risk Domain remains unchanged. Position display is not a substitute for
Risk Facts and must not authorize trades.

## Decision

`NEXT = ENGINEERING_STORY`

Story 0041 should implement local PAPER position querying and deterministic
current valuation for the account positions endpoint, preserving the existing
LIVE path. It should include the minimum positions-page adaptation needed to
prevent a PAPER position from invoking the LIVE close action. The dashboard
aggregation rewrite is deferred. No new ADR is required: ADR-042 already
defines the authority split and ADR-043/Story 0040 define the identity and
facts boundaries.

## Validation Expectations

- A persisted PAPER OPEN Trade is returned by the account positions endpoint
  after reload.
- PAPER position queries do not call Broker Service account/position facts.
- LIVE position queries retain Broker Service authority.
- The position fact/application contract is neutral or uses explicit
  source-specific adapters; local `Trade` state is not labeled as broker
  authority.
- The response exposes enough mode/source information for the frontend to avoid
  LIVE close/reconcile controls and Kraken-specific messaging for PAPER.
- Current PAPER valuation uses fresh Market Data and exposes unavailable/stale
  price state without inventing a value.
- Account ownership and canonical Account/BrokerAccount relation are enforced.
- No provider-specific position reference is required for PAPER.
- Existing LIVE position and dashboard tests remain green.
- A missing Market Data snapshot does not authorize or fabricate valuation.

## Concrete Contract Constraints

The implementation may choose different class names, but the behavior must be
equivalent to this source matrix:

| Input | PAPER | LIVE |
|---|---|---|
| Account identity | `Account.accountId` path variable | `Account.accountId` path variable |
| Mode and routing | local linked `BrokerAccount` | local linked `BrokerAccount` |
| Position authority | OPEN local `Trade` rows | Broker Service position facts |
| Balance/equity for position percentages | local Account state, with explicit documented basis | existing broker/dashboard basis |
| Current price | Market Data snapshot | existing Market Data snapshot |
| Provider mutation reference | not applicable | existing provider reference |

The PAPER mapper must preserve local identifiers and fields without creating a
fake provider reference. If `OpenPositionDashboardView.positionId` remains a
string for compatibility, its PAPER value must be a documented stable local
identifier such as the persisted Trade ID.

The implementation must decide and document whether the positions endpoint
returns only local OPEN Trades or aggregates same-symbol/same-side Trades. It
must not silently merge opposite-side exposures, because PAPER exit semantics
are deferred and ADR-042 forbids accidental reversal through an exit path.

## Known Deferred Work

- PAPER full exit through an explicit EXIT execution intent.
- Local realized-PnL settlement and closed-state transition for exit.
- PAPER dashboard aggregation, including local balance/equity and freshness.
- Complete PAPER risk facts for margin, ledger baseline, and protection.
- Universal Position aggregate, partial close, reversal, and simulation realism.

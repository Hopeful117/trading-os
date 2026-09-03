# Story 0032 — Dedicated Open Positions Monitoring

## Metadata

**ID:** `0032`

**Title:** Dedicated Open Positions Monitoring

**Status:** Completed

---

## Goal

After executing a trade, the trader can reliably inspect their currently open positions through a dedicated `/positions` page that answers: "What positions do I currently have open, and what is their current deterministic state?"

This Story is strictly **read-only position monitoring**. It does NOT introduce position closing, modification, or any broker-mutating operation.

---

## Context

Story 0030 established human-controlled execution.

Story 0031 closed the execution feedback loop.

The next product capability should extend the lifecycle to:

```
Market Intelligence
        ↓
Opportunity
        ↓
Trade Plan
        ↓
Risk
        ↓
Human Execution
        ↓
Execution Feedback
        ↓
Open Position Monitoring
```

The sidebar already contains a `/positions` link, but the route and page do not exist. The trader currently sees positions only in the dashboard table, which is embedded in a larger dashboard response and not optimized for focused position monitoring.

---

## Problem

Three concrete issues prevent effective position monitoring:

**1. No Dedicated Positions Page:** The sidebar link `/positions` exists but has no corresponding route or component. The trader cannot navigate to a focused view of their open positions.

**2. Positions Embedded in Dashboard:** Position data is only available as part of the `DashboardSummary` response from `GET /api/v1/accounts/{accountId}/dashboard`. This couples position monitoring to the full dashboard payload.

**3. No Dedicated Position Endpoint:** There is no `GET /api/v1/accounts/{accountId}/positions` endpoint. The dashboard endpoint returns the entire dashboard including account summary, risk, alerts, markets, and freshness—unnecessary for position monitoring.

---

## Scope

### In scope

- Add `GET /api/v1/accounts/{accountId}/positions` endpoint in Trading Core
- Create Angular `/positions` route and Positions component
- Create Angular `PositionService` for position data access
- Display position cards with instrument, side, quantity, entry price, current price, unrealized PnL, stop loss, take profit, protection status
- Show position alerts (existing deterministic alerts from `DashboardAlertService`)
- Show data freshness information
- Account-scoped position display (consistent with dashboard navigation)
- Low-frequency polling while page is visible
- Empty states: loading, no positions, broker unavailable, authorization failure
- Reuse existing `OpenPositionDashboardView` and `PositionValuationService`

### Out of scope

- Close position, partial close, modify SL/TP
- Position history, closed trades
- Execution→position correlation
- Multi-account aggregation
- WebSocket/SSE position streaming
- Real-time price updates
- Monitoring Agent, AI recommendations
- FTMO-specific rules
- cTrader implementation

---

## Acceptance Criteria

* [ ] AC1: Authenticated user with an account can navigate to `/positions` and see a dedicated positions page.
* [ ] AC2: The page displays only positions belonging to the authorized account scope.
* [ ] AC3: Each position shows: instrument, side (LONG/SHORT), quantity, entry price, current price, unrealized PnL (amount and percentage), stop loss, take profit, protection status, opened at.
* [ ] AC4: Unrealized PnL is computed on the backend by `PositionValuationService`, not in Angular.
* [ ] AC5: Protection status uses existing deterministic `PositionProtectionStatus` (PROTECTED, MISSING_STOP_LOSS, UNKNOWN).
* [ ] AC6: Existing deterministic position alerts are displayed (MISSING_STOP_LOSS, NEAR_STOP_LOSS, MARKET_PRICE_UNAVAILABLE, MARKET_NOT_TRADABLE).
* [ ] AC7: When position retrieval succeeds with no open positions, the page displays an explicit empty state: "Aucune position ouverte."
* [ ] AC8: When position retrieval fails, the page displays an error state distinct from the empty state.
* [ ] AC9: When the page is visible, positions are refreshed every 10 seconds.
* [ ] AC10: Polling stops when the component is destroyed (no subscription leaks).
* [ ] AC11: The sidebar `/positions` link navigates to the new page (completing existing navigation contract).
* [ ] AC12: No UI control causes broker trading state to change.
* [ ] AC13: Relevant tests pass. No unrelated behavior is changed.

---

## Constraints

* Preserve existing position architecture (broker-authoritative external state projected through `BrokerPositionFact`).
* Respect ADR-001 (human authority), ADR-014 (decision pipeline).
* Trading Core remains the application boundary for user context, account ownership, and position projection.
* Angular must not call Broker Service directly.
* Position data is sensitive; cross-user position exposure is unacceptable.
* Do not introduce a new Position aggregate merely for the Positions page.
* Reuse existing `OpenPositionDashboardView` and `PositionValuationService`.
* Do not introduce AI, recommendations, or monitoring agents.
* Do not commit, push, or merge automatically.

---

## Relevant ADRs

* `docs/architecture/adr/ADR-001.md` — Trading OS Vision (human authority)
* `docs/architecture/adr/ADR-014.md` — Trading Decision Pipeline

---

## Relevant Modules

* `trading-core` — Add dedicated positions endpoint
* `trading-os-web` — Angular positions page, route, service

---

## Repository Baseline

```text
ROOT = /home/ludo/Bureau/workspace/trading-os
BRANCH = main
HEAD = 3a5cfbe
WORKTREE = clean
STORY_0031_STATE = merged (PR #27)
```

---

## Position Authority

```text
POSITION_AUTHORITY = broker-authoritative external trading state
POSITION_SOURCE = Broker Service → PositionCapability → List<PositionSnapshot>
APPLICATION_PROJECTION = OpenPositionDashboardView (reuse existing)
PERSISTED = no (positions are live broker state, not persisted in Trading Core)
```

---

## API Decision

```text
EXISTING_API = GET /api/v1/accounts/{accountId}/dashboard (returns DashboardSummary with openPositions)
SUFFICIENT = no (dashboard endpoint is too heavy for focused position monitoring)
NEW_ENDPOINT_REQUIRED = yes
DECISION = Add GET /api/v1/accounts/{accountId}/positions returning List<OpenPositionDashboardView>
```

The new endpoint extracts position data from the existing dashboard pipeline without requiring the full dashboard payload. This preserves the existing `DashboardQueryService.buildPositions()` logic while providing a focused API for the positions page.

---

## Account Scope

```text
POSITION_SCOPE = account-scoped (one account at a time)
RATIONALE = consistent with existing dashboard navigation and account selection pattern
```

The positions page inherits the account context from the sidebar/account selector, consistent with the dashboard. Multi-account aggregation is a future enhancement.

---

## Data Contract

```text
FIELD                    | SOURCE                                    | REQUIRED | NOTES
-------------------------|-------------------------------------------|----------|------
positionId               | BrokerPositionFact.positionId             | yes      | broker position identifier
accountId                | Account.accountId                         | yes      | from authenticated user context
marketId                 | MarketResponse.marketId                   | no       | null if market not found
symbol                   | BrokerPositionFact.symbol                 | yes      | broker symbol (e.g., XBT/USD)
side                     | BrokerPositionFact.side                   | yes      | BUY (LONG) or SELL (SHORT)
quantity                 | BrokerPositionFact.quantity                | yes      | signed quantity
entryPrice               | BrokerPositionFact.entryPrice             | yes      | from broker
currentPrice             | MarketPriceFact.price                     | no       | null if market data unavailable
stopLoss                 | BrokerPositionFact.stopLoss               | no       | null if not set
takeProfit               | BrokerPositionFact.takeProfit             | no       | null if not set
unrealizedPnl            | PositionValuation.pnl                     | no       | computed backend
unrealizedPnlPercentage  | PositionValuation.pnlPercentage           | no       | computed backend
brokerUnrealizedPnl      | BrokerPositionFact.brokerUnrealizedPnl   | no       | from broker
riskAmount               | PositionValuation.riskAmount              | yes      | computed backend
riskPercentage           | PositionValuation.riskPercentage          | yes      | computed backend
exposure                 | PositionValuation.exposure                | yes      | computed backend
protectionStatus         | PositionProtectionStatus                   | yes      | deterministic
marketTradable           | MarketPriceFact.tradable                  | yes      | from market data
openedAt                 | BrokerPositionFact.openedAt               | no       | from broker
priceOccurredAt          | MarketPriceFact.occurredAt                | no       | from market data
calculatedAt             | DashboardQueryService                     | yes      | timestamp of calculation
```

---

## Valuation Semantics

```text
CURRENT_PRICE_MEANS = last traded price from Market Data Service for the matched market
UNREALIZED_PNL_SOURCE = PositionValuationService.value() using TradingCalculatorService.calculatePnL()
CALCULATION_OWNER = Trading Core backend (deterministic)
FRESHNESS = priceOccurredAt timestamp from MarketPriceFact
```

---

## Protection Semantics

```text
STOP_LOSS = BrokerPositionFact.stopLoss (from broker position snapshot)
TAKE_PROFIT = BrokerPositionFact.takeProfit (from broker position snapshot)
PROTECTION_STATUS = PositionProtectionStatus enum (PROTECTED, MISSING_STOP_LOSS, UNKNOWN)
EXISTING_ALERTS_REUSED = yes (MISSING_STOP_LOSS, NEAR_STOP_LOSS, MARKET_PRICE_UNAVAILABLE, MARKET_NOT_TRADABLE)
```

---

## Refresh Design

```text
STRATEGY = low-frequency HTTP polling while page is visible
INTERVAL = 10 seconds
START = component init (after initial load)
STOP = component destroy (subscription cleanup via async pipe or takeUntil)
ERROR_BEHAVIOR = preserve last known state, show error banner, do not display "no positions"
```

---

## Security

```text
AUTHENTICATION = JWT required (authGuard on route)
OWNERSHIP = Trading Core verifies account ownership via username
CROSS_USER_PROTECTION = account-scoped endpoint prevents cross-user position exposure
```

---

## Provider Compatibility

```text
KRAKEN_COUPLING = minimal (PositionSnapshot and OpenPositionDashboardView are broker-agnostic)
CTRADER_FUTURE_IMPACT = low (read-only monitoring tolerates current projection; future multi-position per instrument may require model extension)
REQUIRES_REDESIGN_NOW = no
```

---

## Monitoring Boundary

```text
READ_ONLY = YES
BROKER_MUTATION = NO
POSITION_CLOSE = OUT_OF_SCOPE
SL_TP_MODIFICATION = OUT_OF_SCOPE
```

---

## Empty and Error States

```text
LOADING = "Chargement des positions..."
NO_ACCOUNT = "Aucun compte sélectionné."
NO_POSITIONS = "Aucune position ouverte."
POSITIONS_AVAILABLE = display position cards
BROKER_UNAVAILABLE = "Les données du broker sont temporairement indisponibles."
RETRIEVAL_FAILED = "Erreur lors de la récupération des positions."
```

---

## Angular Architecture

Follow existing Trading OS frontend conventions:

- Standalone component
- Reactive services with Observables
- Async pipe for template binding
- Account selection via `BehaviorSubject` (same pattern as Dashboard)
- `PositionService` for position data access
- Low-frequency polling with `timer(0, 10_000)` and `switchMap`
- Subscription cleanup via `shareReplay({ bufferSize: 1, refCount: true })` and component destroy
- No manual subscriptions, no `ChangeDetectorRef`

---

## UI Design

Use the established Trading OS visual language:

- Professional dark dashboard
- Restrained blue accents
- Position cards with clear hierarchy
- Compact trading information
- Subtle status badges for protection status
- Responsive layout
- Clear positive/negative PnL presentation (green/red)
- Consistent with existing dashboard position display

---

## Testing Requirements

### Trading Core

```text
- GET /api/v1/accounts/{accountId}/positions returns positions for owned account
- Non-owner cannot retrieve positions (403/404)
- Empty positions returns legitimate empty list
- Broker failure is distinguishable from empty positions
- Position projection matches existing OpenPositionDashboardView
```

### Angular

```text
- Route loads successfully
- Loading state displayed during initial fetch
- Positions rendered when data available
- Empty state displayed when no positions
- Error state displayed when retrieval fails
- PnL displayed correctly from backend projection
- Protection information displayed
- Polling stops on component destroy
- Sidebar link navigates to positions page
```

---

## Definition of Done

* [ ] Dedicated positions endpoint added in Trading Core
* [ ] Angular positions page created
* [ ] Angular positions route registered
* [ ] Angular positions service created
* [ ] Sidebar `/positions` link functional
* [ ] Position cards display all required fields
* [ ] Protection status displayed
* [ ] Deterministic alerts displayed
* [ ] Empty and error states implemented
* [ ] Low-frequency polling implemented
* [ ] Subscription cleanup verified
* [ ] No broker mutation possible
* [ ] Automated tests pass
* [ ] Angular production build succeeds

---

## Validation

* Trading Core tests (positions endpoint, ownership, projection)
* Angular tests (positions component, service, polling, empty/error states)
* Angular production build
* Manual verification: sidebar link navigates to positions page
* Manual verification: positions display correctly
* Manual verification: polling refreshes data
* Manual verification: no broker mutation controls exist

# Repository Analysis - Story 0080

## Scope

Story 0080 addresses the missing Trading Core path for calculating complete
multi-asset account equity in the account base currency. The work is limited to
Dashboard aggregation and its deterministic valuation contract. It does not
extend Story 0079 Challenge progression or continuous PAPER monitoring.

## Current Repository State

- Current branch is `main`.
- Current HEAD is the merge commit containing Story 0079.
- The only uncommitted change is the new Story 0080 directory and its Story
  artifact.
- No implementation files have been modified for Story 0080.

## Current Equity Path

`DashboardQueryService` currently:

1. Loads normalized broker account facts.
2. Selects the account base-currency balance only.
3. Calculates open-position unrealized PnL.
4. Passes `balance + unrealizedPnl` to `AccountEquityService`.
5. Prefers `brokerEquity` only when it is present, fresh, and within the
   existing one-percent divergence tolerance.

`AccountEquityService` therefore has no concept of non-base-currency balances,
valuation facts, incomplete conversion, or valuation provenance.

The public `AccountDashboardSummary` exposes only:

- balance;
- equity;
- PnL and drawdown values;
- `equitySource`.

There is currently no public valuation completeness, status, timestamp, or
policy field.

## Existing Integration Capabilities

### Broker Service Contract

`BrokerAccountDto` and `BrokerAccountFact` already expose:

- broker base currency;
- a map of asset balances;
- optional broker equity;
- open positions;
- broker data timestamp.

The contract is provider-neutral at the Trading Core boundary, but it does not
declare whether balances include position collateral, spot holdings, derivative
notional, or values already represented by open-position PnL. This semantic gap
must be resolved before implementation to prevent double counting.

### Market Data Valuation

Market Data already exposes:

```text
POST /internal/v1/valuation-snapshots/batch
```

`ValuationSnapshotService` supports:

- identity conversion;
- direct conversion using a conservative bid;
- inverse conversion using a conservative ask;
- valuation timestamps and captured timestamps;
- a five-minute default maximum observation age;
- complete/incomplete snapshot status;
- per-fact statuses;
- conversion-leg and source-observation provenance;
- persisted snapshot versioning.

`ValuationSnapshotServiceTest` already covers direct, inverse, identity, stale,
future-only, late-arriving, and provenance behavior.

Trading Core's `MarketDataClient` currently supports market catalogue and
position-price snapshot calls only. It has no request/response contract for the
valuation batch endpoint.

### Existing Position Valuation

`PositionQueryService` calculates open-position PnL from market prices and uses
bid for BUY positions and ask for SELL positions. This is separate from the
new account-asset valuation path and currently assumes quote-currency
compatibility for position calculations.

The implementation must explicitly define whether account balances represent
cash/holdings that should be valued independently, or whether some balances are
already the settlement value of open positions. The current repository does not
establish that distinction.

## Architectural Boundary

ADR-019 makes Trading Core the owner of the Dashboard aggregation and keeps
financial aggregation out of Angular. Market Data remains responsible for
market observations and valuation facts. The intended integration is therefore:

```text
Broker Service account facts
        +
Market Data valuation snapshot
        ↓
Trading Core equity aggregation
        ↓
Dashboard contract and risk consumers
```

ADR-044 requires internal valuation endpoints to remain service-authenticated.
Any new Feign call must use the existing internal service-authentication
configuration rather than making the valuation endpoint public.

## Recommended Implementation Boundary

The implementation plan should evaluate a small Trading Core valuation adapter
that:

- converts the normalized asset balance map into valuation batch assets;
- requests one valuation snapshot for the account base currency and timestamp;
- multiplies each asset amount by its returned unit value;
- rejects or marks the aggregate incomplete when any supported asset lacks a
  current valid fact;
- records the valuation status and provenance in the internal equity result;
- feeds the selected resolved equity into existing drawdown and risk
  calculations;
- leaves the existing broker-equity preference only where total-value
  semantics are proven.

The exact treatment of open-position PnL must be specified in the
Implementation Plan after confirming the broker facts for supported execution
modes. It must not be inferred from the presence of both a balance map and an
open-position list.

## Risks and Open Decisions

1. **Balance semantics:** determine whether broker balances are cash/spot
   holdings, collateral, or a mixture that already includes position value.
2. **PAPER semantics:** determine whether persisted PAPER balances and local
   open positions can be valued through the same path without violating ADR-042
   authority boundaries.
3. **Incomplete totals:** choose whether the Dashboard returns a null equity,
   a degraded partial value, or keeps a separately sourced broker total when a
   conversion fact is unavailable. The Story requires this choice to be
   explicit and non-silent.
4. **Public contract:** decide the minimal status/provenance fields needed by
   the Dashboard without exposing Market Data provider payloads.
5. **Broker total trust:** establish when `brokerEquity` is authoritative enough
   to avoid revaluing all balances and how divergence is reported.

These are implementation-policy decisions, not reasons to move aggregation into
the frontend. If they require a new service responsibility or valuation policy,
an ADR must be approved before implementation proceeds.

## Validation Expectations

- Market Data existing valuation tests remain green.
- Trading Core tests cover the new Feign contract and complete/incomplete
  multi-asset Dashboard equity.
- Tests cover direct, inverse, identity, stale, missing, and invalid facts.
- Tests prove no double counting between account balances and open-position PnL
  for each supported account-fact semantic.
- Dashboard risk and drawdown assertions use the resolved equity.
- Frontend tests/build run only if the Dashboard contract or rendering changes.
- `git diff --check` and complete diff inspection.

## Context Limitations

- DevLog context retrieval was attempted but unavailable because the configured
  provider reported exhausted quota. The analysis therefore relies on current
  repository artifacts and ADRs.
- No workspace `TOOLS.md` mapping was found, so DevLog lifecycle registration
  could not be performed.
- The Obsidian vault was not consulted because current repository Stories, ADRs,
  and implementation evidence were sufficient.

## Gate

Repository Analysis is complete and awaits explicit human approval. No
Implementation Plan or code changes should begin until this analysis is
approved.

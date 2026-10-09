# Implementation Plan - Story 0080

## Objective

Add deterministic multi-asset account-equity valuation to the Trading Core
Dashboard while preserving the existing service boundaries and avoiding double
counting between balances and open-position PnL.

## Design Decisions

### 1. Reuse the existing Market Data valuation contract

Trading Core already has `MarketValuationClient` and `MarketValuationPort` for
Risk Domain valuation. The implementation will not create a second Feign
contract or duplicate Market Data transport records.

The valuation adapter will be moved or factored into a neutral Trading Core
integration package so both Risk Domain and Dashboard can consume the same
provider-neutral snapshot contract. The existing service-authentication
configuration and endpoint remain unchanged:

```text
POST /internal/v1/valuation-snapshots/batch
```

The existing refresh-before-read behavior remains the source of current
observations for runtime Dashboard valuation.

### 2. PAPER equity uses authoritative asset balances

For PAPER accounts, persisted `Account.balances` are authoritative financial
state. Each balance is valued in the account base currency. Open PAPER
position PnL is not added separately because PAPER settlement updates balances
and a balance valuation already captures the current value of long holdings or
short borrowed inventory.

This preserves ADR-042's Trading Core ownership of PAPER state and avoids
double counting the same simulated exposure.

### 3. LIVE/broker equity preserves explicit broker semantics

For broker-backed accounts, the implementation will distinguish:

- a broker-provided total equity that is fresh and coherent; and
- normalized broker balances plus open-position facts used for calculated
  equity.

The calculated path will use the broker account fact semantics established by
the implementation: balance assets are valued through the valuation snapshot,
and open-position PnL is added only when those positions are not already
represented by the balance facts. If the provider cannot establish this
distinction, the result is incomplete/degraded rather than silently combining
both values.

The broker adapter will not gain Dashboard or risk logic. If the current
provider contract cannot express the distinction safely, the normalized fact
contract will be extended with explicit semantics rather than inferred from
payload shape.

### 4. Incomplete valuation is fail-closed for completeness

Every non-zero supported asset must have an available, non-stale valuation fact.
If any fact is unavailable, stale, invalid, or unsupported:

- the calculated result is marked incomplete/degraded;
- no partial calculated total is presented as a complete equity value;
- a fresh, explicitly total broker equity may remain usable as the selected
  source when its trust conditions are met;
- otherwise equity is unavailable and downstream risk/drawdown percentages use
  the existing null/unavailable handling.

The public Dashboard contract receives a small provider-neutral status/source
extension, not raw Market Data payloads.

## Implementation Steps

1. Refactor the existing valuation transport and adapter from the Risk-specific
   package into a neutral Trading Core market-data integration package while
   preserving `MarketValuationPort` behavior and existing Risk callers.
2. Add a Dashboard account-valuation application service that:
   - normalizes asset names and base currency;
   - removes null/invalid zero-value facts according to explicit rules;
   - requests one valuation snapshot for all account assets;
   - multiplies each asset amount by its unit conversion value;
   - produces total value, completeness, status, timestamp, policy version,
     and compact provenance references.
3. Extend normalized account facts with the minimum execution-mode/account
   semantics required to select the PAPER balance-only path safely.
4. Update `DashboardQueryService` to resolve account equity through the new
   service before calculating drawdown, used-risk percentages, alerts, and the
   final account summary.
5. Preserve broker-equity preference only after the new calculated result and
   total-value trust checks are available. Keep divergence detection and make
   the selected source/status explicit.
6. Extend `AccountDashboardSummary` with provider-neutral valuation status and
   provenance fields only where required by the approved contract.
7. Update frontend models/templates only if the new status fields need to be
   displayed; Angular will continue to render backend values and will not
   perform conversions or aggregation.
8. Add focused tests before broad validation.

## Expected Files and Areas

### Trading Core

- Existing Risk valuation adapter and port package: factor into a reusable
  market-data valuation integration boundary.
- `dashboard/service/AccountEquityService.java`: accept resolved multi-asset
  calculation facts while retaining broker selection/divergence policy.
- `dashboard/service/DashboardQueryService.java`: use resolved valuation before
  risk and drawdown calculations.
- `dashboard/integration/BrokerAccountFact.java` and mapper: preserve the
  necessary normalized account semantics.
- `dashboard/model/AccountDashboardSummary.java`: expose minimal valuation
  status/provenance metadata.
- New dashboard valuation service/result types and valuation DTO tests.
- Existing `MarketDataClient`/Feign configuration only if the factorization
  requires contract consolidation.

### Market Data

- No behavior change is expected in `ValuationSnapshotService`.
- Add or adjust only contract tests if the existing endpoint needs validation
  for the Dashboard use case.

### Frontend

- Dashboard model/template/service only if status/source presentation changes.
- No client-side financial calculation or cross-service aggregation.

## Test Plan

### Market Data

- Preserve existing valuation snapshot coverage for identity, direct, inverse,
  stale, missing, future-only, late-arriving, and provenance cases.
- Add endpoint contract coverage only if transport changes.

### Trading Core valuation

- Base-only balance uses identity conversion.
- Multiple balances are aggregated in the reporting currency.
- Direct and inverse conversion facts are consumed correctly.
- Zero balances do not require unnecessary conversion facts.
- Stale, missing, invalid, and unsupported assets produce incomplete status.
- PAPER balances are valued without adding the same open-position PnL again.
- Broker calculated semantics do not double count balances and position PnL.
- Fresh coherent broker total remains preferred.
- Divergent broker total selects the safe calculated/incomplete result.

### Dashboard orchestration

- Resolved equity is used for drawdown and risk percentages.
- Valuation status is propagated to freshness/alerts without hiding failures.
- Broker failure and valuation failure preserve existing unavailable/degraded
  behavior.
- Existing no-position and position dashboard regressions remain green.

### Frontend

- Run focused tests and production build only if the public Dashboard contract
  or rendering changes.

## Validation Commands

```text
./mvnw -q test                         # market-data
./mvnw -q test                         # trading-core
./mvnw -q verify                       # trading-core
npm run test:ci                        # trading-os-web, if changed
npm run build                          # trading-os-web, if changed
git diff --check
```

Commands must be executed from their respective module directories and reported
with their actual results. No validation result is implied by this plan.

## Risks and Stop Conditions

- If provider balance semantics cannot be established without changing the
  Broker Service contract, stop and request an architectural/product decision.
- If PAPER balance valuation conflicts with persisted equity or Challenge
  progression authority, stop rather than altering Story 0079 behavior.
- If the public Dashboard contract requires raw provider or Market Data
  payloads, stop and revise the contract before implementation.
- If factorizing the valuation client changes Risk Domain behavior, preserve
  existing behavior first and isolate the Dashboard extension.
- Do not implement partial equity as a complete value merely to keep the UI
  populated.

## Documentation Reconciliation

Expected outcome: update this Story's implementation report and any canonical
API/architecture documentation only if the Dashboard contract or service
integration changes require it. No broad documentation rewrite is planned.

## Gate

Implementation Plan is complete and awaits explicit human approval. No code
changes or delegated implementation should begin until this plan is approved.

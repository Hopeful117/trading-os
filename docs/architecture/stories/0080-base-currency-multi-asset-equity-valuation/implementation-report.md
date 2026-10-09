# Implementation Report - Story 0080

## Implemented

- Added a shared `market_data.valuation.MarketValuationPort` contract and kept
  the existing Risk Domain port as a compatibility facade.
- Added `AccountBalanceValuationService` to value all non-zero account assets in
  the reporting currency through the existing Market Data valuation adapter.
- Aggregated identity, direct, and inverse valuation facts with explicit
  complete/incomplete/unavailable status and valuation timestamp/policy data.
- Extended the Dashboard account summary with valuation status, timestamp, and
  policy version without exposing provider payloads.
- Propagated valuation failure as degraded/unavailable Dashboard state instead
  of fabricating equity or risk percentages.
- Canonicalized Kraken Bitcoin aliases (`XXBT` and `BTC`) to the persisted
  Market Data asset symbol `XBT` for balance valuation and position matching.
- Added an explicit normalized broker `PositionPnlTreatment` so position PnL
  is either additive or declared already included in balances; the current
  broker mapping declares the existing additive semantics.
- Preserved broker-equity preference and divergence handling.
- Added focused tests for multi-asset aggregation, incomplete valuation,
  unavailable equity, and normalized broker PnL semantics.

## Files Changed

### Trading Core

- `dashboard/service/AccountBalanceValuationService.java`
- `dashboard/service/AccountValuationResult.java`
- `dashboard/service/AccountEquityService.java`
- `dashboard/service/AccountEquityResult.java`
- `dashboard/service/DashboardQueryService.java`
- `dashboard/model/AccountDashboardSummary.java`
- `dashboard/integration/BrokerAccountFact.java`
- `dashboard/integration/BrokerDashboardMapper.java`
- `dashboard/integration/PositionPnlTreatment.java`
- `market_data/valuation/MarketValuationPort.java`
- `risk/application/port/MarketValuationPort.java`
- corresponding Dashboard and mapper tests

### Market Data

No production behavior changed. Existing valuation snapshot behavior is reused.

### Frontend

No frontend change was required. The added backend fields are additive and the
frontend continues to display backend-calculated equity.

## Validation

- `./mvnw -q -Dtest=DashboardQueryServiceTest,AccountBalanceValuationServiceTest,AccountEquityServiceTest test`
  from `trading-core`: passed.
- Follow-up regression tests for PAPER authority, explicit broker-total trust,
  missing balances, null amounts, and position-currency fail-closed behavior:
  passed.
- Dashboard test suite: passed.
- `./mvnw -q verify` from `trading-core`: passed.
- `./mvnw -q verify` from `market-data`: passed.
- `git diff --check`: passed.
- Existing test logs contain expected warnings from malformed-data and negative
  integration scenarios; no test failure was reported.
- No authenticated runtime scenario with a live multi-asset account was
  executed.

## Documentation Reconciliation

Updated the canonical Story status and added this implementation report. No
broader architecture or API documentation update was required because the
Dashboard contract extension remains provider-neutral and internal valuation
transport is unchanged.

## Vault Outcome

The Obsidian vault was not consulted. No vault action is proposed.

## Review Status

The independent code review initially found five major defects. The follow-up
implementation addresses them and the targeted regression tests pass. Human
acceptance remains pending. No commit or Git integration action was performed.

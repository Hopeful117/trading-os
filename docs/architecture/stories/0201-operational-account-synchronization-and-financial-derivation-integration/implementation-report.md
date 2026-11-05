# Story 0201 - Implementation Report

## Implemented

- Exposed provider-neutral partial synchronization facts through Broker Service,
  Trading Core, persistence and Angular account views.
- Reused `CTraderDerivedEquityExperiment` without making its result authoritative
  or admissible for FTMO risk.
- Added Flyway migration `V53` for the derived financial diagnostic.
- Preserved explicit availability states and unavailability reasons instead of
  converting unavailable values to numeric zero or empty authoritative data.
- Added operational synchronization feedback and diagnostic-only messaging in
  the account UI.

## Runtime Evidence

Validation was performed against the existing authenticated FTMO cTrader account
`49411967` / broker account
`7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456` through the local Trading OS UI.

- Trading Core and Broker Service started successfully.
- Flyway applied migration `V53` successfully.
- Operational synchronization returned `SYNCED_PARTIAL`.
- Snapshot state was `INCOMPLETE / FRESH`.
- Balance was visible as `50000 USD`.
- Positions and orders were `AVAILABLE_EMPTY`.
- Deals were `UNAVAILABLE_OR_ABSENT`.
- Unavailability reasons included deal history, equity, used margin and free
  margin limitations.
- UI displayed `FINANCIAL_DATA_PARTIAL`, `RISK_EVIDENCE_INCOMPLETE` and
  `TRADING_NOT_AUTHORIZED`.
- Corrected the Account API projection so a persisted equity value is not
  exposed for an `INCOMPLETE` broker snapshot; unavailable current equity is
  now represented as `null` while the persisted historical value is retained.
- Market Data authorization was completed for the cTrader account ending in
  `780`, whose provider identifier is `49411967`; the active Market Data token
  now matches the configured account.
- The active FTMO Challenge was visible as `READY` with four cTrader markets in
  the eligible universe.
- A bounded all-eligible market scan completed with live Market Data. EUR/USD
  and GBP/USD were evaluable; US30/USD and XAU/USD were excluded for
  insufficient history. A EUR/USD opportunity was observed without execution
  authorization.
- No provider mutation was performed.
- Database verification found zero `execution_intent`, `execution_attempt` and
  `execution_broker_order` rows during this validation.

## Blocked Validation

The active Challenge and eligible markets are now available. The account-scoped
operational synchronization action was attempted through the normal UI, but
returned HTTP `409 Conflict` because the existing authoritative broker snapshot
was still protected by the broker safety window. The existing projection remained
`ACCOUNT_SYNCHRONIZED` with `INCOMPLETE / FRESH` facts.

A representative manual Trade Plan was then prepared locally with EUR/USD
market data and submitted only to the Trade Planning API. The API returned HTTP
`403 Forbidden`, so no Trade Plan was created and T0/T1 could not be entered.
Runtime logs refine the blocker: the authenticated user owns the FTMO account,
but that account has no effective Trade Planning Profile, so
`ManualTradePlanOrchestrationService` cannot continue at profile resolution.
No profile was created or assigned automatically because its risk budget is a
product decision.

Following explicit confirmation, profile `847e0717-4482-4912-9bed-89d583afb4ef`
version `1` was created and assigned to the FTMO account. It uses a `125 USD`
per-plan risk budget, `MARKET` entry, `1%` stop preference, `2R` target,
`INTRADAY` horizon and `4h` validity. A manual EUR/USD Trade Plan was then
created successfully in Trading Core as `0cc15278-53a3-3fc4-8b52-54e419df369b`
version `1`, status `PROPOSED`, with `112.50 USD` monetary risk. Following
explicit human acceptance, the plan advanced to version `2` with status
`ACCEPTED`. The T0 risk evaluation returned `CONTEXT_UNAVAILABLE` with blocking
reason `DEPENDENCY_UNAVAILABLE`, because the required broker risk context is
still unavailable. Runtime logs identified two sequential causes: the market
catalogue contained multiple `EUR/USD` entries and valuation selected by symbol
only; after correcting that ambiguity, the broker snapshot was protected by the
configured `PT20M` cTrader snapshot cooldown and returned
`BROKER_SNAPSHOT_DEFERRED`. No execution intent was created and no provider
mutation was performed.
The refusal was not bypassed and no provider order endpoint was called.

The account dashboard also returned HTTP `409 Conflict` because the last broker
snapshot was protected by the existing broker safety window. The UI rendered
this as `Broker refresh temporarily deferred`, which is the expected fail-closed
behavior.

## Validation

- Broker Service compile: passed.
- Trading Core compile: passed.
- Targeted Broker Service tests: passed.
- Targeted Trading Core tests: passed.
- Additional Trading Core mapper/synchronization/controller tests after the
  partial-equity projection correction: passed.
- Angular CI suite: passed, 56 files / 563 tests.
- Angular production build: passed.
- Docker Compose image build: passed.
- Container startup: passed.
- Flyway `V53`: applied successfully.
- `git diff --check`: passed.
- Market Data authorization: completed for provider account `49411967`.
- Runtime scan: completed with two evaluable markets and two insufficient-history exclusions.
- Manual Trade Plan API: profile assigned and EUR/USD plan accepted as version
  `2`.
- T0 risk evaluation: `CONTEXT_UNAVAILABLE` / `DEPENDENCY_UNAVAILABLE`; the
  execution intent list remained empty.
- Corrected market valuation resolution to use provider and product type when a
  symbol is ambiguous; targeted valuation tests pass. A runtime retry remains
  deferred until the existing cTrader snapshot cooldown expires.

The complete Trading Core Maven suite remains subject to the repository's known
PostgreSQL integration timeout limitation.

## Safety

`CTRADER_SAFETY_EXECUTION_ENABLED` remained disabled. No order submission,
cancellation, amendment or close operation was executed.

## Documentation Outcome

Story-local documentation was updated to replace the stale missing-Challenge
blocker with the current runtime evidence. No ADR, policy, provider contract or
execution safety setting was changed.

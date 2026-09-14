# Implementation Plan - Story 0038

## Phase A - Persisted Regression

- Add a Spring Boot integration test with the `test` profile.
- Create the user, PAPER BrokerAccount, financial Account, and initial balance
  through the existing repositories/schema.
- Execute a deterministic BUY through `ExecuteTradeService`.
- Clear the persistence context and reload all relevant state.

## Phase B - Persistence Wiring

- Persist broker-order fills in a dedicated table and repository.
- Load fills when rehydrating BrokerOrder.
- Ensure the existing Account-to-Trade relationship persists the settlement
  projection.

## Phase C - Validation

- Assert terminal execution state, PAPER ownership and mode, no live broker
  call, order/fill values, balances, equity, and open trade projection.
- Run focused execution tests, the complete Trading Core suite, `verify`, and
  `git diff --check`.

## Non-Goals

- No new PAPER semantics or LIVE/PAPER uniqueness rules.
- No Broker Service, frontend, sandbox, or network changes.
- No changes to broker status contracts or position architecture.

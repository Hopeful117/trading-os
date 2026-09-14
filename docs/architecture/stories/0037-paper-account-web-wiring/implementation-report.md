# Implementation Report - Story 0037

## Status

Implemented and ready for human review. No commit, push, or merge was made.

## Trading Core Changes

- `BrokerAccountResponse` now exposes `executionMode`.
- `BrokerAccountService` injects the owner reference into the PAPER financial
  `Account` before saving it.
- Broker-account response and service tests were updated for the contract.
- A regression test verifies PAPER creation, owner association, initial equity,
  and PAPER response mode.

## Angular Changes

- Added `ExecutionMode = LIVE | PAPER` to the broker-account model.
- Added `BrokerAccountService.createPaper()`.
- LIVE creation explicitly sends `executionMode: LIVE`.
- The Accounts form now switches between credential fields and initial capital.
- PAPER creation skips credential submission and reloads broker/financial
  accounts after success.
- Broker-account rows display the execution mode.
- Added component and HTTP-service coverage for PAPER creation and validation.

## Behavior Delivered

```text
Accounts page
    -> PAPER + initial capital
    -> Trading Core POST /api/v1/broker-accounts
    -> persisted PAPER BrokerAccount
    -> persisted owner-scoped financial Account
```

The LIVE path remains:

```text
Accounts page
    -> LIVE + credentials
    -> create broker account
    -> submit write-only credentials
```

## Files Changed by This Story

### Trading Core

- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/api/BrokerAccountResponse.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/application/BrokerAccountService.java`
- broker-account API/application tests

### Angular

- `trading-os-web/src/app/core/models/broker-account.model.ts`
- `trading-os-web/src/app/core/services/broker-account.service.ts`
- `trading-os-web/src/app/core/services/broker-account.service.spec.ts`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.ts`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.html`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.spec.ts`

## Deliberately Excluded Files

Unrelated Broker/Kraken refactoring, IDE changes, investigation reports, and
position-close test changes were pre-existing and were not part of this Story.

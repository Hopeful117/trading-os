# Implementation Plan - Story 0037

## Phase A - Trading Core Contract

- Add `executionMode` to `BrokerAccountResponse`.
- Ensure the PAPER financial Account receives the authenticated User before
  persistence.
- Update affected constructor fixtures and response tests.

## Phase B - Angular Contract

- Add `ExecutionMode` to the broker-account model.
- Add `createPaper()` to `BrokerAccountService`.
- Include `executionMode: LIVE` in the existing LIVE creation request.

## Phase C - Accounts Page

- Add a mode selector and conditional initial-capital/credential fields.
- Validate positive initial capital for PAPER locally.
- Show explicit PAPER success/error feedback.
- Display the returned mode in the broker-account list.

## Phase D - Validation

- Add backend ownership and PAPER creation assertions.
- Add Angular service and component tests for PAPER and LIVE paths.
- Run focused suites, complete Trading Core suite, complete Angular suite,
  Angular build, formatting, and `git diff --check`.

## Non-Goals

- No execution pipeline redesign.
- No new account uniqueness semantics.
- No provider contract normalization.
- No Broker Service or external network change.

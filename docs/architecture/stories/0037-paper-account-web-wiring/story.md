# Story 0037 - PAPER Account Web Wiring

## Metadata

**ID:** `0037`

**Title:** Wire PAPER account creation and mode visibility in the web application

**Status:** Review

## Goal

Allow an authenticated trader to create a PAPER BrokerAccount from the
Accounts page, provide its initial capital, distinguish it from a LIVE
account, and see the resulting mode without entering live broker credentials.

## Context

The PAPER Account / Simulated Execution milestone is merged into `main`.
Trading Core already accepts `ExecutionMode.PAPER` and `initialCapital`,
creates the simulated financial account, routes execution to the simulator,
and settles fills deterministically.

The web application still treated every broker account as a credential-backed
LIVE account and did not expose the execution mode returned by the backend.

## Problem

The existing Accounts page could not create a PAPER account because it did not
send the required execution mode or initial capital and always required API
credentials. The broker-account response also omitted the mode, so the user
could not distinguish LIVE and PAPER accounts in the UI.

## Scope

- Propagate `executionMode` in the Trading Core broker-account response.
- Preserve the owner when creating the PAPER financial Account.
- Add LIVE/PAPER selection to the Angular Accounts form.
- Require positive initial capital for PAPER creation.
- Skip credential submission for PAPER accounts.
- Keep the existing LIVE credential validation flow unchanged.
- Display `LIVE` or `PAPER` in the broker-account list.
- Add focused backend and frontend regression tests.

## Out of Scope

- Changing LIVE/PAPER uniqueness rules.
- Adding a new Account-to-BrokerAccount domain relation.
- Changing execution, fill, balance, equity, or risk semantics.
- Selecting a BrokerAccount directly from the TradePlan page.
- BrokerOrderStatus contract alignment.
- Broker Service, cTrader, FTMO, sandbox, or frontend redesign work.

## Acceptance Criteria

- [x] The Accounts form exposes LIVE and PAPER modes.
- [x] PAPER creation requires a positive initial capital.
- [x] PAPER creation does not request broker credentials.
- [x] LIVE creation still submits credentials through the existing flow.
- [x] Broker-account responses expose `executionMode`.
- [x] A PAPER financial Account is associated with the authenticated user.
- [x] The broker-account list displays the execution mode.
- [x] Existing deterministic execution behavior is unchanged.
- [x] Focused and complete backend/frontend validations pass.
- [x] `git diff --check` passes.

## Constraints

- Preserve the existing Trading Core and Broker Service responsibility
  boundaries.
- Keep PAPER as an execution mode, not a broker provider.
- Keep credentials out of the PAPER path.
- Preserve reactive Angular patterns and existing account-page UX.
- Do not introduce a new dependency or persistence model.
- Do not commit, push, or merge automatically.

## Relevant ADRs

- `docs/implementation/ADR-029-implementation.md` - execution pipeline
- `docs/implementation/ADR-028-implementation.md` - deterministic risk
- `docs/architecture/stories/0036-close-execution-feedback-loop/story.md` -
  execution feedback context

## Relevant Modules

- `trading-core`
- `trading-os-web`

## Validation

- Focused Trading Core broker-account tests.
- Complete Trading Core Maven suite.
- Complete Angular test suite.
- Angular production build.
- Prettier check.
- `git diff --check`.

## Definition of Done

- [x] Repository Analysis completed.
- [x] Implementation completed.
- [x] Relevant validation executed.
- [ ] Diff reviewed and accepted by the human engineer.
- [ ] Human commit created.

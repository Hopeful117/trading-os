# Repository Analysis - Story 0037

## Repository Checkpoint

| Field | Value |
|---|---|
| Branch | `story/0037-paper-account-web-wiring` |
| Baseline main | `03e3a6fbcea8d07402408bc1206dd09af72af5fd` |
| Origin main | `03e3a6fbcea8d07402408bc1206dd09af72af5fd` |
| Worktree before this Story | Dirty with unrelated Broker/Kraken changes |
| Story status | Review; not committed or merged |

## Existing Backend Contract

Trading Core already exposes:

- `CreateBrokerAccountRequest.executionMode`;
- `CreateBrokerAccountRequest.initialCapital`;
- PAPER account creation and settlement;
- LIVE/PAPER execution routing;
- deterministic simulated execution.

The gap was that `BrokerAccountResponse` did not return `executionMode` and
the PAPER-created financial `Account` was not assigned to the authenticated
user. The latter prevented normal account-scoped queries from finding the
created account.

## Existing Frontend Contract

The Accounts page already loaded broker accounts and submitted a LIVE
credential-validation flow. It had no mode selector, no initial-capital input,
and no PAPER branch. The Angular broker-account model also omitted the mode.

The frontend already uses Angular reactive forms, typed service methods, and
Observable-driven account loading. The implementation can follow these
conventions without a UX redesign.

## Boundary Decision

The Story does not add a direct Account-to-BrokerAccount relation. TradePlan
execution continues to use the existing backend-authoritative resolution of
the trading account and broker account. Adding a new relation would be a
separate domain decision.

## Readiness and Result

The gap was mechanical DTO/API/frontend propagation plus an existing ownership
propagation omission. The Story was safe to implement without a new domain or
architecture decision.

`IMPLEMENTATION_STATUS = COMPLETE_PENDING_HUMAN_REVIEW`

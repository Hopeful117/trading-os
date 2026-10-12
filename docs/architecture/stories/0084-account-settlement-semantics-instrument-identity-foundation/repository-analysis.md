# Repository Analysis - Story 0084

## Status

`RECORDED AFTER IMPLEMENTATION - HUMAN APPROVAL NOT INFERRED`

## Baseline

- Repository: Trading OS
- Starting branch: `main`
- Starting HEAD: `ab915ab81dd57fb4968f511f5f5f7c3625812ef`
- Starting worktree: clean
- Affected modules: `trading-core`, `market-data`
- DevLog: unavailable because the provider quota was exhausted

## Relevant Architecture

- Trading Core owns Accounts, execution orchestration, and user-specific
  settlement workflows.
- Market Data owns canonical market identity and product metadata.
- Risk, Challenge, Broker Service, and the frontend must not own settlement
  semantics.
- Existing PAPER settlement is inventory-based and must remain unchanged.

## Current Gaps

- Account balances did not declare whether they represented inventory or cash
  settlement semantics.
- Market identity was scoped only by provider and symbol.
- The repository had no deterministic Account/Product compatibility policy.
- Existing historical LIVE accounts could not be classified safely from current
  provider and execution-mode data.

## Required Boundaries

- Add `INVENTORY`, `CASH`, and fail-closed `UNKNOWN` account semantics.
- Add `SPOT` and `LINEAR_PERPETUAL` market product identity.
- Preserve `INVENTORY + SPOT` compatibility.
- Represent `CASH + LINEAR_PERPETUAL` without implementing derivative settlement.
- Reject unsupported and ambiguous combinations before financial mutation.
- Keep derivative accounting, mark-to-market, funding, leverage, liquidation, and
  Challenge redesign out of scope.

## Validation Needs

- Unit coverage for the compatibility predicate.
- Production-path settlement coverage for unsupported product/account pairs.
- Migration coverage for historical account and market classification.
- Persistence coverage for product-scoped market uniqueness.
- Full independent module verification with JaCoCo and diff checks.

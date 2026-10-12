# Story 0086 - Implementation Report

## Result

Implemented provider-neutral PAPER CASH derivative valuation while preserving
LIVE broker authority and INVENTORY/SPOT settlement behavior.

## Repository Baseline

- Branch: `main`
- Starting HEAD: `d783e3652d3fa50de0f2bcdf307a69fe359e5d82`
- Starting worktree: clean
- No commit, push, merge, reset, or checkout performed.
- DevLog context remained unavailable because the provider quota was exhausted.

## Implemented

- Created ADR-052 defining realized-state and equity authority.
- Added `Account.realizedBalance`, valuation status, valuation timestamp, and
  market-data timestamp.
- Added Flyway V27 migration that initializes realized balance only for existing
  PAPER CASH accounts from persisted Story 0085 equity and leaves INVENTORY,
  LIVE, and UNKNOWN accounts untouched.
- Initialized the baseline for newly provisioned PAPER CASH accounts.
- Added `PaperDerivativeAccountValuationService` using fresh Market Data bid/ask
  snapshots and midpoint valuation prices.
- Recomputed equity as realized baseline plus aggregate OPEN-trade P&L.
- Updated `Trade.currentPrice` only after successful valuation.
- Refreshes PAPER CASH valuation before Risk facts are assembled and fails closed
  for unavailable, stale, ambiguous, unsupported, or incomplete market data.
- Updated CASH entry and exit settlement to mutate realized balance once and
  recompute equity without accumulating prior unrealized P&L.
- Updated PAPER CASH Challenge BALANCE progression to use realized balance,
  keeping unrealized profit out of progression.
- Guarded legacy generic equity mutation so only PAPER CASH accounts use the
  realized-baseline path; LIVE and INVENTORY behavior remains separate.
- Added focused accounting, repeated/reversing valuation, freshness, migration,
  Risk-facts, progression, and optimistic-conflict coverage.

## Documentation Outcome

Created the canonical Story 0086 artifacts and ADR-052. No unrelated product,
API, frontend, or operational documentation required updating.

## Vault Outcome

The Obsidian vault was not consulted. No vault action is proposed; the Story is
fully governed by the approved Story, repository code, and accepted ADRs.

## Validation

- Focused Story tests passed:
  `PaperDerivativeAccountValuationServiceTest` (5),
  `PaperCashValuationMigrationTest` (1),
  `PaperCashDerivativeSettlementTest`,
  `ModeAwareRiskFactsProviderTest` (10),
  `ChallengeEvaluatorTest` (7),
  `AccountServiceImplTest`, and `TradingServiceImplTest`.
- `mvn -q verify` in `trading-core` passed.
- `git diff --check` passed.
- SonarQube analysis was not executed; no repository command or authenticated
  analysis result was available for this working-tree change.

## Residual Limitations

- V1 uses midpoint bid/ask and does not introduce a scheduler or valuation
  history.
- The concurrency test validates optimistic persistence conflict handling; a
  deployed multi-process race test remains future integration coverage.
- No frontend changes were made.

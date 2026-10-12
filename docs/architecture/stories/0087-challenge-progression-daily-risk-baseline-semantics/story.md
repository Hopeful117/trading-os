# Story 0087 - Challenge Progression and Daily Risk Baseline Semantics

## Metadata

**ID:** `0087`
**Status:** IMPLEMENTATION IN PROGRESS
**Related Stories:** `0078`, `0079`, `0084`, `0085`, `0086`
**Related ADRs:** `ADR-028`, `ADR-031`, `ADR-051`, `ADR-052`

## Goal

Make Challenge progression and daily Risk baseline semantics generic and
correct for future provider-neutral prop-firm configurations while preserving
Risk Domain authority and the existing architecture.

## Context

Stories 0084-0086 established explicit Account settlement semantics and
authoritative PAPER CASH valuation:

```text
realizedBalance = realized settlement balance
equity = realizedBalance + OPEN-trade unrealized P&L
```

The Challenge model currently supports only `BALANCE` progression. The Risk
system already has timezone-aware Risk Days and immutable per-day baselines, but
account-monitoring baseline creation currently uses static Account starting
capital instead of the authoritative balance observed at the first evaluation
of each Risk Day.

## Problem

Future Challenge definitions must be able to:

- progress from authoritative `BALANCE` or `EQUITY`;
- declare required Account settlement semantics;
- fail closed when authoritative equity is unavailable or stale;
- use the exact Challenge Risk Policy and its effective Risk-day semantics;
- evaluate daily drawdown against the immutable authoritative balance captured
  for the current Risk Day;
- preserve the static total-drawdown starting reference.

## Scope

### Challenge progression

- Add generic `ProgressionValueSource.EQUITY` while preserving `BALANCE`.
- Use authoritative fresh Story 0086 equity for EQUITY progression.
- Never substitute realized balance for unavailable or stale EQUITY.
- Preserve Risk-breach-before-profit-target ordering.
- Keep Challenge active/non-completed when required equity is unavailable.
- Expose the same semantics in the read projection.

### Settlement compatibility

- Add optional provider-neutral `requiredSettlementModel` to
  `ChallengeDefinition` using the existing `SettlementModel` enum.
- Preserve nullable compatibility for historical definitions when required.
- Validate Account settlement compatibility before creating a ChallengeInstance.
- Never mutate the Account settlement model automatically.

### Daily Risk baseline

- Capture the authoritative Account balance on the first authoritative Risk
  evaluation of a Risk Day when no baseline exists.
- Reuse the existing immutable baseline for all later evaluations in that day.
- Capture a new balance for the next Risk Day.
- Preserve the static Account/Challenge starting reference for total drawdown.
- Reuse `RiskDay`, existing timezone/reset configuration, and baseline
  persistence invariants.
- Record provenance for the first-evaluation capture.
- Preserve duplicate/concurrent first-writer behavior.

### Configuration

- Keep Risk timezone and reset time provider-neutral and configurable.
- Do not infer reset configuration from broker provider, execution mode, symbol,
  or plan code.
- Make the ownership and effective configuration path explicit in the
  implementation plan before code changes.

## Out of Scope

- Kraken-specific Challenge definitions or configuration.
- Provider-specific branches or provider-name business rules.
- Story 0088 product restrictions or pre-execution product authorization.
- Story 0089 provider-specific Prop Advanced configuration.
- Frontend changes.
- Historical Risk-Day reconstruction.
- Scheduler or event framework for reset processing.
- Trailing or Lite drawdown semantics.
- Leverage, margin, liquidation, funding, partial closes, hedging, or
  multi-collateral accounting.
- Payouts, funded-account lifecycle, real Prop API integration, Quant, ML, or
  AI behavior.
- Moving Risk formulas into Challenge code.

## Architectural Constraints

- Risk Domain remains authoritative for daily and total drawdown calculations.
- Challenge owns definitions, lifecycle, progression target, and required
  settlement semantics only.
- Account remains authoritative for financial state.
- `BALANCE` must not be silently redefined as `EQUITY`.
- EQUITY progression must fail closed for stale, unavailable, invalid, or
  non-authoritative valuation.
- Daily baselines use authoritative balance, never current equity or
  `max(balance, equity)`.
- Total drawdown must continue using the static starting reference.
- Historical Challenge definitions and Risk baselines must not be silently
  reinterpreted.
- Existing PAPER INVENTORY and LIVE behavior must remain unchanged.
- Challenge start must reject settlement mismatch before creating an instance.

## Acceptance Criteria

### Progression

- Existing BALANCE progression remains unchanged.
- EQUITY below target keeps the Challenge ACTIVE.
- Fresh authoritative EQUITY reaching the target passes the Challenge.
- Realized balance reaching the target does not pass an EQUITY Challenge when
  equity is below target.
- EQUITY reaching the target can pass even when realized balance is below it.
- Stale, unavailable, invalid, or non-authoritative equity cannot pass a
  Challenge.
- A blocking daily or total drawdown breach wins over a simultaneous target.

### Settlement requirement

- Matching required settlement and Account settlement is accepted.
- A CASH-required Challenge rejects an INVENTORY Account before persistence.
- Historical nullable requirement behavior is explicit and tested.

### Daily baseline

- The first evaluation of a Risk Day creates one baseline from authoritative
  balance.
- Repeated evaluations reuse the same baseline.
- Intraday balance/equity changes do not mutate the baseline.
- The next Risk Day captures a new authoritative balance.
- Total drawdown reference remains static.
- Custom reset time and timezone boundaries are deterministic.
- Duplicate/concurrent baseline creation preserves the first stored baseline.

### Regression

- Accounts without an active Challenge continue to work.
- PAPER INVENTORY and LIVE semantics are not reinterpreted.
- Stories 0079, 0085, and 0086 tests remain green.
- Existing Risk evaluation behavior remains green.
- No provider-specific branch or duplicated Risk formula is introduced.

## Migration Strategy

- Use additive Flyway migrations.
- Preserve historical progression rows as BALANCE.
- Remove only the database restriction that currently rejects EQUITY.
- Keep `requiredSettlementModel` nullable for historical definitions unless a
  deterministic migration value is established.
- Do not rewrite historical Risk baselines.

## Validation

Run all affected module validations, including:

```bash
mvn -q verify
git diff --check
```

Also run focused Challenge, Account Risk monitoring, Risk-day baseline, daily
drawdown, total drawdown, PAPER settlement, and PAPER valuation tests. Review
Flyway migrations and report JaCoCo/SonarQube results when configured.

## Stop Conditions

Stop before implementation if:

- Story 0086 authority cannot be verified;
- realized balance and equity cannot be safely distinguished;
- existing baseline persistence cannot support immutable per-Risk-Day rows;
- Challenge-effective Risk-day configuration requires an unsafe shared Account
  mutation or an unapproved architectural redesign;
- correct baseline capture requires unavailable historical reconstruction;
- concurrency guarantees cannot prevent conflicting baselines;
- provider-specific behavior would be required in a generic domain.

## Required Workflow

The Repository Analysis and Implementation Plan were approved before
implementation. Implementation remains uncommitted and requires human review
before finalization.

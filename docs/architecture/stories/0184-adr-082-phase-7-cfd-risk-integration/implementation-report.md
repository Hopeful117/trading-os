# Implementation Report - Story 0184

## Result

Implemented the CFD risk projection boundary in Trading Core. The change remains
uncommitted for human review.

## Changes

- Added `CfdRiskProjectionService` as a provider-neutral application-layer
  projection around `CfdFinancialCalculator`.
- CFD proposed trades now calculate multiplier-aware notional and stop-loss risk
  from the validated contract and explicit market conversion facts.
- CFD current positions now calculate market value and protected stop risk with
  the validated contract multiplier rather than the generic quantity-times-price
  shortcut.
- Reused persisted PAPER `Trade` contract snapshots when a current CFD position
  is locally identified.
- Resolved LIVE CFD contracts through the provider-neutral Market Data contract
  resolver while retaining Phase 6 provider account/position facts as current
  authority.
- Applied the same projection logic to T0 and T1 while preserving their
  independent market freshness acquisition.
- Kept proposed-order margin on `RequiredMarginPort`; current used margin is not
  substituted or used for inference.
- Added focused tests for multiplier-aware notional, stop risk, directed
  cross-currency conversion, and missing conversion fail-closed behavior.
- No files under `risk-domain` were changed.
- No settlement compatibility, migration, broker write, or provider mutation was
  introduced.

## Validation

Passed:

- `./mvnw -q -DskipTests compile` in `trading-core`.
- Focused CFD projection and calculator tests.
- Focused T0/T1 and mode-aware risk-facts tests.
- `./mvnw -q test` in `trading-core`.
- `./mvnw -q verify` in `trading-core`.
- `./trading-core/mvnw -q -f risk-domain/pom.xml verify` from repository root.
- `git diff --check`.
- Sonar issue search returned no open issues for the changed Trading Core files.

Failed or limited:

- SonarQube Quality Gate did not pass for the projects. Trading Core reported
  `new_coverage=71.0` and `new_violations=344`; Risk Domain reported three open
  violations. The changed Trading Core files had no issues in the targeted
  Sonar issue search. Existing out-of-scope Sonar findings were not modified.
- No live broker or cTrader runtime validation was performed.

The Maven test output contains expected warning/error logs from tests that
exercise failure paths; Maven completed successfully.

## Documentation Outcome

No product, runtime, API, or architecture documentation update was required.
The Story artifacts record the new implementation boundary and no external setup
or user-visible contract changed.

## Vault Outcome

No vault action. The vault was not consulted because the repository ADRs and
current source fully covered this repository-local change. No proposal-only
transverse-memory artifact is warranted.

## Delivery

Human review, commit, push, pull request creation, and merge remain pending.

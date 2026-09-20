# Repository Analysis - Story 0055

## Scope

Story 0055 addresses the mismatch between the default PAPER planning budget and
the effective risk profile. The previous default could create a plan with a
notional equal to the whole account balance, which was correctly rejected by
`MAX_EXPOSURE`.

## Current Evidence

* PAPER account creation already validates and stores the effective risk
  profile.
* Trade Planning Profile creation is the earliest stable point to derive a
  compatible PAPER risk budget.
* The implementation derives the budget from `MAX_POSITION_RISK`,
  `MAX_EXPOSURE`, initial capital, and the configured stop-distance ratio.
* Risk evaluation remains the final authority and continues to reject genuinely
  excessive exposure.

## Implementation Boundary

The change is limited to PAPER planning-profile budget derivation and its
regression tests. It does not modify risk thresholds, LIVE behavior, market
valuation, or the Risk Domain rule engine.

## Validation Expectations

* Trading Core sizing and account-service tests;
* Risk Domain regression tests;
* Angular tests and production build;
* authenticated PAPER runtime walkthrough;
* excessive-exposure negative validation;
* `git diff --check`.

## Historical Implementation

The implementation was delivered in commit `8cc2f75`.

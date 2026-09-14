# Code Review - Story 0040

## Review Status

Prepared for human review. This document is not an approval or merge decision.

## Review Scope

- Neutral application Risk Facts boundary.
- Canonical T0/T1 Account and BrokerAccount identity.
- LIVE and PAPER authority routing.
- T1 financial Account persistence.
- Fail-closed missing PAPER facts.
- Market Data and RequiredMarginPort preservation.
- Distinct-identity and mode-routing regressions.

## Findings

No blocking implementation defect was identified in the completion pass.

The following corrections were made during review:

- T1 unavailable persistence no longer stores `intent.brokerAccountId` as the
  financial Account ID.
- Missing canonical Account relation now fails closed.
- Facts and required-margin requests use the resolved canonical BrokerAccount
  ID rather than configuration as the routing authority.
- `RiskFactsProvider` now exposes neutral snapshot records instead of exposing
  `BrokerRiskFactsPort` snapshot types to the orchestration services.
- T0 and T1 reject contradictory legacy provider metadata.
- Unknown or missing execution mode no longer falls through to PAPER routing.

## Residual Risks

- PAPER ledger, margin, and protection facts are intentionally unavailable.
- Existing PAPER settlement and position lifecycle limitations remain outside
  Story 0040 scope.
- Full successful T0/T1 PAPER evaluation cannot be demonstrated until the
  missing authoritative local facts are implemented under a future approved
  scope.
- The PAPER adapter timestamp remains diagnostic because its snapshot is
  intentionally incomplete; no freshness threshold was introduced.
- Existing unrelated worktree changes require separate human diff review.

## Validation

- Focused Story 0040 tests passed.
- Full Trading Core test suite passed.
- `git diff --check` passed.

## Approval

Human code-review approval: pending.

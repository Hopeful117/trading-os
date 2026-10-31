# Code Review - Story 0184

## Scope Reviewed

Reviewed the Story artifacts, the complete local diff, the new CFD projection,
T0/T1 integration, existing margin normalization, provider authority boundaries,
and focused regression tests.

## Findings

No blocking correctness finding was identified in the changed implementation.

The review verified:

- CFD arithmetic remains in Trading Core application composition and reuses
  `CfdFinancialCalculator`.
- The Risk Domain remains unchanged and provider-neutral.
- Contract multipliers are applied to proposed notional, current position value,
  and stop-loss risk.
- Cross-currency conversion requires explicit directed conversion legs and
  preserves the market source version in conversion provenance.
- PAPER contract resolution uses local persisted Trade contract facts.
- LIVE contract resolution uses the provider-neutral Market Data boundary while
  current account and position facts remain provider-authoritative.
- Required proposed-order margin remains separate from current used margin.
- T0 and T1 use the same projection semantics but retain separate market
  acquisition calls and freshness policies.
- Existing settlement compatibility remains unchanged, including CFD rejection.
- No provider payload, cTrader field, broker mutation, migration, or Risk Domain
  change was introduced.

## Residual Risk

- The new direct tests cover the projection component but do not exercise a full
  persisted CFD T0/T1 evaluation against a live provider snapshot. Runtime
  integration remains dependent on the existing contract resolver and Phase 6
  V2 snapshot deployment.
- SonarQube Quality Gates remain red because of project-level coverage and
  existing violations. The targeted changed-file issue search returned no open
  issue, and unrelated Sonar findings were left out of scope.
- No authenticated cTrader or broker runtime validation was performed.

## Review Recommendation

The implementation is ready for human review of the diff. The SonarQube gate
failure and the lack of live runtime validation must remain visible before any
commit or delivery decision.

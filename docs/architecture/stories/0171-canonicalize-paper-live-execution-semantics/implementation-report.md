# Story 0171 - Implementation Report

## Status

`IMPLEMENTED_LOCALLY - AWAITING REVIEW`

The approved semantic clarification is implemented locally. No external broker
order, cTrader mutation, runtime account data, or Challenge monitoring behavior
was changed.

## Implemented

- Documented `ExecutionMode` as an execution-boundary distinction.
- Added ADR-078 defining the canonical PAPER/LIVE rule and separating it from
  provider `isLive`, demo status, economic environment, and execution permission.
- Added the same guardrail to `AGENTS.md`, including the FTMO/cTrader example.
- Added a focused regression proving the simulated adapter rejects a `LIVE`
  account before requesting market data.
- Added a cTrader/FTMO DEMO-shaped provisioning fixture proving imported
  provider-backed accounts are represented as `ExecutionMode.LIVE`.
- Added direct routing regressions proving `LIVE` invokes the external adapter
  for submission, cancellation, and reconciliation, and never invokes the
  simulator.
- Added Accounts UI wording explaining that PAPER is internal simulation and
  LIVE is an external broker boundary, not necessarily real money.
- Kept existing routing architecture, Risk Facts behavior, cTrader binding,
  runtime captures, and Challenge monitoring unchanged.

## Changed Files

- `AGENTS.md`
- `docs/architecture/adr/ADR-078-paper-live-execution-boundary.md`
- `docs/architecture/stories/0171-canonicalize-paper-live-execution-semantics/`
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/domain/ExecutionMode.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/brokeraccount/application/BrokerAccountProvisioningServiceTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/execution/ModeSpecificExecutionBoundaryTest.java`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.html`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.spec.ts`

## Validation

Passed:

- `./mvnw -q -Dtest=ModeSpecificExecutionBoundaryTest,BrokerAccountProvisioningServiceTest test`
- `./mvnw -q -Dtest=PaperExecutionVerticalRegressionTest test`
- `npm run test:ci -- --include='src/app/features/accounts/pages/accounts/accounts.spec.ts'`
  - 33 tests passed.
- `npm run build`
- `git diff --check`

The Angular build completes successfully but reports existing bundle and SCSS
budget warnings. No new external execution path was exercised.

The full `./mvnw -q test` suite was also attempted. It could not complete
cleanly because the environment did not provide the PostgreSQL Testcontainers
ports required by existing migration and persistence integration tests
(`localhost:33054`/`localhost:33056` connection refused). The focused tests
above passed independently.

## Open Scope Decision

`ActiveChallengeMonitoringService` still filters to PAPER accounts. Extending
monitoring to connected LIVE FTMO/cTrader accounts remains a separate product
decision and was intentionally not included in Story 0171.

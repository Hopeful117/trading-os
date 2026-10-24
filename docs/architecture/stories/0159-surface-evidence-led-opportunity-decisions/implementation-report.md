# Implementation Report - Story 0159

## Outcome

Implemented the evidence-led Opportunity decision surface in the existing
Trader Cockpit.

The Cockpit now presents the active Opportunity as:

```text
Understand → Challenge → Decide
```

The implementation reuses the existing Opportunity and Trend Context contracts.
It does not add backend endpoints, persistence, Risk logic, Trade Plan logic,
or execution behavior. A narrowly scoped backend data-repair migration was
added after runtime validation exposed legacy account metadata incompatible
with the current broker-account contract.

## Changes

- Added structured Opportunity evidence presentation for direction, scenario,
  explanation, type, timeframe, status, score, setup description, setup
  triggers, validity timestamps, origin, evaluation time, observations, and
  analysis provenance.
- Added explicit Opportunity validity presentation for valid, not-yet-valid,
  and expired states.
- Prevented expired and not-yet-valid Opportunities from entering the existing
  TradePlan creation path.
- Prevented non-`ACTIVE`, malformed, and boundary-expired Opportunities from
  being presented as actionable.
- Cancelled and ignored in-flight TradePlan preparation when account, market,
  Opportunity route, or component context changes.
- Added a workspace-local `NO TRADE` state with no backend mutation.
- Preserved `PREPARE TRADE` through the existing
  `TradePlanService.createFromOpportunity()` and PlanPage navigation.
- Cleared the presentation decision when account, market, or Opportunity
  context changes.
- Added responsive styling while keeping the existing workstation primitives and
  mobile layout conventions.
- Added explicit Opportunity loading/unavailable states and a three-stage
  `Understand → Challenge → Decide` flow with Trend Context evidence content.
- Added an explicit user-visible error state when existing TradePlan
  preparation fails.
- Added `V40__normalize_legacy_account_provider_metadata.sql` to normalize
  legacy `accounts.broker` values to the linked broker account's opaque
  provider registration reference.

## Files Changed

- `trading-os-web/src/app/features/decision-workspace/decision-workspace.ts`
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.html`
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.scss`
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.spec.ts`
- `trading-core/src/main/resources/db/migration/common/V40__normalize_legacy_account_provider_metadata.sql`
- `trading-core/src/test/java/com/hope/trading/trading_core/migration/PostgresFreshInstallMigrationTest.java`

## Validation

- `npm run test:ci`: passed, 54 test files and 535 tests.
- `npm run build`: passed.
- Prettier check: passed for all changed frontend files.
- `git diff --check`: passed.
- Trading Core targeted validation: passed, 17 tests with 0 failures and 0
  errors, including PostgreSQL fresh-install, historical-upgrade, and legacy
  provider normalization scenarios.
- Trading Core full validation: passed, 830 tests with 0 failures and 0
  errors.

The Angular build still reports pre-existing bundle and component-style budget
warnings. The modified DecisionWorkspace stylesheet is below its blocking 8 kB
component budget at 7.97 kB; no new build error remains.

The test environment emits existing jsdom canvas warnings because the optional
`canvas` package is not installed. Tests pass despite those warnings.

Independent code review completed with verdict `PASS`. No material engineering
issues remain from the available repository evidence.

Authenticated browser validation was executed against the running frontend on
port `4201` with the backend services available. The Cockpit loaded the active
`0G/EUR` Opportunity and rendered the complete `Understand → Challenge →
Decide` flow, including Opportunity provenance, validity, setup trigger, and
Trend Context unavailable state. The workspace-local `NO TRADE` action was
verified without a backend request. The narrow mobile viewport rendered the
responsive navigation, evidence stages, action controls, chart, order book,
and market information without layout failure.

`PREPARE TRADE` was revalidated against the running frontend and services after
applying the migration. The existing endpoint
`POST /api/v1/trade-plans/opportunities/{opportunityId}/trade-plans` returned
`200 OK`, and the browser navigated to the resulting `PROPOSED` Trade Plan
version for `0G/EUR`.

## Scope Review

- One backend database migration and its migration-history assertions changed
  to repair legacy runtime account metadata.
- No gateway, Risk, broker, execution, or market-data authority changed.
- No durable Opportunity-level decision was introduced.
- No commit, push, merge, or branch integration was performed.

## Review State

Implementation is ready for independent code review. The Story remains in
`Review` until that review and human integration are completed. No commit,
push, merge, or branch integration was performed.

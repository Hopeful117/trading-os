# Repository Analysis - Story 0198

## Scope Confirmation

Story `0198` is the next available Story number. The repository is currently on
`main` at `22569e1b`, aligned with `origin/main`, with a clean working tree.
Existing independent work was preserved in a named stash before the update to
`main`; it is outside this Story.

The requested work is validation-first. No implementation or real provider call
is authorized by this analysis artifact.

## Relevant Architecture

- Broker Service owns cTrader authentication, credentials, provider transport,
  account binding, read-only synchronization, provider mapping and execution
  adapter boundaries.
- Trading Core owns broker accounts, synchronization orchestration, Trade Intent,
  T0/T1 risk orchestration, execution state and reconciliation.
- Risk Domain remains deterministic and provider-neutral.
- Angular uses existing account, dashboard, market, trade-planning and execution
  services/components.
- Gateway routes public frontend traffic to Broker Service and Trading Core.

## Existing cTrader Entry Points

- `CTraderAuthenticationController` manages an existing account's OAuth start,
  callback, attempt status and external-account binding.
- `CTraderReadOnlyValidationController` exposes authenticated instrument,
  quote and instrument-metadata checks under `/internal/v1`.
- `CTraderAuthoritativeSnapshotCapability` provides the existing provider-neutral
  account snapshot boundary.
- `BrokerQueryControllers` exposes account, snapshot, positions, orders,
  capabilities and risk-snapshot queries.
- `AccountSynchronizationV2Controller` triggers authenticated Trading Core
  synchronization and live CFD reconciliation.
- Angular `BrokerAccountService` and `AccountService` already contain account,
  credential, authorization and synchronization paths.

## Safety Findings

- `docker-compose.yml` explicitly sets `CTRADER_SAFETY_EXECUTION_ENABLED=false`.
- Existing cTrader safety controls include request budgets, alert thresholds,
  snapshot cooldown and snapshot lease controls.
- Stories 0193-0197 preserve read-only provider behavior, capability-scoped
  facts, fail-closed risk evidence and unavailable equity semantics.
- ADR-074 requires durable local-account to external-account identity matching,
  environment matching, completeness/freshness classification and no fallback.
- ADR-084 keeps LIVE margin provider-authoritative and rejects invented equity,
  free-margin or account-margin formulas.
- ADR-086 allows independent invariant diagnostics but keeps aggregate execution
  fail-closed when mandatory evidence is incomplete or inadmissible.

## Account Identity Status

The repository does not prove the identity of the existing FTMO Free Trial
account. The value `49289512` appears in tests and historical fixtures, but test
data is not evidence of the currently configured account. No real account ID,
environment or credential may be inferred from it.

Before Phase A, inspect the normal running application's broker-account and
connection status mechanisms, resolve the authenticated owner's durable local
broker account, and compare the provider response account ID and environment.
If this cannot be done without exposing or recreating credentials, stop real
validation and report `BLOCKED`.

## Expected Data Boundaries

The existing cTrader observation can preserve balance, valuation asset, account
metadata, positions, orders, position PnL, deals, collection availability,
timestamps, completeness and explicit reasons. Equity remains provider-
authoritative/unavailable for normalized FTMO risk admission. Story 0197's
derived candidate remains experimental and must not be used for this validation.

The validation must record missing or unsupported fields rather than filling
them from derived values or fixtures.

## E2E Test Surfaces

### Real read-only validation

Use the existing account/credential/token mechanisms and only the existing FTMO
Free Trial account. Candidate checks include connection status, account snapshot,
instrument metadata, quote, symbols, positions, orders, PnL, deals and Trading
Core synchronization. Request safety cooldowns and budgets must be respected;
they must not be bypassed.

### Trade Intent and risk

Use the existing Angular/API path for Trade Intent creation. Evaluate the real
facts available to T0 and T1. Verify that independent invariants can produce
diagnostics while aggregate execution remains blocked when equity or another
mandatory fact is unavailable.

### Simulated execution

Use existing provider-neutral ports and execution orchestration with an isolated
simulated cTrader adapter or existing test double. Approval must come from the
actual T0/T1 services in the test scenario. Cover accepted, rejected, timeout,
unknown and reconciliation paths, especially duplicate-submission prevention.

### Angular

Validate existing account, market, decision-workspace, manual trade and
execution-history components and their services. Avoid UI redesign. If a local
functional defect is found, record the smallest compatible correction and obtain
the required implementation approval before changing code.

## Evidence Matrix Contract

The final report must use these exact result classes:

```text
PASS
FAIL
BLOCKED
NOT_TESTED
```

Every row must identify whether evidence came from a real cTrader call, a
simulated adapter, an integration test, a unit test or an environment check.
Fixtures and mocks must never be labelled as real provider validation.

## Risks and Blockers

- The target account is not yet identified with repository evidence.
- Credentials may be stored only in a running application/database and cannot be
  inspected or printed directly.
- Existing read-only requests may be blocked by persisted cooldown/lease safety
  controls; bypassing them is forbidden.
- No real order submission is allowed, so first-trade readiness cannot be
  declared solely from this Story.
- Angular E2E execution may require running services and authenticated browser
  state not available in the repository-only environment.
- DevLog context was unavailable after two same-key timeout attempts.

## Required Validation Before Implementation

1. Human approval of this Repository Analysis.
2. Runtime/account preflight proving the existing FTMO Free Trial identity and
   environment without exposing secrets.
3. An Implementation Plan if any local correction or test harness change is
   needed.
4. Human approval of that Implementation Plan before code changes.

Until these conditions are satisfied, the safe outcome is repository analysis
only and no real cTrader call.

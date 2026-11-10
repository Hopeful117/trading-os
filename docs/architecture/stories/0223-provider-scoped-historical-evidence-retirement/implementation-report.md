# Implementation Report - Story 0223

## Status

Implementation completed for the approved domain/preflight boundary. Physical
database retirement was not executed or authorized.

## Implemented

- Accepted ADR-091 and preserved its separate DBA authorization boundary.
- Added Market Data migration `V26__provider_scoped_historical_evidence_retirement.sql`.
- Added exact provider/symbol/market/depth retirement scope and business/
  operational state models.
- Added durable operation and append-only transition audit persistence.
- Added exact-scope preflight with mixed-scope rejection, dependency/hold
  inventory, deterministic identity fingerprints and PostgreSQL catalog safety
  checks.
- Added idempotent `PREPARED` and `SOURCE_RETIRED` transitions plus operational
  transition/reconciliation states.
- Added source-retired dependency state without changing ordinary `RELEASED`
  semantics.
- Added capture-boundary rejection for retired provider/symbol/market/depth
  scopes.
- Added replay `SOURCE_RETIRED` behavior and prevented promotion/reuse of
  source-retired artifacts using the exact provider/symbol/market/depth scope.
- Added authenticated Market Intelligence acknowledgement enforcement for the
  source-retirement transition.
- Added prepared-inventory fingerprint matching and server-derived audit actor
  enforcement for acknowledgement.
- Aligned the Market Data acknowledgement POST/GET response with the Feign
  `operationId/dependencyFingerprint/state/reason` contract.
- Added durable approval evidence migration `V27__source_retirement_approval_evidence.sql`.
- Added row locking around the capture retirement guard and conflict checks for
  repeated operational transitions.
- Added internal read-only preparation, status, source-retirement and
  operational-transition endpoints. No endpoint performs physical cleanup.
- Added Market Intelligence migration and durable acknowledgement service with
  `CONFIRMED` and `RECONCILIATION_REQUIRED` states.
- Extended the internal Market Data Feign contract for acknowledgement and
  status reads.
- Added focused scope and replay regression tests.

## Validation

Passed:

- `mvn -q -DskipTests compile` in `market-data`.
- `mvn -q -DskipTests compile` in `market-intelligence`.
- `mvn -q -Dtest=OrderBookReplayServiceTest,HistoricalEvidenceRetirementScopeTest test`
  in `market-data`.
- `mvn -q -Dtest=OrderBookRetentionPersistenceTest test` in `market-data`.
- Focused workflow, persistence and security integration tests in `market-data`.
- Added service tests for stale inventory rejection and authenticated actor
  attribution.
- `mvn -q -DskipTests package` in `market-intelligence`.
- `git diff --check`.
- PostgreSQL Testcontainers migration validation applied the new Market Data
  and Market Intelligence migrations successfully in isolated test databases.

Blocked or incomplete:

- Full `market-data` test execution still has one unrelated flaky cTrader
  reconnect failure; the retirement/replay tests pass.
- Full `market-intelligence` test execution exceeded the available command
  timeout while running its existing long integration suite; compilation and
  packaging passed.
- No production/runtime database validation was performed.
- No external backup restore was performed.
- No physical `TRUNCATE`, `DELETE`, `DROP`, or equivalent operation was run.
- No deployment, restart, broker operation, commit or push was performed.

## Documentation Reconciliation

Updated Story 0223 status and added the Repository Analysis, Implementation
Plan and this Implementation Report. ADR-091 now records explicit human
acceptance while retaining the no-physical-mutation boundary.

Added the Story-local `operational-runbook.md` covering backup/restore,
quiescence, exact-scope physical-operation safeguards, post-operation
validation and ambiguous-outcome reconciliation. The physical action remains a
future separately authorized DBA step.

## Vault Outcome

- Vault consulted: no.
- Outcome: no vault action.
- Rationale: all required architecture, implementation and validation context
  was available in the repository and accepted ADRs; no curated transverse
  knowledge was needed to implement this local Story.

## Residual Risks

- The Market Intelligence acknowledgement service is an internal application
  boundary and still requires deployed cross-service integration validation.
- Existing provider-neutral replay requests select and validate the actual
  provider identity before applying source-retirement guards; mixed sequences
  fail closed during preflight.
- Backup/restore evidence, quiescence and post-physical-operation validation
  remain operational prerequisites and are intentionally not proven here.

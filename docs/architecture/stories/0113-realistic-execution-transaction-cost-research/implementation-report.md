# Implementation Report - Story 0113

## Scope Completed

The evidence foundation and provider-neutral economic contract changes are
present in the current worktree. Formal follow-up Story authorization remains
required before integration. The implementation does not include a historical
execution simulator, Kraken acquisition adapters, public APIs, or PAPER
settlement integration.

## Changes

### Market Data

- Added `HistoricalFundingEvidence` and explicit captured/backfill origins.
- Added product identity, settlement asset, and effective funding intervals so
  historical funding observations carry their calculation semantics.
- Added deterministic funding evidence fingerprints that exclude capture-time
  metadata so retries identify the same source observation.
- Added immutable-style JDBC persistence with fingerprint idempotency.
- Added strict cutoff reads that require both funding time and `knownAt` to be
  eligible and exclude historical backfill from observed reads.
- Added Flyway migration `V7__historical_funding_evidence.sql`.

### Broker Service

- Added provider-neutral `HistoricalFeeSchedule` with product identity, effective intervals,
  maker/taker rates, source provenance, observation/known timestamps, origin,
  and fingerprint.
- Added deterministic fee schedule fingerprints.
- Added idempotent JDBC persistence and strict effective-time/`knownAt` reads for
  captured schedules.
- Added application and PostgreSQL exclusion constraints to reject overlapping
  captured schedules, including concurrent duplicate-safe inserts.
- Added Flyway migration `V3__historical_fee_schedule.sql`.
- Added Flyway migration `V4__prevent_overlapping_fee_schedules.sql`.

### Trading Core

- Added provider-neutral economic contracts for reference market state,
  strategy intent, simulated execution, transaction costs, and economic result.
- Added a deterministic market-order evaluator that uses one execution-impact
  component for spread/crossing, plus explicit fee and funding components.
- Added explicit `AVAILABLE`, `INSUFFICIENT_DATA`, `STALE`, `UNSUPPORTED`, and
  `INVALID_INPUT` outcomes, plus `DEGRADED` when cost provenance is incomplete.
- Economic results now retain fee/funding evidence fingerprints, sources,
  effective intervals, and known-at timestamps, and reject ineligible evidence.
- Added tests proving deterministic fingerprints, missing-evidence behavior,
  temporal rejection, and no separate double-counted slippage component.

### Provider-Neutral Economic Contract

- Evolved the existing economic foundation instead of creating a parallel domain.
- Added explicit `EconomicProductType`, `SimulatedExecutionStatus`,
  `CostComponentStatus`, and `CostCompleteness` vocabularies.
- Extended `SimulatedExecution` with requested, filled, and unfilled quantities,
  average execution price, and explicit execution status while preserving the
  existing single-fill construction path.
- Added `ExplicitCost` to distinguish available, unavailable,
  not-applicable, insufficient, and unsupported cost components.
- Extended `TransactionCosts` with commission/funding/other component states,
  transaction-cost policy version, and derived completeness.
- Extended `EconomicResult` with entry/exit lifecycle references, execution
  status, reference return, gross and execution-adjusted PnL, optional net PnL,
  and policy fingerprints. `NetReturn` remains unavailable because no universal
  denominator has been approved.
- Added `EconomicLifecycleEvaluator` for deterministic entry/exit aggregation;
  it does not acquire evidence or call infrastructure.
- Added strict cost-evidence eligibility at both lifecycle execution times.
- Preserved spread accounting in execution prices and did not add a second
  spread-cost deduction.

## Boundaries Preserved

- Funding evidence remains owned by Market Data.
- Account/provider fee schedule evidence remains owned by Broker Service.
- No provider DTOs were exposed through the new domain contracts.
- No current provider state or current fee configuration is used as a historical
  fallback.
- `ObservedMarketOutcome`, Risk, execution state, settlement, and Python
  research authority were not changed.
- Existing unrelated worktree changes were preserved.

## Validation

- `cd market-data && ./mvnw -Dtest=HistoricalFundingEvidencePersistenceTest test`
  passed after correcting H2 duplicate detection.
- `cd broker-service && ./mvnw -Dtest=HistoricalFeeScheduleRepositoryTest test`
  passed.
- `cd market-data && ./mvnw verify` passed: 226 tests, coverage checks passed.
- `cd broker-service && ./mvnw verify` passed: 218 tests, coverage checks passed.
- `cd trading-core && ./mvnw verify` passed, coverage checks passed.
- `git diff --check` passed.
- `cd trading-core && mvn -q -Dtest=MarketOrderEconomicEvaluatorTest,EconomicLifecycleEvaluatorTest test`
  passed, including constructor invariants, decision-time eligibility, zero-fill
  status, missing-cost completeness, compatibility, and negative-cost tests.
- `cd trading-core && mvn -q test` was started but exceeded the 120-second
  command timeout while the existing integration suite was still running; no
  failure from the new economic tests was reported.

SonarQube validation previously passed for `trading-os:market-data` and
`trading-os:broker-service`. SonarQube was not rerun after these review fixes;
the previous `trading-os:trading-core` result failed because the project reports 143 new violations and 76.5% new-code
coverage against an 80% threshold. The reported violations are predominantly
pre-existing broader Trading Core findings. No open issue remains on the new
economic package after removing its unused import.

The full verification output includes existing warnings about the H2/Flyway
version and deprecated Mockito dynamic agent behavior. These warnings did not
fail validation.

## Deferred Extensions

- Provider-specific acquisition adapters and capture orchestration remain
  deferred until the provider endpoints and historical availability are
  validated. The new persistence contracts are ready for those adapters.
- Cross-service transport is deferred until the evidence payloads and funding
  settlement policy are approved; domain contracts do not expose provider DTOs.
- Queue-position modeling, latency calibration, sophisticated partial-fill
  behavior, and broad limit-order simulation remain explicitly unsupported.
- PAPER settlement integration remains separate so the current PAPER execution
  behavior is not silently changed by incomplete historical evidence.
- Historical MARKET Order Book replay, fee/funding evidence adapters, economic
  dataset integration, and Python consumption remain deferred to later Stories.

## Documentation Outcome

Documentation update: this implementation report was added. No broader
architecture or operational documentation was changed because acquisition and
simulation behavior are not implemented yet.

## Vault Outcome

- Vault consulted during this implementation: no.
- Outcome: no vault action.
- Rationale: the repository, approved Story artifacts, and ADRs provided the
  required authoritative context; no curated personal note was needed to define
  the current implementation boundary.

## Human Boundary

No commit, push, merge, or deployment was performed.

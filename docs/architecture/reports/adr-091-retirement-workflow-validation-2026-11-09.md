# ADR-091 Retirement Workflow Validation

**Date:** 2026-11-09  
**Scope:** provider-scoped historical order-book evidence retirement  
**Final verdict:** `DOMAIN_VALIDATION_FAILED`

## 1. Executive Summary

ADR-091 is accepted for implementation, but the current implementation is not
safe to promote to a separately authorized physical retirement operation.

Positive evidence exists for:

- durable V26/V27 and Market Intelligence V26 schema changes;
- exact scope value validation at the Java value-object boundary;
- source-retired replay fail-closed behavior in Market Data;
- artifact fallback exclusion when the source scope is retired;
- capture rejection at the Market Data repository boundary;
- service JWT rejection for unauthenticated and unauthorized internal calls;
- live runtime availability of Market Data, Market Intelligence and the web UI;
- successful cTrader provider unit-test execution in the current run.

The domain validation nevertheless fails because:

1. `InternalOrderBookRetentionController` exposes
   `/source-retirement/{operationId}/operational-transition` and accepts
   `PHYSICAL_IN_PROGRESS` and `PHYSICAL_RETIRED` through ordinary HTTP. No
   DBA-only authorization or external-operation boundary protects those
   states.
2. `transitionOperational` can advance operational state before the business
   state is `SOURCE_RETIRED`.
3. Preparation and Market Intelligence acknowledgement accept actor identity
   from request bodies instead of deriving it from the authenticated principal.
4. Exact-scope preflight uses unbounded count queries without a final
   transactionally protected assertion. A concurrent writer can invalidate the
   preflight before a future relation-wide `TRUNCATE TABLE ONLY`.
5. No isolated PostgreSQL validation demonstrated the complete ADR-091
   mutation workflow with synthetic evidence, artifacts, dependencies,
   approvals and concurrent writers.

The current historical evidence was not deleted, truncated, modified or
retired. No physical operation was executed.

## 2. Environment Isolation Evidence

### Existing protected databases

| Environment | Classification | Evidence | Result |
|---|---|---|---|
| Market Data PostgreSQL | Existing production-like database | Container `market_data-postgres`, database `market_data`, volume `trading-os_market_data` | `PROTECTED` |
| Market Intelligence PostgreSQL | Existing runtime database | Container `market-intelligence-postgres` and compose-managed data volume | `PROTECTED` |
| Local unit tests | H2 in-memory | `market-data/src/test/resources/application-test.properties`, `market-intelligence/src/test/resources/application-test.properties` | `AVAILABLE` |
| PostgreSQL integration tests | Testcontainers PostgreSQL | Existing repository tests use ephemeral databases | `PARTIALLY_AVAILABLE` |

The host filesystem had approximately 9.6 GB available at 98% usage. No new
Docker volume, clone of the evidence relation, local backup, Docker prune or
volume removal was performed. The repository's PostgreSQL integration test was
run once and created isolated ephemeral Testcontainers databases on its own;
the test cleaned those databases after execution and no existing Trading OS
volume was used by that test. Further disposable PostgreSQL provisioning was
not continued because of the disk-pressure stop condition.

The current Market Data evidence relation remained present as a regular table:

- database: `market_data`;
- relation: `public.historical_order_book_evidence`;
- database size observed: approximately 85 GB;
- inbound foreign keys observed: 0;
- non-internal triggers observed: 0.

Only read-only metadata and count queries were executed against the existing
database. No evidence-table mutation was issued.

## 3. Implemented Contract Inventory

### Confirmed implementation

- ADR-091 is marked `Accepted - human approval recorded`.
- Story 0223 is marked implementation completed with validation limitations.
- Market Data migrations:
  - `V26__provider_scoped_historical_evidence_retirement.sql`;
  - `V27__source_retirement_approval_evidence.sql`.
- Market Intelligence migration:
  - `V26__historical_source_retirement_acknowledgement.sql`.
- Market Data operation and audit records persist operation identity, scope,
  approval reference, actor, fingerprints and transition audit.
- `SOURCE_RETIRED` is separate from `RELEASED` in the dependency constraint.
- Market Data replay checks source-retirement state before artifact fallback and
  before canonical replay completion.
- Evidence persistence checks the exact provider, provider symbol, market ID and
  depth before insert.
- The runbook keeps physical SQL outside application code.

### Deviations

- Physical operational states are still reachable through a normal internal
  HTTP endpoint.
- The operational transition service does not require business state
  `SOURCE_RETIRED` before advancing beyond preflight.
- The controller accepts body actor values for preparation and operational
  transitions.
- Market Intelligence acknowledgement persists body-supplied actor and scope
  without independently loading and comparing the Market Data operation scope.
- Preflight catalog safety checks inbound foreign keys and triggers only. It does
  not verify relation schema identity, partitioning, views/materialized views,
  owner or all dependent object categories described by ADR-091.

## 4. Authentication And Authorization Results

| Test | Result | Evidence |
|---|---|---|
| Unauthenticated Market Data internal request | `PASSED` | Runtime retention health request returned HTTP 403; security integration tests passed |
| Unauthenticated Market Intelligence internal request | `PASSED` | `MiSecurityIntegrationTest` passed |
| Authenticated unauthorized service caller | `PASSED` | Market Data security integration tests reject unknown callers |
| Authorized retention caller | `PASSED` | Existing security tests allow configured retention callers |
| Source-retired acknowledgement actor derived from Market Data principal | `PASSED` | `InternalOrderBookRetentionController` uses authenticated `market-intelligence` principal |
| Preparation actor derived from authenticated principal | `FAILED` | `prepareSourceRetirement` passes `request.actor()` to the service |
| Operational transition actor derived from authenticated principal | `FAILED` | Controller passes `request.actor()` directly |
| Market Intelligence acknowledgement actor derived from authenticated principal | `FAILED` | `SourceRetirementAcknowledgementService` persists `request.actor()` |
| Physical state transition blocked at HTTP boundary | `FAILED` | Controller accepts `PHYSICAL_IN_PROGRESS` and `PHYSICAL_RETIRED` |

The service JWT boundary itself is active and rejects unauthenticated access.
That boundary is not sufficient for ADR-091 because the retention authorization
set grants access to a generic controller containing physical-state transitions.

## 5. State Machine Validation

### Executed

| Scenario | Result | Evidence |
|---|---|---|
| Scope requires provider, symbol, market ID and positive depth | `PASSED` | `HistoricalEvidenceRetirementScopeTest` and constructor validation |
| Stale dependency fingerprint rejected | `PASSED` | `OrderBookSourceRetirementServiceTest` |
| Source-retired actor uses authenticated service name | `PASSED` | Focused unit test |
| Invalid operational transition rejected by service | `PASSED` | Transition guard exists and targeted service tests compile/pass |
| Duplicate source-retired acknowledgement idempotence | `PARTIALLY_VERIFIED` | Service code contains idempotent path; no PostgreSQL integration fixture executed |
| Ambiguous result and reconciliation | `NOT_RUN` | No isolated cross-service failure-injection environment available |
| Concurrent operation conflict | `NOT_RUN` | No isolated PostgreSQL concurrent fixture executed |
| Terminal state protection | `PARTIALLY_VERIFIED` | Service rejects transitions from `PHYSICAL_RETIRED`, but HTTP can reach that state |
| Business/operational ordering | `FAILED` | Operational service does not require `SOURCE_RETIRED` before backup/quiescence/physical states |

The implementation has the intended enum states, but the exposed transition
surface does not enforce the ADR boundary between domain readiness and the
separately authorized DBA operation.

## 6. Replay And Artifact Validation

| Scenario | Result | Evidence |
|---|---|---|
| Normal retained replay | `PASSED` | `OrderBookReplayServiceTest` |
| Retired raw source returns `SOURCE_RETIRED` | `PASSED` | `OrderBookReplayServiceTest.refusesReplayWhenTheHistoricalSourceWasRetired` |
| Retired artifact is excluded from semantic fallback | `PASSED` | Replay filters source-retired artifact scope before returning `AVAILABLE` |
| Missing raw source returns unavailable | `PASSED` | Existing replay test returns `UNAVAILABLE` without a trusted snapshot |
| Invalid/incomplete artifact falls back to canonical replay | `PASSED` | Existing replay tests |
| Protected dependency behavior | `PARTIALLY_VERIFIED` | Existing ADR-080 retention tests; no retirement fixture with synthetic protected dependency |
| Other provider unaffected | `NOT_RUN` | No isolated multi-provider PostgreSQL fixture |
| FeatureSnapshot readable | `NOT_RUN` | No retirement mutation was executed in an isolated fixture |
| Outcome readable | `NOT_RUN` | No retirement mutation was executed in an isolated fixture |
| Existing research export readable | `NOT_RUN` | No retirement mutation was executed in an isolated fixture |
| Temporary Market Data unavailability fails closed | `PARTIALLY_VERIFIED` | Market Intelligence readiness converts non-`AVAILABLE` and exceptions to zero availability; no retirement-state synchronization test |
| Stale retirement state fails closed | `NOT_RUN` | No cross-database stale-state fixture |

Market Intelligence's readiness code counts only a replay response whose status
is exactly `AVAILABLE`; `SOURCE_RETIRED`, `UNAVAILABLE` and remote failures do
not count as available. This is a positive fail-closed implementation result,
but direct source-retirement integration coverage is missing.

## 7. Historical Capture Guard Validation

| Scenario | Result | Evidence |
|---|---|---|
| Normal capture path | `PASSED` | `OrderBookEvidenceCaptureServiceTest` |
| Retired scope rejected at persistence boundary | `PARTIALLY_VERIFIED` | Repository code calls exact source-retirement guard; no isolated retired-row fixture |
| Re-enabled configuration cannot bypass retirement | `PARTIALLY_VERIFIED` | Guard is below configuration, but no integration fixture |
| Rejection observable | `NOT_RUN` | No runtime synthetic capture request |
| Live Kraken acquisition unaffected | `PASSED` | Existing runtime remained connected and synchronized; no retirement transition applied |
| Current market prices remain available | `PASSED` | Market catalog runtime request HTTP 200; no physical retirement applied |
| cTrader OHLC unaffected | `PASSED` | cTrader provider test passed in the current run; runtime logs showed successful cTrader requests |
| Other provider scope writable | `NOT_RUN` | No isolated synthetic database fixture |

The guard is implemented in `OrderBookEvidenceRepository`, not only in the
configuration path. That is the correct boundary, but it still needs a
PostgreSQL integration test with a persisted `SOURCE_RETIRED` operation.

## 8. Exact-Scope And Relation-Wide Safety

| Scenario | Result | Evidence |
|---|---|---|
| Exact provider/symbol/market/depth represented | `PASSED` | Scope record and SQL predicates include all four dimensions |
| Wildcard provider rejected | `PASSED` | Provider is an enum; no wildcard provider value exists |
| Wildcard symbol rejected | `FAILED` | `providerSymbol` rejects blank values but accepts wildcard-like strings |
| Mixed provider/symbol/depth/canonical identity fixture | `NOT_RUN` | No synthetic PostgreSQL fixture |
| Unexpected provider fixture | `NOT_RUN` | No synthetic PostgreSQL fixture |
| Empty relation behavior | `PARTIALLY_VERIFIED` | Preflight rejects `targetRows == 0`; no isolated test documents intended policy |
| Scope query timeout | `NOT_RUN` | No safe PostgreSQL timeout fixture |
| Dependency inventory change | `NOT_RUN` | No concurrent isolated fixture |
| Concurrent evidence insertion | `NOT_RUN` | No locking/race fixture |
| Final assertion race protection | `FAILED` | Preflight performs separate count queries without relation lock or final assertion |

The current preflight calculates total rows, out-of-scope rows and related
counts in separate queries. It does not lock the evidence relation and does not
persist a final relation-wide assertion that a DBA operation must consume under
the same lock. A writer can insert an out-of-scope row after preflight and
before a future `TRUNCATE TABLE ONLY`. The runbook correctly requires a fresh
DBA-side assertion, but the application readiness record itself cannot prove
that race was closed.

The future DBA procedure must perform the final assertion in the same controlled
maintenance transaction as the `TRUNCATE`, acquire `ACCESS EXCLUSIVE` on the
approved relation, set bounded `lock_timeout` and `statement_timeout`, and
abort on any out-of-scope row, lock conflict or schema mismatch. This was not
executed.

## 9. Flyway Migration Results

| Validation | Result | Evidence |
|---|---|---|
| Existing Market Data runtime upgrade | `PASSED` | Runtime Flyway history contains V26 and V27; operation/audit/approval tables are empty |
| Existing Market Intelligence runtime upgrade | `PASSED` | Runtime and test logs show V26 applied |
| New Market Intelligence chain from empty schema | `PASSED` | Isolated Testcontainers logs applied migrations 1 through 26 |
| Upgrade from Market Intelligence V19 | `FAILED` | Existing test expected version 25 but actual current version is 26 |
| Test assertion correction | `PASSED` | Assertion changed from 25 to 26; rerun was not performed due disk-pressure stop condition |
| New Market Data PostgreSQL chain | `BLOCKED` | No additional PostgreSQL resource created under current disk pressure |
| Existing migrations modified | `PASSED` | V26/V27 are additive new migrations; no prior migration change identified |
| Migration performs physical evidence deletion | `PASSED` | V26/V27 contain metadata/schema changes only; no DELETE/TRUNCATE/DROP |
| Startup hook performs physical retirement | `PASSED` | No startup physical-retirement hook found |

The isolated migration failure was a stale test expectation, not a Flyway
migration failure. The narrow test-only correction is in:

`market-intelligence/src/test/java/com/hope/trading/market_intelligence/adapter/persistence/HistoricalReconstructionLifecyclePostgresIntegrationTest.java`

## 10. Test Suite Status

### Passed

```text
market-data:
  mvn -q -Dtest=HistoricalEvidenceRetirementScopeTest,OrderBookSourceRetirementServiceTest,OrderBookReplayServiceTest,OrderBookEvidenceCaptureServiceTest,InternalOrderBookRetentionControllerTest test
  mvn -q -Dtest=MarketDataSecurityIntegrationTest test
  mvn -q -Dtest=CTraderDataStreamProviderTest test

market-intelligence:
  mvn -q -Dtest=HistoricalReconstructionPolicyTest,HistoricalReconstructionServiceTest,HistoricalReconstructionPreflightTest,HistoricalReadinessServiceTest,DefaultArtifactResolverTest,ArtifactInvalidationServiceTest test
  mvn -q -Dtest=MiSecurityIntegrationTest test
```

All commands above exited successfully. Test logs contain expected mocked
failure-path warnings and Java/Mockito dynamic-agent warnings; these did not
produce test failures.

### Failed or incomplete

| Test/validation | Status | Reason |
|---|---|---|
| `HistoricalReconstructionLifecyclePostgresIntegrationTest` | `FAILED` | Stale expected Flyway version `25`; actual version `26` |
| Same test after assertion correction | `NOT_RUN` | Re-run would provision another PostgreSQL resource under 9.6 GB free space |
| Full Market Data suite | `PARTIALLY VERIFIED` | Earlier run reported flaky cTrader failure; focused cTrader test passed on reproduction |
| Full Market Intelligence suite | `BLOCKED` | Earlier run exceeded available timeout; no repeated expensive run without a diagnosed bottleneck |
| Full ADR-091 authenticated mutation workflow | `NOT_RUN` | Would mutate database state and no isolated PostgreSQL fixture was safely available |
| Cross-service timeout/ambiguous reconciliation | `NOT_RUN` | No isolated failure-injection environment |

No test was disabled, weakened or hidden.

## 11. Backup And Restore Readiness

**Classification: `UNVERIFIED`**

Repository inspection found no verified backup artifact or executable backup and
restore procedure for the Market Data evidence relation. No `pg_dump`,
`pg_restore`, `pg_basebackup`, volume snapshot or 85 GiB backup was created.

The current root filesystem has approximately 9.6 GB free, which is
insufficient for a local full evidence backup or shadow relation operation.
Before physical retirement, the DBA must provide external storage and execute an
isolated restore validation covering:

- raw evidence relation and indexes;
- V26/V27 retirement metadata and approval evidence;
- sequences, artifacts, dependencies and holds;
- required application schema;
- replay behavior before retirement;
- FeatureSnapshot, outcome and research-record continuity where applicable.

Without this evidence, post-commit recovery is not established.

## 12. Operational Readiness Checklist

| Requirement | Status | Assessment |
|---|---|---|
| Exact relation identity | `PASSED` | Relation is `public.historical_order_book_evidence` in `market_data` |
| Exact approved provider/symbol/depth | `PARTIALLY VERIFIED` | Existing report measured KRAKEN/PF_XBTUSD/25; exhaustive current assertion not repeated |
| Canonical market identity | `PARTIALLY VERIFIED` | UUID is persisted in scope; no final DBA assertion performed |
| Dependency inventory | `PARTIALLY VERIFIED` | Fingerprint implementation exists; no synthetic mutation workflow executed |
| Source-retirement state | `NOT_RUN` | Existing operation tables remain empty; intentionally no retirement mutation |
| Historical capture disabled | `PASSED` | Runtime configuration observed disabled; durable retired-scope test not run |
| Replay/capture quiescence | `NOT_RUN` | No physical-operation window exists |
| PostgreSQL locks | `NOT_RUN` | No physical operation or lock acquisition attempted |
| WAL/archive capacity | `UNVERIFIED` | No DBA measurement available |
| Filesystem capacity | `FAILED` | 9.6 GB free at 98% usage; unsafe margin for backup/recovery |
| Backup and restore | `UNVERIFIED` | No isolated restore evidence |
| Human approval | `PARTIALLY VERIFIED` | ADR approval is recorded; separate physical DBA authorization is absent |
| Maintenance window | `NOT_RUN` | No operation authorized |
| Post-operation verification | `NOT_RUN` | No physical operation executed |
| Rollback/recovery owner | `UNVERIFIED` | No operational evidence found |

## 13. Defects Found And Fixes Applied

### Corrected

- Updated the stale isolated migration test expectation from Flyway version `25`
  to `26` after the new Market Intelligence V26 migration was confirmed.

### Not corrected because they require contract/security hardening

- Ordinary HTTP can request `PHYSICAL_IN_PROGRESS` and `PHYSICAL_RETIRED`.
- Operational transitions are not gated on business state `SOURCE_RETIRED`.
- Preparation and operational actor values are body-controlled.
- Market Intelligence acknowledgement actor and scope are body-controlled and
  are not independently reconciled against the Market Data operation.
- Exact-scope preflight has no final lock-protected assertion and does not cover
  all catalog object categories.

These are not cosmetic issues. They require a focused authorization and
operational-boundary change before a physical operation can be considered.

## 14. Remaining Blockers

1. Remove or harden the HTTP operational transition surface so application
   traffic cannot assert physical progress or physical completion.
2. Derive all operation actors from authenticated principals and validate exact
   approval ownership; do not trust body actor claims.
3. Require `SOURCE_RETIRED` before operational progression beyond the preflight
   state.
4. Add isolated PostgreSQL integration tests with synthetic multi-provider,
   multi-symbol, multi-depth evidence, artifacts, dependencies, holds and
   concurrent writers.
5. Add a final relation-wide assertion under appropriate PostgreSQL locking for
   the future DBA operation.
6. Validate full Market Data migration chain in an approved disposable
   PostgreSQL environment with sufficient disk headroom.
7. Provide and restore-test an external backup outside the nearly full root
   filesystem.
8. Measure WAL/archive headroom, lock behavior and post-operation filesystem
   reserve during an approved staging exercise.
9. Rerun the corrected migration test and diagnose the full Market Intelligence
   timeout in an environment with sufficient test budget.

## 15. Final Go/No-Go Verdict

### `DOMAIN_VALIDATION_FAILED`

The implementation demonstrates several correct fail-closed mechanisms, but the
domain and authorization contract is not yet validated as safe. In particular,
the current HTTP transition endpoint can record physical operational states and
the exact-scope preflight is not race-safe by itself.

Therefore:

- **Physical retirement:** `NO-GO`.
- **Separate DBA approval request:** do not request yet.
- **Existing historical evidence:** untouched by this validation.
- **Next engineering step:** harden the authorization/physical-operation
  boundary, then add isolated PostgreSQL integration coverage and external
  backup/restore evidence before reassessment.

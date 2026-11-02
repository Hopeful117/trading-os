# CFD Epic Validation - Initial Qualification Run

## Run Identity

```text
RUN_ID = 2026-11-01-initial-qualification
DATE = 2026-11-01
REPOSITORY_SHA = edb5e7e47eee79d4cbe7491b2b5321b151c029df
REMOTE_MAIN = edb5e7e47eee79d4cbe7491b2b5321b151c029df
BRANCH = validation/cfd-epic-qualification
WORKTREE_BEFORE_RUN = clean
DATABASE_MIGRATION_ADDED = NO
SETTLEMENT_COMPATIBILITY_CHANGED = NO
```

The SHA is the current `origin/main` after `git fetch origin` and fast-forwarding
the validation branch. The run used local Docker/Testcontainers PostgreSQL,
Java 21, Maven wrappers where available, Docker 29.1.3, and the repository's
configured local quality tooling. No external provider or broker mutation was
performed.

## Evidence Classification

- Current production source and current executable tests are the primary evidence.
- ADRs and historical Stories are traceability and architectural context.
- Historical PASS/BLOCKED labels were not promoted automatically.
- The current positive PAPER CFD settlement and persistence hardening suites are
  selected but disabled because compatibility remains rejected.

## Capability Maturity

```text
CFD_IMPLEMENTED = YES
CFD_PROVEN = NO
CFD_VALIDATED = NO
```

The contract, risk projection, local margin authority, valuation, and fail-closed
execution routing are implemented. The complete positive accounting/lifecycle
capability is not currently proven by active executable evidence.

## Gate Results

| Gate | Result | Current evidence and reason |
|---|---|---|
| G1 - CFD Contract & Product Semantics | PASS | `InstrumentFinancialContract`, `InstrumentProductType`, `CfdFinancialCalculator`, `CfdRiskProjectionService`, valuation and contract tests pass. Explicit contract facts, signed quantities, multiplier, currencies, precision and fail-closed validation are present. |
| G2 - Margin Authority | PASS | `MarginPolicyPostgresIntegrationTest` passed 5 tests, including persistence/reload, immutable versions, constraints and `1000 * 1.10 * 1% = 11.00 USD`. `PaperRequiredMarginClient` is local and provider-neutral for PAPER. |
| G3 - Risk & T0/T1 Revalidation | BLOCKED | Real `TradePlanRiskEvaluationService` and `ExecutionTimeRiskRevalidationService` orchestration passed. `PaperCfdDeterministicLifecycleIntegrationTest` passed 3 tests, including persisted T0/T1 and T1 rejection before submission/mutation. Active tests prove missing `sourcePrice` fails closed and that the T1 freshness requirement is requested, but do not prove stale source timestamps or freshness mismatch rejection as independent executable scenarios. |
| G4 - CASH CFD Accounting Semantics | BLOCKED | Positive cash exposure/no-inventory evidence exists only in `PaperCfdSettlementServiceTest`, which ran 8 tests with 8 skipped. No active current run proves the positive accounting boundary without bypassing rejected compatibility. |
| G5 - CFD Lifecycle | BLOCKED | Open, MTM, close, realized PnL and zero-exposure scenarios are implemented in the deferred suite but are not active evidence. The positive lifecycle is blocked by the compatibility guard. |
| G6 - Persistence, Identity & Exactly-Once | BLOCKED | Atomic PostgreSQL provenance claim is implemented and generic persistence tests pass, but the CFD same-fill replay/concurrency suite ran 6 tests with 6 skipped. No active CFD durable replay proof exists in this run. |
| G7 - Settlement Compatibility Authorization | BLOCKED | Intentionally not executed. `CASH + CFD`, `INVENTORY + CFD`, and `UNKNOWN + CFD` remain rejected. G7 authorization is not permitted because G4-G6 are not PASS. |
| G8 - Real Application Path E2E | BLOCKED | G7 is not authorized; the positive application lifecycle cannot run while `CASH + CFD` is rejected. The negative T1 and compatibility rejection paths are proven. |

## G1-G6 Blockers

1. The current active evidence proves fail-closed T0/T1 and compatibility
   rejection, but not positive cash CFD accounting.
2. Positive CFD settlement and persistence/replay tests are deliberately
   disabled by `@Disabled` while ADR-083/Phase 8 compatibility remains blocked.
3. T1 freshness evidence is incomplete: the current run does not prove stale
   source timestamps and freshness mismatch rejection through independent active
   scenarios.
4. This creates a validation sequencing gap: G4-G6 require an approved positive
   accounting boundary before G7, but the current production boundary rejects
   `CASH + CFD`. A future corrective decision must define that approved boundary
   without a test-only compatibility bypass.
5. The repository quality pipeline has a separate pre-existing coverage blocker:
   `broker-service` measured `75.33%` line coverage against the `80%` gate. The
   affected CFD modules passed their functional tests; this quality failure is
   reported separately and was not changed in this run.

## Margin

```text
PAPER_MARGIN_AUTHORITY = LOCAL_VERSIONED_MARGIN_POLICY
LIVE_MARGIN_AUTHORITY = PROVIDER_AUTHORITATIVE
MARGIN_POLICY_PERSISTENCE = PASS
MARGIN_POLICY_IMMUTABILITY = PASS
PAPER_RUNTIME_BROKER_MARGIN_DEPENDENCY = NO
```

The current `PaperRequiredMarginClient` still validates the financial-account to
broker-routing-account binding, but its PAPER margin calculation uses the local
policy, contract, exposure, valuation and FX context rather than a Broker
Service margin preview.

## Risk

```text
REAL_T0 = YES
REAL_T1 = YES
T1_MOCKED = NO
T0_PASS_T1_REJECT = PROVEN
MISSING_T1_FAIL_CLOSED = PASS
STALE_T1_FAIL_CLOSED = BLOCKED (no independent active stale-timestamp scenario)
FRESHNESS_MISMATCH_FAIL_CLOSED = BLOCKED (no independent active mismatch scenario)
```

Infrastructure facts are controlled at test boundaries, but the production
risk, persistence, lifecycle and decision services execute. The active
integration test changes the authoritative risk price and proves T1 rejection
without execution, settlement, or financial mutation.

## Accounting

```text
SIGNED_CFD_EXPOSURE = PROVEN at contract/calculation boundary; BLOCKED at active positive settlement boundary
BASE_INVENTORY_CREATED = NO by current CFD settlement design; positive lifecycle proof BLOCKED
FULL_NOTIONAL_DEBIT = NO by current CFD settlement design; positive lifecycle proof BLOCKED
MTM_DISTINCT_FROM_SETTLEMENT = PROVEN by valuation implementation/tests; active end-to-end proof BLOCKED
```

No positive accounting claim is promoted to a complete gate PASS while the
corresponding executable suite is disabled.

## Lifecycle

```text
OPEN = BLOCKED
MTM = BLOCKED
CLOSE = BLOCKED
REALIZED_PNL = BLOCKED
FINAL_ZERO_EXPOSURE = BLOCKED
```

The code paths and deferred tests exist, but the active validation run proves
only that the incompatible path rejects before mutation.

## Costs

```text
COMMISSION = SUPPORTED for explicit fill fees in the deferred settlement implementation
SPREAD_SLIPPAGE = UNMODELED
FINANCING = OUT_OF_SCOPE
SWAP = OUT_OF_SCOPE
UNKNOWN_COST_AS_ZERO = NO
COST_BLOCKS_EPIC = YES when the cost is applicable to a claimed positive lifecycle
```

This classification follows ADR-082. Unsupported or ambiguous applicable costs
must remain unavailable and must not silently become zero.

## Persistence

```text
POSTGRES_EVIDENCE = PASS for margin policy, migrations, generic execution persistence and T0/T1 evidence
CANONICAL_TRADE_IDENTITY = BLOCKED for complete CFD lifecycle evidence
DURABLE_SAME_FILL_REPLAY = BLOCKED for CFD positive lifecycle evidence
EXACTLY_ONCE = BLOCKED for CFD positive lifecycle evidence
ATOMIC_PROVENANCE_CLAIM_IMPLEMENTATION = PRESENT
```

`JpaTradeExecutionProvenanceRepository.insertIfAbsent` uses PostgreSQL
`ON CONFLICT (broker_order_id,fill_id) DO NOTHING`. That implementation evidence
does not replace the skipped end-to-end CFD replay proof.

## Compatibility

```text
CASH_CFD = REJECTED
INVENTORY_CFD = REJECTED
UNKNOWN_CFD = REJECTED
SETTLEMENT_COMPATIBILITY_CHANGED = NO
READY_FOR_G7_AUTHORIZATION = NO
```

`SettlementCompatibilityPolicy` was not modified. `SettlementCompatibilityPolicyTest`
passed 2 tests, and both the direct CFD settlement service and the standard
application path reject incompatible PAPER CFD settlement.

## Application Path

```text
INTERNAL_CFD_CAPABILITY = BLOCKED as a complete positive capability
REAL_APPLICATION_PATH = BLOCKED
CURRENT_REAL_PATH_BLOCKER = G7 compatibility authorization is not executed; positive G4-G6 evidence is incomplete
NEGATIVE_T1_APPLICATION_PATH = PROVEN
NEGATIVE_CASH_CFD_APPLICATION_PATH = PROVEN
```

## Safety

```text
AUTOMATIC_EXECUTION = NO
HUMAN_AUTHORIZATION_REQUIRED = YES
CTRADER_SAFETY_EXECUTION_ENABLED = false
REAL_CTRADER_ORDERS = 0
REAL_CTRADER_CLOSES = 0
PROVIDER_MUTATIONS = 0
```

## Validation Commands

| Validation | Result | Evidence |
|---|---|---|
| Focused CFD/risk/margin/persistence tests | PASS | Maven exit status 0; PostgreSQL/Testcontainers active where applicable. |
| Positive CFD settlement suite | NOT_RUN for behavior | `PaperCfdSettlementServiceTest`: 8 skipped, 0 failures. |
| Positive CFD persistence/replay suite | NOT_RUN for behavior | `PaperCfdSettlementPersistenceHardeningIntegrationTest`: 6 skipped, 0 failures. |
| PostgreSQL tests | PASS | `MarginPolicyPostgresIntegrationTest`: 5 passed; active lifecycle integration: 3 passed. |
| Migration tests | PASS | `CfdFinancialProjectionMigrationTest` and `AccountSettlementMigrationTest` passed; no migration changed. |
| `trading-core ./mvnw -q test` | PASS | Exit status 0. |
| `trading-core ./mvnw -q verify` | PASS | Exit status 0. |
| `risk-domain mvn -q verify` | PASS | Exit status 0. |
| Repository quality pipeline | FAIL | Functional module verification completed, but aggregate coverage gate failed on `broker-service` at 75.33% (<80%). |
| Sonar quality scan | NOT_RUN | `quality-scan.sh` stopped at the repository coverage gate before scanner execution. |
| `git diff --check` | PASS | No whitespace errors. |

The full test and verify logs include the known historical Flyway V34 failure-path
log during migration coverage; Maven exit status remained 0 and V34 was not
modified.

## Final Decision

```text
EPIC_RESULT = NOT_READY
CFD_CAPABILITY = IMPLEMENTED (partial evidence), NOT PROVEN as a complete capability
READY_FOR_CASH_PLUS_CFD = NO
READY_FOR_G7_AUTHORIZATION = NO
```

## Next Required Action

Do not authorize `CASH + CFD` yet. First resolve the G4-G6 evidence gap by
defining and approving a non-bypass positive accounting/persistence validation
boundary that can run while compatibility remains fail-closed, or explicitly
revise the gate dependency through a new architectural decision. Then rerun G4,
G5 and G6 with active executable evidence. Independently address the repository
coverage failure in `broker-service` before treating the quality pipeline as
green. Only after G1-G6 are all PASS may a separately authorized G7 run change
compatibility and immediately execute G8.

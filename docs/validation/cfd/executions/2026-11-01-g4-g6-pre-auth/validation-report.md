# CFD G4-G6 Pre-Authorization Validation Report

## Execution Metadata

| Field | Value |
|---|---|
| Date | 2026-11-01 |
| Branch | `validation/cfd-epic-qualification` |
| Commit under test | `edb5e7e47eee79d4cbe7491b2b5321b151c029df` |
| Scope | Pre-authorization evidence analysis for G4-G6 |
| Production changes | None |
| Compatibility policy change | None |
| Provider mutation | None |

## Objective

Assess the evidence that can be collected for G4-G6 while preserving the
current compatibility policy:

```text
CASH + CFD = REJECTED
INVENTORY + CFD = REJECTED
UNKNOWN + CFD = REJECTED
```

This run intentionally does not enable the disabled positive settlement suites,
change `SettlementCompatibilityPolicy`, execute G7, or execute G8.

## Evidence Classification

The run distinguishes supporting capability evidence from authorized application
path evidence:

| Evidence | Result | Meaning |
|---|---|---|
| CFD financial calculation | PASS | Deterministic PnL and financial semantics execute correctly in the calculator tests |
| CFD valuation | PASS | MTM and realization support is covered by valuation tests |
| CFD projection schema | PASS | Projection migration contract is valid |
| Paper cash schema | PASS | Existing paper cash migration contract is valid |
| Compatibility policy | PASS | Current rejection behavior remains enforced |
| T0/T1 lifecycle rejection and no mutation | PASS | G3 application evidence remains valid |
| Positive settlement accounting path | NOT PROVEN | The production settlement boundary rejects the current `CASH + CFD` combination before mutation |
| Positive lifecycle path | NOT PROVEN | The production open/MTM/close path cannot be executed without compatibility authorization |
| Durable CFD settlement replay path | NOT PROVEN | The positive PostgreSQL settlement/replay suite remains disabled |

## Validation Commands

Executed from `trading-core`:

```bash
./mvnw -q -Dtest=CfdFinancialCalculatorTest,CfdValuationServiceTest,SettlementCompatibilityPolicyTest test
./mvnw -q -Dtest=CfdFinancialProjectionMigrationTest,PaperCashValuationMigrationTest test
./mvnw -q -Dtest=PostgresFreshInstallMigrationTest test
./mvnw -q -Dtest=PaperCfdDeterministicLifecycleIntegrationTest test
```

Results:

| Suite | Result |
|---|---|
| `CfdFinancialCalculatorTest` | PASS, 12 tests |
| `CfdValuationServiceTest` | PASS, 5 tests |
| `SettlementCompatibilityPolicyTest` | PASS, 2 tests |
| `CfdFinancialProjectionMigrationTest` | PASS, 1 test |
| `PaperCashValuationMigrationTest` | PASS, 1 test |
| `PostgresFreshInstallMigrationTest` | PASS, 6 tests |
| `PaperCfdDeterministicLifecycleIntegrationTest` | PASS, 5 tests |

The migration suite includes an expected negative migration assertion; the
logged rollback at migration 34 is part of that test's fail-closed coverage,
not a failed test result.

## G4 Result

```text
G4 = BLOCKED
```

Supporting calculator, valuation, and schema evidence passes. G4 requires
positive accounting evidence through the authorized production settlement
boundary. `PaperCfdSettlementService` invokes
`SettlementCompatibilityPolicy.requireCompatible` before entry or exit
mutation, and the current policy rejects `CASH + CFD`. The positive
`PaperCfdSettlementServiceTest` therefore remains disabled and cannot provide
PASS evidence without a separately authorized compatibility change.

## G5 Result

```text
G5 = BLOCKED
```

The calculator and valuation tests support the required financial operations,
and the G3 integration suite proves the negative T1 path does not mutate
execution or financial state. The positive open -> MTM -> close -> zero
exposure lifecycle is not executable through the current authorized boundary.
The disabled positive settlement suite is deferred evidence, not PASS evidence.

## G6 Result

```text
G6 = BLOCKED
```

The PostgreSQL migration suite passes and confirms the current schema history
and provenance constraints. It does not prove the CFD settlement replay
contract. The same-fill durable reconstruction, duplicate settlement, and
concurrent provenance claim tests remain in
`PaperCfdSettlementPersistenceHardeningIntegrationTest`, which is disabled
while compatibility authorization remains blocked. Enabling it now would
execute positive settlement behavior without G7 authorization.

## Disabled Suites

The following suites were inspected but not enabled:

```text
PaperCfdSettlementServiceTest
PaperCfdSettlementPersistenceHardeningIntegrationTest
```

Both are disabled with the repository's existing ADR-083 / Phase 8
authorization guard. No test-only bypass, direct service construction, policy
mutation, or compatibility override was introduced.

## CFD Epic Status

```text
G1 = PASS
G2 = PASS
G3 = PASS
G4 = BLOCKED
G5 = BLOCKED
G6 = BLOCKED
G7 = BLOCKED
G8 = BLOCKED

CAPABILITY_SUPPORTING_EVIDENCE = PASS
CFD_IMPLEMENTED = YES
CFD_PROVEN = NO
CFD_VALIDATED = NO
```

## Decision

Do not authorize `CASH + CFD`. The pre-authorization run confirms that the
financial calculator, valuation model, migration contracts, current rejection
policy, and negative T1 path are functioning, but it does not close the
mandatory positive application-path gates G4-G6.

No protocol change was required: the existing protocol already states that
disabled positive tests are not PASS evidence and that G4-G6 depend on positive
settlement or replay evidence.

## Next Required Action

Resolve the authorization boundary through an approved architectural/product
decision before enabling positive settlement evidence. After that decision,
run G4-G6 through the authorized production path, obtain independent review,
then consider G7. G8 remains prohibited until G7 passes.

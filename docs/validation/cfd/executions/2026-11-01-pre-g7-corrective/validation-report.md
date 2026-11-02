# CFD Pre-G7 Corrective Run

## Execution Metadata

| Field | Value |
|---|---|
| Date | 2026-11-01 |
| Branch | `validation/cfd-epic-qualification` |
| Repository SHA | `edb5e7e47eee79d4cbe7491b2b5321b151c029df` |
| Origin main | `edb5e7e47eee79d4cbe7491b2b5321b151c029df` |
| Scope | Entry provenance atomicity and CFD cost semantics before G7 |
| Compatibility policy changed | No |
| Provider execution enabled | No |
| G7/G8 executed | No |

## Objective

Resolve the two independent pre-G7 concerns without enabling `CASH + CFD`:

1. Make entry provenance claims atomic and transactionally coherent with the
   generated CFD trade identity and economic mutation.
2. Classify current cost semantics from ADR-082 and the PAPER execution/fill
   model without inventing a second cost engine or double-counting prices.

## Entry Provenance

### Before

The entry path performed:

```text
findByBrokerOrderIdAndFillId(brokerOrderId, fillId)
    -> if absent
    -> account/trade/economic mutation
    -> provenance.save(...)
```

The unique `(broker_order_id, fill_id)` constraint prevented duplicate durable
rows, but concurrent entry settlement could race between the read and the save.
The losing transaction could receive a uniqueness conflict rather than a normal
idempotent replay result.

Exit already used `saveIfAbsent(...)` before its economic mutation and was not
redesigned.

### After

Entry now uses the existing provenance table and canonical
`(broker_order_id, fill_id)` identity:

```text
claimEntryIfAbsent(..., trade_id = NULL)
    -> if already claimed, reload completed trade identity and return it
    -> if claim succeeds, perform entry mutation
    -> completeEntryClaim(..., generated trade_id)
```

The claim, trade/account mutation, and claim completion execute inside the
existing `@Transactional` settlement boundary. A rollback removes the claim;
there is no committed provenance marker without its economic mutation.

Migration `V51__allow_entry_provenance_claim.sql` only makes the existing
provenance `trade_id` nullable while an entry claim is in progress. The existing
unique constraint remains the canonical replay key. No parallel idempotency
subsystem was added.

## Atomicity Evidence

`PaperCfdEntryProvenanceAtomicityIntegrationTest` uses PostgreSQL/Testcontainers
and covers:

- sequential same-fill claim and replay;
- four concurrent attempts using the exact same broker order and fill IDs;
- rollback leaving no replay marker.

Results:

```text
Tests run: 3
Failures: 0
Errors: 0
Skipped: 0
Concurrent attempts: 4
Provenance rows for fill: 1
Successful claim/economic-boundary results: 1 claim, remaining attempts replayed the same trade identity
Rollback claim residue: 0 rows
```

This proves the PostgreSQL provenance claim boundary and transaction behavior.
It does not claim positive CFD settlement G6 evidence because the settlement
service still rejects `CASH + CFD` before the positive path can execute.

## Cost Semantics

The classification follows ADR-082 sections 3.8, 6, and 7, plus the current
PAPER execution model:

| Category | Classification | Evidence |
|---|---|---|
| Commission | `SUPPORTED_EXPLICITLY` | `BrokerOrder.Fill.fee` is persisted and deducted by settlement; deterministic non-zero fee behavior exists in the deferred CFD suite and active paper cash-derivative regression coverage |
| Spread | `CAPTURED_IN_AUTHORITATIVE_FILL_PRICE` | `SimulatedExecutionAdapter` fills BUY at ask and SELL at bid; CFD PnL uses the persisted entry/exit fill prices |
| Slippage | `CAPTURED_IN_AUTHORITATIVE_FILL_PRICE` | Execution carries a reference price and authoritative fill price; CFD accounting uses the actual persisted fill, so execution-price deviation is not charged a second time |
| Financing | `EXPLICITLY_OUT_OF_SCOPE` | ADR-082 excludes provider-specific financing schedules from this implementation boundary |
| Swap | `EXPLICITLY_OUT_OF_SCOPE` | No provider-specific swap schedule is part of ADR-082's current boundary |
| Other provider cost | `UNMODELED_BLOCKER` if applicable and non-zero | Unknown additional costs must fail closed; they are not mapped to zero |

Spread and slippage remain distinct concepts. The classification does not claim
that a separate spread/slippage ledger exists. It states that the current
authoritative executable fill price captures the supported price effect once.
Adding a second synthetic spread or slippage fee would double-count the same
execution economics.

Unknown or applicable unsupported costs are not silently converted to zero.
They must make the relevant settlement or risk context unavailable, consistent
with ADR-082.

## Post-G7 Test Assumptions

The following assertions were found in
`PaperCfdSettlementPersistenceHardeningIntegrationTest`:

```text
reconstructsLongAndShortMtmAfterFlushClearAndReload
concurrentReplayProducesOneEconomicEffect
```

Both call `assertCashCfdRemainsGloballyRejected()`. They are compatibility
guard assertions embedded in deferred persistence tests, not independent
accounting invariants. They must be removed or relocated to a compatibility
policy test during the future human-reviewed G7 run. They were not changed now.

## Validation

Focused results:

| Validation | Result |
|---|---|
| `PaperCfdEntryProvenanceAtomicityIntegrationTest` | PASS, 3 PostgreSQL tests |
| `PaperCfdDeterministicLifecycleIntegrationTest` | PASS, 5 tests |
| `PostgresFreshInstallMigrationTest` | PASS, 6 tests |
| `CfdFinancialCalculatorTest` | PASS, 12 tests |
| `CfdValuationServiceTest` | PASS, 5 tests |
| `PaperCashDerivativeSettlementTest` | PASS, 5 tests |
| `PaperExecutionPersistenceIntegrationTest` | PASS, 2 tests |
| `SettlementCompatibilityPolicyTest` | PASS, 2 tests |
| `CfdFinancialProjectionMigrationTest` | PASS |
| `PaperCashValuationMigrationTest` | PASS |
| `trading-core ./mvnw -q verify` | PASS |
| `risk-domain mvn -q verify` | PASS |
| `git diff --check` | PASS |
| Separate `trading-core ./mvnw -q test` invocation | TIMEOUT at 120 seconds; no test failure was reported before timeout |

The known unrelated repository quality issue remains separate:
`broker-service` coverage is `75.33%` against the required `80%`.

## Gate Status

```text
G1 = PASS
G2 = PASS
G3 = PASS
G4 = BLOCKED
G5 = BLOCKED
G6 = BLOCKED
G7 = BLOCKED
G8 = BLOCKED
```

G4-G6 remain blocked because positive settlement/replay evidence still requires
the compatibility change. This run does not convert supporting capability or
claim evidence into positive settlement gate PASS results.

## Independent Blockers

```text
HIGH_FINANCIAL_FINDINGS = NONE
MEDIUM_FINANCIAL_FINDINGS = NONE
ENTRY_PROVENANCE_ATOMICITY = RESOLVED
COST_SEMANTICS = RESOLVED
INDEPENDENT_G4_G6_BLOCKERS = NONE
IS_COMPATIBILITY_THE_ONLY_REMAINING_BLOCKER = YES
```

## Safety Invariants

```text
CASH_CFD = REJECTED
INVENTORY_CFD = REJECTED
UNKNOWN_CFD = REJECTED
SETTLEMENT_COMPATIBILITY_CHANGED = NO
AUTOMATIC_EXECUTION = NO
HUMAN_AUTHORIZATION_REQUIRED = YES
CTRADER_SAFETY_EXECUTION_ENABLED = false
REAL_CTRADER_ORDERS = 0
REAL_CTRADER_CLOSES = 0
PROVIDER_MUTATIONS = 0
```

## Readiness Decision

```text
SAFE_TO_PREPARE_CONDITIONAL_G7_RUN = YES
```

This is preparation readiness only. G7 was not executed or authorized. The next
run requires explicit human review, a narrowly scoped `CASH + CFD` compatibility
change, immediate G4-G6 execution, and rollback/closure of the attempt if any
positive accounting, lifecycle, or replay evidence fails. `INVENTORY + CFD` and
`UNKNOWN + CFD` must remain rejected, and G8 remains prohibited until G4-G7
pass.

# CFD G4-G6 Evidence Hardening Corrective Run

## Execution Metadata

| Field | Value |
|---|---|
| Date | 2026-11-02 |
| Branch | `validation/cfd-epic-qualification` |
| Repository SHA | `edb5e7e47eee79d4cbe7491b2b5321b151c029df` |
| Origin main | `edb5e7e47eee79d4cbe7491b2b5321b151c029df` |
| Scope | Resolve M1-M7 from the independent G7 review |
| Production code changed | Yes |
| Migration changed | No |
| New migration added | No |
| Compatibility broadened | No |
| G7 executed | No |
| G8 executed | No |

## M6 - Deterministic Trade Identity

The old entry path recovered the persisted trade with an unordered
`persisted.getTrades().stream()...findFirst()` lookup. Production now persists
the newly created `Trade` directly through the transaction's `EntityManager`,
flushes it, and returns that same managed mutation's generated trade ID.

The identity regression test keeps another CFD trade on the account and proves:

```text
CREATED_TRADE_ID = PROVENANCE_TRADE_ID
UNORDERED_IDENTITY_SELECTION = NO
```

## M1 - Explicit G4 Financial Invariants

The positive service and PostgreSQL lifecycle evidence now asserts:

| Invariant | Result |
|---|---|
| Signed long CFD exposure | PASS, persisted quantity `+1000` |
| Base inventory delta | PASS, EUR inventory `0 -> 0` |
| Full notional debit | PASS, notional `1100.00`, cash delta `-2.50` fee only |
| Margin is not a spot purchase | PASS, existing G2 margin evidence is `11.00` against `1100.00` notional and no EUR inventory is created |
| Authoritative entry fill price | PASS, fill `1.10000`; persisted entry `1.10000`; limit/reference `1.11000` |
| MTM unrealized effect | PASS, mark `1.11000` produces `10.00` unrealized PnL |
| MTM realized cash unchanged | PASS |
| MTM entry price unchanged | PASS |
| MTM signed quantity unchanged | PASS |
| MTM base inventory unchanged | PASS |

## M2 - Cost Fail-Closed

The current PAPER execution boundary represents explicit execution fees only via
`BrokerOrder.Fill.fee`, and the persisted fill fee is non-null. Unsupported
additional provider costs have no representable input into the PAPER CFD
settlement boundary; they cannot be silently converted to zero there.

`PaperCfdSettlementServiceTest#rejectsUnavailableExecutionFeeAtTheFillBoundary`
proves an unavailable fee is rejected at the fill boundary rather than treated
as zero. Financing and swap remain explicitly out of scope as required by the
protocol.

## M3 - Complete Production Lifecycle

Test:

```text
PaperCfdSettlementPersistenceHardeningIntegrationTest
    #completesOneProductionBackedCfdLifecycleAcrossDurableReloads
```

The single PostgreSQL/Testcontainers scenario performs:

```text
flat
-> PaperCfdSettlementService entry
-> committed persisted open CFD
-> EntityManager clear and durable reload
-> PaperCfdSettlementService.refreshEquity MTM
-> PaperCfdSettlementService exit
-> EntityManager clear and durable reload
-> realized PnL and zero open exposure
```

No repository is mocked and no synthetic trade row is inserted for the open
state. Execution evidence rows are persisted only to provide the same
broker-order/fill boundary used by the production settlement service.

## M4 - Durable Sequential Entry Replay

`replaysCommittedEntryOnceAfterDurableReconstruction` settles one entry, clears
the persistence context, and processes the exact same broker order and fill
again.

```text
TRADE_COUNT: 1 -> 1
PROVENANCE_ROWS: 1 -> 1
OPEN_EXPOSURE: 1000 -> 1000
REALIZED_CASH: 9997.50 -> 9997.50
FEE_EFFECT: 2.50 -> 2.50
ADDITIONAL_ECONOMIC_EFFECT: NO
```

## M5 - Concurrent Entry Exactly Once

`concurrentEntryReplayProducesOneEconomicEffect` uses four independent
transaction contexts against PostgreSQL with the same broker order ID and fill
ID.

```text
CONCURRENT_ATTEMPTS: 4
PROVENANCE_ROWS: 1
TRADE_COUNT: 1
EXPECTED_EXPOSURE_DELTA: +1000
ACTUAL_EXPOSURE_DELTA: +1000
EXPECTED_CASH_DELTA: -2.50
ACTUAL_CASH_DELTA: -2.50
EXPECTED_FEE_EFFECT: 2.50
ACTUAL_FEE_EFFECT: 2.50
ECONOMIC_EFFECT_COUNT: 1
EXPOSURE_EFFECT_COUNT: 1
CASH_EFFECT_COUNT: 1
FEE_EFFECT_COUNT: 1
UNCAUGHT_UNIQUENESS_CONFLICT: NO
```

## M7 - Entry Transaction Rollback

`rollsBackEntryEconomicMutationWhenProvenanceCompletionFails` spies on the
production `completeEntryClaim` collaborator and throws after the entry trade
has been flushed and the incomplete provenance claim exists.

After rollback:

```text
PROVENANCE_CLAIM: absent
TRADE: absent
EXPOSURE_EFFECT: 0
CASH_EFFECT: 0
FEE_EFFECT: 0
PARTIAL_STATE_REMAINING: NO
```

The exact same fill is then retried successfully, proving the rolled-back claim
does not poison future processing.

## Finding Status

```text
M1 = RESOLVED
M2 = RESOLVED
M3 = RESOLVED
M4 = RESOLVED
M5 = RESOLVED
M6 = RESOLVED
M7 = RESOLVED
UNRESOLVED_PREVIOUS_MEDIUM_FINDINGS = NONE
NEW_HIGH_FINDINGS = NONE
NEW_MEDIUM_FINDINGS = NONE
```

## Compatibility and Safety

```text
CASH_CFD = ALLOWED
INVENTORY_CFD = REJECTED
UNKNOWN_CFD = REJECTED
ADDITIONAL_COMPATIBILITY_PAIRS_CHANGED = NO
AUTOMATIC_EXECUTION = NO
HUMAN_AUTHORIZATION_REQUIRED = YES
CTRADER_SAFETY_EXECUTION_ENABLED = false
LIVE_CFD_SETTLEMENT_AUTHORIZED = NO
REAL_CTRADER_ORDERS = 0
REAL_CTRADER_CLOSES = 0
PROVIDER_MUTATIONS = 0
```

## Validation

| Validation | Result |
|---|---|
| `PaperCfdSettlementServiceTest` | PASS, 10 tests |
| `PaperCfdSettlementPersistenceHardeningIntegrationTest` | PASS, 10 PostgreSQL tests |
| `PaperCfdDeterministicLifecycleIntegrationTest` | PASS, 5 tests |
| `PaperCfdEntryProvenanceAtomicityIntegrationTest` | PASS, 3 PostgreSQL tests |
| `SettlementCompatibilityPolicyTest` | PASS |
| `PostgresFreshInstallMigrationTest` | PASS |
| `trading-core ./mvnw -q test` | PASS |
| `trading-core ./mvnw -q verify` | PASS |
| `risk-domain mvn -q verify` | PASS |
| `git diff --check` | PASS |
| Repository quality scan | NOT RUN |

The known unrelated `broker-service` coverage issue remains outside this
corrective run.

## Qualification Status

```text
G4_CORRECTIVE_EVIDENCE = READY_FOR_REVIEW
G5_CORRECTIVE_EVIDENCE = READY_FOR_REVIEW
G6_CORRECTIVE_EVIDENCE = READY_FOR_REVIEW
G7 = PENDING
G8_EXECUTED = NO
READY_FOR_G8 = NO
CFD_IMPLEMENTED = YES
CFD_PROVEN = NO
CFD_VALIDATED = NO
READY_TO_REPEAT_INDEPENDENT_G7_REVIEW = YES
```

This run does not approve G7 and does not execute G8. The next action is a fresh
independent read-only G7 review of the current diff and this report.

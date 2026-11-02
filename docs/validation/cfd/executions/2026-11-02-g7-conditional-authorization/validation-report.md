# CFD Conditional G7 Qualification Run

## Execution Metadata

| Field | Value |
|---|---|
| Date | 2026-11-02 |
| Branch | `validation/cfd-epic-qualification` |
| Repository SHA | `edb5e7e47eee79d4cbe7491b2b5321b151c029df` |
| Origin main | `edb5e7e47eee79d4cbe7491b2b5321b151c029df` |
| Scope | Conditional `CASH + CFD` compatibility qualification and immediate G4-G6 evidence |
| Compatibility policy changed | Yes, only `CASH + CFD` |
| Provider execution enabled | No |
| G8 executed | No |

## Authorization Boundary

The authorized transition was applied exactly as specified:

```text
CASH + CFD = ALLOWED
INVENTORY + CFD = REJECTED
UNKNOWN + CFD = REJECTED
```

No other settlement compatibility pair was changed. Generic PAPER settlement
continues to reject CFD and requires the dedicated `PaperCfdSettlementService`.

## Positive G4-G6 Evidence

The following positive tests were enabled and executed through the real PAPER
settlement and PostgreSQL/Testcontainers persistence paths:

| Evidence | Result |
|---|---|
| `PaperCfdSettlementServiceTest` | PASS, 8 tests |
| `PaperCfdDeterministicLifecycleIntegrationTest` | PASS, 5 tests |
| `PaperCfdSettlementPersistenceHardeningIntegrationTest` | PASS, 7 tests |
| `PaperCfdEntryProvenanceAtomicityIntegrationTest` | PASS, 3 tests |
| `SettlementCompatibilityPolicyTest` | PASS, 2 tests |

The persistence evidence covers durable reconstruction, MTM, close semantics,
same-fill replay, rollback, and four concurrent identical entry attempts. The
concurrent entry run produced one trade, one exposure effect, one fee/cash
effect, and one provenance row.

## Validation Commands

| Command | Result |
|---|---|
| `trading-core ./mvnw -q -Dtest=SettlementCompatibilityPolicyTest,PaperCfdSettlementServiceTest,PaperCfdDeterministicLifecycleIntegrationTest,PaperCfdSettlementPersistenceHardeningIntegrationTest,PaperCfdEntryProvenanceAtomicityIntegrationTest test` | PASS, 25 tests |
| `trading-core ./mvnw -q test` | PASS |
| `trading-core ./mvnw -q verify` | PASS |
| `risk-domain mvn -q verify` | PASS |
| `git diff --check` | PASS |
| Repository quality scan | NOT RUN |

The known unrelated repository quality issue remains: `broker-service` reports
`75.33%` line coverage against the required `80%` threshold.

## Safety Invariants

```text
AUTOMATIC_EXECUTION = NO
HUMAN_AUTHORIZATION_REQUIRED = YES
CTRADER_SAFETY_EXECUTION_ENABLED = false
REAL_CTRADER_ORDERS = 0
REAL_CTRADER_CLOSES = 0
PROVIDER_MUTATIONS = 0
INVENTORY_PLUS_CFD = REJECTED
UNKNOWN_PLUS_CFD = REJECTED
G8 = NOT_RUN
```

## Gate Status

```text
G1 = PASS
G2 = PASS
G3 = PASS
G4 = PASS
G5 = PASS
G6 = PASS
G7 = BLOCKED_PENDING_INDEPENDENT_REVIEW
G8 = BLOCKED
```

G4-G6 passed their immediate qualification evidence. G7 is not marked PASS by
the implementation agent because the protocol requires an independent review
with no HIGH/MEDIUM accounting or risk findings. G8 remains prohibited until
G7 is confirmed by that review and separately authorized.

## Decision

```text
POSITIVE_CFD_ACCOUNTING = PROVEN
POSITIVE_CFD_LIFECYCLE = PROVEN
POSITIVE_CFD_PERSISTENCE_AND_EXACTLY_ONCE = PROVEN
CONDITIONAL_G7_COMPATIBILITY_CHANGE = APPLIED_NARROWLY
CFD_VALIDATED = NO
```

No rollback is indicated by the executed G4-G6 evidence. Human review is still
required to confirm G7; no provider-backed execution or G8 application-path
qualification may begin before that review.

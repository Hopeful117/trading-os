# Story 0099 Resumed PAPER Derivative Lifecycle Report

Date: 2026-10-14
Runtime: `trading-e2e-0099`

## Scope

This report resumes the existing PAPER Challenge runtime after the previous
validated ExecutionIntent expired. The account and Challenge were preserved. A
fresh TradePlan, acceptance, PRE_TRADE Risk evaluation, and ExecutionIntent were
created through public APIs. The run stopped at the first genuine execution
blocker. No production code or direct database mutation was performed.

## Runtime Identity

- Runtime Compose project: `trading-e2e-0099`.
- Runtime services: 13 containers, all running before the fresh chain.
- Repository HEAD: `9a402e649b627adbf0aa895dbb1a435d11fda750`.
- `origin/main`: `9a402e649b627adbf0aa895dbb1a435d11fda750`.
- Runtime tested SHA: `9a402e649b627adbf0aa895dbb1a435d11fda750`.
- Story 0099 merge SHA: `9a402e649b627adbf0aa895dbb1a435d11fda750`.
- DevLog Story Agent was unavailable because its provider quota was exhausted.
- Existing Story 0098 and Story 0099 reports were preserved.

## Existing Context Recovery

| Entity | Identifier | Result |
|---|---|---|
| User | `fb568107-ab95-4b44-bc37-d35379e14beb` | PASS |
| BrokerAccount | `2db55dd0-1977-422e-94ad-b99a30cd810d` | PASS, PAPER |
| Account | `db936193-175b-404c-9a52-9c37edeed9c0` | PASS, USD/CASH |
| ChallengeInstance | `0a47e3ef-9e49-4714-a75a-29323bb9b01d` | PASS, ACTIVE |
| Historical expired intent | `2fb1050e-9c47-4428-8e7e-446dcc307183` | Preserved, unchanged |

The recovered account returned:

```text
realizedBalance: 50000.00 USD
peakEquity:      50000.00 USD
valuation:       FRESH
positions:        []
```

The recovered Challenge returned `ACTIVE` with current value `50000.00 USD`.
The previous intent remained `VALIDATED` with its original expiration and was
not reactivated or modified.

## Fresh Market Data

The existing market remained:

```text
marketId:        5209423a-ccc7-4c35-aca0-7bc3c43cb6ea
provider:        KRAKEN
symbol:          BTC/USD
productType:     LINEAR_PERPETUAL
tradingStatus:   OPEN
tradable:        true
```

A fresh Kraken Futures REST quote was captured at
`2026-10-14T17:26:19Z` for provider symbol `PF_XBTUSD`:

```text
bid:             81813
ask:             81814
last/reference:  81813
```

The provider response did not expose a timestamp, so the local capture time is
the recorded observation time. The public market catalogue confirmed the
canonical product identity and tradability.

## Fresh Chain

The previous accepted TradePlan was not reused because its reference price was
stale relative to the fresh quote. The minimum fresh public workflow was used:

```text
fresh market quote
→ new manual TradePlan
→ ACCEPTED version 2
→ fresh PRE_TRADE Risk
→ fresh ExecutionIntent
```

| Entity | Identifier | Result |
|---|---|---|
| Fresh TradePlan | `ca945995-73ce-3fdf-80ca-d10426e9533d` | Created and accepted |
| Fresh PRE_TRADE Risk | `386321a4-6798-4c33-b048-150754fd817a` | `COMPLETED`, `APPROVED` |
| Fresh ExecutionIntent | `13fe94fc-471a-487d-979e-5641f6790406` | `201`, `VALIDATED` |

The fresh plan used `LONG`, `MARKET`, quantity `0.001`, reference price
`81813`, stop `80000`, and monetary risk `10`.

## Fresh ExecutionIntent Timing

```text
createdAt:             2026-10-14T17:26:38.933123Z
expiresAt:             2026-10-14T17:46:38Z
execution request:     2026-10-14T17:26:38Z capture time
remaining lifetime:    approximately 20 minutes at request creation
```

The public validation response was `201 Created`. The execution request was
sent immediately by the same command after validation. It did not return within
the 120-second command timeout, so it was not retried blindly.

## Snapshot A

The authoritative pre-entry snapshot immediately before the fresh chain was:

| Field | Value |
|---|---:|
| Timestamp | `2026-10-14T17:25:51.577723Z` |
| Realized balance | `50000.00 USD` |
| Unrealized P&L | `0` |
| Equity | `50000.00 USD` |
| Peak equity | `50000.00 USD` |
| Valuation status | `FRESH` |
| Open positions | `[]` |
| Challenge status | `ACTIVE` |
| Challenge current value | `50000.00 USD` |
| Challenge current profit | `0E-12` |
| Target progress | `0E-12` |

## Execution Result and First Blocker

The execution request was:

```text
POST /api/v1/executions/13fe94fc-471a-487d-979e-5641f6790406/execute
```

The client timed out after 120 seconds. The execution request did enter the
Trading Core pipeline. A JVM thread dump showed the request in:

```text
ExecutionController.execute
→ ExecuteTradeService.execute
→ ExecutionFinalizationStep.execute
→ PaperSettlementService.settle:146
→ ChallengeReevaluationService.reevaluate:47
```

The application transaction had already performed the Account update and flush
at `PaperSettlementService.java:142-144`. It then called a `REQUIRES_NEW`
Challenge reevaluation while the outer execution transaction still held the
Account row lock. PostgreSQL verification showed:

```text
pid 69: idle in transaction, waiting for client, previous query UPDATE accounts...
pid 70: active, waiting on transactionid lock, ChallengeInstance query
pid 71: active, waiting on tuple lock, ChallengeInstance query
```

The scheduled Challenge monitor also stopped completing after the execution
request began. This is consistent with a transaction/lock deadlock or indefinite
lock wait caused by nested Challenge reevaluation during PAPER settlement.

The public API state remained `VALIDATED` because the outer execution transaction
had not committed. No safe retry was attempted.

## Persistence Evidence

Read-only PostgreSQL verification returned:

```text
Fresh intent:       VALIDATED, no active_attempt_id
ExecutionAttempt:   0 durable rows for fresh intent
T1 evaluation:      0 durable rows for fresh intent
Execution events:   ExecutionIntentCreated, ExecutionIntentValidated only
Trades:             0 rows for the account
```

The absence of durable attempt/T1/Trade rows is expected while the execution
transaction is blocked before commit. The thread dump proves the request reached
Paper settlement, but it does not establish a committed broker or trade result.

No Broker Service log showed a live order submission. The account remained PAPER.

## Workflow Matrix

| Stage | Result | Evidence |
|---|---|---|
| Existing runtime recovery | PASS | 13 Compose services running |
| Account/Challenge recovery | PASS | USD/CASH account, ACTIVE Challenge |
| Fresh Market Data | PASS | OPEN tradable KRAKEN LINEAR_PERPETUAL, quote 81813 |
| TradePlan reuse/new version | PASS | New plan required due fresh reference price |
| Human acceptance | PASS | New plan version 2 `ACCEPTED` |
| Fresh PRE_TRADE Risk | PASS | `386321a4...`, `APPROVED` |
| Fresh ExecutionIntent | PASS | `13fe94fc...`, `201`, `VALIDATED` |
| Intent executed before expiration | FAIL | Request timed out in settlement |
| T1 Risk | NOT REACHED / NOT DURABLY OBSERVED | No durable T1 row; pipeline later reached settlement |
| PAPER ENTRY | BLOCKED | Execution transaction did not complete |
| OPEN Trade | NOT REACHED | No durable Trade |
| CASH settlement | BLOCKED | Settlement entered but did not commit |
| Position projection | PASS | Pre-entry positions were empty |
| Fresh MTM | NOT REACHED | No committed position |
| Open-position Challenge monitoring | BLOCKED | Scheduler blocked after settlement contention |
| Close | NOT REACHED | No committed entry |
| Realized settlement | NOT REACHED | No committed entry |
| Post-close Challenge monitoring | NOT REACHED | No committed entry |
| Angular verification | NOT EXECUTED | Backend blocker before OPEN state |

## Financial Reconciliation

Only Snapshot A exists. Snapshots B-F are not available because no committed
entry was produced.

```text
Opening fee:       not committed / not observable
Closing fee:       not applicable
Gross P&L:         not applicable
Net realized effect: 0 observed in durable account state
```

The durable account remained at `realizedBalance=50000.00` and `equity=50000.00`
with no positions. No accounting discrepancy can be computed beyond the
uncommitted transaction state.

## Findings

### BLOCKER: PAPER settlement deadlocks on nested Challenge reevaluation

Severity: `BLOCKER` for the PAPER derivative lifecycle.

Responsible boundary:

```text
trading-core
PaperSettlementService.settle:142-146
ChallengeReevaluationService.reevaluate:45-50
```

Likely root cause: `PaperSettlementService` flushes the Account update inside
the outer execution transaction and then invokes Challenge reevaluation with
`REQUIRES_NEW`. The new transaction queries Challenge/account state while the
outer transaction retains locks needed by the reevaluation path. The execution
request and scheduled monitor then wait indefinitely on PostgreSQL locks.

### OBSERVATION: Previous optimistic-lock collision

The earlier transient optimistic-lock collision remains historical context. No
new optimistic-lock exception was observed in this run; the observed failure is
an indefinite lock wait during settlement/Challenge reevaluation.

### KNOWN LIMITATION: Challenge DTO projection

Daily reference balance, daily loss remaining, total drawdown remaining, and
`evaluatedAt` remain nullable in the public Challenge DTO.

### KNOWN LIMITATION: Risk Day fidelity

V1 still uses the first authoritative observation within the Risk Day rather
than reconstructing the exact Kraken 00:30 UTC baseline.

## Final Verdict

```text
STORY 0099 RUNTIME: PASS
EXECUTION INTENT EXPIRATION: EXPECTED BEHAVIOR
E2E PAPER DERIVATIVE LIFECYCLE: FAIL

FIRST PRODUCT BLOCKER: PAPER settlement deadlocks while invoking nested
ChallengeReevaluationService REQUIRES_NEW after flushing the Account update.

RECOMMENDED NEXT STORY: Make PAPER settlement and post-settlement Challenge
reevaluation transactionally non-blocking, preserving CASH/LINEAR_PERPETUAL
settlement, T1 Risk, and Challenge fail-closed behavior.
```

# Story 0100 Final PAPER Derived Lifecycle Runtime Report

Date: 2026-10-14
Runtime: `trading-e2e-paper-final`

## Scope

This run validated the final PAPER derived lifecycle on the current `main`
after Story 0100. The run stopped at the first product blocker. No production
code, Risk rule, Challenge rule, execution state, or database row was modified
directly.

## Runtime Identity

- Repository HEAD: `dafc1dbdd9014a20d0b93a0d7d9a1450ae50d874`.
- `origin/main`: `dafc1dbdd9014a20d0b93a0d7d9a1450ae50d874`.
- Story 0100 implementation parent: `d2c4c2cf9a6d4671053e866bbeb04c95f4015b9e`.
- ADR-054 was present and accepted.
- DevLog Story Agent was unavailable because its provider quota was exhausted.
- Existing Story 0098 and Story 0099 reports were preserved.

## Stack Validation

The old Trading OS containers were stopped and removed without removing their
volumes. A fresh Compose project was then built and started:

```text
docker compose project: trading-e2e-paper-final
network: trading-e2e-paper-final_trading-network
database: fresh PostgreSQL volumes
services: 13 running
```

Evidence:

- All application images built successfully.
- Trading Core applied Flyway migrations 1 through 32, including V32
  `challenge reevaluation outbox`.
- Market Data completed its startup market synchronization.
- Gateway became reachable and returned `401` for unauthenticated health access.
- The isolated Compose runtime remained running after the validation stopped.

## Architecture Checks

- ADR-054 durable post-commit reevaluation is present.
- `PaperSettlementService` flushes settlement state and enqueues the outbox
  request in the same transaction.
- `ChallengeReevaluationDeliveryService` claims and delivers requests in an
  independent transaction with retry and lease renewal.
- `ChallengeInstanceRepository` retains `PESSIMISTIC_WRITE` locking.
- No direct pre-commit Challenge reevaluation call remains in the finalization
  path.

## Public Runtime Workflow

| Stage | Result | Evidence |
|---|---|---|
| Fresh Compose startup | PASS | 13 services running |
| Flyway V32 | PASS | Trading Core log: successfully applied 32 migrations |
| User registration/login | PASS | Public Gateway APIs returned success |
| PAPER broker account | PASS | Public provisioning succeeded |
| CASH settlement model | PASS | Required to match the Challenge definition |
| Challenge catalog/start | PASS after using `settlementModel=CASH` | Independent final provisioning proof returned `200`, Challenge `8abbb14d-1448-4eee-a4e6-825e8d821e88`; the default `INVENTORY` account correctly returned `400` because the Challenge requires `CASH` |
| Market catalogue | PASS | Public catalogue exposed tradable `LINEAR_PERPETUAL` markets |
| Active scan | PASS with partial result | Terminal status `PARTIALLY_COMPLETED` is supported by the scan domain |
| Derived `LINEAR_PERPETUAL` opportunity | BLOCKED | No compatible opportunity was produced |
| Derived TradePlan | NOT REACHED | No compatible opportunity ID |
| T0 Risk | NOT REACHED | No TradePlan |
| ExecutionIntent | NOT REACHED | No T0 authorization |
| T1 Risk | NOT REACHED | No execution request |
| PAPER entry/settlement | NOT REACHED | No execution |
| Outbox delivery | NOT REACHED | No committed settlement trigger |
| Close/realized settlement | NOT REACHED | No entry |
| Snapshots A-F | NOT AVAILABLE | Lifecycle stopped before TradePlan creation |

## First Product Blocker

The selected fresh derivative market was:

```text
marketId:   09b6a477-dd67-4f92-908b-9e5bfe6495f4
symbol:     BTC/USD
productType: LINEAR_PERPETUAL
tradable:   true
```

The public `ALL_ELIGIBLE` scan returned terminal `PARTIALLY_COMPLETED` and
active opportunities, but no opportunity whose `marketId` belonged to a
tradable `LINEAR_PERPETUAL` market. The strategy logs showed matches only for
SPOT markets:

```text
0G/EUR, 0G/USD, 1INCH/EUR, 1INCH/USD,
2Z/EUR, 2Z/USD, A/EUR, A/USD
```

The derivative candidate filter returned an empty result for a fresh account
and scan (`accountId=cf5f2f54-12b4-455b-a306-24d919af9a9c`,
`scanId=adb7cd0e-3e47-46ba-86bd-feb555889bb8`). Therefore the required derived
workflow cannot reach TradePlan creation without selecting a SPOT opportunity,
which would violate the Challenge `LINEAR_PERPETUAL` requirement.

This is a Market Intelligence/runtime data-selection blocker, not a reason to
bypass Challenge compatibility or create a manual TradePlan in this run.

## Known Non-Blockers

- `POST /api/v1/markets/synchronize` returned `403` for a normal user. This is
  already documented by Story 0098; startup synchronization populated the
  public catalogue, so the privileged endpoint was not used as a workaround.
- `PARTIALLY_COMPLETED` is a supported terminal Active Scan state. The existing
  proof script incorrectly accepts only `COMPLETED`/`SUCCEEDED`; the public
  scan state itself was not treated as a defect.
- Omitting `settlementModel` creates an `INVENTORY` account. The Challenge
  correctly rejects it because its required model is `CASH`; the final run used
  the public `CASH` provisioning field.

## Final Verdict

```text
STORY 0100 ARCHITECTURE: PASS
STORY 0100 FRESH RUNTIME STARTUP: PASS
E2E PAPER DERIVED LIFECYCLE: BLOCKED
FIRST PRODUCT BLOCKER: Market Intelligence produced no compatible
LINEAR_PERPETUAL opportunity for the fresh derived scan.
NO STORY 0101 CREATED
```

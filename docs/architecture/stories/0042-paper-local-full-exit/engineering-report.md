# Engineering Report - Story 0042

## Architecture Mapping

| Invariant | Implementation evidence | Status |
|---|---|---|
| PAPER authority is Trading Core | `PaperExitService`, `PaperSettlementService` | Implemented |
| LIVE authority is Broker | Existing `PositionCloseService` path unchanged | Regression passed |
| Local Trade target | `ExecutionIntent.targetTradeId`, `PaperExitService` | Implemented |
| Explicit EXIT purpose | `ExecutionPurpose`, intent persistence/mapper | Implemented |
| Server-derived side/quantity | `PaperExitService` from reloaded Trade | Implemented |
| Full close only | Settlement requires fill quantity equal to Trade quantity | Implemented |
| Gross deterministic PnL | `TradingCalculatorService.calculatePnL()` | Focused tests passed |
| Per-asset accounting | Exit branch in `PaperSettlementService` | Focused tests passed |
| Atomic local settlement | Spring transaction plus Account/Trade mutation | Acceptance-proven |
| Durable concurrent protection | JPA `@Version` on Account and Trade; acceptance race test | Acceptance-proven |
| Idempotent replay | Unique intent key and target identity checks; acceptance replay test | Acceptance-proven |
| Story 0041 OPEN query | CLOSED status naturally excluded; acceptance reload/query test | Acceptance-proven |
| No PAPER broker close | Controller routes local request to `PaperExitService`; acceptance no-interaction test | Acceptance-proven |

## Accounting Invariant

For no unrelated account mutations:

```text
final quote = initial quote + gross PnL - entry fee - exit fee
final base = initial base
final equity = initial equity + gross PnL - entry fee - exit fee
```

The implementation stores gross PnL on `Trade` and applies fees to Account
cash/equity because the current Trade schema has no fee field. The current
simulator produces zero fees, while focused settlement tests prove non-zero
exit fee handling.

## Risk Boundary

EXIT intents skip entry-only TradePlan/T1 revalidation. They still require
authenticated Account/Trade ownership, canonical BrokerAccount resolution,
PAPER mode, OPEN state, valid target quantity, and an executable simulated
fill. No PAPER risk-facts redesign or risk override was introduced.

## Review Gate

```text
HUMAN_DIFF_REVIEW = APPROVED
CODE_REVIEW = APPROVED
REPOSITORY_ANALYSIS = APPROVED
IMPLEMENTATION_PLAN = APPROVED
STORY_ACCEPTANCE = APPROVED
```

No blocking findings remain. The known compatibility-shaped close response is
non-blocking future refinement and is intentionally unchanged in this Story.

# FTMO LIVE Account Capability-Aware Readiness Validation

## Verdict

`READINESS_BLOCKED_BY_PROVIDER`

The account is connected and partially observable. Account display remains
available, while T0 is explicitly not evaluated at the status-only boundary and
LIVE execution remains blocked.

## Observed State

| Fact | Result |
|---|---|
| Trading OS account | `7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456` |
| Provider | cTrader |
| External account | `49411967` |
| Execution mode | `LIVE` |
| Connection | `CONNECTED` |
| Balance | `50179.47 USD` |
| Equity | `50179.47 USD`, `BROKER_DERIVED` empty-account fact |
| Positions | Empty and available |
| Orders | Empty and available |
| Snapshot | `FRESH`, `INCOMPLETE` |
| Used margin | Unavailable |
| Free margin | Unavailable |

## Readiness

```text
status=CONNECTED_BUT_FACTS_INCOMPLETE
accountObservation=PARTIAL
t0Risk=NOT_EVALUATED
liveExecution=BLOCKED
executionAllowed=false
riskEvaluationAllowed=false
unavailableFacts=USED_MARGIN_UNAVAILABLE, FREE_MARGIN_UNAVAILABLE
derivedFacts=EQUITY
```

`NOT_EVALUATED` is intentional: the status endpoint has no selected Risk policy
or invariant context and must not turn structurally available facts into a
positive T0 result. Aggregate Risk evaluation remains subject to the actual
`RiskFactRequirements` and fail-closed admission rules.

## Read-Only Operations

The following authorized reads succeeded after rebuilding and restarting
`trading-app`:

- account metadata;
- account positions;
- account read-only status;
- EUR/USD market catalogue.

No synchronization, risk evaluation with persistent state, approval, execution
authorization, order, position mutation or permission change was performed.

## Remaining Blockers

- The provider does not expose authoritative aggregate `usedMargin` for this
  account observation.
- The provider does not expose authoritative aggregate `freeMargin` for this
  account observation.
- No approved contract proves that empty positions and orders imply zero used
  margin.

## Validation Evidence

- Focused Trading Core account-status and controller tests passed.
- Docker build/package completed successfully.
- `git diff --check` passed.
- The first request immediately after restart failed transiently while the
  container was starting; the same read succeeded after startup completed.

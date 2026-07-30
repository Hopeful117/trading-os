# ADR-028 Risk Domain

Pure Java 21 implementation of the deterministic risk domain. The module has
no Spring, persistence, broker, messaging or AI dependency.

## Evaluation contract

Infrastructure first captures the account, portfolio, market, trading and
effective rule-set snapshots. It then maps the immutable TradePlan version to
`ProposedTrade` and uses `RiskEvaluationContextBuilder`. The builder validates
and assembles these inputs only; its context constructor is not public.

`DeterministicRiskEngine` explicitly executes observed metrics, portfolio
projection, derived metrics, rule traversal and result aggregation. Rules see
only `RiskRuleEvaluationContext`, which contains the precomputed metrics.

`RiskEngines.standard(version, clock)` provides the initial ADR-028 rule set:

- `MAX_POSITION_RISK`;
- `MAX_EXPOSURE`;
- `DAILY_DRAWDOWN`.

Configured thresholds use decimal ratios (`0.01` means 1%).

## V1 formulas

- floating PnL = equity - balance;
- free margin = equity - used margin;
- projected exposure = exposure of the net projected positions;
- projected margin = current non-position margin plus projected position margin;
- portfolio heat = losses at stop of projected positions;
- position risk ratio = projected instrument loss at stop / balance;
- exposure ratio = projected exposure / equity;
- daily drawdown ratio =
  `(max(daily start balance - equity, 0) + projected portfolio heat)
  / daily start balance`.

Opposite trades reduce the existing position proportionally, close it at equal
quantity, or open only the remainder in the opposite direction. Projected
positions are returned in stable instrument order.

All monetary values involved in one calculation must use the account currency.
Conversion is deliberately outside the domain and must already be reflected in
the immutable snapshots.

## Determinism and failure behavior

The caller supplies the clock. Rules execute in stable category, priority and
identifier order. Every rule is evaluated even after a blocking violation.
Unknown configured rules produce `INCOMPLETE`; calculation or rule errors
produce `FAILED`. Neither state contains a business authorization decision.
Only a `FAILURE` with `BLOCKING` severity produces `REJECTED`; informational
failures remain non-blocking.

Policy conflict semantics are delegated to
`RuleConflictResolutionStrategy`. The standard rules use upper-bound
resolution; future rules register their own strategy without changing the
resolver.

`RiskEvaluationRecord` keeps the complete context and result.
`RiskReplayService` evaluates it again with the recorded timestamp and compares
the complete artifact.

Run the verification suite with:

```shell
mvn test
```

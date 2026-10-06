# Story 0077 - Implementation Plan

## Design

Use immutable context facts rather than making the Risk Engine aware of reset
schedules or providers.

- `DailyRiskBaseline` carries the authoritative reference balance, effective
  instant, source and provenance.
- `AccountSnapshot` carries `accountStartingBalance` and
  `dailyRiskBaseline`.
- `ProjectedMetrics` carries both daily and total projected drawdown.
- `RiskMetrics` carries both daily and total drawdown ratios while retaining
  the existing `DAILY_DRAWDOWN` identifier.
- `MAX_TOTAL_DRAWDOWN` evaluates the projected total drawdown ratio against its
  configured maximum ratio.
- Existing profiles remain valid; the new rule is optional.
- Trading Core currently supplies the account balance as the starting balance
  when no separate persisted lifetime reference exists. This preserves the
  current paper/live boundary without fabricating a provider-specific value;
  the supplied value is captured in the immutable context for later lifecycle
  integration.

## Changes

1. Add the baseline value object and extend Risk snapshots and metrics.
2. Add and register `MAX_TOTAL_DRAWDOWN`.
3. Update profile validation and database rule vocabulary.
4. Propagate the new facts through both pre-trade evaluation paths.
5. Add focused domain, policy, validation and integration regression tests.

## Verification

- `mvn -pl risk-domain test`
- `mvn -pl trading-core -am test` or the smallest successful equivalent if
  repository dependency resolution prevents the command.
- `git diff --check`

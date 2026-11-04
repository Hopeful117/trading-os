# Implementation Report - Story 0195

## Summary

Implemented the first incremental slice of ADR-086. The Risk Domain now
distinguishes an invariant with unavailable evidence from an invariant that
failed its deterministic threshold, while the aggregate decision remains
strictly fail-closed.

## Implemented Changes

- Added `RuleStatus.INCOMPLETE` to the existing rule-status vocabulary.
- Added evidence availability hooks to `RiskRule` and implemented explicit
  equity-dependent diagnostics for exposure and drawdown rules.
- Allowed the normalized account boundary to represent unavailable equity
  without replacing it with balance or zero.
- Made floating PnL, projected drawdown, exposure ratio and drawdown ratios
  unavailable when their equity input is missing.
- Updated `DeterministicRiskEngine` to emit per-rule `INCOMPLETE` results.
- Updated `RiskResultAggregator` to withhold global decisions whenever any rule
  is incomplete.
- Added invariant-specific evidence requirements to `RiskFactRequirements`.
- Preserved `EquityFact` through the legacy Broker Service risk snapshot mapping
  and marked Kraken TradeBalance equity as broker-reported evidence.
- Updated initial risk evaluation and T1 revalidation to pass inadmissible or
  unavailable equity as unavailable to the Risk Domain instead of failing before
  independent diagnostics can be produced.
- Added regression coverage proving position risk can pass while daily drawdown
  remains incomplete and the global decision is absent.

## Safety Guarantees

- No `equity = balance` fallback exists.
- No zero or stale equity fallback exists.
- `BROKER_DERIVED` equity remains inadmissible for strict policies unless the
  existing PAPER exception explicitly applies.
- An incomplete mandatory rule cannot produce an execution decision.
- T0 and T1 use the same `RiskFactRequirements` resolution and both preserve
  fail-closed behavior.
- No cTrader LIVE call, order, mutation or safety-flag change was performed.
- FTMO policy thresholds and version `1.0.0` were not changed.

## Validation

Passed:

- `mvn -q test` in `risk-domain`.
- Focused Risk Domain integration and projection tests.
- Focused Trading Core risk, provenance, provider-mapping, T0 and T1 tests.
- Focused Broker Service risk snapshot and security contract tests.
- `git diff --check`.

Passed:

- Complete `mvn -q test` in `trading-core` with the extended 300-second timeout.
- Complete `mvn -q test` in `broker-service` with the extended 300-second
  timeout.

Unavailable or not applicable:

- No provider validation was performed. cTrader remains unavailable for
  equity-dependent FTMO evaluation.
- No SonarQube scan was run because no applicable repository command or
  configured quality gate was identified during this change.

## Documentation Reconciliation

Updated canonical Story and ADR artifacts only. No additional runtime or API
documentation was required because the provider-neutral contract remains
internal and the existing architecture documentation already points to ADR and
Story directories.

## Vault Outcome

The Obsidian vault was not consulted. This Story was fully determined by the
approved repository ADRs, Story, current source and tests. No vault action is
proposed.

## Working Tree

The working tree contains the pre-existing uncommitted cTrader and Story 0194
changes, plus the ADR-086, Story 0195 and implementation changes listed above.
No commit, push, merge, reset or cleanup operation was performed.

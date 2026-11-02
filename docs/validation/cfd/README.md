# CFD Validation

This directory is the durable qualification framework for the Trading OS CFD
capability. It replaces an automatic sequence of implementation Stories with a
repeatable validation protocol.

## Protocol and Executions

`CFD-VALIDATION-PROTOCOL.md` defines the invariant gates, evidence required,
dependencies, and closure rule. It is independent of any commit or run date.

Each run is recorded below `executions/<run-id>/validation-report.md`. Reports
are immutable historical evidence and must record the exact repository SHA,
environment, commands, actual results, and blockers. Supporting files belong in
that run's `evidence/` directory.

Rerun the protocol by checking out a current validation branch, reading the
protocol, executing its focused commands and then the repository-standard
`trading-core` test and verification commands. Do not modify
`SettlementCompatibilityPolicy` as part of an ordinary qualification run.

## Maturity Vocabulary

- `IMPLEMENTED`: production code for the capability exists.
- `PROVEN`: required behavior has reproducible executable evidence.
- `VALIDATED`: every mandatory protocol gate has passed through the approved
  application architecture.

These states are independent. A capability can be implemented and proven while
remaining unvalidated because compatibility authorization or the real
application path is still blocked.

## Closure Rule

The CFD epic may close only when G1 through G8 are all `PASS`. There is no
weighted score or majority rule. Any mandatory `BLOCKED`, `FAIL`, or `NOT_RUN`
result means `EPIC_RESULT = NOT_READY`.

This protocol validates PAPER CFD capability only. It does not authorize LIVE
execution, cTrader orders, or provider mutations.

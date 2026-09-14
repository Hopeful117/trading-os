# Story 0040 Implementation Report

## Result

Implemented the mode-aware application Risk Facts boundary and corrected T0/T1
financial identity resolution. Production wiring uses `RiskFactsProvider`; the
Risk Domain remains unchanged and fact-source agnostic.

## Identity

- T0 starts from `Command.accountId` and requires `Account.brokerAccountId`.
- T1 loads `TradePlan.tradingAccountId` before loading the financial Account.
- T1 requires the canonical relation to match both configuration and
  `ExecutionIntent.brokerAccountId`.
- T1 success and unavailable persistence store the financial Account ID.
- Execution mode is read from the canonical linked `BrokerAccount`.

## Facts Authority

- LIVE facts are delegated to the existing broker-authoritative adapter and are
  mapped into neutral provider snapshots.
- PAPER facts are constructed from local Account balances and OPEN/CLOSED Trade
  state. No BrokerRiskFactsPort call is made for PAPER.
- PAPER ledger, margin, and protection gaps remain explicit unavailable facts;
  they fail closed without zero/default substitution.
- Market Data and RequiredMarginPort remain existing valuation and margin
  boundaries. No freshness policy or risk rule was changed.

## Validation

- Focused T0/T1 and mode-aware facts tests pass.
- Full Trading Core Maven test suite passes.
- `git diff --check` passes.

## Review State

This report does not approve code review, human review, commit, or merge.

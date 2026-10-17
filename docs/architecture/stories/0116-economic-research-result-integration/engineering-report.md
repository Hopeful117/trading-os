# Engineering Report - Story 0116

## Decision

Story 0116 is implemented within the approved `ADR-063` and `ADR-064`
boundaries. The first historical economic evaluation path is available in
Trading Core without adding fee/funding acquisition, persistence, public API,
dataset, PAPER, or Risk behavior.

## Workflow Evidence

- Story 0116: approved by the human engineer.
- Repository Analysis: approved by the human engineer.
- Implementation Plan: approved by the human engineer.
- Implementation: completed.
- Documentation reconciliation: completed through the Story implementation
  report.
- Independent code review: completed with no remaining findings.
- Code review: approved by the human engineer.

## Architecture

- `ADR-063`: `Accepted`, preserved.
- `ADR-064`: `Accepted`, preserved.
- Trading Core remains the authority for historical lifecycle economics.
- Market Data remains the authority for strict historical Order Book replay.
- Broker Service remains the authority for provider fee facts.
- Funding acquisition remains provider-blocked and outside this Story.
- No architecture amendment is required.

## Delivered Behavior

- Historical entry and exit MARKET executions use independent replay cutoffs.
- Exit side is opposite the entry side.
- Exit quantity is actual entry filled quantity.
- Partial entry and partial exit preserve closed and residual exposure.
- No-fill and unavailable-exit paths fail closed without synthetic prices or
  closure.
- Execution-adjusted P&L uses actual entry/exit execution prices and closed
  quantity.
- Missing commission/funding remain explicit cost states.
- Cost completeness remains separate from economic result availability.
- Authoritative NetPnL requires a fully costed closed lifecycle.
- NetReturn remains unavailable without a canonical denominator policy.
- Economic fingerprints are deterministic and include lifecycle/cost semantics.

## Validation Evidence

Passed:

```text
trading-core focused lifecycle/replay/economic tests
market-data OrderBookReplayServiceTest
trading-core -DskipTests package
git diff --check
```

Independent review confirmed no remaining findings after the final corrections.

Limitations:

- Full Trading Core integration verification remains environment-limited by
  PostgreSQL/Testcontainers connection failures and existing scheduled-task
  errors.
- Authenticated runtime historical economic proof was not available.
- SonarQube validation was not executed.

## Frozen Boundaries

`ObservedMarketOutcome`, `research-dataset-v1`, `feature-projection-v1`, PAPER,
Risk, fee acquisition, funding acquisition, public APIs, persistence, Market
Data production semantics, and Broker production semantics remain unchanged by
this Story.

## Vault Outcome

No curated vault context materially informed the implementation. No vault action
is proposed; the outcome remains proposal-free and read-only.

## Next Primary Story

**Economic Research Dataset**: define a separate versioned economic sample
contract that consumes `EconomicResult` while preserving execution provenance,
cost completeness, residual exposure, and unavailable NetPnL semantics. The
existing `research-dataset-v1` remains frozen.

## Integration Boundary

The human engineer may now review the staged diff and commit the dedicated Story
branch. Push, pull request creation, merge, and deployment remain separate human
decisions.

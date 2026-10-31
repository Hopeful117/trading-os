# Story 0180 - Implementation Report

## Summary

Implemented the first read-only CFD valuation boundary for PAPER exposures.
Market Data can now evaluate valuation facts without persisting a valuation
snapshot or refreshing provider observations. Trading Core can reconstruct a
persisted CFD contract, select conservative executable-side marks, calculate
deterministic gross PnL and apply an explicit directed currency conversion.

LIVE CFD valuation fails closed until provider-authoritative position
reconciliation exists.

## Changed Files

- `market-data/src/main/java/com/hope/trading/market_data/service/ValuationSnapshotService.java`
  - extracted reusable fact evaluation;
  - added non-persisting `evaluate` operation;
  - preserved existing snapshot persistence in `create`.
- `market-data/src/main/java/com/hope/trading/market_data/controller/InternalValuationFactsController.java`
  - added `POST /internal/v1/valuation-facts/batch`.
- `trading-core/src/main/java/com/hope/trading/trading_core/market_data/valuation/ReadOnlyMarketValuationPort.java`
  - added a read-only provider-neutral valuation boundary.
- `trading-core/src/main/java/com/hope/trading/trading_core/market_data/valuation/MarketValuationPort.java`
  - added typed source and structured conversion-leg evidence while preserving
    the existing fact constructors.
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/client/MarketValuationClient.java`
  - mapped the internal read-only endpoint;
  - prevented refresh/persistence side effects for read-only requests.
- `trading-core/src/main/java/com/hope/trading/trading_core/valuation/application/CfdValuationService.java`
  - added PAPER CFD valuation orchestration and immutable available/unavailable
    result handling;
  - validates the complete quote-to-reporting conversion chain and account
    reporting currency.
- `market-data/src/test/java/com/hope/trading/market_data/service/ValuationSnapshotServiceTest.java`
  - verifies read-only evaluation does not create snapshots.
- `trading-core/src/test/java/com/hope/trading/trading_core/risk/infrastructure/client/MarketValuationClientTest.java`
  - verifies read-only requests do not refresh observations and retain source
    timestamps.
- `trading-core/src/test/java/com/hope/trading/trading_core/valuation/application/CfdValuationServiceTest.java`
  - covers long/short PnL, conservative side selection, directed conversion and
    LIVE fail-closed behavior.

## Story Alignment

- PAPER uses persisted Trading Core exposure and read-only Market Data facts.
- LIVE does not promote local Trade state to provider authority.
- CFD PnL delegates to `CfdFinancialCalculator`.
- Cross-currency PnL requires an explicit directed conversion fact.
- Missing or incomplete contract/fact evidence fails closed.
- No Trade, Account, settlement, execution or valuation snapshot mutation is
  performed by the new path.
- No database migration, public API, Gateway change or Risk Domain production
  change was introduced.
- Existing snapshot acquisition remains available to existing generic callers.

## Validation

Passed:

- `market-data/./mvnw -q -DskipTests compile`
- `trading-core/./mvnw -q -DskipTests compile`
- `market-data/./mvnw -q -Dtest=ValuationSnapshotServiceTest test`
- `trading-core/./mvnw -q -Dtest=CfdValuationServiceTest,MarketValuationClientTest test`
- `git diff --check`

Additional review-fix validation:

- `trading-core/./mvnw -q -Dtest=CfdValuationServiceTest,MarketValuationClientTest test`
  passed after adding structured FX-leg validation and wrong-direction coverage.
- `market-data/./mvnw -q -DskipTests compile` passed after the review fixes.

Not fully passing or incomplete:

- `market-data/./mvnw -q test` failed in the pre-existing
  `CTraderDataStreamProviderTest.stopsAutomaticReconnectAfterMaximumAttempts`
  test: expected 3 streams but observed 2. The failure is outside the changed
  valuation files and was not reproduced by the targeted valuation test.
- `trading-core/./mvnw -q test` exceeded the 180-second command timeout after
  executing a substantial portion of the suite. No Story 0180-specific failure
  was reported before timeout; the full suite result is inconclusive.
- Repository-wide Maven reactor validation is not applicable because the
  repository contains independent Maven services rather than one root reactor.

## Documentation Outcome

Documentation update: Not required beyond the Story-local artifacts. The new
endpoint is internal, its request/response types are self-describing, and no
public API, configuration, operational procedure or user-facing behavior was
changed.

## Vault Outcome

The Obsidian vault was not consulted. The Story was fully specified by the
approved repository Story, ADRs and current source; no transverse vault context
was necessary.

No vault action is proposed. No `vault-outcome.json` is required.

## DevLog Outcome

DevLog context retrieval was attempted and failed with
`Authentication is required`. Repository evidence and accepted ADRs were used
instead.

## Remaining Risks

- Full module validation remains partially inconclusive because of the existing
  cTrader reconnect failure and the Trading Core timeout.
- The read-only path deliberately reuses existing observations and does not
  acquire or persist fresh provider ticks. Missing or stale observations fail
  closed, as required by the Story.
- The new service is an application capability and is not wired to a public
  controller; public exposure remains out of scope.

## Independent Review Resolution

The independent review identified two HIGH findings, one MEDIUM coverage gap and
one LOW robustness suggestion.

- HIGH FX direction: fixed by preserving structured conversion legs and
  validating the complete quote-to-reporting chain before calculation.
- HIGH FX provenance/timestamps: fixed by retaining per-leg rate, direction,
  price type, provenance, effective timestamp and captured timestamp in the
  provider-neutral fact and immutable result.
- MEDIUM test gap: added wrong-direction coverage and retained existing targeted
  tests for read-only behavior, freshness and no persistence. Full integration
  reconstruction coverage remains outside the completed validation because the
  affected module suites are not fully executable within the bounded run.
- LOW account reporting currency mismatch: fixed by rejecting an explicit
  reporting currency that differs from the account currency.

The final independent review after these corrections returned `PASS` with no
material findings. It verified snapshot reporting-currency consistency,
multi-hop timestamp aggregation, structured-leg propagation, direction checks,
composed-rate validation and compatibility constructors.

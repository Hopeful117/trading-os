# Story 0180 - Code Review

## Findings

The first local review identified no blocking defect. An independent review then
identified two HIGH findings in FX direction/provenance, one MEDIUM coverage gap
and one LOW account-currency robustness issue. The HIGH and LOW findings were
addressed in the current diff.

The implementation preserves the important boundaries:

- the existing persisted valuation-snapshot endpoint remains unchanged in
  behavior;
- the new valuation-facts endpoint calls the same fact evaluation logic without
  saving a `ValuationSnapshot`;
- the read-only Trading Core client skips provider refreshes that could create
  observations;
- CFD arithmetic remains delegated to `CfdFinancialCalculator`;
- PAPER is the only mode that evaluates the persisted local CFD exposure;
- LIVE fails closed before requesting Market Data facts;
- no schema, settlement, Risk Domain or broker mutation was added.

The current implementation additionally:

- retains structured FX conversion legs through the Trading Core boundary;
- validates each leg's direction, positive rate, provenance and timestamps;
- verifies the composed rate matches the reported quote-to-reporting rate;
- rejects reporting currencies that differ from the account currency;
- tests an incorrect FX direction as unavailable.

## Validation Evidence

Passed:

- `market-data/./mvnw -q -DskipTests compile`
- `trading-core/./mvnw -q -DskipTests compile`
- `market-data/./mvnw -q -Dtest=ValuationSnapshotServiceTest test`
- `trading-core/./mvnw -q -Dtest=CfdValuationServiceTest,MarketValuationClientTest test`
- `market-data/./mvnw -q -DskipTests compile`
- `git diff --check`

The full `market-data` suite remains blocked by the unrelated existing
`CTraderDataStreamProviderTest.stopsAutomaticReconnectAfterMaximumAttempts`
failure. The full `trading-core` suite exceeded the bounded 180-second command
timeout and is inconclusive. Neither result identified a Story 0180-specific
failure.

## Residual Risks

- The read-only path intentionally evaluates existing observations only. It does
  not acquire new provider ticks, so stale or missing facts remain unavailable.
- The new application service is not exposed through a public API, by design;
  consumers must be wired in a later approved Story if needed.
- Full module-suite evidence is incomplete due to the unrelated failure and
  timeout above.

## Independent Review Outcome

The final independent review returned `PASS` with no material findings. It
verified snapshot reporting-currency consistency, max timestamp aggregation for
all conversion legs, multi-hop tests, structured provenance propagation,
quote-to-reporting direction validation, composed-rate validation and backward
compatible `Fact` constructors.

No commit, push, merge or pull request was performed.

## Review Outcome

The implementation is technically ready for final human review within the
approved Story boundary. No commit, push, merge or pull request was performed.

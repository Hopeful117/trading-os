# Story 0069 - Implementation Report

## Result

Implemented the bounded Market Facts foundation inside `market-data` without
implementing Candidate Selection, ranking, strategy, Risk, or execution.

## Implementation

- Added `MarketActivityFact` with quote-notional activity calculated as
  `sum(observed base volume * observed VWAP)`.
- Added `MarketDataReadiness` with typed status, lookback, observed candle
  count, normalized candle count, synthetic count, freshness, and provenance
  fields.
- Added `MarketFactsRequest`, `MarketFactsResponse`, and
  `MarketFactStatus` as broker-neutral contracts.
- Added `MarketHistorySnapshot` to preserve provider-observed events separately
  from normalized continuity events.
- Extended `MarketDataProvider` with a compatibility-preserving snapshot
  method. Kraken overrides it with raw mapped events and normalized events.
- Added `MarketFactsService` with deterministic calculations, explicit request
  bounds, provider failure classification, and a 256-entry process-local cache.
- Added audit remediation for complete temporal coverage, evidence-based cache
  expiry, duplicate/conflicting candle handling, market-provider dispatch,
  activity freshness, cache identity metadata, and endpoint-specific service
  authorization.
- Added the internal endpoint:
  `GET /internal/v1/market-facts/{marketId}`
- Preserved the existing OHLC endpoint behavior by delegating it to normalized
  snapshot output.
- Updated Story 0069 status to `Audit remediation implemented - human review
  required`.

## Boundaries and Failure Semantics

- Activity excludes synthetic and incomplete candles from its observed measure.
- Readiness distinguishes `AVAILABLE`, `INSUFFICIENT_DATA`, `STALE`,
  `UNAVAILABLE`, and `UNSUPPORTED`.
- Requests are bounded to the provider's 720-candle history limit.
- Cache keys include market, provider, symbol, base/quote domain, interval,
  activity window, lookback, minimum completed candles, maximum observation age,
  and calculation version. Only fully available facts are cached, and expiry is
  based on latest evidence timestamps rather than calculation time.
- Exact duplicate observed candles are counted once; conflicting duplicates
  make the affected fact non-available. Zero-volume candles contribute zero;
  positive-volume candles require a strictly positive VWAP.
- Provider dispatch is selected from `Market.provider`. Market Facts can be
  authorized for `market-intelligence` through the explicit
  `MARKET_DATA_MARKET_FACTS_AUTHORIZED_CALLERS` and matching trusted JWT secret
  configuration without broadening other internal endpoints.
- Kraken payloads and provider-specific errors are not exposed in the contract.
- No global currency conversion or cross-provider comparison is performed.

## Validation

### Focused Validation

```text
mvn -Dtest=MarketFactsServiceTest,MarketHistoryServiceTest test
mvn -Dtest=MarketDataSecurityIntegrationTest test
```

Result: passed, 0 failures.

Covered behavior includes quote-notional calculation, synthetic exclusion,
temporal gaps, stale evidence, duplicate/conflicting evidence, provider
failure and recovery, evidence-time cache expiry, cache identity invalidation,
provider dispatch, endpoint authorization, explicit bounded HTTP requests, and
typed statuses.

### Module Validation

```text
mvn test
```

Result: passed, 0 failures.

```text
mvn verify
```

Result: passed. JaCoCo coverage checks passed.

```text
git diff --check
```

Result: passed.

## Scope Review

The implementation does not add candidate scoring, market ranking, liquidity,
spread, generic volatility, trend analysis, AI recommendations, account
eligibility, risk decisions, broker calls, or order execution.

Pre-existing worktree changes in Story 0067 and existing investigation reports
were preserved and not included in this implementation scope.

## Remaining Human Actions

- Review the complete code and documentation diff.
- Confirm that process-local bounded reuse is sufficient for the current V1;
  durable or distributed reuse remains a future architectural decision.
- Confirm deployment secret provisioning before enabling
  `market-intelligence` as an authorized Market Facts caller.
- Confirm the internal contract parameters before Story 0070 consumes them.
- Complete runtime Kraken sandbox/deployed validation when the environment is
  available.
- Complete human code review and commit when approved.

Runtime validation was subsequently executed against live Kraken data. It found
that the current open candle is counted as a readiness cadence violation and
recorded an unresolved PEPE arithmetic reconciliation discrepancy. See
`runtime-validation.md`; Story 0069 is not ready for closure until those runtime
findings are resolved.

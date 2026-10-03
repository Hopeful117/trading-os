# Story 0069 Audit Remediation Report

**Date:** 2026-10-03  
**Source audit:** `docs/architecture/reports/story-0069-independent-audit.md`  
**Status:** Implemented - human review required

## Result

The corrective pass addresses the seven high-risk audit findings without
implementing Story 0070 or changing the Market Intelligence decision boundary.

## Remediated Findings

- **H1 - Temporal coverage:** readiness and activity now derive expected
  completed candle timestamps and distinguish missing, shifted, synthetic, and
  cadence-violating evidence from available evidence.
- **H2 - Cache freshness:** cache reuse is checked against the latest activity
  evidence and latest observed close, not `generatedAt`. Non-available results
  are not cached.
- **H3 - Duplicate evidence:** observed events are deduplicated by candle open
  time. Exact duplicates are counted once; conflicting duplicates produce a
  non-available fact and are exposed in the contract.
- **H4 - Provider routing:** `MarketHistoryService` builds a provider registry
  and selects the provider from `Market.provider`. Missing mappings produce an
  explicit unsupported outcome.
- **H5 - Activity freshness:** `MarketActivityFact` now exposes latest eligible
  evidence and its own `STALE` status.
- **H6 - Internal authorization:** Market Facts has a dedicated caller
  allowlist. The default remains `trading-core`; deployment can add
  `market-intelligence` with `MARKET_DATA_MARKET_FACTS_AUTHORIZED_CALLERS` and
  `MARKET_INTELLIGENCE_MARKET_DATA_SERVICE_JWT_SECRET`. Other internal
  endpoints retain the existing authorized caller rule.
- **H7 - Cache identity:** cache keys include provider, symbol, base asset,
  quote asset, request configuration, and calculation version.

## Additional Corrections

- Positive-volume candles with zero VWAP are incomplete; zero-volume candles
  remain valid zero-contribution evidence.
- HTTP duration overflow and request-duration multiplication are rejected as
  bad requests instead of being allowed to overflow.
- Activity and readiness expose expected coverage, missing intervals, cadence
  violations, duplicate counts, and conflict counts.

## Validation

Executed successfully:

```text
mvn -Dtest=MarketFactsServiceTest,MarketHistoryServiceTest test
mvn -Dtest=MarketDataSecurityIntegrationTest test
mvn test
git diff --check
```

The full module test suite passed. Existing warnings and intentionally exercised
provider parsing logs remain non-failing test output.

## Remaining Human Review

- Confirm deployment configuration and secret provisioning before enabling
  `market-intelligence` as an authorized caller.
- Review the complete diff and commit only after human approval.
- Runtime Kraken sandbox validation remains outside this corrective pass.

## Corrective Runtime Remediation

The runtime defect was reproduced by
`MarketFactsServiceTest.currentOpenProviderCandleDoesNotInvalidateCompletedCadence`:
fourteen consecutive completed one-minute candles plus the current open candle
at an unaligned request boundary produced one false cadence violation before
the correction. `MarketFactsService.calculateReadiness()` now evaluates
cadence only over completed, non-synthetic evidence. Open evidence remains in
the provider-observed snapshot and is still excluded from completed coverage.

`MarketFactsKrakenIntegrationTest` uses a Kraken-shaped response with fourteen
completed entries and one current open entry, then passes through the real
Kraken mapper, normalizer, provider, history service, and Market Facts service.

## Semantic Test Matrix

| Scenario | Test | Layer | Invariant / expected result |
|---|---|---|---|
| Current-open runtime regression | `currentOpenProviderCandleDoesNotInvalidateCompletedCadence` | Unit | Open evidence cannot invalidate valid completed cadence; `AVAILABLE` |
| Kraken completed + open response | `providerShapedKrakenHistoryWithCurrentOpenCandleIsAvailable` | Integration | Provider mapping preserves open state; `AVAILABLE` |
| Complete coverage | `calculatesQuoteNotionalFromCompletedObservedCandles` | Unit | Complete activity/readiness is `AVAILABLE` |
| Leading/middle gaps | `incompleteRequestedLookbackIsNotAvailable`, `excludesSyntheticCandlesAndReportsIncompleteEvidence` | Unit/normalizer | Missing or synthetic continuity is not provider completeness |
| Trailing completed gap | `currentOpenProviderCandleDoesNotInvalidateCompletedCadence` | Unit | Current open interval is excluded from completed cadence; explicit trailing-gap fixture remains NOT separately covered |
| Exact duplicate | `duplicateObservedCandleIsCountedOnce` | Unit | Duplicate does not inflate activity |
| Conflicting duplicate | `conflictingDuplicateCannotProduceAvailableFacts` | Unit | Conflict is fail-safe, not trusted |
| Fresh/stale/cache boundary | `reportsStaleCompletedEvidence`, `cacheExpiryUsesEvidenceTimeNotGenerationTime` | Unit | Freshness controls status and cache reuse |
| Unsupported provider | `unsupportedMarketProviderIsExplicit` | Adapter/service | Unsupported evidence is not relabeled or available |
| Security authorized/unauthorized | `marketIntelligenceCredentialCanUseOnlyMarketFactsEndpoint`, `unknownServiceCannotUseMarketFactsEndpoint` | Security | Only authorized service access succeeds |
| Zero volume / positive-volume zero VWAP | `zeroVolumeIsValidZeroContributionEvidence`, `positiveVolumeWithZeroVwapIsIncomplete` | Unit | Zero-volume is valid; unusable metrics are incomplete |
| Malformed/future open timestamp | No dedicated test yet | Unit | NOT COVERED; open evidence must not be used as completed evidence |

The matrix intentionally treats line coverage separately from invariant
coverage. Exact boundary parameterization, malformed open evidence, and
runtime cache refresh remain deterministic-test concerns rather than claims of
live proof. No new property-testing library is justified for 0069.

## Runtime Revalidation

The rebuilt `market-data` image started successfully. The public market
catalogue returned HTTP 200 and exposed XBT/USD, ETH/USD, PEPE/USD, and XBT/EUR.
The four authenticated facts-only requests returned HTTP 401 because the
running container's `TRADING_CORE_MARKET_DATA_SERVICE_JWT_SECRET` value is
empty. The security boundary was not bypassed.

Therefore live readiness, activity, cache reuse/expiry, new-candle refresh, and
the PEPE adjacent-snapshot comparison are `NOT_RUNTIME_PROVEN` in this
corrective pass. The original PEPE difference of 54 quote units remains an
unresolved MEDIUM observation; no deterministic calculation defect was proven
and no production arithmetic change was made. No authenticated calculation or
trading side effect occurred during the blocked revalidation.

## Corrective Verdict

`RUNTIME_VALIDATION_INCOMPLETE`

The open-candle correction and automated regression evidence are complete, but
human closure still requires a configured service JWT and a repeat of the
facts-only live validation for all four markets.

## Latest Authenticated Revalidation

The service JWT configuration was restored in the local runtime without
displaying or changing the secret. Authenticated `trading-core` Market Facts
requests returned HTTP 200 for XBT/USD, ETH/USD, PEPE/USD, and XBT/EUR.
Unauthenticated and unknown-service requests remained HTTP 401.

Live facts-only validation then confirmed 14 expected and observed completed
candles for each market, with zero missing, synthetic, duplicate, conflicting,
or cadence-violating evidence. Normal freshness returned `AVAILABLE`; the
one-second boundary returned `STALE`. Cache reuse and refresh after newer
provider data were observed. Quote-domain separation and the absence of
trading side effects were also confirmed.

The original PEPE difference was not treated as a deterministic arithmetic
defect: a later adjacent-read comparison produced a different 9-unit
discrepancy. The exact mapped snapshot is not exposed by the current contract,
so PEPE remains `STILL_INCONCLUSIVE` and is recorded as a follow-up observation.

The earlier empty-secret runtime result remains historical evidence of a
configuration propagation/stale-container issue. The latest corrective verdict
is `READY_FOR_HUMAN_CLOSURE`. No Story 0070 work, commit, or push was performed.

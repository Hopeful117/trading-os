# Implementation Plan - Story 0114

## Plan Status

Approved by the human engineer.

This plan is conditional on resolving the Kraken Funding Evidence provider
contract before implementation. If the live or official contract cannot safely
map the bounded product to realized/current Funding Evidence, implementation
must stop and the Story must be reported as `PROVIDER_BLOCKED`, `ADR_REQUIRED`,
or `UNKNOWN`.

## Objective

Add the smallest provider-isolated Market Data acquisition path capable of
persisting truthful, idempotent Kraken Funding Evidence for the bounded
perpetual research market, without changing existing OHLC, Order Book,
FeatureSnapshot, Funding Cost, or strategy behavior.

## Preconditions

- Story `0114` is approved.
- Repository Analysis is approved.
- Existing worktree changes remain preserved.
- No database reset or collector restart is performed before the provider gate
  is resolved.
- The existing Funding Evidence migration and repository remain compatible with
  the selected provider semantics.

## Phase 1 - Provider Contract Validation

Before editing application code:

1. Verify the actual bounded runtime market and provider symbol from the
   persisted Market Data catalogue; do not derive identity from a stale UUID.
2. Verify the Kraken Futures contract for the symbol, specifically whether the
   historical endpoint accepts `PF_XBTUSD` and what response it returns.
3. Compare the historical response with the Futures WebSocket ticker fields.
4. Establish whether the selected value is realized/current funding or predicted
   funding. Predicted fields must not be persisted as realized evidence.
5. Establish funding interval, effective start/end, settlement time, source
   timestamp, update cadence, retention, pagination, and correction behavior.
6. Classify historical recoverability as one of:
   `FULLY_RECOVERABLE`, `PARTIALLY_RECOVERABLE`, `LIMITED_RETENTION`,
   `CURRENT_ONLY`, or `UNKNOWN`.
7. Select the acquisition mechanism:
   - historical REST only for backfill, if explicitly governed later; or
   - bounded REST polling for current capture; or
   - existing Futures WebSocket ticker integration for forward capture.
8. Stop before implementation if the response is ambiguous, the symbol is
   unsupported, or realized and predicted values cannot be distinguished.

Expected decision after this phase:

```text
IMPLEMENT
```

If a small model change is required solely to represent a provider-confirmed
semantic distinction, use `ADAPT_MODEL_THEN_IMPLEMENT` and stop for an
architectural decision if the change affects durable historical meaning.

## Phase 2 - Provider Adapter Boundary

If Phase 1 resolves safely:

1. Extend the existing Kraken Futures client or WebSocket DTOs only with the
   provider fields required for the selected Funding Evidence path.
2. Keep Kraken DTOs, provider symbols, transport timestamps, and response
   parsing inside `market-data/.../kraken`.
3. Add a narrow mapper/factory that creates provider-neutral
   `HistoricalFundingEvidence` values.
4. Use `BigDecimal` for the canonical rate and explicit normalization parsing.
5. Map provider timestamps without substituting local persistence time for the
   provider effective time.
6. Set `fetchedAt` from the acquisition boundary, `capturedAt` from the local
   acceptance boundary, and `knownAt` to the earliest defensible acceptance time
   after validation.
7. Use `CAPTURED` origin for normal runtime acquisition only.
8. Preserve the existing fingerprint contract unless Phase 1 proves a material
   semantic omission.

Likely implementation surfaces, subject to Phase 1:

- `market-data/.../kraken/brokerclient/KrakenFuturesHttpClient.java`
- `market-data/.../kraken/dto/futures/`
- `market-data/.../kraken/helper/`
- `market-data/src/main/java/.../model/HistoricalFundingEvidence.java`
- a new bounded Market Data acquisition service
- a new scheduler using existing Spring scheduling conventions
- capture configuration under `market-data/.../config/`

No new service, queue, scheduler framework, public API, or cross-service
transport is planned.

## Phase 3 - Bounded Acquisition

1. Add configuration for only the required activation state, bounded market
   selection, and safe cadence.
2. Default acquisition to disabled when consistent with existing capture
   configuration safety conventions.
3. Resolve the target market by provider, canonical symbol, and product type.
4. Do not enable catalog-wide capture.
5. Choose cadence from provider update/settlement semantics, API limits, and
   semantic duplicate behavior. Do not poll at Order Book frequency.
6. On timeout, provider error, malformed response, missing rate, invalid
   decimal, unknown instrument, or missing timestamp:
   - log a bounded operational failure;
   - persist no evidence;
   - never substitute zero or another provider.
7. Persist through `HistoricalFundingEvidenceRepository.append` and rely on the
   fingerprint/database uniqueness boundary for duplicate semantic facts.
8. Keep corrections immutable: a materially different provider observation is a
   new evidence record, not an update to an old record.

## Phase 4 - Tests

Add focused tests before runtime activation:

### Provider mapping

- valid realized/current provider payload;
- predicted payload rejected or represented distinctly;
- valid `PF_XBTUSD` mapping;
- invalid decimal;
- missing rate or timestamp;
- unknown product or symbol;
- provider timestamp mapping.

### Fingerprint and persistence

- same semantic evidence produces the same fingerprint;
- database identity and capture timestamps do not change the fingerprint;
- materially different funding evidence produces a different fingerprint;
- duplicate persistence is idempotent;
- legitimate new evidence persists;
- concurrent duplicate handling follows the repository/database pattern.

### Temporal and failure behavior

- `knownAt > cutoff` is excluded;
- `knownAt <= cutoff` is eligible when effective-time rules allow it;
- provider failure persists no evidence;
- malformed or missing data never becomes zero funding;
- effective interval and funding time remain valid.

### Regression

- existing Market Data tests remain green;
- OHLC and Order Book capture paths are unchanged;
- FeatureSnapshot projection and persistence remain unchanged.

## Phase 5 - Runtime Activation and Validation

Only after schema, mapping, repository, tests, and configuration pass:

1. Enable Funding capture through the bounded local/research configuration.
2. Avoid restarting Market Data unless required to load the implementation; if a
   restart is required, perform one controlled restart and verify OHLC and Order
   Book resume without reset.
3. Prove one real persisted Funding Evidence row with:
   - market identity;
   - provider and provider symbol;
   - rate;
   - funding/effective interval;
   - `fetchedAt`, `capturedAt`, and `knownAt`;
   - fingerprint;
   - normalization version.
4. Observe repeated acquisition to distinguish semantic duplicate suppression
   from legitimate evidence growth.
5. Re-measure all passive clocks:
   - OHLC count and growth;
   - Order Book count and growth;
   - Funding count and growth or duplicate suppression;
   - FeatureSnapshot count and strict-observed uniqueness.
6. Validate strict Funding Evidence reads at a historical cutoff.

## Bounded Follow-up Inspections

### Order Book checksum

Inspect the existing Kraken DTO and persistence path only. Classify the current
state as A-F from Story 0114. Do not redesign checksum handling in this Story.
Stop and escalate only if the finding invalidates existing Order Book research
truth.

### Trend capability timeout

Trace the `750 ms` timeout to its configuration/default and record whether it is
intentional or historical. Do not modify it in this Story.

## Documentation Reconciliation

After implementation or provider blocking:

- update only canonical Story artifacts and relevant operational/provider
  documentation;
- record the provider decision, recoverability classification, runtime outcome,
  and any blocker in the Implementation Report;
- do not update ADRs unless the provider semantics require a durable architectural
  decision;
- no FeatureProjection documentation or contract changes are expected.

## Validation Commands

Expected targeted commands, adjusted to repository conventions:

```bash
cd market-data && ./mvnw -Dtest=<funding-provider-tests>,<funding-persistence-tests> test
cd market-data && ./mvnw test
cd market-data && ./mvnw verify
git diff --check
git status
```

The exact test selection and any unavailable command must be reported with its
result; no test may be reported as passing unless executed successfully.

## Explicit Non-Goals

The implementation must not add or modify:

- Funding Cost;
- position funding accrual;
- ExecutionModel or TransactionCostModel;
- strategy/backtest/P&L behavior;
- fee or slippage logic;
- FeatureProjection v1/v2;
- Risk, TradePlan, or execution decisions;
- Python research authority.

## Human Approval Gate

This Implementation Plan requires explicit human approval before Phase 1
provider validation is converted into implementation work or any application
files are modified.

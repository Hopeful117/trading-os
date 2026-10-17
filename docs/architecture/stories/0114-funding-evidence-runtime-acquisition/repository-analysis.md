# Repository Analysis - Story 0114

## Objective

Determine whether Trading OS can safely acquire and persist Kraken Funding
Evidence for the bounded linear-perpetual research market, without changing
Funding Cost, FeatureProjection, OHLC, or Order Book semantics.

## Context Sources

- Current repository source, tests, migrations, runtime databases, and Git state.
- Approved Story `0113` and its implementation report.
- Story `0096` for Kraken Futures product identity and `PF_XBTUSD` mapping.
- `ADR-057` for provider-neutral historical evidence and `knownAt` semantics.
- `ADR-063` for bounded Kraken perpetual market evidence acquisition boundaries.
- Official Kraken Futures documentation:
  - `https://docs.kraken.com/exchange/guides/general/historical-data`
  - `https://docs.kraken.com/exchange/api-reference/futures-websocket/ticker`

DevLog was queried for relevant prior context but returned an API quota error
(`insufficient_quota`). Repository evidence is therefore authoritative for the
current analysis. No Obsidian context was required.

## Git State

- Branch: `main`
- `HEAD`: `2d7a23331f1d40e7f030a9af84c486c518c7db96`
- `origin/main`: `2d7a23331f1d40e7f030a9af84c486c518c7db96`
- `git fetch origin`: attempted previously and timed out after 120 seconds.
- Existing unrelated worktree changes were preserved.
- This analysis adds only the Story 0114 artifacts; no application files are
  modified by this stage.

## Existing Funding Foundation

### Domain model

`HistoricalFundingEvidence` already represents:

- `marketId`
- provider and canonical symbol
- provider symbol
- product type
- funding time
- effective interval
- `BigDecimal fundingRate`
- settlement asset
- source identity and source update time
- fetched, captured, and known-at timestamps
- captured/backfill origin
- normalization version
- deterministic evidence fingerprint

The constructor enforces non-null identity and temporal ordering, including:

```text
fetchedAt <= capturedAt <= knownAt
fundingTime within [effectiveFrom, effectiveTo)
```

The model does not have an explicit field distinguishing realized/current
funding from predicted/indicative funding. This distinction must be resolved
before persisting a Kraken ticker value.

### Persistence

`V7__historical_funding_evidence.sql` creates an immutable-style table with:

- UUID primary key
- market/provider/product identity
- funding and effective timestamps
- `NUMERIC(38,18)` funding rate
- source provenance
- fetched/captured/known-at timestamps
- origin and normalization version
- unique evidence fingerprint
- market/funding-time/known-at index

`HistoricalFundingEvidenceRepository.append` uses fingerprint idempotence and
database conflict handling. `findStrictObserved` requires:

```text
funding_time <= asOf
known_at <= asOf
origin = CAPTURED
```

This is sufficient as a first strict temporal query boundary, subject to the
provider semantics being mapped correctly.

### Fingerprint

`HistoricalFundingEvidenceFingerprint` uses SHA-256 over semantic and source
fields, including market identity, provider symbol, funding/effective times,
rate, settlement asset, source identity, origin, and normalization version.

It excludes database identity and capture timestamps. The existing semantics
should be preserved unless provider investigation demonstrates a concrete defect.

### Tests

`HistoricalFundingEvidencePersistenceTest` covers strict cutoff selection and
fingerprint-based duplicate persistence. The foundation has no provider mapping,
polling, or live acquisition tests because acquisition was explicitly deferred
by Story 0113.

## Existing Kraken Integration

`KrakenFuturesHttpClient` currently exposes only:

- `/derivatives/api/v3/instruments`
- `/derivatives/api/v3/instruments/status`
- `/derivatives/api/v3/tickers`

The Futures ticker DTO maps bid, ask, last, last time, tag, and pair, but does
not map:

- current funding rate
- predicted funding rate
- next funding time
- relative funding values

The existing WebSocket infrastructure is already used for bounded Futures
market data, but the current funding-specific provider path is absent.

## Provider Investigation Findings

Official Kraken documentation confirms:

- Futures WebSocket ticker exposes `funding_rate` for perpetuals.
- It separately exposes `funding_rate_prediction`; this is an estimate of the
  next funding period and must not be treated as realized funding.
- The Futures historical data guide documents:

  ```text
  GET /derivatives/api/v3/historicalfundingrates?symbol=<symbol>
  ```

- The documentation examples use `PI_XBTUSD`, while the existing bounded
  perpetual market is expected to use `PF_XBTUSD`.
- The documentation fetched does not specify the historical endpoint response
  schema, pagination, retention, or correction semantics sufficiently for safe
  implementation.

Therefore the provider path is promising but not yet implementation-ready.
The correct historical endpoint behavior for `PF_XBTUSD` must be verified from a
real response or a more specific official contract before choosing the adapter
mapping.

## Runtime Evidence

- Funding table was observed empty.
- OHLC and Order Book capture are active and must not be restarted or changed
  unnecessarily.
- FeatureSnapshot remains healthy from the previous validation:
  `15` snapshots, all `STRICT_OBSERVED`, unique identities and fingerprints.

## Architecture and Ownership

Market Data is the correct owner for:

- Kraken provider acquisition
- provider-specific DTOs and symbols
- normalization to `HistoricalFundingEvidence`
- persistence and strict temporal eligibility

Funding Cost remains outside this Story and outside Market Data acquisition.
Market Intelligence must not become the provider acquisition boundary.

## Risks and Gaps

1. **Historical symbol ambiguity:** official examples use `PI_XBTUSD`, while
   the bounded runtime target is `PF_XBTUSD`.
2. **Historical response contract gap:** response fields and pagination are not
   established sufficiently from the available documentation.
3. **Realized/predicted distinction:** the current model needs a safe source
   mapping decision before using ticker funding fields.
4. **Funding interval semantics:** effective start/end and settlement timing
   need provider confirmation rather than inferred timestamps.
5. **Corrections:** provider correction/version behavior is not established.
6. **No capture configuration or scheduler:** runtime acquisition does not yet
   exist.

## Decision Gate

Current decision: `UNKNOWN`.

The repository foundation is sufficient for a bounded adapter once provider
semantics are confirmed, but implementation must not begin while the actual
`PF_XBTUSD` historical/current funding contract remains unresolved.

The next implementation plan must choose one of:

```text
IMPLEMENT
ADAPT_MODEL_THEN_IMPLEMENT
ADR_REQUIRED
PROVIDER_BLOCKED
UNKNOWN
```

## Proposed Plan Scope

If the provider investigation resolves the gaps without an architectural
decision, the implementation plan should be limited to:

1. Add the minimal Kraken provider DTO/client mapping.
2. Normalize only semantically valid realized/current Funding Evidence.
3. Add bounded, configurable acquisition for the existing perpetual market.
4. Preserve fingerprint idempotence and strict known-at reads.
5. Add provider mapping, temporal, failure, persistence, and runtime regression
   tests.
6. Validate OHLC, Order Book, Funding, and FeatureSnapshot clocks without data
   reset.

No Funding Cost, feature projection, execution, or strategy behavior should be
included.

## Approval State

Story `0114` was approved by the human engineer. This Repository Analysis now
requires explicit human approval before an Implementation Plan may be produced.

# Implementation Report - Story 0114

## Outcome

Implementation was not started because the Kraken provider contract did not
pass the Story's safety gate. No application code, configuration, database
schema, collector, or runtime process was modified.

Final decision: `PROVIDER_BLOCKED`.

## Repository and Runtime Findings

- Runtime bounded market:
  - provider: `KRAKEN`
  - canonical symbol: `BTC/USD`
  - product type: `LINEAR_PERPETUAL`
  - provider symbol: `PF_XBTUSD`
  - market is currently `OPEN` and tradable.
- Funding Evidence table remained empty.
- Existing Funding Evidence model, migration, repository, fingerprint, and
  tests remain unchanged.
- Existing OHLC and Order Book collectors were not restarted or modified.
- Existing FeatureSnapshot state was not modified.

## Kraken Verification

Official documentation states that Futures ticker data exposes separate:

- `funding_rate`: current funding rate for perpetuals;
- `funding_rate_prediction`: predicted rate for the next funding period;
- `next_funding_rate_time`: next funding-rate time on the WebSocket ticker.

The live Futures REST ticker response for `PF_XBTUSD` returned both current and
predicted funding fields. It also returned the provider `serverTime`, but the
REST response did not provide the next funding timestamp or a complete
effective interval.

The officially documented historical endpoint was queried directly:

```text
GET https://futures.kraken.com/derivatives/api/v3/historicalfundingrates?symbol=PF_XBTUSD
GET https://futures.kraken.com/derivatives/api/v3/historicalfundingrates?symbol=PI_XBTUSD
```

Both returned:

```json
{
  "status": "NOT_FOUND",
  "result": "error",
  "errors": [{"code": 0, "message": "404 NOT_FOUND"}]
}
```

The official historical-data guide documents the endpoint but does not define
its response schema, pagination, retention, or correction semantics sufficiently
for this repository's immutable temporal evidence contract.

## Safety Decision

The current ticker data is not sufficient for safe persistence because:

1. Persisting `funding_rate` with REST `serverTime` as `fundingTime` would
   fabricate the funding event time.
2. Persisting it as the next funding event would require an unverified semantic
   assumption about what the current rate represents.
3. `funding_rate_prediction` is explicitly a prediction and must not be stored
   as realized Funding Evidence.
4. The historical endpoint documented by Kraken is unavailable for the tested
   symbols, so historical truth cannot be recovered through the verified path.

The model's required `fundingTime` and effective interval therefore cannot be
populated honestly from the currently verified REST response. Adding a new
model distinction for funding observations versus realized funding would be an
architectural adaptation, not a safe incidental implementation.

## Validation

- Runtime market identity queried from Market Data PostgreSQL.
- Live Kraken Futures ticker queried successfully.
- Historical funding endpoint queried for `PF_XBTUSD` and `PI_XBTUSD`; both
  returned `404 NOT_FOUND`.
- No tests were changed or required because no application code was modified.
- Existing prior validations remain unchanged.
- `git diff --check` passes.

## Deferred Work

A follow-up investigation is required before implementation can resume. It must
resolve at least one of:

- a currently supported Kraken historical funding endpoint and response
  contract;
- an official WebSocket semantic contract proving how `funding_rate` maps to an
  effective funding event and interval;
- an approved model adaptation that explicitly represents current funding-rate
  observations separately from realized funding events.

No funding-adjusted historical evaluation may treat the empty evidence table as
zero funding.

## Scope Checks

- Funding Cost: not implemented.
- Position or settlement logic: not implemented.
- FeatureProjection changes: none.
- OHLC changes: none.
- Order Book changes: none.
- Trend timeout changes: none.
- Commits, pushes, merges, and restarts: none.

## Documentation Outcome

The Story status was changed to `Blocked`, and this implementation report was
added. No application or broad architecture documentation was changed.

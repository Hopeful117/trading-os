# Story 0111 Research Lifecycle Runbook

## Required Services

- `market-data`
- `market-intelligence`
- their PostgreSQL databases
- Eureka/service discovery as required by the local deployment

## Identify BTC/USD

Query Market Data's market catalogue and select the intended bounded identity.
In the investigated local database, the linear perpetual `BTC/USD` market ID
was:

```text
e23ba62a-995f-4755-8deb-11a71ab3788b
```

Verify the identity in the current environment before enabling acquisition.

## Enable Local Acquisition

Configure Market Data and Market Intelligence with the same market ID. For
example, using environment variables:

```text
HISTORICAL_OHLC_CAPTURE_ENABLED=true
HISTORICAL_OHLC_CAPTURE_MARKET_IDS=e23ba62a-995f-4755-8deb-11a71ab3788b
HISTORICAL_OHLC_CAPTURE_INTERVALS=FIFTEEN_MINUTES,ONE_HOUR,FOUR_HOURS

RESEARCH_LIFECYCLE_ENABLED=true
RESEARCH_LIFECYCLE_MARKET_IDS=e23ba62a-995f-4755-8deb-11a71ab3788b
RESEARCH_LIFECYCLE_OBSERVATION_CADENCE=PT1H
RESEARCH_LIFECYCLE_OUTCOME_HORIZONS=PT1H,PT4H,PT24H
```

The lifecycle is disabled by default. Do not configure an uncontrolled catalog
of markets.

## Verify Operation

Inspect Market Data logs for successful historical OHLC capture. Confirm new
rows retain their provider-derived `known_at` and that `known_at` is not rewritten
to `close_time`.

Inspect Market Intelligence logs for strict research execution and projection
activity. Query the readiness endpoint:

```text
GET /internal/v1/research/readiness
```

Use the response to inspect configured markets, evidence freshness, snapshot
cutoffs, outcome maturity/status, and approximate available population.

## Restart Behavior

Restarting the service is safe. The aligned market/cutoff idempotency key and
database-backed execution scope prevent duplicate research executions. Completed
executions can be projected again without creating conflicting snapshots.

Downtime gaps are not filled with fabricated strict observations. The scheduler
resumes at the next valid aligned cutoff.

## Disable Safely

Set:

```text
RESEARCH_LIFECYCLE_ENABLED=false
HISTORICAL_OHLC_CAPTURE_ENABLED=false
```

Restart the affected services if configuration is supplied through container
environment variables. Existing evidence, snapshots, outcomes, and dataset
exports remain unchanged.

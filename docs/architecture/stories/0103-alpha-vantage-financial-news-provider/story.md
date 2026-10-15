# Story 0103 - Alpha Vantage Financial News Provider

## Status

Implementation complete with automated validation. Live provider validation is
not run because no local API key is available.

## Goal

Add the first real provider-backed Financial News acquisition path to Trading
OS using Alpha Vantage `NEWS_SENTIMENT`, while preserving the existing
provider-neutral News Service and Market Intelligence boundaries.

## Provider Contract Verified

The official Alpha Vantage documentation was checked at
`https://www.alphavantage.co/documentation/`.

- Endpoint: `GET https://www.alphavantage.co/query`.
- Required parameters: `function=NEWS_SENTIMENT` and `apikey`.
- Optional filters used by the adapter: `tickers`, `topics`, `time_from`,
  `time_to`, `sort`, and `limit`.
- Request time format: `YYYYMMDDTHHMM` in UTC.
- Supported sort values include `LATEST`, `EARLIEST`, and `RELEVANCE`.
- The documented limit range is up to 1000 results; Trading OS defaults to a
  bounded configurable value of 50.
- Response articles contain fields including `title`, `url`, `time_published`,
  `source`, `summary`, `category_within_source`, `topics`, and
  `ticker_sentiment`. The adapter does not persist raw sentiment fields.
- Provider responses may contain `Note`, `Information`, or `Error Message`
  objects instead of a normal feed. These are treated as provider failures,
  including quota or rate-limit responses.
- Article timestamps are normalized from the provider's compact timestamp form;
  both minute and seconds precision are accepted.

The provider documentation does not provide a stable explicit article ID in the
normalized feed contract used here. The identity strategy is therefore URL
based when a canonical URL is present, with a deterministic composite
fingerprint fallback when it is absent.

## Scope

- Add a capability-specific `FinancialNewsSourcePort`.
- Keep XOOMAR on `EconomicCalendarSourcePort` only.
- Add an opt-in Alpha Vantage infrastructure adapter and bounded scheduler.
- Normalize provider records into the existing `FinancialNewsItem` contract.
- Preserve provider and original publisher as separate values.
- Persist normalized facts through existing Financial News persistence.
- Preserve existing public and internal provider-neutral APIs.
- Propagate stored `marketIds` through the provider-neutral Market Intelligence
  context contract.
- Add deterministic adapter, configuration, synchronization, and context tests.

## Out Of Scope

- Full article bodies or raw provider payload persistence.
- Frontend Financial News presentation.
- AI, sentiment interpretation, embeddings, RAG, clustering, or recommendations.
- Risk, opportunity, TradePlan, Challenge, or execution behavior.
- Provider aggregation or a second Financial News provider.
- Cross-provider deduplication.
- First-seen history or article revision event sourcing.
- Alpha Vantage Premium selection.
- Commercial licensing, redistribution, long-term retention, or ML dataset
  rights approval.

## Architecture

```text
Alpha Vantage NEWS_SENTIMENT
            |
            v
AlphaVantageFinancialNewsSource
            |
FinancialNewsSourcePort
            |
NewsCatalogService
            |
FinancialNewsItem -> PostgreSQL -> public/internal provider-neutral APIs
```

The Alpha Vantage adapter owns HTTP transport, query parameters, API-key
authentication, provider response parsing, quota/error recognition, timestamp
parsing, and normalization. No Alpha Vantage type or credential leaves News
Service infrastructure.

## Normalization

- `sourceName`: `alpha-vantage`.
- `publisher`: Alpha Vantage's original `source` field.
- `title`, `summary`, `canonicalUrl`, and `publishedAt`: mapped from provider
  facts without fabrication.
- `categories`: provider category and topic names as bounded strings.
- `currencies`: `CRYPTO:*` and `FOREX:*` ticker metadata only, normalized to
  uppercase codes.
- `marketIds`: empty because no deterministic Trading OS market mapping is
  available in this Story.
- `impact`: `UNKNOWN`; Alpha Vantage sentiment is not Trading OS impact.
- `sourceUpdatedAt`: absent because the provider article contract does not
  provide a source update timestamp used by this adapter.
- `fetchedAt`: acquisition time, never a substitute for publication time.
- `normalizationVersion`: `alpha-vantage-v1`.

Records without a usable title or publication timestamp are rejected. Valid
records in the same response remain ingestible and the acquisition reports an
`INCOMPLETE` status.

## Synchronization And Availability

Alpha Vantage is disabled by default and requires `ALPHA_VANTAGE_API_KEY` when
enabled. Polling is separately configurable and defaults to a six-hour fixed
delay with a 48-hour lookback and a result limit of 50. These are deployment
configuration values, not domain invariants and do not encode the free-tier
quota.

Provider failures are additive/upsert failures only. Existing catalog rows are
not deleted. Quota and transport failures are reported as `UNAVAILABLE`; a
response containing valid and rejected records is `INCOMPLETE`; a successful
empty feed is `AVAILABLE` with no items.

No public or unauthenticated manual provider execution endpoint is added.

## Data-Use Policy

This development integration is limited to personal/non-commercial Trading OS
use. Only normalized `FinancialNewsItem` facts are stored. Full articles and raw
provider responses are not stored or redistributed. Commercial use, long-term
ML dataset rights, AI processing rights, historical retention policy, and
Premium-plan selection remain explicitly unapproved or deferred.

## Validation Expectations

- Automated tests never call Alpha Vantage.
- News Service tests cover normalization, timestamps, identity, empty feeds,
  malformed records, quota responses, configuration, and synchronization.
- Market Intelligence tests cover provider-neutral `marketIds` propagation.
- News Service `verify`, Gateway tests, Market Intelligence tests, Compose
  configuration, PostgreSQL/Flyway startup, and `git diff --check` are required.
- Live validation, if a local key is available, uses the minimum number of calls
  and records normalized evidence only. The key is never logged or documented.

## Known Limitations

- Alpha Vantage does not provide a stable explicit article ID in the contract
  used here; URL identity is preferred and a composite fallback is documented.
- First-seen and revision history are not modeled.
- Market UUID mapping is intentionally absent.
- Provider attribution and data-use terms must be revalidated for any broader
  deployment or commercial use.
- The current environment may not have a usable Alpha Vantage key.

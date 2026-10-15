# Story 0103 - Runtime Validation Report

## Environment

- Repository HEAD: `e9d7b7c6b60449d33ef302646dc41e85ae99bc3f`.
- Branch: `main`.
- Alpha Vantage provider: enabled only for the controlled live validation
  attempt; disabled by default in the repository configuration.
- API key presence: `YES` in the local `.env` under `ALPHA_VANTAGE_KEY`; the
  value was never printed or persisted.

## Automated Runtime Validation

- `docker compose config -q`: passed.
- News PostgreSQL: running.
- News Service: rebuilt and running with Alpha Vantage enabled for the
  controlled validation attempt.
- Gateway: running.
- Market Intelligence: rebuilt and running.
- Flyway: latest News Service migration is version `3` and successful in
  PostgreSQL.
- Unauthenticated Gateway request to `GET /api/v1/news/items`: returned `401
  Unauthorized`, confirming the route remains protected.
- Authenticated Gateway read: not run because no runtime user credential was
  supplied for this validation.
- Internal Market Intelligence context request: not run because no service
  authentication flow was supplied for this validation.

## Real Provider Validation

- Provider enabled: `YES`, using a temporary environment override mapped from
  the existing local `.env` key. The repository `.env` was not modified.
- News Service startup: `PASS`; the service registered with Eureka and stayed
  healthy after the constructor injection fix.
- Real provider call performed: `YES`; the scheduled synchronization attempted
  one request with the controlled window, limit `10`, and tickers
  `CRYPTO:BTC,CRYPTO:ETH`.
- Provider result: `UNAVAILABLE`, logged as a quota/rate-limit response.
- Persisted normalized items: `0`.
- Rejected item count: not applicable because the provider response was
  rejected before feed normalization.
- Exact provider-side quota consumption: not independently observable; one
  request was initiated by the service.
- No raw provider payload, API key, or article body was recorded.
- This attempt preceded restoration of the global `NEWS_PROVIDER_ENABLED` gate;
  current runtime validation requires both the global and Alpha Vantage
  provider flags to be enabled. No additional live request was made after that
  fix because the provider was already rate-limiting the key.

## Runtime Summary

- Gateway validation: `PASS` for protected unauthenticated access (`401`);
  authenticated read not run.
- Market Intelligence context validation: not run; service authentication was
  not supplied.
- PostgreSQL/Flyway validation: `PASS`, News Service schema version `3`.
- Alpha Vantage live validation: `BLOCKED BY PROVIDER QUOTA/RATE LIMIT`.

## Evidence Policy

If live validation is run, record only provider status, returned/normalized/
persisted counts, rejected count, latest publication time, publisher/category
examples, availability, Gateway result, and Market Intelligence context result.
Do not record the API key, raw response, or full article text.

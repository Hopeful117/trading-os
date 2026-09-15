# Story 0041 Implementation Report

## Result

Implemented, validated, and human-approved in the working tree. No commit or
push was performed.

## Production Changes

- Position queries resolve the financial `Account`, canonical linked
  `BrokerAccount`, owner, and `ExecutionMode` before selecting the source.
- LIVE positions continue to use Broker Service/provider facts.
- PAPER positions are built from local OPEN `Trade` records and use the Trade
  ID as their stable local position identifier.
- PAPER queries do not call Broker Service account or position facts.
- The position fact contract is provider-neutral through `PositionFact`, with
  explicit `PositionSource` response metadata.
- Market Data bid/ask values are exposed to Trading Core. PAPER BUY positions
  use bid and PAPER SELL positions use ask when the snapshot is fresh and
  positive and has a non-null occurrence timestamp.
- PAPER quote-currency mismatches, stale data, missing data, unavailable market
  catalogues, and ambiguous normalized symbols remain explicitly represented
  through `PositionValuationStatus`; no last-price or zero-value fallback is
  introduced.
- Exposure is null when no valid current valuation exists.
- The positions page only exposes LIVE close/reconcile controls and Kraken FIFO
  messaging for broker-sourced positions.

## Test Changes

- Added local PAPER position mapping and valuation tests.
- Added `PositionPersistenceIntegrationTest`, covering real persistence,
  `EntityManager.flush()/clear()`, authenticated HTTP access, and post-query
  persistence reload.
- Added fresh bid/ask, stale, unsupported-currency, and ambiguous-market tests.
- Added regression coverage ensuring a `FRESH` snapshot without `occurredAt` is
  not used for valuation.
- Added controller coverage proving PAPER routing does not interact with
  `BrokerApiClient`.
- Updated existing Trading Core and Angular fixtures for the additive response
  fields and neutral position fact.

## Validation Evidence

| Command | Result |
|---|---|
| `./mvnw -q -Dtest=PositionPersistenceIntegrationTest test` | Passed |
| `./mvnw -q -Dtest=PositionPersistenceIntegrationTest,PositionControllerTest,PositionQueryServiceTest,PositionValuationServiceTest test` | Passed |
| `./mvnw -q -Dtest=PositionControllerTest,PositionQueryServiceTest,PositionValuationServiceTest test` | Passed |
| `./mvnw -q test` | Passed |
| `npm run test:ci` | Passed |
| `npm run build` | Passed with existing bundle-size budget warnings |
| `git diff --check` | Passed |

## Known Limitations

- PAPER close/exit and settlement remain intentionally out of scope.
- The Angular production build still reports existing initial-bundle and SCSS
  budget warnings.

## Review State

`STORY_0041_APPROVED_READY_FOR_COMMIT_AND_MERGE`

No remaining implementation finding exists. Implementation review and human
approval are complete. Commit and merge remain pending and were not performed
by this task.

# Story 0041 Implementation Report

## Result

Implemented, validated, and human-approved in the working tree. No commit or
push was performed.

## Production Changes

- Gateway trade routing is enabled for `/api/v1/trades/**`.
- Trade requests now carry and validate a positive `entryPrice`.
- Trade quantity persistence uses fractional precision (`NUMERIC(38,12)`).
- The precision correction is applied by Flyway migration
  `V16__preserve_fractional_trade_quantity.sql`; `V15` was already occupied by
  an existing migration.

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

### Runtime PAPER Evidence

| Scenario | Result |
|---|---|
| Authenticated account provisioning through Trading Core | Passed |
| Trade creation through Gateway with `entryPrice=60000` and `quantity=0.001` | Passed |
| Persisted position quantity after reload/query | `0.001000000000` |
| Position query without an exploitable Market Data snapshot | `UNAVAILABLE`, no fabricated price/PnL/exposure |

The runtime check used a `KRAKEN` broker account with `executionMode=PAPER`,
which is the current provider-neutral PAPER provisioning contract. A matching
market catalogue entry was not accompanied by a usable current ticker during
the check, so the explicit unavailable valuation path was the expected result.

### UI Smoke Evidence

The deployed Angular interface was exercised with a fresh user through:

1. Registration.
2. Login.
3. PAPER account page navigation.
4. Execution mode selection, initial capital entry, and risk-profile selection.
5. PAPER account creation.
6. Dashboard and positions page navigation.

The account creation feedback was displayed successfully and the dashboard
recognized the new PAPER account. The UI currently has no direct trade-entry
screen or `POST /api/v1/trades` client service. Trade initiation from the UI is
therefore not available in this build; the final position creation was already
validated through the Gateway API, while the UI onboarding and position-read
path were validated separately.

## Known Limitations

- PAPER close/exit and settlement remain intentionally out of scope.
- The Angular production build still reports existing initial-bundle and SCSS
  budget warnings.

## Review State

`STORY_0041_APPROVED_READY_FOR_COMMIT_AND_MERGE`

No remaining implementation finding exists. Implementation review and human
approval are complete. Commit and merge remain pending and were not performed
by this task.

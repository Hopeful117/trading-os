# Implementation Report - Story 0200

## Status

Implementation completed locally. Controlled runtime synchronization returned
`SYNCED_PARTIAL` for the target FTMO account without provider execution.

## Changes

- Added `BrokerAccountSynchronizationResult` for explicit complete/partial V2
  responses.
- Changed V2 synchronization to accept fresh incomplete snapshots when the
  account facts and available capabilities are valid.
- Persisted account-level snapshot completeness, freshness, timestamps,
  collection availability and bounded reasons.
- Added Flyway migration `V52__persist_broker_snapshot_capability_state.sql`.
- Persisted balances and reconciled positions only when their collections are
  authoritative.
- Preserved local positions when positions are unavailable or truncated.
- Disabled deal close/baseline mutation when deal history is unavailable or
  truncated.
- Copied LIVE equity only when the existing equity provenance and quality facts
  are authoritative and coherent.
- Kept the legacy V1 synchronization path unchanged.
- Preserved the existing identity, duplicate-reference, stale-observation,
  transaction and fail-closed risk protections.

## Safety Outcome

- No cTrader order endpoint was called.
- No OAuth flow, credential or account mapping was changed.
- `CTRADER_SAFETY_EXECUTION_ENABLED` remained disabled.
- Missing equity, margin, free margin and deal history were not converted into
  numeric or authoritative fallback values.
- Partial synchronization is returned as diagnostic-only state and does not
  authorize risk or execution.

## Tests

Passed:

- `mvn -Dtest=BrokerSynchronizationServiceImplTest,AccountSynchronizationV2ControllerTest test`
  - 28 tests, 0 failures.
- `mvn -Dtest=BrokerSynchronizationPersistenceIntegrationTest test`
  - 4 tests, 0 failures.
- `mvn -DskipTests compile`
- `git diff --check`
- Docker image build for `trading-app`.
- Runtime Flyway validation and application of migration V52.

Incomplete:

- `mvn test` did not finish within 240 seconds. The run reached PostgreSQL
  integration tests, which lost the test database connection at
  `localhost:33144`; no Story 0200 test failure was observed before timeout.
- Broker Service tests were not rerun because the provider-neutral broker
  snapshot contract was not changed.

## Runtime Validation

Target:

```text
external account: 49411967
Trading Core broker account: 7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456
```

The authenticated browser session confirmed the target account mapping and
existing balance projection. A bounded POST was issued to:

```text
/api/v2/accounts/7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456/synchronize
```

The request returned HTTP 200 with:

```text
status: SYNCED_PARTIAL
completeness: INCOMPLETE
freshness: FRESH
positionAvailability: AVAILABLE_EMPTY
orderAvailability: AVAILABLE_EMPTY
pnlAvailability: AVAILABLE_EMPTY
dealAvailability: UNAVAILABLE_OR_ABSENT
diagnosticOnly: true
```

The persisted `accounts` row for external account `49411967` matched the
response. It contains `INCOMPLETE/FRESH`, the same collection availability and
the bounded reasons:
`DEAL_HISTORY_UNAVAILABLE, OBSERVATION_INCOMPLETE, EQUITY_UNAVAILABLE,
USED_MARGIN_UNAVAILABLE, FREE_MARGIN_UNAVAILABLE`.

## Documentation Outcome

Story-local artifacts were updated only. No ADR or global product documentation
was changed.

## Remaining Limitation

The full Trading Core Maven suite remains incomplete because the local
PostgreSQL integration-test connection timed out. The controlled Story 0200
runtime path and targeted tests are validated.

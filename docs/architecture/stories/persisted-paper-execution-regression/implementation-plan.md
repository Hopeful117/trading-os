# Implementation Plan - Persisted PAPER Execution Regression

## Phase A - Test Context

- Use the existing Trading Core Spring test profile and H2/Flyway setup.
- Load real JPA repositories and application beans.
- Keep external provider calls at mocked client boundaries.

## Phase B - Persisted Fixture

- Persist a test User.
- Persist or create the associated financial Account using existing entity
  relationships.
- Persist a PAPER BrokerAccount owned by that User.
- Persist the existing risk and execution prerequisites required by the
  normal pipeline.

## Phase C - Execution

- Execute through the application service used by the normal pipeline.
- Provide deterministic market snapshots for both BUY and SELL scenarios.
- Verify T1/risk validation occurs before simulated submission.
- Verify the live broker execution client receives no calls.

## Phase D - Persistence Assertions

- Reload the BrokerAccount and verify ownership and PAPER mode.
- Reload execution intent, attempt, broker order, and fill records.
- Verify acknowledgement `fillPrice` and persisted fill values.
- Reload the Account and verify balances, equity, and peak equity according to
  existing settlement rules.
- Reload the Trade projection and verify instrument, direction, quantity,
  entry/current price, and open state.
- Verify the execution is `COMPLETED` and repeated finalization does not
  duplicate settlement or Trade state.

## Phase E - Validation

- Run the new regression by itself.
- Run focused broker-account and execution tests.
- Run `./mvnw -B clean verify` in `trading-core`.
- Run `git diff --check`.

## Production Change Policy

The expected diff is test-only. A production change is permitted only when the
test exposes a mechanical defect in already approved PAPER behavior, such as
missing persistence propagation or bean wiring. A new business semantic must
stop implementation and be reported as `DECISION_REQUIRED`.

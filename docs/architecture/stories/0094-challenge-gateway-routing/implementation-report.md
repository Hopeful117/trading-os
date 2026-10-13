# Story 0094 Implementation Report

## Result

Implemented the Gateway routes required to expose the Trading Core Challenge
API through the public `/api/v1` boundary.

## Changes

- Added `GET /api/v1/challenges` routing to `lb://trading-core`.
- Added `POST /api/v1/accounts/{accountId}/challenge` routing to
  `lb://trading-core`.
- Added `GET /api/v1/accounts/{accountId}/challenge` routing to
  `lb://trading-core`.
- Preserved the existing JWT filter and downstream authorization boundary.
- Added route predicate and downstream forwarding/security tests.
- Did not modify Challenge domain logic, Risk behavior, or scheduler behavior.

## Validation

- `mvn -f gateway/pom.xml test`: passed, 39 tests.
- `git diff --check`: passed.
- Added regression coverage proving the account-specific Challenge route wins
  over the broad account route.
- Added downstream authorization coverage proving a foreign Challenge account
  is rejected with `403` while the authenticated bearer token is preserved.
- Runtime Gateway validation on `http://localhost:17880`:
  - authenticated `GET /api/v1/challenges`: `200` with catalog data;
  - authenticated `POST /api/v1/accounts/{accountId}/challenge`: `200`,
    Challenge status `ACTIVE`;
  - authenticated `GET /api/v1/accounts/{accountId}/challenge`: `200`.
- The first runtime account was rejected with `400` because its settlement
  model was incompatible with the Challenge definition. A `CASH` account was
  then created and the required start/progress calls passed. This confirms the
  existing domain compatibility rule rather than a Gateway routing failure.

## Independent Review Follow-up

- The review found no confirmed functional or security defects.
- Both identified minor test gaps were closed: effective route precedence and
  owner-authorization behavior at the Gateway-to-Trading-Core boundary.

## Environment Note

The isolated Gateway container initially started on a different Docker network
after rebuild. It was attached to the existing E2E network without changing
application code. Eureka service discovery then refreshed and Gateway routing
validated successfully.

## Remaining Review Items

- Changes remain uncommitted for human review.
- DevLog context was unavailable during implementation because the service
  returned `429 credit_balance_exhausted`.

# Code Review Checklist - Story 0058

## Reviewed Scope

* `TradePlanOrigin` and origin-specific domain invariants.
* Manual planning application path.
* Authenticated manual Trade Plan endpoint.
* Origin and author persistence migration.
* Opportunity-origin regression behavior.
* Domain, application, HTTP and persistence tests.

## Findings

The current working tree adds a durable idempotency record and propagates
`Idempotency-Key`. The blocking findings from the independent review were
corrected and revalidated.

### Major - Public retry fingerprint is unstable

Resolved. The fingerprint now uses stable actor/account and trade-request
fields, excluding per-attempt context identifiers and timestamps.

### Major - Plan and idempotency record are not atomic

Resolved. `ManualTradePlanCreationTransaction` persists the Trade Plan and
idempotency record in one transaction.

### Major - JVM synchronization is not a distributed claim

Resolved. Deterministic actor/account/key plan identity plus the unique
database claim and transactional boundary protect concurrent instances.

### Minor - Key validation and error propagation are incomplete

Resolved. Keys are bounded to 200 characters and Trading Core preserves
`IDEMPOTENCY_CONFLICT`.

### Major - Manual request validation is incomplete

Resolved. Missing planning contexts and currencies inconsistent with the
account context are rejected before persistence with `INVALID_TRADING_CONTEXT`.

### Minor - Orphaned idempotency records are uncontrolled

Resolved. A durable idempotency record pointing to a missing plan now produces
the controlled `IDEMPOTENCY_STATE_INVALID` server error.

### Major - Fingerprint omits expiration

Resolved. `expiresAt` is now part of the fingerprint and has a regression test
covering same-key reuse with a changed expiration.

## Review Notes

* Manual plans do not manufacture Opportunity provenance.
* Opportunity-origin plans still require Opportunity and Observation references.
* Manual author identity is derived from `MiUserPrincipal` rather than the
  request body.
* Manual creation persists a proposed plan only.
* Risk evaluation and Execution Intent creation remain downstream.
* Existing rows are migrated to `OPPORTUNITY`; missing historical authors are
  not fabricated.
* No provider-specific broker fields were added to the Trade Plan model.
* Unit tests cover replay, payload conflicts, expiration conflicts, invalid
  contexts, currency mismatches, and orphaned idempotency records.
* The authenticated local E2E covers the public Gateway path, persisted replay,
  and same-key conflict.

## Independent Review Follow-up

`RESOLVED`

The dedicated local E2E replacement
`artifacts/run-manual-trade-plan-idempotency-e2e.sh` passed after rebuilding the
runtime services. It verified `201` creation, same plan ID/version on replay,
and `409 IDEMPOTENCY_CONFLICT` for a changed request.

## Known Risks

* The manual request accepts explicit sizing and risk inputs. Trading Core Risk
  Evaluation remains responsible for authorizing those values.
* Gateway authentication and production database migration were not exercised.
* The full Trading Core suite still has two unrelated PAPER short-margin
  regression errors; the idempotency-related tests pass.
* The broader PAPER scan proof remains independently limited by
  `MARKET_FACT_EVALUATION_BUDGET_EXHAUSTED`; it is not required for this
  idempotency review.
* The complete manual-plan-to-PAPER-execution journey is a subsequent Story.

## Human Review Required

* Confirm the manual API contract and required explicit sizing fields.
* Review the production migration strategy for historical author values.
* Validate the authenticated Gateway route and downstream Risk Evaluation.
* Approve the next Story for human validation and Execution Intent creation.

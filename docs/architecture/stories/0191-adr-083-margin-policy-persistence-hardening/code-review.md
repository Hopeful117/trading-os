# Code Review - Story 0191

## Verdict

**PASS - no HIGH or MEDIUM findings.**

## Review Scope

Reviewed the Story 0191 implementation diff, V50 migration, JPA mapping,
assignment resolver, PostgreSQL integration tests, migration assertions, and
regression boundaries against ADR-082, ADR-083, Stories 0188-0190, and the
approved Implementation Plan.

## Confirmed Guarantees

- V49 and all historical migrations remain unchanged.
- `margin_rate` is stored as exact PostgreSQL numeric and rejects values with
  more than eight fractional digits instead of silently rounding them.
- Zero, negative, and greater-than-one rates are rejected at the database
  boundary; Java validation remains active.
- Policy economics cannot be updated or deleted through PostgreSQL.
- Policy identity remains the composite `(id, version)` key.
- V1 and V2 are separate durable rows; V1 remains unchanged after V2 creation.
- Account assignment uses the financial `Account` foreign key and existing
  broker-account ownership checks; no broker-owned policy authority was added.
- Duplicate assignments at the same effective boundary are rejected by the
  existing database uniqueness constraint, and unknown account/policy references
  are rejected by the foreign keys.
- The real PAPER resolver reloads the assigned policy and calculates `11.00 USD`
  from the explicit `0.01` policy fixture.
- Expiry, revocation state, assignment timestamps, and assignment provenance are
  asserted after persistence-context reconstruction.
- PostgreSQL evidence covers post-commit persistence-context reconstruction rather
  than treating one managed entity as restart proof.
- Story 0190 T1 source-price/freshness behavior was not changed.
- LIVE provider authority, Risk Domain, Broker Service, compatibility rejection,
  and cTrader safety execution remain unchanged.

## Residual Risks and Limits

- The test proves post-commit persistence-context reconstruction, not a full
  Spring process restart. A stronger process-restart test remains optional future
  evidence.
- Full PAPER CFD settlement/MTM lifecycle E2E remains the next Story and is not
  included here.
- The known V34 migration log entry is an intentional pre-existing rejection
  scenario covered by its test; it is not a V50 regression.
- The regression boundaries are covered by existing named Trading Core tests and
  the full Trading Core suite; no production code in those boundaries changed.

## Review Result

```text
NEW_HIGH_FINDINGS: 0
NEW_MEDIUM_FINDINGS: 0
INDEPENDENT_REVIEW_FINDINGS_CLOSED: YES
V49_MODIFIED: NO
T1_REGRESSION: NO
CASH_PLUS_CFD_ENABLED: NO
```

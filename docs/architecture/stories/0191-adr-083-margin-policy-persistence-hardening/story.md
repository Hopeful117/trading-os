# Story 0191 - ADR-083 MarginPolicy Persistence Hardening

## Metadata

**ID:** `0191`
**Title:** ADR-083 MarginPolicy Persistence Hardening
**Status:** Repository Analysis complete - awaiting human approval
**Related ADRs:** `ADR-082`, `ADR-083`
**Predecessor Stories:** `0188`, `0189`, `0190`

## Goal

Make persisted PAPER `MarginPolicy` authority trustworthy across creation,
assignment, commit, reload, persistence-context reconstruction, and later T0/T1
resolution. Invalid policy economics, provenance, version, or assignment state
must not silently enter or mutate authoritative PostgreSQL state.

## Scope

- Add forward-only PostgreSQL protection for ADR-083 policy invariants.
- Preserve immutable policy economics while allowing explicit reassignment to a
  separately persisted policy version.
- Prove policy creation, assignment, commit, reconstruction, reload, and real
  PAPER margin resolution with PostgreSQL/Testcontainers.
- Prove margin-rate boundaries, provenance, scope, timestamps, identity, version,
  assignment, and fail-closed ambiguity behavior.
- Preserve Story 0190 T1 valuation semantics and the existing LIVE authority.
- Update migration and historical-install expectations without editing V49.

## Out Of Scope

- Enabling `CASH + CFD`, `INVENTORY + CFD`, or any compatibility change.
- Full PAPER CFD settlement/lifecycle E2E.
- Risk Domain, Broker Service, cTrader execution, provider mutations, or LIVE
  authority changes.
- Provider-specific policy persistence or leverage as canonical authority.
- Mutable global defaults or silent policy replacement.
- Editing migrations V1 through V49.

## Acceptance Criteria

- A valid policy and explicit financial-account assignment survive PostgreSQL
  commit, persistence-context reconstruction, and reload with identity, version,
  provenance, scope, timestamps, and margin rate unchanged.
- PostgreSQL rejects zero, negative, greater-than-one, and more-than-eight-decimal
  margin rates without silently rounding excess precision.
- Existing policy economics cannot be updated or deleted through the durable
  persistence boundary; a new version is a new row.
- Assignment referential integrity and deterministic authority ambiguity behavior
  are proven against PostgreSQL.
- A reloaded assignment resolves through the real PAPER resolver and can produce
  the deterministic EUR/USD fixture margin of `11.00 USD` for a `0.01` rate.
- Fresh-install and historical-upgrade migration tests reach the new schema.
- Story 0190 T1 tests, compatibility rejection, LIVE provider authority, and
  Risk Domain boundaries remain unchanged.
- No broker orders, closes, provider mutations, or cTrader safety execution occur.

## Safety Invariants

```text
PAPER_MARGIN_AUTHORITY = LOCAL_VERSIONED_POLICY
LIVE_MARGIN_AUTHORITY = PROVIDER
RISK_DOMAIN_CHANGED = NO
BROKER_SERVICE_CHANGED = NO
CASH_PLUS_CFD = REJECTED
INVENTORY_PLUS_CFD = REJECTED
UNKNOWN_PLUS_CFD = REJECTED
SETTLEMENT_COMPATIBILITY_CHANGED = NO
CTRADER_SAFETY_EXECUTION_ENABLED = false
```

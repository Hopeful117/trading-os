# Implementation Plan - Story 0039

## Approved Scope

Implement the canonical `Account -> BrokerAccount` relation and explicit PAPER risk-profile provisioning without changing Broker Service, Angular, Risk Facts, or T0/T1 semantics.

## Planned Changes

1. Add nullable `Account.brokerAccountId` and repository lookup by canonical broker-account ID.
2. Add exact `RiskProfileReference(profileId, semanticVersion)` to the provisioning request.
3. Add shared structural validation for existing versioned profiles and persist configuration plus assignment.
4. Make PAPER provisioning validate the profile before writes and persist the full graph in one transaction.
5. Add V12 to backfill only provable relations, add FK/unique constraints, and remove the legacy `(user_id, broker)` identity constraint.
6. Resolve PAPER settlement through the canonical relation instead of owner/provider inference.
7. Add focused unit, persistence, migration, independent-ID, ownership, profile, and rollback coverage.

## Invariants

- `Account.accountId` and `BrokerAccount.id` remain independent identifiers.
- Linked records must have the same owner and non-contradictory provider metadata.
- One canonical broker account maps to at most one financial account.
- PAPER requires the caller’s exact existing profile ID and semantic version.
- No partial PAPER graph is committed after a required local failure.
- LIVE synchronization behavior remains provider-authoritative and unchanged.

## Validation Plan

- Focused Trading Core tests for provisioning, persistence, risk configuration, and PAPER execution.
- Full `./mvnw test` in `trading-core`.
- Flyway migration execution on clean H2 schema.
- `git diff --check` and manual diff review.

## Non-Goals

No Broker Service or frontend changes, no profile authoring/defaults, no Risk Facts redesign, no Story B implementation, and no automatic legacy-data repair.

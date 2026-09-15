# Implementation Plan - Story 0043

## Approval Gate

Implementation is authorized by the human engineer. This document remains the
approved implementation boundary; no code is changed by updating this artifact.

## Final Provisioning Decision

Use a versioned Flyway reference-data migration in `trading-core` to insert an
explicit platform RiskProfile and its complete rule set. This is preferable to
startup bootstrap for immutable policy because deployment history is
deterministic and auditable, and restarts cannot reset persisted policy.

This is not an automatic default. The migration provisions availability only;
the human later assigns an exact version during PAPER onboarding. It must not
insert any account assignment.

The approved initial policy is `PAPER_STANDARD` / `TRADING_OS_STANDARD_RISK`,
version `1.0.0`, with `MAX_POSITION_RISK=1%`, `MAX_EXPOSURE=3%`, and
`DAILY_DRAWDOWN=3%`. The implementation uses profile UUID
`0a10c7e2-9d1e-4f5a-b6c8-123456789043` and `NUMERIC(30,12)` values
`0.010000000000`, `0.030000000000`, and `0.030000000000`.

## Planned Changes

1. Add the approved profile and complete rule set through a versioned Flyway
   reference-data migration, without account assignment.
2. Define a broker-neutral, authenticated Trading Core read model for eligible
   immutable RiskProfile versions; do not expose rule mutation through it.
3. Add the Gateway route required for the frontend profile-catalog request.
4. Add typed Angular profile models and an observable catalog load with explicit
   loading, empty, and error states.
5. Add a required PAPER profile selector and include its exact ID/version in
   `createPaper()`.
6. Map backend validation failures to actionable, non-sensitive onboarding
   feedback.
7. Verify successful account reload and the existing 0042 PAPER execution/exit
   behavior without changing its semantics.

## Invariants

- Trading Core remains the authority for profile validity and assignment.
- No profile is invented, copied, or assigned implicitly.
- A profile ID and semantic version are one inseparable reference.
- LIVE account creation remains behaviorally unchanged.
- No partial PAPER account graph is considered successful.

## Validation Plan

- Trading Core focused API/catalog and authorization tests.
- Clean-schema migration test proving the initial profile and rules are
  available, with no account assignment side effect.
- Trading Core provisioning regression tests with exact profile references.
- Gateway route tests if route configuration changes.
- Angular service and Accounts component tests for selection, empty, invalid,
  and successful states.
- Existing relevant Trading Core and Angular suites.
- Angular production build.
- `git diff --check`.

## Implementation Deviations

The PostgreSQL-specific partial-index migration had the same Flyway version as
the common position-close migration. It was renamed from V8 to V15 so the
application can validate and start without changing any business behavior.
No profile CRUD, ownership model, default assignment, risk-rule redesign, or
LIVE/Story 0042 semantic change was introduced.

## Implementation Risks

- The product still requires a deployed Trading Core database with V14 applied
  before the profile is visible.
- Catalog access relies on the existing global authenticated-request security
  boundary; no separate profile ownership model was introduced.
- If the implementation introduces platform profile administration or a new
  cross-user policy authority, stop and create or update an ADR before coding.

## Non-Goals

No AI implementation, risk-rule redesign, default profile bootstrap, LIVE broker
redesign, position redesign, analytics page, or automatic trading.

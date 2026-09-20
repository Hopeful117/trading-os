# Repository Analysis - Story 0053

## Scope

Story 0053 closes the gap between PAPER account onboarding and Trade Plan
creation. A PAPER account previously received a risk profile but no effective
Trade Planning Profile, causing `PLANNING_PROFILE_MISSING` during plan creation.

## Current Evidence

* PAPER account creation is owned by Trading Core.
* Risk Profiles and Trade Planning Profiles are separate concepts.
* The implementation commit adds a platform-managed, versioned planning profile
  for new PAPER accounts.
* Account DTO and Angular account cards expose the effective profile references.
* Ownership tests cover account and profile access.

## Implementation Boundary

The implementation is limited to Trading Core account provisioning, profile
mapping, ownership checks, and the corresponding account presentation. It does
not change LIVE defaults, Risk Domain rules, or human authorization.

## Validation Expectations

* Trading Core account and profile tests;
* account-provisioning integration coverage;
* Angular account-card tests;
* Angular production build;
* authenticated PAPER journey through Trade Plan creation;
* `git diff --check`.

## Historical Implementation

The implementation was delivered in commit `698fe03` and introduced ADR-046
for the platform-managed effective planning profile decision.

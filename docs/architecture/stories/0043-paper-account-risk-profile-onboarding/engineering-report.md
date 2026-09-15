# Engineering Report - Story 0043

## Status

`STORY_0043_IMPLEMENTED_HUMAN_REVIEWED_PRODUCT_ACCEPTANCE_PARTIAL`

## Outcome

Story 0043 now provisions the approved PAPER risk policy, exposes an
authenticated read-only catalog, and requires explicit exact profile/version
selection during Angular PAPER onboarding.

## Changes Made

Implementation and Story documentation changed:

- `story.md`
- `repository-analysis.md`
- `implementation-plan.md`
- `engineering-report.md`
- `implementation-report.md`
- `code-review.md`

No unrelated source, ADR, execution, settlement, or LIVE behavior was changed.

## Validation

Validation was executed after implementation. The local multi-service runtime
was rebuilt and started successfully after resolving the pre-existing Flyway
version collision between the common V8 migration and the PostgreSQL-specific
partial-index migration, which is now V15. The local Flyway history was repaired
for the already-applied V3 checksum, and all 15 migrations validated/applied.

Authenticated API and browser acceptance passed for login, eligible catalog
loading, explicit PAPER profile selection, PAPER account creation, canonical
account reload, balance display, and the empty positions state. No browser
console errors were reported. The complete opportunity-to-plan-to-risk-to-
execution-to-full-exit journey remains unproven because no position was
generated during this acceptance run.

## Implementation Report

```text
BASELINE_HEAD = 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149

STORY = 0043

CURRENT_PROFILE_PERSISTENCE = risk_profile and risk_profile_rule tables from V2; immutable JPA read model
CURRENT_PROFILE_CREATION_PATH = no production path; tests insert explicit SQL fixtures
CURRENT_PROFILE_OWNERSHIP = no owner relation; PLATFORM is a policy authority value, not user ownership
CURRENT_PROFILE_ASSIGNMENT = RiskPersistence.assignProfile during atomic PAPER provisioning
CURRENT_PROFILE_VALIDATION = RiskProfileValidator plus exact profile/version lookup and provisioning checks

EXISTING_OPERATIONAL_DATA_CONVENTION = Flyway for durable database evolution; ApplicationRunner bootstrap exists only for Market Intelligence strategies

OPTIONS_EVALUATED = versioned Flyway reference data; controlled application bootstrap; explicit administrative provisioning; existing repository mechanisms
SELECTED_PROVISIONING_MECHANISM = versioned Flyway reference-data migration in Trading Core
WHY_SELECTED = deterministic deployment history, durable persistence, exact version identity, no restart mutation, smallest V1 mechanism

PROFILE_PROVISIONING_IS_SEPARATE_FROM_ASSIGNMENT = YES

PROFILE_OWNERSHIP = platform reference data for V1; no durable owner model exists
PROFILE_VISIBILITY = authenticated users may see the read-only platform catalog only; no user/private profile visibility is invented
PROFILE_ELIGIBILITY_RULE = persisted exact version with valid metadata, supported authority, complete required rule set, valid rule fields/provenance, and platform catalog visibility

CATALOG_AUTHORITY = TRADING_CORE
CATALOG_MUTABLE = NO

INITIAL_PROFILE_SOURCE = human-approved Trading OS PAPER Standard Risk Policy, 2026-09-15
INITIAL_PROFILE_BUSINESS_RULES_ALREADY_DEFINED = YES

PROFILE_IDENTITY = profileId + semanticVersion
VERSION_BEHAVIOR = exact immutable pair; no latest-version semantics; stale, missing, invalid, or non-visible references fail closed

ZERO_PROFILE_BEHAVIOR = catalog is empty, PAPER submission disabled, actionable prerequisite message
INVALID_PROFILE_BEHAVIOR = Trading Core rejects before operational account creation; actionable non-sensitive error
STALE_SELECTION_BEHAVIOR = fail closed and ask the user to reload the catalog

GATEWAY_CHANGE_REQUIRED = YES, read-only authenticated route only
ANGULAR_CHANGE_REQUIRED = YES, reactive catalog and exact profile selector

ENGINEERING_ACCEPTANCE_DEFINED = YES
PRODUCT_ACCEPTANCE_DEFINED = YES
FULL_PAPER_JOURNEY_PROOF_DEFINED = YES

LEARN_AREAS = Flyway reference data, immutable JPA read models, REST catalog contract, transaction boundaries
PAIR_AREAS = Spring Security visibility/authorization, Trading Core integration tests, Angular reactive state and empty/error UX
DELEGATE_AREAS = repetitive DTO/controller wiring, focused regression tests, frontend template styling after the contract is understood

NEW_ADR_REQUIRED = NO

STORY_0043_READY_FOR_IMPLEMENTATION_AUTHORIZATION = YES, implementation complete and ready for human review

PRODUCTION_CODE_CHANGED = YES
TESTS_CHANGED = YES
IMPLEMENTATION_PERFORMED = YES
COMMIT = NO
PUSH = NO
MERGE = NO
```

## Decisions

- Story 0043 is implemented and ready for human code review.
- The selected technical mechanism is a versioned Flyway reference-data
  migration.
- The approved initial policy is `PAPER_STANDARD` /
  `TRADING_OS_STANDARD_RISK`, version `1.0.0`, with 1%, 3%, and 3% limits.
- Profile technical ID: `0a10c7e2-9d1e-4f5a-b6c8-123456789043`.
- Policy technical ID: `TRADING_OS_STANDARD_RISK`.
- Technical ratios: `0.010000000000`, `0.030000000000`, `0.030000000000`.
- `NEW_ADR_REQUIRED = NO` for this scope.
- Do not weaken ADR-043 by adding an implicit default risk profile.
- Defer AI, analytics, news/macro, and expanded position monitoring until the
  basic PAPER journey is creatable by a normal user.

## Human Review Required

Review the implementation diff, migration deployment behavior, and the product
journey in a running environment. If a new authority boundary, cross-user
visibility rule, automatic assignment, or mutable lifecycle is found, stop and
prepare an ADR before integration.

## Direct Answers

1. A valid RiskProfile enters the system through a versioned Trading Core
   Flyway reference-data migration containing the profile and all required rules.
2. For V1 it is platform-owned reference data in policy authority terms; the
   repository has no user-owner relation.
3. Authenticated users may see the authorized read-only platform catalog. No
   private or cross-user profile model is introduced.
4. It is eligible when persisted, structurally valid, complete for the current
   supported rule vocabulary, and visible in the platform catalog. Trading Core
   decides this.
5. The first V1 profile obtains its actual rules from the human-approved
   Trading OS PAPER Standard Risk Policy: 1%, 3%, and 3%. The implementation
   must preserve those values exactly.
6. No. The migration only provisions availability; assignment requires the
   human's selected exact pair.
7. Yes, provided the implementation remains within the existing authority and
   visibility model. No new ADR is required for this proposal.
8. A normal authenticated user selects an exact visible profile version in
   Accounts, creates PAPER, reloads the canonical account, and completes the
   existing opportunity-to-risk-to-execute-to-full-exit journey without API
   construction.
9. Tests must cover migration availability without assignment, catalog
   authorization and eligibility, exact pair propagation, empty/invalid/stale
   selection failures, atomic provisioning and reload, LIVE regression, and the
   full PAPER journey through Story 0042 exit semantics.
10. It is ready for human code review and Git integration. Product acceptance
    remains pending a normal-user runtime journey check.

## Required Implementation Summary

```text
STORY = 0043
IMPLEMENTATION_PERFORMED = YES

PROFILE_LOGICAL_NAME = PAPER_STANDARD
PROFILE_TECHNICAL_ID = 0a10c7e2-9d1e-4f5a-b6c8-123456789043
PROFILE_SEMANTIC_VERSION = 1.0.0

POLICY_LOGICAL_NAME = TRADING_OS_STANDARD_RISK
POLICY_TECHNICAL_ID = TRADING_OS_STANDARD_RISK
POLICY_VERSION = 1.0.0

MAX_POSITION_RISK_BUSINESS = 1%
MAX_POSITION_RISK_TECHNICAL = 0.010000000000
MAX_EXPOSURE_BUSINESS = 3%
MAX_EXPOSURE_TECHNICAL = 0.030000000000
DAILY_DRAWDOWN_BUSINESS = 3%
DAILY_DRAWDOWN_TECHNICAL = 0.030000000000

FLYWAY_MIGRATION = V14__seed_paper_standard_risk_profile.sql
PROFILE_PROVISIONED = YES
AUTOMATIC_ASSIGNMENT = NO

CATALOG_IMPLEMENTED = YES
CATALOG_ENDPOINT = GET /api/v1/risk-profiles/eligible
CATALOG_AUTHENTICATED = YES via existing anyRequest().authenticated() boundary
CATALOG_READ_ONLY = YES
CATALOG_AUTHORITY = TRADING_CORE

GATEWAY_IMPLEMENTED = YES, risk-profile-catalog route to trading-core
ANGULAR_PROFILE_MODEL_IMPLEMENTED = YES
ANGULAR_REACTIVE_CATALOG_IMPLEMENTED = YES
EXPLICIT_PROFILE_SELECTION_IMPLEMENTED = YES
EXACT_PROFILE_VERSION_SUBMITTED = YES, profileId::semanticVersion mapped to exact request fields

ZERO_PROFILE_STATE_IMPLEMENTED = YES, disabled submission and actionable message
INVALID_PROFILE_STATE_IMPLEMENTED = YES, server rejection and actionable feedback
STALE_SELECTION_STATE_IMPLEMENTED = YES, fail-closed backend rejection with reload guidance

TRADING_CORE_REVALIDATES_PROFILE = YES
ATOMIC_PROVISIONING_PRESERVED = YES
LIVE_BEHAVIOR_PRESERVED = YES
STORY_0042_SEMANTICS_PRESERVED = YES

MIGRATION_TESTS = PASS, RiskPersistenceTest
CATALOG_TESTS = PASS, RiskProfileCatalogServiceTest and RiskProfileCatalogControllerTest
SECURITY_TESTS = PASS, existing JwtSecurityBoundaryTest plus global authenticated route policy
PROVISIONING_TESTS = PASS, RiskPersistenceTest and existing BrokerAccountServiceOwnershipTest
ANGULAR_TESTS = PASS, 297 full-suite tests; 27 focused Story tests
ANGULAR_BUILD = PASS, npm run build, existing budget warnings only
REGRESSION_TESTS = PASS, full Trading Core and Gateway suites; Story 0042 PaperExitAcceptanceIntegrationTest included
GIT_DIFF_CHECK = PASS

FULL_PAPER_JOURNEY_PROVEN = PARTIAL, authenticated browser onboarding and canonical reload pass; no position was generated for execution/full-exit proof
ENGINEERING_ACCEPTANCE = PASS
PRODUCT_ACCEPTANCE = PARTIAL, normal-user browser onboarding verified; full execution/exit remains pending
CODE_REVIEW = COMPLETE, NO FINDINGS
OPEN_FINDINGS = NONE; product runtime verification remains open
NEW_ADR_REQUIRED = NO

PRODUCTION_CODE_CHANGED = YES
COMMIT = PENDING DEDICATED BRANCH COMMIT
PUSH = NO
MERGE = NO
```

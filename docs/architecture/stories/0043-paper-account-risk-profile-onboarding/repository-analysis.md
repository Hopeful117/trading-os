# Repository Analysis - Story 0043

## Status

IMPLEMENTED - READY FOR HUMAN REVIEW

## Repository Baseline

- Branch: `main`
- HEAD: `18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`
- Working tree before refinement: clean
- Story 0042: merged and accepted

## Product Capability Matrix

| Stage | Current state | Evidence |
|---|---|---|
| Market data | Implemented and web-exposed | Market Data APIs and Angular market pages |
| Deterministic intelligence | Implemented | `ProductionIntelligencePipeline`, OHLC/spread capabilities |
| Opportunities and scans | Implemented | `MarketIntelligenceController`, scan panel, opportunity pages |
| Trade planning | Implemented | `PreparePlanPage`, plan continuation APIs |
| Risk evaluation | Implemented but profile-dependent | `TradePlanRiskEvaluationService`, `RiskPersistence` |
| PAPER account onboarding | Backend contract exists; web path broken | `BrokerAccountService.create()` lines 44-75; Angular `createPaper()` lines 21-32 |
| Human execution | Implemented after 0042 | `PlanPage`, execution service and Trading Core execution APIs |
| PAPER full exit | Implemented and accepted | `PaperExitService`, Story 0042 |
| Position monitoring | Basic query/valuation surface exists | Angular `/positions` route and local PAPER position query |
| Analytics/learning | Backend pieces exist; product surface incomplete | `TradeAnalyticsController`; no `/analytics` route |
| AI interpretation | Not available | `DisabledAiEngineAdapter` |

## Confirmed Blocking Defect

`CreateBrokerAccountRequest` accepts `@Valid RiskProfileReference riskProfile`
and `BrokerAccountService.create()` requires it for PAPER accounts before any
operational graph is created. The Angular `BrokerAccountService.createPaper()`
request sends no `riskProfile` field. The Accounts component therefore reaches a
backend rejection for every normal PAPER creation attempt.

## Profile Availability Gap

Risk profile persistence and validation exist in Trading Core:

- `RiskPersistence.profile(profileId, semanticVersion)` resolves an exact
  immutable version.
- `RiskProfileValidator` validates structure and normalized rule completeness.
- `V2__trade_plan_risk_evaluation.sql` creates the profile tables.

No profile catalog controller, frontend profile model, or profile-management
surface was found. No risk-profile seed migration was found. Therefore a UI fix
that merely adds two fields would still leave users without a supported way to
discover an eligible pair of identifiers.

ADR-043 explicitly rejects silently creating or inferring a default profile.
The correct product behavior is explicit selection, or a clear unavailable
state when platform operations have not made a profile available.

## Current Profile Lifecycle

| Question | Current repository answer |
|---|---|
| Persistence | `risk_profile` plus `risk_profile_rule`, created by V2 schema migration |
| Creation today | No production creation path; tests insert explicit SQL fixtures |
| Versioning | Composite identity `(id, semantic_version)`; entities are `@Immutable` |
| Ownership | No owner column or ownership service; `authority` is a policy authority enum, not an authenticated owner relation |
| Platform ownership | Supported as a stored authority value and used by fixtures, but no platform catalog owner is modeled |
| User ownership | Not implemented |
| Assignment | `RiskPersistence.assignProfile(accountId, profileId, semanticVersion, ...)`, during PAPER provisioning |
| Validation | `RiskProfileValidator` checks semantic versions, metadata, authority, exact required rule set, rule versions, ratios, and assignment provenance |
| Reference | Exact `RiskProfileReference(profileId, semanticVersion)`; no latest-version lookup |

The current ownership classification is therefore **PLATFORM_REFERENCE_DATA for
the proposed V1 catalog, but not yet modeled as a durable ownership relation**.
This Story must not expose USER or cross-user ownership semantics that the
repository does not support.

## Operational-Data Conventions

Trading Core uses Flyway migrations for durable schema evolution. Market
Intelligence uses `BuiltinStrategyBootstrap` as an idempotent startup catalog
bootstrap, but that mechanism belongs to its strategy aggregate and is not a
RiskProfile lifecycle. No Trading Core risk-profile bootstrap, seed migration,
admin API, or configuration-driven profile writer exists.

For immutable, versioned risk policy reference data, a versioned Flyway
reference-data migration is the smallest deterministic mechanism: deployment
history identifies when a policy version became available, database constraints
persist the exact pair, and no restart-time mutation or hidden default is
introduced. It must be added only after the policy's actual rules are approved.

## Provisioning Versus Assignment

`PROFILE_PROVISIONING != PROFILE_ASSIGNMENT`.

- Provisioning inserts an explicit immutable profile version and its rule set.
- Assignment occurs only when the authenticated human submits the selected exact
  pair for PAPER account creation.
- The migration must not write
  `account_risk_profile_assignment` or select a default account profile.

## Catalog And Eligibility

The catalog authority is Trading Core. It should expose only unassigned,
persisted profile versions that pass the same structural eligibility validation
used for provisioning, with policy metadata already present in the domain:
`profileId`, `semanticVersion`, `policyId`, `policyVersion`, `authority`,
`createdAt`, `provenance`, and a summary of the three stored rules. No name,
description, market scope, or user ownership field exists and should not be
invented.

For this Story, PAPER eligibility means:

```text
persisted
+ exact profile/version reference
+ valid semantic versions and metadata
+ supported authority
+ exactly MAX_POSITION_RISK, MAX_EXPOSURE, DAILY_DRAWDOWN rules
+ valid rule versions, categories, severities, priorities, ratios, provenance
+ platform catalog visibility
```

The server revalidates after catalog selection. A deleted or changed profile
cannot be silently accepted; immutability and foreign keys normally make
disappearance or mutation impossible, while a non-visible or invalid reference
fails closed.

## Initial V1 Profile

No initial profile ID, semantic version, rule source, or approved numeric limits
were found in the repository. The test fixture's UUID and `2.1.0` version are
test data only and are not a production policy. Legacy `Rules` values are
explicitly rejected as an implicit translation by ADR-043.

Required operational input before implementation:

```text
INITIAL_PROFILE_SOURCE = human-approved Trading OS PAPER Standard Risk Policy, 2026-09-15
INITIAL_PROFILE_IDENTITY = PAPER_STANDARD / TRADING_OS_STANDARD_RISK
INITIAL_PROFILE_VERSION = profile 1.0.0 / policy 1.0.0
INITIAL_PROFILE_RULE_SOURCE = human-approved product policy with 1%, 3%, 3% limits
INITIAL_PROFILE_BUSINESS_RULES_ALREADY_DEFINED = YES

Technical profile ID implemented: `0a10c7e2-9d1e-4f5a-b6c8-123456789043`.
`policyId` is already a string column, so its technical value is the approved
`TRADING_OS_STANDARD_RISK`. The profile UUID is a stable repository-style
literal, deterministic across environments. The approved logical names are not
permission to change UUID-based columns into strings.
```

## Implemented Surface

- Flyway `V14__seed_paper_standard_risk_profile.sql` provisions the profile and
  three rules only.
- `GET /api/v1/risk-profiles/eligible` is a read-only authenticated Trading Core
  endpoint, routed through Gateway.
- `RiskProfileCatalogService` filters PLATFORM profiles through the existing
  `RiskProfileValidator` before returning a catalog read model.
- Angular Accounts loads the catalog reactively, requires an explicit composite
  profile/version selection, and sends the exact pair to PAPER provisioning.

## Existing Journey After Onboarding

Once a valid PAPER account exists, the repository now contains the principal V1
journey: scan or inspect an opportunity, prepare and accept a plan, evaluate
risk, explicitly execute, query the local position, and fully close it through
the local exit path. Story 0042 must remain unchanged in intent.

## Candidate Ranking

| Candidate | Value | Blocking impact | Scope confidence | Decision |
|---|---:|---:|---:|---|
| PAPER risk-profile onboarding | High | Directly blocks new PAPER users | High | Recommend |
| Analytics and feedback loop | Medium-high | Does not block first trade | Medium | Defer |
| AI Engine | High potential | Cannot compensate for blocked deterministic setup | Low | Defer |
| Active position monitoring | Medium | Basic position query/close now exists | Medium | Defer |
| News/macro | Medium | Additive, not a core blocker | Low | Defer |

## Conclusion

The first missing capability preventing a genuinely usable PAPER session was not
another intelligence capability. It is the unbroken, explicit risk-profile
onboarding contract. Flyway is the approved provisioning mechanism and the
initial policy definition is now provisioned. This Story remains intentionally
narrow and preserves the fail-closed risk design.

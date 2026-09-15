# Story 0043 - Explicit PAPER Risk-Profile Onboarding

## Metadata

**ID:** `0043`
**Title:** Explicit PAPER Risk-Profile Onboarding
**Status:** IMPLEMENTED - HUMAN REVIEWED; PRODUCT ACCEPTANCE PARTIAL
**Baseline:** `main` at `18f9d99` (Story 0042 merged)
**Related ADRs:** ADR-042, ADR-043

## Final Refinement Decision

Eligible platform RiskProfiles will enter the system through an explicit
versioned Flyway reference-data migration, after the business policy and its
rule limits have been separately approved. The migration inserts the immutable
`risk_profile` row and its complete `risk_profile_rule` set. It makes a profile
available; it does not assign that profile to any account.

The initial V1 profile is now human-approved with the business identity and
limits below. Technical identifiers must be chosen from the existing model and
recorded when the migration is implemented; they must not be invented as
business semantics.

## Approved V1 Policy

```text
POLICY_NAME = Trading OS PAPER Standard Risk Policy
PROFILE_LOGICAL_NAME = PAPER_STANDARD
SEMANTIC_VERSION = 1.0.0
POLICY_LOGICAL_NAME = TRADING_OS_STANDARD_RISK
POLICY_VERSION = 1.0.0
AUTHORITY = PLATFORM
MAX_POSITION_RISK = 1%
MAX_EXPOSURE = 3%
DAILY_DRAWDOWN = 3%
PROVENANCE = Trading OS PAPER Standard Risk Policy v1, human-approved product policy, 2026-09-15
```

The approved business limits are independent of FTMO, Kraken, legacy `Rules`,
or AI output. Their technical representation is `NUMERIC(30,12)`:
`0.010000000000`, `0.030000000000`, and `0.030000000000`.

## Goal

Allow a human trader to create a usable PAPER account from the web application
by selecting an exact existing valid versioned RiskProfile and sending that
reference through the established Trading Core provisioning contract.

If no eligible profile is available, onboarding must explain the prerequisite
and fail closed. The Story must not invent, silently copy, or automatically
assign a risk profile.

## Context

Story 0039 correctly changed PAPER provisioning to require an explicit
`RiskProfileReference(profileId, semanticVersion)`. Story 0042 then completed
the local PAPER entry/exit path. The current frontend still follows the older
provisioning contract and cannot provide the required profile reference.

The result is that the intended PAPER journey is operational only for callers
that already know a valid profile ID and semantic version. A new user cannot
complete account onboarding through the Accounts page.

## Problem

`BrokerAccountService.createPaper()` sends provider, display name, execution
mode, and initial capital, but omits `riskProfile`. Trading Core rejects the
request before creating the account. There is also no user-facing read-only
catalog of eligible profiles from which an exact immutable version can be
selected.

This is a product-blocking contract gap, not a reason to relax the deterministic
risk boundary.

## Scope

- Expose a user-authorized read-only catalog of existing eligible RiskProfile
  versions, including the exact ID, semantic version, policy metadata, and
  enough rule summary for an informed selection.
- Add explicit RiskProfile selection to PAPER account onboarding.
- Send the selected `profileId` and `semanticVersion` in the existing
  `CreateBrokerAccountRequest` contract.
- Preserve Trading Core validation as the final authority for existence,
  structure, supported vocabulary, and assignment.
- Make missing profile availability and rejected profile references visible as
  actionable onboarding errors.
- Preserve the selected exact profile reference in the resulting account
  provisioning state and account reload behavior.
- Add focused contract, service, component, and end-to-end provisioning
  coverage as implementation work.

## Out of Scope

- Automatic default-profile creation or assignment.
- Copying a profile from another account without explicit user selection.
- Risk-profile authoring, editing, deletion, or version mutation.
- Changes to risk rules, Risk Facts, T0/T1, position sizing, or margin semantics.
- Changes to LIVE broker provisioning or credential handling.
- AI Engine implementation, news, macro, passive scanning, or strategy redesign.
- PAPER fees, slippage, partial exits, or position-model redesign.
- Commit, push, merge, or deployment work during Story authoring.

## Acceptance Criteria

- [x] Given eligible persisted RiskProfile versions, the authenticated user can
      retrieve only the catalog authorized by the repository's ownership and
      exposure rules.
- [x] Given multiple versions of a profile, the UI preserves and submits the
      exact selected `profileId` and `semanticVersion` pair.
- [x] Given a PAPER account creation request with a selected valid profile,
      Trading Core provisions the existing atomic PAPER graph without changing
      its validation or authority semantics.
- [x] Given no eligible profile, the UI prevents submission and explains that
      an administrator must make a valid versioned profile available.
- [x] Given an unknown, malformed, incomplete, or unsupported profile
      reference, provisioning fails closed and no usable account graph is
      created.
- [x] Given a successful provisioning, the account can be reloaded with its
      canonical Account-to-BrokerAccount relation, risk configuration, and exact
      profile assignment intact.
- [x] Given existing LIVE onboarding, its credential and provider behavior is
      unchanged.
- [ ] Given the normal PAPER journey, a user can proceed from account creation
      to opportunity, plan, risk decision, human execution, position query, and
      full local exit without manually constructing an API request.

## Constraints

- Respect ADR-042 and ADR-043.
- RiskProfile remains immutable, versioned, explicit, and deterministic.
- The frontend is not an authority for profile validity or risk eligibility.
- Do not expose credentials or provider-specific payloads.
- Use existing Angular reactive conventions and Trading Core/Gateway API
  conventions.
- Do not claim runtime or test validation during refinement.

## Relevant Modules

- `trading-core`
- `gateway`
- `trading-os-web`

## Test Intent

Implementation must cover exact profile selection, empty catalog behavior,
authorization, invalid references, successful reload, rollback/no partial graph,
LIVE regression, and the complete PAPER onboarding-to-exit path.

## Architectural Decision

`NEW_ADR_REQUIRED = NO` for the proposed scope. The Story exposes and consumes
the already accepted explicit profile-assignment contract; it does not create a
new authority boundary. A separate ADR is required if implementation proposes
automatic profile policy, cross-user profile visibility, or a new risk authority.

# Story 0053 - Make a Trade Planning Profile effective for PAPER accounts

## Metadata

**ID:** `0053`
**Title:** Make a Trade Planning Profile effective for PAPER accounts
**Status:** Approved

---

## Goal

Allow an authenticated user to create a PAPER account and continue through
Trade Plan creation without bypassing ownership, risk, or human-authorization
controls.

---

## Context

PAPER account onboarding assigns the versioned risk profile and initial capital,
but does not assign a Trade Planning Profile. Creating a Trade Plan therefore
fails with `PLANNING_PROFILE_MISSING` and the message `Account has no effective
Trade Planning Profile`.

Risk profiles and Trade Planning Profiles are separate concepts and must not be
silently merged. This Story uses a validated, versioned platform-managed Trade
Planning Profile for new PAPER accounts. User-selected planning profiles remain
out of scope.

---

## Scope

* Provide a valid versioned platform-managed Trade Planning Profile.
* Assign it transactionally during PAPER account creation.
* Preserve manual Trade Planning Profile assignment and ownership checks.
* Validate profile/account currency compatibility.
* Expose effective risk and planning profile references in the account view.
* Add backend, frontend, and integration regression coverage.
* Re-run the authenticated PAPER journey from Story 0048.

---

## Out of Scope

* Changing Risk Domain rules or thresholds.
* Merging risk profiles and Trade Planning Profiles.
* Changing LIVE behavior.
* Adding user selection of Trade Planning Profiles.
* Autonomous execution or unattended trading.
* Introducing a new E2E framework.

---

## Acceptance Criteria

* [ ] PAPER account creation assigns a valid versioned Trade Planning Profile.
* [ ] The assignment is transactional and remains immutable by version.
* [ ] The effective planning profile endpoint returns the assigned profile to
      the account owner.
* [ ] Creating a Trade Plan from a PAPER opportunity no longer fails with
      `PLANNING_PROFILE_MISSING`.
* [ ] Profile/account currency mismatch is rejected explicitly.
* [ ] Ownership checks remain enforced for profile and account operations.
* [ ] Account reload displays the selected risk profile and effective planning
      profile versions.
* [ ] LIVE accounts do not receive the PAPER default automatically.
* [ ] Affected backend and frontend tests pass.
* [ ] The Angular production build passes.
* [ ] Story 0048 is re-run through Trade Plan creation from the web interface.

---

## Relevant Modules

* `trading-core`
* `trading-os-web`

## Related Stories

* `0043-paper-account-risk-profile-onboarding`
* `0048-paper-trading-journey-runtime-acceptance`
* `0050-paper-risk-context`

---

## Validation

* Trading Core profile, account-provisioning, and Trade Plan integration tests.
* Angular `npm run test:ci`.
* Angular `npm run build`.
* Authenticated PAPER runtime walkthrough.
* `git diff --check`.

---

## Definition of Done

* [ ] Implementation completed within this Story's scope.
* [ ] Acceptance criteria validated with evidence.
* [ ] Human code review completed.
* [ ] Human commit created.

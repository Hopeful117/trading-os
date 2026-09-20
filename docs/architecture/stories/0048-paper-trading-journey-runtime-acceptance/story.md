# Story 0048 - Validate the PAPER trading journey through the web application

## Metadata

**ID:** `0048`

**Title:** Validate the PAPER trading journey through the web application

**Status:** Completed

---

## Goal

Demonstrate that a normal authenticated user can complete the supported PAPER
journey through the web application, from account onboarding to local position
exit, without manually constructing API requests.

This Story produces product acceptance evidence. It is not a request to bypass
the human approval boundary or to create autonomous trading behavior.

---

## Context

Story 0043 implemented explicit versioned risk-profile selection for PAPER
account onboarding, but its final acceptance criterion remains incomplete: the
normal PAPER journey has not been demonstrated end to end.

The relevant capabilities now exist across Stories 0021, 0023, 0030, 0031,
0032, 0041, and 0042. The remaining uncertainty is whether the complete user
journey works in an authenticated runtime with real persisted state and the
current Gateway/service configuration.

---

## Problem

Unit and integration tests prove individual seams, but they do not prove that
the trader can move between the screens and complete the full journey. A
failure at account selection, opportunity availability, plan navigation,
execution feedback, position loading, or local exit can remain invisible until
runtime use.

---

## Scope

* Define a repeatable authenticated runtime scenario using a PAPER account.
* Verify explicit selection of an eligible immutable risk profile.
* Verify account creation, account reload, and initial capital visibility.
* Verify navigation through active opportunity, Trade Plan creation, human
  decision, deterministic risk evaluation, and explicit execution.
* Verify execution feedback and persisted PAPER position visibility after
  reload.
* Verify full local exposure close and final empty-position state.
* Capture failures with the screen, request boundary, service, and state where
  the journey stops.
* Add automated coverage only for deterministic regressions discovered during
  the runtime validation.

---

## Out of Scope

* LIVE broker or Kraken sandbox acceptance.
* Autonomous trading or unattended execution.
* Creating or changing risk-policy business rules.
* Introducing a new E2E framework unless the repository has an approved need
  and location for it.
* Replacing backend integration tests with browser-only tests.
* Strategy redesign, AI Engine work, analytics, or historical reporting.

---

## Acceptance Criteria

* [x] A fresh authenticated user can create a PAPER account by selecting an
      eligible versioned risk profile and providing valid initial capital.
* [x] The created account and its risk configuration remain visible after
      reload.
* [x] The user can navigate from an active opportunity to a Trade Plan without
      manually constructing an API request.
* [x] The user can explicitly accept the plan, evaluate deterministic risk, and
      explicitly authorize execution.
* [x] The resulting PAPER execution is visible through the frontend and the
      corresponding position is visible after reload.
* [x] The user can explicitly close the full PAPER exposure and observe the
      resulting empty-position state after reload.
* [x] No step requires bypassing authentication, ownership checks, risk rules,
      or human authorization.
* [x] Runtime evidence records environment, scenario data, observed states,
      and any unresolved blocker without claiming unsupported success.
* [x] Relevant Angular and affected backend tests pass, along with the
      production build.

---

## Constraints

* Use only existing supported APIs and web routes.
* Use a deterministic, isolated PAPER account and non-production credentials.
* Do not weaken security, ownership, risk, or idempotency controls to make the
  scenario pass.
* The trader remains responsible for accepting risk and authorizing execution.
* Existing runtime and test data must not be deleted or altered outside the
  scenario's authorized scope.
* Any product or architecture gap discovered during validation becomes a
  separately approved Story or a documented blocker.

---

## Relevant ADRs

* `docs/architecture/adr/ADR-001.md` - Trading OS Vision and Human Authority
* `docs/architecture/adr/ADR-014.md` - Trading Decision Pipeline
* `docs/architecture/adr/ADR-028.md` - Deterministic Risk
* `docs/architecture/adr/ADR-029.md` - Execution Domain Architecture

---

## Relevant Modules

* `trading-os-web`
* `trading-core`
* `market-intelligence`
* `gateway`
* `risk-domain`

---

## Validation

* Angular `npm run test:ci`.
* Angular `npm run build`.
* Affected Maven module test suites.
* Authenticated runtime walkthrough using the configured local environment.
* Persistence verification after page reload and service restart where
  supported by the environment.
* `git diff --check` and review of the resulting evidence.

---

## Definition of Done

* [x] Repository Analysis approved
* [x] Implementation Plan approved when required
* [x] Runtime scenario approved
* [x] Implementation or regression fixes completed when required
* [x] Relevant validation executed
* [x] Runtime acceptance evidence completed
* [x] Diff reviewed in IntelliJ
* [x] Code Review approved
* [x] Engineering Report completed
* [x] Human commit created

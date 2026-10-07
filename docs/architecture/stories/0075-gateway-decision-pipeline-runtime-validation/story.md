# Story 0075 - Validate the Decision Pipeline through the Gateway

## Metadata

**ID:** `0075`
**Title:** Validate the Decision Pipeline through the Gateway
**Status:** CLOSED - HUMAN ACCEPTED

The implementation and authenticated PAPER Gateway runtime evidence were
reviewed and accepted by the human engineer. The final human Git commit remains
pending under the repository workflow; this status does not authorize another
implementation, commit, push, or merge operation.

---

## Goal

Prove that an authenticated trader can complete the supported PAPER decision
journey through the public Gateway after the Opportunity and Decision Workspace
convergence.

The public Gateway must route the browser to the intended service contracts,
preserve authenticated identity and idempotency semantics, and expose useful
downstream failures.

---

## Context

Story `0062` converged Opportunity preparation and the account-scoped Decision
Workspace. Story `0074` added the XOOMAR economic-calendar adapter and its
news-service persistence path.

The Gateway already declares routes for Opportunities, Trade Plans,
Executions, Markets, News and the market-data WebSocket. Existing route tests
cover important route-table behavior, but they do not prove every browser
request reaches a compatible downstream controller with the expected path,
identity context, and response contract.

The V1 trader definition of done identifies Gateway reachability and a complete
Opportunity -> TradePlan -> Risk -> Execution journey as closure blockers.

---

## Problem

Individual service tests and abstract Gateway route tests can pass while the
real browser journey still fails because of a path mismatch, an incorrect
downstream mapping, a lost header, or an unhandled downstream error.

The current execution surface is especially at risk because the public Gateway
uses `/api/v1/executions/**` while parts of Trading Core expose legacy execution
paths. The economic calendar route also needs a contract-level check after the
new provider integration.

---

## Scope

* Verify and correct, where required, the Gateway mappings used by the current
  browser decision journey:
  * `/api/v1/opportunities/**`;
  * `/api/v1/trade-plans/**`;
  * `/api/v1/executions/**`;
  * `/api/v1/accounts/**`;
  * `/api/v1/news/**` for economic-calendar reads;
  * `/api/v1/markets/**` and `/ws/market-data` where used by the Workspace.
* Verify that authenticated user identity is derived from the validated JWT and
  cannot be replaced by a client-supplied actor value at the Gateway boundary.
* Verify that `Idempotency-Key` reaches the relevant downstream command
  boundary unchanged.
* Add Gateway contract/integration coverage against compatible downstream
  controllers or approved service stubs, including execution path alignment.
* Validate the browser journey from an active Opportunity through the
  Decision Workspace, TradePlan, human decision, deterministic Risk evaluation,
  and PAPER execution.
* Verify persisted PAPER position visibility after navigation or reload.
* Verify that the economic-calendar read path remains available without
  exposing XOOMAR payloads or requiring a provider credential.
* Record failures with the originating screen, Gateway request, downstream
  service, and persisted state.

---

## Out of Scope

* Kraken sandbox or LIVE execution acceptance.
* Reworking the Risk Domain or TradePlan lifecycle.
* AI interpretation of economic events.
* A frontend economic-calendar presentation.
* Broad remediation of legacy Trade ownership findings, JWT fallback secrets,
  or service-local authentication boundaries; those require separate approved
  security Stories.
* Removing legacy Market Intelligence routes before a separate migration
  decision.
* Introducing a new E2E framework without an approved repository location and
  workflow.

---

## Acceptance Criteria

* [x] The Angular application uses the public Gateway for every request in the
      validated journey and does not call service ports directly.
* [x] Authenticated Opportunity list/detail requests reach the intended Market
      Intelligence controllers through `/api/v1/opportunities/**`.
* [x] Opportunity-origin and MANUAL TradePlan creation reach the intended
      Trading Core contract through `/api/v1/trade-plans/**`.
* [x] Risk evaluation and human decision requests reach the intended contract
      with account and plan ownership enforced.
* [x] Execution requests reach a compatible Trading Core controller through the
      public execution route, or the route/controller contract is corrected and
      covered by a regression test.
* [x] `Idempotency-Key` and validated actor context survive the Gateway path
      without accepting client-controlled identity overrides.
* [x] Economic-calendar reads reach News Service through `/api/v1/news/**` and
      return the normalized domain contract rather than provider payloads.
* [x] Downstream 4xx/5xx, timeout, and unavailable-service states remain
      visible and actionable in the frontend.
* [x] A fresh authenticated PAPER scenario completes Opportunity -> Workspace
      -> TradePlan -> human decision -> Risk -> execution without manually
      constructing HTTP requests.
* [x] The resulting PAPER position is visible after reload and no execution
      occurs without explicit human authorization.
* [x] Gateway, affected service, and Angular tests pass; the production build
      and `git diff --check` pass.
* [x] Runtime evidence records the environment, requests, observed states, and
      any unresolved blocker.

---

## Constraints

* The Gateway remains a technical ingress and must not contain business logic.
* Preserve the responsibility chain defined by ADR-014.
* Preserve the authenticated Gateway boundary defined by ADR-002, ADR-013 and
  ADR-044.
* Preserve TradePlan idempotency and execution semantics defined by ADR-029 and
  ADR-041.
* Keep XOOMAR-specific payloads inside the News Service infrastructure adapter.
* PAPER position authority remains local to Trading Core.
* Do not weaken authentication, ownership, risk, or human-approval controls to
  make the scenario pass.
* Do not commit, push, merge, or rewrite history automatically.

---

## Relevant ADRs

* `docs/architecture/adr/ADR-002.md`
* `docs/architecture/adr/ADR-013.md`
* `docs/architecture/adr/ADR-014.md`
* `docs/architecture/adr/ADR-029.md`
* `docs/architecture/adr/ADR-041.md`
* `docs/architecture/adr/ADR-044.md`

---

## Relevant Modules

* `gateway`
* `trading-core`
* `market-intelligence`
* `news-service`
* `trading-os-web`

---

## Validation

* Targeted Gateway route and downstream contract tests.
* Affected Trading Core, Market Intelligence, and News Service tests.
* Angular `npm run test:ci` and `npm run build`.
* `npx prettier --check .` from `trading-os-web`.
* Authenticated PAPER runtime walkthrough through the configured local
  environment.
* Persistence verification after reload.
* Runtime evidence: `docs/investigations/paper-trading-journey-runtime-acceptance-2026-09-20.md`.
* `git diff --check`.

---

## Definition of Done

* [x] Repository Analysis completed.
* [x] Story scope approved.
* [ ] Implementation Plan approved when required.
* [x] Implementation completed.
* [x] Relevant validation executed.
* [x] Runtime evidence completed.
* [ ] Diff reviewed in IntelliJ.
* [ ] Code Review approved.
* [ ] Engineering Report completed.
* [ ] Human commit created.

## Documentation Reconciliation

The implementation and runtime acceptance evidence are present in the merged
commit `d74a79f`, and all Story acceptance criteria are checked above. Human
closure was accepted on 2026-10-06. The final human Git commit remains pending
under the repository workflow.

# Story 0049 - Provide trade history and plan continuity in the web app

## Metadata

**ID:** `0049`

**Title:** Provide trade history and plan continuity in the web app

**Status:** Completed

---

## Goal

An authenticated trader can return to a previous Trade Plan or execution and
understand its current state without relying on browser history, technical
identifiers, or the opportunities list.

The web application should support daily review while keeping authoritative
trade and execution state in the existing backend services.

---

## Context

The current web application has views for active opportunities, one Trade Plan
version, execution feedback, and open positions. Story 0023 intentionally left
plan-history browsing out of scope, and Story 0031 intentionally left an
execution-history page out of scope.

Trading Core already exposes an owned execution list, while the frontend does
not consume it. The sidebar also exposes an `/analytics` link without a
corresponding route.

---

## Problem

After leaving the Plan page, the trader has no reliable way to find a prior
decision or execution. The current navigation is optimized for the active
opportunity list, not for reviewing accepted, rejected, expired, failed, or
completed trades.

This makes it difficult to answer what was decided, what was executed, and
which current position or close result belongs to that decision.

---

## Scope

* Add an authenticated trader-facing view for owned executions using the
  existing public execution-list contract where it is sufficient.
* Display human-readable execution states, instrument context when available,
  account context, timestamps, broker reference, fill summary, and safe next
  actions.
* Provide navigation from an execution to its Trade Plan and relevant account
  positions when the relationship is available.
* Provide a useful entry point for existing Trade Plans, or document the
  minimal backend read contract required if no owned plan-list contract exists.
* Make the `/analytics` navigation honest: implement an approved route or
  remove/defer the link until an Analytics Story exists.
* Add focused Angular tests for empty, loading, error, ownership, status, and
  navigation states.

---

## Out of Scope

* New trading, risk, or execution decisions.
* Automatic retry, reconciliation, or position management.
* Strategy analytics, performance calculations, equity curves, or backtesting.
* Changing the existing Trade Plan lifecycle or execution domain.
* Cross-account aggregation without an explicit product decision.
* Exposing global recovery or provider-specific broker data.

---

## Acceptance Criteria

 * [x] An authenticated trader can view only their own executions and sees an
      explicit loading, empty, and error state.
 * [x] Execution statuses are rendered with the existing safe semantics;
      uncertain outcomes are not presented as failures or successes.
 * [x] An execution can be followed to its Trade Plan and account positions when
      the backend relationship is available.
 * [x] The interface exposes no blind retry for uncertain execution outcomes.
 * [x] Existing backend contracts are reused when sufficient; any missing
      contract is identified and approved separately before implementation.
 * [x] The `/analytics` sidebar entry is no longer a dead navigation target.
 * [x] Angular tests cover ownership-scoped data, status rendering, empty and
      error states, and navigation.
 * [x] `npm run test:ci`, `npm run build`, and `git diff --check` pass.
 * [x] No unrelated behavior is changed.

---

## Constraints

* Trading Core remains authoritative for owned execution and Trade Plan data.
* The frontend must not derive performance, risk, fill, or lifecycle truth.
* Preserve account ownership and authenticated Gateway boundaries.
* Preserve the existing distinction between broker acknowledgement, fill,
  failure, and uncertain outcome.
* Do not create an ad hoc analytics contract as a substitute for a product
  decision.
* Do not expose raw provider responses or global recovery operations.

---

## Relevant ADRs

* `docs/architecture/adr/ADR-001.md` - Human Authority
* `docs/architecture/adr/ADR-014.md` - Trading Decision Pipeline
* `docs/architecture/adr/ADR-029.md` - Execution Domain Architecture

---

## Relevant Modules

* `trading-os-web`
* `trading-core` - only if an approved read contract is missing
* `gateway` - only if an intended public route is not reachable

---

## Validation

* Focused Angular service, component, route, and navigation tests.
* Angular production build.
* Contract verification for the existing execution list and plan reads.
* Authenticated manual verification with multiple execution states.
* Verification that cross-user data cannot be displayed.
* `git diff --check`.

---

## Definition of Done

 * [x] Repository Analysis approved
 * [x] Implementation Plan approved when required
 * [x] Any required contract decision approved
 * [x] Implementation completed
 * [x] Relevant validation executed
 * [x] Diff reviewed in IntelliJ
 * [x] Code Review approved
 * [x] Engineering Report completed
 * [x] Human commit created

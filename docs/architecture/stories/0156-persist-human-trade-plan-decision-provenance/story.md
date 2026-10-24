# Story 0156 - Persist Human Trade Plan Decision Provenance

## Metadata

**ID:** `0156`
**Title:** Persist Human Trade Plan Decision Provenance
**Status:** IMPLEMENTED - VALIDATION COMPLETE

## Goal

Preserve durable provenance for the authenticated human decision that accepts or
rejects a TradePlan version.

## Context

The current Opportunity -> TradePlan -> human decision -> deterministic Risk
workflow already performs actor-bound decision checks. The accepted or rejected
TradePlan version is persisted, but the version does not record the decision
actor or decision time. The decision is therefore enforceable at the command
boundary but incomplete as an auditable product fact.

## Scope

* Add optional decision actor and decision timestamp fields to the existing
  immutable TradePlan version model.
* Populate those fields for authenticated ACCEPT and REJECT decisions.
* Preserve decision provenance when later versions are created by Risk or
  execution lifecycle transitions.
* Persist and expose the fields through existing TradePlan persistence and
  response contracts.
* Add focused domain, persistence, application, and API regression tests.

## Out of Scope

* Changing Opportunity, ActiveScan, FeatureProjectionV1, scoring, or ranking.
* Adding a separate decision aggregate or microservice.
* Changing Risk rules or execution authorization.
* Adding AI or broker-provider behavior.
* Replacing the existing actor-bound decision endpoint.

## Acceptance Criteria

* An authenticated ACCEPT or REJECT decision records the authenticated actor
  and the decision timestamp on the resulting TradePlan version.
* The persisted decision provenance survives reload and is visible through the
  existing TradePlan response.
* Later RISK_VALIDATED, READY_TO_EXECUTE, and EXECUTED versions preserve the
  original human decision provenance.
* Repeated identical decisions remain idempotent and do not rewrite provenance.
* Wrong-owner, stale-version, and conflicting-decision protections remain
  unchanged.
* TradePlan creation and legacy test fixtures remain compatible.
* No risk approval or execution authorization is implied by human decision
  provenance.

## Constraints

* TradePlan remains the canonical durable authority for this lifecycle.
* Trading Core remains authoritative for authenticated actor identity.
* Market Intelligence remains authoritative for TradePlan persistence.
* Risk remains deterministic and independent from human decision provenance.
* No new durable architectural authority is introduced.

## Relevant ADRs

* `docs/architecture/adr/ADR-001.md`
* `docs/architecture/adr/ADR-014.md`
* `docs/architecture/adr/ADR-027.md`
* `docs/architecture/adr/ADR-031.md`
* `docs/architecture/adr/ADR-044.md`

## Relevant Modules

* `market-intelligence`
* `trading-core` only for existing contract regression tests if required

## Validation

* Targeted Market Intelligence TradePlan domain, application, persistence, and
  web tests.
* Existing Trading Core TradePlan decision tests.
* `git diff --check`.

## Definition of Done

* [x] Implementation completed.
* [x] Relevant validation executed.
* [x] Documentation reconciliation completed.
* [ ] Code Review completed.
* [ ] Human commit created.

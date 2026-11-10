# Story 0223 - Provider-Scoped Historical Evidence Retirement

## Metadata

**ID:** `0223`
**Title:** Provider-Scoped Historical Evidence Retirement
**Status:** Implementation completed - validation limitations recorded
**Priority:** P1 - Storage Safety and Research Trustworthiness
**Related ADRs:** `ADR-057`, `ADR-060`, `ADR-062`, `ADR-067`, `ADR-069`, `ADR-079`, `ADR-080`, `ADR-091`

## Goal

Implement the governed domain and preflight workflow required to retire one
exact provider-scoped historical order-book evidence source without weakening
live market functionality, historical provenance, replay trust, dependency
safety or human-controlled physical database authorization.

The implementation must prepare and prove the operation. It must not execute
physical deletion, truncate a relation, modify the current runtime dataset or
authorize a DBA action.

## Context

The Market Data database contains a legacy Kraken order-book evidence relation
whose reviewed target scope is provider `KRAKEN`, provider symbol `PF_XBTUSD`,
depth `25`, with the canonical market identity recorded explicitly. Product
approval permits abandoning raw historical reproducibility for this exact
scope so that cTrader historical research can remain the priority.

ADR-080 governs ordinary segment retention and remains unchanged. ADR-091
defines an exceptional source-retirement workflow with separate business and
operational state, explicit dependency transition, exact-scope preflight,
backup/restore validation, historical capture blocking, replay fail-closed
behavior and separately authorized physical execution.

Market Data and Market Intelligence cannot commit one distributed transaction.
The implementation must therefore use durable idempotent transitions, operation
identity, dependency acknowledgement and reconciliation rather than assuming
atomic cross-database behavior.

## Scope

- Add a provider-neutral, durable retirement-operation model for exact
  provider, provider symbol, depth and canonical market identity.
- Represent the ADR-091 lifecycle, including `PREPARED`, `SOURCE_RETIRED`,
  `PHYSICAL_RETIRED`, `FAILED`, `AMBIGUOUS` and `RECONCILIATION_REQUIRED`.
- Make transitions monotonic and idempotent, preserving operation identity,
  approval reference, actor, timestamps, reasons and fingerprints.
- Implement exact-scope read-only preflight that fails closed for any
  out-of-scope row, unknown dependency, unexpected database object, stale
  inventory or contradictory provider identity.
- Persist the artifact/dependency inventory and coordinate the explicit
  `SOURCE_RETIRED` transition across Market Data and Market Intelligence using
  existing service boundaries and a reconciliation-safe contract.
- Make source-dependent replay and artifact reuse report unavailable or
  source-retired semantics rather than `AVAILABLE` after the transition.
- Block historical evidence capture for the retired provider/symbol/depth at
  the persistence boundary while preserving live in-memory acquisition,
  current prices, balances, positions and account synchronization.
- Validate approval reference, exact authorized scope, operation identity and
  physical-action authorization without treating client claims or disk pressure
  as authority.
- Provide a runbook for backup/restore validation, quiescence, separately
  authorized physical execution, post-operation validation and reconciliation.

## Out Of Scope

- Executing `TRUNCATE`, `DELETE`, `DROP`, `CASCADE`, `RESTART IDENTITY` or any
  other physical database mutation.
- Changing existing Flyway migrations in place or introducing a generic bulk
  deletion framework.
- Automatic disk-pressure deletion, automatic scheduling or autonomous DBA
  execution.
- Deleting or mutating FeatureSnapshots, outcomes, research executions, dataset
  manifests, exports or audit records.
- Changing ADR-080 ordinary segment retention eligibility.
- Disabling live Kraken acquisition, prices, balances, positions, account
  synchronization or unrelated provider functionality.
- Changing cTrader historical research behavior or provider contracts.
- Introducing distributed transactions, a new event broker, a generic job
  platform, partition migration or a second full relation copy.
- Committing, pushing, merging, deploying, restarting services or mutating the
  constrained runtime environment.

## Acceptance Criteria

- [ ] A retirement operation records exact provider, provider symbol, depth and
      canonical market identity; wildcard scope is rejected.
- [ ] The operation has durable operation identity, approval reference, actor,
      fingerprints, timestamps, transition reason and immutable audit history.
- [ ] Business and operational states follow ADR-091 and invalid, backward,
      duplicate, failed and ambiguous transitions are deterministic.
- [ ] Repeating an identical request is idempotent; changed scope, approval,
      inventory or preflight fingerprint requires a new operation identity.
- [ ] Exact-scope preflight proves all relation rows are in scope and fails
      closed for any out-of-scope or unverifiable row.
- [ ] Preflight inventories artifacts, dependencies, holds and dependent
      database objects and persists a stable inventory fingerprint.
- [ ] Unknown, stale, unreleased, held, untrusted or contradictory dependencies
      block preparation and produce actionable blocker categories.
- [ ] Market Intelligence dependencies transition explicitly to
      `SOURCE_RETIRED`; source retirement is refused when acknowledgement is
      missing, stale or contradictory.
- [ ] Cross-database timeout, partial acknowledgement and unknown commit
      outcomes produce `AMBIGUOUS` or `RECONCILIATION_REQUIRED`, never blind
      retry or inferred success.
- [ ] Source-retired artifacts remain auditable but cannot be returned as
      replay `AVAILABLE` or silently used as reproducible source evidence.
- [ ] Historical capture rejects the exact retired scope at the persistence
      boundary while live acquisition and unrelated scopes continue to work.
- [ ] Approval validation rejects missing, expired, revoked, mismatched or
      client-supplied-only authorization references.
- [ ] The implementation exposes readiness for, but does not perform, the
      separately authorized physical operation.
- [ ] Runbook acceptance criteria cover external backup restore validation,
      reader/writer quiescence, `TRUNCATE TABLE ONLY` safeguards, post-operation
      validation and ambiguous-operation reconciliation.
- [ ] Tests cover exact scope, mixed-scope rejection, dependency transition,
      replay fail-closed behavior, capture guard, live acquisition preservation,
      idempotency, failure recovery and cross-database inconsistency.
- [ ] No current runtime evidence rows are deleted or mutated during
      implementation or validation.
- [ ] Focused tests, relevant Market Data and Market Intelligence validation,
      documentation checks and `git diff --check` pass.

## Constraints

- Preserve Market Data ownership of raw evidence, capture, replay, trust and
  source-retirement authority.
- Preserve Market Intelligence ownership of artifact/dependency lifecycle and
  derived research records.
- Preserve immutable FeatureSnapshot, outcome, research and export contracts.
- Preserve `knownAt` and provider-specific chronology semantics.
- Keep `SOURCE_RETIRED` distinct from ordinary `RELEASED`.
- Fail closed for unknown evidence, unknown dependencies, incomplete inventory,
  stale fingerprints, ambiguous state or unavailable backup validation.
- Do not infer authority from disk pressure, provider demo/live labels, client
  headers or inferred symbol identity.
- Keep physical PostgreSQL details in the operational adapter/runbook rather
  than provider-neutral domain contracts.
- Do not modify pre-existing user changes or runtime artifacts.
- Do not commit, push, merge or create a pull request automatically.

## Relevant ADRs

- `docs/architecture/adr/ADR-057.md`
- `docs/architecture/adr/ADR-060.md`
- `docs/architecture/adr/ADR-062.md`
- `docs/architecture/adr/ADR-067.md`
- `docs/architecture/adr/ADR-069.md`
- `docs/architecture/adr/ADR-079-deterministic-liquidity-evidence-trust-and-policy-boundary.md`
- `docs/architecture/adr/ADR-080-bounded-order-book-evidence-storage-and-physical-retention.md`
- `docs/architecture/adr/ADR-091-proposed-provider-scoped-full-historical-evidence-retirement.md`

## Relevant Modules

- `market-data`
- `market-intelligence`

## Validation

- Focused retirement-operation, transition, persistence and preflight tests.
- Cross-service dependency acknowledgement and reconciliation tests using
  isolated fixtures and failure injection.
- Replay and artifact availability regression tests for `SOURCE_RETIRED`.
- Historical capture guard tests proving live acquisition remains available.
- Approval, idempotency, fingerprint and exact-scope validation tests.
- Structural database-object and runbook validation without applying physical
  changes to the current runtime database.
- Backup/restore acceptance evidence recorded as an operational prerequisite;
  no production backup restore is performed by the implementation task.
- Relevant module tests/builds and `git diff --check`.

## Definition Of Done

- [ ] ADR-091 accepted by the human engineer and architecture owner.
- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Domain and preflight implementation completed without physical deletion.
- [ ] Relevant validation executed and reported.
- [ ] Operational runbook completed.
- [ ] Documentation reconciled.
- [ ] Code Review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created separately.

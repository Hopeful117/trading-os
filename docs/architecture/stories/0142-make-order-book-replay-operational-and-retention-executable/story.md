# Story 0142 - Make Order-Book Replay Operational and Retention Executable

## Metadata

**ID:** `0142`
**Title:** Make Order-Book Replay Operational and Retention Executable
**Status:** Draft
**Related Stories:** `0141`
**Related ADRs:** `ADR-063`, `ADR-065`, `ADR-067`, `ADR-069`, `ADR-079`

---

## Goal

Make historical Order Book replay operational on the existing large evidence
population and provide a controlled retention execution path without weakening
replay trust, provenance, dependency, hold, audit, or fail-closed safeguards.

Automatic destructive retention must remain disabled by default. The existing
runtime evidence population must remain untouched during implementation and
validation.

---

## Context

Market Data owns normalized Order Book evidence, sequence validity, deterministic
reconstruction, replay, replay promotion, and evidence retention. The canonical
evidence table has grown to approximately 87 GB. The current replay path selects
eligible evidence but materializes the complete result in application memory,
which makes replay operationally unsafe on the existing population.

Retention domain logic and guarded internal endpoints already exist:

```text
GET  /internal/v1/order-book/retention/preview
POST /internal/v1/order-book/retention/retire
```

`previewRetirement()` records a dry-run operation and therefore has a database
side effect. No automatic scheduler or coordinator currently invokes the
retention workflow.

ADR-063 defines the canonical snapshot/delta and `knownAt` replay semantics.
ADR-065 defines sequence and generation boundaries. ADR-067 defines lifecycle-
aware retention and explicit dependency release. ADR-069 requires measured,
semantically equivalent replay acceleration. ADR-079 preserves provider-specific
trust and chronology semantics, including local-observation semantics for
cTrader.

---

## Problem

Replay currently loads all eligible evidence after the selected sequence start
into a `List<OrderBookEvidence>` before reconstruction. It also retains the
source fingerprint list needed by the replay result and promotion contract.
Against the existing population this can scan, deserialize, and retain too much
data, causing replay timeouts and unbounded application memory pressure.

Retention eligibility is guarded, but there is no controlled automatic execution
path. Evidence can therefore continue growing indefinitely unless an operator
manually invokes the internal workflow.

---

## Scope

- Map and preserve the complete current replay path and its cutoff, trust,
  sequence, provenance, ordering, and fingerprint semantics.
- Select one valid trusted snapshot or checkpoint at or before the requested
  cutoff as the reconstruction base.
- Retrieve only eligible evidence after that base and through the cutoff.
- Move filtering into the database and use deterministic streaming, keyset
  pagination, or bounded batches where required by the actual query shape.
- Keep replay application memory bounded by the reconstruction state and bounded
  retrieval state rather than the complete historical evidence population.
- Preserve Kraken provider chronology, provider sequence, checksum/trust, and
  strict continuity semantics.
- Preserve cTrader local-observation replay, local cutoff semantics, connection
  generation isolation, and the absence of provider-sequence authority.
- Add only the minimum query/index support justified by the implemented replay
  predicates.
- Do not apply a large-table migration or index build to the constrained runtime
  database during validation.
- Add a controlled retention coordinator using existing retention service
  safeguards.
- Keep automatic scheduling disabled by default with an independent destructive
  retirement gate.
- Prevent overlapping local retention executions.
- Keep retention execution incremental and bounded by the existing or introduced
  configurable batch limit.
- Expose bounded retention and replay operational visibility consistent with
  existing project conventions.
- Add focused replay, retention, coordinator, and regression tests.
- Reconcile relevant operational and architecture documentation.

---

## Out of Scope

- Deleting, compacting, archiving, or reclassifying the current approximately
  87 GB runtime evidence population.
- Applying a large-table index migration to the constrained runtime database.
- Semantic capture compression or changing the policy that persists valid
  provider updates.
- Weakening dependency, hold, trust, provenance, audit, or destructive-retention
  safeguards.
- Age-only deletion or disk-pressure emergency deletion.
- Increasing JVM heap as a replay solution.
- Changing cTrader configuration, switching LIVE to DEMO, or resuming cTrader
  runtime validation.
- Secret rotation or reading, printing, or logging secret values.
- Liquidity thresholds, liquidity policy activation, or current tradeability
  activation.
- Market Intelligence, TradePlan, Risk, Execution, Broker, Gateway, or frontend
  behavior changes.
- P0-C or any unrelated product work.
- Runtime restart, deployment, or destructive validation against the current
  constrained environment.

---

## Acceptance Criteria

- [ ] Replay selects exactly one valid reconstruction sequence and one trusted
      snapshot or checkpoint eligible at the requested cutoff.
- [ ] Replay applies only eligible evidence from that base through the cutoff,
      with database-side filtering and deterministic ordering.
- [ ] Replay does not materialize the complete historical evidence population
      merely to reconstruct one requested state.
- [ ] Replay has an explicit bounded streaming, pagination, or batch behavior;
      it fails closed when configured operational bounds cannot be respected.
- [ ] Snapshot/checkpoint base selection, cutoff exclusion, sequence boundaries,
      trust rejection, equal-timestamp ordering, empty results, missing bases,
      and multiple candidate bases are covered by tests.
- [ ] Kraken provider chronology, provider sequence, checksum/trust, and strict
      continuity semantics remain unchanged.
- [ ] cTrader local-observation replay, local cutoff semantics, and connection
      generation isolation remain unchanged.
- [ ] Representative old and new replay results are semantically equivalent for
      levels, ordering, quantities, cutoff, trust, provenance, and stable
      fingerprints where required by the contract.
- [ ] A representative large synthetic test demonstrates bounded retrieval and
      does not require gigabytes of test data or memory.
- [ ] Any new index or migration is the minimum justified by the final query,
      is structurally tested, and is not applied to the constrained runtime
      database during validation.
- [ ] A controlled retention coordinator invokes the existing preview and
      retirement services without duplicating candidate eligibility logic.
- [ ] Retention scheduling is disabled by default and destructive retirement
      remains independently disabled by default.
- [ ] Retention executions cannot overlap within a service instance.
- [ ] Retention uses bounded incremental batches and records auditable dry-run
      and destructive outcomes.
- [ ] Existing safeguards remain enforced: sealed or old invalidated sequence,
      released dependencies, released holds, at least one released dependency,
      trusted artifacts, dry-run confirmation, and destructive enablement.
- [ ] Disk pressure produces health/observability signals only and never bypasses
      domain retention eligibility.
- [ ] Retention and replay observability reports bounded counts, mode, duration,
      candidates, blocked candidates, retired rows, replay batches, and failures
      without logging individual evidence rows.
- [ ] Focused tests, relevant Market Data regression tests, module build, and
      `git diff --check` pass.
- [ ] The current runtime evidence population remains unchanged:
      `ROWS_DELETED_FROM_CURRENT_RUNTIME = 0`.
- [ ] No cTrader validation, broker mutation, intelligence, liquidity-policy,
      risk, or execution behavior changes occur.

---

## Constraints

- Preserve Market Data ownership of acquisition, evidence, replay, promotion,
  retention, and trust semantics.
- Treat canonical snapshots and deltas as authoritative evidence.
- Use the latest valid checkpoint or snapshot only when its sequence, trust,
  provenance, known-at boundary, normalization version, and source identity are
  verifiable.
- Never bridge sequences, connection generations, continuity gaps, or provider
  trust boundaries.
- Preserve `knownAt <= evaluationCutoff` and fail closed for incomplete,
  ambiguous, invalidated, untrusted, or unverifiable evidence.
- Preserve provider-specific chronology semantics; provider neutrality does not
  imply provider equivalence.
- Keep scheduler enablement and destructive retirement as separate gates.
- Do not introduce a generic job platform or distributed-lock dependency without
  repository evidence requiring it.
- Do not create speculative indexes or run dangerous `EXPLAIN ANALYZE` queries
  against the constrained runtime database.
- Do not modify pre-existing untracked runtime or network artifacts.
- Do not commit, push, merge, or create a pull request automatically before the
  implementation workflow and validation gates are complete.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-063.md`
- `docs/architecture/adr/ADR-065.md`
- `docs/architecture/adr/ADR-067.md`
- `docs/architecture/adr/ADR-069.md`
- `docs/architecture/adr/ADR-079-deterministic-liquidity-evidence-trust-and-policy-boundary.md`

---

## Relevant Modules

- `market-data`

---

## Validation

- Focused `OrderBookReplayService` and repository query/reconstruction tests.
- Tests for checkpoint/base selection, deterministic batching or streaming,
  cutoff handling, trust, sequence boundaries, and old/new result equivalence.
- Kraken replay regression tests.
- cTrader local-observation replay regression tests using isolated fixtures only.
- Retention service and persistence tests for all fail-closed safeguards.
- Retention coordinator tests for disabled defaults, independent destructive
  gating, bounded batches, overlap prevention, dry-run mode, and observability.
- Relevant Market Data module test suite and production build.
- Structural migration/index validation without applying large-table changes to
  the current runtime database.
- Plain `EXPLAIN` only where safe and useful; no dangerous full execution plans.
- `git diff --check`.
- No runtime restart, deployment, cTrader subscription, current-dataset cleanup,
  or broker mutation.

---

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Bounded replay implementation completed.
- [ ] Controlled retention execution path completed.
- [ ] Existing retention safeguards verified unchanged.
- [ ] Documentation reconciliation completed.
- [ ] Relevant validation executed and reported.
- [ ] Diff reviewed in IntelliJ.
- [ ] Code Review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created.

# Story 0144 - Close S1 Unattended Collection Gate

## Metadata

**ID:** `0144`

**Title:** Close S1 Unattended Collection Gate

**Status:** Approved

**Related Stories:** `0143`

**Related ADRs:** `ADR-067`, `ADR-068`, `ADR-069`

---

## Goal

Prove that the existing S1 research acquisition pipeline can remain running in
the background for multiple days without uncontrolled storage growth, silent
evidence loss, broken retention, replay failure on valid post-recovery evidence,
recurring persistence failure, or routine manual intervention.

This Story closes the operational safety gate for S1. It does not make S1
experimentally ready and does not produce or evaluate research results.

---

## Context

The S1 lifecycle is progressing: Order Book evidence, FeatureSnapshots, and
1h/4h/24h outcomes continue to advance. The previous operational investigation
classified the system as:

```text
S1_COLLECTION = DEGRADED
S1_EXPERIMENTAL_READINESS = NOT_READY
S1_CAN_RUN_UNATTENDED = NO
S1_BLOCKS_V1_DEVELOPMENT = NO
```

The primary remaining operational concern is that safe completed retention has
not been demonstrated against the current runtime evidence. Story 0143 added
bounded operational replay-artifact population, but its runtime validation and
retention lifecycle validation remain incomplete.

Repository and current runtime state are authoritative. Historical investigation
reports provide context only.

---

## Problem

The runtime contains a large and continuously growing Order Book evidence
population. A previous checkpoint observed a retention operation in
`DRY_RUN/SELECTED` state with no completion, while replay had previously timed
out against the large evidence table.

Without current evidence for safe preview, valid trusted replay, completed
retirement, idempotency, post-retirement replay, and sustained acquisition,
multi-day unattended collection cannot be approved.

---

## Scope

- Re-measure repository and runtime baselines before any retention action.
- Execute the supported retention preview/dry-run path first.
- Verify selected sequences are non-active, dependency-safe, replay-safe, and
  deterministically explainable.
- Inspect replay artifact, promotion, reuse, fallback, invalidation, and
  protected-dependency state.
- Perform representative trusted replay before retirement, including valid
  post-recovery evidence.
- Execute one small reviewed real retirement operation through the canonical
  retention lifecycle only when preview and replay validation are safe.
- Verify retirement completion, protected evidence, active-sequence continuity,
  and supported idempotency behavior.
- Repeat representative replay after retirement and verify semantic equivalence
  or the accepted artifact/fallback behavior.
- Verify FeatureSnapshot and ObservedMarketOutcome integrity after retirement.
- Measure acquisition continuity, storage behavior, replay latency, provider
  recovery, and recurring operational errors.
- Capture clean T0, T+2h, T+4h, and T+8h checkpoints when the execution
  environment can remain active for the full window.
- If the full window cannot be observed, provide exact non-destructive commands,
  queries, and acceptance thresholds and mark the long-window validation as
  pending.
- Produce the validation report:
  `docs/architecture/reports/s1-unattended-collection-gate-validation.md`.

---

## Out of Scope

- Improving S1 experimental readiness or increasing readiness percentages.
- Obtaining economic, profitability, win-rate, P&L, or strategy results.
- Changing signal thresholds, LONG/SHORT classifier semantics, or canonical
  regime semantics.
- Changing `FeatureProjectionV1`, FeatureSnapshot identity/provenance, cutoff
  semantics, research cadence, market scope, outcome horizons, or outcome
  semantics.
- Changing readiness thresholds, including signal, replay, fee, and duration
  gates.
- Adding markets or changing provider acquisition scope.
- Manually deleting, truncating, compacting, or rewriting research evidence.
- Executing raw SQL deletion or destructive database maintenance.
- Running `VACUUM FULL` merely to demonstrate filesystem reclamation.
- Introducing a new storage, cache, monitoring, or orchestration subsystem.
- Changing accepted replay trust, dependency, or retention semantics.
- Rewriting the S1 lifecycle tests without an actual defect.
- Implementing Trading OS V1 product work, including the manual PAPER journey,
  Decision History, analytics, AI, passive scanning, LIVE trading, cTrader, or
  FTMO integration.

---

## Acceptance Criteria

- [ ] Current branch, HEAD, `origin/main`, worktree, relevant commits, ADRs,
      Stories, implementation, endpoints, tests, and runtime baseline are
      recorded.
- [ ] Current acquisition, snapshot, outcome, artifact, dependency, retention,
      replay, and storage state is re-measured without mutating evidence.
- [ ] The supported retention preview completes and records eligible sequences,
      protected sequences, evidence counts, exclusion reasons, and expected
      logical reclamation where available.
- [ ] Every selected sequence is verified to be non-active, dependency-safe,
      replay-safe, and explainable before retirement.
- [ ] At least one valid post-recovery trusted replay completes through the
      canonical bounded path before retirement.
- [ ] Replay is verified to use one applicable sequence, bounded checkpoint or
      snapshot state, same-sequence deltas, and `knownAt <= cutoff` without
      look-ahead or full historical-table loading.
- [ ] A small real retirement operation is executed only through the canonical
      retention lifecycle after safe preview and replay validation.
- [ ] The operation reaches the repository-defined completed state, records its
      operation identity and affected/protected evidence, and is observable.
- [ ] Supported idempotent re-evaluation does not duplicate state, delete
      protected evidence, or corrupt artifact dependencies.
- [ ] Representative replay after retirement succeeds or uses the approved
      artifact/fallback path with semantic equivalence and fail-closed behavior.
- [ ] ACTIVE sequence evidence, protected dependencies, FeatureSnapshots, and
      ObservedMarketOutcomes remain intact and semantically unchanged.
- [ ] New Order Book evidence continues arriving during and after retirement;
      provider reconnects, if observed, create valid replacement sequences and
      resume acquisition automatically.
- [ ] Storage behavior is measured with logical and physical PostgreSQL
      semantics distinguished; no claim depends on immediate filesystem shrink.
- [ ] Recurring persistence, PostgreSQL, replay, retention, dependency,
      authentication, lifecycle, projection, and outcome errors are classified.
- [ ] Fee evidence remains explicit and no arbitrary fee schedule is introduced.
- [ ] Experimental readiness is reported separately and is not used as the
      collection-health acceptance gate.
- [ ] Focused affected tests pass where relevant; no expensive unrelated build
      is run merely for ceremony.
- [ ] The requested T+2h/T+4h/T+8h runtime window is either observed or marked
      `PENDING_RUNTIME_WINDOW` with exact follow-up commands and thresholds.
- [ ] The validation report records the collection, unattended, V1 critical-path,
      and Story verdicts using the required vocabulary.
- [ ] No unrelated behavior is changed.

---

## Safety Constraints

- Preserve active-sequence protection, dependency protection, fail-closed
  unknown state, explicit retirement, resumability, idempotency, and replay
  correctness.
- Do not retire evidence required by an active replay, frozen research fact,
  unresolved diagnostic, or retention hold.
- Do not use manual `DELETE`, `TRUNCATE`, `DROP`, or equivalent destructive SQL.
- Do not intentionally kill services, corrupt data, or disrupt networking to
  manufacture failure-recovery evidence.
- Do not restart healthy services without a concrete validation reason.
- Do not expose or inspect secrets unnecessarily.
- Do not change S1 semantics or readiness thresholds.
- If safe retention requires new domain semantics or an architectural change,
  stop and return `ARCHITECTURE_DECISION_REQUIRED`.
- If the execution environment cannot wait for eight hours, do not fabricate
  checkpoints or claim unattended approval.
- Do not commit, push, merge, or discard unrelated work automatically.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-067.md` - bounded Order Book evidence retention and replay promotion
- `docs/architecture/adr/ADR-068.md` - readiness projection and freshness architecture
- `docs/architecture/adr/ADR-069.md` - checkpoint-aware and promoted Order Book replay acceleration

---

## Relevant Modules

- `market-data`
- `market-intelligence`

---

## Validation

### Repository validation

- Record Git baseline and inspect current replay, retention, artifact, storage
  pressure, endpoint, and test implementations.
- Confirm whether runtime validation alone is sufficient before modifying code.

### Retention validation

- Preview eligible and protected sequences through the supported endpoint.
- Validate active, dependency, artifact, sequence, hold, and unknown-state
  predicates.
- Execute one small canonical retirement operation only after preview approval.
- Verify completion, operation audit state, affected rows, protected rows, and
  idempotency.

### Replay validation

- Validate a post-recovery replay before retirement.
- Validate bounded sequence/checkpoint/delta selection and no look-ahead.
- Validate replay after retirement through artifact or canonical fallback.
- Validate missing/invalid artifact and missing evidence remain fail-closed.

### Data and runtime validation

- Compare FeatureSnapshot and ObservedMarketOutcome counts, statuses, versions,
  fingerprints, and provenance before and after retirement.
- Compare evidence rows, newest `knownAt`, active sequence, storage headroom,
  table/index sizes, Docker footprint, and relevant logs.
- Capture T0/T+2h/T+4h/T+8h when possible.
- If the long window is unavailable, provide exact read-only follow-up commands
  and mark the window pending.

### Tests and build

- Run focused Market Data retention/replay tests and relevant Market Intelligence
  artifact/lifecycle tests if code changes or test evidence is needed.
- Run affected module tests after any code change.
- Run `git diff --check`.
- Do not run unrelated full builds solely for ceremony.

---

## Required Report Summary

The validation report must end with:

```text
S1 ACQUISITION:
<HEALTHY / DEGRADED / BROKEN / INCONCLUSIVE>

S1 SNAPSHOT LIFECYCLE:
<HEALTHY / DEGRADED / BROKEN / INCONCLUSIVE>

S1 OUTCOME LIFECYCLE:
<HEALTHY / DEGRADED / BROKEN / INCONCLUSIVE>

POST-RECOVERY TRUSTED REPLAY:
<HEALTHY / DEGRADED / BROKEN / INCONCLUSIVE>

RETENTION PREVIEW:
<SAFE / UNSAFE / INCONCLUSIVE>

REAL RETIREMENT:
<VALIDATED / FAILED / NOT_EXECUTED>

REPLAY AFTER RETIREMENT:
<VALIDATED / FAILED / INCONCLUSIVE>

STORAGE OUTLOOK:
<BOUNDED / LIKELY_BOUNDED / UNBOUNDED / INCONCLUSIVE>

PROVIDER RECOVERY:
<HEALTHY / DEGRADED / BROKEN / INCONCLUSIVE>

S1 EXPERIMENTAL READINESS:
<READY / NOT_READY / INCONCLUSIVE>

S1 COLLECTION:
<HEALTHY / HEALTHY_WITH_RESIDUALS / DEGRADED / BROKEN / INCONCLUSIVE>

S1 CAN RUN UNATTENDED:
<YES / YES_WITH_MONITORING / NO / INCONCLUSIVE>

S1 RELEASES V1 CRITICAL PATH:
<YES / NO / INCONCLUSIVE>

STORY VERDICT:
<VALIDATED / VALIDATED_WITH_RESIDUALS / SMALL_FIX_REQUIRED / OPERATIONAL_GATE_FAILED / ARCHITECTURE_DECISION_REQUIRED / PENDING_RUNTIME_WINDOW>

NEXT PRODUCT STORY:
<Validate MANUAL PAPER Daily Driver / NONE>

NEXT ACTION:
<one precise action>
```

---

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Retention preview and operational boundaries approved.
- [ ] Any bounded implementation fix is completed within the approved scope.
- [ ] Safe canonical retirement and post-retirement replay are validated, or a
      precise blocking verdict is recorded.
- [ ] Required runtime checkpoints are observed or explicitly marked pending.
- [ ] Validation report completed.
- [ ] Code Review approved when code changed.
- [ ] Engineering Report completed when code changed.
- [ ] Human commit created.

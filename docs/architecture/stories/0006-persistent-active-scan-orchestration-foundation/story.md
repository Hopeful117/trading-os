# Story 0006 — Persistent Active Scan Orchestration Foundation

## Metadata

**ID:** `0006`
**Title:** Persistent Active Scan Orchestration Foundation
**Status:** Draft

## Goal

Introduce the smallest durable `ActiveScan` orchestration slice required to
turn one validated Story 0005 `EffectiveScanScope` into exactly one logical
single-market `AnalysisExecution(ACTIVE)` per eligible market, with durable
linkage, deterministic retry behavior and safe post-commit dispatch.

## Context

Story 0005 runtime-confirmed the deterministic Active Scanner pre-analysis
boundary:

`User intent`
+ `owned account`
+ `requested markets`
↓
`deterministic hard eligibility`
↓
`EffectiveScanScope`

ADR-033 now requires the next orchestration layer:

`EffectiveScanScope`
↓
`ActiveScan` persisted
↓
`N AnalysisExecution(ACTIVE)`
↓
`existing pipeline / provenance`

The repository already contains:

- persistent single-market `AnalysisExecution`;
- deterministic `ActiveScanScopeResolutionService`;
- active/passive analysis strategies;
- asynchronous local analysis dispatch;
- per-analysis `PipelineRun` provenance;
- downstream Observation, Opportunity, Trade Plan and Risk flows.

What does not yet exist is the durable multi-market orchestration concept above
`AnalysisExecution` required by ADR-033.

## Problem

Trading OS can now deterministically decide which markets are eligible for an
active scan, but it still cannot durably represent the scan itself or safely
orchestrate its eligible child analyses.

If the current implementation simply loops over eligible markets and calls
`AnalysisExecutionService.create(...)`, it cannot truthfully guarantee:

- one persisted `ActiveScan` per logical idempotent request;
- one logical child analysis per eligible market under retries;
- durable scan-to-child linkage before dispatch;
- recoverable behavior after process crash or partial dispatch.

Without this foundation, ADR-033 remains only partially implemented.

## Scope

This Story introduces the minimum persistent orchestration foundation in
`market-intelligence`.

Included:

- persist one `ActiveScan` aggregate for one logical scan request;
- persist immutable scan scope snapshot data derived from Story 0005;
- persist one `ActiveScanMarket` row per resolved candidate market;
- persist exclusion diagnostics for ineligible markets;
- map each eligible market to exactly one logical `AnalysisExecution(ACTIVE)`;
- add deterministic scan-level idempotency;
- add deterministic child idempotency derived from scan identity and market;
- enrich the existing analysis execution application seam so child execution can
  be registered durably before dispatch;
- dispatch child analyses only after durable scan/linkage commit;
- define exact Story 0006 scan and per-market orchestration states;
- expose the minimum API required to create a scan and inspect minimal durable
  state;
- add focused automated tests for orchestration, idempotency and transaction
  boundaries.

## Out of Scope

- aggregate child completion reconciliation;
- full `ActiveScan` lifecycle projection;
- `PARTIALLY_COMPLETED` scan semantics;
- scan cancellation fan-out;
- per-market result projection;
- opportunity/result aggregation;
- cross-market ranking;
- "best trade" claims;
- `OpportunityScore` normalization;
- AI interpretation or AI ranking;
- Trade Plan generation as part of scan creation;
- Risk Domain invocation;
- broker execution;
- Passive Scanner redesign;
- generic workflow/orchestration engine;
- scheduler or background recovery worker unless repository evidence later
  proves one is strictly required;
- watchlists;
- news service work;
- order-flow feature work beyond current analysis reuse;
- Quant/V2 research, backtesting, portfolio optimization or Strategy
  aggregates.

## Acceptance Criteria

- [ ] One logical idempotent scan request creates exactly one persisted
      `ActiveScan`.
- [ ] Scan idempotency is scoped by authenticated actor so one user's
      `Idempotency-Key` cannot collide with or expose another user's scan.
- [ ] Reusing the same scan `Idempotency-Key` with different logical request
      content is rejected with an explicit conflict.
- [ ] Story 0006 consumes the resolved Story 0005 scope contract and does not
      recompute eligibility from scratch after resolution.
- [ ] The persisted scan snapshot preserves requested markets, candidate
      markets, eligibility decisions and effective markets with deterministic
      ordering.
- [ ] Each excluded market is persisted with explicit exclusion diagnostics and
      no child `AnalysisExecution`.
- [ ] Each eligible market is linked to exactly one logical
      `AnalysisExecution(ACTIVE)`.
- [ ] Retrying the same scan request does not create duplicate scans.
- [ ] Retrying the same scan request does not create duplicate logical child
      analyses.
- [ ] `AnalysisExecution` remains a single-market aggregate.
- [ ] `PipelineRun` semantics remain unchanged and continue to be scoped to one
      `AnalysisExecution`.
- [ ] Child linkage is durably persisted before any child analysis is
      dispatched.
- [ ] The durable transition from eligible linked child to
      `DISPATCH_REQUESTED` is persisted in a dedicated post-commit transaction
      before dispatcher invocation.
- [ ] The post-commit durable claim is executed through a dedicated
      `@Transactional(propagation = REQUIRES_NEW)` application seam rather than
      an implicit write inside `afterCommit()`.
- [ ] Concurrent retries cannot both successfully claim the same child
      `AnalysisExecution` for dispatch handoff.
- [ ] Concurrent retries may re-invoke dispatcher handoff, but only one path
      can atomically advance the child from `REQUESTED`/`ACCEPTED` into actual
      processing.
- [ ] Dispatch failure after commit preserves persisted scan and linkage state.
- [ ] Dispatch handoff is truthful as `AT_LEAST_ONCE_IDEMPOTENT`, not
      exactly-once.
- [ ] Empty `EffectiveScanScope` persists a terminal zero-child no-work scan.
- [ ] No Risk Domain call is introduced.
- [ ] No Trade Plan is created during scan creation.
- [ ] No broker execution path is introduced.
- [ ] Passive scanner behavior remains unchanged.
- [ ] Relevant focused tests and the `market-intelligence` module suite pass.

## Constraints

- Preserve ADR-033 Active/Passive orchestration semantics.
- `ActiveScan` belongs above `AnalysisExecution`.
- `AnalysisExecution` remains independently owned and single-market.
- `PipelineRun` remains one-analysis pipeline provenance.
- Reuse Story 0005 `ActiveScanScopeResolutionService` as the deterministic scope
  authority.
- Do not duplicate Account, MarketState, Opportunity, Risk or Broker
  authority.
- Do not introduce AI as orchestration authority.
- The scan API must compare replayed requests against persisted immutable scan
  request identity rather than reusing a key blindly.
- Keep the slice limited to durable creation, durable linkage, idempotency and
  safe dispatch.
- Defer richer lifecycle/result concerns to Story 0007.
- Do not commit, push or merge automatically.

## Relevant ADRs

- `ADR-020` Market Intelligence Architecture
- `ADR-023` Capability Execution Model
- `ADR-025` Observation Model
- `ADR-026` Trading Opportunity Model
- `ADR-028` Risk Engine Architecture
- `ADR-030` Broker Service Architecture
- `ADR-032` Represent Trade Plan Entry Intent Explicitly
- `ADR-033` Active and Passive Market Intelligence Orchestration

## Relevant Modules

- `market-intelligence`
- `market-data`
- `trading-core`
- `gateway`

## Validation

Expected validation for this Story:

- targeted `market-intelligence` tests for `ActiveScan` creation,
  persistence, scope snapshot and child linkage;
- idempotency tests at scan level and child-analysis level;
- transaction/dispatch tests proving linkage exists before dispatch;
- retry tests after partial registration/dispatch attempts;
- empty-scope tests;
- ownership and API tests;
- regression tests proving no Risk, Trade Plan or broker behavior is invoked;
- full `market-intelligence` Maven test suite;
- focused existing Story 0005 and `AnalysisExecution` tests remain green.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved.
- [ ] Implementation completed.
- [ ] Relevant tests pass.
- [ ] Code review approved.
- [ ] Human commit created.

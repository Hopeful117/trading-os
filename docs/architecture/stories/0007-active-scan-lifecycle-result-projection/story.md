# Story 0007 — Persistent Active Scan Lifecycle Reconciliation & Result Projection

## Metadata

**ID:** `0007`  
**Title:** Persistent Active Scan Lifecycle Reconciliation & Result Projection  
**Status:** Draft

## Goal

Extend the persisted Active Scan foundation introduced by Story 0006 so that an
actor-owned `ActiveScan` becomes a truthful, pollable and trader-readable unit
of work.

After this Story, a trader who launches an Active Scan must be able to poll the
scan and understand:

- whether it is still running;
- how much work has resolved;
- which markets were excluded;
- which analyses succeeded;
- which analyses failed;
- which analyses completed without producing an opportunity;
- which opportunities were produced;
- when the scan has reached a terminal truthful state.

The trader must not need to inspect raw `AnalysisExecution` or
`PipelineRun` records manually to understand scan progress.

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

Story 0006 then introduced the durable orchestration foundation:

`EffectiveScanScope`  
↓  
`ActiveScan` persisted  
↓  
`ActiveScanMarket` persisted  
↓  
`AnalysisExecution(ACTIVE)` registered/reused  
↓  
durable linkage before dispatch  
↓  
after-commit dispatch

What remains missing is the trader-facing lifecycle and result layer required
to make this orchestration usable as part of a real short-session trading
workflow.

ADR-033 defines Active Scanner as intention-driven orchestration above
single-market analysis. Story 0007 must preserve that boundary while making the
scan observable and useful.

## Problem

The current Story 0006 implementation persists the scan and its child linkage
correctly, but the existing `GET /api/v1/intelligence/scans/{scanId}` response
still exposes mostly raw orchestration state:

- `ActiveScan.status` only distinguishes `READY_TO_DISPATCH`,
  `DISPATCH_REQUESTED` and `COMPLETED_NO_WORK`;
- no aggregate lifecycle reconciliation exists;
- no truthful scan progress is computed;
- no trader-readable per-market outcome model exists;
- no opportunity projection exists through current lineage;
- no distinction is exposed between:
  - analysis still running,
  - analysis failed,
  - analysis completed successfully but produced no signal,
  - analysis completed and produced an opportunity.

Without this layer, Active Scanner remains a durable backend mechanism rather
than a usable trading workflow primitive.

## Trader / User Value

This Story improves the trader workflow from:

`account -> scope -> persistent scan -> opaque orchestration`

to:

`account -> scope -> persistent scan -> polling -> progress -> per-market outcomes -> opportunities`

For a discretionary trader with limited session time, this matters because it:

- removes the need to inspect internal engineering objects;
- makes multi-market scans understandable while they are still in progress;
- reveals excluded markets and failure cases explicitly;
- shows where no opportunity was found without treating that as an error;
- surfaces produced opportunities through existing lineage;
- prepares the product for a short-session trader workflow without prematurely
  absorbing Trade Planning, Risk, Broker or challenge-progress concerns.

## Dependencies

- `ADR-033` — Active and Passive Market Intelligence Orchestration
- Story 0005 — Account-Aware Active Scan Scope Resolution
- Story 0006 — Persistent Active Scan Orchestration Foundation

## Scope

Included:

- extend `ActiveScan` lifecycle beyond Story 0006 foundation states;
- define forward-only scan lifecycle reconciliation semantics;
- reconcile scan lifecycle synchronously on actor-owned read;
- compute deterministic progress counts from persisted child truth;
- expose a trader-facing `ActiveScan` projection through the existing owned GET;
- project excluded markets, running analyses, failed analyses, no-signal
  analyses and opportunity-producing analyses distinctly;
- reconstruct opportunity lineage through existing persisted provenance rather
  than introducing a second opportunity authority;
- keep reconciliation idempotent for unchanged persisted child truth;
- preserve Story 0006 crash/retry-safe orchestration guarantees;
- add focused tests for lifecycle reconciliation, result projection, ownership
  and repeated polling.

## Out of Scope

- scan creation redesign;
- Story 0005 scope resolution changes;
- Story 0006 idempotency or dispatch redesign;
- cancellation command or cancellation fan-out;
- `CANCELLING` / `CANCELLED` scan states;
- scheduler, worker or event-driven reconciliation infrastructure;
- SSE or WebSocket delivery;
- global cross-market ranking;
- "best trade" claims;
- `OpportunityScore` normalization;
- AI interpretation or AI ranking;
- Trade Plan generation changes;
- Risk Domain changes;
- broker execution;
- position monitoring;
- challenge progress / prop-firm progress model;
- Passive Scanner redesign;
- distributed orchestration infrastructure;
- Quant / V2 research capabilities.

## Lifecycle Semantics

### Existing Story 0006 States

- `READY_TO_DISPATCH`
- `DISPATCH_REQUESTED`
- `COMPLETED_NO_WORK`

### Story 0007 Lifecycle Extension

Story 0007 extends the lifecycle with:

- `RUNNING`
- `PARTIALLY_COMPLETED`
- `COMPLETED`
- `FAILED`

### Scan State Meaning

- `COMPLETED_NO_WORK`
  - effective scope contained zero eligible markets;
  - terminal successful no-work outcome.
- `RUNNING`
  - at least one eligible child remains unresolved;
  - includes the period where some children may already have finished.
- `COMPLETED`
  - all eligible children resolved;
  - no failure-class child outcome remains;
  - at least one successful analytical outcome may or may not have produced an
    opportunity.
- `PARTIALLY_COMPLETED`
  - terminal;
  - all eligible children resolved;
  - at least one usable successful analytical outcome exists;
  - at least one child resolved into a failure-class outcome.
- `FAILED`
  - terminal;
  - all eligible children resolved;
  - no usable successful analytical outcome survived;
  - all eligible children resolved into failure-class outcomes.

### Important Distinction

`PARTIALLY_COMPLETED` does **not** mean "some children finished while others
are still running".

That situation remains:

- `RUNNING`

with partial projected results already visible.

## Reconciliation Semantics

Story 0007 uses synchronous read-side reconciliation.

Conceptually:

`GET /api/v1/intelligence/scans/{scanId}`  
↓  
ownership validation  
↓  
load persisted `ActiveScan`  
↓  
load persisted `ActiveScanMarket` rows  
↓  
load linked `AnalysisExecution` rows  
↓  
load required `PipelineRun` / `TradingOpportunity` lineage  
↓  
reconcile lifecycle from child truth  
↓  
persist forward-only scan status when needed  
↓  
return trader-facing projection

The persisted `ActiveScan.status` exists for cheap polling and clear aggregate
semantics, but it is not an independent source of truth. It must be reconciled
from persisted child truth.

Reconciliation must be:

- deterministic;
- forward-only;
- idempotent for unchanged child truth;
- read-safe;
- free of child creation or dispatch side effects.

## No-Signal Semantics

Story 0007 must represent:

`analysis completed successfully but produced no TradingOpportunity`

as a valid successful analytical outcome.

It must not be treated as failure.

The trader-facing result must distinguish:

- `ANALYSIS_FAILED`

from:

- `ANALYSIS_COMPLETED_NO_OPPORTUNITY`

without fabricating a fake opportunity.

## Result Projection Requirements

The trader-facing projection must expose:

- scan identity and ownership context;
- current truthful lifecycle status;
- deterministic timestamps;
- progress counts derived from resolved child truth;
- deterministic per-market ordering;
- excluded-market diagnostics;
- linked child `AnalysisExecution` identifiers where applicable;
- trader-readable per-market outcomes;
- projected opportunity data when current lineage produces one.

The projection must not simply mirror JPA entities or raw repository payloads.

## Opportunity Lineage Requirements

Story 0007 must retrieve opportunities through existing lineage.

Architectural preference:

- do not persist `opportunityId` on `ActiveScanMarket` if current persisted
  lineage already provides a truthful relation.

Expected lineage source:

`AnalysisExecution -> PipelineRun -> TradingOpportunity`

Story 0007 may add read-side query seams if needed to reconstruct this
efficiently, but it must not redefine ownership of `PipelineRun`,
`Observation` or `TradingOpportunity`.

## Security Requirements

Story 0007 must preserve Story 0006 trust boundaries:

`External client -> Gateway -> JWT -> trusted X-Actor-Id -> internal-only market-intelligence`

Requirements:

- scan GET remains actor-owned;
- another actor cannot read the scan or its projected results;
- any result enrichment on GET must preserve the same ownership boundary;
- no UUID secrecy assumptions;
- no reintroduction of direct host exposure for `market-intelligence`;
- no local Spring Security added to `market-intelligence` without a new
  architectural reason.

## Polling Model

Story 0007 uses HTTP polling.

The existing actor-owned GET becomes the primary polling endpoint and must be:

- safe to repeat;
- deterministic for unchanged persisted state;
- efficient enough for short dashboard polling;
- compatible with a future Angular polling UI.

This Story does not introduce SSE or WebSocket infrastructure.

## Acceptance Criteria

- [ ] Actor-owned `GET /api/v1/intelligence/scans/{scanId}` returns a
      trader-readable lifecycle/result projection rather than only minimal
      orchestration state.
- [ ] `COMPLETED_NO_WORK` remains the truthful terminal state for scans whose
      effective scope has zero eligible markets.
- [ ] A scan with at least one unresolved eligible child is projected as
      `RUNNING`.
- [ ] A scan whose eligible children all resolve successfully is projected as
      `COMPLETED`.
- [ ] A scan whose eligible children all resolve and include both usable
      successful outcomes and failure-class outcomes is projected as
      `PARTIALLY_COMPLETED`.
- [ ] A scan whose eligible children all resolve into failure-class outcomes
      and no usable successful result survives is projected as `FAILED`.
- [ ] Successful analysis with no produced opportunity is represented
      explicitly and is not classified as failure.
- [ ] Excluded markets remain visible with deterministic exclusion reasons and
      no child `AnalysisExecution`.
- [ ] Per-market projection preserves deterministic ordinal ordering from the
      persisted scan-market rows.
- [ ] Opportunity data is projected through existing lineage without changing
      `PipelineRun` or `TradingOpportunity` ownership.
- [ ] Reconciliation never creates a second `AnalysisExecution`.
- [ ] Reconciliation never dispatches child work.
- [ ] Reconciliation is forward-only and does not regress a terminal
      `ActiveScan`.
- [ ] Repeating GET for unchanged persisted child truth yields the same
      lifecycle classification and projection content.
- [ ] Another actor cannot read the scan.
- [ ] No cancellation API is introduced in this Story.
- [ ] No Risk Domain invocation is introduced.
- [ ] No broker execution path is introduced.
- [ ] Passive Scanner behavior remains unchanged.

## Non-Functional Requirements

- preserve Story 0005 deterministic scope authority;
- preserve Story 0006 idempotent durable orchestration foundation;
- avoid obvious polling-time N+1 query patterns;
- prefer derivation over persistence for progress/result data;
- keep read-side reconciliation simple enough to remain maintainable in a
  single-service deployment today while remaining extensible later;
- avoid premature distributed/evented infrastructure;
- preserve crash/retry truthfulness after service restart.

## Explicit Story 0008 Deferrals

This Story explicitly defers to a future Story 0008 candidate:

- actor-owned scan cancellation command;
- cancellation idempotency;
- child cancellation fan-out;
- cancellation races and recovery;
- terminal aggregate cancellation semantics.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved.
- [ ] Implementation completed.
- [ ] Relevant tests pass.
- [ ] Runtime benchmark evidence recorded.
- [ ] Human commit created.

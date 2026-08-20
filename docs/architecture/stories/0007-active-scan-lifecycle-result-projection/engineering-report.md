# Engineering Report — Story 0007

## Status

Implemented locally on branch
`story/0007-active-scan-lifecycle-result-projection`.

## Key Engineering Findings

### 1. Read-Side Reconciliation Was Sufficient

The approved synchronous read-side reconciliation model fit the repository
without requiring:

- scheduler infrastructure;
- domain events;
- Kafka;
- cancellation plumbing;
- a second persistence model.

The existing persisted truth was enough:

- `ActiveScan`
- `ActiveScanMarket`
- `AnalysisExecution`
- `ConsolidatedIntelligence`
- `PipelineRun`
- `TradingOpportunity`

That allowed Story 0007 to remain a narrow extension of Story 0006 rather than
becoming a broader orchestration redesign.

### 2. Aggregate Partial Completion Is Not Child Partial Completion

Repository reality confirmed the approved distinction:

- `AnalysisExecution.PARTIALLY_COMPLETED` is still an in-flight child execution
  condition for scan reconciliation purposes;
- terminal `ActiveScan.PARTIALLY_COMPLETED` only becomes truthful when every
  eligible child has resolved and the final aggregate mix contains both usable
  successes and failure-class outcomes.

Encoding that distinction explicitly prevented a misleading aggregate state.

### 3. No-Signal Is a Success, Not a Failure

The real downstream signal for "analysis succeeded but no opportunity exists"
comes from pipeline truth:

- `PipelineRunStatus.COMPLETED_NO_SIGNAL`

Treating that state as a success-class outcome was essential. Without it,
Story 0007 would incorrectly label analytically useful markets as failures and
would distort both aggregate lifecycle and trader-facing progress.

### 4. Existing Lineage Was Enough

Opportunity projection did not require a new persisted relation.

The repository already supported truthful lineage through:

- child `AnalysisExecution`
- pipeline run provenance
- versioned `TradingOpportunity`

Story 0007 therefore avoided storing `opportunityId` on `ActiveScanMarket`,
which would have duplicated truth and created another consistency burden.

### 5. Batch Reads Were Necessary

The old minimal scan GET would have become an obvious polling N+1 path if it
loaded executions, pipelines, and opportunities per market.

The narrow repository enrichments added in Story 0007 were therefore justified:

- batch execution lookup by ids;
- batch pipeline-run lookup by execution ids and pipeline version;
- batch exact opportunity lookup by versioned reference.

This stayed within repository conventions while keeping the polling endpoint
credible for repeated use.

### 6. Forward-Only Persistence Remained Small

Persisting the aggregate status on GET was still compatible with the plan,
provided the implementation enforced:

- no regression;
- no mutation once terminal;
- idempotent no-op for unchanged truth.

That made the aggregate cheap to poll while still preserving child truth as the
real source of lifecycle meaning.

### 7. Story 0006 Recovery Windows Were Preserved

Story 0007 correctly keeps Story 0006 pre-run states visible when child truth
has not yet advanced:

- `READY_TO_DISPATCH`
- `DISPATCH_REQUESTED`

This avoids fabricating `RUNNING` simply because a GET happened after create.

### 8. Runtime E2E Proof Is Environment-Limited, Not Story-Limited

Automated tests fully validated the Story 0007 code path locally, but the full
Gateway runtime benchmark remains partially blocked in this workstation
environment because:

- `trading-app` was not running;
- starting it through normal Docker Compose failed on missing local Maven
  artifact `com.hope.trading:risk-domain:jar:0.0.1-SNAPSHOT`.

That is a runtime composition issue outside the scope of Story 0007
implementation itself.

## Why This Design Remains Clean

Story 0007 improves trader usability without absorbing later concerns:

- no cancellation state machine;
- no generic orchestration framework;
- no ranking layer;
- no Risk Domain coupling;
- no Broker coupling;
- no Passive Scanner coupling;
- no AI reinterpretation layer.

The result is a coherent bridge between:

- Story 0006 durable orchestration

and

- future trader workflow steps that need readable scan output.

## Story 0008 Boundary

Story 0007 intentionally leaves cancellation for the next story boundary.

Natural follow-up:

- actor-owned cancel command;
- cancellation idempotency;
- child fan-out;
- cancellation crash recovery;
- aggregate cancelled state semantics.

The current implementation does not preclude that next step.

## Unexpected Repository Findings

- `AnalysisExecution.COMPLETED` alone is not enough to classify a successful
  scan market outcome; downstream pipeline truth is still required.
- missing expected lineage after a completed execution is best surfaced as a
  bounded failure diagnostic rather than silently downgraded to no-opportunity.
- the runtime Docker build path for `trading-app` is currently more fragile
  than the `market-intelligence` module path because of the missing local
  `risk-domain` artifact.

## Overall Verdict

Story 0007 achieved the intended engineering outcome:

- `ActiveScan` is now a truthful, observable aggregate;
- lifecycle is derived from authoritative child truth;
- result projection is trader-readable;
- no-signal remains a valid success;
- opportunity lineage is reused rather than duplicated;
- polling remains deterministic and reasonably efficient;
- Story 0005, Story 0006, and ADR-033 boundaries remain intact.

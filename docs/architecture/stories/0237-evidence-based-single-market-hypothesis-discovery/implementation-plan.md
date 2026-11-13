# Implementation Plan - Story 0237

## Status

Prepared - waiting for explicit human approval before implementation.

This plan is derived from the approved Story and Repository Analysis. Approval
authorizes implementation of this plan only; it does not authorize LIVE broker
operations, strategy activation, account synchronization or delivery actions.

## Implementation Strategy

Implement the smallest additive vertical slice in the existing Market
Intelligence research workflow:

1. Add an objective-aware request contract while preserving comparative default
   behavior.
2. Persist the temporal research scope and objective type as part of the
   durable, idempotent execution.
3. Build a cutoff-consistent single-market context from existing historical
   evidence/readiness ports.
4. Add a strict, versioned `ResearchHypothesis` proposal projection and
   validation boundary.
5. Extend the Java/Python AI contract with a distinct discovery mode without
   weakening the existing thesis/adaptive contract.
6. Expose the result through the existing REST and MCP research surfaces.
7. Validate comparative regressions, single-market behavior, evidence safety,
   AI schema behavior, persistence and MCP compatibility.

No new service or generic workflow framework is introduced.

## Design Decisions For Implementation

### Objective and temporal scope

Add a Java enum with exactly:

- `COMPARATIVE_MARKET_RESEARCH`;
- `SINGLE_MARKET_HYPOTHESIS_DISCOVERY`.

The external request omits the objective type by defaulting to the existing
comparative value. The single-market request carries a typed temporal scope:

- existing `OhlcInterval` value for timeframe;
- existing `OutcomeHorizon` duration for measurable horizon;
- inclusive UTC `cutoffStart` and exclusive UTC `cutoffEnd`, represented as a
  bounded historical window.

Comparative requests continue to use their existing behavior. Temporal fields
are required for discovery and are included in validation and fingerprinting.
Provider/product identity remains resolved from the canonical market. Optional
request constraints are compared with the resolved identity and never trusted as
authority.

### Execution persistence

Extend `research_executions` with the objective and temporal-scope fields needed
to reconstruct the request after enqueue. Use a new Flyway migration after the
current latest migration. Existing rows receive the comparative default and
null temporal scope, preserving historical executions.

Update the domain object, JPA entity, repository mapper and test in-memory
repository together. The execution fingerprint will include normalized:

- objective type;
- sorted/normalized market IDs according to the existing contract;
- research scope and actor;
- objective/question;
- depth;
- timeframe, horizon and cutoff window;
- request contract version.

The current comparative effective inputs remain semantically unchanged apart
from the deterministic default objective token.

### Historical context

Introduce a small application port/assembler for single-market discovery that
uses existing historical contracts rather than current `MarketDataClient`
analysis:

- `HistoricalOhlcClient` and existing historical context contributors for
  cutoff-bound OHLC context;
- `FeatureSnapshotRepository` for versioned deterministic features where the
  requested population is available;
- `ObservedMarketOutcomeRepository` only for already observed, cutoff-eligible
  outcome references;
- existing artifact/provenance contracts for fingerprints and source metadata.

The assembler returns a typed context with market identity, temporal scope,
schema version, quality, diagnostics, evidence references and fingerprint. It
must fail closed to `INSUFFICIENT_EVIDENCE` when the required population or
cutoff-consistent evidence is unavailable. It must not call the current/live
research path as a fallback.

The implementation will not create new raw OHLC storage. It will reference
existing bounded evidence and artifact projections. Existing readiness/preflight
logic is reused where it provides the required strict-observed guarantees; it is
not treated as proof of a complete hypothesis context without checking the
requested cutoff and horizon.

### ResearchHypothesis persistence

Create a dedicated immutable `research_hypotheses` persistence projection rather
than overloading the execution JSON report. This is required for versioned
proposals and prevents report replacement from erasing proposal history.

The table and domain object will contain:

- proposal ID and version;
- parent research execution ID;
- canonical market identity;
- objective and temporal scope;
- observed behavior, relationship and measurable expected outcome;
- applicability and falsification conditions;
- supporting, contradicting and missing evidence references;
- limitations and qualitative uncertainty;
- generator/provenance metadata;
- context fingerprint;
- validation status;
- immutable timestamps.

The repository will enforce uniqueness for `(research_id, hypothesis_id,
version)` and will reject replacement of an existing version. The report
projection will contain bounded proposal summaries and outcome status while
preserving the existing comparative fields.

### AI contract

Add `HYPOTHESIS_DISCOVERY` to the Java and Python phase/mode contracts, but do
not make `MarketThesisInput` optional for the existing interpretation modes.

Implement a separate strict discovery request/response variant behind the same
provider-independent `AiEnginePort` and HTTP adapter. The discovery request
will contain the authorized historical context, bounded objective/question,
temporal scope, execution policy and trace. It will not contain a predefined
strategy, direction or target hypothesis.

The Python response will use strict models for zero or more proposals with
bounded text and evidence references. Java will own proposal identity,
execution/context fingerprints, canonical market identity, provenance and
validation status. Unknown fields, unsupported evidence references and temporal
violations are rejected.

The existing thesis/adaptive request models, research modes and response
behavior remain unchanged for comparative research.

### Outcome handling

Add report-level outcome values without replacing the existing execution
statuses:

- `HYPOTHESIS_PROPOSED`;
- `NO_HYPOTHESIS`;
- `INCONCLUSIVE`;
- `INSUFFICIENT_EVIDENCE`;
- `AI_UNAVAILABLE`.

No proposal entity is written for an explicit no-hypothesis or insufficient
evidence result. A deterministic-only candidate is labeled
`DETERMINISTIC_ONLY`, never AI-discovered.

## Ordered File-Level Work

### 1. Domain and request contracts

Add or update:

- `ResearchObjectiveType`;
- `ResearchTemporalScope`;
- `ResearchContext` and evidence-reference value objects;
- `ResearchHypothesis`, proposal status and research outcome types;
- `ResearchExecutionRequest`;
- `ResearchApplicationService.ResearchRequest`;
- `ResearchExecution` and `ResearchReport`.

Preserve constructor compatibility where existing tests and persisted
comparative behavior require it. New constructors/factories must default only
the comparative objective, not silently invent single-market temporal data.

### 2. Validation and orchestration

Modify `ResearchApplicationService` to:

- validate objective-specific cardinality;
- validate temporal scope only for discovery;
- include all material fields in the fingerprint;
- persist and reconstruct the complete request;
- route comparative requests through the existing path unchanged;
- route discovery requests through the historical context assembler;
- stop before AI on insufficient context;
- validate and persist returned proposals;
- checkpoint bounded report/outcome state through the existing lifecycle.

Keep actor, scope, idempotency, authorization provenance and worker race
handling unchanged.

### 3. Persistence and migration

Update:

- `JpaResearchExecutionEntity`;
- `JpaResearchExecutionRepository`;
- `InMemoryResearchExecutionRepository` test adapter;
- new `JpaResearchHypothesisEntity` and Spring Data repository;
- new domain repository port and adapter;
- new Flyway migration after `V26` for execution fields and the hypothesis
  table/indexes.

Migration defaults must preserve all existing comparative executions. Existing
JSON reports remain readable. The hypothesis table must not expose raw prompt,
credential or provider-secret data.

### 4. Historical context adapter

Add an application service/port using existing `HistoricalOhlcClient`, feature
snapshots, observed outcomes and artifact provenance. Add focused tests for:

- complete cutoff-consistent context;
- missing/stale/contradictory evidence;
- future/post-cutoff evidence rejection;
- deterministic fingerprint stability;
- no current-quote fallback.

### 5. AI Java and Python contracts

Update Java:

- `AiInterpretationPhase` or the equivalent discovery mode contract;
- `AiAnalysisCommand`/adapter request variants;
- `HttpAiEngineAdapter`;
- disabled adapter behavior;
- strict response validation and mapping.

Update Python:

- strict discovery request/response models;
- mode dispatch and output validation;
- tests for malformed fields, missing proposal fields, evidence references,
  temporal consistency and bounded output.

Do not expose a direct AI Engine endpoint to MCP or frontend clients.

### 6. REST and MCP

Update:

- `MarketIntelligenceController` request mapping and response projection;
- `ResearchExecutionResponse` and report serialization;
- `mcp-adapter/app/server.py::start_market_research`;
- `mcp-adapter/tests/test_server.py`;
- `mcp-adapter/README.md` only where the existing research tool contract is
  documented.

The MCP tool will default to comparative behavior, allow one market only for
the discovery objective, require its temporal fields for discovery and retain
the existing idempotency key. It will continue using the public Gateway route.

The server file already contains unrelated active-scan modifications. Apply a
narrow change only to the research function and preserve the scan diff exactly.

### 7. Optional frontend read projection

Do not add a new page in this Story. If the existing research service already
has a suitable read model, add only typed display fields for objective,
outcome, proposals and limitations using Observables/async pipe. Otherwise
defer frontend presentation to a follow-up and validate REST/MCP retrieval.

## Test Sequence

1. Run existing Market Intelligence research tests before changes where
   practical to establish the baseline against the dirty worktree.
2. Add and run domain/request/fingerprint tests.
3. Add and run historical context and proposal validation tests.
4. Add and run persistence migration/repository tests.
5. Add and run Java AI adapter contract tests.
6. Run AI Engine tests through `ai-engine/.venv/bin/python -m pytest` if that
   environment is available; install the declared test extra if required.
7. Run MCP tests through `mcp-adapter/.venv/bin/python -m pytest`.
8. Run targeted Java validation:

   ```bash
   (cd market-intelligence && ../trading-core/mvnw test)
   ```

9. Run broader repository validation if shared contract changes require it:

   ```bash
   ./scripts/test-all.sh
   ./scripts/quality-verify.sh
   ```

10. Run `git diff --check` and review the complete diff while excluding
    unrelated pre-existing changes from the implementation report.

No cTrader, broker, account, LIVE scan or execution validation is part of this
plan.

## Documentation Reconciliation

After implementation, inspect and update only documentation affected by the
new public research contract:

- `mcp-adapter/README.md` if MCP parameter semantics change;
- relevant API/architecture documentation if a canonical contract document
  exists;
- the Story implementation report with exact validation and outcome evidence.

No broad README, ADR or historical report rewrite is planned.

Vault outcome for this Story is currently expected to be `no vault action`:
the repository ADR, Story and source code fully define the implementation
context, and no curated vault note was consulted. This remains proposal-only
and does not authorize vault writes.

## Expected Deliverables

- Java request/domain/orchestration changes.
- Java persistence migration and hypothesis repository.
- Java/Python strict AI discovery contract changes.
- REST/MCP compatibility changes and tests.
- Targeted implementation tests and validation evidence.
- `implementation-report.md` recording documentation reconciliation, tests,
  unresolved risks and the vault outcome.

No commit, push, merge, pull request or broker operation is included.

## Plan Approval Gate

Human approval is required for this Implementation Plan before any
implementation file is modified. If approved, implementation begins with the
domain/request and persistence contract changes, followed by historical
context, AI contract, REST/MCP integration and validation.

# Repository Analysis - Story 0223

## Status

Completed - human approval required before Implementation Planning.

No production code, migration, runtime database, credential, broker, commit,
push or merge operation was performed during this analysis. Existing unrelated
worktree changes were preserved.

## Repository Baseline

- Repository: `/home/ludo/workspace/trading-os`
- Branch: `main`
- Working tree: already contains unrelated modifications and untracked
  reports/stories; none were reverted or included in this Story analysis.
- Current Story: `0223-provider-scoped-historical-evidence-retirement`.
- Accepted architecture decision: `ADR-091`, with implementation acceptance
  limited to domain/preflight behavior and no physical database mutation.
- No usable DevLog MCP capability or repository mapping is available in this
  execution context. The repository and accepted ADRs are the authoritative
  sources for current implementation. Obsidian was not consulted because the
  required context is repository-local.

## Context Sources

Read and reconciled:

- repository `AGENTS.md` and engineering workflow instructions;
- Story 0223;
- ADR-057, ADR-060, ADR-062, ADR-067, ADR-069, ADR-079 and ADR-080;
- accepted ADR-091;
- the existing Kraken order-book evidence, replay and retention services;
- current evidence, artifact, dependency, hold, segment and operation models;
- existing Market Data retention controller and tests;
- existing Market Intelligence artifact/dependency ports and Market Data Feign
  client;
- existing Flyway migrations relevant to order-book evidence and retention.

## Current Market Data Boundary

Market Data owns canonical order-book persistence, sequence lifecycle,
reconstruction, replay promotion and ordinary retention. The current capture
path is `OrderBookEvidenceCaptureService`, which accepts provider and local
observations, maintains in-memory active sequences and delegates persistence to
`OrderBookEvidenceRepository`.

`OrderBookEvidenceRepository` currently:

- validates canonical evidence fingerprints and normalization version;
- persists evidence into `historical_order_book_evidence`;
- creates/seals/invalidates sequences;
- opens and closes retention segments;
- exposes trusted replay-base and bounded evidence streaming queries;
- has no provider-scope retirement guard.

The capture key is currently market ID plus depth. Provider and provider symbol
are carried by the evidence row but are not part of the in-memory sequence map.
The Story therefore requires the retirement guard to be evaluated against the
fully persisted provider/symbol/depth scope at the persistence boundary, while
preserving existing live acquisition and sequence behavior for other scopes.

`OrderBookReplayService` first resolves a trusted replay base, then streams
trusted evidence through a bounded accumulator. Artifact reuse is currently
accepted when the artifact fingerprint and current protected dependency checks
pass. There is no `SOURCE_RETIRED` state in the current replay result or
artifact lookup contract, so source-retirement behavior must be additive and
fail closed without treating a retired artifact as `AVAILABLE`.

`OrderBookRetentionService` currently supports ordinary artifact dependency
protection/release, retention holds, dry-run preview and bounded logical
retirement. Its existing `RELEASED` dependency state is intentionally not a
replacement for `SOURCE_RETIRED`. The exceptional workflow needs a separate
operation model and explicit dependency transition rather than reusing ordinary
retention eligibility.

The existing `InternalOrderBookRetentionController` exposes guarded internal
retention operations. It is an existing boundary, not evidence that a new
physical deletion endpoint is authorized. The Story must expose preparation and
readiness only, with no endpoint that performs physical truncation or deletion.

## Current Market Intelligence Boundary

Market Intelligence owns artifact/dependency lifecycle for derived research and
uses `OrderBookRetentionClient` to protect and release Market Data replay
dependencies. The current client has `protect`, ordinary `release`, artifact
promotion and reusable-artifact calls. It has no source-retirement
acknowledgement, inventory fingerprint or reconciliation contract.

`ArtifactDependencyRegistry` is an in-memory/application port abstraction for
artifact dependency relationships and does not represent the durable Market
Data retention dependency table. The implementation must not pretend that this
registry alone is sufficient for cross-database retirement acknowledgement.

The minimal boundary is an additive provider-neutral client/application contract
that carries operation identity, exact scope and dependency inventory
fingerprint. Market Intelligence must durably mark its reviewed dependencies as
`SOURCE_RETIRED` before Market Data commits source retirement. The two services
must expose reconciliation-safe reads and idempotent acknowledgement behavior;
no distributed transaction coordinator is justified by current repository
evidence.

## Persistence And Migration Findings

Existing Market Data migrations include:

- `V6__historical_order_book_evidence.sql` for raw evidence;
- `V11__order_book_retention_lifecycle.sql` for artifacts, dependencies, holds
  and retention operations;
- `V12__order_book_replay_artifact_provenance.sql` for provenance;
- `V13__order_book_retirement_progress.sql` for retirement progress;
- `V23__order_book_evidence_segments.sql` for segment metadata.

There is no existing source-retirement operation, immutable transition audit,
exact provider-scope authorization record, or retired-source status. New
schema changes must therefore be additive migrations. Existing migrations must
not be edited in place, and no migration may execute physical cleanup against
the current runtime relation.

The implementation needs separate durable records for operation identity and
transition audit, or a demonstrably equivalent append-only model. It must store
the exact scope, approval reference, fingerprints, dependency acknowledgement,
operational state and reconciliation outcome. Physical database measurements
and PostgreSQL action details belong in the operational adapter/runbook rather
than the provider-neutral domain model.

## Required Implementation Surfaces

### Market Data

- domain records/enums for exact retirement scope, business state, operational
  state, blockers and audit transitions;
- additive persistence and repository queries for operation/preflight/audit;
- exact-scope preflight against evidence, sequences, artifacts, dependencies,
  holds and dependent database objects;
- idempotent state transition service with fail-closed validation;
- source-retirement acknowledgement boundary for Market Intelligence;
- replay/artifact availability guard for `SOURCE_RETIRED`;
- capture persistence guard for retired provider/symbol/depth;
- internal read-only preparation/readiness endpoints if required by existing
  controller conventions;
- focused unit, repository and controller tests.

### Market Intelligence

- durable or existing persistence-backed source-retired dependency transition;
- additive Market Data client contract for acknowledgement and reconciliation;
- idempotent handling of timeout, stale fingerprint, partial acknowledgement and
  contradictory state;
- artifact resolver/replay consumer behavior that exposes source retirement;
- focused application, client and integration tests.

### Documentation And Operations

- update only canonical operational documentation required for the new
  preparation/readiness workflow;
- document backup/restore evidence, quiescence, exact `TRUNCATE TABLE ONLY`
  safeguards, post-operation validation and ambiguous-operation recovery;
- do not document physical execution as completed by this implementation.

## Architectural Risks

1. **Cross-database partial commit:** Market Data and Market Intelligence
   cannot atomically transition together. Operation identity, fingerprints,
   monotonic transitions and reconciliation are mandatory.
2. **Capture race:** a source-retirement transition can race with an incoming
   provider update. The persistence guard must be authoritative at insert time
   and must fail closed for a retired scope.
3. **Artifact false availability:** existing signed artifacts can pass current
   fingerprint checks after raw evidence is retired. Resolver queries must
   distinguish integrity from source reproducibility.
4. **Scope ambiguity:** market ID alone is insufficient because provider and
   provider symbol are part of the approved scope. Any mixed or unverifiable
   row must reject preparation.
5. **Existing retention coupling:** ordinary `RELEASED` and segment retirement
   must not be silently reused for exceptional source retirement.
6. **Unsafe physical leakage:** provider-neutral application contracts must not
   expose a generic destructive endpoint or allow a client to request database
   mutation.

## Proposed Minimal Implementation Sequence

1. Add additive domain/persistence types and migration for retirement operation,
   exact scope, audit transitions and source-retired dependency semantics.
2. Implement deterministic Market Data preflight and idempotent state machine,
   including dependency acknowledgement and reconciliation reads.
3. Add capture-boundary guard and replay/artifact fail-closed behavior.
4. Implement the Market Intelligence acknowledgement/reconciliation adapter
   without changing ordinary dependency release semantics.
5. Add focused tests for mixed scopes, idempotency, stale fingerprints,
   cross-service failure, replay availability, capture preservation and live
   unrelated scopes.
6. Reconcile the operational runbook and produce the implementation report.

This sequence is a planning input only. Implementation Planning remains gated
on explicit human approval of this Repository Analysis.

## Validation Boundary

Applicable validation is expected to include:

- focused Market Data unit, repository and controller tests;
- focused Market Intelligence application/client tests;
- additive migration/schema validation using isolated test infrastructure;
- no physical mutation or runtime cleanup;
- `git diff --check`;
- relevant module builds and test suites after implementation.

No production database, broker, live provider, deployment, restart, commit or
push is part of Repository Analysis or the approved implementation boundary.

## Conclusion

The repository has reusable evidence, replay, artifact and ordinary retention
foundations, but no source-retirement lifecycle. The Story is implementable as
an additive, provider-neutral domain/preflight capability across Market Data and
Market Intelligence. The principal design constraint is explicit
cross-database reconciliation: physical retirement must remain a later,
separately authorized operational action and cannot be hidden behind ordinary
retention or replay behavior.

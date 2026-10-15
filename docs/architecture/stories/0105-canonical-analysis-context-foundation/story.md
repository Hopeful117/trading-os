# Story 0105 - Canonical Temporal Analysis Context Foundation

## Metadata

- **ID:** `0105`
- **Title:** Canonical Temporal Analysis Context Foundation
- **Status:** IMPLEMENTATION COMPLETE - HUMAN REVIEW PENDING
- **Related Stories:** `0062`, `0063`, `0071`, `0073`, `0098`, `0104`
- **Related ADRs:** `ADR-020`, `ADR-021`, `ADR-022`, `ADR-025`, `ADR-031`, `ADR-033`, `ADR-048`

## Goal

Establish a canonical, immutable and provider-neutral Analysis Context contract
for Market Intelligence.

The context must be temporally correct, reproducible and explicit about source
provenance, freshness, completeness and unavailable sections. It must support
deterministic analysis today and provide a safe input boundary for future ML or
LLM capabilities without granting those capabilities business or execution
authority.

## Context

Market Intelligence already assembles market snapshots, historical OHLC and
news-related sections through contributors. Market Data and News Service expose
normalized provider-neutral facts with source and capture timestamps. Existing
Market Intelligence artifacts, observations, opportunities and Trade Plans also
retain portions of their input and provenance context.

The current platform needs one explicit context contract that can be used by
passive scans, active scans, deterministic capabilities, future ML/LLM
capabilities and reproducibility tooling without duplicating source facts or
moving ownership of market facts, risk, accounts or execution into Market
Intelligence.

The architecture must preserve the distinction between:

- when a fact occurred or became effective;
- when the platform observed or fetched it;
- when the analysis was evaluated;
- when a derived result is valid;
- whether a section is available, stale, incomplete, unsupported or unavailable.

## Problem

Analysis inputs are assembled from multiple services and data types with
different temporal and availability semantics. Without a canonical context
identity and explicit evidence boundaries, later consumers may combine facts
from incompatible cut-offs, use information that was not known at evaluation
time, lose provider/source references, or fail to reproduce a historical
analysis.

The absence of a stable contract also makes it difficult to distinguish source
facts from derived analysis, to expose degraded context safely, and to prepare a
bounded context for future AI capabilities.

## Scope

- Define an immutable provider-neutral Analysis Context domain contract in
  Market Intelligence.
- Define the context identity, market identity, analysis purpose/mode,
  evaluation cut-off and context version.
- Define section-level status and availability semantics for available, stale,
  incomplete, unsupported, unavailable and missing evidence.
- Preserve source provenance for every included section, including provider or
  source identity, occurrence/effective time, capture/fetch time and relevant
  normalization or contract versions.
- Preserve explicit temporal boundaries so data newer than the evaluation
  cut-off cannot be used as analysis input.
- Define deterministic ordering, bounded section sizes and immutable payload
  semantics suitable for serialization and hashing.
- Define a stable context fingerprint or equivalent identity for identical
  accepted inputs, versions and policies.
- Define the distinction between source facts, derived deterministic analysis,
  model output and human decisions.
- Define context disclosure rules for deterministic capabilities and future
  AI/LLM capabilities, including explicit exclusion of account, Risk and
  execution authority from the market context.
- Reuse the existing Market Data, News Service and Market Intelligence
  contracts where they already satisfy the required semantics.
- Add focused validation for temporal cut-offs, provenance retention, status
  semantics, boundedness, ordering, immutability and fingerprint determinism.
- Document the minimum context requirements and failure behavior for passive
  analysis, active scans and future capability consumers.

## Out Of Scope

- Implementing a real AI Engine, LLM, ML model, RAG pipeline, embeddings or
  vector store.
- Training datasets, labels, model evaluation, model serving or feature-store
  infrastructure.
- Introducing a new Market Data, News Service or provider integration.
- Moving market-fact normalization or provider-specific behavior into Market
  Intelligence.
- Replacing the existing Market Data or News Service ownership boundaries.
- Rebuilding the complete persistence model for all Market Intelligence
  artifacts or introducing distributed caching.
- Changing Risk rules, account context, position sizing, authorization,
  TradePlan decisions or execution state transitions.
- Allowing Market Intelligence, AI or ML capabilities to place broker orders or
  override deterministic Risk decisions.
- Adding a frontend context editor, scanner redesign or new public API unless
  required to expose and validate the established contract.
- Adding provider-specific fields to public or domain contracts.
- Retrospectively manufacturing missing provenance, timestamps or source facts.

## Architecture

```text
Market Data -----------+
                        |
News Service -----------+--> Market Intelligence
                        |       |
Existing observations --+       v
                              Canonical Analysis Context
                                |
                +---------------+----------------+
                |                                |
        Deterministic capabilities       Future ML/LLM capabilities
                |                                |
                +---------------+----------------+
                                v
                 Observations / Opportunities / Plans
```

Market Data remains authoritative for normalized market facts, timestamps,
market identity and market-data freshness. News Service remains authoritative
for normalized news and economic-event facts. Market Intelligence owns context
assembly, purpose-specific disclosure, capability orchestration and derived
analysis provenance.

The context is an input/evidence boundary. It is not an authoritative Risk
snapshot, account snapshot, broker state or execution command.

## Acceptance Criteria

### Canonical Contract

- [ ] A single immutable Analysis Context contract is defined for Market
      Intelligence consumers.
- [ ] The contract contains canonical market identity, analysis purpose/mode,
      evaluation cut-off, context version and context identity.
- [ ] The contract is provider-neutral and does not expose raw provider DTOs,
      provider credentials or provider-specific decision logic.
- [ ] Section payloads are bounded, deterministically ordered and safe to
      serialize without relying on mutable service state.

### Temporal Integrity

- [ ] Every included fact preserves the relevant occurrence/effective timestamp
      and capture/fetch timestamp when supplied by its source.
- [ ] Facts newer than the evaluation cut-off are rejected or excluded
      explicitly; they are never silently used.
- [ ] Context assembly uses one explicit evaluation boundary for all sections,
      with section-specific freshness rules where required.
- [ ] Late arrival, stale data, missing timestamps and conflicting temporal
      values produce explicit diagnostics or unavailable status.
- [ ] Repeated assembly from identical accepted source inputs and policy
      versions produces equivalent context content.

### Provenance And Availability

- [ ] Every section identifies its source contract/version and preserves the
      available source/provider identity and source references.
- [ ] `AVAILABLE`, `STALE`, `INCOMPLETE`, `UNSUPPORTED`, `UNAVAILABLE` and
      `MISSING` are distinct outcomes where applicable.
- [ ] Degraded sections cannot be presented as complete evidence.
- [ ] Missing or unavailable optional context does not manufacture neutral facts,
      timestamps, prices, events or analysis conclusions.
- [ ] Required context failures have explicit minimum-context behavior for each
      supported analysis mode.

### Reproducibility

- [ ] The context records the normalization, policy, profile or rule versions
      that materially affect its interpretation.
- [ ] Identical accepted inputs, ordering rules and versions produce the same
      context fingerprint or equivalent stable identity.
- [ ] A changed source fact, cut-off, policy, section status or relevant version
      changes the context identity when it changes the effective input.
- [ ] Derived observations and opportunities can reference the exact context
      identity used for their production.
- [ ] Reproduction does not require provider re-fetching or access to mutable
      current service state.

### Capability And AI Boundaries

- [ ] Deterministic capabilities consume the canonical context without taking
      ownership of source facts.
- [ ] Future ML/LLM capabilities receive an explicit, bounded disclosure of
      context sections and provenance rather than unrestricted service access.
- [ ] Source facts, deterministic derived values, model outputs and human
      decisions remain distinguishable in the contract and downstream records.
- [ ] The context does not include authoritative account, Risk, authorization,
      broker or execution state.
- [ ] No consumer can use the context contract to bypass deterministic Risk or
      human execution approval.

### Validation

- [ ] Focused Market Intelligence tests cover temporal cut-offs, future facts,
      stale/incomplete/unavailable sections, provenance retention and bounded
      ordering.
- [ ] Fingerprint tests cover deterministic equality and meaningful input
      changes.
- [ ] Mapping tests prove Market Data and News Service source metadata is not
      silently discarded.
- [ ] Regression tests prove existing passive scans, active scans, deterministic
      capabilities and observation creation remain compatible.
- [ ] Relevant Market Data and News Service contract tests pass when their
      contracts are extended.
- [ ] `git diff --check` passes.

## Constraints

- Market Intelligence remains the business orchestrator for analysis context;
  it must not become the authority for normalized market facts.
- Deterministic business, financial and Risk decisions remain deterministic.
- Provider-specific payloads, symbols, transport details and errors stay inside
  infrastructure adapters.
- Temporal correctness and no-lookahead behavior take precedence over context
  completeness.
- Unknown, stale or unavailable information must remain explicit and must not be
  converted into a neutral or positive conclusion.
- Context payloads must be bounded for synchronous analysis and future model
  disclosure.
- Existing service boundaries, security controls and authentication flows must
  be preserved.
- Existing unrelated worktree changes must remain untouched.
- No new persistence or service boundary is introduced without a separate
  approved architectural decision when the existing boundaries are insufficient.
- No commit, push, merge or history rewrite is performed automatically.

## Relevant ADRs

- `docs/architecture/adr/ADR-020.md` - Market Intelligence architecture and
  context orchestration.
- `docs/architecture/adr/ADR-021.md` - AI execution governance and bounded
  disclosure.
- `docs/architecture/adr/ADR-022.md` - Artifact freshness, reuse and
  invalidation.
- `docs/architecture/adr/ADR-025.md` - Immutable observation and provenance
  model.
- `docs/architecture/adr/ADR-031.md` - Separation between market context and
  authoritative Risk/account context.
- `docs/architecture/adr/ADR-033.md` - Active/passive orchestration and scanner
  boundaries.
- `docs/architecture/adr/ADR-048.md` - Evidence authority, provenance,
  immutability and temporal integrity.

## Relevant Modules

- `market-intelligence`
- `market-data`
- `news-service`

## Expected Validation

- Focused Market Intelligence domain, context-assembly, adapter and capability
  tests.
- Focused Market Data and News Service contract tests if their response
  metadata contracts are extended.
- Market Intelligence architecture tests and relevant passive/active scan
  regression tests.
- Module-level Maven validation for affected services.
- `git diff --check`.
- Manual or integration verification of reproducible context assembly using
  fixed source fixtures, including unavailable and stale sections.

## Definition Of Done

- [ ] Story scope approved by the human engineer.
- [ ] Repository Analysis completed and approved.
- [ ] Implementation Plan completed and approved when required.
- [x] Canonical Analysis Context contract implemented within this Story
      boundary.
- [x] Temporal, provenance, availability, boundedness and fingerprint behavior
      covered by tests.
- [x] Existing Market Intelligence consumers remain compatible or are migrated
      within scope.
- [x] Documentation reconciliation completed.
- [x] Independent code review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created.

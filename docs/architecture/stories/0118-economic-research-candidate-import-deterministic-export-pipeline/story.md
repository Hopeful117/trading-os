# Story 0118 - Economic Research Candidate Import and Deterministic Export Pipeline

## Metadata

**ID:** `0118`

**Title:** Economic Research Candidate Import and Deterministic Export Pipeline

**Status:** Draft

---

## Goal

Operationalize the provider-neutral economic research contract established by
Story 0117.

Provide a bounded deterministic bridge from provider-neutral candidates produced
by Python or Jupyter to authoritative Java historical economic evaluation and
back to deterministic economic research artifacts.

```text
FeatureSnapshot reference
        -> candidate import and validation
        -> exact FeatureSnapshot resolution
        -> historical MARKET entry/exit replay
        -> EconomicResult
        -> deterministic result export and manifest
```

This is research infrastructure, not a backtesting engine, strategy engine, ML
pipeline, or trading signal.

---

## Context

Story 0117 introduced the provider-neutral economic contract in Trading Core:

- `EconomicResearchDatasetDefinition`;
- `EconomicResearchCandidate`;
- `EconomicResearchResult`;
- `EconomicResearchDatasetManifest`;
- hypothesis, sizing, exit, identity, and canonical serialization contracts.

The current repository already contains the authoritative Java foundations:

- immutable `FeatureSnapshot` and exact semantic identity;
- strict historical Order Book replay;
- `StrategyIntent`;
- `SimulatedExecution`;
- `HistoricalEconomicEvaluationService`;
- `EconomicResult` with explicit execution, cost, completeness, and residual
  exposure semantics.

The objective `research-dataset-v1` remains a separate boundary consisting of
`FeatureSnapshot` and explicitly selected `ObservedMarketOutcome` facts.

---

## Problem

The economic contract currently exists only as in-memory domain records and
canonical document serializers. There is no operational path to:

- import a bounded candidate population;
- reject invalid or unverifiable candidates;
- resolve the exact referenced `FeatureSnapshot`;
- evaluate candidates through the existing historical economic service;
- preserve failed, partial, unsupported, and cost-incomplete outcomes;
- export deterministic candidate, result, and manifest artifacts;
- validate idempotence and cross-language consumption.

---

## Scope

- Add a provider-neutral, versioned candidate import format, preferably JSONL.
- Validate schema, dataset definition, policy versions, candidate fingerprints,
  `StrategyIntent`, quantity, market, T0, hypothesis, and exit semantics.
- Reject unknown versions and invalid candidates fail-closed.
- Support V1 `MARKET` candidates only; do not convert `LIMIT` candidates.
- Resolve the exact immutable `FeatureSnapshot` by semantic identity and verify
  its fingerprint without nearest/latest fallback.
- Orchestrate candidates through `HistoricalEconomicEvaluationService`.
- Derive exit intent from the canonical time-horizon exit policy.
- Preserve partial entry fills, partial exit fills, residual exposure, zero-fill
  outcomes, execution unavailability, and unsupported outcomes.
- Preserve `UNAVAILABLE`, `INSUFFICIENT_EVIDENCE`, and `NOT_APPLICABLE` cost
  states without converting missing costs to zero.
- Reuse the existing `EconomicResult`; do not create a parallel accounting
  model.
- Export deterministic candidate JSONL, result JSONL, and economic manifest
  artifacts with canonical ordering, checksums, fingerprints, UTC timestamps,
  BigDecimal precision, and explicit missingness.
- Ensure every input candidate is accounted for in the export and manifest.
- Provide a bounded repository-consistent invocation path.
- Add an end-to-end deterministic fixture proving import, validation, exact
  snapshot resolution, replay, result export, manifest construction, and
  validation.
- Add tests for invalid inputs, missing evidence, partial execution, cost
  missingness, deterministic identity, ordering, round-trip behavior, and
  manifest count reconciliation.
- Reconcile the canonical Story implementation report and operational
  documentation after implementation.

---

## Out of Scope

- Generic backtesting or event-driven simulation.
- Strategy engine, signal engine, opportunity scoring, ML, LLM, or notebooks.
- Walk-forward, out-of-sample, statistical, or economic experiments.
- Risk, TradePlan, PAPER, live execution, broker integration, or gateway/public
  APIs.
- Historical `LIMIT` execution.
- Fee acquisition, funding acquisition, synthetic fees, or synthetic funding.
- `EconomicResult` persistence, candidate/result tables, migrations, Kafka, or
  distributed job orchestration.
- Massive historical backfill, Cartesian candidate generation, or automatic
  population expansion.
- Changes to `research-dataset-v1`, `ResearchSample`, objective
  `DatasetManifest`, `FeatureProjectionV1`, `ObservedMarketOutcome`, or a new
  `FeatureProjectionV2`.
- Recalculation of fills, fees, funding, PnL, or cost completeness in Python.
- Modifying or silently accepting ADRs whose repository status is still
  `Proposed`.

---

## Architectural Invariants

- Market Intelligence owns research population/orchestration and
  `FeatureSnapshot` resolution.
- Trading Core owns candidate validation, historical execution, costs, and
  economic truth.
- Market Data remains the authority for strict historical Order Book evidence.
- Python proposes candidates and consumes Java-produced facts; it is not an
  accounting authority.
- Objective market outcomes remain independent from strategy economics.
- `UNKNOWN != ZERO`, `UNAVAILABLE != ZERO`, and
  `NOT_APPLICABLE != UNAVAILABLE`.
- Feature values are context and must never fabricate execution prices.
- Historical eligibility is governed by `knownAt <= cutoff`.
- Semantic fingerprints exclude random IDs, database IDs, export time, and
  other operational metadata.
- The same candidate, evidence, policies, and models produce the same semantic
  candidate/result/manifest identities.
- No provider-specific DTO or broker implementation detail leaks into the
  research contract.

---

## Acceptance Criteria

- [ ] A bounded candidate JSONL population can be imported deterministically.
- [ ] Unsupported schema, dataset, policy, or execution versions fail closed.
- [ ] Candidate fingerprints are recomputed and mismatches are rejected.
- [ ] Invalid quantity, market, T0, hypothesis, exit, or intent semantics are
      rejected without fallback or silent resizing.
- [ ] V1 accepts `MARKET` only and rejects `LIMIT` without conversion.
- [ ] The exact referenced `FeatureSnapshot` is resolved and its identity is
      verified; nearest/latest fallback is impossible.
- [ ] Candidates use the existing historical economic evaluation authority.
- [ ] Exit quantity never exceeds actually filled entry exposure.
- [ ] Partial entry, partial exit, residual exposure, and zero-fill outcomes are
      preserved honestly.
- [ ] Missing commission and funding remain explicit and are never serialized as
      zero; `NOT_APPLICABLE` remains distinct.
- [ ] Execution-adjusted economics may be exported without falsely claiming
      fully costed `NetPnL` or `NetReturn`.
- [ ] Every accepted or rejected candidate has an explicit exported disposition.
- [ ] Candidate and result JSONL output has deterministic semantic ordering.
- [ ] BigDecimal precision, UTC timestamps, enum values, and missingness survive
      export and round trip.
- [ ] The manifest contains reconciled candidate/result, disposition,
      execution-status, cost-completeness, residual-exposure, missing-cost, and
      checksum accounting.
- [ ] Reordering input transport lines does not change order-independent
      population identity or canonical output.
- [ ] The end-to-end fixture proves Java validation, exact snapshot resolution,
      historical entry/exit replay, result export, manifest generation, and
      validation.
- [ ] Existing objective dataset and historical economic regression tests remain
      unchanged and passing.
- [ ] No persistence, migration, Risk, PAPER, public API, broker, or Python
      accounting behavior is changed.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-059.md` - Objective Future Market Outcomes.
- `docs/architecture/adr/ADR-060.md` - Versioned Feature Projection and
  immutable FeatureSnapshot.
- `docs/architecture/adr/ADR-061.md` - Explicit Selection of Immutable Outcome
  Versions. Repository status remains `Proposed`.
- `docs/architecture/adr/ADR-062.md` - Versioned Research Dataset Boundary.
  Repository status remains `Proposed`.
- `docs/architecture/adr/ADR-063.md` - Historical Order Book Evidence and Replay
  Semantics.
- `docs/architecture/adr/ADR-064.md` - Historical Execution and Transaction-Cost
  Research Architecture.

No ADR is accepted or modified by this Story. If implementation requires a new
architectural authority, create it as a proposal and stop for human approval.

---

## Relevant Modules

- `trading-core`
- `market-intelligence`
- `market-data`

Python remains an external consumer boundary. No Python production service is
introduced.

---

## Validation

- Targeted candidate import, validation, resolution, evaluation, serializer,
  manifest, and end-to-end fixture tests.
- Existing Trading Core economic and historical replay regression tests.
- Existing Market Intelligence objective dataset regression tests.
- Existing Python dataset validation suite where applicable, without changing
  objective dataset semantics.
- Trading Core `mvn test` and `mvn verify`.
- Repository-standard SonarQube analysis when configured.
- `git diff --check`, complete diff review, and working-tree verification.
- Safe bounded runtime proof when current immutable evidence supports entry and
  exit replay; report evidence coverage rather than fabricating a proof.

---

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Implementation completed within this scope.
- [ ] Documentation reconciliation completed.
- [ ] Required validation executed and reported.
- [ ] Second-pass falsification review completed.
- [ ] Code Review completed and approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.

---

## Expected Next Primary Story

Recommend exactly one next Story after runtime coverage is measured:

`First Economic Research Experiment - Always-Long 1h Baseline`

Only recommend it if current immutable FeatureSnapshot and Order Book evidence
coverage is sufficient. Otherwise recommend the single operational Story that
most increases trustworthy research coverage.

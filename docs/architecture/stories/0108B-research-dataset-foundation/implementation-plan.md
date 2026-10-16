# Implementation Plan - Story 0108B

This plan records the implementation shape used for the current worktree
change. It does not constitute human approval of the implementation plan.

## Production Changes

1. Add immutable research domain types for population specifications, outcome
   selection keys, samples, exclusion reasons, sampling cadence, and dataset
   manifests.
2. Implement a deterministic `ResearchDatasetBuilder` over supplied immutable
   feature snapshots and observed outcomes.
3. Bind the builder to the supported `feature-projection-v1` schema and reject
   incomplete or type-incompatible feature snapshots.
4. Require explicit outcome-definition and outcome-fingerprint selection for
   each configured market, cutoff, and horizon.
5. Detect duplicate and conflicting immutable identities without allowing input
   ordering to change factual sample content.
6. Implement deterministic JSONL export that preserves feature types,
   missingness, objective outcome separation, and lineage fingerprints.

## Test Changes

- Verify one sample per snapshot and configured horizon.
- Verify strict schema completeness and feature type validation.
- Verify missingness and typed JSONL serialization.
- Verify incomplete, missing, unselected, and mismatched outcome exclusions.
- Verify duplicate snapshot diagnostics and conflicting outcome rejection.
- Verify reordered inputs, corrections, and unsupported projection versions.
- Run the complete `market-intelligence` Maven validation.

## Documentation

- Add ADR-062 for the versioned research dataset boundary.
- Record the Story analysis, implementation evidence, and independent review.

## Explicit Non-Goals

No feature or outcome calculation, repository population query, dataset table,
ML integration, Python integration, labels, strategy evaluation, backtesting,
P&L, public API, Gateway, frontend, Risk, Broker, or Execution change is
included.

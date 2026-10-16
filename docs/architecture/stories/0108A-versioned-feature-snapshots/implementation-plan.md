# Implementation Plan - Story 0108A

This plan records the implementation shape used for the existing worktree
change. It does not constitute human approval of the implementation plan.

## Production Changes

1. Add immutable domain types for feature schemas, definitions, typed values,
   units, missingness, provenance, feature groups, and snapshots.
2. Implement `FeatureProjectionV1` as a pure deterministic projection over the
   approved persisted artifact groups.
3. Add `FeatureSnapshotProjectionService` to load artifacts, build the snapshot,
   calculate its deterministic fingerprint, and persist it through a port.
4. Add the `FeatureSnapshotRepository` port and JPA adapter.
5. Add Flyway V15 with immutable snapshot storage and uniqueness constraints.
6. Propagate the analysis context fingerprint through execution metadata and
   consolidation.

## Test Changes

- Verify V1 schema and feature values.
- Verify excluded or unavailable inputs are handled explicitly.
- Verify deterministic fingerprints and provenance.
- Verify JPA serialization/deserialization of polymorphic feature values.
- Verify idempotent persistence and conflict protection.
- Run the complete `market-intelligence` Maven validation.

## Documentation

- Add ADR-060 as the canonical architectural decision.
- Record the Story artifacts and validation evidence.

## Explicit Non-Goals

No dataset construction, outcome join, ML integration, provider expansion, or
user-facing API is included.

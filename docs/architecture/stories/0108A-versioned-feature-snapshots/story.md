# Story 0108A - Versioned Feature Projection and Immutable Feature Snapshots

## Metadata

- **ID:** `0108A`
- **Status:** IMPLEMENTED - RETROSPECTIVE ARTIFACTS
- **Related Stories:** `0107C`
- **Related ADRs:** `ADR-060`

## Goal

Create a deterministic, versioned feature projection from persisted Market
Intelligence artifacts and store the resulting immutable `FeatureSnapshot` for
future research and machine-learning consumers.

## Context

Story 0107C establishes immutable objective future outcomes and identifies this
work as the follow-up foundation for versioned features and labels. This Story
implements the feature side only. Outcome semantics remain outside the feature
projection boundary.

The accepted architectural decision is documented in
`docs/architecture/adr/ADR-060.md`.

## Problem

Market Intelligence artifacts contain deterministic observations, but there is
no stable, versioned, provider-neutral feature contract that can be persisted
without coupling future research consumers to mutable execution state or raw
provider payloads.

## Scope

- Define the typed feature schema, feature values, units, and missingness.
- Implement the `feature-projection-v1` projection.
- Project only persisted OHLC Temporal, Market Structure, and Trend Context
  artifacts.
- Persist immutable snapshots with deterministic fingerprints.
- Make repeated writes idempotent and prevent mutation of an existing snapshot.
- Preserve source artifact identity, analyzer versions, and context fingerprint.
- Add Flyway migration V15 and JPA persistence.
- Add focused projection and persistence tests.

## Out Of Scope

- Research datasets, manifests, or `ResearchSample`.
- ML training, Python services, model inference, or feature serving.
- Outcome joins or label generation.
- News, Order Flow, Quote Quality, and provider-specific payloads.
- Changes to deterministic risk decisions or execution behavior.

## Acceptance Criteria

- [x] A typed, versioned feature schema exists.
- [x] `feature-projection-v1` produces deterministic feature values from the
      approved artifact inputs.
- [x] Missing or unavailable source artifacts are represented explicitly and
      are not silently fabricated.
- [x] Snapshots preserve provenance and the analysis context fingerprint.
- [x] Snapshots are immutable and repeated writes with the same identity are
      idempotent.
- [x] A different fingerprint cannot overwrite an existing snapshot identity.
- [x] Feature projection does not consume objective future outcomes.
- [x] Market Intelligence tests and verification pass.

## Constraints

- Preserve Market Intelligence service responsibilities.
- Keep the projection deterministic and provider-neutral.
- Keep future outcomes outside the feature boundary.
- Do not expose raw provider payloads through the feature contract.
- Do not commit, push, merge, or rewrite history automatically.

## Relevant Modules

- `market-intelligence`

## Validation

- `cd market-intelligence && mvn -q test`
- `cd market-intelligence && mvn -q verify`
- `git diff --check`
- Inspect the complete implementation diff and generated migration.

## Definition Of Done

- [ ] Repository Analysis approved by human.
- [ ] Implementation Plan approved by human when required.
- [x] Implementation completed in the worktree.
- [x] Documentation reconciled for the implementation.
- [x] Relevant validation executed.
- [ ] Independent code review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created.

## Artifact Provenance

This Story artifact was created after implementation at the user's request.
Its scope is derived from Story 0107C, accepted ADR-060, the current branch,
and the implementation diff. It does not retroactively establish approval for
any workflow gate.

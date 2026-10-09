# Story 0078 - Challenge Definition and Instance Domain

## Metadata

**ID:** `0078`
**Title:** Challenge Definition and Instance Domain
**Status:** IMPLEMENTED - AWAITING HUMAN REVIEW
**Related ADRs:** `ADR-009`, `ADR-028`, `ADR-029`, `ADR-031`

---

## Goal

Introduce the provider-neutral Trading Core foundation for immutable evaluation
definitions and concrete Challenge attempts associated with an Account.

## Context

Story 0077 established generic Risk baselines, policy versions and immutable
Risk context snapshots. Challenge must reuse those references without duplicating
Risk rules or drawdown calculations.

A Challenge is an optional evaluation contract around an Account. It is not a
BrokerAccount, a Risk Policy, an execution workflow or a runtime progression
projection.

## Problem

Trading Core currently has no persistent identity for:

- an immutable versioned external evaluation definition;
- a concrete evaluation attempt;
- the exact definition and Risk Policy versions used by that attempt;
- a minimal ACTIVE/PASSED/BREACHED lifecycle.

## Scope

- Add immutable, versioned `ChallengeDefinition`.
- Add `ChallengeInstance` associated with an Account.
- Support `BALANCE` as the initial progression value source.
- Preserve exact Risk Policy identity and version.
- Preserve external definition provenance.
- Enforce one ACTIVE Challenge per Account while allowing historical terminal attempts.
- Add a transactional application service to start an attempt.
- Preserve optimistic locking and terminal metadata.
- Add focused domain, application and persistence tests.

## Out of Scope

- `ChallengeProgress`.
- Automatic profit-target evaluation.
- Account-monitoring Risk orchestration.
- Automatic PASSED or BREACHED transitions.
- PAPER settlement hooks or periodic monitoring.
- Challenge reset or multiple active Challenges.
- Reusing one Account for sequential attempts.
- Frontend and public Challenge read models.
- Kraken-specific behavior or commercial plan configuration.
- Trailing drawdown, payouts, funded lifecycle, simulation and Quant functionality.
- New services, Kafka or Redis.

## Domain Model

`ChallengeDefinition` is immutable and identified by `(id, definitionVersion)`.
It stores external identity, capital/progression configuration, an exact Risk
Policy reference and source provenance.

`ChallengeInstance` is a mutable lifecycle aggregate identified by `challengeId`.
It references an Account, the exact definition version and the exact Risk Policy
version. Its starting capital is a snapshot and is never read dynamically from
the definition after creation.

The first version permits one ACTIVE instance per Account. New attempts must use
a new dedicated Account rather than mutating `Account.startingBalance`.

## Acceptance Criteria

- [ ] A valid provider-neutral ChallengeDefinition can be created and persisted.
- [ ] Definition identity and version allow multiple versions to coexist.
- [ ] Definition validation requires positive capital, a positive target ratio,
      `BALANCE`, an exact Risk Policy reference and provenance.
- [ ] Definitions cannot be updated through the domain or persistence mapping.
- [ ] A ChallengeInstance starts ACTIVE and preserves Account, definition,
      definition version, Risk Policy version and starting capital snapshot.
- [ ] A ChallengeInstance supports explicit ACTIVE to PASSED and ACTIVE to
      BREACHED transitions with terminal metadata.
- [ ] Terminal transitions are idempotent and terminal states cannot change.
- [ ] One ACTIVE Challenge per Account is enforced by persistence.
- [ ] Multiple terminal Challenge instances may exist for one Account.
- [ ] Starting a Challenge validates Account ownership, definition existence,
      Risk Policy existence, positive compatible starting state and active-instance
      uniqueness without mutating Account financial state.
- [ ] Challenge code contains no provider-specific business branch.
- [ ] No Risk formulas, Risk Policy resolution or runtime progression is duplicated.
- [ ] Existing Accounts remain valid without a Challenge.
- [ ] Focused and affected Trading Core tests pass.

## Constraints

- Preserve ADR-009 provider independence.
- Preserve ADR-028 deterministic Risk ownership and immutable Risk contexts.
- Preserve ADR-029 execution ownership and existing execution lineage.
- Keep BrokerAccount technical and separate from Challenge ownership.
- Do not reinterpret historical definition versions.
- Do not mutate Account starting-balance semantics.
- Do not implement Story 0079 runtime behavior.

## Relevant Modules

- `trading-core`
- existing `risk-domain` policy/version persistence

## Validation

- Run focused Challenge domain/application/persistence tests.
- Run the complete Trading Core test suite with `mvn verify`.
- Run `git diff --check`.
- Inspect the complete diff for provider-specific leakage and duplicated Risk semantics.

## Definition of Done

- [ ] Implementation completed.
- [ ] Relevant validation executed.
- [ ] Complete diff reviewed.
- [ ] Implementation report completed.
- [ ] Human review and commit completed.

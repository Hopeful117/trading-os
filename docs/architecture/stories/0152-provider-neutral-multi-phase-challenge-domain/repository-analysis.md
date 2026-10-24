# Story 0152 - Repository Analysis

## Status

`ANALYSIS APPROVED - IMPLEMENTATION PLAN REQUIRED`

## Current State

`ChallengeDefinition` is an immutable/versioned JPA entity with one
`profitTargetRatio`, one progression source, one Risk policy reference, and
optional settlement/instrument constraints. `ChallengeInstance` references one
definition version and has only `ACTIVE`, `PASSED`, and `BREACHED` lifecycle
states.

`ChallengeEvaluator` already provides the correct safety ordering:

1. blocking Challenge Risk violation;
2. incomplete Risk evaluation remains active;
3. progression target evaluation;
4. terminal transition.

That ordering must be retained and generalized around the current phase.

`ChallengeProgressService` and `ChallengeProgress` are read-side projections;
they must not become a second Risk calculation authority.

The Risk Domain already owns daily and total drawdown rules, immutable Risk
context, and inclusive breach semantics. The multi-phase change should consume
the completed Risk result and not add FTMO-specific loss calculations.

The repository has no generic executed-trading-day evidence contract and no
phase persistence. Existing Challenge migrations are additive Flyway scripts,
with immutable definition identity represented by `(id, definition_version)`.

## Authority And Compatibility Findings

| Area | Finding | Required treatment |
|---|---|---|
| Definition identity | Versioned and immutable | Preserve `(id, definition_version)` and add child phase identity |
| Phase model | Missing | Add ordered immutable phase records |
| Objectives | One target field only | Add generic objective records without provider names |
| Risk | Drawdown/policy rules already exist | Consume Risk outcomes, do not duplicate formulas |
| Trading days | No domain contract | Add complete/unknown evidence input with resolved day identities |
| Instance state | Single terminal lifecycle | Add current phase and local/provider progression state additively |
| Legacy rows | Existing definitions/instances in production schema | Do not backfill phase history; preserve implicit single-phase behavior |
| Provider confirmation | Not modeled | Represent required confirmation explicitly and fail closed when absent |

## Proposed Smallest Shape

- `ChallengePhaseDefinition`: immutable child of a definition version, with
  stable key, order, progression policy, Risk policy reference, and objective
  collection.
- `ChallengeObjectiveDefinition`: immutable generic objective configuration,
  initially profit target, minimum trading days, and Risk compliance.
- `ChallengePhaseState`: provider-neutral current-phase state with local
  satisfaction and optional provider confirmation evidence.
- `ExecutedTradingEvidence`: application input containing complete/unknown
  status and distinct resolved qualifying trading-day identities.

These names are implementation candidates, not additional architecture
decisions. The implementation plan must keep the model minimal and avoid a
general workflow engine.

## Migration Safety

Use a new additive Flyway migration for phase/objective tables and nullable
instance phase-state fields. Existing definition and instance rows must remain
valid. A null phase reference is interpreted as legacy single-phase behavior;
it is not historical phase reconstruction.

No backfill may infer Phase 1, Verification, minimum days, or provider
confirmation from balances, terminal statuses, current Risk evaluations, or
timestamps.

## Risks

- Over-generalizing objective configuration could create an untyped workflow
  engine; constrain the initial objective vocabulary.
- Phase state could accidentally replace the existing terminal lifecycle; retain
  legacy status semantics and explicit transitions.
- Incomplete evidence could be treated as zero activity; require fail-closed
  evidence status.
- Risk formulas could be duplicated in Challenge; evaluator inputs must come from
  the existing Risk authority.

## DevLog

The applicability check was attempted for this Story. DevLog returned an
upstream provider rate-limit error, so current repository source and accepted
ADR-075 are authoritative for this analysis.

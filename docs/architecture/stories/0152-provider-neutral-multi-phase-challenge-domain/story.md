# Story 0152 - Provider-Neutral Multi-Phase Challenge Domain

## Metadata

- **ID:** `0152`
- **Status:** IMPLEMENTED - AWAITING HUMAN REVIEW
- **Related ADRs:** `ADR-075`, `ADR-009`, `ADR-028`, `ADR-029`, `ADR-031`
- **Related Stories:** `0078`, `0079`, `0089`
- **Repository Analysis:** `repository-analysis.md`
- **Implementation Plan:** `implementation-plan.md`

## Goal

Evolve the provider-neutral Challenge domain from a single immutable definition
and terminal lifecycle into an ordered, immutable multi-phase model that can
evaluate phase-specific objectives and deterministic progression without
coupling Challenge to FTMO, cTrader, or any broker implementation.

## Scope

- Add immutable ordered phase definitions under a versioned Challenge definition.
- Add generic phase objective definitions for profit target, minimum trading days,
  and existing Risk-policy compliance.
- Add deterministic phase ordering and progression state.
- Accept provider-neutral executed-trading evidence for minimum trading days.
- Preserve local objective satisfaction versus provider-confirmed progression.
- Preserve existing Risk authority for drawdown and policy formulas.
- Preserve legacy single-phase definitions and instances without fabricated phase
  history.
- Add additive persistence migrations and deterministic tests.

## Out Of Scope

- FTMO-specific classes, constants, or runtime branches.
- cTrader dependencies, authentication, execution, or synchronization.
- FTMO-specific Maximum Daily Loss semantics.
- New Risk formulas or duplicated Risk calculations.
- Provider account lifecycle, payouts, funded-account behavior, or terminal
  provider actions.
- Frontend changes or Challenge API expansion beyond what compilation and tests
  require.
- Spotware runtime validation.

## Acceptance Criteria

- Challenge definitions remain immutable and versioned.
- New definitions can persist at least two ordered phases with distinct
  objectives and Risk policy references.
- A phase cannot be skipped or activated out of order.
- Phase completion is deterministic and requires all applicable objectives.
- Minimum trading days count only distinct qualifying days from complete,
  provider-neutral executed-trading evidence.
- Unknown, incomplete, stale, or contradictory evidence cannot satisfy an
  objective or advance a phase.
- Local satisfaction and required provider confirmation are distinct states.
- Blocking Risk breach wins over simultaneous phase completion.
- Existing single-phase definitions and instances preserve their behavior.
- Existing rows are preserved without fabricated phase state or confirmation.
- No FTMO/cTrader dependency or provider-specific Challenge branch is added.
- Focused tests cover phase ordering, completion, minimum days, breach
  precedence, immutability, legacy behavior, and invalid/unknown state.

## Validation

- Focused Trading Core Challenge tests.
- Trading Core persistence/migration tests relevant to Challenge.
- Existing Challenge benchmark and lifecycle tests.
- `mvn -q verify` in the affected module.
- `git diff --check` and complete diff inspection.

## Human Boundary

This Story is authorized for planning by approval of ADR-075. Implementation
requires explicit approval of the implementation plan before code or migration
changes begin.

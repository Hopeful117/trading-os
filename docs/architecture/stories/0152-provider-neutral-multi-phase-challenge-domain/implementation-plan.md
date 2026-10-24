# Story 0152 - Implementation Plan

## Status

`IMPLEMENTATION PLAN - HUMAN APPROVAL REQUIRED`

## Plan

1. Add immutable provider-neutral phase and objective domain types under the
   existing Challenge package. Keep the initial objective kinds explicit and
   small: profit target, minimum trading days, and Risk compliance.
2. Extend `ChallengeDefinition` with an immutable ordered phase view while
   preserving its existing single-phase fields and factory behavior for legacy
   callers.
3. Add additive persistence for phase definitions and objectives using the
   definition ID/version as the parent identity. Enforce unique phase order and
   stable phase keys within a definition version.
4. Extend `ChallengeInstance` with nullable current-phase state and explicit
   local/provider progression state. Preserve the existing terminal status and
   optimistic locking behavior.
5. Introduce a provider-neutral executed-trading evidence input whose day
   identities are already resolved by the application boundary. Reject or leave
   unsatisfied any incomplete, stale, contradictory, or unknown evidence.
6. Generalize `ChallengeEvaluator` to evaluate all objectives for the current
   phase, preserve Risk-breach-first ordering, advance only to the next ordered
   phase, and pass only after the final phase reaches its required progression
   state.
7. Preserve legacy behavior by adapting definitions without explicit phases to
   one implicit phase and by leaving existing instance phase columns null.
8. Add focused unit tests for phase ordering, distinct phase objectives, minimum
   trading days, local/provider-confirmed progression, breach precedence,
   immutable definitions, legacy challenges, and invalid/unknown evidence.
9. Add persistence/migration coverage for fresh multi-phase definitions and
   existing legacy rows where the repository's test harness supports it.
10. Run targeted Trading Core verification, existing Challenge benchmark tests,
    `mvn -q verify` for the affected module, `git diff --check`, and a complete
    diff review. Do not run or add cTrader execution validation.

## Constraints

- No FTMO or cTrader identifiers in Challenge domain code.
- No cTrader dependency or execution enablement.
- No Risk formula duplication or FTMO-specific Maximum Daily Loss rule.
- No destructive migration, historical phase backfill, or inferred provider
  confirmation.
- No unrelated changes to frontend, Broker Service, Market Data, or Spotware
  runtime validation.

## Expected Files / Areas

- `trading-core/.../challenge/domain/`
- `trading-core/.../challenge/application/`
- `trading-core/src/main/resources/db/migration/common/`
- focused Challenge tests and migration integration tests

## Documentation Reconciliation

The accepted ADR-075 and this Story define the architectural and implementation
scope. After implementation, update only canonical Story closure artifacts and
any Challenge documentation whose behavior changes. No FTMO/cTrader runtime
documentation is required.

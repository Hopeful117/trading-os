# Implementation Report - Story 0181

## Status

Implementation is present and validated at the affected module level. The
independent review findings around target identity, fill consistency, and normal
PAPER pipeline selection were corrected. Story closure remains pending because
restart-context reconstruction and durable concurrent replay are not covered by
the new focused test yet.

## Changes

- Added `PaperCfdSettlementService` as an internal PAPER-only CFD accounting
  boundary.
- Added signed CFD entry persistence without inventory mutation or full-notional
  cash debit.
- Added read-only CFD MTM equity projection and explicit full-exit realization.
- Added exact fill provenance replay handling through the existing
  `TradeExecutionProvenanceStore`.
- Extended `CfdValuationService` with explicit-price realization and fail-closed
  FX fact validation.
- Routed identified PAPER CFD executions from `ExecutionFinalizationStep` to the
  dedicated service without changing global compatibility policy.
- Added fill-quantity and exit-instrument consistency validation.
- Added focused long/short entry, MTM, full-exit, replay, partial-exit, wrong
  instrument, fill mismatch, and LIVE rejection tests.
- Did not modify `SettlementCompatibilityPolicy`, migrations, broker calls, or
  execution safety configuration.

## Validation

- `trading-core/./mvnw -q -DskipTests compile`: passed.
- Focused CFD and settlement regression tests: passed.
- `trading-core/./mvnw -q test`: passed with `EXIT_STATUS=0`.
- `git diff --check`: passed.

Independent review fixes were revalidated with:

- `trading-core/./mvnw -q -Dtest=PaperCfdSettlementServiceTest,ExecutionFinalizationStepRegressionTest test`: passed.
- `trading-core/./mvnw -q test`: passed with `EXIT_STATUS=0`.

## Remaining Evidence Gap

The current test suite does not yet prove a persistence-context clear/reload
followed by MTM reconstruction, nor a durable concurrent duplicate replay. The
implementation uses persisted trade contract fields, account projection fields,
and the existing unique provenance boundary, but those scenarios should be
covered before Story 0181 is marked complete.

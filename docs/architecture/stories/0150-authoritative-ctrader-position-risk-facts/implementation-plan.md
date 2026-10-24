# Story 0150 - Implementation Plan

## Status

`IMPLEMENTED LOCALLY`

1. Extend the provider-owned cTrader position observation with nullable position
   margin and stop-loss facts.
2. Extend the provider-neutral `PositionSnapshot` and Trading Core v2 DTO with
   the same facts, retaining compatibility constructors for existing providers.
3. Map cTrader `usedMargin` and current `stopLoss` without calculation or
   provider-specific types in Trading Core.
4. Map stop-loss evidence into the existing Risk `Stop` contract with a
   deterministic position-derived provenance reference.
5. Keep absent stop-loss as explicit `UNPROTECTED`; keep missing margin as
   incomplete and fail-closed.
6. Add provider mapping, Risk mapping, missing-margin and unprotected-position
   tests.

## Constraints

- No cTrader transport or DTO types cross the Broker Service boundary.
- No margin heuristic or account-margin allocation is introduced.
- No Risk formula or execution behavior changes.
- No FTMO rules are introduced.
- Kraken and PAPER compatibility constructors and paths remain unchanged.

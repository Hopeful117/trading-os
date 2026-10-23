# Story 0149 - Implementation Plan

## Status

`RETROSPECTIVE CLOSURE ARTIFACT - IMPLEMENTATION COMPLETE`

## Plan

1. Inject the existing `BrokerAccountSnapshotV2Client` into the live
   `ModeAwareRiskFactsProvider`.
2. Keep the legacy Broker Risk facts path for Kraken compatibility when the v2
   route is retryably unavailable or returns its legacy compatibility shape.
3. Validate local account ID, opaque provider registration reference and bound
   external account before mapping any v2 facts.
4. Map account and bounded deal facts into the existing `RiskFactsProvider`
   contract, preserving settlement assets and the requested interval.
5. Mark stale, incomplete, missing-account and open-position-without-required-
   Risk-facts snapshots unavailable.
6. Add focused regression tests and run the affected Trading Core validation.

## Constraints

- No cTrader DTOs or transport types cross into Risk Domain.
- No FTMO-specific formulas are introduced.
- No cTrader execution capability is enabled.
- Pre-existing untracked reports remain untouched and unstaged.

## Documentation Reconciliation

The missing Story and closure artifacts are created as part of the explicitly
authorized retrospective closure. No unrelated documentation was changed.

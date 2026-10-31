# Implementation Plan - Story 0183

1. Correct the cTrader observation producer to map provider account equity,
   used margin, and free margin directly, retaining canonical signed quantity
   normalization and provider position identity inside Broker Service.
2. Make the provider-neutral V2 snapshot completeness decision fail closed when
   required LIVE account facts are absent, while preserving existing freshness
   semantics and read-only safety.
3. Add a Trading Core provider-neutral `LiveCfdReconciliationService` that
   resolves canonical CFD contracts, compares existing provider-linked local
   exposure by provider reference, and reports the five explicit outcomes.
4. Add focused tests for account facts, signed LONG/SHORT positions, product and
   contract failures, all reconciliation outcomes, stale/incomplete absence,
   LIVE/PAPER authority separation, required-margin separation, and unchanged
   compatibility policy.
5. Run targeted tests, full Trading Core tests, Broker Service tests, and
   `git diff --check`; review the complete diff for provider leakage or mutation.

No database migration, risk formula change, settlement change, or provider write
operation is planned.

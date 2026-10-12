# Story 0086 - Implementation Plan

1. Add `realizedBalance` and explicit valuation status/timestamps to `Account`,
   expose justified read-model fields, and add migration V27.
2. Initialize the realized baseline for newly provisioned PAPER accounts.
3. Add `PaperDerivativeAccountValuationService` with midpoint pricing, fresh
   snapshot validation, deterministic P&L aggregation, optimistic persistence,
   and a pure known-mark settlement recomputation path.
4. Integrate refresh before PAPER CASH Risk facts and fail closed on incomplete
   valuation.
5. Adapt CASH settlement entry and exit to update realized state exactly once
   and recompute equity from open positions.
6. Add focused unit and integration coverage for accounting, repeated and
   reversing valuations, freshness, migration, Risk, Challenge, and races.
7. Reconcile documentation, run focused tests, full Trading Core verification,
   and `git diff --check`.

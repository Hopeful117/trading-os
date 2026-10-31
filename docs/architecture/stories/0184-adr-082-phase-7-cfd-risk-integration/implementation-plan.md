# Implementation Plan - Story 0184

## 1. Establish the provider-neutral CFD risk projection boundary

- Add the smallest Trading Core application-layer component needed to project
  CFD financial facts for Risk, reused by both T0 and T1.
- Make the component consume `InstrumentFinancialContract`,
  `CfdFinancialCalculator`, provider-neutral market facts, and explicit
  reporting-currency conversion facts.
- Keep generic SPOT and `LINEAR_PERPETUAL` projection on the existing path.
- Represent unavailable, stale, incomplete, contradictory, or unreconciled CFD
  facts as the existing fail-closed risk context outcome.

## 2. Preserve and expose complete CFD contract facts

- Extend the provider-neutral risk-facts mapping only where required to carry
  product classification and a validated CFD contract/provenance to the
  application-layer projector.
- PAPER positions must use the persisted `Trade` contract snapshot when it is
  complete; missing persisted CFD contract facts fail closed.
- LIVE positions must use the Phase 6 authoritative position boundary and a
  provider-neutral contract resolved through the existing Market Data contract
  resolver. Local persisted exposure may support identity/reconciliation but
  must not replace current provider facts.
- Do not expose cTrader payloads, provider-specific volume terms, or provider
  field names in any risk contract.

## 3. Integrate proposed CFD notional and stop risk at T0 and T1

- In `TradePlanRiskEvaluationService`, detect CFD plans from the existing
  provider-neutral `instrumentProductType` metadata.
- Resolve and validate the CFD contract before building `ProposedTrade`.
- Use the plan entry/reference price and the validated contract to calculate
  quote-currency notional and stop-loss risk with `CfdFinancialCalculator`.
- Convert both values to the Risk reporting currency using the existing
  timestamped directed Market Data conversion facts; reject missing or
  directionally invalid conversion evidence.
- Apply the same logic in `ExecutionTimeRiskRevalidationService`, using its
  independent T1 market acquisition and freshness policy.
- Do not silently trust persisted generic CFD notional/expected-risk fields when
  they conflict with the validated contract calculation.

## 4. Integrate CFD current-position facts

- Update the shared position projection used by T0 and T1 to branch only in
  Trading Core when a position is explicitly classified as CFD.
- For CFD positions, use the conservative mark already acquired for the
  position, the signed canonical quantity, the entry price, the validated
  contract multiplier, and explicit quote-to-reporting conversion.
- Calculate market value/notional and protected stop-loss risk through
  `CfdFinancialCalculator` or a thin application composition around it.
- Preserve existing protection-status validation, including automated-plan
  fail-closed behavior for missing or partial protection.
- Keep position margin as an observed current-position fact; do not route it
  into proposed-order required margin.

## 5. Preserve authoritative margin and account semantics

- Keep proposed-order margin resolution on `RequiredMarginPort`.
- Continue using `RequiredMarginCurrencyNormalizer` with complete valuation
  evidence, explicit currency direction, source identity, version, and
  observation time.
- Ensure current LIVE used margin remains an account/position observation only.
- Ensure PAPER account equity continues to come from the local CFD valuation
  path and does not call Broker Service.
- Ensure LIVE account equity continues to come from the Phase 6 provider-
  authoritative snapshot and is not recomputed from local positions.

## 6. Add focused regression coverage

- Add unit tests for the shared CFD projection: long/short quantities,
  multiplier, notional, adverse stop risk, identity currency, cross-currency
  conversion, provenance, stale facts, and missing contract facts.
- Extend `ModeAwareRiskFactsProviderTest` for PAPER local authority, persisted
  contract completeness, and zero Broker Service calls.
- Extend T0 and T1 service tests for CFD proposed facts, current positions,
  independent market acquisition, and fail-closed behavior.
- Add LIVE tests for authoritative account/position facts, contract resolution,
  reconciliation requirements, and separation of current used margin from
  proposed required margin.
- Preserve existing SPOT/`LINEAR_PERPETUAL`, protection, settlement
  compatibility, and Risk Domain tests.

## 7. Validate and reconcile documentation

- Run focused Trading Core tests for all changed classes.
- Run full Trading Core tests and the Risk Domain test suite.
- Run Broker Service tests if provider-neutral snapshot contracts are changed.
- Run `git diff --check` and inspect the complete diff for provider leakage,
  mutation, authority inversion, and accidental compatibility changes.
- Record that no database migration, settlement-policy update, provider write,
  or user-facing documentation update is required unless implementation
  evidence changes that conclusion.

## Expected File Areas

- `trading-core/src/main/java/.../risk/application/TradePlanRiskEvaluationService.java`
- `trading-core/src/main/java/.../execution/application/service/ExecutionTimeRiskRevalidationService.java`
- `trading-core/src/main/java/.../risk/infrastructure/client/ModeAwareRiskFactsProvider.java`
- `trading-core/src/main/java/.../risk/application/port/RiskFactsProvider.java`
- a new or existing provider-neutral CFD risk projection component under
  `trading-core/.../risk/application/`
- focused tests under `trading-core/src/test/java/.../risk/` and
  `trading-core/src/test/java/.../execution/`

No files under `risk-domain` are expected to change.

## Implementation Boundary

This plan does not authorize implementation until it is explicitly approved by
the human engineer. No implementation files have been modified for Story 0184.

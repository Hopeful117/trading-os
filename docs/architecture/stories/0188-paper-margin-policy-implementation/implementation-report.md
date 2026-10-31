# Implementation Report - Story 0188

## Outcome

Implemented the V1 local PAPER margin authority boundary in Trading Core.

## Changes

- Added immutable provider-neutral `MarginPolicy` validation with positive static
  rates, an eight-decimal maximum rate scale, explicit reporting-currency scale,
  provenance, effective time, and fail-closed unsupported flags.
- Added durable versioned policy and account-assignment persistence plus migration
  `V49__paper_margin_policy.sql`. Existing accounts without an assignment remain
  unavailable; no implicit policy was seeded.
- Added `PaperMarginExposureSnapshot`, a read-side validator that aggregates open
  CFD trades only when account, instrument, contract identity/version, status,
  quantity, direction, and timestamps are complete. It rejects conflicting
  contracts and hedging without changing settlement target-trade behavior.
- Added exact V1 incremental netting calculation. Quote-currency arithmetic uses
  exact `BigDecimal` operations, directed ordered FX legs are validated, and the
  converted positive amount is rounded with explicit `CEILING` currency scale.
- Extended `RequiredMarginPort.Request` with financial-account and policy context
  while retaining the existing constructor for callers that do not provide the
  new context.
- Added structured `MarginResolutionContext` and `MarginEvidence` to the margin
  fact. T0 resolves and persists the context in the existing risk context
  snapshot; PAPER T1 loads that exact policy/version context and fails closed when
  the snapshot, policy version, assignment, or policy validity is unavailable.
- Preserved deterministic application diagnostics through `Resolution.reason`
  while retaining the existing Optional/monetary Risk projection. Zero margin is
  accepted for reductions and closes; negative margin remains invalid.
- Added owner-to-owner account binding checks, proposed-contract versus exposure
  contract checks, assignment ordering/uniqueness and expected-version checks,
  canonical order-independent exposure snapshot hashing, and composed FX-rate,
  directed-path, provenance, and freshness validation.
- Routed PAPER margin requests to local policy/exposure/valuation calculation;
  PAPER no longer calls the Broker Service preview. LIVE continues through the
  existing provider-backed client.
- Updated T0 and T1 request construction to carry the canonical financial account
  identity and reporting currency. Existing Risk Domain monetary fact boundaries
  remain unchanged.

## Validation

- `mvn -q -DskipTests compile` in `trading-core`: passed.
- `mvn -q -Dtest=PaperMarginCalculatorTest,PaperMarginExposureSnapshotTest,RequiredMarginCurrencyNormalizerTest,BrokerRequiredMarginClientTest,TradePlanRiskEvaluationServiceTest,ExecutionTimeRiskRevalidationServiceTest,RiskPersistenceTest test`:
  passed.
- `git diff --check`: passed.

The full Trading Core test suite was not run.

## Limitations

- Market Data valuation integration must expose complete conversion legs for
  cross-currency PAPER margin; absent or ambiguous legs fail closed.
- Persistence-assignment integration tests against PostgreSQL were not run; the
  migration and repository constraints were exercised with the focused H2
  persistence test.
- DevLog context was unavailable because authentication was required. The accepted
  ADR, Story artifacts, and current repository were used instead.

## Documentation

Documentation update: this implementation report was added. No unrelated ADR,
Story 0185, or Story 0187 artifact was modified.

## Vault Outcome

The Obsidian vault was not consulted. No vault action is proposed because this
Story is fully specified by the accepted repository ADR, approved Story plan, and
current source. No curated vault content was modified.

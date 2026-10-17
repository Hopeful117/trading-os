# Story 0117 - Provider-Neutral Economic Research Dataset Contract

## Status

Repository Analysis pending human approval.

## Goal

Define and implement the minimum provider-neutral contract for a separate,
versioned, joinable economic research dataset while preserving
`research-dataset-v1` as the objective market research dataset.

## Scope

- Define an immutable economic dataset definition with deterministic identity.
- Define strategy hypothesis identity and version metadata without executable
  strategy logic.
- Define a candidate envelope referencing a FeatureSnapshot identity,
  StrategyIntent, sizing policy, exit policy, and research definition.
- Define deterministic StrategyIntent, candidate, and economic sample/result
  identity material.
- Define explicit V1 fixed-base-quantity and time-horizon exit policies.
- Define a separate economic dataset manifest and execution/cost accounting.
- Define deterministic nested export contracts without adding production
  persistence or changing the existing objective export.
- Prove Java/Python ownership boundaries and candidate-driven evaluation.
- Add focused domain/serialization/fingerprint tests.

## Out Of Scope

- Changes to `research-dataset-v1`, `ResearchSample`, or objective
  `DatasetManifest`.
- Changes to `FeatureProjectionV1`, creation of FeatureProjectionV2, or Order
  Book feature integration.
- Changes to ObservedMarketOutcome, MARKET replay, PAPER, Risk, fee/funding
  acquisition, or execution semantics.
- EconomicResult persistence, database migrations, backfill, Cartesian
  precomputation, statistical experiments, notebooks, ML, or Python accounting.
- Marking an ADR as Accepted without explicit human architecture approval.

## Constraints

- ADR-064 remains the accepted architectural authority for separate economic
  research, Trading Core economic authority, and the Java/Python boundary.
- Objective market truth is referenced by stable semantic FeatureSnapshot
  identity/fingerprint and is not duplicated into the candidate as authority.
- Economic results may exist without an ObservedMarketOutcome; objective outcome
  joins are exact and optional.
- Unknown, unavailable, and not-applicable costs remain distinct from zero.
- Partial fills and residual exposure remain explicit.
- Semantic identity excludes random IDs, database IDs, and operational times.

## Acceptance Criteria

1. `research-dataset-v1`, `ResearchSample`, `DatasetManifest`,
   `FeatureProjectionV1`, and `ObservedMarketOutcome` are unchanged.
2. The economic dataset definition has deterministic fingerprinting and includes
   only the minimum required population, hypothesis, sizing, exit, execution,
   cost, evidence, and optional outcome-join semantics.
3. Strategy hypothesis identity contains only stable ID/version metadata.
4. Candidate identity references FeatureSnapshot semantic identity and includes
   hypothesis, intent, sizing, exit, and execution policy material.
5. V1 supports explicit fixed-base quantity and time-horizon exit policies.
6. Quantity, side, horizon, hypothesis version, execution model, and cost model
   changes produce distinct semantic identities.
7. A candidate/result can reference the same FeatureSnapshot as other candidates
   without embedding its feature payload.
8. Economic result envelopes preserve execution status, requested/filled
   quantity, residual exposure, cost component status, and completeness.
9. A result can represent execution-adjusted economics with unavailable costs
   without producing a fully-net claim or converting missing costs to zero.
10. Objective outcome references are exact and optional; no latest-row lookup is
    introduced.
11. The separate economic manifest accounts for candidates, results, execution
    statuses, completeness, and exclusion/censoring reasons.
12. Canonical serialization is deterministic, nested, UTC/Instant-based, and
    preserves BigDecimal and explicit missingness.
13. No production persistence, migration, replay, PAPER, Risk, fee/funding,
    Python accounting, or objective dataset behavior changes.

## Validation Expectations

- Focused Trading Core and Market Intelligence tests for identity and policy
  contracts.
- Serialization round-trip/determinism tests.
- Existing objective dataset regression tests.
- Existing Trading Core replay/economic regression tests.
- Existing Python dataset validator/tests without modifying Python.
- `git diff --check` and module-appropriate Maven validation.

# Story 0128 - Align Passive Analysis Artifact Production with Feature Projection Requirements

## Goal

Ensure future strict passive research executions request the deterministic OHLC
Range analysis required by `feature-projection-v1`, without changing projection
semantics or rewriting existing immutable snapshots.

## Confirmed Pre-Story Defect

The runtime baseline contained 43 `FeatureSnapshot` rows. None had PRESENT
`ohlc.range_fraction` or `ohlc.simple_return` values. Strict passive execution
requested Trend Context but not the capability producing
`ohlc-range-analysis-result`. The projection correctly represented the absent
artifact as explicit OHLC missingness.

## Scope

- Align the strict passive execution plan with the existing OHLC Range capability.
- Add a focused regression test for the plan/projection artifact contract.
- Preserve strict historical evidence and existing partial snapshot semantics.
- Preserve existing immutable snapshots and historical OHLC evidence.

## Out Of Scope

- Historical snapshot regeneration or mutation.
- Market Data changes, provider changes, or runtime restarts.
- Generic capability dependency infrastructure.
- Research dataset, economic replay, ML, or feature projection changes.

## Acceptance Criteria

- Strict passive research requests `ohlc-range-analysis` and `trend-context-analysis`.
- Existing artifact dependency resolution continues to provide Market Structure.
- Eligible OHLC evidence can produce PRESENT OHLC Temporal features.
- Genuine OHLC unavailability remains explicit `UNAVAILABLE`.
- Existing snapshots and evidence remain unchanged.
- A regression test fails if the passive plan stops requesting the OHLC capability.

## Architectural Decision

No new ADR is required. This change aligns an existing execution plan with the
existing ADR-060 `feature-projection-v1` artifact contract; it introduces no new
ownership boundary or dependency framework.

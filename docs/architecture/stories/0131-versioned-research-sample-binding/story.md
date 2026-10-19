# Story 0131 - Make ResearchSample Populations Version-Reproducible

## Metadata

**ID:** `0131`
**Status:** Review - approved
**Related Investigation:** FeatureSnapshot Completeness & ResearchSample Linkage Root-Cause Investigation
**Related ADRs:** `ADR-060`, `ADR-061`, `ADR-062`, `ADR-064`, `ADR-066`

---

## Goal

Make every Research V1 population reproducibly bind one exact immutable
FeatureSnapshot version and one exact immutable ObservedMarketOutcome version,
while allowing each experiment to declare only the feature values it actually
requires.

The resulting ResearchSample and dataset manifest must remain unchanged when
additional immutable artifact versions are persisted later.

---

## Context

FeatureSnapshots are immutable, versioned projections. Multiple snapshots may
legitimately share a market and evaluation cutoff when their context,
capabilities, provenance, or projection version differs.

ObservedMarketOutcomes are also immutable and versioned. Outcome selection is
already explicit by outcome fingerprint, but snapshot selection is currently
implicit because the dataset builder consumes all snapshots in a cutoff range.

ADR-066 formalizes the canonical binding and experiment-level feature
eligibility policy.

---

## Problem

The current ResearchDatasetBuilder can consume a newly persisted snapshot version
without any change to the ResearchPopulationSpec. A duplicate cutoff can
therefore produce a different population or additional ResearchSamples.

The current global accepted-missingness policy also cannot express that a
directional control experiment requires schema validity but no specific
FeatureProjection value.

---

## Scope

- Add explicit immutable FeatureSnapshot identity selections to the research population contract.
- Resolve selected FeatureSnapshots exactly by semantic fingerprint.
- Preserve explicit ObservedMarketOutcome fingerprint selection.
- Make missing selected snapshots and outcomes fail closed.
- Make ResearchDatasetBuilder consume only selected snapshot versions.
- Record selected snapshot identities and experiment feature requirements in the dataset manifest.
- Add immutable experiment feature requirements using required feature names.
- Exclude a sample when a required feature is not available under the declared policy.
- Allow non-required feature missingness without excluding the sample.
- Define Q1's required feature set as empty while retaining schema validity requirements.
- Add focused tests for duplicate cutoffs, version reproducibility, missing identities, and feature eligibility.

---

## Out of Scope

- Changes to `feature-projection-v1` or its 43-feature schema.
- Mutation, regeneration, or backfill of historical FeatureSnapshots.
- Mutation or backfill of ObservedMarketOutcomes.
- Historical OHLC acquisition or feature-generation changes.
- Order Book trust, replay, or epoch changes.
- Historical fee acquisition or commission calculation changes.
- Funding acquisition or funding semantics.
- Economic execution, fill, spread, slippage, or P&L changes.
- Q1 `ALWAYS_LONG` or `ALWAYS_SHORT` execution.
- ML, Python model training, feature selection, or strategy implementation.
- Durable dataset tables or unrelated research infrastructure.

---

## Acceptance Criteria

- [ ] A ResearchPopulationSpec explicitly identifies the FeatureSnapshot version for every selected sample identity.
- [ ] A selected FeatureSnapshot is resolved by exact immutable fingerprint and never by market plus cutoff alone.
- [ ] Existing explicit ObservedMarketOutcome fingerprint selection remains exact and fail-closed.
- [ ] A missing selected snapshot fails without selecting a replacement version.
- [ ] A missing selected outcome fails without selecting a replacement version.
- [ ] Adding another immutable snapshot at an existing market and cutoff does not change a frozen population.
- [ ] Adding another immutable outcome version at an existing market, cutoff, and horizon does not change a frozen population.
- [ ] Duplicate snapshot versions without explicit selection are rejected or represented by deterministic fail-closed semantics.
- [ ] ResearchSample fingerprints remain based on the exact selected snapshot and outcome identities.
- [ ] Dataset manifests preserve selected snapshot identities, selected outcome identities, and feature requirements.
- [ ] Feature requirements are declared per experiment using a small immutable representation.
- [ ] Non-required unavailable feature values do not exclude a schema-valid sample.
- [ ] Required unavailable feature values produce a deterministic exclusion reason.
- [ ] Q1's required feature count is zero while schema-valid FeatureSnapshot selection remains mandatory.
- [ ] FeatureProjection V1 remains unchanged.
- [ ] Historical research evidence remains unchanged.
- [ ] No Q1 economic evaluation is executed.
- [ ] Relevant tests pass and `git diff --check` passes.

---

## Constraints

- Preserve immutable historical evidence and temporal known-at semantics.
- Use semantic fingerprints for portable research identity; database UUIDs remain operational identities only.
- Do not introduce latest-row, row-order, creation-time, or profitability-based selection.
- Preserve the separation between FeatureSnapshot truth and objective future outcome truth.
- Keep required-feature eligibility separate from Order Book, fee, funding, and economic eligibility.
- Preserve existing service responsibilities and deterministic behavior.
- Do not introduce a database migration unless implementation proves it unavoidable; stop for review if one appears necessary.
- Do not commit, push, or merge without explicit human authorization.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-060.md`
- `docs/architecture/adr/ADR-061.md`
- `docs/architecture/adr/ADR-062.md`
- `docs/architecture/adr/ADR-064.md`
- `docs/architecture/adr/ADR-066.md`

---

## Relevant Modules

- `market-intelligence`

---

## Validation

- Targeted unit tests for population specifications, exact snapshot resolution, dataset building, manifest export, and feature eligibility.
- Reproducibility tests for later snapshot and outcome versions.
- Duplicate-cutoff and missing-identity fail-closed tests.
- `market-intelligence` module verification using the repository-standard Maven command.
- Read-only runtime validation against the existing persisted population where safely supported.
- Confirm no Q1 economic evaluation is executed.
- `git diff --check`.

---

## Definition of Done

- [ ] Repository Analysis approved
- [ ] Implementation Plan approved when required
- [ ] ADR-066 accepted
- [ ] Implementation completed
- [ ] Relevant validation executed
- [ ] Diff reviewed in IntelliJ
- [x] Code Review completed
- [ ] Engineering Report completed
- [ ] Human commit created

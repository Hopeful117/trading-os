# Story 0112B - Deterministic Liquidity Intelligence

## Metadata

**ID:** `0112B`

**Title:** Deterministic Liquidity Intelligence

**Status:** Approved

**Related Stories:** `0112A`, `0106`, `0108A`

**Related ADRs:** `ADR-056`, `ADR-060`, `ADR-063`

This artifact was created at the human engineer's request because the canonical
Story 0112B artifact was missing. Its scope is derived from the accepted ADRs and
the explicit 0112B reference in Story 0112A. It does not establish approval for
any workflow gate.

---

## Goal

Consume a strictly replayed, provider-neutral Order Book context and produce a
small, deterministic, versioned set of liquidity measurements that can be used by
Market Intelligence without becoming a trading, Risk, or execution authority.

The measurements must remain traceable to the exact replay result and source
evidence that produced them.

---

## Context

Story 0112A establishes the durable Order Book evidence and replay foundation for
the bounded Kraken `PF_XBTUSD` market at depth 25. Its replay result is the only
historical Order Book state that this Story may consume for strict analysis.

ADR-063 assigns Order Book interpretation to Market Intelligence and explicitly
identifies deterministic liquidity features as the next Story. ADR-056 defines
the deterministic analyzer boundary, explicit degraded statuses, and the rule
that deterministic measurements do not create trading decisions.

ADR-060 governs any future versioned feature projection or immutable snapshot
integration. Existing `feature-projection-v1` semantics must not be silently
changed.

---

## Problem

The platform can now preserve and replay trusted historical Order Book state, but
it does not yet expose typed deterministic liquidity measurements from that state.
Future analysis would otherwise need to recalculate Order Book semantics in
consumers, risk using current operational state, or introduce unversioned feature
meaning into existing projections.

---

## Scope

- Define a provider-neutral `OrderBookContext` input boundary for Market
  Intelligence using a completed valid replay result.
- Define a versioned deterministic liquidity feature contract with explicit
  names, types, units, ordering, provenance, and missingness semantics.
- Implement the initial bounded liquidity measurements selected during planning
  from the following ADR-063 candidates:
  - best bid/ask spread measurements;
  - visible depth measurements over the replayed levels;
  - bid/ask volume imbalance;
  - microprice when the required quantities are valid;
  - bounded liquidity concentration when its deterministic definition is
    explicitly approved.
- Preserve explicit `AVAILABLE`, `INSUFFICIENT_DATA`, `INVALID_INPUT`,
  `STALE_INPUT`, and `UNAVAILABLE` outcomes where applicable.
- Preserve the replay cutoff, replay sequence identity, source evidence range,
  fingerprints, normalization version, and analyzer/version provenance.
- Ensure equal typed inputs and equal policy parameters produce equal outputs and
  fingerprints.
- Add focused Market Intelligence tests for valid, missing, invalid, stale, and
  boundary inputs.
- Determine the compatible persistence/projection boundary during planning. If
  these features become part of an immutable FeatureSnapshot, use a new explicit
  projection version rather than changing `feature-projection-v1`.

---

## Out of Scope

- Order Book acquisition, persistence, checksum validation, sequence lifecycle,
  or replay implementation owned by Story 0112A.
- Any use of current provider state, REST state, or the operational in-memory
  Order Book as a historical analysis fallback.
- Absorption detection, trade-tape analysis, or combined order-flow analysis.
- Generic scores, predictions, regime labels, profitability claims, or strategy
  semantics.
- Opportunities, TradePlans, Risk decisions, authorization, or execution.
- AI, LLM, ML, feature preprocessing, labels, outcomes, or research dataset
  changes.
- Silent changes to `feature-projection-v1` or existing FeatureSnapshot meaning.
- Catalog-wide or multi-provider rollout beyond the bounded replay contract.
- Frontend, Gateway, Broker, Risk, Execution, or public API changes.
- New infrastructure, streaming platforms, or a new service.

---

## Acceptance Criteria

- [ ] A typed provider-neutral Order Book context boundary consumes only a valid
      replay result at an explicit evaluation cutoff.
- [ ] The initial liquidity feature set and each formula, unit, scale, and
      missingness rule are explicitly versioned and documented.
- [ ] Feature calculation is deterministic, provider-neutral, and independent of
      clocks, mutable global state, network calls, and current provider state.
- [ ] Invalid or insufficient Order Book inputs produce explicit degraded results
      and are never silently converted to zero, false, neutral, or NaN values.
- [ ] Results preserve replay cutoff, sequence identity, source evidence range,
      source fingerprints, and analyzer/version provenance.
- [ ] Equal semantic inputs and parameters produce the same ordered feature
      values and deterministic fingerprint.
- [ ] Features do not modify Risk, authorization, execution, Opportunity, or
      TradePlan decisions.
- [ ] Existing `feature-projection-v1` behavior remains unchanged; any immutable
      feature integration uses a separately versioned projection.
- [ ] Focused tests cover valid calculations, empty books, invalid levels,
      crossed books, zero quantities, cutoff/staleness handling, determinism,
      provenance, and versioning.
- [ ] Applicable Market Intelligence verification, `git diff --check`, and
      repository quality validation pass.
- [ ] No unrelated service responsibility changes are introduced.

---

## Constraints

- Market Data remains authoritative for normalized Order Book facts and replay
  truth.
- Market Intelligence remains responsible for deterministic interpretation only.
- Preserve the temporal semantics of ADR-057 and ADR-058 through the replay
  result's explicit cutoff and known-at boundaries.
- Respect ADR-056's explicit missing, stale, invalid, and unavailable semantics.
- Respect ADR-060's immutable versioning and provenance rules if persistence or
  FeatureSnapshot integration is included.
- Do not leak Kraken DTOs or provider-specific transport semantics into the
  deterministic feature contract.
- Do not weaken security controls or add public exposure.
- Do not modify unrelated worktree changes.
- Do not commit, push, merge, or delete branches automatically.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-056.md` - Deterministic Market Intelligence and
  analyzer boundaries.
- `docs/architecture/adr/ADR-060.md` - Versioned Feature Projection and immutable
  FeatureSnapshot semantics.
- `docs/architecture/adr/ADR-063.md` - Historical Order Book evidence and replay
  semantics.

---

## Relevant Modules

- `market-intelligence`
- `market-data` only for the replay contract consumed by Market Intelligence.

---

## Validation

- Focused Market Intelligence unit tests for each selected deterministic
  measurement and explicit degraded state.
- Contract tests proving that only valid replay results are accepted.
- Temporal tests proving that evaluation cutoff, `knownAt`, sequence boundaries,
  and source provenance are preserved.
- Determinism and fingerprint regression tests.
- Feature projection/persistence tests if the approved implementation integrates
  with a new projection version.
- Full `market-intelligence` Maven test and verification suites.
- SonarQube validation when configured for the affected project.
- `git diff --check` and review of the complete implementation diff.

---

## Definition of Done

- [ ] Repository Analysis approved by human.
- [ ] Implementation Plan approved when required.
- [ ] Initial feature set and formulas approved through the implementation plan.
- [ ] Implementation completed within the approved boundary.
- [ ] Documentation reconciled.
- [ ] Relevant validation executed successfully.
- [ ] Code Review completed and approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.

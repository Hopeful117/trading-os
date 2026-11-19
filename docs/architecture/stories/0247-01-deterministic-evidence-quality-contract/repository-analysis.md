# Repository Analysis - Story 0247-01

## Scope

This analysis covers the first dependency-ordered Story of Epic 0247:
`Deterministic Evidence Quality Contract`.

The Epic and proposal were approved by the project owner on 2026-11-19 for
implementation planning and execution. The current repository state is
`a6211407` on `main`. The pre-existing untracked `CLAUDE.md` is unrelated and
must remain untouched.

## Existing Contracts

### Market Thesis

`MarketThesis` already carries the primary evidence-bearing interpretation:

- market and instrument identity;
- version and lineage;
- thesis state and optional scenario;
- evidence entries and human-readable reasons;
- evaluated time, source cutoff and validity window;
- composer version and input fingerprint;
- supersession reference.

`MarketThesisEvidence` already represents role, availability, contradiction
severity, source, reference, timeframe, explanation, observation time,
provider, instrument, source fingerprint, rule version, cutoff and whether the
evidence is required.

The constructor prevents several unsafe combinations, including unavailable
supporting evidence, available missing evidence, unavailable contradictions and
available evidence newer than the source cutoff. A blocking contradiction also
cannot produce a candidate thesis.

### Evidence Composition

`MarketThesisComposer` is the existing owner of thesis evidence composition. It
already emits explicit missing states for unavailable, stale, cutoff-incompatible
and not-implemented inputs, including volume and advanced liquidity. It also
computes the thesis input fingerprint and composer version.

The new contract must assess this existing evidence rather than reimplement the
composition rules.

### Tradeability

`TradeabilityAssessment` already has policy version and result fingerprint, plus
evidence roles for supporting, weakening, blocking, missing and informational
facts. It preserves cutoff, observation time and source metadata.

The new assessment should consume this contract and must not create a second
tradeability policy.

### Strategy and Opportunity

Strategy evaluation already has applicability and evidence provenance concepts.
`TradingOpportunity` already preserves strategy-match identity, setup snapshot,
market identity, scan provenance, analysis execution and Market Thesis
references. `OpportunityRankingEngine` currently ranks business-priority scores
with stable ID/version tie-breaking; this Story must not change that behavior.

## Persistence and Projection

Market Intelligence uses JPA entities and Spring Data repositories for Market
Thesis, tradeability-related artifacts and Trading Opportunity. Existing
fingerprint and version fields provide patterns for immutable deterministic
records. The exact persistence integration point must be selected after tracing
the current thesis and opportunity write paths; the Story must not introduce a
parallel aggregate without evidence of a storage gap.

## Relevant Architecture Constraints

- Market Data owns provider-neutral observed facts and market identity.
- Market Intelligence owns deterministic evidence composition and interpretation.
- Trading Core and Risk Domain remain authoritative for account, risk,
  authorization and execution state.
- AI cannot override deterministic evidence, freshness, applicability or risk.
- No new microservice or generic workflow engine is justified.
- Existing `MarketThesis`, `StrategyMatch` and `TradingOpportunity` contracts
  should be extended only where a concrete gap exists.

## Proposed Implementation Boundary

1. Add a small domain assessment model containing policy version, dimension
   states, reason codes, source references and fingerprint.
2. Add a deterministic evaluator that consumes existing thesis, tradeability and
   strategy evidence without recalculating their owned policies.
3. Define explicit non-positive semantics for missing, stale, unavailable,
   contradictory and not-implemented inputs.
4. Use canonical ordering and serialized values for fingerprint generation.
5. Persist the assessment alongside the existing Market Intelligence lineage
   only if the current write path can do so without changing existing contract
   meaning; otherwise expose a versioned projection and record the persistence
   gap for the next planning decision.
6. Add focused fixtures and round-trip tests before broader module validation.

## Main Risks

- Duplicating `MarketThesisComposer` or `TradeabilityCalculator` semantics.
- Treating evidence quality as probability or a ranking score.
- Losing provider, cutoff or rule-version provenance during persistence.
- Making fingerprints depend on collection iteration order.
- Introducing a new aggregate where existing thesis/opportunity persistence is
  sufficient.

## Validation Plan

- Domain tests for identical-input determinism and all negative evidence states.
- Fingerprint tests with reordered evidence and changed policy/version inputs.
- Persistence round-trip tests for every persisted field.
- Existing Market Intelligence tests for thesis, tradeability, strategy and
  opportunity behavior.
- `./scripts/test-all.sh` or the documented targeted Market Intelligence Maven
  validation, followed by `git diff --check`.

## Unknowns Requiring Resolution During Planning

- Whether the assessment should be a value object embedded in an existing
  `MarketThesis`/opportunity projection or a separate persisted record.
- The exact required evidence set by strategy and timeframe, which must come
  from existing strategy contracts rather than new inferred policy.
- Whether a public API projection is needed in this Story; the acceptance
  criteria require persistability, but not a new frontend surface.

## Conclusion

The repository already contains most raw evidence and provenance primitives.
The smallest safe implementation is an assessment layer over those primitives,
with explicit non-positive states and stable identity, while leaving thesis
composition, tradeability policy, ranking and execution authority unchanged.

# Implementation Plan - Story 0247-01

## Objective

Add a deterministic, versioned evidence-quality assessment over the evidence
already produced by Market Intelligence, without changing thesis composition,
tradeability policy, opportunity ranking, risk, authorization or execution.

## Design

### 1. Domain contract

Introduce a typed assessment under the existing Market Intelligence domain with:

- assessment policy/version;
- evaluation cutoff and evaluated-at timestamp;
- identity references for market, instrument, provider and timeframe when
  available;
- explicit dimensions for completeness, freshness, provenance, contradiction,
  applicability and missing-policy state;
- deterministic reason codes with explanations;
- source evidence references and input fingerprints;
- a stable result fingerprint.

Dimension states will be descriptive rather than probabilistic. A missing,
stale, unavailable, contradictory or not-implemented input will produce an
explicit non-positive state. The contract will not expose probability,
confidence or profitability semantics.

### 2. Deterministic evaluator

Add a pure evaluator that consumes existing `MarketThesis`,
`TradeabilityAssessment` and strategy applicability/provenance values. It will:

- reuse existing evidence roles and availability values;
- classify required evidence independently from optional evidence;
- preserve blocking and weakening contradictions;
- distinguish unavailable and not-implemented policies;
- compare evidence timestamps with the supplied cutoff and validity windows;
- sort all fingerprint inputs canonically;
- generate reasons in deterministic order.

The evaluator will not query repositories, read the clock, call providers or
recalculate tradeability, thesis composition or strategy rules.

### 3. Persistence boundary

First inspect the existing Market Thesis and opportunity write paths. If the
assessment can be stored as an immutable versioned projection without changing
existing aggregate semantics, add the smallest JPA entity/repository/mapper
needed for round-trip persistence. Otherwise keep the domain contract and
application projection isolated, document the storage gap, and do not create a
parallel generic evidence store.

Persistence must retain the assessment policy version, dimension states, reason
codes, cutoff, source references, input fingerprint and result fingerprint.

### 4. Tests

Add focused tests for:

- identical inputs and repeated evaluation;
- evidence ordering independence;
- fresh complete evidence;
- stale evidence;
- missing and unavailable evidence;
- contradictory evidence, including blocking contradictions;
- not-implemented policy evidence;
- changed policy/input fingerprints;
- persistence round-trip if persistence is added;
- preservation of existing thesis, tradeability and opportunity behavior.

## Expected Files

Likely implementation locations, subject to repository inspection during coding:

- `market-intelligence/src/main/java/.../domain/evidencequality/`;
- `market-intelligence/src/main/java/.../application/evidencequality/`;
- existing Market Intelligence persistence files only if the write-path analysis
  demonstrates a concrete integration point;
- corresponding domain/application/persistence test packages;
- Story implementation report after validation.

No frontend, Gateway, Trading Core, Risk Domain, Broker Service or AI Engine
changes are expected.

## Validation

1. Run focused Market Intelligence tests for the new evaluator and persistence.
2. Run the complete Market Intelligence Maven test/build validation.
3. Run repository-standard quality validation applicable to the changed module.
4. Run `git diff --check`.
5. Review the complete diff for authority-boundary and deterministic-fingerprint
   regressions.

## Risks and Mitigations

- **Duplicated policy:** consume existing evidence instead of recomputing it.
- **False certainty:** make negative and unavailable states explicit and test
  them directly.
- **Unstable fingerprints:** canonicalize lists and nullable values before
  hashing.
- **Persistence scope creep:** add storage only when an existing write path
  provides a concrete, bounded integration point.
- **Probability leakage:** prohibit probability/confidence/profitability names
  and fields in the contract.

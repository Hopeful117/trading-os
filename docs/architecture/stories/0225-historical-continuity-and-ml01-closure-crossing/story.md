# Story 0225 - Historical Continuity and ML-01 Verified Closure Crossing

## Metadata

**ID:** `0225`
**Title:** Historical Continuity and ML-01 Verified Closure Crossing
**Status:** Implementation complete - human validation pending
**Priority:** P1 - Research Trustworthiness
**Predecessors:** `0221`, `0222`
**Related ADRs:** `ADR-057`, `ADR-058`, `ADR-060`, `ADR-061`, `ADR-062`, `ADR-093`, `ADR-094`

## Goal

Implement the accepted Market Data continuity contract and apply the approved
ML-01 policy for counting valid H1 observations across verified market closures,
without converting unexplained gaps, provider outages or corrupted observations
into closures.

## Context

Market Data already provides historical OHLC evidence, gap classifications,
continuity reports and deterministic fingerprints. Market Intelligence already
builds ML-01 H1 features and H+4/H+12/H+24 outcomes. The existing preparation
path requires exact timestamp adjacency and therefore cannot distinguish a
verified historical closure from missing data.

ADR-094 defines the shared responsibility boundary: Market Data classifies base
transitions from scoped closure evidence, while Market Intelligence decides
whether each feature or outcome consumer accepts those transitions. ML-01 may
cross verified closures, but must remain fail-closed for unexplained gaps,
unknown closure evidence and invalid observations.

## Problem

The current continuity path has no reusable consumer-policy contract for valid
observations separated by an evidence-backed closure. A permissive gap skip would
weaken research integrity, while treating every absent interval as data loss
prevents approved ML-01 windows from being evaluated correctly.

## Scope

- Extend or reuse the existing Market Data continuity and gap-assessment
  contracts to represent `CONTIGUOUS`, `VERIFIED_CLOSURE`,
  `UNEXPLAINED_GAP`, `INVALID_OBSERVATION` and `UNKNOWN_CLOSURE_EVIDENCE`.
- Preserve scoped closure provenance, `knownAt`, actual timestamps, elapsed
  duration, reopening price gaps and deterministic fingerprints.
- Keep Market Data responsible for base closure assessment and Market
  Intelligence responsible for consumer acceptance.
- Add the ML-01 consumer policy allowing the 33-candle volatility lookback and
  H+4/H+12/H+24 horizons to count valid H1 observations across verified
  closures.
- Ensure all four ML-01 feature lookbacks and horizons reject unexplained gaps,
  unknown closure evidence and invalid observations.
- Report continuity classifications and exclusion reasons in ML-01 readiness and
  dataset diagnostics.
- Add deterministic synthetic tests and a bounded real-data validation procedure
  using only approved, scoped closure evidence.

## Out Of Scope

- Selecting or integrating an unapproved closure-data provider or calendar.
- Treating weekday/weekend shape, live `MarketState`, broker interruption or
  provider unavailability as verified historical closure evidence.
- Repairing, rewriting or deleting HistData source files.
- Changing ATR, realized-volatility, directional-efficiency or outcome formulas.
- Elapsed-time normalization, fabricated candles, interpolation, forward-fill or
  silent gap skipping.
- Train/validation/test boundaries, purge/embargo policy or minimum sample-size
  decisions.
- Model training, model serving, broker operations, execution or live trading.
- New services, generic policy engines, new MCP permissions or new AgentGrants.

## Acceptance Criteria

- [ ] Market Data emits deterministic base classifications for all supported
      transition cases without suppressing `VERIFIED_CLOSURE` for consumer
      reasons.
- [ ] Closure evidence is scoped by canonical market/venue, versioned,
      fingerprinted and bounded by the applicable historical `knownAt` rule.
- [ ] Conflicting, stale, missing or out-of-boundary closure evidence produces
      `UNKNOWN_CLOSURE_EVIDENCE` or another explicit fail-closed result.
- [ ] Invalid, incomplete, duplicate-conflicted and chronologically inverted
      observations never become closures.
- [ ] ML-01 counts valid H1 observations, not elapsed hours, for its 33-candle
      lookback and H+4/H+12/H+24 horizons.
- [ ] ML-01 preserves actual timestamps, elapsed duration and reopening price
      gaps when an approved closure is crossed.
- [ ] ML-01 excludes every window containing an unexplained gap, unknown closure
      evidence or invalid observation.
- [ ] Continuity policy, closure evidence and source fingerprints are included in
      derived readiness and dataset artifacts.
- [ ] Synthetic tests cover contiguous data, recurring closure, holiday closure,
      exceptional closure, unknown evidence, unexplained gaps, invalid candles,
      duplicate conflicts and end-of-dataset truncation.
- [ ] A bounded real-data validation reports source identity/checksums, closure
      evidence provenance, classifications, eligibility and unresolved evidence;
      it does not claim readiness from synthetic tests.
- [ ] Existing contiguous-input feature and outcome results remain unchanged.
- [ ] No source repair, broker mutation, autonomous trading authority or new
      service is introduced.

## Constraints

- Market Data owns canonical historical evidence and base continuity assessment.
- Market Intelligence owns feature/outcome consumer policy and derived artifacts.
- Python consumes validated bundles and does not become canonical truth.
- `knownAt <= evaluationCutoff` remains enforced for prospective research.
- The implementation must stop or remain diagnostic-only if no approved closure
  evidence source is available for the selected real-data validation.
- Do not commit, push or merge automatically.

## Relevant ADRs

- `docs/architecture/adr/ADR-057.md` - historical evidence and known-at semantics
- `docs/architecture/adr/ADR-058.md` - strict historical replay boundary
- `docs/architecture/adr/ADR-093-proposed-ml-01-chronological-experimental-protocol.md`
- `docs/architecture/adr/ADR-094-market-data-continuity-and-verified-closure-evidence-policy.md`

## Relevant Modules

- `market-data`
- `market-intelligence`
- `research/ml` only for bounded consumer validation if the existing bundle contract requires it

## Validation

- Targeted Market Data continuity, evidence and fingerprint tests.
- Targeted Market Intelligence ML-01 feature, outcome and readiness tests.
- Existing affected-module tests and `git diff --check`.
- Bounded real-data validation only after closure evidence authority and output
  scope receive human approval.
- No model training or live-provider mutation.

## Definition Of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Closure evidence authority and real-data validation scope approved.
- [ ] Implementation completed.
- [ ] Relevant validation executed.
- [ ] Documentation and artifacts reconciled.
- [ ] Code Review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created separately.

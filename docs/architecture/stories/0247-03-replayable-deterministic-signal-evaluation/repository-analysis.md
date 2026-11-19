# Repository Analysis - Story 0247-03

## Scope

This analysis covers Story `0247-03 - Replayable Deterministic Signal Evaluation`.
The Story is approved for Repository Analysis only; implementation is not authorized.

The current repository state is `main` with the Story file and Epic status update
uncommitted. The prior Story `0247-02` is merged at `24a6dbf2`. DevLog was queried
for applicability, but did not resolve a Story UUID and returned low-confidence,
truncated historical context. The repository is therefore authoritative for the
current implementation and the canonical Story/ADR files are authoritative for
scope and constraints.

## Existing Contracts

### Strict Historical Evidence

`EvidenceAcquisitionPolicy.STRICT_OBSERVED` is already the explicit policy for
historical analysis. `IntelligenceContextAssembler` requires an evaluation cutoff
for this policy and propagates one bounded request cutoff to all context
contributors.

ADR-058 establishes the critical boundary:

- Market Data owns durable captured historical facts and eligibility selection.
- Market Intelligence validates the contract, assembles context and runs the
  existing deterministic capabilities.
- Strict replay must never fall back to a current provider response.
- Partial contexts use the existing context status and quality model.

`historicalEvidenceSelection` is already carried through
`IntelligenceAnalysisRequest` and `AnalysisExecutionProvenance`. Existing market
data contributors consume that selection when a strict historical request is
executed, but the selection is currently stored as a serialized request value
rather than as the complete Story-specific replay result.

### Deterministic Strategy Evaluation

`StrategyDefinition` represents one immutable semantic strategy version identified
by `(strategyId, version)`. Semantic changes are expressed by deriving a new
version; an existing version is not redefined. `StrategyDefinitionRepository`
already provides exact-version lookup.

`StrategyEvaluationContext` is provider-neutral and contains market, instrument,
timeframe, authoritative evaluation time, typed semantic inputs, provenance and a
stable SHA-256 digest. Its digest uses canonical ordering of semantic inputs.

`LiveStrategyEvaluationRunner` already delegates both assembled contexts and
observations to the deterministic `StrategyEvaluationService`. The resulting
`StrategyEvaluation` records the exact strategy identity, status, direction,
condition results, consumed inputs, context digest and evidence provenance.

This is the evaluation path the Story must reuse. A second evaluator or a generic
backtesting abstraction would duplicate existing deterministic semantics and violate
the Story boundary.

### Existing Historical Reconstruction

`HistoricalReconstructionService` already performs a bounded strict historical
workflow:

1. preflight selects admissible evidence;
2. a historical `AnalysisExecution` is registered with an idempotency key;
3. the existing analysis path is dispatched with `STRICT_OBSERVED` and the selected
   evidence;
4. terminal execution is awaited;
5. strict feature projection can be produced from the execution artifacts;
6. objective market outcome evaluation and research export may follow.

The service is not a direct fit for this Story as a result producer. It is coupled
to feature projection, `ObservedMarketOutcome`, research export, local output files,
and a human-approved reconstruction command. The replay artifact must reuse its
strict selection and execution seams without importing outcome, dataset, trade or
export semantics.

### Existing Provenance and Fingerprints

`AnalysisExecutionProvenance` already persists market, execution mode, objective,
strategy version text, cutoff, evidence policy, execution origin, reconstruction
request fingerprint and historical evidence selection.

`IntelligenceContext` and `StrategyEvaluationContext` already expose deterministic
context identity. `FeatureSnapshot` also follows the accepted immutable,
fingerprinted persistence pattern and excludes operational timestamps from semantic
identity. These are implementation patterns, not permission to use feature
snapshots as signal-replay results.

## Persistence and Idempotency

The repository has append-oriented persistence patterns for immutable analytical
facts and duplicate-safe `saveIfAbsent` operations. `AnalysisExecution` itself is
an asynchronous technical aggregate with idempotency-key lookup and immutable
terminal result semantics.

No existing repository or domain contract was found that persists the complete
Story-specific replay result, including:

- product/instrument and timeframe as explicit replay identity;
- exact strategy definition identity and semantic version;
- selected evidence provenance and admissibility diagnostics;
- explicit missing, excluded, stale, contradictory, gapped and incomplete states;
- versioned spread and transaction-cost assumptions;
- a result fingerprint distinct from an execution or context fingerprint.

The smallest safe persistence boundary is therefore a new Market Intelligence
domain result and repository/adapter, linked to the existing strict analysis
execution and evidence-selection records. It must be append-only and unique by a
canonical semantic fingerprint. A repeated request with the same semantic inputs
must return the existing fact; changed source evidence, strategy definition,
policy, cutoff or cost assumptions must create a new fact rather than mutate one.

The exact physical schema, aggregate name and transaction boundary remain planning
decisions. They must not be inferred from `ObservedMarketOutcome`, because ADR-059
and ADR-060 keep future market outcomes separate from replay inputs and signal
context.

## Architectural Constraints

- Market Data remains authoritative for historical provider-neutral facts,
  eligibility, coverage, gaps and source fingerprints.
- Market Intelligence owns strict context assembly, deterministic signal evaluation,
  replay interpretation and result provenance.
- Trading Core and Risk Domain remain authoritative for risk, authorization and
  execution; none of those concerns belong in this result.
- `STRICT_OBSERVED` and `knownAt <= evaluationCutoff` must fail closed.
- Current-provider fallback is forbidden for replay.
- Future evidence and `ObservedMarketOutcome` must not enter the replay context.
- Spread and transaction-cost assumptions are evaluation inputs only and must not be
  represented as executed-trade performance.
- No public Gateway, frontend, ActiveScan, new provider, new microservice or generic
  workflow engine is justified by this Story.
- AI remains optional and downstream of the deterministic replay artifact.

## Proposed Implementation Boundary

1. Define a bounded replay request/value model for one market, instrument/product,
   timeframe, cutoff, exact strategy version, strict evidence selection and explicit
   cost-assumption versions.
2. Resolve the exact strategy version through `StrategyDefinitionRepository` and
   reject ambiguous or unavailable definitions rather than selecting a latest
   version implicitly.
3. Reuse the existing strict historical preflight and `AnalysisExecutionService`
   path to assemble context and evaluate the deterministic signal.
4. Extend or adapt the existing evidence-selection contract so admissibility
   diagnostics distinguish missing, excluded, stale, contradictory, gapped and
   insufficient samples without treating any of them as a positive signal.
5. Define a provider-neutral immutable replay result containing the replay identity,
   signal evaluation status, context/evidence provenance, cost assumptions,
   diagnostics and canonical fingerprint. The result must not contain P&L,
   profitability, probability, risk, fill or execution authority.
6. Persist the result with exact-fingerprint duplicate protection and immutable
   correction semantics.
7. Add deterministic canonicalization tests, golden replay fixtures, cutoff and
   `knownAt` look-ahead fixtures, incomplete-history fixtures, provenance tests,
   cost-assumption tests and duplicate/correction tests.

## Main Risks

- Reusing `HistoricalReconstructionService` too broadly would accidentally couple
  this Story to `ObservedMarketOutcome`, dataset export or human-approved local
  reconstruction workflow.
- Treating an `AnalysisExecution` fingerprint as the complete replay fingerprint
  would omit the exact strategy definition, product/timeframe, evidence selection
  and cost assumptions.
- Persisting only `historicalEvidenceSelection` as an opaque string may make
  provenance and gap diagnostics non-auditable; the canonical selected evidence
  contract must remain recoverable.
- A strategy definition may be semantically immutable while governance metadata is
  mutable. Only semantic identity may participate in the replay fingerprint; the
  implementation must not silently replay a different version.
- Existing context quality allows partial contexts. Replay interpretation must
  preserve explicit non-evaluable states and must never convert partial evidence
  into a positive signal result.
- Adding spread or transaction-cost fields near outcome or trade contracts could
  leak performance semantics into a result that is explicitly not a trade result.
- Operational timestamps, database identifiers and random UUIDs must not affect the
  deterministic fingerprint.

## Validation Plan

- Unit tests for replay request invariants and exact strategy-version selection.
- Golden fixtures proving identical semantic inputs produce identical result
  fingerprints.
- Exact-cutoff, pre-cutoff, post-cutoff and `knownAt` look-ahead rejection tests.
- Strict-provider-unavailable tests proving no current-provider fallback occurs.
- Missing, excluded, stale, contradictory, gapped and incomplete-history fixtures.
- Provenance round-trip tests for provider, product/instrument, timeframe, policy,
  cutoff, strategy version and selected evidence.
- Versioned spread and transaction-cost assumption persistence tests.
- Duplicate replay and immutable-correction tests.
- Targeted `market-intelligence` Maven verification and `git diff --check`.

## Unknowns Requiring Resolution During Planning

- The existing Market Data selection/preflight contract must be checked to determine
  whether it already exposes all required sample-level diagnostics, or whether a
  narrow provider-neutral extension is needed.
- The canonical representation of product/instrument and timeframe must be selected
  from existing Market Intelligence/Market Data contracts rather than introducing
  parallel strings or enums.
- The exact spread and transaction-cost assumption value objects and version source
  are not present in the inspected signal-evaluation contracts; their ownership and
  canonical serialization must be decided before implementation.
- It remains to determine whether replay invokes the full analysis execution path or
  a narrower existing strategy-evaluation seam after strict context assembly. The
  choice must preserve the same deterministic capabilities and avoid duplicating
  orchestration.
- The result repository's transaction/idempotency behavior must be aligned with the
  existing persistence adapter conventions and concurrency tests.

## Conclusion

The repository already supplies the critical strict historical evidence boundary,
deterministic strategy evaluator, exact strategy-version repository, provenance
fields and immutable fingerprinting patterns. The missing capability is a focused,
persisted replay-result contract over those seams.

The smallest safe implementation is a new Market Intelligence replay result and
bounded application service that reuses strict evidence selection and the existing
deterministic evaluation path, while keeping outcomes, research exports, trading
performance and execution authority outside the Story.

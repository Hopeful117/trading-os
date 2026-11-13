# Repository Analysis - Story 0236

## Status

Repository Analysis approved by the human engineer before implementation.

## Context Assessment

ADR-100 and Story 0235 are accepted and define a narrow `FOREX_QUOTE_BASIC`
exception to the former `LIVE_CURRENT` prohibition. The latest operational
validation is authoritative for the observed runtime: the refreshed MCP schema
accepted the profile, the correct LIVE cTrader EUR/USD CFD was selected, but the
downstream Thesis still reported `TRADEABILITY_UNAVAILABLE`. The validation
report is present locally and is the source of the scan identifiers and runtime
timestamps. DevLog was queried, but its projection was truncated and did not
contain the validation evidence; the current repository and runtime evidence
were therefore used as authority. The Obsidian vault was not consulted because
the repository contains the canonical ADR, Story and implementation contracts.

## Repository Evidence

### Request and active-scan propagation

The MCP adapter accepts `tradeability_evidence_profile` and forwards it as
`tradeabilityEvidenceProfile` in the Gateway request. The active-scan DTO,
command, fingerprint and `ActiveScanCreationTransaction` all carry the profile.
`ActiveScanCreationTransaction.buildMarkets` creates the child
`IntelligenceAnalysisRequest` with `EvidenceAcquisitionPolicy.LIVE_CURRENT` and
calls `withTradeabilityEvidenceProfile(profile)`. Existing tests assert this
local propagation.

This rules out the previously suspected stale MCP schema/profile propagation
boundary for the latest scan, subject to runtime provenance being exposed.

### First failing boundary: active execution rejects LIVE_CURRENT

The active scan dispatches through `LocalAnalysisExecutionDispatcher` into
`CapabilityAnalysisCoordinator`, which uses `ExecutionEngine` and the artifact
capability contract. `ActiveAnalysisStrategy` selects
`TradeabilityAnalysisCapability` when the request profile is
`FOREX_QUOTE_BASIC` or `LEVEL_II_REQUIRED`.

However, `TradeabilityAnalysisCapability.execute()` begins with a legacy guard:

```text
if any input provenance policy == LIVE_CURRENT
    throw "Tradeability requires approved non-live evidence provenance ..."
```

That guard contradicts ADR-100 for the explicitly approved Forex profile. The
active artifact execution therefore fails before `TradeabilityCalculator` can
inspect the current quote. The capability execution is not completed and its
`TRADEABILITY_ASSESSMENT` artifact is not available to the observation builder.
This is the first confirmed loss of expected evidence.

### Missing evidence propagation into Market Thesis

`ObservationBuilder` keeps only completed capability executions. The
`OhlcTrendObservationRule` extracts `TRADEABILITY_ASSESSMENT` only from a
completed artifact result and places it in `MarketThesisObservationPayload`.
When the active Tradeability execution fails, the payload contains no
tradeability content. `MarketThesisComposer.tradeabilityContent()` returns
`null`, and `addTradeabilityEvidence()` correctly emits required
`TRADEABILITY_UNAVAILABLE`. The Thesis condition remains `PENDING` and the
fail-closed state is correct for the missing input, but the missing input is
caused by the incompatible execution guard rather than by a proven absence of
the provider quote.

### Additional contract gap to verify in implementation

`TradeabilityCalculator` currently marks missing historical range context and
missing news context as required evidence, while ADR-100 defines
`FOREX_QUOTE_BASIC` around fresh two-sided quote identity/provenance and keeps
liquidity unassessed. The implementation phase must verify whether these
requirements are intended for all tradeability profiles or incorrectly make
basic Forex quote analysis fail closed. This is a bounded second-stage defect
candidate, not a basis for a speculative fix before tests establish the
profile-specific contract.

The calculator already preserves useful rules that must remain unchanged:

- invalid or future quotes are rejected with explicit diagnostics;
- a valid quote produces `SPREAD_OBSERVED` without a suitability conclusion;
- missing order-book evidence is non-required for the basic calculator path;
- stale snapshots remain explicitly stale;
- no midpoint or synthetic spread is substituted.

### Capability and provenance persistence

`AnalysisExecution` persists `AnalysisExecutionProvenance`, including market,
mode, account, scan identifiers, evaluation cutoff, evidence policy and
tradeability profile. `CapabilityExecution` and its `CapabilityResult` persist
capability state, artifacts, diagnostics and findings in the capability
repository. `ConsolidatedIntelligence` also carries context summaries, findings,
warnings and capability execution metadata.

The data needed for observability therefore exists across existing aggregates;
the active-scan projection currently discards it. `ActiveScanResultProjection`
and `ActiveScanResponse` expose only execution ID, status, result quality,
outcome, scan diagnostic and opportunities. They do not expose effective
policy/profile, cutoff, capability executions, context provenance or Thesis
evidence.

### Authorized projection boundary

`ActiveScanApplicationService.findOwnedProjection()` scopes the scan lookup by
actor ID before reconciliation. The active-scan controller uses that owned
projection for `/api/v1/intelligence/scans/{scanId}` and resolves agent actors
through the agent principal path. The new detail contract should extend this
owned projection rather than expose arbitrary analysis records.

The existing `/api/v1/intelligence/analyses/{executionId}/capabilities` endpoint
directly queries capability executions and does not visibly perform the same
scan/account ownership check in its controller. It must not be reused for the
new authorized agent projection without a demonstrated authorization boundary.
Cross-account and cross-agent tests are required before exposing detailed
evidence.

## Root Cause Classification

1. **Confirmed primary defect:** the active artifact execution path still
   applies the pre-ADR-100 non-live provenance rejection to
   `FOREX_QUOTE_BASIC`, so quote acquisition is never reached by the
   Tradeability capability in the operational scan.
2. **Confirmed downstream effect:** the failed capability has no completed
   `TRADEABILITY_ASSESSMENT` artifact, so observation construction omits
   tradeability and Market Thesis emits `TRADEABILITY_UNAVAILABLE`.
3. **Confirmed observability gap:** active-scan read projection omits the
   persisted effective provenance and capability execution details, making the
   failure boundary invisible to the authorized MCP consumer.
4. **Open bounded verification:** profile-specific requiredness of range/news
   evidence may still prevent a valid quote-only assessment after the primary
   guard is corrected. Tests must establish this before implementation changes.

## Proposed Correction Boundary

Use the existing artifact capability and provenance contracts. The smallest
justified implementation should:

- allow `LIVE_CURRENT` only when the request profile is the explicitly admitted
  `FOREX_QUOTE_BASIC` and preserve the existing strict rejection for unsupported
  or Level-II-required paths;
- make any profile-specific calculator requiredness explicit and deterministic;
- retain quote identity, provider, cutoff, freshness, bid/ask and spread
  evidence without treating spread as liquidity;
- project the existing execution provenance, capability outcomes, bounded
  diagnostics and Thesis evidence through the already actor/account-scoped scan
  detail response;
- preserve all existing Market Thesis blocking and Risk/execution boundaries.

No new ADR is currently required. The correction fits ADR-100 and existing
capability/projection contracts. If implementation reveals that a new normative
policy is required, stop and request an ADR decision rather than inferring one.

## Deterministic Test Strategy

### Capability execution

- `FOREX_QUOTE_BASIC` plus `LIVE_CURRENT` reaches the calculator and produces a
  completed artifact for valid quote evidence.
- `UNSUPPORTED` remains rejected for live tradeability.
- `LEVEL_II_REQUIRED` remains blocked without approved order-book evidence.
- Provider, market identity, cutoff and freshness mismatches remain explicit
  diagnostics.

### Calculator and Thesis

- fresh two-sided quote, including bid equal to ask;
- missing, one-sided, invalid, negative-spread, stale and future quote;
- provider/market identity mismatch;
- quote/spread provenance and price/pip measurements where metadata permits;
- valid basic quote with absent Level II does not become a liquidity claim;
- missing tradeability remains required missing evidence;
- blocking structural or momentum contradictions keep `NO_TRADE`.

### Active scan and projection

- effective `LIVE_CURRENT` and `FOREX_QUOTE_BASIC` persist on child execution;
- capability status, diagnostics, evidence quality and provenance are projected;
- Thesis evidence is projected without provider secrets;
- owned actor/account reads succeed;
- cross-account or cross-actor reads are denied;
- existing minimal scan lifecycle and opportunity projections remain compatible.

Prefer service orchestration tests using real domain components and mock only
the external Market Data provider boundary.

## Operational Validation

After approved implementation and automated validation, rebuild only the
affected Market Intelligence/Gateway services and run one controlled scan using
the existing authorized MCP connection for the known LIVE cTrader EUR/USD CFD.
Verify the persisted profile, policy, cutoff, provider quote provenance,
freshness, capability result, Thesis evidence and authorized projection. A
precise deterministic rejection is acceptable; `TRADEABILITY_UNAVAILABLE`
despite a valid quote must remain a failure. Do not synchronize the account,
create a TradePlan, evaluate Risk, request approval or mutate the broker.

## Required Human Approval

Repository Analysis is complete for review. Implementation and the
Implementation Plan must not begin until the human explicitly approves this
analysis and then approves the resulting plan.

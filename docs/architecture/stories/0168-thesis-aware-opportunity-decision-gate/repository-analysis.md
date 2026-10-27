# Repository Analysis

## Story Understanding

Story 0168 requests a decision-quality gate between an active Opportunity and
TradePlan preparation. The feature must reuse the existing deterministic Market
Thesis authority, preserve exact immutable lineage, expose the structured Thesis
inside the Trader Cockpit, and prevent backend bypass of the state gate.

The Story does not authorize new analytical capabilities or changes to Risk,
execution, sizing, challenge semantics, Stop, or Target ownership.

## Repository Baseline

| Item | Current value |
|---|---|
| Branch | `main` |
| HEAD | `a67641c126123491285b3bc2891b300626e07bf2` |
| Working tree | clean before Story 0168 artifacts |
| Existing canonical Story | none for Story 0168; supplied Story used as input |
| DevLog | Story Agent attempted; unavailable due provider error |

## Architecture Authority

ADR-076 defines Market Thesis as a market-scoped, strategy-neutral, immutable,
versioned decision-support authority. It explicitly permits downstream
Opportunity and TradePlan references to the exact Thesis id/version while
prohibiting Thesis from owning Risk, TradePlan lifecycle, or execution.

Stories 0159 and 0161 establish `DecisionWorkspace` as the existing human
investigation surface and preserve the existing TradePlan/Risk boundaries.

Trading Core owns user-specific trading workflows, account ownership, human
decision identity, and deterministic orchestration. Market Intelligence owns
Market Thesis, Opportunities, and TradePlan generation.

## Current Relationship

```text
Observation
  -> MarketThesisComposer
  -> persisted MarketThesis
  -> StrategyEvaluation
  -> StrategyMatch
  -> TradingOpportunity
  -> DecisionWorkspace
  -> TradePlan
  -> Risk
```

`ProductionIntelligencePipeline` persists a Thesis before creating an
Opportunity, but currently discards the returned Thesis identity. An
Opportunity retains `analysisExecutionId`, observations, strategy match, and
setup snapshot, but not the exact Thesis id/version.

## Current Market Thesis

`MarketThesis` already provides the required structured data:

- `MarketThesisState`: `NO_TRADE`, `WATCH`, `CANDIDATE`;
- optional `MarketThesisScenario`;
- confirmation and invalidation conditions;
- relevant structural levels;
- supporting, contradicting, and missing evidence;
- contradiction severity;
- evidence availability;
- evaluation time, source cutoff, validity, composer version, and fingerprint.

`MarketThesisController` already supports exact reads by Thesis id/version and a
separate latest query. The new Opportunity workflow must use the exact read,
never the latest query.

## Current Opportunity

`TradingOpportunity` is immutable per version and persists through a JSON
payload in `trading_opportunity_versions`. The payload contains setup snapshot,
strategy match, account/scan provenance, and analysis execution provenance.

The current Opportunity API exposes generic setup facts through
`OpportunityResponse`, but no Thesis reference or Thesis content.

Because the payload is versioned JSON, adding nullable Thesis reference fields
is bounded and preserves deserialization of legacy rows. Legacy rows without a
Thesis reference must not be prepared as actionable Opportunities.

## Current Preparation Boundary

The public path is:

```text
DecisionWorkspace
  -> Trading Core OpportunityTradePlanController
  -> OpportunityTradePlanOrchestrationService
  -> Market Intelligence internal generation endpoint
  -> OpportunityTradePlanGenerationService
  -> TradePlanApplicationService
  -> Risk preflight
```

The current generation service loads the latest active Opportunity by id and
does not inspect Market Thesis state. The request does not carry an Opportunity
version. This permits a stale cockpit context to reach the latest Opportunity
version and leaves the gate enforceable only in Angular, which violates the
Story.

## Current TradePlan Provenance

`TradingRationale` already preserves Opportunity, Observation, AI-analysis,
thesis prose, confirmation conditions, and invalidation conditions. It does not
preserve a Market Thesis id/version. Adding an exact Thesis reference preserves
lineage without copying the complete Thesis into every TradePlan.

Existing TradePlan version persistence and human decision provenance remain
compatible with this addition.

## Current NO TRADE Handling

`DecisionWorkspace` currently stores `NO_TRADE` only in component state. No
existing durable decision or trade-outcome record can represent an Opportunity
rejection without a TradePlan.

The approved bounded addition is an account-scoped `OpportunityDecision` in
Trading Core. It references the exact Opportunity and Market Thesis but does
not become an analytical, Risk, TradePlan, or execution authority.

## Required Changes

1. Add exact Thesis reference to Opportunity domain, payload, persistence
   mapping, creation pipeline, and API response.
2. Add exact Thesis presentation contract to the Opportunity investigation.
3. Add exact Opportunity version and Thesis gate checks to backend preparation.
4. Add Thesis reference to TradePlan rationale and downstream response/snapshot
   contracts.
5. Add durable authenticated NO TRADE command and ownership/idempotency checks.
6. Integrate structured Thesis evidence and gate state into DecisionWorkspace.
7. Add focused backend and frontend validation.

## Risks

- Legacy Opportunities without Thesis lineage must become non-preparable rather
  than silently resolving latest Thesis data.
- The Opportunity list contract may incur additional Thesis reads if complete
  Thesis content is embedded; implementation should prefer exact bounded reads
  or a server-side exact projection rather than latest lookup.
- Durable NO TRADE must remain separate from TradePlan acceptance/rejection and
  execution history.
- Stale evidence must remain explicit and must not be rendered as safe or
  actionable.

## Conclusion

The change is a bounded cross-module contract extension. No new analytical
engine or Risk authority is required. The critical architectural decisions are
exact Thesis reference preservation and a small Trading Core human decision
record for NO TRADE.

Implementation is not authorized until the Implementation Plan is explicitly
approved.

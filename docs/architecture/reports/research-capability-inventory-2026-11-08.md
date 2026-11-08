# Trading OS Research Capability Inventory

**Date:** 2026-11-08
**Mode:** read-only repository audit
**Scope:** Market Intelligence, AI Engine, MCP adapter, research/ml, scanners and Web retrieval

## Verdict

Trading OS has a real deterministic research foundation, not merely a collection of strategy stubs. The canonical execution path is Java-owned, capability-based, provenance-aware and persisted. AI interpretation is an optional advisory branch. The system is suitable for controlled research and data accumulation, but not yet for calibrated predictions, profitability claims, or autonomous execution.

## Capability Matrix

| Capability | Current evidence | Status | Boundary |
|---|---|---|---|
| Market identity and normalized context | `IntelligenceContextAssembler`, `ContextSection`, market-data adapters | Implemented | Market Data supplies facts; Intelligence consumes them |
| Current quote/spread analysis | `SpreadAnalysisCapability`, `QuoteQualityCalculator` | Implemented with degraded states | No suitability or risk authority |
| OHLC range analysis | `OhlcRangeAnalysisCapability` | Implemented | Deterministic measurements only |
| OHLC momentum analysis | `OhlcMomentumAnalysisCapability`, `MomentumCalculator` | Implemented and versioned | No probability or edge claim |
| Market structure | `MarketStructureAnalysisCapability`, `HistoricalStructureArtifactValidator` | Implemented and versioned | Individual structural evidence; clustering remains limited |
| Trend context | `TrendContextAnalysisCapability` and observation services | Implemented | Context/evidence, not a trade decision |
| Order-book liquidity | `OrderBookLiquidityCapability` | Implemented contract and capability path | Operational availability depends on order-book evidence |
| Tradeability | `TradeabilityAnalysisCapability`, `TradeabilityCalculator` | Implemented as explicit evidence | Does not authorize a trade |
| Market Thesis | `MarketThesisComposer`, persisted thesis repository | Implemented | Conditional scenario and evidence composition |
| Capability planning/execution | `CapabilityRegistry`, `ExecutionPlanner`, `ExecutionEngine`, `LocalCapabilityExecutor` | Implemented | Bounded local execution and retry state |
| Adaptive follow-up | `ResearchApplicationService` plus `HttpAiAnalysisCapability` | Implemented, allowlisted and budgeted | Java validates capability, version, market and budget |
| Final AI synthesis | `HttpAiAnalysisCapability`, `AiHypothesisReconciliation` | Implemented contract | Must reference authorized evidence; advisory only |
| Active scanning | `ActiveScanApplicationService`, reconciliation and opportunity services | Implemented | Scans produce observations/opportunities, not orders |
| Strategy matching | `StrategyEvaluatorRegistry`, built-in evaluators | Implemented | Deterministic strategy compatibility evidence |
| Research report lifecycle | `ResearchApplicationService`, `ResearchExecution`, `ResearchReport` | Implemented | Async, idempotent, checkpointed and owned |
| Historical reconstruction | `HistoricalReconstructionService`, policy and validators | Implemented with narrow allowlist | Strict cutoff and supported capabilities only |
| Dataset export | `ResearchDatasetBuilder` and controlled export runner | Implemented | Produces validated offline bundles |
| ML experiment/benchmark | `research/ml` Python package | Offline-only | No production model serving or training lifecycle |
| UI research retrieval | `ResearchService`, `/research` route | Partial | Readiness/integrity UI exists; full execution/report workflow is not exposed here |
| MCP research tools | `mcp-adapter/app/server.py` | Exposed | Backend authorization integration remains incomplete |

## Canonical Runtime Path

```text
Market Data
  -> IntelligenceContextAssembler
  -> CapabilityAnalysisCoordinator
  -> deterministic capabilities/artifacts
  -> Market Thesis
  -> ResearchApplicationService
  -> optional initial AI interpretation
  -> Java-authorized deterministic follow-up
  -> optional final AI synthesis
  -> ResearchExecution / ResearchReport
  -> REST, Web or MCP read retrieval
```

`CapabilityAnalysisCoordinator` is the authoritative bridge from the public analysis lifecycle to the capability engine. It assembles context, materializes artifacts, selects capabilities, enforces historical reconstruction policy, runs the execution engine and consolidates findings with provenance and quality.

## Safety Boundary

The research report explicitly states that it is advisory and produces no trade authorization or calibrated probability/profitability claim. Concrete TradePlans remain subject to deterministic Risk and human authorization. No inspected research path places a broker order directly.

## Main Gaps

- Adaptive research is not a general autonomous planner; it permits only one allowlisted follow-up and two current capability IDs.
- AI interpretation is gated on a persisted Market Thesis, which can suppress advisory interpretation when the thesis is absent.
- The MCP adapter exposes research tools, but `MarketIntelligenceController.actorId()` accepts only `MiUserPrincipal`; `AgentPermission.RESEARCH_RUN` exists in Trading Core but is not wired into this controller.
- Historical and ML evidence is contractually strong but operationally data-limited.
- The Web research service currently retrieves readiness and integrity, not the complete research execution/report lifecycle.

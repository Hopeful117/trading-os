# Trading Research Engine Audit

**Date:** 2026-11-13

**Mode:** read-only repository and bounded runtime audit

**DevLog:** unavailable in the current environment (`MCP error -32000:
Connection closed`). The current repository, accepted ADRs, existing reports,
tests and authorized read-only runtime projections were used instead.

## Executive Verdict

Trading OS has a credible deterministic research foundation, but it does not
currently provide a complete autonomous strategy-discovery and profitability-
validation engine.

| Capability | Verdict | Evidence level |
|---|---|---|
| Market evidence acquisition and provenance | Implemented foundation | CODE_CONFIRMED / OBSERVED |
| Deterministic Market Thesis composition | Implemented, fail-closed | CODE_CONFIRMED / OBSERVED |
| Strategy execution/evaluation | Implemented for a bounded catalogue | CODE_CONFIRMED |
| Autonomous strategy discovery | Not implemented as a production capability | CODE_CONFIRMED |
| Generalized backtesting engine | Not implemented | CODE_CONFIRMED |
| Offline research experiments | Implemented, bounded and non-trading | CODE_CONFIRMED |
| AI-assisted interpretation | Implemented in shadow mode | CODE_CONFIRMED |
| AI-led strategy validation | Not authoritative and not sufficient for activation | CODE_CONFIRMED |
| Runtime EUR/USD research evidence | Partial and degraded | OBSERVED |
| Live risk/execution readiness for inspected account | Blocked | OBSERVED |

The correct current product description is therefore:

> A deterministic market-intelligence and research-evidence platform with a
> small governed strategy catalogue, bounded offline experiments and optional
> shadow-mode AI interpretation.

It is not yet a self-directed system that discovers strategies, proves their
robustness, promotes them, and validates live profitability without
predefined strategy semantics and human governance.

## Safety And Scope

No source code, production configuration, strategy definition, Market Thesis,
Trade Plan, risk evaluation, broker order, account synchronization,
reconciliation, credential, grant or permission was changed.

The worktree was already dirty. Existing changes and untracked files were not
reverted or modified. This audit adds only this report.

The runtime checks used read-only Trading OS projections for the explicitly
authorized account and EUR/USD market catalogue. No scan or research workflow
was started by this audit.

## Evidence Boundary

Evidence labels:

- `CODE_CONFIRMED`: established from the current implementation.
- `TEST_CONFIRMED`: established by an existing test.
- `OBSERVED`: returned by an authorized running service projection.
- `INFERRED`: supported interpretation, not a direct persisted fact.
- `UNKNOWN`: the inspected contract does not expose enough evidence.

## Current Research Pipeline

The current production path is structurally coherent:

```text
Market evidence
  -> capabilities and observations
  -> deterministic Market Thesis
  -> governed strategy definitions
  -> strategy evaluation
  -> opportunities
  -> trade planning and risk boundaries
```

`ProductionIntelligencePipeline` reads persisted `StrategyDefinition` values,
applies governance eligibility first, then timeframe/provider applicability,
and only then invokes a matching evaluator. An ineligible definition does not
reach an evaluator and cannot produce a live-evaluation result.

`MarketThesisComposer` composes versioned, fingerprinted evidence and treats
missing, stale, identity-incompatible or cutoff-incompatible evidence as
unknown/unavailable rather than silently assuming validity. It explicitly
marks volume and advanced liquidity semantics as not implemented. Required
evidence can therefore keep the Thesis in `NO_TRADE` or `WATCH`.

This is a meaningful deterministic foundation, not merely scaffolding.

## Strategy Capability

The production catalogue currently contains three code-defined strategy
definitions:

- `Legacy OHLC Trend`: a migration/bootstrap strategy, explicitly unvalidated,
  with a permissive non-zero price-change rule.
- `OHLC Range Expansion`: bounded to crypto/Kraken/M15 and explicitly not
  quantitatively validated.
- `Conservative Trend Following V1`: based on persisted Trend Context and
  explicitly disabled and unvalidated.

The evaluator registry is extensible, and definitions are persisted as the
runtime governance source of truth. That supports controlled addition of
strategies, but it is not autonomous discovery. There is no production
capability that searches a hypothesis space, generates candidate rules,
performs multiple-comparison control, or creates an accepted strategy
definition from discovered results.

Existing governance tests confirm that validation and operational activation
are separate gates. `StrategyDefinition.recordValidation` also states that
tests and pipeline proofs are not quantitative validation evidence and that no
normal production producer for such evidence exists yet.

## Backtesting And Profitability Validation

The repository contains replay and readiness infrastructure, but this must not
be confused with a generalized backtesting engine.

Implemented elements include:

- deterministic evidence cutoffs and fingerprints;
- bounded order-book replay dependencies;
- replay/fee readiness gates for the S1 research hypothesis;
- offline chronological dataset experiments;
- train/test purging and train-only preprocessing in the research ML package;
- an AI prediction benchmark using validated historical samples.

The research ML package explicitly states that its gross directional-return
proxy excludes fees, funding, slippage, execution and tradeability and must
not be reported as profitability. The frontend intentionally reports that
returns, win rate, P&L, Sharpe and backtest comparison are unavailable until
the evidence gates and preregistered evaluation workflow are complete.

Therefore the following capabilities are not evidenced as generally
available:

- strategy-agnostic event-driven backtest execution;
- complete order lifecycle simulation from signal to fill to exit;
- configurable slippage, latency, spread and partial-fill simulation;
- portfolio-level exposure and capital allocation replay;
- transaction-cost-complete net P&L;
- walk-forward or nested validation as a reusable strategy service;
- robustness testing across markets, regimes and parameter perturbations;
- statistically controlled selection among many discovered candidates.

The existing `backtest://` references in tests are evidence references or
fixtures, not proof that a production backtest engine exists.

## AI And Research Orchestration

The AI Engine is intentionally disabled by default and only supports shadow
mode through the public service contract. Non-shadow execution is rejected
until a canonical write flow exists.

`ExecutionStore` is process-memory state backed by a thread pool. It provides
bounded asynchronous execution, idempotency by execution fingerprint,
cancellation and provenance metadata, but it is not a durable research
experiment store or a promotion authority.

Market Intelligence reports AI status and evidence references, while the
research report explicitly states that it is advisory, does not authorize a
trade, and produces no calibrated probability or profitability claim.

The resulting boundary is appropriate: AI can interpret and summarize
structured evidence, but it does not override deterministic Thesis, risk or
human authorization. It also means AI cannot currently serve as the missing
autonomous strategy-discovery or validation layer.

## Runtime EUR/USD Observation

Read-only runtime projections for the authorized FTMO cTrader account returned:

- account connection: `CONNECTED`;
- account status: `CONNECTED_BUT_FACTS_INCOMPLETE`;
- execution allowed: `false`;
- risk evaluation allowed: `false`;
- one open EUR/USD BUY position with no stop loss;
- balance available in USD, but equity, free margin, used margin and
  unrealized P&L unavailable;
- account completeness: `INCOMPLETE`;
- market state for the cTrader EUR/USD CFD: `OPEN` and `tradable=true` in the
  catalogue projection;
- position valuation status: `UNKNOWN_MARKET`.

This is useful evidence that the account and market are discoverable, but it is
not sufficient for a valid risk decision or execution. The missing account
facts correctly keep the execution boundary blocked. The open position without
a stop loss is an observed account-protection fact, not a recommendation or a
permission to amend it.

The market catalogue also contains multiple EUR/USD records across providers
and product types. They must remain distinct until provider identity and
product type establish equivalence; the audit does not merge them.

## Maturity Assessment

| Area | Maturity | Main limitation |
|---|---|---|
| Data provenance and cutoff semantics | Strong foundation | Runtime coverage remains market/provider dependent |
| Deterministic analysis | Strong foundation | Capability breadth and external evidence vary |
| Market Thesis | Governed foundation | Missing liquidity/volume and incomplete live evidence can block it |
| Strategy catalogue | Early production | Small, partly disabled/unvalidated, code-backed catalogue |
| Strategy discovery | Not available | No candidate-generation and selection workflow |
| Backtesting | Partial research support | No generalized cost-complete portfolio simulator |
| AI research | Shadow-mode foundation | No durable execution store or authoritative promotion flow |
| Runtime Forex research | Partial/degraded | Quote/tradeability and account facts are incomplete |
| Promotion governance | Strong boundary | Accepted quantitative validation producer is still missing |

## Gaps To Resolve Before Claiming Autonomous Research

1. Define and implement a bounded candidate/hypothesis model that can be
   reproduced from versioned inputs.
2. Provide a generalized historical evaluator with event ordering, fills,
   costs, slippage, latency, funding and protection semantics.
3. Add walk-forward, holdout and robustness protocols with multiple-testing
   controls.
4. Persist experiment manifests, candidate results, datasets, code/config
   fingerprints and approval lineage durably.
5. Produce accepted strategy-validation evidence bound to an exact strategy ID
   and version.
6. Keep `VALIDATED` and `ENABLED` as separate human-governed transitions.
7. Improve EUR/USD runtime evidence so quote freshness, bid/ask/spread and
   account valuation facts are complete enough for deterministic evaluation.

These are audit findings and sequencing recommendations, not implementation
authorization.

## Final Conclusion

The project has done the important foundational work correctly: deterministic
evidence, provenance, cutoff discipline, Thesis fail-closed behavior,
strategy governance, bounded offline research and AI authority boundaries.

The missing capability is not a single disabled flag. It is the complete
research loop from hypothesis generation through reproducible, cost-complete,
out-of-sample validation and governed promotion. Current evidence supports
calling the system a governed research and market-intelligence foundation,
not an autonomous strategy-discovery engine.

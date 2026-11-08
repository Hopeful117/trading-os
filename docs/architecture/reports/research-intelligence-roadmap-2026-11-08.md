# Research Intelligence Roadmap

**Date:** 2026-11-08
**Status:** proposed sequence; requires human approval before implementation

## Phase 1: Close the Trust Boundary

### Story R1: Authorize Agent Research End-to-End

Integrate Gateway `AgentPrincipal` propagation with Market Intelligence research start/status/report ownership and enforce the exact `RESEARCH_SCOPE` grant. Add authenticated positive, expired-grant, wrong-scope, replay and ownership tests. Preserve default-deny behavior and audit identity/correlation.

### Story R2: Prove Research Lifecycle Observability

Expose and validate request, claim, checkpoint, requeue, partial, failure and terminal report states. Add metrics and correlation identifiers sufficient to diagnose queue saturation, retries and AI branch failures.

## Phase 2: Make Historical Evidence Usable

### Story R3: Populate Versioned Historical Research Bundles

Produce controlled exports with continuity, cutoff, missingness, provenance and outcome completeness gates. Include effective sample size, overlapping horizons and unavailable-label handling.

### Story R4: Complete Cost-Aware Replay

Implement deterministic replay contracts for spread, fees, slippage/impact, latency, partial fills, cancellations, funding/borrow and ambiguous broker outcomes. Keep replay separate from Risk authority and live execution.

## Phase 3: Evaluate Intelligence

### Story R5: Controlled Hybrid Research Evaluation

Run deterministic-only, initial-AI and adaptive-final-synthesis paths over identical evidence. Measure evidence-grounding, completeness, contradiction handling, latency, failure rate and reviewer usefulness.

### Story R6: Strategy and Scanner Research Link

Define how scanner observations, opportunities, Market Thesis, research reports and strategy matches reference one another without making AI or research a direct execution authority.

## Phase 4: ML Readiness

### Story R7: Model Provenance and Registry Contract

Add model/feature/dataset identity, training interval, code version, evaluation protocol, calibration, approval status, deployment version and rollback metadata. Do not enable live inference until readiness gates pass.

### Story R8: Production Evaluation and Drift Monitoring

Add walk-forward evaluation, regime/instrument sensitivity, calibration monitoring, data-quality alerts and drift handling. Report uncertainty and abstention explicitly.

## Explicit Non-Goals

- autonomous trading;
- AI override of deterministic Risk;
- profitability claims from OHLC-only experiments;
- unbounded tool use by AI or MCP;
- weakening authorization to make a demo pass;
- replacing Java canonical truth with notebook or model output.

## Ordering Rationale

Authorization and historical evidence precede more intelligence. Cost realism precedes profitability interpretation. Controlled comparison precedes productizing adaptive AI. Model lifecycle work comes only after the dataset and evaluation contracts are demonstrably satisfied.

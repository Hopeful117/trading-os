# Hybrid Research Architecture Recommendation

**Date:** 2026-11-08
**Decision status:** recommendation for human approval; no architecture changed

## Recommendation

Retain a deterministic-first hybrid architecture. Do not replace the capability engine with an AI-first or autonomous research planner.

```text
Canonical market data
  -> deterministic context and capabilities
  -> versioned Market Thesis
  -> bounded AI interpretation
  -> Java-authorized deterministic follow-up
  -> evidence-grounded final synthesis
  -> advisory report
```

## Why

The current design already provides the controls required for trustworthy research: canonical identity, cutoffs, freshness, explicit missingness, provenance, capability versions, bounded follow-up, evidence-reference validation and persisted lifecycle state. These controls are more valuable than earlier AI invocation.

AI may interpret structured evidence, compare hypotheses, explain contradictions and request a bounded follow-up. It must not acquire data, alter canonical facts, choose arbitrary capabilities, override deterministic findings, evaluate Risk, or execute trades.

## Controlled Alternative

If the product needs useful reports when a complete thesis is unavailable, introduce an explicit `RESEARCH_READY` context policy rather than weakening the existing thesis gate implicitly. Compare three paths over identical evidence:

1. deterministic-only;
2. initial AI interpretation;
3. adaptive follow-up plus final synthesis.

Measure completeness, evidence-grounding validity, contradiction handling, latency, failure rate and human usefulness. Do not use execution P&L as the first acceptance metric.

## Mandatory Gates

- canonical market/instrument identity;
- source cutoff and temporal validity;
- freshness and provenance;
- missing, stale and contradictory evidence;
- Java authorization of every adaptive capability;
- bounded call, context, follow-up and output budgets;
- validation of every AI evidence reference;
- independent deterministic Risk for concrete TradePlans;
- human authorization before execution.

## Optional Research Inputs

Order book, advanced liquidity, non-blocking news, tradeability and candidate eligibility may be optional for informational research when the report preserves their absence. They remain mandatory or independently evaluated where a concrete execution decision requires them.

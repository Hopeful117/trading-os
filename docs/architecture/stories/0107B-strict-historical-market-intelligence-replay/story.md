# Story 0107B - Strict Historical Market Intelligence Replay

## Objective

Provide the first trustworthy historical Market Intelligence replay slice:

```text
strict captured OHLC evidence
    -> IntelligenceContext @ T
    -> OHLC Temporal
    -> Market Structure
    -> Trend Context
```

## Scope

- Explicit `LIVE_CURRENT` and `STRICT_OBSERVED` evidence policies.
- Provider-neutral historical OHLC client and DTOs.
- Strict captured-evidence validation at the canonical cutoff.
- Shared OHLC evidence sources for live and strict acquisition.
- Historical OHLC and Trend Context contributor integration.
- Cutoff and evidence-policy persistence through `AnalysisExecution`.
- Bounded historical provenance and context fingerprint participation.
- Strict capability planning without current Market Snapshot requirements.

## Temporal Invariants

Strict OHLC evidence must be:

```text
closed = true
synthetic = false
origin = CAPTURED
closeTime <= evaluationCutoff
knownAt <= evaluationCutoff
```

No strict replay path calls the live provider as fallback. All Trend Context
roles use the same cutoff.

## Non-Goals

Historical quote/snapshot, MarketState, News, economic-event revisions,
backfill, outcomes, strategy backtesting, ML, LLM, batch replay, and frontend
changes are deferred.

# Story 0029 — Code Review

**Date**: 2026-08-25
**Reviewer**: Implementation Engineer (self-review)
**Status**: APPROVED

---

## Domain boundary

- `OpportunitySetupSnapshot` records *what was true at detection*: price context, evaluator description, trigger facts, match instant. No entry/stop/target/sizing/order concepts anywhere in the Opportunity chain — verified by the existing `commandContainsKnowledgeButNoExecutionOrTradePlanData` test (record components checked reflectively) plus runtime evidence: TradePlan entry 369.42 ≠ snapshot referencePrice 367.05.
- `TradePlan` remains sole owner of execution parameters; no planning code touched.

## Determinism

- Description comes from `StrategyEvaluation.explanation()` — evaluator-owned, strategy-specific logic stays in evaluators; factory contains zero strategy branching (proven by fake-strategy test).
- No wall-clock injection in snapshot content; identical inputs produce an identical snapshot (dedicated test).
- Reference price is read from observation evidence inside the same pipeline transaction — never a retroactive Market Data lookup.

## Historical truth

- Snapshot persisted in payload JSON at creation; GET paths perform pure deserialization (`JpaTradingOpportunityRepository.domain()`).
- `OpportunityBuilder.transition` copies `previous.setup()` verbatim — status changes never mutate setup facts (test: transition preserves verbatim).
- Legacy rows deserialize to null via Jackson default and are surfaced as `"setup": null` / hidden UI section (tests + runtime check on pre-0029 row).

## Strategy extensibility

- Triggers are a thin projection of `ConditionResult` values verbatim; condition IDs are evaluator-chosen business-readable strings. Adding a strategy requires only its own evaluator + definition — no shared-code edits. Fake-strategy test demonstrates the generic path.

## Compatibility

- REST additive: 18 historical fields untouched; `setup` nullable. Gateway routes unchanged (shape flows through existing `/api/v1/opportunities/**` route).
- Persistence: payload JSON absorbs the field with no DB migration; legacy payload test proves backward compatibility byte-for-byte on old JSON.
- Angular model optional field; list/detail degrade honestly.

## Concerns reviewed

1. **MATCH with empty conditionResults** would fail snapshot construction → pipeline transaction rolls back loudly. Accepted deliberately: creating an unexplainable opportunity silently would violate the trader-value contract. Both production evaluators always emit conditions on MATCH.
2. **`passed` flag dropped from triggers** — correct: persisted StrategyMatch exists only for MATCH evaluations, so all recorded conditions passed. Documented in javadoc.
3. **Description embeds validity timestamp** (legacy evaluator) — deterministic given same inputs; acceptable per Story determinism requirement.
4. **Nullable referencePrice inside snapshot** — defensive for future price-less strategies; both-null-or-error invariant prevents ambiguous half-price snapshots.

## Verdict

**APPROVED** — focused, deterministic, boundary-preserving, well-tested, runtime-proven.

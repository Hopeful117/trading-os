# AI Research Final Closure - 2026-11-12

## Verdict

`VALIDATED_WITH_NON_BLOCKING_LIMITATIONS`

The AI Research milestone is operationally validated for the bounded,
advisory-only workflow exercised by the second controlled E2E. It is ready for
human review and Git integration, but not for claiming that adaptive final
synthesis is complete under the older Story 0212 contract.

## E2E Evidence

- Execution: `1cce4a64-b5d2-4815-9100-f77c74808c28`
- Markets: EUR/USD and GBP/USD cTrader CFD records
- Lifecycle: `REQUESTED` -> `RUNNING` -> `COMPLETED`
- AI status: `COMPLETED` independently for both markets
- AI budget: one invocation per eligible market; no unbounded retry observed
- Provenance: provider, model, execution ID, classification, shadow mode and
  timestamps available for both markets
- Freshness: thesis `STALE`, live evidence `CURRENT` for both markets
- Interpretation: both markets returned `NO_TRADE` / `INSUFFICIENT_DATA`
- Persistence/retrieval: completed report available through the authorized
  report path
- Trading safety: no TradePlan, risk evaluation, approval, broker operation,
  position change, account mutation, scope change or grant change

The E2E used the rebuilt Java and Python services. No additional live research
execution was performed during this review.

## `finalAiInterpretation`

### Classification

`EXPECTED_OPTIONAL` for the completed E2E, with a documented contract
inconsistency for adaptive follow-up executions.

### Evidence

- Declared as `MarketResearchResult.finalAiInterpretation` in
  `ResearchReport.java`, so it is a per-market optional field.
- Initialized to `null` by `ResearchApplicationService`; no mapper overwrites
  or drops a populated value.
- `HttpAiAnalysisCapability.analyzeFinal(...)` and the Python
  `FINAL_SYNTHESIS` contract still exist.
- The current orchestration does not call `analyzeFinal(...)`. When a bounded
  deterministic follow-up is collected, it records
  `FINAL_SYNTHESIS_SKIPPED_BY_MARKET_AI_BUDGET` and preserves follow-up evidence.
- Legacy `ResearchReport.MarketResearchResult` constructors default the field to
  `null`, so old persisted JSON remains readable.
- `ResearchEvaluation` handles a null final interpretation explicitly.
- No frontend consumer of this field was found.

The current E2E had no adaptive follow-up request, so null is valid for this
execution. The older Story 0212 implementation report states that a final
synthesis should follow an authorized adaptive follow-up. The current
one-invocation-per-market policy intentionally changes that behavior. This is
not a mapping defect, but it is a contract decision that needs a separate
approved story if final synthesis is required again.

## Implementation Review

### Included Research changes

- Java per-market AI budget and explicit status/diagnostic handling.
- Java/Python provenance contract and runtime classification.
- Separate thesis and live-evidence freshness fields and timestamps.
- Additive report compatibility constructors and JSON persistence behavior.
- Java adapter mapping tests and ResearchApplicationService regression tests.
- Python provenance, fallback, mock and secret-safety tests.
- Related Research implementation, diagnosis and E2E validation documents.

### Compatibility and safety

- No database migration was introduced; reports remain bounded JSON in the
  existing `report` column.
- Existing request scope size, authorization boundary and idempotency behavior
  were not changed.
- AI remains advisory-only; no deterministic risk or execution authority was
  added.
- Provider provenance is derived from the provider path that actually ran, and
  no token, prompt or raw provider payload is persisted.

### Scheduled warnings

Pre-existing scheduler warnings were observed for forbidden replay artifact
population, feature snapshot conflicts and unavailable market-data discovery.
They did not prevent the controlled E2E from acquiring current live evidence
or completing both AI interpretations. They are non-blocking for this closure,
but should be tracked separately because they may reduce evidence completeness
for other research modes.

## Validation

Commands executed:

```text
../trading-core/mvnw -q -Dtest=ResearchApplicationServiceTest,ResearchEvaluationTest,HttpAiEngineAdapterTest test
```

Result: 27 tests passed, 0 failures, 0 errors, 0 skipped.

```text
../trading-core/mvnw -q test
```

Result: complete `market-intelligence` test suite passed. Existing Spring,
Mockito/Byte Buddy, H2/Flyway, service-discovery and expected concurrency
constraint warnings were emitted; no test failure occurred.

```text
.venv/bin/python -m pytest -q
```

Result: 19 passed, 0 failed, 0 skipped.

```text
git diff --check
```

Result: passed.

No paid external AI provider was invoked by automated tests. No new live
research execution was performed during this review.

## Git Review

### Include in Research closure commit

- Modified `ai-engine/app/**` provenance/runtime files and their tests.
- Modified Research Java adapter, port, service, domain and test files.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/AiProvenance.java`.
- `docs/architecture/stories/0208-trading-research-agent-foundation-mcp-integration/implementation-report.md`.
- `docs/validation/ai-research-quality-diagnosis-2026-11-12.md`.
- `docs/validation/ai-research-reliability-implementation-2026-11-12.md`.
- `docs/validation/ai-research-first-e2e-validation-2026-11-12.md`.
- This closure report.

### Exclude from this Research closure commit

- `docs/architecture/reports/trading-os-weekly-ai-workflows-preflight-2026-11-12.md`:
  broader workflow preflight, not part of the Research implementation.
- `docs/validation/mcp-list-markets-failure-investigation-2026-11-12.md`:
  MCP market-discovery investigation, useful background but separate scope.

No accidental source changes were found in broker, trading, risk, account,
Gateway or frontend modules. No migration ordering concern was found.

## Follow-ups

1. Resolve the Story 0212 contradiction: either formally retain the
   one-invocation-per-market policy and close final synthesis as deferred, or
   approve a new bounded multi-phase AI budget and restore terminal synthesis.
2. Investigate scheduled `Forbidden` replay population and feature snapshot
   identity conflicts separately; do not change them as part of this closure.
3. Add a future contract test for the chosen adaptive final-synthesis policy.

## PAPER Readiness

The Research milestone is ready to hand off to a separate PAPER execution
workflow, subject to the normal human approval gate. This closure produced no
TradePlan, risk evaluation or execution authorization. The PAPER workflow must
still independently validate its own scope, deterministic risk checks, paper
execution boundary and human approval before any simulated order is created.

## Suggested Commit Message

```text
feat(research): validate bounded per-market AI research provenance
```

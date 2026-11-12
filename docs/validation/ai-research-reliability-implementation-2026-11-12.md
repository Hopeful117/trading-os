# AI Research Reliability Implementation - 2026-11-12

## Outcome

Implemented the approved reliability changes for the Java Market Intelligence
Research workflow and Python AI Engine. The change remains advisory-only: it
does not authorize TradePlans, Risk decisions, execution intents, broker calls,
or trades.

No live research execution was started after the implementation. No grants,
scopes, credentials, provider configuration, or historical reports were
modified.

## Root Causes Addressed

### Multi-market AI skip

`ResearchApplicationService` no longer uses the research-wide `initialAiUsed`
gate. AI eligibility and budget are evaluated for each market. The explicit
Java policy is:

- maximum markets remains two, preserving the existing public request contract;
- maximum AI invocations per research is two;
- maximum AI invocations per market is one;
- a failed first market does not consume eligibility for the next market;
- no second provider call is made for final AI synthesis after a market's one
  invocation has been used;
- skipped markets receive explicit diagnostics.

The report distinguishes `COMPLETED`, `FAILED`, `DISABLED`, `NOT_REQUESTED`,
`SKIPPED_BY_BUDGET`, and `SKIPPED_INELIGIBLE` at market level. Existing string
status fields were retained for compatibility rather than introducing a new
public enum migration.

### Freshness conflation

The existing `freshness` field retains its prior thesis-freshness semantics.
Additive market fields now expose:

- `thesisFreshness`;
- `evidenceFreshness`;
- `thesisEvaluatedAt`;
- `thesisValidFrom`;
- `thesisValidUntil`;
- `evidenceEvaluationCutoff`;
- `evidenceSourceOccurredAt`;
- `evidenceFetchedAt`.

Evidence freshness is derived from the deterministic analysis context section
statuses. Missing, unavailable, unsupported, or incomplete evidence is not
reported as current. Stale evidence remains stale. Missing timestamps remain
missing. The existing deterministic freshness/status contracts are reused; no
new time threshold was introduced.

### AI provenance ambiguity

The Python result contract now returns non-sensitive per-execution provenance.
The classification is produced from the provider path that actually ran, not
from configuration alone. Java maps and persists the provenance per market.

Supported classifications:

- `EXTERNAL_MODEL`;
- `INTERNAL_DETERMINISTIC`;
- `FALLBACK`;
- `MOCK`;
- `NOT_INVOKED`;
- `UNKNOWN`.

Successful configured OpenAI/ChatGPT provider execution is classified as
`EXTERNAL_MODEL`. Explicit mock execution is `MOCK`. A successful fallback is
`FALLBACK`. Failed provider execution is `UNKNOWN` because no completed model
inference exists. Provider name, model, execution ID, shadow mode, invocation
and completion timestamps, fallback indicator, sanitized failure reason, and
contract version are retained when available.

Secrets, prompts, access tokens, raw provider payloads, and account-sensitive
data are not stored in provenance.

## Files Changed

### Java

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/ResearchApplicationService.java`
  - per-market AI budget/status decisions;
  - evidence freshness derivation and timestamps;
  - per-market provenance persistence;
  - one-provider-call-per-market enforcement.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/research/ResearchReport.java`
  - additive freshness and provenance fields;
  - compatibility constructors for existing reports and fixtures.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/AiProvenance.java`
  - Java provenance value object.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/AiStructuredResult.java`
  - carries AI provenance through the port boundary.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/capability/CapabilityAnalysisResult.java`
  - carries provenance through the capability result.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/capability/HttpAiAnalysisCapability.java`
  - preserves adapter provenance.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/ai/AiEngineResponses.java`
  - additive Python result provenance mapping.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/ai/HttpAiEngineAdapter.java`
  - maps Python provenance without inferring it.
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/research/ResearchApplicationServiceTest.java`
  - multi-market, failure isolation, status, budget, freshness, and compatibility tests.
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/adapter/ai/HttpAiEngineAdapterTest.java`
  - provenance mapping regression test.

### Python

- `ai-engine/app/domain/models.py`
  - provenance classification and result contract.
- `ai-engine/app/infrastructure/execution_store.py`
  - runtime provenance construction.
- `ai-engine/app/infrastructure/provider_registry.py`
  - thread-local fallback classification and sanitized fallback reason.
- `ai-engine/app/infrastructure/providers/chatgpt.py`
- `ai-engine/app/infrastructure/providers/openai.py`
- `ai-engine/app/infrastructure/providers/mock.py`
  - provider execution classifications.
- `ai-engine/app/main.py`
  - passes shadow-mode runtime state into execution results.
- `ai-engine/tests/test_api.py`
- `ai-engine/tests/test_provider_runtime.py`
  - mock, fallback, shadow-mode, serialization, and secret-safety coverage.

### Documentation

- `docs/architecture/stories/0208-trading-research-agent-foundation-mcp-integration/implementation-report.md`
  - updated reliability contract and compatibility behavior.
- This report.

## Architecture Decisions

No ADR conflict was found. The implementation preserves the accepted boundary:

```text
MCP/Gateway
    -> Java ResearchApplicationService
       -> deterministic evidence
       -> Java-owned AI budget and eligibility
       -> AiEnginePort
          -> Python provider/runtime
       -> persisted ResearchReport
```

Java remains authoritative for orchestration, budget, lifecycle, validation,
and persistence. Python remains a bounded advisory reasoning provider. No AI
result becomes a deterministic fact, Risk decision, or execution authority.

The approved one-provider-call-per-market policy required a deliberate change
to the existing adaptive final-synthesis path. Deterministic follow-up evidence
may still be collected, but a second AI final-synthesis call is skipped and
recorded as `FINAL_SYNTHESIS_SKIPPED_BY_MARKET_AI_BUDGET`.

## Persistence and Compatibility

No database migration was required. Research reports are persisted as bounded
JSON, and the new fields are additive. Previously persisted reports remain
readable with absent fields represented by compatibility defaults. Historical
reports are not rewritten.

The existing REST and MCP research endpoint names and idempotency behavior were
not changed. No frontend changes were required because the new report fields
are additive and existing consumers can continue using `freshness`.

## Validation

Executed commands:

```text
mvn -q -f market-intelligence/pom.xml -Dtest=ResearchApplicationServiceTest,HttpAiEngineAdapterTest test
```

Result: passed, 13 focused Java tests.

```text
mvn -q -f market-intelligence/pom.xml test
```

Result: passed for the complete Market Intelligence module test suite. Flyway
test migrations validated through schema version 26. The suite emitted existing
Spring, Mockito/Byte Buddy, H2/Flyway, and service-discovery warnings but no
test failures.

```text
ai-engine/.venv/bin/python -m pytest ai-engine/tests -q
```

Result: `19 passed`.

```text
git diff --check
```

Result: passed.

DevLog applicability was attempted for prior project context, but the DevLog
connection closed before returning a result. Repository source, the required
validation reports, and ADR-048/089/090/092 were used as authoritative context.

## Remaining Risks

- The external provider path is unit-tested with mocked provider calls. No paid
  or live external model request was made by the test suite.
- Provider correlation IDs remain null because the current provider adapters do
  not expose a safe correlation identifier in their response contract.
- The current public request contract accepts exactly two markets, so runtime
  `SKIPPED_BY_BUDGET` for a third market is covered by policy semantics rather
  than a public three-market request test.
- The final AI synthesis phase is now intentionally unavailable when it would
  violate the one-call-per-market policy. This is explicit in the report but
  should be reconsidered if a future policy permits multiple calls per market.
- Runtime deployment must load both the Java and Python contract changes before
  an operational E2E can verify end-to-end provenance.

## Final Review Outcome

The controlled execution `1cce4a64-b5d2-4815-9100-f77c74808c28` completed after
the Java and Python services were rebuilt. Both eligible markets completed one
independent AI interpretation within the configured budget:

- EUR/USD: `aiStatus=COMPLETED`, provider provenance present;
- GBP/USD: `aiStatus=COMPLETED`, provider provenance present;
- both markets: thesis freshness `STALE`, live evidence freshness `CURRENT`;
- both markets: `NO_TRADE` / `INSUFFICIENT_DATA`;
- no TradePlan, risk evaluation, broker operation, position or account mutation.

`finalAiInterpretation` is classified as `EXPECTED_OPTIONAL` for this execution:
the research request did not produce an adaptive follow-up request, and the
report field is per-market rather than a research-wide synthesis field. It is
declared in `ResearchReport.MarketResearchResult`, serialized through the
persisted JSON report, exposed by the normal report response, and consumed
defensively by `ResearchEvaluation`.

The field is not dropped by the Java/Python mapping. When an adaptive follow-up
is requested, the current one-invocation-per-market policy deliberately records
`FINAL_SYNTHESIS_SKIPPED_BY_MARKET_AI_BUDGET` and leaves the optional final field
null. This differs from the older Story 0212 contract, which described a
terminal final synthesis after follow-up. Resolving that policy contradiction
requires a separate approved decision/story; no new LLM invocation was added
in this closure.

Final validation classification: `VALIDATED_WITH_NON_BLOCKING_LIMITATIONS`.

## Deployment Considerations

Before validation, restart/redeploy the Market Intelligence and AI Engine
processes through the normal development workflow so both sides use the new
DTOs. Do not change provider credentials or runtime configuration as part of
this implementation. Confirm that the configured service authentication and
existing AI Engine shadow-mode policy remain active.

## Final E2E Prerequisites

1. Human-authenticated research scope and active `research:run` grant.
2. Exactly two authorized canonical market IDs.
3. Restarted Java and Python services with the new contracts loaded.
4. Existing provider credential/configuration independently verified by the
   operator, without recording secrets.
5. Human approval immediately before starting one controlled research request.
6. Post-run inspection confirming both markets have independent AI statuses,
   per-market provenance, separate thesis/evidence freshness, and no hidden
   budget skip.
7. Confirm no TradePlan, Risk, execution, broker, or position mutation occurred.

No deployment, live research, or trading operation was performed by this task.

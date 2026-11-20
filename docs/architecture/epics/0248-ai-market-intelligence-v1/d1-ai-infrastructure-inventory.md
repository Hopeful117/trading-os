# D1 - AI Infrastructure Inventory

## Scope

This inventory covers the existing AI Engine, its Market Intelligence adapter,
provider integrations, execution state, validation, security, and tests. It is
an ES-01 audit only. No production behavior is changed by this document.

## Inventory

| Area | Existing implementation | Status | Evidence |
|---|---|---|---|
| AI runtime and entry points | FastAPI application with health, submit, progress, result, and cancel endpoints | REUSE | `ai-engine/app/main.py:14-18,45-52,77-144` |
| Service authentication | Optional configuration gate, JWT issuer/audience/HS256 validation on internal endpoints | REUSE | `ai-engine/app/main.py:27-43`; `ai-engine/app/config.py:36-39` |
| AI contract | Strict Pydantic request/result/provenance models, camel-case serialization, forbidden extra fields | ADAPT | `ai-engine/app/domain/models.py:16-22,197-236,264-319` |
| Provider abstraction | Mock, OpenAI, and ChatGPT OAuth provider selection | REUSE | `ai-engine/app/infrastructure/provider_registry.py:45-58` |
| Provider fallback | Runtime fallback on `RuntimeError` or `ValueError`, with fallback provenance | REUSE | `ai-engine/app/infrastructure/provider_registry.py:12-43` |
| Structured output validation | Request invariants, status/result consistency, and provider-output schema validation | REUSE | `ai-engine/app/domain/models.py:197-236,306-312`; `ai-engine/app/application/service.py:21-76` |
| Evidence-reference validation | Supporting, contradictory, reconciliation, and hypothesis references are checked against authorized evidence | REUSE | `ai-engine/app/application/service.py:25-70` |
| AI Engine technical lifecycle | `ACCEPTED -> RUNNING -> COMPLETED/FAILED/CANCELLED` in an in-memory store and thread pool | REUSE with limits | `ai-engine/app/infrastructure/execution_store.py:37-61,63-106,131-163` |
| AI Engine retry | Provider client retry configuration exists; no AI Engine execution retry loop is verified | UNKNOWN/DEFER | `ai-engine/app/config.py:13-18`; no execution-store retry path found |
| Market Intelligence adapter | Submit, poll, result mapping, and cancel against the AI Engine | REUSE | `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/ai/HttpAiEngineAdapter.java:32-105` |
| AI capability orchestration | Polls the AI Engine for up to 30 seconds and cancels on timeout/interruption | ADAPT | `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/capability/HttpAiAnalysisCapability.java:54-117` |
| Existing AI modes | `PASSIVE`, `ACTIVE`, plus research phases; Epic modes are not present | ADAPT | `HttpAiAnalysisCapability.java:35-44`; `ai-engine/app/domain/models.py:150-153,205-209` |
| Durable technical execution state | Market Intelligence `AnalysisExecution` aggregate persisted in `analysis_executions` | REUSE | `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/execution/AnalysisExecutionService.java:21-58`; `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaAnalysisExecutionEntity.java:8-22` |
| Durable research history | Research executions persist report, status, attempts, stage, and failure metadata | REUSE | `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaResearchExecutionEntity.java:11-98`; `ResearchApplicationService.java:119-220` |
| Opportunity lifecycle | Durable versioned opportunities with explicit lifecycle policy | REUSE | `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaTradingOpportunityEntity.java:9-28`; `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/opportunity/OpportunityLifecyclePolicy.java:7-20` |
| Deterministic opportunity eligibility | Passive candidate evaluator checks status, validity, strategy match, observations, thesis, evidence, freshness, and market identity | REUSE | `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/passive/PassiveCandidateEvaluator.java:123-216` |
| Opportunity assessment mode | No verified Epic-specific `OPPORTUNITY_ASSESSMENT` orchestration | BUILD | Existing capability exposes only `PASSIVE`/`ACTIVE`; `HttpAiAnalysisCapability.java:35-44` |
| Diagnostic mode | No verified Epic-specific `DIAGNOSTIC` orchestration | BUILD | No matching Epic mode or workflow found in repository |
| Frontend Epic workflow | No verified Epic-specific AI review/approval flow | UNKNOWN | Targeted frontend search; no implementation evidence established |

## Reusable Boundaries

The smallest safe reuse boundary is the existing Market Intelligence durable
analysis lifecycle plus its AI Engine adapter. The AI Engine remains provider-
neutral and advisory. Provider payloads remain inside AI Engine; Market
Intelligence receives normalized results, provenance, and evidence references.
Deterministic eligibility and mode selection remain in Market Intelligence.

## Material Limitations

- The AI Engine execution store is in memory and is not restart-safe. This does
  not mean that all Market Intelligence execution history is in memory:
  `AnalysisExecution` and research executions are durably persisted.
- The current integration is centered on generic passive/active interpretation
  and research. Epic-specific `OPPORTUNITY_ASSESSMENT` and `DIAGNOSTIC` mode
  selection are not verified.
- The existing deterministic eligibility rules do not by themselves define the
  Epic policy for when an ineligible opportunity should receive a diagnostic.
- Provider availability and model behavior are environment-dependent; this
  audit does not claim live-provider validation.
- The repository does not prove a complete frontend review/approval flow for
  Epic 0248.

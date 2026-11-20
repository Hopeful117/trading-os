# D4 - Reuse, Adapt, Build Matrix

| Capability | Existing component | Current behavior | Epic 0248 requirement | Classification | Required change | Evidence |
|---|---|---|---|---|---|---|
| AI Engine HTTP API | FastAPI internal analysis endpoints | Submit, progress, result, cancel; shadow-mode only | Support Epic requests without provider leakage | REUSE | None at infrastructure level; add Epic mapping outside provider boundary | `ai-engine/app/main.py:77-144` |
| Pydantic contracts | Strict `InterpretationRequest` and `InterpretationResult` | Generic thesis/context/research phases | Represent Epic mode and output semantics | ADAPT | Extend only after Epic output contract approval | `ai-engine/app/domain/models.py:197-236,264-319` |
| Provider registry | `build_provider`, `FallbackLLMProvider` | Mock/OpenAI/ChatGPT selection and fallback | Same provider isolation and provenance | REUSE | Preserve fallback policy and provenance | `provider_registry.py:12-58` |
| Model configuration | `Settings` | Provider/model, limits, retry, fallback, auth configuration | Bounded Epic-specific policy | ADAPT | Define approved Epic limits; do not expose secrets | `config.py:5-39` |
| Prompt handling | Provider adapters and request model | Provider-specific translation exists | Epic-specific interpretation prompt/context | ADAPT | Add Epic prompt mapping inside existing provider boundary | `ai-engine/app/infrastructure/providers/` |
| Structured output validation | Pydantic plus `InterpretationService` | Schema and status/evidence checks | Validate Epic result types | ADAPT | Add Epic-specific invariants | `models.py:306-312`; `service.py:21-76` |
| Evidence validation | `InterpretationService` | Rejects unknown evidence references | Preserve grounding for Epic results | REUSE | Map ContextPack references into authorized evidence set | `service.py:25-70` |
| AI execution lifecycle | AI Engine `ExecutionStore` | In-memory async lifecycle | Provider invocation mechanics | REUSE | Accept restart limitation; do not create another AI orchestrator | `execution_store.py:37-163` |
| Durable execution lifecycle | Market Intelligence `AnalysisExecution` | Durable status, policy, idempotency, cancellation, recovery | Business execution tracking | REUSE | Associate Epic request/mode/context metadata if approved | `AnalysisExecutionService.java:48-58,278-337`; `JpaAnalysisExecutionEntity.java:8-22` |
| Market Intelligence AI adapter | `HttpAiEngineAdapter` | Maps result findings, provenance, and cancellation | Epic mode/result mapping | ADAPT | Map approved Epic modes and result projections | `HttpAiEngineAdapter.java:32-105` |
| Research orchestration | `ResearchApplicationService` | Durable research execution, deterministic gates, AI budget, partial reports | Reference pattern, not Epic owner | REUSE | Do not force Epic through comparative research semantics | `ResearchApplicationService.java:119-220,386-523` |
| Deterministic eligibility | `PassiveCandidateEvaluator` | Evaluates current candidate eligibility and diagnostics | Input to Java mode selection | REUSE | Reuse output; do not duplicate rules | `PassiveCandidateEvaluator.java:123-216` |
| ContextPack construction | Context assembly and `IntelligenceContext` | Deterministic context sections exist | Stable Epic snapshot identity/version | ADAPT | Define Epic mapping/versioning | Existing context assembler and tests |
| `OPPORTUNITY_ASSESSMENT` | None verified | No Epic mode | Analyze eligible opportunity | BUILD | Add Java-owned mode selection/orchestration | No matching implementation found |
| `DIAGNOSTIC` | None verified | No Epic mode | Explain blocked/non-exploitable opportunity | BUILD | Define diagnosable conditions and projection | No matching implementation found |
| Analysis persistence | `analysis_executions`, research reports, opportunity versions | Durable but not Epic-specific | Persist mode, opportunity version, context/result provenance | ADAPT | Reuse existing stores; approve missing metadata | Persistence entities and migrations |
| Frontend integration | Existing Angular application | Epic flow not verified | Present result and human review states | UNKNOWN | Inspect/define after API contract approval | No Epic-specific implementation evidence |
| Risk and execution | Trading Core/Risk boundaries | Deterministic and human-authorized | Must remain outside AI | REUSE | No AI authority expansion | `AGENTS.md:199-230,271-305` |

## Smallest Required Change Set

1. Java-owned deterministic mode selection using the existing opportunity and
   eligibility representations.
2. Epic-specific ContextPack and result mapping at the existing adapter seam.
3. Durable association of analysis mode, opportunity version, context identity,
   and AI execution reference using existing Market Intelligence persistence
   boundaries.
4. Explicit policy for diagnostic eligibility and cross-service recovery.

No second provider runtime, AI execution orchestrator, eligibility engine, or
risk/execution path is justified by the current evidence.

## Non-Goals

- No autonomous trading loop.
- No direct AI-to-broker path.
- No replacement of deterministic risk evaluation.
- No provider-specific contract exposed by Market Intelligence or the frontend.
- No claim that live OpenAI/ChatGPT calls have been validated by this audit.

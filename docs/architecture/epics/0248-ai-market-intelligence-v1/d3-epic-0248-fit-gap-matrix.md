# D3 - Epic 0248 Fit-Gap Matrix

| Epic capability | Existing fit | Gap | Recommendation | Evidence |
|---|---|---|---|---|
| AI-assisted market interpretation | Strong | Epic-specific prompts and output semantics are not yet evidenced | Adapt existing AI capability | `ai-engine/app/application/service.py`; `HttpAiAnalysisCapability.java` |
| Deterministic ContextPack | Partial | Existing deterministic context sections exist, but no verified Epic-specific snapshot contract | Adapt existing context assembly; do not duplicate it | `IntelligenceContextAssembler`; `IntelligenceContext` and context tests |
| Evidence-grounded output | Strong foundation | Need Epic-specific evidence taxonomy and acceptance rules | Adapt validation and report mapping | `ai-engine/app/domain/validation.py`; `ResearchReport.java` |
| Opportunity eligibility | Strong deterministic foundation | Existing rules are passive-candidate rules; Epic policy for analysis requests is not defined | Reuse evaluator as an input, do not duplicate rules | `PassiveCandidateEvaluator.java:123-216`; `OpportunityLifecyclePolicy.java:7-20` |
| Opportunity assessment | Partial | No verified `OPPORTUNITY_ASSESSMENT` mode or complete opportunity-to-AI orchestration | Adapt existing execution and adapter boundaries | `HttpAiAnalysisCapability.java:31-44`; `AnalysisExecutionService.java:48-58` |
| Diagnostic mode | Not found | No verified `DIAGNOSTIC` mode or representation of diagnosable-but-ineligible opportunity | Build bounded orchestration after policy approval | No matching mode/workflow found |
| Structured response schema | Strong foundation | Epic-specific discriminated output contract missing | Adapt Pydantic/domain contracts | `ai-engine/app/domain/models.py` |
| Async execution | Strong foundation | AI Engine store is process-local, while Market Intelligence lifecycle is durable; cross-service recovery is not atomic | Reuse Market Intelligence lifecycle and adapter; define recovery/idempotency policy | `execution_store.py:37-61`; `AnalysisExecutionService.java:48-58,278-337` |
| Provider abstraction | Strong | Live provider policy and cost controls require Epic configuration | Reuse, configure conservatively | `provider_registry.py`; `config.py` |
| Security and service auth | Strong foundation | Epic resource authorization must be mapped explicitly | Reuse auth; add resource checks where needed | `main.py`; `security.py` |
| Human review | Partial | Frontend Epic review/approval flow not evidenced | Build frontend workflow after contracts are approved | `trading-os-web/src/app/` |
| Risk integration | Existing deterministic boundary | No AI authority gap if preserved | Do not move risk decisions into AI | ADR-101; risk module |
| Execution integration | Existing Trading Core boundary | No direct AI execution path should be added | Preserve human authorization and Trading Core orchestration | `trading-core`; ADR-089 |
| Durable auditability | Partial | Durable technical execution and research history exist, but Epic-specific mode/context/result history is not verified | Adapt existing persistence ownership; approve any new record requirements | `JpaAnalysisExecutionEntity.java:8-22`; `JpaResearchExecutionEntity.java:11-98` |

## Fit Classification

- **Reuse:** provider registry/fallback, structured and evidence validation,
  service authentication, deterministic opportunity eligibility, durable
  Market Intelligence execution lifecycle, and the existing adapter boundary.
- **Adapt:** AI request/output contracts, ContextPack mapping, adapter mode
  mapping, result persistence, and frontend presentation.
- **Build:** only the Epic-specific mode-selection/orchestration layer and the
  missing diagnostic/assessment projections.
- **Unresolved:** whether Epic V1 requires durable cross-service recovery of an
  AI provider invocation, and what blocked conditions are diagnosable versus
  request-rejected.

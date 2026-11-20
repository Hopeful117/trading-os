# D6 - Audit Evidence Index

## Repository and Governance

| Evidence | Relevance |
|---|---|
| `AGENTS.md` | Architecture, human authority, deterministic risk, testing, and documentation rules |
| `TOOLS.md` | Local validation commands and AI/provider safety constraints |
| `docs/architecture/README.md` | Canonical architecture-document conventions |
| `docs/architecture/adr/ADR-076-market-thesis-authority-and-evidence-composition.md` | Market Thesis authority and evidence composition |
| `docs/architecture/adr/ADR-089-native-trading-research-agent-mcp-responsibility-boundary.md` | AI, Market Intelligence, MCP, and execution responsibility boundaries |
| `docs/architecture/adr/ADR-101-proposed-autonomous-trading-research-lifecycle-and-governance.md` | AI governance and non-authoritative recommendations |

## Claim Index

| Claim ID | Claim | Source | Lines | Evidence type | Confidence |
|---|---|---|---|---|---|
| E-001 | AI Engine exposes authenticated submit/progress/result/cancel endpoints and creates the runtime store at startup | `ai-engine/app/main.py` | L14-L18, L27-L43, L77-L144 | Implementation | High |
| E-002 | AI Engine request/result models are strict and enforce context/status invariants | `ai-engine/app/domain/models.py` | L16-L22, L197-L236, L293-L312 | Implementation | High |
| E-003 | Evidence references returned by providers are validated against authorized evidence | `ai-engine/app/application/service.py` | L21-L70 | Implementation | High |
| E-004 | Provider fallback catches runtime/value failures and records fallback provenance | `ai-engine/app/infrastructure/provider_registry.py` | L12-L43 | Implementation | High |
| E-005 | AI Engine execution state is process-local and uses a thread pool | `ai-engine/app/infrastructure/execution_store.py` | L37-L61 | Implementation | High |
| E-006 | AI Engine runs timeout checks and produces explicit completed/failed/cancelled results | `ai-engine/app/infrastructure/execution_store.py` | L63-L106 | Implementation | High |
| E-007 | AI Engine cancellation is event-based and active provider interruption is not proven | `ai-engine/app/infrastructure/execution_store.py` | L146-L163 | Implementation + limitation | High |
| E-008 | Market Intelligence adapter maps submit, polling, results, provenance, and cancel | `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/ai/HttpAiEngineAdapter.java` | L32-L105 | Implementation | High |
| E-009 | Market Intelligence capability polls for 30 seconds and cancels on timeout/interruption | `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/capability/HttpAiAnalysisCapability.java` | L76-L117 | Implementation | High |
| E-010 | Existing capability exposes PASSIVE and ACTIVE, not Epic-specific modes | `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/capability/HttpAiAnalysisCapability.java` | L31-L44 | Implementation | High |
| E-011 | Durable Market Intelligence execution has idempotent creation, dispatch, lookup, and cancellation | `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/execution/AnalysisExecutionService.java` | L21-L58, L278-L337 | Implementation | High |
| E-012 | Durable execution transitions and terminal states are explicitly modeled | `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/execution/AnalysisExecution.java` | L15-L49, L144-L209 | Implementation | High |
| E-013 | Durable execution persistence stores status, expiry, market, policy, and serialized payload | `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaAnalysisExecutionEntity.java` | L8-L22 | Implementation | High |
| E-014 | Deterministic passive eligibility checks opportunity, thesis, observations, evidence, freshness, and market identity | `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/passive/PassiveCandidateEvaluator.java` | L123-L216 | Implementation | High |
| E-015 | Opportunity lifecycle transitions are deterministic and statuses are explicit | `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/opportunity/OpportunityLifecyclePolicy.java`; `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/opportunity/OpportunityStatus.java` | L7-L20; L3-L4 | Implementation | High |
| E-016 | Research execution is durable and records reports, attempts, stages, and failures | `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/ResearchApplicationService.java`; `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaResearchExecutionEntity.java` | L119-L220; L11-L98 | Implementation | High |
| E-017 | Versioned opportunities are persisted with status, identity, provenance, and analysis execution reference | `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaTradingOpportunityEntity.java` | L9-L28 | Implementation | High |
| E-018 | AI Engine execution retry is not established; configured provider retry is distinct | `ai-engine/app/config.py`; `ai-engine/app/infrastructure/provider_registry.py` | L13-L18; L34-L43 | Implementation + repository search | Medium |
| E-019 | Epic-specific opportunity assessment and diagnostic modes are not verified | Repository search | No matching implementation found | Negative evidence | Medium |

## AI Engine Evidence

| File | Evidence used |
|---|---|
| `ai-engine/app/main.py` | FastAPI endpoints, service boundary, authentication wiring |
| `ai-engine/app/application/service.py` | Provider result validation and evidence-reference enforcement |
| `ai-engine/app/domain/models.py` | Structured request/result/error contracts |
| `ai-engine/app/domain/validation.py` | Evidence and output validation |
| `ai-engine/app/infrastructure/execution_store.py` | Process-local execution lifecycle, timeout checks, fingerprint idempotency, and cancellation |
| `ai-engine/app/infrastructure/provider_registry.py` | Provider selection and fallback |
| `ai-engine/app/infrastructure/providers/` | Mock and external-provider adapters |
| `ai-engine/app/config.py` | Limits, timeout, retry, and shadow-mode configuration |
| `ai-engine/tests/` | Executed local AI validation |

## Market Intelligence Evidence

| File or area | Evidence used |
|---|---|
| `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/ai/HttpAiEngineAdapter.java` | HTTP boundary to AI Engine |
| `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/ai/AiEngineAnalysisRequest.java` | AI request construction |
| `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/capability/HttpAiAnalysisCapability.java` | Application capability and result mapping |
| `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/ResearchApplicationService.java` | Research execution orchestration |
| `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/research/ResearchReport.java` | Report, evidence, and provenance model |
| `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/execution/AnalysisExecutionService.java` | Durable technical execution creation, dispatch, recovery, and cancellation |
| `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/execution/AnalysisExecution.java` | Verified execution state transitions |
| `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/passive/PassiveCandidateEvaluator.java` | Deterministic opportunity eligibility and blocking diagnostics |
| `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/opportunity/OpportunityLifecyclePolicy.java` | Opportunity lifecycle authority |
| `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaAnalysisExecutionEntity.java` | Durable analysis execution schema mapping |
| `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaTradingOpportunityEntity.java` | Durable opportunity version mapping |
| `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaResearchExecutionEntity.java` | Durable research history mapping |
| `market-intelligence/src/test/` | Executed targeted Java validation |

## Validation Evidence

- `ai-engine`: `.venv/bin/python -m pytest` -> **20 passed**.
- `market-intelligence`: `../trading-core/mvnw -q -Dtest=HttpAiEngineAdapterTest,AiEngineAnalysisRequestTest,ResearchApplicationServiceTest,ResearchEvaluationTest test` -> **passed**.
- Additional lifecycle and eligibility evidence was inspected without modifying
  application code or tests.
- No live provider call was made.
- No cTrader or broker mutation was performed.
- No repository file outside this audit directory was modified.

## Evidence Limits

- DevLog context was unavailable due to `GROUNDING_REQUESTED_FILE_SCOPE`.
- Live provider behavior, production deployment topology, and frontend Epic 0248
  acceptance behavior are not proven by local tests.
- The AI Engine runtime store is process-local, but Market Intelligence has a
  separate durable technical execution aggregate and durable research history.
- Cross-service atomicity between durable Market Intelligence state and the
  AI Engine provider invocation is not established.
- Active provider cancellation, provider success followed by response-loss,
  and exact Epic diagnostic eligibility are not fully verified.

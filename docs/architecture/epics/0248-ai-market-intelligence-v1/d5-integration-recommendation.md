# D5 - Integration Recommendation

## Recommendation

Integrate Epic 0248 through the existing Market Intelligence to AI Engine
boundary and reuse the existing durable `AnalysisExecution` lifecycle. Keep AI
Engine responsible for bounded interpretation, structured output, provider
isolation, evidence-reference validation, and temporary provider-execution
state. Keep Market Intelligence responsible for deterministic eligibility,
Java-only mode selection, ContextPack assembly, domain projection, and durable
business-analysis records.

## Recommended Sequence

1. Define the deterministic inputs that distinguish an eligible opportunity
   from a blocked or non-exploitable one. Reuse `PassiveCandidateEvaluator`
   outputs; do not duplicate its rules.
2. Approve which blocked conditions are diagnosable and which reject the
   request, including missing resource and authorization behavior.
3. Define the Epic-specific ContextPack identity/version and structured outputs.
4. Extend the existing Java-to-AI contract minimally; Java selects the mode and
   the LLM cannot select or override it.
5. Associate the Epic mode, opportunity version, deterministic eligibility
   snapshot, ContextPack identity, AI execution reference, result, provenance,
   and failure metadata with existing durable Market Intelligence state.
6. Add the human-facing review workflow only after the contract and state model
   are approved.

## Architectural Guardrails

- AI output is a recommendation or explanation, never a business authority.
- Java selects `OPPORTUNITY_ASSESSMENT` or `DIAGNOSTIC`; the LLM never selects
  or overrides the mode.
- Evidence references must resolve to deterministic source material.
- Risk and execution remain in their existing deterministic services.
- Provider credentials, prompts, raw provider payloads, and provider errors stay
  inside AI Engine infrastructure.
- The AI Engine in-memory store must not be presented as durable business
  history. Market Intelligence owns durable analysis history.
- Provider success followed by response-delivery or persistence failure needs
  explicit idempotency/reconciliation handling before production claims.
- The system must remain useful with the mock provider and with AI disabled.

## Decision

The existing infrastructure is sufficient as a foundation for ES-02 without a
second execution orchestrator. It is not a complete Epic 0248 implementation.
Human approval is still required for the ContextPack contract, the diagnostic
eligibility policy, the durable analysis-history shape, and cross-service
recovery semantics.

## Ownership Boundary

| Concern | Owner | Current evidence/constraint |
|---|---|---|
| Eligibility and mode selection | Market Intelligence | Existing deterministic evaluator; Epic modes missing |
| Provider invocation and temporary state | AI Engine | In-memory `ExecutionStore`; restart-loss risk |
| Durable technical execution | Market Intelligence | `AnalysisExecution` and `analysis_executions` |
| Durable business-analysis history | Market Intelligence | Requires Epic-specific association/metadata decision |
| Risk and execution authority | Risk Domain, Trading Core, Broker Service | Must not move into AI |

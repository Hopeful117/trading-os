# Repository Analysis - Story 0248-03

## Status

Prepared for human approval. This document does not authorize implementation.

## 1. Scope Understanding

ES-03 is the first orchestration increment after ES-02. It must connect the immutable ContextPack and
result contracts to a Java-owned request boundary without adding durable business-history persistence,
provider activation, frontend behavior or execution authority.

The current repository already has reusable opportunity repositories, deterministic passive eligibility,
durable technical analysis execution and a provider-neutral AI adapter. The missing boundary is a
manual single-opportunity interpretation application service that loads an exact opportunity version,
selects a mode deterministically and preserves the ES-02 identity through request creation.

## 2. Current Architecture

### Opportunity loading

`TradingOpportunityRepository` exposes `find(OpportunityId, OpportunityVersion)`, `findLatest`, history,
active and exact-reference queries. `OpportunityController` currently exposes latest opportunity reads
under `/api/v1/opportunities`, but no single-version AI interpretation operation exists.

The repository therefore supports exact version loading without introducing a new persistence boundary.
Resource authorization and ownership must be resolved explicitly; a client-provided opportunity ID or
version cannot establish authority by itself.

### Deterministic eligibility

`PassiveCandidateEvaluator.evaluateOne` loads the exact opportunity version and returns an empty result
when the version is missing. It evaluates the existing opportunity, strategy match, observations,
Market Thesis, market identity and freshness rules, then adds `OPPORTUNITY_SUPERSEDED` when a newer
version exists.

Its `SingleEvaluation` exposes the deterministic `PassiveAwarenessProjection` and a market-data
availability flag. The evaluator is the existing source of truth and must be reused rather than
duplicated by ES-03.

### Technical execution

`AnalysisExecutionService` owns registration, idempotency lookup, dispatch, execution identity,
provenance, cancellation and durable lifecycle state. Its current `create` path is designed around
`IntelligenceAnalysisRequest` and existing `AnalysisExecutionMode` values such as `PASSIVE` and
`ACTIVE`. Those values must remain distinct from ES-02 `AiInterpretationMode`.

The service currently dispatches immediately after registration. ES-03 must not make an ambiguous AI
outcome look terminal or create a second execution identity. Any additional request metadata must be
additive and preserve the existing lifecycle boundary.

### AI adapter

`HttpAiAnalysisCapability` accepts existing `PASSIVE` and `ACTIVE` analysis execution modes and builds
an `AiAnalysisCommand` from `IntelligenceContext`, Market Thesis and the existing policy. The
`HttpAiEngineAdapter` maps that command to the provider-neutral `AiEngineAnalysisRequest`.

`AiEngineAnalysisRequest` currently transports a generic authorized context, thesis, policy, trace,
research mode, idempotency key and interpretation phase. It does not yet transport the ES-02
ContextPack identity/version/fingerprint or the Epic interpretation mode. This is a likely ES-04 wire
contract change. ES-03 should either stop at a provider-neutral orchestration seam or add only the
minimal additive mapping approved by the implementation plan.

### Existing public request boundary

`MarketIntelligenceController` exposes `POST /api/v1/intelligence/analyses` for generic intelligence
analysis. It creates a random execution ID and delegates directly to `AnalysisExecutionService`.
This endpoint accepts market-level analysis input, not an exact opportunity/version request. ES-03
should not overload this contract ambiguously; a dedicated opportunity interpretation request boundary
or an explicit additive endpoint is safer.

## 3. Required Ownership

| Concern | Owner | ES-03 treatment |
|---|---|---|
| Exact opportunity/version loading | Market Intelligence | Reuse repository port |
| Resource authorization | Market Intelligence/security boundary | Verify authenticated owner/resource scope |
| Eligibility and blocker diagnostics | `PassiveCandidateEvaluator` | Reuse output only |
| Mode selection | Java application/domain layer | Add one policy boundary after OD-01 approval |
| ContextPack construction | Market Intelligence | Reuse existing context assembly and ES-02 factory |
| Technical idempotency/lifecycle | `AnalysisExecutionService` | Extend minimally if required |
| Provider invocation | AI Engine adapter | Do not leak provider details |
| Risk and execution | Risk Domain/Trading Core/Broker Service | No changes |

## 4. Eligibility and Rejection Model

The following behavior is supported by current evidence and remains subject to OD-01 approval:

- missing exact opportunity version: reject before AI submission;
- superseded exact version: reject unless the approved policy explicitly allows a diagnostic, and never
  produce an assessment for it;
- structurally incoherent references: reject rather than diagnose data-integrity failure;
- evaluator diagnostics with sufficient authorized evidence: potentially produce `DIAGNOSTIC`;
- no diagnostics and evaluator eligibility true: produce `OPPORTUNITY_ASSESSMENT`;
- unavailable market data or insufficient context: reject or return an explicit non-submitted result,
  according to the approved policy; do not infer eligibility from AI output.

The exact status/diagnostic mapping is not currently accepted by ADR-103. Implementation must stop if
OD-01 is not approved before mode-selection code is written.

## 5. Proposed Application Flow

1. Authenticate the caller and resolve authorized opportunity scope.
2. Load the exact `OpportunityId` and `OpportunityVersion`.
3. Reject missing, unauthorized or structurally invalid references.
4. Invoke `PassiveCandidateEvaluator.evaluateOne` at a single evaluation instant.
5. Apply the approved OD-01 mapping to determine assessment, diagnostic or rejection.
6. Assemble an immutable ES-02 ContextPack from the deterministic context boundary.
7. Derive one request fingerprint from the exact opportunity/version, mode, ContextPack and idempotency
   key semantics.
8. Register or reuse one durable `AnalysisExecution`.
9. Dispatch through the existing capability/adapter boundary only when the request is accepted.
10. Return technical execution identity and selected mode without exposing provider payloads or implying
    a trading decision.

## 6. Main Gaps

- No application service owns single-opportunity AI interpretation requests.
- No endpoint accepts an exact opportunity/version plus idempotency key for this use case.
- Existing execution provenance models generic analysis modes, not ES-02 interpretation modes.
- Existing AI wire contracts do not carry ContextPack identity/version/fingerprint.
- Current AI capability still assumes generic `IntelligenceContext` and existing `PASSIVE`/`ACTIVE`
  execution semantics.
- No explicit owner/resource authorization path for this operation has been verified.
- Durable business-analysis history remains intentionally deferred to ES-05.

## 7. Risks

- Conflating `AiInterpretationMode` with `AnalysisExecutionMode` would change existing passive/active
  behavior.
- Reimplementing evaluator diagnostics would cause passive awareness and AI requests to diverge.
- Reusing the generic analysis endpoint could allow market-level requests without exact opportunity
  identity.
- Extending the existing AI wire request before the ES-04 contract is approved could create a partial,
  provider-leaking contract.
- Treating a superseded opportunity as currently eligible would violate stale-result safety.
- A client-controlled account or owner identifier must never establish authorization.

## 8. Recommended Implementation Boundary

ES-03 should implement a pure deterministic mode-selection/application boundary and its tests first.
The boundary should produce a provider-neutral accepted request containing:

- exact opportunity identity and version;
- selected `AiInterpretationMode`;
- immutable ContextPack identity/version/fingerprint;
- deterministic eligibility/blocking snapshot;
- execution idempotency identity;
- authorization/provenance references.

The existing HTTP adapter should be adapted only after ES-04 confirms the additive wire shape. ES-03
must not add database migrations or a business-history aggregate.

## 9. Validation Plan

- Unit tests for assessment, diagnostic and rejection decisions.
- Exact-version, superseded-version and structural-reference fixtures.
- Authorization failure and missing-resource tests.
- ContextPack identity/fingerprint propagation tests.
- Same idempotency key and fingerprint reuse tests.
- Conflicting fingerprint rejection tests.
- Regression tests for existing generic `/api/v1/intelligence/analyses` behavior.
- Targeted and full `market-intelligence` Maven validation.
- `git diff --check`.

## 10. Approval Requirements

Before implementation planning can begin, the human must approve:

- this Repository Analysis;
- the exact OD-01 diagnostic mapping or an explicit decision to make ES-03 assessment-only;
- the selected application/API boundary;
- whether ES-04 wire compatibility is included in ES-03 or remains a separate Story.

## 11. Sources

- `docs/architecture/epics/0248-ai-market-intelligence-v1/epic.md`
- `docs/architecture/epics/0248-ai-market-intelligence-v1/d4-reuse-adapt-build-matrix.md`
- `docs/architecture/epics/0248-ai-market-intelligence-v1/d5-integration-recommendation.md`
- `docs/architecture/adr/ADR-103-proposed-ai-market-intelligence-v1-integration-architecture.md`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/passive/PassiveCandidateEvaluator.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/execution/AnalysisExecutionService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/TradingOpportunityRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/ai/AiEngineAnalysisRequest.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/MarketIntelligenceController.java`

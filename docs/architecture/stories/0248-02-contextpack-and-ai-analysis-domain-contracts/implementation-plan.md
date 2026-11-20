# Implementation Plan - Story 0248-02

## Status

Prepared after human approval of the Repository Analysis. Implementation is not authorized until
this plan is explicitly approved.

## Implementation Objective

Introduce the smallest provider-neutral contract layer needed to bind an AI interpretation to an
authorized, immutable ContextPack and an exact Trading Opportunity version. Add explicit assessment,
diagnostic and technical recovery semantics while preserving the existing Market Intelligence
execution boundary and all deterministic business authority.

This plan does not implement the complete orchestration, durable business-history projection, provider
activation or any broker/execution path.

## Decisions

### Separate business interpretation mode from execution mode

Do not extend `AnalysisExecutionMode`, whose current values `PASSIVE` and `ACTIVE` select existing
execution strategies. Add a separate provider-neutral interpretation mode with:

- `OPPORTUNITY_ASSESSMENT`;
- `DIAGNOSTIC`.

The Java-owned orchestration in a later Story will select this mode. The AI Engine will receive the
selected mode as authorized input and will never select it.

### ContextPack identity

Define an immutable ContextPack contract containing:

- stable ContextPack identifier;
- schema version;
- exact opportunity identity and version when opportunity-scoped;
- market identity;
- exact Market Thesis identity and version when present;
- evaluation cutoff;
- authorized sections derived from the existing `IntelligenceContext`;
- context quality and deterministic diagnostics;
- source/provenance references;
- canonical input fingerprint.

The ContextPack will wrap or project the existing `IntelligenceContext`; it will not duplicate
contributor logic or create a second context assembly pipeline.

### Authorization model

Use the newer `ContextClassification` and `ContextDisclosurePolicy` as the authority boundary. Keep
`ContextSensitivity` only where required for the existing compatibility wire model. A ContextPack must
contain only sections authorized for `ContextConsumer.AI_CAPABILITY` and the selected maximum
classification.

### Fingerprint canonicalization

Preserve the existing `ContextFingerprintFactory` inputs, but add a canonical ContextPack fingerprint
contract that includes the opportunity/version and ContextPack schema identity. Canonicalization must
sort unordered collections and serialize nested payload/provenance values deterministically rather than
depending on `String.valueOf` or map insertion order.

Equivalent input ordering must produce the same fingerprint. A change to any authorized identity,
version, cutoff, section status, payload, provenance or policy input must produce a different
fingerprint.

### Diagnostic eligibility

The contract factory/validator will accept `DIAGNOSTIC` only when:

- the exact opportunity version exists;
- the opportunity and its references are structurally coherent;
- deterministic evaluation provides at least one policy-approved blocking reason;
- every reason is traceable to authorized deterministic evidence.

Missing, superseded, structurally corrupt and non-diagnosable cases are rejected before AI submission.
Expired, inactive and invalidated opportunities may be diagnosed when their deterministic reason is
known and traceable.

ES-02 will define the reason-code contract and validation boundary but will not add a second copy of
`PassiveCandidateEvaluator` rules.

### Business result and technical execution separation

Keep `AnalysisExecution` as the technical lifecycle and idempotency aggregate. Define an immutable
projection-ready business result envelope containing:

- execution identity and idempotency key reference;
- interpretation mode;
- exact opportunity identity/version;
- ContextPack identity/version/fingerprint;
- typed assessment or diagnostic payload;
- deterministic evidence/provenance references;
- AI provenance where an AI result exists;
- acceptance timestamp and contract version.

This contract does not create a database table or replace `ConsolidatedIntelligence` in this Story.

### Recovery state

Add `RECOVERY_REQUIRED` to `AnalysisExecutionStatus` as a non-terminal state. It is entered when an
execution outcome is ambiguous or the provider state cannot be reconciled. It preserves the same
execution identity, idempotency key and ContextPack fingerprint.

Allowed recovery behavior:

- no blind duplicate submission;
- explicit reconciliation may return to a running/accepted state or a terminal state;
- technical recovery does not create a second business result;
- recovery does not mutate opportunity, thesis, risk, TradePlan or execution authority.

The exact transition table will be encoded in `AnalysisExecution` and tested as a state-machine
contract.

## Planned Changes

### 1. Add domain identity and mode contracts

Add under the existing Market Intelligence domain boundary, using the repository's package convention:

- `AiInterpretationMode`;
- `ContextPackId` or equivalent stable identifier value object;
- `OpportunityVersionReference` for exact opportunity identity/version;
- `ContextPackSchema` or stable schema-version constant;
- `DiagnosticReason` with deterministic code, explanation and evidence references.

Names may be adjusted only where existing repository conventions provide an equivalent contract.

Do not modify `AnalysisExecutionMode` or existing passive/active strategy semantics.

### 2. Add immutable ContextPack contract

Add a `ContextPack` aggregate/value contract that:

- copies all collections and maps at construction;
- requires schema version, fingerprint, cutoff and authorized context;
- preserves exact opportunity/thesis references;
- exposes only authorized sections;
- rejects inconsistent market, thesis and opportunity references;
- rejects future evidence relative to the evaluation cutoff;
- preserves context quality and deterministic diagnostics;
- is not mutable after construction.

Provide a factory/assembler adapter around `IntelligenceContext` rather than introducing new
contributors or provider-specific context loading.

### 3. Add canonical fingerprint support

Extend the existing context fingerprint boundary minimally, or add a ContextPack-specific canonical
fingerprint helper if that avoids changing legacy fingerprints.

The helper must canonicalize:

- sorted section types;
- sorted requirement and evidence references;
- stable enum names;
- normalized timestamps;
- deterministic nested maps and collections;
- explicit absent versus empty values.

Existing `analysis-context-v1` fingerprints must not be silently reinterpreted. The new ContextPack
schema/version must be explicit.

### 4. Add typed business result contracts

Add immutable contracts for:

- common result identity and provenance;
- `OpportunityAssessmentResult`;
- `OpportunityDiagnosticResult`;
- accepted result envelope or sealed result hierarchy;
- result acceptance/rejection reason codes.

Assessment payloads may contain qualitative AI findings, uncertainty and supporting evidence, but must
not contain authoritative opportunity status, risk, TradePlan or execution decisions.

Diagnostic payloads must contain deterministic blocking reason references and may contain an AI
explanation grounded in the authorized ContextPack. A diagnostic must never be used as an automatic
passive-to-active escalation.

### 5. Add result acceptance validation

Add a pure domain/application validator or factory that rejects results when any of the following
differs from the authorized request:

- interpretation mode;
- execution identity;
- opportunity ID or version;
- ContextPack ID or schema version;
- ContextPack fingerprint;
- market or thesis identity;
- provenance/evidence references.

The validator must distinguish stale/invalid technical results from valid diagnostic business results.
It must never consult or mutate Risk, TradePlan, Trading Core or Broker Service.

### 6. Extend technical recovery state

Modify only the existing execution state contract required for `RECOVERY_REQUIRED`:

- add the enum value;
- add legal transitions from ambiguous in-flight states;
- define transitions out of recovery for explicit reconciliation;
- keep the state non-terminal;
- preserve retry metadata and idempotency invariants;
- add an explicit failure/rejection reason when recovery cannot be resolved.

Do not change current provider retry budgets or introduce automatic retry behavior in this Story.

### 7. Adapt wire contracts only where necessary

Update `AiEngineAnalysisRequest` and the Python Pydantic request/result models only if the approved
ContextPack contract must cross the existing adapter boundary for contract validation.

Any wire extension must:

- remain provider-neutral;
- use strict validation and forbidden unknown fields;
- preserve existing interpretation phases and research modes for unrelated callers;
- carry ContextPack identity/version/fingerprint and exact opportunity reference;
- never expose credentials or execution authority.

Do not activate an AI provider or change runtime enablement defaults.

## Test Plan

### ContextPack contract tests

Add tests covering:

- immutable collection and map defensive copies;
- required identity, schema, cutoff and fingerprint validation;
- exact opportunity and thesis reference consistency;
- authorization of AI-visible sections;
- future evidence rejection;
- quality and deterministic diagnostics preservation;
- stable fingerprint for equivalent input ordering;
- changed fingerprint for each material input change;
- absent versus empty value semantics.

### Mode and eligibility tests

Add tests covering:

- eligible exact opportunity produces `OPPORTUNITY_ASSESSMENT` eligibility;
- exact inactive/expired/invalidated opportunity with approved deterministic reason produces
  `DIAGNOSTIC` eligibility;
- missing opportunity version is rejected;
- superseded version is rejected;
- structural reference mismatch is rejected;
- no deterministic blocking reason is rejected;
- evaluator reason codes are not generated independently by the new contract layer.

### Result acceptance tests

Add tests covering:

- valid assessment acceptance;
- valid diagnostic acceptance;
- mode mismatch;
- execution ID mismatch;
- opportunity ID/version mismatch;
- ContextPack ID/schema/fingerprint mismatch;
- thesis or market mismatch;
- unknown evidence reference;
- immutable accepted result and no domain-authority mutation.

### Recovery state-machine tests

Extend `AnalysisExecutionServiceTest` or add focused execution aggregate tests covering:

- legal entry into `RECOVERY_REQUIRED` from ambiguous in-flight states;
- illegal entry from terminal states;
- recovery remains non-terminal;
- explicit reconciliation to running/accepted or terminal state;
- same idempotency key and execution identity are preserved;
- duplicate business result is not accepted;
- retry budget is not silently bypassed.

### Wire compatibility tests

If Python models change, add strict Pydantic tests for:

- ContextPack fields and aliases;
- exact opportunity/version validation;
- result mode and identity matching;
- forbidden extra fields;
- existing generic interpretation flows remaining valid.

## Validation

Run targeted Java validation from `market-intelligence` using the repository Maven wrapper:

```bash
./../trading-core/mvnw -q -Dtest=<new-contextpack-tests>,<new-result-tests>,<execution-tests> test
```

If Python wire models change, run through the project virtual environment:

```bash
ai-engine/.venv/bin/python -m pytest ai-engine/tests
```

Also run:

```bash
git diff --check
```

The broader `./scripts/test-all.sh` and `./scripts/quality-verify.sh` remain applicable at
implementation validation, but are not required before this plan approval gate.

## Files Expected To Change

Likely Java files:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/` new ContextPack,
  mode, result and identity contracts;
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/context/`
  ContextPack factory/canonical fingerprint support;
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/execution/AnalysisExecutionStatus.java`;
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/execution/AnalysisExecution.java`;
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/` only if
  the existing AI boundary needs additive typed contracts;
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/ai/AiEngineAnalysisRequest.java`
  only if the approved wire contract crosses this boundary.

Likely tests:

- new ContextPack/domain contract tests under `market-intelligence/src/test/java/.../domain/` or the
  existing `application/context/` package;
- execution state tests under `market-intelligence/src/test/java/.../application/execution/`;
- AI adapter contract tests only if the wire mapping changes;
- `ai-engine/tests/` only if Python models change.

Expected unchanged areas:

- `TradingOpportunity` status semantics and `PassiveCandidateEvaluator` rule ownership;
- `MarketThesis` authority and persistence;
- complete orchestration services;
- durable business-history persistence and migrations;
- Trading Core, Risk Domain, Broker Service, Gateway and frontend;
- provider credentials, provider activation and broker operations.

## Risks And Mitigations

- **Contract duplication:** wrap existing `IntelligenceContext` and reuse evaluator/disclosure seams;
  do not create parallel context assembly or eligibility rules.
- **Fingerprint drift:** introduce an explicit ContextPack schema/version and canonical serializer;
  do not reinterpret legacy fingerprints silently.
- **Mode confusion:** keep interpretation mode separate from `PASSIVE`/`ACTIVE` execution mode.
- **Recovery misclassification:** preserve ambiguous technical outcomes as `RECOVERY_REQUIRED`, not as
  diagnostic or successful partial results.
- **History overreach:** define projection-ready fields without adding persistence or migrations.
- **AI authority leakage:** validate that results remain advisory and cannot mutate deterministic
  opportunity, thesis, risk or execution state.
- **Wire scope expansion:** change Python/adapter contracts only when required for strict compatibility
  tests; do not activate providers.

## Definition Of Done

- The ContextPack, mode, result and recovery contracts are implemented within the approved scope.
- Existing deterministic context, opportunity and execution ownership boundaries remain intact.
- Assessment and diagnostic results are distinct, traceable and stale-safe.
- `RECOVERY_REQUIRED` is non-terminal and idempotent.
- Contract and state-machine tests pass.
- Applicable Java/Python validation and `git diff --check` pass.
- No provider activation, persistence migration, frontend, broker or execution change is introduced.
- Implementation Report records documentation reconciliation and validation evidence.
- No implementation begins before explicit human approval of this plan.

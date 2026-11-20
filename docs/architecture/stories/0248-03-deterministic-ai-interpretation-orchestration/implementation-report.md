# Implementation Report - Story 0248-03

## Status

Implementation complete - independent review findings corrected; final review pending.

## Implemented Scope

Implemented the first Java-owned orchestration boundary after ES-02 without adding provider activation,
business-history persistence, frontend behavior or broker/execution authority.

### Deterministic mode selection

- Added `OpportunityInterpretationModeSelector`.
- Reuses `PassiveCandidateEvaluator.evaluateOne` for exact opportunity-version evaluation.
- Selects `OPPORTUNITY_ASSESSMENT` only for an exact coherent eligible version.
- Selects `DIAGNOSTIC` only for allow-listed deterministic blockers.
- Rejects missing, superseded, structurally incoherent and non-diagnosable versions.
- Evaluates the exact opportunity once per request; ContextPack assembly does not trigger a second evaluation.
- Keeps the allow-list explicit and does not duplicate evaluator rules.

### Orchestration and authorization

- Added `OpportunityInterpretationOrchestrationService`.
- Loads exact `OpportunityId` and `OpportunityVersion`.
- Verifies account ownership through the existing Trading Core account boundary and authenticated user ID.
- Assembles an immutable ES-02 `ContextPack` from the existing context assembler.
- Uses the `AnalysisExecution` identity as the `ContextPack` identity so ES-03 and ES-04 share a stable correlation key.
- Registers and dispatches one idempotent technical `AnalysisExecution`.
- Persists and compares exact opportunity/version, selected mode and ContextPack identity/fingerprint metadata
  before reuse, including concurrent-save recovery.
- Preserves the provider-neutral interpretation metadata in the durable execution provenance for ES-04.
- Returns the selected mode, eligibility snapshot and ContextPack identity to the request boundary.

### HTTP boundary

- Added `POST /api/v1/opportunities/{opportunityId}/interpretations`.
- Requires `Idempotency-Key` and an authenticated `MiUserPrincipal`.
- Requires an exact opportunity version in the request body.
- Rejects arbitrary caller-supplied evaluation cutoffs because the current context policy is `LIVE_CURRENT`;
  the immutable opportunity evaluation timestamp is used as the deterministic cutoff.
- Returns `202 Accepted` with execution, mode, opportunity and ContextPack identity.
- Does not expose provider payloads or create any trading authority.

### Deliberate ES-04 boundary

ES-03 deliberately stops at the provider-neutral durable execution boundary. It registers the exact
interpretation request without dispatching the existing generic AI path, because that path cannot yet
consume the immutable ContextPack. ES-04 owns dispatch adaptation. Existing generic callers remain
unchanged.

## Tests Added

- `OpportunityInterpretationModeSelectorTest`
  - eligible exact version selects assessment;
  - approved deterministic blocker selects diagnostic;
  - structural blocker is rejected;
  - missing exact version is rejected.
- `AnalysisExecutionServiceTest`
  - equivalent interpretation fingerprints reuse one execution;
  - conflicting opportunity versions are rejected;
  - interpretation metadata survives redispatch.

## Validation

Passed:

```bash
cd market-intelligence
../trading-core/mvnw -q -DskipTests package
../trading-core/mvnw -q -Dtest=AnalysisExecutionServiceTest,OpportunityInterpretationModeSelectorTest,ContextPackContractTest,AiAnalysisResultContractTest,AnalysisExecutionRecoveryTest test
git diff --check
```

The unchanged `ai-engine` regression suite was also executed: 20 tests passed.

The full `market-intelligence` suite was started with a five-minute limit but did not provide a
conclusive terminal result before the execution window ended. It is therefore not claimed as passed
locally. The run emitted existing integration-test warnings and service-unavailable diagnostics; no
ES-03-specific failure was observed in the captured output.

## Documentation Reconciliation

Updated Story-local Epic and ES-03 artifacts. No API-wide documentation or provider configuration was
updated because the wire contract remains deferred to ES-04. The endpoint and ownership boundary are
described in this report and the ES-03 Story artifacts.

## Scope Verification

No changes were made to:

- AI provider activation or credentials;
- durable business-analysis history or migrations;
- Market Thesis, Risk, TradePlan, Trading Core or Broker Service authority;
- frontend behavior;
- PAPER or LIVE execution semantics.

No commit or push was performed for ES-03.

## Review Corrections

- Conflicting idempotency fingerprints now fail with HTTP 409.
- Invalid requests map to HTTP 400; missing or unauthorized resources map opaquely to 404;
  superseded versions map to 409 and structural rejection maps to 422.
- Stable execution identity is derived from the idempotency key for deterministic retries.
- ES-03 registers rather than dispatches the generic AI execution path, preventing a reconstructed
  context or thesis from being submitted under the ES-03 ContextPack identity.

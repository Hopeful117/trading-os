# Implementation Plan - Story 0212

## Plan Status

Prepared after explicit approval of the Story 0212 Repository Analysis. This
plan requires explicit human approval before implementation begins.

## Approach

Implement one vertical slice inside the existing Java-controlled research
workflow. Keep the initial interpretation, deterministic follow-up and final
synthesis as explicit bounded phases. Java will assemble and validate all
evidence; Python will receive only the authorized typed synthesis context and
will not be able to initiate another capability.

No new service, framework, persistence aggregate, MCP tool or ADR is planned.

## 1. Provider-Neutral Synthesis Contracts

Extend the existing Java/Python interpretation contract minimally:

- Add an explicit interpretation phase distinguishing initial adaptive
  interpretation from terminal final synthesis.
- Add a bounded final-synthesis context containing the original objective,
  initial structured interpretation, requested capability, authorization and
  actual follow-up result, including status, provenance, freshness and
  diagnostics.
- Add structured hypothesis reconciliation output with a bounded effect such as
  `CONFIRMED`, `STRENGTHENED`, `WEAKENED`, `INVALIDATED`, `UNCHANGED` or
  `INCONCLUSIVE`, plus evidence references and an explanation.
- Make final synthesis output incapable of carrying a usable follow-up request;
  Java will reject any non-null request defensively if one is nevertheless
  decoded.
- Preserve existing initial request/result JSON compatibility through defaults
  for missing phase and reconciliation fields.

Likely surfaces:

- `market-intelligence` `AiAnalysisCommand`, typed AI adapter wires and
  `HttpAiAnalysisCapability`.
- `ai-engine/app/domain/models.py`, application validation and provider-facing
  structured output handling.

## 2. Deterministic Follow-up Evidence Assembly

In `ResearchApplicationService`:

- Preserve the initial `CapabilityAnalysisResult` separately from the follow-up
  result.
- Convert the actual follow-up findings into bounded, explicitly labeled
  synthesis evidence with stable IDs derived from the follow-up execution ID and
  finding identity.
- Carry capability ID/version, execution ID, market ID, source timestamps,
  input fingerprint and status where available.
- Distinguish successful, partial, failed, rejected and unavailable follow-up
  outcomes. A failed or rejected follow-up contributes diagnostics, not invented
  evidence.
- Include the complete authorized evidence ID set when building the final
  synthesis request.

The follow-up findings remain research evidence references and do not mutate
`MarketThesis`, observations, opportunities or any trading authority.

## 3. Bounded Final Synthesis Orchestration

Change the adaptive flow to:

1. Execute initial AI interpretation.
2. Validate and authorize at most one requested capability.
3. Execute the authorized deterministic follow-up.
4. If a follow-up was executed and initial interpretation is valid, submit at
   most one final synthesis request with the actual result.
5. Validate final output and reconcile it with the initial interpretation.
6. Persist initial interpretation, final interpretation, reconciliation and
   diagnostics.

Enforce the total budget in Java as two maximum AI calls per research execution:
one initial call and one final call. The final request uses a terminal phase and
cannot request another capability. Existing per-call timeout, cancellation and
research expiry behavior remains in force; final synthesis failure must not
replace valid earlier results.

No final synthesis is performed when no follow-up was requested or executed.

## 4. Evidence Validation and Reconciliation

Add a Java-side validation/reconciliation component in the existing research
application package, unless an existing helper is demonstrably suitable.

It will:

- reject evidence references absent from the complete authorized initial plus
  follow-up set;
- reject final claims that reference rejected, unavailable or fabricated work;
- reject a final follow-up request or downgrade the final result to an explicit
  unsupported outcome;
- require every non-`UNCHANGED`/non-`INCONCLUSIVE` hypothesis effect to include
  at least one valid evidence reference;
- prevent repeated text without new evidence from being marked as improvement;
- classify final synthesis failure, inconclusive output, unsupported output and
  no meaningful change without treating them as quality improvements.

The matching strategy will use stable hypothesis IDs supplied by the bounded
contract where available, with deterministic fallback matching against the
initial hypothesis text. It will not infer statistical confidence or trading
performance.

## 5. Report Integration

Extend `ResearchReport` additively and preserve old JSON deserialization:

- retain the existing initial interpretation field;
- add an optional final interpretation field;
- add bounded reconciliation records containing hypothesis, effect,
  explanation and valid evidence references;
- use explicit interpretation-effect states for not executed, not reevaluated,
  completed/no change, supported revision, inconclusive and failed/unavailable;
- preserve existing adaptive record defaults for legacy reports.

Avoid a database migration unless serialization tests prove the existing JSON
column cannot represent the additive fields. Existing REST and MCP projections
remain unchanged at the endpoint/tool level.

## 6. Evaluation and Fixtures

Extend `ResearchEvaluation` and `ResearchEvaluationFixtures` with deterministic
cases for:

- supported revision;
- supported confirmation/strengthening;
- contradictory evidence;
- inconclusive follow-up;
- failed final synthesis with preserved initial result;
- fabricated evidence reference;
- no-follow-up without a second AI call.

Scenario F from Story 0211 becomes a supported or explicitly limited result
based on the fixture outcome rather than only `NOT_REEVALUATED`. The evaluator
will inspect evidence references and interpretation effects, not model wording
or call count alone.

## 7. Tests

Add focused tests before broad validation:

- Java service tests for initial/follow-up/final sequencing and total AI-call
  budget.
- Java tests for follow-up success, failure, partial evidence, final timeout,
  malformed output, unsupported final request and preservation of partial
  reports.
- Java tests for reconciliation effects and fabricated references.
- Python Pydantic/service/provider tests for phase-specific contracts,
  terminal synthesis and evidence validation.
- Backward-compatible report deserialization tests.
- Existing ownership, idempotency and MCP compatibility tests.

No live provider calls will be added to CI.

## 8. Validation and Optional Live Run

Run:

- focused Market Intelligence tests;
- full Market Intelligence Maven suite;
- AI Engine pytest suite;
- MCP Adapter pytest suite if report mapping is touched;
- `git diff --check`;
- repository quality validation applicable to changed modules.

After offline validation, prepare an opt-in ChatGPT OAuth run only if the
runtime is available and explicitly authorized. Record sanitized provider/model,
call count, latency, effect and evidence-validation outcome without persisting
credentials or raw payloads.

## Risks and Mitigations

- **Contract drift:** use additive fields and compatibility defaults, with Java
  and Python contract tests executed together.
- **Unsupported AI claims:** validate all final references and effect evidence
  in Java before persistence.
- **Budget exhaustion:** reserve exactly one final call only after an authorized
  follow-up and enforce the total in Java.
- **Loss of valid research on late failure:** retain initial interpretation and
  actual follow-up result, mark final synthesis unavailable.
- **False research improvement:** require new valid evidence for supported,
  strengthened, weakened or invalidated effects.
- **Scope expansion:** do not change MCP, Gateway, persistence ownership,
  trading, risk, broker or canonical thesis boundaries.

## Expected Deliverables

- Updated Java/Python typed contracts and bounded orchestration.
- Evidence reconciliation and report integration.
- Story 0212 deterministic evaluation fixtures and tests.
- Implementation Report, documentation reconciliation outcome and independent
  Code Review Report after implementation.

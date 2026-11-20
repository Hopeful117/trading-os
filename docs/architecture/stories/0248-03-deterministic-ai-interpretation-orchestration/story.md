# Story 0248-03 - Deterministic AI Interpretation Orchestration

## Metadata

**ID:** `0248-03`
**Title:** Deterministic AI Interpretation Orchestration
**Status:** Implemented - review pending.
**Priority:** P1 - Trading Intelligence
**Epic:** `0248 - AI Market Intelligence V1`
**Predecessor:** `0248-02 - Define ContextPack and AI Analysis Domain Contracts`
**Related ADRs:** `ADR-076`, `ADR-089`, `ADR-101`, `ADR-102`, `ADR-103`

## Goal

Connect the ES-02 domain contracts to a Java-owned application orchestration boundary that accepts
one authorized, immutable Trading Opportunity version and deterministically selects whether to request
an `OPPORTUNITY_ASSESSMENT`, a `DIAGNOSTIC`, or no AI interpretation.

## Product Value

The platform can request an advisory interpretation without allowing the AI Engine to decide the mode,
reconstruct eligibility, load unrestricted data or mutate canonical trading decisions.

## Scope

### In scope

- Exact opportunity/version loading and resource authorization.
- Reuse of `PassiveCandidateEvaluator` and existing opportunity lifecycle authorities.
- Java-owned mode selection using an approved diagnostic policy.
- ContextPack assembly through existing deterministic context contributors.
- Creation of one idempotent durable `AnalysisExecution` per request.
- Projection of accepted request identity into the existing AI adapter boundary.
- Rejection of missing, unauthorized, superseded or structurally incoherent opportunity versions.
- Domain and application tests for assessment, diagnostic and rejection paths.

### Out of scope

- Durable business-analysis history projection and migrations.
- Provider activation, prompt redesign or model-quality evaluation.
- Frontend changes.
- Automatic retry or cross-service recovery policy beyond preserving ES-02 recovery identity.
- Changes to Market Thesis, opportunity lifecycle authority, Risk, TradePlan, Trading Core, Broker
  Service or execution.
- Broker operations, PAPER execution or LIVE execution.

## Constraints

- Market Intelligence remains the sole owner of mode selection and ContextPack construction.
- `PassiveCandidateEvaluator` remains the source of deterministic eligibility and blocker diagnostics;
  this Story must not duplicate its rules.
- AI output remains advisory and cannot establish authority.
- Existing `AnalysisExecution` remains the technical idempotency and lifecycle boundary.
- Provider-specific payloads and credentials remain inside AI Engine infrastructure.
- OD-01 must be explicitly resolved before implementation of diagnostic mode mapping.

## Acceptance Criteria

- [ ] An authorized exact opportunity version can produce an assessment request only when deterministic
      eligibility permits it.
- [ ] A diagnostic request is produced only for an exact coherent opportunity version and an approved
      deterministic blocker.
- [ ] Missing, unauthorized, superseded and structurally incoherent versions are rejected before AI
      submission.
- [ ] Mode selection is performed in Java and is not delegated to the AI Engine.
- [ ] The request uses an immutable ES-02 ContextPack and preserves its identity, version and
      fingerprint.
- [ ] Duplicate requests with the same idempotency key and fingerprint reuse the existing durable
      execution; conflicting fingerprints are rejected.
- [ ] Request construction does not mutate Opportunity, Market Thesis, Risk, TradePlan or execution
      authority.
- [ ] Tests cover assessment, diagnostic, rejection, identity propagation and idempotency behavior.
- [ ] No provider activation, persistence migration, frontend or broker behavior is introduced.

## Validation Expectations

- Targeted `market-intelligence` Maven tests for orchestration, eligibility, ContextPack and execution.
- Existing `PassiveCandidateEvaluator` and analysis execution regression tests.
- `git diff --check`.
- Full `market-intelligence` validation when implementation is complete.

## Open Questions For Repository Analysis

- Which existing application service and controller boundary should own manual interpretation requests?
- Which exact opportunity authorization port is reusable without trusting client-controlled ownership?
- Which approved OD-01 blocker mappings are representable by the current evaluator output?
- Can the existing AI adapter carry ES-02 identity without requiring ES-04 wire changes, or must ES-03
  stop at a provider-neutral request boundary?

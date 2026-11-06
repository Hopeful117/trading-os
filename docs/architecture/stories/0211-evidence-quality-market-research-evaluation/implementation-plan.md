# Implementation Plan - Story 0211

## Approval Status

Proposed plan. No implementation files may be modified until explicit human
approval.

## Smallest Vertical Slice

```text
versioned fixtures
  -> deterministic baseline report
  -> optional mock/LLM interpretation
  -> existing one-follow-up path
  -> deterministic rubric evaluator
  -> machine results + human-review notes
```

The evaluator is a library/test-facing component, not a new service or MCP
capability. It consumes the existing report and fixture metadata and emits a
provider-independent evaluation result.

## Phase 1 - Confirm Contracts

1. Verify the exact serialized `ResearchReport`, REST retrieval and MCP mapping.
2. Map `MarketThesisEvidence`, deterministic `ConsolidatedIntelligence`, child
   analysis provenance and adaptive records to the rubric.
3. Confirm which existing test fixtures represent canonical BTC/USD and ETH/USD
   identities and whether any real persisted data is reproducibly exportable.
4. If no real historical fixture is available, use synthetic data only and label
   it in every evaluation artifact.

## Phase 2 - Evaluation Fixtures and Rubric

1. Add versioned, deterministic fixture objects/resources for scenarios A-F.
2. Add typed expected conditions, such as required contradiction, stale evidence
   or relevant follow-up, without encoding a desired bullish/bearish answer.
3. Implement deterministic checks for reference validity, provenance,
   freshness, contradiction/missingness, unsupported claims, follow-up governance,
   invalidation, uncertainty and report usability fields.
4. Return per-criterion outcomes and diagnostics; do not collapse them into an
   authoritative score.

## Phase 3 - Minimal Report Lineage Improvements

Only fields demonstrated as missing by Phase 2 may be added. Prefer additive
nullable fields and preserve existing JSON names. Candidate additions are:

- evidence-level source/provider, observed-at, valid-until/source-cutoff,
  capability ID/version and input fingerprint;
- deterministic analysis execution ID;
- adaptive follow-up evidence result and whether it changed/refined the final
  interpretation;
- explicit evaluation timestamp/cutoff separate from generated-at.

Do not persist raw prompts, raw model responses, secrets or duplicate large
artifacts. Keep report schema versioning explicit and test old report retrieval.

## Phase 4 - Baseline Comparison

For every fixture, run:

1. deterministic report only;
2. mock LLM-assisted report through the existing AI boundary;
3. adaptive mock path for scenario F.

Record evidence coverage, unsupported-claim findings, missing/stale detection,
contradictions, follow-up relevance, latency, model calls, usage if available,
and reproducibility. Subjective usefulness is captured separately as review
notes.

## Phase 5 - Optional Real Provider Procedure

Reuse Story 0210's configured ChatGPT OAuth provider. Require explicit operator
authorization for one bounded run, record only sanitized metadata, and keep the
fixture/input/output association free of secrets. Do not add this to CI or run
it automatically during tests.

## Test Plan

### Market Intelligence

- six scenario fixture construction and expected rubric outcomes;
- valid/unknown/stale/missing/contradictory evidence handling;
- provenance and lineage serialization;
- deterministic versus LLM-assisted comparison;
- relevant, irrelevant, malformed, unauthorized and budget-exhausted follow-up;
- follow-up result changes/refines/does-not-change interpretation;
- partial/failed report retrieval and backward-compatible old report JSON.

### AI Engine

- provider-independent fixture outputs satisfy strict contracts;
- unknown evidence references remain rejected;
- missing, contradiction, invalidation and uncertainty fields remain bounded;
- no live provider calls in standard tests.

### MCP/Gateway

- existing research start/status/report contracts remain compatible;
- no evaluation logic or internal fields leak through MCP unless already part of
  the public report contract.

### Commands

```text
cd market-intelligence && ../trading-core/mvnw test
cd ai-engine && .venv/bin/pytest
cd mcp-adapter && .venv/bin/pytest
git diff --check
```

Run broader repository validation when the changed modules and environment
support it. Live provider validation remains opt-in and excluded from CI.

## Risks and Tradeoffs

- Synthetic fixtures can validate contracts and behavior but cannot prove live
  market usefulness; this limitation must be explicit.
- A rubric can detect grounding and governance failures, but trader usefulness
  still requires human review.
- Adding lineage fields to the report is safer than introducing normalized
  evaluation persistence, but the bounded JSON contract must remain size-limited.
- The current AI service validates argument evidence references; any broader
  claim validation must remain deterministic and must not pretend to understand
  arbitrary natural language perfectly.

## Stop Conditions

Stop and request an ADR if evaluation requires changing ownership between Java,
Python or MCP, adding a new deployed service, or making LLM output authoritative.

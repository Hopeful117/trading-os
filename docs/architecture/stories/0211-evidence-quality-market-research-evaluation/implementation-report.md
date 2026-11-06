# Implementation Report - Story 0211

## Delivered

- Added a provider-independent deterministic `ResearchEvaluation` rubric with
  per-criterion `PASS`, `FAIL` and `INSUFFICIENT_EVIDENCE` outcomes.
- Added six explicit synthetic BTC/USD versus ETH/USD scenarios covering trend,
  contradiction, missingness, stale data, comparison and adaptive investigation.
- Added reproducible report fixtures and comparison of deterministic baseline and
  assisted reports without an opaque aggregate quality score.
- Added checks for evidence IDs, source/reference grounding, market provenance,
  deterministic execution identity, capability/version lineage, freshness,
  contradictions, missing evidence, invalidation, follow-up governance and
  consistency.
- Added additive deterministic provenance fields to research reports:
  capability ID/version, thesis lineage/version, analysis execution ID and input
  fingerprint.
- Added an explicit `interpretationEffect` field to adaptive capability records.
  Existing records default to `NOT_REEVALUATED`, making the current adaptive
  limitation visible rather than implying that follow-up evidence changed the
  final interpretation.
- Preserved the existing REST, MCP, lifecycle, provider and trading boundaries.

## Evaluation Findings

- The deterministic baseline can be checked reproducibly for all six synthetic
  scenarios.
- Unknown AI evidence references fail the grounding criterion.
- Contradictory, missing and stale evidence can be detected independently of the
  provider.
- The current adaptive workflow executes the authorized follow-up, but does not
  perform a final interpretation pass over the returned evidence. Scenario F is
  therefore reported as `INSUFFICIENT_EVIDENCE` for follow-up effect. This is a
  measured quality gap, not a successful adaptive-value claim.
- Human trader usefulness, semantic truth and market predictive value remain
  review dimensions; the evaluator intentionally does not assign probabilities
  or profitability scores.

## Validation

- `cd market-intelligence && ../trading-core/mvnw -q -Dtest=ResearchEvaluationTest,ResearchApplicationServiceTest test`: **passed**, including negative and legacy-compatibility cases
- `cd market-intelligence && ../trading-core/mvnw -q test`: **passed**
- `cd ai-engine && .venv/bin/pytest`: **16 passed**
- `cd mcp-adapter && .venv/bin/pytest`: **30 passed**
- `git diff --check`: **passed**

The independent review findings concerning aggregate status, provenance
completeness, follow-up authorization, legacy null handling, missing-evidence
references and unsupported structured claims were corrected and covered by
focused tests. Empty and non-array AI findings collections are also rejected.

The full Market Intelligence run emitted existing test-environment warnings
about unavailable service discovery, missing test JWT configuration and expected
concurrency constraint races; Maven completed successfully.

## Live Provider Evaluation

Not executed for Story 0211. The procedure remains opt-in and reuses the
operator-authorized ChatGPT OAuth runtime from Story 0210. No live-provider
result is represented as a fixture result, and no token, prompt or raw provider
payload is persisted.

## Remaining Limitations

- Synthetic fixtures validate contract behavior, not current market conditions.
- The adaptive follow-up needs a future bounded final synthesis step before its
  research value can be considered demonstrated.
- The rubric validates structured grounding and governance but cannot replace
  human review of explanation quality.
- Evaluation results are test/report artifacts, not canonical Market Thesis,
  Opportunity, Risk or execution authority.

# Story 0211 - Evidence Quality & Market Research Evaluation

## Metadata

**ID:** `0211`
**Title:** Evidence Quality & Market Research Evaluation
**Status:** Implementation completed - awaiting code review and human delivery approval
**Priority:** P1 - Trading Intelligence
**Predecessors:** `0208`, `0209`, `0210`
**Related ADRs:** `ADR-020`, `ADR-021`, `ADR-028`, `ADR-034`, `ADR-076`, `ADR-088`, `ADR-089`

## Goal

Establish a reproducible, explainable way to determine whether Trading OS
research is grounded, coherent and useful to a discretionary trader. Compare
deterministic evidence with the existing provider-neutral AI interpretation and
the existing one-follow-up adaptive path without granting the AI authority over
market facts or trading decisions.

## Scope

- Add clearly labeled BTC/USD versus ETH/USD evaluation fixtures using existing
  deterministic evidence shapes; keep synthetic fixtures separate from real data.
- Cover trend confirmation, contradiction, missing evidence, stale evidence,
  comparative research and one adaptive follow-up.
- Add a provider-independent, deterministic rubric evaluator around the existing
  research workflow.
- Separate machine-checkable criteria from qualitative human-review criteria.
- Improve report lineage only where evaluation shows it is required, preserving
  backward-compatible REST/MCP retrieval.
- Compare deterministic baseline, LLM-assisted output and adaptive output for
  evidence coverage, unsupported claims, missingness, contradictions, follow-up
  relevance, reproducibility, latency and usage when available.
- Add an opt-in real-provider evaluation procedure using Story 0210 runtime;
  never call a live provider from CI.
- Produce an evaluation report containing measured results, limitations and
  recommended next improvements.

## Out of Scope

- New LLM providers, OAuth changes or model configuration redesign.
- New MCP tools, services, vector databases, RAG, frontend features or generic
  agent frameworks.
- New market-analysis algorithms without a concrete evaluation finding.
- Statistical win probabilities, profitability claims or strategy validation.
- Mutation of Market Thesis, Observation, Opportunity, Risk, TradePlan,
  ExecutionIntent, positions or broker state.
- Autonomous capability execution or adaptive loops beyond Story 0209's one
  authorized follow-up.
- Persisting raw prompts, credentials, raw provider payloads or chain-of-thought.

## Evaluation Dataset

The fixture matrix uses canonical BTC/USD and ETH/USD-shaped market identities
and explicit evidence IDs. Each fixture declares whether it is synthetic or
derived from persisted/reproducible real data.

- A: coherent supporting trend evidence and meaningful invalidation conditions.
- B: conflicting deterministic observations requiring explicit contradiction.
- C: insufficient evidence requiring a limited conclusion and missing-data list.
- D: evidence stale relative to the research timestamp and source cutoff.
- E: two markets with different evidence strength under common criteria.
- F: ambiguous initial evidence where one allowlisted follow-up is relevant,
  authorized, executed and reflected in the final interpretation.

## Evaluation Rubric

Machine-checkable checks:

- every claim reference resolves to supplied evidence in scope;
- provenance includes market identity, source/provider, timestamps, capability and
  execution identity where available;
- stale, missing and contradictory evidence remains explicit;
- unsupported conclusions are limited or rejected;
- follow-up is allowlisted, in scope, budget-compliant and relevant to stated
  uncertainty;
- executed evidence is not confused with proposed or rejected work;
- invalidation and uncertainty fields are present when evidence is limited;
- deterministic and LLM-assisted outputs are reproducible under the same fixture;
- latency, model/provider, call count and usage are recorded when available.

Human-review checks:

- a trader can understand the scenario and its limitations;
- the explanation distinguishes observation from interpretation;
- the follow-up would materially reduce uncertainty;
- comparative language does not imply unsupported future performance.

No single authoritative quality score is produced. Results are reported by
criterion with pass/fail/insufficient-evidence and reviewer notes.

## Acceptance Criteria

- [x] Six reproducible BTC/USD versus ETH/USD scenarios exist and are clearly
  labeled synthetic or real-data-derived.
- [x] The deterministic baseline is evaluated without LLM interpretation.
- [x] The LLM-assisted path is evaluated through the existing provider-neutral
  contract and rejects unknown evidence references and unsupported output.
- [x] The adaptive scenario records initial uncertainty, requested capability,
  authorization, execution result, returned evidence and interpretation change.
- [x] Stale, missing and contradictory evidence are explicitly detected in the
  evaluation output.
- [x] Report provenance preserves canonical market identity, source/provider,
  timestamps, data freshness, deterministic capability/version, artifact or
  input fingerprint, execution ID and evidence references where available.
- [x] Existing REST and MCP report retrieval remains backward compatible.
- [x] Unauthorized capability requests never execute and are evaluated as such.
- [x] Partial and failed research remain explicit and distinguishable from a
  successful deterministic baseline.
- [x] Automated tests use deterministic fixtures and contain no live LLM calls.
- [x] An opt-in real-provider procedure records sanitized provider/model,
  latency, usage, budget and limitations without secrets.
- [x] No trading, risk, broker, canonical thesis or opportunity authority changes.

## Relevant Modules

- `market-intelligence`
- `ai-engine` only for provider-neutral contract/evaluation fixture gaps
- `mcp-adapter` only if serialized report compatibility requires tests or mapping

## Validation

- Targeted Market Intelligence evaluation and report tests.
- AI Engine strict contract/evidence tests.
- MCP compatibility tests if report fields change.
- Existing affected-module suites and `git diff --check`.
- Separate opt-in real-provider run, never standard CI.

## Workflow Gate

Implementation is not authorized until the Repository Analysis and
Implementation Plan receive explicit human approval.

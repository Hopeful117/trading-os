# Trading OS Research Agent and AI-First Architecture Assessment

## Scope

This assessment evaluates the merged `main` baseline at
`ec84cf7384b1b598db369c43c2fbd58ae07c982c` after Stories 0208-0212. It does not
modify application code, change business rules, create an ADR or authorize a
trade.

## Executive Conclusion

The deterministic-first authority model should be retained. The research layer
should be treated as a constrained hybrid rather than migrated to an autonomous
AI-first pipeline.

A persisted Market Thesis artifact is a useful and justified AI context boundary.
A complete thesis, `CANDIDATE` state, current tradeability, or complete Level II
liquidity evidence should not be required for advisory interpretation when the
report explicitly preserves missingness, freshness, contradictions and
limitations.

The evidence is not sufficient to claim measurable value from adaptive AI yet.
The appropriate next step is a controlled comparison of deterministic-only,
initial-AI and adaptive-final-synthesis paths over identical evidence.

## Verified Baseline

- `main` is clean and points to the merge of PR #257.
- Market Intelligence owns research orchestration, deterministic context,
  Market Thesis, AI authorization, budgets, persistence and report retrieval.
- AI Engine is a provider-neutral, shadow-mode advisory service.
- MCP is a local stdio, Gateway-backed anti-corruption layer.
- Risk is an independent deterministic authorization engine.
- No trade, broker action or execution was performed.

Primary references:

- `docs/architecture/adr/ADR-076-market-thesis-authority-and-evidence-composition.md`
- `docs/architecture/adr/ADR-079-deterministic-liquidity-evidence-trust-and-policy-boundary.md`
- `docs/architecture/adr/ADR-088-mcp-first-agent-interaction-boundary.md`
- `docs/architecture/adr/ADR-089-native-trading-research-agent-mcp-responsibility-boundary.md`
- Stories 0208, 0209, 0210, 0211 and 0212

## Implemented Pipeline

```text
Market Data contributors
    -> IntelligenceContextAssembler
    -> CapabilityAnalysisCoordinator
    -> deterministic capabilities and observations
    -> MarketThesisComposer
    -> ResearchApplicationService
    -> initial AI interpretation
    -> Java-authorized deterministic follow-up
    -> terminal final AI synthesis
    -> ResearchExecution / ResearchReport persistence
```

The flow is asynchronous at the research lifecycle boundary. Evidence and
capability requests remain Java-controlled. Python receives an authorized,
provider-neutral request and cannot access Trading OS services or execute tools.

## Stage Assessment

| Stage | Inputs | Outputs | Main gate | Persistence/observability |
|---|---|---|---|---|
| Market Data | provider facts and normalized identity | snapshots, OHLC, order book and context sections | provider/data validity | Market Data repositories and provenance |
| Deterministic analysis | `IntelligenceAnalysisRequest`, context requirements | capability results, observations, artifacts | required context, cutoff, identity | analysis executions and artifacts |
| Market Thesis | observations and typed findings | conditional scenario, supporting/contradicting/missing evidence | composition policy and freshness | versioned thesis with lineage/fingerprint |
| Research orchestration | two canonical markets, objective, depth | bounded research execution | actor, idempotency, scope, budgets | `ResearchExecution` checkpoints and terminal report |
| Initial AI | thesis and authorized context | findings, hypotheses, missingness, optional follow-up request | provider availability, schema and evidence references | AI execution reference and report projection |
| Follow-up | one AI request | deterministic capability result or rejection | Java allowlist, version, market scope and budget | requested/executed/rejected capability records |
| Final synthesis | initial findings plus follow-up evidence | reconciliation effects and final findings | no new capability request, evidence grounding | final interpretation and reconciliation |
| REST/MCP retrieval | owned research ID and user auth | report/status projection | Gateway auth and ownership | read-only access only |

## Deterministic Bottleneck Diagnosis

The current `ResearchApplicationService` gates AI on `thesis.isEmpty()`, not on
`MarketThesisState.CANDIDATE`. This is an important distinction:

- A thesis artifact provides identity, cutoff, provenance and missingness.
- `WATCH` and `NO_TRADE` are valid analytical states that can be explained by AI.
- Tradeability is execution-context evidence, not a universal prerequisite for
  informational research.
- Risk evaluates a concrete TradePlan independently and remains fail-closed.

The current implementation still has a coupling worth measuring: if no thesis is
persisted, AI is marked `NOT_REQUESTED`, even when some validated context could
support an advisory limited report. This is not enough evidence to justify
removing the thesis boundary. It supports a controlled experiment for a future
explicit `RESEARCH_READY` context projection.

## Gates That Must Remain Mandatory

- canonical market and instrument identity;
- market-consistent evidence;
- source cutoff and temporal validity;
- freshness and provenance;
- explicit missing/stale/contradictory evidence;
- evidence-reference validation;
- Java authorization of adaptive capabilities;
- bounded AI calls, follow-ups, context and output sizes;
- deterministic Risk for concrete TradePlans;
- human authorization before execution.

Relaxing these gates risks cross-market contamination, temporal leakage,
fabricated evidence, hidden tool access or AI-derived execution authority.

## Inputs That May Be Optional For Research

Subject to explicit policy and explicit report missingness:

- order-book and Level II evidence;
- advanced liquidity measurements;
- volume, currently not implemented;
- non-blocking news context;
- tradeability evidence for informational analysis;
- candidate eligibility;
- additional deterministic follow-up capabilities.

Optional research input does not mean optional execution validation. A report may
describe an untradeable or insufficiently evidenced market, but it must not turn
that description into a TradePlan or order authorization.

## Architecture Comparison

### Architecture A: deterministic-first

Strengths:

- stable provenance and repeatability;
- explicit missingness and contradictions;
- straightforward auditability;
- low hallucination and tool-use risk;
- strong alignment with existing ADRs.

Costs:

- missing thesis artifacts suppress AI interpretation;
- useful explanations may wait for a full deterministic lifecycle;
- provider/data gaps can reduce analytical coverage;
- current runtime evidence does not show how much AI improves the baseline.

### Architecture B: hybrid AI-first

Potential strengths:

- AI can compare available evidence sooner;
- missing optional inputs can be explained rather than treated as a hard stop;
- hypotheses and uncertainty may be useful even when execution suitability is
  unavailable.

Required safeguards:

- Java remains the sole context and capability authority;
- AI cannot acquire data, alter canonical facts or call tools directly;
- every claim remains evidence-grounded;
- missingness and stale evidence remain first-class;
- Risk and execution remain independent;
- research reports clearly distinguish advisory hypotheses from suitability.

Risks:

- incomplete context may produce plausible but weak explanations;
- an AI-first label may hide a policy relaxation rather than improve usefulness;
- provider fallback can alter provenance and comparability;
- runtime usefulness is unmeasured.

## Scenario Matrix

| Scenario | Permitted research result | Execution result |
|---|---|---|
| Complete evidence | deterministic and AI interpretation | separately evaluated by Risk |
| Missing tradeability | advisory explanation with explicit unavailable evidence | no execution suitability |
| Missing order book | use other evidence; mark liquidity missing | candidate policy decides; Risk remains independent |
| Stale evidence | degraded or rejected affected branch | no current execution conclusion |
| Contradictory signals | competing hypotheses and contradiction preserved | no AI override of thesis or Risk |
| Insufficient history | limited report with uncertainty | no unsupported levels or probability |
| Volatility | current bounded evidence and uncertainty | current deterministic execution/risk checks required |
| Provider limitation | partial/failure report with provenance | no retry or fallback claim without policy |

## AI Capability Status

| Capability | Status |
|---|---|
| Structured evidence-grounded interpretation | Implemented and validated |
| Missing/stale/contradiction representation | Implemented; live qualitative usefulness unvalidated |
| One authorized follow-up | Implemented; offline validated, live unvalidated |
| Final synthesis and reconciliation | Implemented; offline validated, live unvalidated |
| Multi-timeframe interpretation | Partially implemented through deterministic context |
| Support/resistance hypotheses | Partially implemented through structural evidence |
| Order-book/liquidity reasoning | Blocked/partial by current evidence and policy |
| Trusted news/macro context | Blocked by unvalidated live context |
| Calibrated probabilities | Missing by design |
| Executable entry/stop/target generation | Outside this workflow |

## Validation Evidence

Offline project suites pass for Market Intelligence, AI Engine and MCP Adapter;
Market Data full tests and the deterministic research evaluation also pass.
The Docker stack is running and the AI Engine health endpoint reports enabled
shadow mode with `openai_chatgpt`. Unauthenticated Gateway calls return `401`.

The prior real-provider campaign proved initial AI interpretation over authorized
synthetic evidence and persisted report retrieval. It did not prove a complete
live adaptive cycle: the provider follow-up was rejected by the Java allowlist,
tradeability remained unavailable, and authenticated MCP replay was not run
because no user JWT was available to the local process.

These are environmental and authorization limitations, not grounds to weaken
the current controls.

## Controlled Experiment Execution

The reproducible offline experiment was executed against the existing synthetic
fixture matrix without changing application code:

- 7 scenarios: trend confirmation, contradictory signals, missing evidence,
  stale data, comparative markets, adaptive investigation and no-follow-up;
- deterministic baseline checks;
- deterministic-versus-assisted comparison checks;
- adaptive follow-up governance and final-synthesis checks;
- evidence grounding, provenance, freshness, contradiction, missingness,
  invalidation and reconciliation checks;
- 14 tests executed, 0 failures, 0 errors, 0 skipped;
- Maven build result: `BUILD SUCCESS`.

The experiment confirms contract-level behavior and preserves the expected
deterministic safeguards. It does not provide a live trader-usefulness score:
the fixture harness intentionally uses synthetic evidence, and the live
adaptive cycle remains blocked by the unavailable authenticated user session,
tradeability evidence and the provider follow-up allowlist result described
above.

Therefore the experiment result is **architecturally safe but analytically
inconclusive**. It supports retaining the current boundaries and does not yet
justify an AI-first migration.

## Independent Review

An independent repository assessment challenged both architecture options and
reached the same bounded conclusion: retain deterministic authority, avoid a
generic AI-first rewrite, and run a controlled comparison. The unresolved issues
are policy and measurement questions, not authorization to change the current
architecture.

## Recommendation

Choose **Option 3: controlled experiment**.

Compare deterministic-only, initial-AI and one-follow-up/final-synthesis reports
over identical fixtures and replayable real evidence. Measure evidence coverage,
unsupported claims, contradiction preservation, missingness, uncertainty
reduction, trader comprehension, latency, call count and provider usage.

Do not infer profitability or calibrated predictive accuracy. Do not modify the
architecture until the experiment demonstrates measurable analytical value and
the human owner approves the resulting policy/ADR direction.

## Human Decision

Option 3 was approved by the human owner on 2026-11-06. This approval selects the
controlled experiment direction; it does not authorize an architecture migration,
an ADR, a new Story, a provider policy change or any trade execution.

## Incremental Roadmap

1. Reconcile Story 0209 status metadata with the delivered implementation.
2. Define and document `research-ready`, `candidate-ready` and
   `liquidity-qualified` policy profiles.
3. Add explicit report projections for thesis state, research quality and
   execution suitability.
4. Run an authenticated bounded live campaign with an approved allowlisted
   follow-up capability.
5. Add comparison telemetry and human-review rubric results.
6. Reassess whether a limited context-first path is justified. Keep Risk,
   Trading Core, Broker Service and human authorization unchanged.

## Decisions Requiring Human Approval

- whether AI may interpret a `NO_TRADE` thesis caused by a blocking contradiction;
- the definition of a research-ready context;
- the policy requiring Level II evidence for candidate presentation;
- the minimum evidence of AI value required before architectural change;
- provider fallback and provenance policy;
- any future ADR or Story authorizing a hybrid context projection.

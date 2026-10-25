# Repository Analysis - Story 0163

## Story

0163 - Deterministic Market Thesis Composition

## Repository State

| Field | Value |
|---|---|
| Branch | `main` |
| HEAD | `d8cf1053385731339b19d0500d7872b5faa53434` |
| Remote baseline | `origin/main` points to the same commit |
| Working tree | Story 0163 and accepted ADR-076 artifacts are uncommitted; no production implementation changes |
| DevLog | Unavailable for this analysis: previous Story Agent request failed with `LLM_PROVIDER_ERROR` |
| Obsidian | Not consulted; current repository and accepted ADRs provide the required project context |

The repository remains authoritative for current implementation. Story approval
is recorded in the Story metadata, but it does not approve this Repository
Analysis or a future Implementation Plan.

## Analysis Sources

### Story and governing decision

- `docs/architecture/stories/0163-market-thesis-composition/story.md`
- `docs/architecture/adr/ADR-076-market-thesis-authority-and-evidence-composition.md`
- `docs/investigations/trading-intelligence-layers-architecture-2026-09-23.md`
- `docs/investigations/v1-trading-intelligence-reality-check.md`

### Related architecture

- `docs/architecture/adr/ADR-006.md`
- `docs/architecture/adr/ADR-020.md`
- `docs/architecture/adr/ADR-021.md`
- `docs/architecture/adr/ADR-025.md`
- `docs/architecture/adr/ADR-027.md`
- `docs/architecture/adr/ADR-028.md`
- `docs/architecture/adr/ADR-034.md`
- `docs/architecture/adr/ADR-038.md`

### Relevant implementation

- `market-intelligence/.../application/pipeline/ProductionIntelligencePipeline.java`
- `market-intelligence/.../application/observation/ObservationBuilder.java`
- `market-intelligence/.../application/observation/TrendContextObservationService.java`
- `market-intelligence/.../application/observation/TrendContextObservationRule.java`
- `market-intelligence/.../domain/observation/Observation.java`
- `market-intelligence/.../domain/observation/ObservationEvidence.java`
- `market-intelligence/.../domain/trendcontext/TrendContextAssessment.java`
- `market-intelligence/.../domain/trendcontext/TrendContextFinding.java`
- `market-intelligence/.../domain/trendcontext/TrendContextContradiction.java`
- `market-intelligence/.../domain/trendcontext/TrendInvalidation.java`
- `market-intelligence/.../domain/marketstructure/MarketStructureResult.java`
- `market-intelligence/.../strategy/domain/StrategyMatch.java`
- `market-intelligence/.../domain/opportunity/TradingOpportunity.java`
- `market-intelligence/.../domain/tradeplan/TradePlan.java`
- `market-intelligence/.../domain/IntelligenceContext.java`
- `market-intelligence/.../domain/ContextSection.java`
- `market-intelligence/.../domain/ContextSectionStatus.java`

## Current Analytical Pipeline

The current production path is:

```text
Market Data client
    -> AnalysisExecution and capability executions/artifacts
    -> ObservationBuilder / consolidation rule
    -> persisted Observation
    -> StrategyEvaluation for each governed/applicable StrategyDefinition
    -> persisted StrategyMatch for MATCH
    -> TradingOpportunity projection
    -> TradePlan generation and lifecycle
    -> Risk handoff
    -> human authorization and execution boundaries
```

`ProductionIntelligencePipeline` currently builds an Observation, selects
governed and applicable strategies, evaluates each strategy independently,
persists matches, and creates Opportunities only for successful matches. It
does not compose a Market Thesis and must not be broadened implicitly by this
Story.

## Existing Authorities and Reusable Boundaries

### Facts and context

Market Data remains the authority for normalized public market facts. The
`IntelligenceContextAssembler` already provides bounded context sections with
`AVAILABLE`, `STALE`, `MISSING`, `UNAVAILABLE`, `UNSUPPORTED`, and `INCOMPLETE`
states, provenance, cutoff and a context fingerprint.

No generic Fact aggregate is needed.

### Observation

`Observation` already provides:

- immutable identity, lineage and version;
- instrument and typed Observation type;
- created/validity timestamps;
- supersession references;
- consolidation rule version;
- evidence list and typed payload;
- persisted repository and JPA adapters.

`ObservationEvidence` already references capability execution traces, observed
time, measurements, thresholds and source capability metadata. It lacks thesis
role, contradiction severity and thesis-state semantics. Market Thesis should
reference and selectively snapshot this evidence rather than replace it.

### Trend Context

`TrendContextAssessment` already carries:

- market/provider/symbol;
- assessment and cutoff timestamps;
- input/profile/rule fingerprints;
- timeframe-role assessments;
- direction, regime, phase and attention;
- findings, contradictions, exclusions and invalidations;
- assessment fingerprint.

This is the strongest existing deterministic input for V1 composition. Its
contradictions and invalidations must be preserved, not flattened into a score.

### Market Structure

`MarketStructureResult` already carries provider-neutral market identity,
interval, cutoff, algorithm/policy versions, input/result fingerprints,
availability, findings, retained/all swings and structural relations. It is the
authority for structural swings, relations and levels. Thesis composition should
reference these values and not create a second structure engine.

### Strategy and Opportunity

`StrategyMatch` is an immutable fact that one Strategy version matched one market
context. `TradingOpportunity` is a trader-facing candidate derived from that
match and currently includes strategy, observation, scan, account and analysis
execution provenance.

ADR-034 therefore prevents Market Thesis from replacing StrategyMatch or
directly creating an Opportunity. The initial Story should add no Opportunity
score or lifecycle change.

### TradePlan, Risk and execution

`TradePlan` already separates execution parameters from `TradingRationale` and
uses immutable ID/version lineage. Risk handoff is explicit and downstream.
Market Thesis may later be referenced in rationale, but the Story must not
change executable Entry, Stop, Target, sizing, Risk, or execution semantics.

## Domain Gap

No current aggregate or value object represents all of the following together:

- one conditional scenario;
- supporting evidence;
- contradicting evidence;
- missing/stale evidence;
- structured confirmation;
- structured invalidation;
- relevant levels;
- analytical state `NO_TRADE`, `WATCH`, or `CANDIDATE`;
- immutable composition provenance.

Extending Observation alone would overload a reusable evidence concept.
Extending TradingOpportunity would violate ADR-034 and account/strategy
boundaries. A new Market Thesis authority is therefore required by ADR-076.

## Recommended Domain Boundary

The implementation should introduce a Market Intelligence-owned, market-scoped
and strategy-neutral Market Thesis aggregate/value model with a thesis-specific
evidence contribution model.

The contribution model should reference existing source evidence and preserve a
composition snapshot. It must not become a universal evidence service or source
of market facts.

The logical composition boundary is:

```text
Observation / Trend Context / Market Structure / context statuses
    -> deterministic Market Thesis composer
    -> immutable versioned Market Thesis
    -> read contract and future provenance references
```

The composer should use explicit rule/state predicates rather than a universal
quality score. A blocking contradiction must prevent `CANDIDATE`; missing
required evidence must never become support; stale required evidence must not
produce a fresh candidate.

## State and Lifecycle Analysis

The minimum state machine is:

- `NO_TRADE`: no responsible candidate scenario is currently available;
- `WATCH`: a coherent scenario exists but confirmation is pending or a
  non-blocking limitation remains;
- `CANDIDATE`: required evidence is present and fresh, confirmation is
  satisfied, and no blocking contradiction remains.

These states are analytical only. They are not human decisions, strategy
validation, Risk decisions, or execution states.

Reevaluation must append a new immutable thesis version or lineage. It must
record source cutoff, validity, evidence timestamps, source fingerprints,
composer version and changed input references. Historical thesis content must
never be rewritten.

## Persistence and Provenance

The Story requires `VERSIONED_PERSISTED` business evidence. Transient-only
composition would prevent reliable:

- `NO_TRADE` history;
- Opportunity/TradePlan provenance;
- entry-thesis preservation;
- future Position Intelligence comparison;
- post-trade and research joins.

Existing Observation persistence patterns are reusable, but Market Thesis needs
its own persistence boundary because its lifecycle, state and composition role
are distinct. The implementation plan must decide the exact entity/table and
migration shape without duplicating raw provider payloads.

Minimum provenance includes thesis ID/lineage/version, market identity,
analysis execution when available, cutoff/validity, composer policy/version,
input fingerprints and source evidence references.

## Expected Affected Areas

Likely affected areas, subject to the approved Implementation Plan:

- `market-intelligence` domain model and application composer;
- `market-intelligence` persistence ports, entities, mappers and migrations;
- `market-intelligence` read/application contract if exposure is included;
- focused Market Intelligence tests;
- downstream TradePlan/Opportunity contract tests only if provenance references
  are added in a backward-compatible manner.

The following areas should not change unless the Implementation Plan proves an
existing contract is insufficient:

- `market-data` analyzer and provider semantics;
- `risk-domain` financial rules;
- `trading-core` account/challenge/execution authorities;
- `broker-service` and cTrader;
- `FeatureProjectionV1`;
- frontend financial or analytical calculations.

## Risks and Open Implementation Decisions

### Risks

1. A thesis-specific evidence model could become a generic untyped evidence
   platform. The implementation must keep it narrow and source-referential.
2. Persisting snapshots and references may drift if source fingerprints are not
   mandatory and versioned.
3. State predicates could accidentally become a hidden Opportunity score or Risk
   gate. The state must remain analytical and explicit.
4. A direction-specific thesis could be confused with a strategy-specific setup.
5. Downstream TradePlan provenance could accidentally rewrite historical context.
6. A read contract could expose missingness as a false negative or false support.

### Decisions reserved for the Implementation Plan

- Exact aggregate/value-object decomposition.
- Whether a thesis version is keyed by analysis execution or by a deterministic
  scenario lineage plus execution reference.
- Exact persistence tables and migration layout.
- Exact read endpoint and authorization boundary if a product surface is needed.
- Required versus optional evidence policy representation for V1.
- Whether TradePlan/Opportunity references are included in this Story or kept as
  a follow-up compatibility Story.

These decisions must remain within ADR-076. A new authority or changed lifecycle
would require renewed architectural review.

## Blockers

No blocker prevents Implementation Planning.

Implementation must stop and request a decision if it requires:

- changing existing analyzer semantics;
- changing StrategyMatch or Opportunity meaning;
- introducing account/challenge authority into Market Thesis;
- making Market Thesis a Risk or execution gate;
- adding AI-generated canonical thesis content;
- adding cTrader or reference-market semantics;
- mutating historical thesis, TradePlan, or position context;
- creating a second generic evidence authority.

## Validation Baseline

The implementation will require at least:

- deterministic domain tests for all thesis states and invariants;
- supporting/contradicting evidence tests;
- multi-timeframe disagreement tests;
- missing, unavailable, stale, unknown, not-applicable, and not-implemented
  tests;
- immutable version and lineage persistence tests;
- deterministic replay/fingerprint tests;
- no-Risk/no-execution-authority tests;
- affected Market Intelligence Maven tests;
- formatting and `git diff --check`.

No cTrader runtime validation is required for this Story.

## Repository Analysis Conclusion

Story 0163 is implementable within the accepted ADR-076 boundary. The
repository already provides sufficient source authorities and provenance
patterns, but it does not provide the Market Thesis authority itself. The
smallest coherent implementation is a new Market Intelligence persisted thesis
model and deterministic composer that references existing evidence without
changing downstream Opportunity, TradePlan, Risk or execution semantics.

Repository Analysis is complete and awaits explicit human approval before an
Implementation Plan is produced.

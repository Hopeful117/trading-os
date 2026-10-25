# Story 0163 - Deterministic Market Thesis Composition

## Metadata

**ID:** `0163`
**Title:** Deterministic Market Thesis Composition
**Status:** Approved

## Goal

Establish the first production Market Thesis capability so Trading OS can
compose existing deterministic market evidence into an auditable, conditional,
market-scoped interpretation without conflating analysis with Opportunity,
TradePlan, Risk, or execution authorization.

## Context

ADR-076 establishes Market Thesis as a new canonical Market Intelligence
authority. The repository already contains deterministic market facts, Trend
Context, Market Structure, durable Observations, StrategyMatch provenance,
Trading Opportunities, and Trade Plans.

The current pipeline can calculate and persist individual analytical results,
but it cannot preserve one coherent scenario with supporting evidence,
contradicting evidence, missingness, freshness, confirmation, invalidation, and
a truthful analytical state.

The capability must remain useful while Momentum, richer Volume, advanced
Liquidity, AI interpretation, and cTrader Market Data are unavailable. Missing
capabilities must remain explicitly missing rather than being inferred from
unrelated Trend or Structure evidence.

## Trader Question

> Given the current deterministic evidence, is there a coherent conditional
> market scenario worth investigating, what supports or contradicts it, what
> would confirm or invalidate it, and is the analytical state `NO_TRADE`,
> `WATCH`, or `CANDIDATE`?

## Scope

- Add the Market Intelligence domain model for an immutable, versioned Market
  Thesis and its thesis-specific evidence contributions.
- Compose existing authoritative evidence from Observations, Trend Context,
  Market Structure, context availability/freshness, and other supported
  deterministic contributors.
- Preserve separate supporting, contradicting, stale, unavailable, unknown,
  not-applicable, and not-implemented evidence states.
- Represent structured conditional scenario, direction, confirmation,
  invalidation, relevant levels, analytical state, reasons, and provenance.
- Support the analytical states `NO_TRADE`, `WATCH`, and `CANDIDATE`.
- Persist immutable thesis versions with lineage, validity, input fingerprints,
  composer/version provenance, and source evidence references.
- Make reevaluation produce a new immutable version rather than mutating a
  historical thesis.
- Provide a read-only application/query boundary sufficient for human decision
  support and downstream provenance references.
- Preserve market scope and keep account/challenge context outside thesis
  identity and authority.
- Keep the first implementation deterministic and replayable from the recorded
  authoritative inputs and versions.

## Out of Scope

- Changing existing Trend Context, Market Structure, volatility, spread,
  liquidity, news, or other analyzer semantics.
- Implementing Momentum, advanced Volume, advanced Liquidity, Order Flow,
  Session, or Reference Market analyzers.
- Adding cTrader Market Data or provider-specific market contracts.
- Replacing or changing `Observation`, `StrategyMatch`, `TradingOpportunity`,
  Opportunity score, `TradePlan`, Risk, execution, position, or challenge
  authorities.
- Automatically creating or ranking Trading Opportunities from a thesis.
- Automatically creating Trade Plans, approving Risk, or authorizing execution.
- Adding a universal evidence service or generic confidence/quality score.
- Adding AI, LLM interpretation, agent reasoning, or AI-generated canonical
  thesis content.
- Changing `FeatureProjectionV1` or research dataset semantics.
- Implementing Position Intelligence thesis comparison or continuous monitoring.
- Solving execution-market versus reference-market mapping.
- Adding human trading decisions, execution commands, or automatic `NO_TRADE`
  decisions distinct from the analytical thesis state.
- Creating a new microservice.

## Acceptance Criteria

- [ ] A Market Thesis is a first-class Market Intelligence domain concept with
      explicit identity, lineage, version, market scope, evaluation cutoff,
      validity, state, and provenance.
- [ ] A thesis is composed only from identified authoritative inputs and records
      the composer policy/version and deterministic input/evidence fingerprints.
- [ ] Supporting and contradicting evidence are separate typed contributions and
      preserve source references, source capability, market/instrument,
      timeframe, observed-at, cutoff, freshness, and analysis/version
      provenance where available.
- [ ] Contradiction severity is explicit and a blocking contradiction cannot be
      hidden by confidence averaging or a universal score.
- [ ] Missingness distinguishes at least `AVAILABLE`, `UNAVAILABLE`, `STALE`,
      `UNKNOWN`, `NOT_APPLICABLE`, and `NOT_IMPLEMENTED` without treating
      missing evidence as negative market evidence or support.
- [ ] Required stale or unavailable evidence cannot silently produce a fresh
      `CANDIDATE`.
- [ ] Existing Trend Context contradictions, exclusions, invalidations,
      timeframe disagreement, and Market Structure invalidation evidence remain
      visible in the composed thesis.
- [ ] A thesis can represent a structured conditional scenario without making a
      prediction or probability claim.
- [ ] Confirmation is represented by structured conditions or evidence
      references, including pending and satisfied states.
- [ ] Invalidation is represented separately from an executable Stop and may
      reference authoritative structural invalidation evidence.
- [ ] Relevant levels reference Market Structure authority and do not introduce
      a second market-structure engine.
- [ ] `NO_TRADE`, `WATCH`, and `CANDIDATE` are deterministic analytical states
      with structured reasons and no Risk, execution, or human-decision meaning.
- [ ] `NO_TRADE` is persisted as a successful analytical outcome, including
      insufficient evidence, blocking contradiction, stale required evidence,
      unavailable required evidence, and no-valid-scenario cases where
      applicable.
- [ ] Missing Momentum, Volume, and advanced Liquidity capabilities remain
      explicit gaps and are never inferred from Trend or Structure.
- [ ] New evaluation produces a new immutable thesis version or lineage and
      never rewrites historical thesis content.
- [ ] Persistence supports deterministic reconstruction of the thesis at its
      historical evaluation time without duplicating provider payloads
      unnecessarily.
- [ ] Market Thesis is market-scoped and does not include account identity,
      challenge rules, balance, equity, drawdown, exposure, or position sizing
      in its authority or identity.
- [ ] The capability does not create or authorize a Trading Opportunity,
      TradePlan, Risk decision, execution command, or broker operation by
      itself.
- [ ] StrategyMatch remains the authority for a named strategy/version match;
      multiple strategies can consume the same thesis without changing thesis
      semantics.
- [ ] TradePlan provenance can reference the exact thesis ID/version without
      mutating the thesis or changing executable Stop, Target, sizing, or Risk
      semantics.
- [ ] Provider-specific fields and cTrader identifiers do not cross into the
      Market Thesis contract.
- [ ] A read-only contract exposes the thesis state, scenario, evidence,
      contradictions, missingness, freshness, confirmation, invalidation,
      levels, validity, and provenance without frontend recomputation.
- [ ] Focused tests prove deterministic replay, evidence polarity, contradiction
      visibility, missingness, freshness, state transitions, immutability,
      version lineage, ownership of persistence, and no-authorization behavior.
- [ ] Affected module tests, formatting, and `git diff --check` pass.

## Constraints

- ADR-076 is the governing architectural decision.
- Market Data remains authoritative for normalized market facts, state and
  freshness.
- Market Intelligence owns thesis composition and thesis persistence.
- Observation remains reusable durable analytical evidence.
- Trend Context and Market Structure remain authoritative for their own typed
  semantics.
- StrategyMatch and TradingOpportunity semantics from ADR-034 remain unchanged.
- TradePlan remains the executable proposal boundary from ADR-027.
- Risk remains the deterministic financial authorization boundary.
- Human authorization remains required before execution.
- Thesis versions are immutable and re-evaluation is append-only.
- The implementation must not silently convert unavailable, stale, unsupported,
  or not-implemented evidence into a negative or positive market conclusion.
- Provider-neutral contracts must not expose provider payloads or credentials.
- No commit, push, merge, pull request, branch deletion, or destructive Git
  operation is performed automatically.

## Relevant ADRs

- `docs/architecture/adr/ADR-006.md`
- `docs/architecture/adr/ADR-020.md`
- `docs/architecture/adr/ADR-021.md`
- `docs/architecture/adr/ADR-025.md`
- `docs/architecture/adr/ADR-027.md`
- `docs/architecture/adr/ADR-028.md`
- `docs/architecture/adr/ADR-034.md`
- `docs/architecture/adr/ADR-038.md`
- `docs/architecture/adr/ADR-076-market-thesis-authority-and-evidence-composition.md`

## Relevant Stories

- `0065` - Trend Context Strategy Boundary.
- `0071` - Market Structure Foundation.
- `0159` - Surface Evidence-Led Opportunity Decisions in the Trader Cockpit.
- `0160` - Deterministic Trade Scenario Projection.
- `0162` - Position Intelligence View.

## Relevant Modules

- `market-intelligence`
- `market-data`
- `risk-domain` (contract compatibility only)
- `trading-core` (provenance compatibility only)
- `trading-os-web` (read-only contract consumer only, if exposed in scope)

## Validation

- Focused Market Intelligence domain tests for thesis state and invariants.
- Persistence tests for immutable versions, lineage, source references,
  fingerprints, supersession, and replay.
- Contract tests for Observation, Trend Context, Market Structure, Opportunity,
  and TradePlan provenance compatibility.
- Negative tests proving that Market Thesis cannot call or authorize Risk or
  execution boundaries.
- Tests for available, stale, unavailable, unknown, not-applicable, and
  not-implemented evidence.
- Tests for supporting evidence, weakening contradictions, blocking
  contradictions, and multi-timeframe disagreement.
- Affected Maven test suites and formatting checks.
- `git diff --check`.
- Runtime validation only when an authenticated deterministic Market Thesis
  surface exists; no cTrader runtime validation is required for this Story.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Implementation completed.
- [ ] Documentation reconciliation completed.
- [ ] Relevant validation executed.
- [ ] Diff reviewed in IntelliJ.
- [ ] Independent Code Review approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.

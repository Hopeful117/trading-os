# Story 0164 - Structural Levels and Objectives for Market Thesis

## Metadata

**ID:** `0164`
**Title:** Structural Levels and Objectives for Market Thesis
**Status:** Approved

## Goal

Deepen the deterministic Market Thesis evidence layer by composing the existing
Market Structure and Trend Context facts into trader-meaningful structural
levels, thesis invalidation, a next structural objective, and opposing
obstacles to that objective.

The capability must improve the quality of the market explanation without
creating a second Market Structure engine, changing executable planning,
changing Risk semantics, or authorizing a trade.

## Context

Story 0163 established Market Thesis as the canonical deterministic composition
authority. The current composer persists a valid thesis and exposes structural
levels and invalidation references, but those values remain shallow:

- confirmed swings are projected as an undifferentiated level list;
- protected levels are not clearly expressed by market role;
- structural invalidation is not composed as an explicit scenario basis;
- no next structural objective is selected;
- no meaningful opposing levels between the current reference and objective are
  exposed as obstacles;
- a mathematically attractive downstream objective cannot be challenged by
  visible structural resistance or support in the path.

The repository already contains provider-neutral deterministic foundations:

- confirmed swings and their timestamps;
- HH, HL, LH, LL and equality relations;
- protected levels;
- close breaks, wick breaks and reclaims;
- timeframe roles and multi-timeframe context;
- structural invalidation evidence;
- Trend Context levels, pullback and extension context;
- immutable evidence fingerprints and source provenance.

The investigation after Story 0163 concluded that the shallowest remaining
high-value layer is the interpretation of those existing structural facts into
relevant levels and objective geometry. A new Momentum, Volume, Order Book,
cTrader, AI, or reference-market capability is not required for this boundary.

## Trader Question

> What important structural levels matter for this scenario, at what level is
> the scenario objectively invalidated, what next structural level justifies the
> objective, and what opposing structural levels stand in the way?

## Scope

- Add one bounded deterministic Market Intelligence capability for structural
  level and objective composition.
- Consume existing provider-neutral Market Structure and Trend Context results.
- Classify relevant levels with explicit roles, including where applicable:
  confirmed swing, protected level, structural invalidation, objective, and
  opposing obstacle.
- Preserve market structure timeframe, source references, observed timestamps,
  cutoff, freshness, algorithm/rule versions, and evidence fingerprints.
- Select a next structural objective only from authoritative existing structural
  levels and relations.
- Identify opposing structural levels between the scenario reference price and
  the selected objective when a valid reference price is available.
- Represent unavailable reference price, objective, distance, or obstacle data
  explicitly rather than fabricating values.
- Compose the resulting evidence into the existing Market Thesis scenario and
  evidence model.
- Expose sufficient structured information for a human consumer to understand
  level relevance, invalidation, objective basis, and obstacles.
- Preserve deterministic replay, immutable thesis versions, and source
  provenance.

## Out of Scope

- Replacing or changing the existing Market Structure engine or its swing,
  relation, break, reclaim, or protected-level semantics.
- Creating a second market-structure or universal level engine.
- Inventing liquidity pools, stop clusters, support/resistance probability, or
  unobserved order-flow behavior.
- Adding Momentum, Volume, advanced Liquidity, Order Book, Session, News, AI,
  cTrader Market Data, or reference-market analysis.
- Changing `FeatureProjectionV1` or S1 research semantics.
- Changing Trend Context semantics, profile rules, or freshness policy.
- Creating executable Entry, Stop, or Target parameters.
- Changing TradePlan, Risk, position sizing, execution, reconciliation, or
  broker behavior.
- Making Market Thesis an account-scoped, challenge-scoped, or Risk authority.
- Adding a confidence score, universal confluence score, ranking formula, or
  automatic trade decision.
- Automatically creating, ranking, approving, or authorizing a Trading
  Opportunity or TradePlan.
- Adding a frontend screen or frontend-side analytical calculation.
- Adding a new microservice.

## Acceptance Criteria

- [ ] Structural level composition consumes only identified authoritative
      Market Structure and Trend Context inputs and records deterministic
      capability, rule, policy, cutoff, and fingerprint provenance.
- [ ] A relevant level preserves its source timeframe, source reference, price,
      observed/pivot and confirmation timestamps where available, and validity
      status.
- [ ] Level roles distinguish at minimum confirmed structural level, protected
      level, thesis invalidation level, structural objective, and opposing
      obstacle when the source evidence supports those roles.
- [ ] A level is not classified as protected, invalidating, objective, or
      obstructing solely because of its field name; the role follows an explicit
      deterministic policy backed by existing structural semantics.
- [ ] Structural invalidation remains distinct from an executable Stop and is
      represented as the market condition that makes the scenario objectively
      wrong.
- [ ] The selected structural objective is derived from an authoritative
      existing level or relation and does not claim an unsupported liquidity
      target or future price outcome.
- [ ] Opposing structural levels between the valid scenario reference price and
      the selected objective are exposed as obstacles when the reference price,
      direction, and level ordering are determinable.
- [ ] Distance to a level or objective is absent, unavailable, or explicitly
      marked unknown when the required reference price or compatible evidence is
      unavailable; no distance is fabricated.
- [ ] Levels from incompatible market, instrument, timeframe, provider, cutoff,
      or rule provenance are not silently composed as current evidence.
- [ ] Stale, unavailable, invalid, insufficient, or not-applicable source
      evidence remains distinguishable and is never converted into supporting
      evidence.
- [ ] A structural obstacle can contribute a visible contradiction or weakening
      condition according to the existing Market Thesis policy, without being
      collapsed into a numeric confidence score.
- [ ] The Market Thesis scenario exposes structured confirmation and
      invalidation references that can explain why a scenario remains `WATCH`,
      becomes `CANDIDATE`, or is `NO_TRADE` where the approved state policy
      requires it.
- [ ] A blocking structural invalidation cannot produce `CANDIDATE`.
- [ ] Identical authoritative inputs, policy versions, and evidence versions
      produce identical level roles, objective selection, obstacles, fingerprints,
      and thesis content.
- [ ] Re-evaluation creates a new immutable thesis version or lineage and never
      mutates a historical thesis or downstream TradePlan rationale.
- [ ] Market Thesis remains market-scoped and strategy-neutral.
- [ ] The capability does not alter StrategyMatch, TradingOpportunity,
      Opportunity score, TradePlan lifecycle, executable Stop/Target, Risk,
      execution, or human authorization semantics.
- [ ] No provider-specific payloads, cTrader identifiers, credentials, or
      reference-market assumptions cross the provider-neutral thesis contract.
- [ ] Focused tests cover aligned structure, opposing structure, invalidation,
      missing reference price, stale evidence, incompatible provenance,
      objective absence, obstacle ordering, deterministic replay, and immutable
      thesis persistence.
- [ ] Affected Market Intelligence tests, formatting checks, and `git diff
      --check` pass.

## Constraints

- ADR-076 is the governing architectural decision.
- Market Data remains authoritative for normalized market facts and freshness.
- Market Structure remains authoritative for swings, relations, breaks, reclaims,
  protected levels, and structural validity.
- Trend Context remains authoritative for timeframe roles, trend direction,
  phase, pullback, extension, and invalidation evidence that it already owns.
- Market Intelligence owns deterministic level/objective composition into Market
  Thesis.
- Market Thesis remains decision support, not prediction, probability, Risk,
  TradePlan, execution, or human authorization.
- Structural invalidation must not be conflated with an executable Stop.
- Structural objective must not be conflated with an executable Target.
- Obstacles must be based on visible authoritative structural levels, not
  inferred stop pools or unsupported liquidity claims.
- Missing, stale, unavailable, invalid, and not-implemented evidence must remain
  explicit and must not be treated as positive or negative market facts.
- Provider-neutral contracts must not expose provider payloads or credentials.
- `FeatureProjectionV1` and S1 semantics are frozen for this Story.
- No commit, push, merge, pull request, branch deletion, or destructive Git
  operation is performed automatically.

## Relevant ADRs

- `docs/architecture/adr/ADR-076-market-thesis-authority-and-evidence-composition.md`
- `docs/architecture/adr/ADR-020.md`
- `docs/architecture/adr/ADR-025.md`
- `docs/architecture/adr/ADR-027.md`
- `docs/architecture/adr/ADR-028.md`
- `docs/architecture/adr/ADR-034.md`
- `docs/architecture/adr/ADR-038.md`

## Relevant Stories

- `0063` - Deterministic Trend Context Engine.
- `0064` - Trend Context Observation Integration.
- `0071` - Market Structure Foundation.
- `0159` - Surface Evidence-Led Opportunity Decisions in the Trader Cockpit.
- `0160` - Deterministic Trade Scenario Projection.
- `0162` - Position Intelligence View.
- `0163` - Deterministic Market Thesis Composition.

## Relevant Modules

- `market-intelligence`
- `market-data` (existing provider-neutral facts and contract compatibility)
- `trading-core` (provenance compatibility only, if required)
- `risk-domain` (no semantic changes; compatibility verification only)

## Architecture Decision Gate

ADR-076 is sufficient if this Story reuses existing Market Structure and Trend
Context authority and only adds thesis-level role composition. A new ADR is
required if implementation changes the authority or semantics of Market
Structure, introduces a competing level engine, changes executable Stop/Target
ownership, or changes Risk or challenge behavior.

## Validation

- Focused Market Intelligence domain tests for level-role classification and
  objective selection.
- Tests for bullish and bearish level ordering.
- Tests for protected-level and structural-invalidation semantics.
- Tests for objective absence and obstacle absence.
- Tests for opposing structural obstacles between reference and objective.
- Tests for missing or unavailable reference price.
- Tests for stale, invalid, insufficient, and incompatible source evidence.
- Tests for multi-timeframe source provenance and cutoff compatibility.
- Tests proving structural invalidation and objective basis remain distinct from
  executable Stop and Target values.
- Deterministic replay and fingerprint tests.
- Immutable thesis persistence and version-lineage tests.
- Negative tests proving no Risk, execution, StrategyMatch, Opportunity, or
  human-authorization boundary is called or changed.
- Affected Market Intelligence Maven tests and formatting checks.
- `git diff --check`.
- No cTrader runtime validation is required unless the implementation expands
  beyond the provider-neutral existing OHLC/evidence contracts.

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

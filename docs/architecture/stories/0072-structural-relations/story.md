# Story 0072 - Reusable Structural Relations

## Metadata

**ID:** `0072`

**Title:** Reusable Structural Relations

**Status:** In Progress

**Predecessor:** Story 0071 - Market Structure Foundation

**Size:** Medium

**Implementation Risk:** High

**ADR:** ADR-049 - Reusable Deterministic Market Structure Capability

**Related Stories:** Stories 0062, 0063, 0064, 0065, and 0071

This Story is a design artifact. It does not authorize implementation, commit,
push, merge, deployment, or creation of additional Story artifacts.

## Goal

Make the existing deterministic structural relations reusable from the Market
Structure authority while preserving the accepted Trend Context semantics.

The result must expose explicit relation evidence between consecutive retained
confirmed swings of the same type:

```text
retained confirmed SwingPoints
        |
        v
structural relations
  HIGH: HH / LH / EQ_HIGH
  LOW:  HL / LL / EQ_LOW
        |
        v
Trend Context and future deterministic consumers
```

This Story extracts and reuses the existing relation calculation. It does not
introduce a new market-structure methodology or claim that any relation is a
trading signal.

## Context

Story 0071 established confirmed neutral SwingPoint evidence as the single
structural authority inside Market Intelligence. Trend Context now consumes
those retained points, but it still owns the relation calculation that compares
the latest two retained highs and latest two retained lows.

The current accepted semantics are implemented in
`TrendContextEngine.relation(...)` and `SwingRelation`:

- fewer than two same-type swings produce no relation;
- a higher latest high produces `HH`;
- a lower latest high produces `LH`;
- an equal latest high produces `EQ_HIGH`;
- a higher latest low produces `HL`;
- a lower latest low produces `LL`;
- an equal latest low produces `EQ_LOW`.

ADR-049 permits later migration of structural relations, but requires one
authority, deterministic replay, explicit provenance, and preservation of the
existing Trend Context behavior.

## Problem

Structural relations remain private to Trend Context. A future deterministic
consumer would either depend on the higher-level Trend Context assessment or
duplicate comparisons over SwingPoints. Duplication could produce conflicting
HH/HL/LH/LL or equality semantics.

The relation result must also remain distinct from higher-level interpretation:
an `HH` or `HL` is structural evidence, not a direction, regime, phase,
strategy condition, opportunity, risk decision, or execution authorization.

## Scope

### Included

- Define a provider-neutral, deterministic structural relation result in the
  existing Market Structure capability boundary.
- Calculate relations only from retained, confirmed Market Structure points.
- Preserve exact comparison semantics using the existing numeric values; do not
  introduce tolerance, rounding, or tick-size assumptions.
- Preserve separate high and low relation semantics:
  - `HH`, `LH`, and `EQ_HIGH` compare consecutive retained high points;
  - `HL`, `LL`, and `EQ_LOW` compare consecutive retained low points.
- Preserve chronological ordering and explicit relation scope of one market, one
  interval, one cutoff, and one algorithm/policy identity.
- Preserve references to both compared SwingPoints and their source/provenance
  identity.
- Represent insufficient same-type evidence explicitly rather than converting it
  to a neutral or directional relation.
- Make Trend Context consume the extracted relation result instead of calculating
  the same latest-pair comparison independently.
- Preserve the existing Trend Context assessment fields and downstream behavior
  for accepted fixtures.
- Reuse the existing capability, artifact, `AnalysisExecution`, fingerprint,
  freshness, and provenance conventions established by Story 0071.
- Add deterministic unit, capability, and Trend Context regression coverage.

### Relation semantics

For each interval, the relation calculation operates on the retained confirmed
SwingPoints already produced by Story 0071. Suppressed candidates are not valid
inputs to the authoritative relation result.

For same-type points ordered by pivot time, compare the latest point with its
immediate predecessor:

```text
high latest > high previous  -> HH
high latest < high previous  -> LH
high latest = high previous  -> EQ_HIGH

low latest > low previous    -> HL
low latest < low previous    -> LL
low latest = low previous    -> EQ_LOW
```

The implementation may expose the complete adjacent relation sequence or a
latest relation projection, provided the compared points, ordering, scope, and
provenance are preserved and Trend Context receives the equivalent current
latest relation values.

## Out of Scope

- A second SwingPoint or pivot engine.
- Changes to strict pivot detection, confirmation, suppression, cutoff, or
  evidence eligibility established by Story 0071.
- Provisional, repainting, or unconfirmed structural points.
- Direction, regime, phase, alignment, attention, or multi-timeframe
  interpretation.
- Protected-level migration.
- Structural breaks, failed-break reclaim, transitions, BOS, or CHOCH.
- Trend lines or chart-specific projections.
- ATR, EMA, volatility, prominence, ZigZag, or adaptive relation thresholds.
- Tolerance-based equality, price normalization, or provider-specific precision
  rules.
- StrategyDefinition, StrategyEvaluation, StrategyMatch, or
  TradingOpportunity changes.
- TradePlan, Risk, broker, execution, or financial authorization changes.
- AI, ML, ranking, or probabilistic interpretation.
- A standalone Market Structure database, table, read model, or event stream.
- A catalogue-wide relation cache or passive scanner.
- Direct Observation creation by Market Structure.
- Changes to public APIs, frontend presentation, or gateway routing.

## Acceptance Criteria

### AC1 - Single relation authority

- Structural relations are calculated from the retained SwingPoints produced by
  the Story 0071 Market Structure authority.
- No independent relation comparison remains in the Trend Context production
  path.
- Any compatibility adapter delegates to the extracted relation result and does
  not duplicate business logic.

### AC2 - Exact existing semantics

- Consecutive retained highs produce exactly `HH`, `LH`, or `EQ_HIGH` according
  to exact numeric comparison.
- Consecutive retained lows produce exactly `HL`, `LL`, or `EQ_LOW` according
  to exact numeric comparison.
- High and low sequences are evaluated independently.
- A relation is not produced when fewer than two eligible same-type points exist.
- Suppressed candidates do not alter the authoritative relation.
- Equal values retain equality semantics and are not forced into an up or down
  relation.

### AC3 - Temporal honesty and identity

- Every relation is scoped to one market, one interval, one explicit cutoff, and
  one algorithm/policy identity.
- Both compared SwingPoint references are retained.
- No relation uses a SwingPoint whose confirmation timestamp is after the
  analysis cutoff.
- No evidence after the cutoff affects the relation result.
- The relation result preserves input fingerprint, source references, rule/policy
  identity, and material result fingerprint using existing conventions.

### AC4 - Explicit evidence status

- Valid evidence with fewer than two same-type points is distinguishable from
  unavailable, stale, insufficient, or invalid source evidence.
- A missing relation is not represented as a valid bearish, bullish, neutral, or
  otherwise directional conclusion.
- Invalid or conflicting input cannot produce an apparently valid relation.

### AC5 - Trend Context compatibility

- Trend Context consumes the extracted relation result.
- Existing `highRelation` and `lowRelation` values remain semantically
  equivalent for accepted fixtures.
- Existing direction, regime, phase, protected-level, break, transition,
  pullback, extension, invalidation, alignment, attention, observation, and
  strategy behavior remains unchanged.
- Any representation or fingerprint change caused by the extraction is
  documented and covered by regression tests.

### AC6 - Capability and artifact boundaries

- Relations are integrated through the existing Market Structure capability and
  artifact lifecycle.
- Identical market/interval/cutoff/algorithm/policy/input requests can reuse the
  same relation result within one `AnalysisExecution` through existing identity
  and provenance behavior.
- No second execution aggregate, scanner, service, or generic capability
  framework is introduced.
- No standalone relation persistence projection is introduced.

### AC7 - Deterministic validation

- Tests cover HH, LH, EQ_HIGH, HL, LL, and EQ_LOW.
- Tests cover insufficient highs, insufficient lows, mixed high/low sequences,
  suppressed candidates, exact equality, cutoff boundaries, and post-cutoff
  evidence.
- Tests prove deterministic output for identical evidence, cutoff, algorithm,
  and policy inputs.
- Existing Trend Context, capability, observation, strategy, and relevant Market
  Data regression tests pass.
- `git diff --check` passes.

### AC8 - Forbidden side effects remain absent

- Relation calculation creates no StrategyMatch, TradingOpportunity, TradePlan,
  Risk result, execution command, or financial authorization.
- Market Structure does not create an Observation.
- No provider credentials, broker payloads, or provider-specific trading
  concepts enter the relation domain.

## Constraints

- Preserve the service responsibilities defined by the repository architecture.
- Respect ADR-048 evidence authority and ADR-049 Market Structure ownership.
- Keep relation calculation pure and deterministic.
- Reuse the Story 0071 confirmed SwingPoint authority; do not reimplement it.
- Preserve explicit cutoff, confirmation, provenance, and fingerprint semantics.
- Do not promote current profile values into universal relation thresholds.
- Do not introduce equality tolerance without a new explicit architectural and
  domain decision.
- Do not change accepted Trend Context semantics silently.
- Stop and report if implementation requires changing ADR-049 ownership,
  persistence, Observation, strategy, risk, or execution boundaries.
- Preserve unrelated worktree changes.
- Do not commit, push, merge, or deploy automatically.

## Relevant ADRs

- `docs/architecture/adr/ADR-048.md` - Intelligence Evidence and Authority
- `docs/architecture/adr/ADR-049.md` - Reusable Deterministic Market Structure
  Capability

## Relevant Modules

- `market-intelligence`
- `market-data` only for existing provider-neutral evidence contracts and
  regression validation; no ownership change is authorized

## Dependencies

- Story 0071 must be implemented and accepted as the single confirmed
  SwingPoint authority.
- Existing Trend Context relation fixtures and semantics are the compatibility
  baseline.
- Existing capability/artifact and `AnalysisExecution` boundaries remain
  available for reuse.

## Validation

- Add focused Market Structure relation engine tests using fixed candles,
  explicit cutoffs, stable source IDs, and exact decimal values.
- Add capability/artifact tests for relation identity, provenance, status, and
  reuse within one `AnalysisExecution`.
- Add Trend Context regression tests proving equivalent `highRelation` and
  `lowRelation` values after extraction.
- Run the focused Market Intelligence relation and Trend Context tests.
- Run the complete Market Intelligence Maven test suite.
- Run relevant Market Data normalization/history tests.
- Run `git diff --check`.
- Execute runtime or replay validation when required by the current workflow and
  report any unexecuted path explicitly.
- Complete independent review focused on single authority, equality semantics,
  cutoff integrity, provenance, and forbidden side effects.

## Definition of Done

- [ ] Story 0072 is approved by the human engineer.
- [ ] Repository Analysis is accepted if required by the project workflow.
- [ ] Implementation Plan is accepted if required by the project workflow.
- [ ] One deterministic Market Structure relation authority is implemented.
- [ ] HH, LH, EQ_HIGH, HL, LL, and EQ_LOW semantics are preserved exactly.
- [ ] Insufficient and invalid evidence remain explicit and fail closed.
- [ ] Compared SwingPoint references, cutoff, input identity, and provenance are
      preserved.
- [ ] Trend Context consumes extracted relations without duplicate comparison
      logic.
- [ ] Existing Trend Context semantics remain regression-compatible.
- [ ] No standalone persistence, direct Observation creation, or financial side
      effect is introduced.
- [ ] Relevant tests, module validation, and diff checks pass.
- [ ] Runtime/replay validation is completed or explicitly reported as not
      executed.
- [ ] Independent code review is completed.
- [ ] Human approval is obtained before commit.

## Final Verdict

**IN PROGRESS - VALIDATION AND HUMAN REVIEW REQUIRED**

ADR-049 provides the architectural direction for a reusable structural
relation layer after the confirmed SwingPoint foundation. The implementation
has started under the approved scope; final acceptance remains subject to
validation, independent review, and human approval before commit.

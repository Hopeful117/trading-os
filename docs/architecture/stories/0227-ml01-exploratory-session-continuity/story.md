# Story 0227 - ML-01 Exploratory Session Continuity

## Metadata

**ID:** `0227`
**Title:** ML-01 Exploratory Session Continuity
**Status:** Review
**Priority:** P1 - Research Trustworthiness
**Related ADRs:** `ADR-092`, `ADR-093`, `ADR-094`, `ADR-095`
**Predecessor:** `Story 0226` - ML-01 Exploratory H1 Admissibility Evaluator
**Related Story:** `Story 0225` - Historical Continuity and ML-01 Verified Closure Crossing

## Goal

Implement the bounded, deterministic and research-only HistData session
continuity policy required by ADR-095 for the ML-01 exploratory population.
The policy must distinguish ordinary H1 adjacency from the explicitly permitted
HistData weekend-session assumption without changing strict Market Data quality
or closure evidence.

## Context

Story 0226 provides the immutable H1 exploratory admissibility decision and
research dataset preparation boundary. HistData contains a recurring source-
local interruption from Friday 17:00 to Sunday 17:00. ADR-095 permits one
bounded retrospective session transition for this offline research profile,
but does not permit treating the transition as a verified Market Data closure.

The output remains non-operational. The strict continuity classification must
remain fail-closed and separately visible from the exploratory continuity
stratum. No result may become canonical Market Data evidence or a trusted
`FeatureSnapshot`.

## Scope

- Add a named, versioned evaluator for the bounded HistData weekend-session
  convention.
- Accept only a Friday 17:00 source-local boundary followed by a Sunday 17:00
  to 18:00 source-local reopening.
- Enforce a maximum elapsed gap of 49 hours and at most one assumed session
  transition per feature-plus-outcome window.
- Preserve `CONTIGUOUS`, `UNEXPLAINED_GAP`, `INVALID_OBSERVATION` and other
  strict quality evidence without relabeling it as `VERIFIED_CLOSURE`.
- Produce explicit `CONTIGUOUS_ONLY` and
  `ASSUMED_SESSION_TRANSITION` research strata.
- Count feature, future and combined assumed transitions independently.
- Preserve actual timestamps, elapsed durations, transition classifications and
  policy provenance in rows, manifests and quality reports.
- Include the continuity policy in deterministic dataset fingerprints and
  published-bundle validation.
- Add deterministic unit and focused integration tests for valid boundaries,
  invalid gaps, multiple transitions, dataset edges and strict-provenance
  isolation.

## Out Of Scope

- Creating or changing Market Data verified closure evidence.
- Converting a weekend assumption into `VERIFIED_CLOSURE`.
- Provider calendars, session-calendar services or a generic continuity engine.
- Source repair, interpolation, forward fill, backfill or synthetic candles.
- Production `FeatureSnapshot` evidence or operational trading behavior.
- Model training, clustering, ranking, serving or broker operations.
- New services, APIs, MCP permissions, AgentGrants or database migrations.
- Scientific partition approval, minimum sample-size approval or ML readiness
  decisions beyond recording the required blockers.

## Architectural Constraints

- Market Data remains authoritative for strict historical evidence and closure
  classification.
- Market Intelligence owns the exploratory consumer policy and derived bundle
  metadata.
- The implementation must reuse Story 0226's parsed HistData and admissibility
  contracts rather than introduce a second parser.
- The policy is retrospective research-only and must be identified by a stable
  policy ID and version.
- Unknown, contradictory or invalid observations fail closed.
- The MCP Adapter and AI Engine remain bounded consumers and execution cannot
  be reached from this Story.

## Acceptance Criteria

- [ ] Exact H1 adjacency is classified as `CONTIGUOUS`.
- [ ] A valid Friday 17:00 to Sunday 17:00 source-local transition is classified
      as `ASSUMED_SESSION_TRANSITION` only when all bounded policy rules pass.
- [ ] Sunday reopening at 18:00 is accepted; later reopening is rejected.
- [ ] Gaps outside the bounded weekend convention, observed activity inside the
      assumed closure, invalid observations and dataset-edge transitions fail
      closed as non-assumed transitions.
- [ ] The evaluator enforces a maximum elapsed gap of 49 hours.
- [ ] Feature and future windows record separate and combined transition counts.
- [ ] Windows containing more than one assumed transition are unavailable.
- [ ] Research rows and manifests expose `CONTIGUOUS_ONLY` or
      `ASSUMED_SESSION_TRANSITION` without mutating strict evidence.
- [ ] Strict unexplained-gap provenance remains visible in quality diagnostics
      and continues to fail closed.
- [ ] Policy ID, version, parameters, source timezone and transition limit are
      included in deterministic provenance and dataset fingerprints.
- [ ] Published-bundle validation verifies the continuity policy contract.
- [ ] Repeated evaluation with identical evidence is deterministic.
- [ ] No canonical production evidence, broker mutation or autonomous execution
      authority is introduced.

## Testing Expectations

- Unit tests for exact adjacency, valid weekend transitions, 18:00 reopening,
  late reopening, wrong weekday, observed interior activity, invalid quality,
  dataset edges and multiple transitions.
- Focused preparation tests proving assumed strata are emitted while strict
  unexplained-gap diagnostics remain fail-closed.
- Regression tests for existing contiguous feature and outcome preparation.
- Export and bundle-fingerprint regression tests.
- Full affected-module Maven tests and `git diff --check`.

## Validation Boundary

Validation is offline and bounded to the Market Intelligence module. No
provider download, live market operation, MCP execution, model training or
broker mutation is required or permitted.

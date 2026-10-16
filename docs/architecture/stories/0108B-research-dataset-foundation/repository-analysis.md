# Repository Analysis - Story 0108B

Date: 2026-10-16 · Branch: `main` · Base: `df78d857770f4477a97752f3d1aba91659465729`

## Sources and Authority

- The current repository implementation and tests are authoritative for physical
  state.
- Story 0108B defines the required deterministic dataset boundary and its
  non-goals.
- ADR-060 defines the immutable feature snapshot and no-lookahead boundary.
- ADR-061 defines explicit immutable outcome-version selection.
- ADR-062 records the proposed research dataset boundary implemented by this
  Story.
- DevLog context was unavailable because the configured provider reported an
  insufficient-quota error.

## Current Repository Findings

The affected module is `market-intelligence`. Immutable `FeatureSnapshot` and
`ObservedMarketOutcome` are already the authoritative inputs. The Story adds a
research-only application boundary that joins those facts only through an
explicit `ResearchPopulationSpec`.

V1 supports one sample per feature snapshot and configured outcome horizon. It
requires strict observed feature evidence, validates the fixed
`feature-projection-v1` schema, preserves typed values and missingness, and
requires an explicitly selected outcome fingerprint for every horizon.

Incomplete, pending, unavailable, missing, mismatched, invalid, and duplicate
inputs are represented through deterministic sample exclusion reasons. Conflicting
immutable identities fail loudly rather than allowing input order to determine
the dataset.

No persistence, Flyway migration, public API, Gateway, frontend, Risk, Broker,
Execution, Python, or ML surface is required for this V1 boundary.

## Affected Files

### New production areas

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/research/`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/`

### New tests

- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/research/ResearchDatasetBuilderTest.java`

### Architecture and Story documentation

- `docs/architecture/adr/ADR-062.md`
- `docs/architecture/stories/0108B-research-dataset-foundation/story.md`

## Risks and Validation Gaps

- PostgreSQL runtime validation was not executed because V1 introduces no new
  persistence or migration.
- The export boundary is deterministic and tested at module level, but no
  deployed consumer or future Python reader is part of this Story.
- Human review and commit remain outstanding.

## Analysis Outcome

The implementation boundary is coherent with ADR-060, ADR-061, ADR-062, and
Story 0108B. No additional architectural decision is required for the current
scope.

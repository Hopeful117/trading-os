# Story 0226 - Implementation Report

## Status

Implemented as a research-only, deterministic evaluator contract. HistData
remains locally owned by Market Intelligence; its strict quality and continuity
classifications are explicitly local derived facts, not authoritative
production Market Data classifications.

## Changes

- Added the versioned `ML01_HISTDATA_EXPLORATORY_V1` profile.
- Added immutable research quality states, rejection reasons and admissibility
  decisions.
- Added bucket-level H1 evidence derived from the existing parsed M1 import.
- Recorded valid counts, missing positions, boundary presence, missing runs and
  bucket-scoped integrity indicators.
- Added a deterministic parsed-source fingerprint.
- Included rejected-row and timestamp quality evidence in the fingerprint.
- Added fail-closed handling for missing or blank source fingerprints.
- Added deterministic local strict continuity classifications derived from the
  existing HistData aggregation state.
- Changed ML-01 preparation to select H1 candidates from final evaluator
  decisions rather than copying the strict `COMPLETE` filter.
- Added the dedicated `ml01-research-features-v1` research-only dataset/export
  contract, including per-row admissibility decisions, quality counts, source
  SHA-256 checksums and reproducible policy/scope fingerprints.
- Explicitly marks session-transition strata as deferred; no canonical
  `FeatureSnapshot` evidence is produced.
- Added focused coverage and malformed-row attribution tests.

No parser, synthetic repair, production API, MCP capability, session-transition
evaluator or ML execution was added.

## Validation

Executed from `market-intelligence`:

```text
mvn -q -Dtest=Ml01H1AdmissibilityEvaluatorTest,HistDataOfflinePipelineTest,Ml01HistDataPreparationTest test
mvn -q verify
```

Focused reports: 10 evaluator tests, 3 pipeline tests, 5 preparation tests and
6 affected export-service tests passed. `git diff --check` passed.

## Limitations

- The current HistData projection does not yet receive authoritative strict
  continuity classifications, so its decisions remain `UNKNOWN_QUALITY` until
  that contract is supplied.
- Session continuity and continuity-stratum window construction remain a
  subsequent Story. The current export is bounded to the explicitly declared
  contiguous-only scope.
- Feature calculations still require their existing contiguous history and may
  exclude otherwise admissible partial candles when the feature prerequisites
  are unavailable.

## Independent Review

The independent review verdict was `NEEDS CHANGES` before this boundary
decision. It confirmed that the fingerprint and blank-fingerprint findings were
fixed. The selected local HistData ownership boundary addresses the source
availability issue, and evaluator-driven candidate selection is now integrated.
- No Git commit, push or merge was performed.

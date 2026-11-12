# Story 0230 - ML-01 Chronological Partitioning And Regime Evaluation Protocol

## Metadata

**ID:** `0230`
**Title:** ML-01 Chronological Partitioning And Regime Evaluation Protocol
**Status:** Approved - implementation in progress
**Priority:** P1 - Research Trustworthiness
**Related ADRs:** `ADR-092`, `ADR-093`, `ADR-094`, `ADR-095`, `ADR-096`, `ADR-097`
**Related Stories:** `Story 0228`, `Story 0229`

**Human Approval:** Recorded on 2026-11-12. This approves the Story scope only;
Repository Analysis and the Implementation Plan remain separate approval gates.

## Goal

Implement the approved ADR-097 research protocol for chronological partitioning,
temporal-dependence control, ML-01.2 exploratory KMeans model selection and
ML-01.3 held-out regime evaluation over the prepared Dukascopy H1 research
bundle.

The implementation must produce auditable, deterministic evidence that
separates technical availability from scientific eligibility, prevents future
label leakage, quantifies temporal dependence and keeps the final test period
untouched until the model-selection record is frozen.

This Story remains offline and research-only. It does not produce a trading
signal, profitability claim, execution decision, risk decision or production
Market Data evidence.

## Context

ADR-097 is accepted and defines the protocol boundary. It preserves ADR-093's
four Java-owned features and shared-model baseline while requiring explicit
chronological partitions, dependency-aware purge, embargo diagnostics,
effective-sample reporting, train-only scaling, predeclared KMeans candidates,
stability diagnostics and untouched-test evaluation.

The prepared source is the Dukascopy BID M1 dataset for:

```text
[2025-10-01T00:00:00Z, 2026-10-01T00:00:00Z)
```

The source fingerprint is:

```text
c212d83c0050374400af8e8c3d9961a2bbcf0f9169fd2c149c0513fc3002f198
```

The latest ML-01.1 evidence reports:

| Instrument | Feature available | Joint available | Joint eligible |
|---|---:|---:|---:|
| EUR/USD | 3,530 | 2,145 | 0 |
| GBP/USD | 2,927 | 1,630 | 0 |

The source has overlapping 33-valid-H1 feature lookbacks and H+4/H+12/H+24
future-outcome dependencies. EUR/USD has 48 joint-available segments and
GBP/USD has 39; raw anchor counts must not be represented as independent sample
counts.

The current candidate diagnostics are feasibility evidence only:

| Instrument | Scenario | Train | Validation | Test |
|---|---|---:|---:|---:|
| EUR/USD | 70/15/15 | 1,495 | 308 | 327 |
| GBP/USD | 70/15/15 | 1,099 | 222 | 294 |
| EUR/USD | 60/20/20 | 1,273 | 340 | 515 |
| GBP/USD | 60/20/20 | 919 | 291 | 420 |

These counts must be recalculated from the final implementation output.

## Scope

- Add a versioned ML-01 experiment configuration for the accepted ADR-097
  protocol.
- Load the deterministic Dukascopy ML-01.1 bundle without reparsing or repairing
  source data.
- Represent each row's complete dependency footprint, including feature
  lookback start, cutoff, future observation timestamps and future dependency
  end.
- Generate common UTC chronological partition candidates for 70/15/15 and
  60/20/20 without selecting a scenario from held-out outcomes.
- Assign rows by cutoff timestamp and preserve rejected, unavailable and
  out-of-partition diagnostics.
- Apply actual dependency-aware future-label purge at protected boundaries.
- Compare no additional embargo and the 24-hour candidate embargo as explicit
  diagnostic configurations.
- Prove that retained training and validation rows do not cross the next
  protected future-outcome boundary.
- Report raw anchors, purged anchors, embargo exclusions, segments, overlap,
  non-overlapping-anchor sensitivity and calendar/instrument distributions.
- Implement train-only `StandardScaler` fitting and application to validation
  and test rows.
- Implement predeclared KMeans candidate execution for configured `k` values and
  seeds, with deterministic configuration and artifact fingerprints.
- Select a model using training/validation structural and stability diagnostics
  only; never use test outcomes for selection.
- Preserve the ADR-093 primary pooled model strategy and report EUR/USD and
  GBP/USD diagnostics separately.
- Evaluate the frozen model on the untouched test period for H+4, H+12 and H+24
  descriptive outcomes.
- Report model and outcome sensitivity for 60/60, 59/60 and 55/60 quality
  populations where observations exist.
- Produce deterministic manifests and research-only artifacts sufficient to
  reproduce partitioning, purge, scaling, model selection and held-out
  evaluation.
- Add focused deterministic tests, leakage tests and reproducibility tests.

## Out Of Scope

- Changing ADR-092 through ADR-097.
- Changing ML-01 feature formulas, outcome formulas or Java-owned contracts.
- Changing Dukascopy acquisition, H1 reconstruction or admissibility policy.
- Repairing, interpolating, forward-filling, backfilling or synthesizing source
  observations.
- Treating 55/60 as scientifically equivalent to 60/60.
- Choosing partition, embargo, minimum effective-sample or stability thresholds
  retrospectively from test outcomes.
- Adding an instrument identity feature to the pooled KMeans model.
- Treating raw anchors as independent observations.
- Publishing canonical `FeatureSnapshot` evidence or production Market Data.
- Creating a trading signal, strategy, profitability claim, risk decision or
  execution intent.
- Adding services, databases, Gateway endpoints, MCP permissions or broker
  capabilities.
- Live-provider access, network downloads or live trading.
- Committing, pushing, merging or creating a pull request automatically.

## Architectural Placement And Constraints

The implementation belongs in the existing Market Intelligence offline
historical research preparation boundary and must consume the deterministic
Dukascopy ML-01.1 bundle produced by Story 0229. It must not become a second
source parser or alter normalized market-data artifacts.

Market Data remains authoritative for production evidence. All partition,
purge, scaling, clustering and evaluation outputs are research artifacts and
must carry explicit research-only metadata.

The implementation must:

- preserve provider, instrument, BID side, UTC timestamps and source
  fingerprints;
- preserve strict and exploratory quality classifications side by side;
- fail closed when dependency timestamps, source fingerprints, feature values or
  outcome provenance are missing or contradictory;
- keep test rows and outcomes inaccessible to model-selection code paths;
- make every policy, feature schema, outcome schema, partition, purge, embargo,
  scaler and model configuration versioned and fingerprinted;
- avoid client-controlled or MCP-controlled scientific policy selection;
- avoid claiming scientific validity from technical execution alone.

## Experiment Configuration

The implementation must use an explicit, immutable experiment configuration with
at least:

```text
Experiment: ML01_DUKASCOPY_REGIME_EVALUATION_V1
Primary partition: COMMON_UTC_70_15_15
Sensitivity partition: COMMON_UTC_60_20_20
Primary model: POOLED_KMEANS_WITH_INSTRUMENT_DIAGNOSTICS
Features: normalized_atr, realized_volatility,
          volatility_compression_ratio, directional_efficiency
Feature lookback: 33 valid H1 observations
Outcome horizons: H+4, H+12, H+24 valid H1 observations
K candidates: 2, 3, 4, 5, 6
Embargo diagnostics: NONE, 24H
Scaler fit: TRAIN_ONLY
Test policy: UNTOUCHED_UNTIL_SELECTION_FREEZE
```

The configuration must also identify deterministic seeds, KMeans initialization
and convergence settings, dependency versions, quality profiles, uncertainty
method, block definition and all acceptance thresholds. Missing values must
fail closed rather than receive implementation defaults.

The 70/15/15 and 60/20/20 values are named candidate scenarios. The Story does
not predeclare that one scenario is scientifically superior; the selected
scenario must be recorded before any final test evaluation.

## Dependency And Leakage Contract

Every candidate row must expose:

- `featureLookbackStart`;
- `cutoff`;
- all actual future observation timestamps used by each horizon;
- `futureEnd`;
- source, row, feature and outcome fingerprints.

The implementation must distinguish legitimate historical feature reuse from
future-label leakage. Historical observations before a later row's cutoff may
remain in its lookback. A row is not retained when its future dependency reaches
or crosses the next protected boundary.

The leakage validator must report every excluded row's reason and dependency
timestamps, then fail the run if any retained training/validation row violates
the protected-boundary rule.

## Acceptance Criteria

- [ ] **AC-01 - Versioned configuration:** Every run identifies the experiment,
      source, quality profile, feature/outcome schemas, partition, purge,
      embargo, scaler, model, seeds and dependency versions.
- [ ] **AC-02 - Source integrity:** The runner refuses a bundle whose source,
      row or configuration fingerprint does not match the declared inputs.
- [ ] **AC-03 - Dependency footprint:** Every retained or rejected row exposes
      its actual feature and future dependency timestamps.
- [ ] **AC-04 - Common UTC partition:** Rows are assigned by common UTC cutoff
      boundaries for the configured 70/15/15 and 60/20/20 scenarios; row-count
      partitioning is not used. Every authorized run must contain distinct
      chronological `TRAIN`, `VALIDATION` and untouched `TEST` populations; a
      train/test-only split is invalid.
- [ ] **AC-05 - Dependency purge:** Training and validation rows whose future
      dependencies cross the next protected boundary are excluded with explicit
      reasons.
- [ ] **AC-06 - Embargo diagnostics:** No-embargo and 24-hour embargo runs are
      separately identified, deterministic and never silently combined.
- [ ] **AC-07 - Leakage proof:** The run fails closed if any retained training
      or validation row crosses a protected future boundary.
- [ ] **AC-08 - Dependence report:** The output reports raw and retained anchors,
      segments, segment lengths, overlap, non-overlapping-anchor sensitivity,
      calendar distribution and instrument distribution.
- [ ] **AC-09 - Effective-sample limitation:** The output does not claim that
      raw anchor counts are independent or that an effective-sample threshold is
      satisfied unless an approved configured method proves it.
- [ ] **AC-10 - Train-only scaling:** `StandardScaler` parameters are fitted
      only from the selected training population and are fingerprinted.
- [ ] **AC-11 - Candidate models:** Configured KMeans candidates run with the
      declared `k` values, seeds, initialization and convergence settings without
      hidden defaults.
- [ ] **AC-12 - Selection isolation:** Model selection uses only training and
      validation structural/stability diagnostics; test features and outcomes
      cannot affect selection.
- [ ] **AC-13 - Pooled baseline:** The primary model excludes instrument identity
      from features and reports EUR/USD and GBP/USD diagnostics separately.
- [ ] **AC-14 - Stability evidence:** Candidate diagnostics include separation,
      compactness, population balance, centroid profiles, seed stability,
      chronological-block stability and temporal persistence.
- [ ] **AC-15 - Selection freeze:** The selected model, partition, scaler and
      configuration are fingerprinted before test evaluation begins.
- [ ] **AC-16 - Untouched test:** ML-01.3 evaluates only the frozen model on the
      untouched test population and cannot alter model-selection artifacts.
- [ ] **AC-17 - Held-out outcomes:** H+4, H+12 and H+24 results report counts,
      distributions, cluster imbalance, instrument breakdown and a predeclared
      dependence-aware uncertainty method.
- [ ] **AC-18 - Quality sensitivity:** 60/60, 59/60 and 55/60 diagnostics remain
      distinguishable and are not silently merged or relabeled.
- [ ] **AC-19 - Determinism:** Repeated execution with identical inputs produces
      byte-identical manifests and equivalent model/evaluation artifacts.
- [ ] **AC-20 - Research isolation:** No output is published as production Market
      Data, canonical `FeatureSnapshot`, trading signal or risk evidence.
- [ ] **AC-21 - Fail closed:** Missing provenance, non-finite features,
      contradictory dependencies, invalid configuration or leakage causes a
      visible failure rather than a partial scientific result.
- [ ] **AC-22 - Explicit limitations:** Reports state unresolved sample,
      dependence, partial-H1, instrument-comparability and uncertainty limits.

## Testing Expectations

Tests must include:

- exact UTC boundary assignment tests;
- both partition scenarios and boundary inclusivity/exclusivity;
- future-dependency crossing and non-crossing purge cases;
- no-embargo and 24-hour embargo separation;
- legitimate historical feature overlap without false leakage rejection;
- leakage proof failure for a deliberately crossing retained row;
- segment, overlap and non-overlapping-anchor diagnostics;
- train-only scaler fitting with validation/test contamination guards;
- deterministic KMeans seed and configuration handling;
- selection isolation proving test data cannot affect candidate choice;
- pooled model feature-schema tests excluding instrument identity;
- untouched-test enforcement after selection freeze;
- quality-profile separation for 60/60, 59/60 and 55/60;
- missing, non-finite and contradictory provenance fail-closed cases;
- repeated-run manifest and artifact reproducibility;
- research-isolation tests proving no canonical Market Data publication.

Use the existing Market Intelligence Java test conventions and avoid network
calls, broker calls or live data during tests.

## Validation

- Focused Market Intelligence tests for partitioning, temporal dependence,
  purge, embargo, scaling, model selection and held-out evaluation.
- Affected-module Maven test suite.
- Deterministic repeated-run and fingerprint verification.
- `git diff --check`.
- No provider download, broker access, MCP execution or live mutation.
- Full dataset execution only after implementation gates are approved; generated
  research artifacts remain outside Git according to repository conventions.

## Definition Of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved.
- [ ] Protocol implementation completed within the Market Intelligence research
      boundary.
- [ ] All acceptance criteria pass.
- [ ] Leakage proof and temporal-dependence evidence are present.
- [ ] Model-selection and held-out evaluation artifacts are fingerprinted.
- [ ] Research-only boundaries are enforced.
- [ ] Relevant tests and quality validation pass.
- [ ] Implementation Report records exact changes, evidence and limitations.
- [ ] Documentation reconciliation is complete.
- [ ] Code Review completed.
- [ ] Human commit created separately.

## Constraints

- Preserve ADR-092 Java-controlled research and MCP boundaries.
- Preserve ADR-093 feature, outcome, leakage and non-operational semantics.
- Preserve ADR-094 strict historical continuity and verified-closure authority.
- Preserve ADR-095 and ADR-096 provider-specific admissibility boundaries.
- Implement ADR-097 without changing its approved diagnostic-vs-decision
  distinctions.
- Do not modify pre-existing user changes or generated runtime artifacts.
- Do not commit, push or merge automatically.

# Story 0229 - Dukascopy Exploratory H1 Admissibility And Research Dataset

## Metadata

**ID:** `0229`
**Title:** Dukascopy Exploratory H1 Admissibility And Research Dataset
**Status:** Approved - Implementation in progress
**Priority:** P1 - Research Trustworthiness
**Related ADRs:** `ADR-092`, `ADR-093`, `ADR-094`, `ADR-095`, `ADR-096`
**Related Stories:** `Story 0225`, `Story 0226`, `Story 0227`, `Story 0228`

## Goal

Implement the approved, provider-scoped `ML01_DUKASCOPY_EXPLORATORY_V1`
research boundary from ADR-096. The implementation must deterministically
reconstruct UTC H1 candles from the acquired Dukascopy M1 BID dataset, classify
H1 quality, preserve strict continuity evidence, and produce research-only
feature/outcome eligibility and provenance without changing production Market
Data semantics.

This Story is an offline research implementation. It does not authorize
ML-01.2, KMeans, model training, operational exports, live trading or risk
decisions.

## Context

Story 0228 acquired the reproducible Dukascopy dataset for:

```text
[2025-10-01T00:00:00Z, 2026-10-01T00:00:00Z)
```

The dataset fingerprint is:

```text
c212d83c0050374400af8e8c3d9961a2bbcf0f9169fd2c149c0513fc3002f198
```

The normalized source contains M1 BID observations for EUR/USD and GBP/USD.
The acquisition and `DukascopyOfflinePipeline` currently provide UTC M1
parsing, OHLCV validation, duplicate/conflict handling, missing-interval
diagnostics and assembly. They do not yet provide a Dukascopy-specific H1
research admissibility and eligibility bundle.

The completed ML-01.1 experiments found:

| Diagnostic | EUR/USD | GBP/USD |
|---|---:|---:|
| M1 observations | 371,614 | 371,266 |
| Fully observed H1 | 5,588 | 5,551 |
| Partially observed H1 | 638 | 675 |
| Strict 33-H1 feature anchors | 93 | 24 |
| Strict joint feature/outcome anchors | 0 | 0 |
| 55/60 diagnostic 24-H1 windows, no session assumptions | 2,145 | 1,630 |

The 55/60 figures are diagnostic sensitivity counts, not final eligible row
counts. This Story must recalculate all populations from implementation output.

ADR-096 explicitly forbids reuse of the accepted HistData UTC-05 session
convention from ADR-095. The Dukascopy first implementation uses strict
continuity only and must not cross assumed session transitions.

## Scope

- Add a named and versioned Dukascopy research admissibility profile:
  `ML01_DUKASCOPY_EXPLORATORY_V1`.
- Reconstruct deterministic UTC H1 buckets from observed Dukascopy M1 records.
- Apply the proposed 55/60 minimum with mandatory first and last minutes.
- Preserve the strict baseline and support diagnostic comparison with 60/60 and
  59/60 without making either comparison population disappear.
- Classify research quality as `FULL_OBSERVED`, `PARTIAL_EXPLORATORY`,
  `REJECTED` or `UNKNOWN_QUALITY`.
- Preserve exact duplicate collapse, conflicting duplicate rejection, invalid
  OHLC rejection, malformed-row rejection and chronology diagnostics.
- Record missing-minute positions, missing count, boundary state and maximum
  consecutive missing-minute run.
- Preserve original strict quality and continuity classifications without
  relabeling them as production facts.
- Implement `CONTIGUOUS_ONLY` research continuity for eligible H1 observations.
- Reject feature or outcome windows crossing unknown, unexplained, rejected or
  missing H1 intervals.
- Do not implement or admit assumed session transitions.
- Reuse unchanged Java ML-01 feature definitions and existing deterministic
  outcome definitions where their contracts are compatible.
- Check actual availability of all four features and each of H+4, H+12 and H+24
  outcomes.
- Produce separate diagnostic counts for feature-only, per-horizon and joint
  feature/outcome eligibility.
- Preserve quality, continuity, feature, outcome and rejection metadata in a
  deterministic research-only bundle.
- Include source, policy, continuity, feature, outcome and configuration
  fingerprints.
- Report partition, purge, embargo and minimum-effective-sample readiness as
  explicit approval requirements rather than inventing parameters.
- Add deterministic tests and focused integration tests for the Dukascopy
  research boundary.

## Out Of Scope

- Changing ADR-092, ADR-093, ADR-094, ADR-095 or ADR-096.
- Modifying accepted HistData policy or the HistData UTC-05 session convention.
- Implementing assumed Dukascopy session transitions or weekend normalization.
- Creating or asserting verified Dukascopy closure evidence.
- Changing production Market Data quality, continuity or closure semantics.
- Publishing exploratory values as canonical `FeatureSnapshot` evidence.
- Changing `FeatureProjectionV2`, `VolatilityCalculator` or outcome formulas.
- Choosing chronological partition boundaries, purge duration, embargo duration
  or minimum effective sample sizes without separate scientific approval.
- Running KMeans, ML training, model selection, clustering or ML-01.2.
- Creating a generic quality-policy framework or session-calendar service.
- Merging Dukascopy with HistData or another provider.
- Adding a new service, database, Gateway endpoint, MCP permission or broker
  capability.
- Provider acquisition changes, network downloads or source-data repair.
- Live trading, risk evaluation, execution or automation.

## Architectural Placement And Constraints

The implementation belongs in the existing Market Intelligence historical
research preparation boundary and must consume `DukascopyOfflinePipeline`
evidence. It must not become a second Dukascopy parser or modify the normalized
source files.

Market Data remains authoritative for production historical evidence and ADR-094
continuity semantics. Any Dukascopy H1 quality or admissibility decision is
derived Market Intelligence research metadata and must be explicitly marked as
non-production.

The implementation must:

- preserve provider identity, instrument identity, BID price-side semantics and
  UTC timestamps;
- use actual observed prices only;
- never interpolate, forward-fill, backfill or synthesize a candle;
- preserve actual elapsed timestamps and price jumps;
- fail closed for unknown quality, invalid data and unknown continuity;
- keep strict classifications visible beside exploratory decisions;
- keep quality strata and continuity strata separately reportable;
- avoid client-controlled or MCP-controlled scientific policy selection.

## Research Profile

```text
Policy ID: ML01_DUKASCOPY_EXPLORATORY_V1
Minimum unique valid M1 observations: 55/60
First M1 minute required: YES
Last M1 minute required: YES
Conflicting duplicates: REJECT
Invalid OHLC: REJECT
Malformed observations: REJECT
Chronology anomalies affecting the bucket: REJECT
Missing-minute positions: RECORD
Maximum consecutive missing-minute run: RECORD ONLY
Additional missing-run rejection threshold: NONE
Interpolation, forward fill, backfill and synthetic candles: FORBIDDEN
Session transitions: NOT PERMITTED
Continuity stratum: CONTIGUOUS_ONLY
Usage: OFFLINE EXPLORATORY ML-01 RESEARCH ONLY
```

The 60/60 and 59/60 populations must remain available as comparison
diagnostics. A larger 55/60 count must not be used as evidence of scientific
validity.

## H1 Construction Contract

For each observed valid M1 record, the implementation assigns the record to the
half-open UTC bucket:

```text
[floorToUtcHour(openTime), floorToUtcHour(openTime) + 1 hour)
```

For each bucket, it computes from actual observed M1 records only:

```text
open  = earliest observed M1 open
high  = maximum observed M1 high
low   = minimum observed M1 low
close = latest observed M1 close
```

The H1 record must retain the UTC bucket bounds independently of the first and
last observed minute. It must not pretend that missing minutes were present.

An exact duplicate timestamp with identical OHLCV values may be collapsed to a
single unique observation and must increment duplicate metadata. A conflicting
duplicate timestamp rejects the affected bucket. No conflicting value may be
selected silently.

Every H1 record must retain at least:

- unique valid M1 count;
- observed minute positions;
- missing-minute positions and count;
- first-minute presence;
- last-minute presence;
- maximum consecutive missing-minute run;
- exact duplicate count;
- conflict, invalid, malformed and chronology indicators;
- original strict H1 quality state;
- derived exploratory quality state;
- deterministic rejection reasons;
- source file and row provenance where available.

## Continuity Contract

The Story implements only `CONTIGUOUS_ONLY`:

- adjacent eligible H1 observations must satisfy exact one-hour adjacency;
- a missing or rejected H1 bucket breaks continuity;
- an unknown or unexplained gap rejects the affected dependency window;
- a complete missing H1 interval cannot be crossed because it resembles a
  weekend closure;
- no assumed session transition is created;
- verified closure evidence is not created or inferred by this Story;
- actual previous and next timestamps remain visible in diagnostics.

The strict source continuity classification must remain preserved. A
`PARTIAL_EXPLORATORY` H1 candle can participate in a contiguous research
sequence, but it does not authorize crossing any missing H1 interval.

## Feature And Outcome Contract

For every candidate H1 evaluation candle and each requested horizon:

- the feature lookback contains 33 valid H1 observations;
- all feature dependencies satisfy the Dukascopy policy and continuity rules;
- all four `FeatureProjectionV2` features are actually available;
- H+4, H+12 and H+24 use valid future H1 observations, not elapsed-time
  substitution;
- future outcomes are computed from actual future OHLC observations;
- unknown, rejected, missing or synthetic dependencies exclude the affected
  horizon;
- no future outcome is used in feature construction, admissibility selection or
  any model decision.

The output must distinguish:

1. feature-only structural eligibility;
2. feature-only Java-calculator availability;
3. H+4 eligibility;
4. H+12 eligibility;
5. H+24 eligibility;
6. joint eligibility requiring all four features and all three outcomes.

The joint population must additionally report whether chronological partition,
purge and embargo configuration is unavailable because it still requires
scientific approval. The Story must not invent a ready population by treating
diagnostic 24-H1 counts as joint ML-01 rows.

## Research Provenance Contract

Each H1 and derived row must retain or reference:

- provider, instrument and canonical market identity;
- price side and source timeframe;
- source dataset and manifest fingerprints;
- source file checksums;
- UTC timestamp semantics and research bounds;
- H1 admissibility policy ID and version;
- continuity policy ID and version;
- H1 quality stratum;
- original strict quality and continuity classifications;
- M1 count, missing positions, boundary flags and maximum missing run;
- duplicate, conflict, invalid, malformed and chronology diagnostics;
- feature projection version and per-feature availability;
- outcome definition version and per-horizon availability;
- rejection reason and dependency timestamps;
- actual elapsed lookback and future durations;
- research configuration fingerprint;
- deterministic row and bundle fingerprints.

Changing source data, policy version, continuity policy, feature version,
outcome definition or research configuration must produce a new bundle
fingerprint and must not overwrite prior artifacts.

## Scientific Safeguards

The implementation must prepare the evidence needed to compare:

- strict 60/60, conservative 59/60 and exploratory 55/60;
- full-only versus full-plus-partial populations;
- `FULL_OBSERVED` versus `PARTIAL_EXPLORATORY` rows;
- partial rows by missing-minute count and maximum run;
- EUR/USD versus GBP/USD;
- feature distributions, availability and rejection reasons;
- H+4, H+12 and H+24 outcome distributions and availability;
- rolling overlap, unique H1 units, sessions or trading days and non-overlapping
  diagnostics.

The implementation must not introduce numerical acceptance thresholds for
partial-candle bias, missing runs or effective samples. It must report missing
scientific criteria explicitly and prevent ML-01.2 from being inferred from
successful bundle construction.

## Acceptance Criteria

- [ ] **AC-01 - Profile identity:** Every decision identifies
      `ML01_DUKASCOPY_EXPLORATORY_V1` and its version.
- [ ] **AC-02 - UTC H1 reconstruction:** Identical normalized M1 input produces
      identical UTC H1 timestamps and OHLC values using only observed prices.
- [ ] **AC-03 - Full candle:** 60 unique valid M1 records with no disqualifying
      event produce `FULL_OBSERVED`.
- [ ] **AC-04 - Qualified partial:** 55-59 unique valid M1 records with both
      boundary minutes and no disqualifying event produce
      `PARTIAL_EXPLORATORY`.
- [ ] **AC-05 - Insufficient coverage:** Fewer than 55 unique valid M1 records
      produce `REJECTED`.
- [ ] **AC-06 - Boundary protection:** Missing first or last minute produces
      `REJECTED` even when the count is at least 55.
- [ ] **AC-07 - Duplicate policy:** Exact duplicates are collapsed and recorded;
      conflicting duplicates reject the affected H1 bucket.
- [ ] **AC-08 - Invalid input:** Malformed rows, invalid OHLC, invalid timestamps
      and chronology anomalies affecting a bucket fail closed.
- [ ] **AC-09 - Unknown quality:** Insufficient provenance produces
      `UNKNOWN_QUALITY` and excludes the bucket.
- [ ] **AC-10 - Missingness provenance:** Missing positions, count, boundaries,
      maximum run and all rejection reasons are retained deterministically.
- [ ] **AC-11 - No repair:** No interpolation, forward-fill, backfill or
      synthetic candle is produced.
- [ ] **AC-12 - Strict evidence isolation:** Strict Market Data quality and
      continuity classifications are unchanged and remain visible.
- [ ] **AC-13 - Strict continuity:** Only exact adjacent eligible H1 observations
      form `CONTIGUOUS_ONLY` sequences; unknown or missing H1 intervals reject
      affected windows.
- [ ] **AC-14 - No session assumption:** No weekend or other assumed session
      transition is admitted, inferred or labelled as verified closure.
- [ ] **AC-15 - Feature contract:** The 33-observation lookback uses unchanged
      Java formulas and reports actual availability for all four features.
- [ ] **AC-16 - Outcome contract:** H+4, H+12 and H+24 are independently
      checked from actual future H1 observations and report unavailable reasons.
- [ ] **AC-17 - Joint eligibility:** A row is jointly eligible only when all four
      features and all three outcomes pass their admissibility and continuity
      checks.
- [ ] **AC-18 - Diagnostic distinction:** Structural 24-H1 counts are never
      published as joint feature/outcome eligible counts.
- [ ] **AC-19 - Quality stratification:** Outputs report counts and diagnostics
      separately for all four quality states and both instruments.
- [ ] **AC-20 - Policy sensitivity:** Outputs preserve comparable 60/60, 59/60
      and 55/60 diagnostics without selecting a threshold from output size.
- [ ] **AC-21 - Fingerprints:** Source, policy, continuity, feature, outcome,
      configuration, row and bundle fingerprints are deterministic.
- [ ] **AC-22 - Research isolation:** No exploratory output becomes canonical
      `FeatureSnapshot`, production Market Data evidence or operational input.
- [ ] **AC-23 - Scientific gates:** Missing partition, purge, embargo or
      effective-sample approval is explicit and fail-closed; no default values
      are invented.
- [ ] **AC-24 - Reproducibility:** Re-running against the same source and policy
      produces byte-equivalent decisions, counts and fingerprints.

## Testing Expectations

Tests must include:

- 54, 55, 59 and 60 unique valid M1 observations;
- missing first and missing last minute;
- interior missing positions and consecutive-run calculations;
- exact duplicate and conflicting duplicate timestamps;
- malformed and invalid OHLCV records;
- UTC hour boundaries and month boundaries;
- chronology anomalies and unknown provenance;
- empty H1 buckets and continuity breaks;
- repeated evaluation and policy-version determinism;
- preservation of strict quality and continuity classifications;
- feature availability with full and partial H1 dependencies;
- independent H+4, H+12 and H+24 outcome availability;
- joint eligibility rejection when any dependency or horizon is unavailable;
- instrument separation and quality-stratum counts;
- source, row and bundle fingerprint stability;
- research isolation from canonical `FeatureSnapshot` evidence;
- regression coverage for existing HistData preparation and accepted policies.

## Validation

Validation is offline and bounded to the affected Market Intelligence contracts:

- targeted Market Intelligence Maven tests;
- relevant Dukascopy pipeline and research preparation tests;
- focused integration tests at existing Market Intelligence boundaries;
- deterministic repeated-evaluation and fingerprint checks;
- `git diff --check`;
- final inspection confirming no application code outside Story scope changed.

No provider download, MCP execution, live-provider mutation, dataset repair,
model training or KMeans execution is permitted.

## Definition Of Done

- [ ] Repository Analysis completed and human-approved.
- [ ] Implementation Plan completed and human-approved when required.
- [ ] Dukascopy evaluator and research preparation are implemented within the
      approved Market Intelligence boundary.
- [ ] All acceptance criteria pass.
- [ ] Strict Market Data classifications and accepted HistData behavior remain
      unchanged.
- [ ] Research quality and continuity provenance is complete and reproducible.
- [ ] Feature, per-horizon and joint eligibility counts are recalculated from
      implementation output.
- [ ] Missing partition, purge, embargo and effective-sample decisions remain
      explicit blockers where not approved.
- [ ] Relevant tests and quality validation pass.
- [ ] Documentation reconciliation is completed.
- [ ] Implementation Report records exact changes, validation and limitations.
- [ ] Code Review is completed.
- [ ] Human commit is created separately.

## Constraints

- Preserve ADR-092's Java-controlled research and MCP boundaries.
- Preserve ADR-093's feature, outcome, leakage and non-operational semantics.
- Preserve ADR-094's strict continuity and verified-closure authority.
- Do not reuse ADR-095's HistData UTC-05 session convention.
- Implement only ADR-096's `CONTIGUOUS_ONLY` Dukascopy policy.
- Keep partial H1 decisions derived, explicit, deterministic and fail closed.
- Do not modify pre-existing user changes or ignored market-data artifacts.
- Do not commit, push, merge or create a pull request automatically.

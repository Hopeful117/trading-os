# ADR-093 - Proposed ML-01 Unsupervised Market-Regime Discovery Protocol

## Status

Accepted - human architecture and scientific approval recorded

## Date

2026-11-11

## Decision Type

Offline unsupervised research methodology, feature contract and readiness
governance

## Related ADRs

- ADR-020 - Market Intelligence Architecture
- ADR-022 - Market Intelligence Artifact Freshness and Reuse
- ADR-023 - Capability Execution Model
- ADR-089 - Native Trading Research Agent and MCP Responsibility Boundary

## Context

ML-01 is an unsupervised market-regime discovery experiment. Its approved
objective is to discover regimes from quantitative market-state features and
investigate their association with future market behavior. It is not a
supervised return-prediction, directional-classification or trading-signal
experiment.

Trading OS has a Java-owned deterministic feature boundary and a validated
Python offline research package. `FeatureProjectionV2` defines and computes the
four required volatility/state features. The HistData offline pipeline already
requires these features to be present in strict observed snapshots and records
a versioned dataset manifest. Existing outcome capabilities provide deterministic
market outcomes, but the future-observation outcomes required here must be
inspected for exact semantic compatibility before implementation. Missing
definitions must be identified, not silently substituted or implemented under
this documentation-only change.

## Approved Experimental Scope

ML-01 SHALL be:

- offline research only;
- based initially on HistData for September 2026;
- evaluated on EUR/USD and GBP/USD;
- based on H1 candles;
- one shared KMeans model across both instruments;
- evaluated for candidate cluster counts 2, 3, 4, 5 and 6;
- evaluated globally and separately per instrument.

The Research Agent must participate in the real execution path through the
existing MCP -> Java Market Intelligence research boundary. It must not be a
future placeholder. This participation does not give it authority to bypass
Java-owned deterministic computation, dataset validation or research lifecycle
controls.

## Decision Proposed

ML-01 SHALL consume an immutable validated dataset bundle and produce a complete
experiment manifest, quality report, clustering diagnostics, prospective
outcome evaluation and explicit conclusions. The KMeans model SHALL be fit only
on quantitative state features. Future outcomes SHALL be excluded from all
clustering features and all fitting decisions.

The experiment must preserve the distinction between these dimensions:

- `PIPELINE_VALIDATED`: dataset, integrity, partitions and execution contract
  passed validation;
- `REGIMES_IDENTIFIED`: the shared KMeans run produced characterized clusters
  meeting the approved quality and stability criteria;
- `FORWARD_ASSOCIATION_OBSERVED`: future outcome distributions show an
  approved exploratory association with regimes under untouched evaluation.

These dimensions are not mutually exclusive lifecycle states. Overall results
may additionally be `INCONCLUSIVE`, `INSUFFICIENT_DATA` or
`BLOCKED_BY_DATA_INTEGRITY`.

No ML-01 result is a trading signal, profitability claim, calibrated
probability, strategy promotion or execution authorization. A positive forward
association remains exploratory and does not demonstrate a trading edge.

## Dataset and Feature Contract

The clustering input SHALL contain only these `FeatureProjectionV2` features:

- `ohlc.normalized_atr`;
- `ohlc.realized_volatility`;
- `ohlc.volatility_compression_ratio`;
- `ohlc.directional_efficiency`.

The following MUST NOT be used as clustering features:

- raw ATR;
- instrument identity;
- raw price;
- any future outcome;
- shifted current-state features used as a proxy for an independently computed
  future observation.

The experiment SHALL require `feature-projection-v2`, strict observed evidence,
`PRESENT` status for all four features, immutable feature snapshot fingerprints,
an explicit canonical instrument identity, and dataset/manifest fingerprints.
Feature calculation remains Java-owned. Python may validate, transform and fit
the approved offline model but may not recompute canonical features from raw
provider data or silently substitute another projection version.

Instrument identity is retained for evaluation and distribution diagnostics, not
as a model feature. The shared model therefore learns from the same feature
space across EUR/USD and GBP/USD without an instrument label supplied to
KMeans.

## Future Horizons and Outcomes

For every eligible H1 evaluation candle, the protocol SHALL evaluate future
observations at 4, 12 and 24 valid H1 candles after the evaluation candle. These
are candle-count horizons, not automatic aliases for elapsed-time durations such
as `PT4H`, `PT12H` or `PT24H`.

Future outcomes SHALL be independently computed from actual future OHLC
observations:

1. Future realized volatility.
2. Future directional efficiency.
3. Absolute future net return.

These outcomes are prospective evaluation values only. They MUST NOT be used in
KMeans fitting, feature scaling, cluster-count selection, initialization
selection or any other training/validation decision.

Existing Java outcome capabilities and definitions must be inspected for exact
compatibility with these three outcomes. If a definition, persistence contract
or export representation is missing, ML-01 must report that gap and stop at the
appropriate increment rather than substituting shifted current-state features.

## Continuity, Gaps and Market Closures

The dataset builder SHALL distinguish verified market closures from missing
historical data. A verified closure may be excluded according to an approved
session/calendar policy; an unexplained missing candle is a data-integrity gap.

The protocol SHALL define, before execution:

- what constitutes a verified EUR/USD or GBP/USD market closure;
- whether closure intervals are excluded from valid-candle counting;
- the maximum tolerated unexplained gap;
- whether a future window crossing a closure is eligible;
- whether a future window crossing unexplained missing data is always invalid.

The default safety rule is fail closed: do not silently bridge gaps or treat
elapsed time as a valid-candle count. Every exclusion, closure and gap must be
counted and fingerprinted in the quality report. Numerical defaults remain
subject to scientific approval.

ADR-095 is a narrowly scoped companion decision for a separate, research-only
exploratory population. ADR-093 remains the baseline ML-01 scientific protocol:
its feature definitions, outcome definitions, observation-count horizons,
chronological leakage safeguards and non-operational objective are unchanged.
The strict and exploratory datasets are not interchangeable. ADR-095 may add
derived exploratory admissibility and session-assumption metadata, but it must
not rewrite strict Market Data states or continuity classifications. ADR-094
remains authoritative for strict historical continuity and verified closure
evidence.

## Chronological Partitions and Purging

Both instruments SHALL use common chronological boundaries. The implementation
must provide train, validation and final untouched test partitions. Scaling and
KMeans fitting occur on training data only. Cluster-count selection and any
stability-based parameter selection may use training and approved validation
data, but never final test outcomes or test features in a way that changes the
model.

The protocol SHALL preserve:

- train-only preprocessing and fitting;
- validation-based model selection;
- final untouched test evaluation;
- purging of rows whose future windows cross a protected partition boundary;
- an embargo between fitting and evaluation partitions;
- exclusion of invalid or incomplete future windows;
- explicit effective sample counts after every exclusion.

Ordinary overlap between valid future windows within the same partition is
permitted and must be reported as statistical dependence. Overlap alone is not
an exclusion reason. Future-window overlap that crosses a protected partition
boundary is leakage and must be purged; it must never be silently retained.

The exact partition dates, minimum effective sample sizes, purge rule and
embargo duration are not approved by this ADR. They must be explicit in the
experiment definition fingerprint once approved.

## KMeans Model and Diagnostics

ML-01 SHALL use one shared KMeans model for each candidate cluster count from 2
through 6. Scaling is fitted on training data only. KMeans is fitted on training
features only. Random seeds, dependency versions, preprocessing configuration,
initialization count and convergence parameters must be recorded.

The experiment SHALL characterize:

- cluster sizes globally and per instrument;
- cluster centroids/profiles in the original feature units;
- instrument distributions within clusters;
- cluster separation and interpretability;
- stability across approved initializations;
- temporal stability of cluster assignments and profiles;
- global and per-instrument diagnostics.

Cluster selection MUST not use future outcome performance on the final test set.
The approved cluster-count and stability criteria remain a scientific decision.
A clustering metric alone does not demonstrate forward association or predictive
value.

## Baselines

ML-01 SHALL compare prospective cluster-conditioned outcomes with:

1. Instrument-specific unconditional future behavior computed from the relevant
   training population.
2. A deterministic market-regime classification using training-defined
   volatility and directional-efficiency thresholds.

The exact deterministic threshold definitions, fitting population and tie/
missingness behavior remain subject to scientific approval. Baselines must not
use final test outcomes to define their thresholds.

## Evaluation Metrics

The protocol SHALL report, when defined:

- effective and excluded sample counts;
- cluster size and balance diagnostics;
- cluster separation and interpretability diagnostics;
- initialization and temporal stability diagnostics;
- cross-instrument cluster distribution comparisons;
- future realized-volatility distributions by cluster;
- future directional-efficiency distributions by cluster;
- absolute future net-return distributions by cluster;
- effect sizes against each approved baseline;
- temporal stability of cluster profiles and forward associations;
- global and per-instrument comparisons.

Metrics must include uncertainty or unavailable reasons where applicable and
must not hide undefined results behind zero values. No single clustering metric
or favorable test-period association may be reported as a trading edge.

## Readiness and Insufficient Data

The experiment SHALL fail closed before fitting when required data conditions are
unmet. It must return `INSUFFICIENT_DATA` when September 2026 does not support
scientifically meaningful common chronological partitions or effective samples
after feature checks, continuity checks, future-window validation, purging and
embargo.

The quality report must distinguish:

- insufficient rows by instrument;
- insufficient common chronological coverage;
- insufficient post-purge or post-embargo samples;
- verified closures versus unexplained gaps;
- missing required features;
- missing or invalid future OHLC observations;
- missing future outcomes;
- insufficient cluster support;
- integrity or fingerprint mismatch.

No synthetic fixture, fallback feature version, nearest timestamp, elapsed-time
substitution or alternate instrument may satisfy a readiness gate.

## Reproducibility and Provenance

Every result SHALL record:

- experiment definition version;
- dataset and manifest fingerprints;
- population policy, feature projection and outcome-definition versions;
- canonical instrument IDs and provider/product identities where available;
- exact four-feature requirement and missingness policy;
- H1 continuity, closure and gap policy;
- valid-candle horizon definitions;
- chronological partition, purge and embargo parameters;
- scaler and KMeans configuration, seeds and candidate cluster counts;
- baseline definitions and thresholds;
- runtime and dependency versions;
- effective samples, exclusions and all blocking reasons;
- generated-at timestamp and conclusion dimensions.

The experiment output is an immutable research artifact. Re-running with changed
inputs or protocol parameters produces a new fingerprint and does not overwrite
the prior result as if it were the same experiment.

## Incremental Delivery

Implementation remains incremental and requires human approval between
increments:

### ML-01.1

Produce the experimental dataset, independently computed future outcomes,
chronological partitions, continuity/gap quality report, fingerprints and
functional Research Agent delegation through MCP. Do not fit KMeans unless the
approved increment explicitly includes a smoke validation that cannot be
reported as scientific evidence.

### ML-01.2

Run shared KMeans for candidate cluster counts 2 through 6, the approved
deterministic baseline and clustering diagnostics, using only the approved
training/validation path.

### ML-01.3

Evaluate future outcomes on the untouched test partition, compare against
baselines and produce the scientific interpretation with separate conclusion
dimensions.

## Deferred Scientific Parameters Requiring Approval

This ADR deliberately does not invent numerical values for:

- chronological train, validation and test boundaries;
- minimum effective sample sizes globally and per instrument;
- purge and embargo duration;
- exact verified session-closure and continuity policy;
- deterministic baseline threshold definitions;
- cluster-count selection and stability criteria;
- forward-association assessment and effect-size criteria;
- temporal-stability acceptance criteria;
- missingness and tie handling for future outcomes.

The proposed default process is to calibrate these parameters only after
ML-01.1 produces a real H1 data-quality and population report. The rationale is
to avoid selecting thresholds that the September 2026 population cannot support.
Scientific approval is required before numerical defaults become part of the
experiment definition.

## Consequences

Positive:

- ML-01 correctly separates unsupervised regime discovery from prospective
  evaluation.
- Future information cannot leak into KMeans fitting or model selection.
- Common instrument partitions and explicit gap handling improve comparability.
- Positive associations remain exploratory rather than being mislabeled as
  predictive performance or trading edge.
- The Research Agent is exercised through the real existing MCP/Java path.

Costs and risks:

- Three valid-candle horizons require independently verified future OHLC data.
- Purging, embargo and continuity rules may make September 2026 insufficient.
- Shared clustering requires careful global and per-instrument diagnostics.
- Scientific parameter approval is required before implementation can be
  considered complete.

## Out of Scope

- supervised return prediction or directional classification;
- live inference, online learning or broker execution;
- strategy promotion, risk decisions or trading authorization;
- hyperparameter optimization beyond the approved KMeans candidate counts;
- causal claims or profitability claims;
- replacing Java feature or outcome authority;
- changing accepted ADR-020, ADR-022, ADR-023 or ADR-089 decisions.

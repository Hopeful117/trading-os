# ADR-095 - ML-01 Exploratory Research Admissibility Protocol

| | |
|---|---|
| **Status** | **Accepted** |
| **Date** | 2026-11-11 |
| **Decision Type** | ML-01 offline exploratory research admissibility and provenance |
| **Related ADRs** | ADR-057, ADR-058, ADR-092, ADR-093, ADR-094 |
| **Related Story** | Story 0225 - Historical Continuity and ML-01 Verified Closure Crossing |

## 1. Context

ADR-094 establishes the authoritative historical continuity policy for Trading
OS. It distinguishes valid observations, verified closures, unexplained gaps and
invalid observations. It requires authoritative evidence before a transition can
be classified as `VERIFIED_CLOSURE` and fails closed for unexplained or unknown
historical gaps.

ADR-093 defines ML-01 as an offline, deterministic, chronological research
experiment over H1 observations for EUR/USD and GBP/USD. Its four required
features are:

- `ohlc.normalized_atr`;
- `ohlc.realized_volatility`;
- `ohlc.volatility_compression_ratio`;
- `ohlc.directional_efficiency`.

The current volatility calculation requires 33 contiguous H1 observations. The
September 2026 HistData files contain no strict 33-candle segment: the longest
complete-H1 segments are 23 candles for EUR/USD and 24 candles for GBP/USD.
Strict ML-01 therefore produces no eligible observations.

The existing audits also found incomplete M1 buckets, conflicting duplicates,
chronological source inversions and recurring weekend-associated gaps. A masking
experiment found that interior missing minutes often leave a reconstructed OHLC
unchanged, while missing boundary minutes can materially change the open or
close. That experiment did not establish feature-level accuracy and did not
validate a partial-H1 policy.

The project needs a way to conduct explicitly exploratory offline research with
the existing free HistData source without weakening trusted historical
continuity, creating closure evidence, or presenting exploratory results as
operationally valid.

## 2. Problem

Using the strict continuity policy is correct but leaves September 2026 unable to
reach ML-01.2. Relaxing Market Data completeness or treating recurring weekend
patterns as verified closures would compromise the evidence boundary and create
unreviewed scientific assumptions.

The required separation is therefore:

```text
Market Data strict historical evidence
        |
        +--> Market Intelligence exploratory admissibility
                    |
                    +--> Research-only ML-01 dataset
```

The exploratory path must preserve the original strict classifications and add
research decisions as derived, versioned metadata.

## 3. Decision

Trading OS introduces a separate, versioned Market Intelligence research
admissibility profile for offline exploratory ML-01 analysis.

The accepted V1 profile is:

```text
Profile: ML01_HISTDATA_EXPLORATORY_V1

Minimum unique valid M1 observations: 55/60
First M1 minute required: YES
Last M1 minute required: YES

Conflicting duplicate timestamps: REJECT
Invalid OHLC observations: REJECT
Malformed source observations affecting the bucket: REJECT
Unresolved chronology anomalies affecting the bucket: REJECT

Interpolation: FORBIDDEN
Forward fill: FORBIDDEN
Backfill: FORBIDDEN
Synthetic candles: FORBIDDEN

Maximum consecutive missing-minute run: OBSERVED AND RECORDED
Additional rejection threshold in V1: NONE
```

This profile is accepted for exploratory research implementation only. It has
not been scientifically validated and does not authorize ML-01.2, operational
use or trading decisions.

The parameters authorize only a bounded exploratory dataset experiment. They are
not evidence that partial candles produce accurate ML-01 features. The number of
eligible observations must be recalculated after all profile rules, boundary
checks, conflict checks, chronology checks and continuity-stratum rules are
applied. Existing 55/60 counterfactual counts must not be reused as final
population counts.

The profile must distinguish:

```text
FULL_OBSERVED
PARTIAL_EXPLORATORY
REJECTED
UNKNOWN_QUALITY
```

`PARTIAL_EXPLORATORY` is a derived research decision. It must never change the
original Market Data H1 state or make a partial candle a trusted complete
observation.

## 4. Responsibility Boundaries

### 4.1 Market Data

Market Data remains authoritative for production historical evidence and
provider-backed Market Data records. For the offline HistData source used by
this ADR, Market Intelligence owns the source-local parser and aggregation
contract. Its derived facts are explicitly prefixed `MI_LOCAL_HISTDATA_` in
research provenance and are not authoritative Market Data classifications.

Market Data remains authoritative for:

- raw M1 observations;
- source parsing and normalization;
- source timezone interpretation;
- H1 aggregation;
- OHLC validity;
- duplicate and conflict detection;
- chronological quality findings;
- strict H1 completeness state;
- strict continuity classifications;
- authoritative closure evidence;
- source provenance and checksums.

Market Data must not consume this ADR to weaken its trusted historical model.

### 4.2 Market Intelligence

Market Intelligence owns the ML-01 consumer decision about whether a source-local
HistData observation may be included in an exploratory dataset. It may derive
`PARTIAL_EXPLORATORY` and research-only continuity metadata, but it must not
rewrite or publish the local classifications as production Market Data facts.

Exploratory feature values must not be persisted or exposed as canonical
production `FeatureSnapshot` values. They belong to an explicitly research-only
dataset contract and must carry the admissibility and quality provenance that
produced them.

### 4.3 Research and MCP

The Research Agent and MCP adapter remain bounded consumers of Java-controlled
research contracts under ADR-092. They must not classify closures, choose
admissibility parameters, access raw files directly, or publish an export as
scientifically valid merely because construction completed.

The research output must be marked non-operational. It must not reach risk,
execution, live trading or production market-data decisions.

## 5. H1 Research Completeness

### 5.1 Why 55/60 is considered

The existing September population contains many buckets close to complete and
the missing-M1 sensitivity experiment found that some interior omissions do not
change the reconstructed OHLC. A 55/60 candidate can therefore support a
bounded feasibility investigation without claiming that a partial candle is
equivalent to a complete candle.

The experiment did not validate 55/60 as scientifically safe. The threshold is a
population-selection parameter accepted for this bounded exploratory protocol;
it must not be presented as scientific validation.

### 5.2 Boundary requirements

The first and last M1 minutes are required because overall coverage is not enough
to protect open and close semantics:

- missing the first minute can change the H1 open;
- missing the last minute can change the H1 close;
- close-to-close returns use the H1 close;
- realized volatility and directional efficiency depend on close transitions;
- true range can retain a reopening jump through the previous close.

Interior missingness can hide an H1 high or low, but boundary missingness can
also alter the endpoints used by several features. The initial profile therefore
rejects a bucket missing either boundary minute even when it has at least 55
unique valid minutes. Boundary-complete partial candles can still underestimate
the high or low because interior observations may be absent.

### 5.3 Missingness metadata

Every candidate bucket must record:

- unique valid M1 count;
- missing minute positions;
- missing first-minute flag;
- missing last-minute flag;
- missing interior count;
- maximum consecutive missing-minute run;
- duplicate and conflict indicators;
- invalid and malformed-row indicators;
- source chronology warning;
- original strict H1 state.

The maximum consecutive missing run is observed and reported in V1, but no
additional rejection threshold is defined. The available experiment does not
justify selecting such a threshold without inventing an error tolerance.

### 5.4 Quality states

`FULL_OBSERVED` means 60 unique valid M1 minutes and no rejected quality event.

`PARTIAL_EXPLORATORY` means the accepted minimum and boundary rules pass, no
conflict or invalid observation affects the bucket, and all missingness metadata
is available.

`REJECTED` means a known integrity or admissibility rule fails.

`UNKNOWN_QUALITY` means the available evidence is insufficient to make a
deterministic decision. Unknown quality is fail-closed and cannot enter the
exploratory population.

No state permits interpolation, forward-fill, backfill or synthetic candles.

## 6. Research Continuity Strata

The exploratory dataset must contain two separately identified strata.

### 6.1 `CONTIGUOUS_ONLY`

- Accepts exact adjacent valid H1 observations.
- May contain `FULL_OBSERVED` and approved `PARTIAL_EXPLORATORY` candles.
- Does not use session assumptions.
- Does not cross unexplained gaps.
- Preserves the original strict transition classifications.

This is not equivalent to a fully observed 60/60 population. Partial candles
remain explicitly partial.

### 6.2 `ASSUMED_SESSION_TRANSITION`

- May cross only a separately approved, bounded HistData session assumption.
- Retains the original `UNEXPLAINED_GAP` classification.
- Records the research-only assumption and its version.
- Preserves actual elapsed time and reopening price jumps.
- Remains distinguishable from `CONTIGUOUS_ONLY`.

The two strata must not be silently pooled. All feature, outcome, quality,
partition and clustering diagnostics must report them separately. Pooling would
require a new scientific decision supported by predeclared stability evidence.

For V1, the combined feature-and-outcome window may contain at most one assumed
session transition. A window with more than one such transition is rejected
rather than treated as an ordinary extension of the same assumption. This is a
fixed, accepted V1 parameter.

## 7. Candidate HistData Session Convention

The September observations support investigating a recurring weekend-associated
pattern, but they do not prove a universal Forex session calendar or an
authoritative HistData closure schedule.

The candidate research assumption is therefore:

```text
Assumption profile:
ML01_HISTDATA_WEEKEND_SESSION_ASSUMPTION_V1

Provider: HISTDATA
Instrument scope: EUR/USD, GBP/USD only
Source timezone: fixed UTC-05:00, no DST
Nominal closure start: Friday 17:00 source-local
Nominal closure end: Sunday 17:00 source-local
Latest permitted reopening: Sunday 18:00 source-local
Research purpose: RETROSPECTIVE_EXPLORATORY_ONLY
```

These are accepted research-policy parameters, not verified HistData
market-closure facts. They are bounded by the existing fixed-offset parser and
the observed recurring weekend pattern; they do not assert a universal Forex
calendar or HistData session guarantee. Applying this convention must never
produce `VERIFIED_CLOSURE`; the strict transition remains `UNEXPLAINED_GAP`
unless separate authoritative closure evidence exists under ADR-094.

For a candidate transition, the implementation must identify and record:

1. `previous`: the last admissible H1 observation before the candidate gap;
2. `next`: the first admissible H1 observation after the candidate gap;
3. `nominalClosureStart`: Friday 17:00 source-local;
4. `nominalClosureEnd`: Sunday 17:00 source-local;
5. `latestPermittedReopening`: Sunday 18:00 source-local;
6. `actualGap`: `[previous.closeTime, next.openTime)`;
7. every missing, partial, invalid or conflicting H1 bucket between those
   observations;
8. the actual elapsed duration between `previous.closeTime` and
   `next.openTime`.

The transition matches the convention only when all of these conditions hold:

1. `previous.closeTime` is no later than Friday 17:00;
2. `actualGap.start` is no earlier than Friday 17:00;
3. `next.openTime` is no earlier than Sunday 17:00;
4. `next.openTime` is no later than Sunday 18:00;
5. no observed H1 activity exists inside the nominal closure interval
   `[Friday 17:00, Sunday 17:00)`;
6. no unexplained intraday interval exists outside that nominal interval and
   inside the permitted reopening treatment;
7. `previous` and `next` independently pass the exploratory H1 quality rules;
8. no conflicting, invalid or unknown-quality observation is hidden inside the
   transition; and
9. neither endpoint is a dataset boundary.

Conditions 1 and 2 together require `previous.closeTime` to equal Friday 17:00
exactly. Earlier Friday closes are not supported in V1. The maximum possible
actual gap is therefore 49 elapsed hours, from Friday 17:00 to Sunday 18:00,
using the half-open interval semantics `[openTime, closeTime)`. This maximum is a
research bound, not evidence of a genuine market closure.

The nominal interval must not be substituted for the actual gap duration. The
actual previous and next timestamps, elapsed duration and every intervening
bucket state must be retained.

A boundary candle must independently qualify as `FULL_OBSERVED` or
`PARTIAL_EXPLORATORY`. A candle with fewer than 55 unique valid M1 observations,
a missing first or last M1 minute, a conflict or invalid OHLC is rejected. An
inadmissible candle must not be skipped to manufacture a session transition. A
qualifying partial `next` observation remains partial in the research
provenance.

If any H1 bucket contains observed activity inside the nominal closure interval,
the candidate transition is rejected; that activity must not be silently
discarded to create a longer gap. Missing H1 buckets outside the approved
nominal closure and reopening allowance remain unexplained gaps. No
interpolation, filling or synthetic reopening candle is permitted. The actual
reopening OHLC and price jump are preserved.

V1 does not support earlier Friday closes, provider-specific early closes,
holidays or exceptional schedules. Such cases are rejected, not reclassified.

The minimum safe candidate rules are:

1. The provider and instrument must match the profile scope.
2. The previous and next observations must independently pass exploratory H1
   admissibility.
3. The exact timestamp bounds above must match.
4. No unexplained intraday interval may occur inside the transition.
5. No conflict, invalid or unknown-quality observation may be hidden inside it.
6. The transition must not begin or end at a dataset boundary.
7. The original transition remains `UNEXPLAINED_GAP` in strict provenance.
8. The research record must identify the assumption version, exact bounds and
   actual elapsed duration.
9. The transition must be marked retrospective exploratory and must not support
   prospective readiness or operational use.

An arbitrary Friday-to-Monday gap is not admissible. A gap that exceeds the
approved bounds, includes an intraday interruption, contains invalid data or
cannot be classified deterministically remains rejected.

## 8. Observation-Based Features and Outcomes

The exploratory protocol retains ADR-093's observation-count semantics:

```text
Feature lookback: 33 valid H1 observations
Future outcome horizons: H+4, H+12 and H+24 valid H1 observations
```

These are not aliases for elapsed wall-clock durations.

A valid exploratory observation is an H1 candle that passes the approved
`ML01_HISTDATA_EXPLORATORY_V1` profile. Every transition between observations
must also pass the selected research stratum's continuity rules.

For `CONTIGUOUS_ONLY`, every transition must be exact H1 adjacency. For
`ASSUMED_SESSION_TRANSITION`, the combined feature-and-outcome window may use at
most one transition under the approved research assumption in V1; all other
transitions must be exact adjacency. Any inadmissible transition rejects the
complete combined window.

The combined window is the ordered observation span beginning at the first
feature-lookback observation and ending at the last selected future-outcome
observation. The implementation must count assumed transitions across that
entire span, not separately within the feature lookback and outcome horizon. A
row may therefore be eligible for one outcome horizon and rejected for another
if the selected horizon causes the combined count to exceed one. The
`CONTIGUOUS_ONLY` stratum has a combined count of zero by definition. The
continuity stratum is assigned independently for each selected outcome horizon
from that horizon's combined-window result.

Each requested outcome horizon is evaluated independently. For every candidate
row and each of H+4, H+12 and H+24, the evaluator records:

- assumed transitions in the 33-observation feature lookback;
- assumed transitions in the selected future outcome horizon;
- assumed transitions in the combined feature-and-outcome window.

Zero assumed transitions is eligible only for `CONTIGUOUS_ONLY`. Exactly one
approved assumed transition in the combined window is eligible for
`ASSUMED_SESSION_TRANSITION`. More than one, any unexplained intraday gap, any
invalid or conflicting observation, or any unknown-quality transition rejects
that combined window. A transition in the feature lookback and another in the
future horizon therefore cannot pass merely because each sub-window contains
only one transition.

The dataset must record:

- feature lookback start and end timestamps;
- actual elapsed lookback duration;
- valid observation count;
- partial-candle count;
- feature-lookback assumed-session-transition count;
- future-outcome assumed-session-transition count;
- combined-window assumed-session-transition count;
- strict transition classifications;
- research-only exceptions;
- future horizon start and end timestamps;
- actual elapsed future duration;
- future valid observation count;
- future partial-candle count;
- future transition classifications.

The existing formulas remain unchanged:

- ATR(14) uses true range;
- normalized ATR divides ATR by the latest close;
- realized volatility uses close-to-close returns over the latest volatility
  lookback;
- compression ratio compares current ATR with the 20-value ATR baseline;
- directional efficiency is absolute net movement divided by total absolute
  path movement.

Missing M1 observations are not repaired. Reopening price jumps remain in true
range and close-to-close returns. No elapsed-time normalization or compression
of the reopening transition is permitted.

This clarifies ADR-093 by adding a research-only admissibility profile while
preserving its feature definitions, observation-count horizons, chronological
evaluation and non-operational nature. It does not prove that the partial-H1
population answers the same scientific question as the strict population; that
must be tested and reported as a limitation.

## 9. Leakage and Statistical Controls

The exploratory policy must preserve:

- common chronological train, validation and test boundaries across instruments;
- train-only scaling;
- KMeans fitting only on training features;
- exclusion of future outcomes from feature construction and admissibility
  selection;
- fixed admissibility and session profiles before held-out evaluation;
- explicit purge of future windows crossing protected boundaries;
- explicit embargo;
- no held-out test inspection during policy selection;
- no threshold optimization against test outcomes.

Results must be reported separately for:

- `FULL_OBSERVED` candles;
- `PARTIAL_EXPLORATORY` candles;
- `CONTIGUOUS_ONLY` windows;
- `ASSUMED_SESSION_TRANSITION` windows;
- EUR/USD;
- GBP/USD.

Overlapping feature and future-outcome windows are dependent observations. Row
counts must not be presented as independent sample counts. Reports must include
unique candles, unique sessions or trading days, non-overlapping window counts
and effective partition counts.

## 10. Research Provenance Contract

The minimum ML-01 research metadata contract is:

```text
source identity and checksum
canonical instrument and market ID
source timezone
research policy version
session-assumption version
H1 open and close timestamps
unique observed M1 count
missing-minute positions
first-minute presence
last-minute presence
maximum consecutive missing-minute run
original strict H1 quality state
original continuity classifications
exploratory admissibility decision
research continuity stratum
crossed gap count and duration
combined feature-and-outcome assumed-transition count
feature observation count
feature lookback start/end and elapsed duration
future observation count
future horizon start/end and elapsed duration
research-only status
```

The dataset fingerprint must include the source checksum, population scope,
feature projection version, outcome-definition version, research policy,
session-assumption policy, quality-stratum rules, admissibility parameters and
chronological partition parameters. Per-row provenance must additionally retain
duplicate/conflict indicators and the original strict H1 quality state.

This contract is intentionally limited to ML-01. It does not establish a
generic quality-policy framework or change the provider-neutral Market Data
contracts.

## 11. Scientific Limitations

This ADR explicitly acknowledges:

1. HistData M1 is not tick-level ground truth.
2. Missingness may be nonrandom.
3. Partial H1 candles can underestimate the observed high-low range.
4. Missing boundary minutes can materially affect open and close.
5. The masking experiment did not establish feature-level accuracy.
6. September 2026 contains no strict 33-H1 reference window.
7. Session assumptions can change feature and outcome distributions.
8. Observation-based horizons are not equivalent to elapsed-time horizons.
9. Dataset eligibility does not establish predictive value.
10. KMeans clusters do not automatically represent economically meaningful
    market regimes.

These are scientific limitations, not evidence that the existing production
continuity implementation is defective.

## 12. Approval and Readiness Gates

Progression between these levels is never automatic.

### Level 1 - ADR approval

Human scientific and architecture approval accepts the research protocol and
explicit assumptions for implementation. It does not authorize ML-01.2.

### Level 2 - Exploratory dataset readiness

The implementation proves that:

- source identity and checksum are recorded;
- every row has a deterministic admissibility decision;
- no conflicts, invalid observations or silent repairs are present;
- partial and full rows are distinguishable;
- session assumptions are explicit and bounded;
- strict classifications remain unchanged;
- feature formulas match the approved Java definitions on contiguous full
  inputs;
- actual elapsed durations are recorded;
- future outcomes use valid observation counts;
- dataset and policy fingerprints are reproducible;
- the final eligible counts are recalculated from the approved profile rather
  than copied from prior counterfactual diagnostics;
- the two continuity strata and all four quality states have explicit counts;
- no window exceeds the approved V1 assumed-transition limit.

Level 2 is not scientific validity and does not authorize model execution. Level
2 never automatically authorizes Level 3.

### Level 3 - ML-01.2 execution authorization

Before KMeans, human review must confirm:

- chronological partitions are frozen;
- purge and embargo behavior is validated;
- train-only scaling is validated;
- effective sample sizes are sufficient globally and per instrument;
- both instruments have adequate representation;
- all quality and continuity strata are reported separately;
- sensitivity and stability checks do not show an unexplained dependence on
  partial or assumed-session rows;
- outcome labels are valid and unavailable outcomes remain excluded;
- the held-out test has not been inspected during policy selection.

The approved admissibility profile, session bounds, completeness rules and
stratum definitions must be fixed before any model evaluation. Minimum sample
sizes, partition boundaries and embargo parameters must also be fixed before
model evaluation, but remain separate human scientific decisions.

Minimum sample sizes, partition boundaries, embargo duration and acceptance
criteria remain `REQUIRES_HUMAN_APPROVAL`. This ADR does not invent numerical
values for them.

### Level 4 - Operational use

Not authorized by this ADR. Exploratory ML-01 outputs must not be used for live
trading, risk decisions, execution, market-state authority or production
automation.

## 13. Alternatives Considered

### Option A - Strict policy unchanged

Keep 60/60 completeness, exact continuity and authoritative closure evidence
only.

This has the strongest integrity and lowest implementation risk, but the current
September population remains blocked before ML-01.2.

### Option B - Partial H1 only

Admit qualified partial H1 candles while retaining strict gap crossing.

This is simpler than the accepted protocol, but it does not explicitly represent
session assumptions and may produce a population biased toward source trading
sessions. It remains a useful `CONTIGUOUS_ONLY` stratum but is insufficient for
the complete research question when session transitions matter.

### Option C - Explicit exploratory research policy

Use qualified partial H1 candles and a separately versioned, bounded session
assumption while preserving strict classifications.

This is the selected proposal because it makes the additional assumptions
explicit, preserves the strict Market Data boundary and allows sensitivity
analysis instead of silently changing continuity semantics. Its higher bias and
complexity require separate strata, provenance and human approval.

## 14. Consequences

### Positive

- ADR-094 remains unchanged and authoritative.
- Existing HistData can be studied without inventing closure evidence.
- Partial-candle assumptions become reproducible and auditable.
- Session-transition effects can be reported separately.
- Research outputs remain clearly separated from operational data.
- ML-01 can progress to a protocol-level readiness decision without claiming
  scientific or trading validity.

### Costs and risks

- Partial H1 values may bias all four features.
- Session assumptions may change the distribution of returns and volatility.
- Exploratory rows are not interchangeable with strict observed rows.
- Additional provenance and quality-stratum diagnostics are required.
- Sample counts may look sufficient while independent session counts remain low.
 - The accepted session convention is research-only; separate scientific
   approval remains required for readiness parameters used by model evaluation.

## 15. V1 Scope Boundary

V1 is deliberately limited to:

- the existing HistData M1 datasets;
- EUR/USD and GBP/USD;
- deterministic M1-to-H1 reconstruction and quality evaluation;
- the two research continuity strata;
- versioned research provenance and fingerprints;
- ML-01.1 readiness validation.

V1 excludes:

- other market-data providers;
- a generic session-calendar service;
- a generic data-quality rule engine;
- new microservices or infrastructure boundaries;
- live market processing;
- production feature changes;
- trading execution or risk decisions;
- model training and KMeans;
- automatic scientific approval.

## 16. Relationship to Existing Governance

### ADR-092

The Research Agent and MCP adapter remain bounded interfaces over Java-controlled
research contracts. Neither may choose this profile, classify closures or grant
scientific readiness.

### ADR-093

This ADR is an accepted companion decision. It refines the ML-01 dataset boundary
for a named exploratory profile but does not change the four features, outcome
definitions, KMeans safeguards or non-operational status.

ADR-093 now explicitly distinguishes the strict baseline population from this
exploratory population. No feature, outcome, leakage or operational definition
was changed.

### ADR-094

ADR-094 remains unchanged. This ADR does not add closure evidence, modify strict
classifications or authorize Market Data to accept partial candles as trusted
complete observations.

### Story 0225

Story 0225 remains unchanged. Its scope concerns verified closure evidence and
strict continuity integration. Its current implementation report correctly
records that the production closure source is unavailable and remains fail
closed.

## 17. Engineering Story Allocation

Story 0226 owns the evaluator and the bounded research-only dataset/export
contract. It does not claim readiness or implement session-transition strata.
The remaining follow-up Stories are:

1. **ML-01 Exploratory Session Continuity**
   - Build the `CONTIGUOUS_ONLY` and `ASSUMED_SESSION_TRANSITION` windows.
   - Preserve partial-H1 metadata, assumptions and fingerprints.
   - Keep outputs outside canonical production evidence and FeatureSnapshots.

2. **ML-01.1 scientific readiness validation**
   - Validate formulas, horizons, partitions, purge, embargo, sample counts and
     quality-stratum stability.
   - Produce an explicit readiness result without running KMeans.

ML-01.2 requires a separate approval-gated Story after ML-01.1 readiness.

## 18. Approval Status And Remaining Decisions

### 18.1 Accepted V1 decisions

- The accepted `ML01_HISTDATA_EXPLORATORY_V1` completeness profile includes
  mandatory first and last M1 minutes.
- The accepted frozen V1 HistData session convention is Friday 17:00 through
  Sunday 17:00 nominal closure, Sunday 18:00 latest permitted reopening,
  fixed UTC-05:00 source time, EUR/USD and GBP/USD scope, and retrospective-only
  use.
- The accepted V1 combined-window limit is at most one assumed session
  transition.

These are no longer unresolved parameter-selection questions. Earlier Friday
closes, later reopenings, holidays, exceptional closures and additional
transitions remain rejected in V1 rather than deferred for implicit handling.

### 18.2 Required before ML-01.2

- Minimum effective sample sizes globally and per instrument.
- Chronological train, validation and test boundaries.
- Purge and embargo configuration.
- Quality-stratum stability criteria.
- Feature-sensitivity acceptance criteria.
- Outcome-label availability and missingness handling.
- Criteria for any pooling across quality or continuity strata.
- Explicit human authorization to execute ML-01.2.

The second group must not block implementation of a deterministic evaluator, but
must block model evaluation until fixed and approved.

## 19. Non-Authority

This accepted ADR does not authorize:

- production code changes;
- Market Data policy changes;
- source-data repair;
- closure-provider integration;
- model training or KMeans;
- live trading or risk decisions;
- operational use of exploratory outputs;
- automatic progression through approval levels.

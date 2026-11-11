# ADR-094 - Market Data Continuity and Verified Closure Evidence Policy

| | |
|---|---|
| **Status** | **Accepted - human architecture and ML-01 continuity approval recorded** |
| **Date** | 2026-11-11 |
| **Decision Type** | Historical market-data continuity, closure evidence and deterministic research policy |
| **Related ADRs** | ADR-020, ADR-021, ADR-022, ADR-076, ADR-077, ADR-079, ADR-088, ADR-089, ADR-092, ADR-093 |

## 1. Context And Motivation

Trading OS uses historical market observations for deterministic analysis,
feature engineering, research, outcome evaluation and future backtesting. A
missing observation has different meanings:

- the market was not expected to trade;
- the provider failed to supply expected data;
- the observation was malformed or duplicated;
- the available evidence is insufficient to decide.

These meanings cannot be represented safely by timestamp arithmetic alone.

ML-01.1 exposed the problem. The September 2026 HistData population contains
conflicting duplicate M1 timestamps, chronological inversions, missing M1
intervals, session-uncertain H1 buckets and no eligible four-feature rows. The
current historical preparation path requires exact timestamp adjacency:

```text
previous.closeTime == next.openTime
```

That rule correctly rejects unexplained gaps, but it also rejects any interval
that might be a legitimate market closure because no authoritative historical
closure evidence is currently available to the pipeline.

This ADR defines a reusable domain contract. It is not an ML-01 workaround and
does not authorize a permissive continuity rule, source repair or a new data
provider.

## 2. Existing Implementation Audit

### 2.1 Market Data historical evidence

Market Data already contains provider-neutral historical OHLC concepts:

- `HistoricalOhlcEvidence` carries canonical market identity, interval,
  timestamps, OHLC values, provider identity, source identity, capture times,
  `knownAt`, origin, normalization version and observation fingerprint.
- `HistoricalOhlcRange` carries observations, expected and observed counts,
  gaps, status, evidence policy, evidence fingerprint and normalization
  versions.
- `HistoricalOhlcGapAssessment` carries an interval, classification and reason.
- `HistoricalOhlcContinuityReport` aggregates classifications and exposes a
  `readyFor(...)` fail-closed predicate.
- `HistoricalOhlcRangeFingerprint` includes observations, gaps, policy,
  counts and normalization versions in a deterministic fingerprint.

These contracts are the smallest suitable foundation for a reusable policy.

### 2.2 Existing continuity validation

`HistoricalOhlcContinuityValidator` evaluates expected provider-timeframe open
times and currently supports:

- `EXPECTED_MARKET_CLOSURE`;
- `MISSING_DATA`;
- `INVALID_CANDLE`;
- `DUPLICATE`;
- `UNCERTAIN`.

It also exposes `cTraderForexWeekendAvailability(...)`. That function is a
provider-specific weekend heuristic based on UTC weekday and hour. It is useful
as an explicit cTrader classification input, but it is not sufficient as a
provider-independent historical closure authority:

- it does not carry closure interval evidence or source provenance;
- it does not model holidays or exceptional closures;
- it cannot prove that a particular provider or instrument was expected to be
  unavailable;
- it must not classify a generic weekday pattern as verified merely from its
  shape.

`HistoricalOhlcRangeCalculator` currently builds a regular elapsed-time grid,
accepts only `CAPTURED` evidence, and marks all absent expected intervals as
gaps. It does not consume closure evidence.

### 2.3 Existing live market state

Trading Core has `MarketState`, `TradingStatus`, `tradable` and
`MarketClosureReason`. This is a live execution/tradability model. Its closure
reason describes current provider/business state and is not historical evidence
that a candle was not expected at an earlier time. It must not be reused for
historical continuity.

### 2.4 ML-01.1 and deterministic features

`HistDataOfflinePipeline` validates source rows and aggregates M1 observations
into regular H1 buckets. `Ml01HistDataPreparation` filters complete H1 candles,
constructs a contiguous historical tail and independently constructs future
outcomes. `VolatilityCalculator` requires:

- ATR period: 14 candles;
- baseline length: 20 ATR values;
- total minimum history: 33 observations;
- exact timestamp adjacency between all eligible historical candles.

`FeatureProjectionV2` maps unavailable volatility measurements to explicit
missingness rather than substituting values.

### 2.5 Existing outcome calculations

`MarketOutcomeCalculator` computes deterministic forward price quantities from a
complete ordered future observation set. Its current v1 contract includes signed
forward return, high/low returns and future range.

The ML-01.1 preparation path additionally computes:

- future realized volatility from squared close-to-close changes;
- future directional efficiency as absolute net movement divided by absolute
  path movement;
- absolute future net return.

Neither implementation normalizes by elapsed wall-clock duration. Both preserve
the observed price transition between adjacent supplied observations.

### 2.6 Architectural conclusion

The repository has continuity and evidence primitives, but lacks one shared
contract that:

- separates valid observations from valid transitions;
- consumes versioned closure evidence;
- lets each deterministic consumer declare whether verified closures may be
  crossed;
- preserves actual elapsed time and reopening price gaps;
- fingerprints the policy and closure evidence used.

No contradiction with ADR-092 or ADR-093 was found. ADR-093 already requires
verified closure policy, fail-closed unexplained gaps and valid-candle horizons.
This ADR supplies the reusable policy boundary that ADR-093 requires; it does
not change ADR-093's scientific scope or approve its deferred parameters.

## 3. Domain Definitions

### 3.1 Valid market candle

A valid market candle is a completed observation that satisfies all applicable
requirements:

- canonical market and provider identity are known;
- interval, open time and close time match the approved timeframe;
- OHLC values are present, positive and internally consistent;
- the observation is not synthetic when the consumer requires observed data;
- duplicate selection is resolved only by an explicit source policy, never by
  silently choosing one conflicting value;
- source, capture, `knownAt` and normalization provenance are present;
- the observation fingerprint is valid;
- the observation is not classified as invalid or quarantined.

Timestamp presence alone does not make a candle valid.

### 3.2 Expected market interval

An expected market interval is a timeframe interval during which the canonical
instrument and venue are expected to produce an observation according to an
authoritative schedule or an explicitly selected historical availability
policy.

Expectedness is a property of the instrument, venue, product and effective
calendar. It is not inferred solely from a weekday, elapsed duration or the
presence of neighbouring data.

### 3.3 Verified market closure

A verified market closure is a bounded interval supported by versioned evidence
that the instrument/venue was not expected to produce observations for that
interval.

Verification requires, at minimum:

- canonical market identity and venue/product scope;
- UTC-normalized closure start and end;
- the effective timezone and source calendar basis;
- closure type: scheduled, holiday, exceptional or other explicitly defined
  category;
- evidence source and source version;
- evidence `knownAt` or equivalent historical availability time;
- evidence fingerprint;
- a deterministic applicability result for the requested interval;
- no contradictory higher-authority evidence.

A closure classification is not verified merely because it resembles the usual
Forex weekend pattern.

### 3.4 Unexplained historical gap

An unexplained historical gap is an absent expected interval for which no
applicable, non-contradictory verified closure evidence exists.

The default consumer result is rejection of any continuity-dependent window
that crosses the gap.

### 3.5 Incomplete or corrupted candle

An incomplete or corrupted candle is an observation that fails structural,
temporal, OHLC, duplicate, provenance or completion validation. It is rejected
or quarantined according to the existing source-quality policy. It is never
converted into a closure.

### 3.6 Market tradability state

Market tradability answers whether an order can be executed in real time under
the current Trading OS and provider conditions.

Historical continuity answers whether historical observations form an acceptable
input sequence for a particular calculation.

These are separate concepts. Live `MarketState`, broker schedules, execution
mode and provider trading status must not be used as retroactive historical
closure evidence without an explicitly versioned historical evidence contract.

## 4. Proposed Architectural Decision

Trading OS SHALL use a provider-independent continuity evaluation contract in
Market Data domain models, consumed by Market Intelligence deterministic
calculators and research builders. Market Data owns the base observation and
closure-evidence assessment. Market Intelligence owns the consumer-specific
decision about whether that assessment satisfies a feature or outcome
requirement.

The smallest coherent conceptual contract consists of:

```text
ContinuityPolicy
  policyVersion
  interval
  validObservationRequirements
  closureCrossingMode
  unexplainedGapMode
  duplicateMode
  evidenceCutoffRule

ContinuityTransition
  previousObservation
  nextObservation
  expectedCadence
  actualElapsedDuration
  classification
  closureInterval, when applicable
  closureEvidenceFingerprint, when applicable
  reason

ContinuityAssessment
  market identity
  interval and bounds
  valid observations
  transitions
  exclusions
  policy fingerprint
  closure-evidence fingerprint
  assessment fingerprint
```

The implementation may reuse and extend the existing continuity report and gap
assessment types. It must not create a generic rules engine or a new microservice
for this policy.

### 4.1 Transition classifications

The shared semantic categories are:

```text
CONTIGUOUS
VERIFIED_CLOSURE
UNEXPLAINED_GAP
INVALID_OBSERVATION
UNKNOWN_CLOSURE_EVIDENCE
```

Existing classifications may remain as wire or persistence-compatible values,
but their mapping to these semantics must be explicit.

### 4.2 Contiguous transition

A transition is `CONTIGUOUS` when both observations are valid and the next
observation begins exactly at the expected timeframe boundary after the
previous observation.

### 4.3 Verified-closure transition

A transition is `VERIFIED_CLOSURE` when:

- both surrounding observations are valid;
- the elapsed interval includes a closure interval supported by verified
  evidence;
- no unexplained expected interval remains in the transition;
- the Market Data base assessment can prove the closure for the scoped market,
  venue and historical knowledge boundary.

The assessment must preserve both observation timestamps, the actual elapsed
duration, closure bounds, closure reason, evidence identity and evidence
fingerprint. It must not fabricate candles or compress timestamps.

Whether a consumer accepts this transition is evaluated separately by Market
Intelligence. A base `VERIFIED_CLOSURE` classification must not be suppressed
because one consumer rejects closure crossing.

### 4.4 Unexplained-gap transition

An absent interval that was expected and lacks verified closure evidence is
`UNEXPLAINED_GAP`. Continuity-dependent consumers reject the affected window by
default.

No interpolation, forward-fill, nearest-timestamp substitution or silent
skipping is permitted.

### 4.5 Unknown closure evidence

If the evidence source is missing, stale, contradictory, outside the requested
historical knowledge boundary or unable to decide, the transition is
`UNKNOWN_CLOSURE_EVIDENCE`.

Unknown is fail-closed and is not equivalent to `VERIFIED_CLOSURE`.

### 4.6 Invalid observation

Malformed, incomplete, inconsistent, duplicate-conflicted or otherwise
quarantined observations produce `INVALID_OBSERVATION`. They cannot be used to
justify a closure transition.

## 5. Valid-Observation Horizons And Consumer Policies

Counting valid observations and evaluating transitions are separate operations.

### 5.1 Valid-candle horizon

A horizon such as H+4, H+12 or H+24 means four, twelve or twenty-four valid
future H1 observations selected under the consumer's population and evidence
policy. It is not an alias for four, twelve or twenty-four elapsed hours.

For each candidate window, the consumer must evaluate every transition between
the reference observation and each future observation.

- `CONTIGUOUS` is accepted.
- `VERIFIED_CLOSURE` is accepted only when the consumer policy explicitly
  permits it.
- `UNEXPLAINED_GAP`, `UNKNOWN_CLOSURE_EVIDENCE` or `INVALID_OBSERVATION` rejects
  the window.

The output must retain the number of valid observations, elapsed duration,
transition classifications and exclusion reason.

### 5.2 Feature lookbacks

Feature calculators declare their own continuity capability. The policy is not
globally identical for all indicators.

The default for a strict observed feature is:

- contiguous observations are accepted;
- verified closures are rejected unless the feature explicitly opts into
  closure crossing;
- unexplained gaps and unknown closure evidence are rejected.

A feature may opt into verified-closure crossing only when its mathematical
definition and scientific protocol have approved semantics for the reopening
transition. The opt-in is part of the feature policy version and fingerprint.

### 5.3 The 33-candle volatility lookback

The current volatility calculation requires 33 observations because it uses ATR
14 and a 20-value ATR baseline. Under the default strict policy, those 33
observations must be connected by accepted transitions.

For ML-01, the volatility feature may use 33 valid observed candles across a
`VERIFIED_CLOSURE`. It must retain the real timestamps and transition evidence;
it must not present those observations as 33 elapsed hours. This permission is
specific to the approved ML-01 consumer policy and does not change the default
for other consumers.

## 6. Price Gaps And Indicator Semantics

The current deterministic formulas have these semantics:

### ATR and normalized ATR

ATR uses true range. For each candle after the first in the selected history,
true range is the maximum of:

- current high minus current low;
- absolute current high minus previous close;
- absolute current low minus previous close.

Normalized ATR divides ATR by the latest close.

If a verified closure is crossed and closure crossing is approved, the reopening
candle's true range must retain its difference from the previous close. The
closure must not turn the reopening price gap into zero movement.

### Realized volatility

The current implementation computes the square root of the mean squared
close-to-close returns over the selected candles. It does not normalize by
elapsed wall-clock time.

Crossing a verified closure therefore retains the reopening close-to-close
return. Whether that return should be included in a particular research feature
population is a consumer/scientific policy decision, not an implementation
shortcut.

### Volatility compression ratio

The current implementation divides the latest ATR by the mean of the selected
ATR baseline. Its interpretation changes if the selected window crosses a
closure, especially when the reopening range contains a price gap. The policy
and feature version must record whether such transitions were allowed.

### Directional efficiency

The current implementation is absolute net close movement divided by total
absolute close-to-close path movement. A verified closure crossing preserves the
reopening close transition in the path and net movement.

### Future realized volatility and directional efficiency

ML-01.1 computes these from actual future H1 OHLC observations. A permitted
verified closure crossing preserves the observed reopening transition. An
unexplained gap or unknown closure evidence invalidates the future window.

### Absolute future net return

ML-01.1 computes the absolute value of the future close-to-close return. The
observed reopening price transition remains part of the return when the future
window is accepted.

### Scientific boundary

This ADR does not redefine any feature or outcome formula. A future change to
elapsed-time normalization, closure-gap treatment, annualization, return
weighting or lookback semantics requires a new feature/outcome definition
version and scientific approval.

## 7. Closure Evidence And Provenance

Market Data SHALL own acquisition, normalization and base assessment of
historical closure evidence when such evidence is available. Market Intelligence
SHALL consume the provider-independent contract, declare the requirements of
each deterministic consumer and remain responsible for the validity of its
derived artifacts.

The minimum conceptual closure-evidence record is:

```text
VerifiedClosureEvidence
  evidenceId
  canonicalMarketId
  canonicalInstrument
  venueOrMarketSource
  providerScope, when applicable
  productType, when applicable
  closureStartUtc
  closureEndUtc
  sourceTimezone
  closureType
  reason
  evidenceSourceId
  evidenceSourceVersion
  effectiveFrom
  effectiveTo
  knownAt
  verificationStatus
  evidenceFingerprint
```

Required closure types distinguish at least:

- scheduled recurring closure;
- published holiday closure;
- exceptional venue or instrument closure;
- broker-specific interruption;
- provider unavailable;
- unknown or inferred availability.

Only the first three may potentially support `VERIFIED_CLOSURE`, and only when
the evidence scope matches the canonical market and venue. Broker-specific
interruptions are not automatically market closures. Provider unavailability is
not a market closure. Inferred availability remains unknown unless separately
verified.

The evidence must be available within the research knowledge boundary. For
prospective historical research, closure evidence with `knownAt` after the
evaluation cutoff must not silently influence the result. Retrospective quality
analysis may use later evidence only when explicitly labelled as retrospective
and excluded from prospective readiness claims.

The policy fingerprint must include:

- continuity policy version;
- interval and canonical market identity;
- closure-evidence source IDs and versions;
- applicable closure evidence fingerprints;
- evidence cutoff rule;
- consumer feature/outcome definition version;
- source observation and gap fingerprints.

Changing closure evidence or policy must produce a new continuity assessment and
new derived artifact fingerprint. Existing artifacts remain immutable.

## 8. Responsibility And Dependency Direction

```text
Market Data
  raw observations + provider-neutral closure evidence
  base continuity assessment
        |
        v
Market Intelligence consumer assessment
  feature/outcome policy + deterministic acceptance decisions
        |
        +--> feature calculators
        +--> outcome calculators
        +--> research dataset builders
        +--> artifact and dataset fingerprints
        v
Research Agent / MCP retrieval
```

Responsibilities:

- **Market Data:** owns primary historical observations, provider-specific
  acquisition, normalization, closure evidence and the provider-independent
  base continuity assessment.
- **Market Intelligence:** declares the continuity requirements of each feature
  or outcome, accepts or rejects the base transitions deterministically, and
  records the result in derived artifacts.
- **Feature calculators:** declare their continuity capability and do not infer
  closure status from timestamps.
- **Outcome calculators:** declare horizon and transition requirements and
  preserve closure/reopening evidence in output provenance.
- **Research builders:** expose counts by transition classification, closure
  evidence and exclusion reason.
- **MCP and Research Agent:** retrieve bounded readiness and research results;
  they do not classify historical closures or override continuity decisions.

The design reuses existing Market Data and Market Intelligence boundaries. It
does not create a Calendar Service, new provider integration or generic policy
engine.

## 9. Fail-Closed Data Integrity Rules

The following rules are mandatory:

- conflicting duplicate observations remain visible and are not silently
  resolved;
- chronological inversions remain source-quality findings;
- invalid or incomplete candles cannot become closures;
- unexplained gaps reject affected windows by default;
- unknown closure evidence rejects affected windows by default;
- end-of-dataset truncation is reported separately from source corruption;
- no synthetic candle is created to bridge a closure or gap;
- no interpolation, forward-fill, nearest timestamp or elapsed-time substitution
  is permitted;
- raw source checksums and source identity must be preserved in future research
  artifacts;
- a successful export does not imply scientific readiness;
- readiness fingerprints include continuity policy and closure evidence.

## 10. Consequences And Trade-offs

### Positive

- One reusable distinction exists between missing data and verified closure.
- Historical continuity remains separate from live tradability.
- Consumers can opt into closure crossing only with explicit semantics.
- Reopening price gaps remain observable in true range and returns.
- Closure evidence becomes auditable and fingerprinted.
- ML-01, backtesting and future deterministic consumers can share the same
  continuity vocabulary without sharing feature-specific rules.
- Unknown evidence remains safe by default.

### Costs and risks

- Closure evidence must be sourced, versioned and scoped correctly.
- Consumers must declare continuity policies instead of relying on one global
  adjacency rule.
- Cross-closure calculations require scientific review because elapsed time and
  price gaps may change interpretation.
- Historical data artifacts become sensitive to closure-evidence revisions and
  must be regenerated under new fingerprints.
- Existing provider-specific heuristics require explicit classification and
  must not be mistaken for universal evidence.

## 11. Alternatives Considered

### Timestamp adjacency everywhere

Rejected as the sole policy. It is safely fail-closed for unexplained gaps but
cannot distinguish a verified closure from missing data and produces unnecessary
consumer-specific ambiguity.

### Weekday/weekend inference as verification

Rejected. A recurring pattern is not evidence for every instrument, venue,
provider or historical date. It also cannot represent holidays or exceptional
closures.

### Live MarketState as historical closure evidence

Rejected. Live tradability and closure reasons are not historical observation
evidence and may be provider- or execution-context-specific.

### New Calendar Service

Deferred and rejected for this policy. Existing Market Data ownership and
historical evidence boundaries are sufficient for the first implementation. A
separate service requires an independent ownership and deployment decision.

### Permissive gap skipping

Rejected. Silently skipping missing intervals would hide data loss, change
elapsed-time semantics and weaken scientific reproducibility.

### One universal feature rule

Rejected. ATR, realized volatility, directional efficiency and future outcomes
have related but non-identical mathematical semantics. Each consumer must declare
its capability while reusing the shared continuity assessment.

## 12. ML-01.1 Implications

The approved ML-01.1 continuity policy is:

- the 33-candle volatility lookback may count valid H1 observations across
  verified closures;
- H+4, H+12 and H+24 count valid future H1 observations across verified
  closures;
- all four-feature lookbacks preserve actual timestamps, elapsed duration and
  reopening price gaps;
- every lookback and horizon rejects unexplained gaps, unknown closure evidence
  and invalid observations;
- continuity exclusions would distinguish verified closures from unexplained
  gaps;
- dataset fingerprints would include closure-policy and evidence versions;
- quality reports would expose closure, unknown, gap and invalid-observation
  counts separately.

This approval does not authorize treating an unverified gap as a closure. Real
data remains eligible to cross a closure only when Market Data supplies scoped,
versioned and non-contradictory closure evidence within the applicable
historical knowledge boundary.

The policy does not resolve these independent blockers:

- conflicting EUR/USD source rows;
- conflicting GBP/USD source rows;
- chronological source inversions;
- incomplete or corrupted source intervals;
- September end-of-dataset H+24 truncation;
- unapproved train/validation/test boundaries;
- unapproved embargo and minimum sample sizes;
- missing raw source checksums in already-generated artifacts;
- lack of demonstrated real MCP end-to-end execution.

The policy does not promise that any eligible observations will result. A new
assessment must be run against immutable source files, and all changes must be
visible through new artifacts and fingerprints.

## 13. Future Implementation Story Acceptance Criteria

An implementation Story based on this ADR must provide deterministic tests for:

### Continuity and evidence

- adjacent valid H1 candles produce `CONTIGUOUS`;
- a verified recurring weekend closure produces `VERIFIED_CLOSURE` only with
  matching scoped evidence;
- a verified holiday closure is accepted only for its effective interval;
- a verified exceptional closure preserves reason and evidence provenance;
- unknown closure evidence produces `UNKNOWN_CLOSURE_EVIDENCE` and fails closed;
- an expected missing H1 candle produces `UNEXPLAINED_GAP`;
- conflicting duplicate timestamps produce a visible duplicate/integrity result;
- incomplete H1 candles are rejected and never converted to closures;
- provider-specific interruption is not classified as a market closure without
  matching market evidence.

### Consumer semantics

- a feature consumer that disallows closures rejects a window crossing a
  verified closure;
- a feature consumer that explicitly allows closures can use the valid candles
  while preserving actual timestamps and elapsed duration;
- a 33-candle lookback across an approved closure records the transition and
  retains the reopening price gap;
- H+4, H+12 and H+24 count valid future observations rather than elapsed hours;
- approved closure crossing does not fabricate or interpolate a candle;
- unexplained gaps reject future and lookback windows;
- end-of-dataset truncation is reported separately from continuity corruption.

### Mathematical and reproducibility guarantees

- ATR and true range retain reopening price gaps;
- close-to-close realized volatility retains the observed reopening return;
- directional efficiency retains the observed path transition;
- no elapsed-time normalization is introduced without a new definition version;
- feature and outcome mathematics remain unchanged for a contiguous input;
- changing closure evidence changes the continuity and dataset fingerprints;
- rerunning the same source, policy and evidence produces the same result;
- no future closure evidence is used outside its approved historical knowledge
  boundary;
- no future outcomes enter feature computation, continuity policy selection or
  partition selection.

### Real-data validation

Synthetic fixtures must cover the deterministic cases above. A separate
validation run must use real historical market data and report:

- source checksums;
- provider and canonical market identities;
- closure evidence source/version/fingerprint;
- counts by transition classification;
- feature and outcome eligibility;
- independent source and dataset fingerprints;
- exclusions and unresolved evidence.

Synthetic tests must not be presented as proof of real-data readiness.

## 14. Implementation Boundaries

This ADR does not authorize:

- Java or Python code changes;
- source-data repair or CSV modification;
- a new provider or calendar integration;
- a new microservice or calendar framework;
- changes to ADR-092 or ADR-093;
- new MCP permissions or AgentGrants;
- production exports, broker operations or model training;
- implementation without an approved closure-evidence authority and Story.

The future implementation should first reuse and, where necessary, evolve the
existing Market Data continuity report, gap classifications, historical evidence
fingerprints and Market Intelligence artifact provenance. Existing accepted
contracts must remain compatible or be versioned explicitly.

## 15. Open Scientific Questions Requiring Approval

- Which source or combination of sources is authoritative for historical closure
  evidence for each canonical market and venue?
- What `knownAt` rule applies to retrospective research versus prospective
  backtesting?
- Which approved source or combination of sources is authoritative for historical
  closure evidence for each canonical market and venue?
- Are any additional ML-01 feature-specific exclusions required beyond preserving
  reopening price gaps and rejecting invalid transitions?
- What is the maximum tolerated unexplained gap, if any, for each consumer?
- Which closure evidence conflicts are terminal, and which authority resolves
  them?
- How should provider-specific trading interruptions be represented without
  changing historical market closure semantics?
- What minimum effective sample sizes and partition boundaries will ML-01 use
  after continuity classification?
- Which artifact retention and source-checksum fields are mandatory for future
  research exports?

## 16. Summary

Trading OS should evaluate historical continuity through a shared,
provider-independent, fail-closed domain contract. Valid observations, expected
intervals, verified closures, unexplained gaps, invalid observations and live
tradability must remain distinct.

Verified closures may be crossed only by consumers that explicitly declare and
fingerprint that capability. Actual timestamps, elapsed duration, closure
evidence and reopening price gaps must remain visible. Unknown evidence and
unexplained gaps remain invalid by default.

This accepted decision provides the reusable architectural boundary required by
ADR-093 without weakening ML-01 integrity controls or introducing a new service.

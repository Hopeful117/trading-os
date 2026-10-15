# STORY 0107 - HISTORICAL MARKET INTELLIGENCE REPLAY AND OBJECTIVE FUTURE OUTCOMES

DATE: 2026-10-15
REPOSITORY HEAD: `557fdeaf5c6ef3e8754fcdbf94ad47e994c64042`
ORIGIN/MAIN: `557fdeaf5c6ef3e8754fcdbf94ad47e994c64042`
BRANCH: `main`
WORKTREE AT START: clean and synchronized with `origin/main`

This is an investigation report only. No production code, database migration,
commit, push, merge, or pull request was created. `git fetch origin` succeeded.
No canonical Story 0107 artifact was present in the repository at investigation
time; this report records the requested investigation scope and the evidence
found in the current repository.

## 1. Executive Summary

Trading OS has a credible deterministic analysis boundary, but it does not yet
have the source retention and replay contract required for historical Market
Intelligence evaluation or objective future-outcome labels.

The current system can calculate deterministic results from a bounded context
assembled at execution time. It cannot generally reconstruct the exact facts
that were knowable at an arbitrary historical cutoff without depending on a
live provider, bounded provider history, or incomplete current persistence.

The recommended direction is a hybrid historical evidence and outcome
foundation. It should preserve immutable, provider-neutral source evidence and
context lineage, compute deterministic analyses through the same pure paths,
and add labels only from future observations strictly after the evaluation
cutoff. This foundation should precede ML or performance claims.

## 2. Investigation Question

Determine whether the current architecture can support:

- historical replay of Market Intelligence at an arbitrary evaluation cutoff;
- deterministic reuse of the same analysis semantics in live and replay modes;
- objective future outcome calculation without lookahead leakage;
- reproducible lineage from source facts through analysis and outcome labels;
- later feature generation and ML evaluation without changing authoritative
  deterministic domain boundaries.

## 3. Scope

The investigation covers Market Intelligence, Market Data, News context,
deterministic analyzers, execution and artifact persistence, accepted ADRs, and
the future replay/outcome boundary.

It does not implement historical storage, replay APIs, outcome labels, ML
pipelines, strategy evaluation, execution simulation, or UI changes.

## 4. Repository State

The working branch was `main` at commit `557fdeaf`. It matched
`origin/main`. The worktree was clean before this report was created. Previous
Story 0105 and 0106 changes are merged. No unrelated changes were reverted.

## 5. DevLog Applicability

No DevLog Story Agent capability was available in the connected tool set for
this investigation. Repository source, accepted ADRs, Story artifacts, and Git
history were used instead. This limitation affects historical project-memory
verification, not the current repository-state findings.

## 6. Relevant Completed Work

Story 0105 established the canonical immutable `IntelligenceContext`, section
statuses, provenance, fingerprints, and evaluation cutoff handling.

Story 0106 established deterministic spread, bounded OHLC, market-structure,
and Trend Context analysis paths with versioned artifacts and explicit degraded
semantics.

These Stories provide the calculation boundary, not the historical dataset
boundary.

## 7. Architectural Authority

ADR-056 is the primary authority for normalized facts, deterministic features,
versioned analyses, and future interpretation boundaries. ADR-048 governs
evidence authority, lineage, immutability, and non-authoritative outcomes.
ADR-049 governs confirmed Market Structure and temporal honesty. ADR-035
requires the same evaluator types for live and future historical evaluation.

The repository implementation remains authoritative for what currently exists.

## 8. Canonical Analysis Context

`IntelligenceContext` contains market ID, mode, objective, evaluation cutoff,
schema version, context quality, diagnostics, fingerprint, and typed sections.
The assembler derives a cutoff from the request or its clock and passes the
bounded request to contributors.

The context is immutable in normal Java use and uses defensive copies. It is
not itself a durable historical snapshot store.

## 9. Evaluation Cutoff

`evaluationCutoff` is the correct evidence boundary. Contributors can reject or
exclude facts whose occurrence or capture time is after that cutoff.

The cutoff must be treated as a historical timeline instant, not as the time at
which a replay job happens to execute.

## 10. Cutoff Propagation

The general context assembler propagates the bounded request to contributors.
The general OHLC and market-snapshot paths use the request cutoff. The Trend
Context role-history path was previously identified as using a separate clock
boundary and therefore requires explicit correction before its replayability can
be claimed as complete.

## 11. Known-At Semantics

A fact is eligible only when the system can establish that it was knowable at
the evaluation cutoff. Market occurrence time alone is insufficient when the
fact was acquired or published later. Source occurrence, provider update,
capture/fetch, closure, and correction semantics must remain distinct.

The current context contracts preserve some of these timestamps, but not all of
them in every normalized payload.

## 12. Current OHLC Acquisition

Market Intelligence calls `GET /api/v1/markets/{marketId}/ohlc` with interval
and limit only. The Market Intelligence client has no `evaluationCutoff`,
`from`, or `to` parameter.

The current contributor requests fixed 15-minute history with a limit of 200.
Trend Context requests role-specific bounded history, with configured limits and
possible expansion up to 720 candles.

## 13. OHLC Provider Capability

The Kraken Futures chart client technically accepts `count`, `from`, and `to`,
but the current normalized Market Data service path does not expose a general
historical cutoff contract through Market Intelligence. The presence of a
provider capability is therefore not evidence that replay is currently
supported.

## 14. OHLC Temporal Model

`OhlcEvent` retains open time, close time, occurrence time, closure, fetch time,
synthetic state, source identity, and provider information. This is sufficient
for a stronger source-evidence contract.

`OhlcPoint`, used by `HistoricalOhlcContext`, retains open time, close time,
OHLCV values, and does not retain closure, occurrence, fetch, synthetic, or
source identity fields.

## 15. Closed-Candle Semantics

Market Structure requires closed, non-synthetic evidence and must not treat a
confirmed point as known before its confirmation timestamp. A replay contract
must preserve candle closure as observed or deterministically derivable under a
declared clock policy. The current compact OHLC context cannot independently
prove all of these properties after normalization.

## 16. OHLC Completeness

Market Data has normalization and readiness concepts for ordering, gaps,
duplicates, conflicts, and synthetic candles. The canonical compact OHLC
context does not carry all readiness diagnostics. A future analyzer must not
infer completeness from list size alone.

## 17. OHLC Retention

No durable general OHLC history repository was found. Current OHLC history is
provider-backed and bounded. Existing `PriceObservation` persistence stores
quotes, not a normalized candle series suitable for arbitrary replay.

This is a primary replay limitation.

## 18. Quote Retention

`PriceObservation` stores bid, ask, last price, occurrence time, capture time,
market identity, and provider. It is a useful foundation for historical quote
quality, but the current investigation found no complete Market Intelligence
historical quote-series query and no explicit as-of selection contract.

Quote replay is therefore partial, not complete.

## 19. Order-Flow Retention

Order-book reconstruction is in memory. Recent trades are also bounded in
memory. No durable historical order-book or trade tape suitable for replay was
found.

Order-flow imbalance and absorption remain blocked by data retention and
temporal sequencing, not by arithmetic complexity.

## 20. News Retention

News Service persistence provides historical articles and economic events with
publication, scheduled, actual, source-update, and fetch timestamps. Market
Intelligence receives combined normalized news context, but event values and
some relevance semantics remain weakly typed.

News replay is partial and depends on an explicit known-at and relevance
contract.

## 21. Market Identity

The canonical Market model provides stable market ID, provider, symbol,
provider symbol, base asset, quote asset, and product type. This is sufficient
for current market-scoped lineage, subject to future cross-venue instrument
identity requirements.

## 22. Context Fingerprint

The context fingerprint includes schema, market, mode, objective, cutoff,
requirements, section statuses, provenance, and payload representations. This
supports identity and change detection.

It does not create missing historical facts. A fingerprint of an on-demand
provider response is not a durable replay dataset.

## 23. Execution Persistence

`analysis_executions` stores execution metadata and a serialized snapshot of
execution provenance, policy, capabilities, trace metadata, and consolidated
result. This supports execution audit and result retrieval.

It does not independently provide a queryable immutable source-fact snapshot
for arbitrary future replay.

## 24. Artifact Persistence

`capability_artifacts` stores artifact type/version, parameter fingerprint,
input fingerprint, producing execution ID, produced time, and serialized
payload. This is useful lineage, caching, and result retention.

It is not a substitute for durable source retention because the artifact may be
the result of a context assembled from live provider data and may not preserve
all temporal evidence fields.

## 25. Replay Definition

Replay means recomputing an analysis at a historical cutoff from facts that were
available at that cutoff, using the same algorithm and configuration semantics,
without a current provider response changing the result.

Replaying a previously stored result is retrieval, not recomputation. Both
retrieval and recomputation should be supported distinctly.

## 26. Replay Invariants

Every replay must identify market, cutoff, context schema, source evidence
identity, source acquisition/known-at metadata, analyzer version, configuration
version, and output status.

No evidence after the cutoff may affect the result. Missing or uncertain
evidence must remain explicit and must not become zero, neutral, or false.

## 27. Live and Replay Equivalence

ADR-035 requires the evaluator to receive the same infrastructure-free types in
live and historical modes. The difference should be context assembly and the
timeline instant, not separate evaluator semantics.

Current deterministic calculations are compatible with this direction, but the
historical assembler and source repository contract are not complete.

## 28. Quote Quality Replay

Spread and midpoint calculations are pure and deterministic when bid and ask
are available and valid. Historical replay is feasible from retained quote
observations if an as-of query, source identity, timestamp validity, and
selection policy are defined.

Status: PARTIAL, potentially replayable after source-query work.

## 29. OHLC Replay

Range and return calculations are pure over fixed candles. Replay is not
generally available because current acquisition is bounded, provider-backed,
and lacks a Market Intelligence cutoff query and durable normalized OHLC
retention.

Status: PARTIAL and currently source-limited.

## 30. Market Structure Replay

The structure engine has confirmed-only semantics, confirmation timestamps,
source fingerprints, and explicit cutoff requirements. ADR-049 provides the
correct temporal model.

Status: PARTIAL until upstream cutoff propagation, closure/readiness retention,
and historical source retrieval are complete.

## 31. Trend Context Replay

Trend Context is versioned and structured, but its upstream history acquisition
must use the same evaluation cutoff as the canonical context. Its role history
also needs sufficient historical retention and readiness metadata.

Status: PARTIAL.

## 32. Economic Event Replay

Scheduled event proximity can be deterministic if relevance, event time, source
update time, and known-at semantics are typed. Generic surprise calculations
remain unsafe across incompatible event units and values.

Status: PARTIAL.

## 33. News Density Replay

Article counts and publication recency can be calculated from persisted facts,
provided duplicate, relevance, publication, and source-update policies are
explicit. Provider coverage must not be mistaken for a market fact.

Status: PARTIAL.

## 34. Objective Outcome Definition

An outcome is a mechanically computed observation after an analysis cutoff. It
must not encode whether a trade was good, profitable, attractive, or authorized.
It is a label describing what happened under a declared measurement policy.

## 35. Outcome Reference Price

The outcome contract must declare its reference price source and timestamp. A
candidate default is the first eligible closed candle close at or after the
evaluation cutoff, with an explicit alternative for quote midpoint or another
provider-neutral price.

The reference must never be selected using future extrema or the eventual label.

## 36. Forward Return

For horizon H, a simple forward return can be defined as:

```text
forwardReturn(H) = (referencePriceAtCutoffPlusH - referencePriceAtCutoff)
                   / referencePriceAtCutoff
```

The contract must specify timestamp tolerance, eligible candle state, price
source, precision, and unavailable behavior.

## 37. Maximum Favorable Excursion

For a direction-neutral measurement, the future high and low over a declared
horizon can be retained as raw range facts. Directional maximum favorable
excursion requires a declared long or short orientation and therefore should not
be added to a generic outcome label without that input.

## 38. Maximum Adverse Excursion

Maximum adverse excursion likewise requires a directional reference or explicit
long/short label. It must be computed only from prices strictly after the
cutoff and must retain the horizon and eligible future evidence window.

## 39. Future Range

Future high, low, range, and time-to-extreme are objective measurements when the
future window is fully observed. A partially observed window must be marked
right-censored or incomplete, not treated as a complete negative or neutral
outcome.

## 40. Label Horizons

Horizons must be explicit and versioned, such as one candle, one hour, four
hours, or one day. The system must not emit unlabeled collections of values or
silently mix candle and wall-clock horizons.

## 41. Outcome Availability

An outcome is `AVAILABLE` only when the complete requested future window and
required price evidence exist. It is `CENSORED` or `INCOMPLETE` when the window
extends beyond retained data or the market has insufficient observations.

No unavailable outcome may be represented as zero return.

## 42. Outcome No-Lookahead

Outcome calculation is allowed to read facts after the analysis cutoff because
those facts are the label window. It must not alter the historical context or
analysis input. The cutoff and label window must be persisted separately.

## 43. Analysis/Outcome Separation

Analysis answers what was knowable at T. Outcome answers what happened after T.
They must be separate records or explicitly separate payload sections with
different evidence windows and lineage. Updating an analysis after outcomes are
known would violate immutability.

## 44. Outcome Versioning

Outcome algorithm version, horizon configuration, reference-price policy,
precision, eligibility policy, and source dataset identity must contribute to
outcome identity. A changed label policy must produce a new version, not mutate
historical labels.

## 45. Outcome Storage

The current execution and artifact tables can store serialized results, but
they do not define the required durable outcome dataset contract. A future
outcome store should be keyed by analysis/context identity, horizon policy, and
outcome version, with idempotent writes.

## 46. Feature Dataset Boundary

Typed deterministic analyses should remain authoritative Java evidence. A future
feature projection may flatten typed results into a versioned numeric dataset,
but it must preserve missingness, status, horizon, units, lineage, and label
relationship.

## 47. ML Readiness

ML is not ready to follow directly. Required prerequisites remain incomplete:

- durable historical source facts;
- exact replayable contexts;
- corrected cutoff propagation;
- objective outcome labels;
- sample/population definition;
- selection and survivorship controls;
- cost/slippage-aware evaluation where trading claims are made.

## 48. Snapshot-Only Option

Snapshot-only replay would persist complete context and deterministic results at
execution time. It is simple and reproducible for executions that were actually
captured, but cannot answer arbitrary historical cutoffs before the snapshot
exists and does not provide broad outcome coverage.

Assessment: useful as an audit foundation, insufficient as the sole replay
architecture.

## 49. Reconstruction-Only Option

Reconstruction-only replay would query retained normalized facts and rebuild
contexts on demand. It supports broad replay and avoids storing every context,
but requires durable source retention, exact as-of selection, correction policy,
and stable normalization.

Assessment: viable only after a substantial historical source contract.

## 50. Hybrid Option

Hybrid replay retains immutable source facts and selected context/result
snapshots. It can recompute when source evidence is available and retrieve
exact prior context when it is not, while preserving audit lineage.

Assessment: best fit for current architecture and future ML needs, with higher
storage and governance cost.

## 51. Decision Matrix

| Option | Arbitrary cutoff | Exact prior context | Storage | Current fit |
|---|---:|---:|---:|---:|
| Snapshot-only | No | Yes when captured | Medium | Partial |
| Reconstruction-only | Yes if retained | Not always | High source burden | Future |
| Hybrid | Yes where retained | Yes where captured | Highest | Recommended |

## 52. Recommended Foundation

Adopt a staged hybrid foundation:

1. Correct cutoff propagation and evidence eligibility.
2. Define durable normalized source facts with known-at metadata.
3. Define an immutable replay context/reference contract.
4. Reuse pure deterministic analyzers for live and replay.
5. Define objective outcome windows and statuses.
6. Persist analysis/outcome lineage without mutating analysis facts.

## 53. Proposed Future Boundaries

Market Data should own provider-neutral historical fact retention and as-of
queries. Market Intelligence should own context assembly, deterministic
analysis orchestration, and analysis lineage. A replay/outcome application
boundary may orchestrate future-window calculation, but it must not change
Market Intelligence authority or risk/execution decisions.

## 54. Required Follow-Up Stories

Suggested sequence:

1. Historical normalized OHLC and quote retention with known-at semantics.
2. Replay context contract and deterministic cutoff correction.
3. Historical Market Intelligence replay orchestration.
4. Objective future outcome and censoring contract.
5. Dataset projection and evaluation population controls.

Each Story should define acceptance criteria and avoid silently bundling ML,
strategy, or execution simulation.

## 55. Risks

- Provider corrections can change reconstructed history unless source versions
  and immutable captures are retained.
- Candle closure and fetch time can be lost during normalization.
- Bounded provider limits can create survivorship and coverage bias.
- Partial future windows can create false negative labels.
- Reusing current execution timestamps as evidence timestamps creates leakage.
- Cross-provider volume and price semantics can invalidate comparisons.
- Persisting serialized results without schema/version governance can make old
  artifacts unreadable or semantically ambiguous.

## 56. Validation Plan

Future implementation validation must include:

- fixed historical fixtures with facts before and after cutoff;
- assertions that post-cutoff facts cannot alter analysis;
- candle closure, synthetic, gap, duplicate, and conflict cases;
- deterministic result equality across live-shaped and replay-shaped contexts;
- outcome-window boundary and censoring cases;
- idempotent persistence and version changes;
- complete lineage and fingerprint assertions;
- targeted Market Data and Market Intelligence Maven tests;
- `git diff --check` and repository-specific static checks.

## 57. Open Questions

- Which historical source facts must be retained indefinitely versus by policy?
- Should OHLC storage be event-level, normalized-candle-level, or both?
- What is the authoritative reference price for each outcome family?
- Which horizons are product requirements rather than exploratory analysis?
- How should provider corrections and retroactive source updates be represented?
- Should replay use captured contexts preferentially or always recompute?
- What population and market-selection rules prevent survivorship bias?

## 58. Final Decision

**C - Proceed with a separate hybrid historical evidence and objective outcome
foundation Story before implementing ML or broad backtesting.**

The current deterministic analyzers and context model should be reused, not
replaced. The repository is not ready to claim arbitrary historical replay or
objective outcome coverage today because durable source retention, exact as-of
selection, complete temporal metadata, and outcome contracts are incomplete.

The next implementation Story should first establish the source and replay
invariants. It must preserve the human-controlled decision pipeline, keep
deterministic risk and execution boundaries unchanged, and explicitly prevent
future outcomes from entering historical analysis context.

## Validation Performed

- `git fetch origin`
- Repository and Git history inspection
- Targeted source inspection across Market Intelligence, Market Data, News,
  persistence, and Kraken adapters
- Accepted ADR inspection, including ADR-035, ADR-048, ADR-049, and ADR-056
- No production tests were run because no production code was changed.
- `git diff --check` and final `git status` are required after this report is
  written.

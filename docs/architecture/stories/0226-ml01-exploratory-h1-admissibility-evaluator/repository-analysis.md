# Story 0226 - Repository Analysis

## Purpose

Assess the current repository state before planning implementation of the
deterministic `ML01_HISTDATA_EXPLORATORY_V1` H1 admissibility evaluator.

This analysis is limited to Story 0226. It does not authorize implementation,
session-transition construction, dataset generation or ML execution.

## Repository State

- Branch: `story/0225-historical-continuity-ml01`
- HEAD: `78a37d7b3e518c33befe43d39b326170d7083a4d`
- The worktree contains existing uncommitted Java and documentation changes.
- Those changes are preserved and are not part of this Story's implementation.
- ADR-095 is accepted; ADR-093 contains the narrow strict-versus-exploratory
  clarification; ADR-094 remains unchanged and authoritative.

## Authoritative Scope

### ADR-095

ADR-095 accepts the research-only profile:

- `ML01_HISTDATA_EXPLORATORY_V1`;
- 55/60 minimum unique valid M1 observations;
- first and last M1 minutes mandatory;
- conflicts, invalid OHLC, malformed observations and chronology anomalies
  rejected;
- missing positions and maximum consecutive missing run recorded;
- no additional missing-run threshold;
- no interpolation, forward-fill, backfill or synthetic candles;
- strict Market Data states and continuity classifications preserved;
- outputs remain non-operational and are not canonical `FeatureSnapshot`
  evidence.

The weekend session convention, continuity strata, combined-window transition
limit and H+4/H+12/H+24 eligibility are deliberately outside this Story.

### ADR-093

ADR-093 remains the baseline ML-01 scientific protocol. Its four feature
definitions, future outcome definitions, valid-observation horizons,
chronological safeguards, leakage controls and non-operational objective remain
unchanged. Strict and exploratory populations are separate and must not be
silently pooled.

### ADR-094

ADR-094 remains authoritative for Market Data observation quality, strict
continuity classifications, verified closure evidence and fail-closed handling.
The evaluator may derive a research admissibility decision but must not produce
`VERIFIED_CLOSURE`, rewrite strict states or convert a partial candle into a
trusted complete observation.

### ADR-092

The Research Agent and MCP adapter remain bounded consumers of Java-controlled
research contracts. This Story does not add an MCP capability, permission,
export path or direct filesystem access.

## Current Implementation

### Market Intelligence HistData pipeline

`market-intelligence` currently contains the relevant database-free pipeline:

- `HistDataOfflinePipeline.read(...)` parses fixed UTC-05:00 HistData M1 rows;
- `ImportResult` records accepted observations, quality events, quarantined
  timestamps, invalid timestamps, malformed rows, invalid rows, exact and
  conflicting duplicates, chronology inversions and missing M1 slots;
- `HistDataOfflinePipeline.aggregate(...)` creates H1 buckets with
  `COMPLETE`, `INCOMPLETE`, `INVALID` and `SESSION_UNCERTAIN` states;
- `AggregatedCandle` records expected and observed M1 counts, OHLC values,
  timestamps, state and source file;
- `Ml01HistDataPreparation` currently filters to `COMPLETE` H1 candles before
  feature and outcome preparation and already exposes strict transition and
  closure-evidence behavior for the separate Story 0225 work;
- existing tests use JUnit 5, AssertJ, temporary source files and deterministic
  in-memory fixtures.

The current aggregation retains enough information to identify several quality
events at import level, but `AggregatedCandle` does not expose per-bucket missing
minute positions, first/last minute presence or maximum consecutive missing run.
Those fields are required by ADR-095 and must be surfaced through the smallest
compatible extension of the existing aggregation evidence.

### Market Data contracts

Market Data already provides provider-neutral historical contracts:

- `HistoricalOhlcEvidence` validates identity, interval, timestamps, OHLC,
  provenance, `knownAt` and observation fingerprints;
- `HistoricalOhlcContinuityReport` aggregates expected and valid observations and
  strict gap assessments;
- `HistoricalOhlcGapAssessment` carries interval, classification and reason;
- `OhlcHistoryNormalizer` detects conflicting duplicate OHLC content but also
  contains a generic synthetic missing-interval path that must not be reused for
  this exploratory profile.

The current HistData offline parser and H1 aggregation are implemented inside
Market Intelligence rather than consuming the Market Data historical evidence
API directly. This is an existing boundary to verify during planning. Story
0226 must not create a second authoritative parser. If the evaluator cannot
receive sufficient M1-level evidence through the current boundary, the plan
must identify the smallest explicit contract or metadata extension and keep
Market Data ownership of authoritative classifications.

## Existing Tests And Build

Relevant tests are:

- `HistDataOfflinePipelineTest`: malformed rows, exact duplicates, conflicting
  duplicates, chronology inversions, missing M1 contributors and session-uncertain
  buckets;
- `Ml01HistDataPreparationTest`: H1 preparation, feature/outcome generation,
  verified closure versus unexplained gaps, chronological inversion handling and
  scoped preparation.

The module uses Java 21, Maven, JUnit 5 and AssertJ. No new test framework is
required. The existing `market-intelligence/pom.xml` includes JaCoCo with an
80% bundle line-coverage check during `verify`.

## Required Implementation Boundary

The implementation plan should prefer a small immutable policy/evaluation
contract in Market Intelligence that:

1. receives an already parsed H1 bucket and its M1-level quality evidence;
2. applies the named ADR-095 profile deterministically;
3. returns one of the four research quality states;
4. returns stable rejection reason codes and complete missingness metadata;
5. preserves the original strict H1 state and continuity classifications;
6. identifies the policy/profile version in the result; and
7. exposes no production `FeatureSnapshot` or trusted Market Data state.

The evaluator must fail closed. Known integrity failures should be
`REJECTED`; evidence that is insufficient to make a required decision should be
`UNKNOWN_QUALITY`. Exact reason-code naming should follow existing repository
conventions where available and otherwise be fixed in the implementation plan
before coding.

The implementation must not include:

- a second CSV/M1 parser;
- synthetic or repaired candles;
- a generic quality-rule engine;
- session-calendar logic;
- combined feature/outcome window construction;
- future-horizon eligibility;
- session-transition strata and assumptions;
- changes to ADR-094 or Story 0225.

The approved Story scope was subsequently extended to include the bounded
research-only dataset export and fingerprint contract. It remains outside the
scope of this Story to implement session-transition strata or scientific
readiness.

## Expected Contract Gap

The current import result exposes aggregate quality counters and global sets,
while the Story requires bucket-level provenance. The implementation plan must
resolve how to associate the following with each H1 bucket without reparsing the
source:

- unique valid M1 count;
- missing minute positions;
- first-minute presence;
- last-minute presence;
- maximum consecutive missing-minute run;
- duplicate/conflict, invalid and malformed indicators;
- chronology anomaly status;
- original strict H1 aggregation state;
- original continuity classifications.

The preferred direction is to extend the existing deterministic aggregation
evidence or introduce a narrowly scoped value object at the existing HistData
pipeline boundary. The plan must reject any approach that silently reconstructs
quality from already lossy OHLC values.

## Testing Surface

The implementation plan must cover, using existing Java test conventions:

- parameterized 54, 55, 59 and 60 valid-minute cases;
- missing first and last minute cases;
- exact and conflicting duplicates;
- invalid OHLC and malformed source rows;
- chronology inversions/anomalies;
- missing positions and maximum consecutive missing run;
- unknown or incomplete provenance;
- stable reason codes and policy-version output;
- repeated-evaluation determinism;
- strict-state and continuity-classification immutability;
- research isolation from canonical `FeatureSnapshot` evidence.

Focused Market Intelligence tests are required. Market Data tests are required
only if the implementation plan identifies a necessary contract extension in
that module. Existing affected-module suites and `git diff --check` remain the
validation baseline.

## Risks And Open Questions

- The current HistData aggregation loses some per-bucket M1 metadata; a careless
  implementation could infer missingness from OHLC and produce false eligibility.
- Existing `SESSION_UNCERTAIN` aggregation state must remain a strict input fact,
  not be silently converted into a verified closure or a session transition.
- Existing Story 0225 work and uncommitted changes must not be overwritten or
  folded into Story 0226 without explicit scope review.
- The evaluator's output must remain separate from canonical feature snapshots
  and existing strict preparation results.
- The exact smallest interface extension is not yet selected; it must be resolved
  in the Implementation Plan, not invented during coding.

No architectural contradiction was found. The main implementation dependency is
the bucket-level M1 quality/provenance contract described above.

## Validation Plan

Before implementation completion, run at minimum:

- focused Market Intelligence evaluator and HistData pipeline tests;
- relevant Market Data contract tests if its public contract is extended;
- affected-module Maven tests;
- JaCoCo/verification checks when the module validation workflow requires them;
- `git diff --check`.

No dataset generation, provider download, MCP execution, KMeans execution,
model training or broker/provider mutation is applicable to Story 0226.

## Recommendation

Repository Analysis is complete and supports preparing an Implementation Plan.
The next workflow gate is explicit human approval of this Repository Analysis.
After approval, the Implementation Plan must define the smallest bucket-level
quality-evidence extension before any Java implementation begins.

## References

- `docs/architecture/stories/0226-ml01-exploratory-h1-admissibility-evaluator/story.md`
- `docs/architecture/stories/0225-historical-continuity-and-ml01-closure-crossing/story.md`
- `docs/architecture/stories/0225-historical-continuity-and-ml01-closure-crossing/implementation-report.md`
- `docs/architecture/adr/ADR-092-proposed-mcp-research-operational-control-boundary.md`
- `docs/architecture/adr/ADR-093-proposed-ml-01-chronological-experimental-protocol.md`
- `docs/architecture/adr/ADR-094-market-data-continuity-and-verified-closure-evidence-policy.md`
- `docs/architecture/adr/ADR-095-proposed-ml-01-exploratory-research-admissibility-protocol.md`

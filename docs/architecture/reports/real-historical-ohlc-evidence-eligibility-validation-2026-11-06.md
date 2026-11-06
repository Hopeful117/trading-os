# Trading OS - Real Historical OHLC Evidence Eligibility Validation

**Date:** 2026-11-06  
**Status:** `HISTORICAL_RECONSTRUCTION_REQUIRED`  
**Mode:** read-only repository validation; no database or runtime mutation

## A. Execution Summary

Gate C did not produce a complete research sample. It did establish that real
persisted OHLC evidence is admissible for one bounded candidate window under the
current strict temporal rules. The complete research contract remains unproven
because no matching historical `FeatureSnapshot`, `AnalysisExecution`, or
`ObservedMarketOutcome` exists for that cutoff, and creating those artifacts
would require a historical reconstruction workflow or database writes.

The documentary Story 0142 inconsistency was explicitly treated as non-blocking
for this gate, per human direction. An independent read-only review still
confirmed the provenance and reconstruction boundary findings below.

## B. Selected Evidence

The first deterministic candidate was the lexicographically first persisted
market/interval with captured, closed, non-synthetic evidence. Its `knownAt`
was after the available future candles, so it could not provide an independent
outcome. Per the instruction not to expand without reason, the next
lexicographic supported market/interval was inspected because it had a bounded
window with both strict input evidence and a future row.

Selected candidate:

```text
Market:       8b336ec3-1557-4432-b052-ae569e041dac
Instrument:   cTrader provider symbol 2 / source cTrader:LIVE:2:GBP/USD
Timeframe:    FIFTEEN_MINUTES
Feature rows: 20
Feature end:  2026-11-06T16:30:00Z
Cutoff:       2026-11-06T16:45:00Z
Outcome row:  2026-11-06T16:45:00Z .. 2026-11-06T17:00:00Z
```

The 20 feature observation IDs, ordered newest to oldest, are:

```text
827745ef-d889-49e7-9277-6f11f322f2c5
0f683aba-e3fe-47ec-882a-378af94fafd1
108e6f08-c5a4-4db8-a8c8-f4cc2134c4ff
544e0710-427e-465b-9827-9d3b6fb72ebe
fc665b44-fc61-488d-b41f-5c586f31b6ab
ba794b18-26ee-499a-8b74-11e4a5bdb268
e3cdd922-e557-458c-8489-8d2809cd812e
26098b6a-3932-4a47-a176-e42c1dbef5a4
452f3f26-0273-4186-beb1-ef6c579ee05f
fcaaeda1-d75d-411e-8aa7-4d5ab8ed5247
921fe2ab-3e7b-402e-9549-51f0e58eb885
19cd3b30-dfe2-4194-82c3-1e64cf19758c
24f6d161-7e35-461d-a875-62c326d4bc8a
ec35bc76-01de-4907-b6c8-49986ef305fe
ae516535-d38a-4755-b0b5-bba7136de3b2
31f445b7-7dc0-43ad-b7c7-2c18f71b793f
a210efd6-fc71-46b1-b11f-e1737b45236e
97b4566c-8897-42a0-bd9e-dba8ddeeeee5
98ad715d-c61f-4f4b-b505-744aa2a14e43
1e78b471-27f0-482f-b79b-779ba6ba27e3
```

The independent future observation ID is
`38f1078f-56dc-425f-90d3-61bd2b1fd970`, fingerprint
`04f3e18e65cafb2cc62b948c6e196837a6be8bc424924547f7ac7a40f44c2514`.

The repository does expose the intended bounded read path:

```text
market-data/src/main/java/com/hope/trading/market_data/controller/InternalHistoricalOhlcController.java
GET /internal/v1/historical-ohlc/{marketId}
GET /internal/v1/historical-ohlc/{marketId}/range
```

The persisted evidence model contains the required fields in
`market-data/src/main/java/com/hope/trading/market_data/model/HistoricalOhlcEvidence.java`:

```text
observationId, marketId, interval, openTime, closeTime,
OHLC values, provider, providerSymbol, sourceId,
fetchedAt, capturedAt, knownAt, origin,
normalizationVersion, observationFingerprint
```

The bounded PostgreSQL query verified all selected rows as `CAPTURED`, closed,
non-synthetic, `historical-ohlc-v1`, with non-null `fetchedAt`, `capturedAt`,
`knownAt`, source identity, and observation fingerprints. No write occurred.

## C. Provenance Chain

The current source code shows the intended provenance path:

| Boundary | Result | Evidence |
| --- | --- | --- |
| Market Data persisted OHLC evidence | Structurally present | `HistoricalOhlcEvidence` includes `fetchedAt`, `capturedAt`, `knownAt`, identity, origin, and fingerprint |
| Market Data internal contract | Structurally present | `InternalHistoricalOhlcController` delegates strict historical reads to `HistoricalOhlcHistoryService` |
| Market Intelligence context summary | Remediation present | `ContextSectionSummary` includes `fetchedAt`, source reference/version, normalization version, evidence policy/fingerprint, observation count, and cutoff |
| Context construction | Remediation present | `IntelligenceConsolidator` and `CapabilityAnalysisCoordinator` construct summaries with provenance fields |
| Feature/export eligibility | Strict checks present | `ResearchDatasetExportService` rejects missing `fetchedAt` and evidence known after the feature cutoff; `ResearchDatasetBuilder` also fails closed |

No runtime sample crossed these boundaries during this gate. No synthetic
provenance was introduced.

## D. Temporal Eligibility

The candidate cutoff was selected deterministically as the next quarter-hour
boundary after the selected feature window and is supported by the persisted
evidence. All 20 feature rows satisfy `closeTime <= cutoff` and
`knownAt <= cutoff`.

The current strict rule remains:

```text
knownAt <= evaluationCutoff
```

`sourceOccurredAt` is not used as a substitute for `fetchedAt`. The source code
also preserves the distinction between `FeatureSnapshot.createdAt` and
`evaluationCutoff`; no backdating or retrospective timestamp rewriting was
performed.

The future row is available only for outcome labeling: its interval starts at
the cutoff and ends at `2026-11-06T17:00:00Z`. Its `knownAt` is after the cutoff,
which is acceptable for future outcome availability but not for input features.
No retrospective feature snapshot was created.

## E. Feature Snapshot

The remediation is present in the dirty worktree and preserves context-level
evidence identity and temporal metadata. The source path would use
`HistoricalOhlcEvidenceSource`, `StrictObservedOhlcEvidenceValidator`,
`HistoricalOhlcContextContributor`, and `ContextSectionSummary` without
relaxing `STRICT_OBSERVED`.

However, no persisted feature snapshot exists for the candidate market and
cutoff, and no existing execution context can be reused. Projecting the 20
real rows into a valid historical snapshot therefore requires the missing
historical reconstruction step.

The remediation report records this residual artifact-to-source proof risk and
states that no real historical sample was proven eligible.

## F. Future Outcome

The persisted future OHLC row provides one 15-minute outcome observation after
the cutoff. Its high, low, close, closed flag, captured origin, normalization
version, and fingerprint were inspected. It is temporally independent from the
20 input rows and was not included in the feature cutoff query.

No `market-outcome-v1` domain outcome was generated or persisted. The exact
outcome horizon required by the research population was therefore not
completed.

The existing export code resolves selected outcomes by stable fingerprint and
checks market, cutoff, horizon, and outcome-definition identity. That contract
was inspected but not exercised against a new real sample.

## G. Export Eligibility

No export was executed. No sample was marked eligible by the strict exporter.
The Market Intelligence database contains no matching feature snapshot,
analysis execution, or observed outcome for the candidate market/cutoff, so the
export contract cannot be satisfied from the current persisted state alone.

The known negative reference remains unchanged:

```text
Historical bundle v2: 96 candidates, 0 eligible, 96 excluded
Exclusion reason: FEATURE_EVIDENCE_UNAVAILABLE
```

That frozen bundle was not modified or reinterpreted.

## H. Reproducibility

The OHLC selection was repeated with the same bounded SQL predicates and
returned the same 20 observation IDs and fingerprints. The future row identity
was also stable. Feature snapshot, outcome, and final sample fingerprints could
not be compared because those artifacts were not produced.

The current source contracts preserve stable observation and feature evidence
identities, but real-data reproducibility remains unverified.

## I. Independent Review

An independent read-only reviewer found:

- `CONFIRMED`: the Story 0142 inconsistency is documentary and was explicitly
  ignored for this gate by human direction;
- `CONFIRMED`: selected real OHLC evidence is temporally admissible at the
  candidate cutoff;
- `CONFIRMED`: the future OHLC row is separate from the input feature window;
- `CONFIRMED`: provenance remediation is present in the current source;
- `UNVERIFIED`: a complete feature snapshot and `market-outcome-v1` sample can
  be produced without a reconstruction workflow;
- `BLOCKED`: strict export eligibility cannot be demonstrated without the
  missing persisted historical artifacts.

ADR-063, ADR-067, ADR-069, the Market Data forensic audit, and the remediation
report were inspected. DevLog context was not available through the connected
tools for this run; repository sources were treated as authoritative. Obsidian
was not required for this repository-local validation.

## J. Next Engineering Decision

**Primary recommendation: `HISTORICAL_RECONSTRUCTION_REQUIRED`**

The real OHLC evidence is present and strict temporal admissibility is proven
for the selected window, but the current persisted contracts do not contain the
historical feature snapshot and domain outcome required for export. The
smallest next action is to define or approve a read-only historical
reconstruction path that creates no production writes and preserves the current
evidence identities and cutoff semantics.

Do not weaken `STRICT_OBSERVED`, rewrite evidence, modify frozen samples, or
launch the full benchmark.

## Final Safety Verification

- No new collection occurred.
- No database write, migration, temporary table, index, or export occurred.
- No service was restarted or redeployed.
- No Order Book replay occurred.
- No historical evidence or frozen snapshot was modified.
- No benchmark was launched.
- No LLM, broker, trade, or external provider call occurred.
- No Git commit, push, merge, reset, or checkout occurred.
- Docker build-cache cleanup had completed before this gate; no further Docker
  operation was performed.
- No large artifact was created.

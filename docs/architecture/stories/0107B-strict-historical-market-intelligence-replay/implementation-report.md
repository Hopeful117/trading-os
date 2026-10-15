# Story 0107B - Implementation Report

## 1. Repository State

- Branch: `main`
- Baseline HEAD: `d9174e2e3c1eab05505f70e9f995173414fbcfab`
- Worktree was clean before implementation.
- `git fetch origin` was attempted but blocked by the local SSH agent refusing
  the configured signing key. No Git state was changed.

## 2. Evidence Policy

Added `EvidenceAcquisitionPolicy` with `LIVE_CURRENT` and `STRICT_OBSERVED`.
`ACTIVE` and `PASSIVE` remain operational analysis modes.

## 3. Live and Strict Acquisition

`LiveOhlcEvidenceSource` retains the existing provider-backed OHLC client.
`HistoricalOhlcEvidenceSource` consumes the internal Market Data historical
endpoint through a separate Feign context and validates the strict contract.

Strict validation checks series identity, cutoff, captured origin, closure,
synthetic state, source identity, `closeTime`, `knownAt`, and source update time.

## 4. Context Integration

`HistoricalOhlcContextContributor` and
`TrendContextRoleHistoryContextContributor` select the source explicitly from
the request policy. Both continue to produce the existing analytical payloads.
Historical evidence is validated before projection into `OhlcResponse` and
`OhlcPoint`-based context types.

## 5. Execution Persistence

`AnalysisExecutionProvenance` now retains `evaluationCutoff` and evidence
policy. `dispatchRegistered()` restores both values. Strict requests without an
explicit cutoff are rejected.

## 6. Capability Eligibility

Strict active analysis selects OHLC and Trend Context capabilities without
requiring current Market Snapshot. Market Structure is still resolved through
the existing artifact dependency planner. Live active planning is unchanged.

## 7. Provenance and Fingerprints

Context provenance now supports evidence policy, aggregate evidence fingerprint,
and observation count. The context fingerprint includes evidence policy. Initial
artifact source identity includes strict evidence and normalization identity.

## 8. Tests Added

- strict historical evidence mapping and validation;
- live versus strict source selection;
- strict cutoff and policy persistence through redispatch;
- strict capability planning without current snapshot.

Existing contributor, Trend Context, security, persistence, and strategy tests
were also run.

## 9. Deferred Sections

Historical Quote Quality, Market Snapshot, MarketState, News, economic-event
revision semantics, and backfill remain explicitly deferred.

## 10. Validation

Passed:

```text
mvn -q -DskipTests compile
mvn -q -Dtest=HistoricalOhlcEvidenceSourceTest,OhlcEvidencePolicySelectionTest,AnalysisExecutionServiceTest,AnalysisExecutionStrategyTest,HistoricalOhlcContextContributorTest,TrendContextRoleHistoryContextContributorTest test
mvn -q -Dtest=MiSecurityIntegrationTest,StrategyDefinitionPersistenceIntegrationTest,StrategyMatchPersistenceTest,BuiltinStrategyBootstrapTest,StrategyMatchRequiredTruthTest test
mvn -q -Dtest=HistoricalOhlcEvidenceSourceTest,TrendContextRoleHistoryContextContributorTest,AnalysisExecutionStrategyTest test
mvn -q test
mvn -q verify
git diff --check
```

The full `mvn -q test` run was initially blocked by duplicate Feign client
specification registration; assigning `HistoricalOhlcClient` an explicit
`contextId` resolved that integration issue. Final full test and verify runs
passed after that fix.

No live Kraken or externally deployed PostgreSQL runtime replay was performed;
local PostgreSQL runtime validation is documented below.

## 11. Files Created

- `ADR-058.md`;
- Story 0107B `story.md`;
- this implementation report;
- evidence policy, historical client/source, and replay tests.

## 12. Files Modified

- Market Intelligence request, provenance, context, execution, strategy,
  contributor, client, and test files.

## 13. Known Limitations

- Runtime provider-isolation replay was validated against the local PostgreSQL
  stack; externally deployed PostgreSQL validation remains pending.
- Historical quote/snapshot and News are not replayable in this Story.
- Historical identity reuses canonical market metadata but does not reconstruct
  historical tradability.

## 14. Independent Review Corrections

- Strict passive planning no longer requires the live Market Snapshot or the
  spread capability that depends on it.
- Strict passive planning now requires historical OHLC and Trend Context
  context while keeping canonical market identity optional for replay.
- Trend Context now carries aggregated evidence fingerprint, normalization
  version, policy, and observation count provenance.
- Historical OHLC rejects missing, mixed, or unsupported normalization versions.
- The internal execution `Location` now resolves through a service-authenticated
  internal GET resource for the returned execution identifier.

## 15. Runtime Validation

- Rebuilt and started `market-data` and `market-intelligence` against the local
  PostgreSQL volumes without deleting data.
- Applied Market Data migration V5 for `historical_ohlc_evidence`.
- Captured 598 strict-compatible OHLC observations for `BTC/USD` across
  `FIFTEEN_MINUTES`, `ONE_HOUR`, and `FOUR_HOURS`.
- Added and exercised the internal service endpoint
  `POST /internal/v1/intelligence/analyses` with a `trading-core` service JWT.
- Runtime execution `0916ee09-c743-48bd-a4a3-aa1f00be7b22` completed in
  PostgreSQL with `STRICT_OBSERVED`, the requested cutoff, and
  `resultQuality=DEGRADED` without a failure code.
- No public Gateway route or user JWT was used for the replay trigger.

# Story 0225 - Implementation Report

## Scope

This change completes the missing closure-evidence propagation in the ML-01
export path. It does not add a closure provider, a calendar, a public API, a
permission or a real-data source.

## Original Integration Gap

`HistoricalDatasetExportApplicationService` called
`Ml01HistDataPreparation.prepare(..., List.of())` for the ML-01 export profile.
The Market Data closure-evidence contract and ML-01 consumer policy therefore
existed independently, but an authorized export could never pass verified
closure evidence to the ML-01 consumer.

## Implementation

- Added `HistoricalClosureEvidenceSource` as an application seam for a future
  approved Market Data adapter.
- Added `UnavailableHistoricalClosureEvidenceSource`, which returns no evidence
  in production until an authoritative source is approved. Missing evidence
  remains fail-closed in ML-01 transition classification.
- Added `HistoricalClosureEvidenceAdapter`, which explicitly maps the
  provider-neutral evidence shape to ML-01 and validates market scope, H1
  interval, venue, timestamps, effective scope, export scope, source identity,
  version and fingerprint.
- Updated `HistoricalDatasetExportApplicationService` to obtain evidence through
  the seam and pass the mapped evidence to `Ml01HistDataPreparation`.
- Extended ML-01 closure evidence with market identity, interval and venue so
  those fields remain available during consumer acceptance and provenance
  diagnostics.
- Added closure source/version/venue diagnostics to the quality report.
- Added application-service integration tests for transmission, verified
  crossing, missing evidence, contradictory evidence, out-of-scope evidence,
  late evidence, reopening behavior and fingerprint changes.

## Governance

Market Data remains the owner of canonical evidence acquisition and base
classification. Market Intelligence remains the owner of ML-01 consumer
acceptance. No weekend heuristic, live market state, provider outage or missing
timestamp creates closure evidence. Multiple applicable evidence records remain
ambiguous and fail closed.

No authoritative closure-evidence repository, port or provider was present in
the current Market Data implementation. The new production source therefore
returns an empty list by design. A future adapter must implement the seam from
an approved source; this Story does not authorize that integration.

## Validation

Executed successfully:

- `mvn -q -Dtest=HistoricalDatasetExportApplicationServiceTest,Ml01HistDataPreparationTest,HistDataOfflinePipelineTest test` in `market-intelligence`.
- `mvn -q -Dtest=HistoricalOhlcContinuityValidatorTest test` in `market-data`.
- `mvn -q test` in `market-data`.
- `mvn -q test` in `market-intelligence`.
- `mcp-adapter/.venv/bin/python -m pytest mcp-adapter/tests` previously validated the unaffected MCP adapter: 38 passed.
- `research/ml/.venv/bin/python -m pytest` previously validated the unaffected Python consumer: 57 passed.
- `git diff --check`.

The new integration tests use controlled in-memory evidence and temporary
synthetic test inputs only. They are not deployed MCP end-to-end tests and do
not establish real-data readiness.

## Remaining Blockers

- **Authoritative closure source:** no approved historical closure provider or
  persisted closure-evidence source is available to production exports.
- **HistData quality:** real September 2026 EUR/USD and GBP/USD source files and
  their checksums are not present in this repository, so real population quality
  cannot be assessed here.
- **Scientific parameters:** ML-01 partition and embargo parameters remain
  explicitly unapproved; KMeans and ML-01.2 remain out of scope.
- **Real MCP end-to-end validation:** no deployed runtime validation was run for
  this change; no new permission or broker operation was used.

## Acceptance Status

The integration defect is fixed at the application seam and is covered by
synthetic application-service tests. Story-level scientific completion remains
unverified because authoritative closure evidence, real HistData and approved
scientific parameters are absent.

Documentation update: this implementation report was required by the task. No
ADR or Story scope was changed.

Vault Outcome: no vault action. The repository and accepted ADRs were
sufficient; no curated vault knowledge was needed and the vault remained
read-only.

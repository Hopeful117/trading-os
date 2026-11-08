# Engineering Report - Story 0221

## 1. Repository Baseline

- Story 0220 is merged in `origin/main` at `b21bc3fe`.
- Story 0221 is the next available Story ID.
- Work started from `story/0220-research-lifecycle-observability` at `b1b44720`.
- No existing unrelated work was found before implementation.
- No commit, push, merge or deployment was performed.

## 2. Historical Data Inventory

The repository contains provider-neutral contracts for durable OHLC evidence,
order-book evidence and replay, historical funding evidence, immutable feature
snapshots, immutable objective outcomes, economic events and financial news.
Market Intelligence owns reconstruction, feature/outcome pairing and controlled
export. Python owns offline validation and experimentation only.

Contracts and migrations do not prove that tables are populated. The committed
Java-generated bundle is a fixture with one sample and one `PT1H` outcome.

## 3. Runtime Availability

The local Docker services were running, but read-only requests for research
readiness, research integrity, markets and news events returned HTTP `401`.
No credentials were inspected or bypassed, no SQL was executed and no runtime
state was modified.

## 4. Implementation Summary

- Extended the existing Python validator summary with deterministic
  `summary.quality` output.
- Added coverage, observed gaps, expected-count determinability, cadence
  determinability, duplicate checks, timestamp validity, market identity
  consistency, feature/outcome completeness, provenance, fingerprints and
  approximate horizon windows.
- Preserved fail-closed validation, strict `knownAt` checks, exact outcome
  selection, command names and exit codes.
- Added regression tests and documented the report in the Python README.
- Added Story-local readiness, implementation and code-review reports.

## 5. Dataset Export Evidence

The existing controlled Java export path is represented by the committed
cross-language fixture and its contract was validated through the Python
consumer. The Java export itself was not executed in this validation cycle, and
no new export was created from runtime data because runtime access was
unauthorized. Controlled export execution therefore remains unverified.

Fixture evidence:

- `sampleCount`: `1`;
- `eligibleCount`: `1`;
- `excludedCount`: `0`;
- `horizons`: `PT1H`;
- `featureEvidencePolicy`: `STRICT_OBSERVED`;
- `datasetFingerprint`:
  `8eefc33c7614134c56b8d8cb236197e54011826ef9ac9f83bfb258f48e0eb260`;
- expected physical cadence: `NOT_DETERMINABLE`.

## 6. Quality Report

`research_dataset diagnose` now emits a machine-readable quality report. It
explicitly distinguishes measured fixture facts from dimensions that cannot be
derived from the bundle, including physical cadence, expected observation
count and freshness policy.

## 7. ML Readiness

The feature and outcome contracts are suitable for future offline regime,
volatility, liquidity or anomaly experiments. Actual market coverage,
continuity, regime diversity and effective sample size are not verified. No
model training, inference or profitability claim was performed.

## 8. Tests Executed

- `cd research/ml && .venv/bin/pytest`: `55 passed`.
- `diagnose` against the committed Java-generated fixture: valid quality report.
- `git diff --check`: passed.
- Root-level Python collection was attempted but is not applicable because it
  mixes projects with separate virtual environments; it failed before test
  execution on missing `ai-engine`/`mcp-adapter` dependencies. The targeted
  Research ML suite passed.

## 9. Code Review

An independent review identified evidence wording, outcome metric semantics and
invalid-bundle coverage gaps. Those findings were corrected: export execution
is explicitly marked unverified, outcome status counts are separated from
validation-violation counts, and early invalid-bundle paths now emit an
`INVALID` quality summary. Residual risks concern runtime authorization and
intentionally unavailable cadence/freshness metadata, not temporal validation
regressions.

## 10. Documentation And Vault Outcome

- Updated `research/ml/README.md` for the new diagnostic output.
- Added the historical readiness report, implementation report and code review.
- No ADR was required.
- Obsidian vault was not consulted; no vault action or proposal was generated.

## 11. Final Verdict

```text
RUNTIME_DATA_NOT_VERIFIED
```

The dataset pipeline is implemented and validated against fixtures, but real
historical population cannot be claimed until an authorized read-only runtime
export is produced and its quality report is reviewed.

## 12. Recommended Next Step

Provision isolated read-only access to the Market Data and Market Intelligence
dataset environment, export one declared market/timeframe/cutoff bundle, and
measure actual continuity, provenance, outcome maturity and non-overlapping
windows before selecting the first unsupervised learning experiment.

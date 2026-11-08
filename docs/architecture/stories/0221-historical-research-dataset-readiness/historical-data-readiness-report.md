# Historical Data Readiness Report - Story 0221

## Verdict

```text
RUNTIME_DATA_NOT_VERIFIED
```

The repository contains a reproducible historical dataset contract and a
validated offline consumer, but no authorized read-only runtime access was
available to measure actual persisted evidence. Fixture evidence is not treated
as production coverage.

## Repository Evidence

| Family | Implemented contract | Runtime population |
|---|---|---|
| OHLCV | Market Data `historical_ohlc_evidence`, strict `knownAt` cutoff and range/gap responses | Not verified |
| Quote/spread | Current market facts and deterministic spread capability | Historical population not verified |
| Order book | Immutable trusted snapshots/deltas, sequence replay and bounded segments | Not verified |
| Funding/borrow | Fingerprinted historical funding repository with strict reads | Not verified |
| Structure/volatility/momentum | Deterministic capabilities projected into `feature-projection-v1` | Feature snapshot population not verified |
| Outcomes | Immutable `observed_market_outcomes` selected by complete fingerprint identity | Completeness/maturity not verified |
| Economic events | News Service persistence and bounded time query | Provider synchronization/runtime population not verified |
| Financial news | News Service persistence and bounded time query | Provider synchronization/runtime population not verified |

The canonical export path remains:

```text
strict historical evidence
  -> deterministic reconstruction
  -> immutable FeatureSnapshot
  -> selected immutable ObservedMarketOutcome
  -> ResearchDatasetBuilder
  -> versioned JSONL bundle
  -> Python validation and experiment readiness gates
```

## Fixture Evidence

The committed Java-generated fixture validates cross-language compatibility only:

- observations: `1`;
- eligible samples: `1`;
- excluded samples: `0`;
- markets: `1` fixture UUID;
- horizon: `PT1H`;
- evidence policy: `STRICT_OBSERVED`;
- feature projection: `feature-projection-v1`;
- outcome definition: `market-outcome-v1`;
- dataset fingerprint:
  `8eefc33c7614134c56b8d8cb236197e54011826ef9ac9f83bfb258f48e0eb260`;
- validation: valid;
- physical evidence cadence: `NOT_DETERMINABLE` because the population declares
  `ONE_PER_FEATURE_SNAPSHOT`.

These values are fixture measurements, not runtime historical coverage.

## Runtime Inspection

The local Docker stack was running. Read-only Gateway requests for research
readiness, research integrity, markets and economic events all returned HTTP
`401` without an authorized credential. Internal service authentication was not
bypassed. No database credentials were inspected, no SQL was executed and no
runtime state was changed.

The earlier audit observation of approximately five weeks of OHLC evidence,
zero feature snapshots and seven outcomes is retained only as historical,
environment-specific context. It is not current Story 0221 measurement.

## Quality Report

The existing Python validator now exposes `summary.quality` from `diagnose`.
It reports:

- validation status and observation counts;
- cutoff coverage and observed gaps;
- explicit cadence determinability;
- duplicate identity diagnostics;
- feature missingness and provenance counts;
- outcome status and maturity/provenance issues;
- historical reconstruction lineage coverage;
- approximate non-overlapping windows by horizon;
- dataset and sample fingerprints.

The report distinguishes observed cutoff intervals from physical provider gaps.
It does not claim statistical independence from raw row counts.

## Temporal Integrity

The existing safeguards remain active:

- strict evidence requires `knownAt <= evaluationCutoff`;
- candle close time is not substituted for `knownAt`;
- future outcome evidence remains separate from feature snapshots;
- immutable outcome selection uses the complete outcome fingerprint identity;
- timezone-aware timestamp parsing is preserved;
- invalid future evidence remains a validation failure.

## ML Readiness Matrix

| Experiment | Readiness |
|---|---|
| Volatility regime clustering | Contract feasible; actual continuous coverage unknown |
| Market regime clustering | Feature schema exists; population and effective sample size unknown |
| Liquidity regime detection | Replay contract exists; trusted runtime order-book coverage unknown |
| Anomaly detection | Future offline candidate; comparable population not verified |

No model was trained, deployed or inferred. No profitability or independence
claim is made.

## Minimum Next Data Step

Provision an authorized read-only path to one isolated Market Intelligence and
Market Data dataset environment. Export one controlled bundle for a declared
market, interval, cutoff window and outcome horizon. Then measure actual
coverage, continuity, missingness, provenance, maturity and effective
non-overlapping windows before selecting an unsupervised experiment.

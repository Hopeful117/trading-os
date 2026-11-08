# ML and Historical Data Readiness Assessment

**Date:** 2026-11-08
**Mode:** read-only repository audit

## Verdict

The repository is ready for reproducible offline dataset validation, deterministic baselines, exploratory notebooks and externally produced prediction benchmarking. It is not ready for production ML training, calibrated prediction, live inference, or profitability claims.

## What Exists

`research/ml` validates exported bundles without network access or a Java runtime. Validation covers schema, checksums, fingerprints, missingness, evidence policy and outcome completeness. The experiment command performs chronological splitting, overlap purging and train-only preprocessing. The baseline compares zero prediction, train mean and Ridge. The benchmark scores externally supplied AI directions against the same validated samples.

The package intentionally keeps the boundary:

```text
Java = canonical deterministic market/research truth
Python = validated reusable research logic
Jupyter = exploratory hypothesis discovery
```

The notebook is explicitly not a training system. Tests also assert that the notebook does not train a model.

## Readiness Matrix

| Area | Evidence | Readiness |
|---|---|---|
| Dataset schema and integrity | `validator.py`, manifest/sample fingerprint checks | Ready for offline use |
| Temporal split discipline | chronological 70/30 split and purge logic | Ready for baseline experiments |
| Feature leakage controls | train-only preprocessing and cutoff validation | Foundation ready; broader joins still need operational proof |
| Outcome labeling | exported outcome fingerprints and completeness checks | Contract exists; data volume is insufficient |
| Dataset versioning | manifest, population policy, feature/outcome versions | Foundation present |
| Baseline model | zero, train mean, Ridge | Exploratory baseline only |
| AI prediction benchmark | strict cutoff/evidence validation and direction metrics | Offline benchmark ready |
| Backtesting | no complete cost-aware replay engine evidenced | Not ready |
| Model registry | no production registration/deployment lifecycle evidenced | Not ready |
| Online inference | no production model serving contract evidenced | Not ready |
| Calibration | no calibrated probability contract evidenced | Not ready |
| Drift monitoring | no production drift/alert lifecycle evidenced | Not ready |
| Real historical coverage | fixtures and controlled exports, not sufficient population | Blocked |

## Temporal Requirements

The correct eligibility rule is `evidence.knownAt <= decisionKnownAt`, not candle close time. Feature availability should be the maximum `knownAt` of required inputs. Outcomes must preserve observed/available timestamps and remain censored when unavailable. Overlapping horizons require chronological walk-forward evaluation, purge and embargo; raw row count must not be presented as independent sample size.

The earlier quantitative-readiness investigation recorded only approximately five weeks of OHLC evidence, no operational order-book or funding persistence, zero feature snapshots and seven outcomes in the inspected runtime. That evidence is historical and environment-specific, but it remains a direct warning against profitability claims until a current populated export proves otherwise.

## Required Before ML Claims

1. Populate a versioned export with sufficient multi-market, multi-regime coverage.
2. Prove continuity, freshness, missingness and outcome completeness at export time.
3. Add cost-aware replay for spread, fees, slippage, impact, funding/borrow, latency and partial fills.
4. Define walk-forward, purge, embargo, instrument and regime sensitivity policies.
5. Add model identity, feature contract, training provenance, calibration, evaluation and deployment metadata.
6. Compare every learned model with deterministic baselines and report uncertainty and effective sample size.

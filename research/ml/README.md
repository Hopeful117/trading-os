# Research Dataset Consumer

This package validates Market Intelligence research dataset bundles without
network access or a Java runtime.

## Validate

```text
PYTHONPATH=src python3 -m research_dataset validate <bundle-directory>
PYTHONPATH=src python3 -m research_dataset diagnose <bundle-directory>
```

Validation is fail-closed. It checks the bundle schema, sample-file checksum,
dataset fingerprint material, sample fingerprints, feature missingness, strict
evidence policy, and outcome completeness.

## Java Export

The Market Intelligence service exposes a controlled export runner. Provide a
population specification JSON and an output directory:

```text
java -jar market-intelligence.jar \
  --research.dataset.export.enabled=true \
  --research.dataset.export.spec=/path/population.json \
  --research.dataset.export.output=/path/export
```

The population specification contains `populationPolicyVersion`, `marketIds`,
`startCutoffInclusive`, `endCutoffExclusive`, `featureProjectionVersion`,
`featureEvidencePolicy`, `outcomeDefinitionVersion`, `horizons` as ISO-8601
durations, `samplingCadence`, `acceptedFeatureMissingness`, and
`selectedOutcomeFingerprints` entries with `marketId`, `evaluationCutoff`,
`horizon`, and `outcomeFingerprint`.

## Baseline Experiment

Story 0109B reuses the validated bundle and runs an offline chronological
BTC/USD `PT4H` forward-return experiment:

```text
PYTHONPATH=src python3 -m research_dataset experiment \
  --manifest=/path/export/dataset-manifest.json \
  --samples=/path/export/samples.jsonl \
  --market=BTC/USD \
  --market-id=<validated-market-uuid> \
  --horizon=PT4H \
  --output-dir=/path/research-output
```

The command validates the bundle before loading rows. It uses a chronological
70/30 split, purges training rows whose four-hour outcome window overlaps the
test start, fits preprocessing on train only, and compares ZERO and TRAIN_MEAN
baselines with Ridge `alpha=1.0`.

The 0109A bundle contains market UUIDs but no market symbols, so the BTC/USD
label requires an explicit validated `--market-id`; the experiment never guesses
from a single arbitrary market in the bundle.

The V1 readiness gate requires 90 days of strict observed coverage, 5,000
eligible samples, 500 approximate non-overlapping four-hour windows, 100
non-overlapping test windows, and no gap larger than the canonical 15-minute
capture cadence. A failed gate returns exit code `2` and the
conclusion `BLOCKED_BY_DATA_READINESS`; no model metrics or predictions are
claimed as research evidence. Synthetic fixtures are test-only and must never
be reported as runtime data.

When ready, the output contains `experiment-manifest.json`, `metrics.json`, and
test-only `predictions.jsonl`. Predictive improvement is not a tradable edge
or a profitability claim.

## Interactive Research Workflow

Story 0110 establishes the research-layer boundary:

```text
Java = canonical deterministic market/research truth
Python = validated, reusable, tested research logic
Jupyter = interactive exploration and hypothesis discovery
```

Jupyter does not replace the Java truth boundary or the validated Python
package. When exploratory code becomes reusable or important to correctness,
extract it into the structured Python package and test it there. Notebook 01
does not train a model; the 0109B structured experiment remains the location
for formal baselines and reproducible metrics.

### Start JupyterLab

From the repository root:

```text
python3 -m venv research/ml/.venv
research/ml/.venv/bin/pip install -e research/ml
research/ml/.venv/bin/jupyter lab
```

Open `research/notebooks/01_dataset_exploration.ipynb`. The notebook defaults
to the repository fixture for a deterministic smoke run. Set `DATASET_DIR` in
the first code cell to a Java export directory containing exactly
`dataset-manifest.json` and `samples.jsonl` before exploring runtime data.
The bundle is always loaded through `load_validated_bundle`; direct pandas
JSONL loading is intentionally not supported.

Use **Kernel -> Restart Kernel and Run All Cells** for a clean execution. The
notebook supports an empty eligible population and reports data readiness
without attempting plots that require observations.

Notebook outputs are exploratory and should be cleared before commit. Do not
commit large tables, plots, machine-specific paths, or sensitive runtime data.

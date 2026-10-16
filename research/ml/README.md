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

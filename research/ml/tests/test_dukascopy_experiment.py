import json
import hashlib
import tempfile
import unittest
from pathlib import Path

from research_dataset.dukascopy_experiment import (
    FEATURES,
    DukascopyExperimentBlocked,
    DukascopyExperimentConfig,
    load_bundle,
    run_experiment,
    validate_bundle_shape,
)


def row(partition: str, value: float) -> dict:
    minute = int(value) + {"TRAIN": 0, "VALIDATION": 6, "TEST": 12}[partition]
    return {
        "partition": partition,
        "partitionExclusion": None,
        "instrument": "EUR/USD",
        "rowFingerprint": f"row-{partition}-{value}",
        "cutoff": f"2026-01-01T00:{minute:02d}:00Z",
        "featureLookbackStart": "2025-12-30T00:00:00Z",
        "featureLookbackEnd": f"2025-12-31T23:{value:02d}:00Z",
        "quality": "STRICT",
        "outcomes": {str(horizon): {"status": "AVAILABLE", "futureOpenTimes": [f"2026-01-01T01:{value:02d}:00Z"],
                                     "futureEnd": f"2026-01-01T02:{value:02d}:00Z",
                                     "absoluteFutureNetReturn": value / 100.0,
                                     "realizedVolatility": (value + horizon) / 100.0,
                                     "directionalEfficiency": 0.5} for horizon in (4, 12, 24)},
        "features": {name: {"status": "AVAILABLE", "value": value + index} for index, name in enumerate(FEATURES)},
    }


class DukascopyExperimentTest(unittest.TestCase):
    def bundle(self, root: Path, include_test: bool = True) -> None:
        rows = [row("TRAIN", value) for value in range(6)] + [row("VALIDATION", value) for value in range(6)]
        if include_test:
            rows += [row("TEST", value) for value in range(3)]
        observations_fingerprint = hashlib.sha256("\x1f".join(sorted(
            f'{value["rowFingerprint"]}|{value["partition"]}|null|[]' for value in rows
        )).encode()).hexdigest()
        (root / "dataset-manifest.json").write_text(json.dumps({
            "experimentSchema": "ML01_DUKASCOPY_REGIME_EVALUATION_V1",
            "features": list(FEATURES),
            "partitions": list(("TRAIN", "VALIDATION", "TEST")),
            "rowCount": 15,
            "datasetFingerprint": "dataset",
            "sourceDatasetFingerprint": "source-dataset",
            "configurationFingerprint": "source",
            "sourceConfigurationFingerprint": "source",
            "partitionFingerprint": "partition",
            "observationsFingerprint": observations_fingerprint,
            "partitionScenario": "COMMON_UTC_70_15_15",
            "partitionBoundaries": {"trainEndExclusive": "2026-01-01T00:06:00Z",
                                    "validationEndExclusive": "2026-01-01T00:12:00Z",
                                    "testEndExclusive": "2026-01-01T01:00:00Z"},
            "effectivePartitionCounts": {"TRAIN_EFFECTIVE": 6, "VALIDATION_EFFECTIVE": 6, "TEST_EFFECTIVE": 3},
            "threeWaySplitRequired": True,
            "pooledModel": True,
            "bundleFingerprint": "bundle",
        }), encoding="utf-8")
        (root / "observations.jsonl").write_text(
            "".join(json.dumps(value, sort_keys=True) + "\n" for value in rows), encoding="utf-8")
        (root / "readiness.json").write_text(json.dumps({
            "status": "READY_FOR_EXECUTION", "requiresScientificApproval": False,
            "configurationFingerprint": "source", "bundleFingerprint": "bundle",
        }), encoding="utf-8")
        (root / "partition-manifest.json").write_text(json.dumps({
            "status": "READY_FOR_EXECUTION", "configurationFingerprint": "source",
            "fingerprint": "partition",
        }), encoding="utf-8")
        (root / "source-metadata.json").write_text(json.dumps({
            "sourceDatasetFingerprint": "source-dataset",
        }), encoding="utf-8")

    def test_rejects_train_test_only_bundle(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.bundle(root, include_test=False)
            with self.assertRaises(DukascopyExperimentBlocked):
                load_bundle(root)

    def test_blocks_before_kmeans_when_scientific_configuration_is_incomplete(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.bundle(root)
            output = root / "output"
            result = run_experiment(root, output, DukascopyExperimentConfig("x", "v1", "COMMON_UTC_70_15_15"))
            self.assertEqual(result["status"], "BLOCKED")
            self.assertEqual(result["reason"], "SCIENTIFIC_CONFIGURATION_INCOMPLETE")
            self.assertTrue((output / "readiness.json").is_file())
            self.assertFalse((output / "selection-freeze.json").exists())

    def test_complete_configuration_uses_three_partitions(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.bundle(root)
            config = DukascopyExperimentConfig("x", "v1", "COMMON_UTC_70_15_15",
                                               k_values=(2,), seeds=(0,),
                                               selection_thresholds={"minValidationSilhouette": -1.0},
                                               uncertainty_method="BLOCK_DESCRIPTIVE", block_length=2,
                                               n_init=10, algorithm="lloyd", max_iter=300, tolerance=1e-4,
                                               dependency_versions={"scikit-learn": "test"})
            result = run_experiment(root, root / "output", config)
            self.assertEqual(result["status"], "COMPLETED")
            self.assertEqual(result["testRows"], 3)
            freeze = json.loads((root / "output" / "selection-freeze.json").read_text())
            self.assertFalse(freeze["testEvaluated"])
            self.assertTrue((root / "output" / "experiment-result.json").is_file())
            result_document = json.loads((root / "output" / "experiment-result.json").read_text())
            self.assertEqual(set(result_document["heldOutEvaluation"]["horizons"]), {"4", "12", "24"})

    def test_selection_freeze_is_immutable(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.bundle(root)
            config = DukascopyExperimentConfig("x", "v1", "COMMON_UTC_70_15_15",
                                               k_values=(2,), seeds=(0,),
                                               selection_thresholds={"minValidationSilhouette": -1.0},
                                               uncertainty_method="BLOCK_DESCRIPTIVE", block_length=2,
                                               n_init=10, algorithm="lloyd", max_iter=300, tolerance=1e-4,
                                               dependency_versions={"scikit-learn": "test"})
            output = root / "output"
            self.assertEqual(run_experiment(root, output, config)["status"], "COMPLETED")
            freeze = output / "selection-freeze.json"
            freeze.write_text(freeze.read_text(encoding="utf-8").replace('"testRows":3', '"testRows":4'),
                              encoding="utf-8")
            with self.assertRaisesRegex(DukascopyExperimentBlocked, "immutable artifact"):
                run_experiment(root, output, config)

    def test_shape_requires_all_three_effective_populations(self):
        manifest = {"experimentSchema": "ML01_DUKASCOPY_REGIME_EVALUATION_V1",
                    "features": list(FEATURES), "partitions": ["TRAIN", "VALIDATION", "TEST"]}
        with self.assertRaises(DukascopyExperimentBlocked):
            validate_bundle_shape(manifest, [row("TRAIN", 1), row("VALIDATION", 2)])

    def test_shape_rejects_non_finite_feature(self):
        value = row("TRAIN", 1)
        value["features"][FEATURES[0]]["value"] = float("nan")
        manifest = {"experimentSchema": "ML01_DUKASCOPY_REGIME_EVALUATION_V1",
                    "features": list(FEATURES), "partitions": list(("TRAIN", "VALIDATION", "TEST"))}
        with self.assertRaises(DukascopyExperimentBlocked):
            validate_bundle_shape(manifest, [value, row("VALIDATION", 2), row("TEST", 3)])

import json
import importlib.util
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

from research_dataset.dataset import ValidatedResearchDataset
from research_dataset.__main__ import main
from research_dataset.experiment import (
    ReadinessPolicy,
    Readiness,
    _build_pipeline,
    _conclusion,
    _feature_types,
    _metrics,
    _improvements,
    _non_overlapping_windows,
    _rank,
    _frame,
    _residual_diagnostics,
    _split,
    run_experiment,
)
from research_dataset.validator import ValidationResult


MARKET_ID = "11111111-1111-1111-1111-111111111111"


class ExperimentTest(unittest.TestCase):
    def test_non_overlapping_window_diagnostic_is_deterministic(self):
        base = datetime(2026, 1, 1, tzinfo=timezone.utc)
        cutoffs = [base + timedelta(hours=value) for value in (0, 1, 2, 4, 8, 9)]
        self.assertEqual(_non_overlapping_windows(cutoffs), 3)

    def test_split_is_chronological_and_purges_overlapping_train_rows(self):
        rows = list(self.dataset(20).rows)
        rows.reverse()
        train, test, purged = _split(rows)
        self.assertEqual(len(train), 11)
        self.assertEqual(len(test), 6)
        self.assertEqual(purged, 3)
        self.assertEqual(train[-1]["evaluationCutoff"], "2026-01-01T10:00:00Z")
        self.assertLess(train[-1]["evaluationCutoff"], test[0]["evaluationCutoff"])

    @unittest.skipUnless(
        all(importlib.util.find_spec(name) for name in ("numpy", "pandas", "sklearn")),
        "ML dependencies are installed by the research project environment",
    )
    def test_two_runs_produce_identical_research_artifacts(self):
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            policy = ReadinessPolicy(min_coverage_days=0, min_eligible_samples=10,
                                     min_non_overlapping_windows=1, min_test_windows=1,
                                     max_coverage_gap_hours=2)
            left = run_experiment(self.dataset(20), output_dir=first, readiness_policy=policy, market_id=MARKET_ID)
            right = run_experiment(self.dataset(20), output_dir=second, readiness_policy=policy, market_id=MARKET_ID)
            self.assertEqual(left["manifest"]["experimentFingerprint"], right["manifest"]["experimentFingerprint"])
            for name in ("experiment-manifest.json", "metrics.json", "predictions.jsonl"):
                self.assertEqual((Path(first) / name).read_bytes(), (Path(second) / name).read_bytes())

    @unittest.skipUnless(
        all(importlib.util.find_spec(name) for name in ("numpy", "pandas", "sklearn")),
        "ML dependencies are installed by the research project environment",
    )
    def test_unknown_test_category_is_handled_by_pipeline(self):
        with tempfile.TemporaryDirectory() as directory:
            result = run_experiment(
                self.dataset(20, test_category="UNSEEN"), output_dir=directory, market_id=MARKET_ID,
                readiness_policy=ReadinessPolicy(min_coverage_days=0, min_eligible_samples=10,
                                                 min_non_overlapping_windows=1, min_test_windows=1,
                                                 max_coverage_gap_hours=2),
            )
            self.assertEqual(result["status"], "COMPLETED")

    def test_post_purge_insufficient_population_blocks(self):
        with tempfile.TemporaryDirectory() as directory:
            result = run_experiment(
                self.dataset(2), output_dir=directory, market_id=MARKET_ID,
                readiness_policy=ReadinessPolicy(min_coverage_days=0, min_eligible_samples=1,
                                                 min_non_overlapping_windows=1, min_test_windows=1,
                                                 max_coverage_gap_hours=2),
            )
            self.assertEqual(result["status"], "BLOCKED_BY_DATA_READINESS")
            self.assertIn("PURGE_LEFT_INSUFFICIENT_SPLIT", result["readiness"].reasons)

    @unittest.skipUnless(
        all(importlib.util.find_spec(name) for name in ("numpy", "pandas", "sklearn")),
        "ML dependencies are installed by the research project environment",
    )
    def test_ready_fixture_runs_chronological_purged_ridge(self):
        with tempfile.TemporaryDirectory() as directory:
            result = run_experiment(
                self.dataset(20), output_dir=directory,
                readiness_policy=ReadinessPolicy(min_coverage_days=0, min_eligible_samples=10,
                                                 min_non_overlapping_windows=1, min_test_windows=1,
                                                 max_coverage_gap_hours=2),
                market_id=MARKET_ID,
            )
            self.assertEqual(result["status"], "COMPLETED")
            self.assertEqual(result["metrics"]["purgedSamples"], 3)
            self.assertTrue((Path(directory) / "experiment-manifest.json").is_file())
            self.assertTrue((Path(directory) / "metrics.json").is_file())
            predictions = (Path(directory) / "predictions.jsonl").read_text().splitlines()
            self.assertEqual(len(predictions), result["metrics"]["testSamples"])
            self.assertEqual(json.loads(predictions[0])["trainMeanPrediction"], 0.05)
            self.assertTrue(all(json.loads(line)["zeroPrediction"] == 0.0 for line in predictions))
            self.assertEqual(set(json.loads(predictions[0])), {
                "sampleId", "evaluationCutoff", "actualForwardReturn", "zeroPrediction",
                "trainMeanPrediction", "ridgePrediction",
            })

    def test_empty_runtime_population_blocks_before_model_metrics(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            (output / "experiment-manifest.json").write_text("stale", encoding="utf-8")
            (output / "metrics.json").write_text("stale", encoding="utf-8")
            (output / "predictions.jsonl").write_text("stale", encoding="utf-8")
            result = run_experiment(self.dataset(0), output_dir=directory, market_id=MARKET_ID)
            self.assertEqual(result["status"], "BLOCKED_BY_DATA_READINESS")
            self.assertFalse((output / "metrics.json").exists())
            self.assertFalse((Path(directory) / "predictions.jsonl").exists())

    def test_market_identity_must_be_declared(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                run_experiment(self.dataset(0), output_dir=directory,
                               market_id="22222222-2222-2222-2222-222222222222")

    def test_empty_market_declaration_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            dataset = self.dataset(0)
            manifest = dict(dataset.manifest)
            population = dict(manifest["population"])
            population["marketIds"] = []
            manifest["population"] = population
            unrestricted = ValidatedResearchDataset(manifest, (), ValidationResult((), {}))
            with self.assertRaises(ValueError):
                run_experiment(unrestricted, output_dir=directory, market_id=MARKET_ID)

    def test_experiment_cli_rejects_missing_market_identity(self):
        fixture = Path(__file__).parent / "fixtures" / "java-generated"
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            (output / "metrics.json").write_text("stale", encoding="utf-8")
            (output / "predictions.jsonl").write_text("stale", encoding="utf-8")
            self.assertEqual(main(["experiment", "--manifest", str(fixture / "dataset-manifest.json"),
                                   "--samples", str(fixture / "samples.jsonl"), "--market", "BTC/USD",
                                   "--horizon", "PT4H", "--output-dir", directory]), 1)
            self.assertFalse((output / "metrics.json").exists())
            self.assertFalse((output / "predictions.jsonl").exists())
            self.assertFalse((output / "experiment-manifest.json").exists())

    def test_unacceptable_residual_diagnostics_force_inconclusive(self):
        available = {"available": True, "value": 0.1}
        metrics = {
            "ZERO": {"mae": 1.0},
            "RIDGE": {"mae": 0.5, "constantPrediction": False,
                      "r2": available, "pearson": available, "spearman": available},
            "temporalStability": {"available": True, "ridgeBeatsZeroInBothHalves": True},
            "residualDiagnostics": {"available": True, "acceptable": False},
        }
        self.assertEqual(_conclusion(metrics, Readiness(True, (), {})), "INCONCLUSIVE")

    @unittest.skipUnless(
        all(importlib.util.find_spec(name) for name in ("numpy", "pandas", "sklearn")),
        "ML dependencies are installed by the research project environment",
    )
    def test_preprocessing_state_does_not_change_when_test_values_change(self):
        import numpy as np
        import pandas as pd
        from sklearn.linear_model import Ridge
        dataset = self.dataset(20)
        feature_types = _feature_types(dataset.feature_definitions)
        train_rows, test_rows, _ = _split(list(dataset.rows))
        train_frame, train_target = _frame(train_rows, feature_types, pd, np)
        test_frame, _ = _frame(test_rows, feature_types, pd, np)
        model = _build_pipeline(feature_types, Ridge).fit(train_frame, train_target)
        imputer = model.named_steps["preprocessor"].named_transformers_["numeric"].named_steps["imputer"]
        before = imputer.statistics_.copy()
        changed = test_frame.copy()
        changed["numeric"] = 10_000.0
        changed["category"] = "UNSEEN_TEST_ONLY"
        model.predict(changed)
        self.assertTrue(np.array_equal(before, imputer.statistics_))

    @unittest.skipUnless(
        all(importlib.util.find_spec(name) for name in ("numpy", "pandas")),
        "ML dependencies are installed by the research project environment",
    )
    def test_missingness_is_preserved_as_feature_availability_column(self):
        import numpy as np
        import pandas as pd
        dataset = self.dataset(1)
        row = dict(dataset.rows[0])
        features = dict(row["features"])
        features["numeric"] = {"type": "NUMERIC", "status": "STALE_INPUT", "value": None}
        row["features"] = features
        frame, _ = _frame([row], _feature_types(dataset.feature_definitions), pd, np)
        self.assertEqual(frame.iloc[0]["numeric__missingness"], "STALE_INPUT")
        self.assertTrue(np.isnan(frame.iloc[0]["numeric"]))

    def test_feature_matrix_contains_only_features_and_availability_columns(self):
        dataset = self.dataset(1)
        columns = set(_feature_types(dataset.feature_definitions))
        self.assertEqual(columns, {
            "numeric", "numeric__missingness", "category", "category__missingness",
            "flag", "flag__missingness",
        })
        self.assertTrue(columns.isdisjoint({"outcome", "forwardReturn", "marketId", "evaluationCutoff", "sampleId"}))

    def test_tied_spearman_ranks_use_average_rank(self):
        import numpy as np
        self.assertTrue(np.array_equal(_rank(np.array([1.0, 1.0, 3.0])), np.array([0.5, 0.5, 2.0])))

    def test_metrics_keep_constant_cases_undefined(self):
        import numpy as np
        result = _metrics(np.array([1.0, 1.0]), np.array([1.0, 2.0]), np,
                          lambda a, p: float(np.mean(np.abs(a - p))),
                          lambda a, p: float(np.mean((a - p) ** 2)),
                          lambda a, p: 0.0)
        self.assertFalse(result["r2"]["available"])
        self.assertFalse(result["pearson"]["available"])

    def test_metrics_compute_required_secondary_diagnostics(self):
        import numpy as np
        actual = np.array([1.0, -1.0, 2.0, -2.0])
        predicted = np.array([0.5, -0.5, 1.0, -1.0])
        result = _metrics(actual, predicted, np,
                          lambda a, p: float(np.mean(np.abs(a - p))),
                          lambda a, p: float(np.mean((a - p) ** 2)),
                          lambda a, p: float(1 - np.sum((a - p) ** 2) / np.sum((a - np.mean(a)) ** 2)))
        self.assertAlmostEqual(result["mae"], 0.75)
        self.assertAlmostEqual(result["rmse"], np.sqrt(0.625))
        self.assertTrue(result["r2"]["available"])
        self.assertAlmostEqual(result["r2"]["value"], 0.75)
        self.assertTrue(result["pearson"]["available"])
        self.assertAlmostEqual(result["pearson"]["value"], 1.0)
        self.assertTrue(result["spearman"]["available"])
        self.assertAlmostEqual(result["spearman"]["value"], 1.0)
        self.assertEqual(result["directionalAccuracy"], 1.0)

    def test_baseline_improvements_are_reported_against_each_baseline(self):
        result = _improvements({"ZERO": {"mae": 1.0}, "TRAIN_MEAN": {"mae": 0.8},
                                "RIDGE": {"mae": 0.5}})
        self.assertEqual(result["ZERO"], {"maeAbsolute": 0.5, "maePercentage": 50.0})
        self.assertAlmostEqual(result["TRAIN_MEAN"]["maeAbsolute"], 0.3)
        self.assertAlmostEqual(result["TRAIN_MEAN"]["maePercentage"], 37.5)

    @unittest.skipUnless(
        all(importlib.util.find_spec(name) for name in ("numpy", "pandas")),
        "ML dependencies are installed by the research project environment",
    )
    def test_target_is_forward_return_and_metadata_cannot_enter_features(self):
        import numpy as np
        import pandas as pd
        dataset = self.dataset(1)
        feature_types = _feature_types(dataset.feature_definitions)
        original_frame, original_target = _frame(list(dataset.rows), feature_types, pd, np)
        mutated = dict(dataset.rows[0])
        mutated["marketId"] = "attacker-market"
        mutated["evaluationCutoff"] = "2099-01-01T00:00:00Z"
        mutated.update({"sampleId": "attacker-sample", "horizon": "PT4H",
                        "featureProjectionVersion": "feature-projection-v1",
                        "status": "ELIGIBLE", "featureSnapshotId": "attacker-snapshot",
                        "datasetFingerprint": "attacker-dataset", "lineageFingerprint": "attacker-lineage",
                        "createdAt": "2099-01-01T00:00:00Z", "computedAt": "2099-01-01T00:00:00Z"})
        mutated["outcome"] = {"status": "AVAILABLE", "forwardReturn": 0.99,
                               "knownAt": "2099-01-01T00:00:00Z", "closeTime": "2099-01-01T00:00:00Z",
                               "createdAt": "2099-01-01T00:00:00Z", "leakageField": "must-not-be-feature"}
        mutated_frame, mutated_target = _frame([mutated], feature_types, pd, np)
        self.assertTrue(original_frame.equals(mutated_frame))
        self.assertEqual(original_target[0], 0.0)
        self.assertEqual(mutated_target[0], 0.99)
        excluded = {"sampleId", "marketId", "evaluationCutoff", "horizon", "featureProjectionVersion",
                    "status", "featureSnapshotId", "datasetFingerprint", "lineageFingerprint", "createdAt",
                    "computedAt", "outcome", "forwardReturn", "knownAt", "closeTime", "leakageField"}
        self.assertTrue(set(feature_types).isdisjoint(excluded))

    def test_residual_diagnostics_detect_unstable_or_biased_residuals(self):
        import numpy as np
        result = _residual_diagnostics(np.zeros(4), np.array([0.0, 1.0, 0.0, 10.0]), np)
        self.assertTrue(result["available"])
        self.assertFalse(result["stableScale"])
        self.assertFalse(result["acceptable"])

    def dataset(self, count, test_category=None):
        base = datetime(2026, 1, 1, tzinfo=timezone.utc)
        definitions = [
            {"name": "numeric", "dataType": "NUMERIC"},
            {"name": "category", "dataType": "CATEGORICAL"},
            {"name": "flag", "dataType": "BOOLEAN"},
        ]
        rows = []
        for index in range(count):
            cutoff = (base + timedelta(hours=index)).isoformat().replace("+00:00", "Z")
            rows.append({
                "sampleId": f"sample-{index}", "marketId": MARKET_ID,
                "evaluationCutoff": cutoff, "horizon": "PT4H",
                "featureProjectionVersion": "feature-projection-v1", "status": "ELIGIBLE",
                "features": {
                    "numeric": {"type": "NUMERIC", "status": "PRESENT", "value": index / 10},
                    "category": {"type": "CATEGORICAL", "status": "PRESENT",
                                  "value": test_category if test_category and index >= count * 0.7
                                  else ("A" if index % 2 else "B")},
                    "flag": {"type": "BOOLEAN", "status": "PRESENT", "value": bool(index % 2)},
                },
                "outcome": {"status": "AVAILABLE", "forwardReturn": index / 100},
            })
        return ValidatedResearchDataset(
            manifest={"datasetFingerprint": "dataset", "datasetDefinitionVersion": "research-dataset-v1",
                      "population": {"marketIds": [MARKET_ID], "outcomeDefinitionVersion": "market-outcome-v1"},
                      "featureSchema": {"version": "feature-projection-v1", "definitions": definitions}},
            rows=tuple(rows), validation=ValidationResult((), {}),
        )


if __name__ == "__main__":
    unittest.main()

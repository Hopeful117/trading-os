import json
import tempfile
import unittest
from pathlib import Path

from research_dataset.benchmark import (
    BenchmarkInputError,
    _deterministic_direction,
    _read_predictions,
    _score,
    _select_samples,
)


MARKET_ID = "11111111-1111-1111-1111-111111111111"


def sample(sample_id: str, alignment: str, forward_return: str) -> dict:
    return {
        "sampleId": sample_id,
        "marketId": MARKET_ID,
        "evaluationCutoff": "2026-01-01T10:00:00Z",
        "horizon": "PT1H",
        "status": "ELIGIBLE",
        "features": {"trend.alignment": {"value": alignment}},
        "outcome": {"status": "AVAILABLE", "forwardReturn": forward_return},
    }


class BenchmarkTest(unittest.TestCase):
    def test_ai_predictions_must_not_use_future_context(self):
        rows = [sample("one", "UPTREND", "0.01")]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "predictions.jsonl"
            path.write_text(json.dumps({
                "sampleId": "one",
                "direction": "LONG",
                "contextCutoff": "2026-01-01T10:01:00Z",
                "evidenceCutoffs": [],
            }) + "\n", encoding="utf-8")
            with self.assertRaises(BenchmarkInputError):
                _read_predictions(path, rows)

    def test_prediction_file_must_cover_exact_population(self):
        rows = [sample("one", "UPTREND", "0.01"), sample("two", "DOWNTREND", "-0.01")]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "predictions.jsonl"
            path.write_text(json.dumps({
                "sampleId": "one",
                "direction": "LONG",
                "contextCutoff": "2026-01-01T10:00:00Z",
                "evidenceCutoffs": ["2026-01-01T09:00:00Z"],
            }) + "\n", encoding="utf-8")
            with self.assertRaises(BenchmarkInputError):
                _read_predictions(path, rows)

    def test_prediction_rows_must_be_objects(self):
        rows = [sample("one", "UPTREND", "0.01")]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "predictions.jsonl"
            path.write_text("[]\n", encoding="utf-8")
            with self.assertRaises(BenchmarkInputError):
                _read_predictions(path, rows)

    def test_score_reports_directional_accuracy_and_gross_proxy(self):
        rows = [sample("one", "UPTREND", "0.01"), sample("two", "DOWNTREND", "-0.02")]
        result = _score(rows, lambda row: "LONG" if row["sampleId"] == "one" else "SHORT")
        self.assertEqual(result["directionalAccuracy"], 1.0)
        self.assertEqual(result["nonNeutralCoverage"], 1.0)
        self.assertAlmostEqual(result["meanGrossDirectionalReturn"], 0.015)

    def test_deterministic_direction_uses_java_alignment_values(self):
        self.assertEqual(_deterministic_direction(sample("one", "ALIGNED_UP", "0.01")), "LONG")
        self.assertEqual(_deterministic_direction(sample("one", "PULLBACK_WITHIN_DOWN_BIAS", "0.01")), "SHORT")
        self.assertEqual(_deterministic_direction(sample("one", "CONFLICTING", "0.01")), "NONE")

    def test_benchmark_rejects_samples_without_source_availability(self):
        with self.assertRaises(BenchmarkInputError):
            _select_samples([sample("one", "ALIGNED_UP", "0.01")], MARKET_ID)

    def test_benchmark_accepts_source_availability_at_or_before_cutoff(self):
        row = sample("one", "ALIGNED_UP", "0.01")
        row["featureEvidenceKnownAt"] = "2026-01-01T09:59:59Z"
        self.assertEqual(len(_select_samples([row], MARKET_ID)), 1)


if __name__ == "__main__":
    unittest.main()

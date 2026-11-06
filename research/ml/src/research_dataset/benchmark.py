from __future__ import annotations

import hashlib
import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .dataset import load_validated_paths

SUPPORTED_HORIZONS = {"PT1H", "PT4H", "PT24H"}
VALID_DIRECTIONS = {"LONG", "SHORT", "NONE"}
BENCHMARK_VERSION = "historical-ai-benchmark-v1"


class BenchmarkInputError(ValueError):
    pass


def run_benchmark(
    manifest: str | Path,
    samples: str | Path,
    ai_predictions: str | Path,
    market_id: str,
    output_dir: str | Path,
) -> dict[str, Any]:
    dataset = load_validated_paths(manifest, samples)
    selected = _select_samples(dataset.rows, market_id)
    predictions = _read_predictions(ai_predictions, selected)
    results = {
        "deterministic": _score(selected, _deterministic_direction),
        "ai": _score(selected, lambda row: predictions[row["sampleId"]]["direction"]),
    }
    output = Path(output_dir)
    output.mkdir(parents=True, exist_ok=True)
    benchmark = {
        "benchmarkVersion": BENCHMARK_VERSION,
        "datasetFingerprint": dataset.manifest["datasetFingerprint"],
        "marketId": market_id,
        "sampleCount": len(selected),
        "horizons": sorted({row["horizon"] for row in selected}),
        "predictionInputSha256": _sha256(Path(ai_predictions).read_bytes()),
        "results": results,
        "limitations": [
            "gross directional return proxy only",
            "no fees, funding, slippage, execution or tradeability claim",
            "AI context provenance is accepted only when every declared cutoff is not after evaluationCutoff",
        ],
    }
    (output / "benchmark-manifest.json").write_text(
        json.dumps(benchmark, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    return benchmark


def _select_samples(rows: list[dict[str, Any]], market_id: str) -> list[dict[str, Any]]:
    selected = [
        row for row in rows
        if row.get("marketId") == market_id
        and row.get("status") == "ELIGIBLE"
        and (row.get("outcome") or {}).get("status") == "AVAILABLE"
        and row.get("horizon") in SUPPORTED_HORIZONS
    ]
    if not selected:
        raise BenchmarkInputError("no eligible BTC/USD samples for the requested market id")
    for row in selected:
        known_at = row.get("featureEvidenceKnownAt")
        if known_at is None:
            raise BenchmarkInputError(
                f"sample lacks source-level feature availability: {row['sampleId']}"
            )
        if _parse_time(known_at) > _parse_time(row["evaluationCutoff"]):
            raise BenchmarkInputError(
                f"feature evidence is after evaluation cutoff: {row['sampleId']}"
            )
    return sorted(selected, key=lambda row: (row["evaluationCutoff"], row["horizon"], row["sampleId"]))


def _read_predictions(path: str | Path, samples: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    expected = {row["sampleId"]: row for row in samples}
    result: dict[str, dict[str, Any]] = {}
    for line_number, raw in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip():
            raise BenchmarkInputError(f"blank prediction line: {line_number}")
        try:
            prediction = json.loads(raw)
        except json.JSONDecodeError as error:
            raise BenchmarkInputError(f"invalid prediction JSON at line {line_number}") from error
        if not isinstance(prediction, dict):
            raise BenchmarkInputError(f"prediction must be an object at line {line_number}")
        sample_id = prediction.get("sampleId")
        if sample_id not in expected:
            raise BenchmarkInputError(f"prediction references unknown sample: {sample_id}")
        if sample_id in result:
            raise BenchmarkInputError(f"duplicate prediction: {sample_id}")
        direction = prediction.get("direction")
        if direction not in VALID_DIRECTIONS:
            raise BenchmarkInputError(f"invalid prediction direction at line {line_number}")
        cutoff = _parse_time(expected[sample_id]["evaluationCutoff"])
        context_cutoff = _parse_time(prediction.get("contextCutoff"))
        if context_cutoff > cutoff:
            raise BenchmarkInputError(f"prediction context is after evaluation cutoff: {sample_id}")
        evidence_cutoffs = prediction.get("evidenceCutoffs", [])
        if not isinstance(evidence_cutoffs, list):
            raise BenchmarkInputError(f"evidenceCutoffs must be a list: {sample_id}")
        if any(_parse_time(value) > cutoff for value in evidence_cutoffs):
            raise BenchmarkInputError(f"prediction evidence is after evaluation cutoff: {sample_id}")
        result[sample_id] = prediction
    if set(result) != set(expected):
        missing = sorted(set(expected) - set(result))
        raise BenchmarkInputError(f"missing predictions: {missing[:3]}")
    return result


def _score(rows: list[dict[str, Any]], direction_fn: Any) -> dict[str, Any]:
    correct = 0
    non_neutral = 0
    gross_returns: list[float] = []
    for row in rows:
        direction = direction_fn(row)
        forward_return = float(row["outcome"]["forwardReturn"])
        expected = "LONG" if forward_return > 0 else "SHORT" if forward_return < 0 else "NONE"
        correct += direction == expected
        non_neutral += direction != "NONE"
        gross_returns.append(
            forward_return if direction == "LONG" else -forward_return if direction == "SHORT" else 0.0
        )
    return {
        "sampleCount": len(rows),
        "directionalAccuracy": correct / len(rows),
        "nonNeutralCoverage": non_neutral / len(rows),
        "meanGrossDirectionalReturn": sum(gross_returns) / len(gross_returns),
        "directionCounts": dict(Counter(direction_fn(row) for row in rows)),
    }


def _deterministic_direction(row: dict[str, Any]) -> str:
    alignment = (row.get("features", {}).get("trend.alignment") or {}).get("value")
    return {
        "ALIGNED_UP": "LONG",
        "PULLBACK_WITHIN_UP_BIAS": "LONG",
        "ALIGNED_DOWN": "SHORT",
        "PULLBACK_WITHIN_DOWN_BIAS": "SHORT",
    }.get(alignment, "NONE")


def _parse_time(value: Any) -> datetime:
    if not isinstance(value, str):
        raise BenchmarkInputError("cutoffs must be ISO-8601 strings")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise BenchmarkInputError(f"invalid cutoff: {value}") from error
    if parsed.tzinfo is None:
        raise BenchmarkInputError("cutoffs must include a timezone")
    return parsed.astimezone(timezone.utc)


def _sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()

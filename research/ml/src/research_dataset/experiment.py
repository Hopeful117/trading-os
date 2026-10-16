from __future__ import annotations

import hashlib
import importlib.metadata
import json
import platform
import sys
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path
from typing import Any
from uuid import UUID

from .dataset import ValidatedResearchDataset, load_validated_paths
from .validator import SUPPORTED_FEATURE_VERSIONS, SUPPORTED_OUTCOME_VERSIONS

EXPERIMENT_DEFINITION_VERSION = "research-ml-baseline-v1"
TARGET = "forwardReturn"
MARKET_LABEL = "BTC/USD"
HORIZON = "PT4H"
PURGE_POLICY = "HORIZON_OVERLAP"
READINESS_MIN_DAYS = 90
READINESS_MIN_ELIGIBLE = 5_000
READINESS_MIN_WINDOWS = 500
READINESS_MIN_TEST_WINDOWS = 100
MAX_COVERAGE_GAP_HOURS = 0.25


@dataclass(frozen=True)
class ReadinessPolicy:
    min_coverage_days: int = READINESS_MIN_DAYS
    min_eligible_samples: int = READINESS_MIN_ELIGIBLE
    min_non_overlapping_windows: int = READINESS_MIN_WINDOWS
    min_test_windows: int = READINESS_MIN_TEST_WINDOWS
    max_coverage_gap_hours: float = MAX_COVERAGE_GAP_HOURS


@dataclass(frozen=True)
class Readiness:
    ready: bool
    reasons: tuple[str, ...]
    facts: dict[str, Any]


class DataReadinessBlocked(RuntimeError):
    def __init__(self, readiness: Readiness):
        super().__init__("experiment blocked by data readiness")
        self.readiness = readiness


def run_experiment(
    dataset: ValidatedResearchDataset,
    market: str = MARKET_LABEL,
    horizon: str = HORIZON,
    output_dir: str | Path = "research-output",
    readiness_policy: ReadinessPolicy = ReadinessPolicy(),
    market_id: str | None = None,
) -> dict[str, Any]:
    output = Path(output_dir)
    _prepare_output(output)
    if not dataset.validation.valid:
        raise ValueError("experiment requires a validated dataset")
    if dataset.manifest.get("featureSchema", {}).get("version") not in SUPPORTED_FEATURE_VERSIONS:
        raise ValueError("unsupported feature projection version")
    if dataset.manifest.get("population", {}).get("outcomeDefinitionVersion") not in SUPPORTED_OUTCOME_VERSIONS:
        raise ValueError("unsupported outcome definition version")
    if horizon != HORIZON:
        raise ValueError(f"0109B requires horizon {HORIZON}")
    rows, counts, selected_market = _select_rows(dataset, market, horizon, market_id)
    readiness = _readiness(rows, counts, readiness_policy)
    manifest = _experiment_manifest(dataset, market, horizon, selected_market, readiness, counts, readiness_policy)
    if not readiness.ready:
        manifest["frameworkReady"] = True
        manifest["dataReady"] = False
        manifest["conclusion"] = "BLOCKED_BY_DATA_READINESS"
        _write_json(output / "experiment-manifest.json", manifest)
        return {"status": "BLOCKED_BY_DATA_READINESS", "manifest": manifest, "readiness": readiness}

    import numpy as np
    import pandas as pd
    from sklearn.linear_model import Ridge
    from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score

    train_rows, test_rows, purge_count = _split(rows)

    feature_types = _feature_types(dataset.feature_definitions)
    train_frame, train_target = _frame(train_rows, feature_types, pd, np)
    test_frame, test_target = _frame(test_rows, feature_types, pd, np)
    model = _build_pipeline(feature_types, Ridge)
    model.fit(train_frame, train_target)
    predictions = {
        "ZERO": np.zeros(len(test_target), dtype=float),
        "TRAIN_MEAN": np.full(len(test_target), float(np.mean(train_target)), dtype=float),
        "RIDGE": model.predict(test_frame),
    }
    metrics = {name: _metrics(test_target, values, np, mean_absolute_error,
                              mean_squared_error, r2_score) for name, values in predictions.items()}
    metrics["improvements"] = _improvements(metrics)
    metrics["temporalStability"] = _temporal_stability(test_target, predictions["ZERO"], predictions["RIDGE"], np)
    metrics["residualDiagnostics"] = _residual_diagnostics(test_target, predictions["RIDGE"], np)
    metrics["trainSamples"] = len(train_rows)
    metrics["testSamples"] = len(test_rows)
    metrics["purgedSamples"] = purge_count
    metrics["conclusion"] = _conclusion(metrics, readiness)
    manifest.update({"trainSamples": len(train_rows), "testSamples": len(test_rows), "purgedSamples": purge_count,
                     "frameworkReady": True, "dataReady": True,
                     "trainStart": train_rows[0]["evaluationCutoff"], "trainEnd": train_rows[-1]["evaluationCutoff"],
                     "testStart": test_rows[0]["evaluationCutoff"], "testEnd": test_rows[-1]["evaluationCutoff"],
                     "conclusion": metrics["conclusion"], "model": {"type": "Ridge", "alpha": 1.0},
                     "primaryMetric": "MAE",
                     "baselines": {"ZERO": "predict exact zero", "TRAIN_MEAN": "mean of purged train targets"},
                     "baselineMetrics": {name: metrics[name] for name in ("ZERO", "TRAIN_MEAN")},
                     "preprocessing": {"numeric": "median_imputation_add_indicator_standard_scaler",
                                       "categorical": "constant_missing_one_hot_handle_unknown",
                                       "boolean": "median_imputation_add_indicator"}})
    _write_json(output / "experiment-manifest.json", manifest)
    _write_json(output / "metrics.json", metrics)
    with (output / "predictions.jsonl").open("w", encoding="utf-8") as handle:
        for row, actual, zero, mean, ridge in zip(test_rows, test_target, predictions["ZERO"],
                                                  predictions["TRAIN_MEAN"], predictions["RIDGE"]):
            handle.write(json.dumps({"sampleId": row["sampleId"], "evaluationCutoff": row["evaluationCutoff"],
                                     "actualForwardReturn": _json_number(actual), "zeroPrediction": _json_number(zero),
                                     "trainMeanPrediction": _json_number(mean), "ridgePrediction": _json_number(ridge)},
                                    sort_keys=True, separators=(",", ":")) + "\n")
    return {"status": "COMPLETED", "manifest": manifest, "metrics": metrics}


def run_from_paths(manifest: str | Path, samples: str | Path, market: str, horizon: str,
                   output_dir: str | Path, market_id: str | None = None) -> dict[str, Any]:
    _prepare_output(Path(output_dir))
    return run_experiment(load_validated_paths(manifest, samples), market, horizon, output_dir,
                          market_id=market_id)


def readiness_population(dataset: ValidatedResearchDataset, horizon: str = HORIZON,
                         market_id: str | None = None) -> tuple[list[dict[str, Any]], dict[str, int]]:
    """Return the exact validated population shape used by the readiness gate."""
    rows = list(dataset.rows)
    if market_id is not None:
        rows = [row for row in rows if row.get("marketId") == market_id]
    counts = {"validatedRows": len(dataset.rows), "marketRows": len(rows)}
    rows = [row for row in rows if row.get("horizon") == horizon]
    counts["horizonRows"] = len(rows)
    rows = [row for row in rows if row.get("status") == "ELIGIBLE"]
    counts["eligibleRows"] = len(rows)
    rows = [row for row in rows if (row.get("outcome") or {}).get("status") == "AVAILABLE"]
    counts["availableOutcomeRows"] = len(rows)
    rows = [row for row in rows if row.get("featureProjectionVersion") == "feature-projection-v1"]
    counts["experimentRows"] = len(rows)
    return rows, counts


def _select_rows(dataset: ValidatedResearchDataset, market: str, horizon: str,
                 market_id: str | None) -> tuple[list[dict[str, Any]], dict[str, int], str]:
    if market != MARKET_LABEL:
        raise ValueError("0109B market is fixed to BTC/USD")
    if market_id is None:
        raise ValueError("--market-id is required because the 0109A bundle has no market symbols")
    try:
        selected_market = str(UUID(market_id))
    except ValueError:
        raise ValueError("market-id must be a market UUID") from None
    declared_markets = dataset.manifest.get("population", {}).get("marketIds", [])
    if not declared_markets or selected_market not in declared_markets:
        raise ValueError("market-id must be the declared BTC/USD market identity")
    rows, counts = readiness_population(dataset, horizon=horizon, market_id=selected_market)
    return rows, counts, selected_market


def _build_pipeline(feature_types: dict[str, str], ridge_type: Any) -> Any:
    from sklearn.compose import ColumnTransformer
    from sklearn.impute import SimpleImputer
    from sklearn.pipeline import Pipeline
    from sklearn.preprocessing import OneHotEncoder, StandardScaler

    numeric = [name for name, kind in feature_types.items() if kind == "NUMERIC"]
    categorical = [name for name, kind in feature_types.items() if kind == "CATEGORICAL"]
    boolean = [name for name, kind in feature_types.items() if kind == "BOOLEAN"]
    numeric_pipeline = Pipeline([("imputer", SimpleImputer(strategy="median", add_indicator=True)),
                                 ("scaler", StandardScaler())])
    categorical_pipeline = Pipeline([("imputer", SimpleImputer(strategy="constant", fill_value="__MISSING__")),
                                      ("encoder", OneHotEncoder(handle_unknown="ignore"))])
    boolean_pipeline = Pipeline([("imputer", SimpleImputer(strategy="median", add_indicator=True))])
    preprocessor = ColumnTransformer([("numeric", numeric_pipeline, numeric),
                                      ("categorical", categorical_pipeline, categorical),
                                      ("boolean", boolean_pipeline, boolean)])
    return Pipeline([("preprocessor", preprocessor), ("ridge", ridge_type(alpha=1.0))])


def _readiness(rows: list[dict[str, Any]], counts: dict[str, int], policy: ReadinessPolicy) -> Readiness:
    reasons: list[str] = []
    cutoffs = sorted({_time(row["evaluationCutoff"]) for row in rows})
    coverage_days = (cutoffs[-1] - cutoffs[0]).total_seconds() / 86400 if len(cutoffs) > 1 else 0.0
    windows = _non_overlapping_windows(cutoffs)
    train_rows, test_rows, purge_count = _split(rows) if len(rows) >= 2 else ([], [], 0)
    test_windows = _non_overlapping_windows(sorted({_time(row["evaluationCutoff"]) for row in test_rows}))
    max_gap_hours = max(
        ((right - left).total_seconds() / 3600 for left, right in zip(cutoffs, cutoffs[1:])),
        default=0.0,
    )
    facts = {**counts, "strictObservedCoverageDays": coverage_days,
             "maxEvaluationCutoffGapHours": max_gap_hours,
             "approxNonOverlapping4hWindows": windows, "approxTest4hWindows": test_windows,
             "trainCandidates": max(0, len(rows) - len(test_rows)), "purgedSamples": purge_count,
             "trainSamples": len(train_rows), "testSamples": len(test_rows),
             "trainStart": train_rows[0]["evaluationCutoff"] if train_rows else None,
             "trainEnd": train_rows[-1]["evaluationCutoff"] if train_rows else None,
             "testStart": test_rows[0]["evaluationCutoff"] if test_rows else None,
             "testEnd": test_rows[-1]["evaluationCutoff"] if test_rows else None}
    if len(rows) < policy.min_eligible_samples:
        reasons.append("INSUFFICIENT_ELIGIBLE_SAMPLES")
    if coverage_days < policy.min_coverage_days:
        reasons.append("INSUFFICIENT_STRICT_OBSERVED_COVERAGE")
    if windows < policy.min_non_overlapping_windows:
        reasons.append("INSUFFICIENT_NON_OVERLAPPING_4H_WINDOWS")
    if test_windows < policy.min_test_windows:
        reasons.append("INSUFFICIENT_NON_OVERLAPPING_TEST_WINDOWS")
    if max_gap_hours > policy.max_coverage_gap_hours:
        reasons.append("GAPPED_STRICT_OBSERVED_COVERAGE")
    if not train_rows or not test_rows:
        reasons.append("PURGE_LEFT_INSUFFICIENT_SPLIT")
    if not rows:
        reasons.append("NO_ELIGIBLE_PT4H_AVAILABLE_ROWS")
    return Readiness(not reasons, tuple(reasons), facts)


def _non_overlapping_windows(cutoffs: list[datetime]) -> int:
    count = 0
    last: datetime | None = None
    for cutoff in cutoffs:
        if last is None or cutoff >= last + timedelta(hours=4):
            count += 1
            last = cutoff
    return count


def _split(rows: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]], int]:
    ordered = sorted(rows, key=_row_sort_key)
    if len(ordered) < 2:
        return [], [], 0
    split_index = max(1, min(len(ordered) - 1, int(len(ordered) * 0.7)))
    test_start = _time(ordered[split_index]["evaluationCutoff"])
    train_candidates = ordered[:split_index]
    test_rows = ordered[split_index:]
    train_rows = [row for row in train_candidates if _time(row["evaluationCutoff"]) + timedelta(hours=4) <= test_start]
    return train_rows, test_rows, len(train_candidates) - len(train_rows)


def _feature_types(definitions: Any) -> dict[str, str]:
    result: dict[str, str] = {}
    for definition in definitions:
        name = definition["name"]
        result[name] = definition["dataType"]
        result[f"{name}__missingness"] = "CATEGORICAL"
    return result


def _frame(rows: list[dict[str, Any]], feature_types: dict[str, str], pd: Any, np: Any) -> tuple[Any, Any]:
    values: list[dict[str, Any]] = []
    targets: list[float] = []
    for row in rows:
        projected: dict[str, Any] = {}
        for name, kind in feature_types.items():
            if name.endswith("__missingness"):
                source_name = name.removesuffix("__missingness")
                projected[name] = row["features"][source_name]["status"]
                continue
            feature = row["features"][name]
            if feature["status"] != "PRESENT":
                projected[name] = np.nan
            elif kind == "BOOLEAN":
                projected[name] = 1.0 if feature["value"] else 0.0
            elif kind == "NUMERIC":
                projected[name] = float(feature["value"])
            else:
                projected[name] = feature["value"]
        values.append(projected)
        targets.append(float(row["outcome"][TARGET]))
    return pd.DataFrame(values, columns=list(feature_types)), np.asarray(targets, dtype=float)


def _metrics(actual: Any, predicted: Any, np: Any, mae: Any, mse: Any, r2: Any) -> dict[str, Any]:
    result = {"mae": float(mae(actual, predicted)), "rmse": float(np.sqrt(mse(actual, predicted)))}
    result["constantPrediction"] = bool(np.all(predicted == predicted[0]))
    result["r2"] = _defined_metric(lambda: float(r2(actual, predicted)), actual, predicted, np)
    result["pearson"] = _correlation(actual, predicted, np, "pearson")
    result["spearman"] = _correlation(actual, predicted, np, "spearman")
    result["directionalAccuracy"] = float(np.mean((actual >= 0) == (predicted >= 0)))
    return result


def _temporal_stability(actual: Any, zero: Any, ridge: Any, np: Any) -> dict[str, Any]:
    if len(actual) < 4:
        return {"available": False, "reason": "TOO_FEW_TEST_ROWS"}
    midpoint = len(actual) // 2
    first = _metrics(actual[:midpoint], ridge[:midpoint], np,
                     lambda a, p: float(np.mean(np.abs(a - p))),
                     lambda a, p: float(np.mean((a - p) ** 2)),
                     lambda a, p: 0.0)
    second = _metrics(actual[midpoint:], ridge[midpoint:], np,
                      lambda a, p: float(np.mean(np.abs(a - p))),
                      lambda a, p: float(np.mean((a - p) ** 2)),
                      lambda a, p: 0.0)
    zero_first = float(np.mean(np.abs(actual[:midpoint] - zero[:midpoint])))
    zero_second = float(np.mean(np.abs(actual[midpoint:] - zero[midpoint:])))
    return {"available": True, "ridgeBeatsZeroInBothHalves":
            first["mae"] < zero_first and second["mae"] < zero_second,
            "firstHalfMae": first["mae"], "secondHalfMae": second["mae"]}


def _residual_diagnostics(actual: Any, predicted: Any, np: Any) -> dict[str, Any]:
    residuals = actual - predicted
    if len(residuals) == 0 or not np.isfinite(residuals).all():
        return {"available": False, "reason": "NON_FINITE_RESIDUALS"}
    mean = float(np.mean(residuals))
    standard_deviation = float(np.std(residuals))
    midpoint = len(residuals) // 2
    first_std = float(np.std(residuals[:midpoint]))
    second_std = float(np.std(residuals[midpoint:]))
    scale = max(first_std, second_std)
    floor = min(first_std, second_std)
    stable_scale = floor == 0 and scale == 0 or floor > 0 and scale / floor <= 2
    acceptable_bias = abs(mean) <= 2 * standard_deviation if standard_deviation > 0 else mean == 0
    return {"available": True, "mean": mean, "standardDeviation": standard_deviation,
            "count": len(residuals), "stableScale": stable_scale, "acceptableBias": acceptable_bias,
            "acceptable": stable_scale and acceptable_bias}


def _defined_metric(function: Any, actual: Any, predicted: Any, np: Any) -> Any:
    if len(actual) < 2 or np.all(actual == actual[0]):
        return {"available": False, "reason": "CONSTANT_OR_TOO_FEW_TARGETS"}
    return {"available": True, "value": function()}


def _correlation(actual: Any, predicted: Any, np: Any, method: str) -> dict[str, Any]:
    if len(actual) < 2 or np.all(actual == actual[0]) or np.all(predicted == predicted[0]):
        return {"available": False, "reason": "CONSTANT_OR_TOO_FEW_VALUES"}
    left, right = actual, predicted
    if method == "spearman":
        left, right = _rank(left), _rank(right)
    return {"available": True, "value": float(np.corrcoef(left, right)[0, 1])}


def _rank(values: Any) -> Any:
    import numpy as np
    ranks = np.empty(len(values), dtype=float)
    order = np.argsort(values, kind="mergesort")
    sorted_values = values[order]
    start = 0
    while start < len(values):
        end = start + 1
        while end < len(values) and sorted_values[end] == sorted_values[start]:
            end += 1
        ranks[order[start:end]] = (start + end - 1) / 2.0
        start = end
    return ranks


def _improvements(metrics: dict[str, Any]) -> dict[str, Any]:
    result = {}
    for baseline in ("ZERO", "TRAIN_MEAN"):
        denominator = metrics[baseline]["mae"]
        absolute = denominator - metrics["RIDGE"]["mae"]
        result[baseline] = {"maeAbsolute": absolute,
                            "maePercentage": None if denominator == 0 else absolute / denominator * 100}
    return result


def _conclusion(metrics: dict[str, Any], readiness: Readiness) -> str:
    if not readiness.ready:
        return "BLOCKED_BY_DATA_READINESS"
    ridge = metrics["RIDGE"]
    if ridge["mae"] >= metrics["ZERO"]["mae"]:
        return "NO_MEASURABLE_IMPROVEMENT"
    if ridge["constantPrediction"] or not all(ridge[name]["available"] for name in ("r2", "pearson", "spearman")):
        return "INCONCLUSIVE"
    if not metrics["temporalStability"]["available"] \
            or not metrics["temporalStability"]["ridgeBeatsZeroInBothHalves"] \
            or not metrics["residualDiagnostics"]["available"] \
            or not metrics["residualDiagnostics"]["acceptable"]:
        return "INCONCLUSIVE"
    return "PRELIMINARY_IMPROVEMENT"


def _experiment_manifest(dataset: ValidatedResearchDataset, market: str, horizon: str, selected_market: str,
                         readiness: Readiness, counts: dict[str, int], policy: ReadinessPolicy) -> dict[str, Any]:
    semantic = [f"definition={EXPERIMENT_DEFINITION_VERSION}", f"dataset={dataset.fingerprint}",
                f"market={market}", f"marketId={selected_market}", f"horizon={horizon}", f"target={TARGET}",
                f"featureProjection={dataset.manifest['featureSchema']['version']}",
                f"outcomeDefinition={dataset.manifest['population']['outcomeDefinitionVersion']}",
                f"purge={PURGE_POLICY}", "split=CHRONOLOGICAL_70_30", "model=Ridge", "alpha=1.0",
                "preprocessing=numeric_median_imputer_add_indicator_standard_scaler",
                "preprocessing=categorical_missing_sentinel_one_hot_handle_unknown",
                "preprocessing=boolean_nan_imputer_add_indicator",
                f"minCoverageDays={policy.min_coverage_days}",
                f"minEligibleSamples={policy.min_eligible_samples}",
                f"minNonOverlappingWindows={policy.min_non_overlapping_windows}",
                f"minTestWindows={policy.min_test_windows}",
                f"maxCoverageGapHours={policy.max_coverage_gap_hours}",
                f"trainStart={readiness.facts.get('trainStart')}",
                f"trainEnd={readiness.facts.get('trainEnd')}",
                f"testStart={readiness.facts.get('testStart')}",
                f"testEnd={readiness.facts.get('testEnd')}" ]
    feature_counts = {}
    for definition in dataset.feature_definitions:
        feature_counts[definition["dataType"]] = feature_counts.get(definition["dataType"], 0) + 1
    return {"experimentDefinitionVersion": EXPERIMENT_DEFINITION_VERSION, "datasetFingerprint": dataset.fingerprint,
            "datasetDefinitionVersion": dataset.manifest["datasetDefinitionVersion"],
            "featureProjectionVersion": "feature-projection-v1", "outcomeDefinitionVersion": "market-outcome-v1",
            "market": market, "marketId": selected_market, "horizon": horizon, "target": TARGET,
            "featureCounts": feature_counts,
            "purgePolicy": PURGE_POLICY, "splitPolicy": "CHRONOLOGICAL_70_30", "readiness": readiness.facts,
            "readinessReasons": list(readiness.reasons), "population": counts,
            "readinessPolicy": {"minCoverageDays": policy.min_coverage_days,
                                "minEligibleSamples": policy.min_eligible_samples,
                                "minNonOverlappingWindows": policy.min_non_overlapping_windows,
                                "minTestWindows": policy.min_test_windows,
                                "maxCoverageGapHours": policy.max_coverage_gap_hours},
            "pythonVersion": platform.python_version(), "dependencyVersions": _dependency_versions(),
            "frameworkReady": True, "dataReady": readiness.ready,
            "experimentFingerprint": _fingerprint(semantic)}


def _fingerprint(material: list[str]) -> str:
    return hashlib.sha256("\x1f".join(sorted(material)).encode("utf-8")).hexdigest()


def _dependency_versions() -> dict[str, str]:
    versions = {}
    for name in ("numpy", "pandas", "scikit-learn"):
        try:
            versions[name] = importlib.metadata.version(name)
        except importlib.metadata.PackageNotFoundError:
            versions[name] = "unavailable"
    return versions


def _row_sort_key(row: dict[str, Any]) -> tuple[Any, ...]:
    return (_time(row["evaluationCutoff"]), row["marketId"], row["horizon"], row["sampleId"])


def _time(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    return parsed.astimezone(timezone.utc)


def _json_number(value: Any) -> float:
    number = float(value)
    if number != number or number in (float("inf"), float("-inf")):
        raise ValueError("non-finite experiment value")
    return number


def _write_json(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n", encoding="utf-8")


def _prepare_output(output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    for stale in ("experiment-manifest.json", "metrics.json", "predictions.jsonl"):
        (output / stale).unlink(missing_ok=True)

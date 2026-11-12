from __future__ import annotations

import hashlib
import json
import math
import statistics
from datetime import datetime
from dataclasses import dataclass
from pathlib import Path
from typing import Any


FEATURES = (
    "ohlc.normalized_atr",
    "ohlc.realized_volatility",
    "ohlc.volatility_compression_ratio",
    "ohlc.directional_efficiency",
)
PARTITIONS = ("TRAIN", "VALIDATION", "TEST")


class DukascopyExperimentBlocked(RuntimeError):
    pass


@dataclass(frozen=True)
class DukascopyExperimentConfig:
    experiment: str
    version: str
    partition_scenario: str
    k_values: tuple[int, ...] | None = None
    seeds: tuple[int, ...] | None = None
    selection_thresholds: dict[str, float] | None = None
    uncertainty_method: str | None = None
    block_length: int | None = None
    n_init: int | None = None
    algorithm: str | None = None
    max_iter: int | None = None
    tolerance: float | None = None
    dependency_versions: dict[str, str] | None = None

    def material(self) -> list[str]:
        return [
            self.experiment,
            self.version,
            self.partition_scenario,
            f"k={self.k_values}",
            f"seeds={self.seeds}",
            f"thresholds={json.dumps(self.selection_thresholds, sort_keys=True)}",
            f"uncertainty={self.uncertainty_method}",
            f"blockLength={self.block_length}",
            f"nInit={self.n_init}",
            f"algorithm={self.algorithm}",
            f"maxIter={self.max_iter}",
            f"tolerance={self.tolerance}",
            f"dependencies={json.dumps(self.dependency_versions, sort_keys=True)}",
        ]

    @property
    def fingerprint(self) -> str:
        return _fingerprint(self.material())

    def missing_scientific_configuration(self) -> list[str]:
        missing = []
        if not self.k_values:
            missing.append("k_values")
        if not self.seeds:
            missing.append("seeds")
        if not self.selection_thresholds:
            missing.append("selection_thresholds")
        if not self.uncertainty_method:
            missing.append("uncertainty_method")
        if not self.block_length or self.block_length < 1:
            missing.append("block_length")
        if not self.n_init or self.n_init < 1:
            missing.append("n_init")
        if not self.algorithm:
            missing.append("algorithm")
        if not self.max_iter or self.max_iter < 1:
            missing.append("max_iter")
        if self.tolerance is None or self.tolerance <= 0:
            missing.append("tolerance")
        if not self.dependency_versions:
            missing.append("dependency_versions")
        if "minValidationSilhouette" not in (self.selection_thresholds or {}):
            missing.append("minValidationSilhouette")
        return missing


def load_bundle(bundle: str | Path) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    root = Path(bundle)
    manifest = json.loads((root / "dataset-manifest.json").read_text(encoding="utf-8"))
    rows = [json.loads(line) for line in (root / "observations.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
    validate_bundle_shape(manifest, rows)
    _validate_source_identity(root, manifest)
    return manifest, rows


def validate_bundle_shape(manifest: dict[str, Any], rows: list[dict[str, Any]]) -> None:
    if manifest.get("experimentSchema") != "ML01_DUKASCOPY_REGIME_EVALUATION_V1":
        raise DukascopyExperimentBlocked("unsupported Dukascopy ML-01 experiment schema")
    if tuple(manifest.get("features", ())) != FEATURES:
        raise DukascopyExperimentBlocked("feature order does not match the Java ML-01 contract")
    if set(manifest.get("partitions", ())) != set(PARTITIONS):
        raise DukascopyExperimentBlocked("bundle must declare TRAIN, VALIDATION and TEST")
    required_manifest = ("datasetFingerprint", "sourceConfigurationFingerprint", "configurationFingerprint",
                         "partitionFingerprint", "observationsFingerprint", "partitionScenario",
                         "effectivePartitionCounts")
    if any(not manifest.get(name) for name in required_manifest):
        raise DukascopyExperimentBlocked("bundle manifest is missing scientific identity metadata")
    if not manifest.get("sourceDatasetFingerprint") and not manifest.get("sourceDatasetFingerprints"):
        raise DukascopyExperimentBlocked("bundle manifest is missing source dataset identity")
    if manifest.get("threeWaySplitRequired") is not True or manifest.get("pooledModel") is not True:
        raise DukascopyExperimentBlocked("bundle is not an authorized pooled three-way experiment")
    if manifest.get("rowCount") != len(rows):
        raise DukascopyExperimentBlocked("manifest row count does not match observations")
    seen = {partition: 0 for partition in PARTITIONS}
    previous_cutoff = None
    boundaries = manifest.get("partitionBoundaries", {})
    for row in rows:
        partition = row.get("partition")
        if partition not in PARTITIONS:
            raise DukascopyExperimentBlocked("every row must belong to one of the three partitions")
        cutoff = _parse_timestamp(row.get("cutoff"), "cutoff")
        if previous_cutoff is not None and cutoff < previous_cutoff:
            raise DukascopyExperimentBlocked("observations are not ordered chronologically")
        previous_cutoff = cutoff
        if partition == "TRAIN" and cutoff >= _parse_timestamp(boundaries.get("trainEndExclusive"), "train boundary"):
            raise DukascopyExperimentBlocked("TRAIN row is outside its UTC boundary")
        if partition == "VALIDATION" and not (_parse_timestamp(boundaries.get("trainEndExclusive"), "train boundary")
                                               <= cutoff < _parse_timestamp(boundaries.get("validationEndExclusive"), "validation boundary")):
            raise DukascopyExperimentBlocked("VALIDATION row is outside its UTC boundary")
        if partition == "TEST" and not (_parse_timestamp(boundaries.get("validationEndExclusive"), "validation boundary")
                                        <= cutoff < _parse_timestamp(boundaries.get("testEndExclusive"), "test boundary")):
            raise DukascopyExperimentBlocked("TEST row is outside its UTC boundary")
        if not isinstance(row.get("rowFingerprint"), str) or not row["rowFingerprint"]:
            raise DukascopyExperimentBlocked("every row requires a row fingerprint")
        if row.get("partitionExclusion") is not None:
            _validate_dependency_footprint(row)
            continue
        _validate_dependency_footprint(row)
        seen[partition] += 1
        features = row.get("features")
        if not isinstance(features, dict) or set(features) != set(FEATURES):
            raise DukascopyExperimentBlocked("row feature schema is incomplete")
        for name in FEATURES:
            value = features[name]
            if not isinstance(value, dict) or value.get("status") != "AVAILABLE" or value.get("value") is None:
                raise DukascopyExperimentBlocked(f"row feature is unavailable: {name}")
            if not math.isfinite(float(value["value"])):
                raise DukascopyExperimentBlocked(f"row feature is non-finite: {name}")
    if any(seen[partition] == 0 for partition in PARTITIONS):
        raise DukascopyExperimentBlocked("all three partitions require effective rows")
    for partition in PARTITIONS:
        expected = manifest["effectivePartitionCounts"].get(f"{partition}_EFFECTIVE")
        if expected != seen[partition]:
            raise DukascopyExperimentBlocked(f"effective {partition} count does not match observations")
    identity = [f'{row["rowFingerprint"]}|{row["partition"]}|'
                f'{row.get("partitionExclusion") if row.get("partitionExclusion") is not None else "null"}|'
                f'{_java_list(row.get("crossingHorizons", []))}' for row in rows]
    if _fingerprint(identity) != manifest["observationsFingerprint"]:
        raise DukascopyExperimentBlocked("observations fingerprint mismatch")


def run_experiment(bundle: str | Path, output_dir: str | Path,
                   config: DukascopyExperimentConfig) -> dict[str, Any]:
    manifest, rows = load_bundle(bundle)
    _validate_java_gates(bundle, manifest)
    missing = config.missing_scientific_configuration()
    if missing:
        return _blocked(output_dir, config, "SCIENTIFIC_CONFIGURATION_INCOMPLETE", missing)
    if manifest.get("partitionScenario") != config.partition_scenario:
        return _blocked(output_dir, config, "PARTITION_SCENARIO_MISMATCH", [])
    import numpy as np
    from sklearn.cluster import KMeans
    from sklearn.metrics import adjusted_rand_score, silhouette_score
    from sklearn.preprocessing import StandardScaler

    effective = [row for row in rows if row.get("partitionExclusion") is None]
    train = [row for row in effective if row["partition"] == "TRAIN"]
    validation = [row for row in effective if row["partition"] == "VALIDATION"]
    test = [row for row in effective if row["partition"] == "TEST"]
    if not train or not validation or not test:
        return _blocked(output_dir, config, "THREE_WAY_POPULATION_INCOMPLETE", [])

    scaler = StandardScaler().fit(_matrix(train, np))
    train_matrix = scaler.transform(_matrix(train, np))
    validation_matrix = scaler.transform(_matrix(validation, np))
    candidates = []
    for k in config.k_values or ():
        if k < 2 or k >= len(train):
            raise DukascopyExperimentBlocked("k must be at least 2 and smaller than TRAIN")
        seed_runs = []
        for seed in config.seeds or ():
            model = KMeans(n_clusters=k, random_state=seed, n_init=config.n_init,
                           algorithm=config.algorithm, max_iter=config.max_iter, tol=config.tolerance)
            train_labels = model.fit_predict(train_matrix)
            validation_labels = model.predict(validation_matrix)
            seed_runs.append(validation_labels)
            candidate = {
                "k": k,
                "seed": seed,
                "trainInertia": float(model.inertia_),
                "separation": _minimum_center_distance(model.cluster_centers_, np),
                "centroidProfiles": scaler.inverse_transform(model.cluster_centers_).tolist(),
                "trainSilhouette": float(silhouette_score(train_matrix, train_labels))
                if len(set(train_labels)) > 1 else None,
                "validationSilhouette": float(silhouette_score(validation_matrix, validation_labels))
                if len(set(validation_labels)) > 1 else None,
                "clusterCounts": {str(label): int(sum(train_labels == label)) for label in sorted(set(train_labels))},
                "validationClusterCounts": {str(label): int(sum(validation_labels == label))
                                             for label in sorted(set(validation_labels))},
                "validationBalance": _balance(validation_labels),
                "temporalPersistence": _persistence(validation_labels),
                "chronologicalBlocks": _label_blocks(validation_labels, config.block_length),
            }
            candidates.append(candidate)
        stability = _seed_stability(seed_runs, adjusted_rand_score)
        for candidate in candidates[-len(seed_runs):]:
            candidate["seedStability"] = stability
    selected = max(candidates, key=lambda value: (
        value["validationSilhouette"] if value["validationSilhouette"] is not None else float("-inf"),
        value.get("seedStability", float("-inf")),
        value.get("temporalPersistence", float("-inf")),
        value["trainSilhouette"] if value["trainSilhouette"] is not None else float("-inf"),
        -value["k"],
        -value["seed"],
    ))
    validation_score = selected["validationSilhouette"]
    minimum_score = config.selection_thresholds.get("minValidationSilhouette", float("-inf"))
    if validation_score is None or validation_score < minimum_score:
        return _blocked(output_dir, config, "SELECTION_THRESHOLD_NOT_MET", candidates)

    model = KMeans(n_clusters=selected["k"], random_state=selected["seed"], n_init=config.n_init,
                   algorithm=config.algorithm, max_iter=config.max_iter, tol=config.tolerance)
    model.fit(train_matrix)
    scaler_state = {
        "featureOrder": list(FEATURES),
        "mean": [float(value) for value in scaler.mean_],
        "scale": [float(value) for value in scaler.scale_],
        "trainRowFingerprints": [row["rowFingerprint"] for row in train],
    }
    scaler_state["fingerprint"] = _fingerprint([json.dumps(scaler_state, sort_keys=True)])
    model_state = {
        "k": selected["k"],
        "seed": selected["seed"],
        "nInit": config.n_init,
        "algorithm": config.algorithm,
        "maxIter": config.max_iter,
        "tolerance": config.tolerance,
        "dependencyVersions": config.dependency_versions,
        "featureOrder": list(FEATURES),
        "centers": model.cluster_centers_.tolist(),
    }
    model_state["fingerprint"] = _fingerprint([json.dumps(model_state, sort_keys=True)])
    freeze = {"configFingerprint": config.fingerprint,
              "bundleFingerprint": manifest["bundleFingerprint"],
              "partitionFingerprint": manifest["partitionFingerprint"],
              "selected": selected,
              "scaler": scaler_state, "model": model_state,
              "trainRows": len(train), "validationRows": len(validation),
              "testRows": len(test), "testEvaluated": False}
    _write_immutable(output_dir, "selection-freeze.json", freeze)
    if json.loads((Path(output_dir) / "selection-freeze.json").read_text(encoding="utf-8")) != freeze:
        raise DukascopyExperimentBlocked("selection freeze changed before test evaluation")
    test_labels = model.predict(scaler.transform(_matrix(test, np)))
    held_out = _held_out_evaluation(test, test_labels, config)
    result = {"status": "COMPLETED", "configFingerprint": config.fingerprint,
               "selection": selected, "testRows": len(test), "testClusters": {
                   str(label): int(sum(test_labels == label)) for label in sorted(set(test_labels))
               }, "heldOutEvaluation": held_out}
    _write_immutable(output_dir, "experiment-result.json", result)
    return result


def _matrix(rows: list[dict[str, Any]], np: Any) -> Any:
    return np.asarray([[float(row["features"][name]["value"]) for name in FEATURES] for row in rows], dtype=float)


def _minimum_center_distance(centers: Any, np: Any) -> float | None:
    if len(centers) < 2:
        return None
    return float(min(np.linalg.norm(centers[left] - centers[right])
                     for left in range(len(centers)) for right in range(left + 1, len(centers))))


def _balance(labels: Any) -> float:
    counts = [sum(labels == label) for label in sorted(set(labels))]
    return min(counts) / max(counts) if counts and max(counts) else 0.0


def _persistence(labels: Any) -> float:
    return float(sum(labels[index] == labels[index - 1] for index in range(1, len(labels))) / (len(labels) - 1)) \
        if len(labels) > 1 else 1.0


def _label_blocks(labels: Any, block_length: int | None) -> list[dict[str, Any]]:
    if not block_length:
        return []
    return [{"index": index // block_length,
             "counts": {str(label): int(sum(block == label)) for label in sorted(set(block))}}
            for index in range(0, len(labels), block_length)
            for block in [labels[index:index + block_length]]]


def _seed_stability(runs: list[Any], adjusted_rand_score: Any) -> float:
    if len(runs) < 2:
        return 1.0
    scores = [adjusted_rand_score(runs[left], runs[right])
              for left in range(len(runs)) for right in range(left + 1, len(runs))]
    return float(statistics.fmean(scores))


def _parse_timestamp(value: Any, name: str) -> datetime:
    if not isinstance(value, str):
        raise DukascopyExperimentBlocked(f"missing timestamp: {name}")
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise DukascopyExperimentBlocked(f"invalid timestamp: {name}") from error


def _validate_dependency_footprint(row: dict[str, Any]) -> None:
    if row.get("featureLookbackStart") is None or row.get("featureLookbackEnd") is None:
        raise DukascopyExperimentBlocked("row is missing feature dependency timestamps")
    _parse_timestamp(row["featureLookbackStart"], "featureLookbackStart")
    feature_end = _parse_timestamp(row["featureLookbackEnd"], "featureLookbackEnd")
    cutoff = _parse_timestamp(row["cutoff"], "cutoff")
    if feature_end > cutoff:
        raise DukascopyExperimentBlocked("feature dependency ends after cutoff")
    outcomes = row.get("outcomes")
    if not isinstance(outcomes, dict):
        raise DukascopyExperimentBlocked("row is missing outcome provenance")
    for horizon in ("4", "12", "24"):
        outcome = outcomes.get(horizon)
        if not isinstance(outcome, dict):
            raise DukascopyExperimentBlocked(f"row is missing outcome horizon: {horizon}")
        if outcome.get("status") == "AVAILABLE":
            timestamps = outcome.get("futureOpenTimes")
            if not isinstance(timestamps, list) or not timestamps or outcome.get("futureEnd") is None:
                raise DukascopyExperimentBlocked(f"available outcome lacks dependency footprint: {horizon}")
            parsed = [_parse_timestamp(value, f"futureOpenTimes[{horizon}]") for value in timestamps]
            future_end = _parse_timestamp(outcome["futureEnd"], f"futureEnd[{horizon}]")
            if parsed[-1] > future_end or future_end < cutoff:
                raise DukascopyExperimentBlocked(f"inconsistent future dependency: {horizon}")


def _validate_source_identity(root: Path, manifest: dict[str, Any]) -> None:
    source = root / "source-metadata.json"
    if not source.is_file():
        raise DukascopyExperimentBlocked("missing source metadata")
    metadata = json.loads(source.read_text(encoding="utf-8"))
    if manifest.get("sourceDatasetFingerprint") and metadata.get("sourceDatasetFingerprint") != manifest["sourceDatasetFingerprint"]:
        raise DukascopyExperimentBlocked("source dataset fingerprint mismatch")
    if manifest.get("sourceDatasetFingerprints") and sorted(metadata.get("sourceDatasetFingerprints", [])) != sorted(manifest["sourceDatasetFingerprints"]):
        raise DukascopyExperimentBlocked("pooled source dataset fingerprints mismatch")


def _held_out_evaluation(rows: list[dict[str, Any]], labels: Any,
                         config: DukascopyExperimentConfig) -> dict[str, Any]:
    horizons: dict[str, Any] = {}
    for horizon in (4, 12, 24):
        observations = []
        for row, label in zip(rows, labels):
            outcome = row.get("outcomes", {}).get(str(horizon), {})
            if outcome.get("status") != "AVAILABLE":
                continue
            observations.append({"cluster": int(label), "instrument": row.get("instrument"),
                                 "quality": row.get("quality"), "cutoff": row.get("cutoff"),
                                 "return": float(outcome["absoluteFutureNetReturn"]),
                                 "volatility": float(outcome["realizedVolatility"]),
                                 "efficiency": float(outcome["directionalEfficiency"])})
        by_cluster = {}
        for cluster in sorted({value["cluster"] for value in observations}):
            values = [value for value in observations if value["cluster"] == cluster]
            by_cluster[str(cluster)] = _summary(values, config.block_length)
        horizons[str(horizon)] = {"count": len(observations), "clusters": by_cluster,
                                  "instruments": _group_summary(observations, "instrument"),
                                  "quality": _group_summary(observations, "quality"),
                                  "temporal": _temporal_outcomes(observations, config.block_length),
                                  "global": _summary(observations, config.block_length),
                                  "clusterImbalance": _cluster_imbalance(observations)}
    return {"horizons": horizons,
            "instrumentCounts": _counts(rows, "instrument"),
            "qualityCounts": _counts(rows, "quality"),
            "temporalBlocks": _temporal_blocks(rows, config.block_length),
            "uncertainty": {"method": config.uncertainty_method,
                            "blockLength": config.block_length,
                            "multipleComparison": "BONFERRONI_HORIZON_FAMILY",
                            "familySize": 3,
                            "alpha": 0.05 / 3}}


def _summary(values: list[dict[str, Any]], block_length: int | None = None) -> dict[str, Any]:
    if not values:
        return {"count": 0}
    result = {"count": len(values),
            "meanReturn": statistics.fmean(value["return"] for value in values),
            "meanVolatility": statistics.fmean(value["volatility"] for value in values),
            "meanEfficiency": statistics.fmean(value["efficiency"] for value in values),
            "returnStdev": statistics.stdev(value["return"] for value in values) if len(values) > 1 else 0.0}
    if block_length:
        block_means = [statistics.fmean(value["return"] for value in values[index:index + block_length])
                       for index in range(0, len(values), block_length)]
        result["blockCount"] = len(block_means)
        result["blockMeanRange95"] = _percentile_interval(block_means)
    return result


def _group_summary(values: list[dict[str, Any]], key: str) -> dict[str, Any]:
    groups: dict[str, list[dict[str, Any]]] = {}
    for value in values:
        groups.setdefault(str(value.get(key)), []).append(value)
    return {name: _summary(group) for name, group in sorted(groups.items())}


def _cluster_imbalance(values: list[dict[str, Any]]) -> dict[str, Any]:
    counts = _counts(values, "cluster")
    if not counts:
        return {"counts": {}, "maxMinRatio": None}
    minimum = min(counts.values())
    return {"counts": counts, "maxMinRatio": max(counts.values()) / minimum if minimum else None}


def _temporal_outcomes(values: list[dict[str, Any]], block_length: int | None) -> list[dict[str, Any]]:
    if not block_length:
        return []
    ordered = sorted(values, key=lambda value: value.get("cutoff") or "")
    return [{"index": index // block_length, "summary": _summary(ordered[index:index + block_length])}
            for index in range(0, len(ordered), block_length)]


def _percentile_interval(values: list[float]) -> list[float] | None:
    if not values:
        return None
    ordered = sorted(values)
    if len(ordered) == 1:
        return [ordered[0], ordered[0]]
    low = ordered[max(0, round(0.025 * (len(ordered) - 1)))]
    high = ordered[min(len(ordered) - 1, round(0.975 * (len(ordered) - 1)))]
    return [low, high]


def _counts(rows: list[dict[str, Any]], key: str) -> dict[str, int]:
    counts: dict[str, int] = {}
    for row in rows:
        value = str(row.get(key))
        counts[value] = counts.get(value, 0) + 1
    return dict(sorted(counts.items()))


def _temporal_blocks(rows: list[dict[str, Any]], block_length: int | None) -> dict[str, Any]:
    if not block_length:
        return {"status": "BLOCKED"}
    ordered = sorted(row.get("cutoff") for row in rows if row.get("cutoff"))
    return {"status": "DESCRIPTIVE", "blockLength": block_length,
            "blockCount": (len(ordered) + block_length - 1) // block_length,
            "firstCutoff": ordered[0] if ordered else None,
            "lastCutoff": ordered[-1] if ordered else None}


def _validate_java_gates(bundle: str | Path, manifest: dict[str, Any]) -> None:
    root = Path(bundle)
    readiness = _read_json(root / "readiness.json")
    partition = _read_json(root / "partition-manifest.json")
    if readiness.get("status") != "READY_FOR_EXECUTION" or readiness.get("requiresScientificApproval") is not False:
        raise DukascopyExperimentBlocked("Java readiness gate does not authorize execution")
    if partition.get("status") != "READY_FOR_EXECUTION":
        raise DukascopyExperimentBlocked("Java partition gate does not authorize execution")
    if readiness.get("configurationFingerprint") != manifest["configurationFingerprint"]:
        raise DukascopyExperimentBlocked("readiness configuration fingerprint mismatch")
    if readiness.get("bundleFingerprint") != manifest.get("bundleFingerprint"):
        raise DukascopyExperimentBlocked("readiness bundle fingerprint mismatch")
    if partition.get("configurationFingerprint") != manifest["configurationFingerprint"]:
        raise DukascopyExperimentBlocked("partition configuration fingerprint mismatch")
    if partition.get("fingerprint") != manifest["partitionFingerprint"]:
        raise DukascopyExperimentBlocked("partition fingerprint mismatch")


def _java_list(values: list[Any]) -> str:
    return "[" + ", ".join(str(value) for value in values) + "]"


def _read_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        raise DukascopyExperimentBlocked(f"missing Java gate artifact: {path.name}")
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise DukascopyExperimentBlocked(f"invalid Java gate artifact: {path.name}")
    return value


def _blocked(output_dir: str | Path, config: DukascopyExperimentConfig,
             reason: str, details: Any) -> dict[str, Any]:
    result = {"status": "BLOCKED", "reason": reason, "details": details,
              "configFingerprint": config.fingerprint}
    _write(output_dir, "readiness.json", result)
    return result


def _write(output_dir: str | Path, name: str, value: dict[str, Any]) -> None:
    root = Path(output_dir)
    root.mkdir(parents=True, exist_ok=True)
    (root / name).write_text(json.dumps(value, sort_keys=True, separators=(",", ":")) + "\n", encoding="utf-8")


def _write_immutable(output_dir: str | Path, name: str, value: dict[str, Any]) -> None:
    root = Path(output_dir)
    root.mkdir(parents=True, exist_ok=True)
    target = root / name
    serialized = json.dumps(value, sort_keys=True, separators=(",", ":")) + "\n"
    if target.exists():
        if target.read_text(encoding="utf-8") != serialized:
            raise DukascopyExperimentBlocked(f"immutable artifact already exists with different content: {name}")
        return
    target.write_text(serialized, encoding="utf-8")


def _fingerprint(material: list[str]) -> str:
    return hashlib.sha256("\x1f".join(sorted(material)).encode("utf-8")).hexdigest()

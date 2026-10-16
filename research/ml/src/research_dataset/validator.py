from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any
from uuid import UUID

SEPARATOR = "\x1f"
MANIFEST_FIELDS = {
    "exportSchemaVersion", "datasetDefinitionVersion", "population", "featureSchema",
    "outcomeSchema", "sampleCount", "eligibleCount", "excludedCount", "exclusionCounts",
    "datasetFingerprint", "fingerprintMaterial", "samplesSha256",
}
SAMPLE_FIELDS = {
    "sampleId", "marketId", "evaluationCutoff", "horizon", "featureProjectionVersion",
    "evidencePolicy", "featureSnapshotCreatedAt", "status", "exclusionReason", "features",
    "outcome", "featureProvenance", "lineage",
}
LINEAGE_FIELDS = {
    "featureSnapshotFingerprint", "featureIdentityFingerprint", "contextFingerprint",
    "outcomeFingerprint", "sampleFingerprint", "sampleFingerprintMaterial",
}
OUTCOME_FIELDS = {
    "status", "maturityAt", "computedAt", "outcomeDefinitionVersion", "referenceObservationFingerprint",
    "futureEvidenceFingerprint", "referencePrice", "finalPrice", "futureHigh", "futureLow",
    "forwardReturn", "futureHighReturn", "futureLowReturn", "futureRange",
}
POPULATION_FIELDS = {
    "populationPolicyVersion", "marketIds", "startCutoffInclusive", "endCutoffExclusive",
    "featureProjectionVersion", "featureEvidencePolicy", "outcomeDefinitionVersion", "horizons",
    "samplingCadence", "acceptedFeatureMissingness", "selectedOutcomeFingerprints",
}
VALID_OUTCOME_STATUSES = {"AVAILABLE", "PENDING", "INCOMPLETE", "UNAVAILABLE"}
VALID_HORIZON = re.compile(r"^P(?:(?:\d+D)?(?:T(?:(?:\d+H)?(?:\d+M)?(?:\d+(?:\.\d+)?S)?)?)?)$")
VALID_MISSINGNESS = {"PRESENT", "MISSING", "INSUFFICIENT_DATA", "STALE_INPUT", "INVALID_INPUT",
                     "UNAVAILABLE", "UNSUPPORTED", "NOT_APPLICABLE"}
VALID_EXCLUSION_REASONS = {
    "FEATURE_SNAPSHOT_INVALID", "FEATURE_MISSING_REQUIRED_VALUE", "OUTCOME_MISSING", "OUTCOME_NOT_MATURE",
    "OUTCOME_INCOMPLETE", "OUTCOME_UNAVAILABLE", "OUTCOME_VERSION_NOT_SELECTED", "OUTCOME_VERSION_MISMATCH",
    "OUTCOME_IDENTITY_CONFLICT", "VERSION_MISMATCH", "MARKET_MISMATCH", "CUTOFF_MISMATCH",
    "HORIZON_UNSUPPORTED", "DUPLICATE_FEATURE_SNAPSHOT",
}
PROVENANCE_FIELDS = {
    "sourceCapability", "sourceVersion", "algorithmVersion", "ruleVersion", "profileId", "profileVersion",
    "policyId", "policyVersion", "parameterFingerprint", "inputFingerprint", "resultFingerprint",
    "evidenceFingerprint", "normalizationVersions", "sourceArtifactType", "sourceArtifactProducerId",
    "sourceArtifactProducerVersion", "sourceArtifactParametersFingerprint", "sourceArtifactInputFingerprint",
    "sourceArtifactContractVersion",
}
SUPPORTED_DATASET_VERSIONS = {"research-dataset-v1"}
SUPPORTED_FEATURE_VERSIONS = {"feature-projection-v1"}
SUPPORTED_OUTCOME_VERSIONS = {"market-outcome-v1"}


@dataclass(frozen=True)
class ValidationResult:
    diagnostics: tuple[dict[str, Any], ...]
    summary: dict[str, Any]

    @property
    def valid(self) -> bool:
        return not self.diagnostics

    def as_dict(self) -> dict[str, Any]:
        return {"valid": self.valid, "summary": self.summary, "diagnostics": list(self.diagnostics)}


def validate_bundle(bundle: str | Path) -> ValidationResult:
    root = Path(bundle)
    diagnostics: list[dict[str, Any]] = []
    manifest_path = root / "dataset-manifest.json"
    samples_path = root / "samples.jsonl"
    manifest = _read_json(manifest_path, diagnostics, "manifest.invalid_json")
    if not isinstance(manifest, dict):
        return _result(diagnostics)
    _unknown_fields(manifest, MANIFEST_FIELDS, "manifest", diagnostics)
    _validate_manifest_scalars(manifest, diagnostics)
    if manifest.get("exportSchemaVersion") != "research-dataset-bundle-v1":
        _add(diagnostics, "manifest.unsupported_schema", "unsupported export schema version")

    if not samples_path.is_file():
        _add(diagnostics, "samples.missing", "samples.jsonl is missing")
        return _result(diagnostics)
    sample_bytes = samples_path.read_bytes()
    actual_sha = hashlib.sha256(sample_bytes).hexdigest()
    if manifest.get("samplesSha256") != actual_sha:
        _add(diagnostics, "samples.checksum_mismatch", "samples.jsonl checksum does not match manifest")

    material = manifest.get("fingerprintMaterial")
    if not isinstance(material, list) or not all(isinstance(value, str) for value in material):
        _add(diagnostics, "manifest.invalid_fingerprint_material", "fingerprintMaterial must be a list of strings")
    elif manifest.get("datasetFingerprint") != _fingerprint(material):
        _add(diagnostics, "manifest.fingerprint_mismatch", "dataset fingerprint does not match fingerprint material")

    population = manifest.get("population")
    if not isinstance(population, dict):
        _add(diagnostics, "manifest.invalid_population", "population must be an object")
        population = {}
    feature_schema = manifest.get("featureSchema")
    feature_types = _feature_types(feature_schema, diagnostics, population)
    _validate_population(population, diagnostics)
    expected_count = manifest.get("sampleCount")
    rows: list[dict[str, Any]] = []
    try:
        sample_lines = sample_bytes.decode("utf-8").splitlines()
    except UnicodeDecodeError:
        _add(diagnostics, "samples.invalid_encoding", "samples.jsonl must be UTF-8")
        return _result(diagnostics)
    sample_ids: set[Any] = set()
    sample_fingerprints: set[Any] = set()
    sample_identities: set[tuple[Any, Any, Any, Any]] = set()
    for line_number, raw_line in enumerate(sample_lines, 1):
        if not raw_line.strip():
            _add(diagnostics, "samples.blank_line", "blank JSONL lines are not allowed", line_number)
            continue
        try:
            row = json.loads(raw_line, parse_float=Decimal, parse_int=Decimal,
                             parse_constant=_reject_json_constant)
        except (UnicodeDecodeError, json.JSONDecodeError, ValueError):
            _add(diagnostics, "samples.invalid_json", "invalid JSONL row", line_number)
            continue
        if not isinstance(row, dict):
            _add(diagnostics, "samples.invalid_row", "JSONL row must be an object", line_number)
            continue
        rows.append(row)
        sample_id = row.get("sampleId")
        if not isinstance(sample_id, str):
            _add(diagnostics, "samples.invalid_id", "sampleId must be a string", line_number)
        elif sample_id in sample_ids:
            _add(diagnostics, "samples.duplicate_id", "duplicate sampleId", line_number)
        if isinstance(sample_id, str):
            sample_ids.add(sample_id)
        fingerprint = row.get("lineage", {}).get("sampleFingerprint") if isinstance(row.get("lineage"), dict) else None
        if not isinstance(fingerprint, str):
            _add(diagnostics, "samples.invalid_fingerprint", "sample fingerprint must be a string", line_number)
        elif fingerprint in sample_fingerprints:
            _add(diagnostics, "samples.duplicate_fingerprint", "duplicate sample fingerprint", line_number)
        if isinstance(fingerprint, str):
            sample_fingerprints.add(fingerprint)
            if sample_id != _sample_id(fingerprint):
                _add(diagnostics, "samples.sample_id_mismatch", "sampleId does not match sample fingerprint", line_number)
        lineage = row.get("lineage") if isinstance(row.get("lineage"), dict) else {}
        identity = (row.get("marketId"), row.get("evaluationCutoff"), row.get("horizon"),
                    lineage.get("featureIdentityFingerprint"))
        if all(value is not None for value in identity) and identity in sample_identities:
            _add(diagnostics, "samples.duplicate_identity", "duplicate semantic sample identity", line_number)
        if all(value is not None for value in identity):
            sample_identities.add(identity)
        _validate_row(row, line_number, population, feature_types, diagnostics)

    if isinstance(expected_count, int) and expected_count != len(rows):
        _add(diagnostics, "samples.count_mismatch", "sampleCount does not match JSONL row count")
    if isinstance(manifest.get("eligibleCount"), int):
        actual = sum(row.get("status") == "ELIGIBLE" for row in rows)
        if actual != manifest["eligibleCount"]:
            _add(diagnostics, "samples.eligible_count_mismatch", "eligibleCount does not match JSONL")
    if isinstance(manifest.get("excludedCount"), int):
        actual = sum(row.get("status") == "EXCLUDED" for row in rows)
        if actual != manifest["excludedCount"]:
            _add(diagnostics, "samples.excluded_count_mismatch", "excludedCount does not match JSONL")
    actual_exclusions: dict[str, int] = {}
    for row in rows:
        reason = row.get("exclusionReason")
        if row.get("status") == "EXCLUDED" and isinstance(reason, str):
            actual_exclusions[reason] = actual_exclusions.get(reason, 0) + 1
    if manifest.get("exclusionCounts") != actual_exclusions:
        _add(diagnostics, "samples.exclusion_counts_mismatch", "exclusionCounts does not match JSONL")
    if isinstance(material, list) and all(isinstance(value, str) for value in material):
        expected_material = _dataset_material(manifest, population, rows)
        if sorted(material) != sorted(expected_material):
            _add(diagnostics, "manifest.material_mismatch", "dataset fingerprint material does not match bundle semantics")
    return _result(diagnostics, _summary(manifest, rows))


def _validate_row(row: dict[str, Any], line: int, population: dict[str, Any],
                  feature_types: dict[str, Any], diagnostics: list[dict[str, Any]]) -> None:
    _unknown_fields(row, SAMPLE_FIELDS, f"row {line}", diagnostics, line)
    for field in ("sampleId", "marketId", "evaluationCutoff", "horizon", "featureProjectionVersion", "status", "lineage"):
        if field not in row:
            _add(diagnostics, "samples.missing_field", f"missing required field: {field}", line)
    if row.get("status") not in {"ELIGIBLE", "EXCLUDED"}:
        _add(diagnostics, "samples.invalid_status", "sample status is invalid", line)
    if row.get("status") == "ELIGIBLE" and row.get("exclusionReason") is not None:
        _add(diagnostics, "samples.eligible_exclusion", "eligible row must not have an exclusion reason", line)
    if row.get("status") == "EXCLUDED" and row.get("exclusionReason") not in VALID_EXCLUSION_REASONS:
        _add(diagnostics, "samples.invalid_exclusion", "excluded row must have a valid exclusion reason", line)
    if row.get("evidencePolicy") != "STRICT_OBSERVED":
        _add(diagnostics, "samples.non_strict_evidence", "research rows require STRICT_OBSERVED evidence", line)
    if row.get("featureProjectionVersion") not in SUPPORTED_FEATURE_VERSIONS:
        _add(diagnostics, "samples.unsupported_projection", "unsupported feature projection version", line)
    if row.get("featureProjectionVersion") != population.get("featureProjectionVersion"):
        _add(diagnostics, "samples.projection_mismatch", "feature projection differs from manifest", line)
    try:
        cutoff = _parse_time(row["evaluationCutoff"])
        _parse_time(row["featureSnapshotCreatedAt"])
    except (KeyError, TypeError, ValueError):
        _add(diagnostics, "samples.invalid_cutoff", "evaluationCutoff must be an ISO timestamp", line)
        cutoff = None
    market_id = row.get("marketId")
    try:
        UUID(market_id)
    except (AttributeError, TypeError, ValueError):
        _add(diagnostics, "samples.invalid_market", "marketId must be a UUID", line)
    else:
        declared_markets = population.get("marketIds")
        if isinstance(declared_markets, list) and declared_markets and market_id not in declared_markets:
            _add(diagnostics, "samples.market_outside_population", "marketId is outside the declared population", line)
    if cutoff is not None:
        try:
            start = _parse_time(population["startCutoffInclusive"])
            end = _parse_time(population["endCutoffExclusive"])
            if cutoff < start or cutoff >= end:
                _add(diagnostics, "samples.cutoff_outside_population", "evaluationCutoff is outside the declared population", line)
        except (KeyError, TypeError, ValueError):
            pass
    if row.get("evidencePolicy") != population.get("featureEvidencePolicy"):
        _add(diagnostics, "samples.evidence_policy_mismatch", "row evidence policy differs from manifest", line)
    _validate_known_at(row, cutoff, diagnostics, line)
    _validate_selected_outcome(row, population, diagnostics, line)
    if not isinstance(row.get("horizon"), str) or not _valid_duration(row["horizon"]):
        _add(diagnostics, "samples.invalid_horizon", "horizon must be an ISO-8601 duration", line)
    elif row["horizon"] not in _list_value(population.get("horizons")):
        _add(diagnostics, "samples.horizon_mismatch", "row horizon is not declared by the population", line)
    _validate_provenance(row.get("featureProvenance"), diagnostics, line)

    features = row.get("features")
    if not isinstance(features, dict) or set(features) != set(feature_types):
        _add(diagnostics, "features.schema_mismatch", "feature names do not match manifest schema", line)
    else:
        accepted = set(population.get("acceptedFeatureMissingness", []))
        for name, feature in features.items():
            if not isinstance(feature, dict) or set(feature) != {"type", "status", "value"}:
                _add(diagnostics, "features.invalid_value", f"invalid feature projection: {name}", line)
                continue
            status = feature.get("status")
            expected_type = feature_types.get(name)
            if expected_type is not None and feature.get("type") != expected_type:
                _add(diagnostics, "features.type_mismatch", f"feature type differs from schema: {name}", line)
            if status not in {"PRESENT", "MISSING", "INSUFFICIENT_DATA", "STALE_INPUT", "INVALID_INPUT",
                              "UNAVAILABLE", "UNSUPPORTED", "NOT_APPLICABLE"}:
                _add(diagnostics, "features.invalid_status", f"invalid feature missingness: {name}", line)
            if status != "PRESENT" and status not in accepted:
                _add(diagnostics, "features.disallowed_missingness", f"disallowed missingness: {name}", line)
            if status == "PRESENT" and feature.get("value") is None:
                _add(diagnostics, "features.present_without_value", f"present feature has null value: {name}", line)
            if status != "PRESENT" and feature.get("value") is not None:
                _add(diagnostics, "features.missing_with_value", f"missing feature has a value: {name}", line)
            if status == "PRESENT" and feature.get("value") is not None:
                if feature.get("type") == "NUMERIC":
                    _validate_decimal(feature["value"], f"feature {name}", diagnostics, line)
                elif feature.get("type") == "BOOLEAN" and not isinstance(feature["value"], bool):
                    _add(diagnostics, "features.value_type_mismatch", f"boolean feature has a non-boolean value: {name}", line)
                elif feature.get("type") == "CATEGORICAL" and not isinstance(feature["value"], str):
                    _add(diagnostics, "features.value_type_mismatch", f"categorical feature has a non-string value: {name}", line)

    lineage = row.get("lineage")
    if not isinstance(lineage, dict):
        _add(diagnostics, "lineage.invalid", "lineage must be an object", line)
    else:
        _unknown_fields(lineage, LINEAGE_FIELDS, f"row {line} lineage", diagnostics, line)
        for field in ("featureSnapshotFingerprint", "featureIdentityFingerprint", "contextFingerprint",
                      "sampleFingerprint", "sampleFingerprintMaterial"):
            if field not in lineage or (field != "sampleFingerprintMaterial"
                                        and not isinstance(lineage[field], str)) \
                    or (field != "sampleFingerprintMaterial" and not lineage[field]):
                _add(diagnostics, "lineage.missing_field", f"lineage field is invalid: {field}", line)
        if not isinstance(lineage.get("sampleFingerprint"), str) \
                or not re.fullmatch(r"[0-9a-f]{64}", lineage.get("sampleFingerprint", "")):
            _add(diagnostics, "lineage.invalid_fingerprint", "sampleFingerprint must be lowercase SHA-256", line)
        if row.get("status") == "ELIGIBLE" and not isinstance(lineage.get("outcomeFingerprint"), str):
            _add(diagnostics, "lineage.missing_outcome", "eligible row requires an outcome fingerprint", line)
        material = lineage.get("sampleFingerprintMaterial")
        if isinstance(material, list) and all(isinstance(value, str) for value in material):
            if lineage.get("sampleFingerprint") != _fingerprint(material):
                _add(diagnostics, "samples.fingerprint_mismatch", "sample fingerprint does not match material", line)
            expected_material = _sample_material(row)
            if sorted(material) != sorted(expected_material):
                _add(diagnostics, "samples.material_mismatch", "sample fingerprint material does not match row semantics", line)
        else:
            _add(diagnostics, "samples.invalid_sample_material", "sample fingerprint material is invalid", line)

    outcome = row.get("outcome")
    if isinstance(outcome, dict):
        _unknown_fields(outcome, OUTCOME_FIELDS, f"row {line} outcome", diagnostics, line)
        if outcome.get("status") is not None and outcome.get("status") not in VALID_OUTCOME_STATUSES:
            _add(diagnostics, "outcome.invalid_status", "outcome status is invalid", line)
        for field in ("maturityAt", "computedAt"):
            if outcome.get(field) is not None:
                try:
                    _parse_time(outcome[field])
                except (TypeError, ValueError):
                    _add(diagnostics, "outcome.invalid_timestamp", f"invalid outcome timestamp: {field}", line)
        for field in ("referencePrice", "finalPrice", "futureHigh", "futureLow", "forwardReturn",
                      "futureHighReturn", "futureLowReturn", "futureRange"):
            if outcome.get(field) is not None:
                _validate_decimal(outcome[field], f"outcome {field}", diagnostics, line)
        if outcome.get("outcomeDefinitionVersion") is not None \
                and outcome.get("outcomeDefinitionVersion") != population.get("outcomeDefinitionVersion"):
            _add(diagnostics, "outcome.definition_mismatch", "outcome definition differs from manifest", line)
        if outcome.get("status") is not None:
            required_metadata = ("maturityAt", "computedAt", "outcomeDefinitionVersion")
            if any(outcome.get(field) is None for field in required_metadata):
                _add(diagnostics, "outcome.incomplete_metadata", "non-null outcome is missing required metadata", line)
            elif not all(isinstance(outcome.get(field), str) and outcome[field] for field in required_metadata):
                _add(diagnostics, "outcome.invalid_metadata", "non-null outcome metadata is invalid", line)
            else:
                try:
                    maturity = _parse_time(outcome["maturityAt"])
                    computed = _parse_time(outcome["computedAt"])
                    if outcome["status"] == "PENDING" and computed >= maturity:
                        _add(diagnostics, "outcome.pending_after_maturity", "pending outcome was computed at or after maturity", line)
                except (TypeError, ValueError):
                    _add(diagnostics, "outcome.invalid_timestamp", "non-null outcome timestamp is invalid", line)
            if outcome.get("status") != "AVAILABLE" and any(outcome.get(field) is not None for field in (
                    "referencePrice", "finalPrice", "futureHigh", "futureLow", "forwardReturn",
                    "futureHighReturn", "futureLowReturn", "futureRange")):
                _add(diagnostics, "outcome.non_available_metrics", "non-available outcome cannot contain metrics", line)

    if row.get("status") == "ELIGIBLE":
        if not isinstance(outcome, dict) or outcome.get("status") != "AVAILABLE":
            _add(diagnostics, "outcome.ineligible", "eligible row must contain an AVAILABLE outcome", line)
        elif outcome.get("outcomeDefinitionVersion") != population.get("outcomeDefinitionVersion"):
            _add(diagnostics, "outcome.definition_mismatch", "eligible outcome definition differs from manifest", line)
        elif any(outcome.get(field) is None for field in (
                "maturityAt", "computedAt", "referenceObservationFingerprint", "futureEvidenceFingerprint")):
            _add(diagnostics, "outcome.incomplete_provenance", "available outcome is missing provenance metadata", line)
        elif not all(isinstance(outcome.get(field), str) and outcome[field] for field in (
                "maturityAt", "computedAt", "referenceObservationFingerprint", "futureEvidenceFingerprint")):
            _add(diagnostics, "outcome.invalid_provenance", "available outcome provenance metadata is invalid", line)
        elif cutoff is not None:
            try:
                maturity = _parse_time(outcome["maturityAt"])
                computed = _parse_time(outcome["computedAt"])
                expected_maturity = cutoff + timedelta(
                    microseconds=int(_duration_seconds(row["horizon"]) * Decimal(1_000_000)))
                if maturity != expected_maturity:
                    _add(diagnostics, "outcome.maturity_mismatch", "outcome maturity does not match cutoff and horizon", line)
                if computed < maturity:
                    _add(diagnostics, "outcome.computed_before_maturity", "outcome was computed before maturity", line)
            except (TypeError, ValueError, OverflowError):
                _add(diagnostics, "outcome.invalid_timestamp", "available outcome timestamp is invalid", line)
    elif row.get("status") == "EXCLUDED":
        if not isinstance(outcome, dict):
            _add(diagnostics, "outcome.missing_excluded", "excluded row must contain an outcome object", line)
        elif outcome.get("status") == "AVAILABLE":
            _add(diagnostics, "outcome.excluded_available", "excluded row cannot contain an AVAILABLE outcome", line)
        elif outcome.get("status") is None and set(outcome) != {"status"}:
            _add(diagnostics, "outcome.invalid_excluded", "missing excluded outcome must contain only status", line)
        elif any(outcome.get(field) is not None for field in (
                "referencePrice", "finalPrice", "futureHigh", "futureLow", "forwardReturn",
                "futureHighReturn", "futureLowReturn", "futureRange")):
            _add(diagnostics, "outcome.invalid_excluded", "excluded outcome cannot contain outcome metrics", line)
    if row.get("status") == "ELIGIBLE" and isinstance(outcome, dict) \
            and outcome.get("status") == "AVAILABLE" \
            and any(outcome.get(field) is None for field in (
                "referencePrice", "finalPrice", "futureHigh", "futureLow", "forwardReturn",
                "futureHighReturn", "futureLowReturn", "futureRange")):
        _add(diagnostics, "outcome.incomplete", "available outcome is missing a metric", line)


def _validate_selected_outcome(row: dict[str, Any], population: dict[str, Any],
                               diagnostics: list[dict[str, Any]], line: int) -> None:
    selections = population.get("selectedOutcomeFingerprints")
    if not isinstance(selections, list):
        return
    key = (row.get("marketId"), row.get("evaluationCutoff"), row.get("horizon"))
    selected = {
        (entry.get("marketId"), entry.get("evaluationCutoff"), entry.get("horizon")): entry.get("outcomeFingerprint")
        for entry in selections if isinstance(entry, dict)
    }.get(key)
    lineage = row.get("lineage") if isinstance(row.get("lineage"), dict) else {}
    actual = lineage.get("outcomeFingerprint")
    if selected is None:
        if row.get("status") == "ELIGIBLE" or actual is not None:
            _add(diagnostics, "outcome.selection_missing", "row outcome is not explicitly selected", line)
    elif row.get("status") == "ELIGIBLE" and actual != selected:
        _add(diagnostics, "outcome.selection_mismatch", "row outcome differs from selected fingerprint", line)
    elif actual is not None and actual != selected:
        _add(diagnostics, "outcome.selection_mismatch", "row outcome differs from selected fingerprint", line)


def _validate_known_at(row: dict[str, Any], cutoff: datetime | None,
                       diagnostics: list[dict[str, Any]], line: int) -> None:
    if cutoff is None:
        return
    def visit(value: Any) -> None:
        if isinstance(value, dict):
            if "knownAt" in value:
                try:
                    if _parse_time(value["knownAt"]) > cutoff:
                        _add(diagnostics, "evidence.future_known_at", "knownAt is after evaluationCutoff", line)
                except (TypeError, ValueError):
                    _add(diagnostics, "evidence.invalid_known_at", "knownAt must be an ISO timestamp", line)
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)
    visit(row.get("featureProvenance", []))


def _validate_provenance(value: Any, diagnostics: list[dict[str, Any]], line: int) -> None:
    if not isinstance(value, list):
        _add(diagnostics, "lineage.invalid_provenance", "featureProvenance must be a list", line)
        return
    required_strings = {"sourceCapability", "sourceVersion", "sourceArtifactType", "sourceArtifactProducerId",
                        "sourceArtifactProducerVersion", "sourceArtifactParametersFingerprint",
                        "sourceArtifactInputFingerprint", "sourceArtifactContractVersion"}
    for entry in value:
        if not isinstance(entry, dict) or set(entry) != PROVENANCE_FIELDS:
            _add(diagnostics, "lineage.invalid_provenance", "feature provenance entry has an invalid shape", line)
            continue
        for field in required_strings:
            if not isinstance(entry[field], str) or not entry[field]:
                _add(diagnostics, "lineage.invalid_provenance", f"provenance field is invalid: {field}", line)
        if not isinstance(entry["normalizationVersions"], list) \
                or not all(isinstance(version, str) for version in entry["normalizationVersions"]):
            _add(diagnostics, "lineage.invalid_provenance", "normalizationVersions must be a string list", line)


def _feature_types(schema: Any, diagnostics: list[dict[str, Any]], population: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(schema, dict) or not isinstance(schema.get("definitions"), list):
        _add(diagnostics, "manifest.invalid_feature_schema", "featureSchema.definitions must be a list")
        return {}
    required_fields = {"name", "group", "dataType", "unit", "sourceCapability", "sourceSemantic",
                       "order", "missingnessPolicy", "description"}
    definitions = schema["definitions"]
    for item in definitions:
        if not isinstance(item, dict) or set(item) != required_fields:
            _add(diagnostics, "manifest.invalid_feature_schema", "feature definitions have an invalid shape")
    types = {
        item.get("name"): item.get("dataType")
        for item in definitions
        if isinstance(item, dict)
    }
    if None in types:
        _add(diagnostics, "manifest.invalid_feature_schema", "feature definitions require names")
        types.pop(None)
    valid_types = {"NUMERIC", "CATEGORICAL", "BOOLEAN"}
    for name, data_type in types.items():
        if not isinstance(name, str) or not isinstance(data_type, str) or data_type not in valid_types:
            _add(diagnostics, "manifest.invalid_feature_schema", f"invalid feature definition: {name}")
    if schema.get("version") != population.get("featureProjectionVersion"):
        _add(diagnostics, "manifest.feature_schema_version_mismatch", "feature schema version differs from population")
    if len(types) != len(schema["definitions"]):
        _add(diagnostics, "manifest.invalid_feature_schema", "feature definitions must have unique names")
    orders = [item.get("order") for item in definitions if isinstance(item, dict)]
    if not all(isinstance(order, int) and not isinstance(order, bool) for order in orders) \
            or sorted(orders) != list(range(len(orders))):
        _add(diagnostics, "manifest.invalid_feature_schema", "feature definition order must be contiguous")
    return {name: data_type for name, data_type in types.items() if isinstance(name, str)}


def _validate_population(population: dict[str, Any], diagnostics: list[dict[str, Any]]) -> None:
    _unknown_fields(population, POPULATION_FIELDS, "population", diagnostics)
    for field in ("populationPolicyVersion", "marketIds", "startCutoffInclusive", "endCutoffExclusive",
                  "featureProjectionVersion", "featureEvidencePolicy", "outcomeDefinitionVersion",
                  "horizons", "samplingCadence", "acceptedFeatureMissingness", "selectedOutcomeFingerprints"):
        if field not in population:
            _add(diagnostics, "manifest.invalid_population", f"population field is missing: {field}")
    if population.get("featureEvidencePolicy") != "STRICT_OBSERVED":
        _add(diagnostics, "manifest.non_strict_evidence", "population requires STRICT_OBSERVED evidence")
    for field in ("marketIds", "horizons", "acceptedFeatureMissingness", "selectedOutcomeFingerprints"):
        if field in population and not isinstance(population[field], list):
            _add(diagnostics, "manifest.invalid_population", f"population field must be a list: {field}")
    if isinstance(population.get("marketIds"), list):
        for value in population["marketIds"]:
            try:
                UUID(value)
            except (AttributeError, TypeError, ValueError):
                _add(diagnostics, "manifest.invalid_population", "marketIds must contain UUIDs")
    if isinstance(population.get("selectedOutcomeFingerprints"), list):
        selection_keys: set[tuple[Any, Any, Any]] = set()
        for entry in population["selectedOutcomeFingerprints"]:
            if not isinstance(entry, dict) or set(entry) != {
                    "marketId", "evaluationCutoff", "horizon", "outcomeFingerprint"}:
                _add(diagnostics, "manifest.invalid_population", "selected outcome entry has an invalid shape")
                continue
            key = (entry["marketId"], entry["evaluationCutoff"], entry["horizon"])
            if key in selection_keys:
                _add(diagnostics, "manifest.duplicate_selection", "selected outcome identity is duplicated")
            selection_keys.add(key)
            try:
                UUID(entry["marketId"])
                _parse_time(entry["evaluationCutoff"])
            except (AttributeError, TypeError, ValueError):
                _add(diagnostics, "manifest.invalid_population", "selected outcome identity is invalid")
            if not isinstance(entry["horizon"], str) or not isinstance(entry["outcomeFingerprint"], str) \
                    or not entry["outcomeFingerprint"]:
                _add(diagnostics, "manifest.invalid_population", "selected outcome values are invalid")
            elif not _valid_duration(entry["horizon"]):
                _add(diagnostics, "manifest.invalid_population", "selected outcome horizon is invalid")
            elif entry["horizon"] not in _list_value(population.get("horizons")):
                _add(diagnostics, "manifest.invalid_population", "selected outcome horizon is not declared")
    for field in ("startCutoffInclusive", "endCutoffExclusive"):
        try:
            _parse_time(population[field])
        except (KeyError, TypeError, ValueError):
            _add(diagnostics, "manifest.invalid_population_timestamp", f"invalid population timestamp: {field}")
    for field in ("populationPolicyVersion", "featureProjectionVersion", "outcomeDefinitionVersion",
                  "featureEvidencePolicy", "samplingCadence"):
        if field in population and not isinstance(population[field], str):
            _add(diagnostics, "manifest.invalid_population", f"population field must be a string: {field}")
    if population.get("featureProjectionVersion") not in SUPPORTED_FEATURE_VERSIONS:
        _add(diagnostics, "manifest.unsupported_version", "unsupported feature projection version")
    if population.get("outcomeDefinitionVersion") not in SUPPORTED_OUTCOME_VERSIONS:
        _add(diagnostics, "manifest.unsupported_version", "unsupported outcome definition version")
    for field in ("horizons", "acceptedFeatureMissingness"):
        if isinstance(population.get(field), list) and not all(isinstance(value, str) for value in population[field]):
            _add(diagnostics, "manifest.invalid_population", f"population values must be strings: {field}")
    if isinstance(population.get("horizons"), list):
        for value in population["horizons"]:
            if not isinstance(value, str) or not _valid_duration(value):
                _add(diagnostics, "manifest.invalid_population", "horizons must contain ISO-8601 durations")
    if isinstance(population.get("acceptedFeatureMissingness"), list):
        for value in population["acceptedFeatureMissingness"]:
            if value not in VALID_MISSINGNESS:
                _add(diagnostics, "manifest.invalid_population", "acceptedFeatureMissingness contains an invalid value")


def _validate_manifest_scalars(manifest: dict[str, Any], diagnostics: list[dict[str, Any]]) -> None:
    for field in ("datasetDefinitionVersion", "exportSchemaVersion"):
        if not isinstance(manifest.get(field), str) or not manifest[field]:
            _add(diagnostics, "manifest.invalid_field", f"manifest field must be a non-empty string: {field}")
    if manifest.get("datasetDefinitionVersion") not in SUPPORTED_DATASET_VERSIONS:
        _add(diagnostics, "manifest.unsupported_version", "unsupported dataset definition version")
    counts = (manifest.get("sampleCount"), manifest.get("eligibleCount"), manifest.get("excludedCount"))
    if not all(isinstance(value, int) and not isinstance(value, bool) and value >= 0 for value in counts):
        _add(diagnostics, "manifest.invalid_counts", "manifest counts must be non-negative integers")
    elif counts[1] + counts[2] != counts[0]:
        _add(diagnostics, "manifest.invalid_counts", "eligible and excluded counts must equal sample count")
    if not isinstance(manifest.get("outcomeSchema"), list) \
            or not all(isinstance(value, str) for value in manifest["outcomeSchema"]):
        _add(diagnostics, "manifest.invalid_outcome_schema", "outcomeSchema must be a list of strings")
    exclusion_counts = manifest.get("exclusionCounts")
    if not isinstance(exclusion_counts, dict) or not all(
            isinstance(key, str) and isinstance(value, int) and not isinstance(value, bool) and value >= 0
            for key, value in exclusion_counts.items()):
        _add(diagnostics, "manifest.invalid_exclusion_counts", "exclusionCounts must map strings to non-negative integers")
    for field in ("datasetFingerprint", "samplesSha256"):
        value = manifest.get(field)
        if not isinstance(value, str) or len(value) != 64 or any(character not in "0123456789abcdef" for character in value):
            _add(diagnostics, "manifest.invalid_fingerprint", f"manifest field is not a lowercase SHA-256: {field}")


def _sample_material(row: dict[str, Any]) -> list[str]:
    lineage = row.get("lineage", {})
    reason = row.get("exclusionReason")
    outcome = row.get("outcome")
    if not isinstance(outcome, dict) or outcome.get("status") is None:
        return [
            "definition=research-dataset-v1",
            f"market={row.get('marketId')}",
            f"cutoff={row.get('evaluationCutoff')}",
            f"horizon={row.get('horizon')}",
            f"feature={lineage.get('featureSnapshotFingerprint')}",
            f"reason={reason}",
        ]
    return [
        "definition=research-dataset-v1",
        f"market={row.get('marketId')}",
        f"cutoff={row.get('evaluationCutoff')}",
        f"horizon={row.get('horizon')}",
        f"projection={row.get('featureProjectionVersion')}",
        f"feature={lineage.get('featureSnapshotFingerprint')}",
        f"outcome={lineage.get('outcomeFingerprint')}",
        f"status={row.get('status')}",
        f"reason={reason if reason is not None else 'null'}",
    ]


def _dataset_material(manifest: dict[str, Any], population: dict[str, Any],
                      rows: list[dict[str, Any]]) -> list[str]:
    material = [
        f"definition={manifest.get('datasetDefinitionVersion')}",
        f"populationPolicyVersion={population.get('populationPolicyVersion')}",
        f"startCutoff={population.get('startCutoffInclusive')}",
        f"endCutoff={population.get('endCutoffExclusive')}",
        f"featureProjection={population.get('featureProjectionVersion')}",
        f"featurePolicy={population.get('featureEvidencePolicy')}",
        f"outcomeDefinition={population.get('outcomeDefinitionVersion')}",
        f"cadence={population.get('samplingCadence')}",
    ]
    material.extend(f"market={value}" for value in sorted(_list_value(population.get("marketIds"))))
    material.extend(f"horizon={value}" for value in sorted(_list_value(population.get("horizons"))))
    material.extend(f"acceptedMissingness={value}" for value in sorted(_list_value(population.get("acceptedFeatureMissingness"))))
    for selection in _list_value(population.get("selectedOutcomeFingerprints")):
        if isinstance(selection, dict):
            key = (f"ResearchOutcomeKey[marketId={selection.get('marketId')}, "
                   f"evaluationCutoff={selection.get('evaluationCutoff')}, "
                   f"horizon=OutcomeHorizon[value={selection.get('horizon')}]]")
            material.append(f"selectedOutcome={key}|{selection.get('outcomeFingerprint')}")
    material.extend(
        f"sample={row.get('lineage', {}).get('sampleFingerprint')}"
        for row in rows if isinstance(row.get("lineage"), dict)
    )
    for reason, count in sorted((manifest.get("exclusionCounts") or {}).items()):
        material.append(f"exclusion={reason}|{count}")
    return material


def _list_value(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def _valid_duration(value: Any) -> bool:
    seconds = _duration_seconds(value)
    return seconds is not None and Decimal("0") < seconds <= Decimal(15 * 720 * 60)


def _duration_seconds(value: Any) -> Decimal | None:
    if not isinstance(value, str):
        return None
    match = re.fullmatch(r"P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?", value)
    if not match or not any(group is not None for group in match.groups()):
        return None
    if "T" in value and not any(group is not None for group in match.groups()[1:]):
        return None
    days, hours, minutes, seconds = (Decimal(group or "0") for group in match.groups())
    return days * 86400 + hours * 3600 + minutes * 60 + seconds


def _validate_decimal(value: Any, field: str, diagnostics: list[dict[str, Any]], line: int) -> None:
    if isinstance(value, bool) or not isinstance(value, (Decimal, int, float, str)):
        _add(diagnostics, "values.invalid_decimal", f"invalid decimal value: {field}", line)
        return
    try:
        decimal = Decimal(str(value))
        if not decimal.is_finite():
            raise InvalidOperation
    except (InvalidOperation, ValueError):
        _add(diagnostics, "values.invalid_decimal", f"invalid decimal value: {field}", line)


def _reject_json_constant(value: str) -> None:
    raise ValueError(f"non-standard JSON constant: {value}")


def _read_json(path: Path, diagnostics: list[dict[str, Any]], code: str) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError):
        _add(diagnostics, code, f"cannot read JSON file: {path}")
        return None


def _parse_time(value: Any) -> datetime:
    if not isinstance(value, str):
        raise ValueError(value)
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ValueError("timestamp must include timezone")
    return parsed.astimezone(timezone.utc)


def _fingerprint(material: list[str]) -> str:
    return hashlib.sha256(SEPARATOR.join(sorted(material)).encode("utf-8")).hexdigest()


def _sample_id(fingerprint: str) -> str:
    value = bytearray(hashlib.md5(fingerprint.encode("utf-8")).digest())
    value[6] = (value[6] & 0x0F) | 0x30
    value[8] = (value[8] & 0x3F) | 0x80
    return str(UUID(bytes=bytes(value)))


def _unknown_fields(value: dict[str, Any], allowed: set[str], scope: str,
                    diagnostics: list[dict[str, Any]], line: int | None = None) -> None:
    for field in sorted(set(value) - allowed):
        _add(diagnostics, "schema.unknown_field", f"unknown field in {scope}: {field}", line)


def _add(diagnostics: list[dict[str, Any]], code: str, message: str, line: int | None = None) -> None:
    diagnostic: dict[str, Any] = {"code": code, "message": message}
    if line is not None:
        diagnostic["line"] = line
    diagnostics.append(diagnostic)


def _summary(manifest: dict[str, Any], rows: list[dict[str, Any]]) -> dict[str, Any]:
    exclusions: dict[str, int] = {}
    missingness: dict[str, int] = {}
    outcomes: dict[str, int] = {}
    for row in rows:
        reason = row.get("exclusionReason")
        if isinstance(reason, str):
            exclusions[reason] = exclusions.get(reason, 0) + 1
        for feature in (row.get("features") or {}).values():
            if isinstance(feature, dict) and isinstance(feature.get("status"), str):
                status = feature["status"]
                missingness[status] = missingness.get(status, 0) + 1
        outcome_status = (row.get("outcome") or {}).get("status")
        if isinstance(outcome_status, str):
            outcomes[outcome_status] = outcomes.get(outcome_status, 0) + 1
    eligible = sum(row.get("status") == "ELIGIBLE" for row in rows)
    excluded = sum(row.get("status") == "EXCLUDED" for row in rows)
    return {
        "sampleCount": len(rows),
        "eligibleCount": eligible,
        "excludedCount": excluded,
        "unclassifiedCount": len(rows) - eligible - excluded,
        "markets": sorted({row.get("marketId") for row in rows if isinstance(row.get("marketId"), str)}),
        "horizons": sorted({row.get("horizon") for row in rows if isinstance(row.get("horizon"), str)}),
        "coverage": {"eligible": eligible, "samples": len(rows)},
        "exclusionCounts": dict(sorted(exclusions.items())),
        "outcomeStatuses": dict(sorted(outcomes.items())),
        "featureMissingness": dict(sorted(missingness.items())),
        "manifestDatasetFingerprint": manifest.get("datasetFingerprint"),
    }


def _result(diagnostics: list[dict[str, Any]], summary: dict[str, Any] | None = None) -> ValidationResult:
    return ValidationResult(
        tuple(sorted(diagnostics, key=lambda value: (value.get("line", 0), value["code"], value["message"]))),
        summary or {},
    )

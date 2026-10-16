from __future__ import annotations

import json
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path
from types import MappingProxyType
from typing import Any

from .validator import SUPPORTED_DATASET_VERSIONS, ValidationResult, validate_bundle


class DatasetValidationError(ValueError):
    def __init__(self, result: ValidationResult):
        super().__init__("research dataset validation failed")
        self.result = result


@dataclass(frozen=True)
class ValidatedResearchDataset:
    manifest: dict[str, Any]
    rows: tuple[dict[str, Any], ...]
    validation: ValidationResult

    def __post_init__(self) -> None:
        if not self.validation.valid:
            raise DatasetValidationError(self.validation)
        if self.manifest.get("datasetDefinitionVersion") not in SUPPORTED_DATASET_VERSIONS:
            raise ValueError("unsupported dataset definition version")
        object.__setattr__(self, "manifest", _freeze(self.manifest))
        object.__setattr__(self, "rows", tuple(_freeze(row) for row in self.rows))

    @property
    def fingerprint(self) -> str:
        return self.manifest["datasetFingerprint"]

    @property
    def feature_definitions(self) -> tuple[dict[str, Any], ...]:
        return tuple(self.manifest["featureSchema"]["definitions"])


def load_validated_bundle(bundle: str | Path) -> ValidatedResearchDataset:
    root = Path(bundle)
    result = validate_bundle(root)
    if not result.valid:
        raise DatasetValidationError(result)
    manifest = json.loads((root / "dataset-manifest.json").read_text(encoding="utf-8"))
    rows = tuple(
        json.loads(line, parse_float=Decimal, parse_int=Decimal)
        for line in (root / "samples.jsonl").read_text(encoding="utf-8").splitlines()
        if line.strip()
    )
    return ValidatedResearchDataset(manifest=manifest, rows=rows, validation=result)


def load_validated_paths(manifest: str | Path, samples: str | Path) -> ValidatedResearchDataset:
    manifest_path = Path(manifest)
    samples_path = Path(samples)
    if manifest_path.name != "dataset-manifest.json" or samples_path.name != "samples.jsonl":
        raise ValueError("experiment inputs must use dataset-manifest.json and samples.jsonl")
    if manifest_path.parent != samples_path.parent:
        raise ValueError("manifest and samples must belong to the same bundle directory")
    return load_validated_bundle(manifest_path.parent)


def _freeze(value: Any) -> Any:
    if isinstance(value, dict):
        return MappingProxyType({key: _freeze(child) for key, child in value.items()})
    if isinstance(value, list):
        return tuple(_freeze(child) for child in value)
    return value

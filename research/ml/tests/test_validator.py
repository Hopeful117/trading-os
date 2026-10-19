import hashlib
import io
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

from research_dataset.validator import validate_bundle
from research_dataset.__main__ import main


def fingerprint(material):
    return hashlib.sha256("\x1f".join(sorted(material)).encode()).hexdigest()


class ValidatorTest(unittest.TestCase):
    def test_accepts_committed_java_generated_fixture(self):
        fixture = Path(__file__).parent / "fixtures" / "java-generated"
        result = validate_bundle(fixture)
        self.assertTrue(result.valid, result.diagnostics)

    def test_cli_exit_codes(self):
        fixture = Path(__file__).parent / "fixtures" / "java-generated"
        output = io.StringIO()
        with redirect_stdout(output):
            self.assertEqual(main(["validate", str(fixture)]), 0)
        self.assertIn("valid", output.getvalue())
        output = io.StringIO()
        with redirect_stdout(output):
            self.assertEqual(main(["diagnose", str(fixture)]), 0)
        self.assertIn("featureMissingness", json.loads(output.getvalue())["summary"])

    def test_rejects_unsupported_versions(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture = Path(__file__).parent / "fixtures" / "java-generated"
            for name in ("dataset-manifest.json", "samples.jsonl"):
                (root / name).write_bytes((fixture / name).read_bytes())
            manifest = json.loads((root / "dataset-manifest.json").read_text())
            manifest["datasetDefinitionVersion"] = "research-dataset-v2"
            (root / "dataset-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            result = validate_bundle(root)
            self.assertIn("manifest.unsupported_version", {item["code"] for item in result.diagnostics})

    def test_rejects_missing_provenance_and_outcome_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture = Path(__file__).parent / "fixtures" / "java-generated"
            for name in ("dataset-manifest.json", "samples.jsonl"):
                (root / name).write_bytes((fixture / name).read_bytes())
            row = json.loads((root / "samples.jsonl").read_text())
            row["featureProvenance"] = None
            del row["outcome"]["futureEvidenceFingerprint"]
            samples = json.dumps(row, separators=(",", ":")) + "\n"
            (root / "samples.jsonl").write_text(samples, encoding="utf-8")
            manifest = json.loads((root / "dataset-manifest.json").read_text())
            manifest["samplesSha256"] = hashlib.sha256(samples.encode()).hexdigest()
            (root / "dataset-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            result = validate_bundle(root)
            codes = {item["code"] for item in result.diagnostics}
            self.assertTrue({"lineage.invalid_provenance", "outcome.incomplete_provenance"} <= codes)

    def test_rejects_zero_duration(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_bundle(root)
            manifest = json.loads((root / "dataset-manifest.json").read_text())
            manifest["population"]["horizons"] = ["P0D"]
            (root / "dataset-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            result = validate_bundle(root)
            self.assertIn("manifest.invalid_population", {item["code"] for item in result.diagnostics})

    def test_rejects_invalid_non_null_outcome_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture = Path(__file__).parent / "fixtures" / "java-generated"
            for name in ("dataset-manifest.json", "samples.jsonl"):
                (root / name).write_bytes((fixture / name).read_bytes())
            row = json.loads((root / "samples.jsonl").read_text())
            row["outcome"]["futureEvidenceFingerprint"] = 123
            row["outcome"]["maturityAt"] = "2026-10-15T11:30:00Z"
            samples = json.dumps(row, separators=(",", ":")) + "\n"
            (root / "samples.jsonl").write_text(samples, encoding="utf-8")
            manifest = json.loads((root / "dataset-manifest.json").read_text())
            manifest["samplesSha256"] = hashlib.sha256(samples.encode()).hexdigest()
            (root / "dataset-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            result = validate_bundle(root)
            codes = {item["code"] for item in result.diagnostics}
            self.assertIn("outcome.invalid_provenance", codes)
            row["outcome"]["futureEvidenceFingerprint"] = "future"
            samples = json.dumps(row, separators=(",", ":")) + "\n"
            (root / "samples.jsonl").write_text(samples, encoding="utf-8")
            manifest["samplesSha256"] = hashlib.sha256(samples.encode()).hexdigest()
            (root / "dataset-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            result = validate_bundle(root)
            self.assertIn("outcome.maturity_mismatch", {item["code"] for item in result.diagnostics})

    def make_bundle(self, root: Path) -> None:
        sample_material = [
            "definition=research-dataset-v1",
            "market=11111111-1111-1111-1111-111111111111",
            "cutoff=2026-10-15T10:00:00Z",
            "horizon=PT1H",
            "feature=feature-fingerprint",
            "reason=OUTCOME_MISSING",
        ]
        row = {
            "sampleId": "placeholder",
            "marketId": "11111111-1111-1111-1111-111111111111",
            "evaluationCutoff": "2026-10-15T10:00:00Z",
            "horizon": "PT1H",
            "featureProjectionVersion": "feature-projection-v1",
            "evidencePolicy": "STRICT_OBSERVED",
            "featureSnapshotCreatedAt": "2026-10-15T10:00:00Z",
            "status": "EXCLUDED",
            "exclusionReason": "OUTCOME_MISSING",
            "features": {
                "feature.one": {"type": "NUMERIC", "status": "PRESENT", "value": 1.0}
            },
            "outcome": {"status": None},
            "featureProvenance": [],
            "lineage": {
                "featureSnapshotFingerprint": "feature-fingerprint",
                "featureIdentityFingerprint": "identity-fingerprint",
                "contextFingerprint": "context-fingerprint",
                "outcomeFingerprint": None,
                "sampleFingerprint": fingerprint(sample_material),
                "sampleFingerprintMaterial": sample_material,
            },
        }
        row["sampleId"] = self.sample_id(row["lineage"]["sampleFingerprint"])
        samples = json.dumps(row, separators=(",", ":")) + "\n"
        dataset_material = [
            "definition=research-dataset-v1",
            "populationPolicyVersion=population-v1",
            "startCutoff=2026-10-15T00:00:00Z",
            "endCutoff=2026-10-16T00:00:00Z",
            "featureProjection=feature-projection-v1",
            "featurePolicy=STRICT_OBSERVED",
            "outcomeDefinition=market-outcome-v1",
            "cadence=ONE_PER_FEATURE_SNAPSHOT",
            "horizon=PT1H",
            "featureRequirementsVersion=q1-v1",
            "selectedFeatureSnapshot=ResearchFeatureSnapshotKey[marketId=11111111-1111-1111-1111-111111111111, evaluationCutoff=2026-10-15T10:00:00Z]|feature-fingerprint",
            "sample=" + row["lineage"]["sampleFingerprint"],
            "exclusion=OUTCOME_MISSING|1",
        ]
        manifest = {
            "exportSchemaVersion": "research-dataset-bundle-v1",
            "datasetDefinitionVersion": "research-dataset-v1",
            "population": {
                "populationPolicyVersion": "population-v1",
                "marketIds": [],
                "startCutoffInclusive": "2026-10-15T00:00:00Z",
                "endCutoffExclusive": "2026-10-16T00:00:00Z",
                "featureProjectionVersion": "feature-projection-v1",
                "featureEvidencePolicy": "STRICT_OBSERVED",
                "outcomeDefinitionVersion": "market-outcome-v1",
                "horizons": ["PT1H"],
                "samplingCadence": "ONE_PER_FEATURE_SNAPSHOT",
                "acceptedFeatureMissingness": [],
                "featureRequirements": {
                    "version": "q1-v1",
                    "requiredFeatureNames": [],
                    "acceptedMissingness": [],
                },
                "selectedFeatureSnapshotFingerprints": [{
                    "marketId": "11111111-1111-1111-1111-111111111111",
                    "evaluationCutoff": "2026-10-15T10:00:00Z",
                    "snapshotFingerprint": "feature-fingerprint",
                }],
                "selectedOutcomeFingerprints": [],
            },
            "featureSchema": {"version": "feature-projection-v1", "definitions": [{
                "name": "feature.one", "group": "TEST", "dataType": "NUMERIC", "unit": "RATIO",
                "sourceCapability": "test", "sourceSemantic": "test", "order": 0,
                "missingnessPolicy": "ALLOW", "description": "test"
            }]},
            "outcomeSchema": [],
            "sampleCount": 1,
            "eligibleCount": 0,
            "excludedCount": 1,
            "exclusionCounts": {"OUTCOME_MISSING": 1},
            "datasetFingerprint": fingerprint(dataset_material),
            "fingerprintMaterial": dataset_material,
            "samplesSha256": hashlib.sha256(samples.encode()).hexdigest(),
        }
        (root / "samples.jsonl").write_text(samples, encoding="utf-8")
        (root / "dataset-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")

    @staticmethod
    def sample_id(fingerprint_value):
        import uuid
        value = bytearray(hashlib.md5(fingerprint_value.encode()).digest())
        value[6] = (value[6] & 0x0F) | 0x30
        value[8] = (value[8] & 0x3F) | 0x80
        return str(uuid.UUID(bytes=bytes(value)))

    def test_accepts_java_contract_fixture(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_bundle(root)
            result = validate_bundle(root)
            self.assertTrue(result.valid, result.diagnostics)

    def test_rejects_tampered_samples(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_bundle(root)
            (root / "samples.jsonl").write_text((root / "samples.jsonl").read_text() + "\n", encoding="utf-8")
            result = validate_bundle(root)
            self.assertFalse(result.valid)
            self.assertIn("samples.checksum_mismatch", {item["code"] for item in result.diagnostics})

    def test_rejects_selected_outcome_mismatch(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture = Path(__file__).parent / "fixtures" / "java-generated"
            for name in ("dataset-manifest.json", "samples.jsonl"):
                (root / name).write_bytes((fixture / name).read_bytes())
            manifest = json.loads((root / "dataset-manifest.json").read_text())
            manifest["population"]["selectedOutcomeFingerprints"][0]["outcomeFingerprint"] = "different"
            (root / "dataset-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            result = validate_bundle(root)
            self.assertIn("outcome.selection_mismatch", {item["code"] for item in result.diagnostics})

    def test_rejects_selected_feature_snapshot_mismatch(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture = Path(__file__).parent / "fixtures" / "java-generated"
            for name in ("dataset-manifest.json", "samples.jsonl"):
                (root / name).write_bytes((fixture / name).read_bytes())
            row = json.loads((root / "samples.jsonl").read_text())
            row["lineage"]["featureSnapshotFingerprint"] = "different-snapshot"
            samples = json.dumps(row, separators=(",", ":")) + "\n"
            (root / "samples.jsonl").write_text(samples, encoding="utf-8")
            manifest = json.loads((root / "dataset-manifest.json").read_text())
            manifest["samplesSha256"] = hashlib.sha256(samples.encode()).hexdigest()
            (root / "dataset-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            result = validate_bundle(root)
            self.assertIn("feature.selection_mismatch", {item["code"] for item in result.diagnostics})

    def test_rejects_duplicate_required_feature_names(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture = Path(__file__).parent / "fixtures" / "java-generated"
            for name in ("dataset-manifest.json", "samples.jsonl"):
                (root / name).write_bytes((fixture / name).read_bytes())
            manifest = json.loads((root / "dataset-manifest.json").read_text())
            manifest["population"]["featureRequirements"]["requiredFeatureNames"] = [
                "ohlc.range_fraction", " ohlc.range_fraction "
            ]
            (root / "dataset-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            result = validate_bundle(root)
            self.assertIn("manifest.duplicate_required_feature", {item["code"] for item in result.diagnostics})

    def test_rejects_non_canonical_required_feature_name(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture = Path(__file__).parent / "fixtures" / "java-generated"
            for name in ("dataset-manifest.json", "samples.jsonl"):
                (root / name).write_bytes((fixture / name).read_bytes())
            manifest = json.loads((root / "dataset-manifest.json").read_text())
            manifest["population"]["featureRequirements"]["requiredFeatureNames"] = [
                " ohlc.range_fraction "
            ]
            (root / "dataset-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            result = validate_bundle(root)
            codes = {item["code"] for item in result.diagnostics}
            self.assertIn("manifest.non_canonical_required_feature", codes)

    def test_rejects_malformed_excluded_outcome(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_bundle(root)
            row = json.loads((root / "samples.jsonl").read_text())
            row["outcome"] = {"status": "BROKEN", "computedAt": "not-a-time", "forwardReturn": "NaN"}
            samples = json.dumps(row, separators=(",", ":")) + "\n"
            (root / "samples.jsonl").write_text(samples, encoding="utf-8")
            manifest = json.loads((root / "dataset-manifest.json").read_text())
            manifest["samplesSha256"] = hashlib.sha256(samples.encode()).hexdigest()
            (root / "dataset-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            result = validate_bundle(root)
            codes = {item["code"] for item in result.diagnostics}
            self.assertTrue({"outcome.invalid_status", "outcome.invalid_timestamp", "values.invalid_decimal"} <= codes)

    def test_rejects_invalid_timestamp_status_and_decimal(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_bundle(root)
            row = json.loads((root / "samples.jsonl").read_text())
            row["evaluationCutoff"] = "2026-10-15T10:00:00"
            row["status"] = "UNKNOWN"
            row["features"]["feature.one"]["value"] = "NaN"
            samples = json.dumps(row, separators=(",", ":")) + "\n"
            (root / "samples.jsonl").write_text(samples, encoding="utf-8")
            manifest = json.loads((root / "dataset-manifest.json").read_text())
            manifest["samplesSha256"] = hashlib.sha256(samples.encode()).hexdigest()
            (root / "dataset-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            result = validate_bundle(root)
            codes = {item["code"] for item in result.diagnostics}
            self.assertTrue({"samples.invalid_status", "samples.invalid_cutoff", "values.invalid_decimal"} <= codes)

    def test_rejects_rows_outside_declared_population(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_bundle(root)
            manifest = json.loads((root / "dataset-manifest.json").read_text())
            manifest["population"]["marketIds"] = ["33333333-3333-3333-3333-333333333333"]
            (root / "dataset-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            result = validate_bundle(root)
            self.assertIn("samples.market_outside_population", {item["code"] for item in result.diagnostics})

    def test_rejects_cutoff_outside_declared_population(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_bundle(root)
            row = json.loads((root / "samples.jsonl").read_text())
            row["evaluationCutoff"] = "2026-10-16T00:00:00Z"
            samples = json.dumps(row, separators=(",", ":")) + "\n"
            (root / "samples.jsonl").write_text(samples, encoding="utf-8")
            manifest = json.loads((root / "dataset-manifest.json").read_text())
            manifest["samplesSha256"] = hashlib.sha256(samples.encode()).hexdigest()
            (root / "dataset-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            result = validate_bundle(root)
            self.assertIn("samples.cutoff_outside_population", {item["code"] for item in result.diagnostics})

    def test_rejects_sample_id_mismatch_and_policy_mismatch(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_bundle(root)
            row = json.loads((root / "samples.jsonl").read_text())
            row["sampleId"] = "33333333-3333-3333-3333-333333333333"
            row["evidencePolicy"] = "PROVIDER_REPORTED"
            samples = json.dumps(row, separators=(",", ":")) + "\n"
            (root / "samples.jsonl").write_text(samples, encoding="utf-8")
            manifest = json.loads((root / "dataset-manifest.json").read_text())
            manifest["samplesSha256"] = hashlib.sha256(samples.encode()).hexdigest()
            (root / "dataset-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            result = validate_bundle(root)
            codes = {item["code"] for item in result.diagnostics}
            self.assertTrue({"samples.sample_id_mismatch", "samples.evidence_policy_mismatch"} <= codes)

    def test_rejects_duplicate_semantic_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_bundle(root)
            samples = (root / "samples.jsonl").read_text()
            duplicated = samples + samples
            (root / "samples.jsonl").write_text(duplicated, encoding="utf-8")
            manifest = json.loads((root / "dataset-manifest.json").read_text())
            manifest["sampleCount"] = 2
            manifest["eligibleCount"] = 0
            manifest["excludedCount"] = 2
            manifest["exclusionCounts"] = {"OUTCOME_MISSING": 2}
            manifest["samplesSha256"] = hashlib.sha256(duplicated.encode()).hexdigest()
            (root / "dataset-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            result = validate_bundle(root)
            self.assertIn("samples.duplicate_identity", {item["code"] for item in result.diagnostics})


if __name__ == "__main__":
    unittest.main()

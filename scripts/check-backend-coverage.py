#!/usr/bin/env python3
"""Backend coverage gate: parse JaCoCo XML reports and enforce LINE >= threshold.

Exit code 0 if all protected modules pass, 1 otherwise.
Designed to run after `mvn verify` (which already runs JaCoCo check).
This script provides aggregated reporting and a final gate verdict.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

THRESHOLD = 0.80
MODULES = [
    ("risk-domain", "BUSINESS_MODULE"),
    ("trading-core", "BUSINESS_MODULE"),
    ("broker-service", "BUSINESS_MODULE"),
    ("market-data", "BUSINESS_MODULE"),
    ("market-intelligence", "BUSINESS_MODULE"),
    ("gateway", "INFRASTRUCTURE_MODULE_WITH_COVERAGE_GATE"),
    ("eureka-server", "BOOTSTRAP"),
]

project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
failed = []
results = []

for module, classification in MODULES:
    report_path = os.path.join(project_root, module, "target", "site", "jacoco", "jacoco.xml")
    if not os.path.isfile(report_path):
        results.append((module, classification, "NO_REPORT", 0.0, 0, 0))
        if classification in ("BUSINESS_MODULE", "INFRASTRUCTURE_MODULE_WITH_COVERAGE_GATE"):
            failed.append(module)
        continue

    root = ET.parse(report_path).getroot()
    counters = {c.get("type"): c for c in root.iter("counter")}
    line_counter = counters.get("LINE")
    if line_counter is None:
        results.append((module, classification, "NO_LINE_DATA", 0.0, 0, 0))
        if classification in ("BUSINESS_MODULE", "INFRASTRUCTURE_MODULE_WITH_COVERAGE_GATE"):
            failed.append(module)
        continue

    missed = int(line_counter.get("missed", 0))
    covered = int(line_counter.get("covered", 0))
    total = missed + covered
    ratio = covered / total if total > 0 else 0.0
    pct = ratio * 100

    enforced = classification in ("BUSINESS_MODULE", "INFRASTRUCTURE_MODULE_WITH_COVERAGE_GATE")
    status = "PASS" if ratio >= THRESHOLD or not enforced else "FAIL"
    results.append((module, classification, status, pct, covered, total))

    if enforced and ratio < THRESHOLD:
        failed.append(module)

# Print report
print("=" * 72)
print(f"{'Module':<25} {'Type':<25} {'LINE%':>8} {'Covered':>8} {'Total':>8} {'Gate':>6}")
print("-" * 72)
for module, classification, status, pct, covered, total in results:
    print(f"{module:<25} {classification:<25} {pct:>7.2f}% {covered:>8} {total:>8} {status:>6}")
print("=" * 72)

# Final verdict
if failed:
    print(f"\nBACKEND COVERAGE GATE: FAILED ({len(failed)} module(s) below {THRESHOLD*100:.0f}% LINE)")
    for m in failed:
        print(f"  - {m}")
    sys.exit(1)
else:
    print(f"\nBACKEND COVERAGE GATE: PASSED (all BUSINESS_MODULE >= {THRESHOLD*100:.0f}% LINE)")
    sys.exit(0)

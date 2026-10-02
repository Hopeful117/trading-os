# Implementation Plan - Story 0059

## Plan Status

Compatibility implementation completed. Full Story acceptance remains dependent
on explicit runtime evidence and broader manual-risk regression coverage.

## Step 1 - Inspect Existing Risk Handoff

Confirm that the existing handoff is keyed by TradePlan identity and version,
not by Opportunity identity. Preserve the current immutable evaluation contract.

## Step 2 - Preserve Manual Provenance

Extend the risk snapshot and client transport mapping only as required to retain
the `MANUAL` origin and its existing planning/account provenance. Do not create a
manual-specific Risk contract.

## Step 3 - Preserve Opportunity Compatibility

Run the existing opportunity-origin handoff and client tests. Ensure missing
Opportunity provenance is valid only for `MANUAL` plans and that legacy plans
remain readable.

## Step 4 - Validate Negative Boundaries

Verify that rejected or unknown Risk outcomes still do not create an
ExecutionIntent or broker mutation. Do not weaken fail-closed behavior to make a
manual scenario pass.

## Step 5 - Report Evidence

Record the focused tests actually executed, distinguish compatibility evidence
from full runtime evidence, and keep the Story open if the complete manual path
has not been demonstrated.

## Explicit Non-Goals

Do not implement new Risk rules, sizing algorithms, execution paths, broker calls,
frontend changes or PAPER settlement behavior.

# Implementation Plan - Story 0055

## Plan Status

`IMPLEMENTED - HISTORICAL PLAN`

## Phase 1 - Identify the incompatible default

Compare the generated PAPER planning budget with the effective risk rules and
confirm that the observed `MAX_EXPOSURE` rejection is caused by planning inputs,
not by a risk-engine defect.

## Phase 2 - Derive a compatible budget

For PAPER account provisioning:

1. validate the effective risk profile;
2. read the maximum position-risk and maximum-exposure ratios;
3. calculate the position-risk budget from initial capital;
4. calculate the exposure-compatible budget using stop distance;
5. use the stricter budget when creating the Trade Planning Profile;
6. preserve account currency and instrument compatibility.

## Phase 3 - Preserve authority boundaries

Do not change Risk Domain thresholds or duplicate risk authorization in the
planning layer. The generated plan must still pass through deterministic risk
evaluation before execution.

## Phase 4 - Tests and runtime evidence

Cover the budget calculation, missing-rule failure, account provisioning, and
ownership behavior. Validate both a compatible plan and an intentionally
excessive plan through the supported web flow.

## Implementation Reference

```text
commit = 8cc2f75
```

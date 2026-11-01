# Implementation Plan - Story 0189

## Outcome

The readiness gate is not satisfied. No production implementation plan is
authorized beyond preserving the existing fail-closed behavior.

## Permitted Changes

- Create the Story evidence and review artifacts.
- Preserve `SettlementCompatibilityPolicy`.
- Record the smallest follow-up implementation needed to complete ADR-083 proof.

## Forbidden Changes

- Do not enable `CASH + CFD`.
- Do not use provider preview, leverage, used margin, or position margin for
  PAPER authorization.
- Do not weaken T0/T1 or human authorization gates.
- Do not add a compatibility override or edit V49.
- Do not enable cTrader safety or perform provider mutations.

## Required Follow-up Before Phase 8

1. Make the local margin resolver accept an explicit decision-boundary freshness
   policy and use the fresh valuation source price for the calculation.
2. Bind T1 fresh exposure, contract, mark, FX, and policy assignment evidence to
   the exact immutable T0 policy version.
3. Enforce ADR-083 policy invariants at the durable database boundary, including
   rate precision, effective-time/provenance constraints, and immutability.
4. Add PostgreSQL policy persistence/assignment/restart tests.
5. Add the required T0/T1 behavioral test where a changed authoritative fact
   changes required margin and can turn PASS into REJECT.
6. Add a deterministic application-level PAPER CFD E2E covering local margin,
   explicit authorization, T1, settlement, MTM, full exit, and exactly-once cash
   realization.
7. Add and complete the missing independent Story 0188 code review artifact,
   then rerun the full Trading Core suite and applicable static checks.

Only after those gates pass may a separate compatibility authorization change be
considered.

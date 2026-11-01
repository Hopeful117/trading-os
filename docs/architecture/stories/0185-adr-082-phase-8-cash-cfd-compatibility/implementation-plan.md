# Implementation Plan - Story 0185

## Outcome

This execution is blocked by the Phase 8 readiness gate. No production
compatibility change is authorized.

## Permitted Changes

- Create the Story-local readiness and blocker artifacts.
- Preserve the current compatibility policy and fail-closed margin behavior.
- Record the exact next boundary required before a future authorization attempt.

## Forbidden Changes

- Do not change `SettlementCompatibilityPolicy`.
- Do not route around `RequiredMarginPort`.
- Do not derive margin from leverage, notional, used margin, or free margin.
- Do not call PAPER settlement a proof of risk readiness without a complete
  authoritative required-margin fact.
- Do not enable cTrader safety or execute provider mutations.

## Future Prerequisite Plan

1. Define an approved provider-neutral proposed-order margin fact contract for
   PAPER CFD, including amount, currency, timestamp, source, version, and
   contract identity.
2. Define how that fact is produced without using Broker Service as the PAPER
   financial authority.
3. Prove LIVE provider previews satisfy the same semantics without substituting
   used margin.
4. Add focused T0/T1, missing/stale-margin, routing, and risk-before-settlement
   tests.
5. Re-run the full Phase 8 readiness matrix and only then reconsider the
   compatibility policy.

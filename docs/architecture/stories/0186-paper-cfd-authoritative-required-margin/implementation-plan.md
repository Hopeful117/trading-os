# Implementation Plan - Story 0186

## Decision Gate

The implementation decision is `UNAVAILABLE`. The Story stops before production
implementation because the repository cannot establish authoritative PAPER CFD
margin semantics.

## No-Change Plan

- Preserve `RequiredMarginPort` validation and fail-closed behavior when an
  authoritative proposed-order margin fact is unavailable or invalid. The
  existing PAPER preview remains runtime-available but non-authoritative.
- Preserve the existing LIVE provider preview path.
- Preserve `SettlementCompatibilityPolicy` rejection of `CASH + CFD`.
- Do not add `PaperCfdRequiredMarginCalculator` or any equivalent formula.
- Do not add a mode-routing adapter that would only rename the unproven source.
- Do not change Paper CFD settlement, risk-domain contracts, or database schema.

## Required Follow-Up Before Implementation

1. Decide whether PAPER simulates a provider/reference margin contract or uses
   an explicitly configured Trading OS margin policy.
2. Specify the authoritative proposed-order semantics, including incremental
   versus total margin, account/exposure interaction, hedge/netting behavior,
   instrument parameters, and currency conversion.
3. Obtain provider evidence or approve the policy and provenance contract.
4. Add a provider-neutral fact adapter only after the above decision is accepted.
5. Test PAPER and LIVE routing independently at T0 and T1.

## Forbidden Shortcuts

- `requiredMargin = notional / leverage` without an approved semantic contract;
- use of `usedMargin`, `freeMargin`, or position margin;
- use of `leverageInCents` as a complete margin model;
- fallback to zero or approval on unavailable margin;
- changing settlement compatibility to make the path appear complete.

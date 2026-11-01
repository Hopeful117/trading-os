# Code Review - Story 0187

## Verdict

**PASS - NO HIGH OR MEDIUM FINDINGS**

ADR-083 is accepted-ready as a documentation proposal, pending explicit human
architectural approval. It does not authorize production implementation.

## Review Scope

The independent review covered:

- PAPER versus LIVE authority;
- provider coupling and migration of the current PAPER preview;
- separation from `InstrumentFinancialContract` and FTMO challenge rules;
- incremental versus total margin semantics;
- account identity and policy assignment;
- existing exposure, netting, and hedging boundaries;
- static, tiered, and dynamic policy scope;
- precision, rounding, currency, FX, and freshness;
- policy provenance, immutability, and T0/T1 races;
- Risk Domain and `RequiredMarginPort` boundaries;
- failure semantics and compatibility gating.

## Previous Findings Resolved

### Canonical account and resolution context

ADR-083 now requires a complete future resolution context containing the financial
account identity, routing correlation, policy identity/version, contract,
exposure snapshot, price fact, reporting currency, valuation context, decision
boundary, and requested time. It explicitly prohibits using `brokerAccountId` as
the PAPER financial identity and requires an application-boundary evolution in a
later implementation Story.

### Precision and rounding

V1 now specifies an eight-decimal maximum margin-rate scale, rejection of excess
precision, exact intermediate `BigDecimal` arithmetic, conversion before final
rounding, explicit currency scale, and positive collateral `CEILING` rounding.

### T0/T1 policy races

T0 captures the immutable policy version atomically with the assignment context.
T1 resolves that exact version rather than the current account default. Concurrent
assignment changes, missing versions, revocation, expiry, or failed ownership
binding fail closed.

### Existing PAPER exposure

ADR-083 does not pretend that the current list of persisted `Trade` records is an
implemented net-position boundary. A later read-side exposure snapshot must
validate and aggregate all open CFD trades under V1 netting semantics, preserve
target-trade settlement behavior, and return unavailable for ambiguous or
unsupported exposure.

### FX determinism

FX semantics now require target-per-source directed legs, ordered acyclic
multiplication, exactly one canonical path, explicit quote-to-reporting rate
agreement, per-leg timestamps/provenance, decision-boundary ordering, and an
explicit valuation `maxObservationAge`.

### Failure diagnostics and preview migration

ADR-083 requires deterministic failure reasons to remain available in Trading Core
evidence even if the final Risk-facing projection remains optional. The current
PAPER provider preview is explicitly non-authoritative and unavailable for PAPER
authorization until replaced; it may only serve labelled import/diagnostic use.

## Confirmed Invariants

```text
PAPER_MARGIN_AUTHORITY = LOCAL_VERSIONED_POLICY
LIVE_MARGIN_AUTHORITY = PROVIDER
PAPER_RUNTIME_BROKER_MARGIN_DEPENDENCY_FOR_AUTHORIZATION = NO
INSTRUMENT_FINANCIAL_CONTRACT_SEPARATE = YES
RISK_DOMAIN_MARGIN_FORMULA = NONE
UNCONFIGURED_MARGIN_POLICY = UNAVAILABLE
UNSUPPORTED_MARGIN_SEMANTICS = UNAVAILABLE
POLICY_PROVENANCE = REQUIRED
POLICY_VERSIONING = REQUIRED
T1_REVALIDATION = REQUIRED
CASH_PLUS_CFD = STILL_REJECTED
INVENTORY_PLUS_CFD = STILL_REJECTED
PRODUCTION_CODE_CHANGED = NO
```

## Residual Risks

- DevLog context was unavailable because authentication was required.
- The repository does not yet implement the canonical account relation, policy
  assignment, exposure snapshot, or FX contract described by ADR-083.
- The later implementation Story must reconcile the future resolution context
  with the current `RequiredMarginPort` request shape before coding the
  calculator.

These are explicitly sequenced implementation prerequisites, not defects in this
documentation-only Story.

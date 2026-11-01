# Story 0187 - PAPER Margin Authority and Simulation Policy ADR

## Metadata

**ID:** `0187`
**Title:** PAPER Margin Authority and Simulation Policy ADR
**Status:** Completed - ADR accepted; implementation deferred to a later Story
**Proposed ADR:** `ADR-083`
**Related ADR:** `ADR-082`
**Related Stories:** `0183`, `0184`, `0185`, `0186`

## Goal

Define and document the authoritative semantics and source of proposed-order
required margin for PAPER trading. CFD is the immediate motivating product, but
the architectural concept must remain provider-neutral and extensible to other
margin-based products.

The output is an accepted-ready ADR proposal precise enough for a later
implementation Story. This Story does not implement a margin calculator, add a
database migration, change `RequiredMarginPort`, or enable `CASH + CFD`.

## Scope

- Decide what a PAPER account simulates and who owns PAPER margin truth.
- Define a provider-neutral, versioned margin policy boundary.
- Define proposed-order required margin, including existing exposure semantics.
- Define V1 support and fail-closed behavior for netting, hedging, tiered, and
  dynamic margin semantics.
- Define account/policy ownership, assignment, provenance, lifecycle, and
  persistence requirements conceptually.
- Define currency, explicit FX evidence, freshness, T0/T1, and policy-change
  semantics.
- Define the separation between `InstrumentFinancialContract` and margin policy.
- Resolve the current PAPER -> Broker Service margin-preview inconsistency.
- Preserve LIVE provider authority and current compatibility blocks.
- Define a concrete implementation sequence for a later Story.
- Produce an independent architecture review of the proposed ADR.

## Out of Scope

- Production Java implementation or a margin calculator.
- `MarginPolicy.java`, REST API, configuration UI, or database migration.
- Changes to `RequiredMarginPort` or Risk Domain formulas.
- Provider mutations, cTrader execution, orders, closes, cancels, or E2E broker
  execution.
- FTMO challenge rules such as daily loss, maximum loss, or profit targets.
- Enabling `CASH + CFD` or `INVENTORY + CFD`.
- Modifying historical ADR-082.
- Creating provider-specific margin semantics in Trading Core.

## Acceptance Criteria

- [ ] A new ADR-083 proposal defines one unambiguous PAPER margin authority
      model and does not leave the current provider preview ambiguously
      authoritative.
- [ ] The ADR defines proposed-order required margin as a precise monetary fact
      and distinguishes it from used, free, position, and post-execution margin.
- [ ] The ADR defines policy scope, ownership, versioning, provenance, lifecycle,
      configuration, persistence implications, and effective-time behavior.
- [ ] The ADR defines canonical margin representation, precision, validation,
      rounding, currency, explicit FX provenance, and missing-FX behavior.
- [ ] The ADR explicitly defines V1 existing-exposure, netting/hedging, static /
      tiered / dynamic policy support and fail-closed unsupported cases.
- [ ] The ADR defines deterministic T0/T1 behavior, including policy changes
      between decision boundaries.
- [ ] The ADR preserves `RequiredMarginPort.Fact` as the risk application
      boundary, keeps Risk Domain provider-neutral, and does not put account
      margin semantics into `InstrumentFinancialContract`.
- [ ] The ADR preserves LIVE provider authority, human authorization, and the
      current `CASH + CFD` and `INVENTORY + CFD` rejection.
- [ ] The ADR rejects uncontracted notional/leverage inference, current-margin
      substitution, per-decision PAPER provider calls, skipped PAPER margin,
      and provider-specific Trading Core semantics.
- [ ] A later implementation sequence is defined without implementing it in this
      Story.
- [ ] Story artifacts include repository analysis, implementation plan,
      implementation report, code review, and engineering report as the workflow
      progresses.

## Invariants

```text
PAPER_MARGIN_AUTHORITY = VERSIONED_LOCAL_MARGIN_POLICY
LIVE_MARGIN_AUTHORITY = PROVIDER
PAPER_RUNTIME_BROKER_MARGIN_DEPENDENCY = NO
PAPER_MARGIN_POLICY = PROVIDER_NEUTRAL
INSTRUMENT_FINANCIAL_CONTRACT_SEPARATE_FROM_MARGIN_POLICY = YES
RISK_DOMAIN_MARGIN_FORMULA = NONE
POLICY_PROVENANCE = REQUIRED
POLICY_VERSIONING = REQUIRED
UNCONFIGURED_MARGIN_POLICY = UNAVAILABLE
UNSUPPORTED_MARGIN_SEMANTICS = UNAVAILABLE
T1_REVALIDATION = REQUIRED
CASH_PLUS_CFD = REJECTED
INVENTORY_PLUS_CFD = REJECTED
```

## Workflow Boundary

Only the Story and Repository Analysis are produced initially. Implementation
Planning requires explicit human approval of the Repository Analysis. The ADR
proposal and remaining artifacts are produced only after that approval.

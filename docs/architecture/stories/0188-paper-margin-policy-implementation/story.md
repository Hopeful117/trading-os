# Story 0188 - Implement PAPER Margin Policy and Authority Boundary

## Metadata

**ID:** `0188`
**Title:** Implement PAPER Margin Policy and Authority Boundary
**Status:** Draft - Repository Analysis not started
**Related ADR:** `ADR-083`
**Related ADRs:** `ADR-028`, `ADR-043`, `ADR-051`, `ADR-078`, `ADR-082`
**Predecessor Story:** `0187`

## Goal

Implement the accepted ADR-083 architecture so PAPER proposed-order required
margin is resolved locally from an immutable, versioned, provider-neutral margin
policy rather than from a Broker Service/provider preview.

This Story must make the PAPER margin boundary implementable and reproducible for
the V1 supported model without enabling `CASH + CFD`.

## Scope

- Establish the canonical financial-account to execution-routing-account binding
  required by the margin resolution context.
- Introduce a provider-neutral immutable `MarginPolicy` model and versioned
  assignment for PAPER accounts or the explicitly owned simulation profile.
- Persist or otherwise durably resolve policy identity/version for historical
  risk evidence and T0/T1 revalidation.
- Introduce the validated PAPER exposure snapshot boundary for open CFD trades.
- Implement V1 static margin-rate, linear, netting-only incremental required
  margin semantics.
- Implement explicit precision, positive-collateral rounding, reporting-currency
  conversion, and Market Data FX provenance/freshness rules from ADR-083.
- Preserve deterministic failure reasons at the Trading Core application
  boundary while keeping Risk Domain provider-neutral.
- Route PAPER required-margin resolution to local policy state.
- Preserve LIVE provider-authoritative required-margin resolution.
- Remove PAPER provider preview from the authorization path; retain it only as
  explicitly labelled configuration-import or diagnostic evidence if required.
- Add T0/T1, policy-version, exposure, FX, provenance, restart, and fail-closed
  regression coverage.

## Out of Scope

- Enabling `CASH + CFD` or `INVENTORY + CFD`.
- Changing `SettlementCompatibilityPolicy`.
- Tiered, dynamic, hedged, portfolio-offset, or cross-instrument margin models.
- Provider-specific margin semantics in Trading Core.
- cTrader order placement, cancellation, close, or safety execution.
- FTMO challenge rules, daily loss, maximum loss, or profit targets.
- Changes to Risk Domain formulas or provider branching.
- Automatic migration of existing trades without an approved compatibility path.
- Provider mutations or live execution behavior changes.

## V1 Semantics

```text
PAPER_MARGIN_AUTHORITY = LOCAL_VERSIONED_POLICY
MARGIN_POLICY = PROVIDER_NEUTRAL
MARGIN_REPRESENTATION = STATIC_POSITIVE_MARGIN_RATE
EXPOSURE_MODEL = SIGNED_NET_EXPOSURE_PER_ACCOUNT_AND_INSTRUMENT
NETTING = SUPPORTED
HEDGING = UNSUPPORTED
TIERED_MARGIN = UNSUPPORTED
DYNAMIC_MARGIN = UNSUPPORTED
```

For an existing signed exposure `q0` and signed proposed order `dq`:

```text
q1 = q0 + dq
requiredMargin = max(margin(q1) - margin(q0), 0)
margin(q) = abs(q) * referencePrice * contractMultiplier * marginRate
```

The calculation occurs in quote currency and converts to the account reporting
currency through exactly one explicit directed Market Data FX path when needed.
Unsupported or ambiguous facts fail closed.

## Required Resolution Context

The implementation must not use `brokerAccountId` as the PAPER financial account
identity. Margin resolution must prove a canonical context containing:

- financial account identity;
- routing broker-account correlation, when applicable;
- immutable policy identity and version;
- canonical financial-contract identity and version;
- validated current exposure snapshot and version;
- proposed signed quantity and reference-price fact;
- account reporting currency;
- FX/valuation context;
- T0/T1 decision boundary and request timestamp.

## Acceptance Criteria

- [ ] PAPER margin resolution uses a local immutable versioned policy and does
      not require Broker Service availability.
- [ ] Financial `Account` identity and `BrokerAccount` routing identity are
      explicitly bound and ownership-validated.
- [ ] An unconfigured, missing, revoked, expired, ambiguous, or unsupported
      policy returns deterministic unavailable/fail-closed behavior.
- [ ] V1 supports static positive margin rates with maximum eight-decimal rate
      precision and rejects excessive precision.
- [ ] Intermediate arithmetic is exact; quote-to-reporting conversion precedes
      final positive collateral rounding with explicit currency scale and
      `CEILING` behavior.
- [ ] Exactly one canonical directed FX path is required for cross-currency
      conversion; missing, stale, ambiguous, or reverse paths fail closed.
- [ ] PAPER exposure is reconstructed from a validated read-side snapshot of all
      open CFD trades for the canonical account and instrument.
- [ ] Same-direction increases, partial reductions, full closes, and reversals
      follow ADR-083 netting semantics.
- [ ] Hedging, tiered, dynamic, portfolio-offset, and cross-instrument cases fail
      closed as unsupported.
- [ ] T0 captures the immutable policy version and context for evidence.
- [ ] T1 reuses the exact T0 policy version with fresh market, exposure, and FX
      facts; policy revocation/expiry/missing versions fail closed.
- [ ] Risk Domain receives only normalized monetary required margin and remains
      provider-neutral.
- [ ] LIVE required-margin authority remains provider-backed and unchanged.
- [ ] PAPER provider preview is not used for authorization and is explicitly
      labelled if retained for diagnostics or configuration import.
- [ ] Deterministic failure reasons remain available in Trading Core evidence.
- [ ] Historical/restart reconstruction preserves policy identity/version and
      required provenance.
- [ ] `CASH + CFD` and `INVENTORY + CFD` remain rejected.
- [ ] No broker orders, closes, cancels, or cTrader safety execution are added.

## Validation Expectations

- Focused policy, assignment, identity-binding, and exposure-snapshot tests.
- Focused static margin, netting transition, precision, rounding, FX, and
  failure-reason tests.
- T0/T1 policy-version and fresh-fact parity tests.
- Restart/reproducibility and provenance tests.
- PAPER no-Broker-Service authorization tests.
- LIVE provider-authority regression tests.
- Existing SPOT, `LINEAR_PERPETUAL`, settlement, and compatibility tests.
- Trading Core quality validation and `git diff --check`.
- Independent review focused on authority inversion, account identity, exposure
  semantics, currency determinism, provenance, and compatibility gating.

## Safety Invariants

```text
PAPER_RUNTIME_BROKER_MARGIN_DEPENDENCY_FOR_AUTHORIZATION = NO
LIVE_MARGIN_AUTHORITY = PROVIDER
RISK_DOMAIN_MARGIN_FORMULA = NONE
UNCONFIGURED_MARGIN_POLICY = UNAVAILABLE
UNSUPPORTED_MARGIN_SEMANTICS = UNAVAILABLE
T1_REVALIDATION = REQUIRED
CASH_PLUS_CFD = REJECTED
INVENTORY_PLUS_CFD = REJECTED
CTRADER_SAFETY_EXECUTION_ENABLED = false
```

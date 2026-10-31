# Implementation Plan - Story 0188

## Objective

Implement the accepted ADR-083 PAPER margin authority boundary without changing
settlement compatibility or introducing provider-specific economics into Risk
Domain.

The implementation must be additive, fail-closed, reproducible, and compatible
with the existing Account/BrokerAccount and risk-evidence lifecycle.

## Phase A - Canonical Account Context

1. Reuse `Account.brokerAccountId` and the existing foreign-key relation as the
   canonical financial-to-routing binding.
2. Add one application resolver that loads the financial `Account`, linked
   `BrokerAccount`, owner, execution mode, and reporting currency together.
3. Reject missing, mismatched, revoked, or ambiguous bindings before margin
   policy resolution.
4. Keep provider registration identity separate from both account identities.
5. Ensure T0 and T1 use the same canonical context rules; do not add provider or
   display-name lookup fallbacks.

## Phase B - Immutable Margin Policy Persistence

1. Introduce a provider-neutral `MarginPolicy` aggregate/value model in Trading
   Core, separate from `InstrumentFinancialContract` and risk-rule profiles.
2. Persist immutable policy versions using the established `(id, version)` pattern
   used by `RiskProfileEntity`.
3. Persist account assignment with policy ID/version, assigned time, effective
   time, provenance, and optimistic versioning.
4. Make existing accounts without an assignment explicitly unavailable; do not
   seed an implicit margin policy.
5. Preserve policy versions referenced by risk evidence; never mutate or delete a
   referenced version.
6. Add a migration with explicit constraints for static rate precision, positive
   values, supported scopes, rounding metadata, and provenance.

## Phase C - Policy and Resolution Contracts

1. Define the complete application resolution context:

   ```text
   financialAccountId
   brokerAccountId (routing correlation)
   policyId + policyVersion
   contractId + contractVersion
   exposureSnapshotId + exposureVersion
   reference price fact
   reporting currency
   valuation/FX snapshot context
   decision boundary and requestedAt
   ```

2. Evolve `RequiredMarginPort.Request` or introduce a dedicated mode-aware
   resolver so the current broker-keyed request cannot remain the PAPER authority
   boundary.
3. Keep the final `Fact` monetary and provider-neutral.
4. Preserve deterministic reason codes at the Trading Core application/evidence
   boundary while projecting unavailable results safely to existing Risk callers.
5. Define policy-rule precedence as exact instrument rule, then product rule,
   then explicitly configured account default; reject unresolved or multiply
   matched rules.

## Phase D - PAPER Exposure Snapshot

1. Add a read-side exposure resolver for the canonical financial Account and
   instrument.
2. Load all open CFD `Trade` records consistently under the Account boundary.
3. Validate product type, signed non-zero quantity, contract identity/version,
   account ownership, status, and required timestamps.
4. Aggregate signed quantities for V1 netting-only semantics.
5. Preserve individual target-trade settlement behavior; this resolver must not
   rewrite or merge persisted trades.
6. Fail closed for missing rows, conflicting contracts, ambiguous positions,
   hedging semantics, pending/partial-fill states, or incomplete snapshots.
7. Persist the exposure snapshot identity/version and source trade versions in
   risk evidence.

## Phase E - Deterministic V1 Calculation

1. Accept only static positive margin rates with at most eight fractional decimal
   places.
2. Use exact `BigDecimal` intermediate arithmetic without implicit `MathContext`.
3. Calculate in instrument quote currency:

   ```text
   margin(q) = abs(q) * referencePrice * contractMultiplier * marginRate
   required  = max(margin(existing + proposed) - margin(existing), 0)
   ```

4. Treat reductions and full closes as zero new required margin.
5. Calculate reversal margin from the resulting residual exposure.
6. Reject hedging, tiered, dynamic, portfolio-offset, and cross-instrument cases.
7. Convert quote currency to reporting currency through exactly one canonical
   directed Market Data FX path before rounding.
8. Validate path direction, positive rates, provenance, effective/capture times,
   composed rate agreement, decision-boundary ordering, and max observation age.
9. Round positive final collateral toward positive infinity (`CEILING`) using the
   explicit reporting-currency scale; reject missing or conflicting scale data.

## Phase F - Mode-Aware Integration

1. Route PAPER `RequiredMarginPort` resolution to the local policy/exposure
   resolver.
2. Preserve LIVE provider-authoritative resolution through Broker Service.
3. Remove PAPER provider preview from authorization; if retained, expose it only
   as labelled diagnostic/configuration-import evidence.
4. Do not use provider preview as a fallback for missing PAPER policy or facts.
5. Preserve Risk Domain's normalized monetary input boundary and avoid provider or
   margin-policy branches inside Risk Domain.

## Phase G - T0/T1 and Evidence

1. At T0, atomically capture assignment, immutable policy version, account
   context, contract, exposure, market/FX facts, calculation inputs, and reason
   codes in existing risk evidence.
2. At T1, resolve the exact T0 policy version rather than the current account
   default.
3. Reacquire fresh T1 price, exposure, account binding, and FX facts under the
   execution-time freshness policy.
4. Fail closed for missing, revoked, expired, or unresolvable T0 policy versions,
   concurrent assignment ambiguity, or changed account binding.
5. Never silently substitute a new policy version into an in-flight decision.

## Phase H - Validation

Add focused tests for:

- Account/BrokerAccount identity and ownership binding;
- immutable policy creation, assignment, version lookup, and missing assignment;
- policy precedence and ambiguous rules;
- rate precision, exact arithmetic, currency scale, and `CEILING` rounding;
- opening, increase, partial reduction, close, and reversal transitions;
- exposure snapshot completeness and conflicting/hedged cases;
- directed FX path, freshness, ambiguity, and composed-rate validation;
- T0/T1 policy version reuse and concurrent assignment behavior;
- deterministic failure reason preservation;
- PAPER no-call to Broker Service for authorization;
- LIVE provider-authority regression;
- restart/reconstruction provenance;
- existing SPOT, `LINEAR_PERPETUAL`, settlement, and compatibility behavior.

Run the repository-appropriate Trading Core tests, affected migration/integration
tests, `git diff --check`, and the quality-validation commands required by the
repository.

## Explicit Non-Changes

- Do not enable `CASH + CFD` or `INVENTORY + CFD`.
- Do not change `SettlementCompatibilityPolicy`.
- Do not add provider-specific semantics to Risk Domain or financial contracts.
- Do not implement hedging, tiered, dynamic, or portfolio margin.
- Do not submit, cancel, close, or modify broker orders.
- Do not enable cTrader safety execution.
- Do not create FTMO challenge rules in the margin policy.

## Implementation Stop Conditions

Stop and request architectural guidance if implementation reveals:

- the existing Account/BrokerAccount relation cannot provide the required
  canonical binding;
- the current risk evidence cannot preserve policy/exposure provenance without an
  unapproved architectural change;
- the Market Data contract cannot provide exactly one directed fresh FX path;
- existing PAPER trades cannot be represented by the V1 netting exposure model;
- compatibility would need to change to make tests pass.

## Approval Boundary

This Implementation Plan requires explicit human approval before production
implementation begins.

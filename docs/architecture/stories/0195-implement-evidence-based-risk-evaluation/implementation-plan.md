# Implementation Plan

## Overview

Implement ADR-086 by extending the existing deterministic risk contracts rather
than creating a generic rule framework or a parallel execution path.

The implementation will separate three concerns that are currently coupled:

1. which evidence each configured invariant needs;
2. whether that invariant can be evaluated from the current snapshot;
3. whether the complete policy is safe to authorize.

Independent invariants will produce deterministic diagnostics when their facts
are complete. The aggregate result will remain without an executable decision
until every mandatory applicable invariant is complete and passing. The same
requirement resolution will be used by initial validation and T1.

No cTrader investigation, provider call, policy change, new service or order
operation is planned.

## Design Constraints

- Reuse `RiskStatus`/`RuleStatus` vocabulary where safe. Add an
  `INCOMPLETE` value to the existing per-rule `RuleStatus` only if the current
  `FAILURE` representation cannot distinguish a violated invariant from missing
  evidence. Do not create a second status enum.
- Keep rule evaluators deterministic and provider-neutral.
- Do not create synthetic `Money` values for missing facts.
- Do not change `FTMO_2STEP_50K_RISK` thresholds, versions or provenance policy.
- Preserve the existing aggregate fail-closed behavior and strengthen it where
  a mandatory rule was not evaluated.
- Keep provider decoding and evidence construction outside `risk-domain`.

## Planned Changes

### 1. Define invariant-specific evidence requirements

**Components:** `RiskFactRequirements`, risk rule registry/metadata and related
Trading Core risk ports.

- Extend the existing requirements contract with immutable requirements keyed by
  configured rule ID rather than adding a generic rule engine.
- Resolve the requirements once from the effective policy. Each entry will
  identify the facts needed to build and evaluate that invariant, including
  account, position, stop, equity, baseline and starting-balance requirements
  where applicable.
- Retain an aggregate union for broker snapshot loading, but use the per-rule
  entries for admission and diagnostics.
- Preserve policy-controlled equity provenance and quality requirements.
- Ensure the requirement model can identify mandatory, applicable and
  explicitly not-applicable rules without treating absent data as not applicable.

### 2. Preserve evidence through broker and Trading Core snapshots

**Components:** `BrokerModels.AccountRiskFacts`, `BrokerRiskFactsPort`,
`RiskFactsProvider`, broker risk snapshot mappers and affected adapters.

- Extend `AccountRiskFacts` with the existing neutral equity evidence contract,
  or an equivalent reusable value object, rather than duplicating provenance
  fields.
- Preserve equity value, provenance, availability, quality flags, timestamps,
  source, currency and account binding through the mapping path.
- Treat legacy numeric equity without admissible evidence as unknown for strict
  policies.
- Preserve existing Kraken behavior and cTrader unavailable-equity behavior.
- Add contract tests proving that evidence metadata is not dropped.

### 3. Make Risk Domain evaluation evidence-aware

**Components:** `AccountSnapshot`, `ObservedMetricsCalculator`,
`ProjectionEngine`, `DerivedMetricsCalculator`, `DeterministicRiskEngine`,
`RiskRuleResult` and `RiskResultAggregator`.

- Represent optional financial inputs explicitly at the normalized Risk Domain
  boundary, avoiding nulls and synthetic zero values.
- Compute only metrics whose inputs are present and coherent. A missing equity
  must not prevent position-risk or stop-protection diagnostics when those
  inputs are complete.
- Add an explicit per-rule incomplete result using the existing status model or
  its safe extension, with stable reason codes such as missing equity, missing
  baseline, incomplete positions or inadmissible provenance.
- Preserve existing deterministic evaluators for rules that receive complete
  metrics.
- Ensure `NOT_APPLICABLE` is emitted only from explicit rule applicability and
  not from missing facts.
- Keep `RiskResultAggregator` decision empty/blocking for any incomplete or
  unevaluated mandatory rule, even when other rules pass.

The implementation must avoid constructing an `AccountSnapshot` that claims to
contain equity when the provider did not supply admissible equity. If the
existing snapshot type cannot express this safely, evolve it to explicit
optionality and update metric consumers together rather than adding a fallback.

### 4. Align initial evaluation and T1

**Components:** `TradePlanRiskEvaluationService`,
`ExecutionTimeRiskRevalidationService`, `ModeAwareRiskFactsProvider` and shared
risk requirement resolution.

- Remove only the global precondition that blocks unrelated invariant
  diagnostics when one fact is missing.
- Retain policy-level blocking for any mandatory invariant whose evidence is
  absent, stale, incoherent or inadmissible.
- Pass the same per-invariant requirements and evidence diagnostics into the
  initial Risk Domain evaluation and T1 revalidation.
- Ensure T1 never trusts a T0 partial result and independently rechecks facts,
  freshness, identity, currency and coherence.
- Preserve the existing no-execution path when aggregate authorization is not
  available.

### 5. Tests and regression protection

**Risk Domain tests**

- Position risk and stop protection evaluate without equity when their evidence
  is complete.
- Equity-dependent rules return `INCOMPLETE` with explicit reasons when equity
  is absent, stale, incoherent or inadmissible.
- Missing baseline, starting balance, positions or stops are not treated as
  zero or not applicable.
- A mandatory rule not evaluated blocks the aggregate decision.
- Explicit policy non-applicability remains distinguishable from missing data.

**Trading Core tests**

- Reported admissible equity permits normal evaluation.
- Derived or unknown equity remains inadmissible for FTMO.
- Partial snapshots produce diagnostics but no authorization.
- T0 and T1 use equivalent requirements and T1 blocks changed facts.
- No Broker Service execution request is created for incomplete aggregate risk.

**Broker Service contract tests**

- Equity evidence is preserved through `RiskSnapshot.AccountRiskFacts` and the
  provider-neutral mapping.
- Kraken snapshot behavior remains unchanged.
- cTrader unavailable equity remains unavailable and fail-closed.

Run focused tests first, then affected module suites, and finish with
`git diff --check`.

## Files Expected To Change

- `risk-domain/src/main/java/com/hope/trading/risk/domain/RiskTypes.java`
- `risk-domain/src/main/java/com/hope/trading/risk/snapshot/AccountSnapshot.java`
- `risk-domain/src/main/java/com/hope/trading/risk/metric/ObservedMetricsCalculator.java`
- `risk-domain/src/main/java/com/hope/trading/risk/metric/ProjectionEngine.java`
- `risk-domain/src/main/java/com/hope/trading/risk/metric/DerivedMetricsCalculator.java`
- `risk-domain/src/main/java/com/hope/trading/risk/engine/DeterministicRiskEngine.java`
- `risk-domain/src/main/java/com/hope/trading/risk/engine/RiskResultAggregator.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/port/RiskFactRequirements.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/port/RiskFactsProvider.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/port/BrokerRiskFactsPort.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/domain/model/BrokerModels.java`
- affected T0/T1 risk services and mappers
- focused tests in the affected modules

The exact file set will be confirmed before editing after compilation and call
site inspection. Existing unrelated working-tree modifications will not be
rewritten.

## Ordering

1. Confirm current result and snapshot call sites and compile boundaries.
2. Implement the neutral evidence and per-invariant requirement contracts.
3. Update Risk Domain optional metric/evaluation flow.
4. Update Trading Core mappings and T0/T1 admission.
5. Add focused tests and preserve provider regression coverage.
6. Run quality validation and inspect the complete diff.

## Risks and Stop Conditions

- If making normalized account facts optional would require a broad unrelated
  architectural rewrite, stop and report the boundary instead of introducing a
  synthetic value.
- If existing persisted risk records cannot represent incomplete per-rule
  diagnostics without migration, stop before changing persistence semantics.
- If tests reveal that a current policy depends on an unlisted fact, update the
  requirement matrix and tests before implementation continues.
- Do not resolve any ambiguity by weakening FTMO admissibility or execution
  gates.

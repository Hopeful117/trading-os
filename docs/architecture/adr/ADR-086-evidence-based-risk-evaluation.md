# ADR-086 - Evidence-Based Risk Evaluation

## Status

Accepted - decision provided by product and architecture request

## Date

2026-11-04

## Decision Type

Risk evidence requirements, partial deterministic evaluation and fail-closed execution

## Related ADRs

- ADR-028 - Deterministic Risk Engine Architecture
- ADR-043 - Account Identity and Mode-Specific Risk Facts
- ADR-071 - FTMO Challenge Target and cTrader Broker Boundary
- ADR-074 - cTrader Authoritative READ-ONLY Synchronization Boundary
- ADR-075 - Provider-Neutral Multi-Phase Challenge Domain
- ADR-078 - PAPER/LIVE Execution Boundary
- ADR-082 - Provider-Independent CFD Financial Model and Mark-to-Market Accounting
- ADR-084 - LIVE Financial Facts and Provider Preflight Readiness
- ADR-085 - Broker Equity Resolution and Provenance Contract

## Related Stories

- Story 0194 - Implement Broker Equity Provenance and Admissibility Contract
- Story 0195 - Implement Evidence-Based Risk Evaluation

## 1. Context

The deterministic Risk Domain currently receives an account snapshot with
global financial requirements even when the selected policy contains rules with
different evidence dependencies. For example, position risk can be evaluated
from balance, position and stop-loss facts, while drawdown rules require
admissible equity and account baselines.

ADR-084 and ADR-085 already require capability-scoped facts, explicit
provenance, quality validation and fail-closed behavior for missing or
inadmissible financial evidence. Those decisions do not require every invariant
to become unavailable when an independent invariant has sufficient evidence.

## 2. Problem

Global fact requirements conflate:

- evidence required to evaluate one invariant;
- evidence required to complete the whole policy evaluation;
- evidence required to authorize execution.

This reduces useful diagnostics and makes the Risk Domain more constrained than
the business invariants require. Removing the global conflation must not allow
an incomplete policy evaluation to authorize an order.

## 3. Decision

Trading OS SHALL express evidence requirements per risk invariant and evaluate
each invariant independently when its required facts are complete, coherent,
fresh and policy-admissible.

The evaluation result SHALL distinguish, directly or through an equivalent
existing representation:

```text
PASS
FAIL
INCOMPLETE
NOT_APPLICABLE
```

`NOT_APPLICABLE` is valid only when the selected policy explicitly determines
that the invariant does not apply. Missing data SHALL produce `INCOMPLETE`, not
`NOT_APPLICABLE`, zero, false or an inferred value.

The aggregate evaluation SHALL remain fail-closed. A global execution decision
is available only when every mandatory applicable invariant was evaluated, all
required evidence was admissible and fresh, no invariant failed, and all
existing identity, coherence, T0/T1 and human-authorization gates passed.
Any mandatory `FAIL`, `INCOMPLETE`, missing evaluation, stale or inadmissible
fact, or incoherent snapshot blocks execution.

Partial evaluation is diagnostic and may return results for independently
provable invariants. It is never execution authorization.

Financial fact provenance, quality, account identity, currency, timestamps and
completeness SHALL be preserved from Broker Service through Trading Core into
the evidence consumed by the Risk Domain. Legacy numeric values without the
required provenance remain unknown or inadmissible when the policy requires
demonstrated provenance.

The same evidence requirements and fail-closed aggregation SHALL apply during
initial validation and execution-time T1 revalidation. No new microservice is
introduced and the Risk Domain remains provider-neutral.

## 4. Invariant Evidence Boundary

The implementation SHALL derive requirements from the actual rule definitions.
The initial target is:

| Invariant | Minimum evidence boundary |
|---|---|
| `MAX_POSITION_RISK` | Balance, complete position facts, stop and monetary loss evidence |
| `MAX_EXPOSURE` | Complete projected exposure and admissible equity under the policy definition |
| `DAILY_DRAWDOWN` | Admissible equity, daily baseline and required PnL evidence |
| `MAX_TOTAL_DRAWDOWN` | Admissible equity and account starting balance |
| Stop protection | Complete positions, protective orders and protected quantities |

The boundary is not a permission to approximate missing facts. Each evaluator
must continue to use deterministic normalized financial values.

## 5. Aggregation and Execution Safety

The Risk Domain may expose a completed result for one invariant while another
is `INCOMPLETE`. The aggregate policy status must remain incomplete and expose
no executable approval in that case.

`NOT_APPLICABLE` must be produced only by explicit policy applicability, never
because a fact is absent. T1 must consume the same requirements and reject any
execution attempt whose mandatory invariant set is not complete at the T1
boundary.

## 6. Compatibility

This ADR refines the fact-consumption boundary established by ADR-084 and ADR-
085. It does not change FTMO thresholds, rule semantics, policy versions or the
current rejection of unavailable or non-admissible equity for equity-dependent
FTMO rules. It does not make cTrader LIVE-ready and does not authorize any
provider execution.

Kraken and other providers may expose more complete evidence, but provider
adapters remain responsible only for provider-neutral fact acquisition and
provenance. FTMO policy admissibility remains in Trading Core and deterministic
invariant evaluation remains in the Risk Domain.

## 7. Rejected Alternatives

- Requiring equity globally: safe but unnecessarily prevents independent
  invariant diagnostics.
- Ignoring rules whose evidence is missing: unsafe because it can authorize an
  incomplete policy evaluation.
- Setting `equity = balance`: not financially equivalent and not admissible.
- Numeric zero, stale-value or arbitrary-margin fallbacks: conceal missing
  evidence and violate fail-closed safety.
- Bypassing the Risk Engine: violates deterministic authorization boundaries.
- Adding a cTrader Algo bridge: outside this decision and not required to define
  the provider-neutral evidence model. A future bridge would require its own
  validation and approval.

## 8. Consequences

Positive consequences:

- independently provable invariants remain useful for diagnostics;
- incomplete evidence is explicit and explainable;
- policy-level authorization remains fail-closed;
- provider-specific evidence remains outside the Risk Domain.

Costs and risks:

- rule results and diagnostics require evidence-aware contracts;
- T0 and T1 must share the same requirement resolution;
- snapshot mappings must preserve equity provenance instead of carrying only a
  numeric value;
- existing tests and consumers must distinguish partial diagnostics from an
  executable approval.

## 9. Safety Invariants

```text
EVIDENCE_REQUIREMENTS_PER_INVARIANT = true
PARTIAL_EVALUATION_IS_DIAGNOSTIC_ONLY = true
INCOMPLETE_MANDATORY_INVARIANT = EXECUTION_BLOCKED
MISSING_FACT != NOT_APPLICABLE
MISSING_FACT != ZERO
EQUITY_BALANCE_FALLBACK = false
EQUITY_PROVENANCE_MUST_BE_PRESERVED = true
T0_REQUIREMENTS == T1_REQUIREMENTS = true
RISK_DOMAIN_PROVIDER_NEUTRAL = true
FTMO_POLICY_VERSION_UNCHANGED = true
CTRADER_LIVE_AUTHORIZATION = false
```

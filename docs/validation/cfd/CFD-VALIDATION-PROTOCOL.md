# Trading OS CFD Validation Protocol

## Purpose

This protocol qualifies the current provider-neutral CFD capability against
ADR-082, ADR-083, ADR-051, and the current Trading Core implementation. It
separates implementation evidence from final capability validation and preserves
the fail-closed compatibility boundary.

The protocol does not create an Engineering Story and does not authorize a
production compatibility change.

## Vocabulary

| State | Meaning |
|---|---|
| `IMPLEMENTED` | Production code exists for the capability. |
| `PROVEN` | Required behavior has reproducible executable evidence. |
| `VALIDATED` | Every mandatory gate passed through the approved application path. |

Allowed gate results are `PASS`, `FAIL`, `BLOCKED`, `NOT_RUN`, and
`NOT_APPLICABLE`. `PARTIAL PASS` is not a result; incomplete evidence remains
`BLOCKED`.

## Current Architecture Map

The current production boundaries are:

| Responsibility | Current implementation |
|---|---|
| Contract identity | `InstrumentFinancialContract`, `InstrumentProductType`, `FinancialContractProvenance` |
| CFD calculation | `CfdFinancialCalculator` |
| CFD risk projection | `CfdRiskProjectionService` |
| T0 risk | `TradePlanRiskEvaluationService` |
| T1 risk | `ExecutionTimeRiskRevalidationService` |
| PAPER margin authority | `MarginPolicy`, `MarginPolicyPersistence`, `PaperMarginCalculator`, `PaperRequiredMarginClient` |
| PAPER execution routing | `ExecutionFinalizationStep`, `SimulatedExecutionAdapter` |
| PAPER CFD settlement | `PaperCfdSettlementService` |
| CFD valuation/MTM | `CfdValuationService`, `PaperDerivativeAccountValuationService` |
| Generic PAPER settlement | `PaperSettlementService` |
| Exactly-once provenance | `TradeExecutionProvenanceStore`, `JpaTradeExecutionProvenanceStore`, PostgreSQL unique identity and atomic insert |
| Compatibility authority | `SettlementCompatibilityPolicy` |

The economic model is provider-neutral. cTrader metadata is adapted at the
Market Data boundary and must not define Trading Core financial calculations.
CFD quantity is signed underlying-equivalent quantity; notional and PnL use the
contract multiplier; CFD exposure is cash-settled mark-to-market and does not
create base-asset inventory.

## Governing Decisions and Historical Evidence

Current authority:

- `docs/architecture/adr/ADR-051.md`: compatibility remains fail-closed.
- `docs/architecture/adr/ADR-081-ctrader-instrument-product-classification.md`:
  product identity is explicit and does not authorize settlement.
- `docs/architecture/adr/ADR-082-cfd-financial-model-and-mark-to-market-accounting.md`:
  provider-independent CFD economics and validation requirements.
- `docs/architecture/adr/ADR-083-paper-margin-authority-and-simulation-policy.md`:
  immutable local PAPER margin policy and provider-authoritative LIVE margin.

Historical evidence is traceability, not an automatic current PASS. Relevant
historical material is in Stories 0178-0192, especially 0181-0184, 0186-0189,
0190, 0191, and 0192. Current production code and current executable evidence
override historical status labels when they differ.

## Safety Invariants

Every run must preserve:

```text
AUTOMATIC_EXECUTION = NO
HUMAN_AUTHORIZATION_REQUIRED = YES
CTRADER_SAFETY_EXECUTION_ENABLED = false
REAL_CTRADER_ORDERS = 0
REAL_CTRADER_CLOSES = 0
PROVIDER_MUTATIONS = 0
CASH_PLUS_CFD = REJECTED outside an explicitly authorized conditional G7 run
INVENTORY_PLUS_CFD = REJECTED
UNKNOWN_PLUS_CFD = REJECTED
```

## Mandatory Gates

### G1 — CFD Contract and Product Semantics

Validate the explicit provider-neutral CFD representation:

- `ProductType.CFD` or the repository equivalent is explicit.
- `InstrumentFinancialContract` carries identity, version/provenance, base and
  quote currency, quantity unit, multiplier, precision and constraints.
- Signed quantity semantics distinguish long and short.
- Missing, conflicting, or incomplete metadata fails closed.
- Required economics do not infer meaning from symbol strings or cTrader
  volume fields.
- CFD is not SPOT and is not inventory ownership.

Evidence must include production classes, calculator tests, contract/risk tests,
and current fail-closed tests. PASS requires both implementation and executable
evidence.

### G2 — Margin Authority

Validate ADR-083:

- PAPER uses immutable local `MarginPolicy` authority.
- LIVE remains provider-authoritative.
- Policy identity, version, provenance, scope, rate, precision and effective
  period are durable.
- Assignment is tied to the canonical financial account and survives reload.
- Policy versions are immutable; no mutable global default exists.
- PAPER authorization does not require a Broker Service margin call.
- `RequiredMarginPort` resolves a complete, provenance-bearing fact.
- Missing, stale, ambiguous, unsupported or invalid policy/FX/exposure facts fail
  closed.
- The deterministic fixture `1000 * 1.10 * 1% = 11.00 USD` is proven through
  production calculation.

Required evidence includes `MarginPolicyPostgresIntegrationTest`, relevant
`PaperRequiredMarginClientTest`/calculator tests, migration tests, and current
Trading Core validation.

### G3 — Risk and T0/T1 Revalidation

Validate the real application orchestration:

- T0 persists a decision using T0 facts and freshness.
- Human authorization remains a separate boundary.
- T1 invokes independent production revalidation using fresh execution-time
  facts and the immutable T0 policy context.
- T1 uses authoritative `Fact.sourcePrice`, not the T0 price.
- Missing, stale, mismatched, or unavailable T1 facts fail closed.
- A real scenario proves `T0 PASS -> human authorization -> authoritative fact
  change -> T1 REJECT -> no execution/fill/settlement/financial mutation`.

A mocked T1 decision is insufficient. Controlled infrastructure facts may be
used only when the production T0/T1 services, persistence, lifecycle and
application boundaries execute.

### G4 — CASH CFD Accounting Semantics

Validate ADR-082 economic meaning independently of compatibility authorization:

- Opening CFD creates signed cash exposure.
- Opening CFD does not create base-asset inventory.
- Opening CFD does not debit full notional as a SPOT purchase.
- Margin is collateral authority, not an underlying purchase.
- MTM is distinct from realized settlement and does not mutate entry price.
- EUR/USD CFD behavior cannot appear as EUR inventory ownership.

Positive accounting evidence must execute through an approved internal boundary
only when the protocol can prove the boundary is authorized. Disabled positive
tests are not PASS evidence; they leave this gate BLOCKED.

Cost classification is explicit:

| Cost | Current classification | Closure implication |
|---|---|---|
| Commission/explicit fill fee | `SUPPORTED_EXPLICITLY` where the production settlement path persists `BrokerOrder.Fill.fee` | Requires deterministic non-zero evidence if G4/G5 is active |
| Spread | `CAPTURED_IN_AUTHORITATIVE_FILL_PRICE` for PAPER execution, which fills BUY at ask and SELL at bid | No separate spread charge may be added or double-counted |
| Slippage | `CAPTURED_IN_AUTHORITATIVE_FILL_PRICE` when execution differs from the persisted reference price | The actual authoritative fill is used for entry/exit PnL; no second slippage fee may be invented |
| Financing/swap | `EXPLICITLY_OUT_OF_SCOPE` for the current ADR implementation boundary | Must remain documented and cannot be claimed as validated |

Spread and slippage are distinct economic concepts, but the current PAPER
execution contract persists the authoritative executable fill price. The
simulator selects ask for BUY and bid for SELL, and CFD PnL uses persisted entry
and exit fill prices. Therefore the price effect is captured once in the fill
price rather than represented as a second synthetic cost. Any separate provider
cost not represented by the fill or explicit fee remains unavailable and must
fail closed rather than become zero.

### G5 — CFD Lifecycle

Validate the production lifecycle:

```text
flat -> open CFD -> persisted signed exposure -> MTM -> close -> realized PnL -> zero exposure
```

Evidence must prove authoritative fill/contract identity on open, no realized
cash mutation on MTM, correct reduction/close semantics, final zero signed
exposure, no base inventory, and deterministic supported costs. Positive tests
that are disabled because compatibility is rejected are deferred evidence, not
PASS.

### G6 — Persistence, Identity and Exactly Once

Validate with PostgreSQL/Testcontainers where applicable:

- Trade, fill/execution, CFD exposure, contract, margin policy and provenance
  identities are canonical and unambiguous.
- State survives flush/clear/reload.
- The same stable fill identity can be replayed after durable reconstruction.
- Duplicate settlement, PnL, balance and exposure effects remain zero.
- Provenance claim is atomic under concurrent replay.
- No authoritative lifecycle path relies on unordered `findFirst`, `latest`, or
  instrument-only identity.

The required replay is the same fill, not a newly generated equivalent event.
Disabled positive persistence/replay tests do not satisfy this gate.

### G7 — Conditional Settlement Compatibility Authorization

After G1-G3 pass, the pre-G7 independent blockers are resolved, and a human
explicitly authorizes the narrowly scoped qualification run, G7 may apply only
the following compatibility transition:

```text
CASH + CFD = ALLOWED
INVENTORY + CFD = REJECTED
UNKNOWN + CFD = REJECTED
```

The compatibility transition is conditional authorization, not automatic G7
PASS. G7 becomes `PASS` only after the immediately following positive G4-G6
qualification passes and independent review finds no HIGH/MEDIUM accounting or
risk issue. If any positive G4-G6 gate fails, G7 fails, G8 remains prohibited,
and the conditional compatibility change requires human rollback review.

Conditional G7 procedure:

1. Review all G1-G6 current evidence.
2. Confirm CASH is the approved CFD settlement model and inventory remains forbidden.
3. Change only `CASH + CFD` to `ALLOWED`.
4. Run focused compatibility regressions.
5. Execute positive G4-G6 through the real PAPER settlement and PostgreSQL persistence paths.
6. Confirm G7 only if G4-G6 pass; otherwise stop before G8.
7. Keep G8 as a separate human-reviewed application-path qualification.

### G8 — Real Application Path E2E

After G7 authorization, prove through the real application path:

```text
PAPER account -> CFD TradePlan -> T0 -> human authorization -> independent T1
-> PAPER simulated execution/fill -> CFD settlement -> persisted exposure
-> MTM -> close -> realized PnL -> zero exposure
```

Also prove the negative T1 rejection path produces no fill, settlement, or
financial mutation. No internal settlement bypass, compatibility bypass,
test-only endpoint, automatic authorization, or provider mutation is allowed.

## Dependency Graph

```text
G1 ─┐
G2 ─┤
G3 ─┤
G4 ─┼──> G7 compatibility authorization ──> G8 real application E2E
G5 ─┤
G6 ─┘
```

The original strict dependency between positive G4-G6 evidence and G7 is
circular because the legitimate positive settlement path itself requires
`CASH + CFD` compatibility. The conditional procedure resolves this by making
G7 a human-authorized experiment whose final status depends on immediate G4-G6
results. G8 cannot pass until G7 is confirmed and its separate application-path
run succeeds.

## Validation Matrix

The protocol describes criteria; each execution report records the actual result.

| Gate | Criterion | Evidence class | Mandatory | Result |
|---|---|---|---|---|
| G1 | Explicit CFD contract and product identity | Production classes, calculator and contract tests | YES | execution-dependent |
| G1 | Signed quantity, multiplier and currency semantics | Unit/integration | YES | execution-dependent |
| G1 | Incomplete/ambiguous contract fails closed | Unit/integration | YES | execution-dependent |
| G2 | Local immutable PAPER policy | Production, PostgreSQL integration | YES | execution-dependent |
| G2 | Policy persistence/reload and `11.00 USD` calculation | PostgreSQL integration | YES | execution-dependent |
| G2 | LIVE provider authority remains separate | Production and regression tests | YES | execution-dependent |
| G3 | Real T0 and independent T1 | Application integration | YES | execution-dependent |
| G3 | T0 PASS -> T1 REJECT with no mutation | Application integration | YES | execution-dependent |
| G3 | Missing/stale/mismatched T1 fails closed | Unit/integration | YES | execution-dependent |
| G4 | Cash exposure without inventory/full-notional debit | Positive settlement integration | YES | execution-dependent |
| G4 | MTM distinct from realized settlement | Positive valuation integration | YES | execution-dependent |
| G5 | Open/MTM/close/zero exposure | Positive lifecycle integration | YES | execution-dependent |
| G5 | Supported cost semantics are deterministic | Positive lifecycle integration | YES | execution-dependent |
| G6 | Durable canonical identities and reload | PostgreSQL integration | YES | execution-dependent |
| G6 | Same-fill replay is exactly once, including concurrency | PostgreSQL integration | YES | execution-dependent |
| G7 | Explicitly authorized CASH + CFD compatibility | Policy regression and review | YES | execution-dependent |
| G8 | Real positive application lifecycle | Application E2E | YES | execution-dependent |
| G8 | Real negative T1 application lifecycle | Application E2E | YES | execution-dependent |

## Commands

Focused commands must be selected from current test names and recorded in the
execution report. The standard broad commands are:

```bash
cd trading-core
./mvnw -q test
./mvnw -q verify
```

Also run applicable Risk Domain verification, PostgreSQL/Testcontainers tests,
migration tests, repository quality checks, and `git diff --check`. A timeout is
reported as `TIMEOUT`, never as `PASS`. Quality scans are reported separately
from functional evidence.

## Closure Rule

```text
CFD EPIC MAY CLOSE IFF
G1 = PASS AND G2 = PASS AND G3 = PASS AND G4 = PASS
AND G5 = PASS AND G6 = PASS AND G7 = PASS AND G8 = PASS
```

ADR-082 remains an ADR after completion. Its implementation status may later be
recorded as completed according to repository convention. A conditional G7 run
may change only the explicitly authorized `CASH + CFD` compatibility pair; it
must not broaden compatibility or change ADR status.

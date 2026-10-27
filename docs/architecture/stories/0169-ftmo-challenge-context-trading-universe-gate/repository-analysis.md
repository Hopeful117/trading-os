# Repository Analysis

## Story Understanding

Story 0169 requests one bounded Challenge-safety gate before Active Scan
creation. The gate must prove that the selected Challenge definition, Account,
Risk configuration, broker/provider context, and effective market universe are
compatible. An invalid context must fail closed before market analysis.

The Story does not authorize new analysis, Risk-rule changes, account-balance
mutation, global Kraken restrictions, or execution changes.

## Repository Baseline

| Item | Current value |
|---|---|
| Branch | `main` |
| HEAD | `e5c5b21b` |
| Working tree | clean before Story 0169 artifacts |
| Canonical Story | `docs/architecture/stories/0169-ftmo-challenge-context-trading-universe-gate/story.md` |
| DevLog | Story Agent attempted; unavailable due provider error |
| Current runtime baseline | merged `main`; no production code changes made for this Story |

## Relevant Architecture Authority

Story 0078 establishes Challenge as an optional, provider-neutral evaluation
contract around an Account. `ChallengeDefinition` is immutable and versioned;
`ChallengeInstance` preserves the exact definition, Risk Policy, starting
capital, and Account references. Challenge must not duplicate Risk formulas or
become a BrokerAccount or execution workflow.

Story 0154 defines the FTMO 2-Step 50k definition through the generic Challenge
model and records cTrader as the execution-platform/integration target in its
provenance. It explicitly preserves provider-neutral Challenge and Risk
boundaries and does not implement runtime cTrader validation.

Story 0167 establishes cTrader Market Data as the intended FTMO dry-run source
through the provider-neutral Market Data boundary. Kraken remains a supported
provider for other Trading OS workflows.

## Current Challenge and Account Model

`ChallengeDefinition` currently contains:

- immutable provider/product/plan identity;
- capital currency and starting capital;
- required settlement model;
- required instrument product type;
- exact Risk Policy id/version;
- optional Risk-day timezone/reset configuration;
- ordered phase definitions and provenance.

`ChallengeInstance` preserves the Account id, exact definition version, Risk
Policy version, starting capital, phase, and lifecycle status.

`Account` currently contains broker-account reference, a free-form broker value,
settlement model, currency, balances/equity, starting balance, and Risk rules.
`BrokerAccount` contains an opaque provider-registration reference and execution
mode, but the current Account/Challenge relationship does not expose a complete
provider-compatible Challenge trading context.

## Current Challenge Activation Behavior

`StartChallengeService` already enforces:

1. Account existence and ownership.
2. One active Challenge per Account.
3. Challenge definition existence.
4. Required settlement-model compatibility.
5. Referenced Risk Policy existence.
6. Risk-day timezone and reset-time equality.
7. Positive Account starting balance.
8. Currency equality.
9. Exact Account starting balance equality with definition starting capital.

The observed runtime failure is therefore expected: the 10,000 USD PAPER
Account cannot start the 50,000 USD FTMO definition, and its Risk-day
configuration did not match the definition. This service correctly rejected
activation.

The gap is that Active Scan creation does not require an active Challenge, so
the rejected context can still reach Market Intelligence.

## Current Instrument Compatibility

`ChallengeInstrumentCompatibilityService` is an existing partial boundary. It
loads the active Challenge and rejects a mismatching
`requiredInstrumentProductType`, but it returns successfully when no active
Challenge exists and does not validate provider identity or market provider.

The generic Challenge definition currently represents FTMO provider and
required instrument product type, but the runtime definition observed in the
dry-run did not provide enough provider/platform binding to constrain the scan
to cTrader markets.

## Current Active Scan Flow

The current public flow is effectively:

```text
Active Scan request
    -> ActiveScanApplicationService
    -> ActiveScanCreationTransaction
    -> ActiveScanScopeResolutionService
    -> Market Data catalogue
    -> eligibility/tradability/capability filtering
    -> Active Scan persistence and dispatch
```

`ActiveScanApplicationService` handles idempotency and delegates creation. It
does not validate Challenge state.

`ActiveScanScopeResolutionService`:

- verifies Account ownership through Trading Core;
- loads the complete Market Data catalogue;
- resolves either selected market ids or all catalogue markets;
- filters market existence, tradability, Market Facts readiness, and optionally
  broker capabilities;
- persists the resulting effective market ids.

It does not currently:

- load an active Challenge for the Account;
- compare Challenge definition/account/Risk readiness;
- derive a provider-compatible Challenge universe;
- reject a Kraken market in an FTMO/cTrader context;
- fail closed when no active Challenge exists for an FTMO-oriented scan.

This is the direct root cause of the observed coexistence:

```text
Challenge activation rejected
    + no Challenge precondition in Active Scan
    + full Market Data catalogue resolution
    = Kraken markets available to the scan
```

## Current Provider Boundaries

Trading Core has a `MarketProvider` enum containing `KRAKEN`, `FTMO`, and other
provider values. Market Data exposes provider identity on normalized Market
records and has separate Kraken and cTrader adapter paths. BrokerAccount uses a
provider-registration reference rather than embedding provider-specific DTOs.

The repository therefore contains reusable provider-neutral boundaries, but no
single validated mapping currently connects:

```text
Challenge definition/provider
    -> BrokerAccount/provider registration
    -> normalized Market.provider
```

The implementation must resolve this through the existing abstractions or add
the smallest explicit compatibility metadata required by the Story. It must
not scatter FTMO conditionals across Market Intelligence or disable Kraken
globally.

## Current UI Boundary

The Opportunities/DecisionWorkspace flow already selects an Account, chooses a
market-scan scope, and submits Active Scan requests. It currently does not show
an authoritative Challenge readiness summary before enabling the scan. Backend
authority is therefore required even if the UI is changed to disable the scan
button.

## Required Changes

1. Define the smallest provider-neutral Challenge trading-context/readiness
   contract using existing Challenge, Account, BrokerAccount, Risk, and Market
   abstractions.
2. Add backend pre-scan validation for active Challenge, account compatibility,
   Risk configuration, ownership, provider context, and non-empty compatible
   market scope.
3. Extend Active Scan scope resolution to filter the effective universe by the
   validated Challenge provider context before existing eligibility checks.
4. Return structured deterministic failure outcomes to the API and frontend.
5. Surface readiness and effective provider/universe in the existing Trader
   Cockpit/Market Navigator.
6. Preserve non-Challenge and Kraken behavior.
7. Add focused backend, frontend, and runtime validation without executing a
   PAPER trade.

## Risks and Decisions Requiring Plan Detail

- The repository currently has both `FTMO` and `KRAKEN` provider concepts, while
  cTrader is represented through separate adapter/provider-registration paths.
  The implementation plan must identify the authoritative compatibility key
  before changing contracts.
- The FTMO definition currently has required instrument-product semantics but
  the runtime observation proves that this is insufficient to identify the
  cTrader market universe. The plan must avoid using `requiredInstrumentProductType`
  as a provider substitute.
- A compatible FTMO PAPER Account cannot be produced by mutating a historical
  Account starting balance. Story 0078 requires a dedicated Account for a new
  starting state.
- The Challenge definition and Risk policy remain authoritative; the gate must
  validate readiness without duplicating Risk calculations.
- An empty cTrader catalogue must produce an explicit fail-closed result and
  never fall back to Kraken.

## Validation Expectations

- Focused Challenge activation/readiness tests.
- Account balance, Risk-day, ownership, and provider mismatch tests.
- Selected and all-eligible market-universe tests.
- Empty compatible-universe and direct API bypass tests.
- Non-Challenge Kraken regression tests.
- Angular readiness and scan-button tests.
- Affected Maven module tests, Angular tests/build, and `git diff --check`.
- Bounded local runtime proof with a coherent FTMO/cTrader PAPER context and no
  PAPER execution.

## Conclusion

The observed defect is not an FTMO activation validation failure: activation
already fails closed for the mismatched Account. The missing invariant is the
precondition between Challenge readiness and Active Scan creation, together
with an explicit provider-compatible market-universe binding.

The proposed implementation is a bounded cross-module gate spanning Trading
Core Challenge/account context, Market Intelligence Active Scan scope, and the
existing Angular Market Navigator. It must preserve the provider-neutral
architecture and existing non-Challenge Kraken workflows.

Implementation is not authorized until this Repository Analysis is explicitly
approved by the human engineer.

# Implementation Plan

## Overview

Story 0169 will add one deterministic pre-scan readiness gate connecting the
existing Challenge, Account, Risk, BrokerAccount, and normalized Market
boundaries. The gate will prevent Active Scan creation when the selected
Challenge context is absent, financially incompatible, provider-incompatible,
or has no compatible markets.

The implementation preserves these authority boundaries:

```text
Trading Core         -> Challenge/account ownership and readiness facts
Risk                  -> Risk policy and financial-rule authority
Broker Service        -> broker/provider identity and connection facts
Market Data           -> normalized market provider and market catalogue
Market Intelligence   -> scan scope resolution and scan lifecycle
Trading OS Web        -> presentation of readiness and scan affordance
```

Implementation is not authorized until this plan is explicitly approved.

## Phase 1 - Provider-Neutral Compatibility Contract

Trace and reuse the existing provider-registration and normalized market
provider identities. Add only the smallest immutable Challenge compatibility
metadata required to answer:

```text
which broker/provider context is required by this Challenge?
which normalized Market provider is compatible with it?
which account/broker binding supplies that context?
```

Do not use the Challenge `provider` field as an implicit market provider when
the repository semantics distinguish Challenge provider (`FTMO`) from execution
market provider (`CTRADER`). Do not add FTMO conditionals throughout unrelated
services.

The plan will prefer an explicit provider-neutral compatibility value/reference
on the Challenge definition and its immutable instance snapshot, with the FTMO
definition seed/configuration supplying the cTrader requirement. Existing
Kraken and non-Challenge paths must remain unchanged.

## Phase 2 - Challenge Readiness Service

Create or extend a small Trading Core application boundary that resolves a
caller-owned Account and active Challenge into a structured readiness result.

Validate, without duplicating Risk formulas:

1. Account exists and belongs to the caller.
2. Active Challenge exists and its phase permits preparation.
3. Challenge definition and phase references are available.
4. Account starting-balance/currency semantics match the immutable Challenge
   starting capital.
5. The Challenge Risk Policy and Account Risk-day configuration match the
   Challenge-effective configuration.
6. The Account's BrokerAccount/provider binding is present and compatible.

Return stable provider-neutral readiness statuses and actionable details. Do not
silently repair Account state, mutate historical balances, or select another
Challenge definition.

## Phase 3 - Active Scan Backend Gate

Integrate readiness into the authenticated Active Scan creation/scope path
before the existing catalogue resolution and scan persistence.

For an account with an active Challenge:

1. Resolve Challenge readiness.
2. Reject any non-ready result before Active Scan creation.
3. Resolve the requested/effective catalogue.
4. Filter by the Challenge-required normalized Market provider before existing
   tradability, Market Facts, and broker-capability checks.
5. Reject selected incompatible markets explicitly.
6. Reject an empty compatible universe with a structured no-compatible-markets
   result; never fall back to Kraken.
7. Persist the effective provider and compatible scope in the scan snapshot so
   later dispatch cannot silently widen the universe.

For accounts without an active Challenge, preserve current generic and Kraken
scope behavior.

The direct API path must use the same gate as the UI path. Idempotency and
ownership checks remain intact.

## Phase 4 - API and Error Contract

Expose a bounded readiness/context projection through the existing trading
workflow API so the frontend can display the authoritative state before scan.

Expose, where available:

- Challenge identity and version;
- current phase;
- Challenge starting capital and currency;
- selected Account identity and balance/equity;
- Risk Policy identity/version and Risk-day status;
- Broker/provider context;
- required/effective Market provider;
- compatible market count;
- readiness status and actionable reasons.

Use the repository's existing problem/error conventions. A generic HTTP 400
must not be the only trader-facing explanation. No cTrader or broker DTOs may
leak into generic Challenge or Market Intelligence domain contracts.

## Phase 5 - Trader Cockpit and Market Navigator

Add a reactive pre-scan context panel to the existing Opportunities/
DecisionWorkspace workflow. It must show:

```text
Challenge
Phase
Challenge size
Trading account
Provider context
Market universe provider/count
Challenge readiness
Blocking reason when not ready
```

Disable the scan action when backend readiness is blocked. Keep the backend
gate authoritative; UI state is not a security or safety boundary.

Do not create a new settings application or duplicate Challenge/Risk
calculations in Angular.

## Phase 6 - Runtime Configuration and Bounded Proof

Use the supported PAPER account flow to establish a coherent FTMO context. If a
historical Account cannot be adapted without changing persisted truth, create a
new supported PAPER Account with the correct starting capital, Risk-day
configuration, Risk Policy, BrokerAccount/provider binding, and cTrader market
context.

Validate in order:

1. FTMO definition and immutable version.
2. Compatible PAPER Account balance/equity.
3. Challenge activation.
4. Challenge phase and effective Risk configuration.
5. cTrader provider context.
6. cTrader-only effective market universe.
7. Kraken excluded from the FTMO scope.
8. One bounded Active Scan using the compatible scope.

Do not execute a PAPER trade. Do not run the previous unrestricted 1,476-market
scan. A valid empty-universe failure is acceptable evidence if no cTrader market
is available.

## Phase 7 - Tests

### Trading Core

- Challenge/account starting capital mismatch blocks readiness.
- Currency, ownership, active-phase, and Risk-day mismatches block readiness.
- Missing provider/broker binding blocks Challenge readiness.
- Compatible Challenge context returns READY.
- Existing non-Challenge accounts remain valid.

### Market Intelligence

- Active Scan rejects blocked Challenge context.
- Direct API calls cannot bypass the pre-scan gate.
- FTMO/cTrader scope excludes Kraken markets.
- Selected Kraken market is rejected for FTMO/cTrader context.
- Compatible cTrader market remains eligible.
- Empty compatible universe fails closed.
- Generic and non-Challenge Kraken scanning remains backward compatible.
- Effective provider and scope are preserved in the scan snapshot.

### API and Angular

- Structured readiness statuses and actionable problem responses are exposed.
- Cockpit renders Challenge/account/provider/universe/readiness context.
- Scan button is disabled for blocked readiness.
- UI preserves generic/non-Challenge scan behavior.

## Validation Commands

```text
mvn -q test                         # affected Trading Core module
mvn -q test                         # affected Market Intelligence module
mvn -q test                         # affected Market Data/Broker modules if changed
npm run test:ci                     # trading-os-web
npm run build                       # trading-os-web
git diff --check
```

The exact affected Maven modules and frontend command output will be recorded
after implementation. Runtime validation is required when the local connected
cTrader environment is available.

## Documentation Reconciliation

Update only the Story implementation report and canonical runtime/API
documentation if the new readiness contract changes documented behavior.
Do not modify ADRs unless the implementation proves an existing responsibility
boundary is insufficient. No Obsidian vault writes are permitted.

## Residual Risks

- The repository currently represents Challenge provider, BrokerAccount provider
  registration, and normalized Market provider in different modules. The
  implementation must establish one explicit compatibility mapping without
  coupling Trading Core to cTrader DTOs.
- The local cTrader environment may not provide a compatible authenticated
  execution account or non-empty market catalogue. In that case runtime proof
  must report the exact blocked state rather than fabricate readiness.
- FTMO definition provenance may describe platform constraints that are not yet
  represented as executable metadata. Those constraints must be resolved in the
  generic definition/configuration boundary, not by UI-only filtering.

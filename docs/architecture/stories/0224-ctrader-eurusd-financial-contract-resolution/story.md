# Story 0224 - cTrader EURUSD Financial Contract Resolution and Synchronization Recovery

## Metadata

**ID:** `0224`

**Title:** cTrader EURUSD Financial Contract Resolution and Synchronization Recovery

**Status:** Draft

**Priority:** P0 - Synchronization Recovery and Risk Evidence Integrity

**Related ADRs:** `ADR-082`, `ADR-083`

**Predecessor Stories:** `0177`, `0178`, `0179`, `0183`, `0193`, `0199`, `0201`, `0202`

## Goal

Restore deterministic financial-contract resolution for the active cTrader
EURUSD market and complete one controlled read-only broker synchronization
cycle without weakening financial evidence validation or risk fail-closed
behavior.

The existing provider-neutral financial contract model and Market Data source
remain authoritative. The Story must not create a second cTrader-specific
financial calculator or change risk policy.

## Context

Runtime connectivity has already been validated. The cTrader connection for
external account `49411967` is connected, and authenticated EURUSD read-only
validation succeeds through the normal Gateway and Eureka path.

The active broker account is:

```text
broker account: 7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456
Trading Core account: b91c63b6-9357-4f0a-9d4d-3f3e2e8c9f11
provider: CTRADER
provider account: 49411967
currency: USD
```

The current market identity is:

```text
provider symbol: EURUSD
canonical instrument: EUR/USD
product: CFD
provider symbol ID: 1
quantity unit: UNDERLYING_UNITS
```

The verified cTrader quantity constraints are `minVolume=100000`,
`maxVolume=10000000000`, `stepVolume=100000`, and `lotSize=10000000`.
The existing conversion remains `providerVolume = canonicalQuantity * 100`.

A previous synchronization acquired a fresh `INCOMPLETE` broker snapshot but
Trading Core failed during persistence with:

```text
financial contract market is unavailable
```

The failure was raised while resolving the financial contract for an open
position. The exact lookup identity and source of the missing market must be
confirmed from the current repository before implementation.

The historical Trade Plan
`0cc15278-53a3-3fc4-8b52-54e419df369b`, version `2`, remains `SPOT` and must
not be migrated or modified. The active risk policy remains
`MAX_EXPOSURE=3%` and `MAX_POSITION_RISK=1%`.

## Problem

Trading Core cannot complete broker snapshot persistence because its existing
financial-contract resolver cannot resolve the canonical EUR/USD CFD market
for the cTrader synchronization path. This blocks recovery of the account
projection even though Broker Service can retrieve and normalize provider
facts.

The implementation must distinguish:

- unavailable financial-contract identity or semantics; and
- an incomplete broker financial snapshot whose missing facts remain
  unavailable to risk consumers.

Neither condition may be converted into a successful complete risk snapshot.

## Scope

- Trace the synchronization path from cTrader snapshot acquisition through
  Trading Core financial-contract resolution and persistence.
- Identify the exact resolver method, requested market identity, lookup key,
  authoritative contract source, and first failing boundary.
- Restore deterministic `CTRADER / EUR/USD / CFD` resolution through the
  existing Market Data and Trading Core architecture.
- Preserve explicit product classification, canonical quantity unit,
  contract multiplier, currencies, precision, provenance, and validation.
- Reuse the existing provider-neutral financial-contract model and source of
  truth; do not duplicate contract definitions in Broker Service or Trading
  Core.
- Permit broker snapshot persistence only according to the existing domain
  contract, including explicitly supported `INCOMPLETE` snapshots.
- Preserve independent snapshot freshness and completeness semantics.
- Add focused regression coverage for valid mapping, unknown or ambiguous
  identity, unsupported product mapping, contract fields, historical SPOT
  plan preservation, and incomplete snapshot behavior.
- Build and redeploy only affected services, preserving database volumes.
- Execute one controlled authenticated read-only synchronization and record
  the contract resolution, snapshot, persistence, and risk-evidence result.
- Verify that missing mandatory financial evidence remains fail-closed for
  risk consumers.

## Out of Scope

- Changing `MAX_EXPOSURE=3%` or `MAX_POSITION_RISK=1%`.
- Changing risk profiles, challenge definitions, or risk assignments.
- Modifying the historical SPOT Trade Plan version 2.
- Creating a new Trade Plan or running a risk evaluation.
- Requesting approval, creating execution intents, or submitting orders.
- Modifying broker positions or executing provider mutations.
- Changing provider quantity conversion, margin policy, exposure
  aggregation, or stop-risk calculations.
- Inferring margin from leverage or fabricating financial facts.
- Adding a generic SPOT fallback for an unresolved CFD market.
- Exposing internal endpoints publicly or weakening JWT/security controls.
- Creating a new microservice or cTrader-specific financial calculator.
- Repeating the completed Gateway, Eureka, JWT, or quantity-conversion
  investigations without new evidence.
- Full-stack restart, database reset, volume reset, full test-suite
  execution, commit, push, merge, or pull-request creation.

## Acceptance Criteria

- [ ] The exact failing resolver method, requested identity, lookup key,
      expected identity, actual response, and first failing boundary are
      documented in the implementation report.
- [ ] `CTRADER / EUR/USD / CFD` resolves through the existing authoritative
      financial-contract source.
- [ ] Provider symbol `EURUSD` maps deterministically to canonical `EUR/USD`
      without leaking provider identity into provider-neutral domain contracts.
- [ ] Product classification remains explicit as `CFD`; no SPOT fallback is
      introduced.
- [ ] The resolved contract exposes the existing canonical quantity unit,
      verified multiplier, currencies, precision, and provenance.
- [ ] Unknown instruments, unsupported product mappings, and ambiguous market
      identities fail closed.
- [ ] Historical SPOT Trade Plan `0cc15278-53a3-3fc4-8b52-54e419df369b`,
      version `2`, remains unchanged.
- [ ] Existing incomplete-snapshot validation remains strict: missing
      mandatory equity, used-margin, or free-margin facts do not produce
      `COMPLETE` financial evidence.
- [ ] Incomplete snapshot persistence follows the existing domain contract;
      no new persistence policy is introduced.
- [ ] Risk consumers remain fail-closed when required financial evidence is
      missing or incomplete.
- [ ] Focused tests cover valid cTrader CFD resolution, canonical mapping,
      quantity unit, multiplier preservation, unknown/unsupported/ambiguous
      resolution, SPOT plan preservation, incomplete persistence semantics,
      and risk evidence unavailability.
- [ ] One authenticated read-only synchronization completes or reports the
      exact remaining domain blocker without fabricating facts.
- [ ] Runtime evidence records synchronization timestamp, snapshot identity,
      contract identity/version, completeness, freshness, persistence result,
      and unavailable financial fields.
- [ ] Kraken mappings, existing SPOT contracts, risk policies, and quantity
      conversion remain unchanged.
- [ ] No trade, risk evaluation, approval, execution intent, or broker
      mutation is created.
- [ ] Relevant focused validation and `git diff --check` pass.

## Constraints

- Preserve Market Data ownership of canonical market and financial-contract
  definitions.
- Preserve Trading Core ownership of synchronization orchestration and
  persistence decisions.
- Preserve Broker Service ownership of provider authentication, normalized
  provider facts, and cTrader infrastructure details.
- Keep provider-specific symbol IDs and payload fields inside adapters or
  explicit provenance fields where the existing contract permits them.
- Keep product classification explicit and deterministic.
- Treat missing, stale, contradictory, or ambiguous financial evidence as
  unavailable.
- Do not silently merge provider symbols, canonical instruments, market IDs,
  or product classifications.
- Preserve all pre-existing worktree modifications.
- Do not commit, push, merge, or create a pull request automatically.

## Relevant ADRs

- `docs/architecture/adr/ADR-082.md`
- `docs/architecture/adr/ADR-083-paper-margin-authority-and-simulation-policy.md`

## Relevant Modules

- `trading-core`
- `broker-service`
- `market-data`
- `risk-domain`

## Validation

- Inspect the current resolver, market-data contract source, mapper, broker
  snapshot DTO, and synchronization persistence boundary.
- Run only focused tests in affected modules for the confirmed defect.
- Build only affected services.
- Redeploy only affected services with existing database volumes preserved.
- Use the authenticated Gateway application path for one controlled
  read-only synchronization.
- Verify the resulting snapshot and risk-evidence status without running a
  risk evaluation.
- Run `git diff --check` and review the complete focused diff.

## Definition of Done

- [ ] Repository Analysis approved
- [ ] Implementation Plan approved when required
- [ ] Contract-resolution correction implemented
- [ ] Focused regression tests completed
- [ ] Runtime synchronization validated
- [ ] Risk fail-closed behavior verified
- [ ] Documentation reconciled
- [ ] Code Review completed
- [ ] Engineering Report completed
- [ ] Human commit created separately

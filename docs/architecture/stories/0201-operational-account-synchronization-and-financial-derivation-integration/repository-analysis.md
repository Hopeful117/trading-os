# Repository Analysis - Story 0201

## Analysis Status

Repository Analysis is complete and requires explicit human approval before an
Implementation Plan is created. No implementation files have been changed for
Story 0201.

## Repository and Git State

- Repository root: `/home/ludo/workspace/trading-os`.
- Branch: `main`, aligned with `origin/main` at inspection time.
- The working tree is intentionally dirty with uncommitted changes from Stories
  0198, 0199 and 0200. Those changes must not be reverted or included in an
  unrelated cleanup.
- Story 0201 did not previously exist and has now been created with this Story
  and analysis artifact only.
- No `TOOLS.md` mapping was found in the repository, so DevLog lifecycle
  registration could not be performed from repository configuration.

## DevLog Context

The DevLog Story Agent was queried with idempotency key
`story-0201-context-20261105` and resumed once with the exact same request.
Both requests timed out. Repository code, accepted ADRs and the prior Story
reports are therefore the authoritative sources for this analysis. No DevLog
context is claimed.

## Confirmed Runtime Baseline

Story 0200 provides the current controlled runtime evidence for the existing
account:

```text
provider: cTrader
external account: 49411967
Trading Core broker account: 7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456
currency: USD
balance: 50000
status: SYNCED_PARTIAL
completeness: INCOMPLETE
freshness: FRESH
positions: AVAILABLE_EMPTY
orders: AVAILABLE_EMPTY
PnL: AVAILABLE_EMPTY
equity: unavailable
used margin: unavailable
free margin: unavailable
diagnosticOnly: true
CTRADER_SAFETY_EXECUTION_ENABLED: false
```

V52 snapshot metadata was verified in PostgreSQL. No provider order or other
mutation was performed.

## Existing Operational Synchronization Flow

```text
Account UI / authenticated API
  -> Gateway
  -> AccountSynchronizationV2Controller
  -> BrokerSynchronizationServiceImpl
  -> BrokerAccountSnapshotV2Client
  -> Broker Service snapshot endpoint
  -> CTraderAuthoritativeSnapshotCapability
  -> READ-ONLY cTrader observation
  -> capability-scoped Account persistence
  -> Account API and risk fact projection
```

Story 0200 already changed Trading Core to persist valid partial account facts,
retain unavailable collection state and return `BrokerAccountSynchronizationResult`.
The remaining operational gap is at the normal account projection and UI:
`AccountDto`, `AccountMapper`, the Angular `Account` model, account cards and
the account synchronization action do not currently expose the V2 capability
metadata or distinguish partial synchronization from trading readiness.

The legacy `/api/v1/accounts/synchronize` action remains a separate string-valued
Kraken-oriented operation. The implementation plan must not silently replace or
mislabel that contract. It should decide whether the normal FTMO account path
uses the existing V2 endpoint directly or adds a compatible account-scoped
operation while preserving existing callers.

## Financial Derivation Boundary

`broker-service` contains the existing isolated
`CTraderDerivedEquityExperiment` from Story 0197. It computes:

```text
balance + sum(netUnrealizedPnl)
```

The experiment already validates account identity, complete positions and PnL,
duplicate/mismatched references, timestamps, freshness, collection window and
balance version. Its result is versioned `CTRADER_DERIVED_EQUITY_V1` and marked
`UNVERIFIED`.

It is deliberately not wired into `BrokerAccountSnapshotV2`, `RiskFactsProvider`,
T0, T1 or execution. Any integration in Story 0201 must preserve a separate
diagnostic/provenance representation and must not write the candidate into the
authoritative `Account.equity` field or `EquityFact` used for FTMO admission
unless an explicit approved contract exists.

There is an authority conflict that must remain explicit during planning:

- Story 0201 requests reuse of the candidate in the normal collection path when
  justified, but only as `BROKER_DERIVED` and non-admissible.
- ADR-084 and ADR-085 state that no cTrader current-equity derivation is
  approved for LIVE risk and that missing equity remains fail-closed.

The safe repository interpretation is diagnostic integration only: expose a
candidate and its validation state separately from authoritative risk facts.
The plan must not promote it to LIVE equity or alter FTMO policy admissibility.

ADR-082/084 also prohibit deriving account used margin from notional, leverage,
or summed position margin without a validated provider contract. The current
empty-position observation therefore does not prove used margin is zero. Free
margin cannot be calculated unless both admissible equity and used margin exist.

`RequiredMarginPort` is already the provider-neutral proposed-order margin
boundary consumed by the risk workflow. ADR-084 records that the cTrader
ExpectedMargin adapter is not implemented/validated in the current baseline.
This is a likely LIVE preflight blocker, distinct from current account used
margin.

## Account API and Angular Gap

The backend `AccountDto` currently exposes account identity, currency, balances,
equity, valuation state and risk/trade-planning profile references, but not V52
snapshot capability state, synchronization status, provenance or reasons.
`AccountMapper` similarly exposes no snapshot metadata.

The Angular `Account` model declares `equity` as a required number, which is
unsafe for `UNAVAILABLE` values. The Accounts page currently renders balances
and provider/account connection state, while `AccountCard` does not render
positions, orders, missing financial capabilities or partial synchronization
status. The existing `AccountService.synchronize()` calls the legacy V1 route
and expects plain text.

The manual trade page currently filters to PAPER accounts only, so the existing
LIVE FTMO account cannot enter that UI path. This is a relevant operational
integration gap, but the implementation plan must keep the UI change focused on
status and validation visibility rather than a broad trade-ticket refactor.

## Risk and T0/T1 Boundary

`ModeAwareRiskFactsProvider` loads an authoritative V2 snapshot for LIVE accounts
and preserves collection availability, equity provenance and diagnostic reasons.
It marks the snapshot incomplete when required evidence is absent.

`TradePlanRiskEvaluationService` then:

- resolves the effective policy and evidence requirements;
- requires structural identity, freshness and interval validity;
- blocks missing required balance, positions, starting balance or deal history;
- checks equity admissibility by provenance and quality;
- requires market valuation and proposed CFD contract facts;
- resolves proposed-order margin through `RequiredMarginPort`;
- evaluates the deterministic engine and persists the result.

ADR-086 requires partial invariant diagnostics to remain non-authorizing. The
aggregate result must remain incomplete when a mandatory FTMO invariant lacks
admissible equity, deal history, margin or other required evidence. T0 and T1
must share those requirements; no implementation may bypass either gate.

Expected current LIVE outcome is therefore a deterministic fail-closed result,
most likely blocked by unavailable/non-admissible equity and/or required margin
preflight, with deal history additionally blocking any policy invariant that
requires closed deals. The exact first blocker must be captured by the runtime
Trade Intent validation, not guessed from the snapshot alone.

## Affected Components

Potential implementation surfaces, subject to the approved plan:

- `trading-core` Account DTO, mapper, account API and synchronization result
  projection;
- `trading-core` snapshot metadata/provenance projection and risk diagnostics;
- `trading-core` Trade Intent/T0/T1 API test fixtures and readiness reporting;
- `broker-service` only if the existing provider-neutral derivation result needs
  a neutral transport/diagnostic contract; no cTrader payload leakage;
- `trading-os-web` account model, account service, account card/page and focused
  tests;
- Story-local runtime matrix, implementation report and safety report.

No change is currently justified to FTMO policy thresholds, the Risk Domain
algorithms, cTrader execution adapters or provider mutation behavior.

## Required Validation

- Preserve and rerun Story 0200 synchronization tests and persistence tests.
- Add API/DTO/mapper tests for unavailable versus available-empty metadata.
- Add derivation integration tests proving candidate provenance and rejection as
  FTMO-admissible evidence.
- Add tests for non-calculable used/free margin without zero fallback.
- Execute the existing Trade Intent through T0/T1/readiness and record the exact
  deterministic blocker.
- Add Angular tests for nullable financial values, partial status and diagnostic
  rendering without false readiness.
- Run relevant Angular production build and `git diff --check`.
- Retry the Trading Core PostgreSQL integration suite without redesigning test
  infrastructure; report any remaining environment failure precisely.
- Perform one bounded authenticated READ-ONLY synchronization of external
  account `49411967` after the implementation, then verify PostgreSQL and API
  projections.

## Material Risks and Decisions for the Plan

1. The plan must choose a provider-neutral representation for the 0197
   diagnostic candidate without reusing authoritative equity fields.
2. The plan must decide how to expose nullable/unknown financial values in the
   existing Account API without breaking existing Angular consumers.
3. The plan must preserve the legacy V1 account synchronization contract while
   making the cTrader V2 result available in the normal operational path.
4. The plan must define how a representative Trade Intent is created/reused for
   validation without changing acceptance or submitting an order.
5. If a required LIVE preflight margin capability is absent, the result must be
   documented as a blocker rather than implemented with an unapproved formula.

## Required Next Stage

The Repository Analysis is complete. Human approval is required before writing
the Implementation Plan. After approval, the plan will define the smallest
provider-neutral operational projection and diagnostic derivation integration
that satisfies Story 0201 without changing FTMO policy admissibility or
execution safety.

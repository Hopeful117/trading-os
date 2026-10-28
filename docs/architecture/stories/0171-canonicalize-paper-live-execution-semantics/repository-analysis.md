# Repository Analysis - Story 0171

## Analysis Status

**Story:** `0171 - Canonicalize PAPER and LIVE Execution Semantics`
**Status:** Approved
**Analysis date:** 2026-10-28

## Sources and Baseline

- Current branch: `main`
- `HEAD`: `8e6cda94a404661746d71297406df2c83f772475`
- `main`: `8e6cda94a404661746d71297406df2c83f772475`
- Worktree: clean with respect to tracked files; five untracked runtime network
  capture files from the previous rehearsal remain untouched.
- DevLog: unavailable during analysis because the provider returned HTTP 429
  rate-limit errors. Repository sources remain authoritative.
- Curated vault: not consulted; no transverse note was required to establish
  the current repository behavior.

## Executive Conclusion

The current execution routing already encodes the intended boundary in the
main execution path:

```text
PAPER -> SimulatedExecutionAdapter -> local Trading Core settlement
LIVE  -> BrokerExecutionAdapter -> Broker Service/provider boundary
```

The strongest existing safety tests also verify that PAPER execution does not
interact with the LIVE broker client. No active production mapping from
provider `isLive` or an economic demo label to `ExecutionMode` was found.

The Story is therefore primarily a semantic clarification and consistency
change, not an execution-routing redesign. The implementation should preserve
the existing routing and strengthen its domain documentation and focused
regression coverage.

## Confirmed Operational Confusion

The previous cTrader dry-run provides the concrete failure this Story must
prevent:

```text
Trading OS account mode = LIVE
Provider = cTrader
Provider account = economically DEMO/SIMULATED
Provider binding = external broker boundary
```

The requested cTrader dry-run was nevertheless routed conceptually through the
PAPER journey because the provider's demo classification was treated as proof
that the Trading OS account should be PAPER. That interpretation was wrong.

The correct classification is:

```text
cTrader demo account connected to Trading OS
-> Trading OS ExecutionMode.LIVE
-> provider-backed facts and external-boundary workflow
-> execution may still remain disabled
```

PAPER would have been correct only for a Trading OS account whose orders,
fills, settlement, and position lifecycle remain entirely inside Trading OS.
The provider's demo/economic status must not select the local PAPER journey.

This is both a documentation gap and an agent/operator reasoning guardrail gap.
It must be addressed explicitly in the canonical ADR, `ExecutionMode` domain
documentation, `AGENTS.md`, and regression tests that distinguish provider demo
from Trading OS PAPER.

One functional boundary requires explicit review during implementation:
`ActiveChallengeMonitoringService` currently evaluates only active Challenges
whose linked BrokerAccount is `ExecutionMode.PAPER`. Under the new canonical
meaning, a connected FTMO/cTrader Challenge is `LIVE`; whether Challenge
monitoring must include that path is a product behavior decision. This Story
must not silently broaden Challenge monitoring without confirming that scope.

## Current Domain Model

### ExecutionMode

`trading-core/.../brokeraccount/domain/ExecutionMode.java` exposes only:

```java
LIVE,
PAPER
```

The enum has no domain documentation. This is the clearest direct gap and the
smallest authoritative place to state:

- PAPER means local Trading OS simulation;
- LIVE means participation in an external broker/provider boundary;
- LIVE does not mean real money;
- provider environment and economic classification are separate dimensions.

### BrokerAccount

`BrokerAccount` persists `ExecutionMode` as a required enum field. Account
creation accepts the mode explicitly. Connected broker import in
`BrokerAccountProvisioningService` hardcodes `ExecutionMode.LIVE`, which is
consistent with the external-boundary rule and provides the correct behavior
for connected cTrader accounts regardless of economic demo status.

The persisted mode must not be inferred from provider labels or rewritten for
existing accounts.

### Account Creation and cTrader Binding

The Angular Accounts page offers a PAPER/LIVE selector for generic broker
creation. The cTrader path bypasses that mode selector, creates a cTrader
connection, performs OAuth account binding, and provisions the Trading Core
account through the connected broker flow. The backend provisioning path
imports connected broker accounts as LIVE.

This is semantically correct for a connected provider account. The UI should
explain the distinction instead of presenting `LIVE` as an implicit real-money
label.

The previous runtime finding that an FTMO account was unbound is a separate
runtime/configuration issue and is explicitly out of scope unless the audit
proves it was caused by PAPER/LIVE confusion.

## Execution Routing Audit

### RoutingBrokerExecutionAdapter

`RoutingBrokerExecutionAdapter` selects adapters from the persisted
`BrokerAccount.executionMode` for:

- submission;
- cancellation;
- reconciliation.

`PAPER` selects `SimulatedExecutionAdapter`; `LIVE` selects
`BrokerExecutionAdapter`. No provider environment or provider `isLive` field is
used.

### SimulatedExecutionAdapter

`SimulatedExecutionAdapter` independently reloads the BrokerAccount and rejects
any request whose mode is not PAPER. It obtains prices from Market Data,
generates a `SIM-*` order identifier, and does not call Broker Service.

This is a strong safety invariant. It should be documented directly and
covered by a focused LIVE rejection test if the current tests do not already
provide that assertion.

### BrokerExecutionAdapter

`BrokerExecutionAdapter` maps execution requests to the broker-neutral Broker
Service client. It is the LIVE external-boundary adapter and preserves unknown
provider outcomes for retry/reconciliation handling.

### PAPER Exit and Position Close

`PaperExitService` requires `ExecutionMode.PAPER` and delegates to the common
execution pipeline, which routes to the simulator and local settlement.

`PositionController` selects local Trading Core positions for PAPER and
provider-backed facts for LIVE. The separate LIVE position-close path remains
provider-backed. This matches ADR-042 and should not be redesigned.

### Risk Facts

`ModeAwareRiskFactsProvider` selects authoritative external snapshots or the
broker risk-facts fallback for LIVE, and builds local account/trade facts for
PAPER. This is the intended authority split:

```text
PAPER -> Trading Core local facts
LIVE  -> external provider facts through Broker Service
```

The Risk Domain itself remains mode-independent.

## cTrader Provider Audit

The cTrader adapter owns `isLive` and provider environment details. The
current cTrader code:

- records provider-reported `isLive`;
- recognizes the FTMO FREE semantic exception;
- keeps transport environment separate from effective demo classification;
- admits cTrader execution only after provider connection, environment,
  effective demo classification, permissions, and fresh complete observation
  checks.

No direct `isLive -> ExecutionMode` mapping was found. This boundary must remain
inside Broker Service and must not be duplicated in Trading Core.

The Story should add a focused semantic regression at the smallest appropriate
boundary. It should verify that a provider-backed economically simulated
cTrader/FTMO account is represented as Trading OS LIVE without changing the
provider `isLive` fact. It must use a fake or mock and submit no order.

## Challenge Audit

`ActiveChallengeMonitoringService` currently skips any active Challenge whose
linked BrokerAccount is not PAPER and logs `reason=NOT_PAPER`. This is not a
provider `isLive` inference, but it is a mode assumption that may conflict with
the new canonical FTMO/cTrader example.

Classification:

- Confirmed fact: connected external accounts are LIVE.
- Confirmed fact: active Challenge monitoring currently evaluates PAPER only.
- Unknown: whether LIVE FTMO Challenge monitoring is required by the current
  product scope or intentionally deferred until provider execution readiness.

Implementation must preserve this distinction and request a product decision
if changing the filter becomes necessary. It must not silently expand Challenge
monitoring as a side effect of terminology work.

## Frontend Audit

### Account Management

The Accounts page currently displays raw `LIVE` and `PAPER` values and uses
labels such as `Connecter le compte LIVE` and `Créer le compte PAPER`. PAPER is
described as simulated, but LIVE is not described as provider-connected.

Minimal wording is recommended:

```text
PAPER - Simulated inside Trading OS
LIVE - Connected to external broker
```

This must not change persisted values or account creation behavior.

### Other LIVE Labels

Several Angular models use `LIVE` for freshness/status values, including
Dashboard, market quotes, OHLC, and workspace freshness. Existing tests
explicitly distinguish freshness `LIVE` from other status values. These are not
`ExecutionMode` values and must not be renamed or conflated during this Story.

Position Intelligence uses `PAPER · Trading Core` versus `LIVE · Broker
Service` for fact provenance. This is semantically aligned with the canonical
boundary, but its wording should be reviewed alongside the account UI.

## Documentation Audit

### Correct and Authoritative

- ADR-042 explicitly defines local PAPER authority and external LIVE position
  authority.
- ADR-043 explicitly separates LIVE and PAPER Risk Facts sources.
- ADR-071 explicitly separates FTMO, cTrader, provider `isLive`, effective
  environment, and economic/business classification.
- V1 acceptance documentation states that PAPER routing comes from persisted
  BrokerAccount mode and that LIVE is broker-authoritative.

### Missing or Potentially Misleading

- `ExecutionMode` has no direct domain documentation.
- `AGENTS.md` does not state the canonical PAPER/LIVE rule or the FTMO/cTrader
  example, leaving future agents vulnerable to inferring FTMO demo -> PAPER.
- Current account UI exposes raw modes without explaining the execution-boundary
  meaning.
- `docs/architecture/reports/prop-challenge-readiness.md` describes the
  demonstrated PAPER subset versus the real-provider procedure. This is a
  historical readiness statement, but it can be misread as equating PAPER with
  economic simulation and should be classified carefully rather than rewritten
  wholesale.
- Story 0169 uses a `compatible PAPER account` in its original FTMO dry-run
  context. That Story is historical/current workflow context and must not be
  rewritten merely to erase its history; a reconciliation note or current ADR
  should provide the canonical interpretation.

## Existing Test Coverage

Existing coverage already proves substantial parts of the boundary:

- `PaperExecutionVerticalRegressionTest` verifies PAPER routing, simulated
  fills, local settlement, stale-data rejection, and no interactions with the
  mocked LIVE broker client.
- `PaperExecutionPersistenceIntegrationTest` verifies persisted PAPER mode.
- `PaperSettlementExitTest` and `PaperExitAcceptanceIntegrationTest` cover
  local PAPER exit behavior.
- `ModeSpecificExecutionBoundaryTest` verifies that PAPER does not claim
  external reconciliation.
- `ModeAwareRiskFactsProviderTest` covers source selection and PAPER/LIVE facts.

Likely focused additions are:

- explicit `SimulatedExecutionAdapter` rejection of LIVE submission;
- explicit routing assertions for LIVE versus PAPER without provider mutation;
- a cTrader/FTMO semantic fixture proving external economically simulated
  accounts remain LIVE;
- documentation/UI assertions only if wording changes.

Avoid duplicating the existing full PAPER vertical regression suite.

## Proposed Implementation Surface

Expected files/surfaces, subject to the approved Implementation Plan:

- `trading-core/.../brokeraccount/domain/ExecutionMode.java`
- focused Trading Core execution boundary tests
- focused cTrader/FTMO semantic test boundary, likely in Broker Service or a
  provider-neutral account/provisioning boundary depending on existing fixture
  ownership
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.html`
  and related tests if wording is changed
- `AGENTS.md`
- a new canonical ADR under `docs/architecture/adr/`
- this Story's implementation and review artifacts

No runtime account data, cTrader binding, provider configuration, execution
budget, snapshot cooldown, Risk formula, Challenge definition, or market data
should change.

## Risks and Open Decisions

1. **Challenge monitoring scope:** decide whether LIVE FTMO/cTrader Challenges
   must be monitored now or remain excluded until provider execution readiness.
2. **ADR placement:** a new ADR is appropriate because the Story establishes a
   cross-module semantic boundary and rejects competing interpretations.
3. **UI wording scope:** keep changes minimal and avoid renaming freshness
   statuses that also use the word LIVE.
4. **Historical documentation:** preserve historical Story/report text and add
   current canonical guidance rather than rewriting prior evidence.

## Repository Analysis Recommendation

Proceed with a focused Implementation Plan. The plan should preserve existing
execution routing, add the enum/domain documentation and invariant tests, add
the canonical ADR and coding-agent guardrail, and make only minimal UI wording
changes. It should explicitly decide whether the Challenge monitoring filter
is out of scope or requires a separate approved change.

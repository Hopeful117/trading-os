# Repository Analysis - Story 0170

## Story Understanding

Story 0170 adds a controlled cTrader demo execution capability after the
existing authentication and authoritative read-only synchronization stages. It
must not turn cTrader into an autonomous trader, leak provider details into
Trading Core, or bypass Challenge, Risk, ownership, idempotency, reconciliation,
or human-authorization controls.

The runtime target is one explicitly selected provider-confirmed demo account.
The Story does not authorize live execution, automatic account selection, or
blind retries after uncertain provider responses.

## Repository Baseline

| Item | Current value |
|---|---|
| Canonical Story | `docs/architecture/stories/0170-ctrader-demo-execution-and-reconciliation/story.md` |
| Related target decision | `docs/architecture/adr/ADR-071.md` |
| Working tree | Dirty; existing Story 0169 and cTrader connection changes are present and must not be reverted |
| DevLog | Story Agent attempted; unavailable because the provider returned an LLM error |
| cTrader runtime state | OAuth account binding exists; previous runtime account was bound but required reauthorization for trade permission |
| Current cTrader capability | Authoritative read-only snapshot only |
| Current cTrader execution | Not implemented |

## Architectural Authority

ADR-071 establishes the following boundaries:

- FTMO is the Challenge/business provider.
- cTrader is the broker/execution platform.
- Broker Service owns cTrader OAuth, tokens, transport, mappings, provider
  errors, connection lifecycle, synchronization, and provider capabilities.
- Trading Core owns provider-neutral execution authorization, deterministic Risk,
  execution state, Challenge state, and reconciliation decisions.
- Read-only synchronization, complete authoritative facts, freshness, and
  explicit human authorization precede any financial command.
- Unknown provider outcomes must not be retried blindly.

ADR-071 currently records execution readiness as `NOT_READY` and requires
account-specific proof before execution can be considered ready. This Story is
the implementation and validation work that can produce that proof; it does
not weaken the ADR invariants.

## Current cTrader Authentication And Connection

`CTraderAuthenticationService` currently:

1. creates an authorization attempt with the configured environment;
2. requests OAuth with `scope=accounts`;
3. exchanges the code and discovers authorized account IDs;
4. stores the token encrypted;
5. authorizes the selected account;
6. obtains a complete read-only observation;
7. marks the local connection `CONNECTED` with read-only permissions.

The current working tree already contains the previous `CREATED -> CONNECTED`
read-only transition change. That change is not the implementation of Story
0170 and must remain limited to read-only validation.

`CTraderAuthorizationAttempt` has a `permissionScope` field defaulted to
`accounts`, but the current flow does not populate it from the provider or use
it to establish trade permission.

`JsonWebSocketCTraderOpenApiClient` currently implements:

- application authentication;
- account discovery;
- account authorization;
- authoritative read-only observation using trader, assets, symbols,
  reconciliation, P&L, and deal-list requests.

Account discovery logs the provider `isLive` field but discards it. The current
connection stores only the configured environment and selected external account
ID. Therefore the repository cannot yet prove that a selected account is a
provider-confirmed demo account.

## Current Broker Capability Boundary

`BrokerCapabilities` already defines provider-neutral contracts for:

- `ExecutionCapability`;
- `ReconciliationCapability`;
- `OrderCapability`;
- `AuthoritativeSnapshotCapability`;
- technical and margin capabilities.

`CTraderBrokerProvider` exposes only `AuthoritativeSnapshotCapability`.
`KrakenCapabilities` is the existing reference implementation for execution
and reconciliation. It maps an `ExecutionRequest`, derives a stable provider
client order ID from the idempotency key, distinguishes rejected/unknown
outcomes, and reconciles by searching provider orders.

The cTrader adapter must follow this provider-neutral composition pattern. Its
protocol request/response types must remain inside Broker Service.

## Current Execution Contracts

The Broker Service `ExecutionRequest` currently contains:

- execution intent ID;
- execution attempt ID;
- idempotency key;
- broker account ID;
- instrument;
- side;
- order type;
- quantity;
- optional limit price.

It does not contain a stop-loss, take-profit, provider symbol ID, or explicit
execution constraints. This is a material gap because Story 0170 requires a
protective stop for the bounded demo proof and cTrader requires provider symbol
identity and volume units. The implementation plan must decide whether to extend
the shared broker-neutral execution model or introduce a separate safety-bound
execution constraint contract. Provider-specific fields must not be added to
Trading Core.

The existing broker-neutral results already distinguish:

- `Acknowledged`;
- `Rejected`;
- `Unknown`;
- `ReconciledOrder`;
- `ConfirmedAbsent`;
- `Inconsistent`.

These are reusable for cTrader, but cTrader reconciliation must query orders and
deals using a stable client reference and must preserve uncertainty when the
provider response cannot establish the final state.

## Current Trading Core Execution Guards

Trading Core exposes the existing execution lifecycle through:

```text
validate and create intent
    -> explicit execution endpoint
    -> ExecuteTradeService
    -> T1 Risk revalidation
    -> ChallengeExecutionAdmissionService
    -> broker submission
    -> response processing
    -> finalization
```

`ExecuteTradeService` already rejects terminal or expired intents, invokes the
Challenge execution admission service for account-bound intents, performs T1
Risk revalidation for entry executions, and then submits through the broker
port.

`ChallengeExecutionAdmissionService` currently validates canonical instrument
compatibility for active Challenges. It does not yet verify:

- fresh and complete provider snapshot facts immediately before execution;
- cTrader trade permission;
- provider-confirmed demo environment;
- stop-loss presence and provider constraints;
- cTrader-specific order reconciliation.

These checks must be added at the appropriate boundaries without placing cTrader
conditionals in Trading Core.

## Current Runtime And Safety Gaps

The following gaps block Story 0170 today:

1. OAuth requests only the account-access scope.
2. Effective provider trade permission is not detected or persisted.
3. Provider-confirmed demo/live account identity is not persisted or enforced.
4. cTrader has no order submission or reconciliation transport methods.
5. cTrader has no `ExecutionCapability` or execution-capable provider wiring.
6. The shared execution request has no protective-stop representation.
7. cTrader symbol metadata and volume-unit conversion are not available to an
   execution adapter.
8. Immediate pre-execution freshness/completeness admission is not explicit.
9. No cTrader-specific execution and unknown-outcome tests exist.
10. The current bound token was issued for read-only account access and must not
    be assumed to authorize a write operation.

## Required Implementation Shape

The implementation plan should preserve the following flow:

```text
human-approved execution intent
    -> Trading Core Challenge/Risk/admission guards
    -> fresh complete authoritative cTrader snapshot
    -> Broker Service provider-neutral ExecutionCapability
    -> cTrader NewOrder request with stable client reference
    -> acknowledged/rejected/unknown result
    -> authoritative order/deal/position reconciliation
```

The adapter must fail closed when any of these facts is absent or contradictory:

- selected external account identity;
- provider-confirmed demo environment;
- OAuth trade permission;
- current account and instrument constraints;
- fresh complete balance, margin, order, deal, and position facts;
- valid quantity and precision;
- protective stop accepted by the provider;
- stable idempotency reference.

## Runtime Validation Boundary

The runtime proof must be a separately invoked human-controlled action after
implementation and validation. It must:

- reauthorize the exact selected cTrader demo account with trade permission;
- confirm provider environment is demo and the external account ID matches;
- confirm the FTMO Challenge is active and readiness is `READY`;
- confirm the selected instrument and quantity satisfy provider facts and Risk;
- submit at most one minimal order with a protective stop;
- record the provider response without secrets;
- reconcile order/deal/position state authoritatively;
- verify no live account and no second order were touched.

No startup hook, test, migration, deployment, or automatic retry may submit the
runtime order.

## Risks And Decisions Required In The Plan

- The exact cTrader OAuth scope syntax and returned permission semantics must be
  verified against current official documentation and the exact application
  registration.
- The exact FTMO cTrader account's write permission and demo classification must
  be proven from provider facts, not inferred from the local environment.
- The shared execution model must gain protective-stop semantics without
  coupling Trading Core to cTrader.
- cTrader symbol ID and volume-unit conversion must be sourced from current
  provider metadata and validated against the order request.
- cTrader response and event payloads must be mapped into existing unknown and
  reconciliation states without treating an acknowledgement as a fill.
- A failed or ambiguous bounded demo order must stop the proof and require
  manual reconciliation; it must not automatically send a second order.

## Validation Expectations

- cTrader authentication and permission tests.
- Provider environment and account-identity tests.
- cTrader order mapping and volume/precision tests.
- cTrader provider capability composition tests.
- Acknowledged, rejected, timeout, protocol-error, and reconciliation tests.
- Trading Core execution-admission and protective-stop tests.
- Existing Kraken execution/reconciliation regressions.
- Affected Maven tests, `git diff --check`, and architecture boundary checks.
- Manual runtime read-only proof before the separately authorized demo write
  proof.

## Repository Analysis Conclusion

Story 0170 is implementable within the existing provider-neutral architecture,
but it is not a small OAuth-only change. The minimum coherent implementation
must extend both the cTrader adapter and the shared execution safety contract,
while retaining Trading Core's provider-neutral responsibility boundary.

The next workflow stage is an Implementation Plan. Implementation must not begin
until this Repository Analysis has been explicitly approved.

## Vault Outcome

The Obsidian vault was not consulted. The analysis was fully supported by the
current repository, accepted ADR-071, the Story artifacts, and current cTrader
Open API documentation. No vault action is proposed.

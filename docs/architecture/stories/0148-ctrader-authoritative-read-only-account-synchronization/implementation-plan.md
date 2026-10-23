# Story 0148 - Implementation Plan

## Status

`IMPLEMENTED_LOCALLY - REAL_PROVIDER_VALIDATION_REQUIRED`

This plan is based on the approved Repository Analysis, accepted ADR-071/072/073
and accepted ADR-074. The plan was approved before implementation.

## Outcome

Implement a Broker Service-owned cTrader READ-ONLY synchronization capability
that produces a complete, coherent, fresh and owner-scoped provider-neutral
observation for the explicitly bound external account.

The intended boundary is:

```text
cTrader authentication + binding
    -> Broker Service provider adapter
    -> provider-neutral authoritative observation
    -> Trading Core financial snapshot assembly
    -> deterministic Risk Domain
```

The result must preserve:

```text
authenticated + bound != synchronized
synchronized != Risk ready
Risk ready != execution ready
provider facts != business/provider rules
```

No cTrader execution or mutation capability is included.

## Preconditions

Implementation may begin only after:

- human approval of this Implementation Plan;
- ADR-074 is accepted and remains the governing synchronization boundary;
- current branch, HEAD and worktree are revalidated;
- pre-existing untracked reports remain untouched and unstaged;
- Story 0147 authentication/binding behavior remains green;
- no real cTrader credential is used in deterministic tests;
- no FTMO Challenge, Market Data, S1 or AI scope is introduced.

## 1. Contract Audit and Neutral Observation Model

First audit the current `BrokerModels`, v2 DTOs, capabilities, controller,
Feign client and Trading Core synchronization path. Preserve existing Kraken
contracts where possible and avoid creating a second parallel snapshot model.

Extend `BrokerAccountSnapshotV2` or introduce the smallest equivalent neutral
contract with explicit fields for:

- local `BrokerAccount` identity;
- opaque provider registration reference where required for routing only;
- bound external account identity, environment and account currency;
- balance, equity, used margin and free margin as nullable authoritative facts;
- normalized positions with provider references and provider timestamps;
- pending orders with provider references, statuses and quantities;
- bounded deals/fills with provider references, order/position references,
  execution time, quantity, price, commission, swap and realized P&L where
  available;
- provider precision/scaling metadata or already normalized `BigDecimal` values;
- observation start, observation completion and provider update timestamps;
- completeness state, freshness state and explicit unavailable/missing reasons.

Do not represent absent cTrader facts as zero, empty, current or inferred.
Do not expose cTrader protobuf/JSON classes, transport messages, tokens or raw
provider errors in the neutral contract.

The contract must distinguish provider-neutral observation facts from Trading
Core-owned derived `AccountSnapshot`, `PortfolioSnapshot`, Risk profiles and
authorization decisions.

Expected affected areas, subject to exact audit:

- `broker-service/.../broker/domain/model/`
- `broker-service/.../broker/domain/capability/`
- `broker-service/.../broker/api/`
- `broker-service/.../broker/application/`
- `trading-core/.../broker/dto/`
- `trading-core/.../broker/apiClient/`
- `trading-core/.../broker/service/`

## 2. Provider-Scoped cTrader Synchronization Port

Add a narrow provider-neutral synchronization capability or extend the existing
capability abstraction so Broker Service can request one owner-scoped account
observation without Trading Core knowing cTrader operations.

The operation must receive only:

- authenticated owner identity;
- local `BrokerAccount`/connection identity;
- bound external account identity;
- bounded observation request parameters;
- correlation/request ID.

The operation must return only the neutral observation or a sanitized
provider-neutral unavailable outcome.

Reuse the existing account/position/order/risk capability conventions instead of
creating an endpoint-specific orchestration abstraction without need. If the
current interfaces cannot express a bounded coherent read, add the smallest
capability extension and preserve existing Kraken implementations through
default or explicit capability behavior.

## 3. cTrader Read-Only Adapter

Implement provider-specific synchronization under Broker Service cTrader
infrastructure, alongside the existing authentication adapter. The adapter must
isolate:

- application/account session authentication reuse;
- account identity validation;
- account/trader facts retrieval;
- open position retrieval and unrealized P&L retrieval where required;
- pending order retrieval;
- bounded deal/fill retrieval;
- provider error and disconnect translation;
- demo/live endpoint validation;
- cTrader money precision and volume conversion.

Use the existing direct protocol boundary unless the implementation audit proves
that an SDK is unavoidable. Do not add a cTrader SDK by default.

The adapter may use only READ-ONLY cTrader operations. It must not implement or
register order placement, cancellation, amendment, position close, SL/TP or
other mutation requests.

Provider identifiers must be retained as opaque references. Symbol identity must
not be guessed when a safe provider-to-domain mapping is unavailable; that fact
must make the observation incomplete or unavailable according to the neutral
contract.

## 4. Binding, Ownership and Environment Validation

Before any provider read:

- authenticate the caller owner at the application boundary;
- resolve the local `BrokerAccount` and `BrokerConnection` through the canonical
  owner-scoped relation;
- verify the connection provider is cTrader;
- verify the durable external account binding exists;
- verify cTrader authentication/session material is valid or refreshable;
- verify the configured demo/live environment matches the bound account;
- verify the provider request uses exactly the durable bound external ID.

After every provider response:

- compare `ctidTraderAccountId` with the durable binding;
- reject any mismatch without changing the binding;
- reject account/environment ambiguity;
- never fall back to another provider, account or persisted observation.

The implementation must preserve the 0147 behavior that expiry, revocation or
disconnect invalidates current provider usability without deleting the durable
external identity.

## 5. Coherent Observation Orchestration

Implement one synchronization orchestration that records:

```text
observationStartedAt
providerRead timestamps
observationCompletedAt
required fact categories
received fact categories
missing/unavailable reasons
```

The orchestration must:

- use one bounded request window;
- retrieve all required fact categories before publishing a complete result;
- preserve individual provider timestamps;
- detect provider disconnect, timeout, malformed response and identity mismatch;
- avoid publishing a partial result as complete;
- reject a result exceeding the configured freshness/staleness threshold;
- never silently merge current and historical values into one fresh observation;
- make repeated synchronization idempotent using provider identifiers;
- make persistence failure fail closed when persistence participates in the
  current observation publication path.

If cTrader cannot provide a fact required by the selected observation contract,
return explicit incompleteness rather than deriving the fact from local data.

## 6. Persistence and Restart Semantics

Reuse existing persistence conventions, including stable provider identity
mappings, rather than introducing a second persistence system.

Persist only what is justified by the implementation audit:

- normalized observation metadata and version if operational history/restart
  tracking requires it;
- owner/local account/external account identity;
- completeness/freshness state and observation window;
- stable position/order/deal provider identifiers needed for deduplication.

Do not persist raw cTrader payloads, tokens or transport sessions.

After restart or reconnect, persisted facts may be retained for history but must
not be reported as current authoritative facts until a new authenticated
synchronization succeeds. Test this explicitly.

If a schema migration is required, follow existing migration conventions and do
not rewrite ambiguous historical Kraken data. Stop for an ADR amendment if the
migration changes the authority or identity boundary.

## 7. Trading Core Consumption and Risk Boundary

Update Trading Core only enough to consume the provider-neutral observation and
assemble its existing financial snapshots deterministically.

Trading Core must:

- route by local `BrokerAccount.id`;
- reject incomplete, stale, unavailable or identity-invalid observations;
- preserve account/position/order/deal provider references without provider DTOs;
- avoid interpreting provider facts as FTMO rules;
- avoid accepting a partial observation as valid Risk input;
- preserve existing Kraken synchronization and Risk behavior.

Do not add cTrader provider names, enums, SDK types, OAuth details or cTrader
error branches to Trading Core.

## 8. API and Error Semantics

Expose only the existing provider-neutral internal synchronization boundary or the
smallest versioned extension required by the contract. Do not expose raw cTrader
operations through a public API.

Map failures into sanitized provider-neutral outcomes that distinguish at least:

- not authenticated, expired or revoked;
- not bound or wrong environment;
- owner/provider/account mismatch;
- provider timeout/disconnect/unavailable;
- malformed or unsupported provider fact;
- incomplete observation;
- stale observation;
- persistence failure;
- unsupported capability.

Responses, logs, metrics and exceptions must redact tokens, credentials, raw
payloads and unnecessary provider identifiers.

## 9. Focused Tests

Add deterministic tests with synthetic owner, local account and external account
identities for:

- complete account/position/order/deal synchronization;
- exact bound-account response validation;
- cross-owner and wrong-account response rejection;
- unbound, expired, revoked and disconnected authentication;
- demo/live environment mismatch;
- missing account, positions, orders or deals;
- partial response and incoherent observation windows;
- provider timeout, disconnect, malformed response and unsupported fact;
- stale observation rejection and configured freshness boundary;
- cTrader money precision and volume normalization;
- provider symbol identity ambiguity;
- duplicate synchronization and stable provider identifiers;
- restart does not make historical data fresh;
- persistence failure fail-closed behavior;
- token/payload/error redaction;
- absence of cTrader mutation capabilities;
- Trading Core provider-neutral boundary;
- existing Kraken synchronization, RiskSnapshot and execution regressions.

Tests must assert domain outcomes, completeness/freshness metadata, ownership,
identity and persisted state, not only mock invocation counts.

## 10. Architecture and Boundary Checks

Add or run repository checks proving:

- no cTrader DTO, token, transport or provider error crosses into Trading Core;
- no FTMO Challenge rule appears in Broker Service synchronization code;
- no cTrader mutation capability is registered or reachable;
- no cTrader path falls back to Kraken;
- no missing provider fact is defaulted to zero or empty current data;
- no persisted observation is promoted to fresh after restart without a new read;
- existing Kraken provider and execution boundaries remain unchanged.

## 11. Validation Commands

Run the affected modules and repository checks:

```bash
cd broker-service
mvn test
```

```bash
cd trading-core
mvn test
```

```bash
git diff --check
```

Run migration/repository integration tests if persistence changes. Run the
repository quality/SonarQube workflow when applicable. Report every check as
executed, passed, failed, blocked or non-applicable.

## 12. Controlled Real-Provider Validation

Deterministic tests cannot prove cTrader protocol compatibility or target-account
fact availability. Any human-authorized real validation must use:

1. a human-created cTrader Open API application;
2. Broker Service configuration/secret management only;
3. `accounts`/view-only permission;
4. one owner-scoped already-bound demo or explicitly approved account;
5. evidence of account/position/order/deal read behavior without raw payloads;
6. evidence of completeness, timestamps, freshness, reconnect and restart
   handling;
7. proof that no mutation capability is enabled;
8. cleanup/revocation of test material.

No order, amendment, cancellation, close, SL/TP mutation or live Challenge
execution is permitted.

Expected final status before real validation:

```text
LOCAL_DETERMINISTIC_TEST = PASS
REAL_CTRADER_READ_VALIDATION = REQUIRED
CTRADER_EXECUTION = NO
EXECUTION_READINESS = NOT_READY
```

## 13. Stop Conditions

Stop and request an ADR amendment or human decision if:

- the neutral contract cannot preserve completeness/freshness without leaking
  cTrader types;
- cTrader requires trading scope for the requested read-only facts;
- response identity cannot be matched to the durable binding;
- a second credential/persistence system is required;
- the adapter requires a provider SDK that changes the approved boundary;
- a schema migration rewrites ambiguous historical data;
- the implementation would make Trading Core apply FTMO rules;
- a partial/stale observation must be accepted to make tests or UI pass;
- real validation requires mutation permission;
- implementation touches Market Data, S1, AI or Challenge lifecycle semantics.

## Deliverables

- provider-neutral authoritative synchronization contract;
- cTrader Broker Service READ-ONLY adapter and orchestration;
- explicit completeness/coherence/freshness and identity validation;
- persistence/restart behavior required by the approved design;
- Trading Core consumption without provider leakage;
- focused tests and Kraken regression evidence;
- updated Story implementation report;
- subsequent code review and engineering reports;
- controlled real-provider READ-ONLY validation procedure/status.

## Next Workflow Gate

Human approval of this Implementation Plan is required before implementation may
begin. Approval authorizes the implementation stage only; it does not authorize
real-provider credentials, live trading, mutation capabilities or execution.

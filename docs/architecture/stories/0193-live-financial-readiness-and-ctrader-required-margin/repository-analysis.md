# Repository Analysis

## Story Understanding

Story 0193 requests the implementation of the LIVE financial-readiness model
approved by ADR-084. The requested behavior separates four concerns:

- capability-scoped completeness of a current provider observation;
- readiness of the facts required by the selected deterministic risk policy;
- readiness of a proposed-order preflight, including provider-authoritative
  required margin;
- execution readiness, including fresh T1 validation and human authorization.

The cTrader-specific part must remain inside Broker Service. Trading Core must
consume only provider-neutral facts and must reuse `RequiredMarginPort` for both
PAPER and LIVE, with different authority implementations. Risk Domain must
continue to consume normalized deterministic facts only.

The explicit scope includes the neutral snapshot/readiness semantics, deal
availability propagation, the cTrader ExpectedMargin adapter for a validated
support subset, fail-closed LIVE required-margin handling, T0/T1 integration and
deterministic tests. The Story also requires a controlled READ-ONLY provider
validation boundary.

The explicit exclusions include local cTrader equity or free-margin derivation,
generic account-margin aggregation from position margins, changes to PAPER
margin authority, provider mutations, frontend/database work, automatic
trading authorization and rewriting historical ADR or Story artifacts.

## Repository Summary

Trading OS is split into independent Maven services and shared domain modules.
The relevant boundary is:

```text
cTrader protocol and provider facts
    -> broker-service
    -> provider-neutral broker contracts
    -> trading-core risk/preflight orchestration
    -> risk-domain deterministic calculations
```

Broker Service already exposes a provider-neutral account snapshot capability
and a provider-neutral margin-preview capability. Trading Core already has
separate LIVE/PAPER fact loading, required-margin resolution, T0 preflight and
T1 execution-time revalidation. The current implementation is stricter than
ADR-084 in two places: account snapshot completeness requires all account
financial scalars, and risk-facts admission requires all of those scalars even
when a selected policy may not consume them.

## Affected Modules

- `broker-service`
  - `broker.domain.model.BrokerModels` owns `BrokerAccountSnapshotV2`, account
    facts, position/order/deal snapshots and collection availability.
  - `ctrader.synchronization.CTraderReadOnlyObservation` keeps cTrader-owned
    fields, provider-field presence and collection state inside the adapter.
  - `ctrader.synchronization.CTraderAuthoritativeSnapshotCapability` validates
    account identity and freshness and maps cTrader observations to the neutral
    snapshot.
  - `ctrader.authentication.JsonWebSocketCTraderOpenApiClient` performs the
    current cTrader READ-ONLY requests and decodes trader, reconcile, PnL and
    deal responses.
  - `broker.domain.capability.BrokerCapabilities.MarginCapability` and
    `BrokerOperationServices` already provide the neutral margin-preview
    boundary. The cTrader provider currently has no identified ExpectedMargin
    implementation in the inspected source.

- `trading-core`
  - `risk.application.port.RequiredMarginPort` is the existing neutral
    proposed-order margin boundary and already carries source, version,
    observation time, context and evidence fields.
  - `risk.infrastructure.client.BrokerRequiredMarginClient` dispatches PAPER
    to the local margin client and LIVE to Broker Service, validates technical
    facts and rejects stale, invalid or unproven results.
  - `risk.infrastructure.client.ModeAwareRiskFactsProvider` selects PAPER
    local facts or LIVE authoritative snapshot facts, validates identity and
    currently requires valuation asset, balance, equity, account margin and
    free margin for a complete LIVE risk snapshot.
  - `risk.application.TradePlanRiskEvaluationService` performs the existing T0
    risk preflight and persists risk results.
  - `execution.application.service.ExecutionTimeRiskRevalidationService`
    performs the existing T1 account/snapshot/market/risk checks and resolves
    required margin before building the proposed trade.
  - `broker` DTO/client classes carry the current snapshot and margin-preview
    HTTP contracts. They are provider-neutral but currently model account
    `equity`, `usedMargin` and `freeMargin` as ordinary nullable fields without
    a capability-specific readiness contract.

- `risk-domain`
  - `risk.snapshot.AccountSnapshot`, `ObservedMetricsCalculator`,
    `ProjectionEngine` and the risk rules consume normalized account facts.
  - The module contains no cTrader protocol handling and must remain free of
    cTrader DTOs, enums, scaling rules or provider-specific margin formulas.

## Existing Implementation

### Broker Service observation

`CTraderOpenApiClient.readOnlyObservation` currently requests trader data,
assets, symbols, reconcile state, position PnL and a bounded deal list. The JSON
mapper preserves provider monetary scaling and maps positions, orders, deals and
position PnL into `CTraderReadOnlyObservation`.

`CTraderReadOnlyObservation` already represents provider-field presence and
availability for positions, orders and PnL. Its collection enum currently has
`UNAVAILABLE_OR_ABSENT`, `AVAILABLE_EMPTY` and `AVAILABLE_NON_EMPTY`, but no
`TRUNCATED` value. Deal collection state is not represented independently in
the observation record even though `hasMore` is detected and produces the
`DEAL_HISTORY_TRUNCATED` reason.

`CTraderAuthoritativeSnapshotCapability` maps the observation to
`BrokerAccountSnapshotV2`. It currently marks the snapshot complete only when
valuation asset, balance, equity, account used margin, free margin, PnL
completeness and freshness all satisfy the strict completeness check. It maps
positions, orders, deals and position PnL, but `BrokerAccountSnapshotV2` has no
deal availability field. Therefore a deal list that is absent or truncated is
not preserved as a first-class neutral collection state.

The existing cTrader tests cover identity validation, freshness, precision,
position/order/PnL availability, malformed collections, missing position
margin, missing PnL and truncated deal reasons. They also currently encode the
behavior that an omitted valid repeated field may be treated as authoritative
empty by the decoder.

### Broker Service proposed-order margin

The neutral broker capability model already contains `MarginCapability` and
`MarginPreviewRequest`/`MarginPreview`. The internal controllers expose
`POST /internal/v1/broker-accounts/{id}/margin-preview` and the provider-scoped
variant. `BrokerOperationServices` resolves the provider capability and records
the operation.

The inspected provider implementations include a Kraken margin-preview
implementation. No cTrader ExpectedMargin request/response mapping or support
matrix was found in the inspected cTrader source. The cTrader OpenAPI client
currently contains read-only observation, instrument metadata, execution
contract and quote operations, but no ExpectedMargin operation.

### Trading Core required margin and readiness

`RequiredMarginPort` already represents a proposed-order margin fact with
amount, currency, source identity, source version, observation time and
optional durable context/evidence. `BrokerRequiredMarginClient` preserves the
PAPER/LIVE authority split, validates broker technical facts and rejects stale,
invalid, mismatched or unavailable LIVE previews. This is the reusable neutral
boundary required by ADR-084; a second margin port is not present or needed.

`TradePlanRiskEvaluationService` performs T0 preflight through the existing
risk evaluation flow. `ExecutionTimeRiskRevalidationService` performs T1,
loads LIVE facts through `ModeAwareRiskFactsProvider`, validates the broker
snapshot, obtains market valuation and resolves required margin before creating
the proposed trade. Missing required margin currently produces an unavailable
T1 result and prevents continuation.

The current T1 implementation also reads `brokerSnapshot.account().equity()`
and `margin()` as mandatory values through its risk-fact path. This is
consistent with the current strict snapshot contract but conflicts with the
capability-scoped and policy-dependent semantics required by Story 0193.

`ModeAwareRiskFactsProvider` currently marks a LIVE authoritative snapshot
incomplete when any of valuation asset, balance, equity, used margin or free
margin is null. It also marks a position incomplete when position margin is
missing. It maps deals into the neutral risk snapshot but has no independent
deal-history availability input; an empty deals list is therefore not
distinguishable from an unavailable deal collection at this boundary.

### Risk Domain

Risk Domain models `AccountSnapshot` with required balance, equity and used
margin values and calculates free margin as `equity - usedMargin` for its
observed metrics. That model is suitable for a normalized risk context only
when the selected risk policy has authoritative values for the fields it
consumes. It must not be changed to interpret cTrader protocol data or to
derive provider-authoritative cTrader values. The Story requires the affected
readiness/admission path to avoid passing invented values into this domain.

### Existing behavior that must remain unchanged

- PAPER required margin remains governed by the local versioned
  `PaperRequiredMarginClient`/`MarginPolicy` path.
- Broker Service remains the owner of cTrader protocol decoding, scaling,
  capability detection and provider error translation.
- Risk Domain remains deterministic and provider-neutral.
- cTrader observation and margin capability paths remain READ-ONLY; no order
  mutation capability may be added.
- T0/T1 and human-authorization execution gates remain mandatory.

## Relevant Documentation

- `AGENTS.md`
- `docs/architecture/adr/ADR-028.md`
- `docs/architecture/adr/ADR-043.md`
- `docs/architecture/adr/ADR-071.md`
- `docs/architecture/adr/ADR-074-ctrader-authoritative-read-only-synchronization.md`
- `docs/architecture/adr/ADR-078-paper-live-execution-boundary.md`
- `docs/architecture/adr/ADR-082-cfd-financial-model-and-mark-to-market-accounting.md`
- `docs/architecture/adr/ADR-083-paper-margin-authority-and-simulation-policy.md`
- `docs/architecture/adr/ADR-084-live-financial-facts-and-provider-preflight-readiness.md`
- `docs/architecture/stories/0148-ctrader-authoritative-read-only-account-synchronization/`
- `docs/architecture/stories/0193-live-financial-readiness-and-ctrader-required-margin/story.md`
- `docs/workflow/ai-workflow.md`
- `docs/workflow/ai-roles.md`
- `docs/workflow/story-template.md`
- `docs/workflow/prompts/common-principles.md`
- `docs/architecture/README.md`

DevLog was consulted for project context, but the returned context was
truncated. It was used only to prioritize verification; the current repository
is authoritative for the implementation facts above. The curated Obsidian
vault was not needed for this repository-specific analysis.

## Constraints

- Current-account completeness must be scoped to a declared capability; it
  cannot require every financial scalar for every broker capability.
- Missing facts must remain absent and must not become zero, empty, false or
  locally derived values without an approved semantic contract.
- Equity-dependent risk remains fail-closed when authoritative equity is
  unavailable.
- Missing free margin must not universally disable cTrader, but a policy that
  explicitly requires free margin must fail closed.
- Position-level margin, account-level used margin and proposed-order required
  margin are separate facts.
- Deal availability must distinguish empty, non-empty, unavailable/absent and
  truncated collections.
- LIVE proposed-order margin must come from provider-authoritative semantics
  through `RequiredMarginPort`; leverage, notional, position margin, free
  margin or order rejection cannot be fallback sources.
- PAPER must continue to use the local deterministic margin authority.
- Trading Core and Risk Domain must not contain cTrader DTOs, protocol messages,
  enums, `moneyDigits` handling or a cTrader margin engine.
- Required facts must be fresh, identity-bound and provenance-bearing at the
  applicable T0/T1 boundary.
- No cTrader provider mutation, automatic trading authorization, credential
  use or uncontrolled provider call is authorized by this Story.
- Historical ADRs and Story 0183 artifacts must not be rewritten.

## Risks

- **Contract compatibility risk:** changing snapshot completeness or adding
  availability fields affects Broker Service payloads, Trading Core DTOs,
  synchronization, reconciliation and risk consumers. Existing constructors and
  tests encode the current strict semantics.
- **Fail-open risk:** relaxing the universal account-fact check without making
  policy requirements explicit could admit an equity-dependent or deal-dependent
  risk evaluation with incomplete facts.
- **Semantic conflation risk:** reusing position margin or current account
  margin for proposed-order margin would violate ADR-082/083 and could allow a
  provider order to be submitted without a valid preflight quote.
- **Deal-history risk:** the current decoder detects `hasMore` but the neutral
  snapshot does not preserve deal availability, so treating an omitted or
  truncated collection as empty could understate realized-loss risk.
- **Provider protocol risk:** ExpectedMargin support is not present in the
  inspected cTrader adapter. Unsupported request combinations, protocol enum
  values, volume/price scaling and monetary precision must fail closed rather
  than be approximated.
- **Readiness propagation risk:** T0 and T1 currently rely on a complete risk
  snapshot and existing required-margin resolution. Introducing distinct
  readiness states without preserving persisted provenance could make a
  preflight appear ready while its safety-critical facts are stale or from a
  different account.
- **Risk Domain compatibility risk:** `risk-domain` currently requires account
  equity and used margin for its normalized `AccountSnapshot`. Passing null or
  invented substitutes would violate its invariants; any change must preserve
  the domain boundary and deterministic behavior.

## Open Questions

- The repository does not yet identify the exact cTrader ExpectedMargin request
  support subset, including supported order types and any required provider
  instrument/volume/price constraints. The Implementation Plan must resolve
  this from the available cTrader protocol/client dependencies and record the
  supported capability matrix.
- The neutral `BrokerAccountSnapshotV2` contract has no deal availability field
  and the current collection enum has no `TRUNCATED` value. The Implementation
  Plan must define the backward-compatible contract shape and consumer behavior
  before implementation.

## Recommendation

Ready for planning

The ownership boundaries, existing extension points and safety constraints are
sufficiently understood for an Implementation Plan. The two open questions are
implementation-contract details explicitly within the approved Story and must
be resolved in the plan before code changes begin.

## Implementation Readiness

The repository contains the principal technical prerequisites:

- a provider-neutral snapshot capability and HTTP contract;
- a cTrader READ-ONLY observation adapter;
- a provider-neutral `RequiredMarginPort`;
- Broker Service margin-preview capability boundaries;
- PAPER and LIVE margin authority separation;
- T0 preflight and T1 revalidation services;
- deterministic Broker Service and Trading Core tests.

The missing behavior is capability-scoped snapshot completeness, independent
deal availability propagation, policy-dependent readiness, cTrader ExpectedMargin
adaptation and the corresponding T0/T1 admission rules and tests. No blocking
ADR conflict or missing ownership was found. No production implementation is
authorized by this artifact.

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.

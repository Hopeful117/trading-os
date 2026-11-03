# Implementation Plan

## Overview

Implement Story 0193 by extending the existing provider-neutral observation and
margin-preview boundaries rather than introducing parallel contracts.

The implementation will make current-account completeness capability-scoped,
propagate deal-history availability independently, and evaluate risk/preflight
readiness from the facts required by the selected policy. Broker Service will
add the supported cTrader ExpectedMargin capability behind the existing neutral
`MarginCapability` and `margin-preview` endpoint. Trading Core will continue to
use `RequiredMarginPort`, with PAPER remaining local-policy authoritative and
LIVE remaining provider authoritative. T0 and T1 will fail closed when their
required normalized facts or required-margin provenance are unavailable,
stale, unsupported or invalid.

No cTrader protocol type will cross into Trading Core or Risk Domain. No
provider mutation capability, database migration or external dependency is
planned.

## Planned Changes

### 1. Extend neutral collection and capability contracts

- **Component:** Broker Service domain models and Trading Core broker DTOs.
- **Change:** Add explicit deal collection availability, including
  `AVAILABLE_EMPTY`, `AVAILABLE_NON_EMPTY`, `UNAVAILABLE_OR_ABSENT` and
  `TRUNCATED`, to the neutral snapshot contract. Preserve constructor and JSON
  compatibility where practical through additive fields/defaults.
- **Change:** Introduce the smallest neutral capability declaration/readiness
  representation needed to state which current facts a snapshot claims to
  cover. The contract must distinguish observation completeness from policy and
  execution readiness; it must not require all financial scalars universally.
- **Reason:** The current snapshot has independent position/order/PnL
  availability but no deal availability and treats account fields as universal.
- **Constraint:** Missing values remain absent. Position margin cannot become
  account used margin, and proposed-order margin remains separate.

### 2. Correct cTrader READ-ONLY observation semantics

- **Component:** `CTraderReadOnlyObservation`,
  `JsonWebSocketCTraderOpenApiClient` and
  `CTraderAuthoritativeSnapshotCapability`.
- **Change:** Preserve deal collection state independently from the deal values.
  Map `hasMore=true` to `TRUNCATED`; do not classify an omitted deal collection
  as authoritative empty unless the validated provider response contract proves
  that meaning. Keep position/order/PnL handling compatible with their existing
  validated semantics.
- **Change:** Make snapshot completeness depend on the requested observation
  capability and freshness/identity coherence, not on unconditional presence of
  cTrader `equity`, account `usedMargin` and `freeMargin`.
- **Change:** Continue to preserve provider field presence, monetary scale,
  position-level margin, PnL coverage, missing reasons and account identity.
- **Reason:** Broker Service owns cTrader decoding and must expose only
  provider-authoritative facts without inventing account-level semantics.
- **Constraint:** The observation path remains READ-ONLY and must continue to
  use `CTraderRequestSafetyGuard`; no order operation is added.

### 3. Propagate capability-scoped facts into Trading Core

- **Component:** `BrokerAccountSnapshotV2Dto`, account-facts DTOs,
  `BrokerAccountSnapshotV2Client`, `ModeAwareRiskFactsProvider` and consumers
  such as synchronization/reconciliation services.
- **Change:** Map the additive availability/capability fields without converting
  absent facts into zero or empty values.
- **Change:** Separate observation completeness from policy-dependent risk
  readiness. A policy requiring authoritative equity, complete deals, current
  positions or another safety-critical fact must fail closed; unrelated policies
  must not be blocked by an unused absent fact.
- **Change:** Preserve explicit deal availability through the risk-facts
  boundary so an authoritative empty collection is distinguishable from absent
  or truncated history.
- **Reason:** Current `ModeAwareRiskFactsProvider` marks every LIVE snapshot
  incomplete when equity, used margin or free margin is absent and does not
  carry deal availability.
- **Constraint:** Trading Core remains provider-neutral and must not inspect
  cTrader protocol fields, enums or `moneyDigits`.

### 4. Implement cTrader ExpectedMargin capability

- **Component:** cTrader OpenAPI client/adapter, `CTraderBrokerProvider`,
  `BrokerCapabilities.MarginCapability`, and existing margin-preview services.
- **Change:** Add a cTrader adapter operation for the explicitly supported
  `ProtoOAExpectedMarginReq/Res` subset. Keep protocol message identifiers,
  enum handling, account/instrument identity, volume/price scaling, money
  precision and unsupported-response classification inside Broker Service.
- **Change:** Expose the adapter through the existing `MarginCapability` so the
  existing `/internal/v1/broker-accounts/{id}/margin-preview` path can serve
  cTrader without a new provider-specific Trading Core API.
- **Change:** Return only a positive, monetary, fresh, account/instrument-bound
  neutral `MarginPreview` with source identity and source version. Unsupported,
  ambiguous, stale or invalid ExpectedMargin results must fail closed.
- **Reason:** LIVE required margin must be provider-authoritative before any
  provider mutation; provider rejection cannot substitute for preflight.
- **Constraint:** The support matrix must be explicit. No fallback to leverage,
  notional, position margin, current account margin or free margin is allowed.

### 5. Refine `RequiredMarginPort` evidence and readiness admission

- **Component:** `RequiredMarginPort`, `BrokerRequiredMarginClient`, margin
  evidence/context types and the T0/T1 risk services.
- **Change:** Preserve or extend provenance so LIVE required-margin facts retain
  provider source, source version, observation time, request identity and
  applicable freshness/support evidence at both T0 and T1.
- **Change:** Keep PAPER resolution on `PaperRequiredMarginClient` and its
  versioned `MarginPolicy`; only LIVE dispatches to Broker Service.
- **Change:** Ensure missing, stale, unsupported or invalid LIVE margin produces
  an unavailable readiness result before provider submission, with a stable
  reason code and no fallback calculation.
- **Reason:** The existing port and clients already implement much of this
  behavior; the Story requires the semantics to remain explicit while the new
  cTrader capability is added.
- **Constraint:** Do not create a second margin port or move provider logic into
  Risk Domain.

### 6. Integrate distinct readiness at T0 and T1

- **Component:** `TradePlanRiskEvaluationService`,
  `ExecutionTimeRiskRevalidationService`, execution admission/lifecycle paths
  and related persisted risk evidence.
- **Change:** Evaluate observation completeness, policy risk readiness, LIVE
  preflight readiness and execution readiness as separate decisions. Existing
  human authorization, account identity, execution-mode and execution-enabled
  gates remain mandatory.
- **Change:** At T0 and T1, request and validate the required margin for the
  applicable proposed order and preserve its provenance in the existing risk
  evidence/persistence payloads. T1 must independently revalidate the required
  fact rather than trusting T0 alone.
- **Change:** Ensure the unavailable path transitions and reason codes remain
  fail-closed and do not create an execution attempt.
- **Reason:** Current services already resolve margin before building the
  proposed trade, but their account-fact admission is based on the old strict
  snapshot semantics.
- **Constraint:** No provider order, cancellation, amendment or close operation
  may be invoked by readiness evaluation.

### 7. Update deterministic tests and controlled validation boundary

- **Component:** Broker Service, Trading Core and relevant Risk Domain tests.
- **Change:** Add/update tests for capability-scoped account facts, missing
  equity, missing free margin, missing account used margin, position margin
  separation, deal empty/non-empty/unavailable/truncated states, cTrader
  ExpectedMargin scaling and unsupported semantics, provenance, stale results,
  T0/T1 revalidation and no-provider-mutation behavior.
- **Change:** Add a controlled READ-ONLY validation procedure/evidence boundary
  for cTrader ExpectedMargin only if repository conventions provide a canonical
  location; otherwise record the provider-validation limitation in the
  implementation report without credentials or live calls.
- **Reason:** The acceptance criteria require deterministic coverage and honest
  reporting of provider-validation limitations.
- **Constraint:** Deterministic tests must not call cTrader or use credentials.

## Files to Modify

- `broker-service/src/main/java/com/hope/trading/broker_service/broker/domain/model/BrokerModels.java`
  - Add neutral deal availability/capability fields while preserving validation
    invariants and compatibility constructors.
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/synchronization/CTraderReadOnlyObservation.java`
  - Represent deal availability and any neutral observation capability metadata.
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/authentication/CTraderOpenApiClient.java`
  - Add the adapter-facing ExpectedMargin operation if the existing client
    abstraction is the established extension point.
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/authentication/JsonWebSocketCTraderOpenApiClient.java`
  - Decode the supported ExpectedMargin protocol subset and preserve cTrader
    scaling/unsupported semantics; correct deal availability mapping.
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/synchronization/CTraderAuthoritativeSnapshotCapability.java`
  - Map capability-scoped completeness and deal availability into neutral facts.
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/CTraderBrokerProvider.java`
  - Expose the cTrader margin capability without enabling provider mutation.
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/domain/capability/BrokerCapabilities.java`
  - Modify only if the existing neutral margin capability needs an additive
    request/result distinction for ExpectedMargin support.
- `trading-core/src/main/java/com/hope/trading/trading_core/broker/dto/BrokerAccountSnapshotV2Dto.java`
  - Propagate neutral capability and deal availability fields.
- `trading-core/src/main/java/com/hope/trading/trading_core/broker/dto/BrokerAccountFactsV2Dto.java`
  - Preserve nullable/provider-absent account facts without universal
    completeness assumptions.
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/port/RiskFactsProvider.java`
  - Carry the neutral availability/readiness inputs required by risk policy
    evaluation.
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/client/ModeAwareRiskFactsProvider.java`
  - Remove universal financial-scalar admission and apply policy-dependent
    fail-closed checks.
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/port/RequiredMarginPort.java`
  - Extend evidence only if required to preserve LIVE ExpectedMargin provenance.
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/client/BrokerRequiredMarginClient.java`
  - Validate the cTrader-capability result and preserve LIVE/PAPER authority.
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/TradePlanRiskEvaluationService.java`
  - Integrate distinct T0 risk/preflight readiness and evidence where current
    snapshot completeness assumptions are consumed.
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/service/ExecutionTimeRiskRevalidationService.java`
  - Integrate independent T1 readiness and required-margin revalidation.
- `risk-domain` risk snapshot/metric consumers only if the normalized contract
  must be adjusted; retain provider neutrality and deterministic invariants.

## Files to Create

- A cTrader ExpectedMargin adapter class under
  `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/` if
  keeping the operation separate from `JsonWebSocketCTraderOpenApiClient` is
  required by the existing adapter conventions.
- Focused test classes under the existing Broker Service cTrader capability and
  Trading Core risk infrastructure test packages for new readiness and
  ExpectedMargin behavior.
- A provider-validation procedure/evidence artifact only if a canonical
  repository location already exists; otherwise no new operational document is
  created and the limitation is recorded in the Implementation Report.

## Dependencies

- Existing cTrader websocket/session request infrastructure and protocol
  constants or generated message support available in the repository/build
  dependencies.
- Existing Broker Service capability registry and internal margin-preview
  controllers.
- Existing Trading Core Feign client, `RequiredMarginPort`, market valuation,
  risk persistence and T0/T1 lifecycle services.
- Existing cTrader account identity, safety guard and credential boundaries.
- No new external dependency is planned. If the build does not expose the
  required ExpectedMargin protocol messages, implementation must stop and
  report the missing dependency rather than approximate the protocol.

Ordering dependencies:

1. Establish the neutral collection/capability contract.
2. Adapt cTrader observation mapping to that contract.
3. Propagate the contract through Trading Core risk facts.
4. Implement and expose cTrader ExpectedMargin through the existing Broker
   capability boundary.
5. Integrate T0/T1 readiness and provenance.
6. Complete focused deterministic tests and any authorized READ-ONLY evidence.

## Test Plan

### Broker Service

- Update `CTraderReadOnlyObservation` and
  `CTraderAuthoritativeSnapshotCapability` tests for nullable optional account
  facts, capability-scoped completeness and deal availability.
- Update `JsonWebSocketCTraderOpenApiClientTest` for authoritative empty,
  non-empty, absent and truncated deal collections; preserve tests proving no
  local equity/free-margin/account-margin derivation.
- Add cTrader ExpectedMargin adapter tests for supported requests, account and
  instrument identity, money precision, volume/price scaling, unsupported
  semantics, invalid responses and stale/unavailable responses.
- Verify the cTrader provider exposes `MarginCapability` but no new mutation
  capability.

### Trading Core

- Update `ModeAwareRiskFactsProviderTest` for policy-dependent admission:
  missing equity blocks equity-dependent risk, missing free margin does not
  universally block unrelated policy paths, and missing/truncated deals block
  deal-dependent policies.
- Update `BrokerRequiredMarginClientTest` for provider-authoritative LIVE
  facts, provenance, stale/unsupported/mismatched responses and continued
  PAPER local-policy behavior.
- Update `TradePlanRiskEvaluationServiceTest` and
  `ExecutionTimeRiskRevalidationServiceTest` for T0/T1 missing-margin,
  readiness-state and independent revalidation behavior.
- Preserve execution-pipeline regression tests proving no execution attempt is
  created when readiness is unavailable.

### Risk Domain and module validation

- Run existing Risk Domain regression tests to prove no cTrader types or
  provider-specific margin semantics enter the domain.
- Run focused tests first, then the affected Broker Service and Trading Core
  module test suites.
- Execute `git diff --check`.
- If provider validation is separately authorized, perform only the canonical
  READ-ONLY ExpectedMargin validation with execution safety disabled; otherwise
  report it as unavailable/incomplete.

Expected success conditions are deterministic test/build success, preserved
PAPER behavior, fail-closed LIVE safety-critical paths, no provider mutation,
and neutral contracts free of cTrader implementation details. No tests have
been run as part of this plan.

## Risks

- **Neutral contract breakage:** additive fields and compatibility constructors
  must be used so existing Broker Service/Trading Core consumers do not silently
  reinterpret JSON. Tests will cover old and new construction paths.
- **Policy requirement ambiguity:** readiness evaluation must use explicit policy
  fact requirements, not a universal boolean. The implementation must stop if
  the current risk-profile model cannot express the required distinction.
- **ExpectedMargin protocol uncertainty:** exact supported cTrader message and
  request semantics must be verified from repository dependencies before code is
  written. Unsupported combinations remain unavailable rather than guessed.
- **Persisted evidence drift:** T0/T1 response and margin evidence must retain
  the same source identity/version/time used for the decision. Existing
  persistence payloads should be extended only if they cannot carry the new
  neutral evidence.
- **Risk Domain nullability:** no null or invented substitute may be passed into
  `AccountSnapshot`; the adapter/readiness boundary must reject or route
  policy-inapplicable missing facts before domain evaluation.
- **Provider safety:** all tests use mocks/fixtures and no cTrader credential or
  mutation path. Any controlled validation remains a separately authorized
  READ-ONLY action.

## Validation Checklist

- [ ] `BrokerAccountSnapshotV2` completeness is capability-scoped.
- [ ] Missing facts are preserved as missing and never silently derived.
- [ ] Deal availability distinguishes empty, non-empty, absent/unavailable and
      truncated states through the neutral boundary.
- [ ] Equity-dependent policies fail closed without authoritative equity.
- [ ] Missing free margin does not universally disable cTrader.
- [ ] Account used margin is never derived from position margins or leverage.
- [ ] Position margin and proposed-order required margin remain distinct.
- [ ] cTrader ExpectedMargin support is explicit and unsupported semantics fail
      closed.
- [ ] LIVE required margin is provider-authoritative through `RequiredMarginPort`.
- [ ] PAPER remains governed by the versioned local `MarginPolicy`.
- [ ] Required-margin identity, freshness, source and provenance are preserved
      at T0 and independently revalidated at T1.
- [ ] No cTrader DTOs, enums, protocol messages or `moneyDigits` semantics enter
      Trading Core or Risk Domain.
- [ ] No provider mutation capability is added or enabled.
- [ ] Focused Broker Service, Trading Core and Risk Domain tests pass.
- [ ] Affected Maven module validation and `git diff --check` pass.
- [ ] Provider validation is either performed only under an authorized
      READ-ONLY procedure or explicitly reported as unavailable.
- [ ] No frontend, database, historical ADR/Story or unrelated broker changes
      are included.
- [ ] Documentation reconciliation records whether a canonical provider
      validation artifact was required.

## Recommendation

Ready for implementation

The implementation strategy is defined using existing boundaries and the
approved Story/ADR constraints. The ExpectedMargin support matrix and exact
neutral contract additions are implementation decisions within the approved
scope, not unresolved product or architecture choices. If the required cTrader
protocol support is absent from build dependencies, implementation must stop and
request guidance rather than introduce an approximation.

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.

# Repository Analysis - Story 0176

## Status

Analysis complete. Implementation is blocked pending human resolution of a
scope/architecture contradiction concerning partial close.

## Baseline

- Branch: `main`
- HEAD: `aa25df717af1c1186c7f70409780e54a46442536`
- `git pull --ff-only`: already up to date
- Existing untracked runtime artifacts were preserved.
- DevLog context unavailable: authentication required.

## Existing Architecture

### Broker capability

`BrokerCapabilities.PositionManagementCapability` currently exposes only:

- `resolveTarget(ResolveTargetRequest)`
- `executeClose(ExecuteCloseRequest)`
- `reconcile(ReconcileCloseRequest)`

`ExecuteCloseRequest` contains only:

- broker account ID;
- resolved mutation scope;
- idempotency key.

There is no broker-neutral close quantity or partial/full close discriminator.

### Existing provider implementations

`KrakenPositionManagementCapability` is the only current implementation. Its
semantics are provider-specific: it resolves a read reference to a pair/side
mutation scope, aggregates exposure and submits a reduce-only opposite-side
market order. It must remain a reference for lifecycle outcomes, not a cTrader
specification.

`CTraderBrokerProvider` currently registers:

- `CTraderAuthoritativeSnapshotCapability`;
- `CTraderTechnicalCapabilities`;
- `CTraderExecutionCapability` when enabled.

It does not register `PositionManagementCapability`.

### cTrader provider boundary

`CTraderOpenApiClient` currently supports order submission and read-only
observation, but has no broker-neutral or provider-specific close request
method. `JsonWebSocketCTraderOpenApiClient` already parses cTrader
`positionId`, position volume and close-deal facts during observations.

ADR-040 records the cTrader provider semantics as:

- explicit `int64 positionId` identity;
- `ProtoOAClosePositionReq` addressed by `positionId + volume`;
- full and partial close supported by the provider;
- oversized volume rejected by cTrader;
- `clientMsgId` correlation is not a documented idempotency guarantee.

### Existing Trading Core path

Trading Core already exposes:

`POST /api/v1/accounts/{accountId}/positions/close`

and a reconciliation endpoint. The Gateway's `/api/v1/accounts/**` route forwards
this broker-neutral path to Trading Core. Angular already invokes the same
workflow through `PositionService`.

Trading Core passes only the broker position reference and idempotency key into
the existing close port. It contains no cTrader-specific logic.

### Existing cTrader quantity contract

`CTraderExecutionInstrumentContract.providerVolume(BigDecimal quantity)` is the
single existing conversion contract. It uses exact decimal arithmetic and
validates provider min/max/step constraints without rounding.

The contract can be reused by a future cTrader close adapter, but the current
broker-neutral close request does not provide a partial-close quantity to it.

## Governing Decisions

- ADR-040 explicitly defines the initial position-management capability as
  `FULL EXPOSURE CLOSE` and lists `partial close` as out of scope.
- ADR-042 preserves provider-specific cTrader position addressability and keeps
  provider mutation inside Broker Service.
- Story 0173 provides durable cTrader position identity and authoritative
  position/deal reconciliation, but intentionally contains no broker writes.
- Stories 0174 and 0175 provide the cTrader instrument and safety contracts,
  while explicitly excluding position close.

## Findings

1. The cTrader capability gap is confirmed and can be implemented as a focused
   Broker Service adapter for full close without changing Trading Core.
2. The provider protocol operation is known from ADR-040, but the current
   `CTraderOpenApiClient` must be extended inside the cTrader adapter to expose
   a position-targeted close request.
3. Full-close identity can use the durable opaque `brokerPositionReference`,
   resolved to exactly one cTrader `positionId` in Broker Service.
4. Account/provider/environment checks and the existing cTrader safety flag can
   be reused before any provider request.
5. The existing close result model already represents acknowledged, rejected and
   unknown outcomes and can support reconciliation without optimistic local
   state.
6. Partial close cannot be implemented through the existing broker-neutral
   contract: there is no quantity field, and ADR-040 explicitly excludes the
   operation from the initial capability.

## Material Contradiction

The requested Story scope requires partial close, while the accepted current
architecture defines the existing capability as full-close-only and provides no
quantity-bearing close command.

Implementing partial close safely would require at least a broker-neutral
contract change and corresponding Trading Core command propagation. That is an
architectural scope expansion, not a provider-only adapter implementation.

The implementation must not silently choose between:

- extending `PositionManagementCapability` and its command path to carry a
  quantity; or
- deferring partial close and implementing only the already-approved full-close
  capability.

## Recommended Resolution

Choose one explicitly:

1. **Minimal V1:** implement cTrader full close only, mark partial close
   deferred, and preserve ADR-040. This is the smallest safe slice before
   authenticated DEMO preparation.
2. **Expanded contract:** approve a follow-up architectural change to add
   provider-neutral partial-close quantity semantics, then implement cTrader
   and update ADR-040/affected command contracts before coding.

Until this choice is made, implementation and an implementation plan are
blocked by the repository governance rules. No broker mutation has occurred.

## Validation Already Performed

- Baseline and fast-forward state inspected.
- Broker Service cTrader and Trading Core close tests previously passed.
- Gateway routing integration test passed.
- Market Intelligence governance tests passed.
- Angular tests: 56 files, 573 tests passed.
- Angular production build passed with existing budget warnings.
- `git diff --check` passed before the new Story documentation.

## Repository Impact

The only tracked changes in this analysis stage are:

- `docs/architecture/stories/0176-ctrader-position-management-lifecycle/story.md`
- `docs/architecture/stories/0176-ctrader-position-management-lifecycle/repository-analysis.md`

No source code, runtime configuration, database migration, frontend code or
provider credential was changed.

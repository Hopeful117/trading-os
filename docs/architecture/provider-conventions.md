# Provider Conventions

This document is the quick reference for provider-specific conventions that are
easy to miss when working on Broker Service, Market Data or validation tooling.
It records verified facts only. Provider details remain implementation concerns
inside infrastructure adapters and must not leak into Trading Core or Risk
Domain contracts.

Last repository verification: 2026-11-03.

## Reading Rules

- Treat the provider adapter and its tests as the source of current behavior.
- Treat this document as a navigation aid, not as a replacement for provider
  documentation or source code.
- Add a source path and verification date when recording a new convention.
- Mark an unknown convention as `UNKNOWN`; do not infer it from a symbol name or
  from another provider.
- Never record credentials, tokens, private endpoints or account secrets here.

## Cross-Provider Rules

| Rule | Convention |
| --- | --- |
| Boundary | Provider payloads, authentication, protocol identifiers, mappings and provider errors stay inside Broker Service or Market Data infrastructure. |
| Domain symbols | Use normalized Trading OS instruments in neutral contracts. Translate to provider symbols only inside the adapter. |
| Errors | Translate provider errors into neutral outcomes or bounded diagnostics. Do not expose raw provider payloads. |
| Writes | Do not retry unsafe provider writes automatically. Ambiguous writes remain unknown until reconciliation. |
| Credentials | Credentials are account-scoped, encrypted and only opened for the operation that needs them. |
| Validation | Record provider, environment, account identity, symbol convention, request shape and safety state before a controlled validation. |

## cTrader Open API

### Environment and account identity

| Concern | Convention |
| --- | --- |
| Environment | `demo` must use a `demo.ctraderapi.com` endpoint; non-demo/live must use a `live.ctraderapi.com` endpoint. |
| Account identity | The durable external account identity is the cTrader `ctidTraderAccountId`, represented as the Broker Service external account ID. |
| Binding | Provider data must match the durable binding and configured environment. Provider data must not silently replace the binding. |
| Authorization | OAuth authorization requests `scope=trading` in the current Broker Service flow. |
| Credential state | The active cTrader token is stored as an encrypted Broker Service secret; the connection must be `CONNECTED` and have an active credential reference. |

### Symbols and quantities

| Concern | Convention |
| --- | --- |
| Symbol lookup | Current cTrader symbol resolution is an exact case-insensitive match against the provider `symbolName`. It does not remove or add separators. |
| Observed account behavior | The validated account exposed `EURUSD`; a request using `EUR/USD` returned `CTRADER_UNKNOWN_INSTRUMENT`. |
| Validation input | Use the exact symbol returned by the account's cTrader symbol list, for example `EURUSD` for this account. Do not generalize this to every cTrader account. |
| Volume | ExpectedMargin converts neutral quantity to the provider volume required by cTrader. The response volume must match the requested provider volume. |
| Money precision | ExpectedMargin uses the provider `moneyDigits` value to scale the returned margin amount. |
| Side | Supported ExpectedMargin sides are `BUY` and `SELL`. |

### ExpectedMargin

| Concern | Convention |
| --- | --- |
| Request | `ProtoOAExpectedMarginReq`, payload type `2139`. |
| Response | `ProtoOAExpectedMarginRes`, payload type `2140`. |
| Scope | Read-only provider capability. It must not place, modify or close an order. |
| Safety | cTrader snapshot lease and cooldown apply before opening the provider session. Execution remains disabled unless explicitly enabled by policy. |
| Failure behavior | Missing, stale, unsupported, mismatched or invalid margin facts fail closed. No fallback to leverage, notional, free margin or position margin. |
| Diagnostics | Provider failures may expose bounded stage, payload type, provider error code/reason/description and correlation ID. Credentials and raw payloads must remain redacted. |

### Safety and retry behavior

- Snapshot safety is persisted by external account scope in
  `ctrader_request_safety`.
- A deferred snapshot is HTTP `409` with
  `code=BROKER_SNAPSHOT_DEFERRED`.
- The response includes `reason=COOLDOWN` or `reason=CONCURRENT` and, when
  known, `retryAt`.
- Provisioning, synchronization and ExpectedMargin can share the same account
  snapshot safety scope. Check the structured deferral response before retrying.
- Never update the safety tables manually to bypass a cooldown.

### Source references

- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/authentication/JsonWebSocketCTraderOpenApiClient.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/CTraderMarginCapability.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/authentication/CTraderRequestSafetyGuard.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/authentication/CTraderAuthenticationExceptionHandler.java`
- `docs/architecture/adr/ADR-074-ctrader-authoritative-read-only-synchronization.md`
- `docs/architecture/stories/0148-ctrader-authoritative-read-only-synchronization/story.md`
- `docs/architecture/stories/0193-live-financial-readiness-and-ctrader-required-margin/story.md`

## Kraken

### Authentication and request construction

| Concern | Convention |
| --- | --- |
| Private authentication | Requests use `API-Key` and `API-Sign` generated from the account-scoped credential. |
| Body encoding | Private REST bodies use `application/x-www-form-urlencoded`. |
| Nonce | Nonces are monotonic and thread-safe. |
| Credential lifetime | Temporary credential copies are cleared after signing or operation completion. |
| Error boundary | Kraken HTTP statuses, REST exceptions and raw provider errors remain inside the infrastructure adapter and are translated before leaving it. |

### Pairs and order mapping

| Neutral concept | Kraken convention |
| --- | --- |
| Instrument | Sent as the Kraken `pair` parameter. The exact accepted pair is provider/API-specific and must be obtained from the Kraken market catalogue or account capability. |
| Order side | Neutral `side` maps to Kraken `type`. |
| Order type | Neutral `orderType` maps to Kraken `ordertype`. |
| Quantity | Neutral `quantity` maps to Kraken `volume`. |
| Limit price | Neutral `limitPrice` maps to Kraken `price`. |
| Idempotency | The neutral idempotency key is deterministically transformed into Kraken `cl_ord_id`. This does not authorize automatic retry. |
| Position symbol normalization | Legacy Kraken prefixes `XXBT`, `XETH`, `ZUSD` and `ZEUR` are normalized to `BTC`, `ETH`, `USD` and `EUR` in mapped positions. |

### Source references

- `broker-service/src/main/java/com/hope/trading/broker_service/kraken/credential/KrakenRestCredentialProbe.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/kraken/httpClient/KrakenRestClientImpl.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/kraken/KrakenMapper.java`
- `docs/implementation/ADR-030-implementation.md`
- `docs/investigations/data-integration-inventory-2026-09-24.md`

## Validation Checklist

Before a controlled provider validation:

1. Confirm the provider registration and environment.
2. Confirm the durable local and external account identities.
3. Confirm credential status without exposing credential material.
4. Resolve the exact provider symbol from the provider catalogue or symbol list.
5. Confirm the operation is read-only and execution safety is disabled when required.
6. Check safety state and use `retryAt` instead of retrying during a cooldown.
7. Send one request with a correlation ID and preserve the structured result.
8. Record the provider response, source version, observed timestamp and any
   bounded diagnostic without recording secrets or raw payloads.

## Known Gaps

- Kraken pair conventions are not yet represented by a versioned provider
  contract fixture in the repository.
- cTrader symbol aliases vary by account/provider catalogue; the current exact
  matching behavior is intentionally not generalized here.
- Durable operation-level audit linking every provider payload to an operation
  and correlation ID remains a separate observability improvement.

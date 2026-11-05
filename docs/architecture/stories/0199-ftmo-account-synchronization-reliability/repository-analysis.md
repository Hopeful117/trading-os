# Repository Analysis - Story 0199

## Current State

The repository is on `main` at `22569e1b`, aligned with `origin/main`. The
Story 0198 directory is uncommitted and contains its approved analysis, plan,
matrix and report. Those files are preserved and are not part of this Story's
implementation scope.

DevLog was queried twice with the same idempotency key and timed out both times.
This analysis therefore uses the current repository and the validated Story
0198 evidence as authoritative for current physical state.

## Real Synchronization Flow

```text
POST /api/v2/accounts/{brokerAccountId}/synchronize
  -> Gateway route `accounts`
  -> Trading Core AccountSynchronizationV2Controller
  -> BrokerSynchronizationServiceImpl.synchronize(ownerId, brokerAccountId)
  -> BrokerAccountSnapshotV2Client Feign GET
     /internal/v2/broker-accounts/{brokerAccountId}/snapshot
  -> Broker Service AccountSnapshotV2Controller
  -> GetAccountSnapshotV2Service
  -> CTraderAuthoritativeSnapshotCapability.snapshot
  -> CTraderOpenApiClient.readOnlyObservation
  -> cTrader WebSocket Open API
  -> Trading Core snapshot validation and local account persistence
```

Gateway routes `/api/v1/accounts/**` and `/api/v2/accounts/**` to Trading Core.
The Trading Core controller is synchronous and returns success only after the
transactional synchronization service returns. The service rejects non-LIVE
accounts, identity mismatches, incomplete collections, incomplete account facts,
and non-`COMPLETE`/non-`FRESH` snapshots before persistence.

## Current Evidence

Story 0198 proved a real read-only cTrader snapshot for the existing account:
balance USD 50,000, no positions and no orders. Equity, used margin, free margin
and deals were unavailable, so the snapshot was `INCOMPLETE`. No value may be
derived from balance or empty positions.

The earlier E2E synchronization attempt is not sufficient to identify the
blocking stage. It was issued with a browser `fetch` rather than the Angular
HttpClient path, so it did not use the application's authentication interceptor.
The browser request also had no bounded application response captured.

## Reliability Risks Requiring Controlled Reproduction

`JsonWebSocketCTraderOpenApiClient.readOnlyObservation` performs application
authentication and multiple account-scoped requests sequentially. Each response
wait in `Session.request` is bounded to 15 seconds, but the aggregate path may
wait across all requests for roughly two minutes before returning an error.
`Session` also uses `join()` for WebSocket creation, sends and close operations;
these operations have no explicit per-operation timeout in the current code.

These are code-level hypotheses, not yet the demonstrated root cause. Feign
timeout configuration for this client was not found in the Trading Core resource
configuration, so the effective downstream client timeout must be verified in
the running service or tests. The authenticated route must be reproduced before
changing timeout policy.

## Safety and Financial Boundaries

- The cTrader safety execution flag remains disabled.
- The target account must be resolved through the existing application mapping.
- Provider identity and environment must match before real calls.
- Incomplete equity and margin remain unavailable and inadmissible.
- Synchronization may persist only facts allowed by the existing contract.
- No order API or `ProtoOANewOrderReq` is in scope.

## Required Next Stage

The Repository Analysis requires human approval before an Implementation Plan or
code change. After approval, the plan should first define a bounded authenticated
reproduction and instrumentation points, then select the smallest correction
supported by the trace.

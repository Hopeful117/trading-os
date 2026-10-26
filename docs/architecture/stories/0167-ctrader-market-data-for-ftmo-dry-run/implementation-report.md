# Story 0167 Implementation Report

## Status

`IMPLEMENTATION_PARTIAL` for the deterministic, fixture-tested, and
authenticated catalogue-runtime-validated scope.

`LIVE_CTRADER_RUNTIME_VALIDATED` is claimed for OAuth account binding, token
storage, authenticated account/catalogue access, and accepted read-only
subscriptions. Live quote/trendbar delivery remains unvalidated.

## Implemented

- Added the stateless `secret-crypto` module for AES-GCM/key-provider primitives.
- Added the stateless `ctrader-openapi-technical` module for cTrader protocol
  constants, correlation, and price decoding.
- Refactored Broker Service crypto and cTrader payload constants to use the
  shared primitives without changing its public behavior.
- Added a Market Data-owned encrypted cTrader token store and migration.
- Added bounded deployment-managed bootstrap and serialized OAuth token refresh
  with atomic access/refresh token rotation.
- Added a Market Data-owned OAuth initiation and callback at
  `/api/v1/ctrader/authorization` and
  `/api/v1/ctrader/authorization/callback`. The flow requests the read-only
  `accounts` scope, uses hashed one-time state with expiry, exchanges the code
  server-side, validates the configured environment/account context, and stores
  the resulting tokens through the existing encrypted token store.
- Added service-JWT authentication for the protected OAuth initiation endpoint
  and aligned Docker Compose with the cTrader WebSocket environment variable
  fallback.
- Made bootstrap restart-safe for a matching usable token. Environment/account
  mismatches fail closed; confirmed invalid refreshes invalidate, while transient
  refresh failures preserve the active token.
- Added the Market Data-owned cTrader WebSocket session and read-only
  application/account authentication.
- Account environment validation now uses the cTrader account-list response,
  where `isLive` is provided, before authorizing the configured account.
- Added cTrader symbol metadata, historical trendbars, bounded pagination,
  cutoff filtering, deterministic ordering, duplicate/conflict detection, and
  official relative-price decoding from `deltaOpen`, `deltaHigh`, `low`, and
  `deltaClose` wire fields.
- Added cTrader provider routing while preserving Kraken behavior.
- Added explicit runtime mappings for the discovered cTrader symbols:
  `EURUSD` -> `EUR/USD`, `GBPUSD` -> `GBP/USD`, `XAUUSD` -> `XAU/USD`, and
  `US30.cash` -> `US30/USD`. `US30.cash` is classified explicitly as `CFD`.
- Updated market-catalogue synchronization to aggregate all registered providers,
  allowing Kraken and cTrader catalogues to coexist when cTrader is enabled.
- Added live bid/ask and live trendbar handling with provenance, received time,
  forming/closed semantics, duplicate suppression, reconnect, and resubscribe.
- Matching live events are published to every canonical active subscription. As
  the current adapter has no supported provider unsubscribe request, removing
  demand closes and rebuilds the bounded session, replaying remaining demand.
- cTrader catalogue entries remain unavailable/non-tradable until authoritative
  provider facts prove immediate execution availability. Unsupported max/step
  volume facts are not guessed into the smaller canonical constraints model.
  Observed historical bars remain separate from normalized closed-bar results.
- Added focused cTrader, token, pagination, crypto, and compatibility tests.

## Validation

- `cd secret-crypto && mvn verify`: passed, 2 tests.
- `cd ctrader-openapi-technical && mvn verify`: passed, 2 tests.
- Focused cTrader Market Data tests: passed, 24 tests.
- Full Market Data tests: passed, 336 tests.
- Broker Service tests: passed, 290 tests.
- Market Data OAuth token exchange tests: passed, 3 tests.
- `secret-crypto` and `ctrader-openapi-technical` `mvn verify`: passed.
- `git diff --check`: passed.
- `cd market-data && mvn -q clean test`: passed after the final connection-generation fix.
- `cd broker-service && mvn -B clean verify`: passed with 290 tests and the
  repository's 80% JaCoCo line threshold.
- `docker compose build market-data`: passed.
- `docker compose up -d --force-recreate market-data`: passed.
- cTrader OAuth authorization completed for LIVE account `49289512`; the
  callback exchanged and persisted an active encrypted token.
- Authenticated LIVE runtime catalogue synchronization: passed for four cTrader markets;
  `EUR/USD`, `GBP/USD`, and `XAU/USD` are exposed as `SPOT`, while `US30/USD`
  is exposed as `CFD`.
- Startup synchronization log: `Market synchronization completed`.
- Authenticated read-only TICKER subscription for `EUR/USD`: accepted by cTrader.
- Authenticated read-only OHLC subscription for `EUR/USD`: accepted by the
  Market Data boundary with HTTP `204`; no order endpoint was called.
- Dedicated 65-second runtime watch after the OHLC subscription: no cTrader
  `SPOT_EVENT` or Trendbar event was observed.

## Not Performed

- Authenticated cTrader validation used the bound account `49289512`,
  configured as `LIVE`, matching the provider-reported environment. No order
  endpoint was called.
- Live quote events were not observed end-to-end through the public market
  state; the provider accepted the TICKER subscription.
- Live trendbar streaming remains unvalidated: the subscription was accepted,
  but no `SPOT_EVENT` or Trendbar arrived during the bounded runtime watch.
- The OAuth callback is intentionally limited to the read-only `accounts` scope
  and configured source context; it does not expose tokens in its response.
- OAuth account discovery and provider-returned scope introspection are not
  claimed as implemented. The callback validates the configured source account
  context after code exchange; environment/account checks at token use remain
  fail-closed.

## Residual Risks

- Deployment must provide the cTrader client secret, token-store master key,
  and bootstrap token material through an approved secret mechanism.
- Runtime validation must still verify live spot/trendbar payloads, refresh
  behavior, stale state, and reconnect/resubscribe behavior.
- cTrader session/tradability facts and provider-silence recovery still require
  authenticated runtime validation. No weekday or clock heuristic is used.
- Canonical Market State has no distinct `STALE` or `UNSUPPORTED` persistence
  state; silence uses the existing provider-unavailable/closed path while
  observation freshness remains on existing Market Data contracts.

## Integration Boundary

No commit, push, merge, or pull request was performed. Human review and the
approved credentialed cTrader validation procedure remain required. The
repository remains in a dirty working tree with unrelated and Story 0167
changes visible for human review.

========================================
TRADING OS - CTRADER MARKET DATA GATE
========================================

IMPLEMENTATION_COMPLETE:
NO

CANONICAL_INSTRUMENT_MAPPING:
PASS

HISTORICAL_OHLC:
PASS

CLOSED_BAR_SEMANTICS:
PASS

LIVE_BID_ASK:
PASS

FRESHNESS:
PASS

SESSION_TRADABILITY:
PARTIAL

PROVIDER_PROVENANCE:
PASS

MARKET_INTELLIGENCE_COMPATIBILITY:
PASS

KRAKEN_REGRESSION:
PASS

CTRADER_DOM:
OUT_OF_SCOPE

REFERENCE_MARKET_DATA:
LATER

FEATURE_PROJECTION_V1:
UNCHANGED

S1_SEMANTICS:
UNCHANGED

LIVE_CTRADER_RUNTIME_VALIDATED:
OAUTH_ACCOUNT_BINDING_AND_READ_ONLY_SUBSCRIPTIONS

EXTERNAL_RUNTIME_BLOCKER:
NO_TRENDBAR_EVENT_OBSERVED_AFTER_ACCEPTED_SUBSCRIPTION

REALISTIC_FTMO_DRY_RUN_READY:
YES_WITH_LIMITATIONS

NEXT_HUMAN_ACTION:
Investigate provider silence or event publication before claiming live Trendbar
runtime validation; then perform human review of the complete diff.

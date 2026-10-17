# Story 0122 - Startup Restoration Runbook

## Configuration

The Docker Compose deployment enables the bounded V1 desired subscription by
default. Override these non-secret variables when changing the desired scope:

```text
ORDER_BOOK_ACQUISITION_STARTUP_ENABLED=true
ORDER_BOOK_ACQUISITION_DESIRED_SUBSCRIPTIONS_0_PROVIDER=KRAKEN
ORDER_BOOK_ACQUISITION_DESIRED_SUBSCRIPTIONS_0_SYMBOL=BTC/USD
ORDER_BOOK_ACQUISITION_DESIRED_SUBSCRIPTIONS_0_PRODUCT_TYPE=LINEAR_PERPETUAL
ORDER_BOOK_ACQUISITION_DESIRED_SUBSCRIPTIONS_0_PROVIDER_SYMBOL=PF_XBTUSD
ORDER_BOOK_ACQUISITION_DESIRED_SUBSCRIPTIONS_0_DEPTH=25
```

The desired configuration is semantic and resolves the current market catalogue
entry at startup. It is not a persisted database subscription and it never stores
WebSocket or Reactor runtime objects.

## Startup Behavior

After `market-data` reaches `ApplicationReadyEvent`, it validates each configured
entry and invokes the existing Order Book subscription service. A successful
startup emits:

```text
ORDER_BOOK_DESIRED_SUBSCRIPTION_RESTORE_STARTED
ORDER_BOOK_DESIRED_SUBSCRIPTION_RESTORED
```

The provider then emits the existing connection, subscription, snapshot, and
sequence diagnostics. A fresh provider snapshot creates a new sequence ID.

## Failure Behavior

Invalid configuration, missing market catalogue entries, unavailable provider
connectivity, or subscription failures emit:

```text
ORDER_BOOK_DESIRED_SUBSCRIPTION_RESTORE_FAILED
```

The application remains running in degraded acquisition state. No fake active
state, sequence, or evidence is created. This Story does not retry or reconnect
automatically.

## Stop and Restart

Normal shutdown closes the provider runtime connection and leaves desired
configuration unchanged. On the next startup, the configured subscription is
restored and a fresh sequence is created. The previous sequence remains
invalidated and is never resumed.

## Manual REST Semantics

The existing REST subscription endpoint remains an immediate runtime action.
Equivalent startup and REST subscriptions share the provider stream through the
existing reference-counted service. A REST unsubscribe releases only its caller
reference while the configured startup reference remains active. Disable the
configured startup intent and restart the service to stop the configured stream.

## Runtime Verification

Verify the following without calling the manual subscription POST:

1. `ORDER_BOOK_DESIRED_SUBSCRIPTION_RESTORED` is present.
2. Futures `book` subscription is requested for `PF_XBTUSD` depth `25`.
3. `ORDER_BOOK_SEQUENCE_STARTED` shows a new sequence ID.
4. The sequence status is `ACTIVE` and evidence count grows across two bounded
   observations.
5. After a controlled restart, the previous sequence is `INVALIDATED` and a
   different sequence ID becomes active.

Checksum and unexpected-stream reconnect behavior remain separate Stories.

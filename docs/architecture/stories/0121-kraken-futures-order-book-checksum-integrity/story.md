# Story 0121 - Kraken Futures Order Book Checksum Integrity

## Metadata

**ID:** `0121`

**Title:** Kraken Futures Order Book Checksum Integrity

**Status:** Blocked - checksum specification not established

**Related Stories:** `0112A`, `0119`, `0120`

**Related ADRs:** `ADR-063`, `ADR-064`

## Goal

Validate Kraken Futures Order Book integrity at the provider boundary without
weakening sequence continuity, replay, or historical evidence semantics.

## Scope

- Establish the checksum protocol for the exact Futures WebSocket v1 `book`
  feed consumed by Trading OS.
- Preserve provider checksum data if the feed supplies it.
- Validate reconstructed Futures book state only after the protocol is proven.
- Invalidate the active sequence on a confirmed checksum mismatch.
- Preserve existing evidence, replay, lifecycle diagnostics, and fee behavior.

## Blocker

The current provider feed is `wss://futures.kraken.com/ws/v1` with
`product_ids=["PF_XBTUSD"]`. The official Futures `book` reference documents
`book_snapshot`, `book`, `seq`, `timestamp`, bids, and asks, but does not
document a checksum field or a Futures checksum algorithm.

The official CRC32 checksum guide retrieved during analysis applies to Kraken
WebSocket v2 Spot (`wss://ws.kraken.com/v2`), not to the Futures WebSocket v1
feed consumed here. Implementing that algorithm for Futures would be an
unsupported Spot-to-Futures assumption.

Implementation must not proceed until Kraken documents the applicable Futures
checksum semantics or a verified Futures payload/example establishes them.

## Out of Scope

- Spot checksum behavior in the Futures adapter.
- Automatic reconnect, retry, resubscription, or fresh sequence recovery.
- Historical evidence rewriting or retroactive checksum trust.
- Changes to Market Intelligence, Trading Core, Broker Service, fees, funding,
  FeatureSnapshots, outcomes, or economic results.
- Database migrations.

## Acceptance Criteria

- [ ] Exact Kraken Futures checksum specification is established from an
      authoritative source or verified Futures payload.
- [ ] Futures checksum parsing and canonicalization are provider-correct.
- [ ] Existing sequence continuity validation remains active.
- [ ] Confirmed checksum mismatch invalidates the active sequence and prevents
      further trusted evidence under that sequence.
- [ ] Existing historical evidence is preserved without retroactive trust
      upgrade.
- [ ] Strict replay rejects checksum-invalidated sequences.
- [ ] No reconnect or retry behavior is introduced.
- [ ] Focused tests and Market Data validation pass.

## Relevant Modules

- `market-data`

## Current Decision

`CHECKSUM_SPECIFICATION_NOT_ESTABLISHED`. No implementation plan is approved
until the provider protocol blocker is resolved.

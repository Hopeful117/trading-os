# Story 0121 - Implementation Report

## Final Status

`CHECKSUM_SPECIFICATION_NOT_ESTABLISHED`

## Work Performed

- Verified the post-Story-0120 baseline and current worktree.
- Reviewed ADR-063, ADR-064, Stories 0119 and 0120, and the current Order Book
  provider/capture/replay path.
- Revalidated the current checksum trust behavior.
- Inspected official Kraken documentation for the exact Futures WebSocket v1
  `book` feed and compared it with the available Spot WebSocket v2 CRC32 guide.
- Confirmed that no production code, migration, runtime configuration, or
  historical evidence was changed.

## Why Implementation Stopped

The exact checksum protocol for the consumed Kraken Futures WebSocket v1 feed
could not be established. The available official Futures reference documents
sequence numbers and book mutations but no checksum field or checksum
algorithm. The available official CRC32 guide is for Spot WebSocket v2 and
cannot safely be applied to Futures.

Implementing the Spot algorithm would violate the Story's Futures-only
guardrail and could falsely mark reconstructed Futures books as valid.

## Validation

- Repository inspection: completed.
- Official documentation inspection: completed.
- Runtime log inspection: completed; no checksum-bearing Futures payload
  observed.
- Production tests: not run because implementation was intentionally blocked.
- `git diff --check`: passed before the documentation-only artifacts were added;
  it must be rerun after human review.

## No Changes To

- `market-data` production code;
- Order Book persistence or replay;
- historical evidence;
- reconnect or retry behavior;
- Broker Service and Story 0120 fee capture;
- Trading Core, Market Intelligence, funding, or economic research.

## Required Next Step

Obtain authoritative Kraken Futures checksum semantics for
`wss://futures.kraken.com/ws/v1`, or provide a verified Futures payload/example
that establishes the complete canonicalization and checksum contract. Then
resume implementation from the existing provider boundary.

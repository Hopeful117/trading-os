# Story 0121 - Repository Analysis

## Status

Completed for provider-specification review. Implementation blocked.

## Repository State

- Branch: `main`.
- HEAD: `8ba372c3f42dc58121cc06f0673a62ede643051e`.
- `origin/main`: `8ba372c3f42dc58121cc06f0673a62ede643051e`.
- `git fetch origin`: failed because the configured SSH key was rejected by
  GitHub. Local refs already contained the Story 0120 merge.
- Worktree: clean before analysis and unchanged except for these new Story
  analysis artifacts.
- Story 0120 and Story 0119 are present in the baseline.

## Architecture Reviewed

- ADR-063 remains the governing Order Book evidence, trust, sequence, and replay
  architecture.
- ADR-064 remains relevant only as a boundary guardrail; no economic or fee
  behavior is in scope.
- The current flow is:

```text
KrakenDataStreamProvider
    -> KrakenOrderBookMapper
    -> OrderBookEvidenceCaptureService
    -> OrderBookEvidenceRepository
    -> OrderBookReplayService
```

## Current Trust Behavior

`OrderBookEvidenceCaptureService.validChecksum` currently returns `true` only
when `delta.checksum()` is null. A non-null checksum is rejected because the
algorithm is not implemented. A null checksum is accepted under sequence
continuity validation only.

Sequence continuity is checked independently with provider sequence values. A
gap invalidates the active sequence. Persistence failures and stream cleanup
also invalidate the sequence. No reconnect behavior exists and none is
authorized by this Story.

The persisted `trusted` value is derived from the evidence repository trust
path; it does not distinguish historical sequence-only trust from future
checksum-validated trust.

## Provider Boundary

The Futures parser consumes `feed=book` and `feed=book_snapshot` messages from
`wss://futures.kraken.com/ws/v1`. The current DTO preserves:

- product symbol;
- bids and asks;
- timestamp;
- provider sequence;
- an optional checksum field.

The runtime adapter currently reads `checksum` if present, but the official
Futures v1 reference inspected during this analysis does not define that field
or a checksum protocol for this feed.

## Official Sources Inspected

- Kraken Developers documentation index: `https://docs.kraken.com/llms.txt`.
- Kraken Futures WebSocket book reference:
  `https://docs.kraken.com/exchange/api-reference/futures-websocket/book`.
- Kraken WebSocket v2 book checksum guide:
  `https://docs.kraken.com/exchange/guides/websockets/book-checksum-v2`.
- Kraken WebSocket reconnection guide:
  `https://docs.kraken.com/exchange/guides/websockets/reconnection`.

The Futures book reference documents `seq`, snapshots, and deltas but no
checksum field. The CRC32 guide explicitly describes WebSocket v2 Spot book
messages and is not sufficient evidence for the Futures v1 feed.

## Runtime Evidence

- No checksum-bearing Futures payload was present in the available recent
  `market-data` container logs.
- The existing historical Futures sequence remains historical evidence only;
  this analysis does not rewrite, delete, or upgrade it.

## Decision

Stop before production implementation with:

```text
CHECKSUM_SPECIFICATION_NOT_ESTABLISHED
```

The next required input is an authoritative Kraken Futures checksum
specification or a verified Futures payload/example that establishes the exact
field, canonicalization, depth, ordering, and CRC representation.

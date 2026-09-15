# Implementation Plan - Story 0044

## Status

`IMPLEMENTATION PLAN - EXECUTED FOR VALIDATED STORY SLICE`

## Sequence

1. Establish and test user ownership checks in Trading Core.
2. Remove unsafe User entity exposure or replace it with a safe contract.
3. Make production-like JWT configuration fail fast while preserving test and
   development configuration.
4. Add short-lived, receiver-scoped service JWT creation and validation.
5. Propagate service credentials through the current Feign clients without
   trusting copied browser headers.
6. Add signed delegated actor handling for user-driven Core-to-Market-Intelligence
   operations and reject mismatches.
7. Protect Broker Service internal operations with caller and audience checks.
8. Add local Market Intelligence user and service security boundaries.
9. Protect Market Data internal snapshot and valuation operations while preserving
   bounded public reads.
10. Align and prove the Gateway-to-Execution downstream mapping.
11. Apply only matrix-backed legacy endpoint restrictions and WebSocket policy.
12. Run service, contract, ownership, and real-socket runtime validation.

## Executed Slice

Steps 1 through 7 were implemented in the current worktree. Step 12 includes
the real-socket acceptance harness for Core, Broker, and Market Intelligence.

The remaining Gateway, Market Data, and WebSocket items are explicitly outside
this validated closure slice and remain deferred rather than being silently
claimed as implemented.

## Constraints

- No OAuth server, mTLS, service mesh, or new IAM platform.
- No change to LIVE Broker or PAPER Trading Core position authority.
- No change to deterministic risk semantics or human execution authority.
- No automatic deletion of ambiguous endpoints.
- No commit or repository history rewrite by the implementation agent.

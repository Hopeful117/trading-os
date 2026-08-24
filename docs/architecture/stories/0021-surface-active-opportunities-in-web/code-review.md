# Code Review — Story 0021

Date: 2026-08-24 · Reviewer: implementation agent (human review still required)

## Scope compliance

* Frontend-only: confirmed, no backend/Gateway/MI file touched.
* No scan-trigger, trade-plan, risk or AI UI added — detail CTA surface is
  purely informational (back link only).
* Terminology stays deterministic ("Market Intelligence opportunities",
  "strategy match", no "AI" claims).

## Architecture conformance

* Standalone components with `imports`, matching `features/markets` patterns.
* Typed service in `core/services` using `environment.gatewayUrl` like
  `MarketService`.
* Fully reactive: view models are single Observable unions rendered via
  `async`; no manual subscribe in components; no `ChangeDetectorRef`.
* Expiration/status never recomputed client-side; backend is the only source
  of truth (ADR-034/038 respected; UNVALIDATED status not displayed as value).

## Points checked

* `shareReplay({bufferSize:1, refCount:true})` matches existing conventions;
  late subscribers get the last state (covered by a dedicated test).
* HTTP 404 mapped to an explicit `notFound` state via `HttpErrorResponse`
  check before generic error mapping.
* Nullable contract fields (`validUntil`, `strategyMatchId`) rendered
  truthfully (`—`, "Not recorded", "None recorded") — no fabricated data.
* Routes guarded with the existing `authGuard`; param name
  `:opportunityId` follows the `:marketId` precedent.
* Sidebar change limited to one anchor.

## Findings

1. **(Minor, accepted)** Bundle budget warning grows by ~14 kB because all
   current routes are eager; introducing lazy loading for one route would be
   inconsistent. Deferred to a future performance story (already listed as
   technical debt).
2. **(Minor, accepted)** List refresh re-fetches everything without pagination
   — consistent with the ACTIVE endpoint contract used; pagination UI remains
   out of scope.
3. **(Observation)** Existing pages (markets/dashboard) do not implement the
   same explicit loading/error states; this story sets the pattern that could
   be generalized later. Not changed here to keep scope tight.

## Verdict

Implementation conforms to Story scope and repository conventions. Ready for
human review and merge decision.

# Implementation Plan - Story 0158

## Plan Boundary

Implement the smallest frontend-only Market Navigator slice. Reuse the existing
`ScanPanel` and `ActiveScanService`; do not change backend contracts, scan
orchestration, Opportunity ownership, or live market-stream ownership.

## 1. Adapt the Existing Scan Presentation

Modify `ScanPanel` to support both existing `/opportunities` usage and embedded
Cockpit usage without duplicating scanner logic.

- Add an optional account-context input for the parent-selected account.
- Add an optional compact/embedded presentation mode that hides the nested
  account selector when the Cockpit already owns account selection.
- Reset/cancel the current scan view when the supplied account changes so results
  cannot cross account boundaries.
- Preserve the existing standalone Opportunities route behavior and account
  selection when the new input is not supplied.
- Keep `ActiveScanService` calls, scope preflight, idempotency, polling, terminal
  detection, and error mapping unchanged.

## 2. Expose Navigator Selection Intents

Extend the existing presentation output seam with selection intents rather than
calling coordinator services from the panel.

- Emit an eligible market result selection with its market identifier.
- Emit Opportunity selection with the existing Opportunity contract and its
  canonical market identity available to the parent.
- Add an accessible focus/inspect action to per-market results.
- Keep Opportunity chips available for direct Opportunity selection.
- Preserve the existing `opportunitySelected` behavior required by the
  Opportunities route, adapting only the parent wiring and tests if the output
  shape must carry the market identity.

## 3. Compose the Navigator in DecisionWorkspace

Use `DecisionWorkspace` as the account/context coordinator.

- Import and render one `ScanPanel` instance inside the account-scoped
  workstation layout as the left-side Market Navigator region.
- Pass the selected account into the embedded panel and do not expose a second
  account authority there.
- Route market selection through the existing `selectMarket` guard and query
  parameter update.
- Route Opportunity selection through the existing `marketId` and
  `opportunityId` query context, allowing the existing route subscription to
  load and validate the Opportunity evidence.
- Clear scan/navigation selection when the account changes, while preserving the
  Story 0157 Account/Risk strip and dashboard stream.
- Do not add any market stream or duplicate investigation request to the panel.

## 4. Result Semantics and UX

Use the existing `ActiveScanResponse` fields and outcomes as the only semantic
authority.

- Keep scan lifecycle labels truthful for dispatch, running, partial,
  completed, failed, and no-work states.
- Keep per-market Opportunity found, completed-no-opportunity, excluded,
  processing, failed, cancelled, and expired states distinct.
- Preserve the existing successful zero-Opportunity state and make the NO TRADE
  information state visible in the embedded presentation.
- Show existing diagnostics and exclusion reasons without inventing explanations
  or ranking.
- Keep the Navigator compact enough for the existing workstation layout and
  preserve responsive behavior.

## 5. Tests

Add or update focused Angular tests.

### ScanPanel

- embedded account input hides the nested selector;
- account changes cancel/replace the previous scan view;
- lifecycle and per-market status semantics remain intact;
- market selection emits the canonical market identity;
- Opportunity selection preserves market identity;
- zero Opportunity, excluded/unavailable, failed, and partial states remain
  distinct.

### DecisionWorkspace

- renders one embedded Navigator only after account context is selected;
- passes the selected account to the Navigator;
- routes result selection through the existing active-market context;
- routes Opportunity selection with `marketId` and `opportunityId`;
- clears prior Navigator context on account changes;
- preserves existing dashboard and market-stream behavior.

### Regression

- update Opportunities tests only for the compatible output seam;
- run the existing `ActiveScanService` tests unchanged unless a contract-level
  regression is revealed.

## 6. Validation

Run, in order:

1. Focused `ScanPanel`, `DecisionWorkspace`, Opportunities, and service tests.
2. `npm run test:ci`.
3. `npm run build`.
4. Prettier check and `git diff --check`.
5. Authenticated runtime validation at approximately 1920px and 390px:
   account context visible, scan action account-scoped, terminal results
   truthful, candidate selection updates URL and investigation, no duplicate
   live streams, and no horizontal overflow.

## 7. Documentation and Reporting

Update `implementation-report.md` with:

- exact changed files and preserved boundaries;
- scanner reuse and status mapping evidence;
- persistence assessment outcome;
- tests, build, formatting, and runtime evidence;
- explicit documentation outcome;
- explicit vault outcome: no vault action, because no relevant note was found
  and the implementation does not create curated knowledge.

No ADR, backend documentation rewrite, or vault write is planned.

## Expected File Scope

Expected implementation files are limited to:

- `trading-os-web/src/app/features/opportunities/scan-panel/scan-panel.ts`
- `trading-os-web/src/app/features/opportunities/scan-panel/scan-panel.html`
- `trading-os-web/src/app/features/opportunities/scan-panel/scan-panel.scss`
  only if compact embedded presentation requires it
- `trading-os-web/src/app/features/opportunities/scan-panel/scan-panel.spec.ts`
- `trading-os-web/src/app/features/opportunities/opportunities.ts` and its spec
  only if output compatibility requires it
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.ts`
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.html`
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.scss`
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.spec.ts`
- this Story's `implementation-report.md`

Any backend or architectural boundary change discovered during implementation is
outside this plan and must stop for human guidance.

## Approval State

This plan requires explicit human approval before implementation begins.

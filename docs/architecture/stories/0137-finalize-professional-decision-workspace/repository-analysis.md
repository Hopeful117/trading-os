# Repository Analysis

## Current State

Story 0136 is merged on `main` at `12a708a6`. The current workspace already
uses a selector rail and active-market CSS Grid composition. `DecisionWorkspace`
owns account/context/market resolution, opportunity handling, stream lifecycle,
and navigation. Child panels remain input-driven presentation components.

The working tree contained four pre-existing untracked architecture reports;
they were not modified.

## Findings

1. At 1024px, grid children with intrinsic content widths exceeded the active
   workspace even though the parent grid itself fit the viewport.
2. Detailed Trend Context evidence was rendered expanded and its disclosure
   control had no parent state binding, so it could not be meaningfully closed
   and reopened.
3. Runtime walkthroughs showed live quote and Order Flow data after the existing
   local WebSocket-origin correction. The local backend also returned one known
   `501 Not Implemented` response for a market subscription endpoint; this did
   not prevent live WebSocket data and is environment evidence, not a new
   frontend contract change.
4. The semantic heading order is structurally valid for the active workspace:
   one H1, section H2 headings, and panel H3 headings. Native browser focus
   indicators remain available for the workspace controls.

## Decision

Apply bounded frontend-only corrections:

- constrain direct active-workspace grid children with `min-width: 0`;
- make Trend Context evidence controlled and collapsed by default;
- expose disclosure content through `aria-controls` when an id is supplied;
- reset disclosure state when account or market context changes;
- add focused regression coverage for collapsed/expanded Trend Context evidence.

No backend, Risk, execution, persistence, or stream ownership change is
required.

## DevLog

DevLog applicability was checked previously. The provider returned HTTP `429`
with `credit_balance_exhausted`, so no additional historical context was
available. Current repository state and accepted architecture documents were
used instead.

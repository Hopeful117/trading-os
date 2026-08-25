# Story 0026 — Engineering Report

**Date**: 2026-08-25
**Branch**: `feature/story-0026-dashboard-operational-overview`
**Status**: DONE — Ready for human review

---

## What was done

Transformed the Trading OS Dashboard from a partially-deceptive aggregation page into a truthful operational overview.

### Key changes

1. **Fixed error semantics**: Dashboard now shows "Impossible de charger les comptes" when the account service fails, instead of displaying raw error in body
2. **Equity source label**: Shows "calculé à partir du compte broker" when equity is calculated, not raw from broker
3. **Risk UNAVAILABLE explanation**: When no risk rules are configured, shows "Aucune règle de risque configurée pour ce compte." instead of blank
4. **MI panel**: Real data via `OpportunityService.findActive()`, shows active opportunity count, links to /opportunities
5. **Performance panel**: Explicit "not available yet" message instead of fabricated data
6. **Error isolation**: MI failure does not break account dashboard, and vice versa

### What was NOT changed

- No backend changes (Trading Core, MI, Gateway, etc.)
- No new Angular services
- No equity history (deferred)
- No risk configuration UI (deferred)
- No broker sync fixes (out of scope)

---

## Quality gates

| Gate | Status |
|------|--------|
| `ng test` | 227/227 pass |
| `ng build` | Success (budget warning pre-existing) |
| `git diff --check` | Clean |
| Docker rebuild | Done |
| Runtime validation | Dashboard API + MI API confirmed |

---

## Runtime validation

- Login: `scanprobe`/`Str0ngPass!123`
- Dashboard API: equity=0, risk=UNAVAILABLE, equitySource=CALCULATED, no positions
- MI API: 0 active opportunities
- Angular frontend: new build deployed, MI panel present in JS bundle

---

## Known limitations

- MI panel shows active opportunities count only — no scan details (requires new scan listing endpoint in MI backend, out of scope)
- Performance panel is static "not available yet" — equity history requires new backend endpoint
- `equitySourceLabel()` uses French labels — internationalization deferred

---

## Recommendation

APPROVE for human review and merge.

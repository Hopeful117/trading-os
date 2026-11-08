# Research Intelligence Gap Matrix

**Date:** 2026-11-08
**Evidence basis:** current repository plus existing architecture/readiness reports

| Gap | Current evidence | Severity | Consequence | Validation needed |
|---|---|---:|---|---|
| Agent-to-research authorization | `research:run` grant exists; controller requires `MiUserPrincipal` | Critical | MCP research cannot be proven for agents | Authenticated Gateway-to-MI integration tests |
| Historical population | Controlled export/validator exists; runtime evidence was sparse | Critical | No credible model or profitability claims | Current populated export and readiness report |
| Cost-aware replay | Execution contracts exist; complete calibrated replay not evidenced | Critical | Backtests can overstate economic value | PAPER/replay scenarios with fees, spread, slippage, funding and latency |
| Production model lifecycle | Offline Ridge/benchmark only | High | No safe train/register/deploy path | Model metadata, registry and rollback contract |
| Calibration and uncertainty | Reports explicitly avoid calibrated probability | High | Predictions cannot be treated as probabilities | Calibration protocol and out-of-sample evaluation |
| Runtime adaptive validation | Offline tests exist; complete live adaptive cycle not proven | High | AI value and failure behavior remain uncertain | Controlled authenticated campaign |
| Research UI execution/report workflow | UI exposes readiness/integrity service only | Medium | Human users lack full research lifecycle visibility | UI/API contract test and user workflow validation |
| Capability catalog governance | Registry and versions exist; adaptive allowlist is local to service | Medium | Capability availability can drift across entry points | Central catalog/contract checks |
| Scanner/research linkage | Active scans and opportunities are implemented separately from research reports | Medium | Research findings are not yet a complete scanner intelligence loop | Trace scan → observation → opportunity → research/report scenario |
| News/macro evidence | Context type and internal controller exist; complete provider synchronization is incomplete | Medium | Macro conclusions may be unavailable or partial | Provider-backed freshness/provenance test |
| Live order-book/funding evidence | Capability contracts exist; historical readiness report found runtime absence | High | Liquidity/carry conclusions are incomplete | Current deployment and persistence verification |

## Priority Interpretation

The critical path is authorization plus evidence. Building more AI capabilities before those gaps are closed would increase surface area without improving trustworthiness. The first roadmap increments should make an agent-authenticated research run observable and make historical evidence sufficient for repeatable evaluation.

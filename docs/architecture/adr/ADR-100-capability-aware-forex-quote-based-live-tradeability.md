# ADR-100 - Capability-Aware Forex Quote-Based LIVE Tradeability

## Status

ACCEPTED - human architecture approval recorded 2026-11-13

## Date

2026-11-13

## Decision Makers

- Trading OS Architecture
- Human Engineer
- Project Owner

## Decision Type

Market Intelligence evidence policy and capability boundary

## Related ADRs

- ADR-065 - Deterministic evidence chronology and replay
- ADR-069 - Evidence provenance and cutoff integrity
- ADR-076 - Market Thesis authority and evidence composition
- ADR-079 - Deterministic liquidity evidence trust and policy boundary
- ADR-086 - Evidence-based Risk evaluation

## Related Story

- Story 0235 - Capability-Aware Forex Quote-Based LIVE Tradeability

## Supersession Scope

This ADR narrowly supersedes the `LIVE_CURRENT` prohibition in ADR-079 for the
quote-based Forex profile defined here. ADR-079 remains authoritative for
Level II evidence, liquidity trust, reconstruction, replay, calibration and
Candidate requirements. It is not rewritten and is not superseded generally.

## 1. Context

Tradeability currently uses `EvidenceAcquisitionPolicy` to distinguish current
and historically observed evidence, but the capability rejects all
`LIVE_CURRENT` analysis. That behavior is safe for Level II-dependent
strategies, but it also blocks basic Forex analysis that requires only a fresh
valid bid/ask quote.

Forex CFD quotes and provider-visible Level II depth are different evidence
classes. A current quote can support quote validity and spread observation. It
does not prove visible-depth sufficiency, executable quantity, market impact,
global liquidity or a calibrated liquidity policy.

## 2. Decision

Tradeability evidence requirements are capability-aware:

```text
temporal acquisition policy != strategy evidence requirements
```

`LIVE_CURRENT` is permitted only when all of the following hold:

1. The active capability profile explicitly requires only basic quote-based
   evidence.
2. The canonical instrument is an admitted Forex CFD profile.
3. A current market snapshot contains a valid two-sided quote.
4. The quote is fresh and eligible for the evaluation cutoff under its source
   contract.
5. Market identity, provider/source identity and canonical scope are coherent.
6. No active strategy predicate requires Level II, order flow, depth,
   imbalance, impact, replay or calibrated liquidity suitability.

The capability may produce deterministic quote and spread observations. It must
make the unassessed liquidity dimension explicit and must not classify liquidity
as suitable or unsuitable without an approved liquidity policy.

## 3. Evidence Profiles

The implementation must represent the distinction through an existing suitable
capability/strategy contract or a minimal new domain contract. It must not infer
the profile from an instrument name alone or from the presence of a quote.

At minimum, the effective profile must distinguish:

| Profile | Required evidence | Allowed result |
|---|---|---|
| `FOREX_QUOTE_BASIC` | Fresh valid two-sided quote and identity/provenance | Quote/spread observation; liquidity remains unassessed |
| `LEVEL_II_REQUIRED` | Approved ready Level II evidence plus its trust contract | Level II evidence observation; suitability remains policy-governed |
| `UNSUPPORTED` | Explicitly unsupported or unresolved requirements | Fail-closed diagnostic |

The exact Java type or field name is an implementation decision. The contract
must remain deterministic, auditable and versioned.

## 4. Quote Contract

The quote-based profile requires:

- positive bid and ask;
- ask not below bid;
- canonical market identity matching the request;
- source/provider identity preserved in provenance;
- quote occurrence and source observation times not after the cutoff;
- freshness valid at the cutoff;
- no invalidation or known continuity condition that makes the quote
  non-authoritative;
- an explicit diagnostic when the source contract cannot prove freshness or
  identity.

Failure of any required predicate remains `UNKNOWN`, `UNAVAILABLE`, `STALE` or
another precise non-positive state. The implementation must not substitute a
midpoint, last price, stale quote or synthetic spread.

## 5. Level II Boundary

The following ADR-079 rules remain unchanged:

- spread is not liquidity;
- missing depth is not zero liquidity;
- cTrader local reconstruction is not Kraken provider chronology;
- the first cTrader depth event does not establish a complete book;
- reconnect invalidates continuity until readiness is re-established;
- observed but unclassified depth cannot satisfy a liquidity-qualified
  Candidate;
- a strategy that requires Level II fails closed without approved Level II
  evidence.

The quote-based profile must not be used as a fallback for a Level II-required
strategy.

## 6. Market Thesis and Opportunity Boundary

Quote-based LIVE_CURRENT evidence may enter the existing deterministic evidence
composition path, but it does not by itself authorize:

- a `CANDIDATE` conclusion;
- liquidity suitability;
- strategy admission;
- a TradePlan;
- Risk approval;
- execution authorization;
- broker submission.

Existing Market Thesis, opportunity, TradePlan, Risk and execution authorities
remain unchanged. Any later Candidate policy that consumes basic quote evidence
must be separately explicit about the predicates it requires.

## 7. Historical and Artifact Boundary

`STRICT_OBSERVED` cutoff and replay semantics remain unchanged. A live quote
observation must not be persisted or reused as historical evidence merely because
it was wrapped in an artifact.

The existing artifact execution path must continue to reject live provenance
unless a separately approved artifact contract is introduced. This ADR changes
the bounded analysis capability path only.

## 8. Ownership

- Market Data owns quote acquisition, source binding, freshness and provenance.
- Market Intelligence owns capability-aware evidence composition and explicit
  diagnostics.
- Market Thesis owns evidence aggregation and eligibility conclusions.
- Strategy owns strategy-specific required predicates without redefining global
  liquidity semantics.
- Risk owns account, exposure, sizing and financial authorization.

## 9. Alternatives Rejected

### Reject all LIVE_CURRENT Tradeability

Rejected for the bounded Forex quote-based profile because it conflates quote
validity with Level II requirements and blocks useful deterministic analysis.

### Treat every LIVE_CURRENT request as quote-based

Rejected because it would weaken Level II fail-closed behavior and allow a quote
to satisfy an unproven liquidity predicate.

### Use spread as a liquidity proxy

Rejected by ADR-079. Spread measures quote cost, not depth or executable
quantity.

### Infer the profile from the instrument string alone

Rejected because canonical product identity and active strategy requirements are
separate contracts.

## 10. Consequences

### Positive

- Basic Forex quote analysis can be deterministic and current.
- Level II safety remains explicit and fail-closed.
- Evidence acquisition policy is no longer overloaded with strategy semantics.
- Missingness and unassessed liquidity remain visible.
- No Risk or broker boundary changes are required.

### Costs and trade-offs

- Strategy/capability evidence requirements need an explicit contract.
- Additional tests are required to prevent profile fallback regressions.
- Current quote analysis may still result in `WATCH` or `NO_TRADE` because
  downstream eligibility and policy evidence remain independent.

## 11. Required Human Decision

Human architecture approval is required before implementation. Approval must
confirm that:

1. The narrow supersession of ADR-079 is acceptable.
2. `FOREX_QUOTE_BASIC` may use `LIVE_CURRENT` for quote/spread evidence only.
3. Level II-required strategies remain fail-closed without approved Level II
   evidence.
4. No quote-based result automatically becomes a Candidate or execution
   authorization.
5. Story 0235 is the implementation scope.

This ADR authorizes neither broker mutation nor live runtime validation.

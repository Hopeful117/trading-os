# Code Review - Story 0069

## Reviewed Scope

- Market Activity and Market Data Readiness contracts.
- Raw-versus-normalized OHLC preservation.
- Bounded acquisition and process-local reuse.
- Internal HTTP contract and security boundary.
- Unit, controller, and module validation.

## Findings

The independent audit identified seven high-risk defects. The corrective pass
closed H1-H7; the evidence and changes are recorded in
`docs/architecture/reports/story-0069-audit-remediation.md`.

## Review Notes

- Activity uses completed provider-observed OHLC candles and excludes synthetic
  continuity candles.
- Activity and readiness require expected temporal coverage and expose missing,
  cadence, duplicate, and conflict details.
- Readiness does not equate normalized continuity with provider completeness.
- Provider failures are returned as non-available typed outcomes rather than an
  empty successful fact.
- Request history and cache size are explicitly bounded; only fully available
  facts are cached and cache freshness is based on evidence timestamps.
- Provider dispatch follows `Market.provider`; an unregistered provider is an
  explicit unsupported outcome.
- Existing normalized OHLC behavior remains available through the existing
  `MarketHistoryService` method.
- The internal endpoint is protected by the existing `/internal/**` security
  rule and a dedicated caller allowlist without broadening other internal
  endpoints.
- No Market Intelligence, Risk, Trading Core, or Broker Service behavior was
  changed.

## Known Risks

- The cache is process-local and is lost on restart; it is not suitable as a
  durable catalogue-wide projection without a future design decision.
- The current Story exposes the contract but does not yet add a
  `market-intelligence` consumer. Story 0070 must consume the contract rather
  than duplicate the calculations.
- Future provider implementations must override the snapshot method if their
  normalized history can contain synthetic events; the compatibility default
  cannot reconstruct provider-raw completeness from an already normalized list.
- No live provider or sandbox validation was run during this implementation.
- `market-intelligence` authorization requires explicit deployment environment
  configuration and trusted secret provisioning; it is disabled by default.

## Human Review Required

- Confirm the process-local cache trade-off for the current deployment model.
- Confirm the deployment configuration before enabling the
  `market-intelligence` caller.
- Confirm the internal request parameters and freshness semantics before a
  downstream consumer is implemented.
- Review the future-provider snapshot contract requirement.
- Approve the implementation before commit.

## Runtime Follow-up

The authenticated runtime revalidation recorded in
`runtime-validation.md` confirms that the open-candle cadence defect was fixed
and that normal readiness, freshness, cache reuse/refresh, quote separation, and
no-side-effect behavior are operationally consistent for the tested facts-only
scope. The PEPE adjacent-read difference remains inconclusive because the
contract does not expose the exact mapped provider snapshot used by the fact
calculation. It is a follow-up observation, not a confirmed implementation
defect.

Human acceptance of the reviewed Story is now recorded. Git commit creation
remains outside the coding agent boundary.

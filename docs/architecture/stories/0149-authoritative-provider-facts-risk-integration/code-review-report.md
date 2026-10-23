# Story 0149 - Code Review Report

## Review Scope

Independent review of the provider-neutral authoritative v2 to deterministic
Risk integration, including Kraken compatibility, identity checks, fail-closed
semantics, deal interval mapping and test coverage.

## Findings and Disposition

### Resolved - Kraken fallback could be lost on v2 failure

The live provider now falls back only for Feign retryable v2 failures. Provider
responses and decoding failures are not silently treated as legacy data.

**Disposition:** fixed.

### Resolved - Deals outside the Risk interval were forwarded

Authoritative deals are mapped only when their timestamp is within `[from, to)`.
Malformed timestamps are retained so downstream validation fails closed.

**Disposition:** fixed.

### Resolved - Settlement asset was replaced by account currency

Deal mapping now preserves the provider-supplied settlement asset.

**Disposition:** fixed.

### Resolved - Snapshot identity was not checked defensively

Broker account ID, provider registration reference and bound external account
are validated before checking whether account facts are present. Mismatches do
not use the legacy fallback.

**Disposition:** fixed.

## Residual Risks

- The v2 contract still lacks position-level margin and protective-stop facts;
  open-position snapshots therefore remain fail-closed.
- Real cTrader protocol and account semantics remain unvalidated.
- FTMO Challenge rules remain outside this Story.

## Review Result

`PASS WITH EXPLICIT REAL-PROVIDER VALIDATION GAP`

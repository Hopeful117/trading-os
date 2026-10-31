# Repository Analysis - Story 0186

## Repository State

```text
REMOTE_MAIN: 885fb3be7eb26e63404eea39889be4260dcd8d82
LOCAL_BASE: 885fb3be7eb26e63404eea39889be4260dcd8d82
LOCAL_HEAD: 885fb3be7eb26e63404eea39889be4260dcd8d82
CURRENT_BRANCH: story/0186-paper-cfd-authoritative-required-margin
WORKTREE_STATE: Story 0185 artifacts already existed locally and were preserved
STORY_0185_EVIDENCE_AVAILABLE: YES (local only; no remote branch found)
NEXT_AVAILABLE_STORY: 0186
```

Story 0185 was read completely. Its blocker is confirmed, not reconstructed:
the current PAPER required-margin route has no proven authoritative semantics.

## Current Margin Architecture

### RequiredMarginPort

`RequiredMarginPort.Request` contains broker account, instrument, direction,
quantity, price, and request time. `Fact` contains amount, currency, source ID,
source version, and observed time. The port represents a proposed-order margin
fact consumed by both T0 and T1.

`RequiredMarginCurrencyNormalizer` validates amount/provenance/freshness and
normalizes currency. It does not define the economics of the fact.

### PAPER path

`BrokerRequiredMarginClient` detects `ExecutionMode.PAPER`, loads provider
technical capabilities, and calls `previewByProvider`. The Broker Service
Kraken capability calculates:

```text
amount = quantity * price / leverage
leverage = requested leverage or CASH_LEVERAGE (1)
```

This is a provider preview and a leverage-based calculation. A non-empty
`RequiredMarginPort.Fact` is therefore available at runtime, but its semantic
authority for PAPER CFD is unproven. No canonical CFD contract multiplier,
account policy, exposure interaction, hedge/netting rule, or PAPER authority
identity is carried by the request or fact.

The existing test `resolvesPaperMarginThroughTheAuthoritativeBrokerPreview`
verifies routing and fields, but does not prove economic authority. It must not
be treated as proof that PAPER CFD margin is ready.

### LIVE path

The same client uses the account-specific provider preview for LIVE. This path
must remain unchanged. It does not establish a PAPER margin model.

### Other margin facts

- cTrader account `usedMargin` and `freeMargin` are mapped as current provider
  account facts.
- cTrader position `usedMargin` is mapped as current position margin.
- Risk Domain account margin is derived from the current account snapshot and
  is not proposed-order required margin.
- No cTrader operation equivalent to `calculate required margin for this
  proposed order` is present in the repository.

## Provider Evidence

`docs/architecture/reports/ctrader-eurusd-economic-product-classification-investigation.md`
records:

- EURUSD classification as CFD with medium confidence;
- runtime leverage `10000` centi-units;
- leveraged exposure;
- actual EURUSD margin fields unavailable in the runtime response;
- margin confidence `MEDIUM`.

This evidence proves that leverage exists, not that leverage alone is an
authoritative required-margin contract.

## Authority Decision

Neither `EXISTING_AUTHORITATIVE_SOURCE` nor
`DETERMINISTIC_FROM_AUTHORITATIVE_CONTRACT` is justified. Selecting either
would invent economics or silently make Broker Service the PAPER financial
authority.

The safe result is:

```text
MARGIN_MODEL_DECISION = UNAVAILABLE
```

More precisely:

```text
PAPER_MARGIN_PREVIEW_RUNTIME = AVAILABLE
PAPER_MARGIN_PREVIEW_AUTHORITY = UNPROVEN
PAPER_REQUIRED_MARGIN_AUTHORITY = UNAVAILABLE
```

This also exposes an existing architectural inconsistency: the PAPER path
calls Broker Service/provider capabilities even though ADR-082 assigns PAPER
financial authority to Trading Core and Market Data. Story 0186 records the
inconsistency but does not silently change it.

No changes to `RequiredMarginPort`, `SettlementCompatibilityPolicy`, Risk
Domain, Broker Service, or database schema are authorized by this Story.

## DevLog Limitation

DevLog access was unavailable due to authentication. Current repository source,
ADR-082, the local Story 0185 artifacts, and the cTrader investigation report
were used as the authoritative evidence available for this analysis.

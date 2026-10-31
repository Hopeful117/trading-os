# Code Review - Story 0186

## Verdict

**BLOCKED / NO PRODUCTION CHANGE APPROVED**

## Confirmed Findings

The current repository cannot establish a semantically authoritative PAPER CFD
proposed-order required-margin fact.

Evidence:

- `BrokerRequiredMarginClient` delegates PAPER to a provider preview.
- Kraken's preview derives amount from quantity, price, and leverage, with a
  default leverage of one.
- cTrader observations expose used/free account margin and current position
  margin, but no proposed-order margin operation.
- The EURUSD investigation explicitly records unavailable margin fields and
  only medium confidence for margin semantics.

The runtime preview is not absent: `BrokerRequiredMarginClient` can return a
non-empty fact for PAPER. The correct conclusion is therefore
`PREVIEW_AVAILABLE_BUT_NON_AUTHORITATIVE`, not total runtime unavailability.

The PAPER Broker Service call is also inconsistent with ADR-082's PAPER
financial-authority boundary. This is an existing architectural issue and is
not silently changed in Story 0186.

Treating any of these values as PAPER required margin would be one of:

- notional/leverage inference without an approved contract;
- substitution of used or position margin;
- silent provider coupling of PAPER financial authority.

## Review Conclusion

The correct implementation is to preserve semantic fail-closed behavior while
leaving the existing non-authoritative PAPER preview runtime path unchanged,
and keep `CASH + CFD` rejected. A future Story must first approve the margin
authority and semantics; only then may an adapter or deterministic calculator
be added.

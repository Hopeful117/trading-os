# Code Review - Story 0185

## Verdict

**BLOCKED / NO PRODUCTION CHANGE APPROVED**

## Finding

**High: authoritative PAPER CFD required margin is unproven.**

`trading-core` routes PAPER required-margin requests through
`BrokerRequiredMarginClient` to the Broker Service provider preview. Kraken's
preview calculates `quantity * price / leverage` and defaults leverage to one.
The repository does not establish this as a canonical, mode-correct PAPER CFD
required-margin fact, and Phase 8 forbids introducing margin inference to unlock
compatibility.

Changing `SettlementCompatibilityPolicy` would therefore make the accounting
combination appear supported while the risk authorization prerequisite remains
unproven.

## Review Conclusion

The correct implementation is to preserve `CASH + CFD = REJECTED`, record the
readiness blocker, and require a separately approved margin boundary and tests
before attempting Phase 8 again.

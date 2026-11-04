# Independent Validation Protocol

This protocol is a future validation plan. It records no observed reference
value and does not authorize production use.

1. Use a non-production cTrader account with no execution capability enabled.
2. Capture the balance version, balance, complete position set and complete
   unrealized-PnL set for one external account.
3. Repeat the read and compare account identity, balance version, position
   identities and position facts.
4. Reject the sample when a version, identity, position or PnL fact changes.
5. Record the provider's independently displayed equity and its timestamp only
   after confirming that the display has a defined accounting meaning.
6. Compare the displayed value with the candidate while separately recording
   swap, commission, mirroring commission, deposits, withdrawals and other
   balance movements.
7. Repeat across position states, cost states, account types and currencies.
8. Require an accepted accounting decision and reproducible evidence before
   considering any production contract change.

Until this protocol produces independently validated evidence, the candidate
must remain experimental and fail-closed for risk decisions.

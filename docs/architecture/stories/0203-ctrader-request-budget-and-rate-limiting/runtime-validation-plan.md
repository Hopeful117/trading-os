# Runtime Validation Plan

This plan is not executed by the audit.

1. Obtain separate human approval naming account `49411967` and a fixed maximum
   request count.
2. Confirm the configured environment, endpoint and execution flag before any
   authenticated call. Keep execution disabled.
3. Read only the budget endpoint and application diagnostics before the provider
   protocol sequence.
4. Execute one bounded read-only snapshot sequence, counting session setup and
   protocol reservations separately in local diagnostics.
5. Stop immediately on budget refusal, cooldown refusal, timeout, identity
   mismatch or unexpected provider response.
6. Do not retry, reconnect or submit mutations automatically.
7. Reconcile the observed local count with provider evidence without assuming
   FTMO counting or reset semantics.

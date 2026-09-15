# Engineering Report - Story 0044

## Status

`COMPLETE - RUNTIME VALIDATED; LOCAL COMMIT READY`

## Validation Commands

| Command | Result |
|---|---|
| `mvn -q test` in `cross-service-security-acceptance` | Passed |
| `mvn -q test` in `broker-service` | Passed |
| `mvn -q test` in `market-intelligence` | Passed |
| `mvn -q test` in `trading-core` | Passed |
| `git diff --check` | Passed |

## Test Counts

- Cross-service runtime acceptance: `8` tests.
- Broker Service: `213` tests.
- Market Intelligence: `343` tests.
- Trading Core: `541` tests.

## Runtime Result

The real-socket acceptance test proves the current Core-to-Broker and
Core-to-Market-Intelligence trust boundaries using dynamic ports, H2, real
Servlet dispatch, and real Feign HTTP calls. Negative service-authentication and
delegated-actor cases are rejected at the expected boundary.

## Negative-Path Rejection Layers

| Scenario | Validated rejection layer |
|---|---|
| Wrong Broker audience sent to MI | Receiver service authentication/audience validation |
| Wrong MI audience sent to Broker | Receiver service authentication/audience validation |
| Correctly signed but unauthorized service caller | Receiver caller authorization |
| User JWT on MI internal route | Service authentication boundary |
| Service JWT on MI user route | User authentication/trust-class boundary |
| Service JWT on MI management route | Endpoint authorization boundary |
| Missing delegated actor | Delegation validation |
| Delegated actor mismatch | Actor mismatch validation |
| Cross-user resource access | Authoritative ownership validation |
| Risk handoff without fake actor | Accepted service-only handoff by design |

The runtime evidence distinguishes authentication failures from delegation,
caller-authorization, actor-mismatch, and ownership decisions; an HTTP denial
alone is not treated as proof of the intended layer.

## Observations

The test runs emit known framework warnings, including H2/Flyway version
compatibility, explicit dialect deprecation, default development passwords in
test contexts, and Mockito agent warnings. These warnings did not fail the test
suites.

## Closure Decision

Story 0044 is closed for the validated Core/Broker/Market Intelligence security
boundary. Gateway, Market Data, WebSocket, and standalone-analysis ownership
remain explicitly deferred and are not represented as implemented by this
commit.

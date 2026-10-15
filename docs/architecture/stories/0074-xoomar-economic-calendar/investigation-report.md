# Story 0074 - Engineering Investigation Report

## Outcome

The XOOMAR economic-calendar integration is a coherent V1 foundation and is
recommended for closure with an operational-validation limitation. The
implementation preserves the News Service boundary, keeps provider-specific
details inside infrastructure, persists normalized events idempotently, and
propagates unavailable-provider states without fabricating market context.

## Evidence Reviewed

- Story 0073 and its implementation report.
- Story 0074, its implementation report, and the independent code review.
- ADR-008, ADR-020, ADR-022, and ADR-048.
- News Service domain, persistence, API, security, configuration, XOOMAR
  adapter, and synchronization job.
- Market Intelligence News client and context contributor.
- Gateway News route and Docker Compose configuration.

## Validation

Executed successfully:

- `mvn test -q` in `news-service`.
- `mvn verify -q` in `news-service`.
- `mvn test -q` in `market-intelligence`.
- `mvn test -q` in `gateway`.
- `docker compose config -q` from the repository root.
- `git diff --check`.

The automated tests use deterministic fixtures or mocked provider behavior and
do not require external credentials.

## Findings

### Confirmed strengths

- XOOMAR is opt-in and disabled by default.
- Provider DTOs and transport behavior remain inside the XOOMAR adapter.
- Requests use bounded UTC windows and avoid unsafe automatic retries.
- Stable source identity and database uniqueness support idempotent resync.
- Provider failures preserve existing catalog data and produce unavailable
  results.
- Gateway and Market Intelligence integration remain provider-neutral.
- No risk, trade-plan, or execution responsibility was added to News Service.

### Residual limitations

- No live XOOMAR request was executed. External availability, response-shape
  compatibility, rate limits, and attribution terms remain operational risks.
- PostgreSQL runtime migration was not independently exercised in this review;
  automated persistence validation uses H2.
- Financial-news provider integration remains intentionally unavailable.
- Frontend calendar presentation remains out of scope.
- The repository contains pre-existing untracked runtime reports unrelated to
  this investigation; they were preserved unchanged.

## Recommendation

Accept the implementation as the current V1 foundation. Treat live XOOMAR
validation and PostgreSQL runtime verification as follow-up operational work,
not as reasons to expand this implementation or introduce another provider.

## Repository State

No application source files were modified by this investigation. The only new
artifact is this report. No commit, push, merge, or history rewrite was
performed.

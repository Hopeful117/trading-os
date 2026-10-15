# Story 0104 - Code Review Report

## Review Status

Independent review completed. This report does not constitute human approval.

## Findings

The independent review identified three findings. All three were corrected and
covered by the final validation below.

- The frontend consumes only the provider-neutral `/api/v1/news/items` contract.
- `marketIds` are rendered when present, so articles without currencies do not
  lose their normalized market context.
- `UNSUPPORTED` and `UNAVAILABLE` responses are not rendered as usable article
  feeds even if an inconsistent response contains items; `STALE` and
  `INCOMPLETE` items remain visible with warnings.
- No Alpha Vantage or Marketaux identifier, credential, endpoint, quota or
  provider branch appears in the Financial News feature.
- No sentiment, bullish/bearish interpretation, AI/LLM/ML behavior or trading
  side effect was added.
- The route uses the existing `authGuard` and global authentication interceptor.
- Loading, empty, unavailable, unsupported, incomplete, stale and HTTP-error
  states are represented without exposing backend internals.
- External source links use `target="_blank"` and
  `rel="noopener noreferrer"`.
- The implementation uses typed Observables, RxJS composition and the async
  pipe without manual subscriptions or `ChangeDetectorRef`.
- Filters are limited to backend-supported recent time windows and currency.
- Economic Context remains a separate route and passed existing tests and
  deterministic browser validation.
- No News Service, Gateway, provider or persistence files were modified.
- Existing unrelated untracked reports were preserved.

## Static Analysis

- Targeted SonarQube analysis of `financial-news.ts`: no issues after replacing
  the nested ternary with an explicit window-hours map.
- Targeted SonarQube analysis of `news.service.ts`: no issues.
- Full frontend SonarQube analysis found and cleared five accessibility findings
  in the Financial News template and two contrast findings in its stylesheet.
- The project Quality Gate remains `ERROR` because 28 new violations remain in
  unrelated or other-story files; Story 0104 files have no remaining open
  findings.
- Frontend provider-leakage search: no Alpha Vantage, Marketaux, sentiment or
  API-key references in the feature.

## Validation Reviewed

- `npm run test:ci`: 51 test files, 450 tests passed.
- `npm run test:coverage`: 450 tests passed; 82.92% statements, 76.84%
  branches, 81.12% functions and 83.34% lines.
- `npm run build`: passed.
- Prettier check: passed.
- `docker compose config -q`: passed.
- `git diff --check`: passed.
- Browser validation covered authenticated route rendering with deterministic
  intercepted data, empty state, safe source link attributes, Economic Calendar
  navigation and Financial News page mobile layout.

## Residual Risks And Limitations

- Live backend article rendering was not validated because no authenticated
  production data was available; browser populated rendering used a deterministic
  intercepted response.
- The frontend SonarQube project Quality Gate remains failed on unrelated or
  other-story new violations. Story 0104-specific findings are resolved.
- The authenticated global navbar has pre-existing horizontal overflow at the
  tested mobile width. The Financial News page itself has no internal overflow;
  the navbar issue is outside Story 0104.
- Existing non-failing Angular bundle and stylesheet budget warnings remain.

## Previously Reported Findings Resolved

- **Major:** Market identifiers were not rendered. The template now renders
  normalized `marketIds`, and the component test verifies the behavior.
- **Minor:** The Implementation Plan still claimed approval was pending. Its
  status and approval boundary now reflect the approved and executed plan.
- **Minor/test gap:** Unsupported responses containing items were ambiguous. The
  view-model now treats `UNSUPPORTED` and `UNAVAILABLE` as unavailable, and the
  test covers an unsupported response containing items.
- **Major:** Provider-derived availability messages were rendered directly. The
  frontend now uses provider-neutral allowlisted messages.
- **Minor:** Publication times did not identify a timezone. Financial News now
  renders publication and fetch times explicitly in UTC.
- **Minor/test gap:** The retry control was only rendered, not exercised. The
  component test now clicks retry, verifies a new request, and validates the
  recovered feed.

## Human Review Boundary

The implementation is ready for human review and commit decision. No commit,
push, merge or pull request was created.

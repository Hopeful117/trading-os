# Code Review - Story 0211

## Resolved Review Findings

The independent review findings about aggregate status, incomplete provenance,
follow-up governance, legacy null handling, missing-evidence references and
unsupported structured claims were corrected. Focused negative tests now cover
those cases, including empty and non-array findings collections.

## Remaining Finding

### Medium - Adaptive follow-up is not synthesized

`ResearchApplicationService` executes the authorized follow-up capability but
does not send the returned deterministic result through a final AI
interpretation pass. Story 0211 records this explicitly as `NOT_REEVALUATED`,
and the evaluator returns `INSUFFICIENT_EVIDENCE` for follow-up effect. This
remains a product-quality gap for a future focused Story, not an evaluator bug.

## Review Notes

- The evaluator is deterministic and provider-independent.
- The six fixtures are explicitly synthetic and do not claim historical market
  truth.
- Provenance additions are additive inside the existing report JSON and do not
  change Gateway/MCP operation names.
- No new service, provider, MCP capability, execution authority or risk boundary
  was introduced.

## Residual Risks

- Human usefulness review is not automated.
- Real ChatGPT evaluation was not repeated in this Story; Story 0210's live
  validation remains the provider connectivity evidence.

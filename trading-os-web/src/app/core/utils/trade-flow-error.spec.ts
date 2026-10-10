import { HttpErrorResponse } from '@angular/common/http';
import { tradeFlowErrorMessage, tradePreparationFailure } from './trade-flow-error';

describe('tradeFlowErrorMessage', () => {
  it('maps a version conflict without exposing backend details', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: { code: 'VERSION_CONFLICT', message: 'internal detail' },
    });

    expect(tradeFlowErrorMessage(error, 'fallback')).toBe(
      'This trade plan changed. Reload it before trying again.',
    );
    expect(tradeFlowErrorMessage(error, 'fallback')).not.toContain('internal detail');
  });

  it('uses the safe fallback for an unknown client error', () => {
    const error = new HttpErrorResponse({ status: 400 });

    expect(tradeFlowErrorMessage(error, 'Safe fallback')).toBe('Safe fallback');
  });

  it('maps a risk preflight rejection without exposing backend details', () => {
    const error = new HttpErrorResponse({
      status: 422,
      error: { code: 'RISK_PREFLIGHT_REJECTED', message: 'MAX_EXPOSURE: internal detail' },
    });

    expect(tradeFlowErrorMessage(error, 'fallback')).toBe(
      'The proposed trade is not feasible under the current risk limits. Review the blocking rules and try again later.',
    );
  });

  it('preserves structured risk preflight details for the preparation view', () => {
    const error = new HttpErrorResponse({
      status: 422,
      error: {
        code: 'RISK_PREFLIGHT_REJECTED',
        message: 'Risk feasibility check rejected the Trade Plan',
        retryable: true,
        reasons: [{ code: 'MAX_EXPOSURE', severity: 'BLOCKING', message: 'Too much exposure' }],
        warnings: [],
        metrics: { exposureRatio: 0.06 },
      },
    });

    expect(tradePreparationFailure(error)).toMatchObject({
      code: 'RISK_PREFLIGHT_REJECTED',
      retryable: true,
      metrics: { exposureRatio: 0.06 },
    });
    expect(tradePreparationFailure(error)?.reasons[0].code).toBe('MAX_EXPOSURE');
  });

  it.each([500, 503, 0])('maps service failure status %s to an actionable message', (status) => {
    const error = new HttpErrorResponse({ status });

    expect(tradeFlowErrorMessage(error, 'fallback')).toBe(
      'Trading services are temporarily unavailable. Try again later.',
    );
  });
});

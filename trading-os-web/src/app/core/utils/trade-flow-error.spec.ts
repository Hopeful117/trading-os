import { HttpErrorResponse } from '@angular/common/http';
import { tradeFlowErrorMessage } from './trade-flow-error';

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
});

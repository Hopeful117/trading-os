import { marketProductTypeLabel } from './market-response';
import { describe, expect, it } from 'vitest';

describe('marketProductTypeLabel', () => {
  it('labels cTrader CFD markets explicitly', () => {
    expect(marketProductTypeLabel('CFD')).toBe('CFD');
  });

  it('keeps unknown backend values safe for display', () => {
    expect(marketProductTypeLabel(null)).toBe('Unknown');
  });
});

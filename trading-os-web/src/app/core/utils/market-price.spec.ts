import { calculateMarketSpread, formatMarketPrice, marketPricePrecision } from './market-price';

describe('market price utilities', () => {
  it('uses instrument precision instead of permissive fixed rounding', () => {
    expect(marketPricePrecision({ marketConstraints: { pricePrecision: 5 } })).toBe(5);
    expect(formatMarketPrice(1.08501, 5)).toBe('1.08501');
  });

  it('preserves a one-tick forex spread', () => {
    const spread = calculateMarketSpread(1.085, 1.08501, 5);

    expect(formatMarketPrice(spread, 5)).toBe('0.00001');
  });

  it('falls back to eight decimal places for missing precision', () => {
    expect(marketPricePrecision(undefined)).toBe(8);
  });
});

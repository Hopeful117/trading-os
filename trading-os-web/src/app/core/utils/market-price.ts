export interface MarketPricePrecisionSource {
  marketConstraints?: {
    pricePrecision?: number | null;
  } | null;
}

export function marketPricePrecision(market: MarketPricePrecisionSource | null | undefined): number {
  const precision = market?.marketConstraints?.pricePrecision;
  return typeof precision === 'number' && Number.isInteger(precision) && precision >= 0 && precision <= 18
    ? precision
    : 8;
}

export function formatMarketPrice(value: number | null | undefined, precision: number): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return 'UNKNOWN';
  return value.toFixed(precision);
}

export function calculateMarketSpread(bid: number, ask: number, precision: number): number {
  const factor = 10 ** precision;
  return (Math.round(ask * factor) - Math.round(bid * factor)) / factor;
}

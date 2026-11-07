export type MarketProductType = 'SPOT' | 'LINEAR_PERPETUAL' | 'CFD';

export interface MarketResponse {
  marketId: string;

  provider: string;

  symbol: string;

  baseAsset: string;

  quoteAsset: string;

  productType?: MarketProductType | null;

  marketState: {
    tradingStatus: string;
    tradable: boolean;
    closureReason: string;
    lastUpdated: string;
  };

  marketConstraints: {
    minimumOrderSize: number;
    minimumCost: number;
    tickSize: number;
    quantityPrecision: number;
    pricePrecision: number;
  };
}

export function marketProductTypeLabel(productType: MarketProductType | null | undefined): string {
  switch (productType) {
    case 'SPOT':
      return 'Spot';
    case 'LINEAR_PERPETUAL':
      return 'Futures';
    case 'CFD':
      return 'CFD';
    default:
      return 'Unknown';
  }
}

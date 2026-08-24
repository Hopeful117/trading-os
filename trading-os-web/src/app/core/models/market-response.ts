export interface MarketResponse {
  marketId: string;

  provider: string;

  symbol: string;

  baseAsset: string;

  quoteAsset: string;

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

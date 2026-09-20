export type DecisionContextMarketReason = 'MARKET_NOT_FOUND' | 'MARKET_NOT_TRADABLE';

export interface DecisionContextAccount {
  accountId: string;
  brokerAccountId: string | null;
  name: string;
  baseCurrency: string;
  equity: number | null;
  peakEquity: number | null;
  rulesId: string | null;
  userId: string | null;
  riskProfileId: string | null;
  riskProfileSemanticVersion: string | null;
  tradePlanningProfileId: string | null;
  tradePlanningProfileVersion: number | null;
}

export interface DecisionContextMarket {
  marketId: string;
  symbol: string | null;
  provider: string | null;
  eligible: boolean;
  reasons: DecisionContextMarketReason[];
}

export interface DecisionContextResponse {
  account: DecisionContextAccount;
  markets: DecisionContextMarket[];
  eligibleMarketIds: string[];
  resolvedAt: string;
}

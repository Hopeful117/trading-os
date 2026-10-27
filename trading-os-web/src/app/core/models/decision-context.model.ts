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
  readiness?: ChallengeReadiness | null;
  scopeStatus?: string | null;
  scopeMessage?: string | null;
}

export interface ChallengeReadiness {
  accountId: string;
  challengeActive: boolean;
  status: string;
  message: string | null;
  challengeId: string | null;
  challengeDefinitionVersion: string | null;
  phase: string | null;
  challengeStartingCapital: number | null;
  challengeCurrency: string | null;
  accountStartingBalance: number | null;
  accountEquity: number | null;
  riskPolicyId: string | null;
  riskPolicyVersion: string | null;
  brokerAccountId: string | null;
  requiredMarketProvider: string | null;
  requiredProviderRegistrationReference: string | null;
}

export interface CreateTradePlanResponse {
  tradePlanId: string;
  tradePlanVersion: number;
}

export interface TradePlanResponse {
  id: string;
  version: number;
  previousVersion: number | null;
  status: string;
  planningContextId: string;
  planningContextVersion: number;
  contextCapturedAt: string;
  instrument: string;
  direction: string;
  entryType: string;
  entryPrice: number | null;
  stopLoss: number;
  takeProfits: number[];
  quantity: number;
  notional: number;
  monetaryRisk: number;
  riskReward: number;
  expiresAt: string;
  thesis: string;
  opportunityIds: string[];
  observationIds: string[];
  aiAnalysisIds: string[];
  confirmationConditions: string[];
  invalidationConditions: string[];
  managementRules: string[];
  createdAt: string;
  tradingAccountId: string;
}

export interface RiskReason {
  code: string;
  ruleVersion: string | null;
  severity: string;
  message: string;
}

export interface RiskDecisionResponse {
  evaluationId: string;
  tradePlanId: string;
  tradePlanVersion: number;
  accountId: string;
  status: string;
  decision: 'APPROVED' | 'APPROVED_WITH_WARNINGS' | 'REJECTED';
  approved: boolean;
  reasons: RiskReason[];
  warnings: RiskReason[];
  evaluatedAt: string;
}

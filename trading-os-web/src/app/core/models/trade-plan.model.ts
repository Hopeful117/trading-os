export interface CreateTradePlanResponse {
  tradePlanId: string;
  tradePlanVersion: number;
}

export interface ManualTradePlanRequest {
  accountId: string;
  marketId: string;
  direction: 'LONG' | 'SHORT';
  entryType: 'MARKET' | 'LIMIT';
  entryPrice: number | null;
  referencePrice: number;
  stopLoss: number;
  stopRationale: string;
  takeProfits: { price: number; allocationPercent: number }[];
  quantity: number;
  monetaryRisk: number;
  thesis: string;
  confirmationConditions: string[];
  invalidationConditions: string[];
  managementRules?: string[];
}

export interface TradePlanResponse {
  id: string;
  version: number;
  previousVersion: number | null;
  status: string;
  origin?: 'OPPORTUNITY' | 'MANUAL';
  authorId?: string | null;
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
  metrics?: Record<string, number>;
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
  metrics?: Record<string, number>;
}

export interface TradeScenarioProjection {
  tradePlanId: string;
  tradePlanVersion: number;
  accountId: string;
  observedAt: string | null;
  projectedAt: string;
  economicsBasis: 'GROSS';
  current: TradeScenario;
  stop: TradeScenario;
  target: TradeScenario;
  challenge: {
    availability: 'AVAILABLE' | 'PARTIAL' | 'UNAVAILABLE' | 'NOT_APPLICABLE';
    reasons: string[];
    currentValue: number | null;
    targetValue: number | null;
    targetRemaining: number | null;
  };
  provenance: {
    accountVersion: number;
    accountObservedAt: string | null;
    valuationSnapshotId: string | null;
    valuationSourceVersion: number;
    valuationTimestamp: string | null;
    valuationPolicyVersion: string | null;
    challengeId: string | null;
    riskContextEvaluatedAt: string | null;
    challengeEvaluatedAt: string | null;
  };
  economics: {
    availability: 'AVAILABLE' | 'PARTIAL' | 'UNAVAILABLE' | 'NOT_APPLICABLE';
    reasons: string[];
    monetaryRisk: number | null;
    monetaryReward: number | null;
    riskReward: number | null;
  };
}

export interface TradeScenario {
  type: 'CURRENT' | 'STOP' | 'TARGET';
  availability: 'AVAILABLE' | 'PARTIAL' | 'UNAVAILABLE' | 'NOT_APPLICABLE';
  reasons: string[];
  projectedPnl: number | null;
  projectedBalance: number | null;
  projectedEquity: number | null;
  dailyLossRemaining: number | null;
  totalLossRemaining: number | null;
  challengeValue: number | null;
  challengeTargetRemaining: number | null;
}

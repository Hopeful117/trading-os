export type ExecutionAuthorizationStatus =
  | 'PENDING_HUMAN_CONFIRMATION'
  | 'APPROVED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'REVOKED'
  | 'CONSUMING'
  | 'CONSUMED';

export interface ExecutionAuthorizationResponse {
  authorizationId: string;
  status: ExecutionAuthorizationStatus;
  ownerUserId: string;
  agentId: string;
  brokerAccountId: string;
  tradingAccountId: string;
  tradePlanId: string;
  tradePlanVersion: number;
  riskEvaluationId: string;
  canonicalMarketId: string | null;
  requestFingerprint: string;
  confirmationUrl: string;
  expiresAt: string;
  executionIntentId: string | null;
  plan: {
    instrument: string;
    instrumentProvider: string;
    instrumentProductType: string;
    entryType: string;
    direction: string;
    quantity: number;
    entryPrice: number | null;
    stopLoss: number | null;
    takeProfits: number[];
    monetaryRisk: number | null;
    thesis: string;
  };
}

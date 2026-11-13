export type BrokerProvider = 'KRAKEN' | 'CTRADER';
export const KRAKEN_PROVIDER_REGISTRATION_REFERENCE = '7f6f5f8e-3d42-4a0d-9e11-4d8f2a7c6b01';
export const CTRADER_PROVIDER_REGISTRATION_REFERENCE = '3d6c3f0a-8d2d-4f4d-9a4c-7a2e1b5c9012';
export type ExecutionMode = 'LIVE' | 'PAPER';

export type BrokerConnectionStatus =
  | 'CREATED'
  | 'PENDING_VALIDATION'
  | 'CONNECTED'
  | 'INVALID_CREDENTIALS'
  | 'INSUFFICIENT_PERMISSIONS'
  | 'TEMPORARILY_UNAVAILABLE'
  | 'REAUTHENTICATION_REQUIRED'
  | 'DISCONNECTED'
  | 'REVOKED';

export interface BrokerAccount {
  id: string;
  provider: string;
  executionMode: ExecutionMode;
  displayName: string;
  externalAccountId: string | null;
  connectionStatus: BrokerConnectionStatus;
  lastValidatedAt: string | null;
  lastSynchronizedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface BrokerAccountReadOnlyStatus {
  status: string;
  executionAllowed: boolean;
  riskEvaluationAllowed: boolean;
  snapshot: BrokerAccountSnapshot | null;
  readiness?: BrokerAccountReadiness;
}

export interface BrokerAccountReadiness {
  accountObservation: string;
  t0Risk: string;
  liveExecution: string;
  unavailableFacts: string[];
  derivedFacts: string[];
}

export interface CTraderExecutionInstrumentContract {
  symbolId: number;
  symbolName: string;
  digits: number;
  pipPosition: number;
  minVolume: number;
  maxVolume: number;
  stepVolume: number;
  lotSize: number | null;
  maxExposure: number | null;
  stopLossDistance: number | null;
  takeProfitDistance: number | null;
  distanceSetIn: string | null;
}

export interface CTraderReadOnlyValidation {
  contract: CTraderExecutionInstrumentContract;
  metadata: Record<string, unknown>;
}

export interface BrokerAccountSnapshot {
  brokerAccountId: string;
  providerRegistrationReference: string;
  externalAccountId: string | null;
  balances: Record<string, number>;
  positions: BrokerPositionSnapshot[];
  observedAt: string;
  account: BrokerAccountFacts | null;
  orders: BrokerOrderSnapshot[];
  deals: BrokerDealSnapshot[];
  completeness: string;
  freshness: string;
  observationStartedAt: string;
  completedAt: string;
  unavailabilityReasons: string[];
}

export interface BrokerAccountFacts {
  valuationAsset: string | null;
  balance: number | null;
  equity: number | null;
  usedMargin: number | null;
  freeMargin: number | null;
}

export interface BrokerPositionSnapshot {
  instrument: string;
  signedQuantity: number;
  entryPrice: number;
  observedAt: string;
  brokerPositionReference: string;
  margin: number | null;
  stopLoss: number | null;
}

export interface BrokerOrderSnapshot {
  externalOrderId: string;
  clientOrderId: string | null;
  status: string;
  quantity: number;
  filledQuantity: number;
  observedAt: string;
}

export interface BrokerDealSnapshot {
  providerTradeReference: string;
  providerPositionReference: string | null;
  instrument: string;
  settlementAsset: string;
  side: string;
  quantity: number;
  price: number;
  fee: number | null;
  swap: number | null;
  realizedPnl: number | null;
  closedAt: string;
  closingPosition: boolean;
}

export interface CredentialValidation {
  outcome: string;
  connectionStatus: BrokerConnectionStatus;
  missingPermissions: string[];
  validatedAt: string;
  safeMessage: string;
}

export type CTraderAuthorizationStatus =
  'INITIATED' | 'ACCOUNT_SELECTION_REQUIRED' | 'BOUND' | 'FAILED' | 'EXPIRED' | 'REVOKED';

export interface CTraderAuthorization {
  attemptId: string;
  authorizationUrl: string | null;
  status: CTraderAuthorizationStatus;
  discoveredAccountIds: string[];
  selectedExternalAccountId: string | null;
  expiresAt: string;
  safeMessage: string;
}

export interface CTraderMarketDataAuthorization {
  attemptId: string;
  authorizationUrl: string;
  redirectUri: string;
  scope: string;
}

export type CTraderMarketDataAuthorizationStatus = 'INITIATED' | 'COMPLETED' | 'FAILED' | 'EXPIRED';

export interface CTraderMarketDataAuthorizationState {
  attemptId: string;
  status: CTraderMarketDataAuthorizationStatus;
  environment: string | null;
  externalAccountId: number | null;
  failureCode: string | null;
}

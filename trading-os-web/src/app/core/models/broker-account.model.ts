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

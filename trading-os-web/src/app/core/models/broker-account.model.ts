export type BrokerProvider = 'KRAKEN';
export const KRAKEN_PROVIDER_REGISTRATION_REFERENCE = '7f6f5f8e-3d42-4a0d-9e11-4d8f2a7c6b01';
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

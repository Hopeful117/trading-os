export type BrokerProvider = 'KRAKEN';

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
  provider: BrokerProvider;
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

export type AgentType = 'OPENCODE' | 'OTHER';
export type AgentStatus = 'ACTIVE' | 'SUSPENDED' | 'REVOKED';
export type AgentPermission = 'accounts:read' | 'positions:read';
export type AgentResourceType = 'BROKER_ACCOUNT';
export type AgentGrantStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED';

export interface Agent {
  id: string;
  ownerUserId: string;
  displayName: string;
  agentType: AgentType;
  status: AgentStatus;
  createdAt: string;
  updatedAt: string;
}

export interface AgentCredential {
  credentialId: string;
  agentId: string;
  credential: string;
  fingerprint: string;
  credentialVersion: number;
  status: string;
  createdAt: string;
  expiresAt: string | null;
}

export interface AgentGrant {
  id: string;
  agentId: string;
  permission: AgentPermission;
  resourceType: AgentResourceType;
  resourceId: string;
  status: AgentGrantStatus;
  createdAt: string;
  expiresAt: string | null;
  revokedAt: string | null;
  revocationReason: string | null;
  grantVersion: number;
}

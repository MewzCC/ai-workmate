import { request } from '@/lib/oaApi';

export type AgentRiskLevel = 'L0' | 'L1' | 'L2';
export type AgentSideEffect = 'NONE' | 'SINGLE_WRITE';

export interface AiOperationPermissionOverview {
  runtime: {
    agentEnabled: boolean;
    planningEnabled: boolean;
    executionEnabled: boolean;
    writeToolsEnabled: boolean;
  };
  tenantPolicy: { enabled: boolean; writeToolsEnabled: boolean };
  tools: Array<{
    code: string;
    name: string;
    description: string;
    riskLevel: AgentRiskLevel;
    sideEffect: AgentSideEffect;
    confirmationPolicy: 'NONE' | 'EXPLICIT' | 'SECONDARY';
    requiredPermissions: string[];
    pageIds: string[];
    platformEnabled: boolean;
    tenantEnabled: boolean;
    effectiveEnabled: boolean;
  }>;
  roles: Array<{
    code: string;
    name: string;
    description: string;
    builtin: boolean;
    immutable: boolean;
    eligibleToolCodes: string[];
    toolCodes: string[];
  }>;
}

export const aiOperationPermissionApi = {
  overview: () => request<AiOperationPermissionOverview>('/admin/ai-operation-permissions'),
  updateTenantPolicy: (enabled: boolean, writeToolsEnabled: boolean) =>
    request<AiOperationPermissionOverview>('/admin/ai-operation-permissions/tenant-policy', {
      method: 'PUT',
      body: JSON.stringify({ enabled, writeToolsEnabled }),
    }),
  updateToolStatus: (toolCode: string, enabled: boolean) =>
    request<AiOperationPermissionOverview>(
      `/admin/ai-operation-permissions/tools/${encodeURIComponent(toolCode)}`,
      { method: 'PUT', body: JSON.stringify({ enabled }) },
    ),
  updateRoleTools: (roleCode: string, toolCodes: string[]) =>
    request<AiOperationPermissionOverview>(
      `/admin/ai-operation-permissions/roles/${encodeURIComponent(roleCode)}`,
      { method: 'PUT', body: JSON.stringify({ toolCodes }) },
    ),
};

import { afterEach, describe, expect, it, vi } from 'vitest';
import { aiOperationPermissionApi } from './aiOperationPermissionApi';

describe('aiOperationPermissionApi', () => {
  afterEach(() => vi.restoreAllMocks());

  it('updates a role tool grant without sending identity or risk metadata', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(
      JSON.stringify({ code: 200, message: 'ok', data: {} }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    ));
    await aiOperationPermissionApi.updateRoleTools('EMPLOYEE', ['leave.apply']);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/ai-operation-permissions/roles/EMPLOYEE',
      expect.objectContaining({
        method: 'PUT',
        credentials: 'include',
        body: JSON.stringify({ toolCodes: ['leave.apply'] }),
      }),
    );
  });

  it('encodes tool code when updating the tenant override', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(
      JSON.stringify({ code: 200, message: 'ok', data: {} }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    ));
    await aiOperationPermissionApi.updateToolStatus('knowledge.search', false);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/ai-operation-permissions/tools/knowledge.search',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify({ enabled: false }) }),
    );
  });
});

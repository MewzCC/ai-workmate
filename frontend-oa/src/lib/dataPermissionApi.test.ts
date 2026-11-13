import { afterEach, describe, expect, it, vi } from 'vitest';
import { dataPermissionApi } from './dataPermissionApi';

describe('dataPermissionApi',()=>{
  afterEach(()=>vi.restoreAllMocks());
  it('binds a role through the protected data permission endpoint',async()=>{
    const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(new Response(JSON.stringify({code:200,message:'ok',data:null}),{status:200,headers:{'Content-Type':'application/json'}}));
    await dataPermissionApi.bindRole('HR_MANAGER',7);
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/data-permissions/roles/HR_MANAGER',expect.objectContaining({method:'PUT',credentials:'include',body:JSON.stringify({policyId:7})}));
  });
  it('includes optimistic-lock version when deleting a policy',async()=>{
    const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(new Response(JSON.stringify({code:200,message:'ok',data:null}),{status:200,headers:{'Content-Type':'application/json'}}));
    await dataPermissionApi.remove(5,3);
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/data-permissions/policies/5?version=3',expect.objectContaining({method:'DELETE'}));
  });
});

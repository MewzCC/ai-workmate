import { queryString, request } from '@/lib/oaApi';
import type { AccessRole, AccessUser } from '@/lib/accessControlApi';

export type DataScopeType = 'SELF' | 'DEPARTMENT' | 'DEPARTMENT_AND_CHILDREN' | 'CUSTOM_DEPARTMENTS' | 'ALL';
export interface DataPermissionPolicy { id:number; name:string; description?:string; scopeType:DataScopeType; departmentIds:number[]; enabled:boolean; version:number; updatedAt:string; }
export interface DataPermissionOverview { policies:DataPermissionPolicy[]; roles:AccessRole[]; users:AccessUser[]; departments:Array<{id:number;code:string;name:string;parentId?:number;status:number}>; roleBindings:Array<{roleCode:string;policyId:number}>; userExceptions:Array<{userId:number;policyId:number}>; }
export interface DataPermissionPolicyPayload { name:string; description?:string; scopeType:DataScopeType; departmentIds:number[]; enabled:boolean; version?:number; }
export interface DataPermissionPreview { userId:number; source:'ROLE'|'USER_EXCEPTION'; scopeTypes:DataScopeType[]; departmentIds:number[]; visibleUserIds:number[]; }

export const dataPermissionApi = {
  overview: () => request<DataPermissionOverview>('/admin/data-permissions'),
  create: (payload:DataPermissionPolicyPayload) => request<DataPermissionPolicy>('/admin/data-permissions/policies',{method:'POST',body:JSON.stringify(payload)}),
  update: (id:number,payload:DataPermissionPolicyPayload) => request<DataPermissionPolicy>(`/admin/data-permissions/policies/${id}`,{method:'PUT',body:JSON.stringify(payload)}),
  remove: (id:number,version:number) => request<void>(`/admin/data-permissions/policies/${id}${queryString({version})}`,{method:'DELETE'}),
  bindRole: (roleCode:string,policyId:number) => request<void>(`/admin/data-permissions/roles/${encodeURIComponent(roleCode)}`,{method:'PUT',body:JSON.stringify({policyId})}),
  bindUser: (userId:number,policyId:number) => request<void>(`/admin/data-permissions/users/${userId}/exception`,{method:'PUT',body:JSON.stringify({policyId})}),
  clearUser: (userId:number) => request<void>(`/admin/data-permissions/users/${userId}/exception`,{method:'DELETE'}),
  preview: (userId:number) => request<DataPermissionPreview>(`/admin/data-permissions/preview/${userId}`),
};

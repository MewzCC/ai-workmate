import { queryString, request } from '@/lib/oaApi';
export type EndpointStatus='DRAFT'|'ACTIVE'|'DISABLED';
export interface EndpointPayload{code:string;name:string;upstreamCode:string;method:'GET'|'POST'|'PUT'|'PATCH'|'DELETE';relativePath:string;requestTemplate?:string;description?:string;version?:number}
export interface IntegrationEndpoint extends EndpointPayload{id:number;status:EndpointStatus;version:number;updatedAt:string;canManage:boolean;canExecute:boolean;allowedTransitions:EndpointStatus[]}
export interface IntegrationInvocation{id:number;outcome:'SUCCESS'|'FAILED';httpStatus?:number;durationMs:number;responsePreview?:string;errorCode?:string;operatorLabel:string;createdAt:string}
export interface IntegrationPage{records:IntegrationEndpoint[];total:number;page:number;size:number;stats:{total:number;active:number;disabled:number;successful:number;failed:number};canManage:boolean;canExecute:boolean}
export const integrationApi={
 list:(params:Record<string,string|number|undefined>)=>request<IntegrationPage>(`/integration/endpoints${queryString(params)}`),
 detail:(id:number)=>request<{endpoint:IntegrationEndpoint;invocations:IntegrationInvocation[]}>(`/integration/endpoints/${id}`),
 options:()=>request<{upstreams:{code:string;label:string;available:boolean}[]}>('/integration/endpoints/options'),
 create:(payload:EndpointPayload)=>request<IntegrationEndpoint>('/integration/endpoints',{method:'POST',body:JSON.stringify(payload)}),
 update:(id:number,payload:EndpointPayload)=>request<IntegrationEndpoint>(`/integration/endpoints/${id}`,{method:'PUT',body:JSON.stringify(payload)}),
 status:(id:number,status:EndpointStatus,version:number)=>request<IntegrationEndpoint>(`/integration/endpoints/${id}/status`,{method:'POST',body:JSON.stringify({status,version})}),
 execute:(id:number,version:number)=>request<IntegrationInvocation>(`/integration/endpoints/${id}/execute?version=${version}`,{method:'POST'}),
};

import { queryString, request } from '@/lib/oaApi';

export type BudgetStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED' | 'CANCELLED';
export type BudgetOperation = 'OCCUPY' | 'RELEASE' | 'SPEND';
export type BudgetAlert = 'NORMAL' | 'WARNING' | 'FULL';
export interface BudgetPayload { code:string; name:string; fiscalYear:number; ownerUserId:number; totalAmount:number; currency:string; warningThreshold:number; summary?:string; version?:number }
export interface Budget extends BudgetPayload { id:number; ownerLabel:string; occupiedAmount:number; spentAmount:number; availableAmount:number; utilizationPercent:number; alertLevel:BudgetAlert; status:BudgetStatus; version:number; updatedAt:string; canManage:boolean; allowedTransitions:BudgetStatus[] }
export interface BudgetTransaction { id:number; type:string; amount?:number; occupiedBefore:number; occupiedAfter:number; spentBefore:number; spentAfter:number; referenceCode?:string; note?:string; operatorLabel:string; createdAt:string }
export interface BudgetPageResult { records:Budget[]; total:number; page:number; size:number; stats:{totalPlans:number;activePlans:number;warningPlans:number;totalAmount:number;occupiedAmount:number;spentAmount:number;availableAmount:number}; canManage:boolean }
export const budgetApi={
  list:(params:Record<string,string|number|undefined>)=>request<BudgetPageResult>(`/budgets${queryString(params)}`),
  detail:(id:number)=>request<{budget:Budget;transactions:BudgetTransaction[]}>(`/budgets/${id}`),
  options:()=>request<{owners:{id:number;label:string;detail?:string}[]}>('/budgets/options'),
  create:(payload:BudgetPayload)=>request<Budget>('/budgets',{method:'POST',body:JSON.stringify(payload)}),
  update:(id:number,payload:BudgetPayload)=>request<Budget>(`/budgets/${id}`,{method:'PUT',body:JSON.stringify(payload)}),
  status:(id:number,status:BudgetStatus,version:number,reason?:string)=>request<Budget>(`/budgets/${id}/status`,{method:'POST',body:JSON.stringify({status,version,reason})}),
  operate:(id:number,type:BudgetOperation,amount:number,version:number,referenceCode?:string,note?:string)=>request<Budget>(`/budgets/${id}/transactions`,{method:'POST',body:JSON.stringify({type,amount,version,referenceCode,note})}),
};

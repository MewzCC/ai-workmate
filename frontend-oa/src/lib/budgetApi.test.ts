import { afterEach, describe, expect, it, vi } from 'vitest';
import { budgetApi } from './budgetApi';

const result=(data:unknown)=>new Response(JSON.stringify({code:200,message:'ok',data}),{status:200,headers:{'Content-Type':'application/json'}});
afterEach(()=>vi.restoreAllMocks());

describe('预算接口',()=>{
 it('列表查询使用独立预算资源路径',async()=>{const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(result({records:[]}));await budgetApi.list({status:'ACTIVE',fiscalYear:2026,page:1,size:20});expect(fetchMock.mock.calls[0][0]).toBe('/api/budgets?status=ACTIVE&fiscalYear=2026&page=1&size=20')});
 it('额度操作携带类型、金额、业务单号和版本',async()=>{const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(result({id:8}));await budgetApi.operate(8,'OCCUPY',1200,4,'PO-2026-01','采购预占');expect(fetchMock.mock.calls[0][0]).toBe('/api/budgets/8/transactions');expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({method:'POST',body:JSON.stringify({type:'OCCUPY',amount:1200,version:4,referenceCode:'PO-2026-01',note:'采购预占'})}))});
});

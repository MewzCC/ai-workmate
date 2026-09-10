import {afterEach,describe,expect,it,vi} from 'vitest';
import {integrationApi} from './integrationApi';
const result=(data:unknown)=>new Response(JSON.stringify({code:200,message:'ok',data}),{status:200,headers:{'Content-Type':'application/json'}});afterEach(()=>vi.restoreAllMocks());
describe('接口联调中心 API',()=>{
 it('只调用固定的接口目录资源',async()=>{const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(result({records:[]}));await integrationApi.list({status:'ACTIVE',page:1,size:20});expect(fetchMock.mock.calls[0][0]).toBe('/api/integration/endpoints?status=ACTIVE&page=1&size=20')});
 it('执行时只提交资源编号和乐观锁版本',async()=>{const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(result({outcome:'SUCCESS'}));await integrationApi.execute(8,3);expect(fetchMock.mock.calls[0][0]).toBe('/api/integration/endpoints/8/execute?version=3');expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({method:'POST'}));expect(fetchMock.mock.calls[0][1]).not.toHaveProperty('body')});
});

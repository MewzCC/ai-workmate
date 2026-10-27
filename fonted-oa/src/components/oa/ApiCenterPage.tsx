'use client';
import { useCallback,useEffect,useRef,useState } from 'react';
import { Alert,App,Badge,Button,Card,Col,Descriptions,Drawer,Empty,Form,Input,Row,Select,Space,Statistic,Tag,Timeline,Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
import { OaIcon } from '@/components/OaIcon';
import ResponsiveTable from './ResponsiveTable';
import { integrationApi,type EndpointPayload,type EndpointStatus,type IntegrationEndpoint,type IntegrationInvocation } from '@/lib/integrationApi';
import { formatOaApiError } from '@/lib/oaApi';
import { message } from '@/lib/antdMessage';
import { useAuth } from '@/components/auth/AuthProvider';
import { usePermission } from '@/hooks/usePermission';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import { parseRecordId } from '@/lib/recordSelection';
import { usePublishPageAgentContext } from './PageAgentContext';

const statuses:EndpointStatus[]=['DRAFT','ACTIVE','DISABLED']; const methods=['GET','POST','PUT','PATCH','DELETE'];
const statusColors:Record<string,string>={DRAFT:'default',ACTIVE:'green',DISABLED:'orange',SUCCESS:'green',FAILED:'red'};
const EMPTY_STATS={total:0,active:0,disabled:0,successful:0,failed:0};
export default function ApiCenterPage(){
 const {t,i18n}=useTranslation();const {modal}=App.useApp();const [form]=Form.useForm<EndpointPayload>();
 const {user}=useAuth();
 const {hasPermission,permissionVersion}=usePermission('route:api-center');
 const canRead=hasPermission('route:api-center');
 const liveManage=hasPermission('integration:endpoint:manage');
 const liveExecute=hasPermission('integration:endpoint:execute');
 const scope=`${user?.id}:${user?.tenantId}:${permissionVersion}:${canRead}:${liveManage}:${liveExecute}`;
 const activeScope=useRef<string | undefined>(scope);activeScope.current=scope;
 const confirmation=useRef<{destroy:()=>void} | undefined>(undefined);
 const dismissConfirmation=useCallback(()=>{confirmation.current?.destroy();confirmation.current=undefined;},[]);
 const router=useRouter();const requestedId=useSearchParams().get('id');
 const [dataScope,setDataScope]=useState(scope);
 const [storedRecords,setRecords]=useState<IntegrationEndpoint[]>([]);const [storedStats,setStats]=useState(EMPTY_STATS);
 const [storedTotal,setTotal]=useState(0);const [loading,setLoading]=useState(true);const [saving,setSaving]=useState(false);
 const [serverCanManage,setCanManage]=useState(false);const [serverCanExecute,setCanExecute]=useState(false);
 const records=dataScope===scope?storedRecords:[];const stats=dataScope===scope?storedStats:EMPTY_STATS;
 const total=dataScope===scope?storedTotal:0;
 const canManage=canRead&&liveManage&&serverCanManage&&dataScope===scope;
 const canExecute=canRead&&liveExecute&&serverCanExecute&&dataScope===scope;
 const [keyword,setKeyword]=useState('');const [status,setStatus]=useState<EndpointStatus>();const [page,setPage]=useState(1);const [size,setSize]=useState(20);
 const [editor,setEditor]=useState(false);const [editorLoading,setEditorLoading]=useState(false);const [editing,setEditing]=useState<IntegrationEndpoint>();
 const [upstreams,setUpstreams]=useState<{code:string;label:string;available:boolean}[]>([]);
 const [storedDetail,setDetail]=useState<{endpoint:IntegrationEndpoint;invocations:IntegrationInvocation[]}>();const [detailOpen,setDetailOpen]=useState(false);const [detailLoading,setDetailLoading]=useState(false);
 const detail=dataScope===scope?storedDetail:undefined;
 const listRequest=useRef(0);const detailRequest=useRef(0);const editorRequest=useRef(0);const selectedId=useRef<number | undefined>(undefined);
 const selected=dataScope!==scope?undefined:editor?editing:detailOpen?detail?.endpoint:undefined;
 usePublishPageAgentContext({endpointId:selected?.id,version:selected?.version,keyword:keyword||undefined,status,page,size});

 useEffect(()=>{
  dismissConfirmation();
  activeScope.current=scope;listRequest.current+=1;detailRequest.current+=1;editorRequest.current+=1;selectedId.current=undefined;
  setRecords([]);setStats(EMPTY_STATS);setTotal(0);setCanManage(false);setCanExecute(false);
  setDetail(undefined);setDetailOpen(false);setDetailLoading(false);setEditing(undefined);setEditor(false);setEditorLoading(false);setSaving(false);setUpstreams([]);
  return ()=>{dismissConfirmation();activeScope.current=undefined;listRequest.current+=1;detailRequest.current+=1;editorRequest.current+=1;selectedId.current=undefined;};
 },[scope,dismissConfirmation]);
 const load=useCallback(async()=>{
  const request=++listRequest.current;if(!canRead){setLoading(false);return;}
  setLoading(true);
  try{const result=await integrationApi.list({keyword,status,page,size});
   if(request!==listRequest.current||activeScope.current!==scope)return;
   setDataScope(scope);setRecords(result.records);setStats(result.stats);setTotal(result.total);setCanManage(result.canManage);setCanExecute(result.canExecute);
  }catch(error){if(request===listRequest.current&&activeScope.current===scope){setRecords([]);setStats(EMPTY_STATS);setTotal(0);setCanManage(false);setCanExecute(false);message.error(formatOaApiError(error));}}
  finally{if(request===listRequest.current&&activeScope.current===scope)setLoading(false);}
 },[keyword,status,page,size,scope,canRead]);
 const latestLoad=useRef(load);useEffect(()=>{latestLoad.current=load;},[load]);
 useEffect(()=>{void load();},[load]);
 const loadDetail=useCallback(async(id:number)=>{
  dismissConfirmation();
  const request=++detailRequest.current;if(!canRead)return;
  selectedId.current=id;setDetail(undefined);setDetailOpen(true);setDetailLoading(true);
  try{const result=await integrationApi.detail(id);
   if(request!==detailRequest.current||activeScope.current!==scope)return;
   if(result.endpoint.id!==id)throw new Error('RESOURCE_MISMATCH');
   setDetail(result);
  }catch(error){if(request===detailRequest.current&&activeScope.current===scope){selectedId.current=undefined;setDetail(undefined);setDetailOpen(false);message.error(formatOaApiError(error));}}
  finally{if(request===detailRequest.current&&activeScope.current===scope)setDetailLoading(false);}
 },[scope,canRead,dismissConfirmation]);
 useEffect(()=>{
  detailRequest.current+=1;editorRequest.current+=1;selectedId.current=undefined;
  setDetail(undefined);setDetailOpen(false);setDetailLoading(false);setEditing(undefined);setEditor(false);setEditorLoading(false);setSaving(false);
  const id=parseRecordId(requestedId);if(id!==undefined&&canRead)void loadDetail(id);
  return ()=>{dismissConfirmation();detailRequest.current+=1;editorRequest.current+=1;selectedId.current=undefined;};
 },[requestedId,scope,canRead,loadDetail,dismissConfirmation]);
 const closeDetail=()=>{dismissConfirmation();detailRequest.current+=1;selectedId.current=undefined;setDetail(undefined);setDetailOpen(false);setDetailLoading(false);if(requestedId)router.replace('/oa/api-center');};
 const closeEditor=()=>{dismissConfirmation();editorRequest.current+=1;selectedId.current=undefined;setEditing(undefined);setEditor(false);setEditorLoading(false);setSaving(false);};
 const showDetail=(id:number)=>{if(!canRead)return;closeEditor();if(requestedId===String(id))void loadDetail(id);else router.push(`/oa/api-center?id=${id}`);};
 const openEditor=async(record?:IntegrationEndpoint)=>{
  if(!canManage||(record&&(!record.canManage||record.status==='ACTIVE')))return;
  dismissConfirmation();
  const request=++editorRequest.current;const identity=scope;detailRequest.current+=1;selectedId.current=undefined;
  setDetail(undefined);setDetailOpen(false);setDetailLoading(false);setEditing(undefined);form.resetFields();setEditor(true);setEditorLoading(true);
  try{const [options,fresh]=await Promise.all([integrationApi.options(),record?integrationApi.detail(record.id):Promise.resolve(undefined)]);
   if(request!==editorRequest.current||activeScope.current!==identity)return;
   const endpoint=fresh?.endpoint;
   if(record&&(!endpoint||endpoint.id!==record.id||!endpoint.canManage||endpoint.status==='ACTIVE'))throw new Error('RESOURCE_MISMATCH');
   setUpstreams(options.upstreams);setEditing(endpoint);selectedId.current=endpoint?.id;form.setFieldsValue(endpoint?{...endpoint}:{method:'GET'});
  }catch(error){if(request===editorRequest.current&&activeScope.current===identity){closeEditor();message.error(formatOaApiError(error));}}
  finally{if(request===editorRequest.current&&activeScope.current===identity)setEditorLoading(false);}
 };
 const openCreate=()=>openEditor();const openEdit=(endpoint:IntegrationEndpoint)=>openEditor(endpoint);
 const refreshAfterWrite=async(identity:string,id?:number,request?:number)=>{
  if(activeScope.current!==identity)return;
  await latestLoad.current();
  if(activeScope.current===identity&&id!==undefined&&selectedId.current===id&&request===detailRequest.current)await loadDetail(id);
 };
 const save=async()=>{
  if(!canManage||!editor||editorLoading||saving||(editing&&!editing.canManage))return;
  const request=editorRequest.current;const identity=scope;const target=editing;
  try{const value=await form.validateFields();if(request!==editorRequest.current||activeScope.current!==identity)return;
   setSaving(true);if(target)await integrationApi.update(target.id,{...value,version:target.version});else await integrationApi.create(value);
   if(activeScope.current!==identity)return;
   if(request===editorRequest.current){closeEditor();message.success(t('apiCenter.messages.saved'));}
   await refreshAfterWrite(identity);
  }catch(error){if(request===editorRequest.current&&activeScope.current===identity&&!(error&&typeof error==='object'&&'errorFields'in error))message.error(formatOaApiError(error));}
  finally{if(request===editorRequest.current&&activeScope.current===identity)setSaving(false);}
 };
 const confirmOperation=(endpoint:IntegrationEndpoint,target?:EndpointStatus)=>{
  if(target?(!canManage||!endpoint.canManage||!endpoint.allowedTransitions.includes(target)):(!canExecute||!endpoint.canExecute))return;
  dismissConfirmation();
  const identity=scope;const request=detailRequest.current;const editTicket=editorRequest.current;
  const isCurrent=()=>activeScope.current===identity&&request===detailRequest.current&&editTicket===editorRequest.current;
  confirmation.current=modal.confirm({title:t(target?'apiCenter.actions.confirmStatus':'apiCenter.actions.confirmExecute'),
   content:target?t(`apiCenter.status.${target}`):t('apiCenter.actions.executeHint',{name:endpoint.name}),
   okText:target?t('common.confirm'):t('apiCenter.actions.execute'),
   onOk:async()=>{
    if(!isCurrent())return;
    try{if(target){await integrationApi.status(endpoint.id,target,endpoint.version);if(isCurrent())message.success(t('apiCenter.messages.status'));}
     else{const result=await integrationApi.execute(endpoint.id,endpoint.version);if(isCurrent()){result.outcome==='SUCCESS'?message.success(t('apiCenter.messages.success')):message.error(t('apiCenter.messages.failed',{code:result.errorCode??'-'}));}}
     await refreshAfterWrite(identity,endpoint.id,request);
    }catch(error){if(!isCurrent())return;message.error(formatOaApiError(error));return Promise.reject(error);}
   }});
 };
 const changeStatus=(endpoint:IntegrationEndpoint,target:EndpointStatus)=>confirmOperation(endpoint,target);
 const execute=(endpoint:IntegrationEndpoint)=>confirmOperation(endpoint);
 const columns:ColumnsType<IntegrationEndpoint>=[
  {title:t('apiCenter.columns.endpoint'),key:'endpoint',render:(_,e)=><Space direction="vertical" size={0}><Button type="link" onClick={()=>void showDetail(e.id)}>{e.name}</Button><Typography.Text type="secondary">{e.code}</Typography.Text></Space>},
  {title:t('apiCenter.columns.method'),dataIndex:'method',render:v=><Tag color={v==='GET'?'blue':v==='DELETE'?'red':'gold'}>{v}</Tag>},
  {title:t('apiCenter.columns.path'),key:'path',render:(_,e)=><Typography.Text code>{e.upstreamCode}{e.relativePath}</Typography.Text>},
  {title:t('apiCenter.columns.status'),dataIndex:'status',render:v=><Badge status={v==='ACTIVE'?'success':v==='DISABLED'?'warning':'default'} text={t(`apiCenter.status.${v}`)}/>},
  {title:t('apiCenter.columns.updatedAt'),dataIndex:'updatedAt',render:v=>new Date(v).toLocaleString(i18n.language)},
  {title:t('common.actions'),key:'actions',fixed:'right',render:(_,e)=><Space wrap><Button size="small" onClick={()=>void showDetail(e.id)}>{t('common.view')}</Button>{canManage&&e.canManage&&e.status!=='ACTIVE'&&<Button size="small" onClick={()=>void openEdit(e)}>{t('common.edit')}</Button>}{canExecute&&e.canExecute&&<Button type="primary" size="small" icon={<OaIcon name="send"/>} onClick={()=>execute(e)}>{t('apiCenter.actions.execute')}</Button>}</Space>}
 ];
 return <section className="oa-api-center-page"><header className="oa-api-center-hero"><div className="oa-api-center-title"><span className="oa-api-center-pulse"><OaIcon name="api-center" size={26}/></span><div><Typography.Text className="oa-api-center-eyebrow">{t('apiCenter.eyebrow')}</Typography.Text><Typography.Title level={2}>{t('apiCenter.title')}</Typography.Title><Typography.Paragraph>{t('apiCenter.subtitle')}</Typography.Paragraph></div></div><Space direction="vertical" align="end"><Tag icon={<OaIcon name="lock"/>} color="blue">{t('apiCenter.gatewayBadge')}</Tag>{canManage&&<Button type="primary" onClick={()=>void openCreate()} icon={<OaIcon name="add"/>}>{t('apiCenter.actions.create')}</Button>}</Space></header>
 <Row gutter={[12,12]}>{(['total','active','successful','failed'] as const).map(key=><Col xs={12} lg={6} key={key}><Card><Statistic title={t(`apiCenter.stats.${key}`)} value={stats[key]} valueStyle={key==='failed'&&stats.failed?{color:'var(--oa-danger)'}:undefined}/></Card></Col>)}</Row>
 <Card className="oa-api-center-list"><div className="oa-api-center-toolbar"><Input.Search allowClear placeholder={t('apiCenter.filters.keyword')} onSearch={v=>{setKeyword(v);setPage(1)}}/><Select allowClear placeholder={t('apiCenter.filters.status')} options={statuses.map(v=>({value:v,label:t(`apiCenter.status.${v}`)}))} onChange={v=>{setStatus(v);setPage(1)}}/><Button onClick={()=>void load()}>{t('common.refresh')}</Button></div><ResponsiveTable rowKey="id" columns={columns} dataSource={records} loading={loading} pagination={{current:page,pageSize:size,total,showSizeChanger:true,onChange:(p,s)=>{setPage(p);setSize(s)}}}/></Card>
 <Drawer open={editor&&canRead&&dataScope===scope} onClose={closeEditor} loading={editorLoading} title={t(editing?'apiCenter.editor.edit':'apiCenter.editor.create')} width={600} extra={<Button type="primary" loading={saving} disabled={!canManage||editorLoading} onClick={()=>void save()}>{t('common.save')}</Button>}><Alert type="info" showIcon message={t('apiCenter.editor.securityTitle')} description={t('apiCenter.editor.securityHint')}/><Form form={form} layout="vertical" className="oa-api-center-form"><Row gutter={12}><Col span={12}><Form.Item name="code" label={t('apiCenter.fields.code')} rules={[{required:true}]}><Input disabled={!!editing}/></Form.Item></Col><Col span={12}><Form.Item name="name" label={t('apiCenter.fields.name')} rules={[{required:true}]}><Input/></Form.Item></Col></Row><Form.Item name="upstreamCode" label={t('apiCenter.fields.upstream')} rules={[{required:true}]}><Select options={upstreams.map(u=>({value:u.code,label:<Space>{u.label}<Tag color={u.available?'green':'default'}>{t(u.available?'apiCenter.upstream.available':'apiCenter.upstream.unavailable')}</Tag></Space>,disabled:!u.available}))}/></Form.Item><Row gutter={12}><Col span={7}><Form.Item name="method" label={t('apiCenter.fields.method')} rules={[{required:true}]}><Select options={methods.map(v=>({value:v,label:v}))}/></Form.Item></Col><Col span={17}><Form.Item name="relativePath" label={t('apiCenter.fields.path')} rules={[{required:true}]}><Input placeholder={t('apiCenter.editor.pathPlaceholder')}/></Form.Item></Col></Row><Form.Item noStyle shouldUpdate={(a,b)=>a.method!==b.method}>{({getFieldValue})=>!['GET','DELETE'].includes(getFieldValue('method'))&&<Form.Item name="requestTemplate" label={t('apiCenter.fields.template')}><Input.TextArea rows={8} className="oa-api-center-code" placeholder={t('apiCenter.editor.templatePlaceholder')}/></Form.Item>}</Form.Item><Form.Item name="description" label={t('apiCenter.fields.description')}><Input.TextArea rows={3}/></Form.Item></Form></Drawer>
 <Drawer open={detailOpen&&canRead&&dataScope===scope} onClose={closeDetail} loading={detailLoading} title={t('apiCenter.detail.title')} width={720}>{detail?<><Descriptions column={2} items={[{key:'code',label:t('apiCenter.fields.code'),children:detail.endpoint.code},{key:'upstream',label:t('apiCenter.fields.upstream'),children:detail.endpoint.upstreamCode},{key:'method',label:t('apiCenter.fields.method'),children:<Tag>{detail.endpoint.method}</Tag>},{key:'path',label:t('apiCenter.fields.path'),children:<Typography.Text code>{detail.endpoint.relativePath}</Typography.Text>}]}/><Space wrap className="oa-api-center-status-actions">{canManage&&detail.endpoint.canManage&&detail.endpoint.allowedTransitions.map(s=><Button key={s} onClick={()=>changeStatus(detail.endpoint,s)}>{t(`apiCenter.status.${s}`)}</Button>)}{canExecute&&detail.endpoint.canExecute&&<Button type="primary" onClick={()=>execute(detail.endpoint)}>{t('apiCenter.actions.execute')}</Button>}</Space><Typography.Title level={5}>{t('apiCenter.detail.history')}</Typography.Title>{detail.invocations.length?<Timeline items={detail.invocations.map(x=>({color:x.outcome==='SUCCESS'?'green':'red',children:<div className="oa-api-center-run"><Space><Tag color={statusColors[x.outcome]}>{t(`apiCenter.outcome.${x.outcome}`)}</Tag>{x.httpStatus&&<Tag>HTTP {x.httpStatus}</Tag>}<Typography.Text type="secondary">{x.durationMs} ms</Typography.Text></Space><div>{x.operatorLabel} · {new Date(x.createdAt).toLocaleString(i18n.language)}</div>{x.errorCode&&<Alert type="error" message={t(`apiCenter.errors.${x.errorCode}`,{defaultValue:x.errorCode})}/>} {x.responsePreview&&<Typography.Paragraph><pre>{x.responsePreview}</pre></Typography.Paragraph>}</div>}))}/>:<Empty description={t('apiCenter.detail.empty')}/>}</>:<Empty/>}</Drawer>
 </section>;
}

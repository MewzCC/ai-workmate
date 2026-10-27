'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { App, Button, Card, Col, Descriptions, Drawer, Empty, Form, Input, InputNumber, Progress, Row, Select, Space, Statistic, Tag, Timeline, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
import { OaIcon } from '@/components/OaIcon';
import ResponsiveTable from './ResponsiveTable';
import { message } from '@/lib/antdMessage';
import { formatOaApiError } from '@/lib/oaApi';
import { budgetApi, type Budget, type BudgetOperation, type BudgetPayload, type BudgetStatus, type BudgetTransaction } from '@/lib/budgetApi';
import { useAuth } from '@/components/auth/AuthProvider';
import { usePermission } from '@/hooks/usePermission';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import { usePublishPageAgentContext } from './PageAgentContext';
import { parseRecordId } from '@/lib/recordSelection';

const statuses:BudgetStatus[]=['DRAFT','ACTIVE','CLOSED','CANCELLED'];
const operations:BudgetOperation[]=['OCCUPY','RELEASE','SPEND'];
const colors:Record<string,string>={DRAFT:'default',ACTIVE:'green',CLOSED:'blue',CANCELLED:'red',NORMAL:'green',WARNING:'gold',FULL:'red'};
const EMPTY_STATS = {totalPlans:0,activePlans:0,warningPlans:0,totalAmount:0,occupiedAmount:0,spentAmount:0,availableAmount:0};

export default function BudgetPage(){
 const {t,i18n}=useTranslation(); const {modal}=App.useApp(); const [form]=Form.useForm<BudgetPayload>(); const [operationForm]=Form.useForm();
 const {user}=useAuth(); const {allowed:liveManage,permissionVersion}=usePermission('budget:manage');
 const router=useRouter(); const requestedId=useSearchParams().get('id');
 const scope=`${user?.id}:${user?.tenantId}:${permissionVersion}:${liveManage}`;
 const [dataScope,setDataScope]=useState(scope);
 const [storedRecords,setRecords]=useState<Budget[]>([]); const [storedStats,setStats]=useState(EMPTY_STATS);
 const records=dataScope===scope?storedRecords:[]; const stats=dataScope===scope?storedStats:EMPTY_STATS;
 const [total,setTotal]=useState(0); const [serverCanManage,setCanManage]=useState(false); const [loading,setLoading]=useState(true); const [saving,setSaving]=useState(false);
 const canManage=liveManage&&serverCanManage&&dataScope===scope;
 const [keyword,setKeyword]=useState(''); const [status,setStatus]=useState<BudgetStatus>(); const [year,setYear]=useState<number>(); const [page,setPage]=useState(1); const [size,setSize]=useState(20);
 const [editor,setEditor]=useState(false); const [editing,setEditing]=useState<Budget>(); const [detail,setDetail]=useState<{budget:Budget;transactions:BudgetTransaction[]}>(); const [detailOpen,setDetailOpen]=useState(false);
 const [owners,setOwners]=useState<{id:number;label:string;detail?:string}[]>([]); const [operation,setOperation]=useState<{budget:Budget;type:BudgetOperation}>();
 const listRequest=useRef(0); const detailRequest=useRef(0); const editorRequest=useRef(0); const selectedId=useRef<number|undefined>(undefined);
 const [detailLoading,setDetailLoading]=useState(false);
 const selected=dataScope!==scope?undefined:editor?editing:operation?.budget??(detailOpen?detail?.budget:undefined);
 usePublishPageAgentContext({budgetId:selected?.id,version:selected?.version,keyword:keyword||undefined,status,fiscalYear:year,page,size});
 useEffect(()=>{
  listRequest.current+=1;detailRequest.current+=1;editorRequest.current+=1;selectedId.current=undefined;
  setRecords([]);setStats(EMPTY_STATS);setTotal(0);setCanManage(false);setDetail(undefined);setDetailOpen(false);setDetailLoading(false);
  setEditor(false);setEditing(undefined);setOperation(undefined);setSaving(false);
  return()=>{listRequest.current+=1;detailRequest.current+=1;editorRequest.current+=1;selectedId.current=undefined};
 },[scope]);
 const load=useCallback(async()=>{const request=++listRequest.current;setLoading(true);try{const r=await budgetApi.list({keyword,status,fiscalYear:year,page,size});if(request!==listRequest.current)return;setDataScope(scope);setRecords(r.records);setStats(r.stats);setTotal(r.total);setCanManage(r.canManage)}catch(e){if(request===listRequest.current){setRecords([]);setCanManage(false);message.error(formatOaApiError(e))}}finally{if(request===listRequest.current)setLoading(false)}},[keyword,status,year,page,size,scope]);
 useEffect(()=>{void load()},[load]);
 const loadDetail=useCallback(async(id:number)=>{
  const request=++detailRequest.current;selectedId.current=id;setDetail(undefined);setDetailOpen(true);setDetailLoading(true);
  try{const result=await budgetApi.detail(id);if(request!==detailRequest.current)return;
   if(result.budget.id!==id)throw new Error('RESOURCE_MISMATCH');setDetail(result);
  }catch(e){if(request===detailRequest.current){selectedId.current=undefined;setDetail(undefined);setDetailOpen(false);message.error(formatOaApiError(e))}}
  finally{if(request===detailRequest.current)setDetailLoading(false)}
 },[]);
 useEffect(()=>{
  detailRequest.current+=1;selectedId.current=undefined;setDetail(undefined);setDetailOpen(false);
  editorRequest.current+=1;setEditor(false);setEditing(undefined);setOperation(undefined);setSaving(false);
  const id=parseRecordId(requestedId);if(id!==undefined)void loadDetail(id);
  return()=>{detailRequest.current+=1;selectedId.current=undefined};
 },[requestedId,scope,loadDetail]);
 const closeDetail=()=>{detailRequest.current+=1;selectedId.current=undefined;setDetail(undefined);setDetailOpen(false);setDetailLoading(false);if(requestedId)router.replace('/oa/budget')};
 const money=(v:number,c='CNY')=>new Intl.NumberFormat(i18n.language,{style:'currency',currency:c,maximumFractionDigits:2}).format(v||0);
 const openCreate=async()=>{if(!canManage)return;const request=++editorRequest.current;try{const o=await budgetApi.options();if(request!==editorRequest.current)return;setOwners(o.owners);setEditing(undefined);setOperation(undefined);form.resetFields();form.setFieldsValue({fiscalYear:new Date().getFullYear(),currency:'CNY',warningThreshold:80});setEditor(true)}catch(e){if(request===editorRequest.current)message.error(formatOaApiError(e))}};
 const openEdit=async(b:Budget)=>{if(!canManage||!b.canManage)return;const request=++editorRequest.current;try{const o=await budgetApi.options();if(request!==editorRequest.current)return;setOwners(o.owners);setEditing(b);setOperation(undefined);form.setFieldsValue({...b});setEditor(true)}catch(e){if(request===editorRequest.current)message.error(formatOaApiError(e))}};
 const closeEditor=()=>{editorRequest.current+=1;setEditor(false);setEditing(undefined);setSaving(false)};
 const save=async()=>{
  if(!canManage)return;
  const request=editorRequest.current;const target=editing;const selectedRequest=detailRequest.current;
  try{
   const values=await form.validateFields();if(request!==editorRequest.current)return;setSaving(true);
   if(target)await budgetApi.update(target.id,{...values,version:target.version});else await budgetApi.create(values);
   if(request!==editorRequest.current)return;
   message.success(t('budget.messages.saved'));closeEditor();await load();
   if(target&&selectedId.current===target.id&&selectedRequest===detailRequest.current)await loadDetail(target.id);
  }catch(error){if(request===editorRequest.current&&error instanceof Error)message.error(formatOaApiError(error))}
  finally{if(request===editorRequest.current)setSaving(false)}
 };
 const showDetail=(id:number)=>{if(requestedId===String(id))void loadDetail(id);else router.push(`/oa/budget?id=${id}`)};
 const changeStatus=(b:Budget,target:BudgetStatus)=>{if(!canManage||!b.canManage)return;const request=detailRequest.current;return modal.confirm({title:t('budget.actions.confirmStatus'),content:t(`budget.status.${target}`),onOk:async()=>{if(request!==detailRequest.current)return;try{await budgetApi.status(b.id,target,b.version);if(request!==detailRequest.current)return;message.success(t('budget.messages.status'));await load();if(selectedId.current===b.id&&request===detailRequest.current)await loadDetail(b.id)}catch(e){if(request===detailRequest.current)message.error(formatOaApiError(e))}}})};
 const closeOperation=()=>{editorRequest.current+=1;setOperation(undefined);setSaving(false)};
 const submitOperation=async()=>{if(!canManage||!operation)return;const target=operation;const request=editorRequest.current;const selectedRequest=detailRequest.current;try{const v=await operationForm.validateFields();if(request!==editorRequest.current)return;setSaving(true);await budgetApi.operate(target.budget.id,target.type,v.amount,target.budget.version,v.referenceCode,v.note);if(request!==editorRequest.current)return;message.success(t('budget.messages.operation'));closeOperation();operationForm.resetFields();await load();if(selectedId.current===target.budget.id&&selectedRequest===detailRequest.current)await loadDetail(target.budget.id)}catch(e){if(request===editorRequest.current&&e instanceof Error)message.error(formatOaApiError(e))}finally{if(request===editorRequest.current)setSaving(false)}};
 const columns:ColumnsType<Budget>=[
  {title:t('budget.columns.budget'),key:'budget',render:(_,b)=><Space direction="vertical" size={0}><Button type="link" onClick={()=>void showDetail(b.id)}>{b.name}</Button><Typography.Text type="secondary">{b.code} · {b.fiscalYear}</Typography.Text></Space>},
  {title:t('budget.columns.owner'),dataIndex:'ownerLabel'},
  {title:t('budget.columns.total'),render:(_,b)=>money(b.totalAmount,b.currency)},
  {title:t('budget.columns.execution'),key:'usage',render:(_,b)=><div className="oa-budget-progress"><Progress percent={b.utilizationPercent} size="small" status={b.alertLevel==='FULL'?'exception':'normal'}/><small>{t('budget.usage', {occupied:money(b.occupiedAmount,b.currency),spent:money(b.spentAmount,b.currency)})}</small></div>},
  {title:t('budget.columns.available'),render:(_,b)=>money(b.availableAmount,b.currency)},
  {title:t('budget.columns.status'),render:(_,b)=><Space><Tag color={colors[b.status]}>{t(`budget.status.${b.status}`)}</Tag><Tag color={colors[b.alertLevel]}>{t(`budget.alert.${b.alertLevel}`)}</Tag></Space>},
  {title:t('common.actions'),key:'actions',fixed:'right',render:(_,b)=><Space wrap><Button size="small" onClick={()=>void showDetail(b.id)}>{t('common.view')}</Button>{canManage&&b.canManage&&['DRAFT','ACTIVE'].includes(b.status)&&<Button size="small" onClick={()=>void openEdit(b)}>{t('common.edit')}</Button>}{canManage&&b.canManage&&b.status==='ACTIVE'&&operations.map(type=><Button size="small" key={type} onClick={()=>{closeEditor();setOperation({budget:b,type});operationForm.resetFields()}}>{t(`budget.operation.${type}`)}</Button>)}</Space>}
 ];
 return <section className="oa-budget-page">
  <header className="oa-budget-hero"><div><Typography.Text className="oa-budget-eyebrow">{t('budget.eyebrow')}</Typography.Text><Typography.Title level={2}>{t('budget.title')}</Typography.Title><Typography.Paragraph>{t('budget.subtitle')}</Typography.Paragraph></div>{canManage&&<Button type="primary" icon={<OaIcon name="add"/>} onClick={()=>void openCreate()}>{t('budget.actions.create')}</Button>}</header>
  <Row gutter={[12,12]}>{[['totalPlans',stats.totalPlans],['activePlans',stats.activePlans],['warningPlans',stats.warningPlans]].map(([k,v])=><Col xs={12} lg={4} key={String(k)}><Card><Statistic title={t(`budget.stats.${k}`)} value={v}/></Card></Col>)}{[['totalAmount',stats.totalAmount],['occupiedAmount',stats.occupiedAmount],['availableAmount',stats.availableAmount]].map(([k,v])=><Col xs={12} lg={4} key={String(k)}><Card><Statistic title={t(`budget.stats.${k}`)} value={money(Number(v))}/></Card></Col>)}</Row>
  <Card className="oa-budget-list-card"><div className="oa-budget-toolbar"><Input.Search allowClear placeholder={t('budget.filters.keyword')} onSearch={v=>{setKeyword(v);setPage(1)}}/><Select allowClear placeholder={t('budget.filters.status')} options={statuses.map(v=>({value:v,label:t(`budget.status.${v}`)}))} onChange={v=>{setStatus(v);setPage(1)}}/><InputNumber placeholder={t('budget.filters.year')} min={2000} max={2200} onChange={v=>{setYear(v??undefined);setPage(1)}}/><Button onClick={()=>void load()}>{t('common.refresh')}</Button></div><ResponsiveTable rowKey="id" columns={columns} dataSource={records} loading={loading} pagination={{current:page,pageSize:size,total,showSizeChanger:true,onChange:(p,s)=>{setPage(p);setSize(s)}}}/></Card>
  <Drawer open={editor&&dataScope===scope} onClose={closeEditor} title={t(editing?'budget.editor.edit':'budget.editor.create')} width={560} extra={<Button type="primary" loading={saving} disabled={!canManage} onClick={()=>void save()}>{t('common.save')}</Button>}><Form disabled={!canManage} form={form} layout="vertical"><Row gutter={12}><Col span={12}><Form.Item name="code" label={t('budget.fields.code')} rules={[{required:true}]}><Input disabled={!!editing}/></Form.Item></Col><Col span={12}><Form.Item name="fiscalYear" label={t('budget.fields.year')} rules={[{required:true}]}><InputNumber min={2000} max={2200} style={{width:'100%'}}/></Form.Item></Col></Row><Form.Item name="name" label={t('budget.fields.name')} rules={[{required:true}]}><Input/></Form.Item><Form.Item name="ownerUserId" label={t('budget.fields.owner')} rules={[{required:true}]}><Select showSearch optionFilterProp="label" options={owners.map(o=>({value:o.id,label:o.label}))}/></Form.Item><Row gutter={12}><Col span={12}><Form.Item name="totalAmount" label={t('budget.fields.amount')} rules={[{required:true}]}><InputNumber min={0.01} precision={2} style={{width:'100%'}}/></Form.Item></Col><Col span={12}><Form.Item name="currency" label={t('budget.fields.currency')} rules={[{required:true}]}><Select options={['CNY','USD','EUR','HKD'].map(value=>({value,label:value}))}/></Form.Item></Col></Row><Form.Item name="warningThreshold" label={t('budget.fields.threshold')} rules={[{required:true}]}><InputNumber min={1} max={100} addonAfter="%" style={{width:'100%'}}/></Form.Item><Form.Item name="summary" label={t('budget.fields.summary')}><Input.TextArea rows={4}/></Form.Item></Form></Drawer>
  <Drawer open={detailOpen&&dataScope===scope} loading={detailLoading} onClose={closeDetail} title={t('budget.detail.title')} width={680}>{detail?<><Descriptions column={2} items={[{key:'code',label:t('budget.fields.code'),children:detail.budget.code},{key:'owner',label:t('budget.fields.owner'),children:detail.budget.ownerLabel},{key:'total',label:t('budget.columns.total'),children:money(detail.budget.totalAmount,detail.budget.currency)},{key:'available',label:t('budget.columns.available'),children:money(detail.budget.availableAmount,detail.budget.currency)}]}/><Progress percent={detail.budget.utilizationPercent}/>{canManage&&detail.budget.allowedTransitions.length>0&&<Space wrap className="oa-budget-status-actions">{detail.budget.allowedTransitions.map(s=><Button key={s} onClick={()=>changeStatus(detail.budget,s)}>{t(`budget.status.${s}`)}</Button>)}</Space>}<Typography.Title level={5}>{t('budget.detail.timeline')}</Typography.Title>{detail.transactions.length?<Timeline items={detail.transactions.map(x=>({children:<div><strong>{t(`budget.transaction.${x.type}`,{defaultValue:x.type})}</strong>{x.amount!=null&&<Tag>{money(x.amount,detail.budget.currency)}</Tag>}<div>{x.operatorLabel} · {new Date(x.createdAt).toLocaleString(i18n.language)}</div>{x.note&&<Typography.Text type="secondary">{x.note}</Typography.Text>}</div>}))}/>:<Empty/>}</>:<Empty/>}</Drawer>
  <Drawer open={!!operation&&dataScope===scope} onClose={closeOperation} title={operation?t(`budget.operation.${operation.type}`):''} width={440} extra={<Button type="primary" loading={saving} disabled={!canManage} onClick={()=>void submitOperation()}>{t('common.confirm')}</Button>}><Form disabled={!canManage} form={operationForm} layout="vertical"><Form.Item name="amount" label={t('budget.fields.operationAmount')} rules={[{required:true}]}><InputNumber min={0.01} precision={2} style={{width:'100%'}}/></Form.Item><Form.Item name="referenceCode" label={t('budget.fields.reference')}><Input/></Form.Item><Form.Item name="note" label={t('budget.fields.note')}><Input.TextArea rows={4}/></Form.Item></Form></Drawer>
 </section>;
}

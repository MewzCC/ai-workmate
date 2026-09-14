'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { App, Button, Card, Descriptions, Drawer, Empty, Form, Input, Segmented, Select, Space, Spin, Switch, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { OaIcon } from '@/components/OaIcon';
import ResponsiveTable from '@/components/oa/ResponsiveTable';
import { message } from '@/lib/antdMessage';
import { formatOaApiError } from '@/lib/oaApi';
import { dataPermissionApi, type DataPermissionOverview, type DataPermissionPolicy, type DataPermissionPolicyPayload, type DataPermissionPreview, type DataScopeType } from '@/lib/dataPermissionApi';

const scopeTypes:DataScopeType[]=['SELF','DEPARTMENT','DEPARTMENT_AND_CHILDREN','CUSTOM_DEPARTMENTS','ALL'];

export default function DataPermissionPage(){
  const {t}=useTranslation(); const {modal}=App.useApp(); const [form]=Form.useForm<DataPermissionPolicyPayload>();
  const [data,setData]=useState<DataPermissionOverview>(); const [loading,setLoading]=useState(true); const [saving,setSaving]=useState(false);
  const [mode,setMode]=useState<'roles'|'users'>('roles'); const [editor,setEditor]=useState<DataPermissionPolicy|null>(); const [drawerOpen,setDrawerOpen]=useState(false);
  const [preview,setPreview]=useState<DataPermissionPreview>(); const [previewOpen,setPreviewOpen]=useState(false); const [previewLoading,setPreviewLoading]=useState(false);
  const watchedScope=Form.useWatch('scopeType',form);
  const load=useCallback(async()=>{setLoading(true);try{setData(await dataPermissionApi.overview());}catch(error){message.error(formatOaApiError(error));}finally{setLoading(false);}},[]);
  useEffect(()=>{void load();},[load]);
  const departmentMap=useMemo(()=>new Map(data?.departments.map(d=>[d.id,d.name])??[]),[data]);
  const policyOptions=(data?.policies??[]).filter(p=>p.enabled).map(p=>({value:p.id,label:`${p.name} · ${t(`dataPermission.scope.${p.scopeType}`)}`}));
  const openEditor=(policy?:DataPermissionPolicy)=>{setEditor(policy??null);form.resetFields();form.setFieldsValue(policy?{...policy}:{scopeType:'SELF',departmentIds:[],enabled:true});setDrawerOpen(true);};
  const save=async()=>{setSaving(true);try{const values=await form.validateFields();if(values.scopeType!=='CUSTOM_DEPARTMENTS') values.departmentIds=[]; if(editor) await dataPermissionApi.update(editor.id,{...values,version:editor.version});else await dataPermissionApi.create(values);message.success(t('dataPermission.saveSuccess'));setDrawerOpen(false);await load();}catch(error){message.error(formatOaApiError(error));}finally{setSaving(false);}};
  const remove=(policy:DataPermissionPolicy)=>modal.confirm({title:t('dataPermission.deleteTitle'),content:t('dataPermission.deleteContent'),okText:t('common.delete'),cancelText:t('common.cancel'),okButtonProps:{danger:true},onOk:async()=>{try{await dataPermissionApi.remove(policy.id,policy.version);message.success(t('dataPermission.deleteSuccess'));await load();}catch(error){message.error(formatOaApiError(error));}}});
  const bindRole=async(roleCode:string,policyId:number)=>{try{await dataPermissionApi.bindRole(roleCode,policyId);message.success(t('dataPermission.bindSuccess'));await load();}catch(error){message.error(formatOaApiError(error));}};
  const bindUser=async(userId:number,policyId?:number)=>{try{if(policyId)await dataPermissionApi.bindUser(userId,policyId);else await dataPermissionApi.clearUser(userId);message.success(t('dataPermission.bindSuccess'));await load();}catch(error){message.error(formatOaApiError(error));}};
  const showPreview=async(userId:number)=>{setPreviewOpen(true);setPreview(undefined);setPreviewLoading(true);try{setPreview(await dataPermissionApi.preview(userId));}catch(error){message.error(formatOaApiError(error));}finally{setPreviewLoading(false);}};
  const roleColumns:ColumnsType<DataPermissionOverview['roles'][number]>=[
    {title:t('dataPermission.role'),dataIndex:'name',render:(name:string,row)=><div><strong>{name}</strong><Typography.Text type="secondary" className="oa-data-permission-code">{row.code}</Typography.Text></div>},
    {title:t('dataPermission.roleDescription'),dataIndex:'description',ellipsis:true},
    {title:t('dataPermission.effectivePolicy'),key:'policy',width:330,render:(_,row)=>{const value=data?.roleBindings.find(b=>b.roleCode===row.code)?.policyId;return <Select value={value} options={policyOptions} style={{width:'100%'}} onChange={id=>void bindRole(row.code,id)} />;}},
  ];
  const userColumns:ColumnsType<DataPermissionOverview['users'][number]>=[
    {title:t('dataPermission.user'),dataIndex:'name',render:(name:string,row)=><div><strong>{name}</strong><Typography.Text type="secondary" className="oa-data-permission-code">{row.email}</Typography.Text></div>},
    {title:t('dataPermission.department'),dataIndex:'departmentId',render:(id?:number)=>id?departmentMap.get(id)||'-':'-'},
    {title:t('dataPermission.exceptionPolicy'),key:'exception',width:330,render:(_,row)=>{const value=data?.userExceptions.find(item=>item.userId===row.id)?.policyId;return <Select allowClear value={value} placeholder={t('dataPermission.inheritRole')} options={policyOptions} style={{width:'100%'}} onChange={id=>void bindUser(row.id,id)} />;}},
    {title:t('dataPermission.actions'),key:'actions',width:110,fixed:'right',render:(_,row)=><Button type="link" onClick={()=>void showPreview(row.id)}>{t('dataPermission.preview')}</Button>},
  ];
  if(loading&&!data)return <div className="oa-data-permission-loading"><Spin size="large"/></div>;
  return <section className="oa-data-permission-page">
    <header className="oa-data-permission-hero"><div className="oa-data-permission-heading"><span className="oa-data-permission-icon"><OaIcon name="data-permission" size={24}/></span><div><Typography.Text className="oa-data-permission-eyebrow">{t('dataPermission.eyebrow')}</Typography.Text><Typography.Title level={3}>{t('dataPermission.title')}</Typography.Title><Typography.Paragraph>{t('dataPermission.description')}</Typography.Paragraph></div></div><Space><Button icon={<OaIcon name="reload"/>} onClick={()=>void load()}>{t('dataPermission.refresh')}</Button><Button type="primary" icon={<OaIcon name="add"/>} onClick={()=>openEditor()}>{t('dataPermission.newPolicy')}</Button></Space></header>
    <div className="oa-data-permission-stats">{[['policyCount',data?.policies.length??0],['boundRoles',data?.roleBindings.length??0],['exceptions',data?.userExceptions.length??0]].map(([key,value])=><div key={key}><small>{t(`dataPermission.${key}`)}</small><strong>{value}</strong></div>)}<Tag color="green">{t('dataPermission.effectiveImmediately')}</Tag></div>
    <div className="oa-data-permission-workspace"><Card title={t('dataPermission.policies')} className="oa-data-permission-policies" extra={<Tag>{data?.policies.length??0}</Tag>}>
      <div className="oa-data-permission-policy-list">{data?.policies.length?data.policies.map(policy=><article key={policy.id} className="oa-data-permission-policy"><div><strong>{policy.name}</strong><Tag color={policy.enabled?'blue':'default'}>{t(policy.enabled?'dataPermission.enabled':'dataPermission.disabled')}</Tag></div><Typography.Paragraph ellipsis={{rows:2}}>{policy.description||t(`dataPermission.scopeHint.${policy.scopeType}`)}</Typography.Paragraph><div><Tag>{t(`dataPermission.scope.${policy.scopeType}`)}</Tag><Space size={0}><Button type="link" size="small" onClick={()=>openEditor(policy)}>{t('common.edit')}</Button><Button type="link" size="small" danger onClick={()=>remove(policy)}>{t('common.delete')}</Button></Space></div></article>):<Empty description={t('dataPermission.noPolicy')}/>}</div>
    </Card><Card className="oa-data-permission-bindings" title={<Segmented value={mode} onChange={value=>setMode(value as 'roles'|'users')} options={[{value:'roles',label:t('dataPermission.roleBindings')},{value:'users',label:t('dataPermission.userExceptions')}]} />}>
      {mode === 'roles'
        ? <ResponsiveTable rowKey="code" columns={roleColumns} dataSource={data?.roles} loading={loading} pagination={false} />
        : <ResponsiveTable rowKey="id" columns={userColumns} dataSource={data?.users} loading={loading} pagination={false} />}
    </Card></div>
    <Drawer open={drawerOpen} width={520} title={t(editor?'dataPermission.editTitle':'dataPermission.createTitle')} onClose={()=>setDrawerOpen(false)} extra={<Button type="primary" loading={saving} onClick={()=>void save()}>{t('dataPermission.save')}</Button>}><Form form={form} layout="vertical"><Form.Item name="name" label={t('dataPermission.policyName')} rules={[{required:true,message:t('dataPermission.required')}]}><Input maxLength={80}/></Form.Item><Form.Item name="description" label={t('dataPermission.policyDescription')}><Input.TextArea rows={3} maxLength={255} showCount/></Form.Item><Form.Item name="scopeType" label={t('dataPermission.scopeType')} rules={[{required:true}]}><Select options={scopeTypes.map(value=>({value,label:t(`dataPermission.scope.${value}`)}))}/></Form.Item>{watchedScope==='CUSTOM_DEPARTMENTS'&&<Form.Item name="departmentIds" label={t('dataPermission.departments')} rules={[{required:true,type:'array',min:1,message:t('dataPermission.customRequired')}]}><Select mode="multiple" options={data?.departments.map(d=>({value:d.id,label:d.name}))}/></Form.Item>}<Form.Item name="enabled" label={t('dataPermission.status')} valuePropName="checked"><Switch checkedChildren={t('dataPermission.enabled')} unCheckedChildren={t('dataPermission.disabled')}/></Form.Item></Form></Drawer>
    <Drawer open={previewOpen} width={560} title={t('dataPermission.previewTitle')} onClose={()=>setPreviewOpen(false)}>{previewLoading?<Spin/>:preview&&<><Descriptions column={1} bordered><Descriptions.Item label={t('dataPermission.previewUser')}>{data?.users.find(u=>u.id===preview.userId)?.name}</Descriptions.Item><Descriptions.Item label={t('dataPermission.source')}>{t(preview.source==='USER_EXCEPTION'?'dataPermission.sourceException':'dataPermission.sourceRole')}</Descriptions.Item><Descriptions.Item label={t('dataPermission.scopeType')}><Space wrap>{preview.scopeTypes.map(scope=><Tag color="blue" key={scope}>{t(`dataPermission.scope.${scope}`)}</Tag>)}</Space></Descriptions.Item><Descriptions.Item label={t('dataPermission.visibleDepartments')}><Space wrap>{preview.departmentIds.map(id=><Tag key={id}>{departmentMap.get(id)||`#${id}`}</Tag>)}</Space></Descriptions.Item></Descriptions><Card className="oa-data-permission-preview-card"><Typography.Title level={4}>{t('dataPermission.visibleUserCount',{count:preview.visibleUserIds.length})}</Typography.Title><Space wrap>{preview.visibleUserIds.map(id=><Tag key={id}>{data?.users.find(u=>u.id===id)?.name||`#${id}`}</Tag>)}</Space></Card></>}</Drawer>
  </section>;
}

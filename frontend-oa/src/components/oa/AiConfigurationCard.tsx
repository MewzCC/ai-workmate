import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Form, Input, Select, Space, Tag, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/components/auth/AuthProvider';
import { aiConfigurationApi, type AiConfiguration, type AiConfiguredModel } from '@/lib/aiConfigurationApi';
import { message } from '@/lib/antdMessage';
import AiModelTable from './AiModelTable';

export default function AiConfigurationCard() {
 const { user } = useAuth();
 const allowed = Boolean(user?.roles.includes('SUPER_ADMIN') && user.permissions.includes('access:manage'));
 return allowed ? <Editor key={`${user?.tenantId}:${user?.id}:${user?.permissionVersion}`} /> : null;
}
function Editor() {
 const { t } = useTranslation();
 const [form] = Form.useForm<{ baseUrl: string; model: string; apiKey?: string }>();
 const [config, setConfig] = useState<AiConfiguration>();
 const [rows, setRows] = useState<AiConfiguredModel[]>([]);
 const [selected, setSelected] = useState<string[]>([]);
 const [busy, setBusy] = useState('');
 const [result, setResult] = useState<{ type: 'error' | 'success'; text: string }>();
 const active = useRef(true);
 useEffect(() => {
  active.current = true;
  aiConfigurationApi.get().then(value => {
   if (!active.current) return;
   setConfig(value);
   const models = value.models ?? [{ id: value.model, contextWindow: 32768, maxOutputTokens: 4096, supportsImages: false }];
   setRows(models.filter(row => row.id)); setSelected(models.map(row => row.id).filter(Boolean));
   form.setFieldsValue({ baseUrl: value.baseUrl, model: value.model, apiKey: '' });
  }).catch(() => { if (active.current) setResult({ type: 'error', text: t('aiConfiguration.loadFailed') }); });
  return () => { active.current = false; form.resetFields(); };
 }, [form, t]);
 const fail = (error: unknown) => { if (active.current) setResult({ type: 'error', text: error instanceof Error ? error.message : t('aiConfiguration.saveFailed') }); };
 const choose = (ids: string[]) => {
  setSelected(ids);
  if (!ids.includes(form.getFieldValue('model'))) form.setFieldValue('model', ids[0]);
 };
 const discover = async () => {
  try {
   const values = await form.validateFields(['baseUrl', 'apiKey']);
   setBusy('discover'); setResult(undefined);
   const fetched = await aiConfigurationApi.discover(values);
   if (!active.current) return;
   setRows(previous => {
    const merged = new Map(fetched.map(row => [row.id, row]));
    previous.forEach(row => merged.set(row.id, row));
    return [...merged.values()].sort((a, b) => a.id.localeCompare(b.id));
   });
   setResult({ type: 'success', text: t('aiConfiguration.fetched', { count: fetched.length }) });
  } catch (error) { if (error instanceof Error) fail(error); }
  finally { if (active.current) setBusy(''); }
 };
 const test = async (model?: string) => {
  try {
   const values = await form.validateFields(model ? ['baseUrl', 'apiKey'] : ['baseUrl', 'apiKey', 'model']);
   const target = model ?? values.model;
   setBusy(`test:${target}`); setResult(undefined);
   await aiConfigurationApi.test({ ...values, model: target });
   if (active.current) setResult({ type: 'success', text: t('aiConfiguration.testPassedFor', { model: target }) });
  } catch (error) { if (error instanceof Error) fail(error); }
  finally { if (active.current) setBusy(''); }
 };
 const save = async () => {
  try {
   const values = await form.validateFields();
   if (!config) return;
   const models = rows.filter(row => selected.includes(row.id));
   if (!models.length || models.length > 200 || models.some(row => row.maxOutputTokens >= row.contextWindow)) {
    setResult({ type: 'error', text: t('aiConfiguration.invalidModels') }); return;
   }
   setBusy('save'); setResult(undefined);
   const updated = await aiConfigurationApi.save({ ...values, version: config.version, models });
   if (!active.current) return;
   setConfig(updated); form.setFieldValue('apiKey', ''); message.success(t('aiConfiguration.saved'));
  } catch (error) { if (error instanceof Error) fail(error); }
  finally { if (active.current) setBusy(''); }
 };
 return <Card className="oa-domain-card oa-provider-card" title={t('aiConfiguration.title')} extra={<Tag>{t('aiConfiguration.protocol')}</Tag>} loading={!config && !result}>
  <div className="oa-provider-content">
   <Alert type="info" showIcon title={t('aiConfiguration.hint')} />
   {result && <Alert type={result.type} showIcon title={result.text} />}
   <Form name="ai-provider" form={form} layout="vertical" disabled={Boolean(busy) || !config} autoComplete="off">
    <div className="oa-provider-fields">
     <Form.Item name="baseUrl" label={t('aiConfiguration.baseUrl')} extra={t('aiConfiguration.baseUrlHelp')} rules={[{ required: true, type: 'url' }]}><Input maxLength={512} placeholder={t('aiConfiguration.baseUrlExample')} /></Form.Item>
     <Form.Item name="apiKey" label={t('chat.apiKeyLabel')} extra={t(config?.keyConfigured ? 'aiConfiguration.keepKey' : 'aiConfiguration.enterKey')} rules={[{ required: !config?.keyConfigured }]}><Input.Password autoComplete="new-password" maxLength={4096} /></Form.Item>
    </div>
    <div className="oa-provider-model-heading">
     <div><Typography.Title level={5}>{t('aiConfiguration.directory')}</Typography.Title><Typography.Text type="secondary">{t('aiConfiguration.capabilitiesHint')}</Typography.Text></div>
     <Button loading={busy === 'discover'} onClick={() => void discover()}>{t('aiConfiguration.fetchModels')}</Button>
    </div>
    <AiModelTable rows={rows} selected={selected} disabled={Boolean(busy) || !config} busy={busy} onSelection={choose}
     onChange={row => setRows(previous => previous.map(item => item.id === row.id ? row : item))} onTest={id => void test(id)} />
    <div className="oa-provider-footer">
     <Form.Item name="model" label={t('aiConfiguration.defaultModelLabel')} rules={[{ required: true }]}>
      <Select showSearch optionFilterProp="label" options={selected.map(id => ({ label: id, value: id }))} placeholder={t('aiConfiguration.chooseDefault')} />
     </Form.Item>
     <Space wrap>
      <Button loading={busy.startsWith('test:')} disabled={!config || Boolean(busy)} onClick={() => void test()}>{t('aiConfiguration.test')}</Button>
      <Button type="primary" loading={busy === 'save'} disabled={!config || Boolean(busy)} onClick={() => void save()}>{t('common.save')}</Button>
     </Space>
    </div>
   </Form>
  </div>
 </Card>;
}

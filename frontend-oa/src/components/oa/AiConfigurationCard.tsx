import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Drawer, Form, Input, Select, Space, Tabs, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/components/auth/AuthProvider';
import { aiConfigurationApi, type AiChannel, type AiConfiguration, type AiConfiguredModel } from '@/lib/aiConfigurationApi';
import { message } from '@/lib/antdMessage';
import AiModelTable from './AiModelTable';
import AiChannelCards from './AiChannelCards';
import { AI_PROVIDER_PRESETS } from '@/lib/aiProviderPresets';
import AiUsagePanel from './AiUsagePanel';

export default function AiConfigurationCard() {
 const { t } = useTranslation();
 const { user } = useAuth();
 const allowed = Boolean(user?.roles.includes('SUPER_ADMIN') && user.permissions.includes('access:manage'));
 return allowed ? <Tabs key={`${user?.tenantId}:${user?.id}:${user?.permissionVersion}`} destroyOnHidden items={[
  { key: 'channels', label: t('aiConfiguration.channelsTitle'), children: <Channels /> },
  { key: 'usage', label: t('aiConfiguration.usage.title'), children: <AiUsagePanel /> },
 ]} /> : null;
}
function Channels() {
 const { t } = useTranslation();
 const [channels, setChannels] = useState<AiChannel[]>([]);
 const [id, setId] = useState(1);
 const [editing, setEditing] = useState(false);
 const [error, setError] = useState(false);
 useEffect(() => {
  let active = true;
  aiConfigurationApi.channels().then(value => { if (active) setChannels(value); }).catch(() => { if (active) setError(true); });
  return () => { active = false; };
 }, []);
 const channel = channels.find(item => item.id === id);
 return <Space orientation="vertical" style={{ width: '100%' }}>
  {error && <Alert type="error" title={t('aiConfiguration.loadFailed')} />}
  <AiChannelCards channels={channels} selectedId={id} onSelect={value => { setId(value); setEditing(true); }} onAdd={() => { setId(Math.max(id, 1, ...channels.map(item => item.id)) + 1); setEditing(true); }} />
  <Drawer title={t('aiConfiguration.title')} open={editing} onClose={() => setEditing(false)} destroyOnHidden size="min(1100px, 100vw)">
   <Typography.Paragraph type="secondary">{t('aiConfiguration.channelHelp')}</Typography.Paragraph>
   {editing && <Editor key={id} channelId={id} channel={channel} onSaved={value => setChannels(previous => [...previous.filter(item => item.id !== value.id), value])} />}
  </Drawer>
 </Space>;
}
function Editor({ channelId, channel, onSaved }: { channelId: number; channel?: AiChannel; onSaved: (value: AiChannel) => void }) {
 const { t } = useTranslation();
 const [form] = Form.useForm<{ baseUrl: string; model: string; apiKey?: string; customName?: string }>();
 const [config, setConfig] = useState<AiConfiguration>();
 const [provider, setProvider] = useState('custom');
 const [credentialReset, setCredentialReset] = useState(false);
 const nameOptions = [...AI_PROVIDER_PRESETS.map(item => ({ value: item.id, label: t(`aiConfiguration.providers.${item.id}`) })),
  { value: 'custom', label: t('aiConfiguration.customProvider') }];
 const [rows, setRows] = useState<AiConfiguredModel[]>([]);
 const [selected, setSelected] = useState<string[]>([]);
 const [busy, setBusy] = useState('');
 const [result, setResult] = useState<{ type: 'error' | 'success'; text: string }>();
 const active = useRef(true);
 useEffect(() => {
  active.current = true;
  (channel ? Promise.resolve(channel.configuration) : channelId === 1 ? aiConfigurationApi.get() : Promise.resolve({ baseUrl: '', model: '', keyConfigured: false, version: 0, source: 'DATABASE', models: [] })).then(value => {
   if (!active.current) return;
   setConfig(value);
   setCredentialReset(false);
   setProvider(AI_PROVIDER_PRESETS.find(item => item.name === channel?.name && item.baseUrl === value.baseUrl)?.id ?? 'custom');
   const models = value.models ?? [{ id: value.model, contextWindow: 32768, maxOutputTokens: 4096, supportsImages: false }];
   setRows(models.filter(row => row.id)); setSelected(models.map(row => row.id).filter(Boolean));
   form.setFieldsValue({ baseUrl: value.baseUrl, model: value.model, apiKey: '', customName: channel?.name ?? '' });
  }).catch(() => { if (active.current) setResult({ type: 'error', text: t('aiConfiguration.loadFailed') }); });
  return () => { active.current = false; form.resetFields(); };
 }, [form, t, channelId, channel]);
 const fail = (error: unknown) => { if (active.current) setResult({ type: 'error', text: error instanceof Error ? error.message : t('aiConfiguration.saveFailed') }); };
 const changeProvider = (value: string) => {
  if (value === provider) return;
  setProvider(value); setCredentialReset(true); setResult(undefined); setRows([]); setSelected([]);
  const preset = AI_PROVIDER_PRESETS.find(item => item.id === value);
  form.setFieldsValue({ ...(preset ? { baseUrl: preset.baseUrl, customName: preset.name } : {}), apiKey: '', model: undefined });
 };
 const choose = (ids: string[]) => {
  setSelected(ids);
  if (!ids.includes(form.getFieldValue('model'))) form.setFieldValue('model', ids[0]);
 };
 const discover = async () => {
  try {
   const values = await form.validateFields(['baseUrl', 'apiKey']);
   setBusy('discover'); setResult(undefined);
   const fetched = await aiConfigurationApi.discover({ ...values, channelId });
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
   await aiConfigurationApi.test({ ...values, model: target, channelId });
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
   const name = provider === 'custom' ? values.customName!.trim() : AI_PROVIDER_PRESETS.find(item => item.id === provider)!.name;
   const saved = await aiConfigurationApi.saveChannel(channelId, name, { baseUrl: values.baseUrl, model: values.model, apiKey: values.apiKey, version: config.version, models });
   const updated = saved.configuration;
   if (!active.current) return;
   setConfig(updated); onSaved(saved); form.setFieldValue('apiKey', ''); message.success(t('aiConfiguration.saved'));
  } catch (error) { if (error instanceof Error) fail(error); }
  finally { if (active.current) setBusy(''); }
 };
 return <Card className="oa-domain-card oa-provider-card" loading={!config && !result}>
  <div className="oa-provider-content">
   <Alert type="info" showIcon title={t('aiConfiguration.hint')} />
   {result && <Alert type={result.type} showIcon title={result.text} />}
   <Form name="ai-provider" form={form} layout="vertical" disabled={Boolean(busy) || !config} autoComplete="off">
    <Form.Item label={t('aiConfiguration.channel')} htmlFor="ai-provider-channel-name" required style={{ width: 320, maxWidth: '100%' }}>
     <Select id="ai-provider-channel-name" showSearch value={provider} options={nameOptions} optionFilterProp="label"
      onChange={changeProvider}
      placeholder={t('aiConfiguration.chooseChannel')} />
    </Form.Item>
    {provider === 'custom' && <Form.Item name="customName" label={t('aiConfiguration.customName')} rules={[{ required: true, whitespace: true, max: 80 }]} style={{ width: 320, maxWidth: '100%' }}>
     <Input maxLength={80} placeholder={t('aiConfiguration.customNamePlaceholder')} />
    </Form.Item>}
    <div className="oa-provider-fields">
     <Form.Item name="baseUrl" label={t('aiConfiguration.baseUrl')} extra={t('aiConfiguration.baseUrlHelp')} rules={[{ required: true, type: 'url' }]}><Input maxLength={512} placeholder={t('aiConfiguration.baseUrlExample')} /></Form.Item>
     <Form.Item name="apiKey" label={t('chat.apiKeyLabel')} extra={t(config?.keyConfigured && !credentialReset ? 'aiConfiguration.keepKey' : 'aiConfiguration.enterKey')} rules={[{ required: !config?.keyConfigured || credentialReset }]}><Input.Password autoComplete="new-password" maxLength={4096} /></Form.Item>
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

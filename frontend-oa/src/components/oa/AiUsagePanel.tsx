import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Empty, Select, Space, Spin, Statistic, theme } from 'antd';
import { useTranslation } from 'react-i18next';
import type { EChartsOption } from 'echarts';
import { request } from '@/lib/oaApi';
import EChartsCard from './EChartsCard';

interface Bucket { day: string; channelId: number; channelName?: string | null; model: string; calls: number; successes: number; cancelled: number; durationMs: number; reportedCalls: number; inputTokens: number | null; outputTokens: number | null; totalTokens: number | null }
export default function AiUsagePanel() {
 const { t } = useTranslation();
 const { token } = theme.useToken();
 const [days, setDays] = useState(7);
 const [channel, setChannel] = useState<number>();
 const [model, setModel] = useState<string>();
 const [rows, setRows] = useState<Bucket[]>([]);
 const [loading, setLoading] = useState(false);
 const [error, setError] = useState(false);
 const [revision, setRevision] = useState(0);
 useEffect(() => {
  let active = true;
  setLoading(true); setError(false); setRows([]);
  request<Bucket[]>(`/admin/ai-configuration/usage?days=${days}`).then(value => { if (active) setRows(value); })
   .catch(() => { if (active) setError(true); }).finally(() => { if (active) setLoading(false); });
  return () => { active = false; };
 }, [days, revision]);
 const filtered = rows.filter(row => (channel === undefined || row.channelId === channel) && (!model || row.model === model));
 const sum = (key: 'calls' | 'successes' | 'durationMs' | 'reportedCalls' | 'inputTokens' | 'outputTokens' | 'totalTokens') => filtered.reduce((total, row) => total + (row[key] ?? 0), 0);
 const calls = sum('calls'); const reported = sum('reportedCalls');
 const channelLabel = useCallback((id: number) => {
  if (id === 0) return t('aiConfiguration.usage.environment');
  const name = rows.find(row => row.channelId === id)?.channelName;
  if (!name) return t('aiConfiguration.channelNumber', { id });
  const duplicate = new Set(rows.filter(row => row.channelName === name).map(row => row.channelId)).size > 1;
  return duplicate ? t('aiConfiguration.usage.namedChannel', { name, id }) : name;
 }, [rows, t]);
 const options = useMemo(() => {
  const data = rows.filter(row => (channel === undefined || row.channelId === channel) && (!model || row.model === model));
  const dates = [...new Set(data.map(row => row.day))].sort();
  const keys = [...new Set(data.map(row => `${row.channelId}::${row.model}`))];
  const option = (tokens: boolean): EChartsOption => ({
   color: [token.colorPrimary, token.colorSuccess, token.colorWarning, token.colorInfo, '#9b6dd7', '#e86b9d'],
   textStyle: { color: token.colorTextSecondary }, backgroundColor: 'transparent',
   tooltip: { trigger: 'axis', backgroundColor: token.colorBgElevated, textStyle: { color: token.colorText } },
   legend: { type: 'scroll', bottom: 0, textStyle: { color: token.colorTextSecondary } },
   grid: { left: 64, right: 20, top: 24, bottom: 70 },
   xAxis: { type: 'category', data: dates, axisLine: { lineStyle: { color: token.colorBorder } } },
   yAxis: { type: 'value', splitLine: { lineStyle: { color: token.colorBorderSecondary } } },
   series: keys.map(key => ({ id: key, name: t('aiConfiguration.usage.series', { channel: channelLabel(Number(key.slice(0, key.indexOf('::')))), model: key.slice(key.indexOf('::') + 2) }), type: tokens ? 'bar' : 'line', stack: tokens ? 'tokens' : undefined,
    data: dates.map(day => {
     const bucket = data.find(row => row.day === day && `${row.channelId}::${row.model}` === key);
     return tokens ? bucket?.totalTokens ?? null : bucket?.calls ?? 0;
    }), ...(tokens ? {} : { areaStyle: { opacity: 0.08 } }),
   })),
  });
  return { calls: option(false), tokens: option(true) };
 }, [rows, channel, model, token, channelLabel, t]);
 const missing = t('aiConfiguration.usage.unavailable');
 return <div className="oa-provider-content">
  <Space wrap>
   <Select aria-label={t('aiConfiguration.usage.period')} value={days} onChange={setDays} options={[1, 7, 30].map(value => ({ value, label: t('aiConfiguration.usage.days', { count: value }) }))} />
   <Select aria-label={t('aiConfiguration.channel')} allowClear placeholder={t('aiConfiguration.usage.allChannels')} value={channel} onChange={setChannel} style={{ minWidth: 160 }} options={[...new Set(rows.map(row => row.channelId))].map(value => ({ value, label: channelLabel(value) }))} />
   <Select aria-label={t('chat.model')} allowClear showSearch placeholder={t('aiConfiguration.usage.allModels')} value={model} onChange={setModel} style={{ minWidth: 220, maxWidth: '100%' }} options={[...new Set(rows.map(row => row.model))].map(value => ({ value, label: value }))} />
   <Button onClick={() => setRevision(value => value + 1)} loading={loading}>{t('aiConfiguration.usage.refresh')}</Button>
  </Space>
  <Alert type="info" showIcon title={t('aiConfiguration.usage.scope')} />
  {error ? <Alert type="error" title={t('aiConfiguration.loadFailed')} /> : <Spin spinning={loading}>
   <div className="oa-usage-stats">
    <Card className="oa-domain-card"><Statistic title={t('aiConfiguration.usage.calls')} value={calls} /></Card>
    <Card className="oa-domain-card"><Statistic title={t('aiConfiguration.usage.tokens')} value={reported ? sum('totalTokens') : missing} /></Card>
    <Card className="oa-domain-card"><Statistic title={t('aiConfiguration.usage.success')} value={calls ? 100 * sum('successes') / calls : missing} precision={2} suffix={calls ? '%' : undefined} /></Card>
    <Card className="oa-domain-card"><Statistic title={t('aiConfiguration.usage.latency')} value={calls ? sum('durationMs') / calls / 1000 : missing} precision={2} suffix={calls ? t('aiConfiguration.usage.seconds') : undefined} /></Card>
    <Card className="oa-domain-card"><Statistic title={t('aiConfiguration.usage.cost')} value={missing} /></Card>
   </div>
   <Alert style={{ marginBlock: 16 }} type="info" title={t('aiConfiguration.usage.coverage', { reported, calls })} />
   {!filtered.length ? <Card className="oa-domain-card"><Empty description={t('aiConfiguration.usage.empty')} /></Card> : <>
    <EChartsCard title={t('aiConfiguration.usage.tokenTrend')} option={options.tokens} height={320} />
    <EChartsCard title={t('aiConfiguration.usage.callTrend')} option={options.calls} height={320} />
   </>}
  </Spin>}
 </div>;
}

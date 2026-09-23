import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Card, Empty, Segmented, Skeleton, Space, Statistic, Switch, Tag, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import * as echarts from 'echarts/core';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import type { EChartsCoreOption } from 'echarts/core';
import dayjs from 'dayjs';
import { OaIcon } from '@/components/OaIcon';
import { usePermission } from '@/hooks/usePermission';
import { formatOaApiError } from '@/lib/oaApi';
import {
  platformObservabilityApi,
  type ObservabilityRange,
  type PlatformObservabilityOverview,
} from '@/lib/platformObservabilityApi';

echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer]);

type ChartColors = { text: string; muted: string; border: string; primary: string; cyan: string; green: string; amber: string; red: string };
type ChartOptionFactory = (colors: ChartColors) => EChartsCoreOption;

function readColors(element: HTMLElement): ChartColors {
  const styles = getComputedStyle(element);
  const variable = (name: string, fallback: string) => styles.getPropertyValue(name).trim() || fallback;
  return {
    text: variable('--oa-text', '#e9f2ff'), muted: variable('--oa-muted', '#8492a6'),
    border: variable('--oa-border', '#d9e2ef'), primary: variable('--oa-primary', '#2d73ed'),
    cyan: '#37cbd7', green: '#26b886', amber: '#e7a84e', red: variable('--oa-danger', '#e56b72'),
  };
}

function ChartPanel({ title, subtitle, dataCount, option }: {
  title: string; subtitle: string; dataCount: number; option: ChartOptionFactory;
}) {
  const { t } = useTranslation();
  const host = useRef<HTMLDivElement>(null);
  const chart = useRef<echarts.EChartsType | null>(null);

  useEffect(() => {
    const element = host.current;
    if (!element || dataCount === 0) return;
    const instance = echarts.init(element);
    chart.current = instance;
    const render = () => {
      const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      instance.setOption({ ...option(readColors(element)), animationDuration: reducedMotion ? 0 : 320,
        animationDurationUpdate: reducedMotion ? 0 : 240 }, true);
    };
    render();
    const resize = new ResizeObserver(() => instance.resize());
    resize.observe(element);
    const themeObserver = new MutationObserver(render);
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'style', 'data-theme'] });
    themeObserver.observe(document.body, { attributes: true, attributeFilter: ['class', 'style', 'data-theme'] });
    return () => { resize.disconnect(); themeObserver.disconnect(); instance.dispose(); chart.current = null; };
  }, [dataCount, option]);

  return (
    <Card className="oa-observability-chart" title={<span>{title}<small>{subtitle}</small></span>}>
      {dataCount === 0
        ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('platformObservability.noChartData')} />
        : <div ref={host} className="oa-observability-canvas" role="img" aria-label={title} />}
    </Card>
  );
}

export default function PlatformObservabilityPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { allowed: canOpenLogs } = usePermission('route:runtime-logs');
  const [range, setRange] = useState<ObservabilityRange>('7d');
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [overview, setOverview] = useState<PlatformObservabilityOverview>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const requestSequence = useRef(0);
  const load = useCallback(async () => {
    const sequence = ++requestSequence.current;
    setLoading(true);
    setError(undefined);
    try {
      const result = await platformObservabilityApi.overview(range);
      if (sequence === requestSequence.current) setOverview(result);
    } catch (cause) {
      if (sequence === requestSequence.current) { setOverview(undefined); setError(formatOaApiError(cause)); }
    } finally {
      if (sequence === requestSequence.current) setLoading(false);
    }
  }, [range]);
  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    if (!autoRefresh) return;
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void load();
    }, 60_000);
    return () => window.clearInterval(timer);
  }, [autoRefresh, load]);

  const number = useMemo(() => new Intl.NumberFormat(i18n.language), [i18n.language]);
  const series = useMemo(() => {
    if (!overview) return { buckets: [] as string[], labels: [] as string[], bySource: {} as Record<string, number[]>, failed: [] as number[], blocked: [] as number[] };
    const unit = overview.interval === 'hour' ? 'hour' : 'day';
    const last = dayjs(overview.to).startOf(unit);
    const buckets: string[] = [];
    for (let cursor = dayjs(overview.from).startOf(unit); !cursor.isAfter(last) && buckets.length < 32; cursor = cursor.add(1, unit)) {
      buckets.push(cursor.format('YYYY-MM-DDTHH:mm:ss'));
    }
    const points = new Map(overview.timeline.map((point) => [`${dayjs(point.bucket).valueOf()}:${point.source}`, point]));
    const labels = buckets.map((bucket) => new Intl.DateTimeFormat(i18n.language, overview.interval === 'hour'
      ? { month: 'numeric', day: 'numeric', hour: '2-digit' }
      : { month: 'numeric', day: 'numeric' }).format(new Date(bucket)));
    const bySource: Record<string, number[]> = {};
    for (const source of ['HUMAN', 'AGENT', 'INTEGRATION']) {
      bySource[source] = buckets.map((bucket) => points.get(`${dayjs(bucket).valueOf()}:${source}`)?.total ?? 0);
    }
    return {
      buckets, labels, bySource,
      failed: buckets.map((bucket) => ['HUMAN', 'AGENT', 'INTEGRATION'].reduce((sum, source) => sum + (points.get(`${dayjs(bucket).valueOf()}:${source}`)?.failed ?? 0), 0)),
      blocked: buckets.map((bucket) => ['HUMAN', 'AGENT', 'INTEGRATION'].reduce((sum, source) => sum + (points.get(`${dayjs(bucket).valueOf()}:${source}`)?.blocked ?? 0), 0)),
    };
  }, [i18n.language, overview]);

  const volumeOption = useCallback<ChartOptionFactory>((colors) => ({
    color: [colors.primary, colors.cyan, colors.amber],
    tooltip: { trigger: 'axis', renderMode: 'richText' }, legend: { bottom: 0, textStyle: { color: colors.muted } },
    grid: { left: 42, right: 16, top: 20, bottom: 56 },
    xAxis: { type: 'category', data: series.labels, axisLabel: { color: colors.muted }, axisLine: { lineStyle: { color: colors.border } } },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { color: colors.muted }, splitLine: { lineStyle: { color: colors.border, opacity: 0.45 } } },
    series: (['HUMAN', 'AGENT', 'INTEGRATION'] as const).map((source) => ({
      name: t(`runtimeLogs.source.${source}`), type: 'line', smooth: 0.28, symbol: 'circle', symbolSize: 5,
      data: series.bySource[source], areaStyle: { opacity: 0.075 }, lineStyle: { width: 2.5 },
    })),
  }), [series, t]);
  const riskOption = useCallback<ChartOptionFactory>((colors) => ({
    color: [colors.red, colors.amber], tooltip: { trigger: 'axis', renderMode: 'richText' },
    legend: { bottom: 0, textStyle: { color: colors.muted } },
    grid: { left: 42, right: 16, top: 20, bottom: 56 },
    xAxis: { type: 'category', data: series.labels, axisLabel: { color: colors.muted }, axisLine: { lineStyle: { color: colors.border } } },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { color: colors.muted }, splitLine: { lineStyle: { color: colors.border, opacity: 0.45 } } },
    series: [
      { name: t('platformObservability.failed'), type: 'bar', barMaxWidth: 22, data: series.failed, itemStyle: { borderRadius: [4, 4, 0, 0] } },
      { name: t('platformObservability.blocked'), type: 'line', smooth: true, data: series.blocked, lineStyle: { width: 2.5 } },
    ],
  }), [series, t]);
  const sourceOption = useCallback<ChartOptionFactory>((colors) => ({
    color: [colors.primary, colors.cyan, colors.amber],
    tooltip: { trigger: 'item', renderMode: 'richText' }, legend: { bottom: 0, textStyle: { color: colors.muted } },
    series: [{ type: 'pie', radius: ['52%', '73%'], center: ['50%', '44%'], label: { color: colors.text, formatter: '{d}%' },
      data: overview?.sources.map((item) => ({ name: t(`runtimeLogs.source.${item.code}`), value: item.total })) ?? [] }],
  }), [overview, t]);
  const errorOption = useCallback<ChartOptionFactory>((colors) => ({
    color: [colors.red], tooltip: { trigger: 'axis', renderMode: 'richText' },
    grid: { left: 136, right: 28, top: 16, bottom: 24 },
    xAxis: { type: 'value', minInterval: 1, axisLabel: { color: colors.muted }, splitLine: { lineStyle: { color: colors.border, opacity: 0.45 } } },
    yAxis: { type: 'category', inverse: true, data: overview?.errorCodes.map((item) => item.code) ?? [],
      axisLabel: { color: colors.text, width: 120, overflow: 'truncate' }, axisLine: { show: false }, axisTick: { show: false } },
    series: [{ type: 'bar', barMaxWidth: 22, data: overview?.errorCodes.map((item) => item.total) ?? [],
      itemStyle: { borderRadius: [0, 5, 5, 0] } }],
  }), [overview]);

  return (
    <section className="oa-observability-page">
      <header className="oa-observability-hero">
        <div className="oa-observability-hero-copy">
          <Typography.Text className="oa-observability-kicker">{t('platformObservability.eyebrow')}</Typography.Text>
          <Typography.Title level={2}>{t('platformObservability.title')}</Typography.Title>
          <Typography.Paragraph>{t('platformObservability.subtitle')}</Typography.Paragraph>
          <Space wrap>
            <Tag color="blue">{t('platformObservability.tenantScoped')}</Tag>
            <Tag>{t('platformObservability.readOnly')}</Tag>
          </Space>
        </div>
        <div className="oa-observability-actions">
          <Space><Switch size="small" checked={autoRefresh} onChange={setAutoRefresh} aria-label={t('platformObservability.autoRefresh')} />
            <Typography.Text type="secondary">{t('platformObservability.autoRefresh')}</Typography.Text></Space>
          <Segmented<ObservabilityRange> value={range} onChange={(next) => { setRange(next); setOverview(undefined); }} options={([
            { value: '24h', label: t('platformObservability.ranges.day') },
            { value: '7d', label: t('platformObservability.ranges.week') },
            { value: '30d', label: t('platformObservability.ranges.month') },
          ])} />
          <Button icon={<OaIcon name="reload" />} loading={loading} onClick={() => void load()}>{t('common.refresh')}</Button>
          {canOpenLogs && <Button type="primary" icon={<OaIcon name="runtime-logs" />} onClick={() => navigate('/oa/runtime-logs')}>
            {t('platformObservability.openLogs')}
          </Button>}
        </div>
      </header>
      {error && <Alert type="error" showIcon title={error} action={<Button onClick={() => void load()}>{t('common.retry')}</Button>} />}
      {loading && !overview ? <Skeleton active paragraph={{ rows: 10 }} /> : overview && <>
        <div className="oa-observability-metrics">
          <Card><Statistic title={t('platformObservability.total')} value={overview.stats.total ?? 0} formatter={(value) => number.format(Number(value))} /></Card>
          <Card><Statistic title={t('platformObservability.successRate')} value={overview.stats.total ? overview.stats.succeeded / overview.stats.total * 100 : 0} precision={1} suffix="%" /></Card>
          <Card><Statistic title={t('platformObservability.failed')} value={overview.stats.failed ?? 0} formatter={(value) => number.format(Number(value))} /></Card>
          <Card><Statistic title={t('platformObservability.blocked')} value={overview.stats.blocked ?? 0} formatter={(value) => number.format(Number(value))} /></Card>
          <Card><Statistic title={t('platformObservability.p95')} value={overview.p95DurationMs ?? 0} suffix="ms" /></Card>
        </div>
        <div className="oa-observability-grid">
          <ChartPanel title={t('platformObservability.volumeTitle')} subtitle={t('platformObservability.volumeSubtitle')} dataCount={overview.stats.total ? series.buckets.length : 0} option={volumeOption} />
          <ChartPanel title={t('platformObservability.riskTitle')} subtitle={t('platformObservability.riskSubtitle')} dataCount={overview.stats.total ? series.buckets.length : 0} option={riskOption} />
          <ChartPanel title={t('platformObservability.sourceTitle')} subtitle={t('platformObservability.sourceSubtitle')} dataCount={overview.sources.length} option={sourceOption} />
          <ChartPanel title={t('platformObservability.errorTitle')} subtitle={t('platformObservability.errorSubtitle')} dataCount={overview.errorCodes.length} option={errorOption} />
        </div>
        <Typography.Text type="secondary" className="oa-observability-footnote">{t('platformObservability.footnote')}</Typography.Text>
      </>}
    </section>
  );
}

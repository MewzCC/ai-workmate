import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Card, Checkbox, Empty, Input, Modal, Popover, Segmented, Select, Skeleton, Space, Statistic, Switch, Tag, Typography } from 'antd';
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
  type ObservabilityChartKind,
  type ObservabilityChartPreference,
  type PlatformObservabilityOverview,
  type PlatformObservabilityTimeline,
  type ObservabilityTimelinePoint,
} from '@/lib/platformObservabilityApi';

echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer]);

type ChartColors = { text: string; muted: string; border: string; primary: string; cyan: string; green: string; amber: string; red: string };
type ChartSeries = { buckets: string[]; labels: string[]; bySource: Record<string, number[]>; failed: number[]; blocked: number[] };
type ChartOptionFactory = (colors: ChartColors, mode: string, selected: string[], data: ChartSeries) => EChartsCoreOption;
type ChartChoice = { value: string; label: string };
const DEFAULT_CHARTS: ObservabilityChartPreference[] = [
  { id: 'volume', kind: 'volume', title: '', mode: 'line', content: ['HUMAN', 'AGENT', 'INTEGRATION'], size: 'normal', granularity: 'auto' },
  { id: 'risk', kind: 'risk', title: '', mode: 'mixed', content: ['failed', 'blocked'], size: 'normal', granularity: 'auto' },
  { id: 'source', kind: 'source', title: '', mode: 'donut', content: ['HUMAN', 'AGENT', 'INTEGRATION'], size: 'normal', granularity: 'auto' },
  { id: 'error', kind: 'error', title: '', mode: 'bar', content: [], size: 'normal', granularity: 'auto' },
];
const chartKind = (chart: ObservabilityChartPreference): ObservabilityChartKind =>
  (chart.kind ?? chart.id) as ObservabilityChartKind;

function buildSeries(from: string, to: string, interval: 'hour' | 'day',
                     timeline: ObservabilityTimelinePoint[], locale: string): ChartSeries {
  const last = dayjs(to).startOf(interval);
  const buckets: string[] = [];
  for (let cursor = dayjs(from).startOf(interval); !cursor.isAfter(last) && buckets.length < 745; cursor = cursor.add(1, interval)) {
    buckets.push(cursor.format('YYYY-MM-DDTHH:mm:ss'));
  }
  const points = new Map(timeline.map((point) => [`${dayjs(point.bucket).valueOf()}:${point.source}`, point]));
  const formatter = new Intl.DateTimeFormat(locale, interval === 'hour'
    ? { month: 'numeric', day: 'numeric', hour: '2-digit' } : { month: 'numeric', day: 'numeric' });
  const labels = buckets.map((bucket) => formatter.format(new Date(bucket)));
  const bySource: Record<string, number[]> = {};
  for (const source of ['HUMAN', 'AGENT', 'INTEGRATION']) {
    bySource[source] = buckets.map((bucket) => points.get(`${dayjs(bucket).valueOf()}:${source}`)?.total ?? 0);
  }
  return {
    buckets, labels, bySource,
    failed: buckets.map((bucket) => ['HUMAN', 'AGENT', 'INTEGRATION'].reduce((sum, source) =>
      sum + (points.get(`${dayjs(bucket).valueOf()}:${source}`)?.failed ?? 0), 0)),
    blocked: buckets.map((bucket) => ['HUMAN', 'AGENT', 'INTEGRATION'].reduce((sum, source) =>
      sum + (points.get(`${dayjs(bucket).valueOf()}:${source}`)?.blocked ?? 0), 0)),
  };
}

const EMPTY_SERIES: ChartSeries = { buckets: [], labels: [], bySource: {}, failed: [], blocked: [] };

function readColors(element: HTMLElement): ChartColors {
  const styles = getComputedStyle(element);
  const variable = (name: string, fallback: string) => styles.getPropertyValue(name).trim() || fallback;
  return {
    text: variable('--oa-text', '#e9f2ff'), muted: variable('--oa-muted', '#8492a6'),
    border: variable('--oa-border', '#d9e2ef'), primary: variable('--oa-primary', '#2d73ed'),
    cyan: '#37cbd7', green: '#26b886', amber: '#e7a84e', red: variable('--oa-danger', '#e56b72'),
  };
}

function ChartPanel({ title, subtitle, dataCount, option, modes, content, preference, onChange, disabled, seriesData }: {
  title: string; subtitle: string; dataCount: number; option: ChartOptionFactory;
  modes: ChartChoice[]; content: ChartChoice[]; preference: ObservabilityChartPreference;
  onChange: (next: ObservabilityChartPreference) => void;
  disabled: boolean;
  seriesData: ChartSeries;
}) {
  const { t } = useTranslation();
  const host = useRef<HTMLDivElement>(null);
  const chart = useRef<echarts.EChartsType | null>(null);
  const hasData = dataCount > 0;
  const { mode } = preference;
  const selectedValues = content.map((item) => item.value).filter((value) => preference.content.includes(value));
  const activeValues = preference.content.length ? selectedValues : content.map((item) => item.value);
  const renderChart = useRef<() => void>(() => undefined);
  const previousMode = useRef<string | undefined>(undefined);

  renderChart.current = () => {
    const element = host.current;
    const instance = chart.current;
    if (!element || !instance) return;
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    instance.setOption({ ...option(readColors(element), mode, activeValues, seriesData), animationDuration: reducedMotion ? 0 : 320,
      animationDurationUpdate: reducedMotion ? 0 : 240 }, { notMerge: previousMode.current !== mode, replaceMerge: ['series'] });
    previousMode.current = mode;
  };

  useEffect(() => {
    const element = host.current;
    if (!element || !hasData) return;
    const instance = echarts.init(element);
    chart.current = instance;
    const resize = new ResizeObserver(() => instance.resize());
    resize.observe(element);
    const themeObserver = new MutationObserver(() => renderChart.current());
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'style', 'data-theme'] });
    themeObserver.observe(document.body, { attributes: true, attributeFilter: ['class', 'style', 'data-theme'] });
    return () => { resize.disconnect(); themeObserver.disconnect(); instance.dispose(); chart.current = null; previousMode.current = undefined; };
  }, [hasData]);
  useEffect(() => { renderChart.current(); }, [dataCount, option, mode, preference.content, seriesData]);

  return (
    <Card className="oa-observability-chart" title={<span>{title}<small>{subtitle}</small></span>}
      extra={<div className="oa-observability-chart-controls">
        <Segmented size="small" disabled={disabled} aria-label={t('platformObservability.chartType', { title })} value={mode}
          onChange={(next) => onChange({ ...preference, mode: next })} options={modes} />
        <Popover trigger="click" placement="bottomRight" title={t('platformObservability.chartContent')}
          content={<div className="oa-observability-content-options">
            <Checkbox.Group disabled={disabled} value={activeValues} onChange={(values) => { if (values.length) onChange({ ...preference, content: values.map(String) }); }}>
              {content.map((item) => <Checkbox key={item.value} value={item.value}>{item.label}</Checkbox>)}
            </Checkbox.Group>
          </div>}>
          <Button size="small" disabled={disabled} aria-label={t('platformObservability.chartContentFor', { title })}>
            {t('platformObservability.chartContent')} · {activeValues.length}
          </Button>
        </Popover>
      </div>}>
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
  const [alternateTimeline, setAlternateTimeline] = useState<PlatformObservabilityTimeline>();
  const [alternateLoading, setAlternateLoading] = useState(false);
  const [alternateError, setAlternateError] = useState<string>();
  const [alternateRetry, setAlternateRetry] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [charts, setCharts] = useState<ObservabilityChartPreference[]>(DEFAULT_CHARTS);
  const [preferencesLoading, setPreferencesLoading] = useState(true);
  const [preferencesError, setPreferencesError] = useState<string>();
  const [preferencesDirty, setPreferencesDirty] = useState(false);
  const [preferencesSaving, setPreferencesSaving] = useState(false);
  const [layoutOpen, setLayoutOpen] = useState(false);
  const [newKind, setNewKind] = useState<ObservabilityChartKind>('volume');
  const requestSequence = useRef(0);
  const loadPreferences = useCallback(async () => {
    setPreferencesLoading(true);
    setPreferencesError(undefined);
    try {
      const result = await platformObservabilityApi.preferences();
      setCharts(result.charts);
      setPreferencesDirty(false);
    } catch (cause) {
      setPreferencesError(formatOaApiError(cause));
    } finally {
      setPreferencesLoading(false);
    }
  }, []);
  useEffect(() => { void loadPreferences(); }, [loadPreferences]);
  const updateChart = useCallback((next: ObservabilityChartPreference) => {
    setCharts((current) => current.map((chart) => chart.id === next.id ? next : chart));
    setPreferencesDirty(true);
  }, []);
  const moveChart = useCallback((id: string, direction: -1 | 1) => {
    setCharts((current) => {
      const index = current.findIndex((chart) => chart.id === id);
      const target = index + direction;
      if (index < 0 || target < 0 || target >= current.length) return current;
      const next = [...current];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
    setPreferencesDirty(true);
  }, []);
  const nextChartId = useCallback((kind: ObservabilityChartKind, current: ObservabilityChartPreference[]) => {
    let index = 1;
    while (current.some((chart) => chart.id === `${kind}-${index}`)) index += 1;
    return `${kind}-${index}`;
  }, []);
  const addChart = useCallback((kind: ObservabilityChartKind) => {
    setCharts((current) => {
      if (current.length >= 12) return current;
      const template = DEFAULT_CHARTS.find((chart) => chart.kind === kind)!;
      return [...current, { ...template, id: nextChartId(kind, current), content: [...template.content] }];
    });
    setPreferencesDirty(true);
  }, [nextChartId]);
  const duplicateChart = useCallback((id: string) => {
    setCharts((current) => {
      if (current.length >= 12) return current;
      const original = current.find((chart) => chart.id === id);
      if (!original) return current;
      const kind = chartKind(original);
      const index = current.findIndex((chart) => chart.id === id);
      const next = [...current];
      next.splice(index + 1, 0, { ...original, id: nextChartId(kind, current), content: [...original.content] });
      return next;
    });
    setPreferencesDirty(true);
  }, [nextChartId]);
  const removeChart = useCallback((id: string) => {
    setCharts((current) => current.length > 1 ? current.filter((chart) => chart.id !== id) : current);
    setPreferencesDirty(true);
  }, []);
  const savePreferences = useCallback(async () => {
    setPreferencesSaving(true);
    setPreferencesError(undefined);
    try {
      const result = await platformObservabilityApi.updatePreferences(charts);
      setCharts(result.charts);
      setPreferencesDirty(false);
    } catch (cause) {
      setPreferencesError(formatOaApiError(cause));
    } finally {
      setPreferencesSaving(false);
    }
  }, [charts]);
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
  const needsAlternate = !!overview && charts.some((chart) =>
    (chartKind(chart) === 'volume' || chartKind(chart) === 'risk')
      && chart.granularity !== 'auto' && chart.granularity !== undefined
      && chart.granularity !== overview.interval);
  const alternateInterval: 'hour' | 'day' = overview?.interval === 'hour' ? 'day' : 'hour';
  useEffect(() => {
    if (!overview || !needsAlternate) { setAlternateTimeline(undefined); setAlternateError(undefined); return; }
    let active = true;
    setAlternateTimeline(undefined);
    setAlternateError(undefined);
    setAlternateLoading(true);
    void platformObservabilityApi.timeline(overview.from, overview.to, alternateInterval)
      .then((result) => { if (active) setAlternateTimeline(result); })
      .catch((cause) => { if (active) setAlternateError(formatOaApiError(cause)); })
      .finally(() => { if (active) setAlternateLoading(false); });
    return () => { active = false; };
  }, [alternateInterval, alternateRetry, needsAlternate, overview]);
  const series = useMemo(() => overview
    ? buildSeries(overview.from, overview.to, overview.interval, overview.timeline, i18n.language)
    : EMPTY_SERIES, [i18n.language, overview]);
  const alternateSeries = useMemo(() => alternateTimeline
    ? buildSeries(alternateTimeline.from, alternateTimeline.to, alternateTimeline.interval,
      alternateTimeline.timeline, i18n.language)
    : EMPTY_SERIES, [alternateTimeline, i18n.language]);
  const alternateReady = !!overview && !!alternateTimeline
    && alternateTimeline.from === overview.from && alternateTimeline.to === overview.to;

  const sourceChoices = useMemo(() => (['HUMAN', 'AGENT', 'INTEGRATION'] as const).map((source) => ({
    value: source, label: t(`runtimeLogs.source.${source}`),
  })), [t]);
  const riskChoices = useMemo(() => ([
    { value: 'failed', label: t('platformObservability.failed') },
    { value: 'blocked', label: t('platformObservability.blocked') },
  ]), [t]);
  const errorChoices = useMemo(() => overview?.errorCodes.map((item) => ({ value: item.code, label: item.code })) ?? [], [overview]);
  const volumeModes = useMemo(() => ([
    { value: 'line', label: t('platformObservability.chartModes.line') },
    { value: 'area', label: t('platformObservability.chartModes.area') },
    { value: 'bar', label: t('platformObservability.chartModes.bar') },
  ]), [t]);
  const riskModes = useMemo(() => ([
    { value: 'mixed', label: t('platformObservability.chartModes.mixed') },
    { value: 'line', label: t('platformObservability.chartModes.line') },
    { value: 'bar', label: t('platformObservability.chartModes.bar') },
  ]), [t]);
  const distributionModes = useMemo(() => ([
    { value: 'donut', label: t('platformObservability.chartModes.donut') },
    { value: 'bar', label: t('platformObservability.chartModes.bar') },
  ]), [t]);
  const rankingModes = useMemo(() => ([
    { value: 'bar', label: t('platformObservability.chartModes.bar') },
    { value: 'donut', label: t('platformObservability.chartModes.donut') },
  ]), [t]);
  const chartTitles: Record<ObservabilityChartKind, string> = {
    volume: t('platformObservability.volumeTitle'), risk: t('platformObservability.riskTitle'),
    source: t('platformObservability.sourceTitle'), error: t('platformObservability.errorTitle'),
  };
  const volumeOption = useCallback<ChartOptionFactory>((colors, mode, selected, data) => ({
    color: [colors.primary, colors.cyan, colors.amber],
    tooltip: { trigger: 'axis', renderMode: 'richText' }, legend: { bottom: 0, textStyle: { color: colors.muted } },
    grid: { left: 42, right: 16, top: 20, bottom: 56 },
    xAxis: { type: 'category', data: data.labels, axisLabel: { color: colors.muted, hideOverlap: true }, axisLine: { lineStyle: { color: colors.border } } },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { color: colors.muted }, splitLine: { lineStyle: { color: colors.border, opacity: 0.45 } } },
    series: (['HUMAN', 'AGENT', 'INTEGRATION'] as const).filter((source) => selected.includes(source)).map((source) => ({
      name: t(`runtimeLogs.source.${source}`), type: mode === 'bar' ? 'bar' : 'line', smooth: 0.28,
      symbol: 'circle', symbolSize: 5, barMaxWidth: 22,
      data: data.bySource[source], ...(mode === 'area' ? { areaStyle: { opacity: 0.12 } } : {}),
      lineStyle: { width: 2.5 },
    })),
  }), [t]);
  const riskOption = useCallback<ChartOptionFactory>((colors, mode, selected, data) => ({
    color: [colors.red, colors.amber], tooltip: { trigger: 'axis', renderMode: 'richText' },
    legend: { bottom: 0, textStyle: { color: colors.muted } },
    grid: { left: 42, right: 16, top: 20, bottom: 56 },
    xAxis: { type: 'category', data: data.labels, axisLabel: { color: colors.muted, hideOverlap: true }, axisLine: { lineStyle: { color: colors.border } } },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { color: colors.muted }, splitLine: { lineStyle: { color: colors.border, opacity: 0.45 } } },
    series: riskChoices.filter((item) => selected.includes(item.value)).map((item) => ({
      name: item.label, type: mode === 'mixed' ? (item.value === 'failed' ? 'bar' : 'line') : mode,
      data: item.value === 'failed' ? data.failed : data.blocked,
      smooth: true, barMaxWidth: 22, itemStyle: { borderRadius: [4, 4, 0, 0] }, lineStyle: { width: 2.5 },
    })),
  }), [riskChoices]);
  const sourceOption = useCallback<ChartOptionFactory>((colors, mode, selected) => ({
    color: [colors.primary, colors.cyan, colors.amber],
    tooltip: { trigger: mode === 'donut' ? 'item' : 'axis', renderMode: 'richText' },
    ...(mode === 'donut' ? { legend: { bottom: 0, textStyle: { color: colors.muted } } } : {
      grid: { left: 110, right: 28, top: 16, bottom: 24 },
      xAxis: { type: 'value', minInterval: 1, axisLabel: { color: colors.muted }, splitLine: { lineStyle: { color: colors.border, opacity: 0.45 } } },
      yAxis: { type: 'category', inverse: true, data: overview?.sources.filter((item) => selected.includes(item.code)).map((item) => t(`runtimeLogs.source.${item.code}`)) ?? [], axisLabel: { color: colors.text } },
    }),
    series: mode === 'donut'
      ? [{ type: 'pie', radius: ['52%', '73%'], center: ['50%', '44%'], label: { color: colors.text, formatter: '{d}%' },
        data: overview?.sources.filter((item) => selected.includes(item.code)).map((item) => ({ name: t(`runtimeLogs.source.${item.code}`), value: item.total })) ?? [] }]
      : [{ type: 'bar', barMaxWidth: 25, data: overview?.sources.filter((item) => selected.includes(item.code)).map((item) => item.total) ?? [] }],
  }), [overview, t]);
  const errorOption = useCallback<ChartOptionFactory>((colors, mode, selected) => ({
    color: [colors.red], tooltip: { trigger: 'axis', renderMode: 'richText' },
    ...(mode === 'bar' ? {
      grid: { left: 136, right: 28, top: 16, bottom: 24 },
      xAxis: { type: 'value', minInterval: 1, axisLabel: { color: colors.muted }, splitLine: { lineStyle: { color: colors.border, opacity: 0.45 } } },
      yAxis: { type: 'category', inverse: true, data: overview?.errorCodes.filter((item) => selected.includes(item.code)).map((item) => item.code) ?? [],
        axisLabel: { color: colors.text, width: 120, overflow: 'truncate' }, axisLine: { show: false }, axisTick: { show: false } },
    } : { tooltip: { trigger: 'item', renderMode: 'richText' }, legend: { bottom: 0, textStyle: { color: colors.muted } } }),
    series: mode === 'bar'
      ? [{ type: 'bar', barMaxWidth: 22, data: overview?.errorCodes.filter((item) => selected.includes(item.code)).map((item) => item.total) ?? [],
        itemStyle: { borderRadius: [0, 5, 5, 0] } }]
      : [{ type: 'pie', radius: ['52%', '73%'], center: ['50%', '44%'], label: { color: colors.text, formatter: '{d}%' },
        data: overview?.errorCodes.filter((item) => selected.includes(item.code)).map((item) => ({ name: item.code, value: item.total })) ?? [] }],
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
          <Button disabled={preferencesLoading || !!preferencesError || preferencesSaving} onClick={() => setLayoutOpen(true)}>
            {t('platformObservability.layout')}
          </Button>
          <Button type={preferencesDirty ? 'primary' : 'default'} loading={preferencesSaving}
            disabled={!preferencesDirty || preferencesLoading || !!preferencesError} onClick={() => void savePreferences()}>
            {t('platformObservability.savePreferences')}
          </Button>
          {canOpenLogs && <Button type="primary" icon={<OaIcon name="runtime-logs" />} onClick={() => navigate('/oa/runtime-logs')}>
            {t('platformObservability.openLogs')}
          </Button>}
        </div>
      </header>
      {preferencesError && <Alert type="error" showIcon title={t('platformObservability.preferencesError')}
        description={preferencesError} action={<Button onClick={() => void (preferencesDirty ? savePreferences() : loadPreferences())}>
          {t('common.retry')}
        </Button>} />}
      {error && <Alert type="error" showIcon title={error} action={<Button onClick={() => void load()}>{t('common.retry')}</Button>} />}
      {(loading && !overview) || preferencesLoading ? <Skeleton active paragraph={{ rows: 10 }} /> : overview && <>
        <div className="oa-observability-metrics">
          <Card><Statistic title={t('platformObservability.total')} value={overview.stats.total ?? 0} formatter={(value) => number.format(Number(value))} /></Card>
          <Card><Statistic title={t('platformObservability.successRate')} value={overview.stats.total ? overview.stats.succeeded / overview.stats.total * 100 : 0} precision={1} suffix="%" /></Card>
          <Card><Statistic title={t('platformObservability.failed')} value={overview.stats.failed ?? 0} formatter={(value) => number.format(Number(value))} /></Card>
          <Card><Statistic title={t('platformObservability.blocked')} value={overview.stats.blocked ?? 0} formatter={(value) => number.format(Number(value))} /></Card>
          <Card><Statistic title={t('platformObservability.p95')} value={overview.p95DurationMs ?? 0} suffix="ms" /></Card>
        </div>
        <div className="oa-observability-grid">
          {charts.map((chart) => {
            const kind = chartKind(chart);
            const requiresAlternate = (kind === 'volume' || kind === 'risk')
              && chart.granularity !== 'auto' && chart.granularity !== undefined
              && chart.granularity !== overview.interval;
            const chartSeries = requiresAlternate && alternateReady ? alternateSeries : series;
            const settings = kind === 'volume'
              ? { subtitle: t('platformObservability.volumeSubtitle'), dataCount: overview.stats.total ? chartSeries.buckets.length : 0, option: volumeOption, modes: volumeModes, content: sourceChoices }
              : kind === 'risk'
                ? { subtitle: t('platformObservability.riskSubtitle'), dataCount: overview.stats.total ? chartSeries.buckets.length : 0, option: riskOption, modes: riskModes, content: riskChoices }
                : kind === 'source'
                  ? { subtitle: t('platformObservability.sourceSubtitle'), dataCount: overview.sources.length, option: sourceOption, modes: distributionModes, content: sourceChoices }
                  : { subtitle: t('platformObservability.errorSubtitle'),
                    dataCount: overview.errorCodes.filter((item) => !chart.content.length || chart.content.includes(item.code)).length,
                    option: errorOption, modes: rankingModes, content: errorChoices };
            return <div key={chart.id} className={`oa-observability-slot${chart.size === 'wide' ? ' oa-observability-slot-wide' : ''}`}>
              {requiresAlternate && !alternateReady
                ? <Card className="oa-observability-chart" title={chart.title?.trim() || chartTitles[kind]}>
                  {alternateError ? <Alert type="error" showIcon title={alternateError}
                    action={<Button onClick={() => setAlternateRetry((value) => value + 1)}>{t('common.retry')}</Button>} />
                    : <Skeleton active loading={alternateLoading || !alternateReady} paragraph={{ rows: 5 }} />}
                </Card>
                : <ChartPanel title={chart.title?.trim() || chartTitles[kind]} preference={chart} onChange={updateChart}
                    disabled={preferencesSaving || !!preferencesError} seriesData={chartSeries} {...settings} />}
            </div>;
          })}
        </div>
        <Typography.Text type="secondary" className="oa-observability-footnote">{t('platformObservability.footnote')}</Typography.Text>
      </>}
      <Modal title={t('platformObservability.layout')} open={layoutOpen} onCancel={() => setLayoutOpen(false)}
        width={800} footer={<Button type="primary" onClick={() => setLayoutOpen(false)}>{t('common.confirm')}</Button>}>
        <Typography.Paragraph type="secondary">{t('platformObservability.layoutHelp')}</Typography.Paragraph>
        <Space wrap className="oa-observability-add-chart">
          <Select<ObservabilityChartKind> aria-label={t('platformObservability.newChartKind')} value={newKind}
            onChange={setNewKind} style={{ width: 180 }} options={(Object.keys(chartTitles) as ObservabilityChartKind[])
              .map((value) => ({ value, label: chartTitles[value] }))} />
          <Button disabled={charts.length >= 12} onClick={() => addChart(newKind)}>{t('platformObservability.addChart')}</Button>
          <Typography.Text type="secondary">{t('platformObservability.chartLimit', { count: charts.length })}</Typography.Text>
        </Space>
        <div className="oa-observability-layout-list">
          {charts.map((chart, index) => <div key={chart.id} className="oa-observability-layout-row">
            <div className="oa-observability-layout-name">
              <Typography.Text strong>{chartTitles[chartKind(chart)]}</Typography.Text>
              <Input aria-label={t('platformObservability.customTitleFor', { title: chartTitles[chartKind(chart)] })}
                value={chart.title ?? ''} maxLength={40} placeholder={t('platformObservability.customTitle')}
                onChange={(event) => updateChart({ ...chart, title: event.target.value })} />
            </div>
            <Space wrap>
              {(chartKind(chart) === 'volume' || chartKind(chart) === 'risk') &&
                <Select aria-label={t('platformObservability.granularityFor', { title: chart.title || chartTitles[chartKind(chart)] })}
                  value={chart.granularity ?? 'auto'} style={{ width: 112 }}
                  onChange={(granularity) => updateChart({ ...chart, granularity })}
                  options={[{ value: 'auto', label: t('platformObservability.granularity.auto') },
                    { value: 'hour', label: t('platformObservability.granularity.hour') },
                    { value: 'day', label: t('platformObservability.granularity.day') }]} />}
              <Select aria-label={t('platformObservability.chartSizeFor', { title: chart.title || chartTitles[chartKind(chart)] })}
                value={chart.size} style={{ width: 112 }} onChange={(size) => updateChart({ ...chart, size })}
                options={[{ value: 'normal', label: t('platformObservability.normal') }, { value: 'wide', label: t('platformObservability.wide') }]} />
              <Button disabled={index === 0} onClick={() => moveChart(chart.id, -1)}>{t('platformObservability.moveUp')}</Button>
              <Button disabled={index === charts.length - 1} onClick={() => moveChart(chart.id, 1)}>{t('platformObservability.moveDown')}</Button>
              <Button disabled={charts.length >= 12} onClick={() => duplicateChart(chart.id)}>{t('platformObservability.duplicate')}</Button>
              <Button danger disabled={charts.length === 1} onClick={() => removeChart(chart.id)}>{t('common.delete')}</Button>
            </Space>
          </div>)}
        </div>
      </Modal>
    </section>
  );
}

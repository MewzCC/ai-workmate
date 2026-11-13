'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Select,
  Space,
  Statistic,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useTranslation } from 'react-i18next';
import { useLocation } from 'react-router-dom';
import { OaIcon } from '@/components/OaIcon';
import { useIsMobile } from '@/hooks/useIsMobile';
import { message } from '@/lib/antdMessage';
import { formatOaApiError } from '@/lib/oaApi';
import {
  runtimeLogApi,
  type RuntimeLogActorType,
  type RuntimeLogDetail,
  type RuntimeLogOutcome,
  type RuntimeLogGroup,
  type RuntimeLogRecord,
  type RuntimeLogSource,
} from '@/lib/runtimeLogApi';
import ResponsiveTable from './ResponsiveTable';
import { useAuth } from '@/components/auth/AuthProvider';
import { useRegisterPageUiCommands } from './PageUiCommandBridge';
import { usePublishPageAgentContext } from './PageAgentContext';
import { requireUiFieldTypes } from '@/lib/preparedPageUiCommand';
import { requireUiPagination, uiDateRange } from '@/hooks/usePageUiEditorCommands';

const { RangePicker } = DatePicker;
const SOURCES: RuntimeLogSource[] = ['HUMAN', 'AGENT', 'INTEGRATION'];
const OUTCOMES: RuntimeLogOutcome[] = ['RUNNING', 'SUCCEEDED', 'REJECTED', 'FAILED', 'TIMED_OUT', 'RESULT_INVALID'];
const OUTCOME_COLORS: Record<RuntimeLogOutcome, string> = {
  RUNNING: 'processing',
  SUCCEEDED: 'success',
  REJECTED: 'warning',
  FAILED: 'error',
  TIMED_OUT: 'volcano',
  RESULT_INVALID: 'magenta',
};
const ACTOR_COLORS: Record<RuntimeLogActorType, string> = {
  HUMAN: 'blue',
  AI_AGENT: 'purple',
  SYSTEM: 'cyan',
};
const SOURCE_COLORS: Record<RuntimeLogSource, string> = {
  HUMAN: 'blue',
  AGENT: 'purple',
  INTEGRATION: 'cyan',
};

const formatRuntimeTime = (value: string | undefined, locale: string) => value
  ? new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value))
  : '-';

function readDrilldown(search: string) {
  const params = new URLSearchParams(search);
  const from = params.get('from');
  const to = params.get('to');
  const safeRange = from && to && /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?$/.test(from)
    && /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?$/.test(to)
    && dayjs(from).isValid() && dayjs(to).isValid() && !dayjs(from).isAfter(dayjs(to))
    && dayjs(to).diff(dayjs(from), 'day', true) <= 31;
  const source = params.get('source');
  const outcome = params.get('outcome');
  const group = params.get('group');
  const errorCode = params.get('errorCode');
  return {
    source: SOURCES.includes(source as RuntimeLogSource) ? source as RuntimeLogSource : undefined,
    outcome: OUTCOMES.includes(outcome as RuntimeLogOutcome) ? outcome as RuntimeLogOutcome : undefined,
    group: (group === 'FAILED' || group === 'BLOCKED') ? group as RuntimeLogGroup : undefined,
    errorCode: errorCode && /^[A-Za-z0-9_.:-]{1,64}$/.test(errorCode) ? errorCode : '',
    range: safeRange ? [dayjs(from), dayjs(to)] as [Dayjs, Dayjs] : undefined,
    exactRange: safeRange ? { from: from as string, to: to as string } : undefined,
    toExclusive: safeRange && params.get('toExclusive') === 'true',
  };
}

export default function RuntimeLogsPage() {
  const { t, i18n } = useTranslation();
  const { user } = useAuth();
  const scope = `${user?.tenantId}:${user?.id}:${user?.permissionVersion}`;
  const activeScope = useRef<string | undefined>(scope); activeScope.current = scope;
  const listRequest = useRef(0), detailRequest = useRef(0);
  const location = useLocation();
  const drilldown = useMemo(() => readDrilldown(location.search), [location.search]);
  const previousSearch = useRef(location.search);
  const isMobile = useIsMobile();
  const [storedRecords, setRecords] = useState<RuntimeLogRecord[]>([]);
  const [storedStats, setStats] = useState({ total: 0, succeeded: 0, failed: 0, blocked: 0, averageDurationMs: 0 });
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [storedDetail, setDetail] = useState<RuntimeLogDetail>();
  const [detailOpen, setDetailOpen] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [keyword, setKeyword] = useState('');
  const [source, setSource] = useState<RuntimeLogSource | undefined>(drilldown.source);
  const [outcome, setOutcome] = useState<RuntimeLogOutcome | undefined>(drilldown.outcome);
  const [group, setGroup] = useState<RuntimeLogGroup | undefined>(drilldown.group);
  const [errorCode, setErrorCode] = useState(drilldown.errorCode);
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => drilldown.range ?? [dayjs().subtract(7, 'day'), dayjs()]);
  const [exactRange, setExactRange] = useState(drilldown.exactRange);
  const [toExclusive, setToExclusive] = useState(drilldown.toExclusive);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [dataScope, setDataScope] = useState(scope);
  const records = dataScope === scope ? storedRecords : [];
  const detail = dataScope === scope ? storedDetail : undefined;
  const stats = dataScope === scope ? storedStats : { total: 0, succeeded: 0, failed: 0, blocked: 0, averageDurationMs: 0 };

  useEffect(() => {
    if (previousSearch.current === location.search) return;
    previousSearch.current = location.search;
    setSource(drilldown.source);
    setOutcome(drilldown.outcome);
    setGroup(drilldown.group);
    setErrorCode(drilldown.errorCode);
    setRange(drilldown.range ?? [dayjs().subtract(7, 'day'), dayjs()]);
    setExactRange(drilldown.exactRange);
    setToExclusive(drilldown.toExclusive);
    setPage(1);
  }, [drilldown, location.search]);

  const load = useCallback(async () => {
    const ticket = ++listRequest.current;
    setLoading(true);
    try {
      const response = await runtimeLogApi.list({
        source,
        outcome,
        group,
        errorCode: errorCode || undefined,
        keyword: keyword || undefined,
        from: exactRange?.from ?? range[0].format('YYYY-MM-DDTHH:mm:ss'),
        to: exactRange?.to ?? range[1].format('YYYY-MM-DDTHH:mm:ss'),
        toExclusive: toExclusive || undefined,
        page,
        size,
      });
      if (ticket !== listRequest.current || activeScope.current !== scope) return;
      setDataScope(scope); setRecords(response.records);
      setStats(response.stats);
      setTotal(response.total);
    } catch (error) {
      if (ticket !== listRequest.current || activeScope.current !== scope) return;
      setRecords([]); setTotal(0); setStats({ total: 0, succeeded: 0, failed: 0, blocked: 0, averageDurationMs: 0 });
      message.error(formatOaApiError(error));
    } finally {
      if (ticket === listRequest.current && activeScope.current === scope) setLoading(false);
    }
  }, [errorCode, exactRange, group, keyword, outcome, page, range, size, source, toExclusive, scope]);

  useEffect(() => {
    activeScope.current = scope; setRecords([]); setDetail(undefined); setDetailOpen(false);
    return () => { activeScope.current = undefined; listRequest.current += 1; detailRequest.current += 1; };
  }, [scope]);
  useEffect(() => { void load(); return () => { listRequest.current += 1; }; }, [load]);

  const openDetail = useCallback(async (record: Pick<RuntimeLogRecord, 'source' | 'id'>, reportError = true) => {
    const ticket = ++detailRequest.current;
    setDetailOpen(true);
    setDetail(undefined);
    setDetailLoading(true);
    try {
      const result = await runtimeLogApi.detail(record.source, record.id);
      if (ticket !== detailRequest.current || activeScope.current !== scope) return;
      if (result.id !== record.id || result.source !== record.source) throw new Error('RESOURCE_MISMATCH');
      setDataScope(scope); setDetail(result);
    } catch (error) {
      if (ticket !== detailRequest.current || activeScope.current !== scope) return;
      setDetailOpen(false);
      if (!reportError) throw error;
      message.error(formatOaApiError(error));
    } finally {
      if (ticket === detailRequest.current && activeScope.current === scope) setDetailLoading(false);
    }
  }, [scope]);
  usePublishPageAgentContext({ source, outcome, group, errorCode: errorCode || undefined, keyword: keyword || undefined,
    from: exactRange?.from ?? range[0].format('YYYY-MM-DDTHH:mm:ss'), to: exactRange?.to ?? range[1].format('YYYY-MM-DDTHH:mm:ss'),
    toExclusive, page, size, recordId: detail?.id });
  useRegisterPageUiCommands({ 'ui.refreshPage': load,
    'ui.openDetail': async ({ payload: { recordId } }) => {
      const id = Number(recordId), matches = records.filter(item => item.id === id && (!source || item.source === source));
      if (!Number.isSafeInteger(id) || id < 1 || !source && matches.length !== 1) throw new Error('PAGE_UI_FIELD_REJECTED');
      await openDetail({ id, source: source ?? matches[0].source }, false);
    },
    'ui.applyFilter': ({ payload: { filters } }) => {
      requireUiFieldTypes(filters, { source: SOURCES, outcome: OUTCOMES, group: ['FAILED', 'BLOCKED'], errorCode: 'string', keyword: 'string',
        from: 'string', to: 'string', toExclusive: 'boolean', page: 'number', size: 'number' });
      requireUiPagination(filters);
      const dates = uiDateRange(filters, [range[0].format('YYYY-MM-DDTHH:mm:ss'), range[1].format('YYYY-MM-DDTHH:mm:ss')]);
      if (dates && dayjs(dates[1]).diff(dayjs(dates[0]), 'day', true) > 31) throw new Error('PAGE_UI_FIELD_REJECTED');
      if (filters.errorCode !== undefined && !/^[A-Za-z0-9_.:-]{0,64}$/.test(String(filters.errorCode))) throw new Error('PAGE_UI_FIELD_REJECTED');
      if (filters.source !== undefined) setSource(filters.source as RuntimeLogSource);
      if (filters.outcome !== undefined) setOutcome(filters.outcome as RuntimeLogOutcome);
      if (filters.group !== undefined) setGroup(filters.group as RuntimeLogGroup);
      if (filters.keyword !== undefined) { setKeyword(String(filters.keyword)); setSearchText(String(filters.keyword)); }
      if (filters.errorCode !== undefined) {
        setErrorCode(String(filters.errorCode));
      }
      if (dates && (filters.from !== undefined || filters.to !== undefined)) { setRange([dayjs(dates[0]), dayjs(dates[1])]); setExactRange(undefined); setToExclusive(false); }
      if (filters.toExclusive !== undefined) setToExclusive(Boolean(filters.toExclusive));
      setPage(Number(filters.page ?? 1)); if (filters.size !== undefined) setSize(Number(filters.size));
    },
  }, null, `runtime-logs:${source ?? 'ALL'}`);

  const columns = useMemo<ColumnsType<RuntimeLogRecord>>(() => [
    {
      title: t('runtimeLogs.columns.startedAt'),
      dataIndex: 'startedAt',
      width: 188,
      render: (value: string) => formatRuntimeTime(value, i18n.language),
    },
    {
      title: t('runtimeLogs.columns.source'),
      dataIndex: 'source',
      width: 126,
      render: (value: RuntimeLogSource) => (
        <Tag color={SOURCE_COLORS[value]}>{t(`runtimeLogs.source.${value}`)}</Tag>
      ),
    },
    {
      title: t('runtimeLogs.columns.actorType'),
      dataIndex: 'actorType',
      width: 132,
      render: (value: RuntimeLogActorType) => (
        <Tag color={ACTOR_COLORS[value]}>{t(`runtimeLogs.actorType.${value}`)}</Tag>
      ),
    },
    {
      title: t('runtimeLogs.columns.operation'),
      key: 'operation',
      minWidth: 260,
      render: (_, record) => (
        <div className="oa-runtime-operation">
          <Typography.Text strong>{record.operation}</Typography.Text>
          <Space size={6} wrap>
            <Tag variant="filled">{t(`runtimeLogs.eventType.${record.eventType}`)}</Tag>
            <Typography.Text type="secondary" copyable>{record.referenceCode}</Typography.Text>
          </Space>
        </div>
      ),
    },
    {
      title: t('runtimeLogs.columns.outcome'),
      dataIndex: 'outcome',
      width: 132,
      render: (value: RuntimeLogOutcome) => (
        <Tag color={OUTCOME_COLORS[value]}>{t(`runtimeLogs.outcome.${value}`)}</Tag>
      ),
    },
    {
      title: t('runtimeLogs.columns.duration'),
      dataIndex: 'durationMs',
      width: 118,
      render: (value?: number) => value == null ? '-' : t('runtimeLogs.durationMs', { value }),
    },
    { title: t('runtimeLogs.columns.operator'), dataIndex: 'operatorLabel', width: 148, ellipsis: true },
    {
      title: t('runtimeLogs.columns.trace'),
      dataIndex: 'traceId',
      width: 180,
      ellipsis: true,
      render: (value?: string) => value ? <Typography.Text code copyable>{value}</Typography.Text> : '-',
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 92,
      fixed: 'right',
      render: (_, record) => <Button type="link" onClick={() => void openDetail(record)}>{t('common.view')}</Button>,
    },
  ], [i18n.language, t, openDetail]);

  const detailItems = detail ? [
    { key: 'source', label: t('runtimeLogs.detail.source'), children: <Tag color={SOURCE_COLORS[detail.source]}>{t(`runtimeLogs.source.${detail.source}`)}</Tag> },
    { key: 'actorType', label: t('runtimeLogs.detail.actorType'), children: <Tag color={ACTOR_COLORS[detail.actorType]}>{t(`runtimeLogs.actorType.${detail.actorType}`)}</Tag> },
    { key: 'eventType', label: t('runtimeLogs.detail.eventType'), children: t(`runtimeLogs.eventType.${detail.eventType}`) },
    { key: 'outcome', label: t('runtimeLogs.detail.outcome'), children: <Tag color={OUTCOME_COLORS[detail.outcome]}>{t(`runtimeLogs.outcome.${detail.outcome}`)}</Tag> },
    { key: 'reference', label: t('runtimeLogs.detail.reference'), children: <Typography.Text copyable>{detail.referenceCode}</Typography.Text> },
    { key: 'operation', label: t('runtimeLogs.detail.operation'), children: detail.operation },
    { key: 'operator', label: t('runtimeLogs.detail.operator'), children: detail.operatorLabel },
    { key: 'duration', label: t('runtimeLogs.detail.duration'), children: detail.durationMs == null ? '-' : t('runtimeLogs.durationMs', { value: detail.durationMs }) },
    { key: 'started', label: t('runtimeLogs.detail.startedAt'), children: formatRuntimeTime(detail.startedAt, i18n.language) },
    { key: 'completed', label: t('runtimeLogs.detail.completedAt'), children: formatRuntimeTime(detail.completedAt, i18n.language) },
    { key: 'trace', label: t('runtimeLogs.detail.trace'), span: isMobile ? 1 : 2, children: detail.traceId ? <Typography.Text code copyable>{detail.traceId}</Typography.Text> : '-' },
    { key: 'fingerprint', label: t('runtimeLogs.detail.fingerprint'), span: isMobile ? 1 : 2, children: detail.requestFingerprint ? <Typography.Text code copyable>{detail.requestFingerprint}</Typography.Text> : '-' },
    { key: 'decision', label: t('runtimeLogs.detail.decision'), children: detail.decision || '-' },
    { key: 'decisionCode', label: t('runtimeLogs.detail.decisionCode'), children: detail.decisionCode || '-' },
    { key: 'statusCode', label: t('runtimeLogs.detail.statusCode'), children: detail.statusCode ?? '-' },
    { key: 'attempt', label: t('runtimeLogs.detail.attempt'), children: detail.attempt ?? '-' },
    { key: 'resultBytes', label: t('runtimeLogs.detail.resultBytes'), children: detail.resultBytes ?? '-' },
    { key: 'errorCode', label: t('runtimeLogs.detail.errorCode'), children: detail.errorCode || '-' },
    { key: 'clientIp', label: t('runtimeLogs.detail.clientIp'), children: detail.clientIp || '-' },
    { key: 'userAgent', label: t('runtimeLogs.detail.userAgent'), children: detail.userAgent || '-' },
  ] : [];

  return (
    <section className="oa-runtime-logs-page">
      <header className="oa-runtime-logs-header">
        <div className="oa-runtime-logs-heading">
          <span><OaIcon name="runtime-logs" size={24} /></span>
          <div>
            <Typography.Text className="oa-runtime-logs-eyebrow">{t('runtimeLogs.eyebrow')}</Typography.Text>
            <Typography.Title level={3}>{t('runtimeLogs.title')}</Typography.Title>
            <Typography.Paragraph>{t('runtimeLogs.subtitle')}</Typography.Paragraph>
          </div>
        </div>
        <Space orientation="vertical" align="end">
          <Tag color="blue" icon={<OaIcon name="lock" />}>{t('runtimeLogs.readOnlyBadge')}</Tag>
          <Typography.Text type="secondary">{t('runtimeLogs.retentionHint')}</Typography.Text>
        </Space>
      </header>

      <div className="oa-runtime-logs-stats">
        <Card><Statistic title={t('runtimeLogs.stats.total')} value={stats.total} /></Card>
        <Card><Statistic title={t('runtimeLogs.stats.succeeded')} value={stats.succeeded} /></Card>
        <Card><Statistic title={t('runtimeLogs.stats.failed')} value={stats.failed} /></Card>
        <Card><Statistic title={t('runtimeLogs.stats.blocked')} value={stats.blocked} /></Card>
        <Card><Statistic title={t('runtimeLogs.stats.average')} value={stats.averageDurationMs} suffix="ms" /></Card>
      </div>

      <Card className="oa-runtime-logs-card">
        <Alert type="info" showIcon title={t('runtimeLogs.boundaryTitle')} description={t('runtimeLogs.boundaryDescription')} />
        <div className="oa-runtime-logs-toolbar">
          <Input.Search
            allowClear
            value={searchText}
            placeholder={t('runtimeLogs.filters.keyword')}
            onChange={(event) => {
              setSearchText(event.target.value);
              if (!event.target.value) { setKeyword(''); setPage(1); }
            }}
            onSearch={(value) => { setKeyword(value.trim()); setPage(1); }}
          />
          <Select
            allowClear
            value={source}
            placeholder={t('runtimeLogs.filters.source')}
            options={SOURCES.map((value) => ({ value, label: t(`runtimeLogs.source.${value}`) }))}
            onChange={(value) => { setSource(value); setPage(1); }}
          />
          <Select
            allowClear
            value={outcome}
            placeholder={t('runtimeLogs.filters.outcome')}
            options={OUTCOMES.map((value) => ({ value, label: t(`runtimeLogs.outcome.${value}`) }))}
            onChange={(value) => { setOutcome(value); setPage(1); }}
          />
          <Select
            allowClear
            value={group}
            placeholder={t('runtimeLogs.filters.group')}
            options={(['FAILED', 'BLOCKED'] as const).map((value) => ({ value, label: t(`runtimeLogs.groups.${value}`) }))}
            onChange={(value) => { setGroup(value); setPage(1); }}
          />
          <Input
            allowClear
            value={errorCode}
            maxLength={64}
            placeholder={t('runtimeLogs.filters.errorCode')}
            onChange={(event) => { setErrorCode(event.target.value); setPage(1); }}
          />
          <RangePicker
            showTime
            allowClear={false}
            value={range}
            onChange={(value) => {
              if (value?.[0] && value[1]) {
                setRange([value[0], value[1]]);
                setExactRange(undefined);
                setToExclusive(false);
                setPage(1);
              }
            }}
          />
          <Button icon={<OaIcon name="reload" />} onClick={() => void load()}>{t('common.refresh')}</Button>
        </div>
        <ResponsiveTable
          rowKey={(record) => `${record.source}-${record.id}`}
          columns={columns}
          dataSource={records}
          loading={loading}
          scroll={{ x: 1420 }}
          locale={{ emptyText: <Empty description={t('runtimeLogs.empty')} /> }}
          pagination={{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: true,
            onChange: (nextPage, nextSize) => { setPage(nextPage); setSize(nextSize); },
          }}
        />
      </Card>

      <Drawer
        open={detailOpen}
        size={720}
        loading={detailLoading}
        title={t('runtimeLogs.detail.title')}
        onClose={() => { detailRequest.current += 1; setDetailOpen(false); }}
      >
        {detail ? (
          <div className="oa-runtime-log-detail">
            <Alert type="info" showIcon title={t('runtimeLogs.detail.safeTitle')} description={t('runtimeLogs.detail.safeDescription')} />
            <Descriptions bordered column={isMobile ? 1 : 2} items={detailItems} />
            <Typography.Title level={5}>{t('runtimeLogs.detail.preview')}</Typography.Title>
            {detail.detailPreview
              ? <pre>{detail.detailPreview}</pre>
              : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('runtimeLogs.detail.noPreview')} />}
          </div>
        ) : null}
      </Drawer>
    </section>
  );
}

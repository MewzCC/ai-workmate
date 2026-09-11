'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
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
import { OaIcon } from '@/components/OaIcon';
import { useIsMobile } from '@/hooks/useIsMobile';
import { message } from '@/lib/antdMessage';
import { formatOaApiError } from '@/lib/oaApi';
import {
  runtimeLogApi,
  type RuntimeLogDetail,
  type RuntimeLogOutcome,
  type RuntimeLogRecord,
  type RuntimeLogSource,
} from '@/lib/runtimeLogApi';
import ResponsiveTable from './ResponsiveTable';

const { RangePicker } = DatePicker;
const SOURCES: RuntimeLogSource[] = ['INTEGRATION', 'AGENT'];
const OUTCOMES: RuntimeLogOutcome[] = ['RUNNING', 'SUCCEEDED', 'REJECTED', 'FAILED', 'TIMED_OUT', 'RESULT_INVALID'];
const OUTCOME_COLORS: Record<RuntimeLogOutcome, string> = {
  RUNNING: 'processing',
  SUCCEEDED: 'success',
  REJECTED: 'warning',
  FAILED: 'error',
  TIMED_OUT: 'volcano',
  RESULT_INVALID: 'magenta',
};

const formatRuntimeTime = (value: string | undefined, locale: string) => value
  ? new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value))
  : '-';

export default function RuntimeLogsPage() {
  const { t, i18n } = useTranslation();
  const isMobile = useIsMobile();
  const [records, setRecords] = useState<RuntimeLogRecord[]>([]);
  const [stats, setStats] = useState({ total: 0, succeeded: 0, failed: 0, blocked: 0, averageDurationMs: 0 });
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<RuntimeLogDetail>();
  const [detailOpen, setDetailOpen] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [keyword, setKeyword] = useState('');
  const [source, setSource] = useState<RuntimeLogSource>();
  const [outcome, setOutcome] = useState<RuntimeLogOutcome>();
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => [dayjs().subtract(7, 'day'), dayjs()]);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await runtimeLogApi.list({
        source,
        outcome,
        keyword: keyword || undefined,
        from: range[0].format('YYYY-MM-DDTHH:mm:ss'),
        to: range[1].format('YYYY-MM-DDTHH:mm:ss'),
        page,
        size,
      });
      setRecords(response.records);
      setStats(response.stats);
      setTotal(response.total);
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setLoading(false);
    }
  }, [keyword, outcome, page, range, size, source]);

  useEffect(() => { void load(); }, [load]);

  const openDetail = async (record: RuntimeLogRecord) => {
    setDetailOpen(true);
    setDetail(undefined);
    setDetailLoading(true);
    try {
      setDetail(await runtimeLogApi.detail(record.source, record.id));
    } catch (error) {
      message.error(formatOaApiError(error));
      setDetailOpen(false);
    } finally {
      setDetailLoading(false);
    }
  };

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
        <Tag color={value === 'AGENT' ? 'geekblue' : 'cyan'}>{t(`runtimeLogs.source.${value}`)}</Tag>
      ),
    },
    {
      title: t('runtimeLogs.columns.operation'),
      key: 'operation',
      minWidth: 260,
      render: (_, record) => (
        <div className="oa-runtime-operation">
          <Typography.Text strong>{record.operation}</Typography.Text>
          <Typography.Text type="secondary" copyable>{record.referenceCode}</Typography.Text>
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
  ], [i18n.language, t]);

  const detailItems = detail ? [
    { key: 'source', label: t('runtimeLogs.detail.source'), children: <Tag>{t(`runtimeLogs.source.${detail.source}`)}</Tag> },
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
          <RangePicker
            showTime
            allowClear={false}
            value={range}
            onChange={(value) => {
              if (value?.[0] && value[1]) {
                setRange([value[0], value[1]]);
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
          scroll={{ x: 1250 }}
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
        onClose={() => setDetailOpen(false)}
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

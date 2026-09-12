'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Col, Empty, Row, Space, Spin, Statistic, Tag, Timeline, Tooltip, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { EChartsOption } from 'echarts';
import { useTranslation } from 'react-i18next';
import { defaultDashboardExportRange, downloadDashboardExport, exportDashboard, getDashboardOverview, type DashboardMetricCode, type DashboardOverview } from '@/lib/dashboardApi';
import { formatOaApiError } from '@/lib/oaApi';
import { useRouter } from '@/lib/nextCompat';
import { message } from '@/lib/antdMessage';
import { usePermission } from '@/hooks/usePermission';
import { OaIcon } from '@/components/OaIcon';
import EChartsCard from './EChartsCard';
import ResponsiveTable from './ResponsiveTable';

interface DashboardProps {
  primaryColor: string;
  onOpenAi: (prompt?: string) => void;
}

const METRIC_COLORS: Record<DashboardMetricCode, string> = {
  PENDING_TODOS: '#1677ff',
  OVERDUE_TODOS: '#f97316',
  MY_APPLICATIONS: '#7c3aed',
  UNREAD_MESSAGES: '#0891b2',
};

export default function Dashboard({ primaryColor, onOpenAi }: DashboardProps) {
  const router = useRouter();
  const { t, i18n } = useTranslation();
  const [overview, setOverview] = useState<DashboardOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<string>();
  const { allowed: canExport } = usePermission('data:export');

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setOverview(await getDashboardOverview(7));
    } catch (cause) {
      setError(formatOaApiError(cause));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const handleExport = async () => {
    setExporting(true);
    try {
      const response = await exportDashboard(defaultDashboardExportRange(7));
      downloadDashboardExport(response);
      message.success(t('dashboard.messages.exported', { count: response.rowCount }));
    } catch (cause) {
      message.error(formatOaApiError(cause));
    } finally {
      setExporting(false);
    }
  };

  const dateLocale = i18n.resolvedLanguage === 'en-US' ? 'en-US' : 'zh-CN';
  const formatDateTime = (value?: string | null) => value
    ? new Intl.DateTimeFormat(dateLocale, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
    : '-';

  const columns: ColumnsType<DashboardOverview['todos'][number]> = [
    { title: t('dashboard.columns.processName'), dataIndex: 'title', key: 'title', ellipsis: true, minWidth: 180 },
    { title: t('dashboard.columns.applicant'), dataIndex: 'applicantName', key: 'applicantName', width: 120 },
    {
      title: t('dashboard.columns.businessType'), dataIndex: 'businessType', key: 'businessType', width: 150,
      render: (value: string) => t(`dashboard.businessTypes.${value}`, { defaultValue: value }),
    },
    {
      title: t('dashboard.columns.submittedAt'), dataIndex: 'submittedAt', key: 'submittedAt', width: 180,
      render: (value: string) => formatDateTime(value),
    },
    {
      title: t('dashboard.columns.dueAt'), dataIndex: 'dueAt', key: 'dueAt', width: 180,
      render: (value: string | null, record) => record.overdue
        ? <Tag color="error">{t('dashboard.status.overdue')}</Tag>
        : formatDateTime(value),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 236,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Button aria-label={t('dashboard.actions.process')} type="primary" size="small" onClick={() => router.push(`/oa/approval-tasks/${record.taskId}?from=dashboard`)}>
            {t('dashboard.actions.process')}
          </Button>
          <Button aria-label={t('dashboard.actions.view')} size="small" onClick={() => router.push(`/oa/approval-tasks/${record.taskId}?from=dashboard`)}>
            {t('dashboard.actions.view')}
          </Button>
          <Button aria-label={t('dashboard.actions.preReview')} size="small" icon={<OaIcon name="ai" />} onClick={() => onOpenAi(t('dashboard.aiPrompts.preReviewTask', { taskId: record.taskId }))}>
            {t('dashboard.actions.preReview')}
          </Button>
        </Space>
      ),
    },
  ];

  const chartOptions = useMemo(() => overview ? createChartOptions(
    overview,
    primaryColor,
    t('dashboard.chart.submitted'),
    t('dashboard.chart.completed'),
    (type) => t(`dashboard.businessTypes.${type}`, { defaultValue: type }),
  ) : null, [overview, primaryColor, t]);

  return (
    <div className="oa-dashboard">
      <section className="oa-page-title">
        <div>
          <Typography.Text type="secondary">ENTERPRISE OPERATIONS</Typography.Text>
          <Typography.Title level={2}>{t('dashboard.title')}</Typography.Title>
          <Typography.Paragraph>{t('dashboard.description')}</Typography.Paragraph>
          {overview && <Typography.Text type="secondary">{t('dashboard.generatedAt', { time: formatDateTime(overview.generatedAt) })}</Typography.Text>}
        </div>
        <Space className="oa-page-title-actions" wrap>
          <Button icon={<OaIcon name="reload" />} loading={loading} onClick={() => void load()}>{t('common.refresh')}</Button>
          {canExport && <Button loading={exporting} icon={<OaIcon name="export" />} onClick={() => void handleExport()}>{t('dashboard.exportDashboard')}</Button>}
          <Tooltip title={t('dashboard.messages.metricsConfigComingSoon')}>
            <Button disabled icon={<OaIcon name="audit" />}>{t('dashboard.configMetrics')}</Button>
          </Tooltip>
          <Tooltip title={overview?.todos.length ? t('dashboard.messages.selectTodoForPreReview') : t('dashboard.messages.noTodoForPreReview')}>
            <Button disabled type="primary" icon={<OaIcon name="ai" />}>{t('dashboard.aiPreReview')}</Button>
          </Tooltip>
        </Space>
      </section>

      {error && <Alert type="error" showIcon title={t('dashboard.loadFailed')} description={error} action={<Button size="small" onClick={() => void load()}>{t('common.retry')}</Button>} />}

      {loading && !overview ? (
        <Card className="oa-card oa-placeholder-card"><Spin size="large" description={t('common.loading')} /></Card>
      ) : overview && (
        <>
          <Row gutter={[16, 16]}>
            {overview.metrics.map((metric) => (
              <Col xs={12} sm={12} xl={6} key={metric.code}>
                <Card className="oa-card oa-stat-card">
                  <Statistic title={t(`dashboard.metrics.${metric.code}.title`)} value={metric.value} styles={{ content: { color: METRIC_COLORS[metric.code] } }} />
                  <Typography.Text type="secondary">{t(`dashboard.metrics.${metric.code}.description`)}</Typography.Text>
                </Card>
              </Col>
            ))}
          </Row>

          <Card className="oa-card" title={t('dashboard.cards.todoList')}>
            <ResponsiveTable
              rowKey="taskId"
              columns={columns}
              dataSource={overview.todos}
              pagination={false}
              scroll={{ x: 'max-content' }}
              locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('dashboard.empty.todos')} /> }}
            />
          </Card>

          <Row gutter={[16, 16]}>
            <Col xs={24} xl={12}>
              {chartOptions && <EChartsCard title={t('dashboard.charts.processTrend')} option={chartOptions.line} />}
            </Col>
            <Col xs={24} xl={12}>
              {overview.businessDistribution.length > 0 && chartOptions
                ? <EChartsCard title={t('dashboard.charts.moduleDistribution')} option={chartOptions.pie} />
                : <Card className="oa-card" title={t('dashboard.charts.moduleDistribution')}><Empty description={t('dashboard.empty.distribution')} /></Card>}
            </Col>
          </Row>

          <Card className="oa-card" title={t('dashboard.cards.recentActivities')}>
            {overview.recentActivities.length > 0 ? (
              <Timeline items={overview.recentActivities.map((activity) => ({
                color: activity.result === 'SUCCESS' ? 'green' : activity.result === 'DENIED' ? 'red' : 'blue',
                children: <Space direction="vertical" size={0}>
                  <Typography.Text>{activity.summary || `${activity.resourceType} · ${activity.action}`}</Typography.Text>
                  <Typography.Text type="secondary">{formatDateTime(activity.createdAt)}</Typography.Text>
                </Space>,
              }))} />
            ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('dashboard.empty.activities')} />}
          </Card>
        </>
      )}
    </div>
  );
}

function createChartOptions(
  overview: DashboardOverview,
  primaryColor: string,
  submittedLabel: string,
  completedLabel: string,
  businessTypeLabel: (type: string) => string,
): Record<'line' | 'pie', EChartsOption> {
  return {
    line: {
      color: [primaryColor, '#22c55e'],
      tooltip: { trigger: 'axis', backgroundColor: 'rgba(15, 23, 42, 0.92)', borderColor: 'transparent', textStyle: { color: '#fff', fontSize: 12 } },
      legend: { data: [submittedLabel, completedLabel], bottom: 0 },
      grid: { left: 36, right: 18, top: 28, bottom: 48, containLabel: true },
      xAxis: { type: 'category', data: overview.trends.map((point) => point.date.slice(5)) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        { name: submittedLabel, type: 'line', smooth: true, data: overview.trends.map((point) => point.submitted), areaStyle: { opacity: 0.08 } },
        { name: completedLabel, type: 'line', smooth: true, data: overview.trends.map((point) => point.completed) },
      ],
    },
    pie: {
      color: [primaryColor, '#22c55e', '#f59e0b', '#8b5cf6', '#06b6d4'],
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, type: 'scroll' },
      series: [{ type: 'pie', radius: ['42%', '68%'], center: ['50%', '43%'], data: overview.businessDistribution.map((item) => ({ name: businessTypeLabel(item.businessType), value: item.value })), label: { formatter: '{b}\n{c}' } }],
    },
  };
}

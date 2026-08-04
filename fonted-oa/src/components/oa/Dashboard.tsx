'use client';

import { useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Dropdown,
  Empty,
  Input,
  Progress,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Timeline,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { EChartsOption } from 'echarts';
import { useTranslation } from 'react-i18next';
import { approvalRecords, oaMetrics, quickEntries, timelineSeed } from '@/mock/oaDashboard';
import { can } from '@/mock/oaPermissions';
import type { ApprovalRecord, OaRole } from '@/types/oa';
import EChartsCard from './EChartsCard';
import PermissionButton from './PermissionButton';
import { OaIcon } from '@/components/OaIcon';

interface DashboardProps {
  role: OaRole;
  pageId: string;
  pageTitle: string;
  primaryColor: string;
  auditItems: Array<{ color: string; content: string }>;
  onOpenAi: (prompt?: string) => void;
  onAddAudit: (text: string) => void;
}

const ACTION_PROCESS = 'process';
const ACTION_VIEW = 'view';
const ACTION_PRE_REVIEW = 'preReview';
const ACTION_APPROVE = 'approve';
const ACTION_RETURN = 'return';
const ACTION_REMIND = 'remind';
const APPROVAL_ACTIONS = [ACTION_PROCESS, ACTION_PRE_REVIEW, ACTION_APPROVE, ACTION_RETURN, ACTION_REMIND];
const AI_ACTIONS = [ACTION_PROCESS, ACTION_PRE_REVIEW, ACTION_APPROVE];

const tagColor: Record<ApprovalRecord['status'], string> = {
  warning: 'warning',
  processing: 'processing',
  success: 'success',
  error: 'error',
  default: 'default',
};

interface ChartLabels {
  weekdays: string[];
  totalModules: string;
  moduleNames: {
    flowApproval: string;
    financeContract: string;
    orgHr: string;
    adminAsset: string;
    platformIntegration: string;
  };
  systemRunningWell: string;
}

export default function Dashboard({ role, pageId, pageTitle, primaryColor, auditItems, onOpenAi, onAddAudit }: DashboardProps) {
  const { t } = useTranslation();
  const [query, setQuery] = useState('');

  const statusText: Record<ApprovalRecord['status'], string> = {
    warning: t('dashboard.status.warning'),
    processing: t('dashboard.status.processing'),
    success: t('dashboard.status.success'),
    error: t('dashboard.status.error'),
    default: t('dashboard.status.default'),
  };

  const getActionLabel = (action: string): string => {
    const labels: Record<string, string> = {
      [ACTION_PROCESS]: t('dashboard.actions.process'),
      [ACTION_VIEW]: t('dashboard.actions.view'),
      [ACTION_PRE_REVIEW]: t('dashboard.actions.preReview'),
      [ACTION_APPROVE]: t('dashboard.actions.approve'),
      [ACTION_RETURN]: t('dashboard.actions.return'),
      [ACTION_REMIND]: t('dashboard.actions.remind'),
    };
    return labels[action] || action;
  };

  const filteredRecords = approvalRecords.filter((record) => {
    if (!query.trim()) return true;
    return [record.name, record.applicant, record.department, record.node].some((value) => value.includes(query.trim()));
  });

  const chartOptions = useMemo(
    () => createChartOptions(primaryColor, {
      weekdays: t('dashboard.chart.weekdays', { returnObjects: true }) as string[],
      totalModules: t('dashboard.chart.totalModules'),
      moduleNames: {
        flowApproval: t('dashboard.chart.moduleNames.flowApproval'),
        financeContract: t('dashboard.chart.moduleNames.financeContract'),
        orgHr: t('dashboard.chart.moduleNames.orgHr'),
        adminAsset: t('dashboard.chart.moduleNames.adminAsset'),
        platformIntegration: t('dashboard.chart.moduleNames.platformIntegration'),
      },
      systemRunningWell: t('dashboard.chart.systemRunningWell'),
    }),
    [primaryColor, t],
  );

  const handleAction = (action: string, record: ApprovalRecord) => {
    const approveAction = APPROVAL_ACTIONS.includes(action);
    if (role === 'employee' && approveAction) {
      message.warning(t('dashboard.messages.noApprovalPermission'));
      return;
    }

    if (AI_ACTIONS.includes(action)) {
      onOpenAi(t('dashboard.aiPrompts.checkRisk', {
        action: getActionLabel(action),
        name: record.name,
        node: record.node,
      }));
      return;
    }

    const actionLabel = getActionLabel(action);
    message.success(t('dashboard.messages.actionDone', { action: actionLabel, name: record.name }));
    onAddAudit(t('dashboard.auditEntry', { action: actionLabel, id: record.id }));
  };

  const columns: ColumnsType<ApprovalRecord> = [
    { title: t('dashboard.columns.processName'), dataIndex: 'name', key: 'name', ellipsis: true, minWidth: 160 },
    { title: t('dashboard.columns.applicant'), dataIndex: 'applicant', key: 'applicant', width: 100 },
    { title: t('dashboard.columns.department'), dataIndex: 'department', key: 'department', width: 120, responsive: ['md'] },
    { title: t('dashboard.columns.currentNode'), dataIndex: 'node', key: 'node', width: 120, responsive: ['lg'] },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (status: ApprovalRecord['status']) => <Tag color={tagColor[status]}>{statusText[status]}</Tag>,
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Button size="small" type="primary" onClick={() => handleAction(ACTION_PROCESS, record)}>{t('dashboard.actions.process')}</Button>
          <Button size="small" onClick={() => handleAction(ACTION_VIEW, record)}>{t('dashboard.actions.view')}</Button>
          <Dropdown
            menu={{
              items: [
                { key: ACTION_PRE_REVIEW, label: t('dashboard.actions.preReview') },
                { key: ACTION_APPROVE, label: t('dashboard.actions.approve') },
                { key: ACTION_RETURN, label: t('dashboard.actions.return') },
                { key: ACTION_REMIND, label: t('dashboard.actions.remind') },
              ],
              onClick: ({ key: action }) => handleAction(action, record),
            }}
            trigger={['click']}
          >
            <Button size="small" icon={<OaIcon name="more" />} aria-label={t('dashboard.moreActionsAria')} />
          </Dropdown>
        </Space>
      ),
    },
  ];

  if (pageId !== 'dashboard') {
    return (
      <Card className="oa-card oa-placeholder-card">
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={t('dashboard.placeholder.description', { pageTitle })}
        />
        <Space>
          <Button type="primary" icon={<OaIcon name="ai" />} onClick={() => onOpenAi(t('dashboard.aiPrompts.analyzePage', { pageTitle }))}>
            {t('dashboard.placeholder.aiAnalyzePage')}
          </Button>
          <Button onClick={() => message.info(t('dashboard.messages.pageAuditRecorded'))}>{t('dashboard.placeholder.recordAccess')}</Button>
        </Space>
      </Card>
    );
  }

  return (
    <div className="oa-dashboard">
      <section className="oa-page-title">
        <div>
          <Typography.Text type="secondary">Enterprise OA Workspace</Typography.Text>
          <Typography.Title level={2}>{t('dashboard.title')}</Typography.Title>
          <Typography.Paragraph>
            {t('dashboard.description')}
          </Typography.Paragraph>
        </div>
        <Space className="oa-page-title-actions" wrap={false}>
          <PermissionButton role={role} menuId="dashboard" action="export" icon={<OaIcon name="export" />} onClick={() => message.warning(t('dashboard.messages.exportNotAvailable'))}>
            {t('dashboard.exportDashboard')}
          </PermissionButton>
          <Button icon={<OaIcon name="audit" />} onClick={() => message.info(t('dashboard.messages.metricsConfigComingSoon'))}>
            {t('dashboard.configMetrics')}
          </Button>
          <Button type="primary" icon={<OaIcon name="ai" />} onClick={() => onOpenAi(t('dashboard.aiPrompts.preReviewList'))}>
            {t('dashboard.aiPreReview')}
          </Button>
        </Space>
      </section>

      <Row gutter={[16, 16]}>
        {oaMetrics.map((metric) => (
          <Col xs={12} sm={12} md={6} key={metric.title}>
            <Card className="oa-card oa-stat-card">
              <Statistic title={metric.title} value={metric.value} suffix={metric.suffix} styles={{ content: { color: primaryColor } }} />
              <Tag color="blue">{metric.trend}</Tag>
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]}>
        {quickEntries.map((entry) => (
          <Col xs={24} sm={12} xl={6} key={entry.title}>
            <Card
              className="oa-card oa-quick-card"
              hoverable
              onClick={() => onOpenAi(entry.prompt)}
              actions={[
                <Button key="start" type="link" icon={<OaIcon name="ai" />} onClick={(event) => {
                  event.stopPropagation();
                  onOpenAi(entry.prompt);
                }}>
                  {t('dashboard.quickEntryAction')}
                </Button>,
              ]}
            >
              <Card.Meta title={entry.title} description={entry.description} />
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24}>
          <Card
            className="oa-card"
            title={t('dashboard.cards.approvalList')}
            extra={
              <Input.Search
                placeholder={t('dashboard.cards.searchPlaceholder')}
                allowClear
                onSearch={(value) => setQuery(value)}
                style={{ maxWidth: 260 }}
                prefix={<OaIcon name="search" />}
              />
            }
          >
            <Table
              rowKey="id"
              columns={columns}
              dataSource={filteredRecords}
              pagination={{ pageSize: 5 }}
              scroll={{ x: 'max-content' }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24}>
          <Card className="oa-card" title={t('dashboard.cards.timeline')}>
            <Timeline items={[...auditItems, ...timelineSeed]} />
            {!can(role, 'dashboard', 'ai_execute') && (
              <Alert type="warning" showIcon title={t('dashboard.cards.aiLimitedAlert')} />
            )}
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={8}>
          <EChartsCard title={t('dashboard.charts.processTrend')} option={chartOptions.line} />
        </Col>
        <Col xs={24} lg={8}>
          <EChartsCard title={t('dashboard.charts.moduleDistribution')} option={chartOptions.pie} />
        </Col>
        <Col xs={24} lg={8}>
          <EChartsCard title={t('dashboard.charts.systemHealth')} option={chartOptions.gauge} />
        </Col>
      </Row>

      <Card className="oa-card">
        <Descriptions
          title={t('dashboard.integration.title')}
          bordered
          column={{ xs: 1, md: 3 }}
          items={[
            { key: 'backend', label: t('dashboard.integration.backendInterface'), children: 'System / AI Tasks（JWT）' },
            { key: 'charts', label: t('dashboard.integration.chartEngine'), children: 'ECharts' },
            { key: 'permissions', label: t('dashboard.integration.permissionModel'), children: t('dashboard.integration.permissionModelValue') },
          ]}
        />
        <Progress percent={86} strokeColor={primaryColor} className="oa-health-progress" />
      </Card>
    </div>
  );
}

function createChartOptions(primaryColor: string, labels: ChartLabels): Record<'line' | 'pie' | 'gauge', EChartsOption> {
  return {
    line: {
      color: [primaryColor],
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(15, 23, 42, 0.92)',
        borderColor: 'transparent',
        textStyle: { color: '#fff', fontSize: 12 },
      },
      grid: { left: 36, right: 16, top: 24, bottom: 28 },
      xAxis: {
        type: 'category',
        data: labels.weekdays,
        axisLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.4)' } },
        axisLabel: { color: '#94a3b8', fontSize: 11 },
        axisTick: { show: false },
      },
      yAxis: {
        type: 'value',
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#94a3b8', fontSize: 11 },
        splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.16)', type: 'dashed' } },
      },
      series: [{
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        showSymbol: false,
        lineStyle: { width: 3 },
        itemStyle: { borderWidth: 2, borderColor: '#fff' },
        areaStyle: {
          opacity: 0.18,
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: primaryColor },
              { offset: 1, color: 'rgba(255, 255, 255, 0)' },
            ],
          },
        },
        emphasis: { focus: 'series' },
        data: [42, 56, 48, 72, 69, 88],
      }],
    },
    pie: {
      color: ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'],
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(15, 23, 42, 0.92)',
        borderColor: 'transparent',
        textStyle: { color: '#fff', fontSize: 12 },
        formatter: '{b}: {c} ({d}%)',
      },
      legend: {
        bottom: 4,
        left: 'center',
        type: 'scroll',
        itemWidth: 8,
        itemHeight: 8,
        itemGap: 12,
        textStyle: { fontSize: 11, color: '#64748b' },
      },
      graphic: [
        {
          type: 'text',
          left: 'center',
          top: '34%',
          style: {
            text: '100',
            fontSize: 22,
            fontWeight: 'bold',
            fill: '#0f172a',
          },
        },
        {
          type: 'text',
          left: 'center',
          top: '46%',
          style: {
            text: labels.totalModules,
            fontSize: 11,
            fill: '#94a3b8',
          },
        },
      ],
      series: [
        {
          type: 'pie',
          radius: ['44%', '66%'],
          center: ['50%', '42%'],
          avoidLabelOverlap: true,
          itemStyle: {
            borderColor: '#fff',
            borderWidth: 3,
            borderRadius: 6,
          },
          label: { show: false },
          labelLine: { show: false },
          emphasis: {
            scale: true,
            scaleSize: 8,
            itemStyle: { shadowBlur: 16, shadowColor: 'rgba(15, 23, 42, 0.24)' },
          },
          data: [
            { name: labels.moduleNames.flowApproval, value: 36 },
            { name: labels.moduleNames.financeContract, value: 22 },
            { name: labels.moduleNames.orgHr, value: 18 },
            { name: labels.moduleNames.adminAsset, value: 14 },
            { name: labels.moduleNames.platformIntegration, value: 10 },
          ],
        },
      ],
    },
    gauge: {
      series: [
        {
          type: 'gauge',
          radius: '88%',
          center: ['50%', '58%'],
          startAngle: 200,
          endAngle: -20,
          progress: {
            show: true,
            width: 16,
            roundCap: true,
            itemStyle: {
              color: {
                type: 'linear',
                x: 0, y: 0, x2: 1, y2: 0,
                colorStops: [
                  { offset: 0, color: '#10b981' },
                  { offset: 0.5, color: '#34d399' },
                  { offset: 1, color: primaryColor },
                ],
              },
            },
          },
          axisLine: {
            lineStyle: {
              width: 16,
              color: [[1, 'rgba(148, 163, 184, 0.18)']],
            },
          },
          pointer: { show: false },
          axisTick: { show: false },
          splitLine: { show: false },
          axisLabel: { show: false },
          anchor: { show: false },
          detail: {
            valueAnimation: true,
            formatter: '{value}%',
            fontSize: 26,
            fontWeight: 'bold',
            color: '#0f172a',
            offsetCenter: [0, '8%'],
          },
          title: {
            show: true,
            offsetCenter: [0, '38%'],
            color: '#94a3b8',
            fontSize: 12,
          },
          data: [{ value: 92, name: labels.systemRunningWell }],
        },
      ],
    },
  };
}

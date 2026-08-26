'use client';

import { useCallback, useEffect, useState } from 'react';
import { Alert, App as AntdApp, Button, Card, Descriptions, Drawer, Empty, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useTranslation } from 'react-i18next';
import type { AgentTaskDetail, AgentTaskDetailStep, AgentTaskStatus, AgentTaskSummary } from '@/types/oa';
import { agentTaskApi, formatOaApiError } from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';

const CANCELLABLE_STATUSES = new Set<AgentTaskStatus>(['PLAN_READY', 'WAITING_CONFIRMATION', 'QUEUED']);
const STATUS_VALUES: AgentTaskStatus[] = [
  'RECEIVED', 'PLANNING', 'PLAN_READY', 'WAITING_CONFIRMATION', 'QUEUED', 'RUNNING',
  'SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED', 'TIMED_OUT', 'REJECTED', 'EXPIRED', 'CANCELLED',
];

function statusColor(status: AgentTaskStatus): string {
  if (status === 'SUCCEEDED') return 'success';
  if (status === 'PARTIALLY_SUCCEEDED' || status === 'WAITING_CONFIRMATION') return 'warning';
  if (['FAILED', 'TIMED_OUT', 'REJECTED', 'EXPIRED'].includes(status)) return 'error';
  if (status === 'RUNNING' || status === 'QUEUED' || status === 'PLANNING') return 'processing';
  return 'default';
}

function formatTime(value?: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';
}

export default function AiTaskCenterPage() {
  const { t } = useTranslation();
  const { message, modal } = AntdApp.useApp();
  const [records, setRecords] = useState<AgentTaskSummary[]>([]);
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<AgentTaskDetail | null>(null);
  const [cancellingTaskId, setCancellingTaskId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await agentTaskApi.list({ status: status || undefined, page, size: 20 });
      setRecords(response.records);
      setTotal(response.total);
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setLoading(false);
    }
  }, [message, page, status]);

  useEffect(() => { void load(); }, [load]);

  const openDetail = async (taskId: string) => {
    setDetailLoading(true);
    try {
      setDetail(await agentTaskApi.detail(taskId));
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setDetailLoading(false);
    }
  };

  const cancelTask = (task: Pick<AgentTaskSummary, 'taskId' | 'status'>) => {
    modal.confirm({
      title: t('pages.agentTasks.cancelTitle'),
      content: t('pages.agentTasks.cancelContent', { taskId: task.taskId }),
      okText: t('pages.agentTasks.cancelConfirm'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        setCancellingTaskId(task.taskId);
        try {
          const nextDetail = await agentTaskApi.cancel(task.taskId);
          setDetail((current) => current?.taskId === task.taskId ? nextDetail : current);
          message.success(t('pages.agentTasks.cancelSuccess'));
          await load();
        } catch (error) {
          message.error(formatOaApiError(error));
          throw error;
        } finally {
          setCancellingTaskId(null);
        }
      },
    });
  };

  const columns: ColumnsType<AgentTaskSummary> = [
    {
      title: t('pages.agentTasks.columnTask'),
      dataIndex: 'taskId',
      width: 245,
      render: (taskId: string, item) => <div className="agent-task-identity">
        <span className="agent-task-identity__icon"><OaIcon name="history" /></span>
        <span><Typography.Text strong copyable={{ text: taskId }}>{taskId}</Typography.Text><Typography.Text type="secondary">{item.pageId}</Typography.Text></span>
      </div>,
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      width: 165,
      render: (value: AgentTaskStatus) => <Tag color={statusColor(value)}>{t(`oa.ai.status.${value}`)}</Tag>,
    },
    {
      title: t('pages.agentTasks.columnRisk'),
      dataIndex: 'riskLevel',
      width: 90,
      render: (value) => value ? <Tag color={value === 'L2' ? 'red' : value === 'L1' ? 'gold' : 'green'}>{value}</Tag> : '-',
    },
    { title: t('pages.agentTasks.columnCreatedAt'), dataIndex: 'createdAt', width: 180, render: formatTime },
    { title: t('pages.agentTasks.columnUpdatedAt'), dataIndex: 'updatedAt', width: 180, render: formatTime },
    {
      title: t('pages.agentTasks.columnError'),
      dataIndex: 'errorCode',
      ellipsis: true,
      render: (value) => <Typography.Text type={value ? 'danger' : 'secondary'}>{value || '-'}</Typography.Text>,
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 170,
      fixed: 'right',
      render: (_, item) => <Space>
        <Button type="link" size="small" onClick={() => void openDetail(item.taskId)}>{t('pages.agentTasks.view')}</Button>
        {CANCELLABLE_STATUSES.has(item.status) && <Button danger type="link" size="small" loading={cancellingTaskId === item.taskId} onClick={() => cancelTask(item)}>{t('pages.agentTasks.cancelTask')}</Button>}
      </Space>,
    },
  ];

  return <section className="oa-domain-page agent-task-center">
    <div className="oa-domain-heading agent-task-center__heading">
      <div>
        <Typography.Text className="agent-task-center__kicker">CONTROLLED EXECUTION LEDGER</Typography.Text>
        <Typography.Title level={3}>{t('pages.agentTasks.title')}</Typography.Title>
        <Typography.Paragraph type="secondary">{t('pages.agentTasks.description')}</Typography.Paragraph>
      </div>
      <div className="agent-task-center__metric"><span>{t('pages.agentTasks.totalLabel')}</span><strong>{total}</strong><small>{t('pages.agentTasks.ownedOnly')}</small></div>
    </div>

    <Card className="oa-domain-card agent-task-center__card">
      <div className="oa-domain-toolbar">
        <Select
          value={status}
          style={{ minWidth: 210 }}
          aria-label={t('pages.agentTasks.statusFilter')}
          options={[{ value: '', label: t('common.all') }, ...STATUS_VALUES.map((value) => ({ value, label: t(`oa.ai.status.${value}`) }))]}
          onChange={(value) => { setStatus(value); setPage(1); }}
        />
        <Button icon={<OaIcon name="reload" />} onClick={() => void load()}>{t('common.refresh')}</Button>
      </div>
      <Table
        rowKey="taskId"
        columns={columns}
        dataSource={records}
        loading={loading}
        scroll={{ x: 1120 }}
        locale={{ emptyText: <Empty description={t('pages.agentTasks.empty')} /> }}
        pagination={{ current: page, pageSize: 20, total, showSizeChanger: false, showTotal: (value) => t('pages.agentTasks.total', { count: value }), onChange: setPage }}
      />
    </Card>

    <Drawer title={t('pages.agentTasks.detailTitle')} styles={{ wrapper: { width: 660 } }} open={Boolean(detail) || detailLoading} loading={detailLoading} onClose={() => setDetail(null)}>
      {detail && <Space orientation="vertical" size={18} className="agent-task-detail">
        {detail.errorCode && <Alert type="error" showIcon title={t('pages.agentTasks.errorTitle')} description={detail.errorCode} />}
        <Descriptions size="small" column={1} items={[
          { key: 'taskId', label: t('pages.agentTasks.taskId'), children: <Typography.Text copyable={{ text: detail.taskId }}>{detail.taskId}</Typography.Text> },
          { key: 'status', label: t('common.status'), children: <Tag color={statusColor(detail.status)}>{t(`oa.ai.status.${detail.status}`)}</Tag> },
          { key: 'pageId', label: t('pages.agentTasks.pageId'), children: detail.pageId },
          { key: 'input', label: t('pages.agentTasks.input'), children: <Typography.Paragraph className="agent-task-safe-text">{detail.input}</Typography.Paragraph> },
          { key: 'createdAt', label: t('pages.agentTasks.columnCreatedAt'), children: formatTime(detail.createdAt) },
          { key: 'finishedAt', label: t('pages.agentTasks.finishedAt'), children: formatTime(detail.finishedAt) },
        ]} />
        <Typography.Title level={5}>{t('pages.agentTasks.stepsTitle')}</Typography.Title>
        <Table<AgentTaskDetailStep>
          rowKey="sequence"
          size="small"
          pagination={false}
          dataSource={detail.steps}
          columns={[
            { title: '#', dataIndex: 'sequence', width: 48 },
            { title: t('pages.agentTasks.tool'), dataIndex: 'toolCode', width: 170, render: (value) => <Typography.Text code>{value}</Typography.Text> },
            { title: t('common.status'), dataIndex: 'status', width: 110, render: (value) => <Tag>{value}</Tag> },
            { title: t('pages.agentTasks.resultSummary'), dataIndex: 'resultSummary', render: (value) => <Typography.Text className="agent-task-safe-text">{value || '-'}</Typography.Text> },
          ]}
        />
      </Space>}
    </Drawer>
  </section>;
}

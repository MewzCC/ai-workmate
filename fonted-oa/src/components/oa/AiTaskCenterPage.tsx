'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, App as AntdApp, Button, Card, DatePicker, Descriptions, Drawer, Empty, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useTranslation } from 'react-i18next';
import type { AgentTaskDetail, AgentTaskDetailStep, AgentTaskStatus, AgentTaskSummary } from '@/types/oa';
import { agentTaskApi, formatOaApiError } from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';
import ResponsiveTable from './ResponsiveTable';
import { canCancelAgentTask, cancelAgentTask } from '@/lib/agentTaskCancellation';
import { useAuth } from '@/components/auth/AuthProvider';
import { useRegisterPageUiCommands } from './PageUiCommandBridge';
import { usePublishPageAgentContext } from './PageAgentContext';
import { requireUiFieldTypes } from '@/lib/preparedPageUiCommand';
import { requireUiPagination, uiDateRange } from '@/hooks/usePageUiEditorCommands';

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
  const { user } = useAuth();
  const scope = `${user?.tenantId}:${user?.id}:${user?.permissionVersion}`;
  const activeScope = useRef<string | undefined>(scope); activeScope.current = scope;
  const listRequest = useRef(0), detailRequest = useRef(0);
  const cancellationConfirmation = useRef<{ destroy: () => void } | undefined>(undefined);
  const [dataScope, setDataScope] = useState(scope);
  const [storedRecords, setRecords] = useState<AgentTaskSummary[]>([]);
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [storedDetail, setDetail] = useState<AgentTaskDetail | null>(null);
  const detail = dataScope === scope ? storedDetail : null;
  const records = dataScope === scope ? storedRecords : [];
  const [range, setRange] = useState<[string, string]>();
  const [cancellingTaskId, setCancellingTaskId] = useState<string | null>(null);
  const formatTaskError = (errorCode?: string | null) => errorCode
    ? t(`pages.agentTasks.errors.${errorCode}`, {
      defaultValue: t('pages.agentTasks.errors.UNKNOWN', { code: errorCode }),
    })
    : '-';

  const load = useCallback(async () => {
    const ticket = ++listRequest.current;
    setLoading(true);
    try {
      const response = await agentTaskApi.list({ status: status || undefined, from: range?.[0], to: range?.[1], page, size: 20 });
      if (ticket !== listRequest.current || activeScope.current !== scope) return;
      setDataScope(scope); setRecords(response.records);
      setTotal(response.total);
    } catch (error) {
      if (ticket !== listRequest.current || activeScope.current !== scope) return;
      setRecords([]); setTotal(0);
      message.error(formatOaApiError(error));
    } finally {
      if (ticket === listRequest.current && activeScope.current === scope) setLoading(false);
    }
  }, [message, page, status, range, scope]);

  useEffect(() => {
    activeScope.current = scope; setRecords([]); setTotal(0); setDetail(null);
    return () => { listRequest.current += 1; detailRequest.current += 1; activeScope.current = undefined; cancellationConfirmation.current?.destroy(); };
  }, [scope]);
  useEffect(() => { void load(); return () => { listRequest.current += 1; }; }, [load]);

  const openDetail = async (taskId: string, reportError = true) => {
    if (!/^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/.test(taskId)) throw new Error('PAGE_UI_FIELD_REJECTED');
    const ticket = ++detailRequest.current;
    setDetail(null);
    setDetailLoading(true);
    try {
      const result = await agentTaskApi.detail(taskId);
      if (ticket !== detailRequest.current || activeScope.current !== scope) return;
      if (result.taskId.toLowerCase() !== taskId.toLowerCase()) throw new Error('RESOURCE_MISMATCH');
      setDataScope(scope); setDetail(result);
    } catch (error) {
      if (ticket !== detailRequest.current || activeScope.current !== scope) return;
      if (!reportError) throw error;
      message.error(formatOaApiError(error));
    } finally {
      if (ticket === detailRequest.current && activeScope.current === scope) setDetailLoading(false);
    }
  };
  usePublishPageAgentContext({ status: status || undefined, from: range?.[0], to: range?.[1], page, size: 20 });
  useRegisterPageUiCommands({ 'ui.refreshPage': load,
    'ui.openDetail': ({ payload: { recordId } }) => openDetail(String(recordId), false),
    'ui.applyFilter': ({ payload: { filters } }) => {
      requireUiFieldTypes(filters, { status: ['', ...STATUS_VALUES], from: 'string', to: 'string', page: 'number', size: 'number' });
      requireUiPagination(filters);
      if (filters.size !== undefined && filters.size !== 20) throw new Error('PAGE_UI_FIELD_REJECTED');
      const dates = uiDateRange(filters, range);
      if (filters.status !== undefined) setStatus(String(filters.status));
      if (filters.from !== undefined || filters.to !== undefined) setRange(dates);
      setPage(Number(filters.page ?? 1));
    },
  });

  const cancelTask = (task: Pick<AgentTaskSummary, 'taskId' | 'status'>) => {
    cancellationConfirmation.current?.destroy();
    cancellationConfirmation.current = modal.confirm({
      title: t('pages.agentTasks.cancelTitle'),
      content: t('pages.agentTasks.cancelContent', { taskId: task.taskId }),
      okText: t('pages.agentTasks.cancelConfirm'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        if (activeScope.current !== scope) throw new Error(t('oa.ai.uiNotApplied'));
        detailRequest.current += 1; setDetailLoading(false);
        setCancellingTaskId(task.taskId);
        try {
          const outcome = await cancelAgentTask(task.taskId);
          if (activeScope.current !== scope) return;
          const nextDetail = outcome.detail;
          setDetail((current) => current?.taskId === task.taskId ? nextDetail : current);
          if (outcome.cancelled) message.success(t('pages.agentTasks.cancelSuccess'));
          else message.info(t('oa.ai.cancelNoLongerPossible'));
          await load();
        } catch (error) {
          if (activeScope.current !== scope) return;
          message.error(formatOaApiError(error));
          throw error;
        } finally {
          if (activeScope.current === scope) setCancellingTaskId(null);
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
      render: (value) => <Typography.Text type={value ? 'danger' : 'secondary'}>{formatTaskError(value)}</Typography.Text>,
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 170,
      fixed: 'right',
      render: (_, item) => <Space>
        <Button type="link" size="small" onClick={() => void openDetail(item.taskId)}>{t('pages.agentTasks.view')}</Button>
        {canCancelAgentTask(item.status) && <Button danger type="link" size="small" loading={cancellingTaskId === item.taskId} onClick={() => cancelTask(item)}>{t('pages.agentTasks.cancelTask')}</Button>}
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
        <DatePicker.RangePicker showTime value={range ? [dayjs(range[0]), dayjs(range[1])] : null}
          aria-label={t('pages.agentTasks.columnCreatedAt')}
          onChange={dates => { setRange(dates?.[0] && dates[1] ? [dates[0].format('YYYY-MM-DDTHH:mm:ss'), dates[1].format('YYYY-MM-DDTHH:mm:ss')] : undefined); setPage(1); }} />
      </div>
      <ResponsiveTable
        rowKey="taskId"
        columns={columns}
        dataSource={records}
        loading={loading}
        scroll={{ x: 1120 }}
        locale={{ emptyText: <Empty description={t('pages.agentTasks.empty')} /> }}
        pagination={{ current: page, pageSize: 20, total, showSizeChanger: false, showTotal: (value) => t('pages.agentTasks.total', { count: value }), onChange: setPage }}
      />
    </Card>

    <Drawer title={t('pages.agentTasks.detailTitle')} styles={{ wrapper: { width: 660 } }} open={Boolean(detail) || detailLoading} loading={detailLoading} onClose={() => { detailRequest.current += 1; setDetail(null); setDetailLoading(false); }}>
      {detail && <Space orientation="vertical" size={18} className="agent-task-detail">
        {detail.errorCode && <Alert type="error" showIcon title={t('pages.agentTasks.errorTitle')} description={formatTaskError(detail.errorCode)} />}
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

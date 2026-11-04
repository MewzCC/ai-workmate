'use client';

import { useCallback, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import {
  Avatar,
  Alert,
  Button,
  Card,
  DatePicker,
  Empty,
  Segmented,
  Select,
  Space,
  Tag,
  Tabs,
  Typography,
} from 'antd';
import { message } from '@/lib/antdMessage';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { formatOaApiError, OaApiError, todoApi, type TodoItem } from '@/lib/oaApi';
import { genericApprovalTaskApi } from '@/lib/genericApprovalTaskApi';
import { requireTodoQuery, todoQueryFromLocation, TODO_BUSINESS_TYPES, type TodoQueryFilters } from '@/lib/todoQuery';
import { leaveTypeLabel } from './MyApplicationsPage';
import ResponsiveTable from './ResponsiveTable';
import { OaIcon } from '@/components/OaIcon';
import { useTranslation } from 'react-i18next';
import { usePublishPageAgentContext } from './PageAgentContext';
import { useRegisterPageUiCommands, useRegisterPageUiViews } from './PageUiCommandBridge';
import GenericApprovalInbox from './GenericApprovalInbox';
import { requireUiFieldTypes } from '@/lib/preparedPageUiCommand';
import { requireUiPagination } from '@/hooks/usePageUiEditorCommands';
import { useScopedOverview } from '@/hooks/useScopedOverview';
import { usePermission } from '@/hooks/usePermission';
import { useAsyncActionGuard } from '@/hooks/useAsyncActionGuard';

const { RangePicker } = DatePicker;

export default function TodoListPage() {
  const { t } = useTranslation(), router = useRouter(), params = useSearchParams();
  const active = params.get('kind') === 'generic' ? 'generic' : params.get('kind') === 'leave' ? 'leave' : 'all';
  const select = (view: string) => router.push(view === 'generic' ? '/oa/todo?kind=generic' : view === 'leave' ? '/oa/todo?kind=leave' : '/oa/todo');
  useRegisterPageUiViews(active, ['all', 'leave', 'generic'], select);
  let initial: TodoQueryFilters;
  try { initial = todoQueryFromLocation(params); } catch {
    return <Alert type="error" showIcon message={t('approval.genericTasks.invalidQuery')} action={<Button onClick={() => router.push('/oa/todo')}>{t('common.reset')}</Button>} />;
  }
  return <Tabs className="oa-application-tabs" activeKey={active} onChange={select}
    items={[{ key: 'all', label: t('approval.genericTasks.allTab'), children: active === 'all' ? <TodoInbox key={params.toString()} initial={initial} /> : null },
      { key: 'leave', label: t('approval.genericTasks.leaveTab'), children: active === 'leave' ? <TodoInbox key={params.toString()} initial={initial} leaveOnly /> : null },
      { key: 'generic', label: t('approval.genericTasks.genericTab'), children: active === 'generic' ? <GenericApprovalInbox /> : null }]} />;
}

function TodoInbox({ initial, leaveOnly = false }: { initial: TodoQueryFilters; leaveOnly?: boolean }) {
  const router = useRouter();
  const { t } = useTranslation();
  const { allowed } = usePermission('todo:read');
  const [filters, setFilters] = useState(initial);
  const { status, page, size, businessType } = filters;
  const range = useMemo(() => filters.from || filters.to ? [filters.from, filters.to] : undefined, [filters.from, filters.to]);
  const setStatus = (value: string) => setFilters(previous => requireTodoQuery({ ...previous, status: value, page: 1 }));
  const setPage = (value: number) => setFilters(previous => ({ ...previous, page: value }));
  const setSize = (value: number) => setFilters(previous => ({ ...previous, size: value }));
  const setRange = (value: [string, string] | undefined) => setFilters(previous => requireTodoQuery({ ...previous, from: value?.[0], to: value?.[1], page: 1 }));

  usePublishPageAgentContext({
    businessType, status,
    from: range?.[0],
    to: range?.[1],
    page,
    size,
  });

  const query = useCallback(async () => {
    if (!allowed) return null;
    return todoApi.list(filters);
  }, [allowed, filters]);
  const { data, loading, error, load, scope, isCurrent } = useScopedOverview(query);
  const records = useMemo<TodoItem[]>(() => allowed ? data?.records ?? [] : [], [allowed, data]);
  const total = allowed ? data?.total ?? 0 : 0;
  const { capture } = useAsyncActionGuard(scope);
  const openDetail = async (recordId: string | number) => {
    const current = capture();
    if (!current() || !allowed || !isCurrent() || typeof recordId !== 'number' || !Number.isSafeInteger(recordId) || recordId < 1)
      throw new Error('PAGE_UI_FIELD_REJECTED');
    const summary = await todoApi.summary(recordId);
    if (!current() || !isCurrent()) throw new Error('PAGE_UI_COMMAND_REJECTED');
    if (!summary.detailAvailable) throw new OaApiError(t('approval.genericTasks.snapshotMissing'), 409, 'BUSINESS_STATE_INVALID');
    if (summary.businessType === 'GENERIC_APPROVAL') {
      const detail = await genericApprovalTaskApi.detail(recordId);
      if (!current() || !isCurrent()) throw new Error('PAGE_UI_COMMAND_REJECTED');
      if (detail.task.id !== recordId || detail.task.applicationId !== summary.applicationId) throw new Error('PAGE_UI_FIELD_REJECTED');
      router.push(`/oa/todo?kind=generic&taskId=${recordId}`); return;
    }
    const detail = await todoApi.detail(recordId);
    if (!current() || !isCurrent()) throw new Error('PAGE_UI_COMMAND_REJECTED');
    if (detail.id !== summary.applicationId || detail.taskId !== recordId) throw new Error('PAGE_UI_FIELD_REJECTED');
    router.push(`/oa/approval-tasks/${recordId}?from=todo`);
  };

  useRegisterPageUiCommands({
    'ui.refreshPage': async () => { if (!allowed || !isCurrent() || !await load()) throw new Error('PAGE_UI_FIELD_REJECTED'); },
    'ui.applyFilter': ({ payload: { filters: requested } }) => {
      if (!allowed || !isCurrent()) throw new Error('PAGE_UI_FIELD_REJECTED');
      requireUiFieldTypes(requested, { businessType: TODO_BUSINESS_TYPES, status: ['', 'ALL', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'], from: 'string', to: 'string', page: 'number', size: 'number' });
      requireUiPagination(requested);
      if (typeof requested.size === 'number' && requested.size > 50) throw new Error('PAGE_UI_FIELD_REJECTED');
      if (leaveOnly && requested.businessType !== undefined && requested.businessType !== 'LEAVE_APPLICATION') throw new Error('PAGE_UI_FIELD_REJECTED');
      const next = requireTodoQuery({ ...filters, ...requested, page: requested.page ?? 1 });
      setFilters(next);
    },
    'ui.openDetail': ({ payload: { recordId } }) => openDetail(recordId),
  }, null, leaveOnly ? 'leave' : 'all');

  const overdueCount = useMemo(() => records.filter((item) => item.overdue).length, [records]);

  const columns: ColumnsType<TodoItem> = [
    {
      title: t('pages.todo.columnSubject'),
      key: 'subject',
      width: 260,
      render: (_, item) => (
        <div className="leave-table-subject">
          <span className="leave-table-subject__icon is-approval"><OaIcon name="approval" /></span>
          <div>
            <Space>
              <Avatar size="small" src={item.applicantAvatarUrl || undefined}>{item.applicantName.slice(0, 1).toUpperCase()}</Avatar>
              <Typography.Text strong>{item.businessType === 'GENERIC_APPROVAL' ? t('approval.genericTasks.genericSubject', { name: item.applicantName, title: item.title }) : t('pages.todo.subjectTitle', { name: item.applicantName, type: item.leaveType ? leaveTypeLabel(item.leaveType) : t('approval.genericTasks.leaveTab') })}</Typography.Text>
            </Space>
            <Typography.Text type="secondary">
              TK-{String(item.id).padStart(6, '0')} · {t('pages.todo.applicationIdLabel', { id: item.applicationId })}
            </Typography.Text>
          </div>
        </div>
      ),
    },
    { title: t('approval.genericTasks.businessType'), key: 'businessType', width: 140,
      render: (_, item) => <Tag>{t(item.businessType === 'GENERIC_APPROVAL' ? 'approval.genericTasks.genericTab' : 'approval.genericTasks.leaveTab')}</Tag> },
    {
      title: t('pages.todo.columnDuration'),
      dataIndex: 'durationHalfDays',
      width: 110,
      render: (value) => <Typography.Text strong>{typeof value === 'number' ? t('pages.todo.durationValue', { count: value / 2 }) : '-'}</Typography.Text>,
    },
    {
      title: t('pages.todo.columnSubmittedAt'),
      dataIndex: 'submittedAt',
      width: 170,
      render: formatTime,
    },
    {
      title: t('pages.todo.columnSla'),
      key: 'sla',
      width: 190,
      render: (_, item) => (
        <div className="todo-sla-cell">
          <Typography.Text>{item.dueAt ? formatTime(item.dueAt) : t('pages.todo.slaUnset')}</Typography.Text>
          {item.overdue
            ? <Tag color="error">{t('pages.todo.slaOverdue')}</Tag>
            : item.status === 'PENDING' && <Tag color="processing">{t('pages.todo.slaRunning')}</Tag>}
        </div>
      ),
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      width: 110,
      render: (value) => <TodoStatusTag status={value} />,
    },
    {
      title: t('common.actions'),
      width: 130,
      fixed: 'right',
      render: (_, item) => (
        <Button
          type={item.status === 'PENDING' ? 'primary' : 'link'}
          size="small"
          disabled={!allowed || item.detailAvailable === false}
          onClick={() => { void openDetail(item.id).catch(failure => { if (isCurrent()) message.error(formatOaApiError(failure)); }); }}
        >
          {item.detailAvailable === false ? t('approval.genericTasks.snapshotMissing') : item.status === 'PENDING' ? t('pages.todo.actionProcess') : t('pages.todo.actionView')}
        </Button>
      ),
    },
  ];

  return (
    <section className="leave-list-workbench">
      <header className="leave-list-hero todo-list-hero">
        <div>
          <span className="leave-list-hero__kicker">{t('approval.genericTasks.inboxKicker')}</span>
          <Typography.Title level={2}>{t('pages.todo.title')}</Typography.Title>
          <Typography.Paragraph>
            {t('pages.todo.description')}
          </Typography.Paragraph>
        </div>
        <div className="todo-hero-indicator">
          <span>{t(status === 'PENDING' ? 'pages.todo.heroPendingLabel' : 'approval.genericTasks.currentPagePending')}</span>
          <strong>{status === 'PENDING' ? total : records.filter((item) => item.status === 'PENDING').length}</strong>
          <small>{overdueCount > 0 ? t('approval.genericTasks.currentPageOverdue', { count: overdueCount }) : t('pages.todo.heroNoOverdue')}</small>
        </div>
      </header>

      <Card className="leave-list-card" variant="borderless">
        <div className="todo-toolbar">
          <Select aria-label={t('approval.genericTasks.businessType')} disabled={!allowed || leaveOnly} value={businessType} style={{ minWidth: 150 }}
            options={TODO_BUSINESS_TYPES.map(value => ({ value, label: t(value === 'ALL' ? 'approval.genericTasks.allTab' : value === 'LEAVE_APPLICATION' ? 'approval.genericTasks.leaveTab' : 'approval.genericTasks.genericTab') }))}
            onChange={value => setFilters(previous => requireTodoQuery({ ...previous, businessType: value, page: 1 }))} />
          <Segmented
            value={status}
            disabled={!allowed}
            options={[
              { value: 'PENDING', label: t('pages.todo.statusPending') },
              { value: 'APPROVED', label: t('pages.todo.statusApproved') },
              { value: 'REJECTED', label: t('pages.todo.statusRejected') },
              { value: 'CANCELLED', label: t('pages.todo.statusCancelled') },
              { value: 'ALL', label: t('common.all') },
            ]}
            onChange={(value) => { setStatus(String(value)); setPage(1); }}
          />
          <Space wrap>
            <RangePicker
              showTime
              disabled={!allowed}
              allowEmpty={[true, true]}
              value={range ? [range[0] ? dayjs(range[0]) : null, range[1] ? dayjs(range[1]) : null] : null}
              onChange={(dates) => {
                setRange(dates?.[0] || dates?.[1]
                  ? [dates[0]?.format('YYYY-MM-DDTHH:mm:ss') ?? '', dates[1]?.format('YYYY-MM-DDTHH:mm:ss') ?? '']
                  : undefined);
                setPage(1);
              }}
            />
            <Button disabled={!allowed} icon={<OaIcon name="reload" />} onClick={() => void load()}>{t('common.refresh')}</Button>
          </Space>
        </div>
        {!allowed && <Alert type="warning" showIcon title={t('oa.ai.noPermission')} />}
        {allowed && error !== undefined && <Alert type="error" showIcon title={t('common.loadingFailed')}
          description={formatOaApiError(error)} action={<Button onClick={() => void load()}>{t('common.retry')}</Button>} />}
        <ResponsiveTable
          rowKey="id"
          columns={columns}
          dataSource={records}
          loading={loading && allowed}
          locale={{
            emptyText: (
              <Empty
                description={status === 'PENDING' ? t('pages.todo.emptyPending') : t('pages.todo.emptyFiltered')}
              />
            ),
          }}
          scroll={{ x: 1020 }}
          pagination={{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50],
            showTotal: (value) => t('pages.todo.totalTasks', { count: value }),
            onChange: (next, nextSize) => { setPage(next); setSize(nextSize); },
          }}
        />
      </Card>
    </section>
  );
}

function TodoStatusTag({ status }: { status: string }) {
  const { t } = useTranslation();
  const colorMap: Record<string, string> = {
    PENDING: 'processing',
    APPROVED: 'success',
    REJECTED: 'error',
    CANCELLED: 'default',
  };
  const labelKeyMap: Record<string, string> = {
    PENDING: 'pages.todo.statusPending',
    APPROVED: 'pages.todo.statusApproved',
    REJECTED: 'pages.todo.statusRejected',
    CANCELLED: 'pages.todo.statusCancelled',
  };
  const color = colorMap[status] || 'default';
  const labelKey = labelKeyMap[status];
  const label = labelKey ? t(labelKey) : status;
  return <Tag color={color}>{label}</Tag>;
}

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';
}

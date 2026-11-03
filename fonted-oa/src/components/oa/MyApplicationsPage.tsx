'use client';

import { useCallback, useMemo, useRef, useState } from 'react';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import {
  Avatar,
  Alert,
  Button,
  Card,
  Empty,
  Segmented,
  Space,
  Tag,
  Tooltip,
  Tabs,
  Typography,
} from 'antd';
import { message } from '@/lib/antdMessage';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { formatOaApiError, leaveApi, type LeaveApplication, type LeaveStatus } from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';
import { useTranslation } from 'react-i18next';
import i18n from '@/i18n';
import ResponsiveTable from './ResponsiveTable';
import { usePermission } from '@/hooks/usePermission';
import GenericApplicationsPanel from './GenericApplicationsPanel';
import { useAuth } from '@/components/auth/AuthProvider';
import { usePublishPageAgentContext } from './PageAgentContext';
import { useRegisterPageUiCommands, useRegisterPageUiViews } from './PageUiCommandBridge';
import { requireUiFieldTypes } from '@/lib/preparedPageUiCommand';
import { requireUiPagination } from '@/hooks/usePageUiEditorCommands';
import { useScopedOverview } from '@/hooks/useScopedOverview';
import { useAsyncActionGuard } from '@/hooks/useAsyncActionGuard';
import { useAsyncFormAction } from '@/hooks/useAsyncFormAction';
import { useScopedModalConfirm } from '@/hooks/useScopedModalConfirm';
import { useUiFormPreview } from './UiFormPreview';

const STATUS_FILTER_KEYS: (LeaveStatus | '')[] = [
  '', 'DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN',
];

export default function MyApplicationsPage() {
  const { t } = useTranslation();
  const router = useRouter();
  const params = useSearchParams();
  const { allowed: genericAllowed } = usePermission(['approval:mine:read', 'route:approval-start']);
  const { allowed: leaveAllowed } = usePermission('leave:read:self');
  const active = genericAllowed && (params.get('kind') === 'generic' || !leaveAllowed) ? 'generic' : 'leave';
  useRegisterPageUiViews(active, [...(leaveAllowed ? ['leave'] : []), ...(genericAllowed ? ['generic'] : [])], kind => {
    router.push(kind === 'generic' ? '/oa/my-applications?kind=generic' : '/oa/my-applications');
  });
  if (!leaveAllowed && !genericAllowed) return <Alert type="warning" showIcon title={t('oa.ai.noPermission')} />;
  return <Tabs className="oa-application-tabs" activeKey={active}
    onChange={(kind) => router.push(kind === 'generic' ? '/oa/my-applications?kind=generic' : '/oa/my-applications')}
    items={[
      ...(leaveAllowed ? [{ key: 'leave', label: t('approval.genericApplications.leaveTab'), children: active === 'leave' ? <LeaveApplicationsPanel /> : null }] : []),
      ...(genericAllowed ? [{ key: 'generic', label: t('approval.genericApplications.genericTab'), children: active === 'generic' ? <GenericApplicationsPanel /> : null }] : []),
    ]} />;
}

function LeaveApplicationsPanel() {
  const router = useRouter();
  const { user } = useAuth();
  const { t } = useTranslation();
  const { allowed, hasPermission } = usePermission('leave:read:self');
  const [status, setStatus] = useState<LeaveStatus | undefined>();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [actingId, setActingId] = useState<number>();
  const detailRequest = useRef(0);
  usePublishPageAgentContext({ status, page, size });

  const statusOptions = STATUS_FILTER_KEYS.map((value) => ({
    value,
    label: value ? t(`approval.status.${value}`) : t('approval.myApplications.statusFilterAll'),
  }));

  const query = useCallback(async () => {
    if (!allowed) return null;
    const result = await leaveApi.mine({ status, page, size });
    if (result.records.some(item => item.applicantUserId !== user?.id)) throw new Error(t('approval.genericApplications.invalidResult'));
    return result;
  }, [allowed, page, size, status, t, user?.id]);
  const { data, loading, error, load, scope, isCurrent } = useScopedOverview(query);
  const records = useMemo(() => allowed ? data?.records ?? [] : [], [allowed, data]);
  const total = allowed ? data?.total ?? 0 : 0;
  const { capture } = useAsyncActionGuard(scope);
  const action = useAsyncFormAction(scope);
  const confirm = useScopedModalConfirm(scope);
  const preview = useUiFormPreview(scope);

  useRegisterPageUiCommands({
    'ui.refreshPage': async () => { if (!allowed || !isCurrent() || !await load()) throw new Error('PAGE_UI_FIELD_REJECTED'); },
    'ui.applyFilter': ({ payload: { filters } }) => {
      if (!allowed || !isCurrent()) throw new Error('PAGE_UI_FIELD_REJECTED');
      requireUiFieldTypes(filters, { status: STATUS_FILTER_KEYS, page: 'number', size: 'number' }); requireUiPagination(filters);
      if (typeof filters.size === 'number' && filters.size > 50) throw new Error('PAGE_UI_FIELD_REJECTED');
      if (filters.status !== undefined) setStatus((filters.status || undefined) as LeaveStatus | undefined);
      setPage(Number(filters.page ?? 1));
      if (typeof filters.size === 'number') setSize(filters.size);
    },
    'ui.openDetail': async ({ payload: { recordId } }) => {
      const current = capture();
      if (!allowed || !isCurrent() || !current() || typeof recordId !== 'number' || !Number.isSafeInteger(recordId) || recordId < 1) throw new Error('PAGE_UI_FIELD_REJECTED');
      const id = recordId;
      const request = ++detailRequest.current;
      const item = await leaveApi.detail(id);
      if (request !== detailRequest.current || !current() || !isCurrent()) throw new Error('PAGE_UI_COMMAND_REJECTED');
      if (item.id !== id || item.applicantUserId !== user?.id) throw new Error('PAGE_UI_FIELD_REJECTED');
      if (item.canEdit && hasPermission('leave:create') && hasPermission('route:leave-application')) router.push(`/oa/leave-application?id=${id}`);
      else if (item.taskId) router.push(`/oa/approval-tasks/${item.taskId}?from=my-applications`);
      else preview({ id: `#${item.id}`, status: t(`approval.status.${item.status}`), reason: item.reason },
        { id: t('approval.genericApplications.application'), status: t('approval.genericApplications.status'), reason: t('approval.form.reasonLabel') });
    },
  }, null, 'leave-applications');

  const pageMetrics = useMemo(() => ({
    pending: records.filter((item) => item.status === 'PENDING').length,
    completed: records.filter((item) => item.status === 'APPROVED').length,
    attention: records.filter((item) => item.status === 'REJECTED' || item.overdue).length,
  }), [records]);

  const withdraw = (item: LeaveApplication) => {
    if (!allowed || !isCurrent() || !item.canWithdraw || !hasPermission('leave:withdraw')) return;
    confirm({
      title: t('approval.myApplications.withdrawConfirmTitle'),
      content: t('approval.myApplications.withdrawConfirmContent'),
      okText: t('approval.myApplications.withdrawConfirmOk'),
      okButtonProps: { danger: true },
      onOk: async () => {
        const write = action.begin(); if (!write || !isCurrent()) return;
        setActingId(item.id);
        try {
          const result = await leaveApi.withdraw(item.id, item.version);
          if (!write.current() || !isCurrent()) return;
          if (result.id !== item.id || result.applicantUserId !== user?.id || result.status !== 'WITHDRAWN' || result.version <= item.version)
            throw new Error(t('approval.genericApplications.invalidResult'));
          message.success(t('approval.myApplications.withdrawSuccess'));
          await load();
        } catch (error) {
          if (!write.current() || !isCurrent()) return;
          message.error(formatOaApiError(error));
          await load();
        } finally {
          if (write.current()) setActingId(undefined);
          write.finish();
        }
      },
    });
  };

  const remind = (item: LeaveApplication) => {
    if (!allowed || !isCurrent() || !item.canRemind || !hasPermission('leave:remind')) return;
    confirm({
      title: t('approval.myApplications.remindConfirmTitle'),
      content: t('approval.myApplications.remindConfirmContent', {
        name: item.approverName || t('approval.myApplications.approverFallback'),
      }),
      okText: t('approval.myApplications.remindConfirmOk'),
      onOk: async () => {
        const write = action.begin(); if (!write || !isCurrent()) return;
        setActingId(item.id);
        try {
          const result = await leaveApi.remind(item.id, item.version);
          if (!write.current() || !isCurrent()) return;
          if (result.id !== item.id || result.applicantUserId !== user?.id || result.status !== 'PENDING' || result.version <= item.version)
            throw new Error(t('approval.genericApplications.invalidResult'));
          message.success(t('approval.myApplications.remindSuccess'));
        } catch (error) {
          if (write.current() && isCurrent()) message.error(formatOaApiError(error));
        } finally {
          if (write.current() && isCurrent()) { setActingId(undefined); await load(); }
          write.finish();
        }
      },
    });
  };

  const columns: ColumnsType<LeaveApplication> = [
    {
      title: t('approval.myApplications.columnApplication'),
      key: 'application',
      width: 230,
      render: (_, item) => (
        <div className="leave-table-subject">
          <span className="leave-table-subject__icon"><OaIcon name="form" /></span>
          <div>
            <Typography.Text strong>{t('approval.myApplications.applicationTitle', { type: leaveTypeLabel(item.leaveType) })}</Typography.Text>
            <Typography.Text type="secondary">
              LV-{String(item.id).padStart(6, '0')} · {dayjs(item.createdAt).format('MM-DD HH:mm')}
            </Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: t('approval.myApplications.columnRange'),
      key: 'range',
      width: 260,
      render: (_, item) => (
        <div className="leave-table-range">
          <span>{item.startDate} {periodLabel(item.startPeriod)}</span>
          <OaIcon name="next" />
          <span>{item.endDate} {periodLabel(item.endPeriod)}</span>
        </div>
      ),
    },
    {
      title: t('approval.myApplications.columnDuration'),
      dataIndex: 'durationDays',
      width: 90,
      render: (value) => <Typography.Text strong>{t('approval.daysCount', { days: value })}</Typography.Text>,
    },
    {
      title: t('approval.myApplications.columnProgress'),
      key: 'progress',
      width: 180,
      render: (_, item) => (
        <div className="leave-table-progress">
          <StatusTag status={item.status} />
          <Typography.Text type="secondary">
            {item.status === 'DRAFT'
              ? t('approval.myApplications.progressWaitingSubmit')
              : item.status === 'PENDING'
                ? (
                  <Space>
                    <Avatar size="small" src={item.approverAvatarUrl || undefined}>{(item.approverName || '?').slice(0, 1).toUpperCase()}</Avatar>
                    <span>{t('approval.myApplications.progressProcessing', { name: item.approverName || t('approval.myApplications.approverFallback') })}</span>
                  </Space>
                )
                : t('approval.myApplications.progressFinished')}
          </Typography.Text>
        </div>
      ),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 300,
      fixed: 'right',
      render: (_, item) => (
        <Space>
          {item.canEdit && hasPermission('leave:create') && hasPermission('route:leave-application') && (
            <Button
              size="small"
              disabled={action.busy}
              icon={<OaIcon name="edit" />}
              onClick={() => router.push(`/oa/leave-application?id=${item.id}`)}
            >
              {t('approval.myApplications.editButton')}
            </Button>
          )}
          {item.canWithdraw && hasPermission('leave:withdraw') && (
            <Button
              size="small"
              danger
              disabled={action.busy}
              loading={action.busy && actingId === item.id}
              onClick={() => withdraw(item)}
            >
              {t('approval.myApplications.withdrawButton')}
            </Button>
          )}
          {item.status === 'PENDING' && item.taskId && (
            <Tooltip title={!item.canRemind && item.remindAvailableAt
              ? t('approval.myApplications.remindAvailableAt', {
                time: dayjs(item.remindAvailableAt).format('MM-DD HH:mm'),
              })
              : undefined}
            >
              <Button
                size="small"
                disabled={!item.canRemind || !hasPermission('leave:remind') || action.busy}
                loading={action.busy && actingId === item.id}
                onClick={() => remind(item)}
              >
                {t('approval.myApplications.remindButton', { count: item.reminderCount })}
              </Button>
            </Tooltip>
          )}
          {item.taskId && (
            <Button
              size="small"
              type="link"
              onClick={() => router.push(`/oa/approval-tasks/${item.taskId}?from=my-applications`)}
            >
              {t('approval.myApplications.flowDetailButton')}
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <section className="leave-list-workbench">
      <header className="leave-list-hero">
        <div>
          <span className="leave-list-hero__kicker">MY REQUESTS</span>
          <Typography.Title level={2}>{t('approval.myApplications.title')}</Typography.Title>
          <Typography.Paragraph>{t('approval.myApplications.description')}</Typography.Paragraph>
        </div>
        {hasPermission('leave:create') && hasPermission('route:leave-application') && <Button
          type="primary"
          size="large"
          icon={<OaIcon name="add" />}
          onClick={() => router.push('/oa/leave-application')}
        >
          {t('approval.myApplications.newLeave')}
        </Button>}
      </header>

      <div className="leave-metric-strip">
        <div><span>{t('approval.myApplications.metricCurrentFilter')}</span><strong>{total}</strong><small>{t('approval.myApplications.metricCurrentFilterUnit')}</small></div>
        <div><span>{t('approval.myApplications.metricPagePending')}</span><strong>{pageMetrics.pending}</strong><small>{t('approval.myApplications.metricPagePendingUnit')}</small></div>
        <div><span>{t('approval.myApplications.metricPageCompleted')}</span><strong>{pageMetrics.completed}</strong><small>{t('approval.myApplications.metricPageCompletedUnit')}</small></div>
        <div className={pageMetrics.attention ? 'is-attention' : ''}>
          <span>{t('approval.myApplications.metricAttention')}</span><strong>{pageMetrics.attention}</strong><small>{t('approval.myApplications.metricAttentionUnit')}</small>
        </div>
      </div>

      <Card className="leave-list-card" variant="borderless">
        <div className="leave-list-toolbar">
          <Segmented
            block
            value={status || ''}
            disabled={!allowed}
            options={statusOptions}
            onChange={(value) => {
              setStatus((value || undefined) as LeaveStatus | undefined);
              setPage(1);
            }}
          />
          <Button disabled={!allowed} icon={<OaIcon name="reload" />} onClick={() => void load()}>{t('common.refresh')}</Button>
        </div>
        {!allowed && <Alert type="warning" showIcon title={t('oa.ai.noPermission')} />}
        {error !== undefined && <Alert type="error" showIcon title={formatOaApiError(error)} action={<Button onClick={() => void load()}>{t('common.retry')}</Button>} />}
        <ResponsiveTable
          rowKey="id"
          columns={columns}
          dataSource={records}
          loading={loading && allowed}
          locale={{ emptyText: <Empty description={t('approval.myApplications.empty')} /> }}
          scroll={{ x: 1080 }}
          pagination={{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: false,
            showTotal: (value) => t('common.total', { count: value }),
            onChange: (next, count) => { setPage(next); setSize(count); },
          }}
        />
      </Card>
    </section>
  );
}

export function StatusTag({ status }: { status: LeaveStatus }) {
  const { t } = useTranslation();
  const config: Record<LeaveStatus, { color: string; label: string }> = {
    DRAFT: { color: 'default', label: t('approval.status.DRAFT') },
    PENDING: { color: 'processing', label: t('approval.status.PENDING') },
    APPROVED: { color: 'success', label: t('approval.status.APPROVED') },
    REJECTED: { color: 'error', label: t('approval.status.REJECTED') },
    WITHDRAWN: { color: 'warning', label: t('approval.status.WITHDRAWN') },
  };
  const item = config[status];
  return <Tag color={item.color}>{item.label}</Tag>;
}

export function leaveTypeLabel(value: string) {
  const key = 'approval.leaveType.' + value;
  const translated = i18n.t(key);
  return translated === key ? value : translated;
}

export function periodLabel(value: string) {
  const key = 'approval.period.' + value;
  const translated = i18n.t(key);
  return translated === key ? value : translated;
}

'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from '@/lib/nextCompat';
import {
  Avatar,
  Button,
  Card,
  Empty,
  Modal,
  Segmented,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { message } from '@/lib/antdMessage';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { formatOaApiError, leaveApi, type LeaveApplication, type LeaveStatus } from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';
import { useTranslation } from 'react-i18next';
import i18n from '@/i18n';

const STATUS_FILTER_KEYS: (LeaveStatus | '')[] = [
  '', 'DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN',
];

export default function MyApplicationsPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [records, setRecords] = useState<LeaveApplication[]>([]);
  const [status, setStatus] = useState<LeaveStatus | undefined>();
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [actingId, setActingId] = useState<number>();

  const statusOptions = STATUS_FILTER_KEYS.map((value) => ({
    value,
    label: value ? t(`approval.status.${value}`) : t('approval.myApplications.statusFilterAll'),
  }));

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await leaveApi.mine({ status, page, size: 20 });
      setRecords(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setLoading(false);
    }
  }, [page, status]);

  useEffect(() => { void load(); }, [load]);

  const pageMetrics = useMemo(() => ({
    pending: records.filter((item) => item.status === 'PENDING').length,
    completed: records.filter((item) => item.status === 'APPROVED').length,
    attention: records.filter((item) => item.status === 'REJECTED' || item.overdue).length,
  }), [records]);

  const withdraw = (item: LeaveApplication) => {
    Modal.confirm({
      title: t('approval.myApplications.withdrawConfirmTitle'),
      content: t('approval.myApplications.withdrawConfirmContent'),
      okText: t('approval.myApplications.withdrawConfirmOk'),
      okButtonProps: { danger: true },
      onOk: async () => {
        setActingId(item.id);
        try {
          await leaveApi.withdraw(item.id, item.version);
          message.success(t('approval.myApplications.withdrawSuccess'));
          await load();
        } catch (error) {
          message.error(formatOaApiError(error));
          await load();
        } finally {
          setActingId(undefined);
        }
      },
    });
  };

  const remind = (item: LeaveApplication) => {
    Modal.confirm({
      title: t('approval.myApplications.remindConfirmTitle'),
      content: t('approval.myApplications.remindConfirmContent', {
        name: item.approverName || t('approval.myApplications.approverFallback'),
      }),
      okText: t('approval.myApplications.remindConfirmOk'),
      onOk: async () => {
        setActingId(item.id);
        try {
          await leaveApi.remind(item.id, item.version);
          message.success(t('approval.myApplications.remindSuccess'));
        } catch (error) {
          message.error(formatOaApiError(error));
        } finally {
          setActingId(undefined);
          await load();
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
          {item.canEdit && (
            <Button
              size="small"
              icon={<OaIcon name="edit" />}
              onClick={() => router.push(`/oa/leave-application?id=${item.id}`)}
            >
              {t('approval.myApplications.editButton')}
            </Button>
          )}
          {item.canWithdraw && (
            <Button
              size="small"
              danger
              loading={actingId === item.id}
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
                disabled={!item.canRemind}
                loading={actingId === item.id}
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
        <Button
          type="primary"
          size="large"
          icon={<OaIcon name="add" />}
          onClick={() => router.push('/oa/leave-application')}
        >
          {t('approval.myApplications.newLeave')}
        </Button>
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
            options={statusOptions}
            onChange={(value) => {
              setStatus((value || undefined) as LeaveStatus | undefined);
              setPage(1);
            }}
          />
          <Button icon={<OaIcon name="reload" />} onClick={() => void load()}>{t('common.refresh')}</Button>
        </div>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={records}
          loading={loading}
          locale={{ emptyText: <Empty description={t('approval.myApplications.empty')} /> }}
          scroll={{ x: 1080 }}
          pagination={{
            current: page,
            pageSize: 20,
            total,
            showSizeChanger: false,
            showTotal: (value) => t('common.total', { count: value }),
            onChange: setPage,
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

'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from '@/lib/nextCompat';
import {
  Avatar,
  Button,
  Card,
  DatePicker,
  Empty,
  Segmented,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { message } from '@/lib/antdMessage';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { formatOaApiError, todoApi, type TodoItem } from '@/lib/oaApi';
import { leaveTypeLabel } from './MyApplicationsPage';
import { OaIcon } from '@/components/OaIcon';
import { useTranslation } from 'react-i18next';

const { RangePicker } = DatePicker;

export default function TodoListPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [records, setRecords] = useState<TodoItem[]>([]);
  const [status, setStatus] = useState('PENDING');
  const [range, setRange] = useState<[string, string]>();
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await todoApi.list({
        status: status || undefined,
        from: range?.[0],
        to: range?.[1],
        page,
        size: 20,
      });
      setRecords(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setLoading(false);
    }
  }, [page, range, status]);

  useEffect(() => { void load(); }, [load]);

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
              <Typography.Text strong>{t('pages.todo.subjectTitle', { name: item.applicantName, type: leaveTypeLabel(item.leaveType) })}</Typography.Text>
            </Space>
            <Typography.Text type="secondary">
              TK-{String(item.id).padStart(6, '0')} · {t('pages.todo.applicationIdLabel', { id: item.applicationId })}
            </Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: t('pages.todo.columnDuration'),
      dataIndex: 'durationHalfDays',
      width: 110,
      render: (value) => <Typography.Text strong>{t('pages.todo.durationValue', { count: value / 2 })}</Typography.Text>,
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
          onClick={() => router.push(`/oa/approval-tasks/${item.id}?from=todo`)}
        >
          {item.status === 'PENDING' ? t('pages.todo.actionProcess') : t('pages.todo.actionView')}
        </Button>
      ),
    },
  ];

  return (
    <section className="leave-list-workbench">
      <header className="leave-list-hero todo-list-hero">
        <div>
          <span className="leave-list-hero__kicker">APPROVAL INBOX</span>
          <Typography.Title level={2}>{t('pages.todo.title')}</Typography.Title>
          <Typography.Paragraph>
            {t('pages.todo.description')}
          </Typography.Paragraph>
        </div>
        <div className="todo-hero-indicator">
          <span>{t('pages.todo.heroPendingLabel')}</span>
          <strong>{status === 'PENDING' ? total : records.filter((item) => item.status === 'PENDING').length}</strong>
          <small>{overdueCount > 0 ? t('pages.todo.heroOverdue', { count: overdueCount }) : t('pages.todo.heroNoOverdue')}</small>
        </div>
      </header>

      <Card className="leave-list-card" variant="borderless">
        <div className="todo-toolbar">
          <Segmented
            value={status}
            options={[
              { value: 'PENDING', label: t('pages.todo.statusPending') },
              { value: 'APPROVED', label: t('pages.todo.statusApproved') },
              { value: 'REJECTED', label: t('pages.todo.statusRejected') },
              { value: 'CANCELLED', label: t('pages.todo.statusCancelled') },
              { value: '', label: t('common.all') },
            ]}
            onChange={(value) => { setStatus(String(value)); setPage(1); }}
          />
          <Space wrap>
            <RangePicker
              showTime
              onChange={(dates) => {
                setRange(dates?.[0] && dates[1]
                  ? [dates[0].toISOString(), dates[1].toISOString()]
                  : undefined);
                setPage(1);
              }}
            />
            <Button icon={<OaIcon name="reload" />} onClick={() => void load()}>{t('common.refresh')}</Button>
          </Space>
        </div>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={records}
          loading={loading}
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
            pageSize: 20,
            total,
            showSizeChanger: false,
            showTotal: (value) => t('pages.todo.totalTasks', { count: value }),
            onChange: setPage,
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

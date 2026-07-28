'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Button,
  Card,
  DatePicker,
  Empty,
  Segmented,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { formatOaApiError, todoApi, type TodoItem } from '@/lib/oaApi';
import { leaveTypeLabel } from './MyApplicationsPage';
import { OaIcon } from '@/components/OaIcon';

const { RangePicker } = DatePicker;

export default function TodoListPage() {
  const router = useRouter();
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
      title: '待审批事项',
      key: 'subject',
      width: 260,
      render: (_, item) => (
        <div className="leave-table-subject">
          <span className="leave-table-subject__icon is-approval"><OaIcon name="approval" /></span>
          <div>
            <Typography.Text strong>{item.applicantName}的{leaveTypeLabel(item.leaveType)}申请</Typography.Text>
            <Typography.Text type="secondary">
              TK-{String(item.id).padStart(6, '0')} · 申请 #{item.applicationId}
            </Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: '申请时长',
      dataIndex: 'durationHalfDays',
      width: 110,
      render: (value) => <Typography.Text strong>{value / 2} 天</Typography.Text>,
    },
    {
      title: '提交时间',
      dataIndex: 'submittedAt',
      width: 170,
      render: formatTime,
    },
    {
      title: '处理时限',
      key: 'sla',
      width: 190,
      render: (_, item) => (
        <div className="todo-sla-cell">
          <Typography.Text>{item.dueAt ? formatTime(item.dueAt) : '未配置'}</Typography.Text>
          {item.overdue
            ? <Tag color="error">已超时</Tag>
            : item.status === 'PENDING' && <Tag color="processing">计时中</Tag>}
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value) => <TodoStatusTag status={value} />,
    },
    {
      title: '操作',
      width: 130,
      fixed: 'right',
      render: (_, item) => (
        <Button
          type={item.status === 'PENDING' ? 'primary' : 'link'}
          size="small"
          onClick={() => router.push(`/oa/approval-tasks/${item.id}`)}
        >
          {item.status === 'PENDING' ? '立即处理' : '查看详情'}
        </Button>
      ),
    },
  ];

  return (
    <section className="leave-list-workbench">
      <header className="leave-list-hero todo-list-hero">
        <div>
          <span className="leave-list-hero__kicker">APPROVAL INBOX</span>
          <Typography.Title level={2}>审批工作台</Typography.Title>
          <Typography.Paragraph>
            仅展示分配给当前账号的真实任务，按处理时限优先完成审批。
          </Typography.Paragraph>
        </div>
        <div className="todo-hero-indicator">
          <span>当前待处理</span>
          <strong>{status === 'PENDING' ? total : records.filter((item) => item.status === 'PENDING').length}</strong>
          <small>{overdueCount > 0 ? `${overdueCount} 项已超时` : '暂无超时任务'}</small>
        </div>
      </header>

      <Card className="leave-list-card" bordered={false}>
        <div className="todo-toolbar">
          <Segmented
            value={status}
            options={[
              { value: 'PENDING', label: '待处理' },
              { value: 'APPROVED', label: '已通过' },
              { value: 'REJECTED', label: '已退回' },
              { value: 'CANCELLED', label: '已取消' },
              { value: '', label: '全部' },
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
            <Button icon={<OaIcon name="reload" />} onClick={() => void load()}>刷新</Button>
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
                description={status === 'PENDING' ? '当前没有待处理任务' : '当前筛选下暂无记录'}
              />
            ),
          }}
          scroll={{ x: 1020 }}
          pagination={{
            current: page,
            pageSize: 20,
            total,
            showSizeChanger: false,
            showTotal: (value) => `共 ${value} 项任务`,
            onChange: setPage,
          }}
        />
      </Card>
    </section>
  );
}

function TodoStatusTag({ status }: { status: string }) {
  const config: Record<string, { color: string; label: string }> = {
    PENDING: { color: 'processing', label: '待处理' },
    APPROVED: { color: 'success', label: '已通过' },
    REJECTED: { color: 'error', label: '已退回' },
    CANCELLED: { color: 'default', label: '已取消' },
  };
  const item = config[status] || { color: 'default', label: status };
  return <Tag color={item.color}>{item.label}</Tag>;
}

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';
}

'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button, Card, DatePicker, Empty, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
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

  const columns: ColumnsType<TodoItem> = [
    { title: '待办编号', dataIndex: 'id', width: 100, render: (value) => `#${value}` },
    { title: '申请人', dataIndex: 'applicantName', width: 150 },
    { title: '类型', dataIndex: 'leaveType', width: 110, render: leaveTypeLabel },
    { title: '天数', dataIndex: 'durationHalfDays', width: 90, render: (value) => `${value / 2} 天` },
    { title: '提交时间', dataIndex: 'submittedAt', width: 190, render: formatTime },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value, item) => (
        <Space>
          <Tag color={value === 'PENDING' ? 'processing' : 'default'}>{value === 'PENDING' ? '待处理' : value}</Tag>
          {item.overdue && <Tag color="error">已超时</Tag>}
        </Space>
      ),
    },
    {
      title: '操作',
      width: 110,
      render: (_, item) => (
        <Button type="link" onClick={() => router.push(`/oa/approval-tasks/${item.id}`)}>
          查看详情
        </Button>
      ),
    },
  ];

  return (
    <section className="oa-domain-page">
      <div className="oa-domain-heading">
        <div>
          <Typography.Title level={3}>我的待办</Typography.Title>
          <Typography.Paragraph type="secondary">仅展示分配给当前登录用户的真实审批任务。</Typography.Paragraph>
        </div>
      </div>
      <Card className="oa-domain-card">
        <div className="oa-domain-toolbar">
          <Select
            value={status}
            style={{ width: 150 }}
            options={[
              { value: 'PENDING', label: '待处理' },
              { value: 'APPROVED', label: '已通过' },
              { value: 'REJECTED', label: '已退回' },
              { value: 'CANCELLED', label: '已取消' },
              { value: '', label: '全部状态' },
            ]}
            onChange={(value) => { setStatus(value); setPage(1); }}
          />
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
        </div>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={records}
          loading={loading}
          locale={{ emptyText: <Empty description="暂无待办" /> }}
          scroll={{ x: 900 }}
          pagination={{
            current: page, pageSize: 20, total, showSizeChanger: false, onChange: setPage,
          }}
        />
      </Card>
    </section>
  );
}

function formatTime(value?: string) {
  return value ? new Date(value).toLocaleString() : '-';
}

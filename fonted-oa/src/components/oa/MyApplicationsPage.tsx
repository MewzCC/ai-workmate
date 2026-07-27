'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button, Card, Empty, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { formatOaApiError, leaveApi, type LeaveApplication, type LeaveStatus } from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '审批中' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已退回' },
  { value: 'WITHDRAWN', label: '已撤回' },
];

export default function MyApplicationsPage() {
  const router = useRouter();
  const [records, setRecords] = useState<LeaveApplication[]>([]);
  const [status, setStatus] = useState<LeaveStatus | undefined>();
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [actingId, setActingId] = useState<number>();

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

  const withdraw = (item: LeaveApplication) => {
    Modal.confirm({
      title: '确认撤回申请？',
      content: '撤回后原审批待办将立即失效，且该申请不能再次提交。',
      okText: '确认撤回',
      okButtonProps: { danger: true },
      onOk: async () => {
        setActingId(item.id);
        try {
          await leaveApi.withdraw(item.id, item.version);
          message.success('申请已撤回');
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

  const columns: ColumnsType<LeaveApplication> = [
    { title: '申请编号', dataIndex: 'id', width: 100, render: (value) => `#${value}` },
    { title: '类型', dataIndex: 'leaveType', width: 110, render: leaveTypeLabel },
    {
      title: '请假区间',
      key: 'range',
      render: (_, item) => `${item.startDate} ${periodLabel(item.startPeriod)} — ${item.endDate} ${periodLabel(item.endPeriod)}`,
    },
    { title: '天数', dataIndex: 'durationDays', width: 90, render: (value) => `${value} 天` },
    { title: '状态', dataIndex: 'status', width: 105, render: (value) => <StatusTag status={value} /> },
    {
      title: '审批人',
      dataIndex: 'approverName',
      width: 140,
      render: (value) => value || '-',
    },
    {
      title: '操作',
      key: 'actions',
      width: 210,
      render: (_, item) => (
        <Space>
          {item.canEdit && (
            <Button size="small" icon={<OaIcon name="edit" />}
              onClick={() => router.push(`/oa/leave-application?id=${item.id}`)}>
              继续编辑
            </Button>
          )}
          {item.canWithdraw && (
            <Button size="small" danger loading={actingId === item.id}
              onClick={() => withdraw(item)}>
              撤回
            </Button>
          )}
          {item.taskId && (
            <Button size="small" type="link"
              onClick={() => router.push(`/oa/approval-tasks/${item.taskId}`)}>
              查看时间线
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <section className="oa-domain-page">
      <div className="oa-domain-heading">
        <div>
          <Typography.Title level={3}>我的申请</Typography.Title>
          <Typography.Paragraph type="secondary">查看草稿、审批进度与最终结果。</Typography.Paragraph>
        </div>
        <Button type="primary" icon={<OaIcon name="add" />}
          onClick={() => router.push('/oa/leave-application')}>
          新建请假申请
        </Button>
      </div>
      <Card className="oa-domain-card">
        <div className="oa-domain-toolbar">
          <Select
            value={status || ''}
            options={statusOptions}
            style={{ width: 160 }}
            onChange={(value) => { setStatus((value || undefined) as LeaveStatus | undefined); setPage(1); }}
          />
          <Button icon={<OaIcon name="reload" />} onClick={() => void load()}>刷新</Button>
        </div>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={records}
          loading={loading}
          locale={{ emptyText: <Empty description="暂无请假申请" /> }}
          scroll={{ x: 980 }}
          pagination={{
            current: page,
            pageSize: 20,
            total,
            showSizeChanger: false,
            onChange: setPage,
          }}
        />
      </Card>
    </section>
  );
}

export function StatusTag({ status }: { status: LeaveStatus }) {
  const config: Record<LeaveStatus, { color: string; label: string }> = {
    DRAFT: { color: 'default', label: '草稿' },
    PENDING: { color: 'processing', label: '审批中' },
    APPROVED: { color: 'success', label: '已通过' },
    REJECTED: { color: 'error', label: '已退回' },
    WITHDRAWN: { color: 'warning', label: '已撤回' },
  };
  const item = config[status];
  return <Tag color={item.color}>{item.label}</Tag>;
}

export function leaveTypeLabel(value: string) {
  const labels: Record<string, string> = {
    ANNUAL: '年假', PERSONAL: '事假', SICK: '病假', MARRIAGE: '婚假',
    MATERNITY: '产假', PATERNITY: '陪产假', BEREAVEMENT: '丧假',
    COMPENSATORY: '调休', OTHER: '其他',
  };
  return labels[value] || value;
}

export function periodLabel(value: string) {
  return value === 'AM' ? '上午' : '下午';
}

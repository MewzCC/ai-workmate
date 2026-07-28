'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from '@/lib/nextCompat';
import {
  Button,
  Card,
  Empty,
  Modal,
  Segmented,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { message } from '@/lib/antdMessage';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { formatOaApiError, leaveApi, type LeaveApplication, type LeaveStatus } from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';

const statusOptions = [
  { value: '', label: '全部' },
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

  const pageMetrics = useMemo(() => ({
    pending: records.filter((item) => item.status === 'PENDING').length,
    completed: records.filter((item) => item.status === 'APPROVED').length,
    attention: records.filter((item) => item.status === 'REJECTED' || item.overdue).length,
  }), [records]);

  const withdraw = (item: LeaveApplication) => {
    Modal.confirm({
      title: '确认撤回申请？',
      content: '撤回后原审批待办立即失效，该申请进入终态，无法再次提交。',
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
    {
      title: '申请事项',
      key: 'application',
      width: 230,
      render: (_, item) => (
        <div className="leave-table-subject">
          <span className="leave-table-subject__icon"><OaIcon name="form" /></span>
          <div>
            <Typography.Text strong>{leaveTypeLabel(item.leaveType)}申请</Typography.Text>
            <Typography.Text type="secondary">
              LV-{String(item.id).padStart(6, '0')} · {dayjs(item.createdAt).format('MM-DD HH:mm')}
            </Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: '请假时间',
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
      title: '时长',
      dataIndex: 'durationDays',
      width: 90,
      render: (value) => <Typography.Text strong>{value} 天</Typography.Text>,
    },
    {
      title: '流程进度',
      key: 'progress',
      width: 180,
      render: (_, item) => (
        <div className="leave-table-progress">
          <StatusTag status={item.status} />
          <Typography.Text type="secondary">
            {item.status === 'DRAFT'
              ? '等待提交'
              : item.status === 'PENDING'
                ? `${item.approverName || '审批人'}处理中`
                : '流程已结束'}
          </Typography.Text>
        </div>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      fixed: 'right',
      render: (_, item) => (
        <Space>
          {item.canEdit && (
            <Button
              size="small"
              icon={<OaIcon name="edit" />}
              onClick={() => router.push(`/oa/leave-application?id=${item.id}`)}
            >
              继续编辑
            </Button>
          )}
          {item.canWithdraw && (
            <Button
              size="small"
              danger
              loading={actingId === item.id}
              onClick={() => withdraw(item)}
            >
              撤回
            </Button>
          )}
          {item.taskId && (
            <Button
              size="small"
              type="link"
              onClick={() => router.push(`/oa/approval-tasks/${item.taskId}`)}
            >
              流程详情
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
          <Typography.Title level={2}>我的请假申请</Typography.Title>
          <Typography.Paragraph>集中查看草稿、审批进度、处理结果和完整流程记录。</Typography.Paragraph>
        </div>
        <Button
          type="primary"
          size="large"
          icon={<OaIcon name="add" />}
          onClick={() => router.push('/oa/leave-application')}
        >
          发起请假
        </Button>
      </header>

      <div className="leave-metric-strip">
        <div><span>当前筛选</span><strong>{total}</strong><small>条申请</small></div>
        <div><span>本页审批中</span><strong>{pageMetrics.pending}</strong><small>等待处理</small></div>
        <div><span>本页已完成</span><strong>{pageMetrics.completed}</strong><small>审批通过</small></div>
        <div className={pageMetrics.attention ? 'is-attention' : ''}>
          <span>需要关注</span><strong>{pageMetrics.attention}</strong><small>退回或超时</small>
        </div>
      </div>

      <Card className="leave-list-card" bordered={false}>
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
          <Button icon={<OaIcon name="reload" />} onClick={() => void load()}>刷新</Button>
        </div>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={records}
          loading={loading}
          locale={{ emptyText: <Empty description="当前筛选下暂无申请" /> }}
          scroll={{ x: 1080 }}
          pagination={{
            current: page,
            pageSize: 20,
            total,
            showSizeChanger: false,
            showTotal: (value) => `共 ${value} 条`,
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
    ANNUAL: '年假',
    PERSONAL: '事假',
    SICK: '病假',
    MARRIAGE: '婚假',
    MATERNITY: '产假',
    PATERNITY: '陪产假',
    BEREAVEMENT: '丧假',
    COMPENSATORY: '调休',
    OTHER: '其他',
  };
  return labels[value] || value;
}

export function periodLabel(value: string) {
  return value === 'AM' ? '上午' : '下午';
}

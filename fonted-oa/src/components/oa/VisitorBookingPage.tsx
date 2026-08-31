'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
} from 'antd';
import { message } from '@/lib/antdMessage';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import {
  adminAssetsApi,
  type VisitorBooking,
  type VisitorBookingPayload,
  type VisitorBookingStatus,
} from '@/lib/adminAssetsApi';
import { formatOaApiError } from '@/lib/oaApi';
import AdminAssetsPageShell from './AdminAssetsPageShell';

const STATUS_TAG_COLOR: Record<VisitorBookingStatus, string> = {
  PENDING: 'processing',
  APPROVED: 'success',
  CHECKED_IN: 'processing',
  REJECTED: 'error',
  WITHDRAWN: 'default',
  VISITED: 'cyan',
  LEFT: 'default',
  NO_SHOW: 'error',
};

interface BookingFormValues {
  visitorName: string;
  visitorCompany?: string;
  visitorPhone?: string;
  purpose: string;
  hostUserId: number;
  expectedVisitAt: Dayjs;
  expectedLeaveAt?: Dayjs | null;
  plateNumber?: string;
  partySize?: number;
}

export default function VisitorBookingPage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('mine');
  const [mine, setMine] = useState<VisitorBooking[]>([]);
  const [pending, setPending] = useState<VisitorBooking[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);

  const [createOpen, setCreateOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<BookingFormValues>();

  const [decideTarget, setDecideTarget] = useState<VisitorBooking | null>(null);
  const [decideOpen, setDecideOpen] = useState(false);
  const [deciding, setDeciding] = useState(false);
  const [decideForm] = Form.useForm<{ comment?: string }>();

  const loadMine = useCallback(async (p = page, s = size) => {
    setLoading(true);
    try {
      const res = await adminAssetsApi.listMyVisitorBookings({ page: p, size: s });
      setMine(res.records);
      setTotal(res.total);
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setLoading(false);
    }
  }, [page, size]);

  const loadPending = useCallback(async () => {
    setLoading(true);
    try {
      const res = await adminAssetsApi.listPendingVisitorBookings({ page: 1, size: 100 });
      setPending(res.records);
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadMine(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleTabChange = (key: string) => {
    setActiveTab(key);
    if (key === 'pending') {
      loadPending();
    } else {
      loadMine(1);
    }
  };

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const payload: VisitorBookingPayload = {
        visitorName: values.visitorName,
        visitorCompany: values.visitorCompany,
        visitorPhone: values.visitorPhone,
        purpose: values.purpose,
        hostUserId: values.hostUserId,
        expectedVisitAt: values.expectedVisitAt.format('YYYY-MM-DDTHH:mm:ss'),
        expectedLeaveAt: values.expectedLeaveAt
          ? values.expectedLeaveAt.format('YYYY-MM-DDTHH:mm:ss')
          : undefined,
        plateNumber: values.plateNumber,
        partySize: values.partySize,
      };
      await adminAssetsApi.submitVisitorBooking(payload);
      message.success(t('adminAssets.visitor.submitSuccess'));
      setCreateOpen(false);
      form.resetFields();
      await loadMine(1);
    } catch (err) {
      if (err instanceof Error && err.name === 'ValidationError') return;
      message.error(formatOaApiError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleWithdraw = async (record: VisitorBooking) => {
    try {
      await adminAssetsApi.withdrawVisitorBooking(record.id, { version: record.version });
      message.success(t('adminAssets.visitor.withdrawSuccess'));
      await loadMine(page);
    } catch (err) {
      message.error(formatOaApiError(err));
    }
  };

  const handleVisitAction = async (
    record: VisitorBooking,
    action: 'checkIn' | 'arrive' | 'leave' | 'noShow',
  ) => {
    try {
      const payload = { version: record.version };
      if (action === 'checkIn') await adminAssetsApi.checkInVisitor(record.id, payload);
      if (action === 'arrive') await adminAssetsApi.markVisitorArrived(record.id, payload);
      if (action === 'leave') await adminAssetsApi.leaveVisitor(record.id, payload);
      if (action === 'noShow') await adminAssetsApi.markVisitorNoShow(record.id, payload);
      message.success(t(`adminAssets.visitor.visitAction.${action}Success`));
      await loadMine(page);
    } catch (err) {
      message.error(formatOaApiError(err));
    }
  };

  const openDecide = (record: VisitorBooking) => {
    setDecideTarget(record);
    decideForm.resetFields();
    setDecideOpen(true);
  };

  const handleDecide = async (action: 'approve' | 'reject') => {
    if (!decideTarget || !decideTarget.taskId) return;
    try {
      const values = await decideForm.validateFields();
      setDeciding(true);
      const payload = {
        version: decideTarget.taskVersion ?? 0,
        comment: values.comment,
      };
      if (action === 'approve') {
        await adminAssetsApi.approveVisitorBooking(decideTarget.taskId, payload);
        message.success(t('adminAssets.visitor.approveSuccess'));
      } else {
        await adminAssetsApi.rejectVisitorBooking(decideTarget.taskId, payload);
        message.success(t('adminAssets.visitor.rejectSuccess'));
      }
      setDecideOpen(false);
      setDecideTarget(null);
      decideForm.resetFields();
      await loadPending();
    } catch (err) {
      if (err instanceof Error && err.name === 'ValidationError') return;
      message.error(formatOaApiError(err));
    } finally {
      setDeciding(false);
    }
  };

  const mineColumns: ColumnsType<VisitorBooking> = [
    {
      title: t('adminAssets.visitor.visitorName'),
      dataIndex: 'visitorName',
      key: 'visitorName',
    },
    {
      title: t('adminAssets.visitor.visitorCompany'),
      dataIndex: 'visitorCompany',
      key: 'visitorCompany',
      render: (v?: string | null) => v || '-',
    },
    {
      title: t('adminAssets.visitor.purpose'),
      dataIndex: 'purpose',
      key: 'purpose',
      ellipsis: true,
    },
    {
      title: t('adminAssets.visitor.expectedVisitAt'),
      dataIndex: 'expectedVisitAt',
      key: 'expectedVisitAt',
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: t('adminAssets.common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (s: VisitorBookingStatus) => (
        <Tag color={STATUS_TAG_COLOR[s]}>
          {t(`adminAssets.visitor.status.${s}`, { defaultValue: s })}
        </Tag>
      ),
    },
    {
      title: t('adminAssets.common.submittedAt'),
      dataIndex: 'submittedAt',
      key: 'submittedAt',
      render: (v?: string | null) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: t('adminAssets.visitor.visitProgress'),
      key: 'visitProgress',
      responsive: ['lg'],
      render: (_: unknown, record: VisitorBooking) => {
        const occurredAt = record.leftAt || record.visitedAt || record.checkedInAt || record.noShowAt;
        return occurredAt ? (
          <Space direction="vertical" size={0}>
            <span>{t(`adminAssets.visitor.status.${record.status}`)}</span>
            <span>{dayjs(occurredAt).format('YYYY-MM-DD HH:mm')}</span>
            <span>{record.registeredByName || '-'}</span>
          </Space>
        ) : '-';
      },
    },
    {
      title: t('adminAssets.common.action'),
      key: 'action',
      render: (_: unknown, record: VisitorBooking) => (
        <Space wrap>
          {record.canWithdraw && (
            <Button type="link" danger onClick={() => handleWithdraw(record)}>
              {t('adminAssets.common.withdraw')}
            </Button>
          )}
          {record.canCheckIn && (
            <Button type="link" onClick={() => void handleVisitAction(record, 'checkIn')}>
              {t('adminAssets.visitor.visitAction.checkIn')}
            </Button>
          )}
          {record.canMarkVisited && (
            <Button type="link" onClick={() => void handleVisitAction(record, 'arrive')}>
              {t('adminAssets.visitor.visitAction.arrive')}
            </Button>
          )}
          {record.canLeave && (
            <Button type="link" onClick={() => void handleVisitAction(record, 'leave')}>
              {t('adminAssets.visitor.visitAction.leave')}
            </Button>
          )}
          {record.canMarkNoShow && (
            <Button type="link" danger onClick={() => void handleVisitAction(record, 'noShow')}>
              {t('adminAssets.visitor.visitAction.noShow')}
            </Button>
          )}
          {!record.canWithdraw && !record.canCheckIn && !record.canMarkVisited
            && !record.canLeave && !record.canMarkNoShow && '-'}
        </Space>
      ),
    },
  ];

  const pendingColumns: ColumnsType<VisitorBooking> = [
    {
      title: t('adminAssets.common.applicantName'),
      dataIndex: 'applicantName',
      key: 'applicantName',
      render: (v?: string | null) => v || '-',
    },
    {
      title: t('adminAssets.visitor.visitorName'),
      dataIndex: 'visitorName',
      key: 'visitorName',
    },
    {
      title: t('adminAssets.visitor.purpose'),
      dataIndex: 'purpose',
      key: 'purpose',
      ellipsis: true,
    },
    {
      title: t('adminAssets.visitor.expectedVisitAt'),
      dataIndex: 'expectedVisitAt',
      key: 'expectedVisitAt',
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: t('adminAssets.common.submittedAt'),
      dataIndex: 'submittedAt',
      key: 'submittedAt',
      render: (v?: string | null) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: t('adminAssets.common.action'),
      key: 'action',
      render: (_: unknown, record: VisitorBooking) => (
        <Button type="link" onClick={() => openDecide(record)}>
          {t('adminAssets.common.decide')}
        </Button>
      ),
    },
  ];

  return (
    <AdminAssetsPageShell
      eyebrow={t('adminAssets.eyebrow')}
      title={t('adminAssets.visitor.title')}
      description={t('adminAssets.visitor.description')}
      actions={
        <Button type="primary" onClick={() => setCreateOpen(true)}>
          {t('adminAssets.visitor.create')}
        </Button>
      }
    >
      <Spin spinning={loading}>
        <Card className="oa-admin-assets-card oa-admin-assets-card--fill" variant="outlined">
          <Tabs
            activeKey={activeTab}
            onChange={handleTabChange}
            items={[
              {
                key: 'mine',
                label: t('adminAssets.visitor.myApplications'),
                children: (
                  <Table
                    rowKey="id"
                    columns={mineColumns}
                    dataSource={mine}
                    size="middle"
                    pagination={{
                      current: page,
                      pageSize: size,
                      total,
                      showSizeChanger: true,
                      onChange: (p, s) => {
                        setPage(p);
                        setSize(s);
                        loadMine(p, s);
                      },
                    }}
                    locale={{ emptyText: <Empty description={t('adminAssets.common.noData')} /> }}
                  />
                ),
              },
              {
                key: 'pending',
                label: t('adminAssets.visitor.pendingApproval'),
                children: (
                  <Table
                    rowKey="id"
                    columns={pendingColumns}
                    dataSource={pending}
                    size="middle"
                    pagination={false}
                    locale={{ emptyText: <Empty description={t('adminAssets.common.noData')} /> }}
                  />
                ),
              },
            ]}
          />
        </Card>

        <Modal
          title={t('adminAssets.visitor.create')}
          open={createOpen}
          onCancel={() => setCreateOpen(false)}
          onOk={handleCreate}
          confirmLoading={submitting}
          okText={t('adminAssets.common.submit')}
          destroyOnClose
          width={560}
        >
          <Form form={form} layout="vertical" initialValues={{ partySize: 1 }}>
            <Form.Item
              name="visitorName"
              label={t('adminAssets.visitor.visitorName')}
              rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
            >
              <Input maxLength={60} />
            </Form.Item>
            <Form.Item name="visitorCompany" label={t('adminAssets.visitor.visitorCompany')}>
              <Input maxLength={120} />
            </Form.Item>
            <Form.Item name="visitorPhone" label={t('adminAssets.visitor.visitorPhone')}>
              <Input maxLength={40} />
            </Form.Item>
            <Form.Item
              name="purpose"
              label={t('adminAssets.visitor.purpose')}
              rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
            >
              <Input.TextArea rows={3} maxLength={200} showCount />
            </Form.Item>
            <Form.Item
              name="hostUserId"
              label={t('adminAssets.visitor.hostUserId')}
              rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
            >
              <InputNumber style={{ width: '100%' }} min={1} />
            </Form.Item>
            <Form.Item
              name="expectedVisitAt"
              label={t('adminAssets.visitor.expectedVisitAt')}
              rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
            >
              <DatePicker showTime style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="expectedLeaveAt" label={t('adminAssets.visitor.expectedLeaveAt')}>
              <DatePicker showTime style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="plateNumber" label={t('adminAssets.visitor.plateNumber')}>
              <Input maxLength={40} />
            </Form.Item>
            <Form.Item name="partySize" label={t('adminAssets.visitor.partySize')}>
              <InputNumber style={{ width: '100%' }} min={1} />
            </Form.Item>
          </Form>
        </Modal>

        <Modal
          title={t('adminAssets.visitor.decideTitle')}
          open={decideOpen}
          onCancel={() => {
            setDecideOpen(false);
            setDecideTarget(null);
          }}
          footer={[
            <Button
              key="reject"
              danger
              loading={deciding}
              onClick={() => handleDecide('reject')}
            >
              {t('adminAssets.common.reject')}
            </Button>,
            <Button
              key="approve"
              type="primary"
              loading={deciding}
              onClick={() => handleDecide('approve')}
            >
              {t('adminAssets.common.approve')}
            </Button>,
          ]}
          destroyOnClose
        >
          {decideTarget && (
            <div style={{ marginBottom: 16 }}>
              <p>
                <strong>{t('adminAssets.visitor.visitorName')}：</strong>
                {decideTarget.visitorName}
              </p>
              <p>
                <strong>{t('adminAssets.visitor.purpose')}：</strong>
                {decideTarget.purpose}
              </p>
              <p>
                <strong>{t('adminAssets.visitor.expectedVisitAt')}：</strong>
                {dayjs(decideTarget.expectedVisitAt).format('YYYY-MM-DD HH:mm')}
              </p>
            </div>
          )}
          <Form form={decideForm} layout="vertical">
            <Form.Item name="comment" label={t('adminAssets.common.approverComment')}>
              <Input.TextArea rows={3} maxLength={500} showCount />
            </Form.Item>
          </Form>
        </Modal>
      </Spin>
    </AdminAssetsPageShell>
  );
}

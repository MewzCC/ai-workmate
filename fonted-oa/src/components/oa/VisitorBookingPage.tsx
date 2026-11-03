'use client';

import { useEffect, useState } from 'react';
import {
  Button,
  Card,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Spin,
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
import { useAdminAssetApprovalQueue } from '@/hooks/useAdminAssetApprovalQueue';
import { isFormValidationError, useAsyncFormAction } from '@/hooks/useAsyncFormAction';
import { usePublishPageAgentContext } from './PageAgentContext';
import AdminAssetsPageShell from './AdminAssetsPageShell';
import ResponsiveTable from './ResponsiveTable';
import { BusinessUserSelect } from './BusinessUserSelect';

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
  const { activeTab, mine, pending, loading, total, page, size, setPage, setSize,
    loadMine, refresh, captureScope, handleTabChange, status, changeStatus, canCreate, canDecide, scope } = useAdminAssetApprovalQueue({
    pageId: 'visitor-booking', readPermission: 'visitor:read:self', createPermission: 'visitor:create',
    statuses: Object.keys(STATUS_TAG_COLOR) as VisitorBookingStatus[],
    mine: adminAssetsApi.listMyVisitorBookings, pending: adminAssetsApi.listPendingVisitorBookings,
  });

  const [createOpen, setCreateOpen] = useState(false);
  const createAction = useAsyncFormAction(scope);
  const submitting = createAction.busy;
  const [form] = Form.useForm<BookingFormValues>();
  const openCreate = () => { if (!canCreate || !captureScope()()) return; createAction.reset(); form.resetFields(); setCreateOpen(true); };
  usePublishPageAgentContext({ queue: activeTab.toUpperCase(), status, page, size });
  const [decideTarget, setDecideTarget] = useState<VisitorBooking | null>(null);
  const [decideOpen, setDecideOpen] = useState(false);
  const decideAction = useAsyncFormAction(scope);
  const deciding = decideAction.busy;
  const [decideForm] = Form.useForm<{ comment?: string }>();

  useEffect(() => {
    setCreateOpen(false); setDecideOpen(false); setDecideTarget(null);
    form.resetFields(); decideForm.resetFields();
  }, [scope, form, decideForm]);

  const handleCreate = async () => {
    if (!canCreate || submitting) return;
    const action = createAction.begin();
    if (!action) return;
    const isActorCurrent = captureScope();
    try {
      const values = await form.validateFields();
      if (!action.current()) return;
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
      if (action.current()) {
        message.success(t('adminAssets.visitor.submitSuccess'));
        setCreateOpen(false); form.resetFields();
      }
      if (isActorCurrent()) await refresh();
    } catch (err) {
      if (!action.current()) return;
      if (isFormValidationError(err)) return;
      message.error(formatOaApiError(err));
    } finally {
      action.finish();
    }
  };

  const handleWithdraw = async (record: VisitorBooking) => {
    const current = captureScope();
    if (!current() || !record.canWithdraw) return;
    try {
      await adminAssetsApi.withdrawVisitorBooking(record.id, { version: record.version });
      if (!current()) return;
      message.success(t('adminAssets.visitor.withdrawSuccess'));
      await refresh();
    } catch (err) {
      if (current()) message.error(formatOaApiError(err));
    }
  };

  const handleVisitAction = async (
    record: VisitorBooking,
    action: 'checkIn' | 'arrive' | 'leave' | 'noShow',
  ) => {
    const current = captureScope();
    if (!current() || !({ checkIn: record.canCheckIn, arrive: record.canMarkVisited, leave: record.canLeave, noShow: record.canMarkNoShow })[action]) return;
    try {
      const payload = { version: record.version };
      if (action === 'checkIn') await adminAssetsApi.checkInVisitor(record.id, payload);
      if (action === 'arrive') await adminAssetsApi.markVisitorArrived(record.id, payload);
      if (action === 'leave') await adminAssetsApi.leaveVisitor(record.id, payload);
      if (action === 'noShow') await adminAssetsApi.markVisitorNoShow(record.id, payload);
      if (!current()) return;
      message.success(t(`adminAssets.visitor.visitAction.${action}Success`));
      await refresh();
    } catch (err) {
      if (current()) message.error(formatOaApiError(err));
    }
  };

  const openDecide = (record: VisitorBooking) => {
    if (!canDecide || !record.canDecide || !captureScope()()) return;
    decideAction.reset();
    setDecideTarget(record);
    decideForm.resetFields();
    setDecideOpen(true);
  };

  const handleDecide = async (action: 'approve' | 'reject') => {
    if (!canDecide || deciding || !decideTarget || !decideTarget.taskId) return;
    const operation = decideAction.begin();
    if (!operation) return;
    const current = captureScope();
    try {
      const values = await decideForm.validateFields();
      if (!operation.current()) return;
      const payload = {
        version: decideTarget.taskVersion ?? 0,
        comment: values.comment,
      };
      if (action === 'approve') {
        await adminAssetsApi.approveVisitorBooking(decideTarget.taskId, payload);
      } else {
        await adminAssetsApi.rejectVisitorBooking(decideTarget.taskId, payload);
      }
      if (operation.current()) {
        message.success(t(`adminAssets.visitor.${action === 'approve' ? 'approveSuccess' : 'rejectSuccess'}`));
        setDecideOpen(false); setDecideTarget(null); decideForm.resetFields();
      }
      if (current()) await refresh();
    } catch (err) {
      if (!operation.current()) return;
      if (isFormValidationError(err)) return;
      message.error(formatOaApiError(err));
    } finally {
      operation.finish();
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
      actions={canCreate &&
        <Button type="primary" onClick={openCreate}>
          {t('adminAssets.visitor.create')}
        </Button>
      }
    >
      <Spin spinning={loading}>
        <Card className="oa-admin-assets-card oa-admin-assets-card--fill" variant="outlined">
          {activeTab === 'mine' && <Select allowClear value={status} onChange={changeStatus} style={{ width: 180 }}
            aria-label={t('adminAssets.common.status')} placeholder={t('adminAssets.common.status')}
            options={(Object.keys(STATUS_TAG_COLOR) as VisitorBookingStatus[]).map(value => ({ value, label: t(`adminAssets.visitor.status.${value}`) }))} />}
          <Tabs
            activeKey={activeTab}
            onChange={handleTabChange}
            items={[
              {
                key: 'mine',
                label: t('adminAssets.visitor.myApplications'),
                children: (
                  <ResponsiveTable
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
                  <ResponsiveTable
                    rowKey="id"
                    columns={pendingColumns}
                    dataSource={pending}
                    size="middle"
                    pagination={false}
                    locale={{ emptyText: <Empty description={t('adminAssets.common.noData')} /> }}
                  />
                ),
              },
            ].filter(item => item.key !== 'pending' || canDecide)}
          />
        </Card>

        <Modal
          title={t('adminAssets.visitor.create')}
          open={createOpen && canCreate}
          onCancel={() => { createAction.reset(); setCreateOpen(false); }}
          onOk={handleCreate}
          okButtonProps={{ loading: submitting }}
          cancelText={t(submitting ? 'common.close' : 'common.cancel')}
          okText={t('adminAssets.common.submit')}
          destroyOnClose
          width={560}
        >
          <Form form={form} disabled={submitting} layout="vertical" initialValues={{ partySize: 1 }}>
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
              <BusinessUserSelect query={adminAssetsApi.visitorHostOptions} active={createOpen && canCreate} disabled={submitting}
                scopeKey={scope} placeholder={t('adminAssets.visitor.hostUserId')} />
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
          open={decideOpen && canDecide}
          onCancel={() => {
            decideAction.reset();
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
          <Form form={decideForm} disabled={deciding} layout="vertical">
            <Form.Item name="comment" label={t('adminAssets.common.approverComment')}>
              <Input.TextArea rows={3} maxLength={500} showCount />
            </Form.Item>
          </Form>
        </Modal>
      </Spin>
    </AdminAssetsPageShell>
  );
}

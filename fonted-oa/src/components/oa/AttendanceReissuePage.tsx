'use client';

import { useEffect, useState } from 'react';
import {
  Button,
  Card,
  DatePicker,
  Empty,
  Form,
  Input,
  Modal,
  Select,
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
  attendanceApi,
  type AttendanceClockType,
  type AttendanceReissue,
  type AttendanceReissueStatus,
} from '@/lib/attendanceApi';
import { formatOaApiError } from '@/lib/oaApi';

const STATUS_TAG_COLOR: Record<AttendanceReissueStatus, string> = {
  PENDING: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
  WITHDRAWN: 'default',
};

interface ReissueFormValues {
  clockDate: Dayjs;
  clockType: AttendanceClockType;
  reason: string;
}

export default function AttendanceReissuePage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('mine');
  const [mine, setMine] = useState<AttendanceReissue[]>([]);
  const [pending, setPending] = useState<AttendanceReissue[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);

  const [createOpen, setCreateOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<ReissueFormValues>();

  const [decideTarget, setDecideTarget] = useState<AttendanceReissue | null>(null);
  const [decideOpen, setDecideOpen] = useState(false);
  const [deciding, setDeciding] = useState(false);
  const [decideForm] = Form.useForm<{ comment?: string }>();

  const loadMine = async (p = page, s = size) => {
    setLoading(true);
    try {
      const res = await attendanceApi.listMyReissues({ page: p, size: s });
      setMine(res.records);
      setTotal(res.total);
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setLoading(false);
    }
  };

  const loadPending = async () => {
    setLoading(true);
    try {
      const res = await attendanceApi.listPendingReissues({ page: 1, size: 100 });
      setPending(res.records);
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setLoading(false);
    }
  };

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
      await attendanceApi.submitReissue({
        clockDate: values.clockDate.format('YYYY-MM-DD'),
        clockType: values.clockType,
        reason: values.reason,
      });
      message.success(t('attendance.reissue.submitSuccess'));
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

  const handleDecide = async (decision: 'APPROVED' | 'REJECTED') => {
    if (!decideTarget) return;
    try {
      const values = await decideForm.validateFields();
      setDeciding(true);
      await attendanceApi.decideReissue(decideTarget.id, {
        decision,
        comment: values.comment,
      });
      message.success(
        t(`attendance.reissue.${decision === 'APPROVED' ? 'approveSuccess' : 'rejectSuccess'}`),
      );
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

  const mineColumns: ColumnsType<AttendanceReissue> = [
    {
      title: t('attendance.common.date'),
      dataIndex: 'clockDate',
      key: 'clockDate',
      render: (v: string) => dayjs(v).format('YYYY-MM-DD'),
    },
    {
      title: t('attendance.reissue.clockType'),
      dataIndex: 'clockType',
      key: 'clockType',
      render: (v: AttendanceClockType) =>
        t(`attendance.reissue.clockTypeOption.${v}`, { defaultValue: v }),
    },
    {
      title: t('attendance.reissue.reason'),
      dataIndex: 'reason',
      key: 'reason',
      ellipsis: true,
    },
    {
      title: t('attendance.reissue.approverName'),
      dataIndex: 'approverName',
      key: 'approverName',
      render: (v?: string | null) => v || '-',
    },
    {
      title: t('attendance.common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (s: AttendanceReissueStatus) => (
        <Tag color={STATUS_TAG_COLOR[s]}>
          {t(`attendance.reissue.status.${s}`, { defaultValue: s })}
        </Tag>
      ),
    },
    {
      title: t('attendance.common.submittedAt'),
      dataIndex: 'submittedAt',
      key: 'submittedAt',
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm'),
    },
  ];

  const pendingColumns: ColumnsType<AttendanceReissue> = [
    {
      title: t('attendance.common.applicantName'),
      dataIndex: 'applicantName',
      key: 'applicantName',
    },
    {
      title: t('attendance.common.date'),
      dataIndex: 'clockDate',
      key: 'clockDate',
      render: (v: string) => dayjs(v).format('YYYY-MM-DD'),
    },
    {
      title: t('attendance.reissue.clockType'),
      dataIndex: 'clockType',
      key: 'clockType',
      render: (v: AttendanceClockType) =>
        t(`attendance.reissue.clockTypeOption.${v}`, { defaultValue: v }),
    },
    {
      title: t('attendance.reissue.reason'),
      dataIndex: 'reason',
      key: 'reason',
      ellipsis: true,
    },
    {
      title: t('attendance.common.submittedAt'),
      dataIndex: 'submittedAt',
      key: 'submittedAt',
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: t('attendance.common.action'),
      key: 'action',
      render: (_: unknown, record: AttendanceReissue) => (
        <Button
          type="link"
          onClick={() => {
            setDecideTarget(record);
            decideForm.resetFields();
            setDecideOpen(true);
          }}
        >
          {t('attendance.reissue.decide')}
        </Button>
      ),
    },
  ];

  return (
    <Spin spinning={loading}>
      <Card variant="outlined">
        <Tabs
          activeKey={activeTab}
          onChange={handleTabChange}
          items={[
            {
              key: 'mine',
              label: t('attendance.reissue.myApplications'),
              children: (
                <>
                  <Space style={{ marginBottom: 16 }}>
                    <Button type="primary" onClick={() => setCreateOpen(true)}>
                      {t('attendance.reissue.create')}
                    </Button>
                  </Space>
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
                    locale={{ emptyText: <Empty description={t('attendance.common.noData')} /> }}
                  />
                </>
              ),
            },
            {
              key: 'pending',
              label: t('attendance.reissue.pendingApproval'),
              children: (
                <Table
                  rowKey="id"
                  columns={pendingColumns}
                  dataSource={pending}
                  size="middle"
                  pagination={false}
                  locale={{ emptyText: <Empty description={t('attendance.common.noData')} /> }}
                />
              ),
            },
          ]}
        />
      </Card>

      <Modal
        title={t('attendance.reissue.create')}
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={handleCreate}
        confirmLoading={submitting}
        okText={t('attendance.reissue.submit')}
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ clockType: 'CLOCK_IN' }}>
          <Form.Item
            name="clockDate"
            label={t('attendance.common.date')}
            rules={[{ required: true, message: t('attendance.reissue.dateRequired') }]}
          >
            <DatePicker
              style={{ width: '100%' }}
              disabledDate={(d) => d && d.isAfter(dayjs(), 'day')}
            />
          </Form.Item>
          <Form.Item
            name="clockType"
            label={t('attendance.reissue.clockType')}
            rules={[{ required: true, message: t('attendance.reissue.clockTypeRequired') }]}
          >
            <Select
              options={[
                { value: 'CLOCK_IN', label: t('attendance.reissue.clockTypeOption.CLOCK_IN') },
                { value: 'CLOCK_OUT', label: t('attendance.reissue.clockTypeOption.CLOCK_OUT') },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="reason"
            label={t('attendance.reissue.reason')}
            rules={[{ required: true, message: t('attendance.reissue.reasonRequired') }]}
          >
            <Input.TextArea rows={4} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('attendance.reissue.decideTitle')}
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
            onClick={() => handleDecide('REJECTED')}
          >
            {t('attendance.reissue.reject')}
          </Button>,
          <Button
            key="approve"
            type="primary"
            loading={deciding}
            onClick={() => handleDecide('APPROVED')}
          >
            {t('attendance.reissue.approve')}
          </Button>,
        ]}
        destroyOnClose
      >
        {decideTarget && (
          <div style={{ marginBottom: 16 }}>
            <p>
              <strong>{t('attendance.common.applicantName')}：</strong>
              {decideTarget.applicantName}
            </p>
            <p>
              <strong>{t('attendance.common.date')}：</strong>
              {dayjs(decideTarget.clockDate).format('YYYY-MM-DD')}
            </p>
            <p>
              <strong>{t('attendance.reissue.clockType')}：</strong>
              {t(`attendance.reissue.clockTypeOption.${decideTarget.clockType}`)}
            </p>
            <p>
              <strong>{t('attendance.reissue.reason')}：</strong>
              {decideTarget.reason}
            </p>
          </div>
        )}
        <Form form={decideForm} layout="vertical">
          <Form.Item name="comment" label={t('attendance.reissue.approverComment')}>
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Spin>
  );
}

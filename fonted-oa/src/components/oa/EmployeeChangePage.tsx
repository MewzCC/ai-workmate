'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Avatar,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  type TableProps,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/components/auth/AuthProvider';
import { OaIcon } from '@/components/OaIcon';
import { message } from '@/lib/antdMessage';
import {
  hrApi,
  type EmployeeChange,
  type EmployeeChangePayload,
  type EmployeeChangeStatus,
  type EmployeeChangeType,
  type OrganizationOverview,
} from '@/lib/hrApi';

interface ChangeFormValues extends Omit<EmployeeChangePayload, 'effectiveDate'> {
  effectiveDate: Dayjs;
}

const CHANGE_TYPES: EmployeeChangeType[] = ['ONBOARDING', 'REGULARIZATION', 'TRANSFER', 'OFFBOARDING'];
const STATUS_COLORS: Record<EmployeeChangeStatus, string> = {
  PENDING: 'processing', APPROVED: 'warning', EFFECTIVE: 'success', REJECTED: 'error', WITHDRAWN: 'default',
};

export default function EmployeeChangePage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const [form] = Form.useForm<ChangeFormValues>();
  const changeType = Form.useWatch('changeType', form);
  const [overview, setOverview] = useState<OrganizationOverview>();
  const [records, setRecords] = useState<EmployeeChange[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [acting, setActing] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<EmployeeChange>();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<EmployeeChangeStatus>();
  const [typeFilter, setTypeFilter] = useState<EmployeeChangeType>();
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);

  const canManage = user?.permissions.includes('hr:manage') ?? false;
  const needsTarget = changeType === 'ONBOARDING' || changeType === 'TRANSFER';

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [organization, changes] = await Promise.all([
        hrApi.overview(),
        hrApi.listEmployeeChanges({ keyword, status, changeType: typeFilter, page, size: 20 }),
      ]);
      setOverview(organization);
      setRecords(changes.records);
      setTotal(changes.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('errors.hr.employeeLoadFailed'));
    } finally {
      setLoading(false);
    }
  }, [keyword, page, status, t, typeFilter]);

  useEffect(() => { void load(); }, [load]);

  const metrics = useMemo(() => ({
    total,
    pending: records.filter((item) => item.status === 'PENDING').length,
    approved: records.filter((item) => item.status === 'EFFECTIVE').length,
    attention: records.filter((item) => item.status === 'REJECTED' || item.status === 'WITHDRAWN').length,
  }), [records, total]);

  const employeeOptions = (overview?.employees || []).map((employee) => ({
    value: employee.id,
    label: `${employee.name} · ${employee.email}`,
  }));
  const reviewerOptions = (overview?.employees || [])
    .filter((employee) => employee.id !== user?.id
      && ['SUPER_ADMIN', 'SYSTEM_ADMIN'].includes(employee.role)
      && employee.status === 1)
    .map((employee) => ({ value: employee.id, label: `${employee.name} · ${employee.email}` }));

  const openDetail = async (record: EmployeeChange) => {
    try {
      setDetail(await hrApi.employeeChangeDetail(record.id));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('errors.hr.employeeLoadFailed'));
    }
  };

  const submitChange = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      await hrApi.createEmployeeChange({
        ...values,
        effectiveDate: values.effectiveDate.format('YYYY-MM-DD'),
        targetDepartmentId: needsTarget ? values.targetDepartmentId : undefined,
        targetPositionId: needsTarget ? values.targetPositionId : undefined,
        targetSupervisorUserId: needsTarget ? values.targetSupervisorUserId : undefined,
      });
      message.success(t('employeeChange.createSuccess'));
      setCreateOpen(false);
      form.resetFields();
      setPage(1);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('errors.hr.employeeLoadFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  const approve = (record: EmployeeChange) => Modal.confirm({
    title: t('employeeChange.approveTitle'),
    content: t('employeeChange.approveContent'),
    okText: t('employeeChange.approve'),
    cancelText: t('common.cancel'),
    onOk: async () => {
      setActing(true);
      try {
        await hrApi.approveEmployeeChange(record.id, record.version);
        message.success(t('employeeChange.approveSuccess'));
        setDetail(undefined);
        await load();
      } catch (error) {
        message.error(error instanceof Error ? error.message : t('errors.hr.employeeLoadFailed'));
      } finally { setActing(false); }
    },
  });

  const reject = (record: EmployeeChange) => {
    let comment = '';
    Modal.confirm({
      title: t('employeeChange.rejectTitle'),
      okText: t('employeeChange.reject'),
      okButtonProps: { danger: true },
      cancelText: t('common.cancel'),
      content: (
        <Input.TextArea
          rows={4}
          placeholder={t('employeeChange.rejectPlaceholder')}
          onChange={(event) => { comment = event.target.value; }}
        />
      ),
      onOk: async () => {
        if (!comment.trim()) throw new Error(t('employeeChange.rejectRequired'));
        setActing(true);
        try {
          await hrApi.rejectEmployeeChange(record.id, record.version, comment.trim());
          message.success(t('employeeChange.rejectSuccess'));
          setDetail(undefined);
          await load();
        } catch (error) {
          message.error(error instanceof Error ? error.message : t('errors.hr.employeeLoadFailed'));
          throw error;
        } finally { setActing(false); }
      },
    });
  };

  const withdraw = (record: EmployeeChange) => Modal.confirm({
    title: t('employeeChange.withdrawTitle'),
    okText: t('employeeChange.withdraw'),
    okButtonProps: { danger: true },
    cancelText: t('common.cancel'),
    onOk: async () => {
      try {
        await hrApi.withdrawEmployeeChange(record.id, record.version);
        message.success(t('employeeChange.withdrawSuccess'));
        setDetail(undefined);
        await load();
      } catch (error) {
        message.error(error instanceof Error ? error.message : t('errors.hr.employeeLoadFailed'));
      }
    },
  });

  const columns: TableProps<EmployeeChange>['columns'] = [
    {
      title: t('employeeChange.column.employee'), key: 'employee',
      render: (_, record) => (
        <Space><Avatar>{record.employeeName.slice(0, 1)}</Avatar><div><strong>{record.employeeName}</strong><br /><Typography.Text type="secondary">{record.employeeEmail}</Typography.Text></div></Space>
      ),
    },
    { title: t('employeeChange.column.type'), dataIndex: 'changeType', render: (value) => <Tag>{t(`employeeChange.type.${value}`)}</Tag> },
    {
      title: t('employeeChange.column.organization'), key: 'organization',
      render: (_, record) => record.targetDepartmentName || record.targetPositionName ? (
        <Space direction="vertical" size={0}>
          <Typography.Text type="secondary">{t('employeeChange.currentOrganization', { department: record.currentDepartmentName || '-', position: record.currentPositionName || '-' })}</Typography.Text>
          <Typography.Text>{t('employeeChange.targetOrganization', { department: record.targetDepartmentName || '-', position: record.targetPositionName || '-' })}</Typography.Text>
        </Space>
      ) : t('employeeChange.unchangedOrganization'),
    },
    { title: t('employeeChange.column.effectiveDate'), dataIndex: 'effectiveDate' },
    { title: t('employeeChange.column.reviewer'), dataIndex: 'reviewApproverName' },
    { title: t('employeeChange.column.status'), dataIndex: 'status', render: (value) => <Tag color={STATUS_COLORS[value as EmployeeChangeStatus]}>{t(`employeeChange.status.${value}`)}</Tag> },
    {
      title: t('employeeChange.column.action'), key: 'action', fixed: 'right', width: 220,
      render: (_, record) => (
        <Space size={2}>
          <Button type="link" size="small" onClick={() => void openDetail(record)}>{t('employeeChange.view')}</Button>
          {record.canApprove && <Button type="link" size="small" onClick={() => approve(record)}>{t('employeeChange.approve')}</Button>}
          {record.canApprove && <Button danger type="link" size="small" onClick={() => reject(record)}>{t('employeeChange.reject')}</Button>}
          {record.canWithdraw && <Button danger type="link" size="small" onClick={() => withdraw(record)}>{t('employeeChange.withdraw')}</Button>}
        </Space>
      ),
    },
  ];

  return (
    <section className="employee-change-page">
      <header className="employee-change-hero">
        <div><span className="employee-change-hero__kicker">{t('employeeChange.kicker')}</span><Typography.Title level={2}>{t('employeeChange.title')}</Typography.Title><Typography.Paragraph>{t('employeeChange.description')}</Typography.Paragraph></div>
        {canManage && <Button type="primary" size="large" icon={<OaIcon name="employee-change" />} onClick={() => setCreateOpen(true)}>{t('employeeChange.create')}</Button>}
      </header>

      <div className="employee-change-metrics">
        <Card><Statistic title={t('employeeChange.metric.total')} value={metrics.total} /></Card>
        <Card><Statistic title={t('employeeChange.metric.pending')} value={metrics.pending} /></Card>
        <Card><Statistic title={t('employeeChange.metric.effective')} value={metrics.approved} /></Card>
        <Card><Statistic title={t('employeeChange.metric.attention')} value={metrics.attention} /></Card>
      </div>

      <Card className="employee-change-table-card" variant="borderless">
        <div className="employee-change-toolbar">
          <Input.Search allowClear placeholder={t('employeeChange.search')} onSearch={(value) => { setKeyword(value); setPage(1); }} />
          <Select allowClear placeholder={t('employeeChange.allTypes')} options={CHANGE_TYPES.map((value) => ({ value, label: t(`employeeChange.type.${value}`) }))} onChange={(value) => { setTypeFilter(value); setPage(1); }} />
          <Select allowClear placeholder={t('employeeChange.allStatus')} options={(Object.keys(STATUS_COLORS) as EmployeeChangeStatus[]).map((value) => ({ value, label: t(`employeeChange.status.${value}`) }))} onChange={(value) => { setStatus(value); setPage(1); }} />
        </div>
        <Table rowKey="id" loading={loading} columns={columns} dataSource={records} scroll={{ x: 1120 }} locale={{ emptyText: <Empty description={t('employeeChange.empty')} /> }} pagination={{ current: page, pageSize: 20, total, showSizeChanger: false, onChange: setPage }} />
      </Card>

      <Modal open={createOpen} title={t('employeeChange.createTitle')} okText={t('employeeChange.submit')} cancelText={t('common.cancel')} confirmLoading={submitting} onOk={() => void submitChange()} onCancel={() => setCreateOpen(false)} width={680} destroyOnHidden>
        <Form form={form} layout="vertical" initialValues={{ effectiveDate: dayjs().add(1, 'day') }}>
          <div className="employee-change-form-grid">
            <Form.Item name="employeeUserId" label={t('employeeChange.employee')} rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={employeeOptions} placeholder={t('employeeChange.employeePlaceholder')} /></Form.Item>
            <Form.Item name="changeType" label={t('employeeChange.changeType')} rules={[{ required: true }]}><Select options={CHANGE_TYPES.map((value) => ({ value, label: t(`employeeChange.type.${value}`) }))} placeholder={t('employeeChange.typePlaceholder')} /></Form.Item>
            <Form.Item name="effectiveDate" label={t('employeeChange.effectiveDate')} rules={[{ required: true }]}><DatePicker className="employee-change-date-picker" disabledDate={(date) => date.isBefore(dayjs(), 'day')} /></Form.Item>
            <Form.Item name="reviewApproverUserId" label={t('employeeChange.reviewApprover')} rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={reviewerOptions} placeholder={t('employeeChange.reviewApproverPlaceholder')} /></Form.Item>
          </div>
          {needsTarget && <><Alert className="employee-change-target-alert" showIcon type="info" message={t('employeeChange.targetRequiredHint')} /><div className="employee-change-form-grid">
            <Form.Item name="targetDepartmentId" label={t('employeeChange.targetDepartment')} rules={[{ required: true }]}><Select options={(overview?.departments || []).filter((item) => item.status === 1).map((item) => ({ value: item.id, label: item.name }))} /></Form.Item>
            <Form.Item name="targetPositionId" label={t('employeeChange.targetPosition')} rules={[{ required: true }]}><Select options={(overview?.positions || []).filter((item) => item.status === 1).map((item) => ({ value: item.id, label: item.name }))} /></Form.Item>
            <Form.Item name="targetSupervisorUserId" label={t('employeeChange.targetSupervisor')}><Select allowClear showSearch optionFilterProp="label" options={employeeOptions} placeholder={t('employeeChange.optionalSupervisor')} /></Form.Item>
          </div></>}
          <Form.Item name="reason" label={t('employeeChange.reason')} rules={[{ required: true }, { max: 1000 }]}><Input.TextArea rows={4} placeholder={t('employeeChange.reasonPlaceholder')} /></Form.Item>
        </Form>
      </Modal>

      <Drawer open={Boolean(detail)} title={t('employeeChange.detailTitle')} width={620} onClose={() => setDetail(undefined)} extra={detail && <Tag color={STATUS_COLORS[detail.status]}>{t(`employeeChange.status.${detail.status}`)}</Tag>}>
        {detail && <Space direction="vertical" size="large" className="employee-change-detail-stack">
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label={t('employeeChange.employee')}>{detail.employeeName} · {detail.employeeEmail}</Descriptions.Item>
            <Descriptions.Item label={t('employeeChange.changeType')}>{t(`employeeChange.type.${detail.changeType}`)}</Descriptions.Item>
            <Descriptions.Item label={t('employeeChange.effectiveDate')}>{detail.effectiveDate}</Descriptions.Item>
            <Descriptions.Item label={t('employeeChange.applicant')}>{detail.applicantName}</Descriptions.Item>
            <Descriptions.Item label={t('employeeChange.reviewApprover')}>{detail.reviewApproverName}</Descriptions.Item>
            <Descriptions.Item label={t('employeeChange.currentOrganizationLabel')}>{detail.currentDepartmentName || '-'} / {detail.currentPositionName || '-'}</Descriptions.Item>
            <Descriptions.Item label={t('employeeChange.targetOrganizationLabel')}>{detail.targetDepartmentName || '-'} / {detail.targetPositionName || '-'}</Descriptions.Item>
            <Descriptions.Item label={t('employeeChange.currentSupervisor')}>{detail.currentSupervisorName || '-'}</Descriptions.Item>
            <Descriptions.Item label={t('employeeChange.targetSupervisorLabel')}>{detail.targetSupervisorName || '-'}</Descriptions.Item>
            <Descriptions.Item label={t('employeeChange.reasonLabel')}>{detail.reason}</Descriptions.Item>
            <Descriptions.Item label={t('employeeChange.decisionComment')}>{detail.decisionComment || '-'}</Descriptions.Item>
            <Descriptions.Item label={t('employeeChange.submittedAt')}>{new Date(detail.submittedAt).toLocaleString()}</Descriptions.Item>
          </Descriptions>
          {(detail.canApprove || detail.canWithdraw) && <Space>
            {detail.canApprove && <Button type="primary" loading={acting} onClick={() => approve(detail)}>{t('employeeChange.approve')}</Button>}
            {detail.canApprove && <Button danger loading={acting} onClick={() => reject(detail)}>{t('employeeChange.reject')}</Button>}
            {detail.canWithdraw && <Button danger onClick={() => withdraw(detail)}>{t('employeeChange.withdraw')}</Button>}
          </Space>}
        </Space>}
      </Drawer>
    </section>
  );
}

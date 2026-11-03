'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
import ResponsiveTable from './ResponsiveTable';
import { usePermission } from '@/hooks/usePermission';
import { BusinessUserSelect } from './BusinessUserSelect';
import { usePublishPageAgentContext } from './PageAgentContext';
import { useSearchParams } from '@/lib/nextCompat';
import { parseRecordId } from '@/lib/recordSelection';
import { useAsyncActionGuard } from '@/hooks/useAsyncActionGuard';
import { isFormValidationError, useAsyncFormAction } from '@/hooks/useAsyncFormAction';
import { useScopedModalConfirm } from '@/hooks/useScopedModalConfirm';

interface ChangeFormValues extends Omit<EmployeeChangePayload, 'effectiveDate'> {
  effectiveDate: Dayjs;
}

const CHANGE_TYPES: EmployeeChangeType[] = ['ONBOARDING', 'REGULARIZATION', 'TRANSFER', 'OFFBOARDING'];
const STATUS_COLORS: Record<EmployeeChangeStatus, string> = {
  PENDING: 'processing', APPROVED: 'warning', EFFECTIVE: 'success', REJECTED: 'error', WITHDRAWN: 'default',
};
const EMPTY_CHANGES: EmployeeChange[] = [];

export default function EmployeeChangePage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const requestedId = useSearchParams().get('id');
  const [form] = Form.useForm<ChangeFormValues>();
  const changeType = Form.useWatch('changeType', form);
  const [loadedOverview, setOverview] = useState<OrganizationOverview>();
  const [loadedRecords, setRecords] = useState<EmployeeChange[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [loadedDetail, setDetail] = useState<EmployeeChange>();
  const [filterKeyword, setKeyword] = useState('');
  const [filterStatus, setStatus] = useState<EmployeeChangeStatus>();
  const [filterType, setTypeFilter] = useState<EmployeeChangeType>();
  const [filterPage, setPage] = useState(1);
  const [loadedTotal, setTotal] = useState(0);

  const { hasPermission, permissionVersion } = usePermission('route:employee-change');
  const canManage = hasPermission('route:employee-change') && hasPermission('hr:manage');
  const canRead = hasPermission('route:employee-change') && hasPermission('hr:read');
  const scope = `${user?.tenantId}:${user?.id}:${permissionVersion}:${canRead}:${canManage}`;
  const [dataScope, setDataScope] = useState(scope);
  const [detailScope, setDetailScope] = useState(scope);
  const [filterScope, setFilterScope] = useState(scope);
  const keyword = filterScope === scope ? filterKeyword : '';
  const status = filterScope === scope ? filterStatus : undefined;
  const typeFilter = filterScope === scope ? filterType : undefined;
  const page = filterScope === scope ? filterPage : 1;
  const overview = dataScope === scope ? loadedOverview : undefined;
  const records = dataScope === scope ? loadedRecords : EMPTY_CHANGES;
  const total = dataScope === scope ? loadedTotal : 0;
  const detail = detailScope === scope && canRead ? loadedDetail : undefined;
  const { capture: captureScope } = useAsyncActionGuard(scope);
  const createAction = useAsyncFormAction(scope);
  const decisionAction = useAsyncFormAction(scope);
  const resetDecision = decisionAction.reset;
  const submitting = createAction.busy, acting = decisionAction.busy;
  const confirm = useScopedModalConfirm(scope);
  const listRequest = useRef(0);
  const detailRequest = useRef(0);
  const openCreate = () => { if (!canManage || !captureScope()()) return; createAction.reset(); form.resetFields(); setCreateOpen(true); };
  const needsTarget = changeType === 'ONBOARDING' || changeType === 'TRANSFER';

  const load = useCallback(async () => {
    const current = captureScope();
    if (!current()) return;
    const ticket = ++listRequest.current;
    if (!canRead) { setOverview(undefined); setRecords([]); setTotal(0); setLoading(false); return; }
    setLoading(true);
    try {
      const [organization, changes] = await Promise.all([
        hrApi.overview(),
        hrApi.listEmployeeChanges({ keyword, status, changeType: typeFilter, page, size: 20 }),
      ]);
      if (ticket !== listRequest.current || !current()) return;
      setOverview(organization);
      setRecords(changes.records);
      setTotal(changes.total);
      setDataScope(scope);
    } catch (error) {
      if (ticket !== listRequest.current || !current()) return;
      setOverview(undefined); setRecords([]); setTotal(0);
      message.error(error instanceof Error ? error.message : t('errors.hr.employeeLoadFailed'));
    } finally {
      if (ticket === listRequest.current && current()) setLoading(false);
    }
  }, [keyword, page, status, t, typeFilter, canRead, captureScope, scope]);
  const latestLoad = useRef(load); latestLoad.current = load;

  useEffect(() => {
    detailRequest.current += 1;
    setOverview(undefined); setRecords([]); setTotal(0); setDetail(undefined); setCreateOpen(false); form.resetFields();
    setKeyword(''); setStatus(undefined); setTypeFilter(undefined); setPage(1); setFilterScope(scope);
    return () => { detailRequest.current += 1; };
  }, [scope, form]);
  useEffect(() => { void load(); return () => { listRequest.current += 1; }; }, [load]);

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

  const openDetail = useCallback(async (record: Pick<EmployeeChange, 'id'>, reportError = true) => {
    const current = captureScope();
    if (!current() || !canRead) {
      if (!reportError) throw new Error('PAGE_UI_FIELD_REJECTED');
      return;
    }
    resetDecision();
    const ticket = ++detailRequest.current;
    setDetail(undefined);
    try {
      const result = await hrApi.employeeChangeDetail(record.id);
      if (ticket !== detailRequest.current || !current()) return;
      if (result.id !== record.id) throw new Error(t('errors.hr.employeeLoadFailed'));
      setDetailScope(scope);
      setDetail(result);
    } catch (error) {
      if (ticket !== detailRequest.current || !current()) return;
      if (!reportError) throw error;
      message.error(error instanceof Error ? error.message : t('errors.hr.employeeLoadFailed'));
    }
  }, [canRead, captureScope, resetDecision, scope, t]);
  useEffect(() => {
    const id = parseRecordId(requestedId);
    if (id && canRead) void openDetail({ id });
  }, [requestedId, canRead, openDetail]);

  usePublishPageAgentContext({ keyword: keyword || undefined, status, changeType: typeFilter, page, size: 20,
    changeId: detail?.id, version: detail?.version });
  const submitChange = async () => {
    if (!canManage || submitting) return;
    const action = createAction.begin();
    if (!action) return;
    const current = captureScope();
    try {
      const values = await form.validateFields();
      if (!action.current()) return;
      await hrApi.createEmployeeChange({
        ...values,
        effectiveDate: values.effectiveDate.format('YYYY-MM-DD'),
        targetDepartmentId: needsTarget ? values.targetDepartmentId : undefined,
        targetPositionId: needsTarget ? values.targetPositionId : undefined,
        targetSupervisorUserId: needsTarget ? values.targetSupervisorUserId : undefined,
      });
      if (action.current()) {
        message.success(t('employeeChange.createSuccess'));
        setCreateOpen(false); form.resetFields();
      }
      if (current()) await latestLoad.current();
    } catch (error) {
      if (!action.current() || isFormValidationError(error)) return;
      message.error(error instanceof Error ? error.message : t('errors.hr.employeeLoadFailed'));
    } finally {
      action.finish();
    }
  };

  const approve = (record: EmployeeChange) => {
    const current = captureScope(), selection = detailRequest.current;
    if (!current() || !canManage || !record.canApprove) return;
    confirm({
    title: t('employeeChange.approveTitle'),
    content: t('employeeChange.approveContent'),
    okText: t('employeeChange.approve'),
    cancelText: t('common.cancel'),
    onOk: async () => {
      if (!current() || selection !== detailRequest.current) return;
      const action = decisionAction.begin();
      if (!action) return;
      try {
        await hrApi.approveEmployeeChange(record.id, record.version);
        if (action.current() && selection === detailRequest.current) {
          message.success(t('employeeChange.approveSuccess')); setDetail(undefined);
        }
        if (current()) await latestLoad.current();
      } catch (error) {
        if (action.current()) message.error(error instanceof Error ? error.message : t('errors.hr.employeeLoadFailed'));
      } finally { action.finish(); }
    },
  }); };

  const reject = (record: EmployeeChange) => {
    const current = captureScope(), selection = detailRequest.current;
    if (!current() || !canManage || !record.canApprove) return;
    let comment = '';
    confirm({
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
        if (!current() || selection !== detailRequest.current) return;
        if (!comment.trim()) throw new Error(t('employeeChange.rejectRequired'));
        const action = decisionAction.begin();
        if (!action) return;
        try {
          await hrApi.rejectEmployeeChange(record.id, record.version, comment.trim());
          if (action.current() && selection === detailRequest.current) {
            message.success(t('employeeChange.rejectSuccess')); setDetail(undefined);
          }
          if (current()) await latestLoad.current();
        } catch (error) {
          if (!action.current()) return;
          message.error(error instanceof Error ? error.message : t('errors.hr.employeeLoadFailed'));
          throw error;
        } finally { action.finish(); }
      },
    });
  };

  const withdraw = (record: EmployeeChange) => {
    const current = captureScope(), selection = detailRequest.current;
    if (!current() || !canManage || !record.canWithdraw) return;
    confirm({
    title: t('employeeChange.withdrawTitle'),
    okText: t('employeeChange.withdraw'),
    okButtonProps: { danger: true },
    cancelText: t('common.cancel'),
    onOk: async () => {
      if (!current() || selection !== detailRequest.current) return;
      const action = decisionAction.begin();
      if (!action) return;
      try {
        await hrApi.withdrawEmployeeChange(record.id, record.version);
        if (action.current() && selection === detailRequest.current) {
          message.success(t('employeeChange.withdrawSuccess')); setDetail(undefined);
        }
        if (current()) await latestLoad.current();
      } catch (error) {
        if (action.current()) message.error(error instanceof Error ? error.message : t('errors.hr.employeeLoadFailed'));
      } finally { action.finish(); }
    },
  }); };

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
          {canManage && record.canApprove && <Button type="link" size="small" onClick={() => approve(record)}>{t('employeeChange.approve')}</Button>}
          {canManage && record.canApprove && <Button danger type="link" size="small" onClick={() => reject(record)}>{t('employeeChange.reject')}</Button>}
          {canManage && record.canWithdraw && <Button danger type="link" size="small" onClick={() => withdraw(record)}>{t('employeeChange.withdraw')}</Button>}
        </Space>
      ),
    },
  ];

  return (
    <section className="employee-change-page">
      <header className="employee-change-hero">
        <div><span className="employee-change-hero__kicker">{t('employeeChange.kicker')}</span><Typography.Title level={2}>{t('employeeChange.title')}</Typography.Title><Typography.Paragraph>{t('employeeChange.description')}</Typography.Paragraph></div>
        {canManage && <Button type="primary" size="large" icon={<OaIcon name="employee-change" />} onClick={openCreate}>{t('employeeChange.create')}</Button>}
      </header>

      <div className="employee-change-metrics">
        <Card><Statistic title={t('employeeChange.metric.total')} value={metrics.total} /></Card>
        <Card><Statistic title={t('employeeChange.metric.pending')} value={metrics.pending} /></Card>
        <Card><Statistic title={t('employeeChange.metric.effective')} value={metrics.approved} /></Card>
        <Card><Statistic title={t('employeeChange.metric.attention')} value={metrics.attention} /></Card>
      </div>

      <Card className="employee-change-table-card" variant="borderless">
        <div className="employee-change-toolbar">
          <Input.Search key={scope} allowClear placeholder={t('employeeChange.search')} onSearch={(value) => { setKeyword(value); setPage(1); }} />
          <Select allowClear value={typeFilter} placeholder={t('employeeChange.allTypes')} options={CHANGE_TYPES.map((value) => ({ value, label: t(`employeeChange.type.${value}`) }))} onChange={(value) => { setTypeFilter(value); setPage(1); }} />
          <Select allowClear value={status} placeholder={t('employeeChange.allStatus')} options={(Object.keys(STATUS_COLORS) as EmployeeChangeStatus[]).map((value) => ({ value, label: t(`employeeChange.status.${value}`) }))} onChange={(value) => { setStatus(value); setPage(1); }} />
        </div>
        <ResponsiveTable rowKey="id" loading={loading} columns={columns} dataSource={records} scroll={{ x: 1120 }} locale={{ emptyText: <Empty description={t('employeeChange.empty')} /> }} pagination={{ current: page, pageSize: 20, total, showSizeChanger: false, onChange: setPage }} />
      </Card>

      <Modal open={createOpen && canManage} title={t('employeeChange.createTitle')} okText={t('employeeChange.submit')} cancelText={t(submitting ? 'common.close' : 'common.cancel')} okButtonProps={{ loading: submitting }} onOk={() => void submitChange()} onCancel={() => { createAction.reset(); setCreateOpen(false); }} width={680} destroyOnHidden>
        <Form form={form} disabled={submitting} layout="vertical" initialValues={{ effectiveDate: dayjs().add(1, 'day') }}>
          <div className="employee-change-form-grid">
            <Form.Item name="employeeUserId" label={t('employeeChange.employee')} rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={employeeOptions} placeholder={t('employeeChange.employeePlaceholder')} /></Form.Item>
            <Form.Item name="changeType" label={t('employeeChange.changeType')} rules={[{ required: true }]}><Select options={CHANGE_TYPES.map((value) => ({ value, label: t(`employeeChange.type.${value}`) }))} placeholder={t('employeeChange.typePlaceholder')} /></Form.Item>
            <Form.Item name="effectiveDate" label={t('employeeChange.effectiveDate')} rules={[{ required: true }]}><DatePicker className="employee-change-date-picker" disabledDate={(date) => date.isBefore(dayjs(), 'day')} /></Form.Item>
            <Form.Item name="reviewApproverUserId" label={t('employeeChange.reviewApprover')} rules={[{ required: true }]}><BusinessUserSelect query={hrApi.employeeChangeReviewerOptions} active={createOpen && canManage} disabled={submitting} scopeKey={scope} placeholder={t('employeeChange.reviewApproverPlaceholder')} /></Form.Item>
          </div>
          {needsTarget && <><Alert className="employee-change-target-alert" showIcon type="info" message={t('employeeChange.targetRequiredHint')} /><div className="employee-change-form-grid">
            <Form.Item name="targetDepartmentId" label={t('employeeChange.targetDepartment')} rules={[{ required: true }]}><Select options={(overview?.departments || []).filter((item) => item.status === 1).map((item) => ({ value: item.id, label: item.name }))} /></Form.Item>
            <Form.Item name="targetPositionId" label={t('employeeChange.targetPosition')} rules={[{ required: true }]}><Select options={(overview?.positions || []).filter((item) => item.status === 1).map((item) => ({ value: item.id, label: item.name }))} /></Form.Item>
            <Form.Item name="targetSupervisorUserId" label={t('employeeChange.targetSupervisor')}><Select allowClear showSearch optionFilterProp="label" options={employeeOptions} placeholder={t('employeeChange.optionalSupervisor')} /></Form.Item>
          </div></>}
          <Form.Item name="reason" label={t('employeeChange.reason')} rules={[{ required: true }, { max: 1000 }]}><Input.TextArea rows={4} placeholder={t('employeeChange.reasonPlaceholder')} /></Form.Item>
        </Form>
      </Modal>

      <Drawer open={Boolean(detail)} title={t('employeeChange.detailTitle')} width={620} onClose={() => { resetDecision(); detailRequest.current += 1; setDetail(undefined); }} extra={detail && <Tag color={STATUS_COLORS[detail.status]}>{t(`employeeChange.status.${detail.status}`)}</Tag>}>
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
          {canManage && (detail.canApprove || detail.canWithdraw) && <Space>
            {detail.canApprove && <Button type="primary" loading={acting} onClick={() => approve(detail)}>{t('employeeChange.approve')}</Button>}
            {detail.canApprove && <Button danger loading={acting} onClick={() => reject(detail)}>{t('employeeChange.reject')}</Button>}
            {detail.canWithdraw && <Button danger onClick={() => withdraw(detail)}>{t('employeeChange.withdraw')}</Button>}
          </Space>}
        </Space>}
      </Drawer>
    </section>
  );
}

'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import dayjs, { type Dayjs } from 'dayjs';
import {
  App, Button, Card, DatePicker, Descriptions, Drawer, Empty, Form, Input, InputNumber,
  Modal, Progress, Select, Space, Statistic, Tag, Timeline, Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { OaIcon } from '@/components/OaIcon';
import ResponsiveTable from '@/components/oa/ResponsiveTable';
import { message } from '@/lib/antdMessage';
import { formatOaApiError } from '@/lib/oaApi';
import {
  contractApi, type BusinessContract, type ContractCurrency, type ContractDetail,
  type ContractExpiryState, type ContractOption, type ContractPayload, type ContractStatus,
  type ContractType, type FulfillmentStatus,
} from '@/lib/contractApi';

type ContractFormValues = Omit<ContractPayload, 'signedDate' | 'startDate' | 'endDate'> & {
  signedDate?: Dayjs; startDate: Dayjs; endDate: Dayjs;
};
type PaymentValues = { amount: number; paymentDate: Dayjs; reference: string; note?: string };

const STATUSES: ContractStatus[] = ['DRAFT', 'ACTIVE', 'COMPLETED', 'TERMINATED'];
const TYPES: ContractType[] = ['PURCHASE', 'SALES', 'SERVICE', 'LEASE', 'OTHER'];
const CURRENCIES: ContractCurrency[] = ['CNY', 'USD', 'EUR', 'HKD'];
const STATUS_COLORS: Record<ContractStatus, string> = {
  DRAFT: 'default', ACTIVE: 'blue', COMPLETED: 'green', TERMINATED: 'red',
};
const FULFILLMENT_COLORS: Record<FulfillmentStatus, string> = {
  NOT_STARTED: 'default', IN_PROGRESS: 'processing', FULFILLED: 'success', BREACHED: 'error',
};
const EXPIRY_COLORS: Record<ContractExpiryState, string> = {
  NONE: 'default', NORMAL: 'green', EXPIRING: 'gold', EXPIRED: 'red',
};

export default function ContractPage() {
  const { t, i18n } = useTranslation();
  const { modal } = App.useApp();
  const [form] = Form.useForm<ContractFormValues>();
  const [paymentForm] = Form.useForm<PaymentValues>();
  const [records, setRecords] = useState<BusinessContract[]>([]);
  const [stats, setStats] = useState({ total: 0, active: 0, expiring: 0, expired: 0, outstandingAmount: 0 });
  const [options, setOptions] = useState<{ owners: ContractOption[]; suppliers: ContractOption[] }>({ owners: [], suppliers: [] });
  const [total, setTotal] = useState(0);
  const [canManage, setCanManage] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<ContractStatus>();
  const [contractType, setContractType] = useState<ContractType>();
  const [expiryState, setExpiryState] = useState<'EXPIRING' | 'EXPIRED'>();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<BusinessContract>();
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<ContractDetail>();
  const [paymentOpen, setPaymentOpen] = useState(false);
  const [paymentContract, setPaymentContract] = useState<BusinessContract>();

  const statusOptions = useMemo(() => STATUSES.map((value) => ({ value, label: t(`contract.status.${value}`) })), [t]);
  const typeOptions = useMemo(() => TYPES.map((value) => ({ value, label: t(`contract.type.${value}`) })), [t]);
  const ownerOptions = options.owners.map((item) => ({ value: item.id, label: item.label }));
  const supplierOptions = options.suppliers.map((item) => ({ value: item.id, label: item.label }));

  const money = useCallback((value: number, currency: string) => new Intl.NumberFormat(i18n.language, {
    style: 'currency', currency, maximumFractionDigits: 2,
  }).format(Number(value)), [i18n.language]);
  const date = useCallback((value?: string) => value
    ? new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium' }).format(new Date(`${value}T00:00:00`))
    : '—', [i18n.language]);
  const dateTime = useCallback((value: string) => new Intl.DateTimeFormat(i18n.language, {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(new Date(value)), [i18n.language]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await contractApi.list({ keyword, status, contractType, expiryState, page, size });
      setRecords(result.records); setTotal(result.total); setStats(result.stats); setCanManage(result.canManage);
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setLoading(false);
    }
  }, [contractType, expiryState, keyword, page, size, status]);

  const loadOptions = useCallback(async () => {
    try { setOptions(await contractApi.options()); } catch (error) { message.error(formatOaApiError(error)); }
  }, []);

  const loadDetail = useCallback(async (id: number) => {
    setDetailLoading(true);
    try {
      const result = await contractApi.detail(id); setDetail(result); return result;
    } catch (error) {
      message.error(formatOaApiError(error)); return undefined;
    } finally { setDetailLoading(false); }
  }, []);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => { void loadOptions(); }, [loadOptions]);

  const openDetail = (record: BusinessContract) => {
    setDetail(undefined); setDetailOpen(true); void loadDetail(record.id);
  };
  const openCreate = () => {
    setEditing(undefined); form.resetFields();
    form.setFieldsValue({ contractType: 'PURCHASE', currency: 'CNY' });
    setEditorOpen(true);
  };
  const openEdit = (record: BusinessContract) => {
    setDetailOpen(false); setEditing(record);
    form.setFieldsValue({
      code: record.code, name: record.name, contractType: record.contractType,
      counterpartyName: record.counterpartyName, supplierId: record.supplierId,
      ownerUserId: record.ownerUserId, amount: Number(record.amount), currency: record.currency,
      signedDate: record.signedDate ? dayjs(record.signedDate) : undefined,
      startDate: dayjs(record.startDate), endDate: dayjs(record.endDate), summary: record.summary,
    });
    setEditorOpen(true);
  };
  const save = async () => {
    const values = await form.validateFields(); setSaving(true);
    const payload: ContractPayload = {
      ...values, signedDate: values.signedDate?.format('YYYY-MM-DD'),
      startDate: values.startDate.format('YYYY-MM-DD'), endDate: values.endDate.format('YYYY-MM-DD'),
      version: editing?.version,
    };
    try {
      if (editing) await contractApi.update(editing.id, payload); else await contractApi.create(payload);
      setEditorOpen(false); message.success(t('contract.saveSuccess')); await load();
    } catch (error) { message.error(formatOaApiError(error)); } finally { setSaving(false); }
  };

  const confirmChange = (record: BusinessContract, target: ContractStatus | FulfillmentStatus, kind: 'status' | 'fulfillment') => {
    const needsReason = target === 'TERMINATED' || target === 'BREACHED';
    let reason = '';
    modal.confirm({
      title: t(kind === 'status' ? 'contract.dialog.statusTitle' : 'contract.dialog.fulfillmentTitle'),
      content: <div className="oa-contract-action-dialog">
        <Typography.Paragraph>{t('contract.dialog.content', {
          name: record.name, status: t(`contract.${kind === 'status' ? 'status' : 'fulfillment'}.${target}`),
        })}</Typography.Paragraph>
        {needsReason && <Input.TextArea rows={3} maxLength={500} showCount
          aria-label={t('contract.dialog.reason')} placeholder={t('contract.dialog.reasonPlaceholder')}
          onChange={(event) => { reason = event.target.value; }} />}
      </div>,
      okText: t(`contract.actions.${target}`), cancelText: t('common.cancel'),
      okButtonProps: { danger: target === 'TERMINATED' || target === 'BREACHED' },
      onOk: async () => {
        if (needsReason && !reason.trim()) { message.warning(t('contract.dialog.reasonRequired')); return Promise.reject(); }
        try {
          if (kind === 'status') await contractApi.updateStatus(record.id, target as ContractStatus, record.version, reason.trim() || undefined);
          else await contractApi.updateFulfillment(record.id, target as FulfillmentStatus, record.version, reason.trim() || undefined);
          message.success(t(kind === 'status' ? 'contract.statusSuccess' : 'contract.fulfillmentSuccess'));
          await load(); if (detailOpen) await loadDetail(record.id);
        } catch (error) { message.error(formatOaApiError(error)); return Promise.reject(error); }
      },
    });
  };

  const openPayment = (record: BusinessContract) => {
    setPaymentContract(record); paymentForm.resetFields(); paymentForm.setFieldsValue({ paymentDate: dayjs() }); setPaymentOpen(true);
  };
  const savePayment = async () => {
    if (!paymentContract) return;
    const values = await paymentForm.validateFields(); setSaving(true);
    try {
      await contractApi.recordPayment(paymentContract.id, values.amount, values.paymentDate.format('YYYY-MM-DD'),
        values.reference, paymentContract.version, values.note);
      setPaymentOpen(false); message.success(t('contract.paymentSuccess')); await load();
      if (detailOpen) await loadDetail(paymentContract.id);
    } catch (error) { message.error(formatOaApiError(error)); } finally { setSaving(false); }
  };
  const remind = (record: BusinessContract) => modal.confirm({
    title: t('contract.reminder.title'),
    content: t('contract.reminder.content', { owner: record.ownerLabel }),
    okText: t('contract.actions.remind'), cancelText: t('common.cancel'),
    onOk: async () => {
      try {
        await contractApi.remind(record.id, record.version); message.success(t('contract.reminderSuccess'));
        await load(); if (detailOpen) await loadDetail(record.id);
      } catch (error) { message.error(formatOaApiError(error)); return Promise.reject(error); }
    },
  });

  const expiryLabel = (record: BusinessContract) => record.expiryState === 'EXPIRED'
    ? t('contract.expiry.overdue', { count: Math.abs(record.daysUntilExpiry) })
    : record.daysUntilExpiry === 0 ? t('contract.expiry.today')
      : record.expiryState === 'EXPIRING' ? t('contract.expiry.daysLeft', { count: record.daysUntilExpiry })
        : t(`contract.expiry.${record.expiryState}`);

  const columns: ColumnsType<BusinessContract> = [
    { title: t('contract.columns.contract'), key: 'contract', width: 250, render: (_, record) =>
      <div className="oa-contract-name-cell"><span><OaIcon name="contracts" size={18} /></span><div><strong>{record.name}</strong><small>{record.code}</small></div></div> },
    { title: t('contract.columns.type'), dataIndex: 'contractType', width: 110, render: (value: ContractType) => t(`contract.type.${value}`) },
    { title: t('contract.columns.counterparty'), dataIndex: 'counterpartyName', width: 180, ellipsis: true },
    { title: t('contract.columns.owner'), dataIndex: 'ownerLabel', width: 135, ellipsis: true },
    { title: t('contract.columns.amount'), key: 'amount', width: 190, render: (_, record) => {
      const percent = Math.min(100, Math.round(Number(record.paidAmount) / Number(record.amount) * 100));
      return <div className="oa-contract-payment-cell"><strong>{money(record.amount, record.currency)}</strong><Progress percent={percent} size="small" showInfo={false} /><small>{money(record.paidAmount, record.currency)}</small></div>;
    } },
    { title: t('contract.columns.endDate'), key: 'endDate', width: 145, render: (_, record) =>
      <div className="oa-contract-expiry-cell"><span>{date(record.endDate)}</span><Tag color={EXPIRY_COLORS[record.expiryState]}>{expiryLabel(record)}</Tag></div> },
    { title: t('contract.columns.status'), dataIndex: 'status', width: 105, render: (value: ContractStatus) => <Tag color={STATUS_COLORS[value]}>{t(`contract.status.${value}`)}</Tag> },
    { title: t('contract.columns.fulfillment'), dataIndex: 'fulfillmentStatus', width: 115, render: (value: FulfillmentStatus) => <Tag color={FULFILLMENT_COLORS[value]}>{t(`contract.fulfillment.${value}`)}</Tag> },
    { title: t('contract.columns.actions'), key: 'actions', width: canManage ? 132 : 70, fixed: 'right', render: (_, record) => <Space size={2} onClick={(event) => event.stopPropagation()}>
      <Button type="link" size="small" onClick={() => openDetail(record)}>{t('contract.actions.view')}</Button>
      {canManage && (record.status === 'DRAFT' || record.status === 'ACTIVE') && <Button type="link" size="small" onClick={() => openEdit(record)}>{t('contract.actions.edit')}</Button>}
    </Space> },
  ];

  return <section className="oa-contract-page">
    <header className="oa-contract-hero">
      <div className="oa-contract-heading"><span className="oa-contract-heading-icon"><OaIcon name="contracts" size={24} /></span><div>
        <Typography.Text className="oa-contract-eyebrow">{t('contract.eyebrow')}</Typography.Text>
        <Typography.Title level={3}>{t('contract.title')}</Typography.Title>
        <Typography.Paragraph>{t('contract.description')}</Typography.Paragraph>
      </div></div>
      <Space><Button icon={<OaIcon name="reload" />} onClick={() => void load()}>{t('contract.refresh')}</Button>
        {canManage && <Button type="primary" icon={<OaIcon name="add" />} onClick={openCreate}>{t('contract.add')}</Button>}</Space>
    </header>

    <div className="oa-contract-stats">
      <Card><Statistic title={t('contract.total')} value={stats.total} /></Card>
      <Card><Statistic title={t('contract.active')} value={stats.active} /></Card>
      <Card><Statistic title={t('contract.expiring')} value={stats.expiring} /></Card>
      <Card><Statistic title={t('contract.expired')} value={stats.expired} /></Card>
      <Card><Statistic title={t('contract.outstanding')} value={money(stats.outstandingAmount, 'CNY')} /></Card>
    </div>

    <Card className="oa-contract-list-card">
      <div className="oa-contract-toolbar">
        <Input.Search allowClear value={keywordInput} placeholder={t('contract.searchPlaceholder')}
          onChange={(event) => { setKeywordInput(event.target.value); if (!event.target.value) { setKeyword(''); setPage(1); } }}
          onSearch={(value) => { setKeyword(value.trim()); setPage(1); }} />
        <Select allowClear value={status} placeholder={t('contract.allStatuses')} options={statusOptions} onChange={(value) => { setStatus(value); setPage(1); }} />
        <Select allowClear value={contractType} placeholder={t('contract.allTypes')} options={typeOptions} onChange={(value) => { setContractType(value); setPage(1); }} />
        <Select allowClear value={expiryState} placeholder={t('contract.allExpiry')} options={[
          { value: 'EXPIRING', label: t('contract.expiry.EXPIRING') }, { value: 'EXPIRED', label: t('contract.expiry.EXPIRED') },
        ]} onChange={(value) => { setExpiryState(value); setPage(1); }} />
      </div>
      <ResponsiveTable rowKey="id" loading={loading} columns={columns} dataSource={records} scroll={{ x: 1350 }}
        locale={{ emptyText: <Empty description={t('contract.empty')} /> }} onRow={(record) => ({ onClick: () => openDetail(record) })}
        pagination={{ current: page, pageSize: size, total, showSizeChanger: true,
          showTotal: (count) => t('common.total', { count }), onChange: (next, nextSize) => { setPage(next); setSize(nextSize); } }} />
    </Card>

    <Drawer width="min(760px, 100vw)" open={editorOpen} title={t(editing ? 'contract.form.editTitle' : 'contract.form.createTitle')}
      onClose={() => setEditorOpen(false)} extra={<Button type="primary" loading={saving} onClick={() => void save()}>{t('common.save')}</Button>}>
      <ContractForm form={form} editing={Boolean(editing)} typeOptions={typeOptions} ownerOptions={ownerOptions} supplierOptions={supplierOptions} />
    </Drawer>

    <Drawer width="min(820px, 100vw)" open={detailOpen} loading={detailLoading} title={t('contract.detail.title')}
      onClose={() => setDetailOpen(false)} extra={detail?.contract.canManage && (detail.contract.status === 'DRAFT' || detail.contract.status === 'ACTIVE')
        ? <Button icon={<OaIcon name="edit" />} onClick={() => openEdit(detail.contract)}>{t('contract.actions.edit')}</Button> : undefined}>
      {detail ? <ContractDetailView detail={detail} money={money} date={date} dateTime={dateTime}
        expiryLabel={expiryLabel} onChange={confirmChange} onPayment={openPayment} onRemind={remind} /> : <Empty description={t('contract.empty')} />}
    </Drawer>

    <Modal open={paymentOpen} title={t('contract.payment.title')} confirmLoading={saving} onCancel={() => setPaymentOpen(false)}
      okText={t('common.confirm')} cancelText={t('common.cancel')} onOk={() => void savePayment()}>
      {paymentContract && <Typography.Paragraph>{t('contract.payment.remaining', {
        amount: money(Number(paymentContract.amount) - Number(paymentContract.paidAmount), paymentContract.currency),
      })}</Typography.Paragraph>}
      <Form form={paymentForm} layout="vertical">
        <Form.Item name="amount" label={t('contract.payment.amount')} rules={[{ required: true, message: t('contract.form.required') }]}>
          <InputNumber min={0.01} precision={2} max={paymentContract ? Number(paymentContract.amount) - Number(paymentContract.paidAmount) : undefined} className="oa-contract-full-control" />
        </Form.Item>
        <Form.Item name="paymentDate" label={t('contract.payment.date')} rules={[{ required: true, message: t('contract.form.required') }]}><DatePicker className="oa-contract-full-control" /></Form.Item>
        <Form.Item name="reference" label={t('contract.payment.reference')} rules={[{ required: true, message: t('contract.form.required') }]}><Input maxLength={100} placeholder={t('contract.payment.referencePlaceholder')} /></Form.Item>
        <Form.Item name="note" label={t('contract.payment.note')}><Input.TextArea rows={3} maxLength={500} /></Form.Item>
      </Form>
    </Modal>
  </section>;
}

function ContractForm({ form, editing, typeOptions, ownerOptions, supplierOptions }: {
  form: ReturnType<typeof Form.useForm<ContractFormValues>>[0]; editing: boolean;
  typeOptions: Array<{ value: ContractType; label: string }>; ownerOptions: Array<{ value: number; label: string }>;
  supplierOptions: Array<{ value: number; label: string }>;
}) {
  const { t } = useTranslation();
  return <Form form={form} layout="vertical" requiredMark="optional" className="oa-contract-form">
    <Typography.Title level={5}>{t('contract.form.identity')}</Typography.Title><div className="oa-contract-form-grid">
      <Form.Item name="code" label={t('contract.form.code')} rules={[{ required: true, message: t('contract.form.required') }]}><Input disabled={editing} maxLength={64} placeholder={t('contract.form.codePlaceholder')} /></Form.Item>
      <Form.Item name="name" label={t('contract.form.name')} rules={[{ required: true, message: t('contract.form.required') }]}><Input maxLength={160} placeholder={t('contract.form.namePlaceholder')} /></Form.Item>
      <Form.Item name="contractType" label={t('contract.form.type')} rules={[{ required: true, message: t('contract.form.required') }]}><Select options={typeOptions} /></Form.Item>
      <Form.Item name="counterpartyName" label={t('contract.form.counterparty')} rules={[{ required: true, message: t('contract.form.required') }]}><Input maxLength={160} placeholder={t('contract.form.counterpartyPlaceholder')} /></Form.Item>
    </div>
    <Typography.Title level={5}>{t('contract.form.execution')}</Typography.Title><div className="oa-contract-form-grid">
      <Form.Item name="supplierId" label={t('contract.form.supplier')}><Select allowClear showSearch optionFilterProp="label" options={supplierOptions} placeholder={t('contract.form.supplierPlaceholder')} /></Form.Item>
      <Form.Item name="ownerUserId" label={t('contract.form.owner')} rules={[{ required: true, message: t('contract.form.required') }]}><Select showSearch optionFilterProp="label" options={ownerOptions} placeholder={t('contract.form.ownerPlaceholder')} /></Form.Item>
      <Form.Item name="amount" label={t('contract.form.amount')} rules={[{ required: true, message: t('contract.form.required') }]}><InputNumber min={0.01} precision={2} className="oa-contract-full-control" /></Form.Item>
      <Form.Item name="currency" label={t('contract.form.currency')} rules={[{ required: true, message: t('contract.form.required') }]}><Select options={CURRENCIES.map((value) => ({ value, label: value }))} /></Form.Item>
    </div>
    <Typography.Title level={5}>{t('contract.form.dates')}</Typography.Title><div className="oa-contract-form-grid oa-contract-form-grid--dates">
      <Form.Item name="signedDate" label={t('contract.form.signedDate')}><DatePicker className="oa-contract-full-control" /></Form.Item>
      <Form.Item name="startDate" label={t('contract.form.startDate')} rules={[{ required: true, message: t('contract.form.required') }]}><DatePicker className="oa-contract-full-control" /></Form.Item>
      <Form.Item name="endDate" label={t('contract.form.endDate')} dependencies={['startDate']} rules={[
        { required: true, message: t('contract.form.required') },
        ({ getFieldValue }) => ({ validator(_, value) { const start = getFieldValue('startDate'); return !value || !start || !value.isBefore(start) ? Promise.resolve() : Promise.reject(new Error(t('contract.form.dateRangeInvalid'))); } }),
      ]}><DatePicker className="oa-contract-full-control" /></Form.Item>
    </div>
    <Form.Item name="summary" label={t('contract.form.summary')}><Input.TextArea rows={5} maxLength={2000} showCount placeholder={t('contract.form.summaryPlaceholder')} /></Form.Item>
  </Form>;
}

function ContractDetailView({ detail, money, date, dateTime, expiryLabel, onChange, onPayment, onRemind }: {
  detail: ContractDetail; money: (value: number, currency: string) => string;
  date: (value?: string) => string; dateTime: (value: string) => string; expiryLabel: (record: BusinessContract) => string;
  onChange: (record: BusinessContract, target: ContractStatus | FulfillmentStatus, kind: 'status' | 'fulfillment') => void;
  onPayment: (record: BusinessContract) => void; onRemind: (record: BusinessContract) => void;
}) {
  const { t } = useTranslation(); const record = detail.contract;
  const eventValue = (eventType: string, value: string) => {
    if (eventType === 'STATUS_CHANGED') return t(`contract.status.${value}`);
    if (eventType === 'FULFILLMENT_CHANGED') return t(`contract.fulfillment.${value}`);
    return value;
  };
  const outstanding = Number(record.amount) - Number(record.paidAmount);
  return <div className="oa-contract-detail">
    <div className="oa-contract-detail-heading"><span><OaIcon name="contracts" size={26} /></span><div><Typography.Title level={4}>{record.name}</Typography.Title>
      <Space wrap><Typography.Text code>{record.code}</Typography.Text><Tag color={STATUS_COLORS[record.status]}>{t(`contract.status.${record.status}`)}</Tag>
        <Tag color={EXPIRY_COLORS[record.expiryState]}>{expiryLabel(record)}</Tag></Space></div></div>
    {record.canManage && <div className="oa-contract-detail-actions">
      {record.allowedTransitions.map((target) => <Button key={target} type={target === 'ACTIVE' || target === 'COMPLETED' ? 'primary' : 'default'} danger={target === 'TERMINATED'} onClick={() => onChange(record, target, 'status')}>{t(`contract.actions.${target}`)}</Button>)}
      {record.allowedFulfillmentStatuses.map((target) => <Button key={target} danger={target === 'BREACHED'} onClick={() => onChange(record, target, 'fulfillment')}>{t(`contract.actions.${target}`)}</Button>)}
      {record.canRecordPayment && <Button onClick={() => onPayment(record)}>{t('contract.actions.payment')}</Button>}
      {record.canRemind && <Button icon={<OaIcon name="notification" />} onClick={() => onRemind(record)}>{t('contract.actions.remind')}</Button>}
    </div>}
    <Card size="small" title={t('contract.detail.overview')}><Descriptions column={{ xs: 1, sm: 2 }} size="small">
      <Descriptions.Item label={t('contract.form.type')}>{t(`contract.type.${record.contractType}`)}</Descriptions.Item>
      <Descriptions.Item label={t('contract.detail.counterparty')}>{record.counterpartyName}</Descriptions.Item>
      <Descriptions.Item label={t('contract.detail.supplier')}>{record.supplierLabel || '—'}</Descriptions.Item>
      <Descriptions.Item label={t('contract.detail.owner')}>{record.ownerLabel}</Descriptions.Item>
      <Descriptions.Item label={t('contract.detail.signedDate')}>{date(record.signedDate)}</Descriptions.Item>
      <Descriptions.Item label={t('contract.detail.dateRange')}>{date(record.startDate)} — {date(record.endDate)}</Descriptions.Item>
      <Descriptions.Item label={t('contract.detail.summary')} span={2}>{record.summary || '—'}</Descriptions.Item>
    </Descriptions></Card>
    <Card size="small" title={t('contract.detail.commercial')}><div className="oa-contract-money-grid">
      <Statistic title={t('contract.detail.totalAmount')} value={money(record.amount, record.currency)} />
      <Statistic title={t('contract.detail.paidAmount')} value={money(record.paidAmount, record.currency)} />
      <Statistic title={t('contract.detail.outstandingAmount')} value={money(outstanding, record.currency)} />
    </div><Progress percent={Math.min(100, Math.round(Number(record.paidAmount) / Number(record.amount) * 100))} /></Card>
    <Card size="small" title={t('contract.detail.timeline')}>{detail.events.length ? <Timeline items={detail.events.map((item) => ({
      color: item.eventType === 'PAYMENT_RECORDED' ? 'green' : item.eventType === 'EXPIRY_REMINDER' ? 'gold' : 'blue',
      children: <div className="oa-contract-event"><strong>{t(`contract.event.${item.eventType}`)}</strong>
        {item.fromValue && item.toValue && <span>{t('contract.detail.transition', {
          from: eventValue(item.eventType, item.fromValue), to: eventValue(item.eventType, item.toValue),
        })}</span>}
        {item.amount != null && <span>{money(item.amount, record.currency)}</span>}{item.detail && <Typography.Paragraph>{item.detail}</Typography.Paragraph>}
        <small>{dateTime(item.createdAt)} · {t('contract.detail.operator', { name: item.operatorLabel })}</small></div>,
    }))} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('contract.detail.noTimeline')} />}</Card>
  </div>;
}

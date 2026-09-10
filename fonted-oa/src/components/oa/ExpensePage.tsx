'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import dayjs, { type Dayjs } from 'dayjs';
import {
  App, Button, Card, DatePicker, Descriptions, Drawer, Empty, Form, Input, InputNumber,
  Select, Space, Statistic, Tag, Timeline, Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { OaIcon } from '@/components/OaIcon';
import ResponsiveTable from '@/components/oa/ResponsiveTable';
import type { ApprovalApplication } from '@/lib/approvalEngineApi';
import { message } from '@/lib/antdMessage';
import { expenseApi, parseExpenseData, type ExpenseCategory, type ExpenseFormData } from '@/lib/expenseApi';
import { formatOaApiError } from '@/lib/oaApi';

type ExpenseStatus = ApprovalApplication['status'];
type ExpenseValues = Omit<ExpenseFormData, 'expenseDate'> & { expenseDate?: Dayjs };
const STATUSES: ExpenseStatus[] = ['DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'CANCELLED'];
const CATEGORIES: ExpenseCategory[] = ['TRAVEL', 'MEAL', 'TRANSPORT', 'OFFICE', 'OTHER'];
const STATUS_COLORS: Record<ExpenseStatus, string> = {
  DRAFT: 'default', PENDING: 'processing', APPROVED: 'success', REJECTED: 'error', WITHDRAWN: 'warning', CANCELLED: 'default',
};

export default function ExpensePage() {
  const { t, i18n } = useTranslation();
  const { modal } = App.useApp();
  const [form] = Form.useForm<ExpenseValues>();
  const [records, setRecords] = useState<ApprovalApplication[]>([]);
  const [counts, setCounts] = useState({ total: 0, draft: 0, pending: 0, approved: 0 });
  const [status, setStatus] = useState<ExpenseStatus>();
  const [page, setPage] = useState(1); const [size, setSize] = useState(20); const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true); const [saving, setSaving] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false); const [editing, setEditing] = useState<ApprovalApplication>();
  const [detailOpen, setDetailOpen] = useState(false); const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<ApprovalApplication>();

  const money = useCallback((value?: number) => value == null ? '—' : new Intl.NumberFormat(i18n.language, {
    style: 'currency', currency: 'CNY', maximumFractionDigits: 2,
  }).format(value), [i18n.language]);
  const date = useCallback((value?: string | null) => value ? new Intl.DateTimeFormat(i18n.language, {
    dateStyle: 'medium', ...(value.includes('T') ? { timeStyle: 'short' as const } : {}),
  }).format(new Date(value.includes('T') ? value : `${value}T00:00:00`)) : '—', [i18n.language]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [result, all, drafts, pending, approved] = await Promise.all([
        expenseApi.list(status, page, size), expenseApi.list(undefined, 1, 1), expenseApi.list('DRAFT', 1, 1),
        expenseApi.list('PENDING', 1, 1), expenseApi.list('APPROVED', 1, 1),
      ]);
      setRecords(result.records); setTotal(result.total);
      setCounts({ total: all.total, draft: drafts.total, pending: pending.total, approved: approved.total });
    } catch (error) { message.error(formatOaApiError(error)); } finally { setLoading(false); }
  }, [page, size, status]);
  useEffect(() => { void load(); }, [load]);

  const openCreate = () => { setEditing(undefined); form.resetFields(); setEditorOpen(true); };
  const openEdit = (record: ApprovalApplication) => {
    const data = parseExpenseData(record); setEditing(record);
    form.setFieldsValue({ ...data, expenseDate: data.expenseDate ? dayjs(data.expenseDate) : undefined });
    setDetailOpen(false); setEditorOpen(true);
  };
  const formData = (values: ExpenseValues): ExpenseFormData => ({
    ...values, amount: values.amount == null ? undefined : Number(values.amount),
    expenseDate: values.expenseDate?.format('YYYY-MM-DD'), invoiceNumber: values.invoiceNumber?.trim(), reason: values.reason?.trim(),
  });
  const save = async (submit: boolean) => {
    const values = submit ? await form.validateFields() : form.getFieldsValue(); setSaving(true);
    try {
      const saved = editing
        ? await expenseApi.updateDraft(editing.id, editing.version, formData(values))
        : await expenseApi.createDraft(formData(values));
      if (!editing) setEditing(saved);
      if (submit) await expenseApi.submit(saved.id, saved.version);
      setEditorOpen(false); message.success(t(submit ? 'expense.submitSuccess' : 'expense.saveSuccess')); await load();
    } catch (error) { message.error(formatOaApiError(error)); } finally { setSaving(false); }
  };
  const openDetail = async (record: ApprovalApplication) => {
    setDetail(undefined); setDetailOpen(true); setDetailLoading(true);
    try { setDetail(await expenseApi.detail(record.id)); } catch (error) { message.error(formatOaApiError(error)); }
    finally { setDetailLoading(false); }
  };
  const act = (record: ApprovalApplication, kind: 'cancel' | 'withdraw' | 'reopen' | 'remind') => {
    const title = t(`expense.dialog.${kind}Title`);
    modal.confirm({ title, okText: t('common.confirm'), cancelText: t('common.cancel'), okButtonProps: { danger: kind === 'cancel' || kind === 'withdraw' },
      async onOk() {
        try {
          const updated = await expenseApi[kind](record.id, record.version);
          message.success(t(`expense.${kind}Success`)); setDetailOpen(false); await load();
          if (kind === 'reopen') openEdit(updated);
        } catch (error) { message.error(formatOaApiError(error)); throw error; }
      },
    });
  };

  const statusOptions = useMemo(() => STATUSES.map((value) => ({ value, label: t(`expense.status.${value}`) })), [t]);
  const columns: ColumnsType<ApprovalApplication> = [
    { title: t('expense.columns.expense'), key: 'expense', width: 300, render: (_, record) => {
      const data = parseExpenseData(record); return <div className="oa-expense-name-cell"><span><OaIcon name="expense" size={18} /></span><div><strong>{data.reason || record.formName}</strong><small>{data.invoiceNumber || `#${record.id}`}</small></div></div>;
    } },
    { title: t('expense.columns.category'), key: 'category', width: 120, render: (_, record) => { const value = parseExpenseData(record).category; return value ? t(`expense.category.${value}`) : '—'; } },
    { title: t('expense.columns.amount'), key: 'amount', width: 145, render: (_, record) => <strong>{money(parseExpenseData(record).amount)}</strong> },
    { title: t('expense.columns.expenseDate'), key: 'date', width: 135, render: (_, record) => date(parseExpenseData(record).expenseDate) },
    { title: t('expense.columns.submittedAt'), dataIndex: 'submittedAt', width: 165, render: date },
    { title: t('expense.columns.status'), dataIndex: 'status', width: 105, render: (value: ExpenseStatus) => <Tag color={STATUS_COLORS[value]}>{t(`expense.status.${value}`)}</Tag> },
    { title: t('expense.columns.actions'), key: 'actions', width: 150, fixed: 'right', render: (_, record) => <Space size={2} onClick={(event) => event.stopPropagation()}>
      <Button type="link" size="small" onClick={() => void openDetail(record)}>{t('expense.view')}</Button>
      {record.canEditDraft && <Button type="link" size="small" onClick={() => openEdit(record)}>{t('expense.edit')}</Button>}
    </Space> },
  ];

  return <section className="oa-expense-page">
    <header className="oa-expense-hero"><div className="oa-expense-heading"><span><OaIcon name="expense" size={24} /></span><div>
      <Typography.Text className="oa-expense-eyebrow">{t('expense.eyebrow')}</Typography.Text><Typography.Title level={3}>{t('expense.title')}</Typography.Title>
      <Typography.Paragraph>{t('expense.description')}</Typography.Paragraph></div></div>
      <Space><Button icon={<OaIcon name="reload" />} onClick={() => void load()}>{t('expense.refresh')}</Button><Button type="primary" icon={<OaIcon name="add" />} onClick={openCreate}>{t('expense.add')}</Button></Space></header>
    <div className="oa-expense-stats"><Card><Statistic title={t('expense.total')} value={counts.total} /></Card><Card><Statistic title={t('expense.draft')} value={counts.draft} /></Card><Card><Statistic title={t('expense.pending')} value={counts.pending} /></Card><Card><Statistic title={t('expense.approved')} value={counts.approved} /></Card></div>
    <Card className="oa-expense-list-card"><div className="oa-expense-toolbar"><Select allowClear value={status} options={statusOptions} placeholder={t('expense.searchStatus')} onChange={(value) => { setStatus(value); setPage(1); }} /></div>
      <ResponsiveTable rowKey="id" loading={loading} columns={columns} dataSource={records} scroll={{ x: 1120 }} onRow={(record) => ({ onClick: () => void openDetail(record) })}
        locale={{ emptyText: <Empty description={t('expense.empty')} /> }} pagination={{ current: page, pageSize: size, total, showSizeChanger: true,
          showTotal: (count) => t('common.total', { count }), onChange: (next, nextSize) => { setPage(next); setSize(nextSize); } }} /></Card>
    <Drawer width="min(680px, 100vw)" open={editorOpen} title={t(editing ? 'expense.form.editTitle' : 'expense.form.createTitle')} onClose={() => setEditorOpen(false)}
      extra={<Space><Button loading={saving} onClick={() => void save(false)}>{t('expense.saveDraft')}</Button><Button type="primary" loading={saving} onClick={() => void save(true)}>{t('expense.submit')}</Button></Space>}>
      <ExpenseForm form={form} />
    </Drawer>
    <Drawer width="min(760px, 100vw)" open={detailOpen} loading={detailLoading} title={t('expense.detail.title')} onClose={() => setDetailOpen(false)}>
      {detail ? <ExpenseDetail record={detail} money={money} date={date} onEdit={openEdit} onAct={act} /> : <Empty description={t('expense.empty')} />}
    </Drawer>
  </section>;
}

function ExpenseForm({ form }: { form: ReturnType<typeof Form.useForm<ExpenseValues>>[0] }) {
  const { t } = useTranslation();
  return <Form form={form} layout="vertical" className="oa-expense-form" requiredMark="optional">
    <div className="oa-expense-form-grid"><Form.Item name="amount" label={t('expense.form.amount')} rules={[{ required: true, message: t('expense.form.required') }]}><InputNumber min={0.01} precision={2} className="oa-expense-full-control" /></Form.Item>
      <Form.Item name="category" label={t('expense.form.category')} rules={[{ required: true, message: t('expense.form.required') }]}><Select options={CATEGORIES.map((value) => ({ value, label: t(`expense.category.${value}`) }))} /></Form.Item>
      <Form.Item name="expenseDate" label={t('expense.form.expenseDate')} rules={[{ required: true, message: t('expense.form.required') }]}><DatePicker className="oa-expense-full-control" /></Form.Item>
      <Form.Item name="invoiceNumber" label={t('expense.form.invoiceNumber')} rules={[{ required: true, message: t('expense.form.required') }]}><Input maxLength={100} placeholder={t('expense.form.invoicePlaceholder')} /></Form.Item></div>
    <Form.Item name="reason" label={t('expense.form.reason')} rules={[{ required: true, message: t('expense.form.required') }]}><Input.TextArea rows={6} maxLength={1000} showCount placeholder={t('expense.form.reasonPlaceholder')} /></Form.Item>
  </Form>;
}

function ExpenseDetail({ record, money, date, onEdit, onAct }: { record: ApprovalApplication; money: (value?: number) => string; date: (value?: string | null) => string;
  onEdit: (record: ApprovalApplication) => void; onAct: (record: ApprovalApplication, kind: 'cancel' | 'withdraw' | 'reopen' | 'remind') => void }) {
  const { t } = useTranslation(); const data = parseExpenseData(record);
  return <div className="oa-expense-detail"><div className="oa-expense-detail-heading"><span><OaIcon name="expense" size={25} /></span><div><Typography.Title level={4}>{data.reason || record.formName}</Typography.Title><Tag color={STATUS_COLORS[record.status]}>{t(`expense.status.${record.status}`)}</Tag></div></div>
    <Space wrap className="oa-expense-detail-actions">{record.canEditDraft && <Button type="primary" onClick={() => onEdit(record)}>{t('expense.edit')}</Button>}{record.canCancel && <Button danger onClick={() => onAct(record, 'cancel')}>{t('expense.cancel')}</Button>}
      {record.canWithdraw && <Button danger onClick={() => onAct(record, 'withdraw')}>{t('expense.withdraw')}</Button>}{(['REJECTED', 'WITHDRAWN'] as ExpenseStatus[]).includes(record.status) && <Button onClick={() => onAct(record, 'reopen')}>{t('expense.reopen')}</Button>}
      {record.canRemind && <Button icon={<OaIcon name="notification" />} onClick={() => onAct(record, 'remind')}>{t('expense.remind')}</Button>}</Space>
    <Card size="small" title={t('expense.detail.overview')}><Descriptions column={{ xs: 1, sm: 2 }} size="small"><Descriptions.Item label={t('expense.form.amount')}>{money(data.amount)}</Descriptions.Item><Descriptions.Item label={t('expense.form.category')}>{data.category ? t(`expense.category.${data.category}`) : '—'}</Descriptions.Item>
      <Descriptions.Item label={t('expense.form.expenseDate')}>{date(data.expenseDate)}</Descriptions.Item><Descriptions.Item label={t('expense.form.invoiceNumber')}>{data.invoiceNumber || '—'}</Descriptions.Item><Descriptions.Item label={t('expense.detail.applicant')}>{record.applicantName}</Descriptions.Item><Descriptions.Item label={t('expense.detail.currentApprover')}>{record.taskAssigneeName || '—'}</Descriptions.Item>
      <Descriptions.Item label={t('expense.detail.dueAt')}>{date(record.taskDueAt)}</Descriptions.Item><Descriptions.Item label={t('expense.detail.submittedAt')}>{date(record.submittedAt)}</Descriptions.Item></Descriptions></Card>
    <Card size="small" title={t('expense.detail.process')}>{record.timeline?.length ? <Timeline items={record.timeline.map((item) => ({ color: item.toStatus === 'REJECTED' ? 'red' : item.toStatus === 'APPROVED' ? 'green' : 'blue', children: <div className="oa-expense-event"><strong>{item.actorName}</strong><span>{t(`expense.status.${item.fromStatus || 'DRAFT'}`)} → {t(`expense.status.${item.toStatus}`)}</span>{item.comment && <Typography.Paragraph>{item.comment}</Typography.Paragraph>}<small>{date(item.createdAt)}</small></div> }))} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('expense.detail.noTimeline')} />}</Card>
  </div>;
}

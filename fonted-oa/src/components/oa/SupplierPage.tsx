'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  App, Button, Card, Col, Descriptions, Drawer, Empty, Form, Input, Row, Select, Space,
  Statistic, Tag, Timeline, Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { OaIcon } from '@/components/OaIcon';
import ResponsiveTable from '@/components/oa/ResponsiveTable';
import { message } from '@/lib/antdMessage';
import { formatOaApiError } from '@/lib/oaApi';
import {
  supplierApi,
  type Supplier,
  type SupplierCategory,
  type SupplierDetail,
  type SupplierLevel,
  type SupplierPayload,
  type SupplierStatus,
} from '@/lib/supplierApi';

const STATUSES: SupplierStatus[] = ['DRAFT', 'ACTIVE', 'SUSPENDED', 'BLACKLISTED'];
const CATEGORIES: SupplierCategory[] = ['MATERIAL', 'SERVICE', 'LOGISTICS', 'CONSULTING', 'OTHER'];
const LEVELS: SupplierLevel[] = ['STRATEGIC', 'PREFERRED', 'STANDARD', 'RESTRICTED'];
const STATUS_COLORS: Record<SupplierStatus, string> = {
  DRAFT: 'default', ACTIVE: 'green', SUSPENDED: 'gold', BLACKLISTED: 'red',
};
const LEVEL_COLORS: Record<SupplierLevel, string> = {
  STRATEGIC: 'blue', PREFERRED: 'cyan', STANDARD: 'default', RESTRICTED: 'orange',
};

export default function SupplierPage() {
  const { t, i18n } = useTranslation();
  const { modal } = App.useApp();
  const [form] = Form.useForm<SupplierPayload>();
  const [records, setRecords] = useState<Supplier[]>([]);
  const [stats, setStats] = useState({ total: 0, active: 0, suspended: 0, blacklisted: 0 });
  const [total, setTotal] = useState(0);
  const [canManage, setCanManage] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<SupplierStatus>();
  const [category, setCategory] = useState<SupplierCategory>();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<Supplier>();
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<SupplierDetail>();

  const statusOptions = useMemo(() => STATUSES.map((value) => ({
    value, label: t(`supplier.status.${value}`),
  })), [t]);
  const categoryOptions = useMemo(() => CATEGORIES.map((value) => ({
    value, label: t(`supplier.category.${value}`),
  })), [t]);
  const levelOptions = useMemo(() => LEVELS.map((value) => ({
    value, label: t(`supplier.level.${value}`),
  })), [t]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await supplierApi.list({ keyword, status, category, page, size });
      setRecords(result.records);
      setTotal(result.total);
      setStats(result.stats);
      setCanManage(result.canManage);
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setLoading(false);
    }
  }, [category, keyword, page, size, status]);

  useEffect(() => { void load(); }, [load]);

  const loadDetail = useCallback(async (id: number) => {
    setDetailLoading(true);
    try {
      const result = await supplierApi.detail(id);
      setDetail(result);
      return result;
    } catch (error) {
      message.error(formatOaApiError(error));
      return undefined;
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const openDetail = (record: Supplier) => {
    setDetail(undefined);
    setDetailOpen(true);
    void loadDetail(record.id);
  };

  const openCreate = () => {
    setEditing(undefined);
    form.resetFields();
    form.setFieldsValue({ category: 'SERVICE', supplierLevel: 'STANDARD' });
    setEditorOpen(true);
  };

  const openEdit = (record: Supplier) => {
    setDetailOpen(false);
    setEditing(record);
    form.setFieldsValue({
      code: record.code,
      name: record.name,
      shortName: record.shortName,
      unifiedSocialCreditCode: record.unifiedSocialCreditCode,
      category: record.category,
      supplierLevel: record.supplierLevel,
      contactName: record.contactName,
      contactPhone: record.contactPhone,
      contactEmail: record.contactEmail,
      address: record.address,
      paymentTerms: record.paymentTerms,
      riskNote: record.riskNote,
    });
    setEditorOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await supplierApi.update(editing.id, { ...values, version: editing.version });
      } else {
        await supplierApi.create(values);
      }
      setEditorOpen(false);
      message.success(t('supplier.saveSuccess'));
      await load();
      if (detailOpen && editing) await loadDetail(editing.id);
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setSaving(false);
    }
  };

  const changeStatus = (supplier: Supplier, target: SupplierStatus) => {
    const needsReason = target === 'SUSPENDED' || target === 'BLACKLISTED';
    let reason = '';
    modal.confirm({
      title: t('supplier.statusDialog.title'),
      content: (
        <div className="oa-supplier-status-dialog">
          <Typography.Paragraph>
            {t('supplier.statusDialog.content', {
              name: supplier.name,
              status: t(`supplier.status.${target}`),
            })}
          </Typography.Paragraph>
          {needsReason && <Input.TextArea
            rows={3}
            maxLength={500}
            showCount
            placeholder={t('supplier.statusDialog.reasonPlaceholder')}
            aria-label={t('supplier.statusDialog.reason')}
            onChange={(event) => { reason = event.target.value; }}
          />}
        </div>
      ),
      okText: t(`supplier.actions.${target}`),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: target === 'BLACKLISTED' },
      onOk: async () => {
        if (needsReason && !reason.trim()) {
          message.warning(t('supplier.statusDialog.reasonRequired'));
          return Promise.reject();
        }
        try {
          await supplierApi.updateStatus(supplier.id, target, supplier.version, reason.trim() || undefined);
          message.success(t('supplier.statusSuccess'));
          await load();
          if (detailOpen) await loadDetail(supplier.id);
        } catch (error) {
          message.error(formatOaApiError(error));
          return Promise.reject(error);
        }
      },
    });
  };

  const columns: ColumnsType<Supplier> = [
    {
      title: t('supplier.columns.supplier'), key: 'supplier', width: 280,
      render: (_, record) => <div className="oa-supplier-name-cell">
        <span className="oa-supplier-avatar"><OaIcon name="suppliers" size={18} /></span>
        <div><strong>{record.shortName || record.name}</strong><small>{record.name}</small></div>
      </div>,
    },
    {
      title: t('supplier.columns.code'), dataIndex: 'code', width: 145,
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    {
      title: t('supplier.columns.category'), dataIndex: 'category', width: 110,
      render: (value: SupplierCategory) => t(`supplier.category.${value}`),
    },
    {
      title: t('supplier.columns.level'), dataIndex: 'supplierLevel', width: 110,
      render: (value: SupplierLevel) => <Tag color={LEVEL_COLORS[value]}>{t(`supplier.level.${value}`)}</Tag>,
    },
    {
      title: t('supplier.columns.contact'), key: 'contact', width: 190,
      render: (_, record) => <div className="oa-supplier-contact-cell">
        <span>{record.contactName || '—'}</span><small>{record.contactPhone || record.contactEmail || '—'}</small>
      </div>,
    },
    {
      title: t('supplier.columns.status'), dataIndex: 'status', width: 115,
      render: (value: SupplierStatus) => <Tag color={STATUS_COLORS[value]}>{t(`supplier.status.${value}`)}</Tag>,
    },
    {
      title: t('supplier.columns.updatedAt'), dataIndex: 'updatedAt', width: 175,
      render: (value: string) => new Intl.DateTimeFormat(i18n.language, {
        dateStyle: 'medium', timeStyle: 'short',
      }).format(new Date(value)),
    },
    {
      title: t('supplier.columns.actions'), key: 'actions', width: canManage ? 138 : 70, fixed: 'right',
      render: (_, record) => <Space size={2} onClick={(event) => event.stopPropagation()}>
        <Button type="link" size="small" onClick={() => openDetail(record)}>{t('supplier.actions.view')}</Button>
        {canManage && <Button type="link" size="small" onClick={() => openEdit(record)}>{t('supplier.actions.edit')}</Button>}
      </Space>,
    },
  ];

  return (
    <section className="oa-supplier-page">
      <header className="oa-supplier-hero">
        <div className="oa-supplier-heading">
          <span className="oa-supplier-heading-icon"><OaIcon name="suppliers" size={24} /></span>
          <div>
            <Typography.Text className="oa-supplier-eyebrow">{t('supplier.eyebrow')}</Typography.Text>
            <Typography.Title level={3}>{t('supplier.title')}</Typography.Title>
            <Typography.Paragraph>{t('supplier.description')}</Typography.Paragraph>
          </div>
        </div>
        <Space>
          <Button icon={<OaIcon name="reload" />} onClick={() => void load()}>{t('supplier.refresh')}</Button>
          {canManage && <Button type="primary" icon={<OaIcon name="add" />} onClick={openCreate}>{t('supplier.add')}</Button>}
        </Space>
      </header>

      <Row gutter={[12, 12]} className="oa-supplier-stats">
        <Col xs={12} lg={6}><Card><Statistic title={t('supplier.total')} value={stats.total} /></Card></Col>
        <Col xs={12} lg={6}><Card><Statistic title={t('supplier.active')} value={stats.active} /></Card></Col>
        <Col xs={12} lg={6}><Card><Statistic title={t('supplier.suspended')} value={stats.suspended} /></Card></Col>
        <Col xs={12} lg={6}><Card><Statistic title={t('supplier.blacklisted')} value={stats.blacklisted} /></Card></Col>
      </Row>

      <Card className="oa-supplier-list-card" variant="outlined">
        <div className="oa-supplier-toolbar">
          <Input.Search
            allowClear
            value={keywordInput}
            placeholder={t('supplier.searchPlaceholder')}
            onChange={(event) => {
              setKeywordInput(event.target.value);
              if (!event.target.value) { setKeyword(''); setPage(1); }
            }}
            onSearch={(value) => { setKeyword(value.trim()); setPage(1); }}
          />
          <Select
            allowClear value={status} placeholder={t('supplier.allStatuses')} options={statusOptions}
            onChange={(value) => { setStatus(value); setPage(1); }}
          />
          <Select
            allowClear value={category} placeholder={t('supplier.allCategories')} options={categoryOptions}
            onChange={(value) => { setCategory(value); setPage(1); }}
          />
        </div>
        <ResponsiveTable
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={records}
          scroll={{ x: 1180 }}
          locale={{ emptyText: <Empty description={t('supplier.empty')} /> }}
          onRow={(record) => ({ onClick: () => openDetail(record) })}
          pagination={{
            current: page, pageSize: size, total, showSizeChanger: true,
            showTotal: (count) => t('common.total', { count }),
            onChange: (nextPage, nextSize) => { setPage(nextPage); setSize(nextSize); },
          }}
        />
      </Card>

      <Drawer
        width="min(720px, 100vw)"
        open={editorOpen}
        title={t(editing ? 'supplier.form.editTitle' : 'supplier.form.createTitle')}
        onClose={() => setEditorOpen(false)}
        extra={<Button type="primary" loading={saving} onClick={() => void save()}>{t('common.save')}</Button>}
      >
        <SupplierForm form={form} editing={Boolean(editing)} categoryOptions={categoryOptions} levelOptions={levelOptions} />
      </Drawer>

      <Drawer
        width="min(760px, 100vw)"
        open={detailOpen}
        loading={detailLoading}
        title={t('supplier.detail.title')}
        onClose={() => setDetailOpen(false)}
        extra={detail?.supplier.canManage
          ? <Button icon={<OaIcon name="edit" />} onClick={() => openEdit(detail.supplier)}>{t('supplier.actions.edit')}</Button>
          : undefined}
      >
        {detail ? <SupplierDetailView detail={detail} onStatus={changeStatus} /> : <Empty description={t('supplier.empty')} />}
      </Drawer>
    </section>
  );
}

interface SupplierFormProps {
  form: ReturnType<typeof Form.useForm<SupplierPayload>>[0];
  editing: boolean;
  categoryOptions: Array<{ value: SupplierCategory; label: string }>;
  levelOptions: Array<{ value: SupplierLevel; label: string }>;
}

function SupplierForm({ form, editing, categoryOptions, levelOptions }: SupplierFormProps) {
  const { t } = useTranslation();
  return <Form form={form} layout="vertical" requiredMark="optional" className="oa-supplier-form">
    <Typography.Title level={5}>{t('supplier.form.identity')}</Typography.Title>
    <Row gutter={12}>
      <Col xs={24} sm={10}><Form.Item name="code" label={t('supplier.form.code')} rules={[{ required: true, message: t('supplier.form.required') }]}><Input disabled={editing} maxLength={64} placeholder={t('supplier.form.codePlaceholder')} /></Form.Item></Col>
      <Col xs={24} sm={14}><Form.Item name="name" label={t('supplier.form.name')} rules={[{ required: true, message: t('supplier.form.required') }]}><Input maxLength={160} placeholder={t('supplier.form.namePlaceholder')} /></Form.Item></Col>
      <Col xs={24} sm={12}><Form.Item name="shortName" label={t('supplier.form.shortName')}><Input maxLength={80} placeholder={t('supplier.form.shortNamePlaceholder')} /></Form.Item></Col>
      <Col xs={24} sm={12}><Form.Item name="unifiedSocialCreditCode" label={t('supplier.form.creditCode')} rules={[{ pattern: /^[0-9A-Z]{18}$/, message: t('supplier.form.invalidCreditCode') }]}><Input maxLength={18} placeholder={t('supplier.form.creditCodePlaceholder')} /></Form.Item></Col>
      <Col xs={24} sm={12}><Form.Item name="category" label={t('supplier.form.category')} rules={[{ required: true, message: t('supplier.form.required') }]}><Select options={categoryOptions} /></Form.Item></Col>
      <Col xs={24} sm={12}><Form.Item name="supplierLevel" label={t('supplier.form.level')} rules={[{ required: true, message: t('supplier.form.required') }]}><Select options={levelOptions} /></Form.Item></Col>
    </Row>
    <Typography.Title level={5}>{t('supplier.form.contact')}</Typography.Title>
    <Row gutter={12}>
      <Col xs={24} sm={12}><Form.Item name="contactName" label={t('supplier.form.contactName')}><Input maxLength={80} placeholder={t('supplier.form.contactNamePlaceholder')} /></Form.Item></Col>
      <Col xs={24} sm={12}><Form.Item name="contactPhone" label={t('supplier.form.contactPhone')}><Input maxLength={32} placeholder={t('supplier.form.contactPhonePlaceholder')} /></Form.Item></Col>
      <Col xs={24} sm={12}><Form.Item name="contactEmail" label={t('supplier.form.contactEmail')} rules={[{ type: 'email', message: t('supplier.form.invalidEmail') }]}><Input maxLength={160} placeholder={t('supplier.form.contactEmailPlaceholder')} /></Form.Item></Col>
      <Col xs={24} sm={12}><Form.Item name="address" label={t('supplier.form.address')}><Input maxLength={300} placeholder={t('supplier.form.addressPlaceholder')} /></Form.Item></Col>
    </Row>
    <Typography.Title level={5}>{t('supplier.form.cooperation')}</Typography.Title>
    <Form.Item name="paymentTerms" label={t('supplier.form.paymentTerms')}><Input maxLength={120} placeholder={t('supplier.form.paymentTermsPlaceholder')} /></Form.Item>
    <Form.Item name="riskNote" label={t('supplier.form.riskNote')}><Input.TextArea rows={4} maxLength={1000} showCount placeholder={t('supplier.form.riskNotePlaceholder')} /></Form.Item>
  </Form>;
}

function SupplierDetailView({ detail, onStatus }: { detail: SupplierDetail; onStatus: (supplier: Supplier, target: SupplierStatus) => void }) {
  const { t, i18n } = useTranslation();
  const supplier = detail.supplier;
  const formatDate = (value: string) => new Intl.DateTimeFormat(i18n.language, {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(new Date(value));
  return <div className="oa-supplier-detail">
    <div className="oa-supplier-detail-identity">
      <span className="oa-supplier-detail-logo"><OaIcon name="suppliers" size={26} /></span>
      <div><Typography.Title level={4}>{supplier.name}</Typography.Title><Space wrap><Typography.Text code>{supplier.code}</Typography.Text><Tag color={STATUS_COLORS[supplier.status]}>{t(`supplier.status.${supplier.status}`)}</Tag><Tag color={LEVEL_COLORS[supplier.supplierLevel]}>{t(`supplier.level.${supplier.supplierLevel}`)}</Tag></Space></div>
    </div>
    {supplier.canManage && supplier.allowedTransitions.length > 0 && <Space wrap className="oa-supplier-transition-actions">
      {supplier.allowedTransitions.map((target) => <Button key={target} danger={target === 'BLACKLISTED'} type={target === 'ACTIVE' ? 'primary' : 'default'} onClick={() => onStatus(supplier, target)}>{t(`supplier.actions.${target}`)}</Button>)}
    </Space>}
    <Card size="small" title={t('supplier.detail.profile')}>
      <Descriptions column={{ xs: 1, sm: 2 }} size="small">
        <Descriptions.Item label={t('supplier.detail.shortName')}>{supplier.shortName || '—'}</Descriptions.Item>
        <Descriptions.Item label={t('supplier.form.category')}>{t(`supplier.category.${supplier.category}`)}</Descriptions.Item>
        <Descriptions.Item label={t('supplier.detail.creditCode')} span={2}>{supplier.unifiedSocialCreditCode || '—'}</Descriptions.Item>
        <Descriptions.Item label={t('supplier.detail.address')} span={2}>{supplier.address || '—'}</Descriptions.Item>
      </Descriptions>
    </Card>
    <Card size="small" title={t('supplier.detail.contact')}>
      <Descriptions column={{ xs: 1, sm: 2 }} size="small">
        <Descriptions.Item label={t('supplier.form.contactName')}>{supplier.contactName || '—'}</Descriptions.Item>
        <Descriptions.Item label={t('supplier.detail.phone')}>{supplier.contactPhone || '—'}</Descriptions.Item>
        <Descriptions.Item label={t('supplier.detail.email')} span={2}>{supplier.contactEmail || '—'}</Descriptions.Item>
      </Descriptions>
    </Card>
    <Card size="small" title={t('supplier.detail.cooperation')}>
      <Descriptions column={1} size="small">
        <Descriptions.Item label={t('supplier.detail.paymentTerms')}>{supplier.paymentTerms || '—'}</Descriptions.Item>
        <Descriptions.Item label={t('supplier.detail.riskNote')}>{supplier.riskNote || '—'}</Descriptions.Item>
      </Descriptions>
    </Card>
    <Card size="small" title={t('supplier.detail.history')}>
      {detail.statusHistory.length > 0 ? <Timeline items={detail.statusHistory.map((item) => ({
        color: item.toStatus === 'BLACKLISTED' ? 'red' : item.toStatus === 'ACTIVE' ? 'green' : 'blue',
        children: <div className="oa-supplier-history-item"><strong>{item.fromStatus ? t('supplier.detail.transition', { from: t(`supplier.status.${item.fromStatus}`), to: t(`supplier.status.${item.toStatus}`) }) : t('supplier.detail.initial')}</strong><span>{formatDate(item.createdAt)} · {t('supplier.detail.operator', { name: item.operatorLabel })}</span>{item.reason && <Typography.Paragraph>{item.reason}</Typography.Paragraph>}</div>,
      }))} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('supplier.detail.noHistory')} />}
    </Card>
  </div>;
}

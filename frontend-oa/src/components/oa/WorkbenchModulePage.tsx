'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  App,
  Button,
  Card,
  Col,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { OaIcon, type OaIconName } from '@/components/OaIcon';
import { formatOaApiError } from '@/lib/oaApi';
import { message } from '@/lib/antdMessage';
import {
  workbenchApi,
  type WorkbenchRecord,
  type WorkbenchRecordPayload,
  type WorkbenchStatus,
} from '@/lib/workbenchApi';

interface WorkbenchModulePageProps {
  moduleKey: string;
  title: string;
}

const FINANCE_MODULES = new Set(['expense', 'budget', 'contracts', 'suppliers']);
const STATUS_VALUES: WorkbenchStatus[] = ['DRAFT', 'ACTIVE', 'PENDING', 'COMPLETED', 'DISABLED', 'FAILED'];
const STATUS_COLORS: Record<WorkbenchStatus, string> = {
  DRAFT: 'default',
  ACTIVE: 'green',
  PENDING: 'gold',
  COMPLETED: 'blue',
  DISABLED: 'default',
  FAILED: 'red',
};
const MODULE_ICONS: Record<string, OaIconName> = {
  expense: 'expense', budget: 'budget', contracts: 'contracts', suppliers: 'suppliers',
  'api-center': 'api-center', 'page-actions': 'page-actions', 'runtime-logs': 'runtime-logs',
  'sandbox-replay': 'sandbox', 'data-permission': 'data-permission', 'ai-permission': 'robot',
  'tenant-config': 'tenant', dictionary: 'dictionary',
};

export default function WorkbenchModulePage({ moduleKey, title }: WorkbenchModulePageProps) {
  const { t, i18n } = useTranslation();
  const { modal } = App.useApp();
  const [form] = Form.useForm<WorkbenchRecordPayload>();
  const [records, setRecords] = useState<WorkbenchRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [canManage, setCanManage] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string>();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<WorkbenchRecord>();
  const showAmount = FINANCE_MODULES.has(moduleKey);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await workbenchApi.list(moduleKey, { keyword, status, page, size });
      setRecords(result.records);
      setTotal(result.total);
      setCanManage(result.canManage);
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setLoading(false);
    }
  }, [keyword, moduleKey, page, size, status]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(undefined);
    form.resetFields();
    form.setFieldsValue({ status: 'ACTIVE' });
    setDrawerOpen(true);
  };

  const openEdit = (record: WorkbenchRecord) => {
    setEditing(record);
    form.setFieldsValue(record);
    setDrawerOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await workbenchApi.update(moduleKey, editing.id, { ...values, version: editing.version });
      } else {
        await workbenchApi.create(moduleKey, values);
      }
      setDrawerOpen(false);
      message.success(t('workbench.saveSuccess'));
      await load();
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setSaving(false);
    }
  };

  const remove = (record: WorkbenchRecord) => {
    modal.confirm({
      title: t('workbench.deleteTitle'),
      content: t('workbench.deleteContent'),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await workbenchApi.delete(moduleKey, record.id, record.version);
          message.success(t('workbench.deleteSuccess'));
          await load();
        } catch (error) {
          message.error(formatOaApiError(error));
        }
      },
    });
  };

  const statusOptions = useMemo(() => STATUS_VALUES.map((value) => ({
    value,
    label: t(`workbench.statusLabels.${value}`),
  })), [t]);

  const columns: ColumnsType<WorkbenchRecord> = (() => {
    const result: ColumnsType<WorkbenchRecord> = [
      {
        title: t('workbench.code'), dataIndex: 'code', width: 168,
        render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
      },
      { title: t('workbench.title'), dataIndex: 'title', ellipsis: true },
      { title: t('workbench.category'), dataIndex: 'category', width: 150, render: (value?: string) => value || '-' },
      {
        title: t('workbench.status'), dataIndex: 'status', width: 118,
        render: (value: WorkbenchStatus) => (
          <Tag color={STATUS_COLORS[value]}>{t(`workbench.statusLabels.${value}`)}</Tag>
        ),
      },
      { title: t('workbench.owner'), dataIndex: 'owner', width: 150, render: (value?: string) => value || '-' },
      {
        title: t('workbench.updatedAt'), dataIndex: 'updatedAt', width: 178,
        render: (value: string) => new Intl.DateTimeFormat(i18n.language, {
          dateStyle: 'medium', timeStyle: 'short',
        }).format(new Date(value)),
      },
    ];
    if (showAmount) {
      result.splice(4, 0, {
        title: t('workbench.amount'), dataIndex: 'amount', width: 140, align: 'right',
        render: (value?: number) => value == null ? '-' : new Intl.NumberFormat(i18n.language, {
          style: 'currency', currency: 'CNY', maximumFractionDigits: 2,
        }).format(value),
      });
    }
    if (canManage) {
      result.push({
        title: t('workbench.actions'), key: 'actions', width: 130, fixed: 'right',
        render: (_, record) => (
          <Space size={4}>
            <Button type="link" size="small" onClick={() => openEdit(record)}>{t('common.edit')}</Button>
            <Button type="link" size="small" danger onClick={() => remove(record)}>{t('common.delete')}</Button>
          </Space>
        ),
      });
    }
    return result;
  })();

  const activeCount = records.filter((item) => item.status === 'ACTIVE' || item.status === 'COMPLETED').length;
  const pendingCount = records.filter((item) => item.status === 'PENDING' || item.status === 'DRAFT').length;
  const attentionCount = records.filter((item) => item.status === 'FAILED' || item.status === 'DISABLED').length;
  const pageAmount = records.reduce((sum, item) => sum + Number(item.amount || 0), 0);

  return (
    <section className="oa-workbench-module-page">
      <header className="oa-workbench-module-hero">
        <div className="oa-workbench-module-identity">
          <span className="oa-workbench-module-icon"><OaIcon name={MODULE_ICONS[moduleKey] || 'business'} size={22} /></span>
          <div>
            <Typography.Text className="oa-workbench-module-eyebrow">{t('workbench.eyebrow')}</Typography.Text>
            <Typography.Title level={3}>{title}</Typography.Title>
            <Typography.Paragraph>{t(`workbench.descriptions.${moduleKey}`)}</Typography.Paragraph>
          </div>
        </div>
        <Space>
          <Button icon={<OaIcon name="reload" />} onClick={() => void load()}>{t('workbench.refresh')}</Button>
          {canManage && (
            <Button type="primary" icon={<OaIcon name="add" />} onClick={openCreate}>{t('workbench.add')}</Button>
          )}
        </Space>
      </header>

      <Row gutter={[14, 14]} className="oa-workbench-module-stats">
        <Col xs={12} lg={6}><Card><Statistic title={t('workbench.total')} value={total} /></Card></Col>
        <Col xs={12} lg={6}><Card><Statistic title={t('workbench.activeOnPage')} value={activeCount} /></Card></Col>
        <Col xs={12} lg={6}><Card><Statistic title={t('workbench.pendingOnPage')} value={pendingCount} /></Card></Col>
        <Col xs={12} lg={6}><Card><Statistic
          title={t(showAmount ? 'workbench.amountOnPage' : 'workbench.attentionOnPage')}
          value={showAmount ? pageAmount : attentionCount}
          precision={showAmount ? 2 : 0}
          prefix={showAmount ? '¥' : undefined}
        /></Card></Col>
      </Row>

      <Card className="oa-workbench-module-card" variant="outlined">
        <div className="oa-workbench-module-toolbar">
          <Input.Search
            allowClear
            value={searchText}
            placeholder={t('workbench.searchPlaceholder')}
            onChange={(event) => {
              setSearchText(event.target.value);
              if (!event.target.value) { setKeyword(''); setPage(1); }
            }}
            onSearch={(value) => { setKeyword(value.trim()); setPage(1); }}
          />
          <Select
            allowClear
            value={status}
            placeholder={t('workbench.allStatuses')}
            options={statusOptions}
            onChange={(value) => { setStatus(value); setPage(1); }}
          />
        </div>
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={records}
          scroll={{ x: 980 }}
          locale={{ emptyText: <Empty description={t('workbench.empty')} /> }}
          pagination={{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: true,
            onChange: (nextPage, nextSize) => { setPage(nextPage); setSize(nextSize); },
          }}
        />
      </Card>

      <Drawer
        width={520}
        open={drawerOpen}
        title={editing ? t('workbench.edit') : t('workbench.add')}
        onClose={() => setDrawerOpen(false)}
        extra={<Button type="primary" loading={saving} onClick={() => void save()}>{t('common.save')}</Button>}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="code" label={t('workbench.code')} rules={[{ required: true }]}>
            <Input disabled={Boolean(editing)} maxLength={64} placeholder={t('workbench.codePlaceholder')} />
          </Form.Item>
          <Form.Item name="title" label={t('workbench.title')} rules={[{ required: true }]}>
            <Input maxLength={160} placeholder={t('workbench.titlePlaceholder')} />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}><Form.Item name="category" label={t('workbench.category')}><Input maxLength={80} placeholder={t('workbench.categoryPlaceholder')} /></Form.Item></Col>
            <Col span={12}><Form.Item name="status" label={t('workbench.status')}><Select options={statusOptions} /></Form.Item></Col>
          </Row>
          {showAmount && <Form.Item name="amount" label={t('workbench.amount')}><InputNumber min={0} precision={2} className="oa-workbench-module-number" /></Form.Item>}
          <Form.Item name="owner" label={t('workbench.owner')}><Input maxLength={100} placeholder={t('workbench.ownerPlaceholder')} /></Form.Item>
          <Form.Item name="details" label={t('workbench.details')}><Input.TextArea rows={7} maxLength={4000} showCount placeholder={t('workbench.detailsPlaceholder')} /></Form.Item>
        </Form>
      </Drawer>
    </section>
  );
}

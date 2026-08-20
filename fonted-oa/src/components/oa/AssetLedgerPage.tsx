'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tag,
} from 'antd';
import { message } from '@/lib/antdMessage';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import {
  adminAssetsApi,
  type AssetLedger,
  type AssetLedgerPayload,
  type AssetStatus,
} from '@/lib/adminAssetsApi';
import { formatOaApiError } from '@/lib/oaApi';
import AdminAssetsPageShell from './AdminAssetsPageShell';

const STATUS_TAG_COLOR: Record<AssetStatus, string> = {
  IN_USE: 'success',
  IDLE: 'default',
  REPAIRING: 'warning',
  SCRAPPED: 'error',
};

interface AssetFormValues extends Omit<AssetLedgerPayload, 'purchaseDate'> {
  purchaseDate?: string | null;
}

export default function AssetLedgerPage() {
  const { t } = useTranslation();
  const [data, setData] = useState<AssetLedger[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState<string | undefined>(undefined);
  const [status, setStatus] = useState<string | undefined>(undefined);

  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<AssetLedger | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<AssetFormValues>();

  const load = useCallback(async (p = page, s = size) => {
    setLoading(true);
    try {
      const res = await adminAssetsApi.listAssets({
        keyword: keyword || undefined,
        category,
        status: status as AssetStatus | undefined,
        page: p,
        size: s,
      });
      setData(res.records);
      setTotal(res.total);
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setLoading(false);
    }
  }, [keyword, category, status, page, size]);

  useEffect(() => {
    load(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSearch = () => {
    setPage(1);
    load(1);
  };

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setEditOpen(true);
  };

  const openEdit = (record: AssetLedger) => {
    setEditing(record);
    form.setFieldsValue({
      assetCode: record.assetCode,
      name: record.name,
      category: record.category,
      specification: record.specification || '',
      status: record.status,
      departmentId: record.departmentId,
      ownerUserId: record.ownerUserId,
      purchaseDate: record.purchaseDate,
      originalValue: record.originalValue,
      remark: record.remark || '',
    });
    setEditOpen(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const payload: AssetLedgerPayload = {
        ...values,
        purchaseDate: values.purchaseDate || undefined,
      };
      if (editing) {
        await adminAssetsApi.updateAsset(editing.id, payload);
        message.success(t('adminAssets.asset.updateSuccess'));
      } else {
        await adminAssetsApi.createAsset(payload);
        message.success(t('adminAssets.asset.createSuccess'));
      }
      setEditOpen(false);
      await load(page);
    } catch (err) {
      if (err instanceof Error && err.name === 'ValidationError') return;
      message.error(formatOaApiError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await adminAssetsApi.deleteAsset(id);
      message.success(t('adminAssets.asset.deleteSuccess'));
      await load(page);
    } catch (err) {
      message.error(formatOaApiError(err));
    }
  };

  const columns: ColumnsType<AssetLedger> = [
    { title: t('adminAssets.asset.assetCode'), dataIndex: 'assetCode', key: 'assetCode' },
    { title: t('adminAssets.asset.name'), dataIndex: 'name', key: 'name' },
    { title: t('adminAssets.asset.category'), dataIndex: 'category', key: 'category' },
    {
      title: t('adminAssets.asset.status'),
      dataIndex: 'status',
      key: 'status',
      render: (s: AssetStatus) => (
        <Tag color={STATUS_TAG_COLOR[s]}>
          {t(`adminAssets.asset.statusOption.${s}`, { defaultValue: s })}
        </Tag>
      ),
    },
    {
      title: t('adminAssets.asset.ownerName'),
      dataIndex: 'ownerName',
      key: 'ownerName',
      render: (v?: string | null) => v || '-',
    },
    {
      title: t('adminAssets.asset.purchaseDate'),
      dataIndex: 'purchaseDate',
      key: 'purchaseDate',
      render: (v?: string | null) => (v ? dayjs(v).format('YYYY-MM-DD') : '-'),
    },
    {
      title: t('adminAssets.common.action'),
      key: 'action',
      render: (_: unknown, record: AssetLedger) =>
        record.canEdit ? (
          <Space>
            <Button type="link" onClick={() => openEdit(record)}>
              {t('adminAssets.common.edit')}
            </Button>
            <Popconfirm
              title={t('adminAssets.asset.deleteConfirm')}
              onConfirm={() => handleDelete(record.id)}
            >
              <Button type="link" danger disabled={!record.canDelete}>
                {t('adminAssets.common.delete')}
              </Button>
            </Popconfirm>
          </Space>
        ) : (
          '-'
        ),
    },
  ];

  return (
    <AdminAssetsPageShell
      eyebrow={t('adminAssets.eyebrow')}
      title={t('adminAssets.asset.title')}
      description={t('adminAssets.asset.description')}
      actions={
        <Button type="primary" onClick={openCreate}>
          {t('adminAssets.asset.create')}
        </Button>
      }
    >
      <Spin spinning={loading}>
        <Card className="oa-admin-assets-card oa-admin-assets-card--fill" variant="outlined">
          <Space className="oa-admin-assets-filters" wrap>
            <Input.Search
              placeholder={t('adminAssets.asset.searchPlaceholder')}
              allowClear
              style={{ width: 240 }}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onSearch={handleSearch}
            />
            <Select
              allowClear
              placeholder={t('adminAssets.asset.category')}
              style={{ width: 150 }}
              value={category}
              onChange={(v) => setCategory(v)}
              options={[
                { value: 'IT设备', label: 'IT设备' },
                { value: '办公家具', label: '办公家具' },
                { value: '车辆', label: '车辆' },
                { value: '其他', label: t('adminAssets.common.other') },
              ]}
            />
            <Select
              allowClear
              placeholder={t('adminAssets.asset.status')}
              style={{ width: 130 }}
              value={status}
              onChange={(v) => setStatus(v)}
              options={Object.keys(STATUS_TAG_COLOR).map((s) => ({
                value: s,
                label: t(`adminAssets.asset.statusOption.${s}`, { defaultValue: s }),
              }))}
            />
          </Space>

          <Table
            rowKey="id"
            columns={columns}
            dataSource={data}
            size="middle"
            style={{ marginTop: 16 }}
            pagination={{
              current: page,
              pageSize: size,
              total,
              showSizeChanger: true,
              onChange: (p, s) => {
                setPage(p);
                setSize(s);
                load(p, s);
              },
            }}
            locale={{ emptyText: <Empty description={t('adminAssets.common.noData')} /> }}
          />
        </Card>
      </Spin>

      <Modal
        title={editing ? t('adminAssets.asset.edit') : t('adminAssets.asset.create')}
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={handleSubmit}
        confirmLoading={submitting}
        okText={t('adminAssets.common.save')}
        destroyOnClose
        width={560}
      >
        <Form form={form} layout="vertical" initialValues={{ status: 'IN_USE' }}>
          <Form.Item
            name="assetCode"
            label={t('adminAssets.asset.assetCode')}
            rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="name"
            label={t('adminAssets.asset.name')}
            rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
          >
            <Input maxLength={120} />
          </Form.Item>
          <Form.Item
            name="category"
            label={t('adminAssets.asset.category')}
            rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
          >
            <Input maxLength={40} />
          </Form.Item>
          <Form.Item name="specification" label={t('adminAssets.asset.specification')}>
            <Input maxLength={120} />
          </Form.Item>
          <Form.Item name="status" label={t('adminAssets.asset.status')}>
            <Select
              options={Object.keys(STATUS_TAG_COLOR).map((s) => ({
                value: s,
                label: t(`adminAssets.asset.statusOption.${s}`, { defaultValue: s }),
              }))}
            />
          </Form.Item>
          <Form.Item name="ownerUserId" label={t('adminAssets.asset.ownerUserId')}>
            <InputNumber style={{ width: '100%' }} min={1} />
          </Form.Item>
          <Form.Item name="purchaseDate" label={t('adminAssets.asset.purchaseDate')}>
            <Input placeholder="YYYY-MM-DD" />
          </Form.Item>
          <Form.Item name="originalValue" label={t('adminAssets.asset.originalValue')}>
            <InputNumber style={{ width: '100%' }} min={0} precision={2} />
          </Form.Item>
          <Form.Item name="remark" label={t('adminAssets.asset.remark')}>
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </AdminAssetsPageShell>
  );
}

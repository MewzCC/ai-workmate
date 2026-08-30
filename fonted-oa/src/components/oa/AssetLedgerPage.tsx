'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Descriptions,
  Drawer,
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
  Timeline,
  Typography,
} from 'antd';
import { message } from '@/lib/antdMessage';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import {
  adminAssetsApi,
  type AssetLedger,
  type AssetLedgerPayload,
  type AssetOperationPayload,
  type AssetOperationType,
  type AssetStatus,
} from '@/lib/adminAssetsApi';
import { hrApi, type OrganizationOverview } from '@/lib/hrApi';
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

type OperationFormValues = Omit<AssetOperationPayload, 'version'>;

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
  const [operationForm] = Form.useForm<OperationFormValues>();
  const [overview, setOverview] = useState<OrganizationOverview>();
  const [detail, setDetail] = useState<AssetLedger | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [operationType, setOperationType] = useState<AssetOperationType>();
  const [operating, setOperating] = useState(false);
  const targetDepartmentId = Form.useWatch('targetDepartmentId', operationForm);

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
    hrApi.overview().then(setOverview).catch(() => undefined);
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
      departmentId: record.departmentId,
      purchaseDate: record.purchaseDate,
      originalValue: record.originalValue,
      remark: record.remark || '',
      version: record.version,
    });
    setEditOpen(true);
  };

  const openDetail = async (record: AssetLedger) => {
    setDetail(record);
    setDetailLoading(true);
    try {
      setDetail(await adminAssetsApi.getAsset(record.id));
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setDetailLoading(false);
    }
  };

  const openOperation = (type: AssetOperationType) => {
    setOperationType(type);
    operationForm.resetFields();
  };

  const handleOperation = async () => {
    if (!detail || !operationType) return;
    try {
      const values = await operationForm.validateFields();
      setOperating(true);
      const payload = { ...values, version: detail.version };
      const updated = operationType === 'CLAIM'
        ? await adminAssetsApi.claimAsset(detail.id, payload)
        : operationType === 'RETURN'
          ? await adminAssetsApi.returnAsset(detail.id, payload)
          : await adminAssetsApi.transferAsset(detail.id, payload);
      setDetail(updated);
      setData((current) => current.map((item) => item.id === updated.id ? updated : item));
      setOperationType(undefined);
      message.success(t(`adminAssets.asset.operation.${operationType}.success`));
    } catch (err) {
      if (err instanceof Error && err.name === 'ValidationError') return;
      message.error(formatOaApiError(err));
    } finally {
      setOperating(false);
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const payload: AssetLedgerPayload = {
        ...values,
        purchaseDate: values.purchaseDate || undefined,
        version: editing?.version,
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
            <Button type="link" onClick={() => void openDetail(record)}>
              {t('adminAssets.asset.detail')}
            </Button>
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
          <Button type="link" onClick={() => void openDetail(record)}>
            {t('adminAssets.asset.detail')}
          </Button>
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
        <Form form={form} layout="vertical">
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
          {!editing && (
            <Form.Item name="departmentId" label={t('adminAssets.asset.department')}>
              <Select allowClear options={(overview?.departments || []).map((department) => ({
                value: department.id, label: department.name,
              }))} />
            </Form.Item>
          )}
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

      <Drawer
        title={detail ? `${detail.assetCode} · ${detail.name}` : t('adminAssets.asset.detail')}
        open={detail !== null}
        onClose={() => setDetail(null)}
        width={680}
      >
        <Spin spinning={detailLoading}>
          {detail && (
            <Space direction="vertical" size={20} style={{ width: '100%' }}>
              <Descriptions column={2} size="small" bordered>
                <Descriptions.Item label={t('adminAssets.asset.status')}>
                  <Tag color={STATUS_TAG_COLOR[detail.status]}>
                    {t(`adminAssets.asset.statusOption.${detail.status}`)}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label={t('adminAssets.asset.category')}>{detail.category}</Descriptions.Item>
                <Descriptions.Item label={t('adminAssets.asset.department')}>
                  {detail.departmentName || '-'}
                </Descriptions.Item>
                <Descriptions.Item label={t('adminAssets.asset.ownerName')}>
                  {detail.ownerName || '-'}
                </Descriptions.Item>
                <Descriptions.Item label={t('adminAssets.asset.specification')} span={2}>
                  {detail.specification || '-'}
                </Descriptions.Item>
              </Descriptions>
              {detail.canEdit && (
                <Space wrap>
                  <Button type="primary" disabled={detail.status !== 'IDLE'} onClick={() => openOperation('CLAIM')}>
                    {t('adminAssets.asset.operation.CLAIM.action')}
                  </Button>
                  <Button disabled={detail.status !== 'IN_USE'} onClick={() => openOperation('RETURN')}>
                    {t('adminAssets.asset.operation.RETURN.action')}
                  </Button>
                  <Button
                    disabled={!['IDLE', 'IN_USE'].includes(detail.status)}
                    onClick={() => openOperation('TRANSFER')}
                  >
                    {t('adminAssets.asset.operation.TRANSFER.action')}
                  </Button>
                </Space>
              )}
              <Typography.Title level={5}>{t('adminAssets.asset.history')}</Typography.Title>
              {detail.history.length ? (
                <Timeline items={detail.history.map((history) => ({
                  color: history.operationType === 'RETURN' ? 'gray' : 'blue',
                  children: (
                    <Space direction="vertical" size={2}>
                      <Space wrap>
                        <Tag>{t(`adminAssets.asset.operation.${history.operationType}.label`)}</Tag>
                        <Typography.Text type="secondary">{dayjs(history.createdAt).format('YYYY-MM-DD HH:mm')}</Typography.Text>
                      </Space>
                      <Typography.Text>
                        {t('adminAssets.asset.historyRoute', {
                          from: [history.fromDepartmentName, history.fromOwnerName].filter(Boolean).join(' · ') || '-',
                          to: [history.toDepartmentName, history.toOwnerName].filter(Boolean).join(' · ') || '-',
                        })}
                      </Typography.Text>
                      <Typography.Text type="secondary">
                        {history.reason || '-'} · {history.operatorName || '-'}
                      </Typography.Text>
                    </Space>
                  ),
                }))} />
              ) : <Empty description={t('adminAssets.asset.historyEmpty')} />}
            </Space>
          )}
        </Spin>
      </Drawer>

      <Modal
        title={operationType ? t(`adminAssets.asset.operation.${operationType}.title`) : ''}
        open={operationType !== undefined}
        onCancel={() => setOperationType(undefined)}
        onOk={() => void handleOperation()}
        confirmLoading={operating}
        destroyOnClose
      >
        <Form form={operationForm} layout="vertical">
          {operationType !== 'RETURN' && (
            <Form.Item
              name="targetDepartmentId"
              label={t('adminAssets.asset.targetDepartment')}
              rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
            >
              <Select options={(overview?.departments || []).filter((department) =>
                operationType !== 'TRANSFER' || department.id !== detail?.departmentId)
                .map((department) => ({ value: department.id, label: department.name }))} />
            </Form.Item>
          )}
          {(operationType === 'CLAIM' || (operationType === 'TRANSFER' && detail?.status === 'IN_USE')) && (
            <Form.Item
              name="targetOwnerUserId"
              label={t('adminAssets.asset.targetOwner')}
              rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
            >
              <Select showSearch optionFilterProp="label" options={(overview?.employees || [])
                .filter((employee) => employee.status === 1 && employee.departmentId === targetDepartmentId)
                .map((employee) => ({ value: employee.id, label: `${employee.name} · ${employee.email}` }))} />
            </Form.Item>
          )}
          <Form.Item name="reason" label={t('adminAssets.asset.operationReason')}>
            <Input.TextArea maxLength={500} showCount rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </AdminAssetsPageShell>
  );
}

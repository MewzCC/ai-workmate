'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
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
  Tag,
  Timeline,
  Typography,
} from 'antd';
import { message } from '@/lib/antdMessage';
import { useTranslation } from 'react-i18next';
import i18n from '@/i18n';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import {
  adminAssetsApi,
  type AssetLedger,
  type AssetLedgerPayload,
  type AssetInventoryResult,
  type AssetOperationPayload,
  type AssetOperationType,
  type AssetStatus,
  type AssetAssignmentOption,
} from '@/lib/adminAssetsApi';
import { collectPagedRecords } from '@/lib/collectPagedRecords';
import { AuthResponseScopeError } from '@/lib/authEvents';
import { parseRecordId } from '@/lib/recordSelection';
import { formatOaApiError } from '@/lib/oaApi';
import { usePermission } from '@/hooks/usePermission';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import AdminAssetsPageShell from './AdminAssetsPageShell';
import ResponsiveTable from './ResponsiveTable';
import { usePublishPageAgentContext } from './PageAgentContext';
import { useAuth } from '@/components/auth/AuthProvider';
import { useAsyncActionGuard } from '@/hooks/useAsyncActionGuard';
import { isFormValidationError, useAsyncFormAction } from '@/hooks/useAsyncFormAction';

const STATUS_TAG_COLOR: Record<AssetStatus, string> = {
  IN_USE: 'success',
  IDLE: 'default',
  REPAIRING: 'warning',
  SCRAPPED: 'error',
};

interface AssetFormValues extends Omit<AssetLedgerPayload, 'purchaseDate'> {
  purchaseDate?: string | null;
}

type OperationFormValues = Omit<AssetOperationPayload, 'version'> & {
  inventoryResult?: AssetInventoryResult;
  actualStatus?: AssetStatus;
  actualDepartmentId?: number;
  actualOwnerUserId?: number;
};

export default function AssetLedgerPage() {
  const { t } = useTranslation();
  const { allowed: canManage, permissionVersion } = usePermission('asset:write');
  const { allowed: canRead } = usePermission('assets:read');
  const { user } = useAuth();
  const actorScope = `${user?.tenantId}:${user?.id}:${permissionVersion}:${canRead}:${canManage}`;
  const { capture: capturePage } = useAsyncActionGuard(actorScope);
  const editorAction = useAsyncFormAction(actorScope);
  const operationAction = useAsyncFormAction(actorScope);
  const resetEditor = editorAction.reset, resetOperation = operationAction.reset;
  const router = useRouter();
  const requestedId = useSearchParams().get('id');
  const detailRequest = useRef(0);
  const listRequest = useRef(0);
  const [data, setData] = useState<AssetLedger[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState<string | undefined>(undefined);
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [applied, setApplied] = useState<{ scope: string; keyword: string; category?: string; status?: string; page: number; size: number }>({
    scope: actorScope, keyword: '', page: 1, size: 10,
  });

  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<AssetLedger | null>(null);
  const submitting = editorAction.busy;
  const [form] = Form.useForm<AssetFormValues>();
  const [operationForm] = Form.useForm<OperationFormValues>();
  const [departmentOptions, setDepartments] = useState<{ scope: string; rows: AssetAssignmentOption[] }>();
  const departments = departmentOptions?.scope === actorScope ? departmentOptions.rows : [];
  const [custodianOptions, setCustodians] = useState<{ scope: string; rows: AssetAssignmentOption[] }>();
  const [optionsLoading, setOptionsLoading] = useState(false);
  const [detail, setDetail] = useState<AssetLedger | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [operationType, setOperationType] = useState<AssetOperationType>();
  const operating = operationAction.busy;
  const targetDepartmentId = Form.useWatch('targetDepartmentId', operationForm);
  const actualDepartmentId = Form.useWatch('actualDepartmentId', operationForm);
  const inventoryResult = Form.useWatch('inventoryResult', operationForm);

  const selectedAsset = editOpen ? editing : detail;
  usePublishPageAgentContext({
    assetId: selectedAsset?.id,
    version: selectedAsset?.version,
    keyword: applied.scope === actorScope ? applied.keyword || undefined : undefined,
    category: applied.scope === actorScope ? applied.category : undefined,
    status: applied.scope === actorScope ? applied.status : undefined,
    page: applied.scope === actorScope ? applied.page : 1,
    size: applied.scope === actorScope ? applied.size : 10,
  });

  const load = useCallback(async (p = page, s = size, filters?: { keyword: string; category?: string; status?: string }) => {
    const isCurrent = capturePage();
    if (!isCurrent()) return;
    if (!user?.id || !canRead) { setLoading(false); return; }
    const request = ++listRequest.current;
    const draft = filters ?? { keyword, category, status };
    const query = { keyword: draft.keyword.trim(), category: draft.category || undefined, status: draft.status || undefined };
    setLoading(true);
    try {
      const res = await adminAssetsApi.listAssets({
        keyword: query.keyword || undefined,
        category: query.category || undefined,
        status: query.status as AssetStatus | undefined,
        page: p,
        size: s,
      });
      if (request !== listRequest.current || !isCurrent()) return;
      setData(res.records);
      setTotal(res.total);
      setApplied({ ...query, scope: actorScope, page: p, size: s });
    } catch (err) {
      if (request === listRequest.current && isCurrent()) { setData([]); setTotal(0); message.error(formatOaApiError(err)); }
    } finally {
      if (request === listRequest.current && isCurrent()) setLoading(false);
    }
  }, [keyword, category, status, page, size, capturePage, actorScope, user?.id, canRead]);
  const latestLoad = useRef(load); latestLoad.current = load;
  const latestRefresh = useRef(() => load());
  latestRefresh.current = () => applied.scope === actorScope ? load(applied.page, applied.size, applied) : load(1, 10, { keyword: '' });

  useEffect(() => {
    resetEditor(); resetOperation();
    ++detailRequest.current;
    setData([]); setTotal(0); setDetail(null); setDetailLoading(false);
    setEditing(null); setEditOpen(false); setOperationType(undefined);
    form.resetFields(); operationForm.resetFields();
    setKeyword(''); setCategory(undefined); setStatus(undefined); setPage(1); setSize(10);
    void latestLoad.current(1, 10, { keyword: '' });
  }, [actorScope, resetEditor, resetOperation, form, operationForm]);

  useEffect(() => {
    let current = true;
    const isActorCurrent = capturePage();
    setDepartments(undefined);
    if (canManage) adminAssetsApi.assetDepartments().then((rows) => {
      if (current && isActorCurrent()) setDepartments({ scope: actorScope, rows });
    }).catch((error) => { if (current && isActorCurrent()) message.error(formatOaApiError(error)); });
    return () => { current = false; };
  }, [canManage, actorScope, capturePage]);

  const assignmentDepartmentId = operationType === 'INVENTORY' ? actualDepartmentId : targetDepartmentId;
  const assignmentScope = `${actorScope}:${operationType}:${assignmentDepartmentId}`;
  const custodians = custodianOptions?.scope === assignmentScope ? custodianOptions.rows : [];
  useEffect(() => {
    let active = true;
    const isActorCurrent = capturePage();
    const current = () => active && isActorCurrent();
    setCustodians(undefined);
    setOptionsLoading(false);
    if (canManage && operationType && assignmentDepartmentId) {
      setOptionsLoading(true);
      collectPagedRecords((page, size) => {
        if (!current()) throw new AuthResponseScopeError();
        return adminAssetsApi.assetCustodians(assignmentDepartmentId, page, size);
      })
        .then((rows) => { if (current()) setCustodians({ scope: assignmentScope, rows }); })
        .catch((error) => { if (current()) message.error(formatOaApiError(error)); })
        .finally(() => { if (current()) setOptionsLoading(false); });
    }
    return () => { active = false; };
  }, [canManage, actorScope, operationType, assignmentDepartmentId, assignmentScope, capturePage]);

  const handleSearch = () => {
    setPage(1);
    load(1);
  };

  const openCreate = () => {
    if (!canManage || !capturePage()()) return;
    resetEditor();
    setEditing(null);
    form.resetFields();
    setEditOpen(true);
  };

  const openEdit = (record: AssetLedger) => {
    if (!canManage || !record.canEdit || !capturePage()()) return;
    resetEditor();
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

  const loadDetail = useCallback(async (id: number) => {
    const isCurrent = capturePage();
    if (!isCurrent() || !canRead) return;
    resetOperation();
    const request = ++detailRequest.current;
    setDetail(null);
    setOperationType(undefined);
    setDetailLoading(true);
    try {
      const result = await adminAssetsApi.getAsset(id);
      if (request === detailRequest.current && isCurrent()) {
        if (result.id !== id) throw new Error(i18n.t('adminAssets.asset.detailLoadFailed'));
        setDetail(result);
      }
    } catch (err) {
      if (request === detailRequest.current && isCurrent()) message.error(formatOaApiError(err));
    } finally {
      if (request === detailRequest.current && isCurrent()) setDetailLoading(false);
    }
  }, [capturePage, resetOperation, canRead]);

  useEffect(() => {
    const sequence = detailRequest;
    const id = parseRecordId(requestedId);
    setDetail(null);
    setDetailLoading(false);
    if (id) void loadDetail(id);
    return () => { ++sequence.current; };
  }, [requestedId, permissionVersion, loadDetail]);

  const openDetail = (record: AssetLedger) => loadDetail(record.id);

  const closeDetail = () => {
    resetOperation();
    ++detailRequest.current;
    setDetail(null);
    setDetailLoading(false);
    setOperationType(undefined);
    if (requestedId) router.replace('/oa/asset-ledger');
  };

  const openOperation = (type: AssetOperationType) => {
    if (!canManage || !detail?.canEdit || !capturePage()()) return;
    resetOperation();
    setOperationType(type);
    operationForm.resetFields();
    if (type === 'INVENTORY' && detail) {
      operationForm.setFieldsValue({
        inventoryResult: 'MATCH',
        actualStatus: detail.status,
        actualDepartmentId: detail.departmentId || undefined,
        actualOwnerUserId: detail.ownerUserId || undefined,
      });
    }
  };

  const handleOperation = async () => {
    if (!canManage || !detail?.canEdit || !operationType || operating) return;
    const action = operationAction.begin();
    if (!action) return;
    const isCurrent = action.current;
    const isActorCurrent = capturePage();
    const request = detailRequest.current;
    try {
      const values = await operationForm.validateFields();
      if (request !== detailRequest.current || !isCurrent()) return;
      const payload = { ...values, version: detail.version };
      let updated: AssetLedger;
      switch (operationType) {
        case 'CLAIM': updated = await adminAssetsApi.claimAsset(detail.id, payload); break;
        case 'RETURN': updated = await adminAssetsApi.returnAsset(detail.id, payload); break;
        case 'TRANSFER': updated = await adminAssetsApi.transferAsset(detail.id, payload); break;
        case 'REPAIR_START': updated = await adminAssetsApi.startAssetRepair(detail.id, {
          version: detail.version, reason: values.reason || '',
        }); break;
        case 'REPAIR_COMPLETE': updated = await adminAssetsApi.completeAssetRepair(detail.id, {
          version: detail.version, reason: values.reason || '',
        }); break;
        case 'INVENTORY': updated = await adminAssetsApi.inventoryAsset(detail.id, {
          version: detail.version,
          inventoryResult: values.inventoryResult as AssetInventoryResult,
          actualStatus: values.actualStatus,
          actualDepartmentId: values.actualDepartmentId,
          actualOwnerUserId: values.actualOwnerUserId,
          reason: values.reason,
        }); break;
        case 'SCRAP': updated = await adminAssetsApi.scrapAsset(detail.id, {
          version: detail.version, reason: values.reason || '',
        }); break;
      }
      if (request === detailRequest.current && isCurrent()) {
        setDetail(updated);
        setData((current) => current.map((item) => item.id === updated.id ? updated : item));
        setOperationType(undefined);
        message.success(t(`adminAssets.asset.operation.${operationType}.success`));
      }
      if (isActorCurrent()) await latestRefresh.current();
    } catch (err) {
      if (!isCurrent() || request !== detailRequest.current) return;
      if (isFormValidationError(err)) return;
      message.error(formatOaApiError(err));
    } finally {
      action.finish();
    }
  };

  const handleSubmit = async () => {
    if (!canManage || submitting) return;
    const action = editorAction.begin();
    if (!action) return;
    const isCurrent = action.current;
    const isActorCurrent = capturePage();
    try {
      const values = await form.validateFields();
      if (!isCurrent()) return;
      const payload: AssetLedgerPayload = {
        ...values,
        purchaseDate: values.purchaseDate || undefined,
        version: editing?.version,
      };
      if (editing) {
        await adminAssetsApi.updateAsset(editing.id, payload);
      } else {
        await adminAssetsApi.createAsset(payload);
      }
      if (isCurrent()) {
        message.success(t(editing ? 'adminAssets.asset.updateSuccess' : 'adminAssets.asset.createSuccess'));
        setEditOpen(false);
      }
      if (isActorCurrent()) await latestRefresh.current();
    } catch (err) {
      if (!isCurrent()) return;
      if (isFormValidationError(err)) return;
      message.error(formatOaApiError(err));
    } finally {
      action.finish();
    }
  };

  const handleDelete = async (id: number) => {
    if (!canManage) return;
    const isCurrent = capturePage();
    if (!isCurrent()) return;
    try {
      await adminAssetsApi.deleteAsset(id);
      if (!isCurrent()) return;
      message.success(t('adminAssets.asset.deleteSuccess'));
      await latestRefresh.current();
    } catch (err) {
      if (isCurrent()) message.error(formatOaApiError(err));
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
        canManage && record.canEdit ? (
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
      actions={canManage ? (
        <Button type="primary" onClick={openCreate}>
          {t('adminAssets.asset.create')}
        </Button>
      ) : null}
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
                { value: 'IT设备', label: t('adminAssets.asset.categoryOption.it') },
                { value: '办公家具', label: t('adminAssets.asset.categoryOption.furniture') },
                { value: '车辆', label: t('adminAssets.asset.categoryOption.vehicle') },
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

          <ResponsiveTable
            rowKey="id"
            columns={columns}
            dataSource={applied.scope === actorScope ? data : []}
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
                load(p, s, applied);
              },
            }}
            locale={{ emptyText: <Empty description={t('adminAssets.common.noData')} /> }}
          />
        </Card>
      </Spin>

      <Modal
        title={editing ? t('adminAssets.asset.edit') : t('adminAssets.asset.create')}
        open={editOpen && canManage}
        onCancel={() => { resetEditor(); setEditOpen(false); }}
        onOk={handleSubmit}
        okButtonProps={{ disabled: !canManage, loading: submitting }}
        cancelText={t(submitting ? 'common.close' : 'common.cancel')}
        okText={t('adminAssets.common.save')}
        destroyOnClose
        width={560}
      >
        <Form form={form} disabled={submitting} layout="vertical">
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
              <Select allowClear options={departments.map((department) => ({
                value: department.id, label: department.name,
              }))} />
            </Form.Item>
          )}
          <Form.Item name="purchaseDate" label={t('adminAssets.asset.purchaseDate')}>
            <Input placeholder={t('adminAssets.asset.purchaseDatePlaceholder')} />
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
        open={detail !== null || detailLoading}
        onClose={closeDetail}
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
              {canManage && detail.canEdit && (
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
                  <Button disabled={detail.status !== 'IDLE'} onClick={() => openOperation('REPAIR_START')}>
                    {t('adminAssets.asset.operation.REPAIR_START.action')}
                  </Button>
                  <Button disabled={detail.status !== 'REPAIRING'} onClick={() => openOperation('REPAIR_COMPLETE')}>
                    {t('adminAssets.asset.operation.REPAIR_COMPLETE.action')}
                  </Button>
                  <Button disabled={detail.status === 'SCRAPPED'} onClick={() => openOperation('INVENTORY')}>
                    {t('adminAssets.asset.operation.INVENTORY.action')}
                  </Button>
                  <Button
                    danger
                    disabled={!['IDLE', 'REPAIRING'].includes(detail.status)}
                    onClick={() => openOperation('SCRAP')}
                  >
                    {t('adminAssets.asset.operation.SCRAP.action')}
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
                        {history.operationType === 'INVENTORY'
                          ? t('adminAssets.asset.historyInventory', {
                            result: t(`adminAssets.asset.inventoryResult.${history.inventoryResult}`),
                            status: history.actualStatus
                              ? t(`adminAssets.asset.statusOption.${history.actualStatus}`) : '-',
                            location: [history.actualDepartmentName, history.actualOwnerName]
                              .filter(Boolean).join(' · ') || '-',
                          })
                          : t('adminAssets.asset.historyRoute', {
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
        open={operationType !== undefined && canManage}
        onCancel={() => { resetOperation(); setOperationType(undefined); }}
        onOk={() => void handleOperation()}
        okButtonProps={{ disabled: !canManage, loading: operating }}
        cancelText={t(operating ? 'common.close' : 'common.cancel')}
        destroyOnClose
      >
        <Form form={operationForm} disabled={operating} layout="vertical">
          {(operationType === 'CLAIM' || operationType === 'TRANSFER') && (
            <Form.Item
              name="targetDepartmentId"
              label={t('adminAssets.asset.targetDepartment')}
              rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
            >
              <Select onChange={() => operationForm.setFieldValue('targetOwnerUserId', undefined)} options={departments.filter((department) =>
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
              <Select loading={optionsLoading} showSearch optionFilterProp="label" options={custodians
                .filter((employee) => employee.departmentId === targetDepartmentId)
                .map((employee) => ({ value: employee.id, label: employee.name }))} />
            </Form.Item>
          )}
          {operationType === 'INVENTORY' && (
            <>
              <Form.Item
                name="inventoryResult"
                label={t('adminAssets.asset.inventoryResultLabel')}
                rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
              >
                <Select options={[
                  'MATCH', 'MISSING', 'DAMAGED', 'LOCATION_MISMATCH', 'CUSTODIAN_MISMATCH',
                ].map((value) => ({
                  value, label: t(`adminAssets.asset.inventoryResult.${value}`),
                }))} />
              </Form.Item>
              <Form.Item name="actualStatus" label={t('adminAssets.asset.actualStatus')}>
                <Select allowClear options={Object.keys(STATUS_TAG_COLOR).map((value) => ({
                  value, label: t(`adminAssets.asset.statusOption.${value}`),
                }))} />
              </Form.Item>
              <Form.Item name="actualDepartmentId" label={t('adminAssets.asset.actualDepartment')}>
                <Select allowClear onChange={() => operationForm.setFieldValue('actualOwnerUserId', undefined)} options={departments.map((department) => ({
                  value: department.id, label: department.name,
                }))} />
              </Form.Item>
              <Form.Item name="actualOwnerUserId" label={t('adminAssets.asset.actualOwner')}>
                <Select loading={optionsLoading} allowClear showSearch optionFilterProp="label" options={custodians
                  .filter((employee) => employee.departmentId === actualDepartmentId)
                  .map((employee) => ({ value: employee.id, label: employee.name }))} />
              </Form.Item>
            </>
          )}
          <Form.Item
            name="reason"
            label={t('adminAssets.asset.operationReason')}
            rules={[{
              required: ['REPAIR_START', 'REPAIR_COMPLETE', 'SCRAP'].includes(operationType || '')
                || (operationType === 'INVENTORY' && inventoryResult !== 'MATCH'),
              message: t('adminAssets.common.fieldRequired'),
            }]}
          >
            <Input.TextArea maxLength={500} showCount rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </AdminAssetsPageShell>
  );
}

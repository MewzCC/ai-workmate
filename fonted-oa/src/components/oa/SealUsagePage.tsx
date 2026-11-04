'use client';

import { useEffect, useRef, useState } from 'react';
import {
  Button,
  Card,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Spin,
  Tabs,
  Tag,
  Upload,
} from 'antd';
import type { UploadProps } from 'antd';
import { message } from '@/lib/antdMessage';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import {
  adminAssetsApi,
  type SealType,
  type SealUsage,
  type SealUsagePayload,
  type SealUsageStatus,
  type SealUsageDocument,
} from '@/lib/adminAssetsApi';
import { formatOaApiError } from '@/lib/oaApi';
import { useAdminAssetApprovalQueue } from '@/hooks/useAdminAssetApprovalQueue';
import { usePageUiEditorCommands } from '@/hooks/usePageUiEditorCommands';
import { usePageUiFormContext } from '@/hooks/usePageUiFormContext';
import { isFormValidationError, useAsyncFormAction } from '@/hooks/useAsyncFormAction';
import { useAsyncActionGuard } from '@/hooks/useAsyncActionGuard';
import { useScopedModalConfirm } from '@/hooks/useScopedModalConfirm';
import { usePublishPageAgentContext } from './PageAgentContext';
import AdminAssetsPageShell from './AdminAssetsPageShell';
import ResponsiveTable from './ResponsiveTable';

const STATUS_TAG_COLOR: Record<SealUsageStatus, string> = {
  PENDING: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
  WITHDRAWN: 'default',
  USED: 'processing',
  RETURNED: 'default',
};

const SEAL_TYPE_OPTIONS: SealType[] = ['OFFICIAL', 'CONTRACT', 'LEGAL', 'FINANCE', 'OTHER'];

export default function SealUsagePage() {
  const { t } = useTranslation();
  const { activeTab, mine, pending, loading, total, page, size, setPage, setSize,
    loadMine, refresh, captureScope, handleTabChange, applyFilters, status, changeStatus, canCreate, canDecide, scope } = useAdminAssetApprovalQueue({
    pageId: 'seal-usage', readPermission: 'seal:read:self', createPermission: 'seal:create',
    statuses: Object.keys(STATUS_TAG_COLOR) as SealUsageStatus[],
    mine: adminAssetsApi.listMySealUsages, pending: adminAssetsApi.listPendingSealUsages,
  });

  const [createOpen, setCreateOpen] = useState(false);
  const createAction = useAsyncFormAction(scope);
  const confirm = useScopedModalConfirm(scope);
  const submitting = createAction.busy;
  const [form] = Form.useForm<SealUsagePayload>();
  const uiForm = usePageUiFormContext(createOpen && canCreate && !submitting, undefined, undefined, scope);
  const openCreate = () => { if (!canCreate || !captureScope()()) return; createAction.reset(); uiForm.renew(); form.resetFields(); setCreateOpen(true); };
  usePublishPageAgentContext({ queue: activeTab.toUpperCase(), status, page, size });
  usePageUiEditorCommands({ form, open: createOpen && !submitting, canEdit: canCreate, context: uiForm.context,
    create: openCreate, fields: { sealType: SEAL_TYPE_OPTIONS, documentTitle: 'string', usageReason: 'string', copies: 'number' },
    labels: Object.fromEntries(['sealType', 'documentTitle', 'usageReason', 'copies'].map(name => [name, t(`adminAssets.seal.${name}`)])),
    commands: { 'ui.applyFilter': ({ payload: { filters } }) => applyFilters(filters),
      'ui.refreshPage': refresh },
  });

  const [decideTarget, setDecideTarget] = useState<SealUsage | null>(null);
  const [decideOpen, setDecideOpen] = useState(false);
  const decideAction = useAsyncFormAction(scope);
  const deciding = decideAction.busy;
  const [decideForm] = Form.useForm<{ comment?: string }>();
  const [useTarget, setUseTarget] = useState<SealUsage | null>(null);
  const [useOpen, setUseOpen] = useState(false);
  const useAction = useAsyncFormAction(scope);
  const usingSeal = useAction.busy;
  const [useForm] = Form.useForm<{ actualCopies: number; remark?: string }>();
  const [documentTarget, setDocumentTarget] = useState<SealUsage | null>(null);
  const [documentOpen, setDocumentOpen] = useState(false);
  const [documents, setDocuments] = useState<SealUsageDocument[]>([]);
  const [documentLoading, setDocumentLoading] = useState(false);
  const { capture: captureDocuments, invalidate: invalidateDocuments } = useAsyncActionGuard(scope);
  const uploadAction = useAsyncFormAction(scope);
  const documentRequest = useRef(0);

  useEffect(() => {
    invalidateDocuments(); ++documentRequest.current;
    setCreateOpen(false); setDecideOpen(false); setDecideTarget(null);
    setUseOpen(false); setUseTarget(null); setDocumentOpen(false); setDocumentTarget(null); setDocuments([]);
    setDocumentLoading(false);
    form.resetFields(); decideForm.resetFields(); useForm.resetFields();
  }, [scope, form, decideForm, useForm, invalidateDocuments]);

  const handleCreate = async () => {
    if (!canCreate || submitting) return;
    const action = createAction.begin();
    if (!action) return;
    const current = captureScope();
    try {
      const values = await form.validateFields();
      if (!action.current()) return;
      await adminAssetsApi.submitSealUsage(values);
      if (action.current()) {
        message.success(t('adminAssets.seal.submitSuccess'));
        setCreateOpen(false); form.resetFields();
      }
      if (current()) await refresh();
    } catch (err) {
      if (!action.current()) return;
      if (isFormValidationError(err)) return;
      message.error(formatOaApiError(err));
    } finally {
      action.finish();
    }
  };

  const handleWithdraw = async (record: SealUsage) => {
    const current = captureScope();
    if (!current() || !record.canWithdraw) return;
    try {
      await adminAssetsApi.withdrawSealUsage(record.id, { version: record.version });
      if (!current()) return;
      message.success(t('adminAssets.seal.withdrawSuccess'));
      await refresh();
    } catch (err) {
      if (current()) message.error(formatOaApiError(err));
    }
  };

  const openUse = (record: SealUsage) => {
    if (!captureScope()() || !record.canRegisterUse) return;
    useAction.reset();
    setUseTarget(record);
    useForm.setFieldsValue({ actualCopies: record.copies, remark: undefined });
    setUseOpen(true);
  };

  const handleUse = async () => {
    if (!useTarget?.canRegisterUse) return;
    const action = useAction.begin();
    if (!action) return;
    const current = captureScope();
    try {
      const values = await useForm.validateFields();
      if (!action.current()) return;
      await adminAssetsApi.registerSealUse(useTarget.id, {
        version: useTarget.version,
        actualCopies: values.actualCopies,
        remark: values.remark,
      });
      if (action.current()) {
        message.success(t('adminAssets.seal.execution.useSuccess'));
        setUseOpen(false); setUseTarget(null);
      }
      if (current()) await refresh();
    } catch (err) {
      if (!action.current()) return;
      if (isFormValidationError(err)) return;
      message.error(formatOaApiError(err));
    } finally {
      action.finish();
    }
  };

  const handleReturn = (record: SealUsage) => {
    const current = captureScope();
    if (!current() || !record.canReturn) return;
    confirm({
      title: t('adminAssets.seal.execution.returnConfirmTitle'),
      content: t('adminAssets.seal.execution.returnConfirmContent'),
      okText: t('adminAssets.seal.execution.return'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        if (!current()) return;
        try {
          await adminAssetsApi.returnSeal(record.id, { version: record.version });
          if (!current()) return;
          message.success(t('adminAssets.seal.execution.returnSuccess'));
          await refresh();
        } catch (err) {
          if (current()) message.error(formatOaApiError(err));
        }
      },
    });
  };

  const loadDocuments = async (record: SealUsage) => {
    const current = captureDocuments();
    if (!current()) return;
    const request = ++documentRequest.current;
    setDocumentLoading(true);
    try {
      const rows = await adminAssetsApi.listSealUsageDocuments(record.id);
      if (current() && request === documentRequest.current) setDocuments(rows);
    } catch (err) {
      if (current() && request === documentRequest.current) { setDocuments([]); message.error(formatOaApiError(err)); }
    } finally {
      if (current() && request === documentRequest.current) setDocumentLoading(false);
    }
  };

  const openDocuments = async (record: SealUsage) => {
    if (!captureScope()() || !record.canArchiveDocument) return;
    invalidateDocuments(); uploadAction.reset(); setDocuments([]);
    setDocumentTarget(record);
    setDocumentOpen(true);
    await loadDocuments(record);
  };

  const uploadDocument: UploadProps['customRequest'] = async (options) => {
    if (!documentTarget?.canArchiveDocument || !(options.file instanceof File)) return;
    const action = uploadAction.begin();
    if (!action) return;
    try {
      await adminAssetsApi.uploadSealUsageDocument(documentTarget.id, options.file, action.current);
      if (!action.current()) return;
      options.onSuccess?.({});
      message.success(t('adminAssets.seal.document.uploadSuccess'));
      await loadDocuments(documentTarget);
    } catch (err) {
      if (!action.current()) return;
      options.onError?.(err instanceof Error ? err : new Error(String(err)));
      message.error(formatOaApiError(err));
    } finally { action.finish(); }
  };

  const openDecide = (record: SealUsage) => {
    if (!canDecide || !record.canDecide || !captureScope()()) return;
    decideAction.reset();
    setDecideTarget(record);
    decideForm.resetFields();
    setDecideOpen(true);
  };

  const handleDecide = async (action: 'approve' | 'reject') => {
    if (!canDecide || deciding || !decideTarget || !decideTarget.taskId) return;
    const operation = decideAction.begin();
    if (!operation) return;
    const current = captureScope();
    try {
      const values = await decideForm.validateFields();
      if (!operation.current()) return;
      const payload = {
        version: decideTarget.taskVersion ?? 0,
        comment: values.comment,
      };
      if (action === 'approve') {
        await adminAssetsApi.approveSealUsage(decideTarget.taskId, payload);
      } else {
        await adminAssetsApi.rejectSealUsage(decideTarget.taskId, payload);
      }
      if (operation.current()) {
        message.success(t(`adminAssets.seal.${action === 'approve' ? 'approveSuccess' : 'rejectSuccess'}`));
        setDecideOpen(false); setDecideTarget(null); decideForm.resetFields();
      }
      if (current()) await refresh();
    } catch (err) {
      if (!operation.current()) return;
      if (isFormValidationError(err)) return;
      message.error(formatOaApiError(err));
    } finally {
      operation.finish();
    }
  };

  const mineColumns: ColumnsType<SealUsage> = [
    {
      title: t('adminAssets.seal.sealType'),
      dataIndex: 'sealType',
      key: 'sealType',
      render: (v: SealType) =>
        t(`adminAssets.seal.sealTypeOption.${v}`, { defaultValue: v }),
    },
    {
      title: t('adminAssets.seal.documentTitle'),
      dataIndex: 'documentTitle',
      key: 'documentTitle',
      ellipsis: true,
    },
    {
      title: t('adminAssets.seal.copies'),
      dataIndex: 'copies',
      key: 'copies',
    },
    {
      title: t('adminAssets.common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (s: SealUsageStatus) => (
        <Tag color={STATUS_TAG_COLOR[s]}>
          {t(`adminAssets.seal.status.${s}`, { defaultValue: s })}
        </Tag>
      ),
    },
    {
      title: t('adminAssets.common.submittedAt'),
      dataIndex: 'submittedAt',
      key: 'submittedAt',
      render: (v?: string | null) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: t('adminAssets.seal.execution.progress'),
      key: 'executionProgress',
      responsive: ['lg'],
      render: (_: unknown, record: SealUsage) => {
        const time = record.returnedAt || record.usedAt;
        return time ? (
          <Space direction="vertical" size={0}>
            <span>{record.handlerName || '-'}</span>
            <span>{dayjs(time).format('YYYY-MM-DD HH:mm')}</span>
            <span>{record.actualCopies ?? record.copies} {t('adminAssets.seal.execution.copyUnit')}</span>
          </Space>
        ) : '-';
      },
    },
    {
      title: t('adminAssets.common.action'),
      key: 'action',
      render: (_: unknown, record: SealUsage) => (
        <Space wrap>
          {record.canWithdraw && (
            <Button type="link" danger onClick={() => handleWithdraw(record)}>
              {t('adminAssets.common.withdraw')}
            </Button>
          )}
          {record.canRegisterUse && (
            <Button type="link" onClick={() => openUse(record)}>
              {t('adminAssets.seal.execution.use')}
            </Button>
          )}
          {record.canReturn && (
            <Button type="link" onClick={() => handleReturn(record)}>
              {t('adminAssets.seal.execution.return')}
            </Button>
          )}
          {record.canArchiveDocument && (
            <Button type="link" onClick={() => void openDocuments(record)}>
              {t('adminAssets.seal.document.archive')}
            </Button>
          )}
          {!record.canWithdraw && !record.canRegisterUse && !record.canReturn
            && !record.canArchiveDocument && '-'}
        </Space>
      ),
    },
  ];

  const pendingColumns: ColumnsType<SealUsage> = [
    {
      title: t('adminAssets.common.applicantName'),
      dataIndex: 'applicantName',
      key: 'applicantName',
      render: (v?: string | null) => v || '-',
    },
    {
      title: t('adminAssets.seal.sealType'),
      dataIndex: 'sealType',
      key: 'sealType',
      render: (v: SealType) =>
        t(`adminAssets.seal.sealTypeOption.${v}`, { defaultValue: v }),
    },
    {
      title: t('adminAssets.seal.documentTitle'),
      dataIndex: 'documentTitle',
      key: 'documentTitle',
      ellipsis: true,
    },
    {
      title: t('adminAssets.seal.copies'),
      dataIndex: 'copies',
      key: 'copies',
    },
    {
      title: t('adminAssets.common.submittedAt'),
      dataIndex: 'submittedAt',
      key: 'submittedAt',
      render: (v?: string | null) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: t('adminAssets.common.action'),
      key: 'action',
      render: (_: unknown, record: SealUsage) => (
        <Button type="link" onClick={() => openDecide(record)}>
          {t('adminAssets.common.decide')}
        </Button>
      ),
    },
  ];

  return (
    <AdminAssetsPageShell
      eyebrow={t('adminAssets.eyebrow')}
      title={t('adminAssets.seal.title')}
      description={t('adminAssets.seal.description')}
      actions={canCreate &&
        <Button type="primary" onClick={openCreate}>
          {t('adminAssets.seal.create')}
        </Button>
      }
    >
      <Spin spinning={loading}>
        <Card className="oa-admin-assets-card oa-admin-assets-card--fill" variant="outlined">
          {activeTab === 'mine' && <Select allowClear value={status} onChange={changeStatus} style={{ width: 180 }}
            aria-label={t('adminAssets.common.status')} placeholder={t('adminAssets.common.status')}
            options={(Object.keys(STATUS_TAG_COLOR) as SealUsageStatus[]).map(value => ({ value, label: t(`adminAssets.seal.status.${value}`) }))} />}
          <Tabs
            activeKey={activeTab}
            onChange={handleTabChange}
            items={[
              {
                key: 'mine',
                label: t('adminAssets.seal.myApplications'),
                children: (
                  <ResponsiveTable
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
                    locale={{ emptyText: <Empty description={t('adminAssets.common.noData')} /> }}
                  />
                ),
              },
              {
                key: 'pending',
                label: t('adminAssets.seal.pendingApproval'),
                children: (
                  <ResponsiveTable
                    rowKey="id"
                    columns={pendingColumns}
                    dataSource={pending}
                    size="middle"
                    pagination={false}
                    locale={{ emptyText: <Empty description={t('adminAssets.common.noData')} /> }}
                  />
                ),
              },
            ].filter(item => item.key !== 'pending' || canDecide)}
          />
        </Card>

        <Modal
          title={t('adminAssets.seal.create')}
          open={createOpen && canCreate}
          onCancel={() => { createAction.reset(); setCreateOpen(false); }}
          onOk={handleCreate}
          okButtonProps={{ loading: submitting }}
          cancelText={t(submitting ? 'common.close' : 'common.cancel')}
          okText={t('adminAssets.common.submit')}
          destroyOnClose
          width={520}
        >
          <Form form={form} disabled={submitting} layout="vertical" initialValues={{ sealType: 'OFFICIAL', copies: 1 }} onValuesChange={uiForm.renew}>
            <Form.Item name="sealType" label={t('adminAssets.seal.sealType')}>
              <Select
                options={SEAL_TYPE_OPTIONS.map((s) => ({
                  value: s,
                  label: t(`adminAssets.seal.sealTypeOption.${s}`, { defaultValue: s }),
                }))}
              />
            </Form.Item>
            <Form.Item
              name="documentTitle"
              label={t('adminAssets.seal.documentTitle')}
              rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
            >
              <Input maxLength={200} />
            </Form.Item>
            <Form.Item
              name="usageReason"
              label={t('adminAssets.seal.usageReason')}
              rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
            >
              <Input.TextArea rows={4} maxLength={500} showCount />
            </Form.Item>
            <Form.Item name="copies" label={t('adminAssets.seal.copies')}>
              <InputNumber style={{ width: '100%' }} min={1} />
            </Form.Item>
          </Form>
        </Modal>

        <Modal
          title={t('adminAssets.seal.decideTitle')}
          open={decideOpen && canDecide}
          onCancel={() => {
            decideAction.reset();
            setDecideOpen(false);
            setDecideTarget(null);
          }}
          footer={[
            <Button
              key="reject"
              danger
              loading={deciding}
              onClick={() => handleDecide('reject')}
            >
              {t('adminAssets.common.reject')}
            </Button>,
            <Button
              key="approve"
              type="primary"
              loading={deciding}
              onClick={() => handleDecide('approve')}
            >
              {t('adminAssets.common.approve')}
            </Button>,
          ]}
          destroyOnClose
        >
          {decideTarget && (
            <div style={{ marginBottom: 16 }}>
              <p>
                <strong>{t('adminAssets.seal.documentTitle')}：</strong>
                {decideTarget.documentTitle}
              </p>
              <p>
                <strong>{t('adminAssets.seal.usageReason')}：</strong>
                {decideTarget.usageReason}
              </p>
              <p>
                <strong>{t('adminAssets.seal.copies')}：</strong>
                {decideTarget.copies}
              </p>
            </div>
          )}
          <Form form={decideForm} disabled={deciding} layout="vertical">
            <Form.Item name="comment" label={t('adminAssets.common.approverComment')}>
              <Input.TextArea rows={3} maxLength={500} showCount />
            </Form.Item>
          </Form>
        </Modal>

        <Modal
          title={t('adminAssets.seal.execution.useTitle')}
          open={useOpen}
          onCancel={() => {
            useAction.reset();
            setUseOpen(false);
            setUseTarget(null);
          }}
          onOk={() => void handleUse()}
          okButtonProps={{ loading: usingSeal }}
          cancelText={t(usingSeal ? 'common.close' : 'common.cancel')}
          okText={t('adminAssets.seal.execution.use')}
          destroyOnClose
        >
          <Form form={useForm} disabled={usingSeal} layout="vertical">
            <Form.Item
              name="actualCopies"
              label={t('adminAssets.seal.execution.actualCopies')}
              rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
            >
              <InputNumber min={1} max={useTarget?.copies} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="remark" label={t('adminAssets.seal.execution.remark')}>
              <Input.TextArea rows={3} maxLength={500} showCount />
            </Form.Item>
          </Form>
        </Modal>

        <Modal
          title={t('adminAssets.seal.document.title')}
          open={documentOpen}
          onCancel={() => {
            invalidateDocuments(); uploadAction.reset(); ++documentRequest.current; setDocumentLoading(false);
            setDocumentOpen(false);
            setDocumentTarget(null);
            setDocuments([]);
          }}
          footer={null}
          width={640}
          destroyOnClose
        >
          <Upload customRequest={uploadDocument} showUploadList={false} maxCount={1} disabled={uploadAction.busy}>
            <Button loading={uploadAction.busy}>{t('adminAssets.seal.document.upload')}</Button>
          </Upload>
          <Spin spinning={documentLoading}>
            <ResponsiveTable
              style={{ marginTop: 16 }}
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={documents}
              locale={{ emptyText: <Empty description={t('adminAssets.seal.document.empty')} /> }}
              columns={[
                { title: t('adminAssets.seal.document.fileName'), dataIndex: 'displayName' },
                {
                  title: t('adminAssets.seal.document.uploadedAt'), dataIndex: 'createdAt',
                  render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm'),
                },
                {
                  title: t('adminAssets.common.action'), key: 'action',
                  render: (_: unknown, document: SealUsageDocument) => (
                    <Button
                      type="link"
                      onClick={() => {
                        const current = captureDocuments();
                        if (current()) void adminAssetsApi.downloadSealUsageDocument(document, current)
                          .catch((err) => { if (current()) message.error(formatOaApiError(err)); });
                      }}
                    >
                      {t('adminAssets.seal.document.download')}
                    </Button>
                  ),
                },
              ]}
            />
          </Spin>
        </Modal>
      </Spin>
    </AdminAssetsPageShell>
  );
}

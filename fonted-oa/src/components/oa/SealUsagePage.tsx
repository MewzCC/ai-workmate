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
  Select,
  Space,
  Spin,
  Table,
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
import AdminAssetsPageShell from './AdminAssetsPageShell';

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
  const [activeTab, setActiveTab] = useState('mine');
  const [mine, setMine] = useState<SealUsage[]>([]);
  const [pending, setPending] = useState<SealUsage[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);

  const [createOpen, setCreateOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<SealUsagePayload>();

  const [decideTarget, setDecideTarget] = useState<SealUsage | null>(null);
  const [decideOpen, setDecideOpen] = useState(false);
  const [deciding, setDeciding] = useState(false);
  const [decideForm] = Form.useForm<{ comment?: string }>();
  const [useTarget, setUseTarget] = useState<SealUsage | null>(null);
  const [useOpen, setUseOpen] = useState(false);
  const [usingSeal, setUsingSeal] = useState(false);
  const [useForm] = Form.useForm<{ actualCopies: number; remark?: string }>();
  const [documentTarget, setDocumentTarget] = useState<SealUsage | null>(null);
  const [documentOpen, setDocumentOpen] = useState(false);
  const [documents, setDocuments] = useState<SealUsageDocument[]>([]);
  const [documentLoading, setDocumentLoading] = useState(false);

  const loadMine = useCallback(async (p = page, s = size) => {
    setLoading(true);
    try {
      const res = await adminAssetsApi.listMySealUsages({ page: p, size: s });
      setMine(res.records);
      setTotal(res.total);
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setLoading(false);
    }
  }, [page, size]);

  const loadPending = useCallback(async () => {
    setLoading(true);
    try {
      const res = await adminAssetsApi.listPendingSealUsages({ page: 1, size: 100 });
      setPending(res.records);
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadMine(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleTabChange = (key: string) => {
    setActiveTab(key);
    if (key === 'pending') {
      loadPending();
    } else {
      loadMine(1);
    }
  };

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      await adminAssetsApi.submitSealUsage(values);
      message.success(t('adminAssets.seal.submitSuccess'));
      setCreateOpen(false);
      form.resetFields();
      await loadMine(1);
    } catch (err) {
      if (err instanceof Error && err.name === 'ValidationError') return;
      message.error(formatOaApiError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleWithdraw = async (record: SealUsage) => {
    try {
      await adminAssetsApi.withdrawSealUsage(record.id, { version: record.version });
      message.success(t('adminAssets.seal.withdrawSuccess'));
      await loadMine(page);
    } catch (err) {
      message.error(formatOaApiError(err));
    }
  };

  const openUse = (record: SealUsage) => {
    setUseTarget(record);
    useForm.setFieldsValue({ actualCopies: record.copies, remark: undefined });
    setUseOpen(true);
  };

  const handleUse = async () => {
    if (!useTarget) return;
    try {
      const values = await useForm.validateFields();
      setUsingSeal(true);
      await adminAssetsApi.registerSealUse(useTarget.id, {
        version: useTarget.version,
        actualCopies: values.actualCopies,
        remark: values.remark,
      });
      message.success(t('adminAssets.seal.execution.useSuccess'));
      setUseOpen(false);
      setUseTarget(null);
      await loadMine(page);
    } catch (err) {
      if (err instanceof Error && err.name === 'ValidationError') return;
      message.error(formatOaApiError(err));
    } finally {
      setUsingSeal(false);
    }
  };

  const handleReturn = (record: SealUsage) => {
    Modal.confirm({
      title: t('adminAssets.seal.execution.returnConfirmTitle'),
      content: t('adminAssets.seal.execution.returnConfirmContent'),
      okText: t('adminAssets.seal.execution.return'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await adminAssetsApi.returnSeal(record.id, { version: record.version });
          message.success(t('adminAssets.seal.execution.returnSuccess'));
          await loadMine(page);
        } catch (err) {
          message.error(formatOaApiError(err));
        }
      },
    });
  };

  const loadDocuments = async (record: SealUsage) => {
    setDocumentLoading(true);
    try {
      setDocuments(await adminAssetsApi.listSealUsageDocuments(record.id));
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setDocumentLoading(false);
    }
  };

  const openDocuments = async (record: SealUsage) => {
    setDocumentTarget(record);
    setDocumentOpen(true);
    await loadDocuments(record);
  };

  const uploadDocument: UploadProps['customRequest'] = async (options) => {
    if (!documentTarget || !(options.file instanceof File)) return;
    try {
      await adminAssetsApi.uploadSealUsageDocument(documentTarget.id, options.file);
      options.onSuccess?.({});
      message.success(t('adminAssets.seal.document.uploadSuccess'));
      await loadDocuments(documentTarget);
    } catch (err) {
      options.onError?.(err instanceof Error ? err : new Error(String(err)));
      message.error(formatOaApiError(err));
    }
  };

  const openDecide = (record: SealUsage) => {
    setDecideTarget(record);
    decideForm.resetFields();
    setDecideOpen(true);
  };

  const handleDecide = async (action: 'approve' | 'reject') => {
    if (!decideTarget || !decideTarget.taskId) return;
    try {
      const values = await decideForm.validateFields();
      setDeciding(true);
      const payload = {
        version: decideTarget.taskVersion ?? 0,
        comment: values.comment,
      };
      if (action === 'approve') {
        await adminAssetsApi.approveSealUsage(decideTarget.taskId, payload);
        message.success(t('adminAssets.seal.approveSuccess'));
      } else {
        await adminAssetsApi.rejectSealUsage(decideTarget.taskId, payload);
        message.success(t('adminAssets.seal.rejectSuccess'));
      }
      setDecideOpen(false);
      setDecideTarget(null);
      decideForm.resetFields();
      await loadPending();
    } catch (err) {
      if (err instanceof Error && err.name === 'ValidationError') return;
      message.error(formatOaApiError(err));
    } finally {
      setDeciding(false);
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
      actions={
        <Button type="primary" onClick={() => setCreateOpen(true)}>
          {t('adminAssets.seal.create')}
        </Button>
      }
    >
      <Spin spinning={loading}>
        <Card className="oa-admin-assets-card oa-admin-assets-card--fill" variant="outlined">
          <Tabs
            activeKey={activeTab}
            onChange={handleTabChange}
            items={[
              {
                key: 'mine',
                label: t('adminAssets.seal.myApplications'),
                children: (
                  <Table
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
                  <Table
                    rowKey="id"
                    columns={pendingColumns}
                    dataSource={pending}
                    size="middle"
                    pagination={false}
                    locale={{ emptyText: <Empty description={t('adminAssets.common.noData')} /> }}
                  />
                ),
              },
            ]}
          />
        </Card>

        <Modal
          title={t('adminAssets.seal.create')}
          open={createOpen}
          onCancel={() => setCreateOpen(false)}
          onOk={handleCreate}
          confirmLoading={submitting}
          okText={t('adminAssets.common.submit')}
          destroyOnClose
          width={520}
        >
          <Form form={form} layout="vertical" initialValues={{ sealType: 'OFFICIAL', copies: 1 }}>
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
          open={decideOpen}
          onCancel={() => {
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
          <Form form={decideForm} layout="vertical">
            <Form.Item name="comment" label={t('adminAssets.common.approverComment')}>
              <Input.TextArea rows={3} maxLength={500} showCount />
            </Form.Item>
          </Form>
        </Modal>

        <Modal
          title={t('adminAssets.seal.execution.useTitle')}
          open={useOpen}
          onCancel={() => {
            setUseOpen(false);
            setUseTarget(null);
          }}
          onOk={() => void handleUse()}
          confirmLoading={usingSeal}
          okText={t('adminAssets.seal.execution.use')}
          destroyOnClose
        >
          <Form form={useForm} layout="vertical">
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
            setDocumentOpen(false);
            setDocumentTarget(null);
            setDocuments([]);
          }}
          footer={null}
          width={640}
          destroyOnClose
        >
          <Upload customRequest={uploadDocument} showUploadList={false} maxCount={1}>
            <Button>{t('adminAssets.seal.document.upload')}</Button>
          </Upload>
          <Spin spinning={documentLoading}>
            <Table
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
                      onClick={() => void adminAssetsApi.downloadSealUsageDocument(document)
                        .catch((err) => message.error(formatOaApiError(err)))}
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

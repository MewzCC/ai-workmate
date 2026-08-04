'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Result,
  Segmented,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
  Upload,
} from 'antd';
import type { UploadFile } from 'antd';
import type { Key } from 'react';
import type { ColumnsType } from 'antd/es/table';
import type { RcFile } from 'antd/es/upload/interface';
import { message } from '@/lib/antdMessage';
import { OaIcon, oaKnowledgeBaseIconOptions, type OaIconName } from '@/components/OaIcon';
import {
  knowledgeApi,
  type EmbeddingStatus,
  type KnowledgeBase,
  type KnowledgeDocument,
  type KnowledgeSearchItem,
} from '@/lib/knowledgeApi';
import { useRouter } from '@/lib/nextCompat';
import { useTranslation } from 'react-i18next';
import DocumentDetailDrawer from './DocumentDetailDrawer';

const { TextArea } = Input;
const { Dragger } = Upload;

const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;

type CreateMode = 'text' | 'file';

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function StatusTag({ status }: { status: string }) {
  const { t } = useTranslation();
  if (status === 'READY') return <Tag color="success">{t('knowledge.statusReady')}</Tag>;
  if (status === 'PROCESSING') return <Tag color="processing">{t('knowledge.statusProcessing')}</Tag>;
  if (status === 'FAILED') return <Tag color="error">{t('knowledge.statusFailed')}</Tag>;
  return <Tag>{status}</Tag>;
}

function MatchTypeTag({ matchType }: { matchType: string }) {
  const { t } = useTranslation();
  if (matchType === 'HYBRID') return <Tag color="purple">{t('knowledge.matchHybrid')}</Tag>;
  if (matchType === 'SPARSE') return <Tag color="orange">{t('knowledge.matchSparse')}</Tag>;
  return <Tag color="blue">{t('knowledge.matchDense')}</Tag>;
}

interface OverviewTabProps {
  base: KnowledgeBase;
  embedding: EmbeddingStatus | null;
}

function OverviewTab({ base, embedding }: OverviewTabProps) {
  const { t } = useTranslation();
  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card className="oa-domain-card" size="small" title={t('knowledge.overviewStatistics')}>
        <Space size={48} wrap>
          <Statistic title={t('knowledge.overviewDocCount')} value={base.docCount} />
          <Statistic title={t('knowledge.overviewChunkCount')} value={base.chunkCount} />
        </Space>
      </Card>
      <Card className="oa-domain-card" size="small" title={t('knowledge.overviewBasicInfo')}>
        <Descriptions column={2}>
          <Descriptions.Item label={t('common.name')}>{base.name}</Descriptions.Item>
          <Descriptions.Item label={t('knowledge.fieldIcon')}>
            <OaIcon name={(base.icon || 'knowledge-base') as OaIconName} />
          </Descriptions.Item>
          <Descriptions.Item label={t('common.createdAt')}>
            {new Date(base.createdAt).toLocaleString()}
          </Descriptions.Item>
          <Descriptions.Item label={t('common.updatedAt')}>
            {new Date(base.updatedAt).toLocaleString()}
          </Descriptions.Item>
          <Descriptions.Item label={t('knowledge.fieldDescription')} span={2}>
            {base.description || '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>
      <Card className="oa-domain-card" size="small" title={t('knowledge.overviewModelConfig')}>
        <Descriptions column={2}>
          <Descriptions.Item label={t('knowledge.overviewEmbeddingModel')}>
            {base.embeddingProvider && base.embeddingModel
              ? `${base.embeddingProvider} / ${base.embeddingModel}`
              : '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('knowledge.overviewRerankModel')}>
            {base.rerankModel
              ? base.rerankModel
              : embedding?.rerankEnabled
                ? <span>{embedding.rerankModel} <Typography.Text type="secondary">{t('knowledge.overviewGlobalConfig')}</Typography.Text></span>
                : <Typography.Text type="secondary">{t('knowledge.overviewNotEnabled')}</Typography.Text>}
          </Descriptions.Item>
          <Descriptions.Item label={t('knowledge.overviewChunkSize')}>{base.chunkSize} {t('knowledge.charUnit')}</Descriptions.Item>
          <Descriptions.Item label={t('knowledge.overviewChunkOverlap')}>{base.chunkOverlap} {t('knowledge.charUnit')}</Descriptions.Item>
          <Descriptions.Item label={t('knowledge.overviewDenseTopK')}>{base.denseTopK}</Descriptions.Item>
          <Descriptions.Item label={t('knowledge.overviewSparseTopK')}>{base.sparseTopK}</Descriptions.Item>
        </Descriptions>
      </Card>
    </Space>
  );
}

interface DocsTabProps {
  kbId: number;
  onChanged: () => void;
}

interface CreateFormValues {
  filename: string;
  content: string;
}

function DocsTab({ kbId, onChanged }: DocsTabProps) {
  const { t } = useTranslation();
  const [embedding, setEmbedding] = useState<EmbeddingStatus | null>(null);
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [creating, setCreating] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createMode, setCreateMode] = useState<CreateMode>('text');
  const [createForm] = Form.useForm<CreateFormValues>();
  const [uploadFileList, setUploadFileList] = useState<UploadFile[]>([]);
  const [uploadProgress, setUploadProgress] = useState<number | null>(null);
  const [uploadStatus, setUploadStatus] = useState('');
  const [reindexingId, setReindexingId] = useState<number | null>(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);
  const [detailDocumentId, setDetailDocumentId] = useState<number | null>(null);

  const loadEmbedding = useCallback(async () => {
    try {
      setEmbedding(await knowledgeApi.embeddingStatus());
    } catch {
      setEmbedding(null);
    }
  }, []);

  const loadDocuments = useCallback(async () => {
    setLoading(true);
    try {
      const response = await knowledgeApi.list(kbId, page, 20);
      setDocuments(response.records);
      setTotal(response.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('knowledge.docsLoadFailed'));
    } finally {
      setLoading(false);
    }
  }, [kbId, page, t]);

  useEffect(() => {
    void loadEmbedding();
    void loadDocuments();
  }, [loadEmbedding, loadDocuments]);

  const openCreate = (mode: CreateMode) => {
    createForm.resetFields();
    setCreateMode(mode);
    setUploadFileList([]);
    setUploadProgress(null);
    setUploadStatus('');
    setCreateModalOpen(true);
  };

  const closeCreate = () => {
    setCreateModalOpen(false);
    setUploadFileList([]);
    setUploadProgress(null);
    setUploadStatus('');
  };

  const submitCreate = async () => {
    if (createMode === 'file') {
      const files = uploadFileList
        .map((item) => item.originFileObj)
        .filter((file): file is RcFile => file != null);
      if (files.length === 0) {
        message.warning(t('knowledge.docsSelectFileFirst'));
        return;
      }
      setCreating(true);
      setUploadProgress(0);
      try {
        let success = 0;
        const failed: string[] = [];
        for (let index = 0; index < files.length; index++) {
          const file = files[index];
          setUploadStatus(t('knowledge.docsUploading', { index: index + 1, total: files.length, name: file.name }));
          try {
            await knowledgeApi.upload(kbId, file, (percent) => {
              setUploadProgress(Math.round(((index + percent / 100) / files.length) * 100));
            });
            setUploadProgress(Math.round(((index + 1) / files.length) * 100));
            success += 1;
          } catch (error) {
            failed.push(`${file.name}：${error instanceof Error ? error.message : t('knowledge.docsUploadFailed')}`);
          }
        }
        if (success > 0) {
          message.success(success === files.length
            ? t('knowledge.docsUploadAllSuccess', { count: success })
            : t('knowledge.docsUploadPartialSuccess', { success, failed: failed.length }));
        }
        if (failed.length > 0) {
          message.error(failed.slice(0, 3).join('；') + (failed.length > 3 ? '…' : ''));
        }
        closeCreate();
        setPage(1);
        await loadDocuments();
        onChanged();
      } finally {
        setCreating(false);
        setUploadProgress(null);
        setUploadStatus('');
      }
      return;
    }
    const values = await createForm.validateFields();
    setCreating(true);
    try {
      await knowledgeApi.create({
        kbId,
        filename: values.filename.trim(),
        content: values.content,
      });
      message.success(t('knowledge.docsCreateSuccess'));
      closeCreate();
      setPage(1);
      await loadDocuments();
      onChanged();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('knowledge.docsCreateFailed'));
    } finally {
      setCreating(false);
    }
  };

  const confirmDelete = (document: KnowledgeDocument) => {
    Modal.confirm({
      title: t('knowledge.docsConfirmDeleteTitle', { name: document.filename }),
      content: t('knowledge.docsConfirmDeleteContent'),
      okText: t('knowledge.confirmDeleteOk'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await knowledgeApi.remove(document.id);
          message.success(t('knowledge.docsDeleteSuccess'));
          await loadDocuments();
          onChanged();
        } catch (error) {
          message.error(error instanceof Error ? error.message : t('knowledge.docsDeleteFailed'));
        }
      },
    });
  };

  const reindex = async (document: KnowledgeDocument) => {
    setReindexingId(document.id);
    try {
      await knowledgeApi.reindex(document.id);
      message.success(t('knowledge.docsReindexSuccess'));
      await loadDocuments();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('knowledge.docsReindexFailed'));
    } finally {
      setReindexingId(null);
    }
  };

  const selectedIds = () => selectedRowKeys
    .map(Number)
    .filter((value) => Number.isInteger(value) && value > 0);

  const confirmBatchDelete = () => {
    const ids = selectedIds();
    Modal.confirm({
      title: t('knowledge.docsConfirmBatchDeleteTitle', { count: ids.length }),
      content: t('knowledge.docsConfirmBatchDeleteContent'),
      okText: t('knowledge.confirmDeleteOk'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          const deleted = await knowledgeApi.batchDelete(ids);
          message.success(t('knowledge.docsBatchDeleteSuccess', { count: deleted }));
          setSelectedRowKeys([]);
          setPage(1);
          await loadDocuments();
          onChanged();
        } catch (error) {
          message.error(error instanceof Error ? error.message : t('knowledge.docsBatchDeleteFailed'));
        }
      },
    });
  };

  const confirmBatchReindex = () => {
    const ids = selectedIds();
    Modal.confirm({
      title: t('knowledge.docsConfirmBatchReindexTitle', { count: ids.length }),
      content: t('knowledge.docsConfirmBatchReindexContent'),
      okText: t('knowledge.docsConfirmReindexOk'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await knowledgeApi.batchReindex(ids);
          message.success(t('knowledge.docsBatchReindexSuccess', { count: ids.length }));
          setSelectedRowKeys([]);
          await loadDocuments();
        } catch (error) {
          message.error(error instanceof Error ? error.message : t('knowledge.docsBatchReindexFailed'));
        }
      },
    });
  };

  const columns: ColumnsType<KnowledgeDocument> = [
    { title: t('knowledge.colFilename'), dataIndex: 'filename', ellipsis: true },
    {
      title: t('knowledge.colType'),
      dataIndex: 'fileType',
      width: 100,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    {
      title: t('knowledge.colSize'),
      dataIndex: 'fileSize',
      width: 110,
      render: (value: number) => formatBytes(value),
    },
    { title: t('knowledge.colChunkCount'), dataIndex: 'chunkCount', width: 90 },
    { title: t('common.status'), dataIndex: 'status', width: 100, render: (value: string) => <StatusTag status={value} /> },
    {
      title: t('knowledge.colEmbedding'),
      key: 'embedding',
      width: 190,
      render: (_, item) =>
        item.embeddingProvider && item.embeddingModel
          ? `${item.embeddingProvider} / ${item.embeddingModel}`
          : '-',
    },
    {
      title: t('common.createdAt'),
      dataIndex: 'createdAt',
      width: 180,
      render: (value: string) => new Date(value).toLocaleString(),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 240,
      fixed: 'right',
      render: (_, item) => (
        <Space size={4}>
          <Button
            size="small"
            icon={<OaIcon name="search" />}
            onClick={() => setDetailDocumentId(item.id)}
          >
            {t('knowledge.actionDetail')}
          </Button>
          <Button
            size="small"
            icon={<OaIcon name="reload" />}
            loading={reindexingId === item.id}
            disabled={item.status !== 'READY'}
            onClick={() => void reindex(item)}
          >
            {t('knowledge.actionReindex')}
          </Button>
          <Button
            size="small"
            danger
            icon={<OaIcon name="delete" />}
            onClick={() => confirmDelete(item)}
          >
            {t('common.delete')}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {embedding && !embedding.enabled && (
        <Alert
          type="warning"
          showIcon
          message={t('knowledge.embeddingDisabled')}
          description={t('knowledge.embeddingDisabledDesc')}
        />
      )}

      <Space wrap>
        <Button type="primary" icon={<OaIcon name="upload" />} onClick={() => openCreate('file')}>
          {t('knowledge.docsUploadFiles')}
        </Button>
        <Button icon={<OaIcon name="add" />} onClick={() => openCreate('text')}>
          {t('knowledge.docsCreateDocument')}
        </Button>
      </Space>

      {selectedRowKeys.length > 0 && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            padding: '8px 12px',
            borderRadius: 8,
            background: 'var(--oa-fill-secondary, rgba(128,128,128,0.08))',
          }}
        >
          <Typography.Text strong>{t('knowledge.docsSelectedCount', { count: selectedRowKeys.length })}</Typography.Text>
          <Space>
            <Button size="small" icon={<OaIcon name="reload" />} onClick={confirmBatchReindex}>
              {t('knowledge.docsBatchReindex')}
            </Button>
            <Button size="small" danger icon={<OaIcon name="delete" />} onClick={confirmBatchDelete}>
              {t('knowledge.docsBatchDelete')}
            </Button>
            <Button size="small" type="link" onClick={() => setSelectedRowKeys([])}>
              {t('knowledge.docsClearSelection')}
            </Button>
          </Space>
        </div>
      )}

      <Table
        rowKey="id"
        columns={columns}
        dataSource={documents}
        loading={loading}
        rowSelection={{
          selectedRowKeys,
          onChange: setSelectedRowKeys,
        }}
        locale={{ emptyText: <Empty description={t('knowledge.docsEmpty')} /> }}
        scroll={{ x: 1280 }}
        pagination={{
          current: page,
          pageSize: 20,
          total,
          showSizeChanger: false,
          onChange: setPage,
        }}
      />

      <Modal
        title={createMode === 'file' ? t('knowledge.docsUploadTitle') : t('knowledge.docsCreateTitle')}
        open={createModalOpen}
        onCancel={closeCreate}
        onOk={() => void submitCreate()}
        okText={createMode === 'file' ? t('knowledge.docsUploadOk') : t('knowledge.docsCreateOk')}
        cancelText={t('common.cancel')}
        confirmLoading={creating}
        width={640}
      >
        <Segmented
          block
          value={createMode}
          disabled={creating}
          onChange={(value) => setCreateMode(value as CreateMode)}
          options={[
            { label: t('knowledge.docsPlainText'), value: 'text' },
            { label: t('knowledge.docsUploadFile'), value: 'file' },
          ]}
          style={{ marginTop: 8, marginBottom: 16 }}
        />
        {createMode === 'file' ? (
          <>
            <Dragger
              accept=".txt,.pdf,.doc,.docx,.md,.markdown,.csv"
              multiple
              disabled={creating}
              fileList={uploadFileList}
              beforeUpload={(file) => {
                if (file.size > MAX_UPLOAD_BYTES) {
                  message.error(t('knowledge.docsFileTooLarge', { name: file.name }));
                  return Upload.LIST_IGNORE;
                }
                return false;
              }}
              onChange={({ fileList }) => setUploadFileList(fileList)}
            >
              <p className="ant-upload-drag-icon">
                <OaIcon name="upload" size={40} />
              </p>
              <p className="ant-upload-text">{t('knowledge.docsDraggerText')}</p>
              <p className="ant-upload-hint">
                {t('knowledge.docsDraggerHint')}
              </p>
            </Dragger>
            {creating && (
              <div style={{ marginTop: 16 }}>
                {uploadStatus && (
                  <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 4 }}>
                    {uploadStatus}
                  </Typography.Text>
                )}
                <Progress percent={uploadProgress ?? 0} status="active" />
              </div>
            )}
            {!creating && (
              <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
                {t('knowledge.docsUploadHint')}
              </Typography.Paragraph>
            )}
          </>
        ) : (
          <Form form={createForm} layout="vertical">
            <Form.Item
              name="filename"
              label={t('knowledge.docsFieldFilename')}
              rules={[
                { required: true, message: t('knowledge.docsValidateFilenameRequired') },
                { max: 255, message: t('knowledge.docsValidateFilenameMax') },
              ]}
            >
              <Input placeholder={t('knowledge.docsPlaceholderFilename')} maxLength={255} />
            </Form.Item>
            <Form.Item
              name="content"
              label={t('knowledge.docsFieldContent')}
              rules={[
                { required: true, message: t('knowledge.docsValidateContentRequired') },
                { max: 120000, message: t('knowledge.docsValidateContentMax') },
              ]}
            >
              <TextArea
                rows={10}
                placeholder={t('knowledge.docsPlaceholderContent')}
                maxLength={120000}
                showCount
              />
            </Form.Item>
          </Form>
        )}
      </Modal>

      <DocumentDetailDrawer
        open={detailDocumentId != null}
        documentId={detailDocumentId}
        onClose={() => setDetailDocumentId(null)}
        onChanged={() => {
          setPage(1);
          void loadDocuments();
        }}
      />
    </Space>
  );
}

interface QueryTabProps {
  kbId: number;
}

function QueryTab({ kbId }: QueryTabProps) {
  const { t } = useTranslation();
  const [searchQuery, setSearchQuery] = useState('');
  const [resultLimit, setResultLimit] = useState(10);
  const [minScore, setMinScore] = useState<number | null>(0.35);
  const [searching, setSearching] = useState(false);
  const [searchItems, setSearchItems] = useState<KnowledgeSearchItem[]>([]);

  const runSearch = async () => {
    if (!searchQuery.trim()) {
      message.warning(t('knowledge.querySearchRequired'));
      return;
    }
    setSearching(true);
    try {
      const response = await knowledgeApi.searchInBase(kbId, {
        query: searchQuery.trim(),
        topK: resultLimit,
        minScore: minScore ?? undefined,
      });
      setSearchItems(response.records);
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('knowledge.queryFailed'));
      setSearchItems([]);
    } finally {
      setSearching(false);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space.Compact style={{ width: '100%' }}>
        <Input
          value={searchQuery}
          onChange={(event) => setSearchQuery(event.target.value)}
          placeholder={t('knowledge.queryInputPlaceholder')}
          onPressEnter={() => void runSearch()}
        />
        <Button type="primary" loading={searching} onClick={() => void runSearch()}>
          {t('knowledge.queryButton')}
        </Button>
      </Space.Compact>
      <Space wrap size="large">
        <Space size={4}>
          <Typography.Text type="secondary">{t('knowledge.queryResultLimit')}</Typography.Text>
          <InputNumber
            min={1}
            max={20}
            value={resultLimit}
            onChange={(value) => setResultLimit(value ?? 10)}
            style={{ width: 90 }}
          />
        </Space>
        <Space size={4}>
          <Typography.Text type="secondary">{t('knowledge.queryMinScore')}</Typography.Text>
          <InputNumber
            min={0}
            max={1}
            step={0.05}
            value={minScore ?? undefined}
            onChange={(value) => setMinScore(value ?? null)}
            style={{ width: 110 }}
          />
        </Space>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {t('knowledge.queryLimitHint')}
        </Typography.Text>
      </Space>
      <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
        {t('knowledge.queryDescription')}
      </Typography.Paragraph>
      {searchItems.length === 0 ? (
        <Empty
          description={searching ? t('knowledge.querySearching') : t('knowledge.queryEmptyHint')}
          style={{ padding: '32px 0' }}
        />
      ) : (
        searchItems.map((item) => (
          <Card
            key={item.chunkId}
            size="small"
            className="oa-domain-card"
            title={
              <Space>
                <Typography.Text strong>{item.filename}</Typography.Text>
                <Tag>{t('knowledge.queryChunkTag', { index: item.chunkIndex + 1 })}</Tag>
                <MatchTypeTag matchType={item.matchType} />
                <Tag color="blue">{(item.score * 100).toFixed(1)}%</Tag>
              </Space>
            }
          >
            <Typography.Paragraph
              type="secondary"
              style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}
            >
              {item.content}
            </Typography.Paragraph>
          </Card>
        ))
      )}
    </Space>
  );
}

interface SettingsFormValues {
  name: string;
  icon: OaIconName;
  description?: string;
  chunkSize: number;
  chunkOverlap: number;
  denseTopK: number;
  sparseTopK: number;
}

interface SettingsTabProps {
  base: KnowledgeBase;
  onSaved: () => void;
}

function SettingsTab({ base, onSaved }: SettingsTabProps) {
  const { t } = useTranslation();
  const [saving, setSaving] = useState(false);
  const [settingsForm] = Form.useForm<SettingsFormValues>();

  const submitSettings = async () => {
    const values = await settingsForm.validateFields();
    setSaving(true);
    try {
      await knowledgeApi.updateBase(base.id, {
        name: values.name.trim(),
        icon: values.icon,
        description: values.description?.trim() || undefined,
        chunkSize: values.chunkSize,
        chunkOverlap: values.chunkOverlap,
        denseTopK: values.denseTopK,
        sparseTopK: values.sparseTopK,
      });
      message.success(t('knowledge.settingsSaved'));
      onSaved();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('knowledge.settingsSaveFailed'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Form
        form={settingsForm}
        layout="vertical"
        style={{ maxWidth: 560 }}
        initialValues={{
          name: base.name,
          icon: (base.icon || 'knowledge-base') as OaIconName,
          description: base.description || undefined,
          chunkSize: base.chunkSize,
          chunkOverlap: base.chunkOverlap,
          denseTopK: base.denseTopK,
          sparseTopK: base.sparseTopK,
        }}
      >
        <Card className="oa-domain-card" size="small" title={t('knowledge.settingsBasicInfo')} style={{ marginBottom: 16 }}>
          <Form.Item
            name="name"
            label={t('knowledge.fieldName')}
            rules={[
              { required: true, message: t('knowledge.validateNameRequired') },
              { max: 80, message: t('knowledge.validateNameMax') },
            ]}
          >
            <Input maxLength={80} />
          </Form.Item>
          <Form.Item name="icon" label={t('knowledge.fieldIcon')}>
            <Select
              options={oaKnowledgeBaseIconOptions.map((option) => ({
                value: option.value,
                label: (
                  <Space>
                    <OaIcon name={option.value} />
                    {t(option.labelKey)}
                  </Space>
                ),
              }))}
            />
          </Form.Item>
          <Form.Item
            name="description"
            label={t('knowledge.fieldDescription')}
            rules={[{ max: 500, message: t('knowledge.validateDescriptionMax') }]}
          >
            <Input.TextArea rows={3} maxLength={500} />
          </Form.Item>
        </Card>

        <Card className="oa-domain-card" size="small" title={t('knowledge.settingsRetrieval')}>
          <Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
            {t('knowledge.settingsChunkHint')}
          </Typography.Paragraph>
          <Form.Item
            name="chunkSize"
            label={t('knowledge.settingsChunkSizeLabel')}
            tooltip={t('knowledge.settingsChunkSizeTooltip')}
            rules={[
              { required: true, message: t('knowledge.settingsChunkSizeRequired') },
              { type: 'number', min: 100, max: 8000, message: t('knowledge.settingsChunkSizeRange') },
            ]}
          >
            <InputNumber style={{ width: '100%' }} min={100} max={8000} />
          </Form.Item>
          <Form.Item
            name="chunkOverlap"
            label={t('knowledge.settingsChunkOverlapLabel')}
            tooltip={t('knowledge.settingsChunkOverlapTooltip')}
            dependencies={['chunkSize']}
            rules={[
              { required: true, message: t('knowledge.settingsChunkOverlapRequired') },
              { type: 'number', min: 0, max: 4000, message: t('knowledge.settingsChunkOverlapRange') },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  const chunkSize = getFieldValue('chunkSize');
                  if (value != null && chunkSize != null && value >= chunkSize) {
                    return Promise.reject(new Error(t('knowledge.settingsChunkOverlapLessThanSize')));
                  }
                  return Promise.resolve();
                },
              }),
            ]}
          >
            <InputNumber style={{ width: '100%' }} min={0} max={4000} />
          </Form.Item>
          <Form.Item
            name="denseTopK"
            label={t('knowledge.settingsDenseTopKLabel')}
            tooltip={t('knowledge.settingsDenseTopKTooltip')}
            rules={[
              { required: true, message: t('knowledge.settingsDenseTopKRequired') },
              { type: 'number', min: 1, max: 50, message: t('knowledge.settingsDenseTopKRange') },
            ]}
          >
            <InputNumber style={{ width: '100%' }} min={1} max={50} />
          </Form.Item>
          <Form.Item
            name="sparseTopK"
            label={t('knowledge.settingsSparseTopKLabel')}
            tooltip={t('knowledge.settingsSparseTopKTooltip')}
            rules={[
              { required: true, message: t('knowledge.settingsSparseTopKRequired') },
              { type: 'number', min: 0, max: 50, message: t('knowledge.settingsSparseTopKRange') },
            ]}
          >
            <InputNumber style={{ width: '100%' }} min={0} max={50} />
          </Form.Item>
        </Card>
      </Form>

      <Space>
        <Button type="primary" loading={saving} onClick={() => void submitSettings()}>
          {t('knowledge.settingsSave')}
        </Button>
        <Button onClick={() => settingsForm.resetFields()}>{t('common.reset')}</Button>
      </Space>
    </Space>
  );
}

export default function KnowledgeBaseDetail({ kbId }: { kbId: number }) {
  const router = useRouter();
  const { t } = useTranslation();
  const [base, setBase] = useState<KnowledgeBase | null>(null);
  const [loading, setLoading] = useState(true);
  const [embedding, setEmbedding] = useState<EmbeddingStatus | null>(null);

  const loadBase = useCallback(async () => {
    setLoading(true);
    try {
      setBase(await knowledgeApi.getBase(kbId));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('knowledge.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [kbId, t]);

  const loadEmbedding = useCallback(async () => {
    try {
      setEmbedding(await knowledgeApi.embeddingStatus());
    } catch {
      setEmbedding(null);
    }
  }, []);

  useEffect(() => {
    void loadBase();
    void loadEmbedding();
  }, [loadBase, loadEmbedding]);

  if (loading && !base) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!base) {
    return (
      <Result
        status="404"
        title={t('knowledge.notFound')}
        extra={<Button onClick={() => router.push('/oa/knowledge-base')}>{t('knowledge.backToList')}</Button>}
      />
    );
  }

  return (
    <section className="oa-domain-page">
      <div className="oa-domain-heading">
        <Space align="center" size={12}>
          <Button icon={<OaIcon name="previous" />} onClick={() => router.push('/oa/knowledge-base')}>
            {t('common.back')}
          </Button>
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 40,
              height: 40,
              borderRadius: 10,
              background: 'var(--oa-fill-secondary, rgba(128,128,128,0.12))',
            }}
          >
            <OaIcon name={(base.icon || 'knowledge-base') as OaIconName} size={22} />
          </span>
          <div>
            <Typography.Title level={4} style={{ marginBottom: 0 }}>
              {base.name}
            </Typography.Title>
            {base.description && (
              <Typography.Text type="secondary">{base.description}</Typography.Text>
            )}
          </div>
        </Space>
      </div>

      <Card className="oa-domain-card">
        <Tabs
          defaultActiveKey="overview"
          items={[
            { key: 'overview', label: t('knowledge.tabOverview'), children: <OverviewTab base={base} embedding={embedding} /> },
            { key: 'docs', label: t('knowledge.tabDocuments'), children: <DocsTab kbId={kbId} onChanged={loadBase} /> },
            { key: 'query', label: t('knowledge.tabQuery'), children: <QueryTab kbId={kbId} /> },
            { key: 'settings', label: t('knowledge.tabSettings'), children: <SettingsTab base={base} onSaved={loadBase} /> },
          ]}
        />
      </Card>
    </section>
  );
}

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
import { OaIcon, type OaIconName } from '@/components/OaIcon';
import {
  knowledgeApi,
  type EmbeddingStatus,
  type KnowledgeBase,
  type KnowledgeDocument,
  type KnowledgeSearchItem,
} from '@/lib/knowledgeApi';
import { useRouter } from '@/lib/nextCompat';
import DocumentDetailDrawer from './DocumentDetailDrawer';

const { TextArea } = Input;
const { Dragger } = Upload;

const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;

type CreateMode = 'text' | 'file';

const BASE_ICON_OPTIONS: Array<{ value: OaIconName; label: string }> = [
  { value: 'knowledge-base', label: '知识库' },
  { value: 'dashboard', label: '驾驶舱' },
  { value: 'form', label: '表单' },
  { value: 'audit', label: '审计' },
  { value: 'help', label: '帮助' },
  { value: 'organization', label: '组织' },
];

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function statusTag(status: string) {
  if (status === 'READY') return <Tag color="success">已就绪</Tag>;
  if (status === 'PROCESSING') return <Tag color="processing">处理中</Tag>;
  if (status === 'FAILED') return <Tag color="error">失败</Tag>;
  return <Tag>{status}</Tag>;
}

function matchTypeTag(matchType: string) {
  if (matchType === 'HYBRID') return <Tag color="purple">混合</Tag>;
  if (matchType === 'SPARSE') return <Tag color="orange">稀疏</Tag>;
  return <Tag color="blue">稠密</Tag>;
}

interface OverviewTabProps {
  base: KnowledgeBase;
}

function OverviewTab({ base }: OverviewTabProps) {
  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card className="oa-domain-card" size="small" title="统计信息">
        <Space size={48} wrap>
          <Statistic title="文档数量" value={base.docCount} />
          <Statistic title="分块数量" value={base.chunkCount} />
        </Space>
      </Card>
      <Card className="oa-domain-card" size="small" title="基本信息">
        <Descriptions column={2}>
          <Descriptions.Item label="名称">{base.name}</Descriptions.Item>
          <Descriptions.Item label="图标">
            <OaIcon name={(base.icon || 'knowledge-base') as OaIconName} />
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">
            {new Date(base.createdAt).toLocaleString()}
          </Descriptions.Item>
          <Descriptions.Item label="更新时间">
            {new Date(base.updatedAt).toLocaleString()}
          </Descriptions.Item>
          <Descriptions.Item label="描述" span={2}>
            {base.description || '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>
      <Card className="oa-domain-card" size="small" title="模型配置">
        <Descriptions column={2}>
          <Descriptions.Item label="嵌入模型">
            {base.embeddingProvider && base.embeddingModel
              ? `${base.embeddingProvider} / ${base.embeddingModel}`
              : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="重排序模型">
            {base.rerankModel || <Typography.Text type="secondary">未启用</Typography.Text>}
          </Descriptions.Item>
          <Descriptions.Item label="分块大小">{base.chunkSize} 字符</Descriptions.Item>
          <Descriptions.Item label="分块重叠">{base.chunkOverlap} 字符</Descriptions.Item>
          <Descriptions.Item label="稠密检索数量">{base.denseTopK}</Descriptions.Item>
          <Descriptions.Item label="稀疏检索数量">{base.sparseTopK}</Descriptions.Item>
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
      message.error(error instanceof Error ? error.message : '加载知识文档失败');
    } finally {
      setLoading(false);
    }
  }, [kbId, page]);

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
        message.warning('请先选择要上传的文件');
        return;
      }
      setCreating(true);
      setUploadProgress(0);
      try {
        let success = 0;
        const failed: string[] = [];
        for (let index = 0; index < files.length; index++) {
          const file = files[index];
          setUploadStatus(`正在上传 ${index + 1}/${files.length}：${file.name}`);
          try {
            await knowledgeApi.upload(kbId, file, (percent) => {
              setUploadProgress(Math.round(((index + percent / 100) / files.length) * 100));
            });
            setUploadProgress(Math.round(((index + 1) / files.length) * 100));
            success += 1;
          } catch (error) {
            failed.push(`${file.name}：${error instanceof Error ? error.message : '上传失败'}`);
          }
        }
        if (success > 0) {
          message.success(success === files.length
            ? `已上传 ${success} 个文件并完成解析与向量化`
            : `已上传 ${success} 个文件，${failed.length} 个失败`);
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
      message.success('知识文档已创建并完成向量化');
      closeCreate();
      setPage(1);
      await loadDocuments();
      onChanged();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '创建知识文档失败');
    } finally {
      setCreating(false);
    }
  };

  const confirmDelete = (document: KnowledgeDocument) => {
    Modal.confirm({
      title: `确认删除「${document.filename}」？`,
      content: '删除后该文档及其向量分块将被移除，无法恢复。',
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await knowledgeApi.remove(document.id);
          message.success('文档已删除');
          await loadDocuments();
          onChanged();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除文档失败');
        }
      },
    });
  };

  const reindex = async (document: KnowledgeDocument) => {
    setReindexingId(document.id);
    try {
      await knowledgeApi.reindex(document.id);
      message.success('文档已按当前向量模型重新向量化');
      await loadDocuments();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重新向量化失败');
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
      title: `确认删除选中的 ${ids.length} 个文档？`,
      content: '删除后这些文档及其向量分块将被移除，无法恢复。',
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          const deleted = await knowledgeApi.batchDelete(ids);
          message.success(`已删除 ${deleted} 个文档`);
          setSelectedRowKeys([]);
          setPage(1);
          await loadDocuments();
          onChanged();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '批量删除失败');
        }
      },
    });
  };

  const confirmBatchReindex = () => {
    const ids = selectedIds();
    Modal.confirm({
      title: `确认重建选中的 ${ids.length} 个文档？`,
      content: '将按当前向量模型重新向量化全部选中文档，请耐心等待完成。',
      okText: '确认重建',
      cancelText: '取消',
      onOk: async () => {
        try {
          await knowledgeApi.batchReindex(ids);
          message.success(`已重建 ${ids.length} 个文档`);
          setSelectedRowKeys([]);
          await loadDocuments();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '批量重建失败');
        }
      },
    });
  };

  const columns: ColumnsType<KnowledgeDocument> = [
    { title: '文件名', dataIndex: 'filename', ellipsis: true },
    {
      title: '类型',
      dataIndex: 'fileType',
      width: 100,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    {
      title: '大小',
      dataIndex: 'fileSize',
      width: 110,
      render: (value: number) => formatBytes(value),
    },
    { title: '分块数', dataIndex: 'chunkCount', width: 90 },
    { title: '状态', dataIndex: 'status', width: 100, render: statusTag },
    {
      title: '向量模型',
      key: 'embedding',
      width: 190,
      render: (_, item) =>
        item.embeddingProvider && item.embeddingModel
          ? `${item.embeddingProvider} / ${item.embeddingModel}`
          : '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (value: string) => new Date(value).toLocaleString(),
    },
    {
      title: '操作',
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
            详情
          </Button>
          <Button
            size="small"
            icon={<OaIcon name="reload" />}
            loading={reindexingId === item.id}
            disabled={item.status !== 'READY'}
            onClick={() => void reindex(item)}
          >
            重建
          </Button>
          <Button
            size="small"
            danger
            icon={<OaIcon name="delete" />}
            onClick={() => confirmDelete(item)}
          >
            删除
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
          message="Embedding 服务未启用"
          description="请在服务端配置 EMBEDDING_ENABLED=true 及对应的提供方（local / api）后，才能向量化文档。"
        />
      )}

      <Space wrap>
        <Button type="primary" icon={<OaIcon name="upload" />} onClick={() => openCreate('file')}>
          上传文件
        </Button>
        <Button icon={<OaIcon name="add" />} onClick={() => openCreate('text')}>
          新建文档
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
          <Typography.Text strong>已选 {selectedRowKeys.length} 项</Typography.Text>
          <Space>
            <Button size="small" icon={<OaIcon name="reload" />} onClick={confirmBatchReindex}>
              批量重建
            </Button>
            <Button size="small" danger icon={<OaIcon name="delete" />} onClick={confirmBatchDelete}>
              批量删除
            </Button>
            <Button size="small" type="link" onClick={() => setSelectedRowKeys([])}>
              取消选择
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
        locale={{ emptyText: <Empty description="暂无知识文档，点击上方「上传文件」或「新建文档」开始" /> }}
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
        title={createMode === 'file' ? '上传知识文档' : '新建知识文档'}
        open={createModalOpen}
        onCancel={closeCreate}
        onOk={() => void submitCreate()}
        okText={createMode === 'file' ? '上传并向量化' : '创建并向量化'}
        cancelText="取消"
        confirmLoading={creating}
        width={640}
      >
        <Segmented
          block
          value={createMode}
          disabled={creating}
          onChange={(value) => setCreateMode(value as CreateMode)}
          options={[
            { label: '纯文本', value: 'text' },
            { label: '上传文件', value: 'file' },
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
                  message.error(`${file.name} 超过 20MB，已忽略`);
                  return Upload.LIST_IGNORE;
                }
                return false;
              }}
              onChange={({ fileList }) => setUploadFileList(fileList)}
            >
              <p className="ant-upload-drag-icon">
                <OaIcon name="upload" size={40} />
              </p>
              <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
              <p className="ant-upload-hint">
                支持 TXT、PDF、Word（.doc / .docx）等文本文档，可一次选择多个文件，单个不超过 20MB
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
                文件将自动解析文本、分块并向量化，AI 工作空间提问时可检索到其中的内容。
              </Typography.Paragraph>
            )}
          </>
        ) : (
          <Form form={createForm} layout="vertical">
            <Form.Item
              name="filename"
              label="文件名"
              rules={[
                { required: true, message: '请输入文件名' },
                { max: 255, message: '文件名不能超过 255 个字符' },
              ]}
            >
              <Input placeholder="例如：员工手册.md" maxLength={255} />
            </Form.Item>
            <Form.Item
              name="content"
              label="知识内容"
              rules={[
                { required: true, message: '请输入知识内容' },
                { max: 120000, message: '内容不能超过 120000 个字符' },
              ]}
            >
              <TextArea
                rows={10}
                placeholder="粘贴需要入库的知识文本（支持纯文本 / Markdown），保存后将自动分块并向量化"
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
  const [searchQuery, setSearchQuery] = useState('');
  const [resultLimit, setResultLimit] = useState(10);
  const [minScore, setMinScore] = useState<number | null>(0.35);
  const [searching, setSearching] = useState(false);
  const [searchItems, setSearchItems] = useState<KnowledgeSearchItem[]>([]);

  const runSearch = async () => {
    if (!searchQuery.trim()) {
      message.warning('请输入检索问题');
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
      message.error(error instanceof Error ? error.message : '检索失败');
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
          placeholder="输入检索问题，例如：公司请假制度"
          onPressEnter={() => void runSearch()}
        />
        <Button type="primary" loading={searching} onClick={() => void runSearch()}>
          检索
        </Button>
      </Space.Compact>
      <Space wrap size="large">
        <Space size={4}>
          <Typography.Text type="secondary">返回结果数量</Typography.Text>
          <InputNumber
            min={1}
            max={20}
            value={resultLimit}
            onChange={(value) => setResultLimit(value ?? 10)}
            style={{ width: 90 }}
          />
        </Space>
        <Space size={4}>
          <Typography.Text type="secondary">最低相关度</Typography.Text>
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
          数量受知识库设置的稠密/稀疏检索数量限制
        </Typography.Text>
      </Space>
      <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
        检索同时执行稠密（向量语义）与稀疏（关键词全文）召回并按相关度融合排序。
      </Typography.Paragraph>
      {searchItems.length === 0 ? (
        <Empty
          description={searching ? '检索中…' : '输入问题后点击「检索」，查看召回结果'}
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
                <Tag>分块 {item.chunkIndex + 1}</Tag>
                {matchTypeTag(item.matchType)}
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
      message.success('知识库设置已保存');
      onSaved();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存设置失败');
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
        <Card className="oa-domain-card" size="small" title="基本信息" style={{ marginBottom: 16 }}>
          <Form.Item
            name="name"
            label="知识库名称"
            rules={[
              { required: true, message: '请输入知识库名称' },
              { max: 80, message: '名称不能超过 80 个字符' },
            ]}
          >
            <Input maxLength={80} />
          </Form.Item>
          <Form.Item name="icon" label="图标">
            <Select
              options={BASE_ICON_OPTIONS.map((option) => ({
                value: option.value,
                label: (
                  <Space>
                    <OaIcon name={option.value} />
                    {option.label}
                  </Space>
                ),
              }))}
            />
          </Form.Item>
          <Form.Item
            name="description"
            label="描述"
            rules={[{ max: 500, message: '描述不能超过 500 个字符' }]}
          >
            <Input.TextArea rows={3} maxLength={500} />
          </Form.Item>
        </Card>

        <Card className="oa-domain-card" size="small" title="检索与分块参数">
          <Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
            分块参数只影响之后新入库的文档；检索数量即时生效。
          </Typography.Paragraph>
          <Form.Item
            name="chunkSize"
            label="分块大小（字符）"
            tooltip="每个知识分块的最大字符数，建议 500-1500"
            rules={[
              { required: true, message: '请输入分块大小' },
              { type: 'number', min: 100, max: 8000, message: '范围 100-8000' },
            ]}
          >
            <InputNumber style={{ width: '100%' }} min={100} max={8000} />
          </Form.Item>
          <Form.Item
            name="chunkOverlap"
            label="分块重叠（字符）"
            tooltip="相邻分块之间重复保留的字符数，必须小于分块大小"
            dependencies={['chunkSize']}
            rules={[
              { required: true, message: '请输入分块重叠' },
              { type: 'number', min: 0, max: 4000, message: '范围 0-4000' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  const chunkSize = getFieldValue('chunkSize');
                  if (value != null && chunkSize != null && value >= chunkSize) {
                    return Promise.reject(new Error('分块重叠必须小于分块大小'));
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
            label="稠密检索数量"
            tooltip="向量语义召回的最大条数"
            rules={[
              { required: true, message: '请输入稠密检索数量' },
              { type: 'number', min: 1, max: 50, message: '范围 1-50' },
            ]}
          >
            <InputNumber style={{ width: '100%' }} min={1} max={50} />
          </Form.Item>
          <Form.Item
            name="sparseTopK"
            label="稀疏检索数量"
            tooltip="关键词全文召回的最大条数，设为 0 表示仅使用稠密检索"
            rules={[
              { required: true, message: '请输入稀疏检索数量' },
              { type: 'number', min: 0, max: 50, message: '范围 0-50' },
            ]}
          >
            <InputNumber style={{ width: '100%' }} min={0} max={50} />
          </Form.Item>
        </Card>
      </Form>

      <Space>
        <Button type="primary" loading={saving} onClick={() => void submitSettings()}>
          保存设置
        </Button>
        <Button onClick={() => settingsForm.resetFields()}>重置</Button>
      </Space>
    </Space>
  );
}

export default function KnowledgeBaseDetail({ kbId }: { kbId: number }) {
  const router = useRouter();
  const [base, setBase] = useState<KnowledgeBase | null>(null);
  const [loading, setLoading] = useState(true);

  const loadBase = useCallback(async () => {
    setLoading(true);
    try {
      setBase(await knowledgeApi.getBase(kbId));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载知识库失败');
    } finally {
      setLoading(false);
    }
  }, [kbId]);

  useEffect(() => {
    void loadBase();
  }, [loadBase]);

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
        title="知识库不存在"
        extra={<Button onClick={() => router.push('/oa/knowledge-base')}>返回知识库列表</Button>}
      />
    );
  }

  return (
    <section className="oa-domain-page">
      <div className="oa-domain-heading">
        <Space align="center" size={12}>
          <Button icon={<OaIcon name="previous" />} onClick={() => router.push('/oa/knowledge-base')}>
            返回
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
            { key: 'overview', label: '概览', children: <OverviewTab base={base} /> },
            { key: 'docs', label: '文档管理', children: <DocsTab kbId={kbId} onChanged={loadBase} /> },
            { key: 'query', label: '知识库查询', children: <QueryTab kbId={kbId} /> },
            { key: 'settings', label: '设置', children: <SettingsTab base={base} onSaved={loadBase} /> },
          ]}
        />
      </Card>
    </section>
  );
}

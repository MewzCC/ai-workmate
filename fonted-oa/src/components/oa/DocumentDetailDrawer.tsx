'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Descriptions,
  Drawer,
  Empty,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { message } from '@/lib/antdMessage';
import { OaIcon } from '@/components/OaIcon';
import {
  knowledgeApi,
  type KnowledgeChunk,
  type KnowledgeDocumentDetail,
} from '@/lib/knowledgeApi';

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

interface DocumentDetailDrawerProps {
  open: boolean;
  documentId: number | null;
  onClose: () => void;
  onChanged: () => void;
}

export default function DocumentDetailDrawer({
  open,
  documentId,
  onClose,
  onChanged,
}: DocumentDetailDrawerProps) {
  const [detail, setDetail] = useState<KnowledgeDocumentDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [deletingChunkId, setDeletingChunkId] = useState<number | null>(null);
  const [chunkDetail, setChunkDetail] = useState<KnowledgeChunk | null>(null);

  const loadDetail = useCallback(async () => {
    if (!open || documentId == null) return;
    setLoading(true);
    try {
      setDetail(await knowledgeApi.getDocument(documentId));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载文档详情失败');
    } finally {
      setLoading(false);
    }
  }, [open, documentId]);

  useEffect(() => {
    void loadDetail();
  }, [loadDetail]);

  const confirmDeleteChunk = (chunk: KnowledgeChunk) => {
    if (documentId == null) return;
    Modal.confirm({
      title: `确认删除第 ${chunk.chunkIndex + 1} 个分块？`,
      content: '删除后该分块及其向量将被移除，后续分块序号会自动前移。',
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        setDeletingChunkId(chunk.vectorId);
        try {
          await knowledgeApi.deleteChunk(documentId, chunk.vectorId);
          message.success('分块已删除');
          await loadDetail();
          onChanged();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除分块失败');
        } finally {
          setDeletingChunkId(null);
        }
      },
    });
  };

  const chunkColumns: ColumnsType<KnowledgeChunk> = [
    {
      title: '序号',
      dataIndex: 'chunkIndex',
      width: 80,
      render: (value: number) => value + 1,
    },
    {
      title: '内容',
      dataIndex: 'content',
      render: (value: string) => (
        <Typography.Paragraph
          ellipsis={{ rows: 3 }}
          style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}
        >
          {value}
        </Typography.Paragraph>
      ),
    },
    {
      title: '字符数',
      dataIndex: 'charCount',
      width: 100,
      render: (value: number) => value.toLocaleString(),
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      fixed: 'right',
      render: (_, chunk) => (
        <Space size={4}>
          <Button size="small" icon={<OaIcon name="search" />} onClick={() => setChunkDetail(chunk)}>
            查看详情
          </Button>
          <Button
            size="small"
            danger
            icon={<OaIcon name="delete" />}
            loading={deletingChunkId === chunk.vectorId}
            onClick={() => confirmDeleteChunk(chunk)}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Drawer
      title={detail ? `文档详情：${detail.filename}` : '文档详情'}
      open={open}
      onClose={onClose}
      width={880}
      destroyOnClose
    >
      {!detail ? (
        <Empty description="加载中…" />
      ) : (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Descriptions
            column={2}
            size="small"
            bordered
            title="文档信息"
          >
            <Descriptions.Item label="文档名称">{detail.filename}</Descriptions.Item>
            <Descriptions.Item label="文件类型">
              <Tag>{detail.fileType}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="文件大小">{formatBytes(detail.fileSize)}</Descriptions.Item>
            <Descriptions.Item label="分块数量">{detail.chunkCount}</Descriptions.Item>
            <Descriptions.Item label="上传时间">
              {new Date(detail.createdAt).toLocaleString()}
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              {detail.status === 'READY' ? (
                <Tag color="success">已就绪</Tag>
              ) : detail.status === 'PROCESSING' ? (
                <Tag color="processing">处理中</Tag>
              ) : (
                <Tag color="error">{detail.status}</Tag>
              )}
            </Descriptions.Item>
          </Descriptions>

          <Typography.Title level={5} style={{ marginBottom: 0 }}>
            分块列表（{detail.chunks.length}）
          </Typography.Title>
          <Table
            rowKey="vectorId"
            columns={chunkColumns}
            dataSource={detail.chunks}
            loading={loading}
            size="small"
            locale={{ emptyText: <Empty description="该文档暂无分块" /> }}
            scroll={{ x: 760, y: 420 }}
            pagination={detail.chunks.length > 20 ? { pageSize: 20, showSizeChanger: false } : false}
          />
        </Space>
      )}

      <Modal
        title={`分块详情（第 ${chunkDetail ? chunkDetail.chunkIndex + 1 : ''} 块）`}
        open={chunkDetail != null}
        onCancel={() => setChunkDetail(null)}
        footer={null}
        width={720}
      >
        {chunkDetail && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions column={3} size="small" bordered>
              <Descriptions.Item label="序号">{chunkDetail.chunkIndex + 1}</Descriptions.Item>
              <Descriptions.Item label="字符数">
                {chunkDetail.charCount.toLocaleString()}
              </Descriptions.Item>
              <Descriptions.Item label="向量ID">{chunkDetail.vectorId}</Descriptions.Item>
            </Descriptions>
            <Typography.Paragraph
              style={{ marginBottom: 0, whiteSpace: 'pre-wrap', maxHeight: 420, overflow: 'auto' }}
            >
              {chunkDetail.content}
            </Typography.Paragraph>
          </Space>
        )}
      </Modal>
    </Drawer>
  );
}

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
import { useTranslation } from 'react-i18next';

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
  const { t } = useTranslation();
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
      message.error(error instanceof Error ? error.message : t('knowledge.drawerLoadFailed'));
    } finally {
      setLoading(false);
    }
  }, [open, documentId, t]);

  useEffect(() => {
    void loadDetail();
  }, [loadDetail]);

  const confirmDeleteChunk = (chunk: KnowledgeChunk) => {
    if (documentId == null) return;
    Modal.confirm({
      title: t('knowledge.drawerConfirmDeleteChunkTitle', { index: chunk.chunkIndex + 1 }),
      content: t('knowledge.drawerConfirmDeleteChunkContent'),
      okText: t('knowledge.confirmDeleteOk'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: async () => {
        setDeletingChunkId(chunk.vectorId);
        try {
          await knowledgeApi.deleteChunk(documentId, chunk.vectorId);
          message.success(t('knowledge.drawerDeleteChunkSuccess'));
          await loadDetail();
          onChanged();
        } catch (error) {
          message.error(error instanceof Error ? error.message : t('knowledge.drawerDeleteChunkFailed'));
        } finally {
          setDeletingChunkId(null);
        }
      },
    });
  };

  const chunkColumns: ColumnsType<KnowledgeChunk> = [
    {
      title: t('knowledge.drawerColIndex'),
      dataIndex: 'chunkIndex',
      width: 80,
      render: (value: number) => value + 1,
    },
    {
      title: t('knowledge.drawerColContent'),
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
      title: t('knowledge.drawerColCharCount'),
      dataIndex: 'charCount',
      width: 100,
      render: (value: number) => value.toLocaleString(),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 200,
      fixed: 'right',
      render: (_, chunk) => (
        <Space size={4}>
          <Button size="small" icon={<OaIcon name="search" />} onClick={() => setChunkDetail(chunk)}>
            {t('knowledge.drawerViewDetail')}
          </Button>
          <Button
            size="small"
            danger
            icon={<OaIcon name="delete" />}
            loading={deletingChunkId === chunk.vectorId}
            onClick={() => confirmDeleteChunk(chunk)}
          >
            {t('common.delete')}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Drawer
      title={detail ? t('knowledge.drawerTitleWithName', { name: detail.filename }) : t('knowledge.drawerTitle')}
      open={open}
      onClose={onClose}
      width={880}
      destroyOnClose
    >
      {!detail ? (
        <Empty description={t('common.loading')} />
      ) : (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Descriptions
            column={2}
            size="small"
            bordered
            title={t('knowledge.drawerDocInfo')}
          >
            <Descriptions.Item label={t('knowledge.drawerDocName')}>{detail.filename}</Descriptions.Item>
            <Descriptions.Item label={t('knowledge.drawerFileType')}>
              <Tag>{detail.fileType}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('knowledge.drawerFileSize')}>{formatBytes(detail.fileSize)}</Descriptions.Item>
            <Descriptions.Item label={t('knowledge.drawerChunkCount')}>{detail.chunkCount}</Descriptions.Item>
            <Descriptions.Item label={t('knowledge.drawerUploadedAt')}>
              {new Date(detail.createdAt).toLocaleString()}
            </Descriptions.Item>
            <Descriptions.Item label={t('common.status')}>
              {detail.status === 'READY' ? (
                <Tag color="success">{t('knowledge.statusReady')}</Tag>
              ) : detail.status === 'PROCESSING' ? (
                <Tag color="processing">{t('knowledge.statusProcessing')}</Tag>
              ) : (
                <Tag color="error">{detail.status}</Tag>
              )}
            </Descriptions.Item>
          </Descriptions>

          <Typography.Title level={5} style={{ marginBottom: 0 }}>
            {t('knowledge.drawerChunksTitle', { count: detail.chunks.length })}
          </Typography.Title>
          <Table
            rowKey="vectorId"
            columns={chunkColumns}
            dataSource={detail.chunks}
            loading={loading}
            size="small"
            locale={{ emptyText: <Empty description={t('knowledge.drawerNoChunks')} /> }}
            scroll={{ x: 760, y: 420 }}
            pagination={detail.chunks.length > 20 ? { pageSize: 20, showSizeChanger: false } : false}
          />
        </Space>
      )}

      <Modal
        title={t('knowledge.drawerChunkDetailTitle', { index: chunkDetail ? chunkDetail.chunkIndex + 1 : '' })}
        open={chunkDetail != null}
        onCancel={() => setChunkDetail(null)}
        footer={null}
        width={720}
      >
        {chunkDetail && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions column={3} size="small" bordered>
              <Descriptions.Item label={t('knowledge.drawerColIndex')}>{chunkDetail.chunkIndex + 1}</Descriptions.Item>
              <Descriptions.Item label={t('knowledge.drawerColCharCount')}>
                {chunkDetail.charCount.toLocaleString()}
              </Descriptions.Item>
              <Descriptions.Item label={t('knowledge.drawerVectorId')}>{chunkDetail.vectorId}</Descriptions.Item>
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

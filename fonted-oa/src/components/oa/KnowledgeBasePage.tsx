'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Typography,
} from 'antd';
import { message } from '@/lib/antdMessage';
import { OaIcon, oaKnowledgeBaseIconOptions, type OaIconName } from '@/components/OaIcon';
import { knowledgeApi, type KnowledgeBase } from '@/lib/knowledgeApi';
import { useRouter } from '@/lib/nextCompat';
import KnowledgeBaseDetail from './KnowledgeBaseDetail';

interface CreateBaseValues {
  name: string;
  icon?: OaIconName;
  description?: string;
}

export default function KnowledgeBasePage({ kbId }: { kbId?: number }) {
  const router = useRouter();
  const [bases, setBases] = useState<KnowledgeBase[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createForm] = Form.useForm<CreateBaseValues>();
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const loadBases = useCallback(async () => {
    setLoading(true);
    try {
      setBases(await knowledgeApi.listBases());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载知识库失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // 列表视图挂载或从详情页返回时强制刷新，保证文档数/名称等变化可见
    if (!kbId) {
      void loadBases();
    }
  }, [kbId, loadBases]);

  if (kbId) {
    return <KnowledgeBaseDetail kbId={kbId} />;
  }

  const openCreate = () => {
    createForm.resetFields();
    createForm.setFieldsValue({ icon: 'knowledge-base' });
    setCreateModalOpen(true);
  };

  const submitCreate = async () => {
    const values = await createForm.validateFields();
    setCreating(true);
    try {
      await knowledgeApi.createBase({
        name: values.name.trim(),
        icon: values.icon ?? 'knowledge-base',
        description: values.description?.trim() || undefined,
      });
      message.success('知识库已创建');
      setCreateModalOpen(false);
      await loadBases();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '创建知识库失败');
    } finally {
      setCreating(false);
    }
  };

  const confirmDelete = (knowledgeBase: KnowledgeBase) => {
    Modal.confirm({
      title: `确认删除知识库「${knowledgeBase.name}」？`,
      content: `该知识库下的 ${knowledgeBase.docCount} 个文档及其向量分块将一并删除，无法恢复。`,
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        setDeletingId(knowledgeBase.id);
        try {
          await knowledgeApi.deleteBase(knowledgeBase.id);
          message.success('知识库已删除');
          await loadBases();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除知识库失败');
        } finally {
          setDeletingId(null);
        }
      },
    });
  };

  return (
    <section className="oa-domain-page">
      <div className="oa-domain-heading">
        <div>
          <Typography.Title level={3}>知识库管理</Typography.Title>
          <Typography.Paragraph type="secondary">
            管理您的所有知识库集合，在知识库内上传文档、查询与配置检索参数。
          </Typography.Paragraph>
        </div>
        <Space>
          <Button type="primary" icon={<OaIcon name="add" />} onClick={openCreate}>
            新建知识库
          </Button>
        </Space>
      </div>

      <Card
        className="oa-domain-card"
        loading={loading}
        styles={{ body: { padding: 16 } }}
      >
        {bases.length === 0 ? (
          <Empty description="暂无知识库，点击右上角「新建知识库」开始" style={{ padding: '32px 0' }} />
        ) : (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
              gap: 16,
            }}
          >
            {bases.map((item) => (
              <Card
                key={item.id}
                hoverable
                className="oa-domain-card"
                onClick={() => router.push(`/oa/knowledge-bases/${item.id}`)}
              >
                <Space align="start" style={{ width: '100%', justifyContent: 'space-between' }}>
                  <Space align="center">
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
                      <OaIcon name={(item.icon || 'knowledge-base') as OaIconName} size={22} />
                    </span>
                    <div>
                      <Typography.Text strong style={{ fontSize: 15 }}>
                        {item.name}
                      </Typography.Text>
                      {item.description && (
                        <Typography.Paragraph
                          type="secondary"
                          ellipsis={{ rows: 1 }}
                          style={{ marginBottom: 0, maxWidth: 150 }}
                        >
                          {item.description}
                        </Typography.Paragraph>
                      )}
                    </div>
                  </Space>
                  <Button
                    size="small"
                    danger
                    type="text"
                    loading={deletingId === item.id}
                    icon={<OaIcon name="delete" />}
                    onClick={(event) => {
                      event.stopPropagation();
                      confirmDelete(item);
                    }}
                  />
                </Space>
                <div style={{ display: 'flex', gap: 24, marginTop: 16 }}>
                  <div>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>文档</Typography.Text>
                    <div><Typography.Text strong>{item.docCount}</Typography.Text></div>
                  </div>
                  <div>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>分块</Typography.Text>
                    <div><Typography.Text strong>{item.chunkCount}</Typography.Text></div>
                  </div>
                  <div style={{ marginLeft: 'auto', textAlign: 'right' }}>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>更新时间</Typography.Text>
                    <div>
                      <Typography.Text style={{ fontSize: 12 }}>
                        {new Date(item.updatedAt).toLocaleDateString()}
                      </Typography.Text>
                    </div>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        )}
      </Card>

      <Modal
        title="新建知识库"
        open={createModalOpen}
        onCancel={() => setCreateModalOpen(false)}
        onOk={() => void submitCreate()}
        okText="创建"
        cancelText="取消"
        confirmLoading={creating}
        width={480}
      >
        <Form form={createForm} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item
            name="name"
            label="知识库名称"
            rules={[
              { required: true, message: '请输入知识库名称' },
              { max: 80, message: '名称不能超过 80 个字符' },
            ]}
          >
            <Input placeholder="例如：公司制度库" maxLength={80} />
          </Form.Item>
          <Form.Item name="icon" label="图标">
            <Select
              options={oaKnowledgeBaseIconOptions.map((option) => ({
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
            <Input.TextArea rows={3} placeholder="一句话说明这个知识库的用途（可选）" maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </section>
  );
}

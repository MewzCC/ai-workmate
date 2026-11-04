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
import { useTranslation } from 'react-i18next';
import KnowledgeBaseDetail from './KnowledgeBaseDetail';
import { usePageUiEditorCommands } from '@/hooks/usePageUiEditorCommands';
import { usePageUiFormContext } from '@/hooks/usePageUiFormContext';
import { useAuth } from '@/components/auth/AuthProvider';
import { useRegisterPageUiViews } from './PageUiCommandBridge';
import { usePublishPageAgentContext } from './PageAgentContext';
import { requireUiFieldTypes } from '@/lib/preparedPageUiCommand';

interface CreateBaseValues {
  name: string;
  icon?: OaIconName;
  description?: string;
}

export default function KnowledgeBasePage({ kbId }: { kbId?: number }) {
  const router = useRouter();
  const { t } = useTranslation();
  const { user } = useAuth();
  useRegisterPageUiViews('list', ['list', 'overview', 'docs', 'query', 'settings'], async (view, filters) => {
    if (view === 'list') { router.push('/oa/knowledge-base'); return; }
    const id = Number(filters.kbId);
    if (!Number.isSafeInteger(id) || id < 1) throw new Error('PAGE_UI_FIELD_REJECTED');
    const base = await knowledgeApi.getBase(id);
    if (base.id !== id) throw new Error('PAGE_UI_FIELD_REJECTED');
    router.push(`/oa/knowledge-bases/${id}?section=${view}`);
  }, '', !kbId);
  const [bases, setBases] = useState<KnowledgeBase[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createForm] = Form.useForm<CreateBaseValues>();
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [query, setQuery] = useState('');
  const visibleBases = bases.filter(base => `${base.name} ${base.description ?? ''}`.toLowerCase().includes(query.trim().toLowerCase()));
  const uiForm = usePageUiFormContext(!kbId && createModalOpen && !creating, undefined, undefined, `${user?.tenantId}:${user?.id}:${user?.permissionVersion}`);
  const openCreate = () => {
    uiForm.renew(); createForm.resetFields(); createForm.setFieldsValue({ icon: 'knowledge-base' }); setCreateModalOpen(true);
  };

  const loadBases = useCallback(async () => {
    setLoading(true);
    try {
      setBases(await knowledgeApi.listBases());
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('knowledge.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    // 列表视图挂载或从详情页返回时强制刷新，保证文档数/名称等变化可见
    if (!kbId) {
      void loadBases();
    }
  }, [kbId, loadBases]);
  usePublishPageAgentContext({ query: query || undefined });
  usePageUiEditorCommands({ form: createForm, open: createModalOpen && !creating, canEdit: !kbId && Boolean(user), context: uiForm.context,
    viewKey: kbId ? undefined : 'knowledge-base-list', create: openCreate,
    fields: { name: 'string', icon: oaKnowledgeBaseIconOptions.map(option => option.value), description: 'string' },
    labels: { name: t('common.name'), icon: t('knowledge.fieldIcon'), description: t('knowledge.fieldDescription') },
    commands: kbId ? {} : { 'ui.refreshPage': loadBases,
    'ui.applyFilter': ({ payload: { filters } }) => {
      requireUiFieldTypes(filters, { query: 'string' });
      if (filters.query !== undefined) setQuery(String(filters.query));
    },
    'ui.openDetail': async ({ payload: { recordId } }) => {
      const id = Number(recordId);
      if (!Number.isSafeInteger(id) || id < 1) throw new Error('PAGE_UI_FIELD_REJECTED');
      const base = await knowledgeApi.getBase(id);
      if (base.id !== id) throw new Error('PAGE_UI_FIELD_REJECTED');
      router.push(`/oa/knowledge-bases/${id}`);
    },
  } });

  if (kbId) {
    return <KnowledgeBaseDetail kbId={kbId} />;
  }

  const submitCreate = async () => {
    const values = await createForm.validateFields();
    setCreating(true);
    try {
      await knowledgeApi.createBase({
        name: values.name.trim(),
        icon: values.icon ?? 'knowledge-base',
        description: values.description?.trim() || undefined,
      });
      message.success(t('knowledge.createSuccess'));
      setCreateModalOpen(false);
      await loadBases();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('knowledge.createFailed'));
    } finally {
      setCreating(false);
    }
  };

  const confirmDelete = (knowledgeBase: KnowledgeBase) => {
    Modal.confirm({
      title: t('knowledge.confirmDeleteTitle', { name: knowledgeBase.name }),
      content: t('knowledge.confirmDeleteContent', { count: knowledgeBase.docCount }),
      okText: t('knowledge.confirmDeleteOk'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: async () => {
        setDeletingId(knowledgeBase.id);
        try {
          await knowledgeApi.deleteBase(knowledgeBase.id);
          message.success(t('knowledge.deleteSuccess'));
          await loadBases();
        } catch (error) {
          message.error(error instanceof Error ? error.message : t('knowledge.deleteFailed'));
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
          <Typography.Title level={3}>{t('knowledge.title')}</Typography.Title>
          <Typography.Paragraph type="secondary">
            {t('knowledge.description')}
          </Typography.Paragraph>
        </div>
        <Space>
          <Button type="primary" icon={<OaIcon name="add" />} onClick={openCreate}>
            {t('knowledge.createBase')}
          </Button>
        </Space>
      </div>

      <Card
        className="oa-domain-card"
        loading={loading}
        styles={{ body: { padding: 16 } }}
      >
        {bases.length === 0 ? (
          <Empty description={t('knowledge.empty')} style={{ padding: '32px 0' }} />
        ) : (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
              gap: 16,
            }}
          >
            <Input.Search allowClear value={query} onChange={event => setQuery(event.target.value)} placeholder={t('common.searchPlaceholder')} />
            {visibleBases.map((item) => (
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
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>{t('knowledge.statDocuments')}</Typography.Text>
                    <div><Typography.Text strong>{item.docCount}</Typography.Text></div>
                  </div>
                  <div>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>{t('knowledge.statChunks')}</Typography.Text>
                    <div><Typography.Text strong>{item.chunkCount}</Typography.Text></div>
                  </div>
                  <div style={{ marginLeft: 'auto', textAlign: 'right' }}>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>{t('common.updatedAt')}</Typography.Text>
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
        title={t('knowledge.createBase')}
        open={createModalOpen}
        onCancel={() => setCreateModalOpen(false)}
        onOk={() => void submitCreate()}
        okText={t('common.create')}
        cancelText={t('common.cancel')}
        confirmLoading={creating}
        width={480}
      >
        <Form form={createForm} layout="vertical" style={{ marginTop: 8 }} onValuesChange={uiForm.renew}>
          <Form.Item
            name="name"
            label={t('knowledge.fieldName')}
            rules={[
              { required: true, message: t('knowledge.validateNameRequired') },
              { max: 80, message: t('knowledge.validateNameMax') },
            ]}
          >
            <Input placeholder={t('knowledge.placeholderName')} maxLength={80} />
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
            <Input.TextArea rows={3} placeholder={t('knowledge.placeholderDescription')} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </section>
  );
}

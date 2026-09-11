import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { ColumnsType } from 'antd/es/table';
import {
  Alert, Button, Card, Form, Input, Modal, Select, Space, Statistic, Switch, Tag, Typography,
} from 'antd';
import { OaIcon } from '@/components/OaIcon';
import { message } from '@/lib/antdMessage';
import { formatOaApiError } from '@/lib/oaApi';
import { pageActionApi, type PageActionItem, type PageActionOverview } from '@/lib/pageActionApi';
import ResponsiveTable from './ResponsiveTable';

interface PolicyForm { reason: string }

export default function PageActionsPage() {
  const { t } = useTranslation();
  const [data, setData] = useState<PageActionOverview>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [pageId, setPageId] = useState<string>();
  const [keyword, setKeyword] = useState('');
  const [pending, setPending] = useState<{ action: PageActionItem; enabled: boolean }>();
  const [form] = Form.useForm<PolicyForm>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const next = await pageActionApi.overview();
      setData(next);
      setPageId((current) => current && next.pages.some((page) => page.pageId === current)
        ? current : next.pages[0]?.pageId);
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const actions = useMemo(() => {
    const normalized = keyword.trim().toLowerCase();
    return (data?.pages.find((page) => page.pageId === pageId)?.actions || []).filter((action) =>
      !normalized || `${action.toolCode} ${action.name} ${action.description}`.toLowerCase().includes(normalized));
  }, [data?.pages, keyword, pageId]);

  const save = async () => {
    if (!pending) return;
    const values = await form.validateFields();
    setSaving(true);
    try {
      const next = await pageActionApi.update(pending.action, pending.enabled, values.reason.trim());
      setData(next);
      setPending(undefined);
      form.resetFields();
      message.success(t('pageActions.saved'));
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setSaving(false);
    }
  };

  const columns: ColumnsType<PageActionItem> = [
    {
      title: t('pageActions.columns.action'), key: 'action',
      render: (_, action) => <div className="oa-page-actions-name"><strong>{toolName(action, t)}</strong><code>{action.toolCode}</code><small>{toolDescription(action, t)}</small></div>,
    },
    {
      title: t('pageActions.columns.risk'), dataIndex: 'riskLevel', width: 92,
      render: (risk: PageActionItem['riskLevel']) => <Tag color={risk === 'L2' ? 'red' : risk === 'L1' ? 'gold' : 'green'}>{risk}</Tag>,
    },
    {
      title: t('pageActions.columns.policy'), key: 'policy', width: 190,
      render: (_, action) => <Space wrap size={[4, 4]}><Tag>{t(`aiPermission.sideEffect.${action.sideEffect}`)}</Tag><Tag>{t(`aiPermission.confirmation.${action.confirmationPolicy}`)}</Tag></Space>,
    },
    {
      title: t('pageActions.columns.permissions'), dataIndex: 'requiredPermissions', width: 220,
      render: (permissions: string[]) => <Space wrap size={[4, 4]}>{permissions.map((permission) => <Tag key={permission}>{permission}</Tag>)}</Space>,
    },
    {
      title: t('pageActions.columns.status'), key: 'status', width: 150,
      render: (_, action) => <Space><Switch checked={action.enabled} disabled={!data?.canManage} onChange={(enabled) => { setPending({ action, enabled }); form.resetFields(); }} /><Typography.Text type={action.enabled ? 'success' : 'secondary'}>{t(action.enabled ? 'pageActions.enabled' : 'pageActions.disabled')}</Typography.Text></Space>,
    },
  ];

  return (
    <section className="oa-page-actions-page">
      <header className="oa-page-actions-header">
        <div className="oa-page-actions-heading">
          <span><OaIcon name="page-actions" size={24} /></span>
          <div><Typography.Text className="oa-page-actions-eyebrow">{t('pageActions.eyebrow')}</Typography.Text><Typography.Title level={3}>{t('pageActions.title')}</Typography.Title><Typography.Paragraph>{t('pageActions.description')}</Typography.Paragraph></div>
        </div>
        <Button icon={<OaIcon name="reload" />} loading={loading} onClick={() => void load()}>{t('common.refresh')}</Button>
      </header>

      <div className="oa-page-actions-stats">
        <Card><Statistic title={t('pageActions.stats.pages')} value={data?.pages.length || 0} prefix={<OaIcon name="page-actions" />} /></Card>
        <Card><Statistic title={t('pageActions.stats.total')} value={data?.total || 0} /></Card>
        <Card><Statistic title={t('pageActions.stats.enabled')} value={data?.enabled || 0} /></Card>
        <Card><Statistic title={t('pageActions.stats.disabled')} value={data?.disabled || 0} /></Card>
      </div>

      <Card className="oa-page-actions-content" title={t('pageActions.catalog')} extra={<Tag color="blue">{t('pageActions.codeOwned')}</Tag>}>
        <Alert showIcon type="info" title={t('pageActions.boundaryTitle')} description={t('pageActions.boundaryDescription')} />
        <div className="oa-page-actions-toolbar">
          <Select value={pageId} onChange={setPageId} options={(data?.pages || []).map((page) => ({ value: page.pageId, label: t(`oa.menu.${page.pageId}`, { defaultValue: page.pageId }) }))} aria-label={t('pageActions.pageSelect')} />
          <Input allowClear value={keyword} onChange={(event) => setKeyword(event.target.value)} prefix={<OaIcon name="search" />} placeholder={t('pageActions.search')} />
        </div>
        <ResponsiveTable<PageActionItem> rowKey={(action) => `${action.pageId}:${action.toolCode}`} columns={columns} dataSource={actions} loading={loading} pagination={false} locale={{ emptyText: t('pageActions.empty') }} />
      </Card>

      <Modal open={Boolean(pending)} title={t(pending?.enabled ? 'pageActions.enableTitle' : 'pageActions.disableTitle')} okText={t('common.confirm')} cancelText={t('common.cancel')} confirmLoading={saving} onOk={() => void save()} onCancel={() => { setPending(undefined); form.resetFields(); }}>
        <Alert showIcon type={pending?.enabled ? 'info' : 'warning'} title={t(pending?.enabled ? 'pageActions.enableHint' : 'pageActions.disableHint')} />
        <Form form={form} layout="vertical"><Form.Item name="reason" label={t('pageActions.reason')} rules={[{ required: true, min: 2, max: 300, message: t('pageActions.reasonRequired') }]}><Input.TextArea rows={4} maxLength={300} showCount /></Form.Item></Form>
      </Modal>
    </section>
  );
}

function toolName(action: PageActionItem, t: ReturnType<typeof useTranslation>['t']) {
  return t(`aiPermission.tools.${action.toolCode.replaceAll('.', '_')}.name`, { defaultValue: action.name });
}

function toolDescription(action: PageActionItem, t: ReturnType<typeof useTranslation>['t']) {
  return t(`aiPermission.tools.${action.toolCode.replaceAll('.', '_')}.description`, { defaultValue: action.description });
}

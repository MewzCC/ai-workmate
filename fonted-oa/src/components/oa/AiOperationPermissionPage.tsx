import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import {
  Alert, App, Badge, Button, Card, Checkbox, Empty, Segmented, Space, Spin, Switch, Tag, Typography,
} from 'antd';
import { OaIcon } from '@/components/OaIcon';
import {
  aiOperationPermissionApi,
  type AiOperationPermissionOverview,
} from '@/lib/aiOperationPermissionApi';
import { formatOaApiError } from '@/lib/oaApi';
import { message } from '@/lib/antdMessage';

type Role = AiOperationPermissionOverview['roles'][number];
type Tool = AiOperationPermissionOverview['tools'][number];

export default function AiOperationPermissionPage() {
  const { t } = useTranslation();
  const { modal } = App.useApp();
  const [data, setData] = useState<AiOperationPermissionOverview>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [selectedRoleCode, setSelectedRoleCode] = useState('');
  const [selectedToolCodes, setSelectedToolCodes] = useState<string[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const next = await aiOperationPermissionApi.overview();
      setData(next);
      setSelectedRoleCode((current) => current || next.roles[0]?.code || '');
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  const selectedRole = useMemo(
    () => data?.roles.find((role) => role.code === selectedRoleCode),
    [data?.roles, selectedRoleCode],
  );
  useEffect(() => {
    setSelectedToolCodes(selectedRole?.toolCodes || []);
  }, [selectedRole]);

  const updateTenantPolicy = async (enabled: boolean, writeToolsEnabled: boolean) => {
    setSaving(true);
    try {
      setData(await aiOperationPermissionApi.updateTenantPolicy(enabled, writeToolsEnabled));
      message.success(t('aiPermission.saved'));
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setSaving(false);
    }
  };

  const confirmWriteGate = (checked: boolean) => {
    if (!checked) {
      void updateTenantPolicy(data?.tenantPolicy.enabled || false, false);
      return;
    }
    modal.confirm({
      title: t('aiPermission.writeConfirmTitle'),
      content: t('aiPermission.writeConfirmContent'),
      okText: t('aiPermission.enable'),
      cancelText: t('common.cancel'),
      onOk: () => updateTenantPolicy(data?.tenantPolicy.enabled || false, true),
    });
  };

  const updateTool = async (tool: Tool, checked: boolean) => {
    const run = async () => {
      setSaving(true);
      try {
        setData(await aiOperationPermissionApi.updateToolStatus(tool.code, checked));
        message.success(t('aiPermission.saved'));
      } catch (error) {
        message.error(formatOaApiError(error));
      } finally {
        setSaving(false);
      }
    };
    if (checked && tool.sideEffect === 'SINGLE_WRITE') {
      modal.confirm({
        title: t('aiPermission.toolConfirmTitle'),
        content: t('aiPermission.toolConfirmContent', { name: toolName(tool, t) }),
        okText: t('aiPermission.enable'),
        cancelText: t('common.cancel'),
        onOk: run,
      });
    } else {
      await run();
    }
  };

  const saveRole = async () => {
    if (!selectedRole) return;
    setSaving(true);
    try {
      setData(await aiOperationPermissionApi.updateRoleTools(selectedRole.code, selectedToolCodes));
      message.success(t('aiPermission.roleSaved'));
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setSaving(false);
    }
  };

  if (loading && !data) {
    return <div className="oa-ai-permission-loading"><Spin size="large" /></div>;
  }
  if (!data) {
    return <div className="oa-ai-permission-loading"><Empty description={t('aiPermission.loadFailed')} /></div>;
  }

  const runtimeReady = data.runtime.agentEnabled && data.runtime.planningEnabled;
  return (
    <section className="oa-ai-permission-page">
      <header className="oa-ai-permission-header">
        <div className="oa-ai-permission-heading">
          <span className="oa-ai-permission-heading-icon"><OaIcon name="safety" size={24} /></span>
          <div>
            <Typography.Text className="oa-ai-permission-eyebrow">{t('aiPermission.eyebrow')}</Typography.Text>
            <Typography.Title level={3}>{t('aiPermission.title')}</Typography.Title>
            <Typography.Paragraph>{t('aiPermission.description')}</Typography.Paragraph>
          </div>
        </div>
        <Button icon={<OaIcon name="reload" />} onClick={() => void load()}>{t('common.refresh')}</Button>
      </header>

      <div className="oa-ai-permission-runtime">
        <RuntimeBadge label={t('aiPermission.runtime.agent')} ready={data.runtime.agentEnabled} />
        <RuntimeBadge label={t('aiPermission.runtime.planning')} ready={data.runtime.planningEnabled} />
        <RuntimeBadge label={t('aiPermission.runtime.execution')} ready={data.runtime.executionEnabled} />
        <RuntimeBadge label={t('aiPermission.runtime.write')} ready={data.runtime.writeToolsEnabled} />
      </div>

      {!runtimeReady && <Alert showIcon type="warning" message={t('aiPermission.runtimeBlocked')} />}

      <div className="oa-ai-permission-grid">
        <Card className="oa-ai-permission-tools" title={t('aiPermission.toolCatalog')} extra={<Tag>{data.tools.length}</Tag>}>
          <div className="oa-ai-permission-tenant-switches">
            <div><strong>{t('aiPermission.tenantAgent')}</strong><small>{t('aiPermission.tenantAgentHint')}</small></div>
            <Switch loading={saving} checked={data.tenantPolicy.enabled} onChange={(checked) => void updateTenantPolicy(checked, data.tenantPolicy.writeToolsEnabled)} />
            <div><strong>{t('aiPermission.tenantWrite')}</strong><small>{t('aiPermission.tenantWriteHint')}</small></div>
            <Switch loading={saving} checked={data.tenantPolicy.writeToolsEnabled} onChange={confirmWriteGate} />
          </div>
          <div className="oa-ai-permission-tool-list">
            {data.tools.map((tool) => (
              <article className="oa-ai-permission-tool" key={tool.code}>
                <div className="oa-ai-permission-tool-main">
                  <span className={`oa-ai-permission-risk is-${tool.riskLevel.toLowerCase()}`}>{tool.riskLevel}</span>
                  <div><strong>{toolName(tool, t)}</strong><code>{tool.code}</code></div>
                  <Switch disabled={!tool.platformEnabled} loading={saving} checked={tool.tenantEnabled && tool.platformEnabled} onChange={(checked) => void updateTool(tool, checked)} />
                </div>
                <Typography.Paragraph ellipsis={{ rows: 2 }}>{toolDescription(tool, t)}</Typography.Paragraph>
                <Space wrap size={[4, 4]}>
                  <Tag>{t(`aiPermission.sideEffect.${tool.sideEffect}`)}</Tag>
                  <Tag>{t(`aiPermission.confirmation.${tool.confirmationPolicy}`)}</Tag>
                  {tool.pageIds.map((page) => <Tag key={page}>{t(`oa.menu.${page}`, { defaultValue: page })}</Tag>)}
                </Space>
                <Badge status={tool.effectiveEnabled ? 'success' : 'default'} text={t(tool.effectiveEnabled ? 'aiPermission.effective' : 'aiPermission.blocked')} />
              </article>
            ))}
          </div>
        </Card>

        <Card className="oa-ai-permission-roles" title={t('aiPermission.roleMatrix')}>
          <Segmented block value={selectedRoleCode} options={data.roles.map((role) => ({ label: role.name, value: role.code }))} onChange={(value) => setSelectedRoleCode(String(value))} />
          {selectedRole && <RoleEditor role={selectedRole} tools={data.tools} values={selectedToolCodes} onChange={setSelectedToolCodes} t={t} />}
          <Button type="primary" block loading={saving} disabled={!selectedRole || selectedRole.immutable} onClick={() => void saveRole()}>{t('aiPermission.saveRole')}</Button>
        </Card>
      </div>
    </section>
  );
}

function RuntimeBadge({ label, ready }: { label: string; ready: boolean }) {
  return <div><Badge status={ready ? 'success' : 'default'} /><span>{label}</span><strong>{ready ? 'ON' : 'OFF'}</strong></div>;
}

function RoleEditor({ role, tools, values, onChange, t }: { role: Role; tools: Tool[]; values: string[]; onChange: (values: string[]) => void; t: TFunction }) {
  return <div className="oa-ai-permission-role-editor"><div><Typography.Title level={4}>{role.name}</Typography.Title><Typography.Paragraph>{role.description}</Typography.Paragraph>{role.immutable && <Alert showIcon type="info" message={t('aiPermission.superAdminHint')} />}</div><Checkbox.Group value={values} onChange={(next) => onChange(next as string[])} disabled={role.immutable}>{tools.map((tool) => <Checkbox key={tool.code} value={tool.code} disabled={!role.eligibleToolCodes.includes(tool.code)}><span>{toolName(tool, t)}</span><small>{tool.code}</small></Checkbox>)}</Checkbox.Group></div>;
}

function toolName(tool: Tool, t: TFunction) {
  return t(`aiPermission.tools.${tool.code.replaceAll('.', '_')}.name`, { defaultValue: tool.name });
}

function toolDescription(tool: Tool, t: TFunction) {
  return t(`aiPermission.tools.${tool.code.replaceAll('.', '_')}.description`, { defaultValue: tool.description });
}

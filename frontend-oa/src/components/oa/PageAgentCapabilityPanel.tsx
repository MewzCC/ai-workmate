import { Button, Card, Descriptions, Space, Spin, Tag, Tooltip } from 'antd';
import { useTranslation } from 'react-i18next';
import { OaIcon } from '@/components/OaIcon';
import type { OaRole, PageCapability, PageCapabilityTool } from '@/types/oa';
import { agentToolTranslationKey } from '@/lib/agentToolPresentation';
import { PageToolAvailabilityNotice } from './PageToolAvailabilityNotice';

interface PageAgentCapabilityPanelProps {
  pageTitle: string;
  role: OaRole;
  capability: PageCapability | null;
  loading: boolean;
  commandDisabled: boolean;
  onSelectTool: (prompt: string) => void;
}

export default function PageAgentCapabilityPanel({
  pageTitle,
  role,
  capability,
  loading,
  commandDisabled,
  onSelectTool,
}: PageAgentCapabilityPanelProps) {
  const { t } = useTranslation();

  const localizedTool = (tool: PageCapabilityTool) => {
    const key = agentToolTranslationKey(tool.code);
    return {
      name: t(`aiPermission.tools.${key}.name`, { defaultValue: tool.name }),
      description: t(`aiPermission.tools.${key}.description`, { defaultValue: tool.description }),
    };
  };

  const scope = capability?.effectiveDataScopes.length
    ? capability.effectiveDataScopes.join(' · ')
    : t('oa.ai.serverVerifiedScope');

  return (
    <>
      <Card size="small" className="oa-ai-context-card" title={t('oa.ai.contextTitle')}>
        <Descriptions size="small" column={1} items={[
          { key: 'page', label: t('oa.ai.currentPage'), children: pageTitle },
          { key: 'role', label: t('oa.ai.currentRole'), children: role },
          { key: 'scope', label: t('oa.ai.dataScope'), children: scope },
          { key: 'boundary', label: t('oa.ai.securityBoundary'), children: t('oa.ai.gatewayEnforced') },
        ]} />
        {loading ? <Spin size="small" /> : null}
        {!loading && capability ? (
          <>
            <Space wrap className="oa-ai-tags">
              {capability.tools.map((tool) => {
                const localized = localizedTool(tool);
                const sideEffect = t(`aiPermission.sideEffect.${tool.sideEffect}`);
                const confirmation = t(`aiPermission.confirmation.${tool.confirmationPolicy}`);
                return (
                  <Tooltip
                    key={tool.code}
                    title={t('oa.ai.toolSafetySummary', {
                      description: localized.description,
                      sideEffect,
                      confirmation,
                    })}
                  >
                    <Tag color={tool.sideEffect === 'SINGLE_WRITE' ? 'gold' : 'blue'}>
                      {t('oa.ai.toolTag', {
                        name: localized.name,
                        sideEffect,
                        riskLevel: tool.riskLevel,
                      })}
                    </Tag>
                  </Tooltip>
                );
              })}
            </Space>
            <PageToolAvailabilityNotice capability={capability} />
          </>
        ) : null}
      </Card>

      {!loading && Boolean(capability?.tools.length) ? (
        <Card size="small" title={t('oa.ai.quickCommands')}>
          <Space wrap>
            {capability?.tools.map((tool) => {
              const name = localizedTool(tool).name;
              const prompt = t('oa.ai.toolCommandPrompt', { name });
              return (
                <Button
                  key={tool.code}
                  icon={<OaIcon name="ai" />}
                  disabled={commandDisabled}
                  onClick={() => onSelectTool(prompt)}
                >
                  {t('oa.ai.useTool', { name })}
                </Button>
              );
            })}
          </Space>
        </Card>
      ) : null}
    </>
  );
}

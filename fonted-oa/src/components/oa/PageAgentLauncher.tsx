'use client';

import {
  forwardRef,
  useImperativeHandle,
  useState,
} from 'react';
import { useTranslation } from 'react-i18next';
import { FloatButton } from 'antd';
import { OaIcon } from '@/components/OaIcon';
import type { OaRole } from '@/types/oa';
import AIOperationDrawer from './AIOperationDrawer';
import AiMiniPanel from './AiMiniPanel';
import type { PageAgentContextSnapshot } from './PageAgentContext';

const AGENT_WORKSPACE_PAGE_ID = 'ai-workspace';

export interface PageAgentLauncherHandle {
  open: (prompt?: string) => void;
}

interface PageAgentLauncherProps {
  role: OaRole;
  pageId: string;
  pageTitle: string;
  pageContext?: PageAgentContextSnapshot;
  miniEnabled: boolean;
  onPageRefresh?: () => void;
}

/**
 * 全页面共用的 Agent 入口。业务页面只提供当前页面上下文，
 * 抽屉、迷你面板和提示词生命周期统一在这里管理。
 */
const PageAgentLauncher = forwardRef<PageAgentLauncherHandle, PageAgentLauncherProps>(
  function PageAgentLauncher({ role, pageId, pageTitle, pageContext, miniEnabled, onPageRefresh }, ref) {
    const { t } = useTranslation();
    const [open, setOpen] = useState(false);
    const [drawerPresent, setDrawerPresent] = useState(false);
    const [prompt, setPrompt] = useState('');
    const enabled = pageId !== AGENT_WORKSPACE_PAGE_ID;

    useImperativeHandle(ref, () => ({
      open: (initialPrompt?: string) => {
        if (!enabled) return;
        setPrompt(initialPrompt || '');
        setOpen(true);
      },
    }), [enabled]);

    const openAgent = (initialPrompt?: string) => {
      setPrompt(initialPrompt || '');
      setOpen(true);
    };

    return (
      <>
        {enabled && <FloatButton
          type="primary"
          icon={<OaIcon name="ai" size={20} />}
          tooltip={t('oa.ai.openPanel')}
          aria-label={t('oa.ai.openPanel')}
          onClick={() => openAgent()}
        />}

        {enabled && miniEnabled && !open && !drawerPresent ? (
          <AiMiniPanel onOpenAi={openAgent} />
        ) : null}

        <AIOperationDrawer
          open={enabled && open}
          role={role}
          pageId={pageId}
          pageTitle={pageTitle}
          pageContext={pageContext}
          initialPrompt={prompt}
          onClose={() => setOpen(false)}
          onOpenChangeComplete={setDrawerPresent}
          onExecutionCompleted={onPageRefresh}
        />
      </>
    );
  },
);

export default PageAgentLauncher;

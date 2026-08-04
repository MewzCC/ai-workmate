'use client';

import { Anchor, Button, Drawer, Space, Tag, Typography } from 'antd';
import { Trans, useTranslation } from 'react-i18next';
import type { OaRole } from '@/types/oa';
import { OaIcon } from '@/components/OaIcon';

interface HelpDrawerProps {
  open: boolean;
  role: OaRole;
  onClose: () => void;
  onOpenAi: (prompt?: string) => void;
}

interface HelpSection {
  id: string;
  icon: React.ReactNode;
  title: string;
  body: React.ReactNode;
}

const HELP_TRANS_COMPONENTS = {
  strong: <strong />,
  kbd: <Kbd />,
  code: <code />,
};

export default function HelpDrawer({ open, role, onClose, onOpenAi }: HelpDrawerProps) {
  const { t } = useTranslation();

  const sections: HelpSection[] = [
    {
      id: 'overview',
      icon: <OaIcon name="dictionary" />,
      title: t('oa.help.sections.overview.title'),
      body: (
        <Typography>
          <p>{t('oa.help.sections.overview.intro')}</p>
          <ul>
            <li><Trans i18nKey="oa.help.sections.overview.item0" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.overview.item1" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.overview.item2" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.overview.item3" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.overview.item4" components={HELP_TRANS_COMPONENTS} /></li>
          </ul>
        </Typography>
      ),
    },
    {
      id: 'navigation',
      icon: <OaIcon name="process" />,
      title: t('oa.help.sections.navigation.title'),
      body: (
        <Typography>
          <ul>
            <li><Trans i18nKey="oa.help.sections.navigation.item0" components={HELP_TRANS_COMPONENTS} values={{ pathVar: '<pageId>' }} /></li>
            <li><Trans i18nKey="oa.help.sections.navigation.item1" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.navigation.item2" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.navigation.item3" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.navigation.item4" components={HELP_TRANS_COMPONENTS} /></li>
          </ul>
        </Typography>
      ),
    },
    {
      id: 'ai-workspace',
      icon: <OaIcon name="ai" />,
      title: t('oa.help.sections.ai-workspace.title'),
      body: (
        <Typography>
          <ul>
            <li><Trans i18nKey="oa.help.sections.ai-workspace.item0" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.ai-workspace.item1" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.ai-workspace.item2" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.ai-workspace.item3" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.ai-workspace.item4" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.ai-workspace.item5" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.ai-workspace.item6" components={HELP_TRANS_COMPONENTS} /></li>
          </ul>
        </Typography>
      ),
    },
    {
      id: 'ai-drawer',
      icon: <OaIcon name="ai" />,
      title: t('oa.help.sections.ai-drawer.title'),
      body: (
        <Typography>
          <ul>
            <li><Trans i18nKey="oa.help.sections.ai-drawer.item0" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.ai-drawer.item1" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.ai-drawer.item2" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.ai-drawer.item3" components={HELP_TRANS_COMPONENTS} /></li>
          </ul>
        </Typography>
      ),
    },
    {
      id: 'appearance',
      icon: <OaIcon name="appearance" />,
      title: t('oa.help.sections.appearance.title'),
      body: (
        <Typography>
          <ul>
            <li><Trans i18nKey="oa.help.sections.appearance.item0" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.appearance.item1" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.appearance.item2" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.appearance.item3" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.appearance.item4" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.appearance.item5" components={HELP_TRANS_COMPONENTS} /></li>
          </ul>
        </Typography>
      ),
    },
    {
      id: 'permissions',
      icon: <OaIcon name="lock" />,
      title: t('oa.help.sections.permissions.title'),
      body: (
        <Typography>
          <p>{t('oa.help.sections.permissions.currentRole')}<Tag color="blue">{t(`oa.help.roles.${role}`)}</Tag></p>
          <ul>
            <li><Trans i18nKey="oa.help.sections.permissions.item0" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.permissions.item1" components={HELP_TRANS_COMPONENTS} values={{ routeVar: '<routeKey>' }} /></li>
            <li><Trans i18nKey="oa.help.sections.permissions.item2" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.permissions.item3" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.permissions.item4" components={HELP_TRANS_COMPONENTS} /></li>
          </ul>
        </Typography>
      ),
    },
    {
      id: 'security',
      icon: <OaIcon name="access-control" />,
      title: t('oa.help.sections.security.title'),
      body: (
        <Typography>
          <ul>
            <li><Trans i18nKey="oa.help.sections.security.item0" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.security.item1" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.security.item2" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.security.item3" components={HELP_TRANS_COMPONENTS} /></li>
          </ul>
        </Typography>
      ),
    },
    {
      id: 'shortcuts',
      icon: <OaIcon name="lock" />,
      title: t('oa.help.sections.shortcuts.title'),
      body: (
        <Typography>
          <ul>
            <li><Trans i18nKey="oa.help.sections.shortcuts.item0" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.shortcuts.item1" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.shortcuts.item2" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.shortcuts.item3" components={HELP_TRANS_COMPONENTS} /></li>
          </ul>
        </Typography>
      ),
    },
    {
      id: 'tips',
      icon: <OaIcon name="ai" />,
      title: t('oa.help.sections.tips.title'),
      body: (
        <Typography>
          <ul>
            <li><Trans i18nKey="oa.help.sections.tips.item0" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.tips.item1" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.tips.item2" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.tips.item3" components={HELP_TRANS_COMPONENTS} /></li>
            <li><Trans i18nKey="oa.help.sections.tips.item4" components={HELP_TRANS_COMPONENTS} /></li>
          </ul>
        </Typography>
      ),
    },
  ];

  const anchorItems = sections.map((section) => ({
    key: section.id,
    href: `#help-${section.id}`,
    title: (
      <Space size={6}>
        {section.icon}
        <span>{section.title}</span>
      </Space>
    ),
  }));

  return (
    <Drawer
      title={t('oa.help.title')}
      placement="right"
      size="default"
      styles={{ wrapper: { width: 680 } }}
      open={open}
      onClose={onClose}
      destroyOnHidden
    >
      <div className="oa-help-drawer">
        <aside className="oa-help-anchor">
          <Anchor
            items={anchorItems}
            offsetTop={16}
            affix={false}
            getContainer={() => document.querySelector('.ant-drawer-body') as HTMLElement}
          />
        </aside>
        <article className="oa-help-content">
          {sections.map((section) => (
            <section key={section.id} id={`help-${section.id}`} className="oa-help-section">
              <h2 className="oa-help-section-title">
                <Space size={8}>
                  {section.icon}
                  <span>{section.title}</span>
                </Space>
              </h2>
              <div className="oa-help-section-body">{section.body}</div>
            </section>
          ))}
          <section className="oa-help-footer">
            <Typography.Paragraph type="secondary">
              {t('oa.help.footerText')}
            </Typography.Paragraph>
            <Button
              type="primary"
              icon={<OaIcon name="ai" />}
              onClick={() => {
                onClose();
                onOpenAi(t('oa.help.askAiPrompt'));
              }}
            >
              {t('oa.help.askAi')}
            </Button>
          </section>
        </article>
      </div>
    </Drawer>
  );
}

function Kbd({ children }: { children?: React.ReactNode }) {
  return <kbd className="oa-help-kbd">{children}</kbd>;
}

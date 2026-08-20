import type { ReactNode } from 'react';
import { Typography } from 'antd';
import { OaIcon } from '@/components/OaIcon';

interface AdminAssetsPageShellProps {
  title: string;
  description: string;
  eyebrow: string;
  actions?: ReactNode;
  children: ReactNode;
}

export default function AdminAssetsPageShell({
  title,
  description,
  eyebrow,
  actions,
  children,
}: AdminAssetsPageShellProps) {
  return (
    <section className="oa-admin-assets-page">
      <header className="oa-admin-assets-heading">
        <div className="oa-admin-assets-heading__identity">
          <span className="oa-admin-assets-heading__icon" aria-hidden="true">
            <OaIcon name="assets" size={21} />
          </span>
          <div>
            <Typography.Text className="oa-admin-assets-heading__eyebrow">
              {eyebrow}
            </Typography.Text>
            <Typography.Title level={3}>{title}</Typography.Title>
            <Typography.Paragraph type="secondary">{description}</Typography.Paragraph>
          </div>
        </div>
        {actions ? <div className="oa-admin-assets-heading__actions">{actions}</div> : null}
      </header>
      <div className="oa-admin-assets-content">{children}</div>
    </section>
  );
}

import type { ReactNode } from 'react';
import { Typography } from 'antd';
import { OaIcon } from '@/components/OaIcon';

interface AttendancePageShellProps {
  title: string;
  description: string;
  eyebrow: string;
  actions?: ReactNode;
  children: ReactNode;
}

export default function AttendancePageShell({
  title,
  description,
  eyebrow,
  actions,
  children,
}: AttendancePageShellProps) {
  return (
    <section className="oa-attendance-page">
      <header className="oa-attendance-heading">
        <div className="oa-attendance-heading__identity">
          <span className="oa-attendance-heading__icon" aria-hidden="true">
            <OaIcon name="attendance" size={21} />
          </span>
          <div>
            <Typography.Text className="oa-attendance-heading__eyebrow">
              {eyebrow}
            </Typography.Text>
            <Typography.Title level={3}>{title}</Typography.Title>
            <Typography.Paragraph type="secondary">{description}</Typography.Paragraph>
          </div>
        </div>
        {actions ? <div className="oa-attendance-heading__actions">{actions}</div> : null}
      </header>
      <div className="oa-attendance-content">{children}</div>
    </section>
  );
}

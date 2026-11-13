'use client';

import type { ReactNode } from 'react';
import { Typography } from 'antd';

interface ApprovalConfigShellProps {
  eyebrow: string;
  title: string;
  description: string;
  actions?: ReactNode;
  children: ReactNode;
}

/**
 * 流程审批配置页统一外壳：页头 Hero + 内容区，
 * 与审批列表同源的满高工作台布局，内容卡片紧邻页头填充剩余工作区。
 */
export default function ApprovalConfigShell({
  eyebrow,
  title,
  description,
  actions,
  children,
}: ApprovalConfigShellProps) {
  return (
    <section className="leave-list-workbench">
      <header className="leave-list-hero">
        <div>
          <span className="leave-list-hero__kicker">{eyebrow}</span>
          <Typography.Title level={2}>{title}</Typography.Title>
          <Typography.Paragraph>{description}</Typography.Paragraph>
        </div>
        {actions ? <div className="approval-config-hero__actions">{actions}</div> : null}
      </header>
      {children}
    </section>
  );
}
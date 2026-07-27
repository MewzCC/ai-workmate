import type { CSSProperties, SVGProps } from 'react';

export const iconFontNames = [
  'zhinengyouhua',
  'main_icon',
  'CSV',
  'PDF',
  'PNG',
  'JPG',
  'TXT',
  'yonghuguanli',
  'jiaoseguanli',
  'add_oa',
  'pause',
  'xuanxiangka',
  'daochu',
  'daimayunhang',
  'jieguoyulan',
  'siyouguize',
  'shouji',
  'youxiang',
  'dashboard',
  'business',
  'platform',
  'settings',
  'approval',
  'hr',
  'assets',
  'finance',
  'integration',
  'todo',
  'messages',
  'form',
  'process',
  'rules',
  'organization',
  'employee-files',
  'attendance',
  'employee-change',
  'asset-ledger',
  'meeting-room',
  'visitor',
  'seal',
  'expense',
  'budget',
  'contracts',
  'suppliers',
  'api-center',
  'page-actions',
  'runtime-logs',
  'sandbox',
  'access-control',
  'data-permission',
  'audit',
  'tenant',
  'dictionary',
  'help',
  'notification',
  'appearance',
  'logout',
  'edit',
  'delete',
  'upload',
  'save',
  'search',
  'attachment',
  'send',
  'more',
  'copy',
  'reload',
  'lock',
  'history',
] as const;

export type IconFontName = (typeof iconFontNames)[number];

export interface IconFontProps
  extends Omit<SVGProps<SVGSVGElement>, 'children' | 'name'> {
  name: IconFontName;
  size?: CSSProperties['width'];
  title?: string;
}

export function IconFont({
  name,
  size = '1em',
  title,
  style,
  ...props
}: IconFontProps) {
  return (
    <svg
      {...props}
      aria-hidden={title ? undefined : true}
      aria-label={title}
      focusable="false"
      role={title ? 'img' : undefined}
      style={{
        display: 'inline-block',
        width: size,
        height: size,
        flex: 'none',
        fill: 'currentColor',
        verticalAlign: '-0.125em',
        ...style,
      }}
    >
      {title ? <title>{title}</title> : null}
      <use href={`#icon-${name}`} />
    </svg>
  );
}

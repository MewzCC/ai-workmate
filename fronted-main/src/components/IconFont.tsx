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

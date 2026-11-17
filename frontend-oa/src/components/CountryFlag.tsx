import type { AppLocale } from '@/i18n';

interface FlagProps {
  className?: string;
}

/** 生成五角星路径（中心 cx,cy，外接圆半径 r） */
function starPath(cx: number, cy: number, r: number): string {
  const inner = r * 0.382;
  let d = '';
  for (let i = 0; i < 10; i += 1) {
    const angle = (Math.PI / 5) * i - Math.PI / 2;
    const radius = i % 2 === 0 ? r : inner;
    const x = cx + Math.cos(angle) * radius;
    const y = cy + Math.sin(angle) * radius;
    d += `${i === 0 ? 'M' : 'L'}${x.toFixed(2)} ${y.toFixed(2)} `;
  }
  return `${d}Z`;
}

/** 中国国旗（内联 SVG，跨平台一致显示，不依赖系统 emoji 字体） */
export function ChinaFlag({ className }: FlagProps) {
  return (
    <svg viewBox="0 0 30 20" className={className} aria-hidden="true" focusable="false">
      <rect width="30" height="20" fill="#DE2910" />
      <path d={starPath(2.8, 2.8, 2.2)} fill="#FFDE00" />
      <path d={starPath(6.2, 1.7, 1.05)} fill="#FFDE00" />
      <path d={starPath(7.4, 3.0, 1.05)} fill="#FFDE00" />
      <path d={starPath(7.4, 4.6, 1.05)} fill="#FFDE00" />
      <path d={starPath(6.2, 5.9, 1.05)} fill="#FFDE00" />
    </svg>
  );
}

/** 美国国旗（内联 SVG） */
export function UsFlag({ className }: FlagProps) {
  const stripeH = 20 / 13;
  return (
    <svg viewBox="0 0 30 20" className={className} aria-hidden="true" focusable="false">
      <rect width="30" height="20" fill="#FFFFFF" />
      {Array.from({ length: 7 }, (_, i) => (
        <rect key={i} x="0" y={i * stripeH * 2} width="30" height={stripeH} fill="#B22234" />
      ))}
      <rect width="12" height="10.77" fill="#3C3B6E" />
      {Array.from({ length: 5 }, (_, row) =>
        Array.from({ length: 6 }, (_, col) => (
          <circle key={`${row}-${col}`} cx={1 + col * 2} cy={1.08 + row * 2.15} r="0.55" fill="#FFFFFF" />
        )),
      )}
    </svg>
  );
}

/** 按语言自动识别并返回对应国旗 */
export function FlagIcon({ locale, className }: { locale: AppLocale; className?: string }) {
  return locale === 'zh-CN' ? <ChinaFlag className={className} /> : <UsFlag className={className} />;
}

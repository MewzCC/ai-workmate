import { useEffect, useState } from 'react';

/**
 * OA 全局移动端断点：与 AdminLayout 侧栏自动收缩、globals.css 媒体查询共用同一阈值，
 * 移动端表格转卡片（ResponsiveTable）等能力必须复用此常量，避免多处断点漂移。
 */
export const OA_MOBILE_MEDIA_QUERY = '(max-width: 720px)';

function readMatches(query: string): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false;
  }
  return window.matchMedia(query).matches;
}

/** 是否处于移动端视口（≤720px）。SSR/首帧安全：同步读取 matchMedia，不会出现闪烁。 */
export function useIsMobile(query: string = OA_MOBILE_MEDIA_QUERY): boolean {
  const [isMobile, setIsMobile] = useState(() => readMatches(query));

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return undefined;
    }
    const media = window.matchMedia(query);
    const handleChange = (event: MediaQueryListEvent) => setIsMobile(event.matches);
    setIsMobile(media.matches);
    media.addEventListener('change', handleChange);
    return () => media.removeEventListener('change', handleChange);
  }, [query]);

  return isMobile;
}

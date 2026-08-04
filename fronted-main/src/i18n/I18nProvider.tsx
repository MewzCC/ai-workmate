import type { ReactNode } from 'react';
import { useState, useEffect } from 'react';
import { I18nextProvider } from 'react-i18next';
import i18n from './index';

interface Props {
  children: ReactNode;
}

/**
 * 初始化并挂载 i18next。必须在应用根节点包裹此 Provider。
 * 资源与语言检测在 `./index` 中完成配置。
 *
 * <p>i18next 初始化完成前不渲染 children，避免 `t(..., { returnObjects: true })`
 * 在未就绪时返回 key 字符串导致 `.map` 等调用崩溃。
 */
export default function I18nProvider({ children }: Props) {
  const [ready, setReady] = useState(i18n.isInitialized);

  useEffect(() => {
    if (ready) return;
    const onInitialized = () => setReady(true);
    i18n.on('initialized', onInitialized);
    // 兜底：某些情况下事件已触发，直接检查
    if (i18n.isInitialized) setReady(true);
    return () => {
      i18n.off('initialized', onInitialized);
    };
  }, [ready]);

  if (!ready) return null;
  return <I18nextProvider i18n={i18n}>{children}</I18nextProvider>;
}

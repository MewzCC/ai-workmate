import type { ReactNode } from 'react';
import { useState, useEffect } from 'react';
import { I18nextProvider } from 'react-i18next';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';
import i18n, { AppLocale, DEFAULT_LOCALE } from './index';
import { useLocale } from './useLocale';

const ANT_LOCALES: Record<AppLocale, typeof zhCN> = {
  'zh-CN': zhCN,
  'en-US': enUS,
};

interface Props {
  children: ReactNode;
}

/**
 * 初始化 i18next 并同步 Ant Design ConfigProvider locale。
 *
 * 说明：OA 工作台主题 ConfigProvider 位于 AdminLayout 内层（只配置 theme），
 * 此处在最外层注入 locale，内层 ConfigProvider 会继承 locale，确保
 * DatePicker / Pagination / Table / Modal / Notification 等内置组件随语言切换。
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
    if (i18n.isInitialized) setReady(true);
    return () => {
      i18n.off('initialized', onInitialized);
    };
  }, [ready]);

  if (!ready) return null;
  return (
    <I18nextProvider i18n={i18n}>
      <LocaleConfig>{children}</LocaleConfig>
    </I18nextProvider>
  );
}

function LocaleConfig({ children }: { children: ReactNode }) {
  const { locale } = useLocale();
  const antLocale = ANT_LOCALES[locale] ?? ANT_LOCALES[DEFAULT_LOCALE];
  return <ConfigProvider locale={antLocale}>{children}</ConfigProvider>;
}

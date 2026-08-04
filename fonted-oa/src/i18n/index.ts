import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import zhCN from './locales/zh-CN';
import enUS from './locales/en-US';

export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const;
export type AppLocale = (typeof SUPPORTED_LOCALES)[number];
export const DEFAULT_LOCALE: AppLocale = 'zh-CN';
export const LOCALE_STORAGE_KEY = 'workmeta-locale';

// i18next 内部 formatLanguageCode 会调用 Intl.getCanonicalLocales('zh-CN')，
// 标准实现返回 'zh-Hans-CN'（补全脚本子标签 Hans）。
// 若仅以 'zh-CN' 作为 resources key，规范化后的语言码无法命中资源，导致 t() 返回 key 字符串。
// 这里为中文资源补一份规范化别名，保证 resolve 链路能命中。
const zhCNResource = { translation: zhCN };
const enUSResource = { translation: enUS };

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      'zh-CN': zhCNResource,
      'zh-Hans-CN': zhCNResource,
      'en-US': enUSResource,
    },
    fallbackLng: DEFAULT_LOCALE,
    // 不设置 supportedLngs / nonExplicitSupportedLngs：
    // 资源为静态内联，fallbackLng 已可兜底未知语言；
    // 配置 supportedLngs 会在 toResolveHierarchy 中对 canonical 规范化后的语言码做校验，
    // 而 'zh-Hans-CN' / 'zh' 不在 ['zh-CN'] 列表内，会被全部拒绝，导致返回空数组。
    interpolation: {
      // React 已经默认转义，无需 i18next 再转义
      escapeValue: false,
    },
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: LOCALE_STORAGE_KEY,
      caches: ['localStorage'],
    },
    returnEmptyString: false,
  });

export default i18n;

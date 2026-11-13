import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import i18n, {
  AppLocale,
  DEFAULT_LOCALE,
  LOCALE_STORAGE_KEY,
  SUPPORTED_LOCALES,
} from './index';

function normalizeLocale(raw: string | undefined): AppLocale {
  if (!raw) return DEFAULT_LOCALE;
  const lower = raw.toLowerCase();
  if (lower.startsWith('zh')) return 'zh-CN';
  if (lower.startsWith('en')) return 'en-US';
  return DEFAULT_LOCALE;
}

/**
 * 读取、切换语言并持久化到 localStorage `workmeta-locale`。
 * 切换时同步 i18next 当前语言与 document.documentElement.lang。
 */
export function useLocale() {
  const { i18n: instance } = useTranslation();
  const [locale, setLocale] = useState<AppLocale>(() =>
    normalizeLocale(instance.language ?? i18n.language),
  );

  useEffect(() => {
    const handler = (lng: string) => setLocale(normalizeLocale(lng));
    i18n.on('languageChanged', handler);
    return () => {
      i18n.off('languageChanged', handler);
    };
  }, []);

  useEffect(() => {
    if (typeof document !== 'undefined') {
      document.documentElement.lang = locale;
    }
  }, [locale]);

  const changeLanguage = useCallback((next: AppLocale) => {
    const target = SUPPORTED_LOCALES.includes(next) ? next : DEFAULT_LOCALE;
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(LOCALE_STORAGE_KEY, target);
    }
    void i18n.changeLanguage(target);
  }, []);

  return { locale, changeLanguage };
}

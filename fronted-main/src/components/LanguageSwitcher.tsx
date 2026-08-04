import { Languages } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useLocale } from '@/i18n/useLocale';
import type { AppLocale } from '@/i18n';

interface Props {
  className?: string;
}

/**
 * 营销官网语言切换按钮：在 zh-CN / en-US 之间切换。
 */
export default function LanguageSwitcher({ className }: Props) {
  const { t } = useTranslation();
  const { locale, changeLanguage } = useLocale();
  const next: AppLocale = locale === 'zh-CN' ? 'en-US' : 'zh-CN';

  return (
    <button
      type="button"
      className={`wm-lang-toggle ${className ?? ''}`.trim()}
      onClick={() => changeLanguage(next)}
      aria-label={t('language.label')}
      title={t('language.label')}
    >
      <Languages className="h-5 w-5" />
      <span>{locale === 'zh-CN' ? 'EN' : '中'}</span>
    </button>
  );
}

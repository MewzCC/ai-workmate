import { Button } from 'antd';
import { Languages } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useLocale } from '@/i18n/useLocale';
import type { AppLocale } from '@/i18n';

/**
 * OA 工作台语言切换按钮：在 zh-CN / en-US 之间切换。
 * 与营销官网 LanguageSwitcher 保持一致：使用 lucide Languages 图标 + 当前可切换的目标语言缩写。
 * 样式与顶栏通知/头像按钮统一为胶囊形态，适配所有 OA 皮肤。
 */
export default function LanguageSwitcher() {
  const { t } = useTranslation();
  const { locale, changeLanguage } = useLocale();
  const next: AppLocale = locale === 'zh-CN' ? 'en-US' : 'zh-CN';

  return (
    <Button
      className="oa-lang-toggle"
      icon={<Languages className="h-4 w-4" />}
      onClick={() => changeLanguage(next)}
      aria-label={t('common.language')}
      title={t('common.language')}
    >
      {locale === 'zh-CN' ? 'EN' : '中'}
    </Button>
  );
}

import { Button, Dropdown } from 'antd';
import type { MenuProps } from 'antd';
import { Languages } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useLocale } from '@/i18n/useLocale';
import type { AppLocale } from '@/i18n';
import { SUPPORTED_LANGUAGES } from '@/i18n/languages';
import { FlagIcon } from '@/components/CountryFlag';

/**
 * OA 工作台语言切换下拉框，菜单项由 SUPPORTED_LANGUAGES 配置生成。
 * 按钮展示语言图标 + 当前语言国旗，下拉菜单展示各语言（国旗 + 母语名称），当前项高亮打勾。
 * 下拉菜单由 ConfigProvider token 驱动，随主题（含深色/壁纸）自动适配。
 */
export default function LanguageSwitcher() {
  const { t } = useTranslation();
  const { locale, changeLanguage } = useLocale();

  const items: MenuProps['items'] = SUPPORTED_LANGUAGES.map((option) => ({
    key: option.code,
    label: (
      <span className="oa-lang-menu-label">
        <FlagIcon locale={option.code} className="oa-lang-flag" />
        {t(option.labelKey)}
      </span>
    ),
  }));

  return (
    <Dropdown
      menu={{
        items,
        selectable: true,
        selectedKeys: [locale],
        onClick: ({ key }) => changeLanguage(key as AppLocale),
      }}
      trigger={['click']}
      placement="bottomRight"
    >
      <Button
        className="oa-lang-toggle"
        icon={<Languages className="h-4 w-4" />}
        aria-label={t('common.language')}
        title={t('common.language')}
      >
        <span className="oa-lang-btn-content">
          <FlagIcon locale={locale} className="oa-lang-flag" />
          {locale === 'zh-CN' ? '中' : 'EN'}
        </span>
      </Button>
    </Dropdown>
  );
}

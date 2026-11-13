import type { AppLocale } from '@/i18n';

export interface LanguageOption {
  /** 语言 locale 标识，须与后端 LocaleConfig 支持列表一致 */
  code: AppLocale;
  /** 语言显示名称（语言母语名）的 i18n key，zh-CN / en-US 两种资源中均写母语名 */
  labelKey: string;
}

/**
 * OA 支持的语言列表，PC 下拉与移动端菜单统一由此生成。
 *
 * 后续新增语言（如 ja-JP）：
 * 1. 在 `SUPPORTED_LOCALES` 追加 locale，并在两种资源文件补全全部文案；
 * 2. 在 `common` 资源补充 `languageJa: '日本語'`（两种资源保持一致，均用母语名）；
 * 3. 在此列表追加 `{ code: 'ja-JP', labelKey: 'common.languageJa' }`。
 */
export const SUPPORTED_LANGUAGES: LanguageOption[] = [
  { code: 'zh-CN', labelKey: 'common.languageZh' },
  { code: 'en-US', labelKey: 'common.languageEn' },
];

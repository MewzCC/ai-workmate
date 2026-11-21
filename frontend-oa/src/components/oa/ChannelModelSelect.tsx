import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Select, type SelectProps } from 'antd';
import { useScopedOverview } from '@/hooks/useScopedOverview';
import { aiConfigurationApi, type AiModelCategory } from '@/lib/aiConfigurationApi';

interface ChannelModelSelectProps extends Pick<SelectProps<string>, 'id' | 'value' | 'onChange' | 'disabled' | 'getPopupContainer'> {
  category: Extract<AiModelCategory, 'EMBEDDING' | 'RERANK' | 'OCR'>;
}

export default function ChannelModelSelect({ category, value, disabled, ...props }: ChannelModelSelectProps) {
  const { t } = useTranslation();
  const loadModels = useCallback(() => aiConfigurationApi.catalog(category), [category]);
  const { data, loading, load } = useScopedOverview(loadModels);
  const selected = value ?? 'default';
  const options = [
    { value: 'default', label: t('aiConfiguration.useServerDefault'), disabled: false },
    ...(data ?? []).map(model => ({ value: model.id, label: model.id, disabled: false })),
  ];
  if (!options.some(option => option.value === selected)) {
    options.push({ value: selected, label: loading ? selected : t('aiConfiguration.modelUnavailable', { model: selected }), disabled: true });
  }
  if (!loading && data?.length === 0) {
    options.push({ value: '', label: t('aiConfiguration.noEnabledCategoryModels'), disabled: true });
  }
  return <Select {...props} value={selected} disabled={disabled} loading={loading} options={options}
    onOpenChange={open => { if (open) void load(); }}
    showSearch={{ optionFilterProp: 'label' }} />;
}

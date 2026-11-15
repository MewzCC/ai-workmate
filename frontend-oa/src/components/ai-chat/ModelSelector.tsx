import { useEffect, useState } from 'react';
import { Select } from 'antd';
import { useTranslation } from 'react-i18next';
import { aiConfigurationApi } from '@/lib/aiConfigurationApi';

export default function ModelSelector({ value, disabled, onChange }: {
 value: string; disabled: boolean; onChange: (model: string) => void;
}) {
 const { t } = useTranslation();
 const [models, setModels] = useState<string[]>([]);
 const [failed, setFailed] = useState(false);
 useEffect(() => {
  let active = true;
  aiConfigurationApi.catalog().then(rows => { if (active) { setModels(rows.map(row => row.id)); setFailed(false); } })
   .catch(() => { if (active) setFailed(true); });
  return () => { active = false; };
 }, []);
 return <Select
  aria-label={t('chat.switchModel')} className="ai-model-select" value={value} showSearch optionFilterProp="label"
  status={failed ? 'error' : undefined} placeholder={t('aiConfiguration.chooseDefault')}
  options={[{ value: 'default', label: t('aiConfiguration.defaultModel') }, ...models.filter(id => id !== 'default').map(id => ({ value: id, label: id }))]}
  onChange={onChange} disabled={disabled} notFoundContent={t('aiConfiguration.noModels')}
 />;
}

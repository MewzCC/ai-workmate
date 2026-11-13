import { Alert } from 'antd';
import { useTranslation } from 'react-i18next';
import type { PageCapability } from '@/types/oa';

export function PageToolAvailabilityNotice({ capability }: { capability: PageCapability }) {
  const { t } = useTranslation();
  if (capability.tools.length > 0) return null;
  const reason = capability.unavailableReason ?? 'NO_AVAILABLE_TOOLS';
  // Do not display internal policy details or build translation keys from server input.
  return <Alert type="info" showIcon title={t('oa.ai.noActions')}
    description={reason === 'NO_AVAILABLE_TOOLS' ? t('oa.ai.noAvailableToolsReason') : undefined} />;
}

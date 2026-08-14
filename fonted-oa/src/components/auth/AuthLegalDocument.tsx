'use client';

import { CheckCircleOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { Alert, Divider, Typography } from 'antd';
import { useTranslation } from 'react-i18next';

export type LegalDocumentType = 'service' | 'privacy';

interface AuthLegalDocumentProps {
  type: LegalDocumentType;
}

const SERVICE_SECTIONS = ['scope', 'account', 'use', 'content', 'availability', 'termination', 'law'] as const;
const PRIVACY_SECTIONS = ['collection', 'use', 'storage', 'sharing', 'cookies', 'rights', 'minors', 'updates'] as const;

export function AuthLegalDocument({ type }: AuthLegalDocumentProps) {
  const { t } = useTranslation();
  const sections = type === 'service' ? SERVICE_SECTIONS : PRIVACY_SECTIONS;

  return (
    <article className="auth-legal-document">
      <div className="auth-legal-summary">
        <span className="auth-legal-summary-icon" aria-hidden="true">
          {type === 'service' ? <CheckCircleOutlined /> : <SafetyCertificateOutlined />}
        </span>
        <div>
          <Typography.Text className="auth-legal-effective">{t('auth.legal.effectiveDate')}</Typography.Text>
          <Typography.Paragraph>{t(`auth.legal.${type}.intro`)}</Typography.Paragraph>
        </div>
      </div>

      <Alert
        className="auth-legal-highlight"
        type="info"
        showIcon
        message={t(`auth.legal.${type}.highlightTitle`)}
        description={t(`auth.legal.${type}.highlightBody`)}
      />

      <Divider />
      <div className="auth-legal-sections">
        {sections.map((section, index) => (
          <section key={section}>
            <Typography.Title level={4}>
              {index + 1}. {t(`auth.legal.${type}.sections.${section}.title`)}
            </Typography.Title>
            <Typography.Paragraph>{t(`auth.legal.${type}.sections.${section}.body`)}</Typography.Paragraph>
          </section>
        ))}
      </div>
      <Divider />
      <Typography.Paragraph className="auth-legal-contact">
        {t('auth.legal.contact')}
      </Typography.Paragraph>
    </article>
  );
}

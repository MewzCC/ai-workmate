'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  App, Alert, Button, Card, Col, Drawer, Empty, Form, Input, InputNumber, Row, Select,
  Space, Spin, Switch, Tag, Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { FormInstance } from 'antd';
import type { ReactNode } from 'react';
import { OaIcon } from '@/components/OaIcon';
import ResponsiveTable from '@/components/oa/ResponsiveTable';
import { formatOaApiError } from '@/lib/oaApi';
import { message } from '@/lib/antdMessage';
import {
  tenantConfigApi, type TenantBusinessPayload, type TenantConfiguration,
  type TenantConfigurationHistory, type TenantFeaturesPayload, type TenantProfilePayload,
  type TenantSecurityPayload,
} from '@/lib/tenantConfigApi';

type SectionKey = 'profile' | 'features' | 'business' | 'security';
type SavePayload = TenantProfilePayload | TenantFeaturesPayload | TenantBusinessPayload | TenantSecurityPayload;

const sectionIcons: Record<SectionKey, 'tenant' | 'settings' | 'attendance' | 'data-permission'> = {
  profile: 'tenant', features: 'settings', business: 'attendance', security: 'data-permission',
};

export default function TenantConfigPage() {
  const { t, i18n } = useTranslation();
  const { modal } = App.useApp();
  const [profileForm] = Form.useForm<TenantProfilePayload>();
  const [featuresForm] = Form.useForm<TenantFeaturesPayload>();
  const [businessForm] = Form.useForm<TenantBusinessPayload>();
  const [securityForm] = Form.useForm<TenantSecurityPayload>();
  const [configuration, setConfiguration] = useState<TenantConfiguration>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<SectionKey>();
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [history, setHistory] = useState<TenantConfigurationHistory[]>([]);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [historyPage, setHistoryPage] = useState(1);

  const populate = useCallback((value: TenantConfiguration) => {
    setConfiguration(value);
    profileForm.setFieldsValue(value);
    featuresForm.setFieldsValue(value);
    businessForm.setFieldsValue(value);
    securityForm.setFieldsValue(value);
  }, [businessForm, featuresForm, profileForm, securityForm]);

  const load = useCallback(async () => {
    setLoading(true);
    try { populate(await tenantConfigApi.get()); }
    catch (error) { message.error(formatOaApiError(error)); }
    finally { setLoading(false); }
  }, [populate]);

  const loadHistory = useCallback(async (page = historyPage) => {
    setHistoryLoading(true);
    try {
      const result = await tenantConfigApi.history(page, 20);
      setHistory(result.records); setHistoryTotal(result.total); setHistoryPage(result.page);
    } catch (error) { message.error(formatOaApiError(error)); }
    finally { setHistoryLoading(false); }
  }, [historyPage]);

  useEffect(() => { void load(); }, [load]);

  const submit = async (section: SectionKey, payload: SavePayload) => {
    if (!configuration) return;
    const execute = async () => {
      setSaving(section);
      try {
        const body = { ...payload, version: configuration.version };
        const saved = section === 'profile' ? await tenantConfigApi.updateProfile(body as TenantProfilePayload)
          : section === 'features' ? await tenantConfigApi.updateFeatures(body as TenantFeaturesPayload)
            : section === 'business' ? await tenantConfigApi.updateBusiness(body as TenantBusinessPayload)
              : await tenantConfigApi.updateSecurity(body as TenantSecurityPayload);
        populate(saved);
        message.success(t('tenantConfig.saveSuccess'));
        if (historyOpen) void loadHistory(1);
      } catch (error) { message.error(formatOaApiError(error)); }
      finally { setSaving(undefined); }
    };
    modal.confirm({
      title: t('tenantConfig.confirmTitle'), content: t(`tenantConfig.confirm.${section}`),
      okText: t('common.confirm'), cancelText: t('common.cancel'), onOk: execute,
    });
  };

  const enabledCount = useMemo(() => configuration ? [configuration.approvalEnabled,
    configuration.attendanceEnabled, configuration.assetEnabled, configuration.meetingEnabled,
    configuration.visitorEnabled, configuration.sealEnabled].filter(Boolean).length : 0, [configuration]);

  const historyColumns: ColumnsType<TenantConfigurationHistory> = [
    { title: t('tenantConfig.historyVersion'), dataIndex: 'version', width: 90, render: (value: number) => <Tag>v{value}</Tag> },
    { title: t('tenantConfig.historyCategory'), dataIndex: 'category', render: (value: TenantConfigurationHistory['category']) => t(`tenantConfig.category.${value.toLowerCase()}`) },
    { title: t('tenantConfig.historyActor'), dataIndex: 'changedBy', width: 120, render: (value: number) => `#${value}` },
    { title: t('tenantConfig.historyTime'), dataIndex: 'createdAt', width: 190, render: (value: string) => new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) },
  ];

  if (loading && !configuration) return <div className="oa-tenant-loading"><Spin size="large" /></div>;
  if (!configuration) return <Empty description={t('tenantConfig.loadFailed')} />;

  const sections: Array<{ key: SectionKey; form: FormInstance; content: ReactNode }> = [
    { key: 'profile', form: profileForm, content: <>
      <Form.Item name="tenantName" label={t('tenantConfig.tenantName')} rules={[{ required: true }]}><Input maxLength={120} /></Form.Item>
      <Form.Item name="tenantShortName" label={t('tenantConfig.shortName')}><Input maxLength={40} /></Form.Item>
      <Row gutter={12}><Col xs={24} md={12}><Form.Item name="locale" label={t('tenantConfig.locale')} rules={[{ required: true }]}><Select options={[{ value: 'zh-CN', label: t('common.languageZh') }, { value: 'en-US', label: t('common.languageEn') }]} /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item name="timezone" label={t('tenantConfig.timezone')} rules={[{ required: true }]}><Select showSearch options={['Asia/Shanghai', 'Asia/Hong_Kong', 'Asia/Singapore', 'Asia/Tokyo', 'Europe/London', 'America/New_York', 'UTC'].map((value) => ({ value, label: value }))} /></Form.Item></Col></Row>
    </> },
    { key: 'features', form: featuresForm, content: <div className="oa-tenant-switch-grid">{(['approval', 'attendance', 'asset', 'meeting', 'visitor', 'seal'] as const).map((feature) => <div key={feature} className="oa-tenant-switch-item">
      <Form.Item name={`${feature}Enabled`} valuePropName="checked" noStyle><Switch checkedChildren={t('tenantConfig.enabled')} unCheckedChildren={t('tenantConfig.disabled')} /></Form.Item>
      <span><strong>{t(`tenantConfig.feature.${feature}`)}</strong><small>{t(`tenantConfig.featureDescription.${feature}`)}</small></span>
    </div>)}</div> },
    { key: 'business', form: businessForm, content: <Row gutter={12}>
      <Col xs={24} md={8}><Form.Item name="fiscalYearStartMonth" label={t('tenantConfig.fiscalMonth')} rules={[{ required: true }]}><InputNumber min={1} max={12} /></Form.Item></Col>
      <Col xs={24} md={8}><Form.Item name="defaultApprovalDays" label={t('tenantConfig.approvalDays')} rules={[{ required: true }]}><InputNumber min={1} max={30} /></Form.Item></Col>
      <Col xs={24} md={8}><Form.Item name="expenseCurrency" label={t('tenantConfig.currency')} rules={[{ required: true }]}><Select options={['CNY', 'USD', 'EUR', 'HKD'].map((value) => ({ value, label: value }))} /></Form.Item></Col>
    </Row> },
    { key: 'security', form: securityForm, content: <>
      <Alert type="info" showIcon message={t('tenantConfig.securityBoundary')} description={t('tenantConfig.securityBoundaryDescription')} />
      <Row gutter={12} className="oa-tenant-security-fields"><Col xs={24} md={12}><Form.Item name="passwordMinLength" label={t('tenantConfig.passwordLength')} rules={[{ required: true }]}><InputNumber min={8} max={32} /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item name="sessionTimeoutMinutes" label={t('tenantConfig.sessionTimeout')} rules={[{ required: true }]}><InputNumber min={15} max={1440} /></Form.Item></Col></Row>
    </> },
  ];

  return <section className="oa-tenant-page">
    <header className="oa-tenant-hero"><div className="oa-tenant-heading"><span className="oa-tenant-icon"><OaIcon name="tenant" size={24} /></span><div>
      <Typography.Text className="oa-tenant-eyebrow">{t('tenantConfig.eyebrow')}</Typography.Text>
      <Typography.Title level={3}>{t('tenantConfig.title')}</Typography.Title>
      <Typography.Paragraph>{t('tenantConfig.description')}</Typography.Paragraph>
    </div></div><Space><Button icon={<OaIcon name="history" />} onClick={() => { setHistoryOpen(true); void loadHistory(1); }}>{t('tenantConfig.history')}</Button>
      <Button icon={<OaIcon name="reload" />} onClick={() => void load()}>{t('workbench.refresh')}</Button></Space></header>

    <div className="oa-tenant-summary"><div><small>{t('tenantConfig.currentTenant')}</small><strong>{configuration.tenantShortName || configuration.tenantName}</strong></div>
      <div><small>{t('tenantConfig.featureSummary')}</small><strong>{t('tenantConfig.featuresEnabled', { count: enabledCount })}</strong></div>
      <div><small>{t('tenantConfig.securitySummary')}</small><strong>{t('tenantConfig.securityValue', { length: configuration.passwordMinLength, minutes: configuration.sessionTimeoutMinutes })}</strong></div>
      <Tag color={configuration.canManage ? 'blue' : 'default'}>{t(configuration.canManage ? 'tenantConfig.manageMode' : 'tenantConfig.readOnlyMode')}</Tag></div>

    <div className="oa-tenant-grid">{sections.map(({ key, form, content }) => <Card key={key} className={`oa-tenant-section oa-tenant-section--${key}`} title={<Space><OaIcon name={sectionIcons[key]} /><span>{t(`tenantConfig.section.${key}`)}</span></Space>} extra={<Tag>v{configuration.version}</Tag>}>
      <Typography.Paragraph className="oa-tenant-section-description">{t(`tenantConfig.sectionDescription.${key}`)}</Typography.Paragraph>
      <Form form={form} layout="vertical" disabled={!configuration.canManage} onFinish={(values) => void submit(key, values as SavePayload)}>{content}
        {configuration.canManage && <div className="oa-tenant-actions"><Button htmlType="button" onClick={() => populate(configuration)}>{t('common.reset')}</Button><Button type="primary" htmlType="submit" loading={saving === key}>{t('common.save')}</Button></div>}
      </Form>
    </Card>)}</div>

    <Drawer width={680} open={historyOpen} title={t('tenantConfig.historyTitle')} onClose={() => setHistoryOpen(false)}>
      <ResponsiveTable rowKey="id" columns={historyColumns} dataSource={history} loading={historyLoading}
        locale={{ emptyText: <Empty description={t('tenantConfig.emptyHistory')} /> }}
        pagination={{ current: historyPage, pageSize: 20, total: historyTotal, onChange: (page) => void loadHistory(page) }} />
    </Drawer>
  </section>;
}
